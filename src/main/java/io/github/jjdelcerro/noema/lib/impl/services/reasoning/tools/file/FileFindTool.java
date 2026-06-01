package io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.file;

import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.AgentTool;
import io.github.jjdelcerro.noema.lib.impl.AbstractPaginatedAgentTool;
import io.github.jjdelcerro.noema.lib.impl.ToolSpecificationBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.sql.Timestamp;

import static io.github.jjdelcerro.noema.lib.AgentAccessControl.AccessMode.PATH_ACCESS_READ;

/**
 * Tool that lists files and directories matching a glob pattern.
 * Results are paginated and written to a temporary file.
 * Output format: lastModified;type;size;mime;absolutePath
 */
public class FileFindTool extends AbstractPaginatedAgentTool {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileFindTool.class);
    public static final String TOOL_NAME = "file_find";

    // Maximum number of matching entries to write to the temporary file.
    private static final int MAX_OUTPUT_LINES = 50000;

    // Directories that are skipped during traversal to improve performance
//    private static final String[] SKIP_DIRS = { "target", ".git", ".idea", "node_modules" };
    private static final String[] SKIP_DIRS = { };

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
    public String execute(String jsonArguments) {
        try {
            // 1. Parse arguments
            Args args = gson.fromJson(jsonArguments, Args.class);
            if (args.path == null || args.path.trim().isEmpty()) {
                return formatErrorResponse("Parameter 'path' is required and cannot be empty.");
            }
            if (args.pattern == null || args.pattern.trim().isEmpty()) {
                return formatErrorResponse("Parameter 'pattern' is required and cannot be empty.");
            }

            String pattern = args.pattern.trim();

            // 2. Resolve the search target using the access control
            Path target = resolvePathOrNull(args.path, PATH_ACCESS_READ);
            if (target == null) {
                return formatErrorResponse("Access denied or path does not exist: " + args.path);
            }

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
     * Performs the actual find operation and writes matching entries to the output file.
     *
     * @param outputFile   the temporary file to write results into
     * @param searchTarget the file or directory to search in
     * @param pattern      glob pattern for matching
     * @throws IOException if an I/O error occurs
     */
    private void performFind(Path outputFile, Path searchTarget, String pattern) throws IOException {
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);

        try (BufferedWriter writer = Files.newBufferedWriter(outputFile, StandardCharsets.UTF_8)) {
            if (Files.isRegularFile(searchTarget)) {
                // Single file: match its name against the pattern (relative to its parent)
                Path parent = searchTarget.getParent();
                Path toMatch = (parent != null) ? parent.relativize(searchTarget) : searchTarget.getFileName();
                if (matcher.matches(toMatch)) {
                    writeEntry(writer, searchTarget);
                }
            } else if (Files.isDirectory(searchTarget)) {
                walkAndWrite(writer, searchTarget, matcher);
            } else {
                throw new IOException("Path is neither a regular file nor a directory: " + searchTarget);
            }
        }
    }

    /**
     * Recursively walks a directory and writes entries that match the pattern.
     *
     * @param writer   the writer to write output lines
     * @param root     the root directory to start walking from
     * @param matcher  the glob matcher to test against relative paths
     * @throws IOException if an I/O error occurs
     */
    private void walkAndWrite(BufferedWriter writer, Path root, PathMatcher matcher) throws IOException {
        long matches = 0;
        try (var walk = Files.walk(root)) {
            var iterator = walk.iterator();
            while (iterator.hasNext() && matches < MAX_OUTPUT_LINES) {
                Path path = iterator.next();
                // Skip known large/unwanted directories
                if (shouldSkipDirectory(path)) {
                    continue;
                }
                Path relative = root.relativize(path);
                if (matcher.matches(relative)) {
                    writeEntry(writer, path);
                    matches++;
                }
            }
        }
        // If we stopped due to the limit, append a warning line (optional)
        if (matches >= MAX_OUTPUT_LINES) {
            writer.write("\n# WARNING: maximum number of entries (" + MAX_OUTPUT_LINES + ") reached. Some results may be missing.\n");
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

    /**
     * Determines if a directory should be skipped during traversal.
     * This helps avoid walking into known large/unwanted folders like 'target' or '.git'.
     */
    private boolean shouldSkipDirectory(Path path) {
        if (!Files.isDirectory(path)) {
            return false;
        }
        String name = path.getFileName().toString();
        for (String skip : SKIP_DIRS) {
            if (name.equals(skip)) {
                return true;
            }
        }
        return false;
    }

    // Simple argument holder for JSON deserialization
    private static class Args {
        String path;
        String pattern;
    }
}
