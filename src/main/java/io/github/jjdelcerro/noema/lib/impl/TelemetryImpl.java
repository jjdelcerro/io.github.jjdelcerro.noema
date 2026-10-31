package io.github.jjdelcerro.noema.lib.impl;

import io.github.jjdelcerro.noema.lib.api.Telemetry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 *
 * @author jjdelcerro
 */
public class TelemetryImpl implements Telemetry {

  private final ConcurrentHashMap<String, TelemetryEntry> entries;
  
  public static class TelemetryEntryImpl implements TelemetryEntry {

    private String description;
    private String name;
    private final String id;
    private long counter;
    private final ConcurrentHashMap<String, TelemetryEntry> entries;
    
    public TelemetryEntryImpl(String id, String name, String description) {
      this.entries = new ConcurrentHashMap<>();
      this.id = id;
      this.name = name; 
      this.description = description;
      this.counter = 0;
    }
    
    @Override
    public long increment() {
      return ++this.counter;
    }

    @Override
    public long increment(long value) {
      return this.counter+=value;
    }
    
    @Override
    public String getName() {
      return this.name;
    }

    @Override
    public String getDescription() {
      return description;
    }

    @Override
    public String getId() {
      return id;
    }

    @Override
    public long getCounter() {
      return counter;
    }

    @Override
    public long setCounter(long value) {
      return this.counter=value;
    }
        
    @Override
    public TelemetryEntry get(String id) {
      TelemetryEntry entry = this.entries.get(id);
      if( entry == null ) {
        entry = new TelemetryEntryImpl(id, null, null);
        this.entries.put(id, entry);
      }
      return entry;
    }

    @Override
    public void setDescription(String description) {
      this.description = description;
    }

    @Override
    public void setName(String name) {
      this.name = name;
    }

    
    
    
  }
  
  public TelemetryImpl() {
    this.entries = new ConcurrentHashMap<>();
  }
   
  @Override
  public TelemetryEntry registerCounter(String id, String name, String description) {
    TelemetryEntry entry = this.entries.get(id);
    if( entry == null ) {
      entry = new TelemetryEntryImpl(id, name, description);
      this.entries.put(id, entry);
    }
    entry.setName(name);
    entry.setDescription(description);
    return entry;
  }
  
  @Override
  public void increment(String id) {
    TelemetryEntry entry = this.entries.get(id);
    if( entry == null ) {
      entry = this.registerCounter(id, null, null);
    }
    entry.increment();
  }
  
  @Override
  public TelemetryEntry setValue(String id, long value) {
    TelemetryEntry entry = this.entries.get(id);
    if( entry == null ) {
      entry = this.registerCounter(id, null, null);
    }
    entry.setCounter(value);
    return entry;
  }
  
  @Override
  public long getValue(String id) {
    TelemetryEntry entry = this.entries.get(id);
    if( entry == null ) {
      entry = this.registerCounter(id, null, null);
    }
    return entry.getCounter();
  }
  
  @Override
  public TelemetryEntry get(String id) {
    TelemetryEntry entry = this.entries.get(id);
    if( entry == null ) {
      entry = this.registerCounter(id, null, null);
    }
    return entry;
  }

  @Override
  public void increment(String id, long value) {
    TelemetryEntry entry = this.entries.get(id);
    if( entry == null ) {
      entry = this.registerCounter(id, null, null);
    }
    entry.increment(value);
  }
  
  @Override
  public String getReport() {
    StringBuilder s = new StringBuilder();
    List<TelemetryEntry> sorteds = new ArrayList<>(this.entries.values());
    Collections.sort(sorteds, new Comparator<TelemetryEntry>() {
      @Override
      public int compare(TelemetryEntry o1, TelemetryEntry o2) {
        return o1.getId().compareTo(o2.getId());
      }
    });
    for (TelemetryEntry entry : sorteds) {
      s.append("* ").append(entry.getId()).append(" = ").append(entry.getCounter()).append("\n");
    }
    return s.toString();
  }
}
