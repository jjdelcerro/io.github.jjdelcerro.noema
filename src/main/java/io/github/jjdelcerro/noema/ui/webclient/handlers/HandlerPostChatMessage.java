package io.github.jjdelcerro.noema.ui.webclient.handlers;

import io.github.jjdelcerro.noema.lib.api.Agent;
import io.javalin.http.Context;
import java.util.Map;

/**
 *
 * @author jjdelcerro
 */
public class HandlerPostChatMessage extends AbstractHandler {
  
  public static class ChatMessageRequest {
    public String message;
  }
  
  public HandlerPostChatMessage(Agent agent) {
    super(agent);
  }

  @Override
  public void handle(Context ctx) throws Exception {
    String terminalId = ctx.pathParam("terminalId");
    ChatMessageRequest body = ctx.bodyAsClass(ChatMessageRequest.class);

    if (body == null || body.message == null || body.message.trim().isEmpty()) {
      ctx.status(400).json(Map.of("status", "error", "message", "El mensaje no puede estar vacío"));
      return;
    }

    this.agent.putUsersMessage(terminalId, body.message, response -> {
      LOGGER.debug("Mensaje del terminal {} procesado asíncronamente.", terminalId);
    });

    ctx.status(202).json(Map.of(
            "status", "accepted",
            "message", "Message enqueued successfully"
    ));
  }
  
}
