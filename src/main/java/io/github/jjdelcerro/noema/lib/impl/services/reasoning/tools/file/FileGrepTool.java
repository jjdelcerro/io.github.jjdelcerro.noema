package io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.file;

import io.github.jjdelcerro.noema.lib.api.Agent;
import io.github.jjdelcerro.noema.lib.api.AgentTool;
import io.github.jjdelcerro.noema.lib.impl.tools.AbstractPaginatedAgentTool;
import io.github.jjdelcerro.noema.lib.impl.tools.ToolSpecificationBuilder;
import io.github.jjdelcerro.noema.lib.impl.utils.search.SearchMatcher;
import io.github.jjdelcerro.noema.lib.impl.utils.search.SearchResult;
import io.github.jjdelcerro.noema.lib.impl.utils.search.SearchUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.PatternSyntaxException;

/**
 * Tool that performs a case‑insensitive grep search on a file or directory
 * using regular expressions or literal plain text. Results are paginated and
 * written to a temporary file.
 */
public class FileGrepTool extends AbstractPaginatedAgentTool {

  private static final Logger LOGGER = LoggerFactory.getLogger(FileGrepTool.class);
  public static final String TOOL_NAME = "file_grep";

  // Maximum number of matching lines to write to the temporary file.
  private static final int MAX_OUTPUT_LINES = 50000;

  public FileGrepTool(Agent agent) {
    super(agent);
  }

  @Override
  public ToolSpecificationBuilder getSpecification() {
    return ToolSpecificationBuilder.create()
            .name(TOOL_NAME)
            .description("Searches for a regular expression or plain text pattern inside a file or directory.\n\n"
                    + getShortPaginationInstruction()
                    + "\n**Parameters:**\n"
                    + "- `path`: (required) Absolute path to a file or directory.\n"
                    + "- `query`: (required) Search pattern. By default evaluated as a regular expression (case‑insensitive).\n"
                    + "- `filePattern`: (optional) Glob pattern to filter files (e.g. `\"**/*.java\"`, `\"**/pom.xml\"`). Defaults to `\"**\"` (all files).\n"
                    + "- `mode`: (optional) Search mode: `\"regexp\"` (default, regular expression) or `\"plaintext\"` (literal substring match).\n"
                    + "\n**Output format:** each matching line is written as:\n"
                    + "`absolutePath;lineNumber;lineContent`")
            .addStringParameter("path", false, "Path to the file or directory to search in.")
            .addStringParameter("query", false, "Regular expression or plain text to search for (case‑insensitive).")
            .addStringParameter("filePattern", true, "Glob pattern for file filtering (default: `**`).")
            .addStringParameter("mode", true, "Search mode: 'regexp' (default) or 'plaintext'.");
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
      if (args.query == null || args.query.trim().isEmpty()) {
        return formatErrorResponse("Parameter 'query' is required and cannot be empty.");
      }

      // 2. Resolve search mode and build the matcher. Both modes are case
      //    insensitive, as the tool specification states: the third argument
      //    of SearchMatcher.of is the caseSensitive flag.
      String modeStr = StringUtils.isBlank(args.mode) ? "regexp" : args.mode.trim().toLowerCase();
      SearchMatcher matcher;
      try {
        if ("plaintext".equals(modeStr) || "literal".equals(modeStr) || "fixed".equals(modeStr)) {
          matcher = SearchMatcher.of(SearchMatcher.EXACT, args.query.trim(), false);
        } else if ("regexp".equals(modeStr) || "regex".equals(modeStr)) {
          matcher = SearchMatcher.of(SearchMatcher.REGEXP, args.query.trim(), false);
        } else {
          return formatErrorResponse("Invalid mode '" + args.mode + "'. Supported modes are 'regexp' (default) and 'plaintext'.");
        }
      } catch (PatternSyntaxException e) {
        return formatErrorResponse("Invalid regular expression '" + args.query + "': "
                + e.getDescription() + (e.getIndex() >= 0 ? " near index " + e.getIndex() : ""));
      }

      // 3. The glob pattern is normalized by SearchUtils itself.
      String filePattern = args.filePattern;

      // 4. Build the search scope. It is not resolved here: the walk
      //    intersects it with the zones the agent can see, so a scope
      //    wider than the sandbox still yields the accessible part.
      Path target = Path.of(args.path.trim());

      // 5. Create a temporary file for the grep output
      Path tempDir = agent.getPaths().getTempFolder();
      Files.createDirectories(tempDir);
      Path tempFile = Files.createTempFile(tempDir, "grep_", ".tmp");

      // 6. Perform the grep search
      performGrep(tempFile, target, matcher, filePattern);

      // 7. Paginate the output
      String resourceId = getIdFromPath(tempFile);
      if (resourceId == null) {
        return formatErrorResponse("Failed to generate resource ID for grep output.");
      }

      return servePaginatedResource(resourceId);

    } catch (Exception e) {
      LOGGER.warn("Error executing file_grep: " + jsonArguments, e);
      return formatErrorResponse("Error during grep operation: " + e.getMessage());
    }
  }

  /**
   * Searches every visible file under the scope and writes the matching lines
   * to the output file.
   *
   * <p>
   * The traversal, the glob filtering, the per-entry policy check and the
   * encoding detection all live in {@link SearchUtils#search}. This method
   * only drains the results and keeps the global line budget.
   *
   * <p>
   * Note that {@link SearchUtils#search} materializes the whole result before
   * returning, so the budget is applied after the scan has finished.
   *
   * @param outputFile the temporary file to write results into
   * @param searchTarget the file or directory to search in
   * @param matcher matcher that scores every chunk of text
   * @param filePattern glob applied to the path relative to each root
   * @throws IOException if an I/O error occurs
   */
  private void performGrep(Path outputFile, Path searchTarget, SearchMatcher matcher, String filePattern) throws IOException {
    int lineCount = 0;

    SearchResult results = SearchUtils.search(
            agent.getAccessControl(),
            matcher,
            searchTarget,
            filePattern,
            50,
            MAX_OUTPUT_LINES
    );

    try (BufferedWriter writer = Files.newBufferedWriter(outputFile, StandardCharsets.UTF_8)) {
      for (SearchResult.SearchResultEntry result : results.results()) {
        if (lineCount >= MAX_OUTPUT_LINES) {
          break;
        }
        writer.write(result.file().toAbsolutePath() + ";" + result.line() + ";" + result.content() + "\n");
        lineCount++;
      }
      if (lineCount >= MAX_OUTPUT_LINES || results.isLimitReached() ) {
          writer.write("\n# WARNING: search stopped because the maximum number of matching lines ("
                  + MAX_OUTPUT_LINES + ") was reached. Some results may be missing.\n");
      }
    }
  }

  private static class Args {

    String path;
    String query;
    String filePattern;
    String mode;
  }
}
