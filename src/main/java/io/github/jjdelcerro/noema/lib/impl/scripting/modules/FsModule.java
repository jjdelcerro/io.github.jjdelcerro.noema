package io.github.jjdelcerro.noema.lib.impl.scripting.modules;

import groovy.lang.Closure;
import io.github.jjdelcerro.javarcs.lib.RCSCommand;
import io.github.jjdelcerro.javarcs.lib.RCSLocator;
import io.github.jjdelcerro.javarcs.lib.RCSManager;
import io.github.jjdelcerro.javarcs.lib.commands.CheckinOptions;
import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.AgentAccessControl;
import io.github.jjdelcerro.noema.lib.impl.FileFuzzySearchUtils;
import io.github.jjdelcerro.noema.lib.impl.scripting.AbstractScriptModule;
import io.github.jjdelcerro.noema.lib.impl.scripting.ScriptContext;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
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
import org.apache.tika.detect.AutoDetectReader;
import org.apache.tika.metadata.Metadata;

public class FsModule extends AbstractScriptModule {

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

  @Override
  public String help() {
    return """
[agent.fs API]
• lines(path): Iterable<String> (lazy stream, autodetección charset y extracción Tika p/ pdf/docx/odt)
  -> agent.fs.lines('f.txt').each { println it }
• forEachLine(path, {line, num -> ..}): void (num: 1-based)
  -> agent.fs.forEachLine('f.log') { line, num -> if (line.contains('ERR')) println "$num: $line" }
• find(globPattern): Iterable<String> (rutas relativas)
  -> agent.fs.find('**/*.java')
• grep(regex, path): Iterable<GrepMatch(file, line, content)>
  -> agent.fs.grep('(?i)timeout', 'src/').each { println "${it.file}:${it.line} ${it.content}" }
• fuzzygrep(query, path, [glob='**', limit=5, minSim=0.25]): Iterable<FuzzyMatch(file, startLine, endLine, content)>
  -> agent.fs.fuzzygrep('conexión base de datos', 'docs/').each { println "${it.file}:${it.startLine}-${it.endLine}" }
• write(path, String|Iterable): void (auto-mkdir, copia de seguridad automática en RCS)
  -> agent.fs.write('out.txt', ['línea 1', 'línea 2'])
""";
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
   * Writes text content or lines to a file with automatic RCS version control.
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

  /**
   * Itera línea a línea sobre un archivo mediante una closure de Groovy sin
   * saturar la memoria heap. Delega en lines(), por lo que procesa de forma
   * transparente tanto texto plano como documentos (PDF, Word, etc.). Pasa a la
   * closure (line, lineNumber).
   */
  public void forEachLine(String rawPath, Closure<?> closure) {
    int lineNumber = 1;
    for (String line : lines(rawPath)) {
      closure.call(line, lineNumber++);
    }
  }

  /**
   * Devuelve un Iterable de líneas en streaming perezoso (lazy). Detecta
   * automáticamente el tipo MIME del archivo: - Texto plano/código: lectura
   * directa detectando codificación de caracteres. - Documentos (PDF, DOCX,
   * ODT, RTF, etc.): extracción de texto mediante Tika. - Binarios puros
   * (imágenes, ejecutables): lanza IllegalArgumentException.
   */
  public Iterable<String> lines(String rawPath) {
    Path path = validatePath(rawPath, AgentAccessControl.AccessMode.PATH_ACCESS_READ);
    return () -> {
      try {
        BufferedReader reader = openReader(path);
        this.context.registerResource(reader);
        return new AutoClosingLineIterator(reader);
      } catch (Exception e) {
        throw new RuntimeException("Cannot open file: " + rawPath + " (" + e.getMessage() + ")", e);
      }
    };
  }

  /**
   * Inspecciona el archivo y abre el BufferedReader adecuado según su tipo de
   * contenido.
   */
  private BufferedReader openReader(Path path) throws IOException {
    if (!Files.exists(path) || !Files.isRegularFile(path)) {
      throw new IOException("El archivo no existe o no es un archivo regular: " + path);
    }

    String mimeType = null;
    try {
      mimeType = this.tika.detect(path);
    } catch (Exception e) {
      LOGGER.debug("No se pudo detectar MIME con Tika para '{}', fallback a texto plano", path, e);
    }

    if (isExtractableDocument(mimeType)) {
      return createDocumentReader(path, mimeType);
    } else if (isPlainText(mimeType)) {
      return createPlainTextReader(path);
    } else {
      throw new IllegalArgumentException(
              "El archivo '" + path.getFileName() + "' es un binario sin contenido textual legible (MIME detectado: " + mimeType + ")");
    }
  }

  /**
   * Crea un lector para documentos empaquetados/binarios (PDF, Word,
   * OpenOffice, RTF) usando Tika.
   */
  private BufferedReader createDocumentReader(Path path, String mimeType) throws IOException {
    InputStream in = new BufferedInputStream(new FileInputStream(path.toFile()));
    try {
      Metadata metadata = new Metadata();
      if (mimeType != null) {
        metadata.set(Metadata.CONTENT_TYPE, mimeType);
      }
      // tika.parse devuelve un Reader con el texto extraído
      Reader tikaReader = this.tika.parse(in, metadata);
      return new BufferedReader(tikaReader);
    } catch (Exception e) {
      // Si falla la instanciación de Tika, cerramos el stream para evitar fugas
      in.close();
      throw new IOException("Error extrayendo texto del documento: " + path.getFileName() + " (" + e.getMessage() + ")", e);
    }
  }

  /**
   * Crea un lector optimizado para texto plano y código con autodetección de
   * charset (UTF-8, Latin-1, etc.).
   */
  private BufferedReader createPlainTextReader(Path path) throws IOException {
    InputStream in = new BufferedInputStream(new FileInputStream(path.toFile()));
    try {
      Reader autoReader = new AutoDetectReader(in);
      return new BufferedReader(autoReader);
    } catch (Exception e) {
      in.close();
      throw new IOException("Error detectando codificación de texto plano en: " + path.getFileName() + " (" + e.getMessage() + ")", e);
    }
  }

  private boolean isPlainText(String mimeType) {
    if (mimeType == null) {
      return true; // Fallback tolerante
    }
    if (mimeType.startsWith("text/")) {
      return true;
    }
    return mimeType.contains("json")
            || mimeType.contains("xml")
            || mimeType.contains("javascript")
            || mimeType.contains("properties")
            || mimeType.contains("yaml")
            || mimeType.contains("yml")
            || mimeType.contains("csv")
            || mimeType.contains("sql")
            || mimeType.equals("application/x-sh")
            || mimeType.equals("application/x-bat");
  }

  private boolean isExtractableDocument(String mimeType) {
    if (mimeType == null) {
      return false;
    }
    return mimeType.equals("application/pdf")
            || mimeType.equals("application/msword")
            || mimeType.equals("application/rtf")
            || mimeType.equals("application/epub+zip")
            || mimeType.startsWith("application/vnd.openxmlformats-officedocument.")
            || mimeType.startsWith("application/vnd.ms-")
            || mimeType.startsWith("application/vnd.oasis.opendocument.");
  }

  public Iterable<FileFuzzySearchUtils.FuzzyMatch> fuzzygrep(String query, String rawPath) {
    return fuzzygrep(query, rawPath, "**", FileFuzzySearchUtils.DEFAULT_LIMIT, FileFuzzySearchUtils.DEFAULT_MIN_SIMILARITY);
  }

  public Iterable<FileFuzzySearchUtils.FuzzyMatch> fuzzygrep(String query, String rawPath, int limit) {
    return fuzzygrep(query, rawPath, "**", limit, FileFuzzySearchUtils.DEFAULT_MIN_SIMILARITY);
  }

  public Iterable<FileFuzzySearchUtils.FuzzyMatch> fuzzygrep(String query, String rawPath, String filePattern, int limit, double minSimilarity) {
    Path path = validatePath(rawPath, AgentAccessControl.AccessMode.PATH_ACCESS_READ);
    return FileFuzzySearchUtils.search(agent, path, query, filePattern, limit, minSimilarity);
  }
}
