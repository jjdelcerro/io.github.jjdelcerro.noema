package io.github.jjdelcerro.noema.ui.lanterna.settings;


import com.google.gson.JsonObject;
import com.googlecode.lanterna.gui2.Component;
import com.googlecode.lanterna.gui2.Direction;
import com.googlecode.lanterna.gui2.Label;
import com.googlecode.lanterna.gui2.LinearLayout;
import com.googlecode.lanterna.gui2.Panel;
import com.googlecode.lanterna.gui2.TextBox;
import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.ui.common.AgentSettingsItemUI;
import io.github.jjdelcerro.noema.ui.lanterna.LanternaUtils;

public class InputStringItemLanterna extends AbstractAgentSettingsItemLanterna {

    public InputStringItemLanterna(AgentSettingsItemUI parent, Agent agent, JsonObject json) {
        super(parent, agent, json);
    }

    @Override
    public Component getLanternaComponent() {
        Panel panel = new Panel(new LinearLayout(Direction.VERTICAL));
        panel.addComponent(new Label(getLabel()));

        String currentVal = agent.getSettings().getPropertyAsString(getVariableName(), "");
        TextBox textBox = new TextBox(currentVal);
        textBox.setTheme(LanternaUtils.getInputTheme());
        
        // Guardar cuando pierda foco o cambie
        textBox.setTextChangeListener((newText, changedByUser) -> {
            if (changedByUser) {
                agent.getSettings().setProperty(getVariableName(), newText);
                save(); // Guarda e invoca la acción si existe
            }
        });
        textBox.setLayoutData(LinearLayout.createLayoutData(LinearLayout.Alignment.Fill));
        panel.addComponent(textBox);
        return panel;
    }
}
