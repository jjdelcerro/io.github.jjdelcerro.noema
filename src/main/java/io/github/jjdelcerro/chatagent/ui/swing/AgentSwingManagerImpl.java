package io.github.jjdelcerro.chatagent.ui.swing;

import io.github.jjdelcerro.chatagent.ui.console.*;
import io.github.jjdelcerro.chatagent.lib.Agent;
import io.github.jjdelcerro.chatagent.ui.AgentUIManager;
import org.jline.reader.LineReader;
import org.jline.terminal.Terminal;
import io.github.jjdelcerro.chatagent.lib.AgentConsole;
import io.github.jjdelcerro.chatagent.ui.AgentUISettings;
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
