package io.github.jjdelcerro.noema.ui.lanterna.settings;

import com.google.gson.JsonObject;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.*;
import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.settings.AgentSettingsCheckedList;
import io.github.jjdelcerro.noema.ui.common.AgentSettingsItemUI;

import java.util.List;
import java.util.Optional;

public class CheckedListItemLanterna extends AbstractAgentSettingsItemLanterna {

    public CheckedListItemLanterna(AgentSettingsItemUI parent, Agent agent, JsonObject json) {
        super(parent, agent, json);
    }

    @Override
    public Component getLanternaComponent() {
        Panel panel = new Panel(new LinearLayout(Direction.VERTICAL));
        panel.addComponent(new Label(getLabel()));

        CheckBoxList<String> checkBoxList = new CheckBoxList<>(new TerminalSize(40, 8));
        AgentSettingsCheckedList savedData = agent.getSettings().getPropertyAsCheckedList(getVariableName());
        List<AgentSettingsItemUI> availableOptions = getChilds();

        if (availableOptions != null) {
            for (AgentSettingsItemUI option : availableOptions) {
                String technicalName = option.getValue();
                String displayName = option.getLabel().replace("_", " ");

                boolean isChecked = true;
                if (savedData != null) {
                    Optional<? extends AgentSettingsCheckedList.CheckedItem> match = savedData.getItems().stream()
                            .filter(i -> i.getValue().equals(technicalName))
                            .findFirst();

                    if (match.isPresent()) {
                        isChecked = match.get().isChecked();
                    }
                }
                checkBoxList.addItem(displayName, isChecked);
            }
        }

        // Listener al marcar/desmarcar casillas
        checkBoxList.addListener((index, checked) -> {
            if (availableOptions != null && index >= 0 && index < availableOptions.size()) {
                String technicalName = availableOptions.get(index).getValue();
                agent.getSettings().setChecked(getVariableName(), technicalName, checked);
                save();
            }
        });
        checkBoxList.setLayoutData(LinearLayout.createLayoutData(LinearLayout.Alignment.Fill));
        panel.addComponent(checkBoxList);

        // Botonera auxiliar
        Panel btnPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
        btnPanel.addComponent(new Button("Marcar todas", () -> setAllStates(checkBoxList, availableOptions, true)));
        btnPanel.addComponent(new Button("Desmarcar todas", () -> setAllStates(checkBoxList, availableOptions, false)));
        panel.addComponent(btnPanel);
        return panel;
    }

    private void setAllStates(CheckBoxList<String> checkBoxList, List<AgentSettingsItemUI> options, boolean state) {
        if (options == null) return;
        for (int i = 0; i < options.size(); i++) {
            checkBoxList.setChecked(options.get(i).getLabel().replace("_", " "), state);
            agent.getSettings().setChecked(getVariableName(), options.get(i).getValue(), state);
        }
        save();
    }
}
