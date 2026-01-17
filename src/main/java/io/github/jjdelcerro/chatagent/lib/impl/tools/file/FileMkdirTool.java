package io.github.jjdelcerro.chatagent.lib.impl.tools.file;

import com.google.gson.Gson;
import dev.langchain4j.agent.tool.JsonSchemaProperty;
import dev.langchain4j.agent.tool.ToolSpecification;
import io.github.jjdelcerro.chatagent.lib.tools.AgenteTool;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

public class FileMkdirTool implements AgenteTool {

    private final Path rootPath = Paths.get(".").toAbsolutePath().normalize();
    private final Gson gson = new Gson();

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
        return AgenteTool.MODE_WRITE;
    }
    
    @Override
    public String execute(String jsonArguments) {
        try {
            Map<String, String> args = gson.fromJson(jsonArguments, Map.class);
            String relativePath = args.get("path");

            if (relativePath == null || relativePath.isBlank()) {
                return gson.toJson(Map.of("status", "error", "message", "El parámetro 'path' es obligatorio."));
            }

            Path dirPath = rootPath.resolve(relativePath).normalize();

            // SEGURIDAD: Impedir creación de carpetas fuera del proyecto
            if (!dirPath.startsWith(rootPath)) {
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
