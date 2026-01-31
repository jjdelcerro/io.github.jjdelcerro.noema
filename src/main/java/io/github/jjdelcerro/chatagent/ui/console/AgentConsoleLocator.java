package io.github.jjdelcerro.chatagent.ui.console;

import io.github.jjdelcerro.chatagent.ui.AgentUIManager;
import org.jline.reader.LineReader;
import org.jline.terminal.Terminal;

/**
 *
 * @author jjdelcerro
 */
public class AgentConsoleLocator {

    private static AgentUIManager agentUIManager = null;
  
    public static AgentUIManager getAgentUIManager(Terminal terminal, LineReader lineReader) {
        if( agentUIManager == null ) {
          agentUIManager = new AgentConsoleManagerImpl(terminal, lineReader);
        }
        return agentUIManager;
    }
  
}
