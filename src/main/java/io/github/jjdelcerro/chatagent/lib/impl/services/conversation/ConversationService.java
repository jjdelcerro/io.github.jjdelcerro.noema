package io.github.jjdelcerro.chatagent.lib.impl.services.conversation;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import io.github.jjdelcerro.chatagent.lib.AbstractAgentAction;
import io.github.jjdelcerro.chatagent.lib.Agent;
import static io.github.jjdelcerro.chatagent.lib.AgentActions.CHANGE_CONVERSATION_MODEL;
import static io.github.jjdelcerro.chatagent.lib.AgentActions.CHANGE_CONVERSATION_PROVIDER;
import io.github.jjdelcerro.chatagent.lib.impl.services.memory.tools.LookupTurnTool;
import io.github.jjdelcerro.chatagent.lib.impl.services.memory.tools.SearchFullHistoryTool;
import io.github.jjdelcerro.chatagent.lib.persistence.CheckPoint;
import io.github.jjdelcerro.chatagent.lib.persistence.SourceOfTruth;
import io.github.jjdelcerro.chatagent.lib.persistence.Turn;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.commons.lang3.StringUtils;
import io.github.jjdelcerro.chatagent.lib.AgentConsole;
import io.github.jjdelcerro.chatagent.lib.AgentService;
import io.github.jjdelcerro.chatagent.lib.AgentServiceFactory;
import io.github.jjdelcerro.chatagent.lib.AgentSettings;
import static io.github.jjdelcerro.chatagent.lib.AgentSettings.BRAVE_SEARCH_API_KEY;
import io.github.jjdelcerro.chatagent.lib.AgentTool;
import io.github.jjdelcerro.chatagent.lib.impl.services.conversation.tools.events.PoolEventTool;
import io.github.jjdelcerro.chatagent.lib.impl.services.conversation.tools.file.FileExtractTextTool;
import io.github.jjdelcerro.chatagent.lib.impl.services.conversation.tools.file.FileFindTool;
import io.github.jjdelcerro.chatagent.lib.impl.services.conversation.tools.file.FileGrepTool;
import io.github.jjdelcerro.chatagent.lib.impl.services.conversation.tools.file.FileMkdirTool;
import io.github.jjdelcerro.chatagent.lib.impl.services.conversation.tools.file.FilePatchTool;
import io.github.jjdelcerro.chatagent.lib.impl.services.conversation.tools.file.FileReadTool;
import io.github.jjdelcerro.chatagent.lib.impl.services.conversation.tools.file.FileSearchAndReplaceTool;
import io.github.jjdelcerro.chatagent.lib.impl.services.conversation.tools.file.FileWriteTool;
import io.github.jjdelcerro.chatagent.lib.impl.services.conversation.tools.web.LocationTool;
import io.github.jjdelcerro.chatagent.lib.impl.services.conversation.tools.web.TimeTool;
import io.github.jjdelcerro.chatagent.lib.impl.services.conversation.tools.web.WeatherTool;
import io.github.jjdelcerro.chatagent.lib.impl.services.conversation.tools.web.WebGetTikaTool;
import io.github.jjdelcerro.chatagent.lib.impl.services.conversation.tools.web.WebSearchTool;
import io.github.jjdelcerro.chatagent.lib.impl.services.memory.MemoryService;
import java.util.Arrays;

/**
 * Orquestador principal del sistema. Gestiona el bucle de razonamiento, la
 * ejecucion de herramientas y la interaccion con el LLM.
 */
public class ConversationService implements AgentService {

  public static final String NAME = "Conversation";

  public static final String CONVERSATION_PROVIDER_URL = "CONVERSATION_PROVIDER_URL";
  public static final String CONVERSATION_PROVIDER_API_KEY = "CONVERSATION_PROVIDER_API_KEY";
  public static final String CONVERSATION_MODEL_ID = "CONVERSATION_MODEL_ID";

  private final Agent agent;
  private final SourceOfTruth sourceOfTruth;
  private AgentConsole console;
  private final Session session;
  private ChatLanguageModel model;
  private boolean running;

  private CheckPoint activeCheckPoint;

  // Registro de herramientas
  private final Map<String, AgentTool> toolDispatcher = new HashMap<>();
  private final List<ToolSpecification> toolSpecifications = new ArrayList<>();

  private final Queue<Event> pendingEvents = new ConcurrentLinkedQueue<>();
  private final AtomicBoolean isBusy = new AtomicBoolean(false);
  private final AgentServiceFactory factory;

  public ConversationService(AgentServiceFactory factory, Agent agent) {
    this.factory = factory;
    this.agent = agent;
    this.sourceOfTruth = agent.getSourceOfTruth();
    this.console = agent.getConsole();
    this.session = new Session(agent.getDataFolder().toPath());
    this.running = false;
    try {
      this.activeCheckPoint = sourceOfTruth.getLatestCheckPoint();
    } catch (Exception e) {
      e.printStackTrace(); // FIXME: log
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
    this.model = this.agent.createChatModel("CONVERSATION");
    this.running = true;
  }

  public void addTool(AgentTool tool) {
    this.toolDispatcher.put(tool.getName(), tool);
    this.toolSpecifications.add(tool.getSpecification());
  }

  public synchronized void putEvent(String channel, String priority, String eventText) {
    pendingEvents.offer(new Event(channel, priority, eventText));

    // Si el agente no está trabajando en un turno, lanzamos el procesador de cola
    if (!isBusy.get()) {
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

      this.executeReasoningLoop(null);
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
        Response<AiMessage> response = model.generate(messages, toolSpecifications);
        AiMessage aiMessage = response.content();

        // Anadir respuesta al historial
        this.session.add(aiMessage);

        if (aiMessage.hasToolExecutionRequests()) {
          // --- MODO HERRAMIENTA ---
          for (ToolExecutionRequest request : aiMessage.toolExecutionRequests()) {
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
      e.printStackTrace(); // FIXME: log
    } catch (Exception e) {
      this.console.printSystemError("Error inesperado en processTurn: " + e.getMessage());
      e.printStackTrace(); // FIXME: log
    }
    return llmResponse.toString();
  }

  private String getBaseSystemPrompt() {
    String systemPrompt = agent.getResourceAsString("prompts/conversation-system.md");

    if (systemPrompt.isEmpty()) {
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

    AgentTool tool = toolDispatcher.get(toolName);

    if (tool != null) {
      if (tool.getMode() != AgentTool.MODE_READ) {
        boolean authorized = this.console.confirm(
                String.format("El agente quiere ejecutar la herramienta: %s\nArgumentos: %s\n¿Autorizar?", toolName, args)
        );

        if (!authorized) {
          this.console.printSystemLog("Ejecución denegada por el usuario.");
          return "Error: User rejected the execution of tool '" + toolName + "'.";
        }
        this.console.printSystemLog("Ejecutando herramienta: " + toolName);
      } else {
        this.console.printSystemLog(String.format("Ejecutando herramienta: %s\n    Argumentos: %s", toolName, args));
      }
      try {
        return tool.execute(args);
      } catch (Exception e) {
        return "Error ejecutando herramienta: " + e.getMessage();
      }
    } else {
      return "Error: Herramienta '" + toolName + "' no encontrada.";
    }
  }

  private boolean isMemoryTool(String toolName) {
    AgentTool tool = toolDispatcher.get(toolName);
    return tool.getType() == AgentTool.TYPE_MEMORY;
  }

  private void performCompaction() throws SQLException {
    this.console.printSystemLog("Iniciando proceso de compactación de memoria...");

    // 1. Obtener marcas de sesion
    Session.SessionMark mark1 = this.session.getOldestMark();
    Session.SessionMark mark2 = this.session.getCompactMark();

    if (mark1 == null || mark2 == null) {
      this.console.printSystemLog("Advertencia: No hay suficientes datos consolidados para compactar.");
      return;
    }

    // 2. Recuperar turnos de la DB usando el rango de IDs de las marcas
    List<Turn> compactTurns = this.sourceOfTruth.getTurnsByIds(mark1.getTurnId(), mark2.getTurnId());

    if (compactTurns.isEmpty()) {
      this.console.printSystemLog("Advertencia: Rango de compactación vacío.");
      return;
    }

    // 3. MemoryManager crea el CheckPoint
    MemoryService memory = (MemoryService) this.agent.getService(MemoryService.NAME);
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
  public Agent.ModelParameters getModelParameters(String name) {
    AgentSettings settings = this.agent.getSettings();
    switch (name) {
      case "CONVERSATION":
        return new Agent.ModelParameters(
                settings.getProperty(CONVERSATION_PROVIDER_URL),
                settings.getProperty(CONVERSATION_PROVIDER_API_KEY),
                settings.getProperty(CONVERSATION_MODEL_ID),
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
      new TimeTool(this.agent)
    };
    List<AgentTool> tools = new ArrayList<>(Arrays.asList(tools0));

    String braveApiKey = this.agent.getSettings().getProperty(BRAVE_SEARCH_API_KEY);
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

}
