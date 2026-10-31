package io.github.jjdelcerro.noema.lib.spi.memory.projected;

import com.google.gson.JsonObject;
import io.github.jjdelcerro.noema.lib.api.Agent;
import io.github.jjdelcerro.noema.lib.api.Telemetry;
import io.github.jjdelcerro.noema.lib.api.memory.projected.ProjectedMemoryOperation;

/**
 *
 * @author jjdelcerro
 */
public abstract class AbstractProjectedMemoryOperation implements ProjectedMemoryOperation {

  private final Agent agent;

  private final String name;
  private final int priority;
  
  protected AbstractProjectedMemoryOperation(Agent agent, String name, int priority) {
    this.agent = agent;
    this.name = name;
    this.priority = priority;    
  }
  
  public Agent getAgent() {
    return this.agent;
  }
  
  public Telemetry getTelemetry() {
    return this.agent.getTelemetry();
  }

  @Override
  public String getName() {
    return this.name;
  }

  @Override
  public int getPriority() {
    return this.priority;
  }

  @Override
  public JsonObject getState() {
    return null;
  }

  @Override
  public void restoreState(JsonObject state) {
    // Do nothing
  }
  
}
