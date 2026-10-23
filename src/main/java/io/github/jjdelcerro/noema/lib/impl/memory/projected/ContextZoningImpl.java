package io.github.jjdelcerro.noema.lib.impl.memory.projected;

import io.github.jjdelcerro.noema.lib.api.memory.projected.ContextZoning;

/**
 * Default implementation of {@link ContextZoning}.
 *
 * @author jjdelcerro
 */
public class ContextZoningImpl implements ContextZoning {

  private final int size_prunnable;
  private final int size_risk;
  private final int size_safe;

  /**
   * Immutable zone over a range of indexes.
   */
  public static class ZoneImpl implements ContextZone {

    private final int min;
    private final int max;
    private final ContextZoneType type;

    public ZoneImpl(ContextZoneType type, int min, int max) {
      this.type = type;
      this.min = min;
      this.max = max;
    }

    @Override
    public ContextZoneType type() {
      return this.type;
    }

    @Override
    public boolean isEmpty() {
      return (this.max-this.min) <= 0;
    }

    @Override
    public int max() {
      return this.max;
    }

    @Override
    public int min() {
      return this.min;
    }

    @Override
    public boolean contains(int index) {
      return this.min >= 0 && index >= this.min && index < this.max;
    }

  }

  private final int maxMessagesAllowed;

  public ContextZoningImpl(int maxMessages) {
    this.maxMessagesAllowed = maxMessages;
    this.size_prunnable = this.maxMessagesAllowed / 2;
    this.size_risk = this.maxMessagesAllowed / 4;
    this.size_safe = this.maxMessagesAllowed / 4;
  }

  @Override
  public ContextZone getZone(ContextZoneType type) {
    return this.getZone(type, this.maxMessagesAllowed);
  }

  @Override
  public ContextZone getZone(ContextZoneType type, int currentSize) {
    int total_mensajes = Math.max(0, currentSize);
    switch (type) {
      case PRUNABLE:
        return new ZoneImpl(type, 0,  Math.max(0,total_mensajes-size_safe-size_risk));
      case RISK:
        return new ZoneImpl(type, Math.max(0,total_mensajes-size_safe-size_risk),  Math.max(0,total_mensajes-size_safe));
      case SAFE:
        return new ZoneImpl(type, Math.max(0,total_mensajes-size_safe), total_mensajes);
      default:
        throw new IllegalArgumentException("Unsupported zone: " + type);
    }
  }

}
