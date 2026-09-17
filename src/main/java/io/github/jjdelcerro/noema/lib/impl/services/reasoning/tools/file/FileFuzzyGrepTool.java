package io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.file;

import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.AgentTool;
import io.github.jjdelcerro.noema.lib.impl.AbstractPaginatedAgentTool;
import io.github.jjdelcerro.noema.lib.impl.ToolSpecificationBuilder;
import io.github.jjdelcerro.noema.lib.impl.services.embeddings.EmbeddingFilter;
import org.apache.commons.lang3.StringUtils;
import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static io.github.jjdelcerro.noema.lib.AgentAccessControl.AccessMode.PATH_ACCESS_READ;
import io.github.jjdelcerro.noema.lib.impl.FileFuzzySearchUtils;

/**
 * Herramienta de búsqueda semántica en archivos mediante embeddings locales.
 * <p>
 * Recorre archivos en streaming mediante una ventana deslizante de líneas en
 * memoria acotada, vectoriza los fragmentos al vuelo y delega el ranking Top-K
 * en {@link EmbeddingFilter}.
 */
public class FileFuzzyGrepTool extends AbstractPaginatedAgentTool {

  private static final Logger LOGGER = LoggerFactory.getLogger(FileFuzzyGrepTool.class);
  public static final String TOOL_NAME = "file_fuzzygrep";

  private static final int DEFAULT_LIMIT = 5;
  private static final int MAX_LIMIT = 20;
  private static final double DEFAULT_MIN_SIMILARITY = 0.25;

  // Tamaño de la ventana deslizante para no saturar memoria RAM
  private static final int CHUNK_LINES = 35;
  private static final int OVERLAP_LINES = 10;
  private static final int STEP_LINES = CHUNK_LINES - OVERLAP_LINES; // 25 líneas de avance

  private static final int MAX_FILES_TO_SCAN = 100;
  private static final String[] SKIP_DIRS = {"target", ".git", ".idea", "node_modules"};

  private final Tika tika = new Tika();

  public record ChunkInfo(Path file, int startLine, int endLine, String content) {

  }

  public FileFuzzyGrepTool(Agent agent) {
    super(agent);
  }

  @Override
  public ToolSpecificationBuilder getSpecification() {
    return ToolSpecificationBuilder.create()
            .name(TOOL_NAME)
            .description("Busca en archivos por SIGNIFICADO o PROXIMIDAD SEMÁNTICA utilizando embeddings locales.\n"
                    + "Úsala cuando busques una funcionalidad, concepto, regla de negocio o descripción en lenguaje natural, "
                    + "pero DESCONOZCAS los identificadores o nombres exactos de variables/clases.\n"
                    + "Para buscar símbolos literales o expresiones regulares exactas, utiliza 'file_grep'.\n\n"
                    + getShortPaginationInstruction())
            .addStringParameter("path", false, "Ruta del archivo o directorio donde buscar.")
            .addStringParameter("query", false, "Concepto, pregunta o descripción en lenguaje natural a buscar por significado.")
            .addStringParameter("filePattern", true, "Patrón glob para filtrar archivos si se busca en un directorio (default: '**').")
            .addIntegerParameter("limit", true, "Número máximo de fragmentos relevantes a retornar (default: " + DEFAULT_LIMIT + ", max: " + MAX_LIMIT + ").")
            .addNumberParameter("minSimilarity", true, "Umbral mínimo de similitud coseno (default: " + DEFAULT_MIN_SIMILARITY + ", rango: 0.0 a 1.0).");
  }

  @Override
  public int getMode() {
    return AgentTool.MODE_READ;
  }

  @Override
  public String execute(String jsonArguments) {
    try {
      Args args = gson.fromJson(jsonArguments, Args.class);
      if (args == null || StringUtils.isBlank(args.path) || StringUtils.isBlank(args.query)) {
        return formatErrorResponse("Los parámetros 'path' y 'query' son obligatorios.");
      }

      Path target = resolvePathOrNull(args.path, PATH_ACCESS_READ);
      if (target == null) {
        return formatErrorResponse("Acceso denegado o ruta inexistente: " + args.path);
      }

      int limit = args.limit != null ? args.limit : FileFuzzySearchUtils.DEFAULT_LIMIT;
      double minSim = args.minSimilarity != null ? args.minSimilarity : FileFuzzySearchUtils.DEFAULT_MIN_SIMILARITY;

      // Delegación limpia en el motor común
      List<FileFuzzySearchUtils.FuzzyMatch> results = FileFuzzySearchUtils.search(
              agent, target, args.query, args.filePattern, limit, minSim);

      Path tempFile = Files.createTempFile(agent.getPaths().getTempFolder(), "fuzzygrep_", ".tmp");
      writeResults(tempFile, results, args.query.trim(), minSim);

      String resourceId = getIdFromPath(tempFile);
      return servePaginatedResource(resourceId);

    } catch (Exception e) {
      return formatErrorResponse("Error en búsqueda semántica: " + e.getMessage());
    }
  }

  private void writeResults(Path outputFile, List<FileFuzzySearchUtils.FuzzyMatch> results, String query, double minSimilarity) throws IOException {
    try (BufferedWriter writer = Files.newBufferedWriter(outputFile, StandardCharsets.UTF_8)) {
      if (results.isEmpty()) {
        writer.write("No se encontraron fragmentos con similitud >= " + minSimilarity + " para la consulta: '" + query + "'\n");
        return;
      }

      writer.write("CONSULTA SEMÁNTICA: '" + query + "' | COINCIDENCIAS RELEVANTES: " + results.size() + "\n\n");

      for (int i = 0; i < results.size(); i++) {
        FileFuzzySearchUtils.FuzzyMatch c = results.get(i);
        writer.write(String.format("=== COINCIDENCIA %d ===\n", i + 1));
        writer.write(String.format("ARCHIVO: %s (Líneas %d-%d)\n", c.path().toAbsolutePath(), c.startLine(), c.endLine()));
        writer.write("---\n");
        writer.write(c.content());
        writer.write("\n\n");
      }
    }
  }

  private static class Args {

    String path;
    String query;
    String filePattern;
    Integer limit;
    Double minSimilarity;
  }
}
