package io.github.jjdelcerro.noema.ui.webclient.handlers;

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
public class HandlerPostEditorFiximports extends AbstractHandler {

  public HandlerPostEditorFiximports(Agent agent) {
    super(agent);
  }

  /**
   * Ajusta/organiza los imports del fichero indicado, en disco.
   *
   * Parametros de entrada:
   * <pre>
   * {
   *  "file": "/ruta/absoluta/A.java"
   * }
   * </pre>
   *
   * Respuesta:
   * <pre>
   * {
   *  "status": "ok"
   * }
   * </pre>
   *
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
      ctx.status(400).json(Map.of("error", "Service MCP not found"));
      return;
    }
    AgentTool tool = serviceMCP.getFirstTool("aide", "lsp_apply_code_action");
    if (tool == null) {
      ctx.status(400).json(Map.of("error", "Tool lsp_apply_code_action not found"));
      return;
    }
    String projectRoot = findProjectFolder(file);
    String toolArgs = String.format("""
{
  "end_column": null,
  "end_line": null,
  "kind": "source.organizeImports",
  "root": "%s",
  "title": null,
  "file": "%s",
  "column": 0,
  "line": 0
}                      
""", projectRoot, file);
    String resp = tool.execute(toolArgs);

    ctx.contentType("application/json").result(resp);
  }

  
}
