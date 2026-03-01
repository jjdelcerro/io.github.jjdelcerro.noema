package io.github.jjdelcerro.noema.lib.impl.services.scheduler;

import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.AgentService;
import io.github.jjdelcerro.noema.lib.AgentServiceFactory;
import io.github.jjdelcerro.noema.lib.settings.AgentSettings;

/**
 *
 * @author jjdelcerro
 */
public class SchedulerServiceFactory implements AgentServiceFactory {

  @Override
  public String getName() {
    return SchedulerServiceImpl.NAME;
  }

  @Override
  public AgentService createService(Agent agent) {
    return new SchedulerServiceImpl(this, agent);
  }

  @Override
  public boolean canStart(AgentSettings settings) {
    return true;
  }
 
  
}
