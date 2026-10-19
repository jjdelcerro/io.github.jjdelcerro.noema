package io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.file;

import io.github.jjdelcerro.noema.lib.api.Agent;
import io.github.jjdelcerro.noema.lib.api.AgentTool;
import io.github.jjdelcerro.noema.lib.impl.tools.AbstractPaginatedAgentTool;
import io.github.jjdelcerro.noema.lib.impl.tools.ToolSpecificationBuilder;
import io.github.jjdelcerro.noema.lib.impl.utils.CloseableIterable;
import io.github.jjdelcerro.noema.lib.impl.utils.FsUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.tika.detect.AutoDetectReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import static io.github.jjdelcerro.noema.lib.api.AgentAccessControl.AccessMode.PATH_ACCESS_READ;

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
                    + "- `path`: (required) Absolute or relative path to a file or directory.\n"
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

      // 2. Resolve search mode and compile Pattern
      String modeStr = StringUtils.isBlank(args.mode) ? "regexp" : args.mode.trim().toLowerCase();
      Pattern searchPattern;
      try {
        if ("plaintext".equals(modeStr) || "literal".equals(modeStr) || "fixed".equals(modeStr)) {
          searchPattern = Pattern.compile(Pattern.quote(args.query.trim()), Pattern.CASE_INSENSITIVE);
        } else if ("regexp".equals(modeStr) || "regex".equals(modeStr)) {
          searchPattern = Pattern.compile(args.query.trim(), Pattern.CASE_INSENSITIVE);
        } else {
          return formatErrorResponse("Invalid mode '" + args.mode + "'. Supported modes are 'regexp' (default) and 'plaintext'.");
        }
      } catch (PatternSyntaxException e) {
        return formatErrorResponse("Invalid regular expression '" + args.query + "': "
                + e.getDescription() + (e.getIndex() >= 0 ? " near index " + e.getIndex() : ""));
      }

      // 3. Normalize glob pattern
      String filePattern = FsUtils.normalizeGlob(args.filePattern);

      // 4. Build the search scope. It is not resolved here: the walk
      //    intersects it with the zones the agent can see, so a scope
      //    wider than the sandbox still yields the accessible part.
      Path target = Path.of(args.path.trim());

      // 5. Create a temporary file for the grep output
      Path tempDir = agent.getPaths().getTempFolder();
      Files.createDirectories(tempDir);
      Path tempFile = Files.createTempFile(tempDir, "grep_", ".tmp");

      // 6. Perform the grep search
      performGrep(tempFile, target, searchPattern, filePattern);

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
   * The traversal, the glob filtering and the per-entry policy check all live
   * in {@link FsUtils#walk}. This method only drains the result, skips the
   * folders the walk also yields and keeps the global line budget.
   *
   * @param outputFile the temporary file to write results into
   * @param searchTarget the file or directory to search in
   * @param searchPattern compiled pattern to look for
   * @param filePattern glob applied to the path relative to each root
   * @throws IOException if an I/O error occurs
   */
  private void performGrep(Path outputFile, Path searchTarget, Pattern searchPattern, String filePattern) throws IOException {
    long lineCount = 0;

    try (BufferedWriter writer = Files.newBufferedWriter(outputFile, StandardCharsets.UTF_8); CloseableIterable<Path> files = FsUtils.walk(
            agent.getAccessControl(), searchTarget, filePattern, PATH_ACCESS_READ)) {

      for (Path file : files) {
        if (lineCount >= MAX_OUTPUT_LINES) {
          writer.write("\n# WARNING: search stopped because the maximum number of matching lines ("
                  + MAX_OUTPUT_LINES + ") was reached. Some results may be missing.\n");
          break;
        }
        if (!Files.isRegularFile(file)) {
          continue;
        }
        lineCount += grepSingleFile(writer, file, searchPattern, (int) (MAX_OUTPUT_LINES - lineCount));
      }
    }
  }

  /**
   * Searches inside a single file detecting its encoding automatically via
   * Apache Tika.
   *
   * @param limit maximum number of matching lines still allowed by the global
   * budget
   * @return number of matching lines written
   */
  private long grepSingleFile(BufferedWriter writer, Path file, Pattern searchPattern, int limit) {
    long matches = 0;

    try (InputStream in = new BufferedInputStream(new FileInputStream(file.toFile())); BufferedReader reader = new BufferedReader(new AutoDetectReader(in))) {

      String line;
      int lineNum = 1;
      while ((line = reader.readLine()) != null && matches < limit) {
        if (searchPattern.matcher(line).find()) {
          writer.write(file.toAbsolutePath() + ";" + lineNum + ";" + line + "\n");
          matches++;
        }
        lineNum++;
      }
    } catch (Exception e) {
      // Binary or undecodable files are skipped silently
      LOGGER.warn("Skipping unreadable or binary file during grep: {}", file, e);
    }
    return matches;
  }

  private static class Args {

    String path;
    String query;
    String filePattern;
    String mode;
  }
}
