package io.github.jjdelcerro.noema.lib.impl.services.email.tools;

import io.github.jjdelcerro.noema.lib.impl.services.email.EmailService;
import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.impl.AbstractAgentTool;
import io.github.jjdelcerro.noema.lib.impl.ToolSpecificationBuilder;

public class EmailListTool extends AbstractAgentTool {

  public static final String TOOL_NAME = "email_list_inbox";

  public EmailListTool(Agent agent) {
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
            .description("Lista las cabeceras de los últimos 10 correos. Úsalo para identificar qué correos necesitas leer.");
  }

  @Override
  public String execute(String args) {
    EmailService service = (EmailService) this.agent.getService(EmailService.NAME);
    return service.listHeaders();
  }
}
