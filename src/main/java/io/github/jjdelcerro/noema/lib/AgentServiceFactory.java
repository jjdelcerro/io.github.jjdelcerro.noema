package io.github.jjdelcerro.noema.lib;

/**
 *
 * @author jjdelcerro
 */
public interface AgentServiceFactory {
  
  public String getName();
  
  public AgentService createService(Agent agent);
  
  public boolean canStart(AgentSettings settings);
}
