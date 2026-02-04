package io.github.jjdelcerro.chatagent.lib.impl.services.email.tools;

import io.github.jjdelcerro.chatagent.lib.impl.services.email.EmailService;
import dev.langchain4j.agent.tool.ToolSpecification;
import io.github.jjdelcerro.chatagent.lib.Agent;
import io.github.jjdelcerro.chatagent.lib.AgentTool;

public class EmailListTool implements AgentTool {

  private final Agent agent;

  public EmailListTool(Agent agent) {
    this.agent = agent;
  }

  @Override
  public ToolSpecification getSpecification() {
    return ToolSpecification.builder()
            .name("email_list_inbox")
            .description("Lista las cabeceras de los últimos 10 correos. Úsalo para identificar qué correos necesitas leer.")
            .build();
  }

  @Override
  public String execute(String args) {
    EmailService service = (EmailService) this.agent.getService(EmailService.NAME);
    return service.listHeaders();
  }
}
