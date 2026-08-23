package io.github.jjdelcerro.noema.lib.impl.services.reasoning;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.AgentTool;
import io.github.jjdelcerro.noema.lib.AgentTool.TrimResultType;
import io.github.jjdelcerro.noema.lib.impl.AbstractPaginatedAgentTool;
import io.github.jjdelcerro.noema.lib.impl.DateUtils;
import io.github.jjdelcerro.noema.lib.impl.services.memory.tools.AnnotateObservationTool;
import io.github.jjdelcerro.noema.lib.impl.services.sensors.SensorsServiceImpl;
import static io.github.jjdelcerro.noema.lib.impl.services.sensors.SensorsServiceImpl.SYSTEMCLOCK_SENSOR_NAME;
import io.github.jjdelcerro.noema.lib.persistence.CompactedMemory;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static io.github.jjdelcerro.noema.lib.impl.services.sensors.SensorsServiceImpl.SYSTEMNOTIFICATION_SENSOR_NAME;
import io.github.jjdelcerro.noema.lib.services.sensors.ConsumableSensorEvent;
import io.github.jjdelcerro.noema.lib.services.sensors.SensorsService;
import static io.github.jjdelcerro.noema.lib.services.sensors.SensorsService.PRIORITY_HIGH;
import static io.github.jjdelcerro.noema.lib.services.sensors.SensorsService.PRIORITY_NORMAL;
import java.io.Reader;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProjectedMemoryImpl implements ProjectedMemory {
  private static final Logger LOGGER = LoggerFactory.getLogger(ProjectedMemoryImpl.class);
  
  private static final int DEFAULT_MESSAGES_TO_KEEP = 20;
  private static final int MINIMUM_SIZE_FOR_TRIM = 1024;

  private final Function<String, AgentTool> toolSupplier;
  private final Agent agent;
  private final String subchannel;
  private LocalDateTime lastInteractionTime;

  public ProjectedMemoryImpl(
          Agent agent,
          Function<String, AgentTool> toolSupplier,
          String subchannel
  ) {
    this.subchannel = subchannel;
    this.agent = agent;
    this.toolSupplier = toolSupplier;
    load();
  }

  @Override
  public LocalDateTime getLastInteractionTime() {
    return lastInteractionTime;
  }

  @Override
  public void setLastInteractionTime(LocalDateTime lastInteractionTime) {
    this.lastInteractionTime = lastInteractionTime;
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

  private void applySelectiveTrimming(List<ChatMessage> projectedMessages) {
    int total = projectedMessages.size();
    int safeLimit = total - DEFAULT_MESSAGES_TO_KEEP;

    for (int i = 0; i < total; i++) {
      if (i >= safeLimit) {
        break;
      }

      ChatMessage message = projectedMessages.get(i);
      if (message instanceof ToolExecutionResultMessage toolResult) {
        AgentTool tool = this.getTool(toolResult.toolName());

        if (tool != null) {
          String text = toolResult.text();
          if (text != null && text.length() > MINIMUM_SIZE_FOR_TRIM) {
            String trimmedText = tool.trimResult(text, TrimResultType.Trim);
            if (trimmedText != null) {
              ToolExecutionResultMessage trimmedMessage = ToolExecutionResultMessage.from(
                      toolResult.id(),
                      toolResult.toolName(),
                      trimmedText
              );
              projectedMessages.set(i, trimmedMessage);
            }
          }
        }
      }
    }
  }

  private AgentTool getTool(String name) {
    if (this.toolSupplier == null) {
      return null;
    }
    return this.toolSupplier.apply(name);
  }

  private List<String> detectPendingAnnotationResources(List<ChatMessage> projectedMessages) {
    int total = projectedMessages.size();
    int keep = DEFAULT_MESSAGES_TO_KEEP;
    if (total < keep) {
      return Collections.emptyList();
    }

    int riskStartIdx = total - keep;
    int riskEndIdx = total - (keep / 2);

    // Paso 1: Localizar el indice de la ultima nota de cada recurso
    Map<String, Integer> lastAnnotatedIdx = new HashMap<>();
    for (int i = 0; i < total; i++) {
      ChatMessage msg = projectedMessages.get(i);
      if (msg instanceof ToolExecutionResultMessage toolMsg) {
        AgentTool tool = this.getTool(toolMsg.toolName());
        if (tool instanceof AnnotateObservationTool annotateTool) {
          String resourceId = annotateTool.getResourceIdFromResultMessage(toolMsg);
          if (StringUtils.isNotBlank(resourceId)) {
            lastAnnotatedIdx.put(resourceId, i);
          }
        }
      }
    }

    // Paso 2: Detectar lecturas sin anotar en la zona de riesgo
    Set<String> pending = new LinkedHashSet<>();
    for (int i = riskStartIdx; i < riskEndIdx; i++) {
      ChatMessage msg = projectedMessages.get(i);
      if (msg instanceof ToolExecutionResultMessage toolMsg) {
        AgentTool tool = this.getTool(toolMsg.toolName());
        if (tool instanceof AbstractPaginatedAgentTool paginatedTool) {
          String text = toolMsg.text();
          if (text != null && text.length() > MINIMUM_SIZE_FOR_TRIM) {
            String resourceId = paginatedTool.getResourceIdFromResultMessage(toolMsg);
            if (StringUtils.isNotBlank(resourceId)) {
              int lastAnnotated = lastAnnotatedIdx.getOrDefault(resourceId, -1);
              if (i > lastAnnotated) {
                pending.add(resourceId);
              }
            }
          }
        }
      }
    }

    return new ArrayList<>(pending);
  }

  private void injectPendingAnnotationResources(List<ChatMessage> projectedMessages, List<String> pendingAnnotationResources) {
    if (pendingAnnotationResources.isEmpty()) {
      return;
    }

    String responseContents = StringUtils.replace("""
Has leido informacion de recursos sin extraer y consolidar informacion relevante. 
Si hay datos que deban conservarse relacionados con estos recursos usa la herramienta 'annotate_observation' con el parametro 'resource_id' correspondiente.
Los recursos involucrados son: {RESOURCES_LIST}
                           """, "{RESOURCES_LIST}", StringUtils.join(pendingAnnotationResources, ","));

    Map<String, Object> responseMap = Map.of(
            "event_time", DateUtils.now(),
            "current_time", DateUtils.now(),
            "channel", SYSTEMNOTIFICATION_SENSOR_NAME,
            "status", "ok",
            "priority", PRIORITY_HIGH,
            "contents", responseContents
    );

    Gson gson = new GsonBuilder().setPrettyPrinting().create();
    ToolExecutionRequest request = ToolExecutionRequest.builder()
            .id("AnnotateSuggestion_" + UUID.randomUUID().toString().replace("-", ""))
            .name("pool_event")
            .arguments("{}")
            .build();

    projectedMessages.add(AiMessage.from(request));
    projectedMessages.add(ToolExecutionResultMessage.from(request, gson.toJson(responseMap)));
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

    // 2. Capa conversacional: Copia de trabajo de la memoria reciente
    if (recentMemory != null) {
      projectedMessages.addAll(recentMemory.getMessages());
    }

    // 3. Amnesia selectiva: Poda de resultados voluminosos fuera de la ventana
    applySelectiveTrimming(projectedMessages);

    // 4. Deteccion de recursos huerfanos en zona de riesgo
    List<String> pendingAnnotationResources = detectPendingAnnotationResources(projectedMessages);

    // 5. Deteccion de la necesidad de insertar marca de percepcion temporal
    boolean temporalPerceptionRequired = detectTemporalPerceptionRequired(projectedMessages);

    /*
    TODO: habria que plantearse unir en una sola notificacion todas las inyecciones:
    - Percepcion temporal
    - Recordatorio de anotaciones pendientes
    - En un futuro actividad en otras terminales/subcanales
    
     */
    // 7. Inyeccion de la marca de percepcion temporal
    if (temporalPerceptionRequired) {
      injectTemporalPerception(projectedMessages);
    }

    // 8. Inyeccion de notificaciones efimeras (no persisten en RecentMemory)
    if (!pendingAnnotationResources.isEmpty()) {
      injectPendingAnnotationResources(projectedMessages, pendingAnnotationResources);
    }

    Timestamp tm = Timestamp.from(LocalDateTime.now().toInstant(ZoneOffset.UTC));
    Path debugPath = agent.getPaths().getTempFolder().resolve("context-" + this.subchannel + "-" + tm.toString() + ".json");
    this.dump(debugPath, projectedMessages);

    this.setLastInteractionTime(LocalDateTime.now());
    return Collections.unmodifiableList(projectedMessages);
  }

  private boolean detectTemporalPerceptionRequired(List<ChatMessage> projectedMessages) {
    Duration delta = Duration.between(this.getLastInteractionTime(), LocalDateTime.now());
    if (delta.toHours() < 1) {
      return false;
    }
    boolean required = (this.getLastInteractionTime() != null && !projectedMessages.isEmpty());
    return required;
  }

  private void injectTemporalPerception(List<ChatMessage> projectedMessages) {
    // Introduccion de la percepcion temporal.
    SensorsServiceImpl sensors = (SensorsServiceImpl) agent.getService(SensorsService.NAME);
    String content = "Ha pasado " + DateUtils.timeAgo(this.getLastInteractionTime()) + " desde la última interacción con el usuario.";
    ConsumableSensorEvent timerEvent = sensors.createSensorEvent(
            SYSTEMCLOCK_SENSOR_NAME,
            content,
            this.subchannel,
            PRIORITY_NORMAL,
            "A pasado el tiempo",
            LocalDateTime.now(),
            null
    );
    projectedMessages.add(timerEvent.getChatMessage());
    projectedMessages.add(timerEvent.getResponseMessage());
  }

  public void dump(Path path, List<ChatMessage> projectedMessages) {
    if (path == null) {
      return;
    }
    try {
      if (path.getParent() != null) {
        Files.createDirectories(path.getParent());
      }

      Gson gson = new GsonBuilder()
              .setPrettyPrinting()
              .registerTypeAdapter(ChatMessage.class, new RecentMemoryImpl.ChatMessageAdapter())
              .registerTypeAdapter(Content.class, new RecentMemoryImpl.ContentAdapter())
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

  private static class ProjectedMemoryState {

    String lastInteractionTime; // ISO-8601, ej: "2025-05-20T17:00:00"
  }

  private void load() {
    Path stateFile = agent.getPaths().getDataFolder().resolve("projected_memory_" + subchannel + ".json");
    if (!Files.exists(stateFile)) {
      return;
    }
    try (Reader reader = Files.newBufferedReader(stateFile, StandardCharsets.UTF_8)) {
      Gson gson = new Gson();
      ProjectedMemoryState state = gson.fromJson(reader, ProjectedMemoryState.class);
      if (state != null && state.lastInteractionTime != null) {
        this.lastInteractionTime = LocalDateTime.parse(state.lastInteractionTime);
      }
    } catch (Exception e) {
      LOGGER.warn("No se pudo cargar el estado de la memoria proyectada para '{}'", subchannel, e);
    }
  }

  @Override
  public void save() {
    if (this.lastInteractionTime == null) {
      // No hay nada que guardar
      return;
    }
    Path stateFile = agent.getPaths().getDataFolder().resolve("projected_memory_" + subchannel + ".json");
    ProjectedMemoryState state = new ProjectedMemoryState();
    state.lastInteractionTime = this.lastInteractionTime.toString();
    try (Writer writer = Files.newBufferedWriter(stateFile, StandardCharsets.UTF_8)) {
      Gson gson = new GsonBuilder().setPrettyPrinting().create();
      gson.toJson(state, writer);
    } catch (IOException e) {
      LOGGER.warn("No se pudo guardar el estado de la memoria proyectada para '{}'", subchannel, e);
    }
  }
}
