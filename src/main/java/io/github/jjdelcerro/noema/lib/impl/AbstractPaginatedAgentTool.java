package io.github.jjdelcerro.noema.lib.impl;

import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.AgentAccessControl;
import org.apache.commons.collections4.map.LRUMap;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.github.jjdelcerro.noema.lib.AgentAccessControl.AccessMode.PATH_ACCESS_READ;

public abstract class AbstractPaginatedAgentTool extends AbstractAgentTool {

  protected static final Logger LOGGER = LoggerFactory.getLogger(AbstractPaginatedAgentTool.class);
  
  private static final String PREFIX_TMP = "tmp://";
  private static final String PREFIX_CACHE = "cache://";
  private static final String PREFIX_USER = "user://";
  
  private static final int DEFAULT_MAX_LINES = 1000;
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

  protected String servePaginatedResource(String resourceId) {
    return servePaginatedResource(resourceId, 0, getDefaultMaxLines());
  }

  protected String servePaginatedResource(String resourceId, int offset, int limit) {
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

      try (Stream<String> lines = Files.lines(filePath, StandardCharsets.UTF_8)) {
        return executePaginatedRead(lines, resourceId, totalLines, offset, limit);
      }
    } catch (IOException e) {
      LOGGER.warn("Error reading paginated resource: " + resourceId, e);
      return formatErrorResponse("I/O error reading resource: " + e.getMessage());
    } catch (Exception e) {
      LOGGER.warn("Unexpected error reading paginated resource: " + resourceId, e);
      return formatErrorResponse("Unexpected error: " + e.getMessage());
    }
  }

  private String executePaginatedRead(Stream<String> lines, String resourceId, long totalLines, int theOffset, int theLimit) throws IOException {
    int offset = theOffset > 0 ? theOffset : 0;
    int limit = theLimit > 0 ? theLimit : getDefaultMaxLines();

    if (totalLines == 0) {
      return formatResponse(true, offset, limit, 0, null, null);
    }

    if (offset >= totalLines) {
      return formatErrorResponse("Offset (" + offset + ") out of range. Resource has " + totalLines + " lines.");
    }

    String content;
    int linesRead;
    var chunk = lines.skip(offset)
            .limit(limit)
            .collect(Collectors.toList());
    content = String.join("\n", chunk);
    linesRead = chunk.size();

    if (StringUtils.isBlank(content)) {
      return formatResponse(true, offset, limit, totalLines, null, null);
    }

    boolean isTruncated = (offset + linesRead) < totalLines;
    String hint = null;

    if (isTruncated) {
      int nextOffset = offset + linesRead;
      hint = buildPaginationHint(resourceId, nextOffset, limit);
    }

    return formatResponse(false, offset, limit, totalLines, hint, content);
  }

  private long getLineCountWithCache(Path filePath) throws IOException {
    String absPath = filePath.toAbsolutePath().toString();
    FileTime currentModifiedTime = Files.getLastModifiedTime(filePath);

    FileMeta cached = lineCountCache.get(absPath);

    if (cached != null && cached.lastModifiedTime.equals(currentModifiedTime)) {
      return cached.lineCount;
    }

    long count;
    try (Stream<String> lines = Files.lines(filePath, StandardCharsets.UTF_8)) {
      count = lines.count();
    }

    lineCountCache.put(absPath, new FileMeta(count, currentModifiedTime));
    return count;
  }

  private String formatResponse(boolean empty, int offset, int limit, long total, String hint, String content) {
    StringBuilder sb = new StringBuilder();

    sb.append("STATUS: OK\n");

    if (empty) {
      sb.append("EMPTY: true\n");
      sb.append("---\n");
      return sb.toString();
    }

    sb.append("EMPTY: false\n");

    if (offset > 0 || (offset + limit) < total) {
      long endLine = offset + (hint != null ? limit : total - offset) - 1;
      if (endLine >= offset) {
        sb.append("LINE_RANGE: ").append(offset).append("-").append(endLine).append("\n");
      }
      sb.append("TOTAL_LINES: ").append(total).append("\n");
    }

    if (hint != null) {
      sb.append(hint).append("\n");
    }

    sb.append("---\n");

    if (offset > 0 || (offset + limit) < total) {
      sb.append("\n");
    }

    sb.append(content);
    return sb.toString();
  }

  protected String formatErrorResponse(String error) {
    StringBuilder sb = new StringBuilder();
    sb.append("STATUS: ERROR\n");
    sb.append("ERROR: ").append(error).append("\n");
    sb.append("---\n");
    return sb.toString();
  }

  private String buildPaginationHint(String resourceId, int nextOffset, int limit) {
    return "HINT: To read the next block, call 'read_paginated_resource' with args: {\"resource_id\": \"" + resourceId + "\", \"offset\": " + nextOffset + ", \"limit\": " + limit + "}";
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

**Reglas críticas:**
  * El delimitador `---` separa siempre los metadatos del contenido. Nunca interpretes metadatos como datos del archivo.
  * Si recibes un `HINT`, es una instrucción ejecutable para obtener el siguiente bloque. No intentes paginar por tu cuenta.
  * Si el patrón no incluye `HINT`, el recurso ha finalizado. No intentes realizar más llamadas de lectura.
  * Si recibes un error indicando que el recurso temporal ha expirado, debes regenerar el recurso original.
""";
  }
}
