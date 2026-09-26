package io.github.jjdelcerro.noema.lib.impl.services.skills;

import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.AgentService;
import io.github.jjdelcerro.noema.lib.AgentServiceFactory;
import io.github.jjdelcerro.noema.lib.settings.AgentSettings;

/**
 *
 * @author jjdelcerro
 */
public class SkillsServiceFactory implements AgentServiceFactory {

  @Override
  public String getName() {
    return SkillsService.NAME;
  }

  @Override
  public AgentService createService(Agent agent) {
    return new SkillsServiceImpl(this, agent);
  }

  @Override
  public boolean canStart(AgentSettings settings) {
    return true;
  }

}
