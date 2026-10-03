package io.github.jjdelcerro.noema.lib.impl.services.scripting;

import io.github.jjdelcerro.noema.lib.api.services.scripting.ScriptingService;
import io.github.jjdelcerro.noema.lib.impl.services.skills.*;
import io.github.jjdelcerro.noema.lib.api.Agent;
import io.github.jjdelcerro.noema.lib.api.AgentService;
import io.github.jjdelcerro.noema.lib.api.AgentServiceFactory;
import io.github.jjdelcerro.noema.lib.api.settings.AgentSettings;

/**
 *
 * @author jjdelcerro
 */
public class ScriptingServiceFactory implements AgentServiceFactory {

  @Override
  public String getName() {
    return ScriptingService.NAME;
  }

  @Override
  public AgentService createService(Agent agent) {
    return new ScriptingServiceImpl(this, agent);
  }

  @Override
  public boolean canStart(AgentSettings settings) {
    return true;
  }

}
