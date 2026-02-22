package io.github.jjdelcerro.chatagent.lib;

import io.github.jjdelcerro.chatagent.lib.AgentActions.AgentAction;
import io.github.jjdelcerro.chatagent.lib.impl.SQLProvider;
import java.io.File;
import java.util.Collection;
import java.util.function.Supplier;

/**
 *
 * @author jjdelcerro
 */
public interface AgentManager {
  
  public Agent createAgent(ConnectionSupplier memoryDatabase, ConnectionSupplier servicesDatabase, File agentFolder, AgentSettings settings, AgentConsole console);
  
  public AgentActions createActions();
  
  public AgentSettings createSettings();

  public AgentServiceFactory getServiceFactory(String name);
  
  public void registerService(AgentServiceFactory factory);
  
  public Collection<AgentServiceFactory> getServiceFactories();
  
  public SQLProvider getSQLProvider(String providerName);
  
  public void registerAction(Supplier<AgentAction> action);
  
  public Collection<Supplier<AgentActions.AgentAction>> getActions();
}
