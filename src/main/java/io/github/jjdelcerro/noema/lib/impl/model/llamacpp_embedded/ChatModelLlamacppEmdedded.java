package io.github.jjdelcerro.noema.lib.impl.model.llamacpp_embedded;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.CompleteToolCall;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.PartialToolCall;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.jlama.JlamaChatModel;
import dev.langchain4j.model.jlama.JlamaStreamingChatModel;
import dev.langchain4j.model.output.Response;
import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.model.ModelStreamCallback;
import io.github.jjdelcerro.noema.lib.model.ModelParameters;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.commons.collections4.map.LRUMap;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.github.jjdelcerro.noema.lib.impl.model.AbstractChatModel;
import io.github.jjdelcerro.noema.lib.impl.model.InterruptedModelGenerateException;
import java.nio.file.Path;

/**
 *
 * @author jjdelcerro
 */
public class ChatModelLlamacppEmdedded extends AbstractChatModel {

  // TODO: Esta clase viene arrastrada de una implementacion anterior.
  // solo me he dedicado a que compile, no ceo que vaya y ahora 
  // mismo no es la prioeridad
  
  private static final Logger LOGGER = LoggerFactory.getLogger(ChatModelLlamacppEmdedded.class);

  private static final Map<String, ChatModel> modelsCache = new LRUMap(2);
  private static final Map<String, StreamingChatModel> streamingModelsCache = new LRUMap(2);

  private ChatModel model;
  private StreamingChatModel streamingModel;

  public ChatModelLlamacppEmdedded(ModelParameters parameters) {
    super(parameters);
    this.model = null;
    this.streamingModel = null;
  }

  private ChatModel getModel() {
    if (this.model == null) {
      if (this.canCacheTheModel()) {
        String cachekey = this.getTheKeyToCacheTheModel();
        ChatModel theModel = modelsCache.get(cachekey);
        if (theModel != null) {
          return theModel;
        }
      }
      ChatModel theModel = null;
      JlamaChatModel.JlamaChatModelBuilder builder = JlamaChatModel.builder()
              .temperature((float) this.parameters.temperature())
              .threadCount(4)
              .modelName(this.parameters.modelId());
      if (this.getWorkingDirectory() != null) {
        builder.workingDirectory(this.getWorkingDirectory());
      }
      if (this.getModelCachePath() != null) {
        builder.modelCachePath(this.getModelCachePath());
      }
      theModel = builder.build();
      if (this.canCacheTheModel()) {
        String cachekey = this.getTheKeyToCacheTheModel();
        modelsCache.put(cachekey, theModel);
      }
      this.model = theModel;
    }
    return this.model;
  }

  private Path getWorkingDirectory() {
    return null; // FIXME: falta por implementar extraerlo de this.parameters
  }

  private Path getModelCachePath() {
    return null; // FIXME: falta por implementar extraerlo de this.parameters
  }

  private boolean canCacheTheModel() {
    return true; // FIXME: falta por implementar extraerlo de this.parameters
  }

  private String getTheKeyToCacheTheModel() {
    return null; // FIXME: falta por implementar extraerlo de this.parameters
  }

  private StreamingChatModel getStreamingModel() {
    if (this.streamingModel == null) {
      if (this.canCacheTheModel()) {
        String cachekey = this.getTheKeyToCacheTheModel();
        StreamingChatModel theModel = streamingModelsCache.get(cachekey);
        if (theModel != null) {
          return theModel;
        }
      }
      StreamingChatModel theStreamingModel = null;
      JlamaStreamingChatModel.JlamaStreamingChatModelBuilder builder = JlamaStreamingChatModel.builder()
              .temperature((float) this.parameters.temperature())
              .threadCount(4)
              .modelName(this.parameters.modelId());
      if (this.getWorkingDirectory() != null) {
        builder.workingDirectory(this.getWorkingDirectory());
      }
      if (this.getModelCachePath() != null) {
        builder.modelCachePath(this.getModelCachePath());
      }
      theStreamingModel = builder.build();
      if (this.canCacheTheModel()) {
        String cachekey = this.getTheKeyToCacheTheModel();
        streamingModelsCache.put(cachekey, theStreamingModel);
      }
      this.streamingModel = theStreamingModel;
    }
    return this.streamingModel;
  }

  private ChatRequestParameters createChatRequestParameters(List<ToolSpecification> toolSpecifications) {
    DefaultChatRequestParameters.Builder<?> params2 = DefaultChatRequestParameters.builder();
    if (toolSpecifications != null) {
      params2.toolSpecifications(toolSpecifications);
    }
    return params2.build();
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

  private static final long STALL_CHECK_INTERVAL_MILLIS = 20_000L; // cada cuanto miramos el stream
  private static final long STALL_REPORT_MILLIS = 90_000L;  // sin eventos durante 90s -> warning + dump

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
