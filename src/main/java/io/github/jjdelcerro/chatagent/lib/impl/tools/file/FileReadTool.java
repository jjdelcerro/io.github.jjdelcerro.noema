package io.github.jjdelcerro.chatagent.lib.impl.tools.file;

import com.google.gson.Gson;
import static dev.langchain4j.agent.tool.JsonSchemaProperty.description;
import static dev.langchain4j.agent.tool.JsonSchemaProperty.type;
import dev.langchain4j.agent.tool.ToolSpecification;
import io.github.jjdelcerro.chatagent.lib.tools.AgenteTool;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

public class FileReadTool implements AgenteTool {
    /*
    TODO: Paginacion?
    */
    private final Path rootPath = Paths.get(".").toAbsolutePath().normalize();
    private final Gson gson = new Gson();

    @Override
    public ToolSpecification getSpecification() {
        return ToolSpecification.builder()
                .name("file_read")
                .description("Lee el contenido completo de un archivo.")
                .addParameter("path", type("string"), description("Ruta relativa del archivo"))
                .build();
    }

    @Override
    public String execute(String jsonArguments) {
        try {
            Map<String, String> args = gson.fromJson(jsonArguments, Map.class);
            Path filePath = rootPath.resolve(args.get("path")).normalize();

            // Seguridad: No permitir salir del rootPath
            if (!filePath.startsWith(rootPath)) {
                return gson.toJson(Map.of("status", "error", "message", "Acceso denegado fuera del proyecto"));
            }

            String content = Files.readString(filePath);
            return gson.toJson(Map.of("status", "success", "content", content));
        } catch (Exception e) {
            return gson.toJson(Map.of("status", "error", "message", e.getMessage()));
        }
    }
}
