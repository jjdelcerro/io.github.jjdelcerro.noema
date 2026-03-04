package io.github.jjdelcerro.noema.lib;

import io.github.jjdelcerro.noema.lib.settings.AgentSettings;

/**
 *
 * @author jjdelcerro
 */
public interface AgentActions {

  public static final String CHANGE_MEMORY_PROVIDER = "CHANGE_MEMORY_PROVIDER";
  public static final String CHANGE_MEMORY_MODEL = "CHANGE_MEMORY_MODEL";
  public static final String CHANGE_REASONING_PROVIDER = "CHANGE_REASONING_PROVIDER";
  public static final String CHANGE_REASONING_MODEL = "CHANGE_REASONING_MODEL";

  public interface AgentAction {
    public boolean perform(AgentSettings settings);
    public String getName();    
    public void set(String name, Object value);
    public Object get(String name);
    public Agent getAgent();
    public void setAgent(Agent agent);
  }

  public void addAction(AgentAction action);

  public boolean call(String name, AgentSettings settings);
}
