package io.github.jjdelcerro.noema.lib.impl.model;

import io.github.jjdelcerro.noema.lib.model.ModelParameters;
import io.github.jjdelcerro.noema.lib.model.ModelType;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author jjdelcerro
 */
public class ModelParametersImpl implements ModelParameters {

  private static final float DEFAULT_TEMPERATURE = 0.5f;

  final String providerUrl;
  final String providerApiKey;
  double temperature;
  final String modelId;
  int contextSize;
  final Map<String, Object> extraParams;
  final ModelType modelType;

  public ModelParametersImpl(
          String providerUrl,
          String providerApiKey,
          String modelId,
          double temperature
  ) {
    this(providerUrl, providerApiKey, modelId, temperature, new HashMap<>());
  }

  public ModelParametersImpl(
          String providerUrl,
          String providerApiKey,
          String modelId,
          double temperature,
          Map<String, Object> extraParams
  ) {
    this.providerUrl = providerUrl;
    this.providerApiKey = providerApiKey;
    this.temperature = temperature;
    this.modelId = modelId;
    this.extraParams = extraParams;
    this.contextSize = (int) extraParams.getOrDefault("context", -1);
    this.modelType = ModelType.from(
            (String) extraParams.getOrDefault(
                    "type",
                    ModelType.LANGCHAIN_OPENAI.name()
            )
    );
    if (Double.isNaN(temperature)) {
      this.temperature = (double) extraParams.getOrDefault("temperature", DEFAULT_TEMPERATURE);
    }
  }

  /**
   * @return the providerUrl
   */
  @Override
  public String providerUrl() {
    return providerUrl;
  }

  /**
   * @return the providerApiKey
   */
  @Override
  public String providerApiKey() {
    return providerApiKey;
  }

  /**
   * @return the modelId
   */
  @Override
  public String modelId() {
    return modelId;
  }

  /**
   * @return the temperature
   */
  @Override
  public double temperature() {
    if (Double.isNaN(this.temperature)) {
      return DEFAULT_TEMPERATURE;
    }
    return temperature;
  }

  /**
   * @return the contextSize
   */
  @Override
  public int contextSize() {
    return contextSize;
  }

  /**
   * @param contextSize the contextSize to set
   */
  @Override
  public void setContextSize(int contextSize) {
    this.contextSize = contextSize;
  }

  @Override
  public ModelType getModelType() {
    return this.modelType;
  }

  public Map<String, Object> getExtraParams() {
    return extraParams;
  }

  @Override
  public Object getExtraParamValue(String paramName) {
    return this.extraParams.get(paramName);
  }

}
