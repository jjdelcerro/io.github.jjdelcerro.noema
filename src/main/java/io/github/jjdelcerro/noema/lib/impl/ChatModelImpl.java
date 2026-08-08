package io.github.jjdelcerro.noema.lib.impl;

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
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiChatRequestParameters;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.output.Response;
import io.github.jjdelcerro.noema.lib.Agent;
import static io.github.jjdelcerro.noema.lib.Agent.ModelType.LLAMA_EMBEDDED;
import static io.github.jjdelcerro.noema.lib.Agent.ModelType.OPENAI;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
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
import org.apache.commons.collections4.map.LRUMap;
import org.apache.commons.lang3.mutable.MutableBoolean;
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
  private static final Map<String, ChatModel> modelsCache = new LRUMap(2);
  private static final Map<String, StreamingChatModel> streamingModelsCache = new LRUMap(2);

  private ChatModel model;
  private StreamingChatModel streamingModel;
  private final Agent.ModelParameters parameters;

  public ChatModelImpl(Agent.ModelParameters parameters) {
    this.model = null;
    this.streamingModel = null;
    this.parameters = parameters;
  }

  private ChatModel getModel() {
    if (this.model == null) {
      if (this.parameters.canCacheTheModel()) {
        String cachekey = this.parameters.getTheKeyToCacheTheModel();
        ChatModel theModel = modelsCache.get(cachekey);
        if (theModel != null) {
          return theModel;
        }
      }
      ChatModel theModel = null;
      switch (this.getModelType()) {
        case OPENAI:
          theModel = OpenAiChatModel.builder()
                  .baseUrl(this.parameters.providerUrl())
                  .apiKey(this.parameters.providerApiKey())
                  .modelName(this.parameters.modelId())
                  .timeout(Duration.ofSeconds(180))
                  .logRequests(false)
                  .logResponses(false)
                  .build();
          break;
        case LLAMA_EMBEDDED:
          JlamaChatModel.JlamaChatModelBuilder builder = JlamaChatModel.builder()
                  .temperature((float) this.parameters.temperature())
                  .threadCount(4)
                  .modelName(this.parameters.modelId());
          if (this.parameters.getWorkingDirectory() != null) {
            builder.workingDirectory(this.parameters.getWorkingDirectory());
          }
          if (this.parameters.getModelCachePath() != null) {
            builder.modelCachePath(this.parameters.getModelCachePath());
          }
          theModel = builder.build();
          break;

      }
      if (this.parameters.canCacheTheModel()) {
        String cachekey = this.parameters.getTheKeyToCacheTheModel();
        modelsCache.put(cachekey, theModel);
      }
      this.model = theModel;
    }
    return this.model;
  }

  private StreamingChatModel getStreamingModel() {
    if (this.streamingModel == null) {
      if (this.parameters.canCacheTheModel()) {
        String cachekey = this.parameters.getTheKeyToCacheTheModel();
        StreamingChatModel theModel = streamingModelsCache.get(cachekey);
        if (theModel != null) {
          return theModel;
        }
      }
      StreamingChatModel theStreamingModel = null;
      switch (this.getModelType()) {
        case OPENAI:
          theStreamingModel = OpenAiStreamingChatModel.builder()
                  .baseUrl(this.parameters.providerUrl())
                  .apiKey(this.parameters.providerApiKey())
                  .modelName(this.parameters.modelId())
                  .timeout(Duration.ofSeconds(180))
                  .logRequests(false)
                  .logResponses(false)
                  .build();
          break;
        case LLAMA_EMBEDDED:
          JlamaStreamingChatModel.JlamaStreamingChatModelBuilder builder = JlamaStreamingChatModel.builder()
                  .temperature((float) this.parameters.temperature())
                  .threadCount(4)
                  .modelName(this.parameters.modelId());
          if (this.parameters.getWorkingDirectory() != null) {
            builder.workingDirectory(this.parameters.getWorkingDirectory());
          }
          if (this.parameters.getModelCachePath() != null) {
            builder.modelCachePath(this.parameters.getModelCachePath());
          }
          theStreamingModel = builder.build();
          break;
      }
      if (this.parameters.canCacheTheModel()) {
        String cachekey = this.parameters.getTheKeyToCacheTheModel();
        streamingModelsCache.put(cachekey, theStreamingModel);
      }
      this.streamingModel = theStreamingModel;
    }
    return this.streamingModel;
  }

  private static final String[] EXTRA_PARAM_NAMES = {"thinking", "reasoning_effort", "enable_thinking"};

  private ChatRequestParameters createChatRequestParameters(List<ToolSpecification> toolSpecifications) {
    switch (this.getModelType()) {
      case OPENAI:
        OpenAiChatRequestParameters.Builder params1 = OpenAiChatRequestParameters.builder()
                .modelName(this.parameters.modelId())
                .temperature(this.parameters.temperature())
        ;
        Map<String, Object> customParams = new HashMap<>();
        for (String key : EXTRA_PARAM_NAMES) {
            Object value = this.parameters.getExtraValue(key);
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

      case LLAMA_EMBEDDED:
      default:
        DefaultChatRequestParameters.Builder<?> params2 = DefaultChatRequestParameters.builder();
        if (toolSpecifications != null) {
          params2.toolSpecifications(toolSpecifications);
        }
        return params2.build();
          
    }
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

//  @Override
//  public synchronized Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications, MutableBoolean abort) throws Throwable {
//    try {
//      ChatRequest request = createChatRequest(messages, toolSpecifications);
//      final Object wait = new Object();
//      synchronized (wait) {
//        MutableObject<Throwable> exception = new MutableObject<>();
//        MutableObject<ChatResponse> response = new MutableObject<>();
//        StreamingChatResponseHandler handler = new StreamingChatResponseHandler() {
//          @Override
//          public void onCompleteResponse(ChatResponse completeResponse) {
//            synchronized (wait) {
//              response.setValue(completeResponse);
//              wait.notifyAll();
//            }
//          }
//
//          @Override
//          public void onError(Throwable error) {
//            synchronized (wait) {
//              exception.setValue(error);
//              wait.notifyAll();
//            }
//          }
//
//          @Override
//          public void onPartialResponse(String partialResponse) {
//            synchronized (wait) {
//              if (abort.isTrue()) {
//                wait.notifyAll();
//                throw new InterruptedModelGenerateException();
//              }
//            }
//          }
//
//          @Override
//          public void onPartialThinking(PartialThinking partialThinking) {
//            synchronized (wait) {
//              if (abort.isTrue()) {
//                wait.notifyAll();
//                throw new InterruptedModelGenerateException();
//              }
//            }
//          }
//
//          @Override
//          public void onPartialToolCall(PartialToolCall partialToolCall) {
//            synchronized (wait) {
//              if (abort.isTrue()) {
//                wait.notifyAll();
//                throw new InterruptedModelGenerateException();
//              }
//            }
//          }
//
//          @Override
//          public void onCompleteToolCall(CompleteToolCall completeToolCall) {
//            synchronized (wait) {
//              if (abort.isTrue()) {
//                wait.notifyAll();
//                throw new InterruptedModelGenerateException();
//              }
//            }
//          }
//        };
//        this.getStreamingModel().chat(request, handler);
//        while (abort.isFalse() && response.getValue() == null && exception.getValue() == null) {
//          wait.wait(20000);
//        }
//        if (abort.isTrue()) {
//          return null;
//        }
//        if (exception.getValue() != null) {
//          throw exception.getValue();
//        }
//        ChatResponse r = response.getValue();
//        return Response.from(r.aiMessage(), r.tokenUsage(), r.finishReason());
//      }
//    } catch (InterruptedException ex) {
//      LOGGER.warn("generate response interrunped", ex);
//      return null;
//    }
//
//  }

  
    private static final long STALL_CHECK_INTERVAL_MILLIS = 20_000L; // cada cuanto miramos el stream
    private static final long STALL_REPORT_MILLIS = 90_000L;  // sin eventos durante 90s -> warning + dump

    public Response<AiMessage> generate(List<ChatMessage> messages,
            List<ToolSpecification> toolSpecifications,
            MutableBoolean abort) throws Throwable {
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
                    lastEventTime.set(System.currentTimeMillis());
                    if (abort.isTrue()) {
                        future.completeExceptionally(new InterruptedModelGenerateException());
                    }
                }

                @Override
                public void onPartialThinking(PartialThinking partialThinking) {
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

    private void dumpAllThreads() {
        try {
            ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
            ThreadInfo[] threadInfos = threadBean.dumpAllThreads(true, true);
            StringBuilder sb = new StringBuilder();
            for (ThreadInfo info : threadInfos) {
                sb.append(info.toString()).append('\n');
            }
            LOGGER.warn("Thread dump:\n{}", sb.toString());
        } catch (Exception e) {
            LOGGER.warn("No se pudo volcar los hilos", e);
        }
    }

  @Override
  public int getContextSize() {
    return this.parameters.contextSize();
  }

  @Override
  public Agent.ModelParameters getParameters() {
    return parameters;
  }

  @Override
  public Agent.ModelType getModelType() {
    return this.parameters.getModelType();
  }

}
