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
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileReadTool extends AbstractAgentTool {

    /*
    TODO: Paginacion?
    */
    
    public FileReadTool(Agent agent) {
      super(agent);
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

            Path filePath = this.resolvePathOrNull(args.get("path"));
            if (filePath==null) {
                return error("Acceso denegado fuera del proyecto");
            }

            String content = Files.readString(filePath);
            return gson.toJson(Map.of("status", "success", "content", content));
        } catch (Exception e) {
            LOGGER.warn("Can't read file, arguments="+StringUtils.replace(jsonArguments,"\n"," "),e);
            return error(e.getMessage());
        }
    }
}
