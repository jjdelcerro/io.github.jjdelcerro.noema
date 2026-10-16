package io.github.jjdelcerro.noema.ui.webclient.workspace.editor.tools;

import io.github.jjdelcerro.noema.lib.api.Agent;
import io.github.jjdelcerro.noema.lib.api.services.reasoning.ReasoningService;
import io.github.jjdelcerro.noema.lib.impl.tools.AbstractAgentTool;
import io.github.jjdelcerro.noema.lib.impl.tools.ToolSpecificationBuilder;
import io.github.jjdelcerro.noema.ui.webclient.NoemaWebServer;
import java.util.Map;

/**
 * Opens a file in the user's web editor. If the file is already open, it
 * activates its tab; otherwise opens a new one. Optionally positions the
 * cursor at a specific line.
 */
@SuppressWarnings("UseSpecificCatch")
public class EditorOpenFileTool extends AbstractAgentTool {

  public static final String TOOL_NAME = "editor_open_file";

  public EditorOpenFileTool(Agent agent) {
    super(agent);
  }

  @Override
  public ToolSpecificationBuilder getSpecification() {
    return ToolSpecificationBuilder.create()
            .name(TOOL_NAME)
            .description("""
Opens a file in the user's web editor so the user can see it on screen. If
the file is already open, activates its tab (does not reload it). Optionally
positions the cursor at a specific line.

Use this when you want to point the user to a concrete file, or when you
need them to see a specific part of the code you are discussing. The path
must be absolute. The line, if provided, is 1-based.""")
            .addStringParameter("path", false,
                    "Absolute path of the file to open in the editor.")
            .addIntegerParameter("line", true,
                    "Optional 1-based line number to position the cursor at after opening.");
  }

  @Override
  public String execute(String jsonArguments) {
    try {
      Args args = gson.fromJson(jsonArguments, Args.class);
      if (args.path == null || args.path.trim().isEmpty()) {
        return error("Parameter 'path' is required and cannot be empty.");
      }
      NoemaWebServer webserver = (NoemaWebServer) this.agent.get("webserver");
      if (webserver == null) {
        return error("The web editor is not available in the current session.");
      }
      ReasoningService reasoningService = (ReasoningService) this.agent.getService(ReasoningService.NAME);
      webserver.editorOpenFile(reasoningService.getCurrentSubchannel(), args.path, args.line);
      return gson.toJson(Map.of("status", "ok"));
    } catch (Throwable t) {
      return error("Error invoking the editor_open_file tool.");
    }
  }

  private static class Args {
    String path;
    Integer line;
  }
}
