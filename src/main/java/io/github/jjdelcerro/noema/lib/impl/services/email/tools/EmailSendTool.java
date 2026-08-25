package io.github.jjdelcerro.noema.lib.impl.services.email.tools;

import io.github.jjdelcerro.noema.lib.impl.services.email.EmailService;
import io.github.jjdelcerro.noema.lib.Agent;
import java.util.Map;
import io.github.jjdelcerro.noema.lib.AgentTool;
import io.github.jjdelcerro.noema.lib.impl.AbstractAgentTool;
import io.github.jjdelcerro.noema.lib.impl.ToolSpecificationBuilder;

public class EmailSendTool extends AbstractAgentTool {

  public static final String TOOL_NAME = "email_send";

  public EmailSendTool(Agent agent) {
    super(agent);
  }
  
  @Override
  public boolean isAvailableByDefault() {
    return false;
  }

  @Override
  public ToolSpecificationBuilder getSpecification() {
    return ToolSpecificationBuilder.create()
            .name(TOOL_NAME)
            .description("Envía un correo electrónico. Úsalo para entregar resultados o responder al usuario.")
            .addStringParameter("to", false, "Destinatario")
            .addStringParameter("subject", false, "Asunto")
            .addStringParameter("body", false, "Contenido del mensaje");
  }

  @Override
  public int getMode() {
    return AgentTool.MODE_WRITE;
  }

  @Override
  public String execute(String args) {
    EmailService service = (EmailService) this.agent.getService(EmailService.NAME);
    Map<String, String> map = gson.fromJson(args, Map.class);
    return service.send(map.get("to"), map.get("subject"), map.get("body"));
  }
}
