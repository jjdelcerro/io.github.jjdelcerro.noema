package io.github.jjdelcerro.chatagent.lib.impl.tools.mail;

import dev.langchain4j.agent.tool.ToolSpecification;
import io.github.jjdelcerro.chatagent.lib.impl.tools.mail.EmailService;
import io.github.jjdelcerro.chatagent.lib.tools.AgenteTool;

public class EmailListTool implements AgenteTool {

    private final EmailService service;

    public EmailListTool(EmailService service) {
        this.service = service;
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
        return service.listHeaders();
    }
}
