package io.github.jjdelcerro.noema.ui.swing;

import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.ui.AgentUIManager;
import io.github.jjdelcerro.noema.lib.AgentConsole;
import io.github.jjdelcerro.noema.lib.AgentPaths;
import io.github.jjdelcerro.noema.lib.settings.AgentSettings;
import io.github.jjdelcerro.noema.ui.AgentUISettings;

/**
 *
 * @author jjdelcerro
 */
public class AgentSwingManagerImpl implements AgentUIManager {

  private final AgentConsole console;
  
  public AgentSwingManagerImpl(AgentConsole console) {
    this.console = console;
  }

  @Override
  public AgentConsole createConsole() {
    return this.console;
  }

  @Override
  public AgentUISettings createSettings(Agent agent) {
    return new AgentSwingSettingsImpl(this, agent);
  }
  
  @Override
  public AgentUISettings createSettings(AgentSettings settings) {
    return new AgentSwingSettingsImpl(this, settings);
  }
  
}
