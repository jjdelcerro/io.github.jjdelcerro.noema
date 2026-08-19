package io.github.jjdelcerro.noema.lib;

import io.github.jjdelcerro.noema.lib.settings.AgentSettings;
import io.github.jjdelcerro.noema.ui.AgentUILocator;
import io.github.jjdelcerro.noema.ui.AgentUIManager;
import io.github.jjdelcerro.noema.ui.AgentUISettings;

public class FakeAgentUIManager implements AgentUIManager {

    private final AgentConsole console;

    public FakeAgentUIManager() {
        this(new FakeConsole());
    }

    public FakeAgentUIManager(AgentConsole console) {
        this.console = console != null ? console : new FakeConsole();
    }

    public static FakeAgentUIManager register() {
        FakeAgentUIManager manager = new FakeAgentUIManager();
        AgentUILocator.registerAgentUIManager(manager);
        return manager;
    }

    public static FakeAgentUIManager register(AgentConsole console) {
        FakeAgentUIManager manager = new FakeAgentUIManager(console);
        AgentUILocator.registerAgentUIManager(manager);
        return manager;
    }

    @Override
    public AgentConsole createConsole() {
        return this.console;
    }

    @Override
    public AgentUISettings createSettings(Agent agent) {
        return null;
    }

    @Override
    public AgentUISettings createSettings(AgentSettings settings, AgentConsole console) {
        return null;
    }
}
