package io.github.jjdelcerro.noema.ui.lanterna.settings;

import com.google.gson.JsonObject;
import com.googlecode.lanterna.gui2.Component;
import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.ui.common.AgentSettingsItemUI;

public class ValueItemLanterna extends AbstractAgentSettingsItemLanterna {

    public ValueItemLanterna(AgentSettingsItemUI parent, Agent agent, JsonObject json) {
        super(parent, agent, json);
    }

    @Override
    public Component getLanternaComponent() {
        return null; // Consumido por los selectores/combos padres
    }
}
