package io.github.jjdelcerro.chatagent.lib.impl.tools.mail;

import com.google.gson.Gson;
import dev.langchain4j.agent.tool.JsonSchemaProperty;
import dev.langchain4j.agent.tool.ToolSpecification;
import io.github.jjdelcerro.chatagent.lib.impl.tools.mail.EmailService;
import io.github.jjdelcerro.chatagent.lib.tools.AgenteTool;
import java.util.Map;

public class EmailReadTool implements AgenteTool {

    private final EmailService service;
    private final Gson gson = new Gson();

    public EmailReadTool(EmailService service) {
        this.service = service;
    }

    @Override
    public ToolSpecification getSpecification() {
        return ToolSpecification.builder()
                .name("email_read")
                .description("Lee el contenido completo y limpio de un correo usando su UID.")
                .addParameter("uid", JsonSchemaProperty.INTEGER, JsonSchemaProperty.description("El UID del mensaje obtenido de email_list_inbox o de una notificación."))
                .build();
    }

    @Override
    public String execute(String args) {
        Map<String, Double> map = gson.fromJson(args, Map.class);
        return service.read(map.get("uid").longValue());
    }
}
