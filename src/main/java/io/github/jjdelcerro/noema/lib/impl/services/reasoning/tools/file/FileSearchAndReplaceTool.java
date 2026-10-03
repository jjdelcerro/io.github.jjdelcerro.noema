package io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.file;

import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.AgentTool;
import io.github.jjdelcerro.noema.lib.impl.tools.AbstractAgentTool;
import io.github.jjdelcerro.noema.lib.impl.tools.ToolSpecificationBuilder;
import io.github.jjdelcerro.javarcs.lib.RCSCommand;
import io.github.jjdelcerro.javarcs.lib.RCSLocator;
import io.github.jjdelcerro.javarcs.lib.RCSManager;
import io.github.jjdelcerro.javarcs.lib.commands.CheckinOptions;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.github.jjdelcerro.noema.lib.AgentAccessControl.AccessMode.PATH_ACCESS_WRITE;

/**
 * Herramienta de edicion avanzada de archivos mediante busqueda y sustitucion
 * de bloques. Implementa una estrategia de reemplazo en cascada con tolerancia
 * a espacios e indentacion, diagnostico difuso (did-you-mean) ante fallos y
 * vista previa numerada tras el exito.
 */
public class FileSearchAndReplaceTool extends AbstractAgentTool {

  public static final String TOOL_NAME = "file_search_and_replace";
  private static final int CONTEXT_PREVIEW_LINES = 3;
  private static final double MIN_FUZZY_CONFIDENCE = 0.35;

  public FileSearchAndReplaceTool(Agent agent) {
    super(agent);
  }

  @Override
  public boolean isAvailableByDefault() {
    return false;
  }

  @Override
  public ToolSpecificationBuilder getSpecification() {
    return ToolSpecificationBuilder.create()
            .name(TOOL_NAME)
            .description("""
Edita un archivo reemplazando un bloque de texto existente ('oldText') por uno nuevo ('newText').

CARACTERISTICAS Y REGLAS DE USO:
1. Exactitud y contexto: Incluye siempre 2 o 3 lineas de contexto circundante en 'oldText' para garantizar que el bloque sea unico dentro del archivo.
2. Tolerancia automatica: Si la sangria o los espacios no coinciden de forma exacta, el motor adaptara automaticamente el nuevo texto a la indentacion original del archivo.
3. Para eliminar codigo: Especifica el bloque a borrar en 'oldText' y envia 'newText' como cadena vacia.
4. Auto-recuperacion: Si el bloque no se localiza, la herramienta te devolvera las lineas reales del archivo mas parecidas para que puedas corregir tu peticion.
5. Seguridad: Todo cambio realiza una copia de seguridad automatica en el historial de JavaRCS antes de modificar el disco.
""")
            .addStringParameter("path", false, "Ruta relativa del archivo a editar.")
            .addStringParameter("oldText", false, "Bloque de texto exacto a sustituir (debe ser unico en el archivo).")
            .addStringParameter("newText", false, "Nuevo texto que reemplazara a 'oldText'. Puede ser vacio para borrar.");
  }

  @Override
  public int getMode() {
    return AgentTool.MODE_WRITE;
  }

  @Override
  public String execute(String jsonArguments) {
    try {
      Args args = gson.fromJson(jsonArguments, Args.class);

      if (args == null || args.path == null || args.path.isBlank()) {
        return error("El parametro 'path' es obligatorio.");
      }
      if (args.oldText == null || args.oldText.isEmpty()) {
        return error("El parametro 'oldText' es obligatorio y no puede estar vacio.");
      }

      String newText = args.newText != null ? args.newText : "";

      // 1. Validar sandbox de escritura
      Path filePath = this.agent.getAccessControl().resolvePathOrNull(args.path.trim(), PATH_ACCESS_WRITE);
      if (filePath == null) {
        return error("Acceso denegado o ruta fuera del sandbox: " + args.path);
      }

      if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
        return error("El archivo no existe o no es regular: " + args.path);
      }

      // 2. Lectura y deteccion de saltos de linea originales
      String rawContent = Files.readString(filePath, StandardCharsets.UTF_8);
      boolean usesCrlf = rawContent.contains("\r\n");

      String fileContent = rawContent.replace("\r\n", "\n");
      String targetOld = args.oldText.replace("\r\n", "\n");
      String targetNew = newText.replace("\r\n", "\n");

      // 3. ESTRATEGIA EN CASCADA
      ReplacementResult result = null;

      // Nivel 1: Coincidencia exacta
      result = tryExactMatch(fileContent, targetOld, targetNew);

      // Nivel 2: Coincidencia tolerante a indentacion y espacios
      if (result == null) {
        result = tryIndentationTolerantMatch(fileContent, targetOld, targetNew);
      }

      // Nivel 3: Si ambos fallan, buscar candidato aproximado para retroalimentar al LLM
      if (result == null) {
        String fuzzySuggestion = findBestFuzzyCandidate(fileContent, targetOld);
        StringBuilder errorMsg = new StringBuilder();
        errorMsg.append("No se encontro el bloque 'oldText' en '").append(args.path).append("'.\n");

        if (fuzzySuggestion != null) {
          errorMsg.append("El bloque mas parecido encontrado en el archivo es:\n")
                  .append("--------------------------------------------------\n")
                  .append(fuzzySuggestion).append("\n")
                  .append("--------------------------------------------------\n")
                  .append("Asegurate de copiar las lineas reales del archivo respetando su estructura.");
        } else {
          errorMsg.append("Verifica el contenido del archivo con 'file_read' antes de volver a intentar el reemplazo.");
        }
        return error(errorMsg.toString());
      }

      if (result.hasAmbiguityError()) {
        return error(result.errorMessage());
      }

      // 4. Copia de seguridad en JavaRCS previa a la mutacion
      if (Files.exists(filePath) && agent.getAccessControl().isEnabledRCSBackup()) {
        try {
          RCSManager rcsManager = RCSLocator.getRCSManager();
          CheckinOptions options = rcsManager.createCheckinOptions(filePath);
          String author = getReasoningService() != null ? getReasoningService().getModelName() : "NoemaAgent";
          options.setAuthor(author != null ? author : "NoemaAgent");
          options.setInit(true);
          RCSCommand ci = rcsManager.create(options);
          ci.execute(options);
        } catch (Exception ex) {
          LOGGER.warn("No se pudo registrar copia previa en JavaRCS para: " + filePath, ex);
        }
      }

      // 5. Escritura del contenido modificado respetando el formato de linea original
      String finalContent = usesCrlf ? result.updatedContent().replace("\n", "\r\n") : result.updatedContent();
      Files.writeString(filePath, finalContent, StandardCharsets.UTF_8);

      // 6. Generar vista previa con lineas numeradas
      String snippetPreview = generateDiffSnippet(result.updatedContent(), result.startLine(), result.replacedLineCount());

      Map<String, Object> response = new LinkedHashMap<>();
      response.put("status", "success");
      response.put("path", args.path);
      response.put("strategy", result.strategyApplied());
      response.put("message", "Archivo editado correctamente.");
      response.put("preview", snippetPreview);

      return gson.toJson(response);

    } catch (Exception e) {
      LOGGER.warn("Error ejecutando file_search_and_replace: " + jsonArguments, e);
      return error("Error interno al procesar edicion: " + e.getMessage());
    }
  }

  // =========================================================================
  // NIVEL 1: COINCIDENCIA EXACTA
  // =========================================================================
  private ReplacementResult tryExactMatch(String fileContent, String oldText, String newText) {
    int firstIndex = fileContent.indexOf(oldText);
    if (firstIndex == -1) {
      return null;
    }

    // Verificar unicidad
    int secondIndex = fileContent.indexOf(oldText, firstIndex + 1);
    if (secondIndex != -1) {
      int count = countOccurrences(fileContent, oldText);
      return ReplacementResult.ambiguity(String.format(
              "El bloque 'oldText' aparece %d veces en el archivo. Agrega mas lineas de contexto para hacerlo unico.", count));
    }

    int startLine = countLines(fileContent.substring(0, firstIndex)) + 1;
    int replacedLines = countLines(newText);

    String updated = fileContent.substring(0, firstIndex) + newText + fileContent.substring(firstIndex + oldText.length());
    return new ReplacementResult(updated, startLine, replacedLines, "exact_match", null);
  }

  // =========================================================================
  // NIVEL 2: COINCIDENCIA TOLERANTE A INDENTACION Y ESPACIOS
  // =========================================================================
  private ReplacementResult tryIndentationTolerantMatch(String fileContent, String oldText, String newText) {
    List<String> fileLines = Arrays.asList(fileContent.split("\n", -1));
    List<String> oldLines = Arrays.asList(oldText.split("\n", -1));
    List<String> newLines = Arrays.asList(newText.split("\n", -1));

    if (oldLines.isEmpty() || oldLines.size() > fileLines.size()) {
      return null;
    }

    List<Integer> matchIndexes = new ArrayList<>();
    int windowSize = oldLines.size();

    for (int i = 0; i <= fileLines.size() - windowSize; i++) {
      boolean matches = true;
      for (int j = 0; j < windowSize; j++) {
        String fileLineTrim = fileLines.get(i + j).trim();
        String oldLineTrim = oldLines.get(j).trim();

        if (!fileLineTrim.equals(oldLineTrim)) {
          matches = false;
          break;
        }
      }
      if (matches) {
        matchIndexes.add(i);
      }
    }

    if (matchIndexes.isEmpty()) {
      return null;
    }

    if (matchIndexes.size() > 1) {
      return ReplacementResult.ambiguity(String.format(
              "El bloque de codigo coincide con %d secciones diferentes del archivo. Incluye mas lineas de contexto alrededor.",
              matchIndexes.size()));
    }

    int matchStart = matchIndexes.get(0);

    // Calcular indentacion relativa entre el bloque original del fichero y newText
    String baseIndentFile = getLeadingWhitespace(fileLines.get(matchStart));
    String baseIndentOld = getLeadingWhitespace(oldLines.get(0));

    List<String> reindentedNewLines = new ArrayList<>();
    for (String newLine : newLines) {
      if (newLine.trim().isEmpty()) {
        reindentedNewLines.add("");
        continue;
      }

      String lineIndent = getLeadingWhitespace(newLine);
      String content = newLine.substring(lineIndent.length());

      if (lineIndent.startsWith(baseIndentOld)) {
        String relativeIndent = lineIndent.substring(baseIndentOld.length());
        reindentedNewLines.add(baseIndentFile + relativeIndent + content);
      } else {
        reindentedNewLines.add(baseIndentFile + content);
      }
    }

    // Reconstruir lista de lineas con la seccion modificada
    List<String> finalLines = new ArrayList<>(fileLines.subList(0, matchStart));
    finalLines.addAll(reindentedNewLines);
    finalLines.addAll(fileLines.subList(matchStart + windowSize, fileLines.size()));

    String updatedContent = String.join("\n", finalLines);
    int startLineNum = matchStart + 1;
    int replacedLineCount = reindentedNewLines.size();

    return new ReplacementResult(updatedContent, startLineNum, replacedLineCount, "indentation_tolerant", null);
  }

  // =========================================================================
  // NIVEL 3: BUSQUEDA DIFUSA (DID-YOU-MEAN) ANTE FALLOS
  // =========================================================================
  private String findBestFuzzyCandidate(String fileContent, String oldText) {
    List<String> fileLines = Arrays.asList(fileContent.split("\n", -1));
    List<String> oldLines = Arrays.asList(oldText.split("\n", -1));

    int windowSize = oldLines.size();
    if (windowSize == 0 || fileLines.size() < windowSize) {
      return null;
    }

    int bestStartIndex = -1;
    double maxScore = 0.0;

    for (int i = 0; i <= fileLines.size() - windowSize; i++) {
      double currentScore = 0.0;

      for (int j = 0; j < windowSize; j++) {
        String fileTrim = fileLines.get(i + j).trim();
        String oldTrim = oldLines.get(j).trim();

        if (fileTrim.isEmpty() && oldTrim.isEmpty()) {
          currentScore += 1.0;
        } else if (fileTrim.equals(oldTrim)) {
          currentScore += 1.0;
        } else {
          currentScore += computeSimilarity(fileTrim, oldTrim);
        }
      }

      double normalizedScore = currentScore / windowSize;
      if (normalizedScore > maxScore) {
        maxScore = normalizedScore;
        bestStartIndex = i;
      }
    }

    if (bestStartIndex != -1 && maxScore >= MIN_FUZZY_CONFIDENCE) {
      StringBuilder sb = new StringBuilder();
      for (int k = 0; k < windowSize; k++) {
        int lineNum = bestStartIndex + k + 1;
        sb.append(String.format("%4d | %s\n", lineNum, fileLines.get(bestStartIndex + k)));
      }
      return sb.toString().stripTrailing();
    }

    return null;
  }

  private double computeSimilarity(String s1, String s2) {
    if (s1.isEmpty() || s2.isEmpty()) {
      return 0.0;
    }
    int maxLen = Math.max(s1.length(), s2.length());
    int distance = levenshteinDistance(s1, s2);
    return 1.0 - ((double) distance / maxLen);
  }

  private int levenshteinDistance(String s1, String s2) {
    int[] prev = new int[s2.length() + 1];
    int[] curr = new int[s2.length() + 1];

    for (int j = 0; j <= s2.length(); j++) {
      prev[j] = j;
    }

    for (int i = 1; i <= s1.length(); i++) {
      curr[0] = i;
      for (int j = 1; j <= s2.length(); j++) {
        int cost = (s1.charAt(i - 1) == s2.charAt(j - 1)) ? 0 : 1;
        curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
      }
      System.arraycopy(curr, 0, prev, 0, curr.length);
    }
    return prev[s2.length()];
  }

  // =========================================================================
  // SNIPPET DE CONFIRMACION VISUAL (ESTILO ANTHROPIC)
  // =========================================================================
  private String generateDiffSnippet(String updatedContent, int startLine, int replacedLineCount) {
    List<String> lines = Arrays.asList(updatedContent.split("\n", -1));
    int total = lines.size();

    int startIdx = Math.max(0, startLine - 1 - CONTEXT_PREVIEW_LINES);
    int endReplacedIdx = startLine - 1 + replacedLineCount;
    int endIdx = Math.min(total, endReplacedIdx + CONTEXT_PREVIEW_LINES);

    StringBuilder sb = new StringBuilder();
    for (int i = startIdx; i < endIdx; i++) {
      int lineNum = i + 1;
      boolean isModified = (i >= startLine - 1 && i < endReplacedIdx);
      char marker = isModified ? '*' : ' ';
      sb.append(String.format("%4d%c| %s\n", lineNum, marker, lines.get(i)));
    }
    return sb.toString().stripTrailing();
  }

  // =========================================================================
  // UTILIDADES
  // =========================================================================
  private String getLeadingWhitespace(String line) {
    int index = 0;
    while (index < line.length() && Character.isWhitespace(line.charAt(index))) {
      index++;
    }
    return line.substring(0, index);
  }

  private int countOccurrences(String text, String target) {
    int count = 0;
    int index = 0;
    while ((index = text.indexOf(target, index)) != -1) {
      count++;
      index += target.length();
    }
    return count;
  }

  private int countLines(String text) {
    if (text == null || text.isEmpty()) {
      return 0;
    }
    int lines = 1;
    for (int i = 0; i < text.length(); i++) {
      if (text.charAt(i) == '\n') {
        lines++;
      }
    }
    return lines;
  }

  private record ReplacementResult(
          String updatedContent,
          int startLine,
          int replacedLineCount,
          String strategyApplied,
          String errorMessage
          ) {

    public static ReplacementResult ambiguity(String error) {
      return new ReplacementResult(null, -1, -1, null, error);
    }

    public boolean hasAmbiguityError() {
      return errorMessage != null;
    }
  }

  private static class Args {

    String path;
    String oldText;
    String newText;
  }
}
