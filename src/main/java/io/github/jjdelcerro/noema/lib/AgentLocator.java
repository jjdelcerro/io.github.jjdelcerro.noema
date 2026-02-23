package io.github.jjdelcerro.noema.lib;

import io.github.jjdelcerro.noema.lib.impl.AgentManagerImpl;

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
