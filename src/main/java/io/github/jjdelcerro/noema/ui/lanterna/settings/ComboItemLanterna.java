package io.github.jjdelcerro.noema.ui.lanterna.settings;


import com.google.gson.JsonObject;
import com.googlecode.lanterna.gui2.ComboBox;
import com.googlecode.lanterna.gui2.Component;
import com.googlecode.lanterna.gui2.Direction;
import com.googlecode.lanterna.gui2.Label;
import com.googlecode.lanterna.gui2.LinearLayout;
import com.googlecode.lanterna.gui2.Panel;
import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.ui.common.AgentSettingsItemUI;
import java.util.List;

public class ComboItemLanterna extends AbstractAgentSettingsItemLanterna {

    public ComboItemLanterna(AgentSettingsItemUI parent, Agent agent, JsonObject json) {
        super(parent, agent, json);
    }

    @Override
    public Component getLanternaComponent() {
        Panel panel = new Panel(new LinearLayout(Direction.VERTICAL));
        panel.addComponent(new Label(getLabel()));

        ComboBox<String> comboBox = new ComboBox<>();
        List<AgentSettingsItemUI> childs = getChilds();
        
        String currentVal = agent.getSettings().getPropertyAsString(getVariableName(), "");

        if (childs != null) {
            for (AgentSettingsItemUI child : childs) {
                comboBox.addItem(child.getLabel());
                if (child.getValue().equals(currentVal)) {
                    comboBox.setSelectedItem(child.getLabel());
                }
            }
        }

        comboBox.addListener((selectedIndex, previousIndex, itemChangedByUser) -> {
            if (itemChangedByUser && childs != null && selectedIndex >= 0) {
                String selectedVal = childs.get(selectedIndex).getValue();
                agent.getSettings().setProperty(getVariableName(), selectedVal);
                save();
            }
        });
        comboBox.setLayoutData(LinearLayout.createLayoutData(LinearLayout.Alignment.Fill));
        panel.addComponent(comboBox);
        return panel;
    }
}
