package io.github.jjdelcerro.noema.lib.impl.services.sensors.nature.aggregate;

import io.github.jjdelcerro.noema.lib.impl.services.sensors.AbstractSensorEvent;
import io.github.jjdelcerro.noema.lib.services.sensors.SensorEventAggregate;
import io.github.jjdelcerro.noema.lib.services.sensors.SensorInformation;
import io.github.jjdelcerro.noema.lib.services.sensors.SensorsService;
import java.time.LocalDateTime;

public class SensorEventAggregateImpl extends AbstractSensorEvent implements SensorEventAggregate {

  private long count;

  public SensorEventAggregateImpl(SensorInformation sensor, String text, String priority, String status, LocalDateTime timestamp, SensorsService.SensorEventCallback callback) {
    this(sensor, text, priority, status, timestamp, callback, 1);
  }
  
  public SensorEventAggregateImpl(SensorInformation sensor, String text, String priority, String status, LocalDateTime timestamp, SensorsService.SensorEventCallback callback, long count) {
    super(sensor, text, priority, status, timestamp, callback);
    this.count = count;
  }

  public void increment() {
    this.count++;
  }

  public void updateEndTimestamp(LocalDateTime timestamp) {
    this.endTimestamp = timestamp;
  }

  @Override
  public long getCount() {
    return count;
  }

  @Override
  public String getContents() {
    return this.getText() + ". Se han recivido " + count + " eventos de este tipo.";
  }
}
