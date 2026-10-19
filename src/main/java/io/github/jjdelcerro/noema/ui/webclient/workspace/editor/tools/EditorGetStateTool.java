package io.github.jjdelcerro.noema.ui.webclient.workspace.editor.tools;

import io.github.jjdelcerro.noema.lib.api.Agent;
import io.github.jjdelcerro.noema.lib.api.services.reasoning.ReasoningService;
import io.github.jjdelcerro.noema.lib.impl.tools.AbstractAgentTool;
import io.github.jjdelcerro.noema.lib.impl.tools.ToolSpecificationBuilder;
import io.github.jjdelcerro.noema.ui.webclient.NoemaWebServer;
import io.github.jjdelcerro.noema.ui.webclient.workspace.editor.EditorState;

/**
 * Returns the current state of the user's web editor: open tabs, active file,
 * cursor position and selection range.
 */
@SuppressWarnings("UseSpecificCatch")
public class EditorGetStateTool extends AbstractAgentTool {

  public static final String TOOL_NAME = "editor_get_state";

  public EditorGetStateTool(Agent agent) {
    super(agent);
  }

  @Override
  public ToolSpecificationBuilder getSpecification() {
    return ToolSpecificationBuilder.create()
            .name(TOOL_NAME)
            .description("""
Returns the current state of the user's web editor for the current terminal:
the list of open files (with their dirty flag), which file is active, the
cursor line and the current selection range (if any).

Use this when you need to know exactly what the user is looking at, for
example to confirm a reference before acting on it. Takes no parameters.""");
  }

  @Override
  public ToolResult execute(String jsonArguments) {
    try {
      NoemaWebServer webserver = (NoemaWebServer) this.agent.get("webserver");
      if (webserver == null) {
        return error("The web editor is not available in the current session.");
      }
      ReasoningService reasoningService = (ReasoningService) this.agent.getService(ReasoningService.NAME);
      EditorState state = webserver.getEditorState(reasoningService.getCurrentSubchannel());
      if (state == null) {
        return result(gson.toJson(new EditorState())); // TODO: +1 en los numeros de linea?
      }
      return result(gson.toJson(state));
    } catch (Throwable t) {
      return error("Error invoking the editor_get_state tool.");
    }
  }
}
