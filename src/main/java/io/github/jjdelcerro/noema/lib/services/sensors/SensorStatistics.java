package io.github.jjdelcerro.noema.lib.services.sensors;

import java.time.LocalDateTime;

/**
 * Interface defining methods to query the health and performance of a sensor.
 */
public interface SensorStatistics {
    long getTotalEventsActive();
    long getTotalEventsSilenced();
    LocalDateTime getLastEventTimestamp();
    LocalDateTime getLastDeliveryTimestamp();
    double getAverageFrequency(long period);
    boolean isSilenced();
    void setSilenced(boolean muted);
    void incrementActiveEvents();
    void incrementSilencedEvents();
    void updateLastEventTimestamp(LocalDateTime timestamp);
    void updateLastDeliveryTimestamp(LocalDateTime timestamp);
}
