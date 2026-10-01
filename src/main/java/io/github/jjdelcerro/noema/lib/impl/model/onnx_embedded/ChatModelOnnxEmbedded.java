package io.github.jjdelcerro.noema.lib.impl.model.onnx_embedded;

import ai.onnxruntime.genai.GenAIException;
import ai.onnxruntime.genai.GeneratorParams;
import ai.onnxruntime.genai.SimpleGenAI;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.output.Response;
import io.github.jjdelcerro.noema.lib.model.ModelParameters;
import io.github.jjdelcerro.noema.lib.model.ModelStreamCallback;
import io.github.jjdelcerro.noema.lib.impl.model.AbstractChatModel;
import java.nio.file.Path;
import java.util.List;
import org.apache.commons.lang3.mutable.MutableBoolean;

/**
 *
 * @author jjdelcerro
 */
public class ChatModelOnnxEmbedded extends AbstractChatModel {

  private SimpleGenAI model;
  private final Path modelPath;

  public ChatModelOnnxEmbedded(ModelParameters parameters) {
    super(parameters);
    this.model = null;
    this.modelPath = Path.of((String) parameters.getExtraParamValue("modelPath"));
  }

  private SimpleGenAI getModel() throws GenAIException {
    if (model == null) {
      model = new SimpleGenAI(this.modelPath.toString());
    }
    return model;
  }

  @Override
  public synchronized Response<AiMessage> generate(ChatMessage systemPrompt, ChatMessage message) {
    try {
      return generate(List.of(systemPrompt, message), null, null, null);
    } catch (Throwable ex) {
      throw new RuntimeException(ex);
    }
  }

  @Override
  public synchronized Response<AiMessage> generate(List<ChatMessage> messages) {
    try {
      return generate(messages, null, null, null);
    } catch (Throwable ex) {
      throw new RuntimeException(ex);
    }
  }

  @Override
  public synchronized Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
    try {
      return generate(messages, toolSpecifications, null, null);
    } catch (Throwable ex) {
      throw new RuntimeException(ex);
    }
  }

  @Override
  public synchronized Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications, MutableBoolean abort, ModelStreamCallback streamCallback) throws Throwable {
    ChatRequestOnnx chatRequest = new ChatRequestOnnx()
            .setMessages(messages)
            .setToolsSpecification(toolSpecifications);
    // FIXME: falta gestionar la cancelacion y el stream cuando abort y streamCallback != null
    GeneratorParams params = this.getModel().createGeneratorParams();
    params.setSearchOption("max_length", this.parameters.contextSize());
    params.setSearchOption("temperature", this.parameters.temperature());
    String response_raw = this.getModel().generate(params, chatRequest.rawprompt(), null);

    ChatResponseOnnx chatResponse = new ChatResponseOnnx()
            .setToolsSpecification(toolSpecifications)
            .setRawResponse(response_raw);
    Response<AiMessage> response = Response.from(chatResponse.aiMessage());
    return response;
  }

}
