package io.github.jjdelcerro.noema.lib.api;

import io.github.jjdelcerro.noema.lib.api.settings.AgentSettings;

/**
 *
 * @author jjdelcerro
 */
public interface AgentServiceFactory {
  
  public String getName();
  
  public AgentService createService(Agent agent);
  
  public boolean canStart(AgentSettings settings);
}
