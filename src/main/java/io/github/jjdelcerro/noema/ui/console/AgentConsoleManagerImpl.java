package io.github.jjdelcerro.noema.ui.console;

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
public class AgentConsoleManagerImpl implements AgentUIManager {
  
  private final Terminal terminal;
  private final LineReader lineReader;
  
  public AgentConsoleManagerImpl(Terminal terminal, LineReader lineReader) {
    this.terminal = terminal;
    this.lineReader = lineReader;    
  }

  @Override
  public AgentConsole createConsole() {
    return new AgentConsoleImpl(terminal, lineReader);
  }

  @Override
  public AgentUISettings createSettings(Agent agent) {
    return new AgentConsoleSettingsImpl(this, agent);
  }
  
  @Override
  public AgentUISettings createSettings(File dataFolder, AgentConsole console) {
    return new AgentConsoleSettingsImpl(this, dataFolder, console);
  }
  
}
