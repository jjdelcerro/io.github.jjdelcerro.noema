package io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.file;

import io.github.jjdelcerro.noema.lib.api.Agent;
import io.github.jjdelcerro.noema.lib.api.AgentTool;
import io.github.jjdelcerro.noema.lib.impl.tools.AbstractPaginatedAgentTool;
import io.github.jjdelcerro.noema.lib.impl.tools.ToolSpecificationBuilder;
import io.github.jjdelcerro.noema.lib.impl.utils.CloseableIterable;
import io.github.jjdelcerro.noema.lib.impl.utils.FsUtils;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;

import static io.github.jjdelcerro.noema.lib.api.AgentAccessControl.AccessMode.PATH_ACCESS_READ;

/**
 * Tool that lists files and directories matching a glob pattern. Results are
 * paginated and written to a temporary file. Output format:
 * lastModified;type;size;mime;absolutePath
 */
@SuppressWarnings("UseSpecificCatch")
public class FileFindTool extends AbstractPaginatedAgentTool {

  public static final String TOOL_NAME = "file_find";

  // Maximum number of matching entries to write to the temporary file.
  private static final int MAX_OUTPUT_LINES = 50000;

  public FileFindTool(Agent agent) {
    super(agent);
  }

  @Override
  public ToolSpecificationBuilder getSpecification() {
    return ToolSpecificationBuilder.create()
            .name(TOOL_NAME)
            .description("Lists files and directories that match a glob pattern, with pagination.\n\n"
                    + getShortPaginationInstruction()
                    + "\n**Parameters:**\n"
                    + "- `path`: (required) Absolute or relative path to a file or directory.\n"
                    + "- `pattern`: (required) Glob pattern (e.g. `\"**/*.java\"`, `\"pom.xml\"`).\n"
                    + "\n**Output format (one line per entry, fields separated by ';'):**\n"
                    + "`lastModified;type;size;mime;absolutePath`\n"
                    + "Example: `2025-05-30 12:00:00.123;file;2048;text/x-java;/home/user/project/src/Main.java`")
            .addStringParameter("path", false, "Path to the file or directory to search in.")
            .addStringParameter("pattern", false, "Glob pattern for matching file/directory names.");
  }

  @Override
  public int getMode() {
    return AgentTool.MODE_READ;
  }

  @Override
  public ToolResult execute(String jsonArguments) {
    try {
      // 1. Parse arguments
      Args args = gson.fromJson(jsonArguments, Args.class);
      if (args.path == null || args.path.trim().isEmpty()) {
        return formatErrorResponse("Parameter 'path' is required and cannot be empty.");
      }
      if (args.pattern == null || args.pattern.trim().isEmpty()) {
        return formatErrorResponse("Parameter 'pattern' is required and cannot be empty.");
      }

      String pattern = FsUtils.normalizeGlob(args.pattern);

      // 2. Build the search scope. It is not resolved here: the walk
      //    intersects it with the zones the agent can see, so a scope
      //    wider than the sandbox still yields the accessible part.
      Path target = Path.of(args.path.trim());

      // 3. Create a temporary file for the find output
      Path tempDir = agent.getPaths().getTempFolder();
      Files.createDirectories(tempDir);
      Path tempFile = Files.createTempFile(tempDir, "find_", ".tmp");

      // 4. Perform the find and write results to the temporary file
      performFind(tempFile, target, pattern);

      // 5. Paginate the temporary file
      String resourceId = getIdFromPath(tempFile);
      if (resourceId == null) {
        return formatErrorResponse("Failed to generate resource ID for find output.");
      }

      return servePaginatedResource(resourceId);

    } catch (Exception e) {
      LOGGER.warn("Error executing file_find: " + jsonArguments, e);
      return formatErrorResponse("Error during find operation: " + e.getMessage());
    }
  }

  /**
   * Performs the actual find operation and writes matching entries to the
   * output file.
   *
   * <p>
   * The traversal, the glob filtering and the per-entry policy check all live
   * in {@link FsUtils#walk}. This method only drains the result and formats it.
   *
   * @param outputFile the temporary file to write results into
   * @param searchTarget the file or directory to search in
   * @param pattern glob pattern for matching
   * @throws IOException if an I/O error occurs
   */
  private void performFind(Path outputFile, Path searchTarget, String pattern) throws IOException {
    try (BufferedWriter writer = Files.newBufferedWriter(outputFile, StandardCharsets.UTF_8); CloseableIterable<Path> entries = FsUtils.walk(agent.getAccessControl(), searchTarget, pattern, PATH_ACCESS_READ)) {

      int matches = 0;
      for (Path path : entries) {
        if (matches >= MAX_OUTPUT_LINES) {
          writer.write("\n# WARNING: maximum number of entries (" + MAX_OUTPUT_LINES
                  + ") reached. Some results may be missing.\n");
          break;
        }
        writeEntry(writer, path);
        matches++;
      }
    }
  }

  /**
   * Writes a single entry (file or directory) to the output in the format:
   * lastModified;type;size;mime;absolutePath
   */
  private void writeEntry(BufferedWriter writer, Path path) throws IOException {
    String lastModified = Timestamp.from(Files.getLastModifiedTime(path).toInstant()).toString();
    boolean isDir = Files.isDirectory(path);
    String type = isDir ? "dir" : "file";
    long size = isDir ? 0 : Files.size(path);
    String mime;
    if (isDir) {
      mime = "directory";
    } else {
      mime = Files.probeContentType(path);
      if (mime == null) {
        mime = "unknown";
      }
    }
    writer.write(String.format("%s;%s;%d;%s;%s\n",
            lastModified, type, size, mime, path.toAbsolutePath()));
  }

  // Simple argument holder for JSON deserialization
  private static class Args {

    String path;
    String pattern;
  }
}
