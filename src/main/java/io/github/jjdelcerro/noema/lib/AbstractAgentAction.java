package io.github.jjdelcerro.noema.lib;

import java.util.HashMap;

/**
 *
 * @author jjdelcerro
 */
public abstract class AbstractAgentAction implements AgentActions.AgentAction {

  private final String actionName;
  private final HashMap<String, Object> props;

  protected AbstractAgentAction(String actionName) {
    this.props = new HashMap<>();
    this.actionName = actionName;
  }

  protected AbstractAgentAction(Agent agent, String actionName) {
    this(actionName);
    this.setAgent(agent);
  }

  @Override
  public final String getName() {
    return this.actionName;
  }

  @Override
  public final void set(String name, Object value) {
    this.props.put(name, value);
  }

  @Override
  public final Object get(String name) {
    return this.props.get(name);
  }

  @Override
  public final Agent getAgent() {
    return (Agent) this.get("AGENT");
  }

  @Override
  public final void setAgent(Agent agent) {
    this.set("AGENT", agent);
  }
}
