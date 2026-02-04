package io.github.jjdelcerro.chatagent.lib;

import java.io.File;
import java.sql.Connection;
import java.util.Collection;

/**
 *
 * @author jjdelcerro
 */
public interface AgentManager {
  
  public Agent createAgent(Connection knowledgeDatabase, Connection servicesDatabase, File dataFolder, AgentSettings settings, AgentConsole console);
  
  public AgentActions createActions();
  
  public AgentSettings createSettings();

  public AgentServiceFactory getServiceFactory(String name);
  
  public void registerService(AgentServiceFactory factory);
  
  public Collection<AgentServiceFactory> getServiceFactories();
  
    
}
