package io.github.jjdelcerro.noema.lib.impl.model.langchainopenai;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.CompleteToolCall;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.PartialToolCall;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiChatRequestParameters;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.output.Response;
import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.model.ModelStreamCallback;
import io.github.jjdelcerro.noema.lib.model.ModelParameters;
import io.github.jjdelcerro.noema.lib.impl.model.AbstractChatModel;
import io.github.jjdelcerro.noema.lib.impl.model.InterruptedModelGenerateException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author jjdelcerro
 */
public class ChatModelLangchainOpenAI extends AbstractChatModel {

  private static final Logger LOGGER = LoggerFactory.getLogger(ChatModelLangchainOpenAI.class);

  private static final String[] EXTRA_PARAM_NAMES = {"thinking", "reasoning_effort", "enable_thinking"};
  private static final long STALL_CHECK_INTERVAL_MILLIS = 20_000L; // cada cuanto miramos el stream
  private static final long STALL_REPORT_MILLIS = 90_000L;  // sin eventos durante 90s -> warning + dump

  private ChatModel model;
  private StreamingChatModel streamingModel;

  public ChatModelLangchainOpenAI(ModelParameters parameters) {
    super(parameters);
    this.model = null;
    this.streamingModel = null;
  }

  private ChatModel getModel() {
    if (this.model == null) {
      OpenAiChatModel theModel = OpenAiChatModel.builder()
              .baseUrl(this.parameters.providerUrl())
              .apiKey(this.parameters.providerApiKey())
              .modelName(this.parameters.modelId())
              .timeout(Duration.ofSeconds(180))
              .returnThinking(true)
              .sendThinking(true)
              .logRequests(false)
              .logResponses(false)
              .build();
      this.model = theModel;
    }
    return this.model;
  }

  private StreamingChatModel getStreamingModel() {
    if (this.streamingModel == null) {
      StreamingChatModel theStreamingModel = null;
      theStreamingModel = OpenAiStreamingChatModel.builder()
              .baseUrl(this.parameters.providerUrl())
              .apiKey(this.parameters.providerApiKey())
              .modelName(this.parameters.modelId())
              .timeout(Duration.ofSeconds(180))
              .returnThinking(true)
              .sendThinking(true)
              .logRequests(false)
              .logResponses(false)
              .build();
      this.streamingModel = theStreamingModel;
    }
    return this.streamingModel;
  }

  private ChatRequestParameters createChatRequestParameters(List<ToolSpecification> toolSpecifications) {
    OpenAiChatRequestParameters.Builder params1 = OpenAiChatRequestParameters.builder()
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
      params1.customParameters(customParams);
    }
    if (toolSpecifications != null) {
      params1.toolSpecifications(toolSpecifications);
    }
    return params1.build();
  }

  private ChatRequest createChatRequest(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
    ChatRequestParameters params = createChatRequestParameters(toolSpecifications);
    ChatRequest request = ChatRequest.builder()
            .messages(messages)
            .parameters(params)
            .build();
    return request;
  }

  @Override
  public synchronized Response<AiMessage> generate(ChatMessage systemPrompt, ChatMessage message) {
    ChatRequest request = createChatRequest(List.of(systemPrompt, message), null);
    ChatResponse response = this.getModel().chat(request);
    return Response.from(response.aiMessage(), response.tokenUsage(), response.finishReason());
  }

  @Override
  public synchronized Response<AiMessage> generate(List<ChatMessage> messages) {
    ChatRequest request = createChatRequest(messages, null);
    ChatResponse response = this.getModel().chat(request);
    return Response.from(response.aiMessage(), response.tokenUsage(), response.finishReason());
  }

  @Override
  public synchronized Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
    ChatRequest request = createChatRequest(messages, toolSpecifications);
    ChatResponse response = this.getModel().chat(request);
    return Response.from(response.aiMessage(), response.tokenUsage(), response.finishReason());
  }

  public Response<AiMessage> generate(List<ChatMessage> messages,
          List<ToolSpecification> toolSpecifications,
          MutableBoolean abort,
          ModelStreamCallback streamCallback
  ) throws Throwable {
    try {
      ChatRequest request = createChatRequest(messages, toolSpecifications);
      CompletableFuture<ChatResponse> future = new CompletableFuture<>();
      AtomicLong lastEventTime = new AtomicLong(System.currentTimeMillis());
      AtomicBoolean stallReported = new AtomicBoolean(false);

      StreamingChatResponseHandler handler = new StreamingChatResponseHandler() {
        @Override
        public void onCompleteResponse(ChatResponse completeResponse) {
          lastEventTime.set(System.currentTimeMillis());
          future.complete(completeResponse);
        }

        @Override
        public void onError(Throwable error) {
          lastEventTime.set(System.currentTimeMillis());
          future.completeExceptionally(error);
        }

        @Override
        public void onPartialResponse(String partialResponse) {
          if (streamCallback != null) {
            streamCallback.StreamResponse(partialResponse);
            streamCallback.setStreamingUsed(true);
          }
          lastEventTime.set(System.currentTimeMillis());
          if (abort.isTrue()) {
            future.completeExceptionally(new InterruptedModelGenerateException());
          }
        }

        @Override
        public void onPartialThinking(PartialThinking partialThinking) {
          if (streamCallback != null) {
            streamCallback.StreamReasoning(partialThinking.text());
            streamCallback.setStreamingUsed(true);
          }
          lastEventTime.set(System.currentTimeMillis());
          if (abort.isTrue()) {
            future.completeExceptionally(new InterruptedModelGenerateException());
          }
        }

        @Override
        public void onPartialToolCall(PartialToolCall partialToolCall) {
          lastEventTime.set(System.currentTimeMillis());
          if (abort.isTrue()) {
            future.completeExceptionally(new InterruptedModelGenerateException());
          }
        }

        @Override
        public void onCompleteToolCall(CompleteToolCall completeToolCall) {
          lastEventTime.set(System.currentTimeMillis());
          if (abort.isTrue()) {
            future.completeExceptionally(new InterruptedModelGenerateException());
          }
        }
      };

      if (streamCallback != null) {
        streamCallback.setStreamingUsed(false);
      }
      this.getStreamingModel().chat(request, handler);

      while (abort.isFalse() && !future.isDone()) {
        try {
          future.get(STALL_CHECK_INTERVAL_MILLIS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException te) {
          // El stream sigue abierto. Distinguir una respuesta lenta legitima
          // de un stall silencioso mirando el ultimo evento recibido.
          long idleMillis = System.currentTimeMillis() - lastEventTime.get();
          if (idleMillis >= STALL_REPORT_MILLIS && stallReported.compareAndSet(false, true)) {
            LOGGER.warn("Sin eventos del stream desde hace {} ms. Volcando hilos...", idleMillis);
            dumpAllThreads();
          }

          // LÍMITE DURO DE RECUPERACIÓN (ej. 3 minutos)
          if (idleMillis >= 180_000L) {
            LOGGER.error("Timeout extremo alcanzado. Matando la conexión fantasma.");
            future.completeExceptionally(new RuntimeException("Timeout de lectura del Stream HTTP"));
          }
        } catch (ExecutionException ee) {
          break; // salimos; la excepcion se gestiona despues del bucle
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          LOGGER.warn("generate interrumpido", ie);
          return null;
        }
      }

      if (abort.isTrue()) {
        LOGGER.warn("generate abortado por peticion del usuario");
        return null;
      }

      ChatResponse r;
      try {
        r = future.get();
      } catch (ExecutionException ee) {
        throw ee.getCause();
      } catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
        LOGGER.warn("generate interrumpido", ie);
        return null;
      }

      return Response.from(r.aiMessage(), r.tokenUsage(), r.finishReason());
    } catch (InterruptedException ex) {
      LOGGER.warn("generate interrumpido", ex);
      return null;
    }
  }

}
