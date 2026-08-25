package io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.scripting.modules;

import groovy.lang.Closure;
import io.github.jjdelcerro.javarcs.lib.RCSCommand;
import io.github.jjdelcerro.javarcs.lib.RCSLocator;
import io.github.jjdelcerro.javarcs.lib.RCSManager;
import io.github.jjdelcerro.javarcs.lib.commands.CheckinOptions;
import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.AgentAccessControl;
import io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.scripting.AbstractScriptingModule;
import io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.scripting.ScriptContext;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.apache.tika.Tika;

public class FsModule extends AbstractScriptingModule {
  public static record GrepMatch(String file, int line, String content) {

    @Override
    public String toString() {
      return String.format("%s:%d: %s", file, line, content);
    }
  }

  final Tika tika;

  public FsModule(ScriptContext context, Agent agent) {
    super(context, agent, "fs", "modulo de acceso al sistema de ficheros");
    this.tika = new Tika();
  }

  /**
   * Iterates line-by-line over a file using a Groovy closure without heap
   * exhaustion.
   */
  public void forEachLine(String rawPath, Closure<?> closure) {
    Path path = validatePath(rawPath, AgentAccessControl.AccessMode.PATH_ACCESS_READ);
    try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
      String line;
      int lineNumber = 1;
      while ((line = reader.readLine()) != null) {
        closure.call(line, lineNumber++);
      }
    } catch (IOException e) {
      throw new RuntimeException("Error reading lines from: " + rawPath + " (" + e.getMessage() + ")", e);
    }
  }

  /**
   * Returns an Iterable of lines from a file (lazy streaming).
   */
  public Iterable<String> lines(String rawPath) {
    Path path = validatePath(rawPath, AgentAccessControl.AccessMode.PATH_ACCESS_READ);
    return () -> {
      try {
        BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8);
        this.context.registerResource(reader);
        return new AutoClosingLineIterator(reader);
      } catch (IOException e) {
        throw new RuntimeException("Cannot open file: " + rawPath, e);
      }
    };
  }

  /**
   * Finds files matching a glob pattern and returns an Iterable of relative
   * path strings.
   */
  public Iterable<String> find(String globPattern) {
    Path root = agent.getPaths().getWorkspaceFolder();
    if (root == null) {
      return Collections.emptyList();
    }
    PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + globPattern);
    List<String> matches = new ArrayList<>();
    try (Stream<Path> stream = Files.walk(root)) {
      stream.filter(Files::isRegularFile).forEach(p -> {
        Path relative = root.relativize(p);
        if (matcher.matches(relative)) {
          matches.add(relative.toString().replace("\\", "/"));
        }
      });
    } catch (IOException e) {
      LOGGER.warn("Error scanning files with pattern: {}", globPattern, e);
    }
    return matches;
  }

  /**
   * Searches for a regex pattern across a file or directory, returning an
   * Iterable of matches.
   */
  public Iterable<GrepMatch> grep(String regex, String rawPath) {
    Path path = validatePath(rawPath, AgentAccessControl.AccessMode.PATH_ACCESS_READ);
    Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
    List<GrepMatch> results = new ArrayList<>();
    if (Files.isRegularFile(path)) {
      grepSingleFile(path, pattern, results);
    } else if (Files.isDirectory(path)) {
      try (Stream<Path> stream = Files.walk(path)) {
        stream.filter(Files::isRegularFile).forEach(file -> {
          grepSingleFile(file, pattern, results);
        });
      } catch (IOException e) {
        LOGGER.warn("Error walking directory for grep: {}", path, e);
      }
    }
    return results;
  }

  private void grepSingleFile(Path file, Pattern pattern, List<GrepMatch> results) {
    try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
      String line;
      int lineNum = 1;
      Path root = agent.getPaths().getWorkspaceFolder();
      String displayPath = root != null ? root.relativize(file).toString() : file.toString();
      while ((line = reader.readLine()) != null) {
        if (pattern.matcher(line).find()) {
          results.add(new GrepMatch(displayPath.replace("\\", "/"), lineNum, line));
        }
        lineNum++;
      }
    } catch (Exception ignored) {
      // Ignore binary or unreadable files during recursive grep
    }
  }

  /**
   * Writes text content or lines to a file with automatic RCS version
   * control.
   */
  public void write(String rawPath, Object content) {
    Path path = validatePath(rawPath, AgentAccessControl.AccessMode.PATH_ACCESS_WRITE);
    try {
      if (path.getParent() != null) {
        Files.createDirectories(path.getParent());
      }
      // Automatic RCS backup before overwrite
      if (Files.exists(path) && agent.getAccessControl().isEnabledRCSBackup()) {
        RCSManager rcsManager = RCSLocator.getRCSManager();
        CheckinOptions options = rcsManager.createCheckinOptions(path);
        options.setAuthor("ScriptExecution");
        options.setInit(true);
        RCSCommand<CheckinOptions> ci = rcsManager.create(options);
        ci.execute(options);
      }
      if (content instanceof Iterable<?> iterable) {
        List<String> lines = new ArrayList<>();
        for (Object item : iterable) {
          lines.add(Objects.toString(item, ""));
        }
        Files.write(path, lines, StandardCharsets.UTF_8);
      } else {
        Files.writeString(path, Objects.toString(content, ""), StandardCharsets.UTF_8);
      }
    } catch (IOException e) {
      throw new RuntimeException("Error writing file: " + rawPath + " (" + e.getMessage() + ")", e);
    }
  }

  private Path validatePath(String rawPath, AgentAccessControl.AccessMode mode) {
    Path resolved = agent.getAccessControl().resolvePathOrNull(rawPath, mode);
    if (resolved == null) {
      throw new SecurityException("Access Denied: Path not allowed by sandbox policy: " + rawPath);
    }
    return resolved;
  }
  
}
