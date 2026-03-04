package io.github.jjdelcerro.noema.lib.impl.services.sensors;

import io.github.jjdelcerro.noema.lib.services.sensors.SensorInformation;
import io.github.jjdelcerro.noema.lib.services.sensors.SensorNature;

/**
 * POJO implementation of {@link SensorInformation}.
 */
public class SensorInformationImpl implements SensorInformation {

  private final String channel;
  private final String label;
  private final String description;
  private final SensorNature nature;
  private final boolean silenceable;

  public SensorInformationImpl(String channel, String label, SensorNature nature, String description, boolean silenceable) {
    this.channel = channel;
    this.label = label;
    this.nature = nature;
    this.description = description;
    this.silenceable = silenceable;
  }

  @Override
  public String getChannel() {
    return channel;
  }

  @Override
  public String getLabel() {
    return label;
  }

  @Override
  public String getDescription() {
    return description;
  }

  @Override
  public SensorNature getNature() {
    return nature;
  }

  @Override
  public boolean isSilenceable() {
    return silenceable;
  }
}
