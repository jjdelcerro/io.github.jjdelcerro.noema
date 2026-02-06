package io.github.jjdelcerro.chatagent.lib;

import io.github.jjdelcerro.chatagent.lib.impl.SQLProvider;
import java.io.File;
import java.util.Collection;

/**
 *
 * @author jjdelcerro
 */
public interface AgentManager {
  
  public Agent createAgent(ConnectionSupplier memoryDatabase, ConnectionSupplier servicesDatabase, File dataFolder, AgentSettings settings, AgentConsole console);
  
  public AgentActions createActions();
  
  public AgentSettings createSettings();

  public AgentServiceFactory getServiceFactory(String name);
  
  public void registerService(AgentServiceFactory factory);
  
  public Collection<AgentServiceFactory> getServiceFactories();
  
  public SQLProvider getSQLProvider(String providerName);
}
