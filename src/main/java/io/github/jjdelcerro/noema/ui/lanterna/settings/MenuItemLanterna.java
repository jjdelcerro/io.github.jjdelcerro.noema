package io.github.jjdelcerro.noema.ui.lanterna.settings;

import com.google.gson.JsonObject;
import com.googlecode.lanterna.gui2.Component;
import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.ui.common.AgentSettingsItemUI;

public class MenuItemLanterna extends AbstractAgentSettingsItemLanterna {

    public MenuItemLanterna(AgentSettingsItemUI parent, Agent agent, JsonObject json) {
        super(parent, agent, json);
    }

    @Override
    public Component getLanternaComponent() {
        // Los menús son contenedores; sus hijos son los que generan componentes de formulario
        return null;
    }
}
