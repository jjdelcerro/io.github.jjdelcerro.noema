/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package io.github.jjdelcerro.noema.lib.impl.services.sensors;

import io.github.jjdelcerro.noema.lib.impl.services.sensors.nature.aggregate.SensorEventAggregateImpl;
import io.github.jjdelcerro.noema.lib.impl.services.sensors.nature.discrete.SensorEventDiscreteImpl;
import io.github.jjdelcerro.noema.lib.services.sensors.ConsumableSensorEvent;
import io.github.jjdelcerro.noema.lib.services.sensors.SensorData;
import io.github.jjdelcerro.noema.lib.services.sensors.SensorEvent;
import io.github.jjdelcerro.noema.lib.services.sensors.SensorInformation;
import io.github.jjdelcerro.noema.lib.services.sensors.SensorStatistics;
import io.github.jjdelcerro.noema.lib.services.sensors.SensorsService;
import java.time.LocalDateTime;

/**
 *
 * @author jjdelcerro
 */
public abstract class AbstractSensorData implements SensorData {

  protected final SensorInformation info;
  protected final SensorStatistics stats;
  private ConsumableSensorEvent currentEvent;

  protected AbstractSensorData(SensorInformation info, SensorStatistics stats) {
    this.info = info;
    this.stats = stats;
  }

  public abstract ConsumableSensorEvent createSensorEvent(String text, String priority, String status, LocalDateTime timestamp, SensorsService.SensorEventCallback callback);

  protected ConsumableSensorEvent getCurrentEvent() {
    return this.currentEvent;
  }

  protected void setCurrentEvent(ConsumableSensorEvent currentEvent) {
    this.currentEvent = currentEvent;
  }

  @Override
  public boolean isMyEvent(String channel) {
    return info.getChannel().equals(channel);
  }

  @Override
  public SensorStatistics getStatistics() {
    return stats;
  }

  @Override
  public SensorInformation getSensorInformation() {
    return info;
  }

  @Override
  public SensorEvent flushEvent() {
    SensorEvent event = currentEvent;
    currentEvent = null;
    return event;
  }

  @Override
  public boolean hasPendingEvent() {
    return currentEvent != null;
  }

}
