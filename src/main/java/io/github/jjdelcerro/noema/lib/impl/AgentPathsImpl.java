package io.github.jjdelcerro.noema.lib.impl;

import static io.github.jjdelcerro.noema.lib.AgentManager.AGENT_NAME;
import io.github.jjdelcerro.noema.lib.AgentPaths;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 *
 * @author jjdelcerro
 */
public class AgentPathsImpl implements AgentPaths {


  private Path workspaceFolder;

  public AgentPathsImpl(Path workspaceFolder) {
    // Si workspaceFolder es null, solo tenemos acceso a getGlobalConfigFolder()
    // Se utiliza en el boot de la aplicacion.
    if( workspaceFolder != null ) {
      this.workspaceFolder = workspaceFolder.normalize().toAbsolutePath();
    }
  }

  @Override
  public Path getWorkspaceFolder() {
    return this.workspaceFolder;

  }

  @Override
  public void setupHierarchy() {
    if( workspaceFolder == null ) {
      return;
    }
    try {
      Files.createDirectories(this.getConfigFolder());
      Files.createDirectories(this.getDataFolder());
      Files.createDirectories(this.getLogFolder());
      Files.createDirectories(this.getCacheFolder());
      Files.createDirectories(this.getTempFolder());
      Files.createDirectories(this.getSandboxHomeFolder());
    } catch (IOException ex) {
      throw new RuntimeException("Can't create " + AGENT_NAME + " agent folder Hierarchy", ex);
    }
  }

  @Override
  public Path getAgentFolder() {
    if( workspaceFolder == null ) {
      return null;
    }
    return this.workspaceFolder.resolve(AGENT_FOLDER_NAME);
  }

  @Override
  public Path getConfigFolder() {
    if( workspaceFolder == null ) {
      return null;
    }
    return this.workspaceFolder.resolve(Path.of(AGENT_FOLDER_NAME, "var", "config"));
  }

  @Override
  public Path getDataFolder() {
    if( workspaceFolder == null ) {
      return null;
    }
    return this.workspaceFolder.resolve(Path.of(AGENT_FOLDER_NAME, "var", "lib"));
  }

  @Override
  public Path getCacheFolder() {
    if( workspaceFolder == null ) {
      return null;
    }
    return this.workspaceFolder.resolve(Path.of(AGENT_FOLDER_NAME, "var", "cache"));
  }

  @Override
  public Path getTempFolder() {
    if( workspaceFolder == null ) {
      return null;
    }
    return this.workspaceFolder.resolve(Path.of(AGENT_FOLDER_NAME, "var", "tmp"));
  }

  @Override
  public Path getLogFolder() {
    if( workspaceFolder == null ) {
      return null;
    }
    return this.workspaceFolder.resolve(Path.of(AGENT_FOLDER_NAME, "var", "log"));
  }

  @Override
  public Path getSandboxHomeFolder() {
    if( workspaceFolder == null ) {
      return null;
    }
    return this.workspaceFolder.resolve(Path.of(AGENT_FOLDER_NAME, "home"));
  }

  @Override
  public Path getGlobalConfigFolder() {
    return Path.of(System.getProperty("user.home"),AGENT_FOLDER_NAME);
  }
}
