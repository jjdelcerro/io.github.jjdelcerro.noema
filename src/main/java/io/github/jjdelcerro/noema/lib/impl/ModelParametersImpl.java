package io.github.jjdelcerro.noema.lib.impl;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.jjdelcerro.noema.lib.Agent;
import java.nio.file.Path;
import org.apache.commons.lang3.StringUtils;

/**
 *
 * @author jjdelcerro
 */
public class ModelParametersImpl implements Agent.ModelParameters {

  private static final float DEFAULT_TEMPERATURE = 0.5f;
  
  private final String providerUrl;
  private final String providerApiKey;
  private double temperature;
  private String modelId;
  private int contextSize;
  private Path workingDirectory;
  private Path modelCachePath;

  public ModelParametersImpl(
          String providerUrl,
          String providerApiKey,
          String modelId,
          double temperature
  ) {
    this.workingDirectory = null;
    this.modelCachePath = null;
    this.providerUrl = providerUrl;
    this.providerApiKey = providerApiKey;
    this.temperature = temperature;
    if( StringUtils.startsWith(StringUtils.strip(modelId), "{") ) {
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
    if( Double.isNaN(this.temperature) ) {
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
    if (json.has("workingDirectory")) {
        this.workingDirectory = Path.of(json.get("workingDirectory").getAsString());
    }    
    if (json.has("modelCachePath")) {
        this.modelCachePath = Path.of(json.get("modelCachePath").getAsString());
    }    
  }

  @Override
  public Path getWorkingDirectory() {
    return this.workingDirectory;
  }

  @Override
  public Path getModelCachePath() {
    return this.modelCachePath;
  }

  @Override
  public Agent.ModelType getModelType() {
    if( this.workingDirectory == null ) {
      return Agent.ModelType.OPENAI;
    }
    return Agent.ModelType.LLAMA_EMBEDDED;
  }

  @Override
  public boolean canCacheTheModel() {
    return this.getModelType()==Agent.ModelType.LLAMA_EMBEDDED;
  }

  @Override
  public String getTheKeyToCacheTheModel() {
    return this.workingDirectory.resolve(modelId).toString();
  }
  
}
