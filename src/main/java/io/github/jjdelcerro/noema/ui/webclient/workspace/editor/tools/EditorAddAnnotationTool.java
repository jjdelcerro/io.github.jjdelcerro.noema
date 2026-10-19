package io.github.jjdelcerro.noema.ui.webclient.workspace.editor.tools;

import io.github.jjdelcerro.noema.lib.api.Agent;
import io.github.jjdelcerro.noema.lib.api.services.reasoning.ReasoningService;
import io.github.jjdelcerro.noema.lib.impl.tools.AbstractAgentTool;
import io.github.jjdelcerro.noema.lib.impl.tools.ToolSpecificationBuilder;
import io.github.jjdelcerro.noema.ui.webclient.NoemaWebServer;
import java.util.Map;

/**
 * Paints a visual mark (Ace annotation) on a line of the file currently active
 * in the user's web editor, so the agent can point the user at concrete code.
 * The mark never modifies the buffer and is never saved to disk.
 */
@SuppressWarnings("UseSpecificCatch")
public class EditorAddAnnotationTool extends AbstractAgentTool {

  public static final String TOOL_NAME = "editor_add_annotation";

  public EditorAddAnnotationTool(Agent agent) {
    super(agent);
  }

  @Override
  public ToolSpecificationBuilder getSpecification() {
    return ToolSpecificationBuilder.create()
            .name(TOOL_NAME)
            .description("""
Paints a visual mark on a line of the file that is currently active in the
user's web editor, so the user can see exactly which code you are talking
about. The mark is shown in the editor gutter and as a tooltip carrying your
text. It does NOT modify the file and is NOT saved to disk.

Marks accumulate: every call adds a new mark to the current file. Use
editor_clear_annotations to remove all of them.

Use this to point at a concrete line or range (for example the line where you
believe a bug is), instead of only describing it in prose. Lines are 1-based.
The file must be already open in the editor; use editor_open_file first if it
is not.""")
            .addIntegerParameter("line", false,
                    "1-based line number to mark.")
            .addIntegerParameter("line_end", true,
                    "Optional 1-based end line, to mark a whole range instead of a single line.")
            .addStringParameter("text", false,
                    "Short text shown as a tooltip on the mark, explaining why you are pointing there.")
            .addStringParameter("type", true,
                    "Optional mark severity: 'info' (default), 'warning' or 'error'.")
            .addStringParameter("scroll", true,
                    "Optional 'true' (default) to scroll the editor to the marked line, 'false' to leave the viewport untouched.");
  }

  @Override
  public ToolResult execute(String jsonArguments) {
    try {
      Args args = gson.fromJson(jsonArguments, Args.class);
      if (args.line == null) {
        return error("Parameter 'line' is required.");
      }
      if (args.text == null || args.text.trim().isEmpty()) {
        return error("Parameter 'text' is required and cannot be empty.");
      }
      NoemaWebServer webserver = (NoemaWebServer) this.agent.get("webserver");
      if (webserver == null) {
        return error("The web editor is not available in the current session.");
      }
      ReasoningService reasoningService = (ReasoningService) this.agent.getService(ReasoningService.NAME);
      webserver.editorAddAnnotation(
              reasoningService.getCurrentSubchannel(),
              args.line,
              args.line_end,
              args.text,
              args.type,
              args.scroll
      );
      return result(gson.toJson(Map.of("status", "ok")));
    } catch (Throwable t) {
      return error("Error invoking the editor_add_annotation tool.");
    }
  }

  private static class Args {
    Integer line;
    Integer line_end;
    String text;
    String type;
    Boolean scroll;
  }
}
