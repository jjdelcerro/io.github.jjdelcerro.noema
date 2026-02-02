package io.github.jjdelcerro.chatagent.ui.console;

import io.github.jjdelcerro.chatagent.ui.AgentUILocator;
import org.jline.reader.LineReader;
import org.jline.terminal.Terminal;

/**
 *
 * @author jjdelcerro
 */
public class AgentConsoleInitializer {

  public static void init(Terminal terminal, LineReader lineReader) {
    AgentUILocator.registerAgentUIManager(new AgentConsoleManagerImpl(terminal, lineReader));
  }
}
