package io.github.jjdelcerro.noema.ui.webclient.state.tools;

import io.github.jjdelcerro.noema.lib.api.Agent;
import io.github.jjdelcerro.noema.lib.api.services.reasoning.ReasoningService;
import io.github.jjdelcerro.noema.lib.impl.tools.AbstractAgentTool;
import io.github.jjdelcerro.noema.lib.impl.tools.ToolSpecificationBuilder;
import io.github.jjdelcerro.noema.ui.webclient.NoemaWebServer;
import io.github.jjdelcerro.noema.ui.webclient.state.UIState;

/**
 * Returns the state reported by the web client for the current terminal,
 * covering every UI component: the file editor and the notes panel.
 */
@SuppressWarnings("UseSpecificCatch")
public class UIGetStateTool extends AbstractAgentTool {

  public static final String TOOL_NAME = "ui_get_state";

  public UIGetStateTool(Agent agent) {
    super(agent);
  }

  @Override
  public ToolSpecificationBuilder getSpecification() {
    return ToolSpecificationBuilder.create()
            .name(TOOL_NAME)
            .description("""
Returns the current state of the user's web client for the current terminal,
component by component:

- editor: the list of open files (with their dirty flag), which file is
  active, the cursor line and the current selection range (if any).
- notes: the note currently open in the notes panel (title, path, cursor
  line and selection range). It is null when no note is open.

Line numbers are 1-based, as shown in the editor. Use this when you need to
know exactly what the user is looking at or editing, for example to confirm
a reference before acting on it. Takes no parameters.""");
  }

  @Override
  public ToolResult execute(String jsonArguments) {
    try {
      NoemaWebServer webserver = (NoemaWebServer) this.agent.get("webserver");
      if (webserver == null) {
        return error("The web client is not available in the current session.");
      }
      ReasoningService reasoningService = (ReasoningService) this.agent.getService(ReasoningService.NAME);
      UIState state = webserver.getUIState(reasoningService.getCurrentSubchannel());
      if (state == null) {
        return result(gson.toJson(new UIState()));
      }
      return result(gson.toJson(state));
    } catch (Throwable t) {
      return error("Error invoking the ui_get_state tool.");
    }
  }
}
