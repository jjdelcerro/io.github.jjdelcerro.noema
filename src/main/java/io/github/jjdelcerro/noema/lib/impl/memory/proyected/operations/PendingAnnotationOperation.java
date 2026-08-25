package io.github.jjdelcerro.noema.lib.impl.memory.proyected.operations;

import com.google.gson.JsonObject;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import io.github.jjdelcerro.noema.lib.AgentTool;
import io.github.jjdelcerro.noema.lib.impl.AbstractPaginatedAgentTool;
import io.github.jjdelcerro.noema.lib.impl.services.memory.tools.AnnotateObservationTool;
import io.github.jjdelcerro.noema.lib.memory.proyected.ProjectedMemory;
import io.github.jjdelcerro.noema.lib.memory.proyected.ProjectedMemoryOperation;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PendingAnnotationOperation implements ProjectedMemoryOperation {

    private static final int DEFAULT_MESSAGES_TO_KEEP = 20;
    private static final int MINIMUM_SIZE_FOR_TRIM = 1024;
    private static final int PRIORITY = 20;

    private final int messagesToKeep;
    private final int minimumSizeForTrim;

    public PendingAnnotationOperation() {
        this(DEFAULT_MESSAGES_TO_KEEP, MINIMUM_SIZE_FOR_TRIM);
    }

    public PendingAnnotationOperation(int messagesToKeep, int minimumSizeForTrim) {
        this.messagesToKeep = messagesToKeep;
        this.minimumSizeForTrim = minimumSizeForTrim;
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
        List<String> pendingResources = detectPendingAnnotationResources(memory, projectedMessages);
        if (pendingResources.isEmpty()) {
            return;
        }

        String warning = StringUtils.replace("""
Has leido informacion de recursos sin extraer y consolidar informacion relevante.
Si hay datos que deban conservarse relacionados con estos recursos usa la herramienta 'annotate_observation' con el parametro 'resource_id' correspondiente.
Los recursos involucrados son: {RESOURCES_LIST}
                """, "{RESOURCES_LIST}", StringUtils.join(pendingResources, ", ")).trim();

        notifications.add(warning);
    }

    private List<String> detectPendingAnnotationResources(ProjectedMemory memory, List<ChatMessage> projectedMessages) {
        int total = projectedMessages.size();
        if (total < this.messagesToKeep) {
            return Collections.emptyList();
        }

        int riskStartIdx = total - this.messagesToKeep;
        int riskEndIdx = total - (this.messagesToKeep / 2);

        // 1. Localizar el indice de la ultima nota registrada para cada recurso
        Map<String, Integer> lastAnnotatedIdx = new HashMap<>();
        for (int i = 0; i < total; i++) {
            ChatMessage msg = projectedMessages.get(i);
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

        // 2. Detectar lecturas de recursos pesados sin anotar en la zona de riesgo
        Set<String> pending = new LinkedHashSet<>();
        for (int i = riskStartIdx; i < riskEndIdx; i++) {
            ChatMessage msg = projectedMessages.get(i);
            if (msg instanceof ToolExecutionResultMessage toolMsg) {
                AgentTool tool = memory.getTool(toolMsg.toolName());
                if (tool instanceof AbstractPaginatedAgentTool paginatedTool) {
                    String text = toolMsg.text();
                    if (text != null && text.length() > this.minimumSizeForTrim) {
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

    @Override
    public JsonObject getState() {
        return null;
    }

    @Override
    public void restoreState(JsonObject state) {
        // Operacion sin estado persistente
    }
}
