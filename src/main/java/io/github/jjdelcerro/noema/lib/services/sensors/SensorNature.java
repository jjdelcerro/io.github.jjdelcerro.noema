package io.github.jjdelcerro.noema.lib.services.sensors;

/**
 * Enumerated defining the signal processing strategies for sensors.
 */
public enum SensorNature {
    /**
     * Discrete events: atomic, irreducible, and critical. Each event is processed independently.
     */
    DISCRETE,

    /**
     * Mergeable stimuli: narrative or conversational flow. Events are concatenated into blocks.
     */
    MERGEABLE,

    /**
     * Aggregatable events: volume and frequency are key. Events are counted.
     */
    AGGREGATABLE,

    /**
     * State conditions: volatile and only valid in their most recent version.
     */
    STATE,
    
    USER
}
