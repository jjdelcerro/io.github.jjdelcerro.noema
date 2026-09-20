package io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.devel;

import dev.langchain4j.data.message.ToolExecutionResultMessage;
import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.AgentTool;
import io.github.jjdelcerro.noema.lib.impl.AbstractAgentTool;
import io.github.jjdelcerro.noema.lib.impl.ToolSpecificationBuilder;
import io.github.jjdelcerro.noema.lib.memory.projected.ProjectedMemory;
import io.github.jjdelcerro.noema.lib.memory.projected.operations.PinnedTurnsOperation;
import io.github.jjdelcerro.noema.lib.services.reasoning.ReasoningService;

import java.util.Map;

/**
 * Herramienta para descargar el mapa de proyecto activo (project-map.md)
 * liberando el turno fijado en la memoria proyectada y cesando los
 * recordatorios periodicos.
 */
public class UnloadProjectMapTool extends AbstractAgentTool {

  public static final String TOOL_NAME = "unload_project_map";

  public UnloadProjectMapTool(Agent agent) {
    super(agent);
  }

  @Override
  public ToolSpecificationBuilder getSpecification() {
    return ToolSpecificationBuilder.create()
            .name(TOOL_NAME)
            .description("Descarga y retira el mapa de proyecto ('project-map.md') fijado previamente en tu memoria proyectada, "
                    + "liberando espacio en tu ventana de contexto y cesando los recordatorios periodicos. "
                    + "Invocala sin argumentos cuando finalices las tareas de desarrollo sobre el proyecto actual o vayas a cambiar de tarea.");
  }

  @Override
  public int getMode() {
    return AgentTool.MODE_READ;
  }

  @Override
  public int getType() {
    return AgentTool.TYPE_OPERATIONAL;
  }

  @Override
  public String execute(String jsonArguments) {
    try {
      ReasoningService reasoning = (ReasoningService) agent.getService(ReasoningService.NAME);
      if (reasoning == null) {
        return error("ReasoningService no disponible.");
      }

      String subchannel = agent.getCurrentSubchannel();
      ProjectedMemory projectedMemory = reasoning.getProjectedMemory(subchannel);
      if (projectedMemory == null) {
        return error("ProjectedMemory no disponible para el subcanal actual.");
      }

      PinnedTurnsOperation operation = (PinnedTurnsOperation) projectedMemory.getOperation(PinnedTurnsOperation.OPERATION_NAME);
      if (operation == null) {
        return error("Operacion de turnos fijados no encontrada en el pipeline.");
      }

      // Elimina cualquier turno fijado originado por load_project_map
      boolean removed = operation.removePinnedTurn(state -> {
        ToolExecutionResultMessage result = state.getResultMessage();
        return result != null && LoadProjectMapTool.TOOL_NAME.equals(result.toolName());
      });

      if (removed) {
        LOGGER.info("Mapa de proyecto liberado de la memoria proyectada en subcanal '{}'", subchannel);
        return gson.toJson(Map.of(
                "status", "success",
                "message", "El mapa de proyecto ha sido retirado de tu memoria proyectada con exito."
        ));
      } else {
        return gson.toJson(Map.of(
                "status", "success",
                "message", "No habia ningun mapa de proyecto activo fijado en la memoria proyectada."
        ));
      }

    } catch (Exception e) {
      LOGGER.error("Error al descargar el mapa de proyecto", e);
      return error("Error interno al descargar el mapa de proyecto: " + e.getMessage());
    }
  }
}
