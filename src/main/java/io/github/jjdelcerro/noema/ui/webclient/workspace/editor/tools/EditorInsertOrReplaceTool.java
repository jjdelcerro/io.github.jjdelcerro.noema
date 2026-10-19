package io.github.jjdelcerro.noema.ui.webclient.workspace.editor.tools;

import io.github.jjdelcerro.noema.lib.api.Agent;
import io.github.jjdelcerro.noema.lib.api.services.reasoning.ReasoningService;
import io.github.jjdelcerro.noema.lib.impl.tools.AbstractAgentTool;
import io.github.jjdelcerro.noema.lib.impl.tools.ToolSpecificationBuilder;
import io.github.jjdelcerro.noema.ui.webclient.NoemaWebServer;
import java.util.Map;

/**
 * Modifies the buffer of an open file in the web editor: inserts text at a
 * line, or replaces a line range with new text. Does not save to disk; the
 * user decides when to save.
 */
@SuppressWarnings("UseSpecificCatch")
public class EditorInsertOrReplaceTool extends AbstractAgentTool {

  public static final String TOOL_NAME = "editor_insert_or_replace";

  public EditorInsertOrReplaceTool(Agent agent) {
    super(agent);
  }

  @Override
  public ToolSpecificationBuilder getSpecification() {
    return ToolSpecificationBuilder.create()
            .name(TOOL_NAME)
            .description("""
Modifies the buffer of a file that is already open in the user's web editor.
The change is visible immediately but is NOT saved to disk: the user decides
when to save (or discard). Use this to propose a concrete edit in the code
the user is looking at.

Two modes, selected by line_end:
  - Insert: omit line_end. Inserts the text at line_start, shifting the rest
    of the file down.
  - Replace: provide line_end. Replaces the inclusive range [line_start,
    line_end] with the given text.

If text is empty or omitted, the range is deleted (only meaningful in
replace mode). Lines are 1-based. The file must be already open; use
editor_open_file first if it is not.

Never use this to save to disk. If you need to persist changes, use
file_write (which goes through the project's version control).""")
            .addIntegerParameter("line_start", false,
                    "1-based line number where the modification starts.")
            .addIntegerParameter("line_end", true,
                    "Optional 1-based line number of the end of the range to replace. Omit for insertion.")
            .addStringParameter("text", true,
                    "Text to insert or to replace the range with. Empty or omitted means deletion (replace mode only).");
  }

  @Override
  public ToolResult execute(String jsonArguments) {
    try {
      Args args = gson.fromJson(jsonArguments, Args.class);
      if (args.line_start == null) {
        return error("Parameter 'line_start' is required and cannot be empty.");
      }
      NoemaWebServer webserver = (NoemaWebServer) this.agent.get("webserver");
      if (webserver == null) {
        return error("The web editor is not available in the current session.");
      }
      ReasoningService reasoningService = (ReasoningService) this.agent.getService(ReasoningService.NAME);
      webserver.editorInsertOrReplace(
              reasoningService.getCurrentSubchannel(),
              args.line_start,
              args.line_end,
              args.text
      );
      return result(gson.toJson(Map.of("status", "ok")));
    } catch (Throwable t) {
      return error("Error invoking the editor_insert_or_replace tool.");
    }
  }

  private static class Args {
    Integer line_start;
    Integer line_end;
    String text;
  }
}
