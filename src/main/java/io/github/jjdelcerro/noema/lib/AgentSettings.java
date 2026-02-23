package io.github.jjdelcerro.noema.lib;

import java.io.File;

/**
 *
 * @author jjdelcerro
 */
public interface AgentSettings { 
  
  public static final String BRAVE_SEARCH_API_KEY = "BRAVE_SEARCH_API_KEY";
            
  public String getProperty(String name);
  public String setProperty(String name, String value);

  public long getPropertyAsLong(String name, long defaultValue);
    
  public void load(File f);
  public void save();  
}
