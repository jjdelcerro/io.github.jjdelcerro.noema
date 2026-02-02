package io.github.jjdelcerro.chatagent.ui.swing;

import io.github.jjdelcerro.chatagent.ui.AgentUIManager;
import org.jline.reader.LineReader;
import org.jline.terminal.Terminal;

/**
 *
 * @author jjdelcerro
 */
public class AgentSwingLocator {

    private static AgentUIManager agentUIManager = null;
  
    public static AgentUIManager getAgentUIManager() {
        if( agentUIManager == null ) {
          agentUIManager = new AgentSwingManagerImpl();
        }
        return agentUIManager;
    }
  
}
