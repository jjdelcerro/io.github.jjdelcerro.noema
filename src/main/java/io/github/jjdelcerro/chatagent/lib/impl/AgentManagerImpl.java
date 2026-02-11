package io.github.jjdelcerro.chatagent.lib.impl;

import io.github.jjdelcerro.chatagent.lib.Agent;
import io.github.jjdelcerro.chatagent.lib.AgentActions;
import io.github.jjdelcerro.chatagent.lib.AgentConsole;
import io.github.jjdelcerro.chatagent.lib.AgentManager;
import io.github.jjdelcerro.chatagent.lib.AgentServiceFactory;
import io.github.jjdelcerro.chatagent.lib.AgentSettings;
import io.github.jjdelcerro.chatagent.lib.ConnectionSupplier;
import io.github.jjdelcerro.chatagent.lib.impl.services.conversation.ConversationServiceFactory;
import io.github.jjdelcerro.chatagent.lib.impl.services.documents.DocumentsServiceFactory;
import io.github.jjdelcerro.chatagent.lib.impl.services.email.EmailServiceFactory;
import io.github.jjdelcerro.chatagent.lib.impl.services.embeddings.EmbeddingsServiceFactory;
import io.github.jjdelcerro.chatagent.lib.impl.services.memory.MemoryServiceFactory;
import io.github.jjdelcerro.chatagent.lib.impl.services.scheduler.SchedulerServiceFactory;
import io.github.jjdelcerro.chatagent.lib.impl.services.telegram.TelegramServiceFactory;
import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 *
 * @author jjdelcerro
 */
public class AgentManagerImpl implements AgentManager {

  private final Map<String,AgentServiceFactory> serviceFactories;
  private Map<String,SQLProvider> sqlProvider;
  
  public AgentManagerImpl() {
    this.serviceFactories = new LinkedHashMap<>();
    this.sqlProvider = new HashMap<>();
    
    this.registerService(new EmbeddingsServiceFactory());
    this.registerService(new MemoryServiceFactory());
    this.registerService(new ConversationServiceFactory());
    this.registerService(new SchedulerServiceFactory());
    this.registerService(new DocumentsServiceFactory());
    this.registerService(new EmailServiceFactory());
    this.registerService(new TelegramServiceFactory());
  }
  
  public final void registerService(AgentServiceFactory factory) {
    this.serviceFactories.put(factory.getName().toUpperCase(), factory);
  }
  
  public Collection<AgentServiceFactory> getServiceFactories() {
    return this.serviceFactories.values();
  }
  
  public AgentServiceFactory getServiceFactory(String name) {
    return this.serviceFactories.get(name.toUpperCase());
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
  public Agent createAgent(ConnectionSupplier memoryDatabase, ConnectionSupplier servicesDatabase, File dataFolder, AgentSettings settings, AgentConsole console) {
    Agent agent = new AgentImpl(memoryDatabase, servicesDatabase, dataFolder, settings, console);
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

}
