package io.github.jjdelcerro.noema.lib;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

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

  List<Path> listAgentPath(String name);
}
