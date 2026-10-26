package io.github.jjdelcerro.noema.lib.impl.tools;

import dev.langchain4j.data.message.ToolExecutionResultMessage;
import io.github.jjdelcerro.noema.lib.api.Agent;
import org.apache.commons.collections4.map.LRUMap;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Collections;
import java.util.Map;

import static io.github.jjdelcerro.noema.lib.api.AgentAccessControl.AccessMode.PATH_ACCESS_READ;
import io.github.jjdelcerro.noema.lib.api.AgentTool;
import static io.github.jjdelcerro.noema.lib.api.AgentTool.TrimResultType.None;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.Reader;
import org.apache.tika.detect.AutoDetectReader;

public abstract class AbstractPaginatedAgentTool extends AbstractAgentTool {

  private static final String PREFIX_TMP = "tmp://";
  private static final String PREFIX_CACHE = "cache://";
  private static final String PREFIX_USER = "user://";

  private static final int DEFAULT_MAX_LINES = 1000;
  private static final int MAX_CHARS_SOFT = 30000;
  private static final int MAX_CHARS_HARD = 40000;
  private static final int CACHE_SIZE = 30;

  private final Map<String, FileMeta> lineCountCache;

  private record FileMeta(long lineCount, FileTime lastModifiedTime) {

  }

  protected AbstractPaginatedAgentTool(Agent agent) {
    super(agent);
    this.lineCountCache = Collections.synchronizedMap(new LRUMap<>(this.getCacheSize()));
  }

  protected int getDefaultMaxLines() {
    return DEFAULT_MAX_LINES;
  }

  private int getCacheSize() {
    return CACHE_SIZE;
  }

  protected String getIdFromPath(Path path) {
    if (path == null || !path.isAbsolute()) {
      return null;
    }

    Path cacheFolder = agent.getPaths().getCacheFolder().toAbsolutePath().normalize();
    Path tempFolder = agent.getPaths().getTempFolder().toAbsolutePath().normalize();
    Path normalizedPath = path.toAbsolutePath().normalize();

    if (normalizedPath.startsWith(cacheFolder)) {
      Path relativePath = cacheFolder.relativize(normalizedPath);
      if (!relativePath.normalize().startsWith("..")) {
        return PREFIX_CACHE + relativePath.toString().replace("\\", "/");
      }
    } else if (normalizedPath.startsWith(tempFolder)) {
      Path relativePath = tempFolder.relativize(normalizedPath);
      if (!relativePath.normalize().startsWith("..")) {
        return PREFIX_TMP + relativePath.toString().replace("\\", "/");
      }
    }

    return PREFIX_USER + normalizedPath.toString().replace("\\", "/");
  }

  protected Path getPathFromId(String id) {
    if (StringUtils.isBlank(id)) {
      return null;
    }

    if (id.startsWith(PREFIX_TMP)) {
      String relativePath = id.substring(PREFIX_TMP.length());
      Path tempFolder = agent.getPaths().getTempFolder().toAbsolutePath().normalize();
      Path resolved = tempFolder.resolve(relativePath).normalize();
      Path normalizedTemp = tempFolder.normalize();

      if (!resolved.startsWith(normalizedTemp)) {
        LOGGER.warn("Path traversal detected in tmp resource: {}", id);
        return null;
      }
      return resolved;
    }

    if (id.startsWith(PREFIX_CACHE)) {
      String relativePath = id.substring(PREFIX_CACHE.length());
      Path cacheFolder = agent.getPaths().getCacheFolder().toAbsolutePath().normalize();
      Path resolved = cacheFolder.resolve(relativePath).normalize();
      Path normalizedCache = cacheFolder.normalize();

      if (!resolved.startsWith(normalizedCache)) {
        LOGGER.warn("Path traversal detected in cache resource: {}", id);
        return null;
      }
      return resolved;
    }

    if (id.startsWith(PREFIX_USER)) {
      String absolutePath = id.substring(PREFIX_USER.length());
      try {
        Path resolvedPath = Path.of(absolutePath);
        Path validatedPath = agent.getAccessControl().resolvePathOrNull(resolvedPath.toString(), PATH_ACCESS_READ);
        if (validatedPath != null) {
          return validatedPath.toAbsolutePath().normalize();
        }
        return null;
      } catch (Exception e) {
        LOGGER.warn("Invalid user path format: {}", id);
        return null;
      }
    }

    LOGGER.warn("Unknown resource ID prefix: {}", id);
    return null;
  }

  protected ToolResult servePaginatedResource(String resourceId) {
    return servePaginatedResource(resourceId, 0, 0, getDefaultMaxLines());
  }

  protected ToolResult servePaginatedResource(String resourceId, int offset, int limit) {
    return servePaginatedResource(resourceId, offset, 0, limit);
  }

  protected ToolResult servePaginatedResource(String resourceId, int offset, int column, int limit) {
    if (StringUtils.isBlank(resourceId)) {
      return formatErrorResponse("Resource ID is null or empty");
    }

    Path filePath = getPathFromId(resourceId);
    if (filePath == null) {
      return formatErrorResponse("Invalid resource ID or resource not found: " + resourceId);
    }

    try {
      if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
        if (resourceId.startsWith(PREFIX_TMP) || resourceId.startsWith(PREFIX_CACHE)) {
          return formatErrorResponse("Resource expired or deleted. The temporary/cached resource '" + resourceId + "' no longer exists. You need to regenerate the original resource (e.g., re-execute the shell command, re-download the web content, or re-extract the document).");
        } else {
          return formatErrorResponse("File not found or is not accessible: " + filePath);
        }
      }

      if (!Files.isReadable(filePath)) {
        return formatErrorResponse("File is not readable: " + filePath);
      }

      long totalLines = getLineCountWithCache(filePath);

      // Lectura con autodetección de charset vía Tika
      try (InputStream in = new BufferedInputStream(new FileInputStream(filePath.toFile())); BufferedReader reader = new BufferedReader(new AutoDetectReader(in))) {
        return executePaginatedRead(reader, resourceId, totalLines, offset, column, limit);
      }
    } catch (IOException e) {
      LOGGER.warn("Error reading paginated resource: " + resourceId, e);
      return formatErrorResponse("I/O error reading resource: " + e.getMessage());
    } catch (Exception e) {
      LOGGER.warn("Unexpected error reading paginated resource: " + resourceId, e);
      return formatErrorResponse("Unexpected error: " + e.getMessage());
    }
  }

  private record Block(String content, int endLine, int endColumn, boolean hasMore) {

  }

  /**
   * Character cursor that normalises every line terminator variant to '\n'.
   * Both the seeking phase and the capture phase consume through it, so the
   * definition of a line lives in a single place.
   */
  private static final class CharCursor {

    private static final int BUFFER_SIZE = 8192;

    private final Reader reader;
    private final char[] buffer;
    private int bufferIndex;
    private int bufferLength;
    private int rawPushback;
    private boolean pendingLineFeed;
    private boolean exhausted;

    private CharCursor(Reader reader) {
      this.reader = reader;
      this.buffer = new char[BUFFER_SIZE];
      this.bufferIndex = 0;
      this.bufferLength = 0;
      this.rawPushback = -1;
      this.pendingLineFeed = false;
      this.exhausted = false;
    }

    private int readRaw() throws IOException {
      if (this.rawPushback != -1) {
        int saved = this.rawPushback;
        this.rawPushback = -1;
        return saved;
      }
      if (this.bufferIndex >= this.bufferLength) {
        if (this.exhausted) {
          return -1;
        }
        int read = this.reader.read(this.buffer, 0, this.buffer.length);
        if (read <= 0) {
          this.bufferIndex = 0;
          this.bufferLength = 0;
          this.exhausted = true;
          return -1;
        }
        this.bufferIndex = 0;
        this.bufferLength = read;
      }
      char c = this.buffer[this.bufferIndex];
      this.bufferIndex++;
      return c;
    }

    /**
     * Returns the next character, with '\r\n' and a lone '\r' reported as a
     * single '\n'. Returns -1 at the end of the stream.
     */
    private int next() throws IOException {
      int c = this.readRaw();
      if (this.pendingLineFeed) {
        this.pendingLineFeed = false;
        if (c == '\n') {
          c = this.readRaw();
        }
      }
      if (c == -1) {
        return -1;
      }
      if (c == '\r') {
        this.pendingLineFeed = true;
        return '\n';
      }
      return c;
    }

    /**
     * Reports whether more content is available, keeping the character for the
     * next call to next().
     */
    private boolean hasMore() throws IOException {
      if (this.rawPushback != -1) {
        return true;
      }
      if (this.bufferIndex < this.bufferLength) {
        return true;
      }
      if (this.exhausted) {
        return false;
      }
      int read = this.reader.read();
      if (read == -1) {
        this.exhausted = true;
        return false;
      }
      this.rawPushback = read;
      return true;
    }
  }

  /**
   * Advances the cursor until it sits at the given line and column. Both
   * coordinates are zero based.
   */
  private void seek(CharCursor cursor, int offset, int column) throws IOException {
    int currentLine = 0;
    while (currentLine < offset) {
      int c = cursor.next();
      if (c == -1) {
        throw new IllegalArgumentException("Offset (" + offset + ") out of range.");
      }
      if (c == '\n') {
        currentLine++;
      }
    }
    int skipped = 0;
    while (skipped < column) {
      int c = cursor.next();
      if (c == -1 || c == '\n') {
        throw new IllegalArgumentException(
            "Column (" + column + ") out of range for line " + offset + ".");
      }
      skipped++;
    }
  }

  /**
   * Counts lines the same way BufferedReader.readLine does: every terminator
   * closes a line, and a trailing run without terminator counts as one more.
   */
  private long countLines(CharCursor cursor) throws IOException {
    long terminators = 0;
    boolean pending = false;
    int c = cursor.next();
    while (c != -1) {
      if (c == '\n') {
        terminators++;
        pending = false;
      } else {
        pending = true;
      }
      c = cursor.next();
    }
    return pending ? terminators + 1 : terminators;
  }

  /**
   * Consumes characters until the soft budget, the line limit or the hard
   * budget is reached. The soft budget is only evaluated on a line boundary;
   * the hard budget is evaluated on any character.
   */
  private Block readBlock(CharCursor cursor, int startLine, int startColumn, int limit) throws IOException {
    StringBuilder buffer = new StringBuilder();
    int line = startLine;
    int column = startColumn;
    int endLine = startLine;
    int endColumn = startColumn;
    int linesInBlock = 0;

    while (true) {
      int c = cursor.next();

      if (c == -1) {
        return new Block(buffer.toString(), endLine, endColumn, false);
      }

      buffer.append((char) c);

      if (c == '\n') {
        endLine = line;
        endColumn = 0;
        line++;
        column = 0;
        linesInBlock++;

        if (buffer.length() >= MAX_CHARS_SOFT || linesInBlock >= limit) {
          return new Block(buffer.toString(), endLine, endColumn, cursor.hasMore());
        }
      } else {
        column++;
        endLine = line;
        endColumn = column;

        if (buffer.length() >= MAX_CHARS_HARD) {
          return new Block(buffer.toString(), endLine, endColumn, true);
        }
      }
    }
  }

  private ToolResult executePaginatedRead(BufferedReader reader, String resourceId, long totalLines, int theOffset, int theColumn, int theLimit) throws IOException {
    int offset = theOffset > 0 ? theOffset : 0;
    int column = theColumn > 0 ? theColumn : 0;
    int limit = theLimit > 0 ? theLimit : getDefaultMaxLines();

    if (totalLines == 0) {
      return formatResponse(resourceId, true, 0, 0, 0, 0, 0, null, null);
    }

    if (offset >= totalLines) {
      return formatErrorResponse("Offset (" + offset + ") out of range. Resource has " + totalLines + " lines.");
    }

    CharCursor cursor = new CharCursor(reader);

    try {
      seek(cursor, offset, column);
    } catch (IllegalArgumentException e) {
      return formatErrorResponse(e.getMessage());
    }

    Block block = readBlock(cursor, offset, column, limit);

    if (StringUtils.isBlank(block.content())) {
      return formatResponse(resourceId, true, offset, column, offset, column, totalLines, null, null);
    }

    String hint = null;

    if (block.hasMore()) {
      if (block.endColumn() == 0) {
        hint = buildPaginationHint(resourceId, block.endLine() + 1, 0, limit);
      } else {
        hint = buildPaginationHint(resourceId, block.endLine(), block.endColumn(), limit);
      }
    }

    return formatResponse(resourceId, false, offset, column, block.endLine(), block.endColumn(), totalLines, hint, block.content());
  }

  private long getLineCountWithCache(Path filePath) throws IOException {
    String absPath = filePath.toAbsolutePath().toString();
    FileTime currentModifiedTime = Files.getLastModifiedTime(filePath);

    FileMeta cached = lineCountCache.get(absPath);

    if (cached != null && cached.lastModifiedTime.equals(currentModifiedTime)) {
      return cached.lineCount;
    }

    long count;
    try (InputStream in = new BufferedInputStream(new FileInputStream(filePath.toFile())); BufferedReader reader = new BufferedReader(new AutoDetectReader(in))) {
      count = countLines(new CharCursor(reader));
    } catch (Exception e) {
      LOGGER.warn("Error counting lines for resource: " + absPath, e);
      throw new IOException("Unable to count lines for resource: " + absPath, e);
    }

    lineCountCache.put(absPath, new FileMeta(count, currentModifiedTime));
    return count;
  }

  private ToolResult formatResponse(String resourceId, boolean empty, int startLine, int startColumn, int endLine, int endColumn, long total, String hint, String content) {
    StringBuilder sb = new StringBuilder();

    sb.append("STATUS: OK\n");
    sb.append("RESOURCE_ID: " + resourceId + "\n");

    if (empty) {
      sb.append("EMPTY: true\n");
      sb.append("---\n");
      return result(sb.toString());
    }

    sb.append("EMPTY: false\n");

    boolean paginated = startLine > 0 || startColumn > 0 || hint != null;

    if (paginated) {
      sb.append(formatLineRange(startLine, startColumn, endLine, endColumn)).append("\n");
      sb.append("TOTAL_LINES: ").append(total).append("\n");
    }

    if (hint != null) {
      sb.append(hint).append("\n");
    }

    sb.append("---\n");

    if (paginated) {
      sb.append("\n");
    }

    sb.append(content);
    return result(sb.toString());
  }

  private String formatLineRange(int startLine, int startColumn, int endLine, int endColumn) {
    if (startColumn == 0 && endColumn == 0) {
      return "LINE_RANGE: " + startLine + "-" + endLine;
    }
    return "LINE_RANGE: L" + startLine + "C" + startColumn + "-L" + endLine + "C" + endColumn;
  }

  protected ToolResult formatErrorResponse(String error) {
    StringBuilder sb = new StringBuilder();
    sb.append("STATUS: ERROR\n");
    sb.append("ERROR: ").append(error).append("\n");
    sb.append("---\n");
    return result(sb.toString());
  }

  private String buildPaginationHint(String resourceId, int nextLine, int nextColumn, int limit) {
    StringBuilder sb = new StringBuilder();
    sb.append("HINT: To read the next block, call 'read_paginated_resource' with args: {");
    sb.append("\"resource_id\": \"").append(resourceId).append("\", ");
    sb.append("\"offset\": ").append(nextLine);

    if (nextColumn > 0) {
      sb.append(", \"column\": ").append(nextColumn);
    }

    sb.append(", \"limit\": ").append(limit).append("}");
    return sb.toString();
  }

  protected String getShortPaginationInstruction() {
    return """
**FORMATO DE SALIDA:**
    El resultado siempre consta de dos partes separadas estrictamente por el delimitador `---`:
    1. Cabecera, arriba de `---`): Metadatos del sistema. Si el contenido es demasiado largo, se truncará y aparecerá aquí un campo `HINT` con la llamada exacta a la herramienta para continuar leyendo.
    2. Contenido, debajo de `---`): El texto real del archivo o comando.
""";
  }

  protected String getPaginationSystemInstruction() {
    return """
**PROTOCOLO DE RESPUESTA Y NAVEGACIÓN:**

Esta herramienta utiliza un protocolo de respuesta estricto basado en cuatro formatos posibles. Identifica siempre el formato al inicio de la respuesta para saber cómo proceder:

1. **ERROR:**

STATUS: error
ERROR: [description]
---

2. **PAGINACIÓN EN CURSO:**

STATUS: ok
EMPTY: false
LINE_RANGE: [start-end]
TOTAL_LINES: [total]
HINT: [call_to_read_paginated_resource]
---
[Partial content]

3. **FINAL DE RECURSO PAGINADO:**

STATUS: ok
EMPTY: false
LINE_RANGE: [start-end]
TOTAL_LINES: [total]
---
[Final content]

4. **LECTURA BÁSICA (Sin paginación):**

STATUS: ok
EMPTY: [true|false]
---
[Complete content]

**FORMATO DE `LINE_RANGE`:**

Adopta dos formas según los extremos del bloque:

  * Bloque que empieza y termina en línea completa: `LINE_RANGE: 5-12`
  * Bloque con algún extremo parcial: `LINE_RANGE: L5C400-L12C100`

`L` es el número de línea (base cero). `C` es la columna dentro de esa línea (base cero, contada desde el inicio de la línea, no desde el inicio del bloque). Un bloque que empieza en la columna cero se escribe `C0`.

**Reglas críticas:**
  * El delimitador `---` separa siempre los metadatos del contenido. Nunca interpretes metadatos como datos del archivo.
  * Si recibes un `HINT`, es una instrucción ejecutable para obtener el siguiente bloque. No intentes paginar por tu cuenta.
  * Si el patrón no incluye `HINT`, el recurso ha finalizado. No intentes realizar más llamadas de lectura.
  * Si el `HINT` incluye el parámetro `column`, estás reanudando una línea que quedó partida a la mitad: pasa ese valor tal cual, sin modificarlo.
  * Si recibes un error indicando que el recurso temporal ha expirado, debes regenerar el recurso original.
  * Si ves el campo `CONTENT_TRIMMED: true` en la cabecera, significa que el sistema ha borrado el texto del 
    archivo de tu memoria a corto plazo para ahorrar contexto. Asume que ya leíste y procesaste este bloque 
    correctamente en un turno anterior, y si crees que lo necesitas vuelvelo a pedir.
""";
  }

//    private static final String CONTENT_TRIMMED_IN_THE_FOLLOWING_TURNS = "\nCONTENT_TRIMMED: IN THE FOLLOWING TURNS";
  private static final String CONTENT_TRIMMED = "\nCONTENT_TRIMMED: true";

  @Override
  public ToolResult trimResult(ToolResult toolResult, AgentTool.TrimResultType trimResultType) {
    if (toolResult == null ) {
      return null;
    }
    if( toolResult.getType() != ToolResultType.Text ) {
      return toolResult;
    }
    
    if( StringUtils.isBlank(toolResult.text()) ) {
      return toolResult;
    }
    String result = toolResult.text();

    String separator = "\n---\n";
    int separatorIndex = result.indexOf(separator);

    if (separatorIndex == -1) {
      return super.trimResult(toolResult, trimResultType);
    }
    String header = result.substring(0, separatorIndex);
    String body = result.substring(separatorIndex);
    switch (trimResultType) {
      case Trim:
//        LOGGER.info("TRIM RESULTS OF "+this.getClass().getSimpleName());
        return result(header + CONTENT_TRIMMED + separator);
      case None:
      default:
        return null;
    }
  }

  public String getResourceIdFromResultMessage(ToolExecutionResultMessage message) {
    String text = message.text();
    if (text == null) {
      return null;
    }
    // La respuesta tiene formato "STATUS: ...\n...\n---\n...", buscar línea que empiece con "RESOURCE_ID:"
    int separatorIndex = text.indexOf("\n---\n");
    if (separatorIndex == -1) {
      return null;
    }
    String header = text.substring(0, separatorIndex);
    for (String line : header.split("\n")) {
      if (line.startsWith("RESOURCE_ID:")) {
        return line.substring("RESOURCE_ID:".length()).trim();
      }
    }
    return null;
  }
}
