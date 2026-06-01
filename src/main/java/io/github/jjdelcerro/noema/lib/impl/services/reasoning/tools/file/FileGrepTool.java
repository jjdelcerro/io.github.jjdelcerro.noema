package io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.file;

import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.AgentTool;
import io.github.jjdelcerro.noema.lib.impl.AbstractPaginatedAgentTool;
import io.github.jjdelcerro.noema.lib.impl.ToolSpecificationBuilder;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.stream.Stream;

import static io.github.jjdelcerro.noema.lib.AgentAccessControl.AccessMode.PATH_ACCESS_READ;

/**
 * Tool that performs a case‑insensitive grep search on a file or directory.
 * Results are paginated and written to a temporary file.
 */
public class FileGrepTool extends AbstractPaginatedAgentTool {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileGrepTool.class);
    public static final String TOOL_NAME = "file_grep";

    // Maximum number of matching lines to write to the temporary file.
    // Beyond this limit, the search stops and a warning is appended.
    private static final int MAX_OUTPUT_LINES = 50000;

    public FileGrepTool(Agent agent) {
        super(agent);
    }

    @Override
    public ToolSpecificationBuilder getSpecification() {
        return ToolSpecificationBuilder.create()
                .name(TOOL_NAME)
                .description("Searches for a case‑insensitive text pattern inside a file or directory.\n\n"
                        + getShortPaginationInstruction()
                        + "\n**Parameters:**\n"
                        + "- `path`: (required) Absolute or relative path to a file or directory.\n"
                        + "- `query`: (required) Text to search for (case‑insensitive).\n"
                        + "- `filePattern`: (optional) Glob pattern to filter files (e.g. `\"**/*.java\"`).\n"
                        + "  Only used when `path` is a directory. Defaults to `\"**/*\"`.\n"
                        + "\n**Output format:** each matching line is written as:\n"
                        + "`absolutePath:lineNumber:lineContent`")
                .addStringParameter("path", false, "Path to the file or directory to search in.")
                .addStringParameter("query", false, "Text to search for (case‑insensitive).")
                .addStringParameter("filePattern", true, "Glob pattern for file filtering (default: `**/*`).");
    }

    @Override
    public int getMode() {
        return AgentTool.MODE_READ;
    }

    @Override
    public String execute(String jsonArguments) {
        try {
            // 1. Parse arguments
            Args args = gson.fromJson(jsonArguments, Args.class);
            if (args.path == null || args.path.trim().isEmpty()) {
                return formatErrorResponse("Parameter 'path' is required and cannot be empty.");
            }
            if (args.query == null || args.query.trim().isEmpty()) {
                return formatErrorResponse("Parameter 'query' is required and cannot be empty.");
            }

            String filePattern = StringUtils.isBlank(args.filePattern) ? "**/*" : args.filePattern.trim();

            // 2. Resolve the search target using the access control
            Path target = resolvePathOrNull(args.path, PATH_ACCESS_READ);
            if (target == null) {
                return formatErrorResponse("Access denied or path does not exist: " + args.path);
            }

            // 3. Create a temporary file for the grep output
            Path tempDir = agent.getPaths().getTempFolder();
            Files.createDirectories(tempDir); // ensure existence
            Path tempFile = Files.createTempFile(tempDir, "grep_", ".tmp");

            // 4. Perform the grep and write results to the temporary file
            performGrep(tempFile, target, args.query.trim(), filePattern);

            // 5. Paginate the temporary file
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
     * Performs the actual grep search and writes matching lines to the output file.
     *
     * @param outputFile   the temporary file to write results into
     * @param searchTarget the file or directory to search in
     * @param query        the case‑insensitive search string
     * @param filePattern  glob pattern for file filtering (ignored if searchTarget is a regular file)
     * @throws IOException if an I/O error occurs
     */
    private void performGrep(Path outputFile, Path searchTarget, String query, String filePattern) throws IOException {
        String lowerQuery = query.toLowerCase();
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + filePattern);

        long lineCount = 0;      // number of lines written (matches)
        boolean limitReached = false;

        try (BufferedWriter writer = Files.newBufferedWriter(outputFile, StandardCharsets.UTF_8)) {
            if (Files.isRegularFile(searchTarget)) {
                // Single file: ignore filePattern, search directly
                lineCount = grepSingleFile(writer, searchTarget, lowerQuery);
                if (lineCount >= MAX_OUTPUT_LINES) {
                    limitReached = true;
                }
            } else if (Files.isDirectory(searchTarget)) {
                // Recursive directory search
                lineCount = grepDirectory(writer, searchTarget, lowerQuery, matcher);
                if (lineCount >= MAX_OUTPUT_LINES) {
                    limitReached = true;
                }
            } else {
                throw new IOException("Path is neither a regular file nor a directory: " + searchTarget);
            }

            if (limitReached) {
                writer.write("\n# WARNING: search stopped because the maximum number of matching lines ("
                        + MAX_OUTPUT_LINES + ") was reached. Some results may be missing.\n");
            }
        }
    }

    /**
     * Searches inside a single file and writes matches to the writer.
     *
     * @return number of matching lines written
     */
    private long grepSingleFile(BufferedWriter writer, Path file, String lowerQuery) throws IOException {
        long matches = 0;
        try (Stream<String> lines = Files.lines(file, StandardCharsets.UTF_8)) {
            int lineNum = 1;
            var iterator = lines.iterator();
            while (iterator.hasNext() && matches < MAX_OUTPUT_LINES) {
                String line = iterator.next();
                if (line.toLowerCase().contains(lowerQuery)) {
                    writer.write(file.toAbsolutePath() + ";" + lineNum + ";" + line + "\n");
                    matches++;
                }
                lineNum++;
            }
        } catch (IOException e) {
            // If a file cannot be read (permissions, encoding), skip it silently.
            LOGGER.debug("Skipping unreadable file during grep: {}", file, e);
        }
        return matches;
    }

    /**
     * Recursively walks a directory and searches all regular files that match the glob pattern.
     *
     * @return number of matching lines written
     */
    private long grepDirectory(BufferedWriter writer, Path rootDir, String lowerQuery, PathMatcher matcher) throws IOException {
        long matches = 0;
        try (Stream<Path> walk = Files.walk(rootDir)) {
            var iterator = walk.iterator();
            while (iterator.hasNext() && matches < MAX_OUTPUT_LINES) {
                Path file = iterator.next();
                if (!Files.isRegularFile(file)) {
                    continue;
                }
                // Check if the file matches the glob pattern relative to the root directory
                Path relative = rootDir.relativize(file);
                if (!matcher.matches(relative)) {
                    continue;
                }
                // Search inside this file
                matches += grepSingleFile(writer, file, lowerQuery);
            }
        }
        return matches;
    }

    // Simple argument holder for JSON deserialization
    private static class Args {
        String path;
        String query;
        String filePattern;
    }
}
