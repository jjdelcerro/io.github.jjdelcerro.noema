package io.github.jjdelcerro.noema.lib.impl.services.conversation;

import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.AgentService;
import io.github.jjdelcerro.noema.lib.AgentServiceFactory;
import io.github.jjdelcerro.noema.lib.AgentSettings;
import static io.github.jjdelcerro.noema.lib.impl.services.conversation.ConversationService.CONVERSATION_MODEL_ID;
import static io.github.jjdelcerro.noema.lib.impl.services.conversation.ConversationService.CONVERSATION_PROVIDER_API_KEY;
import static io.github.jjdelcerro.noema.lib.impl.services.conversation.ConversationService.CONVERSATION_PROVIDER_URL;
import org.apache.commons.lang3.StringUtils;

/**
 *
 * @author jjdelcerro
 */
public class ConversationServiceFactory implements AgentServiceFactory {

  @Override
  public String getName() {
    return ConversationService.NAME;
  }

  @Override
  public AgentService createService(Agent agent) {
    return new ConversationService(this, agent);
  }

  @Override
  public boolean canStart(AgentSettings settings) {
    String[] names = new String[]{
      CONVERSATION_PROVIDER_URL,
      CONVERSATION_PROVIDER_API_KEY,
      CONVERSATION_MODEL_ID
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
