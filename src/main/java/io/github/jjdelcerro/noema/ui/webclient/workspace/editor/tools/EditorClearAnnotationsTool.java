package io.github.jjdelcerro.noema.ui.webclient.workspace.editor.tools;

import io.github.jjdelcerro.noema.lib.api.Agent;
import io.github.jjdelcerro.noema.lib.api.services.reasoning.ReasoningService;
import io.github.jjdelcerro.noema.lib.impl.tools.AbstractAgentTool;
import io.github.jjdelcerro.noema.lib.impl.tools.ToolSpecificationBuilder;
import io.github.jjdelcerro.noema.ui.webclient.NoemaWebServer;
import java.util.Map;

/**
 * Removes every visual mark painted by the agent on the file currently active
 * in the user's web editor. Marks owned by the editor diagnostics are not
 * affected.
 */
@SuppressWarnings("UseSpecificCatch")
public class EditorClearAnnotationsTool extends AbstractAgentTool {

  public static final String TOOL_NAME = "editor_clear_annotations";

  public EditorClearAnnotationsTool(Agent agent) {
    super(agent);
  }

  @Override
  public ToolSpecificationBuilder getSpecification() {
    return ToolSpecificationBuilder.create()
            .name(TOOL_NAME)
            .description("""
Removes every visual mark that the agent has painted on the file currently
active in the user's web editor. Marks produced by the editor's own
diagnostics are not affected. It does NOT modify the file and does NOT save
anything to disk.

Use it when the marks you painted are no longer relevant (for example before
pointing at something else, or when you are done discussing that code).""");
  }

  @Override
  public ToolResult execute(String jsonArguments) {
    try {
      NoemaWebServer webserver = (NoemaWebServer) this.agent.get("webserver");
      if (webserver == null) {
        return error("The web editor is not available in the current session.");
      }
      ReasoningService reasoningService = (ReasoningService) this.agent.getService(ReasoningService.NAME);
      webserver.editorClearAnnotations(reasoningService.getCurrentSubchannel());
      return result(gson.toJson(Map.of("status", "ok")));
    } catch (Throwable t) {
      return error("Error invoking the editor_clear_annotations tool.");
    }
  }
}
