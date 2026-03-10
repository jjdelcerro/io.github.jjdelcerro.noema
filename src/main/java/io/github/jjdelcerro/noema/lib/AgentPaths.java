package io.github.jjdelcerro.noema.lib;

import java.nio.file.Path;
import java.util.Collection;

/**
 *
 * @author jjdelcerro
 */
public interface AgentPaths {

  String AGENT_FOLDER_NAME = "noema-agent";

  void setupHierarchy();

  Path getAgentFolder();

  Path getConfigFolder();

  Path getCacheFolder();

  Path getTempFolder();

  Path getDataFolder();

  Path getLogFolder();

  Path getSandboxHomeFolder();

  Path getWorkspaceFolder();

  Path getGlobalConfigFolder();

  Path getAgentPath(String name);

  Path getConfigPath(String name);

  Collection<Path> listAgentPath(String name);
}
