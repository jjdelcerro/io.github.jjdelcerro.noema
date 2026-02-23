package io.github.jjdelcerro.noema.lib.impl.services.memory;

import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.AgentService;
import io.github.jjdelcerro.noema.lib.AgentServiceFactory;
import io.github.jjdelcerro.noema.lib.AgentSettings;
import static io.github.jjdelcerro.noema.lib.impl.services.memory.MemoryService.MEMORY_MODEL_ID;
import static io.github.jjdelcerro.noema.lib.impl.services.memory.MemoryService.MEMORY_PROVIDER_API_KEY;
import static io.github.jjdelcerro.noema.lib.impl.services.memory.MemoryService.MEMORY_PROVIDER_URL;
import org.apache.commons.lang3.StringUtils;

/**
 *
 * @author jjdelcerro
 */
public class MemoryServiceFactory implements AgentServiceFactory {

  @Override
  public String getName() {
    return MemoryService.NAME;
  }

  @Override
  public AgentService createService(Agent agent) {
    return new MemoryService(this, agent);
  }

  @Override
  public boolean canStart(AgentSettings settings) {
    String[] names = new String[]{
      MEMORY_PROVIDER_URL,
      MEMORY_PROVIDER_API_KEY,
      MEMORY_MODEL_ID
    };
    for (String name : names) {
      String v = settings.getProperty(name);
      if (StringUtils.isBlank(v)) {
        return false;
      }
    }
    return true;
  }
}
