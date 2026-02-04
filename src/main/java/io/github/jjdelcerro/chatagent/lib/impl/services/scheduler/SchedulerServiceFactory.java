package io.github.jjdelcerro.chatagent.lib.impl.services.scheduler;

import io.github.jjdelcerro.chatagent.lib.Agent;
import io.github.jjdelcerro.chatagent.lib.AgentService;
import io.github.jjdelcerro.chatagent.lib.AgentServiceFactory;
import io.github.jjdelcerro.chatagent.lib.AgentSettings;

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
