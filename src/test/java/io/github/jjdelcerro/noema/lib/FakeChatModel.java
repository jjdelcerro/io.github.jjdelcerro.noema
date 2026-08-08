package io.github.jjdelcerro.noema.lib;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import io.github.jjdelcerro.noema.lib.impl.ModelParametersImpl;
import org.apache.commons.lang3.mutable.MutableBoolean;

import java.util.List;

public class FakeChatModel implements Agent.ChatModel {

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications, MutableBoolean abort) throws Throwable {
        return Response.from(AiMessage.from("Respuesta por defecto de FakeChatModel"), null, FinishReason.STOP);
    }

    @Override public int getContextSize() { return 128000; }
    @Override public Response<AiMessage> generate(ChatMessage systemPrompt, ChatMessage message) { return null; }
    @Override public Response<AiMessage> generate(List<ChatMessage> messages) { return null; }
    @Override public Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) { return null; }
    @Override public Agent.ModelParameters getParameters() { return new ModelParametersImpl("http://fake", "key", "fake-model", 0.5); }
    @Override public Agent.ModelType getModelType() { return Agent.ModelType.OPENAI; }
}