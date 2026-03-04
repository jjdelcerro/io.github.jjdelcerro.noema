package io.github.jjdelcerro.noema.lib.impl.services.telegram;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.Agent.ModelParameters;
import io.github.jjdelcerro.noema.lib.AgentService;
import io.github.jjdelcerro.noema.lib.AgentServiceFactory;
import io.github.jjdelcerro.noema.lib.settings.AgentSettings;
import io.github.jjdelcerro.noema.lib.AgentTool;
import static io.github.jjdelcerro.noema.lib.impl.services.scheduler.SchedulerServiceImpl.SENSOR_NAME;
import io.github.jjdelcerro.noema.lib.services.sensors.SensorNature;
import io.github.jjdelcerro.noema.lib.services.sensors.SensorsService;
import static io.github.jjdelcerro.noema.lib.services.sensors.SensorsService.PRIORITY_NORMAL;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author jjdelcerro
 */
public class TelegramService implements AgentService {
  
  private static final Logger LOGGER = LoggerFactory.getLogger(TelegramService.class);
 
  public static final String NAME = "Telegram";

  public static final String SENSOR_NAME = "TELEGRAM";
  private static final String SENSOR_LABEL = "Telegram";
  private static final String SENSOR_DESCRIPTION = "Telegram"; // FIXME: poner una descripcion decente para el LLM. 
  
  public static final String TELEGRAM_CHAT_ID = "telegram/chat_id";
  public static final String TELEGRAM_API_KEY = "telegram/api_key";

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
    if (this.running || !this.canStart()) {
      return;
    }
    this.agent.registerSensor(
            SENSOR_NAME, 
            SENSOR_LABEL, 
            SensorNature.MERGEABLE, 
            SENSOR_DESCRIPTION
    );    
    AgentSettings settings = agent.getSettings();
    
    this.apiKeyTelegram = settings.getPropertyAsString(TELEGRAM_API_KEY);
    this.authorizedChatId = settings.getPropertyAsLong(TELEGRAM_CHAT_ID, -1);
    if (StringUtils.isBlank(apiKeyTelegram) || authorizedChatId < 0) {
      return;
    }
    this.bot = new TelegramBot(apiKeyTelegram);

    bot.setUpdatesListener(updates -> {
      updates.forEach(update -> {
        if (update.message() != null && update.message().text() != null) {
          long chatId = update.message().chat().id();
          // Seguridad: Solo hacemos caso si eres tú (chatId configurado)
          if (chatId == authorizedChatId) {
            agent.putEvent(SENSOR_NAME, "TELEGRAM MESSAGE RECEIVED", PRIORITY_NORMAL, update.message().text());
          }
        }
      });
      return UpdatesListener.CONFIRMED_UPDATES_ALL;
    });
    this.running = true;
    this.agent.getConsole().printSystemLog("Servicio de telegram iniciado.");
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
//      new TelegramTool(this.agent)
    };
    return Arrays.asList(tools);
  }

  @Override
  public void stop() {
    this.running = false;
  }

}
