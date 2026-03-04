package io.github.jjdelcerro.noema.lib.services.sensors;

import java.time.LocalDateTime;

/**
 * Internal processing logic for a specific sensor.
 */
public interface SensorData {
    void process(String text, String priority, String status, LocalDateTime timestamp, SensorsService.SensorEventCallback callback);
    boolean isMyEvent(String channel);
    SensorEvent flushEvent();
    SensorStatistics getStatistics();
    SensorInformation getSensorInformation();
    boolean hasPendingEvent();
}
