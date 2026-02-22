package io.github.jjdelcerro.chatagent.lib.impl.services.conversation.tools.file;

import io.github.jjdelcerro.chatagent.lib.impl.AbstractAgentTool;
import dev.langchain4j.agent.tool.JsonSchemaProperty;
import dev.langchain4j.agent.tool.ToolSpecification;
import io.github.jjdelcerro.chatagent.lib.Agent;
import io.github.jjdelcerro.chatagent.lib.AgentTool;
import io.github.jjdelcerro.chatagent.lib.impl.services.conversation.ConversationService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Herramienta de lectura para salidas de comandos Shell. No acepta rutas de
 * archivos, solo IDs de recursos generados por 'shell_execute'. Delega la
 * lógica de paginación en FileReadTool.
 */
public class ShellReadOutputTool extends AbstractAgentTool {

  public static final String TOOL_NAME = "shell_read_output";

  public ShellReadOutputTool(Agent agent) {
    super(agent);
  }

  @Override
  public ToolSpecification getSpecification() {
    return ToolSpecification.builder()
            .name(TOOL_NAME)
            .description("Lee bloques adicionales de la salida de un comando previo. "
                    + "Úsalo cuando el sistema te informe de que la salida se guardó en un recurso temporal.")
            .addParameter("id", JsonSchemaProperty.STRING,
                    JsonSchemaProperty.description("El ID del recurso de salida (ej: 'out_abc123')."))
            .addParameter("offset", JsonSchemaProperty.INTEGER,
                    JsonSchemaProperty.description("Línea inicial para empezar a leer (0-based)."))
            .addParameter("limit", JsonSchemaProperty.INTEGER,
                    JsonSchemaProperty.description("Máximo de líneas a leer."))
            .build();
  }

  @Override
  public int getMode() {
    return AgentTool.MODE_READ;
  }

  @Override
  public String execute(String jsonArguments) {
    try {
      Map<String, Object> args = gson.fromJson(jsonArguments, Map.class);
      String id = (String) args.get("id");
      int offset = args.get("offset") != null ? ((Double) args.get("offset")).intValue() : 0;
      int limit = args.get("limit") != null ? ((Double) args.get("limit")).intValue() : 1000;

      if (id == null || id.isBlank()) {
        return error("El parámetro 'id' es obligatorio.");
      }

      // 1. Localizar el "Gestor" (ShellExecuteTool) a través del servicio de conversación
      ConversationService conv = (ConversationService) agent.getService(ConversationService.NAME);
      ShellExecuteTool shellTool = (ShellExecuteTool) conv.getAvailableTool(ShellExecuteTool.TOOL_NAME);

      if (shellTool == null) {
        return error("Servicio de ejecución shell no disponible.");
      }

      // 2. Resolver el ID a una ruta física
      Path outputPath = shellTool.getOutputPath(id);

      if (outputPath == null || !Files.exists(outputPath)) {
        return error("El recurso de salida '" + id + "' ha expirado o no existe. "
                + "Es posible que haya sido eliminado por la política de higiene del disco.");
      }

      // 3. Delegar la lectura a FileReadTool
      FileReadTool fileRead = (FileReadTool) conv.getAvailableTool(FileReadTool.TOOL_NAME);

      // Pasamos el ID como 'originalPath' para que los HINTs de paginación
      // sigan usando el ID y no una ruta de archivo.
      Map<String, Object> shellReadOutputArgs = new HashMap<>();      
      shellReadOutputArgs.put("id", id);
      return fileRead.execute(outputPath, id, TOOL_NAME, shellReadOutputArgs, offset, limit);

    } catch (Exception e) {
      LOGGER.warn("Error en ShellReadOutputTool: " + e.getMessage());
      return error("Error al leer salida de comando: " + e.getMessage());
    }
  }
}
