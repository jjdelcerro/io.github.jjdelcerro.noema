package io.github.jjdelcerro.noema.lib.impl.services.telegram;

import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.AgentService;
import io.github.jjdelcerro.noema.lib.AgentServiceFactory;
import io.github.jjdelcerro.noema.lib.settings.AgentSettings;
import static io.github.jjdelcerro.noema.lib.impl.services.telegram.TelegramService.TELEGRAM_API_KEY;
import static io.github.jjdelcerro.noema.lib.impl.services.telegram.TelegramService.TELEGRAM_CHAT_ID;
import org.apache.commons.lang3.StringUtils;

/**
 *
 * @author jjdelcerro
 */
public class TelegramServiceFactory implements AgentServiceFactory {

  @Override
  public String getName() {
    return TelegramService.NAME;
  }

  @Override
  public AgentService createService(Agent agent) {
    return new TelegramService(this, agent);
  }

  @Override
  public boolean canStart(AgentSettings settings) {
    String apiKeyTelegram = settings.getPropertyAsString(TELEGRAM_API_KEY);
    long authorizedChatId = settings.getPropertyAsLong(TELEGRAM_CHAT_ID, -1);
    return StringUtils.isNotBlank(apiKeyTelegram) && authorizedChatId > 0;
  }

}
