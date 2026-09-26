package io.github.jjdelcerro.noema.lib.impl.services.subagents;

import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.AgentService;
import io.github.jjdelcerro.noema.lib.AgentServiceFactory;
import io.github.jjdelcerro.noema.lib.services.subagents.SubagentsService;
import io.github.jjdelcerro.noema.lib.settings.AgentSettings;

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
