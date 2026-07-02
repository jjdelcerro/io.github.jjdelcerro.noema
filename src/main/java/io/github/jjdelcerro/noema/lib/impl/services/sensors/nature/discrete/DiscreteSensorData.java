package io.github.jjdelcerro.noema.lib.impl.services.sensors.nature.discrete;

import io.github.jjdelcerro.noema.lib.impl.services.sensors.AbstractSensorData;
import io.github.jjdelcerro.noema.lib.services.sensors.SensorInformation;
import io.github.jjdelcerro.noema.lib.services.sensors.SensorStatistics;
import io.github.jjdelcerro.noema.lib.services.sensors.SensorsService;
import java.time.LocalDateTime;

public class DiscreteSensorData extends AbstractSensorData {

  public DiscreteSensorData(SensorInformation info, SensorStatistics stats) {
    super(info, stats);
  }

  @Override
  public SensorEventDiscreteImpl createSensorEvent(String subchannel, String text, String priority, String status, LocalDateTime timestamp, SensorsService.SensorEventCallback callback) {
    return new SensorEventDiscreteImpl(info, subchannel, text, priority, status, timestamp, callback);
  }

  @Override
  public void process(String subchannel, String text, String priority, String status, LocalDateTime timestamp, SensorsService.SensorEventCallback callback) {
    this.setCurrentEvent(this.createSensorEvent(subchannel, text, priority, status, timestamp, callback));
  }

}
