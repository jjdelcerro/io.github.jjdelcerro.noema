package io.github.jjdelcerro.chatagent.lib.impl;

import io.github.jjdelcerro.chatagent.lib.Agent;
import io.github.jjdelcerro.chatagent.lib.AgentActions;
import io.github.jjdelcerro.chatagent.lib.AgentConsole;
import io.github.jjdelcerro.chatagent.lib.AgentManager;
import io.github.jjdelcerro.chatagent.lib.AgentSettings;
import java.io.File;
import java.sql.Connection;

/**
 *
 * @author jjdelcerro
 */
public class AgentManagerImpl implements AgentManager {
  
  public AgentManagerImpl() {
    
  }

  @Override
  public AgentActions createActions() {
    return new AgentActionsImpl();
  }

  @Override
  public AgentSettings createSettings() {
    return new AgentSettingsImpl();
  }

  @Override
  public Agent createAgent(Connection conn, File dataFolder, AgentSettings settings, AgentConsole console) {
    Agent agent = new AgentImpl(conn, dataFolder, settings, console);
    return agent;
  }

}
