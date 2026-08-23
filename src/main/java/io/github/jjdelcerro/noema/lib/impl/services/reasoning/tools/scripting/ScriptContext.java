package io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.scripting;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import groovy.lang.Closure;
import groovy.lang.GroovyObjectSupport;
import groovy.lang.MissingPropertyException;
import io.github.jjdelcerro.javarcs.lib.RCSCommand;
import io.github.jjdelcerro.javarcs.lib.RCSLocator;
import io.github.jjdelcerro.javarcs.lib.RCSManager;
import io.github.jjdelcerro.javarcs.lib.commands.CheckinOptions;
import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.AgentAccessControl;
import static io.github.jjdelcerro.noema.lib.AgentAccessControl.AccessMode.PATH_ACCESS_READ;
import static io.github.jjdelcerro.noema.lib.AgentAccessControl.AccessMode.PATH_ACCESS_WRITE;
import io.github.jjdelcerro.noema.lib.AgentLocator;
import io.github.jjdelcerro.noema.lib.Subagent;
import io.github.jjdelcerro.noema.lib.SubagentDefinition;
import io.github.jjdelcerro.noema.lib.persistence.Turn;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Root context object ('noema') exposed to the embedded Groovy environment.
 * Dynamically resolves facades and provides streamed, safe access to agent
 * capabilities.
 */
public class ScriptContext extends GroovyObjectSupport implements AutoCloseable /*, ScriptingModule */ {

  public static final String CONTEXT_NAME = "noema";
  
  public interface ScriptingModule {

    String getName();

    String getDescription();

    String help();
  }

  public static abstract class AbstractScriptingModule implements ScriptingModule {

    private final String name;
    private final String description;
    protected final Agent agent;
    protected final ScriptContext context;

    protected AbstractScriptingModule(ScriptContext context, Agent agent, String name, String description) {
      this.context = context;
      this.agent = Objects.requireNonNull(agent, "Agent cannot be null");
      this.name = name;
      this.description = description;
    }

    @Override
    public String getName() {
      return name;
    }

    @Override
    public String getDescription() {
      return description;
    }

    @Override
    public String help() {
      return "";
    }

  }

  private static final Logger LOGGER = LoggerFactory.getLogger(ScriptContext.class);

  private final Agent agent;
  private final String subchannel;
  private final Map<String, ScriptingModule> modules;
  private final List<AutoCloseable> openResources = new ArrayList<>();
  
  @SuppressWarnings("OverridableMethodCallInConstructor")
  public ScriptContext(Agent agent, String subchannel, Map<String, Object> sessionState) {
    this.agent = Objects.requireNonNull(agent, "Agent cannot be null");
    this.subchannel = subchannel != null ? subchannel : Agent.DEFAULT_SUBCHANNEL;
    this.modules = new ConcurrentHashMap<>();

    // Register standard static facades
    registerModule(new SessionStateModule(this, this.agent, sessionState));
    registerModule(new FsModule(this, this.agent));
    registerModule(new LlmModule(this, this.agent));
    registerModule(new WebModule(this, this.agent));
    registerModule(new NotesModule(this, this.agent, this.subchannel));
    registerModule(new SubagentsModule(this, this.agent));
    // registerModule(new MCPModule(this, this.agent)); // TODO: implementar el puenete con MCP.
  }

  public String getName() {
    return CONTEXT_NAME;
  }

  public String getDescription() {
    return "";
  }

  public String help() {
    StringBuilder sb = new StringBuilder("Available modules:\n");
    for (ScriptingModule module : modules.values()) {
      sb.append(" - ")
              .append(this.getName())
              .append(".")
              .append(module.getName())
              .append(" - ")
              .append(module.getDescription())
              .append("\n");
    }
    return sb.toString();
  }

  /**
   * Registers a facade dynamically under a specific property name.
   */
  public void registerModule(ScriptingModule module) {
    if (module != null && StringUtils.isNotBlank(module.getName())) {
      this.modules.put(module.getName(), module);
    }
  }

  public ScriptingModule getModule(String name) {
    return this.modules.get(name);
  }

  public Map<String, ScriptingModule> getRegisteredModules() {
    return Collections.unmodifiableMap(this.modules);
  }

  /**
   * Dynamic Groovy property dispatcher. Intercepts property accesses like
   * 'noema.fs', 'noema.llm', etc.
   */
  @Override
  public Object getProperty(String property) {
    if (modules.containsKey(property)) {
      return modules.get(property);
    }
    return super.getProperty(property);
  }

  /**
   * Fallback for missing properties in Groovy.
   */
  public Object propertyMissing(String name) {
    if (modules.containsKey(name)) {
      return modules.get(name);
    }
    throw new MissingPropertyException(
            "Property '" + name + "' is not a registered module. Available modules: " + modules.keySet(),
            name,
            this.getClass()
    );
  }
  
  public AutoCloseable registerResource(AutoCloseable resource) {
    this.openResources.add(resource);
    return resource;
  }

  @Override
  public void close() throws Exception {
    for (AutoCloseable resource : openResources) {
      try {
        if( resource!=null ) {
          resource.close();
        }
      } catch(Exception ex) {
        LOGGER.warn("Can't close resource", ex);
      }
    }
  }

  
  // =========================================================================
  // STATIC FACADE 1: FILESYSTEM (Streaming & Iterables)
  // =========================================================================
  public static class FsModule extends AbstractScriptingModule {

    private final Tika tika;

    public FsModule(ScriptContext context, Agent agent) {
      super(context, agent, "fs", "modulo de acceso al sistema de ficheros");
      this.tika = new Tika();
    }

    /**
     * Iterates line-by-line over a file using a Groovy closure without heap
     * exhaustion.
     */
    public void forEachLine(String rawPath, Closure<?> closure) {
      Path path = validatePath(rawPath, PATH_ACCESS_READ);
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
      Path path = validatePath(rawPath, PATH_ACCESS_READ);
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
      Path path = validatePath(rawPath, PATH_ACCESS_READ);
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
      Path path = validatePath(rawPath, PATH_ACCESS_WRITE);

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

  public static record GrepMatch(String file, int line, String content) {

    @Override
    public String toString() {
      return String.format("%s:%d: %s", file, line, content);
    }
  }

  // =========================================================================
  // STATIC FACADE 2: LLM (Stateless sub-queries)
  // =========================================================================
  public static class LlmModule extends AbstractScriptingModule {

    private static final String DEFAULT_MODEL = "DOCMAPPER_BASIC";

    public LlmModule(ScriptContext context, Agent agent) {
      super(context, agent, "llm", "modulo de acceso al API del LLM");
    }

    /**
     * Performs a stateless semantic query on a text chunk.
     */
    public String query(String prompt, String chunk) {
      return queryWithModel(DEFAULT_MODEL, prompt, chunk);
    }

    public String queryWithModel(String modelId, String prompt, String chunk) {
      String result = agent.callChatModel(modelId, prompt, chunk != null ? chunk : "");
      return result != null ? result.trim() : "";
    }

    /**
     * Performs a structured extraction, returning a parsed JSON Map or List.
     */
    public Object extractJson(String prompt, String chunk) {
      JsonObject json = agent.callChatModelAsJson(DEFAULT_MODEL, prompt, chunk != null ? chunk : "");
      if (json == null) {
        return Collections.emptyMap();
      }
      return new Gson().fromJson(json, Object.class);
    }

    /**
     * Evaluates an iterable of chunks sequentially, applying a prompt to each
     * item.
     */
    public Iterable<String> map(String prompt, Iterable<String> chunks) {
      List<String> results = new ArrayList<>();
      for (String chunk : chunks) {
        results.add(query(prompt, chunk));
      }
      return results;
    }
  }

  // =========================================================================
  // STATIC FACADE 3: WEB (Streamed downloads and search)
  // =========================================================================
  public static class WebModule extends AbstractScriptingModule {

    private final HttpClient httpClient;
    private final Tika tika;

    public WebModule(ScriptContext context, Agent agent) {
      super(context, agent, "web", "modulo de acceso a funciones web");
      this.httpClient = HttpClient.newBuilder()
              .connectTimeout(Duration.ofSeconds(15))
              .followRedirects(HttpClient.Redirect.NORMAL)
              .build();
      this.tika = new Tika();
    }

    /**
     * Downloads and streams lines of text extracted from a URL via Tika.
     */
    public Iterable<String> lines(String url) {
      URI uri = URI.create(url);
      if (!agent.getAccessControl().isAccessible(uri)) {
        throw new SecurityException("Access Denied to URL: " + url);
      }

      try {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .header("User-Agent", "Noema-Bot/1.0")
                .GET()
                .build();

        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
          throw new IOException("HTTP Error " + response.statusCode());
        }

        String contentType = response.headers().firstValue("Content-Type").orElse("text/plain");
        InputStream input = new ByteArrayInputStream(response.body());
        Reader reader = contentType.contains("text/plain")
                ? new InputStreamReader(input, StandardCharsets.UTF_8)
                : tika.parse(input, new Metadata());

        BufferedReader bufferedReader = new BufferedReader(reader);
        this.context.registerResource(bufferedReader);
        return () -> new AutoClosingLineIterator(bufferedReader);

      } catch (Exception e) {
        throw new RuntimeException("Error fetching URL: " + url + " (" + e.getMessage() + ")", e);
      }
    }

    /**
     * Performs web search and returns an Iterable of result maps (title, url,
     * content).
     */
    public Iterable<Map<String, String>> search(String query) {
      String apiKey = agent.getSettings().getPropertyAsString("websearch/tavily_api_key");
      if (StringUtils.isBlank(apiKey)) {
        throw new IllegalStateException("Tavily API Key is not configured.");
      }

      try {
        JsonObject body = new JsonObject();
        body.addProperty("query", query);
        body.addProperty("search_depth", "basic");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.tavily.com/search"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
          return Collections.emptyList();
        }

        JsonObject fullRes = JsonParser.parseString(response.body()).getAsJsonObject();
        List<Map<String, String>> list = new ArrayList<>();

        if (fullRes.has("results")) {
          fullRes.getAsJsonArray("results").forEach(el -> {
            JsonObject item = el.getAsJsonObject();
            Map<String, String> entry = new LinkedHashMap<>();
            entry.put("title", item.has("title") ? item.get("title").getAsString() : "");
            entry.put("url", item.has("url") ? item.get("url").getAsString() : "");
            entry.put("content", item.has("content") ? item.get("content").getAsString() : "");
            list.add(entry);
          });
        }
        return list;

      } catch (Exception e) {
        LOGGER.warn("Web search failed for query: {}", query, e);
        return Collections.emptyList();
      }
    }
  }

  // =========================================================================
  // STATIC FACADE 4: NOTES (Episodic Memory Bridge)
  // =========================================================================
  public static class NotesModule extends AbstractScriptingModule {

    private final String subchannel;

    public NotesModule(ScriptContext context, Agent agent, String subchannel) {
      super(context, agent, "notes", "Modulo de acceso a las anotaciones del agente");
      this.subchannel = subchannel != null ? subchannel : Agent.DEFAULT_SUBCHANNEL;
    }

    public void add(String source, String note) {
      add(source, note, null, null);
    }

    public void add(String source, String note, String resourceId) {
      add(source, note, resourceId, null);
    }

    /**
     * Persists an annotation turn directly into EpisodicMemory for subsequent
     * checkpoint compaction.
     */
    public void add(String source, String note, String resourceId, String type) {
      if (StringUtils.isBlank(note)) {
        return;
      }

      Map<String, Object> args = new LinkedHashMap<>();
      args.put("source", source != null ? source : "script");
      args.put("note", note);
      if (StringUtils.isNotBlank(resourceId)) {
        args.put("resource_id", resourceId);
      }
      if (StringUtils.isNotBlank(type)) {
        args.put("type", type);
      }

      String toolCallJson = new Gson().toJson(args);
      Turn turn = agent.getEpisodicMemory().createTurn(
              LocalDateTime.now(),
              "annotation",
              subchannel,
              null,
              null,
              null,
              toolCallJson,
              "{\"status\": \"success\"}",
              null
      );
      agent.getEpisodicMemory().add(turn);
      LOGGER.info("Knowledge note registered from script: [{}] {}", source, StringUtils.abbreviate(note, 60));
    }
  }

  // =========================================================================
  // STATIC FACADE 5: SUBAGENTS (Worker Recipes)
  // =========================================================================
  public static class SubagentsModule extends AbstractScriptingModule {

    public SubagentsModule(ScriptContext context, Agent agent) {
      super(context, agent, "subagents", "Modulo de acceso a las funciones relacionadas con subagentes");
    }

    /**
     * Runs a subagent recipe synchronously and returns its final response
     * string.
     */
    public String run(String recipeName, Map<String, ?> params) {
      Path xmlPath = agent.getPaths().getAgentPath("var/subagents/" + recipeName + ".xml");
      if (xmlPath == null || !Files.exists(xmlPath)) {
        throw new IllegalArgumentException("Subagent recipe not found: " + recipeName);
      }

      try {
        SubagentDefinition def = AgentLocator.getAgentManager().createSubagentDefinition(xmlPath);
        Path tempWorkspace = agent.getPaths().getTempFolder().resolve("script_subagent_" + System.currentTimeMillis());

        try (Subagent subagent = AgentLocator.getAgentManager().createSubagent(agent, def, tempWorkspace)) {
          return subagent.run(params != null ? params : Collections.emptyMap());
        }
      } catch (Exception e) {
        throw new RuntimeException("Failed running subagent '" + recipeName + "': " + e.getMessage(), e);
      }
    }

    /**
     * Launches a subagent asynchronously in the background.
     */
    public int launch(String recipeName, Map<String, ?> params) {
      Path xmlPath = agent.getPaths().getAgentPath("var/subagents/" + recipeName + ".xml");
      if (xmlPath == null || !Files.exists(xmlPath)) {
        throw new IllegalArgumentException("Subagent recipe not found: " + recipeName);
      }

      try {
        SubagentDefinition def = AgentLocator.getAgentManager().createSubagentDefinition(xmlPath);
        Path tempWorkspace = agent.getPaths().getTempFolder().resolve("script_subagent_async_" + System.currentTimeMillis());
        Subagent subagent = AgentLocator.getAgentManager().createSubagent(agent, def, tempWorkspace);
        return subagent.launch(params != null ? params : Collections.emptyMap());
      } catch (Exception e) {
        throw new RuntimeException("Failed launching subagent '" + recipeName + "': " + e.getMessage(), e);
      }
    }
  }

  public static class SessionStateModule extends AbstractScriptingModule {

    private final Map<String, Object> sessionState;

    public SessionStateModule(ScriptContext context, Agent agent, Map<String, Object> sessionState) {
      super(context, agent, "state", "Modulo encargado de mantener el estado de la sesion");
      this.sessionState = sessionState != null ? sessionState : new ConcurrentHashMap<>();
    }

    public void set(String name, Object value) {
      this.sessionState.put(name, value);
    }

    public Object get(String name) {
      return this.sessionState.get(name);
    }
    
    // Soporte para sintaxis de propiedad en Groovy: noema.state.foo = bar
    public void propertyMissing(String name, Object value) {
        set(name, value);
    }

    public Object propertyMissing(String name) {
        return get(name);
    }    
  }

  // =========================================================================
  // STATIC UTILITY: Lazy Auto-Closing Line Iterator
  // =========================================================================
  public static class AutoClosingLineIterator implements Iterator<String> {

    private final BufferedReader reader;
    private String nextLine = null;
    private boolean finished = false;

    public AutoClosingLineIterator(BufferedReader reader) {
      this.reader = Objects.requireNonNull(reader, "BufferedReader cannot be null");
      advance();
    }

    private void advance() {
      if (finished) {
        return;
      }
      try {
        nextLine = reader.readLine();
        if (nextLine == null) {
          close();
        }
      } catch (IOException e) {
        close();
        throw new RuntimeException("Error reading line: " + e.getMessage(), e);
      }
    }

    private void close() {
      finished = true;
      nextLine = null;
      IOUtils.closeQuietly(reader);
    }

    @Override
    public boolean hasNext() {
      return nextLine != null;
    }

    @Override
    public String next() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }
      String current = nextLine;
      advance();
      return current;
    }
  }
}
