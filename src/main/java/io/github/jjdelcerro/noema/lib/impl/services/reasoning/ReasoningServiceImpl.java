package io.github.jjdelcerro.noema.lib.impl.services.reasoning;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import edu.emory.mathcs.backport.java.util.Collections;
import io.github.jjdelcerro.noema.lib.AbstractAgentAction;
import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.impl.services.memory.tools.LookupTurnTool;
import io.github.jjdelcerro.noema.lib.impl.services.memory.tools.SearchFullHistoryTool;
import io.github.jjdelcerro.noema.lib.persistence.CheckPoint;
import io.github.jjdelcerro.noema.lib.persistence.SourceOfTruth;
import io.github.jjdelcerro.noema.lib.persistence.Turn;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import io.github.jjdelcerro.noema.lib.AgentConsole;
import io.github.jjdelcerro.noema.lib.AgentManager;
import io.github.jjdelcerro.noema.lib.AgentServiceFactory;
import io.github.jjdelcerro.noema.lib.settings.AgentSettings;
import io.github.jjdelcerro.noema.lib.AgentTool;
import io.github.jjdelcerro.noema.lib.impl.ModelParametersImpl;
import io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.file.FileExtractTextTool;
import io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.file.FileFindTool;
import io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.file.FileGrepTool;
import io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.file.FileMkdirTool;
import io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.file.FilePatchTool;
import io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.file.FileReadTool;
import io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.file.FileRecoveryTool;
import io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.file.FileSearchAndReplaceTool;
import io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.file.FileWriteTool;
import io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.file.ShellExecuteTool;
import io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.file.ShellReadOutputTool;
import io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.web.LocationTool;
import io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.web.TimeTool;
import io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.web.WeatherTool;
import io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.web.WebGetTikaTool;
import io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.web.WebSearchTool;
import io.github.jjdelcerro.noema.lib.impl.services.memory.MemoryServiceImpl;
import io.github.jjdelcerro.noema.lib.impl.services.sensors.SensorsServiceImpl;
import io.github.jjdelcerro.noema.lib.services.sensors.ConsumableSensorEvent;
import io.github.jjdelcerro.noema.lib.services.sensors.SensorEventUser;
import io.github.jjdelcerro.noema.lib.services.sensors.SensorsService;
import io.github.jjdelcerro.noema.lib.settings.AgentSettingsCheckedList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.github.jjdelcerro.noema.lib.services.reasoning.ReasoningService;
import static io.github.jjdelcerro.noema.lib.AgentActions.CHANGE_REASONING_MODEL;
import static io.github.jjdelcerro.noema.lib.AgentActions.CHANGE_REASONING_PROVIDER;
import io.github.jjdelcerro.noema.lib.impl.DateUtils;
import io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.identity.ConsultEnvironTool;
import io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.identity.ListSkillsTool;
import io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.identity.LoadSkillTool;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Collection;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.mutable.MutableBoolean;

/**
 * Orquestador principal del sistema. Gestiona el bucle de razonamiento, la
 * ejecucion de herramientas y la interaccion con el LLM.
 */
public class ReasoningServiceImpl implements ReasoningService {

  private static final Logger LOGGER = LoggerFactory.getLogger(ReasoningServiceImpl.class);

  private static final int OVERHEAD_IN_ESTIMATE_TOOLS_TOKEN_COUNT = 15;

  private static class AvailableAgentTool {

    private final AgentTool tool;
    private boolean active;

    public AvailableAgentTool(AgentTool tool) {
      this.tool = tool;
      this.active = tool.isAvailableByDefault();
    }
  }

  private final AgentServiceFactory factory;
  private final Agent agent;
  private final SourceOfTruth sourceOfTruth;
  private final Session session;
  private Agent.ChatModel model;
  private boolean running;

  private CheckPoint activeCheckPoint;

  // Registro de herramientas
  private final Map<String, AvailableAgentTool> availableTools = new LinkedHashMap<>();

  public ReasoningServiceImpl(AgentServiceFactory factory, Agent agent) {
    this.factory = factory;
    this.agent = agent;
    this.sourceOfTruth = agent.getSourceOfTruth();
    this.session = new Session(
            agent.getPaths().getDataFolder(),
            agent.getSettings(),
            (SensorsServiceImpl) agent.getService(SensorsService.NAME)
    );
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
      "var/config/prompts/reasoning-system.md",
      "var/identity/core/readme.md",
      "var/identity/environ/readme.md",
      "var/skills/readme.md"
    };
    for (String resPath : resources) {
      this.agent.installResource(resPath);
    }

    this.agent.getActions().addAction(new AbstractAgentAction(this.agent, CHANGE_REASONING_PROVIDER) {
      @Override
      public boolean perform(AgentSettings settings) {
        model = agent.createChatModel(ReasoningService.ID);
        return true;
      }
    });
    this.agent.getActions().addAction(new AbstractAgentAction(this.agent, CHANGE_REASONING_MODEL) {
      @Override
      public boolean perform(AgentSettings settings) {
        model = agent.createChatModel(ReasoningService.ID);
        return true;
      }
    });
    this.agent.getActions().addAction(new AbstractAgentAction(this.agent, "COMPACT_REASONING") {
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
    this.agent.getActions().addAction(new AbstractAgentAction(this.agent, "REFRESH_REASONING_TOOLS") {
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
//    for (AgentTool tool : this.getAvailableTools()) {
//      LOGGER.info(tool.getSpecification().toString());
//    }
    this.refresh_available_tools();
    this.model = this.agent.createChatModel(ReasoningService.ID);
//    Thread.ofVirtual().name(AgentManager.AGENT_NAME + "-Event-Dispatcher").start(this::eventDispatcher);
    Thread.ofPlatform().name(AgentManager.AGENT_NAME + "-Event-Dispatcher").start(this::eventDispatcher);
    this.running = true;
  }

  @Override
  public void addTool(AgentTool tool) {
    this.availableTools.put(tool.getName(), new AvailableAgentTool(tool));
  }

//  private String getBaseSystemPrompt() {
//    String systemPrompt = agent.getResourceAsString("var/config/prompts/reasoning-system.md");
//
//    if (systemPrompt.isEmpty()) {
//      LOGGER.warn("No se ha podido cargar el prompt del sistema del ReasoningService");
//      throw new RuntimeException("Can't load system prompt from data folder");
//    }
//    systemPrompt = StringUtils.replace(systemPrompt, "{NOW}", DateUtils.now());
//    systemPrompt = StringUtils.replace(systemPrompt, "{LOOKUPTURN}", LookupTurnTool.NAME);
//    systemPrompt = StringUtils.replace(systemPrompt, "{SEARCHFULLHISTORY}", SearchFullHistoryTool.NAME);
//    return systemPrompt;
//  }
  private String getBaseSystemPrompt() {
    StringBuilder sb = new StringBuilder();

    // --- CAPA 1: Instrucciones Operativas (Sistema Nervioso Autónomo) ---
    // Cargamos las instrucciones de comportamiento base
    String basePrompt = agent.getResourceAsString("var/config/prompts/reasoning-system.md");
    if (StringUtils.isBlank(basePrompt)) {
      LOGGER.error("No se pudo cargar el recurso base: var/config/prompts/reasoning-system.md");
      throw new RuntimeException("Error crítico: Prompt de sistema base no encontrado.");
    }
    sb.append(basePrompt).append("\n\n");

    // --- CAPA 2a: Constitución (Identidad Core / ADN Técnico) ---
    // Solo cargamos los módulos que el usuario ha marcado en la configuración
    sb.append("# CONSTITUCIÓN Y REGLAS OPERATIVAS\n");
    sb.append("Debes cumplir estrictamente con las siguientes normas técnicas y metodológicas:\n\n");

    AgentSettingsCheckedList coreSettings = agent.getSettings().getPropertyAsCheckedList("reasoning/identity/core");
    List<Path> coreFiles = agent.getPaths().listAgentPath("var/identity/core");
    Collections.sort(coreFiles);

    if (coreFiles != null && !coreFiles.isEmpty()) {
      for (Path path : coreFiles) {
        String fileName = path.getFileName().toString();
        if (StringUtils.equalsIgnoreCase(fileName, "readme.md")) {
          continue;
        }
        // Verificamos si el módulo está activo en la CheckedList de configuración
        boolean isActive = true;
        if (coreSettings != null) {
          // "01_stack_tecnico.md" -> "01_stack_tecnico"
          String baseName = org.apache.commons.io.FilenameUtils.getBaseName(fileName);
          isActive = coreSettings.getItems().stream()
                  .filter(item -> baseName.equals(item.getValue()))
                  .anyMatch(AgentSettingsCheckedList.CheckedItem::isChecked);
        }
        if (isActive) {
          String content = agent.getResourceAsString("var/identity/core/" + fileName);
          if (StringUtils.isNotBlank(content)) {
            sb.append("## Módulo: ").append(fileName).append("\n");
            sb.append(content).append("\n\n");
          }
        }
      }
    }

    // --- CAPA 2b: Consciencia de Entorno (Índice de Referencias .ref.md) ---
    // Cargamos todas las anclas semánticas disponibles para que el agente sepa qué "puede recordar"
    sb.append("# CONSCIENCIA DE ENTORNO (MEMORIA VIRTUAL)\n");
    sb.append("A continuación se lista un índice de referencias sobre el mundo, biografía y proyectos del usuario. ");
    sb.append("No posees los detalles en este momento, pero si detectas que un tema es relevante, ");
    sb.append("DEBES usar la herramienta {CONSULTENVIRON} para recuperar la información completa antes de responder.\n\n");

    Collection<Path> environFiles = agent.getPaths().listAgentPath("var/identity/environ");
    if (environFiles != null) {
      for (Path path : environFiles) {
        String fileName = path.getFileName().toString();
        if (StringUtils.equalsIgnoreCase(fileName, "readme.md")) {
          continue;
        }
        // Solo cargamos los archivos de referencia ligera
        if (fileName.endsWith(".ref.md")) {
          String refContent = agent.getResourceAsString("var/identity/environ/" + fileName);
          if (StringUtils.isNotBlank(refContent)) {
            sb.append(refContent).append("\n");
            sb.append("---\n"); // Separador visual entre anclas
          }
        }
      }
    }

    // --- CAPA FINAL: Resolución de Placeholders ---
    String finalPrompt = sb.toString();
    finalPrompt = StringUtils.replace(finalPrompt, "{NOW}", DateUtils.now());
    finalPrompt = StringUtils.replace(finalPrompt, "{LOOKUPTURN}", LookupTurnTool.NAME);
    finalPrompt = StringUtils.replace(finalPrompt, "{SEARCHFULLHISTORY}", SearchFullHistoryTool.NAME);
    finalPrompt = StringUtils.replace(finalPrompt, "{CONSULTENVIRON}", ConsultEnvironTool.NAME);

    try {
      FileUtils.writeStringToFile(
              agent.getPaths().getAgentFolder().resolve("var/tmp/reasoning-system-prompt.md").toFile(),
              finalPrompt,
              StandardCharsets.UTF_8
      );
    } catch (IOException ex) {
      LOGGER.warn("Can't write system prompt", ex);
    }
    return finalPrompt;
  }

  private AgentConsole console() {
    return this.agent.getConsole();
  }

  private String executeTool(ToolExecutionRequest request) {
    String toolName = request.name();
    String args = request.arguments();

    AvailableAgentTool availableTool = availableTools.get(toolName);

    if (availableTool != null && availableTool.tool != null) {
      AgentTool tool = availableTool.tool;
      if (tool.getMode() != AgentTool.MODE_READ) {
        boolean authorized = this.console().confirm(
                String.format("El agente quiere ejecutar la herramienta: %s\nArgumentos: %s\n¿Autorizar?", toolName, args)
        );

        if (!authorized) {
          String msg = String.format("Ejecucion de herramienta '%s' denegada por el usuario.", toolName);
          LOGGER.info(msg);
          this.console().printSystemLog(msg);
          return msg;
        }
      }
      String msg = String.format("Ejecutando herramienta: %s\n    Argumentos: %s", toolName, args);
      LOGGER.info(msg);
      this.console().printSystemLog(msg);
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
    this.console().printSystemLog("Iniciando proceso de compactación de memoria...");

    // 1. Obtener marcas de sesion
    Session.SessionMark mark1 = this.session.getOldestMark();
    Session.SessionMark mark2 = this.session.getCompactMark();

    if (mark1 == null || mark2 == null) {
      String msg = "No hay suficientes datos consolidados para compactar.";
      LOGGER.warn(msg);
      this.console().printSystemLog(msg);
      return;
    }

    // 2. Recuperar turnos de la DB usando el rango de IDs de las marcas
    List<Turn> compactTurns = this.sourceOfTruth.getTurnsByIds(mark1.getTurnId(), mark2.getTurnId());

    if (compactTurns.isEmpty()) {
      String msg = String.format("No se han podido recuperar los turnos a compactar (turns[%s:%s]).", mark1.getTurnId(), mark2.getTurnId());
      LOGGER.warn(msg);
      this.console().printSystemLog(msg);
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

    this.console().printSystemLog("Memoria compactada con éxito. Nuevo CheckPoint ID: " + newCheckPoint.getId());
  }

  @Override
  public Agent.ChatModel getModel() {
    return model;
  }

  @Override
  public Agent.ModelParameters getModelParameters(String name) {
    AgentSettings settings = this.agent.getSettings();
    switch (name) {
      case ReasoningService.ID:
        return new ModelParametersImpl(
                settings.getPropertyAsString(REASONING_PROVIDER_URL),
                settings.getPropertyAsString(REASONING_PROVIDER_API_KEY),
                settings.getPropertyAsString(REASONING_MODEL_ID),
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
      new ConsultEnvironTool(this.agent),
      new ListSkillsTool(this.agent),
      new LoadSkillTool(this.agent),
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
    return this.agent.getSettings().getPropertyAsString(REASONING_MODEL_ID);
  }

  public void showSession() {
    List<ChatMessage> history = this.session.getMessages();

    for (ChatMessage message : history) {
      if (message instanceof UserMessage userMsg) {
        console().printUserMessage(userMsg.singleText());

      } else if (message instanceof AiMessage aiMsg) {
        // 1. Si el modelo pidió ejecutar herramientas, informamos de cada una
        if (aiMsg.hasToolExecutionRequests()) {
          for (ToolExecutionRequest req : aiMsg.toolExecutionRequests()) {
            console().printSystemLog(String.format("Ejecutando herramienta: %s\n    Argumentos: %s",
                    req.name(), req.arguments()));
          }
        }
        // Los ToolExecutionResultMessage y SystemMessage se ignoran 
        // ya que no se presentan en la consola.
        // 2. Si el modelo respondió con texto, lo mostramos
        if (StringUtils.isNotBlank(aiMsg.text())) {
          console().printModelResponse(aiMsg.text());
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
   * el usuario en la configuración. Si una herramienta no figura en la
   * configuracion, conserva su estado actual en memoria.
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

  @Override
  public void stop() {
    this.running = false;
  }

  /**
   * Bucle perpetuo de consciencia. Consume señales de los sensores y las
   * procesa íntegramente hasta generar una respuesta o acción.
   */
  @SuppressWarnings("UseSpecificCatch")
  private void eventDispatcher() {
//      TODO: **IMPORTANTE**. hay que ver que pasa cuando el primer mensaje que se envia al LLM
//      es un llamada simulda a pool_event. El otro dia me dio la sensacion que peto la llamada
//      al llm por esto. Habria que ver de reproducirlo y que hecemos si falla.
//              
    SensorsServiceImpl sensors = (SensorsServiceImpl) this.agent.getService(SensorsService.NAME);
    MutableBoolean abort = new MutableBoolean(false);
    int toolExecutionRetries = 0;

    while (this.isRunning()) {
      ConsumableSensorEvent event = null;
      StringBuilder finalLlmResponse = new StringBuilder();
      try {
        event = sensors.getEvent();
        if (event == null) {
          continue;
        }

        String textUser = null;

        if (event instanceof SensorEventUser) {
          // Caso Usuario: Guardamos el prompt para el turno final 'chat'
          textUser = event.getContents();
          this.session.add(event.getChatMessage());
        } else {
          // Caso Sensor: Inyectamos el engaño al protocolo y persistimos el turno de observación
          this.session.add(event.getChatMessage());
          this.session.add(event.getResponseMessage());

          Turn obsTurn = this.sourceOfTruth.createTurn(
                  LocalDateTime.now(),
                  "tool_execution",
                  null, null, null,
                  event.getChatMessage().toString(),
                  event.getResponseMessage().toString(),
                  null
          );
          this.sourceOfTruth.add(obsTurn);
          this.session.consolideTurn(obsTurn);
        }
        toolExecutionRetries = 0;
        boolean turnFinished = false;
        while (!turnFinished && this.isRunning()) {
          List<ChatMessage> context = this.session.getContextMessages(this.activeCheckPoint, getBaseSystemPrompt());

          Response<AiMessage> response = model.generate(context, this.getToolSpecifications(), abort);
          AiMessage aiMessage = response.content();
          this.session.add(aiMessage);

          if (aiMessage.hasToolExecutionRequests()) {
            for (ToolExecutionRequest request : aiMessage.toolExecutionRequests()) {
              String result = executeTool(request);
              String contentType = "tool_execution";
              if (isMemoryTool(request.name())) {
                contentType = "lookup_turn";
              }
              Turn toolTurn = this.sourceOfTruth.createTurn(
                      LocalDateTime.now(),
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
            String aiText = aiMessage.text();
            finalLlmResponse.append(aiText); // No esta claro que sea necesario mantener el finalLlmResponse
            this.console().printModelResponse(aiText);
            Turn responseTurn = this.sourceOfTruth.createTurn(
                    LocalDateTime.now(),
                    "chat",
                    textUser, // Original (si fue UserEvent) o null (si fue Sensor)
                    null,
                    aiText, // Respuesta final del modelo
                    null,
                    null,
                    null
            );
            this.sourceOfTruth.add(responseTurn);
            this.session.consolideTurn(responseTurn);
            if (response.finishReason() == FinishReason.TOOL_EXECUTION) {
              // El modelo anunció una tool en texto pero no la ejecutó formalmente
              // Reinyectamos forzando la ejecución
              this.session.add(new UserMessage("(reintenta la llamada a la herramienta sin ninguna explicacion)"));
              if (toolExecutionRetries++ > 3) {
                throw new RuntimeException("Too many retries for executing tool");
              }
            } else {
              turnFinished = true;
            }
            toolExecutionRetries = 0;
          }
        }
        if (this.session.needCompaction()) {
          performCompaction();
        }

      } catch (Throwable e) {
        LOGGER.error("Error crítico en el bucle de consciencia", e);
        this.console().printSystemError("Dispatcher Critical Error: " + e.getMessage());
      }

      try {
        if (event != null && event.getCallback() != null) {
          event.getCallback().onComplete(finalLlmResponse.toString());
        }
      } catch (Exception e) {
        LOGGER.error("Error ejecutando onComplete", e);
        this.console().printSystemError("Dispatcher error onComplete: " + e.getMessage());
      }
    }
  }

}
