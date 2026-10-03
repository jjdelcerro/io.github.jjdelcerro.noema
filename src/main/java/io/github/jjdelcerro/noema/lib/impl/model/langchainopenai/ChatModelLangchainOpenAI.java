package io.github.jjdelcerro.noema.lib.impl.model.langchainopenai;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiChatRequestParameters;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import io.github.jjdelcerro.noema.lib.impl.model.langchain.AbstractLangchainChatModel;
import io.github.jjdelcerro.noema.lib.model.ModelParameters;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author jjdelcerro
 */
public class ChatModelLangchainOpenAI extends AbstractLangchainChatModel {

  private static final Logger LOGGER = LoggerFactory.getLogger(ChatModelLangchainOpenAI.class);
  private static final String[] EXTRA_PARAM_NAMES = {"thinking", "reasoning_effort", "enable_thinking"};

  private ChatModel model;
  private StreamingChatModel streamingModel;

  public ChatModelLangchainOpenAI(ModelParameters parameters) {
    super(parameters);
    this.model = null;
    this.streamingModel = null;
  }

  @Override
  protected synchronized ChatModel getModel() {
    if (this.model == null) {
      this.model = OpenAiChatModel.builder()
              .baseUrl(this.parameters.providerUrl())
              .apiKey(this.parameters.providerApiKey())
              .modelName(this.parameters.modelId())
              .timeout(Duration.ofSeconds(180))
              .returnThinking(true)
              .sendThinking(true)
              .logRequests(false)
              .logResponses(false)
              .build();
    }
    return this.model;
  }

  @Override
  protected synchronized StreamingChatModel getStreamingModel() {
    if (this.streamingModel == null) {
      this.streamingModel = OpenAiStreamingChatModel.builder()
              .baseUrl(this.parameters.providerUrl())
              .apiKey(this.parameters.providerApiKey())
              .modelName(this.parameters.modelId())
              .timeout(Duration.ofSeconds(180))
              .returnThinking(true)
              .sendThinking(true)
              .logRequests(false)
              .logResponses(false)
              .build();
    }
    return this.streamingModel;
  }

  @Override
  protected ChatRequest createChatRequest(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
    OpenAiChatRequestParameters.Builder paramsBuilder = OpenAiChatRequestParameters.builder()
            .modelName(this.parameters.modelId())
            .temperature(this.parameters.temperature());

    Map<String, Object> customParams = new HashMap<>();
    for (String key : EXTRA_PARAM_NAMES) {
      Object value = this.parameters.getExtraParamValue(key);
      if (value != null) {
        customParams.put(key, value);
      }
    }
    if (!customParams.isEmpty()) {
      paramsBuilder.customParameters(customParams);
    }
    if (toolSpecifications != null && !toolSpecifications.isEmpty()) {
      paramsBuilder.toolSpecifications(toolSpecifications);
    }

    ChatRequestParameters params = paramsBuilder.build();
    return ChatRequest.builder()
            .messages(messages)
            .parameters(params)
            .build();
  }
}
