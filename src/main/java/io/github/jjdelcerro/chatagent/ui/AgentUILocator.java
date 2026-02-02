package io.github.jjdelcerro.chatagent.ui;

/**
 *
 * @author jjdelcerro
 */
public class AgentUILocator {

    private static AgentUIManager agentUIManager = null;
  
    public static void registerAgentUIManager(AgentUIManager theAgentUIManager) {
        agentUIManager = theAgentUIManager;
    }
  
    public static AgentUIManager getAgentUIManager() {
        return agentUIManager;
    }
}
