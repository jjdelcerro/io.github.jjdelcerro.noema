package io.github.jjdelcerro.noema.lib.impl.services.sensors.nature.mergeable;

import io.github.jjdelcerro.noema.lib.impl.services.sensors.AbstractSensorData;
import io.github.jjdelcerro.noema.lib.services.sensors.ConsumableSensorEvent;
import io.github.jjdelcerro.noema.lib.services.sensors.SensorInformation;
import io.github.jjdelcerro.noema.lib.services.sensors.SensorStatistics;
import io.github.jjdelcerro.noema.lib.services.sensors.SensorsService;
import java.time.LocalDateTime;

public class MergeableSensorData extends AbstractSensorData {

  public MergeableSensorData(SensorInformation info, SensorStatistics stats) {
    super(info, stats);
  }

  @Override
  public ConsumableSensorEvent createSensorEvent(String text, String priority, String status, LocalDateTime timestamp, SensorsService.SensorEventCallback callback) {
    return new SensorEventMergeableImpl(info, text, priority, status, timestamp, callback);
  }

  @Override
  protected SensorEventMergeableImpl getCurrentEvent() {
    return (SensorEventMergeableImpl) super.getCurrentEvent();
  }

  @Override
  public void process(String text, String priority, String status, LocalDateTime timestamp, SensorsService.SensorEventCallback callback) {
    if (this.getCurrentEvent() == null) {
      this.setCurrentEvent(this.createSensorEvent(text, priority, status, timestamp, callback));
    } else {
      this.getCurrentEvent().append(text, timestamp);
    }
  }

}
