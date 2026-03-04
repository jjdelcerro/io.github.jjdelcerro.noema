package io.github.jjdelcerro.noema.lib.impl;

import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.AgentActions;
import io.github.jjdelcerro.noema.lib.AgentActions.AgentAction;
import io.github.jjdelcerro.noema.lib.AgentConsole;
import io.github.jjdelcerro.noema.lib.AgentManager;
import io.github.jjdelcerro.noema.lib.AgentPaths;
import io.github.jjdelcerro.noema.lib.AgentServiceFactory;
import io.github.jjdelcerro.noema.lib.settings.AgentSettings;
import io.github.jjdelcerro.noema.lib.ConnectionSupplier;
import io.github.jjdelcerro.noema.lib.impl.services.reasoning.ReasoningServiceFactory;
import io.github.jjdelcerro.noema.lib.impl.services.documents.DocumentsServiceFactory;
import io.github.jjdelcerro.noema.lib.impl.services.email.EmailServiceFactory;
import io.github.jjdelcerro.noema.lib.impl.services.embeddings.EmbeddingsServiceFactory;
import io.github.jjdelcerro.noema.lib.impl.services.memory.MemoryServiceFactory;
import io.github.jjdelcerro.noema.lib.impl.services.scheduler.SchedulerServiceFactory;
import io.github.jjdelcerro.noema.lib.impl.services.sensors.SensorsServiceFactory;
import io.github.jjdelcerro.noema.lib.impl.services.telegram.TelegramServiceFactory;
import io.github.jjdelcerro.noema.lib.impl.settings.AgentSettingsImpl;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 *
 * @author jjdelcerro
 */
public class AgentManagerImpl implements AgentManager {

  private final Map<String,AgentServiceFactory> serviceFactories;
  private final Map<String,SQLProvider> sqlProvider;
  private final List<Supplier<AgentAction>> actions;
  
  public AgentManagerImpl() {
    this.serviceFactories = new LinkedHashMap<>();
    this.sqlProvider = new HashMap<>();
    this.actions = new ArrayList<>();
    
    this.registerService(new EmbeddingsServiceFactory());
    this.registerService(new SensorsServiceFactory());
    
    this.registerService(new MemoryServiceFactory());
    
    this.registerService(new SchedulerServiceFactory());
    this.registerService(new DocumentsServiceFactory());
    this.registerService(new EmailServiceFactory());
    this.registerService(new TelegramServiceFactory());

    this.registerService(new ReasoningServiceFactory());
  }
  
  @Override
  public final void registerService(AgentServiceFactory factory) {
    this.serviceFactories.put(factory.getName().toUpperCase(), factory);
  }
  
  @Override
  public Collection<AgentServiceFactory> getServiceFactories() {
    return this.serviceFactories.values();
  }
  
  @Override
  public AgentServiceFactory getServiceFactory(String name) {
    return this.serviceFactories.get(name.toUpperCase());
  }
  

  @Override
  public AgentActions createActions() {
    return new AgentActionsImpl();
  }

  @Override
  public AgentSettings createSettings(AgentPaths paths) {
    return new AgentSettingsImpl(paths);
  }

  @Override
  public Agent createAgent(ConnectionSupplier memoryDatabase, ConnectionSupplier servicesDatabase, AgentSettings settings, AgentConsole console) {
    Agent agent = new AgentImpl(memoryDatabase, servicesDatabase, settings, console);
    return agent;
  }
  
  @Override
  public SQLProvider getSQLProvider(String providerName) {
    SQLProvider prov = this.sqlProvider.get(providerName.toLowerCase());
    if( prov==null ) {
      prov = new SQLProviderImpl(providerName);
      this.sqlProvider.put(providerName, prov);
    }
    return prov;
  }

  @Override
  public void registerAction(Supplier<AgentActions.AgentAction> action) {
    this.actions.add(action);
  }

  @Override
  public Collection<Supplier<AgentActions.AgentAction>> getActions() {
    return this.actions;
  }

  @Override
  public AgentPaths createAgentPaths(Path workspacetFolder) {
    return new AgentPathsImpl(workspacetFolder);
  }

  @Override
  public String getName() {
     return AGENT_NAME;
  }

  @Override
  public String getVersion() {
    return "0.1.0";
  }
  
}
