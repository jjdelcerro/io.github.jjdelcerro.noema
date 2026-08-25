package io.github.jjdelcerro.noema.lib.impl.memory;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageType;
import static dev.langchain4j.data.message.ChatMessageType.AI;
import static dev.langchain4j.data.message.ChatMessageType.SYSTEM;
import static dev.langchain4j.data.message.ChatMessageType.TOOL_EXECUTION_RESULT;
import static dev.langchain4j.data.message.ChatMessageType.USER;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ContentType;
import static dev.langchain4j.data.message.ContentType.IMAGE;
import static dev.langchain4j.data.message.ContentType.TEXT;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import java.lang.reflect.Type;

/**
 *
 * @author jjdelcerro
 */
public class GsonUtils {

  public static class ChatMessageAdapter implements JsonSerializer<ChatMessage>, JsonDeserializer<ChatMessage> {

    @Override
    public JsonElement serialize(ChatMessage src, Type typeOfSrc, JsonSerializationContext context) {
      JsonObject wrapper = new JsonObject();
      wrapper.addProperty("type", src.type().name());
      wrapper.add("data", context.serialize(src, src.getClass()));
      return wrapper;
    }

    @Override
    public ChatMessage deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
      JsonObject wrapper = json.getAsJsonObject();
      String typeStr = wrapper.get("type").getAsString();
      JsonElement data = wrapper.get("data");
      ChatMessageType type = ChatMessageType.valueOf(typeStr);
      Class<? extends ChatMessage> clazz = switch (type) {
        case USER ->
          UserMessage.class;
        case AI ->
          AiMessage.class;
        case SYSTEM ->
          SystemMessage.class;
        case TOOL_EXECUTION_RESULT ->
          ToolExecutionResultMessage.class;
        default ->
          throw new JsonParseException("Unknown message type: " + type);
      };
      return context.deserialize(data, clazz);
    }
  }

  public static class ContentAdapter implements JsonSerializer<Content>, JsonDeserializer<Content> {

    @Override
    public JsonElement serialize(Content src, Type typeOfSrc, JsonSerializationContext context) {
      JsonObject wrapper = new JsonObject();
      wrapper.addProperty("type", src.type().name());
      wrapper.add("data", context.serialize(src, src.getClass()));
      return wrapper;
    }

    @Override
    public Content deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
      JsonObject wrapper = json.getAsJsonObject();
      String typeStr = wrapper.get("type").getAsString();
      JsonElement data = wrapper.get("data");
      ContentType type = ContentType.valueOf(typeStr);
      Class<? extends Content> clazz = switch (type) {
        case TEXT ->
          TextContent.class;
        case IMAGE ->
          ImageContent.class;
        default ->
          throw new JsonParseException("Unknown content type: " + type);
      };
      return context.deserialize(data, clazz);
    }
  }

  
}
