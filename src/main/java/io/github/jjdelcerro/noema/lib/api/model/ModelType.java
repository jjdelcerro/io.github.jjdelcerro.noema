package io.github.jjdelcerro.noema.lib.api.model;

/**
 *
 * @author jjdelcerro
 */
public enum ModelType {
  LANGCHAIN_OPENAI,
  LLAMACPP_EMBEDDED,
  ONNX_EMBEDDED;

  public static ModelType from(String s) {
    return ModelType.valueOf(s.trim().toUpperCase());
  }
}
