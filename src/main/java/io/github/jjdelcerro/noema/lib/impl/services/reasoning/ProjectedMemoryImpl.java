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
import static io.github.jjdelcerro.noema.lib.services.sensors.SensorsService.PRIORITY_HIGH;
import java.util.function.Function;

public class ProjectedMemoryImpl implements ProjectedMemory {

  private static final int DEFAULT_MESSAGES_TO_KEEP = 20;
  private static final int MINIMUM_SIZE_FOR_TRIM = 1024;

  private final List<ChatMessage> projectedMessages;
  private final List<String> pendingAnnotationResources;
  private final Function<String, AgentTool> toolSupplier;

  public ProjectedMemoryImpl(
          Agent agent,
          Function<String,AgentTool> toolSupplier,
          RecentMemory recentMemory,
          CompactedMemory compactedMemory,
          String systemPrompt) {

    this.toolSupplier = toolSupplier;
    this.projectedMessages = new ArrayList<>();

    // 1. Capa base: Prompt de sistema + Memoria Compactada (Relato)
    assembleSystemContext(systemPrompt, compactedMemory);

    // 2. Capa conversacional: Copia de trabajo de la memoria reciente
    if (recentMemory != null) {
      this.projectedMessages.addAll(recentMemory.getMessages());
    }

    // 3. Amnesia selectiva: Poda de resultados voluminosos fuera de la ventana
    applySelectiveTrimming();

    // 4. Deteccion de recursos huerfanos en zona de riesgo
    this.pendingAnnotationResources = detectPendingAnnotationResources(agent);

    // 5. Inyeccion de notificaciones efimeras (no persisten en RecentMemory)
    injectEphemeralNotifications();
  }

  private void assembleSystemContext(String baseSystemPrompt, CompactedMemory compactedMemory) {
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
      this.projectedMessages.add(SystemMessage.from(sb.toString()));
    }
  }

  private void applySelectiveTrimming() {
    int total = this.projectedMessages.size();
    int safeLimit = total - DEFAULT_MESSAGES_TO_KEEP;

    for (int i = 0; i < total; i++) {
      if (i >= safeLimit) {
        break;
      }

      ChatMessage message = this.projectedMessages.get(i);
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
              this.projectedMessages.set(i, trimmedMessage);
            }
          }
        }
      }
    }
  }
  
  private AgentTool getTool(String name) {
    if( this.toolSupplier == null ) {
      return null;
    }
    return this.toolSupplier.apply(name);
  }

  private List<String> detectPendingAnnotationResources(Agent agent) {
    int total = this.projectedMessages.size();
    int keep = DEFAULT_MESSAGES_TO_KEEP;
    if (total < keep) {
      return Collections.emptyList();
    }

    int riskStartIdx = total - keep;
    int riskEndIdx = total - (keep / 2);

    // Paso 1: Localizar el indice de la ultima nota de cada recurso
    Map<String, Integer> lastAnnotatedIdx = new HashMap<>();
    for (int i = 0; i < total; i++) {
      ChatMessage msg = this.projectedMessages.get(i);
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
      ChatMessage msg = this.projectedMessages.get(i);
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

  private void injectEphemeralNotifications() {
    if (this.pendingAnnotationResources.isEmpty()) {
      return;
    }

    String responseContents = StringUtils.replace("""
Has leido informacion de recursos sin extraer y consolidar informacion relevante. 
Si hay datos que deban conservarse relacionados con estos recursos usa la herramienta 'annotate_observation' con el parametro 'resource_id' correspondiente.
Los recursos involucrados son: {RESOURCES_LIST}
                           """, "{RESOURCES_LIST}", StringUtils.join(this.pendingAnnotationResources, ","));

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

    this.projectedMessages.add(AiMessage.from(request));
    this.projectedMessages.add(ToolExecutionResultMessage.from(request, gson.toJson(responseMap)));
  }

  @Override
  public List<ChatMessage> getMessages() {
    return Collections.unmodifiableList(this.projectedMessages);
  }

  @Override
  public List<String> getPendingAnnotationResources() {
    return Collections.unmodifiableList(this.pendingAnnotationResources);
  }

  @Override
  public void dump(Path path) {
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
        gson.toJson(this.projectedMessages, writer);
        writer.flush();
      }
    } catch (IOException e) {
      throw new RuntimeException("Error guardando volcado de memoria proyectada: " + e.getMessage(), e);
    }
  }
  
}


