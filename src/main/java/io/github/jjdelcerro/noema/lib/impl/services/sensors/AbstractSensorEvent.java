package io.github.jjdelcerro.noema.lib.impl.services.sensors;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import io.github.jjdelcerro.noema.lib.services.sensors.ConsumableSensorEvent;
import io.github.jjdelcerro.noema.lib.services.sensors.SensorEvent;
import io.github.jjdelcerro.noema.lib.services.sensors.SensorInformation;
import io.github.jjdelcerro.noema.lib.services.sensors.SensorsService;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

/**
 * Base abstract class for {@link SensorEvent} implementations. Implementa
 * ConsumableSensorEvent para gestionar la integración con el LLM.
 */
public abstract class AbstractSensorEvent implements ConsumableSensorEvent {

  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

  protected final SensorInformation sensor;
  protected final String priority;
  protected final String status;
  protected final String text; // El estímulo original o base
  protected final LocalDateTime startTimestamp;
  protected LocalDateTime endTimestamp;
  protected LocalDateTime deliveryTimestamp;

  // Mensajes de protocolo para LangChain4j (Lazy Loading)
  private AiMessage aiMessage;
  private ToolExecutionResultMessage responseMessage;
  private final SensorsService.SensorEventCallback callback;

  protected AbstractSensorEvent(SensorInformation sensor, String text, String priority, String status, LocalDateTime startTimestamp, SensorsService.SensorEventCallback callback) {
    this.callback = callback;
    this.sensor = sensor;
    this.text = text;
    this.priority = priority;
    this.status = status;
    this.startTimestamp = startTimestamp;
    this.endTimestamp = startTimestamp;
  }

  public static String now() {
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM 'de' yyyy, HH:mm", new Locale("es", "ES"));
    return LocalDateTime.now().format(formatter);
  }
  
  public SensorInformation getSensor() {
    return this.sensor;
  }
  
  @Override
  public String getChannel() {
    return this.sensor.getChannel();
  }

  @Override
  public String getPriority() {
    return priority;
  }

  @Override
  public String getStatus() {
    return status;
  }

  /**
   * Permite a las subclases acceder al texto base para construir getContents().
   *
   * @return
   */
  protected String getText() {
    return text;
  }

  @Override
  public LocalDateTime getStartTimestamp() {
    return startTimestamp;
  }

  @Override
  public LocalDateTime getEndTimestamp() {
    return endTimestamp;
  }

  @Override
  public LocalDateTime getDeliveryTimestamp() {
    return deliveryTimestamp;
  }

  @Override
  public void setDeliveryTimestamp(LocalDateTime deliveryTimestamp) {
    this.deliveryTimestamp = deliveryTimestamp;
  }

  // --- Implementación de ConsumableSensorEvent ---
  @Override
  public String toJson() {
    return GSON.toJson(Map.of(
            "event_time", startTimestamp,
            "current_time", now(),
            "channel", this.sensor.getChannel(),
            "status", status,
            "priority", priority,
            "contents", getContents() // Polimórfico: cada subclase aporta su texto digerido
    ));
  }

  @Override
  public ChatMessage getChatMessage() {
    if (this.aiMessage == null) {
      buildMessages();
    }
    return this.aiMessage;
  }

  @Override
  public ToolExecutionResultMessage getResponseMessage() {
    if (this.responseMessage == null) {
      buildMessages();
    }
    return this.responseMessage;
  }

  private void buildMessages() {
    // Creamos un ID único para la petición basado en el canal y el tiempo
    String requestId = this.sensor.getChannel() + "_" + startTimestamp;

    ToolExecutionRequest request = ToolExecutionRequest.builder()
            .id(requestId)
            .name("pool_event")
            .arguments("{}")
            .build();

    this.aiMessage = AiMessage.from(request);
    this.responseMessage = ToolExecutionResultMessage.from(request, this.toJson());
  }

  @Override
  public SensorsService.SensorEventCallback getCallback() {
    return callback;
  }
  
}
