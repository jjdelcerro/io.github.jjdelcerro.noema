package io.github.jjdelcerro.noema.lib.impl.services.sensors.nature.mergeable;

import io.github.jjdelcerro.noema.lib.impl.services.sensors.AbstractSensorEvent;
import io.github.jjdelcerro.noema.lib.services.sensors.SensorEventMergeable;
import io.github.jjdelcerro.noema.lib.services.sensors.SensorInformation;
import io.github.jjdelcerro.noema.lib.services.sensors.SensorsService;
import java.time.LocalDateTime;

public class SensorEventMergeableImpl extends AbstractSensorEvent implements SensorEventMergeable {

  private final StringBuilder contentsBuilder;

  public SensorEventMergeableImpl(SensorInformation sensor, String text, String priority, String status, LocalDateTime timestamp, SensorsService.SensorEventCallback callback) {
    this(sensor, text, priority, status, timestamp, callback, timestamp);
  }

  public SensorEventMergeableImpl(SensorInformation sensor, String text, String priority, String status, LocalDateTime timestart, SensorsService.SensorEventCallback callback, LocalDateTime timeend) {
    super(sensor, text, priority, status, timestart, callback);
    this.contentsBuilder = new StringBuilder(text);
    this.endTimestamp = timeend;
  }

  public void append(String text, LocalDateTime timestamp) {
    this.contentsBuilder
            .append("\n[")
            .append(now())
            .append("] ")
            .append(text);
    this.endTimestamp = timestamp;
  }

  @Override
  public String getContents() {
    return contentsBuilder.toString();
  }
}
