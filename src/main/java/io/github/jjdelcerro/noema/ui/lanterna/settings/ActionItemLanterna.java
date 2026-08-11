package io.github.jjdelcerro.noema.ui.lanterna.settings;


import com.google.gson.JsonObject;
import com.googlecode.lanterna.gui2.Button;
import com.googlecode.lanterna.gui2.Component;
import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.ui.common.AgentSettingsItemUI;
import io.github.jjdelcerro.noema.ui.lanterna.settings.AbstractAgentSettingsItemLanterna;

public class ActionItemLanterna extends AbstractAgentSettingsItemLanterna {

    public ActionItemLanterna(AgentSettingsItemUI parent, Agent agent, JsonObject json) {
        super(parent, agent, json);
    }

    @Override
    public Component getLanternaComponent() {
        return new Button(getLabel(), () -> {
            if (getActionName() != null) {
                agent.getActions().call(getActionName(), agent.getSettings());
            }
        });
    }
}
