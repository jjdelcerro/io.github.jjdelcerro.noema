package io.github.jjdelcerro.chatagent.lib.impl.services.telegram;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import io.github.jjdelcerro.chatagent.lib.Agent;
import io.github.jjdelcerro.chatagent.lib.Agent.ModelParameters;
import io.github.jjdelcerro.chatagent.lib.AgentService;
import io.github.jjdelcerro.chatagent.lib.AgentServiceFactory;
import io.github.jjdelcerro.chatagent.lib.AgentSettings;
import io.github.jjdelcerro.chatagent.lib.AgentTool;
import io.github.jjdelcerro.chatagent.lib.impl.services.telegram.tools.TelegramTool;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.lang3.StringUtils;

/**
 *
 * @author jjdelcerro
 */
public class TelegramService implements AgentService {

  public static final String NAME = "Telegram";

  public static final String TELEGRAM_CHAT_ID = "TELEGRAM_CHAT_ID";
  public static final String TELEGRAM_API_KEY = "TELEGRAM_API_KEY";

  private final Agent agent;
  private String apiKeyTelegram;
  private long authorizedChatId;
  private TelegramBot bot;
  private boolean running;
  private final AgentServiceFactory factory;

  public TelegramService(AgentServiceFactory factory, Agent agent) {
    this.factory = factory;
    this.agent = agent;
  }

  @Override
  public AgentServiceFactory getFactory() {
    return factory;
  }
  
  @Override
  public String getName() {
    return NAME;
  }

  public String getApiKeyTelegram() {
    return apiKeyTelegram;
  }

  public long getAuthorizedChatId() {
    return authorizedChatId;
  }

  public TelegramBot getBot() {
    return bot;
  }

  @Override
  public void start() {
    if (!this.canStart()) {
      return;
    }
    AgentSettings settings = agent.getSettings();

    this.apiKeyTelegram = settings.getProperty(TELEGRAM_API_KEY);
    this.authorizedChatId = settings.getPropertyAsLong(TELEGRAM_CHAT_ID, -1);
    if (StringUtils.isBlank(apiKeyTelegram) || authorizedChatId < 0) {
      return;
    }
    this.agent.getConsole().println("");

    this.bot = new TelegramBot(apiKeyTelegram);

    bot.setUpdatesListener(updates -> {
      updates.forEach(update -> {
        if (update.message() != null && update.message().text() != null) {
          long chatId = update.message().chat().id();
          // Seguridad: Solo hacemos caso si eres tú (chatId configurado)
          if (chatId == authorizedChatId) {
            agent.putEvent("Telegram", "normal", update.message().text());
          }
        }
      });
      return UpdatesListener.CONFIRMED_UPDATES_ALL;
    });
    this.running = true;
    this.agent.getConsole().println(">>> Servicio de telegram iniciado.");
  }

  @Override
  public boolean isRunning() {
    return this.running;
  }

  @Override
  public ModelParameters getModelParameters(String name) {
    return null;
  }

  @Override
  public boolean canStart() {
    return this.factory.canStart(agent.getSettings());
  }

  @Override
  public List<AgentTool> getTools() {
    AgentTool[] tools = new AgentTool[]{
      new TelegramTool(this.agent)
    };
    return Arrays.asList(tools);
  }

}
