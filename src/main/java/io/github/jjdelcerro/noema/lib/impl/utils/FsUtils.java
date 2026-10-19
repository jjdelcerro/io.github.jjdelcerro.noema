package io.github.jjdelcerro.noema.lib.impl.utils;

import io.github.jjdelcerro.noema.lib.api.Agent;
import io.github.jjdelcerro.noema.lib.api.AgentAccessControl;
import io.github.jjdelcerro.noema.lib.api.AgentAccessControl.AccessMode;
import static io.github.jjdelcerro.noema.lib.api.AgentAccessControl.AccessMode.PATH_ACCESS_READ;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Filesystem helpers. Every operation here honours the agent access control
 * policy: nothing is returned unless the caller can see it.
 *
 * <p>
 * This class is meant to grow. All filesystem walking performed on behalf of
 * the agent (scripting modules and reasoning tools alike) must go through
 * {@link #walk} so that the visibility rules are applied in a single place.
 *
 * @author jjdelcerro
 */
public class FsUtils {
  
  public static final Logger LOGGER = LoggerFactory.getLogger(FsUtils.class);

  private static final Tika TIKA = new Tika();

  private FsUtils() {
  }

  /**
   * Walks the given scope and returns every visible regular file, read mode.
   * @param accessControl
   * @param scope
   * @return 
   */
  public static CloseableIterable<Path> walk(AgentAccessControl accessControl, Path scope) {
    return walk(accessControl, scope, null, PATH_ACCESS_READ);
  }

  /**
   * Walks the given scope and returns every visible regular file matching the
   * glob pattern, read mode.
   * @param accessControl
   * @param scope
   * @param globPattern
   * @return 
   */
  public static CloseableIterable<Path> walk(AgentAccessControl accessControl, Path scope, String globPattern) {
    return walk(accessControl, scope, globPattern, PATH_ACCESS_READ);
  }

  /**
   * Walks the given scope and returns every visible regular file matching the
   * glob pattern for the requested access mode.
   *
   * <p>
   * The result is lazy: each call to {@link Iterable#iterator()} starts its own
   * traversal and yields files one by one.
   *
   * <p>
   * Entries denied by the policy are skipped without ceremony. A path the agent
   * cannot reach does not exist for it; no distinction is made between denied
   * and missing.
   *
   * <p>
   * Filesystem errors are absorbed one entry at a time: an unreadable folder or
   * a dangling link is skipped and never aborts the traversal. Folder links are
   * not followed, so a link cannot be used to climb out of the authorised zone.
   *
   * <p>
   * The paths handed over are the canonical ones returned by the policy check,
   * not the ones produced by the traversal. When a link is involved those are
   * different objects, and only the canonical one has been validated.
   *
   * @param accessControl policy used to resolve the scope and to filter entries
   * @param scope starting point; may be relative to the agent workspace
   * @param globPattern glob applied to the path relative to the root; null
   * means every file
   * @param mode read or write policy applied to every entry
   * @return visible files, lazy
   */
  public static CloseableIterable<Path> walk(AgentAccessControl accessControl, Path scope, String globPattern, AccessMode mode) {
    Objects.requireNonNull(accessControl, "accessControl");

    AccessMode accessMode = (mode == null) ? PATH_ACCESS_READ : mode;
    PathMatcher matcher = (globPattern == null || globPattern.isBlank())
            ? null
            : FileSystems.getDefault().getPathMatcher("glob:" + globPattern);

    List<Path> scopes = accessControl.resolveRoots(scope, accessMode);
    CloseableIterator[] scopesIterators = new CloseableIterator[scopes.size()];
    for (int i = 0; i < scopes.size(); i++) {
      Path singleScope = scopes.get(i);
      if (Files.isDirectory(singleScope, LinkOption.NOFOLLOW_LINKS)) {
        scopesIterators[i]  = new WalkFsIterator(accessControl, singleScope, matcher, accessMode);
      } else if (!Files.isRegularFile(singleScope)) {
        scopesIterators[i] = CloseableIterator.empty();
      } else if (matcher != null && !matcher.matches(singleScope.getFileName())) {
        scopesIterators[i] = CloseableIterator.empty();
      } else {
        scopesIterators[i] = CloseableIterator.of(singleScope);
      }
    }
    
    return CloseableIterable.of(new ChainedIterator<>(scopesIterators));
  }

  /**
   * Normalises a glob pattern so that it also matches entries sitting directly
   * at the root.
   *
   * <p>
   * A pattern whose leading segments are a recursive wildcard does not match
   * {@code Main.java} when the walk relativises the entry against the root: the
   * relativised path has no leading folder. The pattern is expanded to a brace
   * form covering both shapes.
   *
   * @param filePattern raw pattern
   * @return the normalised pattern, never null
   */
  public static String normalizeGlob(String filePattern) {
    if (StringUtils.isBlank(filePattern) || filePattern.equals("**/*") || filePattern.equals("*")) {
      return "**";
    }
    String trimmed = filePattern.trim();
    if (trimmed.startsWith("**/")) {
      return "{" + trimmed + "," + trimmed.substring(3) + "}";
    }
    return trimmed;
  }

  /**
   * Returns a path as the agent should see it: relative to the workspace when
   * it lives inside it, absolute otherwise.
   *
   * <p>
   * Workspace relative paths stay readable; paths outside the workspace stay
   * absolute so they can be handed back to any operation that accepts a scope.
   *
   * @param agent agent whose workspace is used as reference
   * @param file path to display
   * @return path with forward slashes
   */
  public static String toDisplayPath(Agent agent, Path file) {
    if (agent == null || agent.getPaths() == null || agent.getPaths().getWorkspaceFolder() == null) {
      return file.toString().replace("\\", "/");
    }
    Path root = agent.getPaths().getWorkspaceFolder();
    String displayPath = file.startsWith(root) ? root.relativize(file).toString() : file.toString();
    return displayPath.replace("\\", "/");
  }

  /**
   * Tells whether a file must be treated as binary and therefore skipped by
   * text oriented operations.
   *
   * <p>
   * The decision is based on the detected mime type, not on the extension. When
   * detection fails the file is assumed binary: skipping a readable text file
   * is cheaper than dumping a binary blob into the conversation.
   *
   * @param path file to inspect
   * @return true when the content must not be read as text
   */
  public static boolean isBinaryResource(Path path) {
    try {
      String mimeType = TIKA.detect(path);
      if (mimeType.startsWith("text/")) {
        return false;
      }
      return !(mimeType.contains("json")
              || mimeType.contains("xml")
              || mimeType.contains("javascript")
              || mimeType.contains("properties")
              || mimeType.contains("yaml")
              || mimeType.contains("yml")
              || mimeType.contains("csv")
              || mimeType.contains("sql")
              || mimeType.equals("application/x-sh")
              || mimeType.equals("application/x-bat"));
    } catch (IOException ex) {
      return true;
    }
  }

  /**
   * Depth first traversal over a single root.
   *
   * <p>
   * An open folder holds a {@link DirectoryStream} plus its iterator, so memory
   * stays proportional to the depth of the tree, not to its size. A folder is
   * opened when it is entered and closed as soon as it is exhausted.
   *
   * <p>
   * Streams are released when the traversal reaches its end. A caller that
   * abandons the iterator halfway leaves the folders of the open levels
   * dangling, so iterations are expected to be consumed to the end.
   * Call close() to release them when the iteration is abandoned.
   */
  private static final class WalkFsIterator implements CloseableIterator<Path> {

    private record Folder(DirectoryStream<Path> stream, Iterator<Path> entries) {
    }

    private final AgentAccessControl accessControl;
    private final AccessMode mode;
    private final PathMatcher matcher;
    private final Path root;
    private final Deque<Folder> folderStack;
    private Path next;
    private boolean nextReady;

    WalkFsIterator(AgentAccessControl accessControl, Path root, PathMatcher matcher, AccessMode mode) {
      this.accessControl = accessControl;
      this.mode = mode;
      this.matcher = matcher;
      this.root = root;
      this.folderStack = new ArrayDeque<>();
      this.next = null;
      this.nextReady = false;
      this.openFolder(root);
    }

    @Override
    public boolean hasNext() {
      if (!this.nextReady) {
        this.searchNext();
      }
      return this.nextReady;
    }

    @Override
    public Path next() {
      if (!this.hasNext()) {
        throw new NoSuchElementException();
      }
      this.nextReady = false;
      return this.next;
    }

    /**
     * Positions the iterator on the next visible entry, or marks it as
     * exhausted.
     */
    private void searchNext() {
      while (!this.folderStack.isEmpty()) {
        Folder currentFolder = this.folderStack.peek();

        // Folder exhausted: close it and go back up one level.
        if (!currentFolder.entries().hasNext()) {
          this.closeFolder();
          continue;
        }

        Path entry = currentFolder.entries().next();
        boolean isFolder = Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS);

        // Not a folder and not a regular file (broken link, socket, device): ignored.
        if (!isFolder && !Files.isRegularFile(entry)) {
          continue;
        }

        // Descend into folders whether or not they match: a folder that does
        // not match the pattern can still contain entries that do.
        if (isFolder) {
          this.openFolder(entry);
        }

        if (this.matcher != null && !this.matcher.matches(this.root.relativize(entry))) {
          continue;
        }

        Path resolved = this.accessControl.resolvePathOrNull(entry, this.mode);
        if (resolved == null) {
          continue;
        }

        this.next = resolved;
        this.nextReady = true;
        return;
      }

      this.next = null;
      this.nextReady = false;
    }

    private void openFolder(Path folder) {
      try {
        DirectoryStream<Path> stream = Files.newDirectoryStream(folder);
        this.folderStack.push(new Folder(stream, stream.iterator()));
      } catch (IOException | SecurityException ex) {
        // Unreadable folder: the branch is skipped, the traversal goes on.
        LOGGER.debug("Skipping unreadable folder: {}", folder);
      }
    }

    private void closeFolder() {
      Folder folder = this.folderStack.pop();
      try {
        folder.stream().close();
      } catch (IOException ex) {
        LOGGER.debug("Error closing folder stream", ex);
      }
    }
    
    @Override
    public void close() throws IOException {
        // Cierra los descriptores abiertos si se aborta el recorrido antes de tiempo
        while (!this.folderStack.isEmpty()) {
          Folder folder = this.folderStack.pop();
          IOUtils.closeQuietly(folder.stream);
        }
    }

  }
}
