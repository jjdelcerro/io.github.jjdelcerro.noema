package io.github.jjdelcerro.chatagent.lib.impl.services.conversation.tools.file;

import com.google.gson.Gson;
import dev.langchain4j.agent.tool.JsonSchemaProperty;
import dev.langchain4j.agent.tool.ToolSpecification;
import io.github.jjdelcerro.chatagent.lib.Agent;
import static io.github.jjdelcerro.chatagent.lib.AgentAccessControl.AccessMode.PATH_ACCESS_WRITE;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import io.github.jjdelcerro.chatagent.lib.AgentTool;

public class FileMkdirTool implements AgentTool {

  private final Gson gson = new Gson();

  private final Agent agent;

  public FileMkdirTool(Agent agent) {
    this.agent = agent;
  }

  @Override
  public ToolSpecification getSpecification() {
    return ToolSpecification.builder()
            .name("file_mkdir")
            .description("Crea un nuevo directorio o una ruta de directorios completa.")
            .addParameter("path", JsonSchemaProperty.STRING, JsonSchemaProperty.description("Ruta relativa del directorio a crear (ej: 'src/main/resources/data')"))
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

      if (relativePath == null || relativePath.isBlank()) {
        return gson.toJson(Map.of("status", "error", "message", "El parámetro 'path' es obligatorio."));
      }

      Path dirPath = this.agent.getAccessControl().resolvePathOrNull(relativePath,PATH_ACCESS_WRITE);
      if (dirPath == null) {
        return gson.toJson(Map.of("status", "error", "message", "Acceso denegado: No se puede crear directorios fuera de la raíz del proyecto."));
      }

      if (Files.exists(dirPath)) {
        if (Files.isDirectory(dirPath)) {
          return gson.toJson(Map.of("status", "success", "message", "El directorio ya existe."));
        } else {
          return gson.toJson(Map.of("status", "error", "message", "Ya existe un archivo con ese nombre."));
        }
      }

      // Creación recursiva (mkdir -p)
      Files.createDirectories(dirPath);

      return gson.toJson(Map.of(
              "status", "success",
              "path", relativePath,
              "message", "Directorio creado correctamente."
      ));

    } catch (Exception e) {
      return gson.toJson(Map.of("status", "error", "message", e.getMessage()));
    }
  }
}
