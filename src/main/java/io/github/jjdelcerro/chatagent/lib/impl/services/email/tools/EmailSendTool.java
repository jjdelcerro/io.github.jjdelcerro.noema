package io.github.jjdelcerro.chatagent.lib.impl.services.email.tools;

import io.github.jjdelcerro.chatagent.lib.impl.services.email.EmailService;
import com.google.gson.Gson;
import dev.langchain4j.agent.tool.JsonSchemaProperty;
import dev.langchain4j.agent.tool.ToolSpecification;
import io.github.jjdelcerro.chatagent.lib.Agent;
import java.util.Map;
import io.github.jjdelcerro.chatagent.lib.AgentTool;

public class EmailSendTool implements AgentTool {

  private final Gson gson = new Gson();
  private final Agent agent;

  public EmailSendTool(Agent agent) {
    this.agent = agent;
  }

  @Override
  public ToolSpecification getSpecification() {
    return ToolSpecification.builder()
            .name("email_send")
            .description("Envía un correo electrónico. Úsalo para entregar resultados o responder al usuario.")
            .addParameter("to", JsonSchemaProperty.STRING, JsonSchemaProperty.description("Destinatario"))
            .addParameter("subject", JsonSchemaProperty.STRING, JsonSchemaProperty.description("Asunto"))
            .addParameter("body", JsonSchemaProperty.STRING, JsonSchemaProperty.description("Contenido del mensaje"))
            .build();
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
