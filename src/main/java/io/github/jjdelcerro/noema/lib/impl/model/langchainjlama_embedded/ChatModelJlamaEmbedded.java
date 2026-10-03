package io.github.jjdelcerro.noema.lib.impl.model.jlama;

import dev.langchain4j.community.model.jlama.JlamaChatModel;
import dev.langchain4j.community.model.jlama.JlamaStreamingChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import io.github.jjdelcerro.noema.lib.impl.model.AbstractLangchainChatModel;
import io.github.jjdelcerro.noema.lib.model.ModelParameters;
import java.nio.file.Path;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author jjdelcerro
 */
public class ChatModelJlamaEmbedded extends AbstractLangchainChatModel {

  private static final Logger LOGGER = LoggerFactory.getLogger(ChatModelJlamacppEmbedded.class);

  private final Path modelPath;
  private ChatModel model;
  private StreamingChatModel streamingModel;

  public ChatModelJlamacppEmbedded(ModelParameters parameters) {
    super(parameters);
    this.model = null;
    this.streamingModel = null;

    // Idéntico a ONNX: recuperamos la ruta ya resuelta por AgentManagerImpl
    String rawPath = (String) parameters.getExtraParamValue("modelPath");
    if (StringUtils.isBlank(rawPath)) {
      throw new IllegalArgumentException("El parámetro 'modelPath' es obligatorio para el modelo embebido Jlama");
    }
    this.modelPath = Path.of(rawPath);
  }

  private Path resolveModelCacheDir() {
    // Si modelPath apunta al directorio del modelo específico (/ruta/modelos/llama-3.2-1b),
    // el cache dir es su carpeta padre. Si no tiene padre, usamos el propio path.
    return (this.modelPath.getParent() != null) ? this.modelPath.getParent() : this.modelPath;
  }

  private String resolveModelName() {
    // Si parameters.modelId() viene informado (ej. "llama-3.2-1b") lo usamos;
    // si no, tomamos el nombre del directorio final en el filesystem.
    if (StringUtils.isNotBlank(this.parameters.modelId())) {
      return this.parameters.modelId();
    }
    return this.modelPath.getFileName().toString();
  }

  @Override
  protected synchronized ChatModel getModel() {
    if (this.model == null) {
      LOGGER.info("Cargando modelo Jlama síncrono desde: {} (id: {})", resolveModelCacheDir(), resolveModelName());
      JlamaChatModel.JlamaChatModelBuilder builder = JlamaChatModel.builder()
              .modelCachePath(resolveModelCacheDir())
              .modelName(resolveModelName())
              .temperature((float) this.parameters.temperature());

      if (this.parameters.contextSize() > 0) {
        builder.maxTokens(this.parameters.contextSize());
      }

      this.model = builder.build();
    }
    return this.model;
  }

  @Override
  protected synchronized StreamingChatModel getStreamingModel() {
    if (this.streamingModel == null) {
      LOGGER.info("Cargando modelo Jlama streaming desde: {} (id: {})", resolveModelCacheDir(), resolveModelName());
      JlamaStreamingChatModel.JlamaStreamingChatModelBuilder builder = JlamaStreamingChatModel.builder()
              .modelCachePath(resolveModelCacheDir())
              .modelName(resolveModelName())
              .temperature((float) this.parameters.temperature());

      if (this.parameters.contextSize() > 0) {
        builder.maxTokens(this.parameters.contextSize());
      }

      this.streamingModel = builder.build();
    }
    return this.streamingModel;
  }
}
