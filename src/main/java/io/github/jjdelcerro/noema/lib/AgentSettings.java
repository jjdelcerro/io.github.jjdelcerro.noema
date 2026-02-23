package io.github.jjdelcerro.noema.lib;

import java.io.File;
import java.util.List;

/**
 *
 * @author jjdelcerro
 */
public interface AgentSettings { 
  
  public static final String BRAVE_SEARCH_API_KEY = "BRAVE_SEARCH_API_KEY";
            
  public String getProperty(String name);
  public String setProperty(String name, String value);

  public long getPropertyAsLong(String name, long defaultValue);
    
  public void load();
  public void save();  

  public List<String> getLastWorkspacesPaths();

  public String getLastWorkspacePath();
  
  void setupSettings();
  
  void setupSettings(AgentPaths paths);

  public AgentPaths getPaths();
  
  public void setLastWorkspacePath(String lastWorkspacePath);
}
