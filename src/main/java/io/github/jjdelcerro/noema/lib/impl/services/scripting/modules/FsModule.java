package io.github.jjdelcerro.noema.lib.impl.services.scripting.modules;

import groovy.lang.Closure;
import io.github.jjdelcerro.javarcs.lib.RCSCommand;
import io.github.jjdelcerro.javarcs.lib.RCSLocator;
import io.github.jjdelcerro.javarcs.lib.RCSManager;
import io.github.jjdelcerro.javarcs.lib.commands.CheckinOptions;
import io.github.jjdelcerro.noema.lib.api.Agent;
import io.github.jjdelcerro.noema.lib.api.AgentAccessControl;
import static io.github.jjdelcerro.noema.lib.api.AgentAccessControl.AccessMode.PATH_ACCESS_READ;
import io.github.jjdelcerro.noema.lib.impl.utils.FileFuzzySearchUtils;
import io.github.jjdelcerro.noema.lib.impl.services.scripting.AutoClosingLineIterator;
import io.github.jjdelcerro.noema.lib.api.services.scripting.AbstractScriptModule;
import io.github.jjdelcerro.noema.lib.impl.services.scripting.ScriptContextImpl;
import io.github.jjdelcerro.noema.lib.impl.utils.CloseableIterable;
import io.github.jjdelcerro.noema.lib.impl.utils.FileSearchAndReplaceUtils;
import io.github.jjdelcerro.noema.lib.impl.utils.FileSearchAndReplaceUtils.SearchAndReplaceResult;
import io.github.jjdelcerro.noema.lib.impl.utils.FsUtils;
import io.github.jjdelcerro.noema.lib.impl.utils.search.SearchUtils;
import io.github.jjdelcerro.noema.lib.impl.utils.search.SearchResult;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.tika.Tika;
import org.apache.tika.detect.AutoDetectReader;
import org.apache.tika.metadata.Metadata;
import io.github.jjdelcerro.noema.lib.impl.utils.search.SearchMatcher;
import io.github.jjdelcerro.noema.lib.impl.utils.search.SearchResult.SearchResultEntry;

@SuppressWarnings("UseSpecificCatch")
public class FsModule extends AbstractScriptModule {

//  public static record GrepMatch(String file, int line, String content) {
//
//    @Override
//    public String toString() {
//      return String.format("%s:%d: %s", file, line, content);
//    }
//  }

  final Tika tika;

  public FsModule(ScriptContextImpl context, Agent agent) {
    super(context, agent, "fs", "modulo de acceso al sistema de ficheros");
    this.tika = new Tika();
  }

  @Override
  public String help() {
    return """
[agent.fs API]
Rutas absolutas: una ruta relativa se resuelve contra el directorio de trabajo del
proceso, que no tiene por qué coincidir con la raíz del proyecto. Usa rutas absolutas
para no depender de dónde se inició el agente.
• lines(path): Iterable<String> (lazy stream, autodetección charset y extracción Tika p/ pdf/docx/odt)
  -> agent.fs.lines('/home/user/proyecto/README.md').each { println it }
• forEachLine(path, {line, num -> ..}): void (num: 1-based)
  -> agent.fs.forEachLine('/var/log/app.log') { line, num -> if (line.contains('ERR')) println "$num: $line" }
• find([path], globPattern): Iterable<String> (incluye carpetas)
  -> agent.fs.find('/home/user/proyecto', '**/*.java')
  -> agent.fs.find('/home/user/proyecto', '**/pom.xml')
• grep(regex, path): Iterable<GrepMatch(file, line, content)>
  -> agent.fs.grep('(?i)timeout', '/home/user/proyecto/src').each { println "${it.file}:${it.line} ${it.content}" }
• fuzzygrep(query, path, [glob='**', limit=5, minSim=0.25]): Iterable<FuzzyMatch(file, startLine, endLine, similarity, content)>
  -> agent.fs.fuzzygrep('conexión base de datos', '/home/user/proyecto/docs').each { println "${it.file}:${it.startLine}-${it.endLine}" }
• write(path, String|Iterable): void (auto-mkdir, copia de seguridad automática en RCS)
  -> agent.fs.write('/tmp/out.txt', ['línea 1', 'línea 2'])
• searchAndReplace(path, oldText, newText): SearchAndReplaceResult(status, path, strategy, message, preview, startLine, replacedLineCount)
  -> def res = agent.fs.searchAndReplace('/tmp/f.txt', old, new); println res.status == 'success' ? res.preview : res.message
• delete(path): Map(status, message) (ficheros y carpetas VACIAS; nunca recursivo; copia previa en RCS si esta activo)
  -> println agent.fs.delete('/tmp/out.txt').status         
""";
  }

  /**
   * Finds entries matching a glob pattern inside the workspace.
   *
   * @param globPattern glob applied to the path relative to each root
   * @return display paths, workspace relative when possible
   */
  public Iterable<String> find(String globPattern) {
    return find(".", globPattern);
  }

  /**
   * Finds entries matching a glob pattern, starting at the given scope.
   *
   * <p>
   * The scope is intersected with the zones the agent can see, so a scope
   * wider than the sandbox still yields its accessible part. When the scope
   * does not exist, or lies outside every accessible zone, the result is
   * empty: for the model those paths simply do not exist.
   *
   * <p>
   * Both files and folders are returned. Each entry is checked against the
   * access control on its own, because permissions do not inherit: a denied
   * folder may contain an explicitly allowed file.
   *
   * @param rawPath scope of the search
   * @param globPattern glob applied to the path relative to each root
   * @return display paths, workspace relative when possible
   */
  public Iterable<String> find(String rawPath, String globPattern) {
    Path scope = toScope(rawPath);
    List<String> matches = new ArrayList<>();
    try (CloseableIterable<Path> entries = FsUtils.walk(agent.getAccessControl(), scope,
            FsUtils.normalizeGlob(globPattern), PATH_ACCESS_READ)) {
      for (Path entry : entries) {
        matches.add(FsUtils.toDisplayPath(agent, entry));
      }
    } catch (IOException e) {
      LOGGER.warn("Error scanning files with pattern: {}", globPattern, e);
    }
    return matches;
  }

  /**
   * Searches for a regex pattern across a file or directory, returning an
   * Iterable of matches.
   *
   * <p>
   * The scope is intersected with the zones the agent can see. Folders come
   * out of the walk as well, so they are skipped here: grep only reads files.
   *
   * @param regex regular expression to look for, case insensitive
   * @param rawPath scope of the search
   * @return matches with their display path, line number and content
   */
  public Iterable<SearchResultEntry> grep(String regex, String rawPath) {
    Path scope = toScope(rawPath);
    SearchMatcher matcher = SearchMatcher.of(SearchMatcher.REGEXP, regex, false);
    SearchResult results = SearchUtils.search(
            this.agent.getAccessControl(), 
            matcher, 
            scope
    );
    return results.results();
  }
  
  /**
   * Writes text content or lines to a file with automatic RCS version control.
   * @param rawPath
   * @param content
   */
  public void write(String rawPath, Object content) {
    Path path = validatePath(rawPath, AgentAccessControl.AccessMode.PATH_ACCESS_WRITE);
    try {
      if (path.getParent() != null) {
        Files.createDirectories(path.getParent());
      }
      // Automatic RCS backup before overwrite
      backupInRcs(path);
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

  /**
   * Turns a raw scope into an absolute path. Relative scopes are anchored to
   * the workspace, never to the process working directory.
   */
  private Path toScope(String rawPath) {
    Path workspace = agent.getPaths().getWorkspaceFolder();
    if (rawPath == null || rawPath.trim().isEmpty()) {
      return workspace != null ? workspace : Path.of(".");
    }
    Path scope = Path.of(rawPath.trim());
    if (workspace != null && !scope.isAbsolute()) {
      scope = workspace.resolve(scope);
    }
    return scope;
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
   * @param rawPath
   * @param closure
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
   * @param rawPath
   * @return 
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
    Path path = toScope(rawPath);
    return FileFuzzySearchUtils.search(agent, path, query, filePattern, limit, minSimilarity);
  }

  /**
   * Reemplaza un bloque de texto en un archivo aplicando la estrategia en
   * cascada (exacta -> tolerante a indentacion -> sugerencia difusa),
   * respetando los saltos de linea originales (CRLF/LF) y registrando copia
   * previa en JavaRCS si esta activo.
   *
   * @param rawPath Ruta relativa o absoluta del archivo a modificar.
   * @param oldText Bloque de texto a buscar (debe ser unico en el archivo).
   * @param newText Nuevo texto a insertar (cadena vacia para eliminar).
   * @return SearchAndReplaceResult con el estado, estrategia aplicada, mensaje
   * y vista previa.
   */
  public SearchAndReplaceResult searchAndReplace(String rawPath, String oldText, String newText) {
    return FileSearchAndReplaceUtils.searchAndReplace(
            agent.getAccessControl(),
            rawPath,
            oldText,
            newText
    );
  }

  /**
   * Deletes a regular file, after checking it against the sandbox policy and
   * registering its content in JavaRCS when the backup is enabled.
   *
   * <p>
   * Only regular files are deleted on purpose: a recursive directory removal
   * triggered from a script is a footgun, not a feature. Empty directories are
   * not removed either; the caller can ask for a file and the directory stays.
   *
   * @param rawPath path of the file to delete
   * @return map with status (success|not_found|denied|error), path and message
   */
  public Map<String, Object> delete(String rawPath) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("path", rawPath);
    Path path;
    try {
      path = validatePath(rawPath, AgentAccessControl.AccessMode.PATH_ACCESS_WRITE);
    } catch (SecurityException e) {
      return deleteResult(result, "denied", e.getMessage());
    }
    if (!Files.exists(path)) {
      return deleteResult(result, "not_found", "File does not exist: " + rawPath);
    }
    boolean directory = Files.isDirectory(path);
    if (directory) {
      long entries = countDirectoryEntries(path);
      if (entries < 0) {
        return deleteResult(result, "error", "Cannot inspect directory: " + rawPath);
      }
      if (entries > 0) {
        return deleteResult(result, "denied",
                "Refusing to delete a non-empty directory (" + entries + " entries): " + rawPath);
      }
    }
    try {
      if (!directory) {
        backupInRcs(path);
      }
      Files.delete(path);
      return deleteResult(result, "success", (directory ? "Directory" : "File") + " deleted: " + rawPath);
    } catch (IOException e) {
      return deleteResult(result, "error", "Error deleting " + rawPath + " (" + e.getMessage() + ")");
    }
  }

  /**
   * Counts the entries of a directory without following symbolic links.
   *
   * @param path directory to inspect
   * @return number of entries, or -1 if the directory cannot be read
   */
  private long countDirectoryEntries(Path path) {
    long count = 0;
    try (DirectoryStream<Path> children = Files.newDirectoryStream(path)) {
      for (@SuppressWarnings("unused") Path ignored : children) {
        count++;
      }
      return count;
    } catch (IOException e) {
      return -1;
    }
  }

  /**
   * Registers the current content of a file in JavaRCS, when the backup is
   * enabled. Does nothing if the file does not exist.
   *
   * @param path file to back up
   */
  private void backupInRcs(Path path) {
    if (!Files.exists(path) || !agent.getAccessControl().isEnabledRCSBackup()) {
      return;
    }
    RCSManager rcsManager = RCSLocator.getRCSManager();
    CheckinOptions options = rcsManager.createCheckinOptions(path);
    options.setAuthor("ScriptExecution");
    options.setInit(true);
    RCSCommand<CheckinOptions> ci = rcsManager.create(options);
    ci.execute(options);
  }

  private Map<String, Object> deleteResult(Map<String, Object> result, String status, String message) {
    result.put("status", status);
    result.put("message", message);
    return result;
  }

}
