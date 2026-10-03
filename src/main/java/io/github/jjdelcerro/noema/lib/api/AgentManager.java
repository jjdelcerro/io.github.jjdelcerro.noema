package io.github.jjdelcerro.noema.lib.api;

import io.github.jjdelcerro.noema.lib.api.model.ModelParameters;
import io.github.jjdelcerro.noema.lib.api.services.subagents.SubagentDefinition;
import io.github.jjdelcerro.noema.lib.api.services.subagents.Subagent;
import io.github.jjdelcerro.noema.lib.api.AgentActions.AgentAction;
import io.github.jjdelcerro.noema.lib.impl.SQLProvider;
import io.github.jjdelcerro.noema.lib.api.memory.projected.ProjectedMemoryOperationFactory;
import io.github.jjdelcerro.noema.lib.api.settings.AgentSettings;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

/**
 *
 * @author jjdelcerro
 */
public interface AgentManager {

  String AGENT_NAME = "Noema";

  public AgentPaths createAgentPaths(Path workspaceFolder);

  public AgentSettings createSettings(AgentPaths paths);

  public Agent createAgent(ConnectionSupplier memoryDatabase, ConnectionSupplier servicesDatabase, AgentSettings settings, AgentConsole console);

  public AgentActions createActions();

  public AgentServiceFactory getServiceFactory(String name);

  public void registerService(AgentServiceFactory factory);

  public Collection<AgentServiceFactory> getServiceFactories();

  public SQLProvider getSQLProvider(String providerName);

  public void registerAction(Supplier<AgentAction> action);

  public Collection<Supplier<AgentActions.AgentAction>> getActions();

  public String getName();

  public String getVersion();

  void registerSubagent(Subagent subagent);

  void unregisterSubagent(Subagent subagent);

  List<Subagent> getSubagents();

  Subagent getSubagent(int id);

  void registerProjectedMemoryOperation(ProjectedMemoryOperationFactory factory);

  ProjectedMemoryOperationFactory getProjectedMemoryOperationFactory(String name);

  Collection<ProjectedMemoryOperationFactory> getProjectedMemoryOperationFactories();  

  public ModelParameters getModelParameters(AgentPaths paths, String providerUrl, String providerApiKey, String modelId, double temperature);
}
