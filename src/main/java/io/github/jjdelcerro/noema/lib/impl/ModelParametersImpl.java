package io.github.jjdelcerro.noema.lib.impl;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.github.jjdelcerro.noema.lib.Agent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private JsonObject extraValues;

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
    this.extraValues = gson.fromJson(modelInfo, JsonObject.class);
    this.modelId = extraValues.get("model").getAsString();
    JsonElement x = extraValues.get("context");
    if( x!=null ) {
      this.contextSize = x.getAsInt();
    }
    if( Double.isNaN(temperature) ) {
      x = extraValues.get("temperature");
      if( x!=null ) {
        this.temperature = x.getAsDouble();
      }
    }
    if (extraValues.has("workingDirectory")) {
        this.workingDirectory = Path.of(extraValues.get("workingDirectory").getAsString());
    }    
    if (extraValues.has("modelCachePath")) {
        this.modelCachePath = Path.of(extraValues.get("modelCachePath").getAsString());
    }    
  }


    /**
     * Devuelve el valor de una clave extra como Object (String, Map, List, etc.)
     * @param name nombre de la clave en el JSON
     * @return el valor convertido a su tipo Java correspondiente, o null si no existe
     */
    public Object getExtraValue(String name) {
        if (this.extraValues == null || !this.extraValues.has(name)) {
            return null;
        }
        JsonElement v = this.extraValues.get(name);
        return jsonElementToObject(v);
    }

    private Object jsonElementToObject(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (element.isJsonPrimitive()) {
            JsonPrimitive primitive = element.getAsJsonPrimitive();
            if (primitive.isBoolean()) {
                return primitive.getAsBoolean();
            } else if (primitive.isNumber()) {
                // Para números, devolvemos Double o Integer según corresponda
                Number number = primitive.getAsNumber();
                if (number instanceof Integer || number instanceof Long) {
                    return number.longValue();
                } else {
                    return number.doubleValue();
                }
            } else if (primitive.isString()) {
                return primitive.getAsString();
            }
        }
        if (element.isJsonObject()) {
            return jsonObjectToMap(element.getAsJsonObject());
        }
        if (element.isJsonArray()) {
            return jsonArrayToList(element.getAsJsonArray());
        }
        return null; // no debería ocurrir
    }

    private Map<String, Object> jsonObjectToMap(JsonObject jsonObject) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
            map.put(entry.getKey(), jsonElementToObject(entry.getValue()));
        }
        return map;
    }

    private List<Object> jsonArrayToList(JsonArray jsonArray) {
        List<Object> list = new ArrayList<>();
        for (JsonElement element : jsonArray) {
            list.add(jsonElementToObject(element));
        }
        return list;
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
