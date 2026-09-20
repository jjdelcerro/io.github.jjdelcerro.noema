package io.github.jjdelcerro.noema.lib;

import io.github.jjdelcerro.noema.lib.Agent.ModelParameters;
import java.util.List;

/**
 *
 * @author jjdelcerro
 */
public interface AgentService {

  public String getName();
  
  public AgentServiceFactory getFactory();
  
  public void start();
  
  public void stop();
  
  public boolean canStart();

  public boolean isRunning();

  public void setEnabled(boolean enabled);
  
  public boolean isEnabled();
  
  public ModelParameters getModelParameters(String name);
  
  public List<AgentTool> getTools();
  
}
