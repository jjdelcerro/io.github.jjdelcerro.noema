package io.github.jjdelcerro.noema.lib.impl.services.sensors;

import io.github.jjdelcerro.noema.lib.api.Agent;
import io.github.jjdelcerro.noema.lib.api.AgentService;
import io.github.jjdelcerro.noema.lib.api.AgentServiceFactory;
import io.github.jjdelcerro.noema.lib.api.settings.AgentSettings;

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
