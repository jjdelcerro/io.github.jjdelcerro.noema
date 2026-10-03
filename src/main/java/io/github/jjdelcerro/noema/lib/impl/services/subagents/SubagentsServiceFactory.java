package io.github.jjdelcerro.noema.lib.impl.services.subagents;

import io.github.jjdelcerro.noema.lib.api.Agent;
import io.github.jjdelcerro.noema.lib.api.AgentService;
import io.github.jjdelcerro.noema.lib.api.AgentServiceFactory;
import io.github.jjdelcerro.noema.lib.api.services.subagents.SubagentsService;
import io.github.jjdelcerro.noema.lib.api.settings.AgentSettings;

/**
 *
 * @author jjdelcerro
 */
public class SubagentsServiceFactory implements AgentServiceFactory {

  @Override
  public String getName() {
    return SubagentsService.NAME;
  }

  @Override
  public AgentService createService(Agent agent) {
    return new SubagentsServiceImpl(this, agent);
  }

  @Override
  public boolean canStart(AgentSettings settings) {
    return true;
  }

}
