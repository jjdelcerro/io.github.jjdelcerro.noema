package io.github.jjdelcerro.noema.lib.impl.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.jjdelcerro.javarcs.lib.RCSCommand;
import io.github.jjdelcerro.javarcs.lib.RCSLocator;
import io.github.jjdelcerro.javarcs.lib.RCSManager;
import io.github.jjdelcerro.javarcs.lib.commands.CheckinOptions;
import io.github.jjdelcerro.noema.lib.api.AgentAccessControl;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Utilidad transversal para busqueda y sustitucion de bloques de texto en
 * archivos. Implementa estrategia en cascada (exacta -> tolerante a sangria ->
 * diagnostico difuso), preservacion de saltos de linea (CRLF/LF), backups
 * preventivos en JavaRCS y generacion de snippet diff numerado.
 */
public final class FileSearchAndReplaceUtils {

  private static final Logger LOGGER = LoggerFactory.getLogger(FileSearchAndReplaceUtils.class);

  private static final int CONTEXT_PREVIEW_LINES = 3;
  private static final double MIN_FUZZY_CONFIDENCE = 0.35;
  private static final String DEFAULT_RCS_AUTHOR = "NoemaAgent";

  private static final LevenshteinDistance LEVENSHTEIN = LevenshteinDistance.getDefaultInstance();
  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

  private FileSearchAndReplaceUtils() {
    // Utility class
  }

  // =========================================================================
  // RESULT RECORD
  // =========================================================================
  public record SearchAndReplaceResult(
          String status,
          String path,
          String strategy,
          String message,
          String preview,
          int startLine,
          int replacedLineCount
          ) {

    public boolean isSuccess() {
      return "success".equals(status);
    }

    public Map<String, Object> toMap() {
      Map<String, Object> map = new LinkedHashMap<>();
      map.put("status", status);
      if (path != null) {
        map.put("path", path);
      }
      if (strategy != null) {
        map.put("strategy", strategy);
      }
      if (message != null) {
        map.put("message", message);
      }
      if (preview != null) {
        map.put("preview", preview);
      }
      return map;
    }

    public String toJson() {
      return GSON.toJson(toMap());
    }

    private static SearchAndReplaceResult success(String path, String strategy, String message, String preview, int startLine, int replacedLineCount) {
      return new SearchAndReplaceResult("success", path, strategy, message, preview, startLine, replacedLineCount);
    }

    private static SearchAndReplaceResult error(String path, String message) {
      return new SearchAndReplaceResult("error", path, null, message, null, -1, 0);
    }
  }

  // =========================================================================
  // SOBRECARGAS PUBLICAS
  // =========================================================================
  /**
   * Sobrecarga autonoma para uso directo sin control de acceso ni backups RCS.
   * @param rawPath
   * @param oldText
   * @param newText
   * @return 
   */
  public static SearchAndReplaceResult searchAndReplace(String rawPath, String oldText, String newText) {
    return searchAndReplace(Path::of, rawPath, oldText, newText, false);
  }

  /**
   * Sobrecarga para integracion con el subsistema de seguridad de Noema.
   * @param accessControl
   * @param newText
   * @param rawPath
   * @param oldText
   * @return 
   */
  public static SearchAndReplaceResult searchAndReplace(
          AgentAccessControl accessControl,
          String rawPath,
          String oldText,
          String newText
  ) {
    if (accessControl == null) {
      return searchAndReplace(rawPath, oldText, newText);
    }
    return searchAndReplace(
            p -> accessControl.resolvePathOrNull(p, AgentAccessControl.AccessMode.PATH_ACCESS_WRITE),
            rawPath,
            oldText,
            newText,
            accessControl.isEnabledRCSBackup()
    );
  }

  /**
   * Metodo nucleo con inyeccion funcional del resolvedor de rutas y flag de
   * backup RCS.
   * @param pathResolver
   * @param enableRcsBackup
   * @param rawPath
   * @param newText
   * @param oldText
   * @return 
   */
  public static SearchAndReplaceResult searchAndReplace(
          Function<String, Path> pathResolver,
          String rawPath,
          String oldText,
          String newText,
          boolean enableRcsBackup
  ) {
    if (rawPath == null || rawPath.isBlank()) {
      return SearchAndReplaceResult.error(rawPath, "El parametro 'path' es obligatorio.");
    }
    if (oldText == null || oldText.isEmpty()) {
      return SearchAndReplaceResult.error(rawPath, "El parametro 'oldText' es obligatorio y no puede estar vacio.");
    }

    Path filePath;
    try {
      filePath = pathResolver != null ? pathResolver.apply(rawPath.trim()) : Path.of(rawPath.trim());
    } catch (Exception e) {
      return SearchAndReplaceResult.error(rawPath, "Ruta no valida: " + rawPath);
    }

    if (filePath == null) {
      return SearchAndReplaceResult.error(rawPath, "Acceso denegado o ruta fuera del sandbox: " + rawPath);
    }

    if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
      return SearchAndReplaceResult.error(rawPath, "El archivo no existe o no es regular: " + rawPath);
    }

    try {
      // 1. Lectura y deteccion de saltos de linea originales
      String rawContent = Files.readString(filePath, StandardCharsets.UTF_8);
      boolean usesCrlf = rawContent.contains("\r\n");

      String fileContent = rawContent.replace("\r\n", "\n");
      String targetOld = oldText.replace("\r\n", "\n");
      String targetNew = newText != null ? newText.replace("\r\n", "\n") : "";

      // 2. Estrategia en cascada
      ReplacementResult result = tryExactMatch(fileContent, targetOld, targetNew);

      if (result == null) {
        result = tryIndentationTolerantMatch(fileContent, targetOld, targetNew);
      }

      if (result == null) {
        String fuzzySuggestion = findBestFuzzyCandidate(fileContent, targetOld);
        StringBuilder errorMsg = new StringBuilder();
        errorMsg.append("No se encontro el bloque 'oldText' en '").append(rawPath).append("'.\n");

        if (fuzzySuggestion != null) {
          errorMsg.append("El bloque mas parecido encontrado en el archivo es:\n")
                  .append("--------------------------------------------------\n")
                  .append(fuzzySuggestion).append("\n")
                  .append("--------------------------------------------------\n")
                  .append("Asegurate de copiar las lineas reales del archivo respetando su estructura.");
        } else {
          errorMsg.append("Verifica el contenido del archivo con 'file_read' antes de volver a intentar el reemplazo.");
        }
        return SearchAndReplaceResult.error(rawPath, errorMsg.toString());
      }

      if (result.hasAmbiguityError()) {
        return SearchAndReplaceResult.error(rawPath, result.errorMessage());
      }

      // 3. Backup preventivo en JavaRCS si esta habilitado
      if (enableRcsBackup && Files.exists(filePath)) {
        performRcsBackup(filePath);
      }

      // 4. Escritura respetando formato de linea original
      String finalContent = usesCrlf ? result.updatedContent().replace("\n", "\r\n") : result.updatedContent();
      Files.writeString(filePath, finalContent, StandardCharsets.UTF_8);

      // 5. Generar vista previa con lineas numeradas
      String snippetPreview = generateDiffSnippet(result.updatedContent(), result.startLine(), result.replacedLineCount());

      return SearchAndReplaceResult.success(
              rawPath,
              result.strategyApplied(),
              "Archivo editado correctamente.",
              snippetPreview,
              result.startLine(),
              result.replacedLineCount()
      );

    } catch (IOException e) {
      LOGGER.warn("Error de E/S ejecutando searchAndReplace sobre '{}'", rawPath, e);
      return SearchAndReplaceResult.error(rawPath, "Error de E/S al editar archivo: " + e.getMessage());
    } catch (Exception e) {
      LOGGER.warn("Error inesperado en searchAndReplace sobre '{}'", rawPath, e);
      return SearchAndReplaceResult.error(rawPath, "Error interno al procesar edicion: " + e.getMessage());
    }
  }

  // =========================================================================
  // NIVEL 1: COINCIDENCIA EXACTA
  // =========================================================================
  private static ReplacementResult tryExactMatch(String fileContent, String oldText, String newText) {
    int firstIndex = fileContent.indexOf(oldText);
    if (firstIndex == -1) {
      return null;
    }

    int secondIndex = fileContent.indexOf(oldText, firstIndex + 1);
    if (secondIndex != -1) {
      int count = countOccurrences(fileContent, oldText);
      return ReplacementResult.ambiguity(String.format(
              "El bloque 'oldText' aparece %d veces en el archivo. Agrega mas lineas de contexto para hacerlo unico.", count));
    }

//    int startLine = countLines(fileContent.substring(0, firstIndex)) + 1;
    int startLine = countOccurrences(fileContent.substring(0, firstIndex), "\n") + 1;    
    int replacedLines = countLines(newText);

    String updated = fileContent.substring(0, firstIndex) + newText + fileContent.substring(firstIndex + oldText.length());
    return new ReplacementResult(updated, startLine, replacedLines, "exact_match", null);
  }

  // =========================================================================
  // NIVEL 2: COINCIDENCIA TOLERANTE A INDENTACION Y ESPACIOS
  // =========================================================================
  private static ReplacementResult tryIndentationTolerantMatch(String fileContent, String oldText, String newText) {
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

    List<String> finalLines = new ArrayList<>(fileLines.subList(0, matchStart));
    finalLines.addAll(reindentedNewLines);
    finalLines.addAll(fileLines.subList(matchStart + windowSize, fileLines.size()));

    String updatedContent = String.join("\n", finalLines);
    int startLineNum = matchStart + 1;
    int replacedLineCount = reindentedNewLines.size();

    return new ReplacementResult(updatedContent, startLineNum, replacedLineCount, "indentation_tolerant", null);
  }

  // =========================================================================
  // NIVEL 3: BUSQUEDA DIFUSA (DID-YOU-MEAN)
  // =========================================================================
  private static String findBestFuzzyCandidate(String fileContent, String oldText) {
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

  private static double computeSimilarity(String s1, String s2) {
    if (s1.isEmpty() || s2.isEmpty()) {
      return 0.0;
    }
    int maxLen = Math.max(s1.length(), s2.length());
    int distance = LEVENSHTEIN.apply(s1, s2);
    return 1.0 - ((double) distance / maxLen);
  }

  // =========================================================================
  // VISTA PREVIA Y AUXILIARES
  // =========================================================================
  private static String generateDiffSnippet(String updatedContent, int startLine, int replacedLineCount) {
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

  private static void performRcsBackup(Path filePath) {
    try {
      RCSManager rcsManager = RCSLocator.getRCSManager();
      CheckinOptions options = rcsManager.createCheckinOptions(filePath);
      options.setAuthor(DEFAULT_RCS_AUTHOR);
      options.setInit(true);
      RCSCommand ci = rcsManager.create(options);
      ci.execute(options);
    } catch (Exception ex) {
      LOGGER.warn("No se pudo registrar copia previa en JavaRCS para: {}", filePath, ex);
    }
  }

  private static String getLeadingWhitespace(String line) {
    int index = 0;
    while (index < line.length() && Character.isWhitespace(line.charAt(index))) {
      index++;
    }
    return line.substring(0, index);
  }

  private static int countOccurrences(String text, String target) {
    int count = 0;
    int index = 0;
    while ((index = text.indexOf(target, index)) != -1) {
      count++;
      index += target.length();
    }
    return count;
  }

  private static int countLines(String text) {
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
}
