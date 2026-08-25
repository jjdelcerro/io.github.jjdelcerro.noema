package io.github.jjdelcerro.noema.lib.impl.memory.proyected;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.AgentLocator;
import io.github.jjdelcerro.noema.lib.AgentTool;
import io.github.jjdelcerro.noema.lib.impl.DateUtils;
import io.github.jjdelcerro.noema.lib.impl.memory.GsonUtils;
import io.github.jjdelcerro.noema.lib.memory.recent.RecentMemory;
import io.github.jjdelcerro.noema.lib.memory.compacted.CompactedMemory;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import static io.github.jjdelcerro.noema.lib.impl.services.sensors.SensorsServiceImpl.SYSTEMNOTIFICATION_SENSOR_NAME;
import io.github.jjdelcerro.noema.lib.memory.proyected.ProjectedMemory;
import io.github.jjdelcerro.noema.lib.memory.proyected.ProjectedMemoryOperation;
import io.github.jjdelcerro.noema.lib.memory.proyected.ProjectedMemoryOperationFactory;
import static io.github.jjdelcerro.noema.lib.services.sensors.SensorsService.PRIORITY_HIGH;

public class ProjectedMemoryImpl implements ProjectedMemory {

  private static final Logger LOGGER = LoggerFactory.getLogger(ProjectedMemoryImpl.class);

  private final Agent agent;
  private final Function<String, AgentTool> toolSupplier;
  private final String subchannel;
  private final List<ProjectedMemoryOperation> operations;

  private LocalDateTime lastInteractionTime;
  private long lastInteractionTurn;

  public ProjectedMemoryImpl(
          Agent agent,
          Function<String, AgentTool> toolSupplier,
          String subchannel
  ) {
    this.agent = agent;
    this.toolSupplier = toolSupplier;
    this.subchannel = subchannel;
    this.operations = new ArrayList<>();
    this.lastInteractionTurn = 0L;

    this.initOperations();
    this.load();
  }

  private void initOperations() {
    this.operations.clear();
    Collection<ProjectedMemoryOperationFactory> factories = AgentLocator.getAgentManager().getProjectedMemoryOperationFactories();
    if (factories != null) {
      for (ProjectedMemoryOperationFactory factory : factories) {
        try {
          ProjectedMemoryOperation op = factory.create(null);
          if (op != null) {
            this.operations.add(op);
          }
        } catch (Exception ex) {
          LOGGER.error("Error creating operation from factory '{}'", factory.getName(), ex);
        }
      }
    }
    this.operations.sort(Comparator.comparingInt(ProjectedMemoryOperation::getPriority));
  }

  @Override
  public LocalDateTime getLastInteractionTime() {
    return this.lastInteractionTime;
  }

  @Override
  public void setLastInteractionTime(LocalDateTime lastInteractionTime) {
    this.lastInteractionTime = lastInteractionTime;
  }

  @Override
  public long getLastInteractionTurn() {
    return this.lastInteractionTurn;
  }

  @Override
  public void setLastInteractionTurn(long lastInteractionTurn) {
    this.lastInteractionTurn = lastInteractionTurn;
  }

  @Override
  public AgentTool getTool(String name) {
    if (this.toolSupplier == null) {
      return null;
    }
    return this.toolSupplier.apply(name);
  }

  @Override
  public List<ChatMessage> getMessages(
          RecentMemory recentMemory,
          CompactedMemory compactedMemory,
          String systemPrompt
  ) {
    List<ChatMessage> projectedMessages = new ArrayList<>();

    // 1. Capa base: Prompt de sistema + Memoria Compactada (Relato)
    assembleSystemContext(projectedMessages, systemPrompt, compactedMemory);

    // 2. Capa conversacional: Copia de trabajo de los mensajes recientes
    if (recentMemory != null) {
      projectedMessages.addAll(recentMemory.getMessages());
    }

    // 3. Ejecucion secuencial del Pipeline de Operaciones
    List<String> notifications = new ArrayList<>();
    for (ProjectedMemoryOperation op : this.operations) {
      try {
        op.process(this, projectedMessages, notifications);
      } catch (Exception e) {
        LOGGER.error("Error executing operation '{}' on projected memory", op.getName(), e);
      }
    }

    // 4. Inyeccion unificada de notificaciones efimeras si existen
    if (!notifications.isEmpty()) {
      injectUnifiedNotification(projectedMessages, notifications);
    }

    // 5. Volcado de depuracion en disco
    Timestamp tm = Timestamp.from(LocalDateTime.now().toInstant(ZoneOffset.UTC));
    Path debugPath = agent.getPaths().getTempFolder().resolve("context-" + this.subchannel + "-" + tm.toString() + ".json");
    dump(debugPath, projectedMessages);

    this.setLastInteractionTime(LocalDateTime.now());
    return Collections.unmodifiableList(projectedMessages);
  }

  private void assembleSystemContext(List<ChatMessage> projectedMessages, String baseSystemPrompt, CompactedMemory compactedMemory) {
    StringBuilder sb = new StringBuilder();
    if (StringUtils.isNotBlank(baseSystemPrompt)) {
      sb.append(baseSystemPrompt);
    }

    if (compactedMemory != null && StringUtils.isNotBlank(compactedMemory.getText())) {
      sb.append("\n\n## Contexto consolidado de la conversacion\n");
      sb.append("Resumen actualizado hasta: ").append(DateUtils.toString(compactedMemory.getTimestamp())).append(".\n\n");
      sb.append("--- INICIO DEL RELATO ---\n");
      sb.append(compactedMemory.getText()).append("\n");
      sb.append("--- FIN DEL RELATO ---\n");
    }

    if (sb.length() > 0) {
      projectedMessages.add(SystemMessage.from(sb.toString()));
    }
  }

  private void injectUnifiedNotification(List<ChatMessage> projectedMessages, List<String> notifications) {
    StringBuilder contentsBuilder = new StringBuilder();
    for (int i = 0; i < notifications.size(); i++) {
      if (notifications.size() > 1) {
        contentsBuilder.append(i + 1).append(". ");
      }
      contentsBuilder.append(notifications.get(i));
      if (i < notifications.size() - 1) {
        contentsBuilder.append("\n\n");
      }
    }

    Map<String, Object> responseMap = Map.of(
            "event_time", DateUtils.now(),
            "current_time", DateUtils.now(),
            "channel", SYSTEMNOTIFICATION_SENSOR_NAME,
            "status", "ok",
            "priority", PRIORITY_HIGH,
            "contents", contentsBuilder.toString()
    );

    Gson gson = new GsonBuilder().setPrettyPrinting().create();
    ToolExecutionRequest request = ToolExecutionRequest.builder()
            .id("SystemNotice_" + UUID.randomUUID().toString().replace("-", ""))
            .name("pool_event")
            .arguments("{}")
            .build();

    projectedMessages.add(AiMessage.from(request));
    projectedMessages.add(ToolExecutionResultMessage.from(request, gson.toJson(responseMap)));
  }

  // =========================================================================
  // PERSISTENCIA Y CARGA DE ESTADO
  // =========================================================================
  private static class ProjectedMemoryState {

    String lastInteractionTime;
    long lastInteractionTurn;
    Map<String, JsonObject> operations = new LinkedHashMap<>();
  }

  private void load() {
    Path stateFile = agent.getPaths().getDataFolder().resolve("projected_memory_" + subchannel + ".json");
    if (!Files.exists(stateFile)) {
      return;
    }

    try (Reader reader = Files.newBufferedReader(stateFile, StandardCharsets.UTF_8)) {
      Gson gson = new Gson();
      ProjectedMemoryState state = gson.fromJson(reader, ProjectedMemoryState.class);
      if (state != null) {
        if (state.lastInteractionTime != null) {
          this.lastInteractionTime = LocalDateTime.parse(state.lastInteractionTime);
        }
        this.lastInteractionTurn = state.lastInteractionTurn;

        // Restaurar el estado de cada operacion ya instanciada
        if (state.operations != null) {
          for (ProjectedMemoryOperation op : this.operations) {
            JsonObject opState = state.operations.get(op.getName());
            if (opState != null && !opState.isEmpty()) {
              op.restoreState(opState);
            }
          }
        }
      }
    } catch (Exception e) {
      LOGGER.warn("No se pudo cargar el estado de la memoria proyectada para '{}'", subchannel, e);
    }
  }

  @Override
  public void save() {
    Path stateFile = agent.getPaths().getDataFolder().resolve("projected_memory_" + subchannel + ".json");
    ProjectedMemoryState state = new ProjectedMemoryState();

    if (this.lastInteractionTime != null) {
      state.lastInteractionTime = this.lastInteractionTime.toString();
    }
    state.lastInteractionTurn = this.lastInteractionTurn;

    for (ProjectedMemoryOperation op : this.operations) {
      JsonObject opState = op.getState();
      if (opState != null && !opState.isEmpty()) {
        state.operations.put(op.getName(), opState);
      }
    }

    try {
      if (stateFile.getParent() != null) {
        Files.createDirectories(stateFile.getParent());
      }
      try (Writer writer = Files.newBufferedWriter(stateFile, StandardCharsets.UTF_8)) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        gson.toJson(state, writer);
      }
    } catch (IOException e) {
      LOGGER.warn("No se pudo guardar el estado de la memoria proyectada para '{}'", subchannel, e);
    }
  }

  private void dump(Path path, List<ChatMessage> projectedMessages) {
    if (path == null) {
      return;
    }
    try {
      if (path.getParent() != null) {
        Files.createDirectories(path.getParent());
      }

      Gson gson = new GsonBuilder()
              .setPrettyPrinting()
              .registerTypeAdapter(ChatMessage.class, new GsonUtils.ChatMessageAdapter())
              .registerTypeAdapter(Content.class, new GsonUtils.ContentAdapter())
              .enableComplexMapKeySerialization()
              .create();

      try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
        gson.toJson(projectedMessages, writer);
        writer.flush();
      }
    } catch (IOException e) {
      throw new RuntimeException("Error guardando volcado de memoria proyectada: " + e.getMessage(), e);
    }
  }

  public ProjectedMemoryOperation getOperation(String name) {
    if (name == null) {
      return null;
    }
    for (ProjectedMemoryOperation op : this.operations) {
      if( StringUtils.equalsIgnoreCase(name, op.getName())) {
        return op;
      }
    }
    return null;
  }
}
