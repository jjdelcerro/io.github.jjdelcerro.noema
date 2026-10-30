package io.github.jjdelcerro.noema.lib.impl.memory.projected.operations;

import com.google.gson.JsonObject;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import io.github.jjdelcerro.noema.lib.api.AgentTool;
import io.github.jjdelcerro.noema.lib.api.memory.projected.ContextZoning;
import io.github.jjdelcerro.noema.lib.api.memory.projected.ContextZoning.ContextZone;
import io.github.jjdelcerro.noema.lib.api.memory.projected.ContextZoning.ContextZoneType;
import io.github.jjdelcerro.noema.lib.api.memory.projected.ProjectedMemory;
import io.github.jjdelcerro.noema.lib.api.memory.projected.ProjectedMemoryOperation;
import io.github.jjdelcerro.noema.lib.impl.services.memory.tools.AnnotateObservationTool;
import io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.file.FileFindTool;
import io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.file.FileFuzzyGrepTool;
import io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.file.FileGrepTool;
import io.github.jjdelcerro.noema.lib.impl.tools.AbstractPaginatedAgentTool;
import org.apache.commons.lang3.StringUtils;

import java.util.*;

public class PendingAnnotationOperation implements ProjectedMemoryOperation {

  private static final int MINIMUM_SIZE_FOR_TRIM = 1024;
  private static final int DEFAULT_BATCH_THRESHOLD = 4; // Tamaño de lote para avisar en bloque
  private static final int PRIORITY = 20;

  private final int minimumSizeForTrim;
  private final int batchThreshold;

  public PendingAnnotationOperation() {
    this(MINIMUM_SIZE_FOR_TRIM, DEFAULT_BATCH_THRESHOLD);
  }

  public PendingAnnotationOperation(int minimumSizeForTrim, int batchThreshold) {
    this.minimumSizeForTrim = minimumSizeForTrim;
    this.batchThreshold = batchThreshold;
  }

  @Override
  public String getName() {
    return "pending_annotation";
  }

  @Override
  public int getPriority() {
    return PRIORITY;
  }

  @Override
  public void process(ProjectedMemory memory, List<ChatMessage> projectedMessages, List<String> notifications) {
    int total = projectedMessages.size();
    ContextZoning zoning = memory.getContextZoning();

    ContextZone noticeZone = zoning.getZone(ContextZoneType.PRUNING_NOTIFICATION_ZONE, total);
    ContextZone accumZone = zoning.getZone(ContextZoneType.PRUNING_NOTIFICATION_ACCUMULATION_ZONE, total);

    if (noticeZone.isEmpty() && accumZone.isEmpty()) {
      return;
    }

    // 1. Localizar el índice del último annotate_observation de cada recurso
    Map<String, Integer> lastAnnotatedIdx = findLastAnnotationIndices(memory, projectedMessages);

    // 2. Extraer recursos pendientes en cada una de las dos zonas
    Set<String> urgentResources = scanPendingInZone(memory, projectedMessages, noticeZone, lastAnnotatedIdx);
    Set<String> accumulatedResources = scanPendingInZone(memory, projectedMessages, accumZone, lastAnnotatedIdx);

    // 3. Regla de disparo:
    //    - Salta si hay urgencia (al menos 1 recurso en PRUNING_NOTICE a punto de podarse)
    //    - O BIEN si se ha acumulado un lote suficiente (>= batchThreshold)
    boolean urgentTrigger = !urgentResources.isEmpty();
    boolean batchTrigger = (urgentResources.size() + accumulatedResources.size()) >= this.batchThreshold;

    if (!urgentTrigger && !batchTrigger) {
      return; // Aún no hay volumen ni urgencia: dejamos trabajar al agente
    }

    // 4. Si se dispara, notificamos en bloque todos los recursos pendientes de ambas zonas
    Set<String> allPending = new LinkedHashSet<>(urgentResources);
    allPending.addAll(accumulatedResources);

    if (allPending.isEmpty()) {
      return;
    }

    String warning = """
[PODA DE CONTEXTO] Datos crudos próximos a eliminarse: {resources_list}
Las anotaciones sobreviven; los datos crudos no.
Anota solo conclusiones/decisiones/conocimiento-derivado irrepetible (no dupliques lo que puedas releer o que forme parte de la conversacion con el usuario).
Herramienta: {annotate_observation}(resource_id, note)
""";
        warning = StringUtils.replace(
                warning,
                "{annotate_observation}", 
                AnnotateObservationTool.TOOL_NAME
        );
        warning = StringUtils.replace(
                warning,
                "{resources_list}", 
                StringUtils.join(allPending, ", ")
        );
        warning = warning.trim();
        notifications.add(warning);
  }

  private Map<String, Integer> findLastAnnotationIndices(ProjectedMemory memory, List<ChatMessage> messages) {
    Map<String, Integer> lastAnnotatedIdx = new HashMap<>();
    for (int i = 0; i < messages.size(); i++) {
      ChatMessage msg = messages.get(i);
      if (msg instanceof ToolExecutionResultMessage toolMsg) {
        AgentTool tool = memory.getTool(toolMsg.toolName());
        if (tool instanceof AnnotateObservationTool annotateTool) {
          String resourceId = annotateTool.getResourceIdFromResultMessage(toolMsg);
          if (StringUtils.isNotBlank(resourceId)) {
            lastAnnotatedIdx.put(resourceId, i);
          }
        }
      }
    }
    return lastAnnotatedIdx;
  }

  private Set<String> scanPendingInZone(
          ProjectedMemory memory,
          List<ChatMessage> messages,
          ContextZone zone,
          Map<String, Integer> lastAnnotatedIdx
  ) {
    if (zone.isEmpty()) {
      return Collections.emptySet();
    }

    Set<String> pending = new LinkedHashSet<>();
    for (int i = zone.min(); i < zone.max(); i++) {
      ChatMessage msg = messages.get(i);
      if (msg instanceof ToolExecutionResultMessage toolMsg) {
        AgentTool tool = memory.getTool(toolMsg.toolName());
        if (tool instanceof AbstractPaginatedAgentTool paginatedTool) {
          String text = toolMsg.text();
          if (text != null && text.length() > this.minimumSizeForTrim) {
            String resourceId = paginatedTool.getResourceIdFromResultMessage(toolMsg);
            // ignorar búferes efímeros de búsqueda que no aportan valor anotar
            if (isIgnorableResource(resourceId)) {
              continue;
            }
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
    return pending;
  }

  private boolean isIgnorableResource(String resourceId) {
    if (resourceId == null) {
      return true;
    }
    String[] ignorableResourcesPrefix = new String[] {
      "tmp://"+FileGrepTool.FILE_GREP_TEMPORAL_PREFIX,
      "tmp://"+FileFuzzyGrepTool.FILE_FUZZYGREP_TEMPORAL_PREFIX,
      "tmp://"+FileFindTool.FILE_FIND_TEMPORAL_PREFIX
    };
    for (String ignorableResourcePrefix : ignorableResourcesPrefix) {
      if( resourceId.startsWith(ignorableResourcePrefix) ) {
        return true;
      }
    }
    return false;
  }

  @Override
  public JsonObject getState() {
    return null;
  }

  @Override
  public void restoreState(JsonObject state) {
  }
}
