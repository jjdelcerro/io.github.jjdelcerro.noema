package io.github.jjdelcerro.chatagent.lib.impl.services.conversation.tools.file;

import com.google.gson.Gson;
import static dev.langchain4j.agent.tool.JsonSchemaProperty.description;
import static dev.langchain4j.agent.tool.JsonSchemaProperty.type;
import dev.langchain4j.agent.tool.ToolSpecification;
import io.github.jjdelcerro.chatagent.lib.Agent;
import static io.github.jjdelcerro.chatagent.lib.AgentAccessControl.AccessMode.PATH_ACCESS_READ;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import io.github.jjdelcerro.chatagent.lib.AgentTool;

public class FileReadTool implements AgentTool {
    /*
    TODO: Paginacion?
    */
    private final Gson gson = new Gson();

    private final Agent agent;
    
    public FileReadTool(Agent agent) {
      this.agent = agent;
    }
    
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

            Path filePath = this.agent.getAccessControl().resolvePathOrNull(args.get("path"),PATH_ACCESS_READ);
            if (filePath==null) {
                return gson.toJson(Map.of("status", "error", "message", "Acceso denegado fuera del proyecto"));
            }

            String content = Files.readString(filePath);
            return gson.toJson(Map.of("status", "success", "content", content));
        } catch (Exception e) {
            return gson.toJson(Map.of("status", "error", "message", e.getMessage()));
        }
    }
}
