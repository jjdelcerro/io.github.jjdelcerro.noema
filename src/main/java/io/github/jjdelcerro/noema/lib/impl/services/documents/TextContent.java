package io.github.jjdelcerro.noema.lib.impl.services.documents;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Gestiona el contenido textual de un documento cargado en memoria línea a
 * línea. Proporciona herramientas para exportar a CSV (para el LLM) y extraer
 * rangos de líneas.
 *
 * @author jjdelcerro
 */
public class TextContent extends ArrayList<TextContent.TextLine> {

  /**
   * Representa una línea individual del documento.
   */
  public static class TextLine {

    public final int lineNumber; // 1-indexed para facilitar la vida al LLM
    public final String text;

    public TextLine(int lineNumber, String text) {
      this.lineNumber = lineNumber;
      this.text = text;
    }

    @Override
    public String toString() {
      return lineNumber + ": " + text;
    }
  }

  public TextContent() {
    super();
  }

  /**
   * Carga el documento línea a línea.
   *
   * @param document Ruta al archivo.
   * @param charset Codificación (usualmente UTF_8).
   * @throws IOException Si hay error de lectura.
   */
  public void load(Path document, Charset charset) throws IOException {
    this.clear();
    try (BufferedReader reader = Files.newBufferedReader(document, charset)) {
      String line;
      int currentLine = 1; // Empezamos en 1 para que el LLM no se líe con el 0
      while ((line = reader.readLine()) != null) {
        this.add(new TextLine(currentLine++, line));
      }
    }
  }

  /**
   * Genera un volcado en formato CSV con columnas LINE y CONTENT. Escapa las
   * comillas dobles para que el LLM reciba un CSV válido.
   *
   * @return String con el CSV completo.
   */
  public String toCSV() {
    StringBuilder sb = new StringBuilder();
    sb.append("LINE,CONTENT\n");
    for (TextLine line : this) {
      sb.append(line.lineNumber)
              .append(",\"")
              .append(escapeCsv(line.text))
              .append("\"\n");
    }
    return sb.toString();
  }

  /**
   * Extrae un bloque de texto entre dos líneas (inclusive). Útil para la fase
   * de resumen de secciones.
   *
   * @param startLine Primera línea (1-indexed).
   * @param endLine Última línea (1-indexed).
   * @return El texto concatenado.
   */
  public String getRange(int startLine, int endLine) {
    // Ajustamos de 1-indexed a 0-indexed para el acceso a la lista
    int startIdx = Math.max(0, startLine - 1);
    int endIdx = Math.min(this.size() - 1, endLine - 1);

    if (startIdx > endIdx) {
      return "";
    }

    List<String> lines = new ArrayList<>();
    for (int i = startIdx; i <= endIdx; i++) {
      lines.add(this.get(i).text);
    }
    return String.join("\n", lines);
  }

  /**
   * Escapa comillas dobles duplicándolas según el estándar CSV.
   */
  private String escapeCsv(String text) {
    if (text == null) {
      return "";
    }
    // Reemplazamos " por ""
    return text.replace("\"", "\"\"");
  }
}
