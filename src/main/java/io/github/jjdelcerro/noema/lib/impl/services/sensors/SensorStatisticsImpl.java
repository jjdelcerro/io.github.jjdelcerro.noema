package io.github.jjdelcerro.noema.lib.impl.services.sensors;

import io.github.jjdelcerro.noema.lib.services.sensors.SensorStatistics;
import java.time.LocalDateTime;

/**
 * Implementation of {@link SensorStatistics}.
 */
public class SensorStatisticsImpl implements SensorStatistics {

  private long totalEventsActive;
  private long totalEventsSilenced;
  private LocalDateTime lastEventTimestamp;
  private LocalDateTime lastDeliveryTimestamp;
  private boolean silenced;

  public SensorStatisticsImpl() {
    this.totalEventsActive = 0;
    this.totalEventsSilenced = 0;
    this.lastEventTimestamp = null;
    this.lastDeliveryTimestamp = null;
    this.silenced = false;
  }

  @Override
  public long getTotalEventsActive() {
    return totalEventsActive;
  }

  @Override
  public long getTotalEventsSilenced() {
    return totalEventsSilenced;
  }

  @Override
  public LocalDateTime getLastEventTimestamp() {
    return lastEventTimestamp;
  }

  @Override
  public LocalDateTime getLastDeliveryTimestamp() {
    return lastDeliveryTimestamp;
  }

  @Override
  public double getAverageFrequency(long period) {
    // Basic implementation for now
    if (period <= 0) {
      return 0;
    }
    return (double) totalEventsActive / (period / 1000.0);
  }

  @Override
  public boolean isSilenced() {
    return silenced;
  }

  @Override
  public void setSilenced(boolean silenced) {
    this.silenced = silenced;
  }

  @Override
  public void incrementActiveEvents() {
    this.totalEventsActive++;
  }

  @Override
  public void incrementSilencedEvents() {
    this.totalEventsSilenced++;
  }

  @Override
  public void updateLastEventTimestamp(LocalDateTime timestamp) {
    this.lastEventTimestamp = timestamp;
  }

  @Override
  public void updateLastDeliveryTimestamp(LocalDateTime timestamp) {
    this.lastDeliveryTimestamp = timestamp;
  }

  public void rehydrate(long total_active, long total_silenced, LocalDateTime last_event, LocalDateTime last_delivery, boolean isSilenced) {
    this.totalEventsActive = total_active;
    this.totalEventsSilenced = total_silenced;
    this.lastEventTimestamp = last_event;
    this.lastDeliveryTimestamp = last_delivery;
    this.silenced = isSilenced;
  }
}
