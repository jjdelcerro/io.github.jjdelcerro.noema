package io.github.jjdelcerro.noema.lib.impl.services.email.tools;

import io.github.jjdelcerro.noema.lib.impl.services.email.EmailService;
import io.github.jjdelcerro.noema.lib.Agent;
import java.util.Map;
import io.github.jjdelcerro.noema.lib.impl.AbstractAgentTool;
import io.github.jjdelcerro.noema.lib.impl.ToolSpecificationBuilder;

public class EmailReadTool extends AbstractAgentTool {

  public static final String TOOL_NAME = "email_read";

  public EmailReadTool(Agent agent) {
    super(agent);
  }

  @Override
  public ToolSpecificationBuilder getSpecification() {
    return ToolSpecificationBuilder.create()
            .name(TOOL_NAME)
            .description("Lee el contenido completo y limpio de un correo usando su UID.")
            .addIntegerParameter("uid", false, "El UID del mensaje obtenido de email_list_inbox o de una notificación.");
  }

  @Override
  public String execute(String args) {
    EmailService service = (EmailService) this.agent.getService(EmailService.NAME);
    Map<String, Double> map = gson.fromJson(args, Map.class);
    return service.read(map.get("uid").longValue());
  }
}
