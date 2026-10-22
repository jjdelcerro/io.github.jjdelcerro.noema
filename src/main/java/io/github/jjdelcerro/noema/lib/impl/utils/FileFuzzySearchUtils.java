package io.github.jjdelcerro.noema.lib.impl.utils;

import io.github.jjdelcerro.noema.lib.api.Agent;
import io.github.jjdelcerro.noema.lib.api.AgentAccessControl;
import io.github.jjdelcerro.noema.lib.api.AgentPaths;
import io.github.jjdelcerro.noema.lib.impl.services.embeddings.EmbeddingFilter;
import io.github.jjdelcerro.noema.lib.impl.services.embeddings.EmbeddingsService;
import org.apache.commons.lang3.StringUtils;
import org.apache.tika.detect.AutoDetectReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.commons.lang3.mutable.MutableDouble;

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
  public static final int CHUNK_LINES = 35;
  public static final int OVERLAP_LINES = 10;
  public static final int STEP_LINES = CHUNK_LINES - OVERLAP_LINES; // 25 líneas de avance

  private static final int MAX_FILES_TO_SCAN = 100;

  private FileFuzzySearchUtils() {
    // Utility class
  }

  /**
   * Transforma el flujo de lectura de un fichero antes de trocearlo.
   *
   * Permite que el llamador sustituya el texto crudo del fichero por otro
   * derivado de el, por ejemplo el titulo mas el contenido de una nota
   * obtenidos al deserializar su XML. Si el transformador devuelve null, el
   * fichero se descarta.
   */
  @FunctionalInterface
  public interface ReaderTransformer {
    Reader apply(Reader reader) throws IOException;
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
          MutableDouble similarity,
          String content
          ) {

    @Override
    public String toString() {
      return String.format("file=%s;startline=%d;endline=%d;similarity=%f;content=\n%s\n----------\n", file, startLine, endLine, similarity.doubleValue(), content);
    }
  }

  public static List<FuzzyMatch> search(Agent agent, Path target, String query) {
    return search(agent, target, query, "**", DEFAULT_LIMIT, DEFAULT_MIN_SIMILARITY, null);
  }

  public static List<FuzzyMatch> search(Agent agent, Path target, String query, String filePattern) {
    return search(agent, target, query, filePattern, DEFAULT_LIMIT, DEFAULT_MIN_SIMILARITY, null);
  }

  public static List<FuzzyMatch> search(
          Agent agent,
          Path target,
          String query,
          String filePattern,
          int limit,
          double minSimilarity
  ) {
    return search(agent, target, query, filePattern, limit, minSimilarity, null);
  }
  
  /**
   * Ejecuta la búsqueda semántica sobre un archivo o directorio.
   *
   * <p>
   * El recorrido, el filtrado por glob y la comprobación de política por
   * entrada los resuelve {@link FsUtils#walk}. Por eso {@code target} no
   * necesita venir validado: un ámbito más amplio que las zonas visibles para
   * el agente se intersecta con ellas.
   *
   * @param agent Instancia del agente contenedor.
   * @param target Ámbito de búsqueda (archivo o directorio).
   * @param query Texto o concepto a buscar por similitud semántica.
   * @param filePattern Patrón glob aplicado al path relativo a cada raíz (ej:
   * "**\/*.java").
   * @param limit Número máximo de coincidencias a retornar.
   * @param minSimilarity Umbral mínimo de similitud coseno (0.0 a 1.0).
   * @param readerTransformer Transformador opcional del flujo de lectura. Si
   * es null se usa el texto crudo del fichero. Cuando se aplica, las líneas
   * de {@link FuzzyMatch} se refieren al texto transformado, no al fichero.
   * @return Lista clasificada de fragmentos ordenados de mayor a menor
   * relevancia.
   */
  public static List<FuzzyMatch> search(
          Agent agent,
          Path target,
          String query,
          String filePattern,
          int limit,
          double minSimilarity,
          ReaderTransformer readerTransformer
  ) {
    AgentAccessControl accessControl = agent.getAccessControl();
    AgentPaths paths = agent.getPaths();
    EmbeddingsService embeddingsService = (EmbeddingsService) agent.getService(EmbeddingsService.NAME);
    return search(embeddingsService, accessControl, paths, target, query, filePattern, limit, minSimilarity, readerTransformer);
  }
  
  public static List<FuzzyMatch> search(
          EmbeddingsService embeddingsService,
          AgentAccessControl accessControl,
          AgentPaths paths,
          Path target,
          String query,
          String filePattern,
          int limit,
          double minSimilarity,
          ReaderTransformer readerTransformer
  ) {
  
    if (embeddingsService == null) {
      LOGGER.warn("EmbeddingsService no disponible para búsqueda semántica.");
      return Collections.emptyList();
    }
    if ( target == null || StringUtils.isBlank(query)) {
      return Collections.emptyList();
    }

    int safeLimit = (limit > 0) ? Math.min(limit, MAX_LIMIT) : DEFAULT_LIMIT;
    double safeSimilarity = (!Double.isNaN(minSimilarity) && minSimilarity >= 0.0)
            ? Math.min(1.0, minSimilarity)
            : DEFAULT_MIN_SIMILARITY;

    EmbeddingFilter<FuzzyMatch> filter = embeddingsService.createEmbeddingFilter(
            query.trim(), safeLimit, safeSimilarity);

    ReaderTransformer transformer = (readerTransformer != null) ? readerTransformer : (reader) -> reader;
    int filesScanned = 0;

    try (CloseableIterable<Path> files = FsUtils.walk(
            accessControl, target, 
            FsUtils.normalizeGlob(filePattern),
            AgentAccessControl.AccessMode.PATH_ACCESS_READ
        )) {

      for (Path file : files) {
        if (filesScanned >= MAX_FILES_TO_SCAN) {
          break;
        }
        // The walk yields folders too; only files are worth scanning.
        if (!Files.isRegularFile(file)) {
          continue;
        }
        scanSingleFile(file, FsUtils.toDisplayPath(paths, file), filter, embeddingsService, transformer);
        filesScanned++;
      }
    } catch (IOException e) {
      LOGGER.warn("Error durante el escaneo de búsqueda semántica en: {}", target, e);
    }

    return filter.get();
  }

  private static void scanSingleFile(
          Path file,
          String displayPath,
          EmbeddingFilter<FuzzyMatch> filter,
          EmbeddingsService embeddingsService,
          ReaderTransformer readerTransformer
  ) {
    if (FsUtils.isBinaryResource(file)) {
      return;
    }

    try (InputStream in = new BufferedInputStream(new FileInputStream(file.toFile()));
            Reader detected = new AutoDetectReader(in)) {

      // El transformador trabaja sobre el reader crudo, sin buffer: lo que se
      // consume por lineas es su salida, asi que el buffer se pone despues.
      Reader transformed = readerTransformer.apply(detected);
      if (transformed == null) {
        return;
      }

      try (BufferedReader reader = new BufferedReader(transformed)) {

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
//    System.err.println(startLine+":"+endLine);
    String chunkText = String.join("\n", window).trim();
    if (!chunkText.isBlank()) {
      float[] chunkVec = embeddingsService.embed(chunkText);
      if (chunkVec != null) {
        MutableDouble similarity = new MutableDouble(1);
        similarity.setValue(filter.add(chunkVec, new FuzzyMatch(displayPath, file, startLine, endLine, similarity, chunkText)));

      }
    }
  }
}
