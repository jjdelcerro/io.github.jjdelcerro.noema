package io.github.jjdelcerro.noema.ui.lanterna.settings;

import com.google.gson.JsonObject;
import com.googlecode.lanterna.gui2.Component;
import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.ui.common.AbstractAgentSettingsItemUI;
import io.github.jjdelcerro.noema.ui.common.AgentSettingsItemUI;

public abstract class AbstractAgentSettingsItemLanterna extends AbstractAgentSettingsItemUI {

    protected AbstractAgentSettingsItemLanterna(AgentSettingsItemUI parent, Agent agent, JsonObject json) {
        super(parent, agent, json);
    }

    /**
     * Retorna el componente o panel de formulario en Lanterna para este ajuste.
     */
    public abstract Component getLanternaComponent();

    @Override
    protected AgentSettingsItemUI createItem(AgentSettingsItemUI parent, Agent agent, JsonObject jsonItem) {
        String type = jsonItem.get("type").getAsString().toLowerCase();
        return switch (type) {
            case "menu" -> new MenuItemLanterna(parent, agent, jsonItem);
            case "inputstring" -> new InputStringItemLanterna(parent, agent, jsonItem);
            case "combo", "selectoption" -> new ComboItemLanterna(parent, agent, jsonItem);
            case "checkedlist" -> new CheckedListItemLanterna(parent, agent, jsonItem);
            case "paths" -> new PathsItemLanterna(parent, agent, jsonItem);
            case "action" -> new ActionItemLanterna(parent, agent, jsonItem);
            default -> new ValueItemLanterna(parent, agent, jsonItem);
        };
    }
}
