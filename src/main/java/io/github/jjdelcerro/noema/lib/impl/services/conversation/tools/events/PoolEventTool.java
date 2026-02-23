package io.github.jjdelcerro.noema.lib.impl.services.conversation.tools.events;

import dev.langchain4j.agent.tool.ToolSpecification;
import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.AgentTool;

/**
 * Herramienta de sistema para la gestión de la proactividad.
 * Permite al agente consultar si existen estímulos externos pendientes de procesar.
 */
public class PoolEventTool implements AgentTool {

    public static final String NAME = "pool_event";

    public PoolEventTool(Agent agent) {
        // De momento no necesita estado, pero recibe el agente por consistencia
    }

    @Override
    public ToolSpecification getSpecification() {
        return ToolSpecification.builder()
                .name(NAME)
                .description("Consulta eventos externos pendientes, notificaciones asíncronas o mensajes de sensores del entorno.")
                .build();
    }

    @Override
    public int getType() {
        return AgentTool.TYPE_OPERATIONAL;
    }

    @Override
    public int getMode() {
        // Es de lectura: el agente consulta el estado de su cola de eventos
        return AgentTool.MODE_READ;
    }

    @Override
    public String execute(String jsonArguments) {
        return "{\"status\": \"success\", \"events\": [], \"message\": \"No hay eventos pendientes de procesar.\"}";
    }
}
