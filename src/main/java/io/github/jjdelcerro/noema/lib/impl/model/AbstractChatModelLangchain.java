package io.github.jjdelcerro.noema.lib.impl.model;

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
import dev.langchain4j.model.output.Response;
import io.github.jjdelcerro.noema.lib.api.model.ModelParameters;
import io.github.jjdelcerro.noema.lib.api.model.ModelStreamCallback;
import java.io.IOException;
import java.util.List;
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
 * Clase base para cualquier proveedor que se apoye en la API unificada de
 * LangChain4j. Centraliza las llamadas síncronas, el bucle reactivo de
 * streaming, la detección de bloqueos (stalls) y la cancelación colaborativa.
 */
public abstract class AbstractChatModelLangchain extends AbstractChatModel {

  private static final Logger LOGGER = LoggerFactory.getLogger(AbstractChatModelLangchain.class);

  protected static final long STALL_CHECK_INTERVAL_MILLIS = 20_000L;
  protected static final long STALL_REPORT_MILLIS = 90_000L;
  protected static final long MAX_STALL_TIMEOUT_MILLIS = 180_000L;
  protected static final int MAX_TRANSPORT_RETRIES = 3;
  protected static final long TRANSPORT_RETRY_BASE_MILLIS = 500L;
  private static final int MAX_CAUSE_CHAIN_DEPTH = 16;

  public AbstractChatModelLangchain(ModelParameters parameters) {
    super(parameters);
  }

  /**
   * Proveedor del modelo síncrono subyacente de LangChain4j.
   */
  protected abstract ChatModel getModel();

  /**
   * Proveedor del modelo de streaming subyacente de LangChain4j.
   */
  protected abstract StreamingChatModel getStreamingModel();

  /**
   * Construcción por defecto del ChatRequest estándar de LangChain4j. Las
   * subclases (como OpenAI) pueden sobreescribirlo si requieren parámetros
   * específicos del vendor.
   */
  protected ChatRequest createChatRequest(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
    DefaultChatRequestParameters.Builder paramsBuilder = ChatRequestParameters.builder()
            .modelName(this.parameters.modelId())
            .temperature(this.parameters.temperature());

    if (toolSpecifications != null && !toolSpecifications.isEmpty()) {
      paramsBuilder.toolSpecifications(toolSpecifications);
    }

    return ChatRequest.builder()
            .messages(messages)
            .parameters(paramsBuilder.build())
            .build();
  }

  @Override
  public synchronized Response<AiMessage> generate(ChatMessage systemPrompt, ChatMessage message) {
    return generate(List.of(systemPrompt, message));
  }

  @Override
  public synchronized Response<AiMessage> generate(List<ChatMessage> messages) {
    return generate(messages, null);
  }

  @Override
  public synchronized Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
    ChatRequest request = createChatRequest(messages, toolSpecifications);
    ChatResponse response = this.getModel().chat(request);
    return Response.from(response.aiMessage(), response.tokenUsage(), response.finishReason());
  }

  @Override
  public Response<AiMessage> generate(List<ChatMessage> messages,
          List<ToolSpecification> toolSpecifications,
          MutableBoolean abort,
          ModelStreamCallback streamCallback
  ) throws Throwable {
    ChatRequest request = createChatRequest(messages, toolSpecifications);
    return this.generateWithRetries(request, abort, streamCallback);
  }

  /**
   * Retry loop around a single streaming invocation. A transport failure
   * (connection reset, broken pipe, read timeout) leaves no state behind, so
   * issuing the request again is safe. Two cases are never retried: a user
   * abort, which is control flow rather than an error, and a failure raised
   * after partial output has already reached the user, because a second
   * attempt would duplicate the text already streamed.
   */
  private Response<AiMessage> generateWithRetries(ChatRequest request, MutableBoolean abort,
          ModelStreamCallback streamCallback) throws Throwable {
    for (int attempt = 1; ; attempt++) {
      AtomicBoolean outputEmitted = new AtomicBoolean(false);
      try {
        ChatResponse response = this.generateStreamingAttempt(request, abort, streamCallback, outputEmitted);
        if (response == null) {
          return null;
        }
        return Response.from(response.aiMessage(), response.tokenUsage(), response.finishReason());
      } catch (InterruptedModelGenerateException abortException) {
        LOGGER.warn("generate abortado por peticion del usuario");
        return null;
      } catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
        LOGGER.warn("generate interrumpido", ie);
        return null;
      } catch (Throwable failure) {
        if (!this.isRetryableTransportFailure(failure) || outputEmitted.get()) {
          throw failure;
        }
        if (attempt > MAX_TRANSPORT_RETRIES) {
          LOGGER.error("Fallo transitorio del proveedor persistente tras {} intentos", attempt);
          throw failure;
        }
        long delay = TRANSPORT_RETRY_BASE_MILLIS * attempt;
        LOGGER.warn("Fallo transitorio del proveedor (intento {} de {}): {}. Reintentando en {} ms",
                attempt, MAX_TRANSPORT_RETRIES, failure.getMessage(), delay);
        try {
          Thread.sleep(delay);
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          LOGGER.warn("generate interrumpido durante la espera de reintento", ie);
          return null;
        }
      }
    }
  }

  /**
   * A transport failure surfaces as an IOException somewhere in the cause chain
   * (SocketException, HttpTimeoutException and friends are all IOException).
   * Protocol-level failures -- bad request, authentication, context length --
   * are not, and therefore are never retried.
   */
  private boolean isRetryableTransportFailure(Throwable failure) {
    Throwable current = failure;
    int depth = 0;
    while (current != null && depth < MAX_CAUSE_CHAIN_DEPTH) {
      if (current instanceof IOException) {
        return true;
      }
      current = current.getCause();
      depth++;
    }
    return false;
  }

  /**
   * Runs one streaming invocation and returns its response, or null when the user
   * aborted it. Transport failures are propagated untouched so that the retry
   * loop above can classify them.
   */
  private ChatResponse generateStreamingAttempt(ChatRequest request, MutableBoolean abort,
          ModelStreamCallback streamCallback, AtomicBoolean outputEmitted) throws Throwable {
    try {
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
          outputEmitted.set(true);
          lastEventTime.set(System.currentTimeMillis());
          if (abort != null && abort.isTrue()) {
            future.completeExceptionally(new InterruptedModelGenerateException());
          }
        }

        @Override
        public void onPartialThinking(PartialThinking partialThinking) {
          if (streamCallback != null) {
            streamCallback.StreamReasoning(partialThinking.text());
            streamCallback.setStreamingUsed(true);
          }
          outputEmitted.set(true);
          lastEventTime.set(System.currentTimeMillis());
          if (abort != null && abort.isTrue()) {
            future.completeExceptionally(new InterruptedModelGenerateException());
          }
        }

        @Override
        public void onPartialToolCall(PartialToolCall partialToolCall) {
          lastEventTime.set(System.currentTimeMillis());
          if (abort != null && abort.isTrue()) {
            future.completeExceptionally(new InterruptedModelGenerateException());
          }
        }

        @Override
        public void onCompleteToolCall(CompleteToolCall completeToolCall) {
          lastEventTime.set(System.currentTimeMillis());
          if (abort != null && abort.isTrue()) {
            future.completeExceptionally(new InterruptedModelGenerateException());
          }
        }
      };

      if (streamCallback != null) {
        streamCallback.setStreamingUsed(false);
      }

      this.getStreamingModel().chat(request, handler);

      while ((abort == null || abort.isFalse()) && !future.isDone()) {
        try {
          future.get(STALL_CHECK_INTERVAL_MILLIS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException te) {
          long idleMillis = System.currentTimeMillis() - lastEventTime.get();
          if (idleMillis >= STALL_REPORT_MILLIS && stallReported.compareAndSet(false, true)) {
            LOGGER.warn("Sin eventos de inferencia desde hace {} ms. Volcando hilos...", idleMillis);
            dumpAllThreads();
          }

          if (idleMillis >= MAX_STALL_TIMEOUT_MILLIS) {
            LOGGER.error("Timeout extremo alcanzado en la inferencia.");
            future.completeExceptionally(new RuntimeException("Timeout de lectura en la generación del modelo"));
          }
        } catch (ExecutionException ee) {
          break;
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          LOGGER.warn("generate interrumpido", ie);
          return null;
        }
      }

      if (abort != null && abort.isTrue()) {
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

      return r;
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      LOGGER.warn("generate interrumpido", ex);
      return null;
    }
  }
}
