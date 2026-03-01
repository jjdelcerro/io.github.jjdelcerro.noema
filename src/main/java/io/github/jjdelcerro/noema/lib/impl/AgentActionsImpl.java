package io.github.jjdelcerro.noema.lib.impl;

import io.github.jjdelcerro.noema.lib.AgentActions;
import io.github.jjdelcerro.noema.lib.settings.AgentSettings;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author jjdelcerro
 */
public class AgentActionsImpl implements AgentActions {

  private static final Logger LOGGER = LoggerFactory.getLogger(AgentActionsImpl.class);

  private Map<String, AgentAction> actions;

  public AgentActionsImpl() {
    this.actions = new HashMap<>();
  }

  @Override
  public synchronized void addAction(AgentAction action) {
    if (action == null) {
      LOGGER.warn("Action is null.");
      return;
    }
    this.actions.put(action.getName(), action);
    LOGGER.info("add action '"+action.getName()+"'.");
  }

  @Override
  public synchronized boolean call(String name, AgentSettings settings) {
    try {
      AgentAction action = this.actions.get(name);
      if (action == null) {
        LOGGER.warn("Action '" + name + "' not found.");
        return false;
      }
      return action.perform(settings);
    } catch (Exception ex) {
      LOGGER.warn("Can't perform action '" + name + "'.", ex);
      return false;
    }
  }

}
