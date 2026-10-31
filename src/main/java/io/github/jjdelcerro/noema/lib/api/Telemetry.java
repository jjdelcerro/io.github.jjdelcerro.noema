package io.github.jjdelcerro.noema.lib.api;

/**
 *
 * @author jjdelcerro
 */
public interface Telemetry {

  public void increment(String id, long value);

  public interface TelemetryEntry {

    long getCounter();

    String getDescription();

    String getId();

    String getName();

    long increment();

    public long setCounter(long value);

    TelemetryEntry get(String id);

    public void setDescription(String description);

    public void setName(String name);

    public long increment(long value);
  }
  
  TelemetryEntry get(String id);

  String getReport();

  long getValue(String id);

  void increment(String id);

  TelemetryEntry registerCounter(String id, String name, String description);

  TelemetryEntry setValue(String id, long value);

}
