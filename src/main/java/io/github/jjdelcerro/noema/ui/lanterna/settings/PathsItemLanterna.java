package io.github.jjdelcerro.noema.ui.lanterna.settings;

import com.google.gson.JsonObject;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.*;
import com.googlecode.lanterna.gui2.dialogs.TextInputDialog;
import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.ui.common.AgentSettingsItemUI;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class PathsItemLanterna extends AbstractAgentSettingsItemLanterna {

    public PathsItemLanterna(AgentSettingsItemUI parent, Agent agent, JsonObject json) {
        super(parent, agent, json);
    }

    @Override
    public Component getLanternaComponent() {
        Panel panel = new Panel(new LinearLayout(Direction.VERTICAL));
        panel.addComponent(new Label(getLabel()));

        RadioBoxList<String> pathList = new RadioBoxList<>(new TerminalSize(40, 5));
        List<Path> currentPaths = agent.getSettings().getPropertyAsPaths(getVariableName());

        if (currentPaths != null) {
            for (Path p : currentPaths) {
                pathList.addItem(p.toString());
            }
        }

        panel.addComponent(pathList);

        // Botonera de gestión de rutas
        Panel btnPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));

        btnPanel.addComponent(new Button("Añadir...", () -> {
            if (panel.getTextGUI() instanceof WindowBasedTextGUI textGUI) {
                String newPath = TextInputDialog.showDialog(
                        textGUI,
                        "Añadir Ruta",
                        "Introduce la ruta del archivo o directorio:",
                        ""
                );
                if (newPath != null && !newPath.trim().isEmpty()) {
                    pathList.addItem(newPath.trim());
                    savePathsFromList(pathList);
                }
            }
        }));

        btnPanel.addComponent(new Button("Eliminar", () -> {
            int selected = pathList.getSelectedIndex();
            if (selected >= 0) {
                pathList.removeItem(selected);
                savePathsFromList(pathList);
            }
        }));

        panel.addComponent(btnPanel);
        return panel;
    }

    private void savePathsFromList(RadioBoxList<String> pathList) {
        List<String> pathStrings = new ArrayList<>();
        for (int i = 0; i < pathList.getItemCount(); i++) {
            pathStrings.add(pathList.getItemAt(i));
        }
        agent.getSettings().setProperty(getVariableName(), pathStrings);
        save();
    }
}
