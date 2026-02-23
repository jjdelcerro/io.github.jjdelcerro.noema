package io.github.jjdelcerro.noema.ui.swing;

import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.ui.AgentUIManager;
import org.jline.reader.LineReader;
import org.jline.terminal.Terminal;
import io.github.jjdelcerro.noema.lib.AgentConsole;
import io.github.jjdelcerro.noema.ui.AgentUISettings;
import java.io.File;

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
  public AgentUISettings createSettings(File dataFolder, AgentConsole console) {
    return new AgentSwingSettingsImpl(this, dataFolder, console);
  }
  
}
