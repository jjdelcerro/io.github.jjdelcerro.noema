package io.github.jjdelcerro.chatagent.ui;

import io.github.jjdelcerro.chatagent.lib.Agent;
import io.github.jjdelcerro.chatagent.lib.AgentConsole;
import java.io.File;

/**
 *
 * @author jjdelcerro
 */
public interface AgentUIManager {

    public AgentConsole createConsole();

    public AgentUISettings createSettings(Agent agent);
    
    public AgentUISettings createSettings(File settingsFile, AgentConsole console);
  
}
