package io.github.jjdelcerro.noema.lib.impl.services.sensors.nature.user;

import io.github.jjdelcerro.noema.lib.impl.services.sensors.AbstractSensorData;
import io.github.jjdelcerro.noema.lib.services.sensors.ConsumableSensorEvent;
import io.github.jjdelcerro.noema.lib.services.sensors.SensorInformation;
import io.github.jjdelcerro.noema.lib.services.sensors.SensorStatistics;
import io.github.jjdelcerro.noema.lib.services.sensors.SensorsService;
import java.time.LocalDateTime;

public class UserSensorData extends AbstractSensorData {

  public UserSensorData(SensorInformation info, SensorStatistics stats) {
    super(info, stats);
  }

  @Override
  public ConsumableSensorEvent createSensorEvent(String text, String priority, String status, LocalDateTime timestamp, SensorsService.SensorEventCallback callback) {
    return new SensorEventUserImpl(info, text, priority, status, timestamp, callback);
  }

  @Override
  public void process(String text, String priority, String status, LocalDateTime timestamp, SensorsService.SensorEventCallback callback) {
    this.setCurrentEvent(this.createSensorEvent(text, priority, status, timestamp, callback));
  }

}
