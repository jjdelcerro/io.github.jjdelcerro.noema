package io.github.jjdelcerro.noema.lib.impl.memory.projected.operations;

import com.google.gson.JsonObject;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import io.github.jjdelcerro.noema.lib.api.AgentTool;
import io.github.jjdelcerro.noema.lib.api.AgentTool.ToolResult;
import io.github.jjdelcerro.noema.lib.api.AgentTool.TrimResultType;
import io.github.jjdelcerro.noema.lib.api.memory.projected.ContextZoning;
import io.github.jjdelcerro.noema.lib.api.memory.projected.ContextZoning.ContextZone;
import io.github.jjdelcerro.noema.lib.api.memory.projected.ContextZoning.ContextZoneType;
import io.github.jjdelcerro.noema.lib.api.memory.projected.ProjectedMemory;
import io.github.jjdelcerro.noema.lib.api.memory.projected.ProjectedMemoryOperation;
import io.github.jjdelcerro.noema.lib.impl.tools.ToolResultImpl;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

public class TrimmingOperation implements ProjectedMemoryOperation {

  private static final int MINIMUM_SIZE_FOR_TRIM = 1024;
  private static final int DEFAULT_BATCH_THRESHOLD = 4;
  private static final int PRIORITY = 10;

  private final int minimumSizeForTrim;
  private final int batchThreshold;

  private record PruneCandidate(int index, String id, String toolName, ToolResult trimmedResult) {

  }

  public TrimmingOperation() {
    this(MINIMUM_SIZE_FOR_TRIM, DEFAULT_BATCH_THRESHOLD);
  }

  public TrimmingOperation(int minimumSizeForTrim, int batchThreshold) {
    this.minimumSizeForTrim = minimumSizeForTrim;
    this.batchThreshold = batchThreshold;
  }

  @Override
  public String getName() {
    return "trimming";
  }

  @Override
  public int getPriority() {
    return PRIORITY;
  }

  @Override
  public void process(ProjectedMemory memory, List<ChatMessage> projectedMessages, List<String> notifications) {
    // Habria que medir de alguna manera cual de los dos mecanismos es mas eficaz.
    // uno optimiza el uso del contexto para que sea mejor cognitivamente para el LLM
    // eliminando inmediatamente lo que ya se estima que es ruido (pruning_messages_one_by_one)
    // mientras que el otro premia los aciertos de cache en el proveedor manteniendo en
    // el contexto informacion que ya esta catalogada de ruido (pruning_by_accumulating_messages).
    this.pruning_by_accumulating_messages(memory, projectedMessages, notifications);
//    this.pruning_messages_one_by_one(memory, projectedMessages, notifications); 
  }

  private void pruning_messages_one_by_one(ProjectedMemory memory, List<ChatMessage> projectedMessages, List<String> notifications) {
    int total = projectedMessages.size();

    ContextZoning zoning = memory.getContextZoning();
    ContextZone timmingZone = zoning.getZone(ContextZoneType.PRUNING_ZONE, total);

    for (int i = 0; i < total; i++) {
      if (timmingZone.contains(i)) {
        ChatMessage message = projectedMessages.get(i);
        if (message instanceof ToolExecutionResultMessage toolResult) {
          AgentTool tool = memory.getTool(toolResult.toolName());

          if (tool != null) { // TODO: Deberia filtrar solo las tool de tipo OPERACIONAL?
            String text = toolResult.text();
            if (text != null && text.length() > this.minimumSizeForTrim) {
              ToolResult trimmedText = tool.trimResult(text, TrimResultType.Trim);
              if (trimmedText != null) {
                ToolExecutionResultMessage trimmedMessage = ToolResultImpl.toToolExecutionResultMessage(
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
  }

  private void pruning_by_accumulating_messages(ProjectedMemory memory, List<ChatMessage> projectedMessages, List<String> notifications) {
    int total = projectedMessages.size();
    ContextZoning zoning = memory.getContextZoning();

    ContextZone prunableZone = zoning.getZone(ContextZoneType.PRUNING_ZONE, total);
    ContextZone accumZone = zoning.getZone(ContextZoneType.PRUNING_ACCUMULATION_ZONE, total);

    if (prunableZone.isEmpty() && accumZone.isEmpty()) {
      return;
    }

    // 1. Escanear candidatos pendientes en ambas zonas delegando en la propia herramienta
    List<PruneCandidate> unprunedInPrunable = scanCandidatesInZone(memory, projectedMessages, prunableZone);
    List<PruneCandidate> unprunedInAccum = scanCandidatesInZone(memory, projectedMessages, accumZone);

    // 2. Criterio de disparo por lote o urgencia
    boolean urgentTrigger = !unprunedInPrunable.isEmpty();
    boolean batchTrigger = (unprunedInPrunable.size() + unprunedInAccum.size()) >= this.batchThreshold;

    if (!urgentTrigger && !batchTrigger) {
      return; // Conservamos el prefijo intacto para maximizar el Prompt Cache
    }

    // 3. Poda en bloque aplicando los resultados ya calculados
    List<PruneCandidate> allTargets = new ArrayList<>(unprunedInPrunable);
    allTargets.addAll(unprunedInAccum);

    for (PruneCandidate candidate : allTargets) {
      ToolExecutionResultMessage trimmedMessage = ToolResultImpl.toToolExecutionResultMessage(
              candidate.id(),
              candidate.toolName(),
              candidate.trimmedResult()
      );
      projectedMessages.set(candidate.index(), trimmedMessage);
    }
  }

  private List<PruneCandidate> scanCandidatesInZone(ProjectedMemory memory, List<ChatMessage> messages, ContextZone zone) {
    List<PruneCandidate> candidates = new ArrayList<>();
    if (zone.isEmpty()) {
      return candidates;
    }

    for (int i = zone.min(); i < zone.max(); i++) {
      ChatMessage message = messages.get(i);
      if (message instanceof ToolExecutionResultMessage toolResult) {
        PruneCandidate candidate = evaluatePrunable(memory, toolResult, i);
        if (candidate != null) {
          candidates.add(candidate);
        }
      }
    }
    return candidates;
  }

  /**
   * Evalúa si un mensaje es podable consultando a la herramienta. Si la
   * herramienta devuelve un texto distinto al actual, es un candidato a podar.
   */
  private PruneCandidate evaluatePrunable(ProjectedMemory memory, ToolExecutionResultMessage toolResult, int index) {
    String text = toolResult.text();
    if (text == null || text.length() <= this.minimumSizeForTrim) {
      return null;
    }

    AgentTool tool = memory.getTool(toolResult.toolName());
    if (tool == null) {
      return null;
    }

    ToolResult trimmed = tool.trimResult(text, TrimResultType.Trim);
    if (trimmed == null || StringUtils.isBlank(trimmed.text())) {
      return null;
    }

    // Si el texto resultante es idéntico al original, ya estaba podado o no aplica recorte
    if (trimmed.text().equals(text)) {
      return null;
    }

    return new PruneCandidate(index, toolResult.id(), toolResult.toolName(), trimmed);
  }

  @Override
  public JsonObject getState() {
    return null;
  }

  @Override
  public void restoreState(JsonObject state) {
    // Operación completamente funcional y sin estado persistente
  }
}
