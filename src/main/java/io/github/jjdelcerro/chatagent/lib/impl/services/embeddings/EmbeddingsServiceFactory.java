package io.github.jjdelcerro.chatagent.lib.impl.services.embeddings;

import io.github.jjdelcerro.chatagent.lib.Agent;
import io.github.jjdelcerro.chatagent.lib.AgentService;
import io.github.jjdelcerro.chatagent.lib.AgentServiceFactory;
import io.github.jjdelcerro.chatagent.lib.AgentSettings;

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
