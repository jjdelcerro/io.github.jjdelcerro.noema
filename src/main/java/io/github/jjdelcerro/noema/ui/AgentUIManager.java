package io.github.jjdelcerro.noema.ui;

import io.github.jjdelcerro.noema.lib.api.Agent;
import io.github.jjdelcerro.noema.lib.api.AgentConsole;
import io.github.jjdelcerro.noema.lib.api.settings.AgentSettings;

/**
 *
 * @author jjdelcerro
 */
public interface AgentUIManager {

    public AgentConsole createConsole();

    public AgentUISettings createSettings(Agent agent);
    
    public AgentUISettings createSettings(AgentSettings settings, AgentConsole console);
  
}
