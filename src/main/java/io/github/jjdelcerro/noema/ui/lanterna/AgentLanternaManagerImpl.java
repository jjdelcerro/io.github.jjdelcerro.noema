package io.github.jjdelcerro.noema.ui.lanterna;

import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.AgentConsole;
import io.github.jjdelcerro.noema.lib.settings.AgentSettings;
import io.github.jjdelcerro.noema.ui.AgentUIManager;
import io.github.jjdelcerro.noema.ui.AgentUISettings;

public class AgentLanternaManagerImpl implements AgentUIManager {

    private final AgentConsole console;

    public AgentLanternaManagerImpl(AgentConsole console) {
        this.console = console;
    }

    @Override
    public AgentConsole createConsole() {
        return this.console;
    }

    @Override
    public AgentUISettings createSettings(Agent agent) {
        return new AgentLanternaSettingsImpl(this, agent);
    }

    @Override
    public AgentUISettings createSettings(AgentSettings settings) {
        return new AgentLanternaSettingsImpl(this, settings);
    }
}
