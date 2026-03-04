package io.github.jjdelcerro.noema.lib.impl.services.reasoning;

import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.AgentService;
import io.github.jjdelcerro.noema.lib.AgentServiceFactory;
import io.github.jjdelcerro.noema.lib.settings.AgentSettings;
import org.apache.commons.lang3.StringUtils;
import static io.github.jjdelcerro.noema.lib.impl.services.reasoning.ReasoningServiceImpl.REASONING_PROVIDER_URL;
import static io.github.jjdelcerro.noema.lib.impl.services.reasoning.ReasoningServiceImpl.REASONING_PROVIDER_API_KEY;
import static io.github.jjdelcerro.noema.lib.impl.services.reasoning.ReasoningServiceImpl.REASONING_MODEL_ID;

/**
 *
 * @author jjdelcerro
 */
public class ReasoningServiceFactory implements AgentServiceFactory {

  @Override
  public String getName() {
    return ReasoningServiceImpl.NAME;
  }

  @Override
  public AgentService createService(Agent agent) {
    return new ReasoningServiceImpl(this, agent);
  }

  @Override
  public boolean canStart(AgentSettings settings) {
    String[] names = new String[]{
      REASONING_PROVIDER_URL,
      REASONING_PROVIDER_API_KEY,
      REASONING_MODEL_ID
    };
    for (String name : names) {
      String v = settings.getPropertyAsString(name);
      if (StringUtils.isBlank(v)) {
        return false;
      }
    }
    return true;
  }

  
}
