package io.github.jjdelcerro.noema.lib.impl;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.TokenCountEstimator;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.output.Response;
import io.github.jjdelcerro.noema.lib.Agent;
import java.util.List;

/**
 *
 * @author jjdelcerro
 */
public class ChatModelImpl implements Agent.ChatModel {

  private final OpenAiChatModel model;
  private final Agent.ModelParameters parameters;

  public ChatModelImpl(OpenAiChatModel model, Agent.ModelParameters parameters) {
    this.model = model;
    this.parameters = parameters;
  }

  @Override
  public Response<AiMessage> generate(ChatMessage systemPrompt, ChatMessage message) {
    return this.model.generate(systemPrompt, message);
  }

  @Override
  public Response<AiMessage> generate(List<ChatMessage> messages) {
    return this.model.generate(messages);
  }

  @Override
  public Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
    return this.model.generate(messages, toolSpecifications);
  }

  @Override
  public int getContextSize() {
    return this.parameters.contextSize();
  }

  @Override
  public int estimateTokenCount(String text) {
    if (this.model == null || !(this.model instanceof TokenCountEstimator)) {
      return 0;
    }
    TokenCountEstimator tokenCountEstimator = (TokenCountEstimator) this.model;
    return tokenCountEstimator.estimateTokenCount(text);
  }

  @Override
  public int estimateTokenCount(List<ChatMessage> messages) {
    if (this.model == null || !(this.model instanceof TokenCountEstimator)) {
      return 0;
    }
    TokenCountEstimator tokenCountEstimator = (TokenCountEstimator) this.model;
    return tokenCountEstimator.estimateTokenCount(messages);
  }
  
}
