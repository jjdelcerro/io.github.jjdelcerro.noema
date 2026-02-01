package io.github.jjdelcerro.chatagent.lib.impl.tools.mail;

import com.google.gson.Gson;
import dev.langchain4j.agent.tool.JsonSchemaProperty;
import dev.langchain4j.agent.tool.ToolSpecification;
import io.github.jjdelcerro.chatagent.lib.tools.AgenteTool;
import java.util.Map;

public class EmailSendTool implements AgenteTool {

  private final EmailService service;
  private final Gson gson = new Gson();

  public EmailSendTool(EmailService service) {
    this.service = service;
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
    return AgenteTool.MODE_WRITE;
  }

  @Override
  public String execute(String args) {
    Map<String, String> map = gson.fromJson(args, Map.class);
    return service.send(map.get("to"), map.get("subject"), map.get("body"));
  }
}
