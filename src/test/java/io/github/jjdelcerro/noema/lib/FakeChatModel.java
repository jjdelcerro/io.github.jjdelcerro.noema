package io.github.jjdelcerro.noema.lib;

import io.github.jjdelcerro.noema.lib.model.ChatModel;
import io.github.jjdelcerro.noema.lib.model.ModelParameters;
import io.github.jjdelcerro.noema.lib.model.ModelStreamCallback;
import io.github.jjdelcerro.noema.lib.model.ModelType;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import io.github.jjdelcerro.noema.lib.impl.model.ModelParametersImpl;
import org.apache.commons.lang3.mutable.MutableBoolean;

import java.util.List;

public class FakeChatModel implements ChatModel {

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications, MutableBoolean abort, ModelStreamCallback streamCallback) throws Throwable {
        return Response.from(AiMessage.from("Respuesta por defecto de FakeChatModel"), null, FinishReason.STOP);
    }

    @Override public int getContextSize() { return 128000; }
    @Override public Response<AiMessage> generate(ChatMessage systemPrompt, ChatMessage message) { return null; }
    @Override public Response<AiMessage> generate(List<ChatMessage> messages) { return null; }
    @Override public Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) { return null; }
    @Override public ModelParameters getParameters() { return new ModelParametersImpl("http://fake", "key", "fake-model", 0.5); }
    @Override public ModelType getModelType() { return ModelType.LANGCHAIN_OPENAI; }
}