package io.github.jjdelcerro.noema.lib;

import java.nio.file.Path;

/**
 *
 * @author jjdelcerro
 */
public interface AgentPaths {

  String AGENT_FOLDER_NAME = "noema-agent";

  void setupHierarchy();

  Path getAgentFolder();

  Path getConfigFolder();
  
  Path getConfigFolder(String name);
  
  Path getCacheFolder();
  
  Path getTempFolder();

  Path getDataFolder();

  Path getLogFolder();

  Path getSandboxHomeFolder();

  Path getWorkspaceFolder();

  Path getGlobalConfigFolder();
}
