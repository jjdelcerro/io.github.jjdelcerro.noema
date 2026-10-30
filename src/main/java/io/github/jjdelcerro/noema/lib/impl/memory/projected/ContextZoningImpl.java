package io.github.jjdelcerro.noema.lib.impl.memory.projected;

import io.github.jjdelcerro.noema.lib.api.memory.projected.ContextZoning;

/**
 * Default implementation of {@link ContextZoning}.
 *
 * @author jjdelcerro
 */
public class ContextZoningImpl implements ContextZoning {

  private final int size_safe;
  private final int size_pruning_notification_accumulation;
  private final int size_pruning_notification;
  private final int size_pruning_accumulation;
  private final int size_pruning;

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
    
    double size_new_zone = this.maxMessagesAllowed * 1.0/4.0;
    double size_intermediate_zone = this.maxMessagesAllowed * 1.0/4.0;
    double size_old_zone = this.maxMessagesAllowed * 2.0/4.0;
    
    this.size_safe = (int) size_new_zone;
    
    this.size_pruning_notification_accumulation = (int) (size_intermediate_zone * 2.0/3.0);   
    this.size_pruning_notification = (int) (size_intermediate_zone * 1.0/3.0);
    
    this.size_pruning_accumulation = (int) (size_old_zone * 1.0/4.0);
    this.size_pruning = (int) (size_old_zone * 3.0/4.0);
    
  }

  @Override
  public ContextZone getZone(ContextZoneType type) {
    return this.getZone(type, this.maxMessagesAllowed);
  }

  @Override
  public ContextZone getZone(ContextZoneType type, int currentSize) {
    int total_mensajes = Math.max(0, currentSize);
    switch (type) {
      case SAFE_ZONE:
        return new ZoneImpl(type, 
                Math.max(0,total_mensajes-size_safe), 
                total_mensajes
        );
      case PRUNING_NOTIFICATION_ACCUMULATION_ZONE:
        return new ZoneImpl(type, 
                Math.max(0, total_mensajes - size_safe - size_pruning_notification_accumulation), 
                Math.max(0, total_mensajes - size_safe)
        );
      case PRUNING_NOTIFICATION_ZONE:
        return new ZoneImpl(type, 
                Math.max(0, total_mensajes - size_safe - size_pruning_notification_accumulation - size_pruning_notification), 
                Math.max(0, total_mensajes - size_safe - size_pruning_notification_accumulation)
        );
      case PRUNING_ACCUMULATION_ZONE:
        return new ZoneImpl(type, 
                Math.max(0, total_mensajes - size_safe - size_pruning_notification_accumulation - size_pruning_notification - size_pruning_accumulation), 
                Math.max(0, total_mensajes - size_safe - size_pruning_notification_accumulation - size_pruning_notification)
        );
      case PRUNING_ZONE:
        return new ZoneImpl(type, 
                0,  
                Math.max(0, total_mensajes - size_safe - size_pruning_notification_accumulation - size_pruning_notification - size_pruning_accumulation)
        );
      default:
        throw new IllegalArgumentException("Unsupported zone: " + type);
    }
  }

}
