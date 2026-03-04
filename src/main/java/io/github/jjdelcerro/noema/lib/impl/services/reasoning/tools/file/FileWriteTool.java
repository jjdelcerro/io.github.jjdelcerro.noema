package io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.file;

import dev.langchain4j.agent.tool.JsonSchemaProperty;
import dev.langchain4j.agent.tool.ToolSpecification;
import io.github.jjdelcerro.noema.lib.Agent;
import static io.github.jjdelcerro.noema.lib.AgentAccessControl.AccessMode.PATH_ACCESS_WRITE;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Map;
import io.github.jjdelcerro.noema.lib.AgentTool;
import io.github.jjdelcerro.noema.lib.impl.AbstractAgentTool;
import io.github.jjdelcerro.javarcs.lib.RCSCommand;
import io.github.jjdelcerro.javarcs.lib.RCSLocator;
import io.github.jjdelcerro.javarcs.lib.RCSManager;
import io.github.jjdelcerro.javarcs.lib.commands.CheckinOptions;

public class FileWriteTool extends AbstractAgentTool {

  public FileWriteTool(Agent agent) {
    super(agent);
  }

  @Override
  public ToolSpecification getSpecification() {
    return ToolSpecification.builder()
            .name("file_write")
            .description("Escribe o sobrescribe un archivo con el contenido proporcionado. Crea carpetas automáticamente.")
            .addParameter("path", JsonSchemaProperty.STRING, JsonSchemaProperty.description("Ruta relativa del archivo (ej: 'src/main/resources/config.json')"))
            .addParameter("content", JsonSchemaProperty.STRING, JsonSchemaProperty.description("Contenido completo que se escribirá en el archivo."))
            .build();
  }

  @Override
  public int getMode() {
    return AgentTool.MODE_WRITE;
  }

  @Override
  public String execute(String jsonArguments) {
    try {
      Map<String, String> args = gson.fromJson(jsonArguments, Map.class);
      String relativePath = args.get("path");
      String content = args.get("content");

      if (relativePath == null || content == null) {
        return gson.toJson(Map.of("status", "error", "message", "Faltan parámetros: path o content"));
      }
      Path filePath = this.agent.getAccessControl().resolvePathOrNull(relativePath,PATH_ACCESS_WRITE);
      if (filePath == null) {
        return gson.toJson(Map.of("status", "error", "message", "Acceso denegado: No se permite escribir fuera del directorio del proyecto."));
      }

      // Crear directorios padre si no existen (comportamiento mkdir -p)
      if (filePath.getParent() != null) {
        Files.createDirectories(filePath.getParent());
      }

      if( Files.exists(filePath) ) {
        RCSManager rcsmanager = RCSLocator.getRCSManager();
        CheckinOptions opciones = rcsmanager.createCheckinOptions(filePath);
        opciones.setAuthor(this.getReasoningService().getModelName());
        opciones.setInit(true);
        RCSCommand ci = rcsmanager.create(opciones);
        ci.execute(opciones);
      }
      // Escritura atómica (opcionalmente podrías usar StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
      Files.writeString(filePath, content, StandardCharsets.UTF_8);

      return gson.toJson(Map.of(
              "status", "success",
              "path", relativePath,
              "bytes_written", content.getBytes(StandardCharsets.UTF_8).length
      ));

    } catch (AccessDeniedException e) {
      return gson.toJson(Map.of("status", "error", "message", "Permiso denegado por el Sistema Operativo."));
    } catch (Exception e) {
      return gson.toJson(Map.of("status", "error", "message", e.getClass().getSimpleName() + ": " + e.getMessage()));
    }
  }
  
}
