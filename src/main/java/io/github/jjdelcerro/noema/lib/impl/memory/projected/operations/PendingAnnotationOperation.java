package io.github.jjdelcerro.noema.lib.impl.memory.projected.operations;

import com.google.gson.JsonObject;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import io.github.jjdelcerro.noema.lib.AgentTool;
import io.github.jjdelcerro.noema.lib.impl.tools.AbstractPaginatedAgentTool;
import io.github.jjdelcerro.noema.lib.impl.services.memory.tools.AnnotateObservationTool;
import io.github.jjdelcerro.noema.lib.memory.projected.ProjectedMemory;
import io.github.jjdelcerro.noema.lib.memory.projected.ProjectedMemoryOperation;
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
        // Estoy indeciso de que version dejar, asi que de momento dejo estas tres aqui
        String warning = """
Hay turnos de herramientas con datos crudos en la ventana de consolidación sin anotar.
Las anotaciones sobreviven a la poda del contexto; las lecturas/greps/finds crudos se eliminarán al salir de la ventana reciente.
Anota solo si hay conocimiento derivado relevante (síntesis, relaciones, decisiones). No dupliques datos que puedas releer.
Herramienta: {annotate_observation}(resource_id, note)
Recursos pendientes: {resources_list}
""";
        warning = """
[PODA DE CONTEXTO] Próxima poda de datos crudos: {resources_list} (las anotaciones sobreviven).
Si hay conclusiones o decisiones que no puedas releer, usa {annotate_observation}(resource_id, note); si no, ignora este aviso.
""";
        warning = """
[PODA DE CONTEXTO] Datos crudos próximos a eliminarse: {resources_list}
Las anotaciones sobreviven; los datos crudos no.
Anota solo conclusiones/decisiones/conocimiento-deribado irrepetible (no dupliques lo que puedas releer).
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
                StringUtils.join(pendingResources, ", ")
        );
        warning = warning.trim();
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
