package io.github.jjdelcerro.noema.lib.impl.services.sensors;

import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.AgentService;
import io.github.jjdelcerro.noema.lib.AgentServiceFactory;
import io.github.jjdelcerro.noema.lib.settings.AgentSettings;

/**
 *
 * @author jjdelcerro
 */
public class SensorsServiceFactory implements AgentServiceFactory {

  @Override
  public String getName() {
    return SensorsServiceImpl.NAME;
  }

  @Override
  public AgentService createService(Agent agent) {
    return new SensorsServiceImpl(this, agent);
  }

  @Override
  public boolean canStart(AgentSettings settings) {
    return true;
  }

  
}
