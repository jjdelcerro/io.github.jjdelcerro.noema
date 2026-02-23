package io.github.jjdelcerro.noema.lib.impl.services.conversation.tools.file;

import io.github.jjdelcerro.noema.lib.impl.AbstractAgentTool;
import dev.langchain4j.agent.tool.JsonSchemaProperty;
import dev.langchain4j.agent.tool.ToolSpecification;
import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.AgentTool;
import org.apache.commons.collections4.map.LRUMap;
import org.apache.commons.lang3.StringUtils;
import org.apache.tika.Tika;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.github.jjdelcerro.noema.lib.AgentAccessControl.AccessMode.PATH_ACCESS_READ;
import java.util.HashMap;

public class FileReadTool extends AbstractAgentTool {

  // TODO: gestionar que pasa con lineas muy largas, por ejemplo de 1Mb.
  
  private static final int DEFAULT_MAX_LINES = 1000;
  private static final int CACHE_SIZE = 30;
  public static final String TOOL_NAME = "file_read";

  private final Tika tika = new Tika();

  // Cache: Path Absoluto -> Metadatos (Lineas + Fecha Modificación)
  // Usamos Collections.synchronizedMap porque LRUMap no es thread-safe por defecto
  private final Map<String, FileMeta> lineCountCache;

  private record FileMeta(long lineCount, FileTime lastModifiedTime) {

  }

  public FileReadTool(Agent agent) {
    super(agent);
    this.lineCountCache = Collections.synchronizedMap(new LRUMap<>(this.getCacheSize()));
  }

  private int getDefaultMaxLines() {
    return DEFAULT_MAX_LINES;
  }

  private int getCacheSize() {
    return CACHE_SIZE;
  }

  @Override
  public ToolSpecification getSpecification() {
    return ToolSpecification.builder()
            .name(TOOL_NAME)
            .description("""
                    Lee el contenido de un archivo de texto.
                    Soporta paginación automática para archivos grandes.
                    Si el archivo se trunca por tamaño, se incluirán instrucciones para leer el siguiente bloque.
                    """)
            .addParameter("path", JsonSchemaProperty.STRING,
                    JsonSchemaProperty.description("Ruta del archivo."))
            .addParameter("offset", JsonSchemaProperty.INTEGER,
                    JsonSchemaProperty.description("Línea inicial (0-based). Default: 0."))
            .addParameter("limit", JsonSchemaProperty.INTEGER,
                    JsonSchemaProperty.description("Máximo de líneas a leer. Default: " + this.getDefaultMaxLines()))
            .build();
  }

  @Override
  public int getMode() {
    return AgentTool.MODE_READ;
  }

  @Override
  @SuppressWarnings("UseSpecificCatch")
  public String execute(String jsonArguments) {
    try {
      ReadArgs args = gson.fromJson(jsonArguments, ReadArgs.class);
      String relPath = args.path;
      Path filePath = this.resolvePathOrNull(relPath, PATH_ACCESS_READ);
      if (filePath == null) {
        return error("Acceso denegado o ruta fuera del sandbox.");
      }
      return execute(filePath, relPath, TOOL_NAME, args.offset, args.limit);
    } catch (Exception e) {
      LOGGER.warn("Error leyendo archivo, args=" + StringUtils.replace(jsonArguments, "\n", " "), e);
      return error("Error I/O: " + e.getMessage());
    }
  }

  public String execute(Path thePath, String originalPath, String toolName, int theOffset, int theLimit) throws IOException {
    Map<String,Object> args = Map.of("path", originalPath);
    return this.execute(thePath, originalPath, toolName, args, theOffset, theLimit);
  }
  
  public String execute(Path thePath, String originalPath, String toolName, Map<String,Object> args, int theOffset, int theLimit) throws IOException {
    if (!Files.exists(thePath) || !Files.isRegularFile(thePath)) {
      return error("El archivo no existe o es un directorio: " + thePath);
    }
    if (isBinaryResource(thePath)) {
      String mime = tika.detect(thePath);
      return error("El archivo parece binario (" + mime + "). Usa 'file_extract_text' o 'file_find'.");
    }

    // Obtener total de líneas (cacheado)
    long totalLines = getLineCountWithCache(thePath);

    try (Stream<String> lines = Files.lines(thePath, StandardCharsets.UTF_8)) {
      return this.execute(lines, originalPath, toolName, args, totalLines, theOffset, theLimit);
    }
  }

  public String execute(Stream<String> lines, String originalPath, String toolName, long totalLines, int theOffset, int theLimit) throws IOException {
    Map<String,Object> args = new HashMap<>();
    args.put("path", originalPath);
    return execute(lines, originalPath, toolName, args, totalLines, theOffset, theLimit);
  }
  
  public String execute(Stream<String> lines, String originalPath, String toolName, Map<String,Object> args, long totalLines, int theOffset, int theLimit) throws IOException {

    int offset = theOffset > 0 ? theOffset : 0;
    int limit = theLimit > 0 ? theLimit : getDefaultMaxLines();

    if (offset >= totalLines && totalLines > 0) {
      return error("Offset (" + offset + ") fuera de rango. El archivo tiene " + totalLines + " líneas.");
    }

    String content;
    int linesRead;
    List<String> chunk = lines.skip(offset)
            .limit(limit)
            .collect(Collectors.toList());

    content = String.join("\n", chunk);
    linesRead = chunk.size();

    boolean isTruncated = (offset + linesRead) < totalLines;

    // CASO A: Lectura completa desde el inicio (sin ruido)
    if (offset == 0 && !isTruncated) {
      return content;
    }

    // CASO B: Paginación o Truncado (con metadatos e instrucciones)
    StringBuilder sb = new StringBuilder();
    int nextOffset = offset + linesRead;

    // Cabecera informativa
    sb.append("[SYSTEM: Showing lines ").append(offset)
            .append("-").append(offset + linesRead - 1)
            .append(" of ").append(totalLines).append("]\n");

    // Hint solo si queda contenido por leer
    if (isTruncated) {
      args.put("offset", nextOffset);
      args.put("limit", limit);
      String nextArgs = gson.toJson(args);
      sb.append("[HINT: To read the next block, call tool '")
              .append(toolName)
              .append("' with args: ")
              .append(nextArgs)
              .append("]\n\n");
    } else {
      sb.append("\n"); // Separador simple si es el último bloque paginado
    }

    sb.append(content);
    return sb.toString();

  }

  /**
   * Obtiene el número de líneas usando caché LRU. Invalida si la fecha de
   * modificación del archivo ha cambiado.
   */
  private long getLineCountWithCache(Path filePath) throws IOException {
    String absPath = filePath.toAbsolutePath().toString();
    FileTime currentModifiedTime = Files.getLastModifiedTime(filePath);

    FileMeta cached = lineCountCache.get(absPath);

    // Cache Hit
    if (cached != null && cached.lastModifiedTime.equals(currentModifiedTime)) {
      return cached.lineCount;
    }

    // Cache Miss: Costoso recorrido del stream
    long count;
    try (Stream<String> lines = Files.lines(filePath, StandardCharsets.UTF_8)) {
      count = lines.count();
    }

    lineCountCache.put(absPath, new FileMeta(count, currentModifiedTime));
    return count;
  }

  /**
   * Determina si el archivo es seguro de leer como texto.
   */
  private boolean isBinaryResource(Path path) {
    try {
      String mimeType = tika.detect(path);

      // Lista blanca de tipos seguros
      if (mimeType.startsWith("text/")) {
        return false;
      }
      // Tipos de código/datos comunes que Tika a veces marca como application/
      if (mimeType.contains("json")
              || mimeType.contains("xml")
              || mimeType.contains("javascript")
              || mimeType.contains("properties")
              || mimeType.contains("yaml")
              || mimeType.equals("application/x-sh")
              || mimeType.equals("application/x-bat")) {
        return false;
      }

      // Todo lo demás (PDF, binarios, imágenes) se considera no legible por esta tool
      return true;

    } catch (IOException e) {
      // Si no podemos leerlo para detectar el tipo, asumimos que no podemos leer el contenido
      return true;
    }
  }

  private static class ReadArgs {

    String path;
    int offset;
    int limit;
  }
}
