package io.github.jjdelcerro.noema.lib.impl.services.sensors.nature.user;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import io.github.jjdelcerro.noema.lib.impl.services.sensors.AbstractSensorEvent;
import io.github.jjdelcerro.noema.lib.services.sensors.SensorEventUser;
import io.github.jjdelcerro.noema.lib.services.sensors.SensorInformation;
import io.github.jjdelcerro.noema.lib.services.sensors.SensorsService;
import java.time.LocalDateTime;

public class SensorEventUserImpl extends AbstractSensorEvent implements SensorEventUser {

  public SensorEventUserImpl(SensorInformation sensor, String text, String priority, String status, LocalDateTime timestamp, SensorsService.SensorEventCallback callback) {
    super(sensor, text, priority, status, timestamp, callback);
  }

  @Override
  public String getContents() {
    return this.getText();
  }

  /**
   * Para el usuario, el mensaje principal es un UserMessage puro.
   */
  @Override
  public ChatMessage getChatMessage() {
    return UserMessage.from(this.getText());
  }

  /**
   * El usuario no es una herramienta, por lo que no hay mensaje de respuesta.
   */
  @Override
  public ToolExecutionResultMessage getResponseMessage() {
    return null;
  }
}
