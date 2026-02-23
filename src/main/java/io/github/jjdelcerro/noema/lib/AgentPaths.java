package io.github.jjdelcerro.noema.lib;

import java.nio.file.Path;

/**
 *
 * @author jjdelcerro
 */
public interface AgentPaths {

  String AGENT_FOLDER_NAME = "_noema-agent"; // de momento, un "_" al inicio por comodida, pero deberia ser un "."

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
}
