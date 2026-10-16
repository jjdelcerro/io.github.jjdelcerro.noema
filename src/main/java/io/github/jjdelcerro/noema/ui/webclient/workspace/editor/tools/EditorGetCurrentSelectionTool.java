package io.github.jjdelcerro.noema.ui.webclient.workspace.editor.tools;

import io.github.jjdelcerro.noema.lib.api.Agent;
import io.github.jjdelcerro.noema.lib.api.services.reasoning.ReasoningService;
import io.github.jjdelcerro.noema.lib.impl.tools.AbstractAgentTool;
import io.github.jjdelcerro.noema.lib.impl.tools.ToolSpecificationBuilder;
import io.github.jjdelcerro.noema.ui.webclient.NoemaWebServer;
import io.github.jjdelcerro.noema.ui.webclient.workspace.editor.EditorSelection;
import java.util.Map;

/**
 * Returns the text currently selected by the user in the active file of
 * the web editor.
 */
@SuppressWarnings("UseSpecificCatch")
public class EditorGetCurrentSelectionTool extends AbstractAgentTool {

  public static final String TOOL_NAME = "editor_get_current_selection";

  public EditorGetCurrentSelectionTool(Agent agent) {
    super(agent);
  }

  @Override
  public ToolSpecificationBuilder getSpecification() {
    return ToolSpecificationBuilder.create()
            .name(TOOL_NAME)
            .description("""
Returns the text currently selected by the user in the active file of the
web editor, along with the start and end line of the selection. The lines
are 1-based.

Use this when the user refers to "this", "the selected code" or similar
deictic expressions, and you need the exact text rather than only the
line range. For the range alone, editor_get_state is enough. Takes no
parameters.""");
  }

  @Override
  public String execute(String jsonArguments) {
    try {
      NoemaWebServer webserver = (NoemaWebServer) this.agent.get("webserver");
      if (webserver == null) {
        return error("The web editor is not available in the current session.");
      }
      ReasoningService reasoningService = (ReasoningService) this.agent.getService(ReasoningService.NAME);
      EditorSelection selection = webserver.getCurrentSelection(reasoningService.getCurrentSubchannel());
      if (selection == null) {
        return error("There is no active selection in the current terminal.");
      }
      return gson.toJson(Map.of(
              "file", selection.getFile() != null ? selection.getFile() : "",
              "linestart", selection.getSelectionStartLine() != null ? selection.getSelectionStartLine() : 0,
              "lineend", selection.getSelectionEndLine() != null ? selection.getSelectionEndLine() : 0,
              "text", selection.getText() != null ? selection.getText() : ""
      ));
    } catch (Throwable t) {
      return error("Error invoking the editor_get_current_selection tool.");
    }
  }
}
