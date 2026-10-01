package io.github.jjdelcerro.noema.lib.impl;

import io.github.jjdelcerro.noema.lib.impl.model.ModelParametersImpl;
import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.model.ModelParameters;
import io.github.jjdelcerro.noema.lib.AgentActions;
import io.github.jjdelcerro.noema.lib.AgentActions.AgentAction;
import io.github.jjdelcerro.noema.lib.AgentConsole;
import io.github.jjdelcerro.noema.lib.AgentManager;
import io.github.jjdelcerro.noema.lib.AgentPaths;
import io.github.jjdelcerro.noema.lib.AgentServiceFactory;
import io.github.jjdelcerro.noema.lib.ConnectionSupplier;
import io.github.jjdelcerro.noema.lib.services.subagents.Subagent;
import io.github.jjdelcerro.noema.lib.impl.memory.projected.operations.PendingAnnotationOperationFactory;
import io.github.jjdelcerro.noema.lib.impl.memory.projected.operations.PeripheralAwarenessOperationFactory;
import io.github.jjdelcerro.noema.lib.impl.memory.projected.operations.PinnedTurnsOperationFactory;
import io.github.jjdelcerro.noema.lib.impl.memory.projected.operations.TemporalPerceptionOperationFactory;
import io.github.jjdelcerro.noema.lib.impl.memory.projected.operations.TrimmingOperationFactory;
import io.github.jjdelcerro.noema.lib.impl.model.ModelUtils;
import io.github.jjdelcerro.noema.lib.impl.model.langchainopenai.ChatModelLangchainOpenAI;
import io.github.jjdelcerro.noema.lib.impl.services.embeddings.EmbeddingsServiceFactory;
import io.github.jjdelcerro.noema.lib.impl.services.mcp.McpServiceFactory;
import io.github.jjdelcerro.noema.lib.impl.services.memory.MemoryConsolidationServiceFactory;
import io.github.jjdelcerro.noema.lib.impl.services.reasoning.ReasoningServiceFactory;
import io.github.jjdelcerro.noema.lib.impl.services.scheduler.SchedulerServiceFactory;
import io.github.jjdelcerro.noema.lib.impl.services.scripting.ScriptingServiceFactory;
import io.github.jjdelcerro.noema.lib.impl.services.sensors.SensorsServiceFactory;
import io.github.jjdelcerro.noema.lib.impl.services.skills.SkillsServiceFactory;
import io.github.jjdelcerro.noema.lib.impl.services.subagents.SubagentsServiceFactory;
import io.github.jjdelcerro.noema.lib.impl.settings.AgentSettingsImpl;
import io.github.jjdelcerro.noema.lib.memory.projected.ProjectedMemoryOperationFactory;
import io.github.jjdelcerro.noema.lib.settings.AgentSettings;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.apache.commons.lang3.StringUtils;

/**
 * Default implementation of AgentManager. Central registry and factory for
 * agent services, actions, paths, and active subagents.
 */
public class AgentManagerImpl implements AgentManager {

  private final Map<String, AgentServiceFactory> serviceFactories;
  private final Map<String, SQLProvider> sqlProvider;
  private final List<Supplier<AgentAction>> actions;
  private final Map<Integer, Subagent> activeSubagents;
  private final Map<String, ProjectedMemoryOperationFactory> projectedMemoryOperationFactories;

  public AgentManagerImpl() {
    this.serviceFactories = new LinkedHashMap<>();
    this.projectedMemoryOperationFactories = new LinkedHashMap<>();
    this.sqlProvider = new HashMap<>();
    this.actions = new ArrayList<>();
    this.activeSubagents = new ConcurrentHashMap<>();

    this.registerProjectedMemoryOperation(new TrimmingOperationFactory());
    this.registerProjectedMemoryOperation(new PendingAnnotationOperationFactory());
    this.registerProjectedMemoryOperation(new TemporalPerceptionOperationFactory());
    this.registerProjectedMemoryOperation(new PinnedTurnsOperationFactory());
    this.registerProjectedMemoryOperation(new PeripheralAwarenessOperationFactory());

    this.registerService(new EmbeddingsServiceFactory());
    this.registerService(new SensorsServiceFactory());

    this.registerService(new MemoryConsolidationServiceFactory());
    this.registerService(new McpServiceFactory());

    this.registerService(new SchedulerServiceFactory());
//    this.registerService(new EmailServiceFactory());
//    this.registerService(new TelegramServiceFactory());
    this.registerService(new SkillsServiceFactory());
    this.registerService(new SubagentsServiceFactory());
    this.registerService(new ScriptingServiceFactory());

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
    if (prov == null) {
      prov = new SQLProviderImpl(providerName);
      this.sqlProvider.put(providerName.toLowerCase(), prov);
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
  public AgentPaths createAgentPaths(Path workspaceFolder) {
    return new AgentPathsImpl(workspaceFolder);
  }

  @Override
  public String getName() {
    return AGENT_NAME;
  }

  @Override
  public String getVersion() {
    return "0.1.0";
  }

// =========================================================================
  // SUBAGENT REGISTRY AND FACTORY
  // =========================================================================
  @Override
  public void registerSubagent(Subagent subagent) {
    if (subagent != null) {
      this.activeSubagents.put(subagent.getId(), subagent);
    }
  }

  @Override
  public void unregisterSubagent(Subagent subagent) {
    if (subagent != null) {
      this.activeSubagents.remove(subagent.getId());
    }
  }

  @Override
  public List<Subagent> getSubagents() {
    return new ArrayList<>(this.activeSubagents.values());
  }

  @Override
  public Subagent getSubagent(int id) {
    return this.activeSubagents.get(id);
  }

  @Override
  public final void registerProjectedMemoryOperation(ProjectedMemoryOperationFactory factory) {
    if (factory != null) {
      this.projectedMemoryOperationFactories.put(factory.getName().toUpperCase(), factory);
    }
  }

  @Override
  public ProjectedMemoryOperationFactory getProjectedMemoryOperationFactory(String name) {
    if (name == null) {
      return null;
    }
    return this.projectedMemoryOperationFactories.get(name.toUpperCase());
  }

  @Override
  public Collection<ProjectedMemoryOperationFactory> getProjectedMemoryOperationFactories() {
    return this.projectedMemoryOperationFactories.values();
  }

  @Override
  public ModelParameters getModelParameters(AgentPaths paths, String providerUrl, String providerApiKey, String modelId, double temperature) {
    Map<String, Object> extraParams;
    if (StringUtils.startsWith(StringUtils.strip(modelId), "{")) {
      extraParams = ModelUtils.parseModel(modelId);
      modelId = (String) extraParams.get("model");
    } else {
      extraParams = new HashMap<>();
    }
    Set<String> keys = new HashSet<>(extraParams.keySet());
    for (String key : keys) {
      Object value = extraParams.get(key);
      if( value instanceof String value_s ){
        if( StringUtils.containsIgnoreCase(value_s, "${LocalAgentFolder}") ) {
          String s = StringUtils.replaceIgnoreCase(value_s, "${LocalAgentFolder}", paths.getAgentFolder().toString());
          extraParams.put(key, s);
        }
        if( StringUtils.containsIgnoreCase(value_s, "${GlobalAgentFolder}") ) {
          String s = StringUtils.replaceIgnoreCase(value_s, "${GlobalAgentFolder}", paths.getGlobalConfigFolder().toString());
          extraParams.put(key, s);
        }
      }
    }
    ModelParameters modelParameters = new ModelParametersImpl(providerUrl, providerApiKey, modelId, temperature, extraParams);
    return modelParameters;
  }
}
