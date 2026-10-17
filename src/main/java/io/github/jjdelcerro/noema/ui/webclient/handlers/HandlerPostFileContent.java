package io.github.jjdelcerro.noema.ui.webclient.handlers;

import io.github.jjdelcerro.noema.lib.api.Agent;
import io.github.jjdelcerro.noema.ui.webclient.NoemaWebServer;
import io.javalin.http.Context;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.tika.parser.txt.CharsetDetector;
import org.apache.tika.parser.txt.CharsetMatch;

/**
 *
 * @author jjdelcerro
 */
public class HandlerPostFileContent extends AbstractHandler {

  // Umbral minimo de confianza de Tika para aceptar una deteccion. Un fichero
  // ASCII puro suele devolver ~36, por lo que queda descartado.
  private static final int CONFIDENCE_THRESHOLD = 38;

  // Numero maximo de bytes que se muestrean al detectar el charset.
  private static final int SAMPLE_SIZE = 16384;

  // Declaracion de encoding en un prologo XML: <?xml ... encoding="XXX" ... ?>
  // Grupo 1: apertura hasta la comilla. Grupo 2: valor. Grupo 3: comilla de cierre.
  private static final Pattern XML_ENCODING =
      Pattern.compile("(?i)(<\\?xml[^>]*encoding\\s*=\\s*[\"'])([^\"']+)([\"'])");

  // Charset por defecto (fichero nuevo o cuando nada permite detectarlo).
  private static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;

  public static class FileContentRequest {
    public String file;
    public String content;
  }


  public HandlerPostFileContent(Agent agent) {
    super(agent);
  }

  @Override
  public void handle(Context ctx) throws Exception {
    FileContentRequest body = ctx.bodyAsClass(FileContentRequest.class);
    if (body == null || body.file == null || body.file.isBlank()) {
      ctx.status(400).json(Map.of("error", "El campo 'file' es obligatorio"));
      return;
    }

    Path target = resolveFilePath(body.file);
    if (target == null) {
      ctx.status(400).json(Map.of("error", "Ruta de fichero invalida: " + body.file));
      return;
    }

    try {
      if (target.getParent() != null) {
        Files.createDirectories(target.getParent());
      }

      String content = body.content != null ? body.content : "";

      Charset charset = detectCharset(content, target); // TODO: habria que sacar a algo como DetectCharsetUtils estas funciones.
      if (!canEncode(charset, content)) {
        // El contenido editado tiene caracteres que no existen en el charset
        // elegido: se pasa a UTF-8 y, si habia declaracion, se corrige para
        // que no quede mintiendo.
        charset = DEFAULT_CHARSET;
        content = rewriteDeclaredEncoding(content, charset);
      }

      byte[] bytes = content.getBytes(charset);
      if (charset.equals(detectBomCharset(target))) {
        bytes = prependBom(bytes, charset);
      }
      Files.write(target, bytes);

      ctx.json(Map.of("status", "success", "file", body.file, "encoding", charset.name()));
    } catch (Exception e) {
      LOGGER.error("Error escribiendo fichero: {}", target, e);
      ctx.status(500).json(Map.of("error", "Error al guardar el fichero: " + e.getMessage()));
    }
  }

  /**
   * Determina el charset con el que debe escribirse el fichero a partir del
   * contenido que se va a guardar y de la ruta destino. Prioridad:
   *
   *   1) BOM del fichero existente (hecho fisico del propio fichero).
   *   2) Declaracion de encoding presente en el contenido a guardar (es la
   *      que tendra el fichero resultante, no la del original en disco).
   *   3) Deteccion sobre el fichero original en disco.
   *   4) UTF-8 (fichero nuevo o indetectable).
   *
   * @param content contenido que se va a escribir.
   * @param path    ruta destino (puede no existir todavia).
   * @return el charset a usar; nunca null.
   */
  private Charset detectCharset(String content, Path path) {
    Charset bom = detectBomCharset(path);
    if (bom != null) {
      return bom;
    }
    Charset declared = detectDeclaredCharset(content);
    if (declared != null) {
      return declared;
    }
    Charset detected = detectCharsetFromFile(path);
    if (detected != null) {
      return detected;
    }
    return DEFAULT_CHARSET;
  }

  /**
   * Extrae el charset declarado en el prologo XML del contenido, si lo hay.
   * Se lee del contenido a guardar y no del disco, porque es la declaracion
   * que tendra el fichero resultante.
   */
  private Charset detectDeclaredCharset(String content) {
    if (content == null) {
      return null;
    }
    Matcher matcher = XML_ENCODING.matcher(content);
    if (!matcher.find()) {
      return null;
    }
    try {
      return Charset.forName(matcher.group(2));
    } catch (Exception e) {
      return null;
    }
  }

  /**
   * Detecta el charset de un fichero existente. Descarta primero los casos
   * deterministas (vacio, ASCII puro, UTF-8 valido) y solo despues recurre a
   * Tika, que devuelve ISO-8859-1 por defecto cuando no tiene informacion.
   */
  private Charset detectCharsetFromFile(Path path) {
    if (path == null || !Files.isRegularFile(path)) {
      return null;
    }
    byte[] sample = readSample(path);
    if (sample.length == 0) {
      return DEFAULT_CHARSET;
    }
    if (isPureAscii(sample)) {
      return StandardCharsets.UTF_8;
    }
    try {
      CharsetDetector detector = new CharsetDetector();
      detector.setText(sample);
      CharsetMatch match = detector.detect();
      if (match != null && match.getConfidence() > CONFIDENCE_THRESHOLD) {
        return Charset.forName(match.getName());
      }
    } catch (Exception e) {
      // Deteccion no concluyente: se cae al charset por defecto.
    }
    return null;
  }

  /**
   * Devuelve el charset indicado por el BOM del fichero, o null si no tiene.
   */
  private Charset detectBomCharset(Path path) {
    if (path == null || !Files.isRegularFile(path)) {
      return null;
    }
    byte[] head = readSample(path, 3);
    if (head.length >= 3
        && (head[0] & 0xFF) == 0xEF && (head[1] & 0xFF) == 0xBB && (head[2] & 0xFF) == 0xBF) {
      return StandardCharsets.UTF_8;
    }
    if (head.length >= 2 && (head[0] & 0xFF) == 0xFE && (head[1] & 0xFF) == 0xFF) {
      return StandardCharsets.UTF_16BE;
    }
    if (head.length >= 2 && (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xFE) {
      return StandardCharsets.UTF_16LE;
    }
    return null;
  }

  /**
   * Comprueba que el texto se puede codificar con el charset indicado sin
   * sustituciones silenciosas (evita el '?' de String.getBytes).
   */
  private boolean canEncode(Charset charset, String text) {
    if (charset == null || text == null) {
      return false;
    }
    CharsetEncoder encoder = charset.newEncoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT);
    return encoder.canEncode(text);
  }

  /**
   * Reescribe el valor de 'encoding' de la declaracion XML del contenido, si
   * existe. Se usa cuando el charset de destino cambia (p.ej. a UTF-8) para
   * que la declaracion no quede mintiendo.
   */
  private String rewriteDeclaredEncoding(String content, Charset charset) {
    if (content == null || charset == null) {
      return content;
    }
    Matcher matcher = XML_ENCODING.matcher(content);
    if (!matcher.find()) {
      return content;
    }
    return matcher.replaceFirst("$1" + Matcher.quoteReplacement(charset.name()) + "$3");
  }

  /**
   * Lee hasta SAMPLE_SIZE bytes del principio del fichero. Devuelve un array
   * vacio si el fichero no existe o no se puede leer.
   */
  private byte[] readSample(Path path) {
    return readSample(path, SAMPLE_SIZE);
  }

  private byte[] readSample(Path path, int maxBytes) {
    if (path == null || !Files.isRegularFile(path)) {
      return new byte[0];
    }
    try (InputStream in = new BufferedInputStream(new FileInputStream(path.toFile()))) {
      byte[] buffer = new byte[maxBytes];
      int total = 0;
      int read;
      while (total < maxBytes && (read = in.read(buffer, total, maxBytes - total)) != -1) {
        total += read;
      }
      if (total == maxBytes) {
        return buffer;
      }
      byte[] out = new byte[total];
      System.arraycopy(buffer, 0, out, 0, total);
      return out;
    } catch (Exception e) {
      return new byte[0];
    }
  }

  /**
   * Indica si la muestra es ASCII puro (todos los bytes por debajo de 0x80).
   */
  private boolean isPureAscii(byte[] bytes) {
    for (int i = 0; i < bytes.length; i++) {
      if ((bytes[i] & 0xFF) > 0x7F) {
        return false;
      }
    }
    return true;
  }

  /**
   * Antepone el BOM correspondiente al charset, si lo tiene.
   */
  private byte[] prependBom(byte[] bytes, Charset charset) {
    byte[] bom = bomBytes(charset);
    if (bom == null) {
      return bytes;
    }
    byte[] out = new byte[bom.length + bytes.length];
    System.arraycopy(bom, 0, out, 0, bom.length);
    System.arraycopy(bytes, 0, out, bom.length, bytes.length);
    return out;
  }

  private byte[] bomBytes(Charset charset) {
    if (StandardCharsets.UTF_8.equals(charset)) {
      return new byte[] { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF };
    }
    if (StandardCharsets.UTF_16BE.equals(charset)) {
      return new byte[] { (byte) 0xFE, (byte) 0xFF };
    }
    if (StandardCharsets.UTF_16LE.equals(charset)) {
      return new byte[] { (byte) 0xFF, (byte) 0xFE };
    }
    return null;
  }

}
