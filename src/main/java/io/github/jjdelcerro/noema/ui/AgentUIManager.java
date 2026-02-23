package io.github.jjdelcerro.noema.ui;

import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.AgentConsole;
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
