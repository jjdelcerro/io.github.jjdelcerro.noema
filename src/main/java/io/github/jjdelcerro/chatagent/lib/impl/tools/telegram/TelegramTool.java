package io.github.jjdelcerro.chatagent.lib.impl.tools.telegram;

import com.google.gson.Gson;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.SendResponse;
import dev.langchain4j.agent.tool.JsonSchemaProperty;
import dev.langchain4j.agent.tool.ToolSpecification;
import io.github.jjdelcerro.chatagent.lib.impl.agent.ConversationAgent;
import io.github.jjdelcerro.chatagent.lib.tools.AgenteTool;
import java.util.Map;

/**
 *
 * @author jjdelcerro
 */
public class TelegramTool implements AgenteTool {

    private final TelegramBot bot;
    private final long chatId;
    private final Gson gson = new Gson();

    private TelegramTool(TelegramBot bot, long chatId) {
        this.bot = bot;
        this.chatId = chatId;
    }

    @Override
    public ToolSpecification getSpecification() {
        return ToolSpecification.builder()
                .name("telegram_send")
                .description("Envía un mensaje al usuario a través de Telegram. Úsalo para notificar resultados de tareas largas o alertas proactivas.")
                .addParameter("message", JsonSchemaProperty.STRING, JsonSchemaProperty.description("Contenido del mensaje"))
                .build();
    }

    @Override
    public int getMode() {
        return AgenteTool.MODE_WRITE;
    }

    @Override
    public String execute(String jsonArguments) {
        Map<String, String> args = gson.fromJson(jsonArguments, Map.class);
        SendMessage request = new SendMessage(chatId,args.get("message"));
        SendResponse sr = bot.execute(request);
        return sr.isOk() ? "{\"status\":\"sent\"}" : "{\"status\":\"error\",\"msg\":\"" + sr.description() + "\"}";
    }

    public static TelegramTool create(String apiKeyTelegram, long authorizedChatId, ConversationAgent agent) {
        TelegramBot bot = new TelegramBot(apiKeyTelegram);

        bot.setUpdatesListener(updates -> {
            updates.forEach(update -> {
                if (update.message() != null && update.message().text() != null) {
                    long chatId = update.message().chat().id();
                    // Seguridad: Solo hacemos caso si eres tú (chatId configurado)
                    if (chatId == authorizedChatId) {
                        agent.putEvent("Telegram","normal",update.message().text());
                    }
                }
            });
            return UpdatesListener.CONFIRMED_UPDATES_ALL;
        });
        TelegramTool tool = new TelegramTool(bot, authorizedChatId);
        return tool;
    }
}
