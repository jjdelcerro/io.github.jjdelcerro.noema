package io.github.jjdelcerro.noema.lib.impl;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.jjdelcerro.noema.lib.Agent;
import org.apache.commons.lang3.StringUtils;

/**
 *
 * @author jjdelcerro
 */
public class ModelParametersImpl implements Agent.ModelParameters {

  private final String providerUrl;
  private final String providerApiKey;
  private double temperature;
  private String modelId;
  private int contextSize;

  public ModelParametersImpl(
          String providerUrl,
          String providerApiKey,
          String modelId,
          double temperature
  ) {
    this.providerUrl = providerUrl;
    this.providerApiKey = providerApiKey;
    this.temperature = temperature;
    if( StringUtils.startsWith(modelId, "{") ) {
      parseModel(modelId);
    } else {
      this.modelId = modelId;
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

  private void parseModel(String modelInfo) {
    Gson gson = new Gson();
    JsonObject json = gson.fromJson(modelInfo, JsonObject.class);
    this.modelId = json.get("model").getAsString();
    JsonElement x = json.get("context");
    if( x!=null ) {
      this.contextSize = x.getAsInt();
    }
    if( Double.isNaN(temperature) ) {
      x = json.get("temperature");
      if( x!=null ) {
        this.temperature = x.getAsDouble();
      }
    }
  }

}
