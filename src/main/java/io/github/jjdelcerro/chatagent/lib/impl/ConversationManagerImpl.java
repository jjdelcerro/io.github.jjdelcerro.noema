package io.github.jjdelcerro.chatagent.lib.impl;

import com.google.gson.Gson;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.output.Response;
import io.github.jjdelcerro.chatagent.lib.impl.tools.memory.LookupTurnTool;
import io.github.jjdelcerro.chatagent.lib.impl.tools.memory.SearchFullHistoryTool;
import io.github.jjdelcerro.chatagent.lib.persistence.CheckPoint;
import io.github.jjdelcerro.chatagent.lib.persistence.SourceOfTruth;
import io.github.jjdelcerro.chatagent.lib.persistence.Turn;
import io.github.jjdelcerro.chatagent.lib.tools.AgenteTool;

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
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.commons.lang3.StringUtils;
import io.github.jjdelcerro.chatagent.lib.AgentConsole;
import io.github.jjdelcerro.chatagent.lib.AgentSettings;
import static io.github.jjdelcerro.chatagent.lib.AgentSettings.CONVERSATION_MODEL_ID;
import static io.github.jjdelcerro.chatagent.lib.AgentSettings.CONVERSATION_PROVIDER_API_KEY;
import static io.github.jjdelcerro.chatagent.lib.AgentSettings.CONVERSATION_PROVIDER_URL;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.apache.commons.io.IOUtils;

/**
 * Orquestador principal del sistema. Gestiona el bucle de razonamiento, la
 * ejecucion de herramientas y la interaccion con el LLM.
 */
public class ConversationManagerImpl {

  private static class Event {

    final String channel;
    final String priority;
    final String contents;

    public Event(String channel, String priority, String contents) {
      this.channel = channel;
      this.priority = priority;
      this.contents = contents;
    }

    @Override
    public String toString() {
      return "[channel:" + this.channel + ",priority:" + this.priority + ",text:" + StringUtils.abbreviate(StringUtils.replace(contents, "\n", " "), 40) + "]";
    }

    public String toJson() {
      Gson gson = new Gson();
      return gson.toJson(Map.of(
              "channel", channel,
              "priority", priority,
              "contents", contents
      ));
    }
  }

  private final AgentImpl agent;
  private final SourceOfTruth sourceOfTruth;
  private final MemoryManagerImpl memoryManager;
  private AgentConsole console;
  private final Session session;
  private ChatLanguageModel model;

  private CheckPoint activeCheckPoint;

  // Registro de herramientas
  private final Map<String, AgenteTool> toolDispatcher = new HashMap<>();
  private final List<ToolSpecification> toolSpecifications = new ArrayList<>();

  private final Queue<Event> pendingEvents = new ConcurrentLinkedQueue<>();
  private final AtomicBoolean isBusy = new AtomicBoolean(false);

  public ConversationManagerImpl(AgentImpl agent) {
    this.agent = agent;
    this.sourceOfTruth = agent.getSourceOfTruth();
    this.memoryManager = agent.getMemoryManager();
    this.console = agent.getConsole();
    this.session = new Session(agent.getDataFolder().toPath());
    this.createChatLanguageModel(agent.getSettings());
    try {
      this.activeCheckPoint = sourceOfTruth.getLatestCheckPoint();
    } catch (Exception e) {
      e.printStackTrace(); // FIXME: log
    }
  }

  public final boolean createChatLanguageModel(AgentSettings settings) {
    this.model = this.agent.createChatModel("CONVERSATION", 0.7);
    return true;
  }

  public void addTool(AgenteTool tool) {
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
      this.console.println("Evento: " + event.toString());
      ToolExecutionRequest request = ToolExecutionRequest.builder()
              .name("pool_event")
              .id("pool_event_" + (UUID.randomUUID().toString().replace("-", "")))
              .build();
      ToolExecutionResultMessage result = ToolExecutionResultMessage.from(request, event.toJson());
      this.executeReasoningLoop(result);
    }
  }

  public synchronized String processTurn(String textUser) {
    String value = this.executeReasoningLoop(UserMessage.from(textUser));
    processPendingEvents();
    return value;

  }

  private synchronized String executeReasoningLoop(ChatMessage inputMessage) {
    StringBuilder llmResponse = new StringBuilder();

    try {
      // 1. INYECTAR INPUT 
      String textUser = null;
      if (inputMessage instanceof ToolExecutionResultMessage) {
        ToolExecutionResultMessage result = (ToolExecutionResultMessage) inputMessage;

        // Reconstruimos la petición original para mantener la coherencia del historial
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id(result.id())
                .name(result.toolName())
                .arguments("{}")
                .build();

        // Inyectamos la "intención" simulada del asistente antes del resultado
        this.session.add(AiMessage.from(request));
        this.session.add(result);

        String contentType = "tool_execution";
        Turn toolTurn = this.sourceOfTruth.createTurn(
                Timestamp.from(Instant.now()),
                contentType,
                null,
                null,
                null,
                request.toString(),
                result.toString(),
                null
        );

        this.sourceOfTruth.add(toolTurn);
        this.session.consolideTurn(toolTurn);
      } else {
        this.session.add(inputMessage);
        textUser = ((UserMessage) inputMessage).singleText();
      }

      // 2. BUCLE DE INTERACCION (Reasoning Loop)
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

            // A. Ejecucion
            String result = executeToolLogic(request);

            // B. Tipo de contenido
            String contentType = "tool_execution";
            if (isMemoryTool(request.name())) {
              contentType = "lookup_turn";
            }

            // C. Persistencia del Turno (Archivo)
            Turn toolTurn = this.sourceOfTruth.createTurn(
                    Timestamp.from(Instant.now()),
                    contentType,
                    null, // User text is managed by Session/Messages now
                    null,
                    null,
                    request.toString(),
                    result,
                    null
            );

            this.sourceOfTruth.add(toolTurn);

            // D. Feedback al LLM (Protocolo)
            this.session.add(ToolExecutionResultMessage.from(request, result));

            // E. Consolidar el turno en la sesion
            this.session.consolideTurn(toolTurn);
          }
          // El bucle continua... 

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

      // 3. GESTION DE COMPACTACION
      if (this.session.needCompaction()) {
        performCompaction();
      }

    } catch (SQLException e) {
      this.console.printerrorln("Error critico de base de datos en processTurn: " + e.getMessage());
      e.printStackTrace(); // FIXME: log
    } catch (Exception e) {
      this.console.printerrorln("Error inesperado en processTurn: " + e.getMessage());
      e.printStackTrace(); // FIXME: log
    }
    return llmResponse.toString();
  }

  private String getBaseSystemPrompt() {
    try {
      DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM 'de' yyyy, HH:mm", new Locale("es", "ES"));
      String systemPrompt = IOUtils.resourceToString(
              "io/github/jjdelcerro/chatagent/lib/impl/prompt-system-conversationmanager.md",
              StandardCharsets.UTF_8,
              this.getClass().getClassLoader()
      );
      systemPrompt = StringUtils.replace(systemPrompt, "{NOW}", LocalDateTime.now().format(formatter));
      systemPrompt = StringUtils.replace(systemPrompt, "{LOOKUPTURN}", LookupTurnTool.NAME);
      systemPrompt = StringUtils.replace(systemPrompt, "{SEARCHFULLHISTORY}", SearchFullHistoryTool.NAME);
      return systemPrompt;
    } catch (IOException ex) {
      throw new RuntimeException("Can't load system prompt for conversation manager", ex);
    }
  }

  private String executeToolLogic(ToolExecutionRequest request) {
    String toolName = request.name();
    String args = request.arguments();

    AgenteTool tool = toolDispatcher.get(toolName);

    if (tool != null) {
      if (tool.getMode() != AgenteTool.MODE_READ) {
        boolean authorized = this.console.confirm(
                String.format("El agente quiere ejecutar la herramienta: %s\nArgumentos: %s\n¿Autorizar?", toolName, args)
        );

        if (!authorized) {
          this.console.println("Ejecución denegada por el usuario.");
          return "Error: User rejected the execution of tool '" + toolName + "'.";
        }
        this.console.println("Ejecutando herramienta: " + toolName);
      } else {
        this.console.println(String.format("Ejecutando herramienta: %s\n    Argumentos: %s", toolName, args));
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
    AgenteTool tool = toolDispatcher.get(toolName);
    return tool.getType() == AgenteTool.TYPE_MEMORY;
  }

  private void performCompaction() throws SQLException {
    this.console.println("Iniciando proceso de compactación de memoria...");

    // 1. Obtener marcas de sesion
    Session.SessionMark mark1 = this.session.getOldestMark();
    Session.SessionMark mark2 = this.session.getCompactMark();

    if (mark1 == null || mark2 == null) {
      this.console.println("Advertencia: No hay suficientes datos consolidados para compactar.");
      return;
    }

    // 2. Recuperar turnos de la DB usando el rango de IDs de las marcas
    List<Turn> compactTurns = this.sourceOfTruth.getTurnsByIds(mark1.getTurnId(), mark2.getTurnId());

    if (compactTurns.isEmpty()) {
      this.console.println("Advertencia: Rango de compactación vacío.");
      return;
    }

    // 3. MemoryManager crea el CheckPoint
    CheckPoint newCheckPoint = memoryManager.compact(this.activeCheckPoint, compactTurns);

    // 4. SourceOfTruth persiste
    sourceOfTruth.add(newCheckPoint);

    // 5. Limpieza de Sesion (Borrar mensajes ya compactados)
    this.session.remove(mark1, mark2);

    // 6. Actualizar punteros del Agente
    this.activeCheckPoint = newCheckPoint;

    this.console.println("Memoria compactada con éxito. Nuevo CheckPoint ID: " + newCheckPoint.getId());
  }

  public void setConsole(AgentConsole console) {
    this.console = console;
  }

}
