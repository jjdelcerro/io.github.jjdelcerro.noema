package io.github.jjdelcerro.noema.lib.impl.services.reasoning;

import io.github.jjdelcerro.noema.lib.impl.memory.recent.RecentMemoryImpl;
import io.github.jjdelcerro.noema.lib.memory.recent.RecentMemory;
import io.github.jjdelcerro.noema.lib.memory.projected.ProjectedMemory;
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
import io.github.jjdelcerro.noema.lib.AgentAccessControl;
import io.github.jjdelcerro.noema.lib.impl.services.memory.tools.LookupTurnTool;
import io.github.jjdelcerro.noema.lib.impl.services.memory.tools.SearchFullHistoryTool;
import io.github.jjdelcerro.noema.lib.memory.episodic.Turn;

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
import io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.file.ReadPaginatedResourceTool;
import io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.web.LocationTool;
import io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.web.TimeTool;
import io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.web.WeatherTool;
import io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.web.WebGetTikaTool;
import io.github.jjdelcerro.noema.lib.impl.services.memory.MemoryCompactionServiceImpl;
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
import io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.web.TavilyWebSearchTool;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashMap;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.mutable.MutableBoolean;
import static io.github.jjdelcerro.noema.lib.Agent.DEFAULT_SUBCHANNEL;
import static io.github.jjdelcerro.noema.lib.AgentActions.COMPACT_REASONING_FULL_MEMORY;
import static io.github.jjdelcerro.noema.lib.AgentActions.COMPACT_REASONING_MEMORY;
import io.github.jjdelcerro.noema.lib.impl.memory.projected.ProjectedMemoryImpl;
import io.github.jjdelcerro.noema.lib.impl.services.memory.tools.AnnotateObservationTool;
import io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.scripting.ScriptExecuteTool;
import io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.skills.ActivateSkillTool;
import io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.skills.DeactivateSkillTool;
import io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.skills.ListSkillsTool;
import io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.skills.ReadSkillResourceTool;
import io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.skills.RunSkillScriptTool;
import io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.subagent.LaunchSubagentTool;
import io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.subagent.ListSubagentsTool;
import io.github.jjdelcerro.noema.lib.memory.episodic.EpisodicMemory;
import io.github.jjdelcerro.noema.lib.memory.compacted.CompactedMemory;

/**
 * Orquestador principal del sistema. Gestiona el bucle de razonamiento, la
 * ejecucion de herramientas y la interaccion con el LLM.
 */
public class ReasoningServiceImpl implements ReasoningService {

  private static final Logger LOGGER = LoggerFactory.getLogger(ReasoningServiceImpl.class);

  protected String lastestSystemPrompt;
  protected String currentSubchannel;

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
  private final EpisodicMemory episodicMemory;
  private final Map<String, RecentMemory> recentMemories;
  private final Map<String, ProjectedMemory> projectedMemories;
  private Agent.ChatModel model;
  private boolean running;

  private Map<String, CompactedMemory> activesCompactedMemories;

  // Registro de herramientas
  private final Map<String, AvailableAgentTool> availableTools = new LinkedHashMap<>();

  public ReasoningServiceImpl(AgentServiceFactory factory, Agent agent) {
    this.factory = factory;
    this.agent = agent;
    this.episodicMemory = agent.getEpisodicMemory();
    this.recentMemories = new HashMap<>();
    this.projectedMemories = new HashMap<>();
    this.activesCompactedMemories = new HashMap<>();
    this.running = false;
    this.currentSubchannel = DEFAULT_SUBCHANNEL;
  }

  public RecentMemory createRecentMemory(String subchannel) {
    RecentMemoryImpl recentMemory = new RecentMemoryImpl(
            agent.getPaths().getDataFolder(),
            agent.getSettings(),
            subchannel
    );
    return recentMemory;
  }

  private RecentMemory getRecentMemory(String subchannel) {
    RecentMemory recentMemory = this.recentMemories.get(subchannel);
    if (recentMemory == null) {
      recentMemory = this.createRecentMemory(subchannel);
      this.recentMemories.put(subchannel, recentMemory);
    }
    return recentMemory;
  }

  private ProjectedMemory createProjectedMemory(String subchannel) {
    ProjectedMemory projectedMemory = new ProjectedMemoryImpl(
            agent,
            this::getAvailableTool,
            subchannel
    );
    return projectedMemory;
  }

  public ProjectedMemory getProjectedMemory(String subchannel) {
    ProjectedMemory projectedMemory = this.projectedMemories.get(subchannel);
    if (projectedMemory == null) {
      projectedMemory = this.createProjectedMemory(subchannel);
      this.projectedMemories.put(subchannel, projectedMemory);
    }
    return projectedMemory;
  }

  public CompactedMemory getActiveCompactedMemory(String subchannel) {
    CompactedMemory compactedMemory = this.activesCompactedMemories.get(subchannel);
    if (compactedMemory == null) {
      try {
        compactedMemory = episodicMemory.getLatestCompactedMemory(subchannel);
      } catch (Exception e) {
        LOGGER.warn("No se ha podido recuperar el ultimo CompactedMemory", e);
      }
      this.activesCompactedMemories.put(subchannel, compactedMemory);
    }
    return compactedMemory;
  }

  private CompactedMemory setActiveCompactedMemory(String subchannel, CompactedMemory compactedMemory) {
    this.activesCompactedMemories.put(subchannel, compactedMemory);
    return compactedMemory;
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
      "var/identity/environ/readme.md"
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
    this.agent.getActions().addAction(new AbstractAgentAction(this.agent, COMPACT_REASONING_MEMORY) {
      @Override
      public boolean perform(AgentSettings settings) {
        for (RecentMemory recentMemory : recentMemories.values()) {
          try {
            RecentMemoryImpl.RecentMemoryMark mark1 = recentMemory.getOldestMark();
            RecentMemoryImpl.RecentMemoryMark mark2 = recentMemory.getCompactMark();
            performCompaction(recentMemory, mark1, mark2);
          } catch (Exception ex) {
            LOGGER.warn("Can't compact conversation", ex);
            return false;
          }
        }
        return true;
      }
    });
    this.agent.getActions().addAction(new AbstractAgentAction(this.agent, COMPACT_REASONING_FULL_MEMORY) {
      @Override
      public boolean perform(AgentSettings settings) {
        for (RecentMemory recentMemory : recentMemories.values()) {
          try {
            RecentMemoryImpl.RecentMemoryMark mark1 = recentMemory.getOldestMark();
            RecentMemoryImpl.RecentMemoryMark mark2 = recentMemory.getNewestMark();
            performCompaction(recentMemory, mark1, mark2);
          } catch (Exception ex) {
            LOGGER.warn("Can't compact conversation", ex);
            return false;
          }
        }
        return true;
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
    this.agent.getConsole(DEFAULT_SUBCHANNEL).printSystemLog("Reasoning service " + getModelName());

  }

  @Override
  public void addTool(AgentTool tool) {
    this.availableTools.put(tool.getName(), new AvailableAgentTool(tool));
  }

  public String getBaseSystemPrompt() {
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

    if (coreFiles != null && !coreFiles.isEmpty()) {
      Collections.sort(coreFiles); // Nos aseguramos que s epresenten siempre en el mismo orden
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

    List<Path> environFiles = agent.getPaths().listAgentPath("var/identity/environ");
    if (environFiles != null && !environFiles.isEmpty()) {
      Collections.sort(environFiles); // Nos aseguramos que s epresenten siempre en el mismo orden
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

    // sb.append("**Momento actual de la conversación:** {NOW}\n"); // Ojo, penaliza la cache en la llamada al API
    // --- CAPA FINAL: Resolución de Placeholders ---
    String finalPrompt = sb.toString();
    finalPrompt = StringUtils.replace(finalPrompt, "{NOW}", DateUtils.now());
    finalPrompt = StringUtils.replace(finalPrompt, "{LOOKUPTURN}", LookupTurnTool.NAME);
    finalPrompt = StringUtils.replace(finalPrompt, "{SEARCHFULLHISTORY}", SearchFullHistoryTool.NAME);
    finalPrompt = StringUtils.replace(finalPrompt, "{CONSULTENVIRON}", ConsultEnvironTool.NAME);
    finalPrompt = StringUtils.replace(finalPrompt, "{ANNOTATE_OBSERVATION}", AnnotateObservationTool.TOOL_NAME);

    try {
      FileUtils.writeStringToFile(
              agent.getPaths().getAgentFolder().resolve("var/tmp/reasoning-system-prompt.md").toFile(),
              finalPrompt,
              StandardCharsets.UTF_8
      );
    } catch (IOException ex) {
      LOGGER.warn("Can't write system prompt", ex);
    }
    this.lastestSystemPrompt = finalPrompt;
    return finalPrompt;
  }

  private String getLastestSystemPrompt() {
    if (this.lastestSystemPrompt != null) {
      return this.lastestSystemPrompt;
    }
    return this.getBaseSystemPrompt();
  }

  private AgentConsole console(String subchannel) {
    return this.agent.getConsole(subchannel);
  }

  private String executeTool(RecentMemory recentMemory, ToolExecutionRequest request) {
    String toolName = request.name();
    String args = request.arguments();

    AvailableAgentTool availableTool = availableTools.get(toolName);
    String subchannel = recentMemory.getSubchannel();
    if (availableTool != null && availableTool.tool != null) {
      AgentTool tool = availableTool.tool;
      if (tool.getMode() != AgentTool.MODE_READ && agent.getAccessControl().isHumanConfirmationRequired()) {
        boolean authorized = this.console(subchannel).confirm(
                String.format("El agente quiere ejecutar la herramienta: %s\nArgumentos: %s\n¿Autorizar?", toolName, args)
        );

        if (!authorized) {
          String msg = String.format("Ejecucion de herramienta '%s' denegada por el usuario.", toolName);
          LOGGER.info(msg);
          this.console(subchannel).printSystemLog(msg);
          return msg;
        }
      }
      String msg = String.format("Ejecutando herramienta: %s\n    Argumentos: %s", toolName, args);
      LOGGER.info(msg);
      this.console(subchannel).printSystemLog(msg);
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

  private int getToolType(String toolName) {
    AvailableAgentTool tool = availableTools.get(toolName);
    return tool.tool.getType();
  }

  private void performCompaction(RecentMemory recentMemory) throws SQLException {
    RecentMemoryImpl.RecentMemoryMark mark1 = recentMemory.getOldestMark();
    RecentMemoryImpl.RecentMemoryMark mark2 = recentMemory.getCompactMark();
    this.performCompaction(recentMemory, mark1, mark2);
  }

  private void performCompaction(RecentMemory recentMemory, RecentMemory.RecentMemoryMark mark1, RecentMemory.RecentMemoryMark mark2) throws SQLException {
    String subchannel = recentMemory.getSubchannel();
    this.console(subchannel).printSystemLog("Iniciando proceso de compactación de memoria...");

    if (mark1 == null || mark2 == null) {
      String msg = "No hay suficientes datos consolidados para compactar.";
      LOGGER.warn(msg);
      this.console(subchannel).printSystemLog(msg);
      return;
    }

    // Recuperar turnos de la DB usando el rango de IDs de las marcas
    List<Turn> compactTurns = this.episodicMemory.getTurnsByIds(subchannel, mark1.getTurnId(), mark2.getTurnId());

    if (compactTurns.isEmpty()) {
      String msg = String.format("No se han podido recuperar los turnos a compactar (turns[%s:%s]).", mark1.getTurnId(), mark2.getTurnId());
      LOGGER.warn(msg);
      this.console(subchannel).printSystemLog(msg);
      return;
    }

    // MemoryCompactionService crea el CompactedMemory
    MemoryCompactionServiceImpl memoryCompactionService = (MemoryCompactionServiceImpl) this.agent.getService(MemoryCompactionServiceImpl.NAME);
    CompactedMemory newCompactedMemory = memoryCompactionService.compact(subchannel, this.getActiveCompactedMemory(subchannel), compactTurns);

    // episodicMemory persiste
    episodicMemory.add(newCompactedMemory);

    // Limpieza de la RecentMemory (Borrar mensajes ya compactados)
    recentMemory.remove(mark1, mark2);

    // Actualizar punteros del Agente
    this.setActiveCompactedMemory(subchannel, newCompactedMemory);

    this.console(subchannel).printSystemLog("Memoria compactada con éxito. Nuevo CompactedMemory ID: " + newCompactedMemory.getId());
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
      new TimeTool(this.agent),
      new LaunchSubagentTool(this.agent),
      new ListSubagentsTool(this.agent),
      new ScriptExecuteTool(this.agent),
      new FileGrepTool(this.agent),
      new FileReadTool(this.agent),
      new ListSkillsTool(this.agent),
      new ActivateSkillTool(this.agent),
      new DeactivateSkillTool(this.agent),
      new ReadSkillResourceTool(this.agent),
      new RunSkillScriptTool(this.agent),
      
      new FileFindTool(this.agent),
      new FileWriteTool(this.agent),
      new FileSearchAndReplaceTool(this.agent),
      new FilePatchTool(this.agent),
      new FileMkdirTool(this.agent),
      new FileExtractTextTool(this.agent),
      new WebGetTikaTool(this.agent),
      new WeatherTool(this.agent),
      new LocationTool(this.agent),
      new ShellExecuteTool(this.agent),
      new ReadPaginatedResourceTool(this.agent),
      new FileRecoveryTool(this.agent)
    };
    List<AgentTool> tools = new ArrayList<>(Arrays.asList(tools0));

//    String braveApiKey = this.agent.getSettings().getPropertyAsString(BraveWebSearchTool.BRAVE_SEARCH_API_KEY);
//    if (StringUtils.isNotBlank(braveApiKey)) {
//      tools.add(new BraveWebSearchTool(this.agent));
//    }
    String tavilyApiKey = this.agent.getSettings().getPropertyAsString(TavilyWebSearchTool.TAVILY_API_KEY);
    if (StringUtils.isNotBlank(tavilyApiKey)) {
      tools.add(new TavilyWebSearchTool(this.agent));
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
  public int estimateSystemPromptTokenCount(String subchannel) {
    List<ChatMessage> messages = Collections.singletonList(UserMessage.from(this.getLastestSystemPrompt()));
    return this.agent.estimateTokenCount(messages, null);
  }

  @Override
  public int estimateToolsTokenCount(String subchannel) {
    return this.agent.estimateTokenCount(null, this.getToolSpecifications());
  }

  @Override
  public int estimateMessagesTokenCount(String subchannel) {
    ProjectedMemory projection = this.getProjectedMemory(subchannel);
    return this.agent.estimateTokenCount(
            projection.getMessages(
                    this.getRecentMemory(subchannel),
                    this.getActiveCompactedMemory(subchannel),
                    this.getLastestSystemPrompt()
            ),
            null
    );
  }

  @Override
  public String getModelName() {
    Agent.ChatModel theModel = this.getModel();
    if (theModel == null) {
      return null;
    }
    return theModel.getParameters().modelId();
  }

  private List<ToolSpecification> getToolSpecifications() {
    List<ToolSpecification> toolSpecifications = new ArrayList<>();
    AgentAccessControl accessControl = this.agent.getAccessControl();
    for (AvailableAgentTool availableTool : this.availableTools.values()) {
      if (accessControl.isToolAllowed(availableTool.tool) && availableTool.active) {
        toolSpecifications.add(availableTool.tool.getSpecification().build());
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
    SensorsServiceImpl sensors = (SensorsServiceImpl) this.agent.getService(SensorsService.NAME);

    while (this.isRunning()) {
      ConsumableSensorEvent event;
      try {
        event = sensors.getEvent();
        if (event == null) {
          continue;
        }
        this.processSingleEvent(event);
      } catch (Throwable e) {
        LOGGER.error("Error crítico en el bucle de consciencia", e);
        this.console(currentSubchannel).printSystemError("Dispatcher Critical Error: " + e.getMessage());
      }
    }
  }

  public void processSingleEvent(ConsumableSensorEvent event) throws Throwable {
//      TODO: **IMPORTANTE**. hay que ver que pasa cuando el primer mensaje que se envia al LLM
//      es un llamada simulda a pool_event. El otro dia me dio la sensacion que peto la llamada
//      al llm por esto. Habria que ver de reproducirlo y que hecemos si falla.
//              
    StringBuilder finalLlmResponse = new StringBuilder();
    int toolExecutionRetries;
    MutableBoolean abort = new MutableBoolean(false);
    try {
      this.currentSubchannel = event.getSubchannel();
      String channel = event.getChannel();
      String textUser = null;
      RecentMemory recentMemory = this.getRecentMemory(currentSubchannel);
      CompactedMemory compactedMemory = this.getActiveCompactedMemory(currentSubchannel);
      ProjectedMemory projectedMemory = getProjectedMemory(currentSubchannel);

      if (event instanceof SensorEventUser) {
        // Caso Usuario: Guardamos el prompt para el turno final 'chat'
        textUser = event.getContents();
        recentMemory.add(event.getChatMessage());
      } else {
        // Caso Sensor: Inyectamos el engaño al protocolo y persistimos el turno de observación
        recentMemory.add(event.getChatMessage());
        recentMemory.add(event.getResponseMessage());

        Turn obsTurn = this.episodicMemory.createTurn(
                LocalDateTime.now(),
                "tool_execution",
                currentSubchannel,
                null, null, null,
                event.getChatMessage().toString(),
                event.getResponseMessage().toString(),
                null
        );
        this.episodicMemory.add(obsTurn);
        recentMemory.consolideTurn(obsTurn);
      }
      toolExecutionRetries = 0;
      boolean turnFinished = false;
      while (!turnFinished && this.isRunning()) {
        projectedMemory.setLastInteractionTurn(recentMemory.getLastTurnId());
        Response<AiMessage> response = this.getModel().generate(
                projectedMemory.getMessages(recentMemory, compactedMemory, this.getBaseSystemPrompt()),
                this.getToolSpecifications(),
                abort
        );
        AiMessage aiMessage = response.content();
        recentMemory.add(aiMessage);

        if (aiMessage.hasToolExecutionRequests()) {
          String intermediateText = aiMessage.text();
          if (StringUtils.isNotBlank(intermediateText)) {
            this.console(currentSubchannel).printModelResponse(intermediateText);
//            finalLlmResponse.append(intermediateText).append("\n\n"); 
          }          
          for (ToolExecutionRequest request : aiMessage.toolExecutionRequests()) {
            String result = executeTool(recentMemory, request);

            String contentType;
            switch (this.getToolType(request.name())) {
              case AgentTool.TYPE_ANNOTATION:
                contentType = "annotation";
                break;
              case AgentTool.TYPE_MEMORY:
                contentType = "lookup_turn";
                break;
              case AgentTool.TYPE_OPERATIONAL:
              default:
                contentType = "tool_execution";
            }
            Turn toolTurn = this.episodicMemory.createTurn(
                    LocalDateTime.now(),
                    contentType,
                    currentSubchannel,
                    null,
                    null,
                    null,
                    request.toString(),
                    result,
                    null
            );
            this.episodicMemory.add(toolTurn);
            recentMemory.add(ToolExecutionResultMessage.from(request, result));
            recentMemory.consolideTurn(toolTurn);
          }
          toolExecutionRetries = 0;
        } else {
          String aiText = aiMessage.text();
          finalLlmResponse.append(aiText); // No esta claro que sea necesario mantener el finalLlmResponse
          this.console(currentSubchannel).printModelResponse(aiText);
          Turn responseTurn = this.episodicMemory.createTurn(
                  LocalDateTime.now(),
                  "chat",
                  currentSubchannel,
                  textUser, // Original (si fue UserEvent) o null (si fue Sensor)
                  null,
                  aiText, // Respuesta final del modelo
                  null,
                  null,
                  null
          );
          this.episodicMemory.add(responseTurn);
          recentMemory.consolideTurn(responseTurn);
          if (response.finishReason() == FinishReason.TOOL_EXECUTION) {
            // El modelo anunció una tool en texto pero no la ejecutó formalmente
            // Reinyectamos forzando la ejecución
            if (toolExecutionRetries++ > 3) {
              throw new RuntimeException("Too many retries for executing tool");
            }
            recentMemory.add(new UserMessage("(reintenta la llamada a la herramienta sin ninguna explicacion)"));
          } else {
            turnFinished = true;
            toolExecutionRetries = 0;
          }
        }
        if (recentMemory.needCompaction()) {
          performCompaction(recentMemory);
        }
      }
      if (textUser != null) {
        projectedMemory.setLastInteractionTime(LocalDateTime.now());
      }
      recentMemory.save();
      projectedMemory.save();

      if (recentMemory.needCompaction()) {
        performCompaction(recentMemory);
      }
    } finally {
      try {
        if (event != null && event.getCallback() != null) {
          event.getCallback().onComplete(finalLlmResponse.toString());
        }
      } catch (Exception e) {
        LOGGER.error("Error ejecutando onComplete", e);
        this.console(currentSubchannel).printSystemError("Dispatcher error onComplete: " + e.getMessage());
      }
    }
  }

  @Override
  public int getTurnsCount(String subchannel) {
    return this.getRecentMemory(subchannel).getTurnsCount();
  }

  public String getCurrentSubchannel() {
    return this.currentSubchannel;
  }
}
