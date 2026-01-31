package io.github.jjdelcerro.chatagent.lib;

import java.io.File;

/**
 *
 * @author jjdelcerro
 */
public interface AgentSettings {
  public static final String MEMORY_PROVIDER_URL = "MEMORY_PROVIDER_URL";
  public static final String MEMORY_PROVIDER_API_KEY = "MEMORY_PROVIDER_API_KEY";
  public static final String MEMORY_MODEL_ID = "MEMORY_MODEL_ID";
  public static final String CONVERSATION_PROVIDER_URL = "CONVERSATION_PROVIDER_URL";
  public static final String CONVERSATION_PROVIDER_API_KEY = "CONVERSATION_PROVIDER_API_KEY";
  public static final String CONVERSATION_MODEL_ID = "CONVERSATION_MODEL_ID";
  public static final String BRAVE_SEARCH_API_KEY = "BRAVE_SEARCH_API_KEY";

            
  public String getProperty(String name);
  public String setProperty(String name, String value);
    
  public void load(File f);
  public void save();  
}
