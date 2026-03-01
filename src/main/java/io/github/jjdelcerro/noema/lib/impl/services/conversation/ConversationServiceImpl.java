package io.github.jjdelcerro.noema.lib.impl.services.conversation;

import io.github.jjdelcerro.noema.lib.services.conversarion.ConversationService;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.output.Response;
import io.github.jjdelcerro.noema.lib.AbstractAgentAction;
import io.github.jjdelcerro.noema.lib.Agent;
import static io.github.jjdelcerro.noema.lib.AgentActions.CHANGE_CONVERSATION_MODEL;
import static io.github.jjdelcerro.noema.lib.AgentActions.CHANGE_CONVERSATION_PROVIDER;
import io.github.jjdelcerro.noema.lib.impl.services.memory.tools.LookupTurnTool;
import io.github.jjdelcerro.noema.lib.impl.services.memory.tools.SearchFullHistoryTool;
import io.github.jjdelcerro.noema.lib.persistence.CheckPoint;
import io.github.jjdelcerro.noema.lib.persistence.SourceOfTruth;
import io.github.jjdelcerro.noema.lib.persistence.Turn;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.commons.lang3.StringUtils;
import io.github.jjdelcerro.noema.lib.AgentConsole;
import io.github.jjdelcerro.noema.lib.AgentServiceFactory;
import io.github.jjdelcerro.noema.lib.settings.AgentSettings;
import io.github.jjdelcerro.noema.lib.AgentTool;
import io.github.jjdelcerro.noema.lib.impl.ModelParametersImpl;
import io.github.jjdelcerro.noema.lib.impl.services.conversation.tools.events.PoolEventTool;
import io.github.jjdelcerro.noema.lib.impl.services.conversation.tools.file.FileExtractTextTool;
import io.github.jjdelcerro.noema.lib.impl.services.conversation.tools.file.FileFindTool;
import io.github.jjdelcerro.noema.lib.impl.services.conversation.tools.file.FileGrepTool;
import io.github.jjdelcerro.noema.lib.impl.services.conversation.tools.file.FileMkdirTool;
import io.github.jjdelcerro.noema.lib.impl.services.conversation.tools.file.FilePatchTool;
import io.github.jjdelcerro.noema.lib.impl.services.conversation.tools.file.FileReadTool;
import io.github.jjdelcerro.noema.lib.impl.services.conversation.tools.file.FileRecoveryTool;
import io.github.jjdelcerro.noema.lib.impl.services.conversation.tools.file.FileSearchAndReplaceTool;
import io.github.jjdelcerro.noema.lib.impl.services.conversation.tools.file.FileWriteTool;
import io.github.jjdelcerro.noema.lib.impl.services.conversation.tools.file.ShellExecuteTool;
import io.github.jjdelcerro.noema.lib.impl.services.conversation.tools.file.ShellReadOutputTool;
import io.github.jjdelcerro.noema.lib.impl.services.conversation.tools.web.LocationTool;
import io.github.jjdelcerro.noema.lib.impl.services.conversation.tools.web.TimeTool;
import io.github.jjdelcerro.noema.lib.impl.services.conversation.tools.web.WeatherTool;
import io.github.jjdelcerro.noema.lib.impl.services.conversation.tools.web.WebGetTikaTool;
import io.github.jjdelcerro.noema.lib.impl.services.conversation.tools.web.WebSearchTool;
import io.github.jjdelcerro.noema.lib.impl.services.memory.MemoryServiceImpl;
import io.github.jjdelcerro.noema.lib.settings.AgentSettingsCheckedList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Orquestador principal del sistema. Gestiona el bucle de razonamiento, la
 * ejecucion de herramientas y la interaccion con el LLM.
 */
public class ConversationServiceImpl implements ConversationService {

  private static final Logger LOGGER = LoggerFactory.getLogger(ConversationServiceImpl.class);

  private static final int OVERHEAD_IN_ESTIMATE_TOOLS_TOKEN_COUNT = 15;

  private static class AvailableAgentTool {

    private final AgentTool tool;
    private boolean active;

    public AvailableAgentTool(AgentTool tool) {
      this.tool = tool;
      this.active = tool.isAvailableByDefault();
    }
  }

  private final Agent agent;
  private final SourceOfTruth sourceOfTruth;
  private AgentConsole console;
  private final Session session;
  private Agent.ChatModel model;
  private boolean running;

  private CheckPoint activeCheckPoint;

  // Registro de herramientas
  private final Map<String, AvailableAgentTool> availableTools = new LinkedHashMap<>();

  private final Queue<Event> pendingEvents = new ConcurrentLinkedQueue<>();
  private final AtomicBoolean isBusy = new AtomicBoolean(false);
  private final AgentServiceFactory factory;

  public ConversationServiceImpl(AgentServiceFactory factory, Agent agent) {
    this.factory = factory;
    this.agent = agent;
    this.sourceOfTruth = agent.getSourceOfTruth();
    this.console = agent.getConsole();
    this.session = new Session(agent.getPaths().getDataFolder(), agent.getSettings());
    this.running = false;
    try {
      this.activeCheckPoint = sourceOfTruth.getLatestCheckPoint();
    } catch (Exception e) {
      LOGGER.warn("No se ha podido recuperar el ultimo checkpoint", e);
    }
  }

  @Override
  public AgentServiceFactory getFactory() {
    return factory;
  }

  @Override
  public void start() {
    String[] resources = new String[]{
      "prompts/conversation-system.md"
    };
    for (String resPath : resources) {
      this.agent.installResource(resPath);
    }

    this.agent.getActions().addAction(new AbstractAgentAction(this.agent, CHANGE_CONVERSATION_PROVIDER) {
      @Override
      public boolean perform(AgentSettings settings) {
        model = agent.createChatModel("CONVERSATION");
        return true;
      }
    });
    this.agent.getActions().addAction(new AbstractAgentAction(this.agent, CHANGE_CONVERSATION_MODEL) {
      @Override
      public boolean perform(AgentSettings settings) {
        model = agent.createChatModel("CONVERSATION");
        return true;
      }
    });
    this.agent.getActions().addAction(new AbstractAgentAction(this.agent, "COMPACT_CONVERSATION") {
      @Override
      public boolean perform(AgentSettings settings) {
        try {
          performCompaction();
          return true;
        } catch (Exception ex) {
          LOGGER.warn("Can't compact conversation", ex);
          return false;
        }
      }
    });
    this.agent.getActions().addAction(new AbstractAgentAction(this.agent, "REFRESH_CONVERSATION_TOOLS") {
      @Override
      public boolean perform(AgentSettings settings) {
        try {
          refresh_available_tools();
          return true;
        } catch (Exception ex) {
          LOGGER.warn("Can't refresh active tools", ex);
          return false;
        }
      }
    });
    this.model = this.agent.createChatModel("CONVERSATION");
    this.running = true;
  }

  @Override
  public void addTool(AgentTool tool) {
    this.availableTools.put(tool.getName(), new AvailableAgentTool(tool));
  }

  public synchronized void putEvent(String channel, String status, String priority, String eventText) {
    pendingEvents.offer(new Event(channel, status, priority, eventText));

    // Si el agente no está trabajando en un turno, lanzamos el procesador de cola
    if (!isBusy.get()) { //FIXME: falta gestionar correctamente isBusy
      processPendingEvents();
    }
  }

  private synchronized void processPendingEvents() {
    while (!pendingEvents.isEmpty()) {
      Event event = pendingEvents.poll();
      this.console.printSystemLog("Evento: " + event.toString());

      this.session.add(event.getAiMessage());
      this.session.add(event.getResponseMessage());
      String contentType = "tool_execution";
      Turn toolTurn = this.sourceOfTruth.createTurn(
              Timestamp.from(Instant.now()),
              contentType,
              null,
              null,
              null,
              event.getAiMessage().toString(),
              event.getResponseMessage().toString(),
              null
      );
      this.sourceOfTruth.add(toolTurn);
      this.session.consolideTurn(toolTurn);

      String resp = this.executeReasoningLoop(null);
      if (StringUtils.isNotBlank(resp)) {
        this.agent.getConsole().printModelResponse(resp);
      }
    }
  }

  public synchronized String processTurn(String textUser) {
    UserMessage inputMessage = UserMessage.from(textUser);
    this.session.add(inputMessage);
    String value = this.executeReasoningLoop(textUser);
    processPendingEvents();
    return value;
  }

  private synchronized String executeReasoningLoop(String textUser) {
    StringBuilder llmResponse = new StringBuilder();
    try {
      boolean turnFinished = false;

      while (!turnFinished) {
        // Recuperar contexto actualizado (System + Historial Sesion)
        List<ChatMessage> messages = this.session.getContextMessages(this.activeCheckPoint, getBaseSystemPrompt());

        // Llamada al Modelo
        Response<AiMessage> response = model.generate(messages, this.getToolSpecifications());
        AiMessage aiMessage = response.content();

        // Anadir respuesta al historial
        this.session.add(aiMessage);

        if (aiMessage.hasToolExecutionRequests()) {
          // --- MODO HERRAMIENTA ---
          for (ToolExecutionRequest request : aiMessage.toolExecutionRequests()) {
            // FIXME: Estudiar como podriamos interrumpir aqui flujo de ejecucion 
            // de llamadas a herramientas para introducir un evento.
            
            String result = executeToolLogic(request);
            String contentType = "tool_execution";
            if (isMemoryTool(request.name())) {
              contentType = "lookup_turn";
            }
            Turn toolTurn = this.sourceOfTruth.createTurn(
                    Timestamp.from(Instant.now()),
                    contentType,
                    null,
                    null,
                    null,
                    request.toString(),
                    result,
                    null
            );
            this.sourceOfTruth.add(toolTurn);
            this.session.add(ToolExecutionResultMessage.from(request, result));
            this.session.consolideTurn(toolTurn);
            
          }

        } else {
          // --- MODO RESPUESTA FINAL ---
          String aiText = aiMessage.text();
          Turn responseTurn = this.sourceOfTruth.createTurn(
                  Timestamp.from(Instant.now()),
                  "chat",
                  textUser, // Guardamos el user text original en el turno final para referencia historica
                  null,
                  aiText,
                  null,
                  null,
                  null
          );
          llmResponse.append(aiText);

          this.sourceOfTruth.add(responseTurn);
          this.session.consolideTurn(responseTurn);

          turnFinished = true;
        }
      }

      if (this.session.needCompaction()) {
        performCompaction();
      }

    } catch (SQLException e) {
      this.console.printSystemError("Error critico de base de datos en processTurn: " + e.getMessage());
      LOGGER.warn("Error de base de datos procesando turno.", e);
    } catch (Exception e) {
      this.console.printSystemError("Error inesperado en processTurn: " + e.getMessage());
      LOGGER.warn("Error de inesperado procesando turno.", e);
    }
    return llmResponse.toString();
  }

  private String getBaseSystemPrompt() {
    String systemPrompt = agent.getResourceAsString("prompts/conversation-system.md");

    if (systemPrompt.isEmpty()) {
      LOGGER.warn("No se ha podido cargar el prompt del sistema del ConversationService");
      throw new RuntimeException("Can't load system prompt from data folder");
    }
    systemPrompt = StringUtils.replace(systemPrompt, "{NOW}", now());
    systemPrompt = StringUtils.replace(systemPrompt, "{LOOKUPTURN}", LookupTurnTool.NAME);
    systemPrompt = StringUtils.replace(systemPrompt, "{SEARCHFULLHISTORY}", SearchFullHistoryTool.NAME);
    return systemPrompt;
  }

  private String executeToolLogic(ToolExecutionRequest request) {
    String toolName = request.name();
    String args = request.arguments();

    AvailableAgentTool availableTool = availableTools.get(toolName);

    if (availableTool != null && availableTool.tool != null) {
      AgentTool tool = availableTool.tool;
      if (tool.getMode() != AgentTool.MODE_READ) {
        boolean authorized = this.console.confirm(
                String.format("El agente quiere ejecutar la herramienta: %s\nArgumentos: %s\n¿Autorizar?", toolName, args)
        );

        if (!authorized) {
          String msg = String.format("Ejecucion de herramienta '%s' denegada por el usuario.", toolName);
          LOGGER.info(msg);
          this.console.printSystemLog(msg);
          return msg;
        }
      }
      String msg = String.format("Ejecutando herramienta: %s\n    Argumentos: %s", toolName, args);
      LOGGER.info(msg);
      this.console.printSystemLog(msg);
      try {
        return tool.execute(args);
      } catch (Exception e) {
        String msg1 = "Error ejecutando herramienta '" + toolName + "'.";
        LOGGER.info(msg1, e);
        return msg1 + " " + e.getMessage();
      }
    } else {
      String msg = "Herramienta '" + toolName + "' no encontrada.";
      LOGGER.info(msg);
      return msg;
    }
  }

  private boolean isMemoryTool(String toolName) {
    AvailableAgentTool tool = availableTools.get(toolName);
    return tool.tool.getType() == AgentTool.TYPE_MEMORY;
  }

  private void performCompaction() throws SQLException {
    this.console.printSystemLog("Iniciando proceso de compactación de memoria...");

    // 1. Obtener marcas de sesion
    Session.SessionMark mark1 = this.session.getOldestMark();
    Session.SessionMark mark2 = this.session.getCompactMark();

    if (mark1 == null || mark2 == null) {
      String msg = "No hay suficientes datos consolidados para compactar.";
      LOGGER.warn(msg);
      this.console.printSystemLog(msg);
      return;
    }

    // 2. Recuperar turnos de la DB usando el rango de IDs de las marcas
    List<Turn> compactTurns = this.sourceOfTruth.getTurnsByIds(mark1.getTurnId(), mark2.getTurnId());

    if (compactTurns.isEmpty()) {
      String msg = String.format("No se han podido recuperar los turnos a compactar (turns[%s:%s]).", mark1.getTurnId(), mark2.getTurnId());
      LOGGER.warn(msg);
      this.console.printSystemLog(msg);
      return;
    }

    // 3. MemoryManager crea el CheckPoint
    MemoryServiceImpl memory = (MemoryServiceImpl) this.agent.getService(MemoryServiceImpl.NAME);
    CheckPoint newCheckPoint = memory.compact(this.activeCheckPoint, compactTurns);

    // 4. SourceOfTruth persiste
    sourceOfTruth.add(newCheckPoint);

    // 5. Limpieza de Sesion (Borrar mensajes ya compactados)
    this.session.remove(mark1, mark2);

    // 6. Actualizar punteros del Agente
    this.activeCheckPoint = newCheckPoint;

    this.console.printSystemLog("Memoria compactada con éxito. Nuevo CheckPoint ID: " + newCheckPoint.getId());
  }

  public void setConsole(AgentConsole console) {
    this.console = console;
  }

  @Override
  public Agent.ChatModel getModel() {
    return model;
  }

  @Override
  public Agent.ModelParameters getModelParameters(String name) {
    AgentSettings settings = this.agent.getSettings();
    switch (name) {
      case "CONVERSATION":
        return new ModelParametersImpl(
                settings.getPropertyAsString(CONVERSATION_PROVIDER_URL),
                settings.getPropertyAsString(CONVERSATION_PROVIDER_API_KEY),
                settings.getPropertyAsString(CONVERSATION_MODEL_ID),
                0.7
        );
    }
    return null;
  }

  @Override
  public boolean canStart() {
    return this.factory.canStart(agent.getSettings());
  }

  @Override
  public List<AgentTool> getTools() {
    AgentTool[] tools0 = new AgentTool[]{
      new PoolEventTool(this.agent),
      new FileFindTool(this.agent),
      new FileGrepTool(this.agent),
      new FileReadTool(this.agent),
      new FileWriteTool(this.agent),
      new FileSearchAndReplaceTool(this.agent),
      new FilePatchTool(this.agent),
      new FileMkdirTool(this.agent),
      new FileExtractTextTool(this.agent),
      new WebGetTikaTool(this.agent),
      new WeatherTool(this.agent),
      new LocationTool(this.agent),
      new TimeTool(this.agent),
      new ShellExecuteTool(this.agent),
      new ShellReadOutputTool(this.agent),
      new FileRecoveryTool(this.agent)
    };
    List<AgentTool> tools = new ArrayList<>(Arrays.asList(tools0));

    String braveApiKey = this.agent.getSettings().getPropertyAsString(WebSearchTool.BRAVE_SEARCH_API_KEY);
    if (StringUtils.isNotBlank(braveApiKey)) {
      tools.add(new WebSearchTool(this.agent));
    }
    return tools;
  }

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public boolean isRunning() {
    return this.running;
  }

  public static String now() {
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM 'de' yyyy, HH:mm", new Locale("es", "ES"));
    return LocalDateTime.now().format(formatter);
  }

  @Override
  public int estimateToolsTokenCount() {
    if (this.model == null) {
      return 0;
    }
    int n = 0;
    for (ToolSpecification toolSpecification : this.getToolSpecifications()) {
      String s = toolSpecification.toString();
      n += this.model.estimateTokenCount(s) + OVERHEAD_IN_ESTIMATE_TOOLS_TOKEN_COUNT;
    }
    return n;
  }

  @Override
  public int estimateMessagesTokenCount() {
    if (this.model == null) {
      return 0;
    }
    List<ChatMessage> messages = this.session.getContextMessages(this.activeCheckPoint, getBaseSystemPrompt());
    return this.model.estimateTokenCount(messages);
  }

  @Override
  public String getModelName() {
    return this.agent.getSettings().getPropertyAsString(CONVERSATION_MODEL_ID);
  }

  public void showSession() {
    List<ChatMessage> history = this.session.getMessages();

    for (ChatMessage message : history) {
      if (message instanceof UserMessage userMsg) {
        console.printUserMessage(userMsg.singleText());

      } else if (message instanceof AiMessage aiMsg) {
        // 1. Si el modelo pidió ejecutar herramientas, informamos de cada una
        if (aiMsg.hasToolExecutionRequests()) {
          for (ToolExecutionRequest req : aiMsg.toolExecutionRequests()) {
            console.printSystemLog(String.format("Ejecutando herramienta: %s\n    Argumentos: %s",
                    req.name(), req.arguments()));
          }
        }
        // Los ToolExecutionResultMessage y SystemMessage se ignoran 
        // ya que no se presentan en la consola.
        // 2. Si el modelo respondió con texto, lo mostramos
        if (StringUtils.isNotBlank(aiMsg.text())) {
          console.printModelResponse(aiMsg.text());
        }
      }
    }
  }

  private List<ToolSpecification> getToolSpecifications() {
    List<ToolSpecification> toolSpecifications = new ArrayList<>();
    for (AvailableAgentTool availableTool : this.availableTools.values()) {
      if (availableTool.active) {
        toolSpecifications.add(availableTool.tool.getSpecification());
      }
    }
    return toolSpecifications;
  }

  @Override
  public List<AgentTool> getAvailableTools() {
    List<AgentTool> tools = new ArrayList<>();
    for (AvailableAgentTool tool : this.availableTools.values()) {
      tools.add(tool.tool);
    }
    return tools;
  }

  @Override
  public AgentTool getAvailableTool(String name) {
    for (AvailableAgentTool tool : this.availableTools.values()) {
      if (StringUtils.equals(name, tool.tool.getName())) {
        return tool.tool;
      }
    }
    return null;
  }

  @Override
  public boolean isToolActive(String name) {
    return this.availableTools.get(name).active;
  }

  @Override
  public void setToolActive(String name, boolean active) {
    this.availableTools.get(name).active = active;
  }

  /**
   * Sincroniza el estado de activación de las herramientas con lo definido por
   * el usuario en la configuración.
   * Si una herramienta no figura en la configuracion, conserva su estado actual en memoria.
   */
  private void refresh_available_tools() {
    AgentSettingsCheckedList persistedList = agent.getSettings().getPropertyAsCheckedList(ACTIVE_TOOLS);
    if (persistedList == null) {
        return;
    }
    for (AgentSettingsCheckedList.CheckedItem item : persistedList.getItems()) {
        String technicalName = item.getValue();
        // Buscamos si la herramienta referenciada en el JSON está cargada en el servicio
        AvailableAgentTool available = availableTools.get(technicalName);
        if (available != null) {
            // Sincronizamos el estado: lo que diga el usuario manda sobre el valor en memoria
            available.active = item.isChecked();
            LOGGER.debug("Herramienta '{}' sincronizada desde configuración: {}", 
                         technicalName, available.active ? "ACTIVA" : "INACTIVA");
        }
    }
    // Nota: Las herramientas que están en 'availableTools' pero NO en 'persistedList' 
    // mantienen el valor 'active' que recibieron al ser añadidas (isAvailableByDefault).
  }

}
