package io.github.jjdelcerro.noema.ui.webclient;

import com.google.gson.JsonObject;
import io.github.jjdelcerro.noema.ui.webclient.workspace.editor.tools.EditorOpenFileTool;
import io.github.jjdelcerro.noema.ui.webclient.workspace.editor.EditorState;
import io.github.jjdelcerro.noema.lib.api.Agent;
import io.github.jjdelcerro.noema.lib.api.AgentLocator;
import io.github.jjdelcerro.noema.lib.api.AgentManager;
import io.github.jjdelcerro.noema.lib.api.services.reasoning.ReasoningService;
import io.github.jjdelcerro.noema.lib.impl.utils.AgentUtils;
import io.github.jjdelcerro.noema.ui.webclient.handlers.HandlerGetConfigPath;
import io.github.jjdelcerro.noema.ui.webclient.handlers.HandlerGetConfigUi;
import io.github.jjdelcerro.noema.ui.webclient.handlers.HandlerGetContextXml;
import io.github.jjdelcerro.noema.ui.webclient.handlers.HandlerGetDirectories;
import io.github.jjdelcerro.noema.ui.webclient.handlers.HandlerGetDomain;
import io.github.jjdelcerro.noema.ui.webclient.handlers.HandlerGetFileContent;
import io.github.jjdelcerro.noema.ui.webclient.handlers.HandlerGetFilesTree;
import io.github.jjdelcerro.noema.ui.webclient.handlers.HandlerGetHistory;
import io.github.jjdelcerro.noema.ui.webclient.handlers.HandlerPostAction;
import io.github.jjdelcerro.noema.ui.webclient.handlers.HandlerPostChatMessage;
import io.github.jjdelcerro.noema.ui.webclient.handlers.HandlerPostConfigList;
import io.github.jjdelcerro.noema.ui.webclient.handlers.HandlerPostConfigMultivalue;
import io.github.jjdelcerro.noema.ui.webclient.handlers.HandlerPostConfigPath;
import io.github.jjdelcerro.noema.ui.webclient.handlers.HandlerPostEditorDiagnostics;
import io.github.jjdelcerro.noema.ui.webclient.handlers.HandlerPostEditorFiximports;
import io.github.jjdelcerro.noema.ui.webclient.handlers.HandlerPostEditorFormat;
import io.github.jjdelcerro.noema.ui.webclient.handlers.HandlerPostEditorState;
import io.github.jjdelcerro.noema.ui.webclient.handlers.HandlerPostFileContent;
import io.github.jjdelcerro.noema.ui.webclient.handlers.HandlerPostSetupProject;
import io.github.jjdelcerro.noema.ui.webclient.workspace.editor.EditorAwarenessOperationFactory;
import io.github.jjdelcerro.noema.ui.webclient.workspace.editor.EditorSelection;
import io.github.jjdelcerro.noema.ui.webclient.workspace.editor.tools.EditorGetCurrentSelectionTool;
import io.github.jjdelcerro.noema.ui.webclient.workspace.editor.tools.EditorGetStateTool;
import io.github.jjdelcerro.noema.ui.webclient.workspace.editor.tools.EditorInsertOrReplaceTool;
import io.javalin.Javalin;
import io.javalin.http.sse.SseClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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

    this.agent.set("webserver", this);

    AgentManager manager = AgentLocator.getAgentManager();
    manager.registerProjectedMemoryOperation(new EditorAwarenessOperationFactory());

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
    this.app.post("/api/chat/{terminalId}", new HandlerPostChatMessage(agent));
    this.app.get("/api/chat/{terminalId}/history", new HandlerGetHistory(agent));
    this.app.sse("/api/console/{terminalId}", this::handleSseConsole);
    this.app.get("/api/chat/{terminalId}/context.xml", new HandlerGetContextXml(agent));

    // 2. Configuración dinámica (Rutas específicas antes que las genéricas <path>)
    this.app.get("/api/config/ui", new HandlerGetConfigUi(agent));
    this.app.get("/api/config/domains/{domainName}", new HandlerGetDomain(agent));
    this.app.post("/api/config/multivalue", new HandlerPostConfigMultivalue(agent));
    this.app.post("/api/config/<path>/list", new HandlerPostConfigList(agent));
    this.app.post("/api/config/<path>", new HandlerPostConfigPath(agent));
    this.app.get("/api/config/<path>", new HandlerGetConfigPath(agent));

    // 3. Exploración de Sistema de Archivos
    this.app.get("/api/fs/directories", new HandlerGetDirectories(agent));

    // 4. Ejecución de Acciones del Agente
    this.app.post("/api/actions/{actionName}", new HandlerPostAction(agent));

    // 5. Gestión de Contenido de Ficheros (var:/ y rutas absolutas)
    this.app.get("/api/files/content", new HandlerGetFileContent(agent));
    this.app.post("/api/files/content", new HandlerPostFileContent(agent));
    this.app.get("/api/files/tree", new HandlerGetFilesTree(agent));

    // 6. Editor Web: estado y acciones sobre ficheros
    this.app.post("/api/editor/state", new HandlerPostEditorState(agent));
    this.app.post("/api/editor/diagnostics", new HandlerPostEditorDiagnostics(agent));
    this.app.post("/api/editor/format", new HandlerPostEditorFormat(agent));
    this.app.post("/api/editor/fiximports", new HandlerPostEditorFiximports(agent));

    this.app.post("/api/editor/setupproject", new HandlerPostSetupProject(agent));

    this.app.start(this.address, this.port);

    ReasoningService reasoningService = (ReasoningService) this.agent.getService(ReasoningService.NAME);
    reasoningService.addTool(new EditorOpenFileTool(this.agent));
    reasoningService.addTool(new EditorGetCurrentSelectionTool(this.agent));
    reasoningService.addTool(new EditorGetStateTool(this.agent));
    reasoningService.addTool(new EditorInsertOrReplaceTool(this.agent));

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

  public Map<String, EditorState> getEditorStates() {
    return this.editorStates;
  }

  /**
   * Devuelve el ultimo estado conocido del editor para el terminal indicado, o
   * null si no hay ninguno. Uso interno del agente.
   *
   * @param terminalId
   * @return
   */
  public EditorState getEditorState(String terminalId) {
    return this.editorStates.get(terminalId);
  }

  /**
   * Devuelve la seleccion activa del usuario para un subcanal, o null si no hay
   * estado, no hay fichero activo o no hay seleccion.
   */
  public EditorSelection getCurrentSelection(String subchannel) {
    EditorState state = this.editorStates.get(subchannel);
    if (state == null) {
      return null;
    }
    if (state.getSelectionStartLine() == null || state.getSelectionEndLine() == null) {
      return null;
    }
    EditorSelection selection = new EditorSelection();
    selection.setFile(state.getActiveFile());
    selection.setSelectionStartLine(state.getSelectionStartLine());
    selection.setSelectionEndLine(state.getSelectionEndLine());
    selection.setText(state.getSelectionText());
    return selection;
  }

  /**
   * Ordena al cliente abrir un fichero en el editor (y opcionalmente posicionar
   * el cursor en una linea). Emite un evento SSE 'editor_open' con {file, line}
   * al subcanal indicado.
   *
   * Si el subcanal no tiene consola activa (no hay navegador conectado), se
   * loguea un warning y no se hace nada. El editor simplemente no reacciona.
   */
  public void editorOpenFile(String subchannel, String path, Integer line) {
    SseAgentConsole console = this.activeConsoles.get(subchannel);
    if (console == null) {
      LOGGER.warn("editorOpenFile: no hay consola activa para el subcanal '{}'", subchannel);
      return;
    }
    JsonObject payload = new JsonObject();
    payload.addProperty("file", path);
    if (line != null) {
      payload.addProperty("line", line);
    }
    console.sendEvent("editor_open", payload);
  }

  /**
   * Ordena al cliente aplicar una mutacion al buffer de un fichero abierto:
   * insertar (lineEnd == null) o reemplazar (lineEnd != null) el rango
   * indicado. Emite un evento SSE 'editor_apply' con {lineStart, lineEnd,
   * text}.
   *
   * No se toca el disco: el cliente modifica su buffer y lo marca como sucio.
   * El usuario decide cuando guardar.
   */
  public void editorInsertOrReplace(String subchannel, Integer lineStart, Integer lineEnd, String text) {
    SseAgentConsole console = this.activeConsoles.get(subchannel);
    if (console == null) {
      LOGGER.warn("editorInsertOrReplace: no hay consola activa para el subcanal '{}'", subchannel);
      return;
    }
    JsonObject payload = new JsonObject();
    payload.addProperty("lineStart", lineStart);
    if (lineEnd != null) {
      payload.addProperty("lineEnd", lineEnd);
    }
    if (text != null) {
      payload.addProperty("text", text);
    }
    console.sendEvent("editor_apply", payload);
  }

  /**
   * Notifica al cliente web el resultado de un setup-project por el canal SSE
   * del subcanal indicado. Evento 'editor_setup_done' con {status, message}. Si
   * el subcanal no tiene consola activa (el usuario cerro la pestana), se
   * loguea y no se hace nada.
   */
  public void notifyEditorSetupResult(String subchannel, String status, String message) {
    SseAgentConsole console = this.activeConsoles.get(subchannel);
    if (console == null) {
      LOGGER.warn("notifyEditorSetupResult: no hay consola activa para el subcanal '{}'", subchannel);
      return;
    }
    JsonObject payload = new JsonObject();
    payload.addProperty("status", status);
    payload.addProperty("message", message != null ? message : "");
    console.sendEvent("editor_setup_done", payload);
  }
}
