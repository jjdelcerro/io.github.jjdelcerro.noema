package io.github.jjdelcerro.chatagent.lib.impl.services.conversation;

import com.google.gson.Gson;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;

/**
 *
 * @author jjdelcerro
 */
public class Event {

  private final String channel;
  private final String priority;
  private final String contents;
  private final String event_time;
  private AiMessage aimessage;
  private ToolExecutionResultMessage response;

  public Event(String channel, String priority, String contents) {
    this.event_time = ConversationService.now();
    this.channel = channel;
    this.priority = priority;
    this.contents = contents;
  }

  @Override
  public String toString() {
    return "[channel:" + this.channel
            + ",priority:" + this.priority
            + ",text:" + StringUtils.abbreviate(StringUtils.replace(contents, "\n", " "), 40)
            + "]";
  }

  public final String toJson() {
    Gson gson = new Gson();
    return gson.toJson(Map.of(
            "current_time", ConversationService.now(),
            "event_time", this.event_time,
            "channel", channel,
            "priority", priority,
            "contents", contents
    ));
  }

  private void buildMessages() {
    ToolExecutionRequest request = ToolExecutionRequest.builder()
            .id(channel + "_" + System.currentTimeMillis())
            .name("pool_event")
            .arguments("{}")
            .build();
    this.aimessage = AiMessage.from(request);
    this.response = ToolExecutionResultMessage.from(request, this.toJson());
  }

  public AiMessage getAiMessage() {
    if (this.aimessage == null) {
      buildMessages();
    }
    return this.aimessage;
  }

  public ToolExecutionResultMessage getResponseMessage() {
    if (this.response == null) {
      buildMessages();
    }
    return this.response;
  }

}
