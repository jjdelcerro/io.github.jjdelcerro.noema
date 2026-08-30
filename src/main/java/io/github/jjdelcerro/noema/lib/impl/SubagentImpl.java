package io.github.jjdelcerro.noema.lib.impl;

import com.google.gson.JsonObject;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.AgentAccessControl;
import io.github.jjdelcerro.noema.lib.AgentActions;
import io.github.jjdelcerro.noema.lib.AgentConsole;
import io.github.jjdelcerro.noema.lib.AgentLocator;
import io.github.jjdelcerro.noema.lib.AgentPaths;
import io.github.jjdelcerro.noema.lib.AgentService;
import io.github.jjdelcerro.noema.lib.AgentTool;
import io.github.jjdelcerro.noema.lib.ConnectionSupplier;
import io.github.jjdelcerro.noema.lib.Subagent;
import io.github.jjdelcerro.noema.lib.SubagentDefinition;
import io.github.jjdelcerro.noema.lib.SubagentDefinition.SubagentParam;
import io.github.jjdelcerro.noema.lib.SubagentDefinition.SubagentParamType;
import io.github.jjdelcerro.noema.lib.impl.services.embeddings.EmbeddingsService;
import io.github.jjdelcerro.noema.lib.impl.services.reasoning.ReasoningServiceImpl;
import io.github.jjdelcerro.noema.lib.impl.services.sensors.SensorInformationImpl;
import io.github.jjdelcerro.noema.lib.impl.services.sensors.nature.user.SensorEventUserImpl;
import io.github.jjdelcerro.noema.lib.impl.settings.AgentSettingsImpl;
import io.github.jjdelcerro.noema.lib.memory.episodic.EpisodicMemory;
import io.github.jjdelcerro.noema.lib.memory.episodic.Turn;
import io.github.jjdelcerro.noema.lib.services.reasoning.ReasoningService;
import io.github.jjdelcerro.noema.lib.services.sensors.ConsumableSensorEvent;
import io.github.jjdelcerro.noema.lib.services.sensors.SensorInformation;
import io.github.jjdelcerro.noema.lib.services.sensors.SensorNature;
import io.github.jjdelcerro.noema.lib.services.sensors.SensorsService;
import io.github.jjdelcerro.noema.lib.services.sensors.SensorsService.SensorEventCallback;
import io.github.jjdelcerro.noema.lib.settings.AgentSettings;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import static io.github.jjdelcerro.noema.lib.impl.services.sensors.SensorsServiceImpl.SYSTEMNOTIFICATION_SENSOR_NAME;
import static io.github.jjdelcerro.noema.lib.services.reasoning.ReasoningService.ACTIVE_TOOLS;
import static io.github.jjdelcerro.noema.lib.services.reasoning.ReasoningService.REASONING_MODEL_ID;
import static io.github.jjdelcerro.noema.lib.services.reasoning.ReasoningService.REASONING_PROVIDER_API_KEY;
import static io.github.jjdelcerro.noema.lib.services.reasoning.ReasoningService.REASONING_PROVIDER_URL;
import static io.github.jjdelcerro.noema.lib.services.sensors.SensorsService.PRIORITY_HIGH;
import static io.github.jjdelcerro.noema.lib.services.sensors.SensorsService.PRIORITY_NORMAL;

/**
 * Disposable worker subagent implementation. Encapsulates an isolated workspace
 * sandbox, synchronous two-act execution, background thread launching, path
 * normalization, and automatic cleanup upon completion.
 */
@SuppressWarnings("UseSpecificCatch")
public class SubagentImpl implements Subagent {

  private static final Logger LOGGER = LoggerFactory.getLogger(SubagentImpl.class);
  private static final AtomicInteger ID_GENERATOR = new AtomicInteger(1);

  private final int id;
  private final Agent parent;
  private final SubagentDefinition definition;
  private final Path workspace;

  private SubagentPaths subPaths;
  private AgentSettings subSettings;
  private SubagentConsole subConsole;
  private AgentImpl subAgent;

  private Connection memoryConn;
  private Connection servicesConn;
  private Thread workerThread;
  private Status status;
  private String lastResponse;
  private Throwable lastError;

  public SubagentImpl(Agent parent, SubagentDefinition definition, Path workspace) {
    this.id = ID_GENERATOR.getAndIncrement();
    this.parent = Objects.requireNonNull(parent, "Parent agent cannot be null");
    this.definition = Objects.requireNonNull(definition, "SubagentDefinition cannot be null");
    this.workspace = Objects.requireNonNull(workspace, "Workspace path cannot be null").toAbsolutePath().normalize();
    this.status = Status.CREATED;
  }

  @Override
  public int getId() {
    return this.id;
  }

  @Override
  public SubagentDefinition getDefinition() {
    return this.definition;
  }

  @Override
  public Status getStatus() {
    return this.status;
  }

  @Override
  public String getLastResponse() {
    return this.lastResponse;
  }

  @Override
  public Throwable getLastError() {
    return this.lastError;
  }

  // =========================================================================
  // INITIALIZATION AND SETUP
  // =========================================================================
  private synchronized void setup() {
    if (this.subAgent != null) {
      return;
    }

    try {
      // 1. Paths hierarchy
      this.subPaths = new SubagentPaths(this.workspace);
      this.subPaths.setupHierarchy();

      // 2. Console (writes log file into parent's var/tmp/)
      Path parentTemp = parent.getPaths() != null && parent.getPaths().getTempFolder() != null
              ? parent.getPaths().getTempFolder()
              : this.workspace;

      String logFileName = String.format(
              "subagent_%s_%s_%d.log",
              definition.getName(),
              LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")),
              this.id
      );
      Path logFilePath = parentTemp.resolve(logFileName);
      this.subConsole = new SubagentConsole(logFilePath);

      // 3. Settings configuration
      this.subSettings = new AgentSettingsImpl(this.subPaths);
      this.subSettings.load();
      this.subSettings.setupSettings();

      // Inherit LLM provider credentials from parent
      AgentSettings parentSettings = parent.getSettings();
      if (parentSettings != null) {
        this.subSettings.setProperty(REASONING_PROVIDER_URL, parentSettings.getPropertyAsString(REASONING_PROVIDER_URL));
        this.subSettings.setProperty(REASONING_PROVIDER_API_KEY, parentSettings.getPropertyAsString(REASONING_PROVIDER_API_KEY));
        this.subSettings.setProperty(REASONING_MODEL_ID, parentSettings.getPropertyAsString(REASONING_MODEL_ID));

        // Inherit access control defaults
        this.subSettings.setProperty("access_control/allow_disk_write", parentSettings.getPropertyAsString("access_control/allow_disk_write", "true"));
        this.subSettings.setProperty("access_control/allow_shell_execution", parentSettings.getPropertyAsString("access_control/allow_shell_execution", "false"));
        this.subSettings.setProperty("access_control/allow_internet_access", parentSettings.getPropertyAsString("access_control/allow_internet_access", "false"));
        this.subSettings.setProperty("access_control/enable_rcs_backup", parentSettings.getPropertyAsString("access_control/enable_rcs_backup", "false"));
      }

      // Override model if specified in XML definition
      if (definition.getModelId() != null) {
        this.subSettings.setProperty(REASONING_MODEL_ID, definition.getModelId());
      }

      // Subagents operate in unattended mode
      this.subSettings.setProperty("access_control/humanConfirmationRequired", "false");

      // Whitelist parent's workspace and allowed external paths
      List<String> allowedPaths = new ArrayList<>();
      if (parent.getPaths() != null && parent.getPaths().getWorkspaceFolder() != null) {
        allowedPaths.add(parent.getPaths().getWorkspaceFolder().toAbsolutePath().normalize().toString());
      }
      if (parent.getAccessControl() != null) {
        for (Path p : parent.getAccessControl().getAllowedPaths()) {
          allowedPaths.add(p.toAbsolutePath().normalize().toString());
        }
      }
      this.subSettings.setProperty("access_control/allowed_external_paths", allowedPaths);

      // Tools whitelist: only tools declared in XML are enabled
      List<String> allowedTools = definition.getTools();
      for (String toolName : allowedTools) {
        this.subSettings.setChecked(ACTIVE_TOOLS, toolName, true);
      }
      this.subSettings.save();

      // 4. Deploy prompts
      deployPrompts();

      // 5. Setup isolated H2 databases
      File memoryFile = subPaths.getDataFolder().resolve("sub_memory").toFile();
      File servicesFile = subPaths.getDataFolder().resolve("sub_service").toFile();

      ConnectionSupplier memoryDatabase = new ConnectionSupplier() {
        @Override
        public Connection get() {
          try {
            return DriverManager.getConnection("jdbc:h2:" + memoryFile.getAbsolutePath() + ";AUTO_SERVER=TRUE", "sa", "");
          } catch (SQLException ex) {
            throw new RuntimeException("Cannot open subagent memory database", ex);
          }
        }

        @Override
        public String getProviderName() {
          return "H2";
        }
      };

      ConnectionSupplier servicesDatabase = new ConnectionSupplier() {
        @Override
        public Connection get() {
          try {
            return DriverManager.getConnection("jdbc:h2:" + servicesFile.getAbsolutePath() + ";AUTO_SERVER=TRUE", "sa", "");
          } catch (SQLException ex) {
            throw new RuntimeException("Cannot open subagent services database", ex);
          }
        }

        @Override
        public String getProviderName() {
          return "H2";
        }
      };

      this.memoryConn = memoryDatabase.get();
      this.servicesConn = servicesDatabase.get();

      // 6. Instantiate internal AgentImpl
      this.subAgent = (AgentImpl) AgentLocator.getAgentManager().createAgent(
              memoryDatabase,
              servicesDatabase,
              this.subSettings,
              this.subConsole
      );
      this.subAgent.addSharedService(this.parent.getService(EmbeddingsService.NAME));

    } catch (Exception e) {
      this.status = Status.FAILED;
      this.lastError = e;
      LOGGER.error("Failed to setup subagent '{}' (ID: {})", definition.getName(), this.id, e);
      throw new RuntimeException("Error setting up subagent: " + e.getMessage(), e);
    }
  }

  private void deployPrompts() throws IOException {
    Path promptsDir = subPaths.getConfigFolder().resolve("prompts");
    Files.createDirectories(promptsDir);

    // System prompt: custom from XML or fallback
    Path systemPromptPath = promptsDir.resolve("reasoning-system.md");
    if (definition.hasSystemPrompt()) {
      Files.writeString(systemPromptPath, definition.getSystemPrompt(), StandardCharsets.UTF_8);
    } else {
      AgentUtils.installResource(subPaths, "var/config/prompts/reasoning-system.md");
    }

    // Memory prompt: custom from XML or fallback
    Path memoryPromptPath = promptsDir.resolve("compaction-memory.md");
    if (definition.hasMemoryPrompt()) {
      Files.writeString(memoryPromptPath, definition.getMemoryPrompt(), StandardCharsets.UTF_8);
    } else {
      AgentUtils.installResource(subPaths, "var/config/prompts/compaction-memory.md");
    }
  }

  // =========================================================================
  // PARAMETERS NORMALIZATION (RELATIVE -> ABSOLUTE PATH RESOLUTION)
  // =========================================================================
  private Map<String, Object> normalizeParams(Map<String, ?> rawParams) {
    if (rawParams == null || rawParams.isEmpty()) {
      return Collections.emptyMap();
    }

    Map<String, Object> normalized = new LinkedHashMap<>();
    Path parentWorkspace = (parent.getPaths() != null && parent.getPaths().getWorkspaceFolder() != null)
            ? parent.getPaths().getWorkspaceFolder().toAbsolutePath().normalize()
            : Path.of(".").toAbsolutePath().normalize();

    for (Map.Entry<String, ?> entry : rawParams.entrySet()) {
      String key = entry.getKey();
      Object value = entry.getValue();

      if (value instanceof String strVal) {
        SubagentParam paramDef = definition.getParam(key);
        if (paramDef != null) {
          SubagentParamType type = paramDef.type();
          if (type == SubagentParamType.FILE || type == SubagentParamType.PATH || type == SubagentParamType.DIRECTORY) {
            if (StringUtils.isNotBlank(strVal)) {
              Path p = Path.of(strVal.trim());
              if (!p.isAbsolute()) {
                // Convert relative path to absolute against parent workspace
                value = parentWorkspace.resolve(p).normalize().toString();
              }
            }
          }
        }
      }
      normalized.put(key, value);
    }

    return normalized;
  }

  // =========================================================================
  // EXECUTION API
  // =========================================================================
  @Override
  public synchronized void start() {
    if (this.subAgent == null) {
      setup();
    }
    if (this.subAgent != null) {
      this.subAgent.start();

      // Synchronize active tools whitelist in ReasoningService
      ReasoningService reasoning = (ReasoningService) this.subAgent.getService(ReasoningService.NAME);
      if (reasoning != null) {
        List<String> allowedTools = definition.getTools();
        for (AgentTool tool : reasoning.getAvailableTools()) {
          boolean active = allowedTools.contains(tool.getName());
          reasoning.setToolActive(tool.getName(), active);
        }
      }
    }
  }

  @Override
  public String run(Map<String, ?> params) {
    this.status = Status.RUNNING;
    this.lastError = null;
    this.lastResponse = null;

    try {
      start();

      ReasoningServiceImpl reasoningService = (ReasoningServiceImpl) this.subAgent.getService(ReasoningService.NAME);
      if (reasoningService == null) {
        throw new IllegalStateException("ReasoningService is not available in subagent");
      }

      Map<String, Object> resolvedParams = normalizeParams(params);

      // --- FASE 1: Exploracion e Ingesta ---
      String promptIni = definition.resolvePromptIni(resolvedParams);
      subConsole.printSystemLog(String.format("Starting Subagent '%s' (ID: %d) Phase 1...", definition.getName(), this.id));

      ConsumableSensorEvent eventPhase1 = createUserEvent(promptIni);
      reasoningService.processSingleEvent(eventPhase1);
      extractLastResponse();

      // --- FASE 2: Sintesis y Entrega (Opcional) ---
      if (definition.hasPromptFin()) {
        String promptFin = definition.resolvePromptFin(resolvedParams);
        subConsole.printSystemLog(String.format("Starting Subagent '%s' (ID: %d) Phase 2...", definition.getName(), this.id));

        ConsumableSensorEvent eventPhase2 = createUserEvent(promptFin);
        reasoningService.processSingleEvent(eventPhase2);
        extractLastResponse();
      }

      this.status = Status.FINISHED;
      subConsole.printSystemLog(String.format("Subagent '%s' (ID: %d) completed successfully.", definition.getName(), this.id));
      return this.lastResponse;

    } catch (Throwable e) {
      this.status = Status.FAILED;
      this.lastError = e;
      subConsole.printSystemError(String.format("Subagent '%s' (ID: %d) execution failed: %s", definition.getName(), this.id, e.getMessage()));
      throw new RuntimeException("Subagent execution failed: " + e.getMessage(), e);
    } finally {
      close();
    }
  }

  @Override
  public int launch(Map<String, ?> params) {
    final String originSubchannel = parent.getCurrentSubchannel();
    final int subagentId = this.id;
    final String subagentName = definition.getName();

    AgentLocator.getAgentManager().registerSubagent(this);

    String threadName = String.format("Subagent-%s-%d", subagentName, subagentId);
    this.workerThread = Thread.ofPlatform().name(threadName).start(() -> {
      try {
        String result = run(params);
        String notification = String.format(
                "El subagente '%s' (ID: %d) ha finalizado su tarea con éxito.\nResultado: %s",
                subagentName, subagentId, StringUtils.abbreviate(result, 200)
        );
        parent.putEvent(SYSTEMNOTIFICATION_SENSOR_NAME, originSubchannel, "SUBAGENT_COMPLETED", PRIORITY_NORMAL, notification);
      } catch (Throwable t) {
        LOGGER.warn("Background subagent '{}' (ID: {}) failed", subagentName, subagentId, t);
        String errorNotification = String.format(
                "El subagente '%s' (ID: %d) ha fallado con error: %s",
                subagentName, subagentId, t.getMessage()
        );
        parent.putEvent(SYSTEMNOTIFICATION_SENSOR_NAME, originSubchannel, "SUBAGENT_FAILED", PRIORITY_HIGH, errorNotification);
      } finally {
        AgentLocator.getAgentManager().unregisterSubagent(SubagentImpl.this);
      }
    });

    return this.id;
  }

  private ConsumableSensorEvent createUserEvent(String text) {
    SensorInformation userInfo = new SensorInformationImpl(
            AgentImpl.USER_SENSOR_NAME,
            "User",
            SensorNature.USER,
            "User input",
            false
    );
    return new SensorEventUserImpl(
            userInfo,
            DEFAULT_SUBCHANNEL,
            text,
            SensorsService.PRIORITY_NORMAL,
            "ok",
            LocalDateTime.now(),
            null
    );
  }

  private void extractLastResponse() {
    if (this.subAgent == null || this.subAgent.getEpisodicMemory() == null) {
      return;
    }
    List<Turn> turns = this.subAgent.getEpisodicMemory().getUnconsolidatedTurns(DEFAULT_SUBCHANNEL);
    if (turns != null && !turns.isEmpty()) {
      for (int i = turns.size() - 1; i >= 0; i--) {
        Turn t = turns.get(i);
        if (StringUtils.isNotBlank(t.getTextModel())) {
          this.lastResponse = t.getTextModel();
          break;
        }
      }
    }
  }

  // =========================================================================
  // CLEANUP AND DISPOSAL
  // =========================================================================
  @Override
  public synchronized void stop() {
    if (this.subAgent != null) {
      try {
        this.subAgent.stop();
      } catch (Exception e) {
        LOGGER.warn("Error stopping subAgent", e);
      }
    }

    if (this.memoryConn != null) {
      try {
        this.memoryConn.close();
      } catch (SQLException ignored) {
      }
      this.memoryConn = null;
    }

    if (this.servicesConn != null) {
      try {
        this.servicesConn.close();
      } catch (SQLException ignored) {
      }
      this.servicesConn = null;
    }

    if (this.subConsole != null) {
      this.subConsole.close();
    }

    // Clean up temporary workspace directory
    if (Files.exists(this.workspace)) {
      try {
        FileUtils.deleteDirectory(this.workspace.toFile());
      } catch (IOException e) {
        LOGGER.warn("Could not delete temporary subagent workspace: {}", this.workspace, e);
      }
    }

    this.status = Status.CLOSED;
  }

  @Override
  public void close() {
    stop();
  }

  // =========================================================================
  // AGENT INTERFACE DELEGATION
  // =========================================================================
  @Override
  public AgentPaths getPaths() {
    return this.subPaths;
  }

  @Override
  public AgentActions getActions() {
    return this.subAgent != null ? this.subAgent.getActions() : null;
  }

  @Override
  public AgentSettings getSettings() {
    return this.subSettings;
  }

  @Override
  public AgentConsole getConsole(String subchannel) {
    return this.subConsole;
  }

  @Override
  public AgentConsole getCurrentConsole() {
    return this.subConsole;
  }

  @Override
  public EpisodicMemory getEpisodicMemory() {
    return this.subAgent != null ? this.subAgent.getEpisodicMemory() : null;
  }

  @Override
  public void putEvent(String channel, String subchannel, String status, String priority, String eventText) {
    if (this.subAgent != null) {
      this.subAgent.putEvent(channel, subchannel, status, priority, eventText);
    }
  }

  @Override
  public void putUsersMessage(String subchannel, String text, SensorEventCallback callback) {
    if (this.subAgent != null) {
      this.subAgent.putUsersMessage(subchannel, text, callback);
    }
  }

  @Override
  public SensorInformation registerSensor(String channel, String label, SensorNature nature, String description) {
    return this.subAgent != null ? this.subAgent.registerSensor(channel, label, nature, description) : null;
  }

  @Override
  public AgentAccessControl getAccessControl() {
    return this.subAgent != null ? this.subAgent.getAccessControl() : null;
  }

  @Override
  public void setConsole(String subchannel, AgentConsole console) {
    // No-op: subagent uses its own silent file console
  }

  @Override
  public ConnectionSupplier getServicesDatabase() {
    return this.subAgent != null ? this.subAgent.getServicesDatabase() : null;
  }

  @Override
  public ConnectionSupplier getEpisodicMemoryDatabase() {
    return this.subAgent != null ? this.subAgent.getEpisodicMemoryDatabase() : null;
  }

  @Override
  public AgentService getService(String name) {
    return this.subAgent != null ? this.subAgent.getService(name) : null;
  }

  @Override
  public String getResourceAsString(String resname) {
    return this.subAgent != null ? this.subAgent.getResourceAsString(resname) : "";
  }

  @Override
  public ChatModel createChatModel(String name) {
    return this.subAgent != null ? this.subAgent.createChatModel(name) : null;
  }

  @Override
  public ModelParameters getModelParameters(String name) {
    return this.subAgent != null ? this.subAgent.getModelParameters(name) : null;
  }

  @Override
  public String callChatModel(String modelId, String extractPrompt, String docCsv) {
    return this.subAgent != null ? this.subAgent.callChatModel(modelId, extractPrompt, docCsv) : null;
  }

  @Override
  public JsonObject callChatModelAsJson(String modelId, String summaryPrompt, String contents) {
    return this.subAgent != null ? this.subAgent.callChatModelAsJson(modelId, summaryPrompt, contents) : null;
  }

  @Override
  public void installResource(String resPath) {
    if (this.subAgent != null) {
      this.subAgent.installResource(resPath);
    }
  }

  @Override
  public int getConversationContextSize() {
    return this.subAgent != null ? this.subAgent.getConversationContextSize() : 0;
  }

  @Override
  public int estimateTokenCount(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
    return this.subAgent != null ? this.subAgent.estimateTokenCount(messages, toolSpecifications) : 0;
  }

  @Override
  public String getCurrentSubchannel() {
    return this.subAgent != null ? this.subAgent.getCurrentSubchannel() : DEFAULT_SUBCHANNEL;
  }

  @Override
  public void addSharedService(AgentService service) {
    if( this.subAgent==null ) {
      return;
    }
    this.subAgent.addSharedService(service);
  }

  // =========================================================================
  // STATIC NESTED CLASSES
  // =========================================================================
  /**
   * Isolated path resolution for subagents. Redirects getGlobalConfigFolder to
   * var/globalconfig to prevent leaking parent identity.
   */
  private static class SubagentPaths extends AgentPathsImpl {

    public SubagentPaths(Path workspaceFolder) {
      super(workspaceFolder);
    }

    @Override
    public Path getGlobalConfigFolder() {
      if (getAgentFolder() == null) {
        return null;
      }
      return getAgentFolder().resolve(Path.of("var", "globalconfig"));
    }

    @Override
    public void setupHierarchy() {
      super.setupHierarchy();
      Path globalConfig = getGlobalConfigFolder();
      if (globalConfig != null) {
        try {
          Files.createDirectories(globalConfig);
        } catch (IOException e) {
          LOGGER.warn("Could not create subagent globalconfig folder: {}", globalConfig, e);
        }
      }
    }
  }

  /**
   * Silent console that records execution logs to a file in the parent's temp
   * directory.
   */
  private static class SubagentConsole implements AgentConsole, AutoCloseable {

    private final PrintWriter writer;
    private final Path logFilePath;

    public SubagentConsole(Path logFilePath) {
      this.logFilePath = logFilePath;
      PrintWriter pw = null;
      try {
        if (logFilePath.getParent() != null) {
          Files.createDirectories(logFilePath.getParent());
        }
        pw = new PrintWriter(new BufferedWriter(new FileWriter(logFilePath.toFile(), StandardCharsets.UTF_8, true)), true);
      } catch (IOException e) {
        LOGGER.warn("Could not initialize SubagentConsole log file: {}", logFilePath, e);
      }
      this.writer = pw;
    }

    private synchronized void log(String tag, String message) {
      if (this.writer != null && message != null) {
        String timestamp = DateUtils.now();
        this.writer.println(String.format("[%s] [%s] %s", tag, timestamp, message));
      }
    }

    @Override
    public boolean confirm(String message) {
      log("CONFIRM:AUTO_APPROVED", message);
      return true;
    }

    @Override
    public void printSystemError(String message) {
      log("ERR", message);
    }

    @Override
    public void printSystemLog(String message) {
      log("LOG", message);
    }

    @Override
    public void printSystemLog(String message, Format format) {
      log("LOG", message);
    }

    @Override
    public void printUserMessage(String message) {
      log("USR", message);
    }

    @Override
    public void printModelResponse(String message) {
      log("MODEL", message);
    }

    @Override
    public void printModelReasoning(String message) {
      log("THINKING", message);
    }

    @Override
    public void close() {
      if (this.writer != null) {
        this.writer.flush();
        this.writer.close();
      }
    }
  }
}
