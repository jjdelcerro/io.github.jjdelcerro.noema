package io.github.jjdelcerro.noema.lib.services.sensors;

import io.github.jjdelcerro.noema.lib.services.sensors.SensorsService.SensorEventCallback;
import java.time.LocalDateTime;

/**
 * Base interface for all sensory events.
 */
public interface SensorEvent {
  
    SensorInformation getSensor();
    
    /**
     * Returns the channel of origin.
     * @return The channel ID.
     */
    String getChannel();

    /**
     * Returns the content of the event (text or JSON).
     * @return The event contents.
     */
    String getContents();

    /**
     * Returns the priority of the event.
     * @return The priority level.
     */
    String getPriority();

    /**
     * Returns the status of the event.
     * @return The event status.
     */
    String getStatus();

    /**
     * Returns the timestamp of the first stimulus in this event.
     * @return Start timestamp in milliseconds.
     */
    LocalDateTime getStartTimestamp();

    /**
     * Returns the timestamp of the last stimulus in this event.
     * @return End timestamp in milliseconds.
     */
    LocalDateTime getEndTimestamp();

    /**
     * Returns the timestamp when the event was delivered to the ConversationSercices.
     * @return Delivery timestamp in milliseconds.
     */
    LocalDateTime getDeliveryTimestamp();
    
    /**
     * Sets the delivery timestamp.
     * @param deliveryTimestamp The delivery timestamp in milliseconds.
     */
    void setDeliveryTimestamp(LocalDateTime deliveryTimestamp);
    
    SensorEventCallback getCallback();
}
