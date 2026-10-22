package io.github.jjdelcerro.noema.ui.webclient.workspace.notes;

import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

/**
 * Cache en disco de resultados de busqueda.
 *
 * Un fichero por consulta, nombrado search_{SHA1(mode|root|query)}.idx. El
 * contenido tiene seis lineas de cabecera, un separador y una linea NDJSON por
 * resultado:
 *
 * QUERY: ...
 * MODE: ...
 * ROOT: ...
 * TIMESTAMP: ...
 * TOTAL: ...
 * TRUNCATED: false
 * ---
 * {"id":"...","title":"...",...}
 *
 * TOTAL es el numero de coincidencias reales, que puede ser mayor que el numero
 * de lineas NDJSON si la busqueda alcanzo el limite. TRUNCATED distingue "hay
 * exactamente TOTAL" de "hay al menos TOTAL".
 *
 * La cache es compartida por los tres modos y no conoce el motor que la llena.
 *
 * @author jjdelcerro
 */
public class NoteSearchCache {

  private static final Logger LOGGER = LoggerFactory.getLogger(NoteSearchCache.class);

  public static final String HEADER_QUERY = "QUERY";
  public static final String HEADER_MODE = "MODE";
  public static final String HEADER_ROOT = "ROOT";
  public static final String HEADER_TIMESTAMP = "TIMESTAMP";
  public static final String HEADER_TOTAL = "TOTAL";
  public static final String HEADER_TRUNCATED = "TRUNCATED";

  private static final String SEPARATOR = "---";
  private static final String PREFIX = "search_";
  private static final String SUFFIX = ".idx";
  private static final int MAX_ENTRIES = 10;

  private final Path cacheFolder;
  private final Gson gson;

  public NoteSearchCache(Path cacheFolder) {
    this.cacheFolder = cacheFolder.toAbsolutePath().normalize();
    this.gson = new Gson();
  }

  public Path getCacheFolder() {
    return this.cacheFolder;
  }

  /**
   * Calcula la clave de una consulta. Los parametros fijos del motor (limit y
   * minSimilarity) no entran: si cambian, es una version nueva del codigo, no
   * una consulta distinta.
   *
   * @param mode modo de busqueda
   * @param root subarbol, puede ser null
   * @param query termino buscado
   * @return SHA1 en hexadecimal
   */
  public String keyFor(String mode, String root, String query) {
    String raw = safe(mode) + "|" + safe(root) + "|" + safe(query);
    return this.sha1(raw);
  }

  public Path fileFor(String key) {
    return this.cacheFolder.resolve(PREFIX + key + SUFFIX);
  }

  /**
   * Devuelve el resultado cacheado completo, o null si no existe.
   *
   * @param key clave calculada con keyFor
   * @return resultado o null
   * @throws IOException si falla la lectura
   */
  public NoteSearchResult read(String key) throws IOException {
    return this.read(key, 0, 0);
  }

  /**
   * Devuelve una pagina del resultado cacheado.
   *
   * @param key clave calculada con keyFor
   * @param page pagina solicitada, base cero
   * @param pageSize tamano de pagina; si es menor o igual que cero no se pagina
   * @return resultado paginado, o null si no existe la entrada
   * @throws IOException si falla la lectura
   */
  public NoteSearchResult read(String key, int page, int pageSize) throws IOException {
    Path file = this.fileFor(key);
    if (!Files.isRegularFile(file)) {
      return null;
    }
    // TODO: no usar realAllLines, leer solo las lineas hasta SEPARATOR
    List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
    int separator = lines.indexOf(SEPARATOR);
    if (separator < 0) {
      LOGGER.warn("Entrada de cache sin separador: {}", file);
      return null;
    }

    Map<String, String> headers = new LinkedHashMap<>();
    for (int i = 0; i < separator; i++) {
      String line = lines.get(i);
      int colon = line.indexOf(':');
      if (colon > 0) {
        headers.put(line.substring(0, colon).trim(), line.substring(colon + 1).trim());
      }
    }
    int total = NumberUtils.toInt(headers.get("TOTAL"));
    if( total < 1 ) {
      return null;
    }
    List<NoteSearchItem> items = new ArrayList<>();
    for (int i = separator + 1; i < lines.size(); i++) {
      String line = lines.get(i).trim();
      if (line.isEmpty()) {
        continue;
      }
      NoteSearchItem item = this.fromJson(line);
      if (item != null) {
        items.add(item);
      }
    }

    List<NoteSearchItem> pageItems = items;
    if (pageSize > 0) {
      int from = Math.max(0, page) * pageSize;
      if (from >= items.size()) {
        pageItems = new ArrayList<>();
      } else {
        int to = Math.min(items.size(), from + pageSize);
        pageItems = new ArrayList<>(items.subList(from, to));
      }
    }

    NoteSearchResult result = new NoteSearchResult();
    result.setQuery(headers.get(HEADER_QUERY));
    result.setMode(headers.get(HEADER_MODE));
    result.setRoot(headers.get(HEADER_ROOT));
    result.setTimestamp(headers.get(HEADER_TIMESTAMP));
    result.setTotal(this.parseInt(headers.get(HEADER_TOTAL), items.size()));
    result.setTruncated(Boolean.parseBoolean(headers.get(HEADER_TRUNCATED)));
    result.setItems(pageItems);
    return result;
  }

  /**
   * Escribe el resultado en la cache y aplica la politica de retencion.
   *
   * @param key clave calculada con keyFor
   * @param result resultado completo
   * @throws IOException si falla la escritura
   */
  public void write(String key, NoteSearchResult result) throws IOException {

    if( result == null ) {
      return;
    }
    Files.createDirectories(this.cacheFolder);
    Path target = this.fileFor(key);
    Path temp = this.cacheFolder.resolve(PREFIX + key + SUFFIX + ".tmp");
    try (BufferedWriter writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
      writer.write(HEADER_QUERY + ": " + safe(result.getQuery()));
      writer.newLine();
      writer.write(HEADER_MODE + ": " + safe(result.getMode()));
      writer.newLine();
      writer.write(HEADER_ROOT + ": " + safe(result.getRoot()));
      writer.newLine();
      writer.write(HEADER_TIMESTAMP + ": " + safe(result.getTimestamp()));
      writer.newLine();
      writer.write(HEADER_TOTAL + ": " + result.getTotal());
      writer.newLine();
      writer.write(HEADER_TRUNCATED + ": " + result.isTruncated());
      writer.newLine();
      writer.write(SEPARATOR);
      writer.newLine();
      if (result.getItems() != null) {
        for (NoteSearchItem item : result.getItems()) {
          writer.write(this.gson.toJson(item));
          writer.newLine();
        }
      }
    }
    Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
    this.prune();
  }

  /**
   * Borra la entrada indicada. No falla si no existe.
   *
   * @param key clave de la consulta
   * @throws IOException si falla el borrado
   */
  public void invalidate(String key) throws IOException {
    Files.deleteIfExists(this.fileFor(key));
  }

  /**
   * Conserva solo las MAX_ENTRIES entradas con TIMESTAMP mas reciente.
   *
   * El orden lexicografico de un instante ISO-8601 coincide con el
   * cronologico, asi que no hace falta parsear la fecha.
   */
  private void prune() {
    if (!Files.isDirectory(this.cacheFolder)) {
      return;
    }
    try (Stream<Path> stream = Files.list(this.cacheFolder)) {
      List<Path> files = stream
              .filter(candidate -> candidate.getFileName().toString().endsWith(SUFFIX))
              .collect(Collectors.toList());
      if (files.size() <= MAX_ENTRIES) {
        return;
      }
      files.sort(Comparator.comparing(this::timestampOf).reversed());
      for (int i = MAX_ENTRIES; i < files.size(); i++) {
        Path victim = files.get(i);
        try {
          Files.deleteIfExists(victim);
        } catch (IOException e) {
          LOGGER.warn("No se pudo borrar la entrada de cache: {}", victim, e);
        }
      }
    } catch (IOException e) {
      LOGGER.warn("No se pudo aplicar la politica de retencion en: {}", this.cacheFolder, e);
    }
  }

  private String timestampOf(Path file) {
    try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
      String line;
      while ((line = reader.readLine()) != null) {
        if (SEPARATOR.equals(line.trim())) {
          break;
        }
        if (line.startsWith(HEADER_TIMESTAMP + ":")) {
          return line.substring((HEADER_TIMESTAMP + ":").length()).trim();
        }
      }
    } catch (IOException e) {
      LOGGER.warn("No se pudo leer la cabecera de: {}", file, e);
    }
    return "";
  }

  private NoteSearchItem fromJson(String json) {
    try {
      return this.gson.fromJson(json, NoteSearchItem.class);
    } catch (Exception e) {
      LOGGER.warn("Linea NDJSON invalida en cache: {}", json, e);
      return null;
    }
  }

  private String sha1(String text) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-1");
      byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder(hash.length * 2);
      for (byte value : hash) {
        hex.append(String.format(Locale.ROOT, "%02x", value));
      }
      return hex.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-1 no disponible", e);
    }
  }

  private int parseInt(String text, int fallback) {
    if (text == null) {
      return fallback;
    }
    try {
      return Integer.parseInt(text.trim());
    } catch (NumberFormatException e) {
      return fallback;
    }
  }

  private String safe(String text) {
    return (text == null) ? "" : text;
  }
}
