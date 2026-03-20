package io.github.jjdelcerro.noema.lib.impl;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.TokenCountEstimator;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.output.Response;
import io.github.jjdelcerro.noema.lib.Agent;
import java.time.Duration;
import java.util.List;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.apache.commons.lang3.mutable.MutableObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author jjdelcerro
 */
public class ChatModelImpl implements Agent.ChatModel {

  private static final Logger LOGGER = LoggerFactory.getLogger(ChatModelImpl.class);

  public static class InterruptedModelGenerateException extends RuntimeException {

  }

  private OpenAiChatModel model;
  private OpenAiStreamingChatModel streamingModel;
  private final Agent.ModelParameters parameters;

  public ChatModelImpl(Agent.ModelParameters parameters) {
    this.model = null;
    this.streamingModel = null;
    this.parameters = parameters;
  }

  private OpenAiChatModel getModel() {
    if (this.model == null) {
      OpenAiChatModel model = OpenAiChatModel.builder()
              .baseUrl(this.parameters.providerUrl())
              .apiKey(this.parameters.providerApiKey())
              .modelName(this.parameters.modelId())
              .temperature(this.parameters.temperature())
              .timeout(Duration.ofSeconds(180))
              .logRequests(false)
              .logResponses(false)
              .build();
      this.model = model;
    }
    return this.model;
  }

  public OpenAiStreamingChatModel getStreamingModel() {
    if (this.streamingModel == null) {
      OpenAiStreamingChatModel streamingModel = OpenAiStreamingChatModel.builder()
              .baseUrl(this.parameters.providerUrl())
              .apiKey(this.parameters.providerApiKey())
              .modelName(this.parameters.modelId())
              .temperature(this.parameters.temperature())
              .timeout(Duration.ofSeconds(180))
              .logRequests(false)
              .logResponses(false)
              .build();
      this.streamingModel = streamingModel;
    }
    return this.streamingModel;
  }

  @Override
  public Response<AiMessage> generate(ChatMessage systemPrompt, ChatMessage message) {
    return this.getModel().generate(systemPrompt, message);
  }

  @Override
  public Response<AiMessage> generate(List<ChatMessage> messages) {
    return this.getModel().generate(messages);
  }

  @Override
  public Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
    return this.getModel().generate(messages, toolSpecifications);
  }

  @Override
  public Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications, MutableBoolean abort) throws Throwable {
    try {
      final Object wait = new Object();
      synchronized (wait) {
        MutableObject<Throwable> exception = new MutableObject<>();
        MutableObject<Response> response = new MutableObject<>();
        StreamingResponseHandler<AiMessage> handler = new StreamingResponseHandler<>() {
          @Override
          public void onNext(String token) {
            synchronized (wait) {
              if (abort.isTrue()) {
                wait.notifyAll();
                throw new InterruptedModelGenerateException();
              }
            }
          }

          @Override
          public void onError(Throwable error) {
            synchronized (wait) {
              exception.setValue(error);
              wait.notifyAll();
            }
          }

          @Override
          public void onComplete(Response<AiMessage> theResponse) {
            synchronized (wait) {
              response.setValue(theResponse);
              wait.notifyAll();
            }
          }
        };
        this.getStreamingModel().generate(messages, toolSpecifications, handler);
        while (abort.isFalse() && response.getValue() == null && exception.getValue() == null) {
          wait.wait(20000);
        }
        if (abort.isTrue()) {
          return null;
        }
        if (exception.getValue() != null) {
          throw exception.getValue();
        }
        return response.getValue();
      }
    } catch (InterruptedException ex) {
      LOGGER.warn("generate response interrunped", ex);
      return null;
    }

  }

  @Override
  public int getContextSize() {
    return this.parameters.contextSize();
  }

  @Override
  public int estimateTokenCount(String text) {
    if (!(this.getModel() instanceof TokenCountEstimator)) {
      return 0;
    }
    TokenCountEstimator tokenCountEstimator = (TokenCountEstimator) this.model;
    return tokenCountEstimator.estimateTokenCount(text);
  }

  @Override
  public int estimateTokenCount(List<ChatMessage> messages) {
    if (!(this.getModel() instanceof TokenCountEstimator)) {
      return 0;
    }
    TokenCountEstimator tokenCountEstimator = (TokenCountEstimator) this.model;
    return tokenCountEstimator.estimateTokenCount(messages);
  }

  @Override
  public Agent.ModelParameters getParameters() {
    return parameters;
  }

}
