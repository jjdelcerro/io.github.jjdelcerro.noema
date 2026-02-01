package io.github.jjdelcerro.chatagent.lib.impl.tools.telegram;

import com.google.gson.Gson;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.SendResponse;
import dev.langchain4j.agent.tool.JsonSchemaProperty;
import dev.langchain4j.agent.tool.ToolSpecification;
import io.github.jjdelcerro.chatagent.lib.Agent;
import io.github.jjdelcerro.chatagent.lib.tools.AgenteTool;
import java.util.Map;

/**
 * Puente bidireccional entre el Agente y la plataforma de mensajería Telegram.
 * <p>
 * Esta clase implementa el patrón <b>Sensor/Efector</b> dentro de la
 * arquitectura del agente:
 * <ul>
 * <li><b>Como Efector (Herramienta):</b> Expone la función
 * {@code "telegram_send"} al LLM, permitiéndole enviar notificaciones
 * proactivas, alertas o resultados de tareas de larga duración al usuario, sin
 * necesidad de que este esté mirando la consola.</li>
 *
 * <li><b>Como Sensor (Listener):</b> Mantiene una escucha activa (long-polling)
 * de mensajes entrantes. Cuando se recibe un mensaje, actúa como un sensor que
 * inyecta el contenido directamente en el flujo de conciencia del agente
 * mediante {@link ConversationManagerImpl#putEvent}.</li>
 * </ul>
 * <p>
 * <b>Seguridad:</b>
 * El listener implementa un filtrado estricto por {@code chatId}. Solo se
 * procesan los eventos provenientes del usuario autorizado, descartando
 * silenciosamente cualquier interacción de terceros para evitar ataques de
 * inyección de prompt externos.
 * <p>
 * <b>Estrategia de Inyección:</b>
 * A diferencia del servicio de Email (que solo inyecta metadatos), esta
 * herramienta inyecta el
 * <i>contenido completo</i> del mensaje de texto en el contexto del agente,
 * asumiendo que la interacción por mensajería instantánea es breve,
 * conversacional y de prioridad inmediata.
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
    SendMessage request = new SendMessage(chatId, args.get("message"));
    SendResponse sr = bot.execute(request);
    return sr.isOk() ? "{\"status\":\"sent\"}" : "{\"status\":\"error\",\"msg\":\"" + sr.description() + "\"}";
  }

  /**
   * Factoría estática que inicializa el Bot y configura los sensores
   * (Listeners).
   * <p>
   * Este método registra un {@link com.pengrad.telegrambot.UpdatesListener} que
   * monitorea los mensajes entrantes. Al detectar un mensaje válido del
   * {@code authorizedChatId}, invoca {@link ConversationManagerImpl#putEvent}
   * con prioridad "normal" y canal "Telegram".
   *
   * @param apiKeyTelegram Token de acceso del Bot Father.
   * @param authorizedChatId ID numérico del chat/usuario autorizado (lista
   * blanca).
   * @param agent Referencia al agente para inyectar los eventos.
   * @return Una nueva instancia de {@code TelegramTool} lista para ser
   * registrada.
   */
  public static TelegramTool create(String apiKeyTelegram, long authorizedChatId, Agent agent) {
    TelegramBot bot = new TelegramBot(apiKeyTelegram);

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
    TelegramTool tool = new TelegramTool(bot, authorizedChatId);
    return tool;
  }
}
