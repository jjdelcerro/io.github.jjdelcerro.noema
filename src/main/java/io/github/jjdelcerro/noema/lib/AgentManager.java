package io.github.jjdelcerro.noema.lib;

import io.github.jjdelcerro.noema.lib.AgentActions.AgentAction;
import io.github.jjdelcerro.noema.lib.impl.SQLProvider;
import io.github.jjdelcerro.noema.lib.settings.AgentSettings;
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

  public Agent.ModelParameters getModelParameters(String providerUrl, String providerApiKey, String modelId);

  void registerSubagent(Subagent subagent);

  void unregisterSubagent(Subagent subagent);

  List<Subagent> getSubagents();

  Subagent getSubagent(int id);

  Subagent createSubagent(Agent parent, SubagentDefinition definition, Path workspace);

  SubagentDefinition createSubagentDefinition(Path xmlPath) throws IOException;
}
