package io.github.jjdelcerro.noema.ui.webclient;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Range;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.palantir.javaformat.java.Formatter;
import com.palantir.javaformat.java.FormatterException;
import com.palantir.javaformat.java.JavaFormatterOptions;
import io.github.jjdelcerro.noema.lib.api.Agent;
import io.github.jjdelcerro.noema.lib.api.AgentAccessControl;
import io.github.jjdelcerro.noema.lib.api.AgentConsole;
import io.github.jjdelcerro.noema.lib.api.AgentTool;
import io.github.jjdelcerro.noema.lib.api.settings.AgentSettingsCheckedList;
import io.github.jjdelcerro.noema.lib.api.settings.AgentSettingsGroup;
import io.github.jjdelcerro.noema.lib.api.settings.AgentSettingsItem;
import io.github.jjdelcerro.noema.lib.api.settings.AgentSettingsPaths;
import io.github.jjdelcerro.noema.lib.api.settings.AgentSettingsString;
import io.github.jjdelcerro.noema.lib.api.memory.episodic.Turn;
import io.github.jjdelcerro.noema.lib.impl.services.mcp.McpServiceImpl;
import io.github.jjdelcerro.noema.lib.impl.utils.AgentUtils;
import io.github.jjdelcerro.noema.lib.impl.utils.ConversationJsonToXml;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.sse.SseClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.apache.commons.lang3.StringUtils;

/**
 * Servidor Web embebido para Noema. Organizado mediante referencias a métodos
 * para cada endpoint REST y SSE.
 */
@SuppressWarnings("UseSpecificCatch")
public class NoemaWebServer {

  private static final Logger LOGGER = LoggerFactory.getLogger(NoemaWebServer.class);

  private final Agent agent;
  private final int port;
  private final String address;
  private final Map<String, SseAgentConsole> activeConsoles;
  private final Map<String, EditorState> editorStates;
  private Javalin app;

  public NoemaWebServer(Agent agent, String address, int port) {
    this.agent = agent;
    this.port = port;
    this.address = address;
    this.activeConsoles = new ConcurrentHashMap<>();
    this.editorStates = new ConcurrentHashMap<>();
  }

  /**
   * Inicia el servidor e instala la tabla de rutas mapeada a métodos de la
   * instancia.
   */
  public synchronized void start() {
    if (this.app != null) {
      LOGGER.info("El servidor web de Noema ya se encuentra en ejecución.");
      return;
    }

    URL webappResource = this.getClass().getResource("/webapp");
    Path rootPath = this.agent.getPaths().getAgentFolder().resolve("var/webapp").toAbsolutePath();
    AgentUtils.installResources(webappResource, rootPath, false);

    this.app = Javalin.create(config -> {
      config.bundledPlugins.enableCors(cors -> cors.addRule(it -> it.anyHost()));
      config.staticFiles.add(staticFiles -> {
        staticFiles.directory = rootPath.toString();
        staticFiles.location = io.javalin.http.staticfiles.Location.EXTERNAL;
      });
    });

    // --- TABLA DE ENRUTAMIENTO (BINDING DE MÉTODOS) ---
    // 1. Conversación e Historial
    this.app.post("/api/chat/{terminalId}", this::handlePostChatMessage);
    this.app.get("/api/chat/{terminalId}/history", this::handleGetHistory);
    this.app.sse("/api/console/{terminalId}", this::handleSseConsole);
    this.app.get("/api/chat/{terminalId}/context.xml", this::handleGetContextXml);

    // 2. Configuración dinámica (Rutas específicas antes que las genéricas <path>)
    this.app.get("/api/config/ui", this::handleGetConfigUi);
    this.app.get("/api/config/domains/{domainName}", this::handleGetDomain);
    this.app.post("/api/config/multivalue", this::handlePostConfigMultivalue);
    this.app.post("/api/config/<path>/list", this::handlePostConfigList);
    this.app.post("/api/config/<path>", this::handlePostConfigPath);
    this.app.get("/api/config/<path>", this::handleGetConfigPath);

    // 3. Exploración de Sistema de Archivos
    this.app.get("/api/fs/directories", this::handleGetDirectories);

    // 4. Ejecución de Acciones del Agente
    this.app.post("/api/actions/{actionName}", this::handlePostAction);

    // 5. Gestión de Contenido de Ficheros (var:/ y rutas absolutas)
    this.app.get("/api/files/content", this::handleGetFileContent);
    this.app.post("/api/files/content", this::handlePostFileContent);
    this.app.get("/api/files/tree", this::handleGetFilesTree);

    // 6. Editor Web: estado y acciones sobre ficheros
    this.app.post("/api/editor/state", this::handlePostEditorState);
    this.app.post("/api/editor/diagnostics", this::handlePostEditorDiagnostics);
    this.app.post("/api/editor/format", this::handlePostEditorFormat);
    this.app.post("/api/editor/fiximports", this::handlePostEditorFiximports);

    this.app.start(this.address, this.port);
    LOGGER.info("Servidor web de Noema iniciado correctamente en http://{}:{}", this.address, this.port);
  }

  /**
   * Detiene el servidor web y libera las consolas activas.
   */
  public synchronized void stop() {
    if (this.app != null) {
      this.app.stop();
      this.app = null;
      this.activeConsoles.clear();
      LOGGER.info("Servidor web de Noema detenido.");
    }
  }

  /**
   * Método estático de conveniencia para arranque rápido.
   *
   * @param agent
   * @param address
   * @param port
   * @return
   */
  public static NoemaWebServer startServer(Agent agent, String address, int port) {
    NoemaWebServer server = new NoemaWebServer(agent, address, port);
    server.start();
    return server;
  }

  // =========================================================================
  // HANDLERS DE ENDPOINTS (MÉTODOS DEDICADOS)
  // =========================================================================
  // --- 1. Conversación e Historial ---
  private void handlePostChatMessage(Context ctx) throws Exception {
    String terminalId = ctx.pathParam("terminalId");
    ChatMessageRequest body = ctx.bodyAsClass(ChatMessageRequest.class);

    if (body == null || body.message == null || body.message.trim().isEmpty()) {
      ctx.status(400).json(Map.of("status", "error", "message", "El mensaje no puede estar vacío"));
      return;
    }

    this.agent.putUsersMessage(terminalId, body.message, response -> {
      LOGGER.debug("Mensaje del terminal {} procesado asíncronamente.", terminalId);
    });

    ctx.status(202).json(Map.of(
            "status", "accepted",
            "message", "Message enqueued successfully"
    ));
  }

  private void handleGetHistory(Context ctx) throws Exception {
    String terminalId = ctx.pathParam("terminalId");
    List<Turn> turns = this.agent.getEpisodicMemory().getUnconsolidatedTurns(terminalId);
    List<FlatMessage> flatHistory = new ArrayList<>();

    for (Turn turn : turns) {
      long ts = Timestamp.valueOf(turn.getTimestamp()).getTime();

      if (StringUtils.isNotBlank(turn.getTextUser())) {
        flatHistory.add(new FlatMessage("user-message", turn.getTextUser(), ts));
      }
      if (StringUtils.isNotBlank(turn.getTextModelThinking())) {
        flatHistory.add(new FlatMessage("thinking", turn.getTextModelThinking(), ts));
      }
      if (turn.getToolCall() != null && !turn.getToolCall().trim().isEmpty()) {
        flatHistory.add(new FlatMessage("log", turn.getToolCall(), ts));
      }
      if ("error".equals(turn.getContenttype()) && turn.getToolResult() != null) {
        flatHistory.add(new FlatMessage("error", turn.getToolResult(), ts));
      }
      if (StringUtils.isNotBlank(turn.getTextModel())) {
        flatHistory.add(new FlatMessage("response", turn.getTextModel(), ts));
      }
    }

    ctx.json(flatHistory);
  }

  private void handleSseConsole(SseClient client) {
    String terminalId = client.ctx().pathParam("terminalId");

    SseAgentConsole console = this.activeConsoles.computeIfAbsent(terminalId, id -> {
      SseAgentConsole newConsole = new SseAgentConsole(id);
      this.agent.setConsole(id, newConsole);
      return newConsole;
    });

    console.addClient(client);

    client.onClose(() -> {
      console.removeClient(client);
      if (console.isEmpty()) {
        this.activeConsoles.remove(terminalId);
        this.agent.setConsole(terminalId, null);
      }
    });

    client.keepAlive();
  }

  // --- 2. Configuración Dinámica ---
  private void handleGetConfigUi(Context ctx) throws Exception {
    Path uiPath = this.agent.getPaths().getConfigFolder().resolve("settingsui.json");
    if (!Files.exists(uiPath)) {
      ctx.status(404).json(Map.of("error", "Archivo settingsui.json no encontrado"));
      return;
    }
    String json = Files.readString(uiPath, StandardCharsets.UTF_8);
    ctx.contentType("application/json").result(json);
  }

  private void handleGetDomain(Context ctx) throws Exception {
    String domainName = ctx.pathParam("domainName");
    Path uiPath = this.agent.getPaths().getConfigFolder().resolve("settingsui.json");

    if (!Files.exists(uiPath)) {
      ctx.status(404).json(Map.of("error", "settingsui.json no disponible"));
      return;
    }

    String uiJson = Files.readString(uiPath, StandardCharsets.UTF_8);
    JsonObject uiRoot = JsonParser.parseString(uiJson).getAsJsonObject();
    JsonObject domains = uiRoot.getAsJsonObject("domains");

    if (domains == null || !domains.has(domainName)) {
      ctx.status(404).json(Map.of("error", "Dominio no registrado: " + domainName));
      return;
    }

    String fileName = domains.get(domainName).getAsString();
    Path propPath = this.agent.getPaths().getConfigPath(fileName);

    if (!Files.exists(propPath)) {
      ctx.status(404).json(Map.of("error", "Properties del dominio no encontrado: " + fileName));
      return;
    }

    Properties props = new Properties();
    try (Reader r = Files.newBufferedReader(propPath, StandardCharsets.UTF_8)) {
      props.load(r);
    }

    List<Map<String, String>> result = new ArrayList<>();
    List<String> sortedKeys = new ArrayList<>(props.stringPropertyNames());
    Collections.sort(sortedKeys);

    for (String key : sortedKeys) {
      Map<String, String> entry = new LinkedHashMap<>();
      entry.put("key", key);
      entry.put("label", key.replace('_', ' '));
      entry.put("value", props.getProperty(key));
      result.add(entry);
    }

    ctx.json(result);
  }

  private void handlePostConfigMultivalue(Context ctx) throws Exception {
    JsonArray queryArray = JsonParser.parseString(ctx.body()).getAsJsonArray();
    JsonObject result = new JsonObject();

    Path uiPath = this.agent.getPaths().getConfigFolder().resolve("settingsui.json");
    JsonObject uiRoot = Files.exists(uiPath)
            ? JsonParser.parseString(Files.readString(uiPath, StandardCharsets.UTF_8)).getAsJsonObject()
            : new JsonObject();

    for (JsonElement el : queryArray) {
      JsonObject q = el.getAsJsonObject();
      String qPath = q.get("path").getAsString();
      JsonElement defaultValEl = q.get("defaultValue");

      Object defaultVal = defaultValEl == null ? ""
              : (defaultValEl.isJsonPrimitive() && defaultValEl.getAsJsonPrimitive().isBoolean()
              ? defaultValEl.getAsBoolean() : defaultValEl.getAsString());

      if (q.has("enabledExpression")) {
        Map<String, Object> contextVars = new HashMap<>();
        if (q.has("context")) {
          JsonObject contextJson = q.getAsJsonObject("context");
          for (String k : contextJson.keySet()) {
            contextVars.put(k, contextJson.get(k).getAsString());
          }
        }
        Object val = this.agent.getSettings().eval(
                q.get("enabledExpression").getAsString(), defaultVal, contextVars
        );
        result.addProperty(qPath, Boolean.valueOf(val.toString()));
      } else if (qPath.endsWith("/enabled")) {
        String parentPath = qPath.substring(0, qPath.length() - "/enabled".length());
        String expression = findChildEnabledExpression(uiRoot, parentPath);

        if (expression != null) {
          Map<String, Object> contextVars = new HashMap<>();
          if (q.has("context")) {
            JsonObject contextJson = q.getAsJsonObject("context");
            for (String k : contextJson.keySet()) {
              contextVars.put(k, contextJson.get(k).getAsString());
            }
          }
          Object val = this.agent.getSettings().eval(expression, defaultVal, contextVars);
          result.addProperty(qPath, Boolean.valueOf(val.toString()));
        } else {
          result.addProperty(qPath, true);
        }
      } else {
        String val = this.agent.getSettings().getPropertyAsString(qPath);
        if (val == null) {
          result.addProperty(qPath, defaultVal.toString());
        } else {
          result.addProperty(qPath, val);
        }
      }
    }

    ctx.contentType("application/json").result(new Gson().toJson(result));
  }

  private void handlePostConfigList(Context ctx) throws Exception {
    String path = ctx.pathParam("path");
    JsonArray array = JsonParser.parseString(ctx.body()).getAsJsonArray();
    List<String> values = new ArrayList<>();

    for (JsonElement el : array) {
      values.add(el.getAsString());
    }

    this.agent.getSettings().setProperty(path, values);
    this.agent.getSettings().save();
    triggerAction(path);

    ctx.json(Map.of("status", "success"));
  }

  private void handlePostConfigPath(Context ctx) throws Exception {
    String path = ctx.pathParam("path");
    JsonObject body = JsonParser.parseString(ctx.body()).getAsJsonObject();

    if (!body.has("value")) {
      ctx.status(400).json(Map.of("error", "Falta el campo 'value'"));
      return;
    }

    JsonElement valEl = body.get("value");

    int lastSlash = path.lastIndexOf('/');
    if (lastSlash != -1) {
      String parentPath = path.substring(0, lastSlash);
      String childKey = path.substring(lastSlash + 1);
      AgentSettingsItem parentItem = this.agent.getSettings().getProperty(parentPath);

      if (parentItem instanceof AgentSettingsCheckedList) {
        boolean isChecked = valEl.getAsBoolean();
        this.agent.getSettings().setChecked(parentPath, childKey, isChecked);
        this.agent.getSettings().save();
        triggerAction(parentPath);
        ctx.json(Map.of("status", "success"));
        return;
      }
    }

    this.agent.getSettings().setProperty(path, valEl.getAsString());
    this.agent.getSettings().save();
    triggerAction(path);

    ctx.json(Map.of("status", "success"));
  }

  private void handleGetConfigPath(Context ctx) throws Exception {
    String path = ctx.pathParam("path");
    AgentSettingsItem item = this.agent.getSettings().getProperty(path);

    if (item == null) {
      ctx.status(404).json(Map.of("error", "Propiedad no encontrada: " + path));
      return;
    }

    if (item instanceof AgentSettingsGroup) {
      ctx.contentType("application/json").result(new Gson().toJson(item));
    } else if (item instanceof AgentSettingsString stringItem) {
      ctx.json(Map.of("value", stringItem.getValue()));
    } else if (item instanceof AgentSettingsPaths pathsItem) {
      List<String> rawPaths = pathsItem.getValues().stream().map(Path::toString).toList();
      ctx.json(rawPaths);
    } else if (item instanceof AgentSettingsCheckedList checkedList) {
      ctx.json(checkedList.getItems());
    } else {
      ctx.json(Map.of("value", item.toString()));
    }
  }

  // --- 3. Exploración de Sistema de Archivos ---
  private void handleGetDirectories(Context ctx) throws Exception {
    String pathParam = ctx.queryParam("path");
    Path target;

    if (pathParam == null || pathParam.trim().isEmpty()) {
      // Prioridad: 1º Workspace, 2º user.home, 3º Directorio actual (.)
      if (this.agent.getPaths() != null && this.agent.getPaths().getWorkspaceFolder() != null) {
        target = this.agent.getPaths().getWorkspaceFolder().toAbsolutePath().normalize();
      } else {
        String userHome = System.getProperty("user.home");
        target = (userHome != null && !userHome.isBlank())
                ? Path.of(userHome).toAbsolutePath().normalize()
                : Path.of(".").toAbsolutePath().normalize();
      }
    } else {
      target = Path.of(pathParam.trim()).toAbsolutePath().normalize();
    }

    if (!Files.exists(target) || !Files.isDirectory(target)) {
      ctx.status(404).json(Map.of("error", "Directorio no encontrado: " + target));
      return;
    }

    Path parent = target.getParent();
    List<Map<String, String>> directories = new ArrayList<>();

    if (Files.isReadable(target)) {
      try (var stream = Files.list(target)) {
        directories = stream
                .filter(Files::isDirectory)
                .filter(p -> {
                  try {
                    return !Files.isHidden(p);
                  } catch (Exception e) {
                    return true;
                  }
                })
                .sorted(Comparator.comparing(p -> p.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                .map(p -> {
                  Map<String, String> item = new LinkedHashMap<>();
                  item.put("name", p.getFileName().toString());
                  item.put("path", p.toString());
                  return item;
                })
                .toList();
      } catch (Exception e) {
        LOGGER.warn("No se pudieron listar directorios en: {}", target, e);
      }
    }

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("currentPath", target.toString());
    result.put("parentPath", parent != null ? parent.toString() : null);
    result.put("canGoUp", parent != null);
    result.put("directories", directories);

    ctx.json(result);
  }

  // --- 4. Ejecución de Acciones del Agente ---
  private void handlePostAction(Context ctx) throws Exception {
    String actionName = ctx.pathParam("actionName");
    if (StringUtils.isBlank(actionName)) {
      ctx.status(400).json(Map.of("status", "error", "message", "Nombre de acción no especificado"));
      return;
    }

    try {
      boolean success = this.agent.getActions().call(actionName.trim(), this.agent.getSettings());
      if (success) {
        ctx.json(Map.of("status", "success", "action", actionName));
      } else {
        ctx.status(500).json(Map.of(
                "status", "error",
                "message", "La acción '" + actionName + "' devolvió un estado no exitoso o no se encontró"
        ));
      }
    } catch (Exception e) {
      LOGGER.error("Error ejecutando acción '{}' desde la API web", actionName, e);
      ctx.status(500).json(Map.of("status", "error", "message", e.getMessage() != null ? e.getMessage() : "Error interno"));
    }
  }

  // --- 5. Gestión de Contenido de Ficheros (var:/ y absolutas) ---
  private void handleGetFileContent(Context ctx) throws Exception {
    String fileParam = ctx.queryParam("file");
    if (fileParam == null || fileParam.isBlank()) {
      ctx.status(400).json(Map.of("error", "Parámetro 'file' obligatorio"));
      return;
    }

    Path target = resolveFilePath(fileParam);
    if (target == null || !Files.exists(target)) {
      ctx.status(404).json(Map.of("error", "Fichero no encontrado: " + fileParam));
      return;
    }

    if (Files.isDirectory(target)) {
      ctx.status(400).json(Map.of("error", "La ruta especificada es un directorio, no un fichero: " + fileParam));
      return;
    }

    try {
      String content = Files.readString(target, StandardCharsets.UTF_8);
      Map<String, String> response = new LinkedHashMap<>();
      response.put("file", fileParam);
      response.put("content", content);
      ctx.json(response);
    } catch (Exception e) {
      LOGGER.error("Error leyendo contenido del fichero: {}", target, e);
      ctx.status(500).json(Map.of("error", "Error al leer el fichero: " + e.getMessage()));
    }
  }

  private void handlePostFileContent(Context ctx) throws Exception {
    FileContentRequest body = ctx.bodyAsClass(FileContentRequest.class);
    if (body == null || body.file == null || body.file.isBlank()) {
      ctx.status(400).json(Map.of("error", "El campo 'file' es obligatorio"));
      return;
    }

    Path target = resolveFilePath(body.file);
    if (target == null) {
      ctx.status(400).json(Map.of("error", "Ruta de fichero inválida: " + body.file));
      return;
    }

    try {
      if (target.getParent() != null) {
        Files.createDirectories(target.getParent());
      }
      String contentToWrite = body.content != null ? body.content : "";
      Files.writeString(target, contentToWrite, StandardCharsets.UTF_8);

      ctx.json(Map.of("status", "success", "file", body.file));
    } catch (Exception e) {
      LOGGER.error("Error escribiendo fichero: {}", target, e);
      ctx.status(500).json(Map.of("error", "Error al guardar el fichero: " + e.getMessage()));
    }
  }

  private Path resolveFilePath(String rawFile) {
    if (rawFile == null || rawFile.isBlank()) {
      return null;
    }
    String trimmed = rawFile.trim();
    if (trimmed.startsWith("var:/")) {
      String sub = trimmed.substring("var:/".length());
      while (sub.startsWith("/")) {
        sub = sub.substring(1);
      }
      if (this.agent.getPaths() != null) {
        // getAgentPath comprueba primero en el workspace local (.noema-agent)
        // y si no existe, busca en la configuración global (~/.config/noema-agent)
        Path resolved = this.agent.getPaths().getAgentPath(sub);
        if (resolved != null) {
          return resolved.normalize().toAbsolutePath();
        }
      }
      return Path.of(sub).normalize().toAbsolutePath();
    } else {
      return Path.of(trimmed).normalize().toAbsolutePath();
    }
  }

  // =========================================================================
  // MÉTODOS AUXILIARES E INTERNOS
  // =========================================================================
  private void triggerAction(String variablePath) {
    try {
      Path uiPath = this.agent.getPaths().getConfigFolder().resolve("settingsui.json");
      if (Files.exists(uiPath)) {
        JsonObject uiRoot = JsonParser.parseString(Files.readString(uiPath, StandardCharsets.UTF_8)).getAsJsonObject();
        String actionName = findActionName(uiRoot, variablePath);
        if (actionName != null && !actionName.trim().isEmpty()) {
          LOGGER.debug("Disparando acción lifecycle de Noema: {}", actionName);
          this.agent.getActions().call(actionName, this.agent.getSettings());
        }
      }
    } catch (Exception e) {
      LOGGER.warn("No se pudo ejecutar la acción asociada al path: " + variablePath, e);
    }
  }

  private static String findActionName(JsonElement element, String variableName) {
    if (element.isJsonObject()) {
      JsonObject obj = element.getAsJsonObject();
      if (obj.has("variableName") && obj.get("variableName").getAsString().equals(variableName)) {
        if (obj.has("actionName")) {
          return obj.get("actionName").getAsString();
        }
      }
      if (obj.has("childs")) {
        String action = findActionName(obj.get("childs"), variableName);
        if (action != null) {
          return action;
        }
      }
    } else if (element.isJsonArray()) {
      for (JsonElement child : element.getAsJsonArray()) {
        String action = findActionName(child, variableName);
        if (action != null) {
          return action;
        }
      }
    }
    return null;
  }

  private static String findChildEnabledExpression(JsonElement element, String variableName) {
    if (element.isJsonObject()) {
      JsonObject obj = element.getAsJsonObject();
      if (obj.has("variableName") && obj.get("variableName").getAsString().equals(variableName)) {
        if (obj.has("childEnabled")) {
          return obj.get("childEnabled").getAsString();
        }
      }
      if (obj.has("childs")) {
        String exp = findChildEnabledExpression(obj.get("childs"), variableName);
        if (exp != null) {
          return exp;
        }
      }
    } else if (element.isJsonArray()) {
      for (JsonElement child : element.getAsJsonArray()) {
        String exp = findChildEnabledExpression(child, variableName);
        if (exp != null) {
          return exp;
        }
      }
    }
    return null;
  }

  // =========================================================================
  // DTOs Y CONSOLA SSE
  // =========================================================================
  public static class ChatMessageRequest {

    public String message;
  }

  public static class FileContentRequest {

    public String file;
    public String content;
  }

  public static class FlatMessage {

    public String type;
    public String content;
    public long timestamp;

    public FlatMessage(String type, String content, long timestamp) {
      this.type = type;
      this.content = content;
      this.timestamp = timestamp;
    }
  }

  public static class SseAgentConsole implements AgentConsole {

    private final String subchannel;
    private final List<SseClient> clients = new CopyOnWriteArrayList<>();
    private final BufferedOutput bufferedResponse = new BufferedOutput(
            (String t) -> {
              broadcast("response", t);
            },
            500
    );
    private final BufferedOutput bufferedReasoning = new BufferedOutput(
            (String t) -> {
              broadcast("thinking", t);
            },
            500
    );
    private boolean streamingUsed;

    @Override
    public boolean streamingUsed() {
      return this.streamingUsed;
    }

    @Override
    public void setStreamingUsed(boolean used) {
      this.streamingUsed = used;
    }

    private class BufferedOutput {

      private final Consumer<String> output;
      private final StringBuffer buffer;
      private boolean active;
      private final int delayms;
      private final ScheduledExecutorService scheduler;
      private ScheduledFuture<?> task;

      public BufferedOutput(Consumer<String> output, int delayms) {
        this.output = output;
        this.buffer = new StringBuffer();
        this.delayms = delayms;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(
                Thread.ofPlatform()
                        .daemon(true)
                        .name("noema-buffer-stream-" + subchannel)
                        .factory()
        );
        this.task = null;
        this.active = false;
      }

      public synchronized void add(String s) {
        boolean startTimer = this.buffer.length() == 0;
        this.active = true;
        this.buffer.append(s);
        if (startTimer && this.task == null) {
          this.task = this.scheduler.schedule(this::flush, delayms, TimeUnit.MILLISECONDS);
        }
      }

      private synchronized void flush() {
        String s = buffer.toString();
        if (StringUtils.isNotBlank(s)) {
          try {
            output.accept(s);
          } catch (Exception ex) {
            // Do nothing
          }
        }
        buffer.setLength(0);
        this.task = null;
      }

      public synchronized boolean isActive() {
        return this.active;
      }

      public synchronized void endblock() {
        if (this.task != null) {
          this.task.cancel(false);
        }
        if (this.buffer.length() > 0) {
          String s = buffer.toString();
          if (StringUtils.isNotBlank(s)) {
            try {
              output.accept(s);
            } catch (Exception ex) {
              // Do nothing
            }
          }
          buffer.setLength(0);
        }
        this.active = false;
      }

    }

    public SseAgentConsole(String subchannel) {
      this.subchannel = subchannel;
    }

    public void addClient(SseClient client) {
      this.clients.add(client);
    }

    public void removeClient(SseClient client) {
      this.clients.remove(client);
    }

    public boolean isEmpty() {
      return this.clients.isEmpty();
    }

    private void broadcast(String eventType, String content) {
      JsonObject json = new JsonObject();
      json.addProperty("content", content);
      json.addProperty("timestamp", System.currentTimeMillis());
      String data = new Gson().toJson(json);

      for (SseClient client : clients) {
        try {
          client.sendEvent(eventType, data);
        } catch (Exception e) {
          clients.remove(client);
        }
      }
    }

    @Override
    public boolean confirm(String message) {
      return true;
    }

    @Override
    public void printSystemError(String message) {
      broadcast("error", message);
    }

    @Override
    public void printSystemLog(String message) {
      broadcast("log", message);
    }

    @Override
    public void printSystemLog(String message, Format format) {
      broadcast("log", message);
    }

    @Override
    public void printUserMessage(String message) {
      // Los mensajes de usuario son manejados localmente en la UI
    }

    @Override
    public void printModelResponse(String message) {
      if (this.streamingUsed()) {
        bufferedResponse.endblock();
      } else {
        broadcast("response", message);
      }
    }

    @Override
    public void printModelReasoning(String message) {
      if (this.streamingUsed()) {
        bufferedReasoning.endblock();
      } else {
        broadcast("thinking", message);
      }
    }

    @Override
    public void streamingFinished() {
      if (this.streamingUsed()) {
        this.bufferedReasoning.endblock();
        this.bufferedResponse.endblock();
        broadcast("stream_end", "");
      }
    }

    @Override
    public void StreamReasoning(String s) {
      this.bufferedReasoning.add(s);
    }

    @Override
    public void StreamResponse(String s) {
      this.bufferedResponse.add(s);
    }

  }

  private void handleGetContextXml(Context ctx) throws Exception {
    String terminalId = ctx.pathParam("terminalId");
    String json = this.agent.getProjectedMemoryAsJson(terminalId);

    if (StringUtils.isBlank(json)) {
      ctx.status(404).json(Map.of("error", "No hay contexto proyectado disponible para el terminal: " + terminalId));
      return;
    }

    try (StringReader reader = new StringReader(json); StringWriter writer = new StringWriter()) {
      // Usamos "/css/conversation.xsl" para que el navegador lo renderice nativamente
      // si alguien abre la URL del XML directamente en una pestaña
      ConversationJsonToXml.json2xml(reader, writer, "/css/conversation.xsl");

      byte[] payload = writer.toString().getBytes(StandardCharsets.UTF_8);
      ctx.contentType("application/xml; charset=UTF-8").result(payload);
    } catch (Exception e) {
      LOGGER.error("Error transformando contexto a XML para el terminal '{}'", terminalId, e);
      ctx.status(500).json(Map.of("error", "Fallo al transformar contexto a XML: " + e.getMessage()));
    }
  }

  /**
   * Devuelve la estructura del sistema de archivos por niveles para el
   * explorador del editor web.
   *
   * Sin parametro 'path': lista de raices virtuales. Con parametro 'path':
   * hijos directos de esa carpeta.
   */
  private void handleGetFilesTree(Context ctx) throws Exception {
    String pathParam = ctx.queryParam("path");

    // Caso raiz: raices virtuales
    if (pathParam == null || pathParam.isBlank()) {
      List<Map<String, String>> roots = new ArrayList<>();
      Map<String, Path> virtualRoots = getFilesystemRootNodes(null);
      for (Map.Entry<String, Path> entry : virtualRoots.entrySet()) {
        roots.add(treeEntry(entry.getKey(), entry.getValue(), true));
      }
      ctx.json(roots);
      return;
    }

    // Caso subcarpeta: hijos directos
    Path target;
    try {
      target = Path.of(pathParam.trim()).toAbsolutePath().normalize();
    } catch (Exception e) {
      ctx.status(400).json(Map.of("error", "Ruta invalida: " + pathParam));
      return;
    }

    if (!Files.exists(target) || !Files.isDirectory(target)) {
      ctx.status(404).json(Map.of("error", "Directorio no encontrado: " + pathParam));
      return;
    }

    List<Map<String, String>> children = new ArrayList<>();
    try (var stream = Files.list(target)) {
      List<Path> sorted = stream
              .filter(p -> !isNoiseEntry(p))
              .sorted(Comparator
                      .comparing((Path p) -> !Files.isDirectory(p))
                      .thenComparing(p -> p.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
              .toList();
      for (Path child : sorted) {
        children.add(treeEntry(child.getFileName().toString(), child, Files.isDirectory(child)));
      }
    } catch (Exception e) {
      LOGGER.warn("No se pudo listar el directorio: {}", target, e);
    }

    ctx.json(children);
  }

  /**
   * Construye una entrada {name, path, type} para el arbol web.
   */
  private Map<String, String> treeEntry(String name, Path path, boolean isDirectory) {
    Map<String, String> entry = new LinkedHashMap<>();
    entry.put("name", name);
    entry.put("path", path.toAbsolutePath().normalize().toString());
    entry.put("type", isDirectory ? "directory" : "file");
    return entry;
  }

  /**
   * Filtro de higiene: oculta metadatos de Git, respaldos de JavaRCS y el
   * almacen interno de Noema (.noema-agent/var).
   */
  private boolean isNoiseEntry(Path p) {
    String name = p.getFileName().toString();
    if (".git".equals(name) || name.endsWith(",jv")) {
      return true;
    }
    String normalized = p.toString().replace('\\', '/');
    return normalized.contains("/.noema-agent/var/") || normalized.endsWith("/.noema-agent/var");
  }

  /**
   *
   * Devuelve las raices virtuales del explorador. La clave del Map es el nombre
   * visible en el arbol; el valor, la ruta real. Orden: primero el workspace,
   * luego las rutas externas permitidas.
   */
  private Map<String, Path> getFilesystemRootNodes(@SuppressWarnings("unused") String terminalId) {
    Map<String, Path> roots = new LinkedHashMap<>();
    Map<String, Integer> names = new HashMap<>();
    AgentAccessControl accessControl = this.agent.getAccessControl();

    for (Path path : accessControl.getAllowedPaths()) {
      String name = path.toFile().getName();
      Object x = names.get(name);
      if (x == null) {
        names.put(name, 1);
      } else {
        names.put(name, (int) x + 1);
      }
    }

    for (Path path : accessControl.getAllowedPaths()) {
      path = path.toAbsolutePath().normalize();
      String name = path.toFile().getName();
      int n = (int) names.get(name);
      if (n == 1) {
        roots.put(name, path);
      } else {
        roots.put(path.toString(), path);
      }
    }
    return roots;
  }

  /**
   * Recibe el estado del editor (push desde el cliente). El estado se mantiene
   * por terminalId en una unica instancia que se va mutando en cada llamada:
   * los campos presentes en el JSON se aplican, los ausentes quedan a null (o
   * lista vacia en el caso de openFiles).
   *
   * Contrato del payload (lo genera editor-state.js en el cliente):
   * <pre>
   * {
   *  "terminalId": "default",
   *  "openFiles": [
   *      {
   *        "path": "/ruta/absoluta/A.java",
   *        "dirty": true
   *      },
   *      ...
   *  ],
   *  "activeFile": "/ruta/absoluta/A.java",
   *  "cursorLine": 45,
   *  "selectionStartLine": 45,
   *  "selectionEndLine": 58
   * }
   * </pre> Devuelve 200 {} siempre que el JSON sea valido.
   */
  private void handlePostEditorState(Context ctx) throws Exception {
    JsonObject body;
    try {
      body = JsonParser.parseString(ctx.body()).getAsJsonObject();
    } catch (Exception e) {
      ctx.status(400).json(Map.of("error", "JSON invalido en el cuerpo de la peticion"));
      return;
    }

    String terminalId = jsonStringOrDefault(body, "terminalId", "default");

    EditorState state = this.editorStates.computeIfAbsent(terminalId, id -> {
      EditorState s = new EditorState();
      s.setTerminalId(id);
      return s;
    });

    // Actualizacion completa: la instancia se reutiliza, todos los campos
    // se sustituyen por lo que venga (o por null si no viene).
    state.setActiveFile(jsonStringOrNull(body, "activeFile"));
    state.setCursorLine(jsonIntegerOrNull(body, "cursorLine"));
    state.setSelectionStartLine(jsonIntegerOrNull(body, "selectionStartLine"));
    state.setSelectionEndLine(jsonIntegerOrNull(body, "selectionEndLine"));
    state.setOpenFiles(parseOpenFiles(body));

    ctx.json(Map.of());
  }

  /**
   * Construye la lista de ficheros abiertos a partir del JSON. Devuelve lista
   * vacia si el campo no existe, no es un array, o el array esta vacio.
   */
  private static List<EditorState.OpenFile> parseOpenFiles(JsonObject body) {
    List<EditorState.OpenFile> openFiles = new ArrayList<>();
    JsonElement arr = body.get("openFiles");
    if (arr == null || !arr.isJsonArray()) {
      return openFiles;
    }
    for (JsonElement el : arr.getAsJsonArray()) {
      if (!el.isJsonObject()) {
        continue;
      }
      JsonObject o = el.getAsJsonObject();
      EditorState.OpenFile of = new EditorState.OpenFile();
      of.setPath(jsonStringOrNull(o, "path"));
      JsonElement dirty = o.get("dirty");
      of.setDirty(dirty != null && !dirty.isJsonNull() && dirty.getAsBoolean());
      openFiles.add(of);
    }
    return openFiles;
  }

  /**
   * Devuelve el valor String de una clave del JsonObject, o null si el valor
   * esta ausente o es JSON null.
   */
  private static String jsonStringOrNull(JsonObject obj, String key) {
    JsonElement el = obj.get(key);
    return (el == null || el.isJsonNull()) ? null : el.getAsString();
  }

  /**
   * Devuelve el valor String de una clave del JsonObject, o el valor por
   * defecto si el valor esta ausente, es JSON null o esta en blanco.
   */
  private static String jsonStringOrDefault(JsonObject obj, String key, String defaultValue) {
    String value = jsonStringOrNull(obj, key);
    return (value == null || value.isBlank()) ? defaultValue : value;
  }

  /**
   * Devuelve el valor Integer de una clave del JsonObject, o null si el valor
   * esta ausente o es JSON null.
   */
  private static Integer jsonIntegerOrNull(JsonObject obj, String key) {
    JsonElement el = obj.get(key);
    return (el == null || el.isJsonNull()) ? null : el.getAsInt();
  }

  /**
   * Devuelve diagnosticos (errores, warnings) para un fichero concreto.
   *
   * Contrato de la respuesta (consumido por el frontend, alineado con
   * session.setAnnotations de Ace; row y column son 0-indexados):
   * <pre>
   * {
   *  "annotations": [
   *    { "row": 12, "column": 8, "text": "...", "type": "error" },
   *    { "row": 20, "column": 0, "text": "...", "type": "warning" }
   *  ]
   * }
   * </pre>
   *
   * Parametros de entrada: { "file": "/ruta/absoluta/A.java" }
   */
  private void handlePostEditorDiagnostics(Context ctx) throws Exception {
    JsonObject body;
    try {
      body = JsonParser.parseString(ctx.body()).getAsJsonObject();
    } catch (Exception e) {
      ctx.status(400).json(Map.of("error", "JSON invalido en el cuerpo de la peticion"));
      return;
    }
    String file = body.has("file") ? body.get("file").getAsString() : null;
    if (file == null || file.isBlank()) {
      ctx.status(400).json(Map.of("error", "El campo 'file' es obligatorio"));
      return;
    }

    McpServiceImpl serviceMCP = (McpServiceImpl) this.agent.getService(McpServiceImpl.NAME);
    if (serviceMCP == null) {
      ctx.status(400).json(Map.of("error", "Service MCP not found"));
      return;
    }
    AgentTool tool = serviceMCP.getFirstTool("aide", "lsp_apply_code_action");
    if (tool == null) {
      ctx.status(400).json(Map.of("error", "Tool lsp_apply_code_action not found"));
      return;
    }
    String projectRoot = findProjectFolder(file);
    String toolArgs = String.format("""
{
  "root": "%s",
  "file": "%s"
}    
""", projectRoot, file);
    String resp = tool.execute(toolArgs);

    // TODO: sustituir por procesar el resp y construir la respuesta
    JsonArray annotations = new JsonArray();

    JsonObject a1 = new JsonObject();
    a1.addProperty("row", 12);
    a1.addProperty("column", 8);
    a1.addProperty("text", "Simbolo no encontrado (dummy)");
    a1.addProperty("type", "error");
    annotations.add(a1);

    JsonObject a2 = new JsonObject();
    a2.addProperty("row", 20);
    a2.addProperty("column", 0);
    a2.addProperty("text", "Variable no usada (dummy)");
    a2.addProperty("type", "warning");
    annotations.add(a2);

    JsonObject result = new JsonObject();
    result.add("annotations", annotations);
    ctx.contentType("application/json").result(new Gson().toJson(result));
  }

  /**
   * Formatea el contenido recibido en el cuerpo de la peticion.
   *
   * Request: POST /api/editor/format?ext=java Content-Type: text/plain;
   * charset=UTF-8 Body: contenido crudo del buffer del editor. Response:
   * Content-Type: text/plain; charset=UTF-8 Body: contenido formateado.
   *
   */
  private void handlePostEditorFormat(Context ctx) throws Exception {
    String content = ctx.body();
    content = this.formatJavaCode(content);
    ctx.contentType("text/plain; charset=UTF-8").result(content);
  }

  /**
   * Ajusta/organiza los imports del fichero indicado, en disco.
   *
   * Parametros de entrada:
   * <pre>
   * {
   *  "file": "/ruta/absoluta/A.java"
   * }
   * </pre>
   *
   * Respuesta:
   * <pre>
   * {
   *  "status": "ok"
   * }
   * </pre>
   *
   */
  private void handlePostEditorFiximports(Context ctx) throws Exception {
    JsonObject body;
    try {
      body = JsonParser.parseString(ctx.body()).getAsJsonObject();
    } catch (Exception e) {
      ctx.status(400).json(Map.of("error", "JSON invalido en el cuerpo de la peticion"));
      return;
    }
    String file = body.has("file") ? body.get("file").getAsString() : null;
    if (file == null || file.isBlank()) {
      ctx.status(400).json(Map.of("error", "El campo 'file' es obligatorio"));
      return;
    }

    McpServiceImpl serviceMCP = (McpServiceImpl) this.agent.getService(McpServiceImpl.NAME);
    if (serviceMCP == null) {
      ctx.status(400).json(Map.of("error", "Service MCP not found"));
      return;
    }
    AgentTool tool = serviceMCP.getFirstTool("aide", "lsp_apply_code_action");
    if (tool == null) {
      ctx.status(400).json(Map.of("error", "Tool lsp_apply_code_action not found"));
      return;
    }
    String projectRoot = findProjectFolder(file);
    String toolArgs = String.format("""
{
  "end_column": null,
  "end_line": null,
  "kind": "source.organizeImports",
  "root": "%s",
  "title": null,
  "file": "%s",
  "column": 0,
  "line": 0
}                      
""", projectRoot, file);
    String resp = tool.execute(toolArgs);

    ctx.contentType("application/json").result(resp);
  }

  private String findProjectFolder(String filename) {
    if (filename == null || filename.isBlank()) {
      return null;
    }

    // Normalizamos la ruta para trabajar con una ruta absoluta y sin . o ..
    Path path = Paths.get(filename).toAbsolutePath().normalize();

    // Si es un directorio, empezamos por él mismo.
    // Si es un archivo (o no existe), empezamos por su carpeta padre.
    Path dir = Files.isDirectory(path) ? path : path.getParent();

    // Subimos por el árbol de directorios
    while (dir != null) {
      Path pom = dir.resolve("pom.xml");
      if (Files.isRegularFile(pom)) {
        return dir.toString(); // Carpeta donde se encontró pom.xml
      }
      dir = dir.getParent();
    }

    return null; // No se encontró ningún pom.xml en los ancestros
  }

  /**
   * Devuelve el ultimo estado conocido del editor para el terminal indicado, o
   * null si no hay ninguno. Uso interno del agente.
   * @param terminalId
   * @return 
   */
  public EditorState getEditorState(String terminalId) {
    return this.editorStates.get(terminalId);
  }

//   private String formatJavaCode(String sourceCode) throws FormatterException {
//        // Cargar la implementación del servicio
//        FormatterService formatterService = ServiceLoader.load(FormatterService.class)
//          .findFirst()
//          .orElseThrow(
//              () -> new IllegalStateException(
//                "No se encontró una implementación de FormatterService en el classpath."
//              )
//          );
//
//        return formatterService.formatSourceReflowStringsAndFixImports(sourceCode);
//    }  
  
  private String formatJavaCode(String sourceCode) throws FormatterException {
    // Crear el formateador con el estilo de Palantir
    Formatter formatter = Formatter.createFormatter(
            JavaFormatterOptions.builder()
                    .style(JavaFormatterOptions.Style.PALANTIR)
                    .build()
    );

    // Definir el rango a formatear: todo el String
    ImmutableList<Range<Integer>> ranges = ImmutableList.of(
            Range.closedOpen(0, sourceCode.length())
    );

    // Formatear y devolver el resultado
    return formatter.formatSource(sourceCode, ranges);
  }

}
