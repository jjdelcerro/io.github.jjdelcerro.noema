package io.github.jjdelcerro.noema.lib.api.model;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.output.Response;
import java.util.List;
import org.apache.commons.lang3.mutable.MutableBoolean;

/**
 *
 * @author jjdelcerro
 */
public interface ChatModel {

  public int getContextSize();

  public Response<AiMessage> generate(ChatMessage systemPrompt, ChatMessage message);

  public Response<AiMessage> generate(List<ChatMessage> messages);

  public Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications);

  public Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications, MutableBoolean abort, ModelStreamCallback streamCallback) throws Throwable;

  public ModelParameters getParameters();

  public ModelType getModelType();
  
}
