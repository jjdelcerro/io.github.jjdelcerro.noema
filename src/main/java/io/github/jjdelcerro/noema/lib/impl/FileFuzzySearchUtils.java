package io.github.jjdelcerro.noema.lib.impl;

import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.AgentAccessControl;
import io.github.jjdelcerro.noema.lib.impl.services.embeddings.EmbeddingFilter;
import io.github.jjdelcerro.noema.lib.impl.services.embeddings.EmbeddingsService;
import org.apache.commons.lang3.StringUtils;
import org.apache.tika.Tika;
import org.apache.tika.detect.AutoDetectReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/**
 * Utilidad centralizada para búsqueda semántica en archivos mediante embeddings
 * locales. Implementa escaneo en streaming mediante ventana deslizante
 * desacoplada de la interfaz (reutilizable por herramientas interactivas y
 * módulos de scripting).
 */
public final class FileFuzzySearchUtils {

  private static final Logger LOGGER = LoggerFactory.getLogger(FileFuzzySearchUtils.class);

  public static final int DEFAULT_LIMIT = 5;
  public static final int MAX_LIMIT = 20;
  public static final double DEFAULT_MIN_SIMILARITY = 0.25;

  // Parámetros de la ventana deslizante
  private static final int CHUNK_LINES = 35;
  private static final int OVERLAP_LINES = 10;
  private static final int STEP_LINES = CHUNK_LINES - OVERLAP_LINES; // 25 líneas de avance

  private static final int MAX_FILES_TO_SCAN = 100;
  private static final String[] SKIP_DIRS = {"target", ".git", ".idea", "node_modules"};

  private static final Tika TIKA = new Tika();

  private FileFuzzySearchUtils() {
    // Utility class
  }

  /**
   * Representa una coincidencia semántica en un fragmento de archivo.
   *
   * @param file Ruta relativa normalizada para visualización y scripts (ej:
   * "src/Main.java").
   * @param path Ruta física {@link Path} al archivo.
   * @param startLine Línea inicial del fragmento (1-based).
   * @param endLine Línea final del fragmento.
   * @param content Texto íntegro del fragmento.
   */
  public static record FuzzyMatch(
          String file,
          Path path,
          int startLine,
          int endLine,
          String content
          ) {

    @Override
    public String toString() {
      return String.format("%s:%d-%d:\n%s", file, startLine, endLine, content);
    }
  }

  public static List<FuzzyMatch> search(Agent agent, Path target, String query) {
    return search(agent, target, query, "**", DEFAULT_LIMIT, DEFAULT_MIN_SIMILARITY);
  }

  public static List<FuzzyMatch> search(Agent agent, Path target, String query, String filePattern) {
    return search(agent, target, query, filePattern, DEFAULT_LIMIT, DEFAULT_MIN_SIMILARITY);
  }

  /**
   * Ejecuta la búsqueda semántica sobre un archivo o directorio.
   *
   * @param agent Instancia del agente contenedor.
   * @param target Ruta ya validada en el sandbox.
   * @param query Texto o concepto a buscar por similitud semántica.
   * @param filePattern Patrón glob si target es un directorio (ej:
   * "**\/*.java").
   * @param limit Número máximo de coincidencias a retornar.
   * @param minSimilarity Umbral mínimo de similitud coseno (0.0 a 1.0).
   * @return Lista clasificada de fragmentos ordenados de mayor a menor
   * relevancia.
   */
  public static List<FuzzyMatch> search(
          Agent agent,
          Path target,
          String query,
          String filePattern,
          int limit,
          double minSimilarity
  ) {
    if (agent == null || target == null || StringUtils.isBlank(query) || !Files.exists(target)) {
      return Collections.emptyList();
    }

    EmbeddingsService embeddingsService = (EmbeddingsService) agent.getService(EmbeddingsService.NAME);
    if (embeddingsService == null) {
      LOGGER.warn("EmbeddingsService no disponible para búsqueda semántica.");
      return Collections.emptyList();
    }

    int safeLimit = (limit > 0) ? Math.min(limit, MAX_LIMIT) : DEFAULT_LIMIT;
    double safeSimilarity = (!Double.isNaN(minSimilarity) && minSimilarity >= 0.0)
            ? Math.min(1.0, minSimilarity)
            : DEFAULT_MIN_SIMILARITY;

    EmbeddingFilter<FuzzyMatch> filter = embeddingsService.createEmbeddingFilter(
            query.trim(), safeLimit, safeSimilarity);

    try {
      if (Files.isRegularFile(target)) {
        scanSingleFile(agent, target, toDisplayPath(agent, target), filter, embeddingsService);
      } else if (Files.isDirectory(target)) {
        scanDirectory(agent, target, normalizeGlob(filePattern), filter, embeddingsService);
      }
    } catch (IOException e) {
      LOGGER.warn("Error durante el escaneo de búsqueda semántica en: {}", target, e);
    }

    return filter.get();
  }

  private static void scanSingleFile(
          Agent agent,
          Path file,
          String displayPath,
          EmbeddingFilter<FuzzyMatch> filter,
          EmbeddingsService embeddingsService
  ) {
    if (isBinaryResource(file)) {
      return;
    }

    try (InputStream in = new BufferedInputStream(new FileInputStream(file.toFile())); BufferedReader reader = new BufferedReader(new AutoDetectReader(in))) {

      List<String> window = new ArrayList<>(CHUNK_LINES);
      int windowStartLine = 1;
      int currentLine = 0;
      boolean newLinesAdded = false;

      String line;
      while ((line = reader.readLine()) != null) {
        currentLine++;
        window.add(line);
        newLinesAdded = true;

        if (window.size() >= CHUNK_LINES) {
          processChunk(file, displayPath, window, windowStartLine, currentLine, filter, embeddingsService);
          int toRemove = Math.min(STEP_LINES, window.size());
          window.subList(0, toRemove).clear();
          windowStartLine += toRemove;
          newLinesAdded = false;
        }
      }

      if (newLinesAdded && !window.isEmpty()) {
        processChunk(file, displayPath, window, windowStartLine, currentLine, filter, embeddingsService);
      }

    } catch (Exception e) {
      LOGGER.debug("No se pudo escanear el archivo '{}' en búsqueda semántica: {}", file, e.getMessage());
    }
  }

  private static void processChunk(
          Path file,
          String displayPath,
          List<String> window,
          int startLine,
          int endLine,
          EmbeddingFilter<FuzzyMatch> filter,
          EmbeddingsService embeddingsService
  ) {
    String chunkText = String.join("\n", window).trim();
    if (!chunkText.isBlank()) {
      float[] chunkVec = embeddingsService.embed(chunkText);
      if (chunkVec != null) {
        filter.add(chunkVec, new FuzzyMatch(displayPath, file, startLine, endLine, chunkText));
      }
    }
  }

  private static void scanDirectory(
          Agent agent,
          Path rootDir,
          String normalizedPattern,
          EmbeddingFilter<FuzzyMatch> filter,
          EmbeddingsService embeddingsService
  ) throws IOException {
    PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + normalizedPattern);
    int filesScanned = 0;

    try (Stream<Path> walk = Files.walk(rootDir)) {
      var it = walk.iterator();
      while (it.hasNext() && filesScanned < MAX_FILES_TO_SCAN) {
        Path p = it.next();
        if (shouldSkipDirectory(p)) {
          continue;
        }

        // Validación estricta en el sandbox para cada fichero hijo encontrado
        Path safePath = agent.getAccessControl().resolvePathOrNull(
                p.toString(), AgentAccessControl.AccessMode.PATH_ACCESS_READ);
        if (safePath == null || !Files.isRegularFile(safePath)) {
          continue;
        }

        Path relative = rootDir.relativize(safePath);
        if (!matcher.matches(relative) && !matcher.matches(safePath.getFileName())) {
          continue;
        }

        scanSingleFile(agent, safePath, toDisplayPath(agent, safePath), filter, embeddingsService);
        filesScanned++;
      }
    }
  }

  public static String normalizeGlob(String filePattern) {
    if (StringUtils.isBlank(filePattern) || filePattern.equals("**/*") || filePattern.equals("*")) {
      return "**";
    }
    String trimmed = filePattern.trim();
    if (trimmed.startsWith("**/")) {
      return "{" + trimmed + "," + trimmed.substring(3) + "}";
    }
    return trimmed;
  }

  public static String toDisplayPath(Agent agent, Path file) {
    if (agent == null || agent.getPaths() == null || agent.getPaths().getWorkspaceFolder() == null) {
      return file.toString().replace("\\", "/");
    }
    Path root = agent.getPaths().getWorkspaceFolder();
    String displayPath = file.startsWith(root) ? root.relativize(file).toString() : file.toString();
    return displayPath.replace("\\", "/");
  }

  public static boolean shouldSkipDirectory(Path path) {
    if (!Files.isDirectory(path)) {
      return false;
    }
    String name = path.getFileName() != null ? path.getFileName().toString() : "";
    for (String skip : SKIP_DIRS) {
      if (name.equals(skip)) {
        return true;
      }
    }
    return false;
  }

  public static boolean isBinaryResource(Path path) {
    try {
      String mimeType = TIKA.detect(path);
      if (mimeType.startsWith("text/")) {
        return false;
      }
      return !(mimeType.contains("json")
              || mimeType.contains("xml")
              || mimeType.contains("javascript")
              || mimeType.contains("properties")
              || mimeType.contains("yaml")
              || mimeType.equals("application/x-sh"));
    } catch (IOException e) {
      return true;
    }
  }
}
