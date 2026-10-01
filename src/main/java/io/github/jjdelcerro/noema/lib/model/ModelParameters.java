package io.github.jjdelcerro.noema.lib.model;

import java.util.Map;

/**
 *
 * @author jjdelcerro
 */
public interface ModelParameters {

  public String providerUrl();

  public String providerApiKey();

  public String modelId();

  public double temperature();

  public int contextSize();

  public void setContextSize(int contextSize);

  public ModelType getModelType();

  public Map<String, Object> getExtraParams();

  public Object getExtraParamValue(String paramName);
  
}
