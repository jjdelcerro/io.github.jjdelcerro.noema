package io.github.jjdelcerro.noema.lib.impl.model.llamacpp;

import dev.langchain4j.community.model.llama.LlamaChatModel;
import dev.langchain4j.community.model.llama.LlamaStreamingChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import io.github.jjdelcerro.noema.lib.impl.model.langchain.AbstractLangchainChatModel;
import io.github.jjdelcerro.noema.lib.model.ModelParameters;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Proveedor embebido local basado en llama.cpp (vía java-llama.cpp) para modelos .gguf.
 *
 * @author jjdelcerro
 */
public class ChatModelLlamacppEmbedded extends AbstractLangchainChatModel {

  private static final Logger LOGGER = LoggerFactory.getLogger(ChatModelLlamacppEmbedded.class);

  private final Path modelPath;
  private ChatModel model;
  private StreamingChatModel streamingModel;


  // {
  //   "type": "LLAMACPP_EMBEDDED",
  //   "modelPath": "${LocalAgentFolder}/models/qwen2.5-1.5b-instruct-q4_k_m.gguf",
  //   "temperature": 0.2,
  //   "context": 4096,
  //   "nThreads": 4
  // }

  public ChatModelLlamacppEmbedded(ModelParameters parameters) {
    super(parameters);
    this.model = null;
    this.streamingModel = null;

    // Recuperamos la ruta del fichero .gguf resuelta previamente por AgentManagerImpl
    String rawPath = (String) parameters.getExtraParamValue("modelPath");
    if (StringUtils.isBlank(rawPath)) {
      throw new IllegalArgumentException("El parámetro 'modelPath' es obligatorio para el modelo LLAMACPP_EMBEDDED");
    }

    this.modelPath = Path.of(rawPath);
    if (!Files.exists(this.modelPath)) {
      LOGGER.warn("El fichero de pesos .gguf no existe en la ruta configurada: {}", this.modelPath.toAbsolutePath());
    }
  }

  @Override
  protected synchronized ChatModel getModel() {
    if (this.model == null) {
      LOGGER.info("Cargando modelo GGUF en memoria (síncrono) desde: {}", this.modelPath.toAbsolutePath());

      LlamaChatModel.LlamaChatModelBuilder builder = LlamaChatModel.builder()
              .modelPath(this.modelPath.toString())
              .temperature(this.parameters.temperature());

      // Si se definió tamaño de contexto (n_ctx) en extraParams o parameters
      if (this.parameters.contextSize() > 0) {
        builder.nCtx(this.parameters.contextSize());
      }

      // Parámetros opcionales ajustables desde extraParams
      Object nThreads = this.parameters.getExtraParamValue("nThreads");
      if (nThreads instanceof Number threads) {
        builder.nThreads(threads.intValue());
      }

      this.model = builder.build();
    }
    return this.model;
  }

  @Override
  protected synchronized StreamingChatModel getStreamingModel() {
    if (this.streamingModel == null) {
      LOGGER.info("Cargando modelo GGUF en memoria (streaming) desde: {}", this.modelPath.toAbsolutePath());

      LlamaStreamingChatModel.LlamaStreamingChatModelBuilder builder = LlamaStreamingChatModel.builder()
              .modelPath(this.modelPath.toString())
              .temperature(this.parameters.temperature());

      if (this.parameters.contextSize() > 0) {
        builder.nCtx(this.parameters.contextSize());
      }

      Object nThreads = this.parameters.getExtraParamValue("nThreads");
      if (nThreads instanceof Number threads) {
        builder.nThreads(threads.intValue());
      }

      this.streamingModel = builder.build();
    }
    return this.streamingModel;
  }
}
