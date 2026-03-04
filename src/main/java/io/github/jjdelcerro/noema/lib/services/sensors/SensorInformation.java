package io.github.jjdelcerro.noema.lib.services.sensors;

/**
 * Contract defining the identity and metadata of a sensor.
 */
public interface SensorInformation {
    /**
     * Returns the unique technical identifier of the channel.
     * @return The channel ID.
     */
    String getChannel();

    /**
     * Returns the human-readable label for the sensor.
     * @return The sensor label.
     */
    String getLabel();

    /**
     * Returns a semantic description of the sensor and its purpose.
     * @return The sensor description.
     */
    String getDescription();

    /**
     * Returns the nature of the sensor, which dictates its processing strategy.
     * @return The sensor nature.
     */
    SensorNature getNature();
    
    boolean isSilenceable();
}
