package io.github.jjdelcerro.noema.lib.impl.services.email.tools;

import io.github.jjdelcerro.noema.lib.impl.services.email.EmailService;
import dev.langchain4j.agent.tool.JsonSchemaProperty;
import dev.langchain4j.agent.tool.ToolSpecification;
import io.github.jjdelcerro.noema.lib.Agent;
import java.util.Map;
import io.github.jjdelcerro.noema.lib.impl.AbstractAgentTool;

public class EmailReadTool extends AbstractAgentTool {
  public static final String TOOL_NAME = "email_read";

  public EmailReadTool(Agent agent) {
    super(agent);
  }

  @Override
  public ToolSpecification getSpecification() {
    return ToolSpecification.builder()
            .name(TOOL_NAME)
            .description("Lee el contenido completo y limpio de un correo usando su UID.")
            .addParameter("uid", JsonSchemaProperty.INTEGER, JsonSchemaProperty.description("El UID del mensaje obtenido de email_list_inbox o de una notificación."))
            .build();
  }

  @Override
  public String execute(String args) {
    EmailService service = (EmailService) this.agent.getService(EmailService.NAME);
    Map<String, Double> map = gson.fromJson(args, Map.class);
    return service.read(map.get("uid").longValue());
  }
}
