package io.github.jjdelcerro.noema.lib.impl.services.mcp;

import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.AgentService;
import io.github.jjdelcerro.noema.lib.AgentServiceFactory;
import io.github.jjdelcerro.noema.lib.settings.AgentSettings;
import org.apache.commons.lang3.StringUtils;

/**
 *
 * @author jjdelcerro
 */
public class McpServiceFactory implements AgentServiceFactory {

  @Override
  public String getName() {
    return McpServiceImpl.NAME;
  }

  @Override
  public AgentService createService(Agent agent) {
    return new McpServiceImpl(this, agent);
  }

  @Override
  public boolean canStart(AgentSettings settings) {
    return true;
  }
}
