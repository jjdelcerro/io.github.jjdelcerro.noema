package io.github.jjdelcerro.noema.lib.impl.services.embeddings;

import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.AgentService;
import io.github.jjdelcerro.noema.lib.AgentServiceFactory;
import io.github.jjdelcerro.noema.lib.settings.AgentSettings;

/**
 *
 * @author jjdelcerro
 */
public class EmbeddingsServiceFactory implements AgentServiceFactory {

  @Override
  public String getName() {
    return EmbeddingsService.NAME;
  }

  @Override
  public AgentService createService(Agent agent) {
    return new EmbeddingsService(this, agent);
  }

  @Override
  public boolean canStart(AgentSettings settings) {
    return true;
  }

  
}
