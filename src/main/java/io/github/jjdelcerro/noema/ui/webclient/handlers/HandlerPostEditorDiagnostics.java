package io.github.jjdelcerro.noema.ui.webclient.handlers;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jjdelcerro.noema.lib.api.Agent;
import io.github.jjdelcerro.noema.lib.api.AgentTool;
import io.github.jjdelcerro.noema.lib.impl.services.mcp.McpServiceImpl;
import io.javalin.http.Context;
import java.util.Map;

/**
 *
 * @author jjdelcerro
 */
public class HandlerPostEditorDiagnostics extends AbstractHandler {

  /**
   * Offset a aplicar a line y col que devuelve 'lsp_diagnostics' para
   * convertirlos a 0-based, que es lo que espera Ace en setAnnotations.
   *
   * Segun la convencion observada del servidor MCP, este valor es 1 si el
   * servidor devuelve 1-based, o 0 si devuelve 0-based. Verificar con un
   * caso conocido antes de tocar.
   */
  private static final int LSP_LINE_OFFSET = 0;

  public HandlerPostEditorDiagnostics(Agent agent) {
    super(agent);
  }

  /**
   * Devuelve diagnosticos (errores, warnings) para un fichero concreto.
   *
   * Contrato de la respuesta (consumido por el frontend, alineado con
   * session.setAnnotations de Ace; row y column son 0-indexados):
   * <pre>
   * {
   *  "annotations": [
   *    { "row": 12, "column": 8, "text": "...", "type": "error" },
   *    { "row": 20, "column": 0, "text": "...", "type": "warning" }
   *  ]
   * }
   * </pre>
   *
   * Parametros de entrada: { "file": "/ruta/absoluta/A.java" }
   *
   * La herramienta MCP 'lsp_diagnostics' del servidor 'aide' devuelve una
   * lista JSON de objetos con esta forma (campos aplanados, no LSP puro):
   * <pre>
   * { "severity": "Warning", "line": 4, "col": 7,
   *   "end_line": 4, "end_col": 66, "message": "...", "source": "Java" }
   * </pre>
   * Si no hay problemas devuelve []. Si hay error de infraestructura
   * (por ejemplo, LSP no inicializado), devuelve { "error": "..." }.
   */
  @Override
  public void handle(Context ctx) throws Exception {
    JsonObject body;
    try {
      body = JsonParser.parseString(ctx.body()).getAsJsonObject();
    } catch (Exception e) {
      ctx.status(400).json(Map.of("error", "JSON invalido en el cuerpo de la peticion"));
      return;
    }
    String file = body.has("file") ? body.get("file").getAsString() : null;
    if (file == null || file.isBlank()) {
      ctx.status(400).json(Map.of("error", "El campo 'file' es obligatorio"));
      return;
    }

    McpServiceImpl serviceMCP = (McpServiceImpl) this.agent.getService(McpServiceImpl.NAME);
    if (serviceMCP == null) {
      ctx.status(500).json(Map.of("error", "Service MCP not found"));
      return;
    }
    AgentTool tool = serviceMCP.getFirstTool("aide", "lsp_diagnostics");
    if (tool == null) {
      ctx.status(500).json(Map.of("error", "Tool lsp_diagnostics not found"));
      return;
    }

    String projectRoot = findProjectFolder(file);
    String toolArgs = String.format("""
{
  "file": "%s",
  "root": "%s"
}
""", file, projectRoot);

    String resp = tool.execute(toolArgs);

    JsonArray annotations = new JsonArray();
    if (resp == null || resp.isBlank()) {
      ctx.contentType("application/json").result("{\"annotations\":[]}");
      return;
    }

    JsonElement parsed;
    try {
      parsed = JsonParser.parseString(resp);
    } catch (Exception e) {
      LOGGER.warn("Respuesta de lsp_diagnostics no parseable: {}", resp, e);
      ctx.contentType("application/json").result("{\"annotations\":[]}");
      return;
    }

    // Caso de error de infraestructura: {"error": "..."}
    if (parsed.isJsonObject() && parsed.getAsJsonObject().has("error")) {
      String errorMsg = parsed.getAsJsonObject().get("error").getAsString();
      LOGGER.warn("lsp_diagnostics devolvio error: {}", errorMsg);
      ctx.status(503).json(Map.of("error", errorMsg));
      return;
    }

    // Caso normal: array de diagnosticos
    if (parsed.isJsonArray()) {
      for (JsonElement el : parsed.getAsJsonArray()) {
        if (!el.isJsonObject()) {
          continue;
        }
        JsonObject annotation = convertDiagnostic(el.getAsJsonObject());
        if (annotation != null) {
          annotations.add(annotation);
        }
      }
    } else {
      LOGGER.warn("Respuesta de lsp_diagnostics con forma inesperada: {}", resp);
    }

    JsonObject result = new JsonObject();
    result.add("annotations", annotations);
    ctx.contentType("application/json").result(new Gson().toJson(result));
  }

  /**
   * Convierte un diagnostico del servidor MCP 'aide' al formato de
   * anotacion de Ace: {row, column, text, type}. Devuelve null si el
   * objeto no tiene la estructura minima esperada.
   *
   * Aplica LSP_LINE_OFFSET para pasar de la convencion del servidor a
   * 0-based, que es lo que consume Ace.
   */
  private JsonObject convertDiagnostic(JsonObject diagnostic) {
    if (!diagnostic.has("message") || !diagnostic.has("line") || !diagnostic.has("col")) {
      return null;
    }
    JsonObject annotation = new JsonObject();
    annotation.addProperty("row", diagnostic.get("line").getAsInt() - LSP_LINE_OFFSET);
    annotation.addProperty("column", diagnostic.get("col").getAsInt() - LSP_LINE_OFFSET);
    annotation.addProperty("text", diagnostic.get("message").getAsString());
    annotation.addProperty("type", severityLabel(diagnostic));
    return annotation;
  }

  /**
   * Traduce el campo 'severity' del diagnostico (string) a la etiqueta
   * textual que consume Ace: error, warning, info o hint.
   *
   * Valor por defecto 'info' si el campo falta o no se reconoce.
   */
  private String severityLabel(JsonObject diagnostic) {
    if (!diagnostic.has("severity") || diagnostic.get("severity").isJsonNull()) {
      return "info";
    }
    String severity = diagnostic.get("severity").getAsString();
    if (severity == null) {
      return "info";
    }
    switch (severity.toLowerCase()) {
      case "error":
        return "error";
      case "warning":
        return "warning";
      case "information":
      case "info":
        return "info";
      case "hint":
        return "hint";
      default:
        return "info";
    }
  }

}
