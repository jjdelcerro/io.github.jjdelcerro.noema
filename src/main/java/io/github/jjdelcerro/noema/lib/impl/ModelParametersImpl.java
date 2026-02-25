package io.github.jjdelcerro.noema.lib.impl;

import io.github.jjdelcerro.noema.lib.Agent;

/**
 *
 * @author jjdelcerro
 */
public class ModelParametersImpl implements Agent.ModelParameters {

  private final String providerUrl;
  private final String providerApiKey;
  private final String modelId;
  private final double temperature;
  private int contextSize;

  public ModelParametersImpl(
          String providerUrl,
          String providerApiKey,
          String modelId,
          double temperature
  ) {
    this.providerUrl = providerUrl;
    this.providerApiKey = providerApiKey;
    this.modelId = modelId;
    this.temperature = temperature;
  }

  /**
   * @return the providerUrl
   */
  public String providerUrl() {
    return providerUrl;
  }

  /**
   * @return the providerApiKey
   */
  public String providerApiKey() {
    return providerApiKey;
  }

  /**
   * @return the modelId
   */
  public String modelId() {
    return modelId;
  }

  /**
   * @return the temperature
   */
  public double temperature() {
    return temperature;
  }

  /**
   * @return the contextSize
   */
  public int contextSize() {
    return contextSize;
  }

  /**
   * @param contextSize the contextSize to set
   */
  public void setContextSize(int contextSize) {
    this.contextSize = contextSize;
  }

}
