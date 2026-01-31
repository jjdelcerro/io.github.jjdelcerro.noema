package io.github.jjdelcerro.chatagent.lib;

import io.github.jjdelcerro.chatagent.lib.impl.AgentManagerImpl;
import java.io.Reader;

/**
 *
 * @author jjdelcerro
 */
public class AgentLocator {

    private static AgentManager agentManager = null;
  
    public static AgentManager getAgentManager() {
        if( agentManager == null ) {
          agentManager = new AgentManagerImpl();
        }
        return agentManager;
    }  
}
