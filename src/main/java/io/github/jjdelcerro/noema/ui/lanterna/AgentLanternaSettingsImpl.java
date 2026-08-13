package io.github.jjdelcerro.noema.ui.lanterna;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.*;
import com.googlecode.lanterna.gui2.dialogs.MessageDialog;

import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.AgentConsole;
import io.github.jjdelcerro.noema.lib.settings.AgentSettings;
import io.github.jjdelcerro.noema.ui.AgentUIManager;
import io.github.jjdelcerro.noema.ui.AgentUISettings;
import io.github.jjdelcerro.noema.ui.common.AgentSettingsItemUI;
import io.github.jjdelcerro.noema.ui.common.FakeAgent;
import io.github.jjdelcerro.noema.ui.lanterna.settings.AbstractAgentSettingsItemLanterna;
import io.github.jjdelcerro.noema.ui.lanterna.settings.MenuItemLanterna;

import java.io.FileReader;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

public class AgentLanternaSettingsImpl implements AgentUISettings {

    private final Agent agent;
    private Panel detailPanel;
    private ActionListBox navigationList;

    public AgentLanternaSettingsImpl(AgentUIManager manager, Agent agent) {
        this.agent = agent;
    }

    public AgentLanternaSettingsImpl(AgentUIManager manager, AgentSettings settings) {
        this(manager, new FakeAgent(settings));
    }

    @Override
    public void showWindow() {
        MultiWindowTextGUI gui = null;
        AgentConsole console = agent.getCurrentConsole();
        if (console instanceof AgentLanternaConsoleImpl lanternaConsole) {
            gui = lanternaConsole.getGui();
        }

        if (gui == null) {
            return;
        }

        BasicWindow window = new BasicWindow("Configuración de Noema");
        window.setHints(Arrays.asList(Window.Hint.CENTERED));

        Panel rootPanel = new Panel(new LinearLayout(Direction.VERTICAL));

        // Panel Principal Dividido (Izquierda: Menú / Derecha: Formulario)
        Panel bodyPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));

        navigationList = new ActionListBox(new TerminalSize(30, 23));
        detailPanel = new Panel(new LinearLayout(Direction.VERTICAL));
        detailPanel.setPreferredSize(new TerminalSize(45, 23));

        bodyPanel.addComponent(navigationList.withBorder(Borders.singleLine("Secciones")));
        bodyPanel.addComponent(detailPanel.withBorder(Borders.singleLine("Parámetros")));

        rootPanel.addComponent(bodyPanel);

        // Botón inferior para cerrar
        Button btnClose = new Button("Cerrar", window::close);
        rootPanel.addComponent(btnClose, LinearLayout.createLayoutData(LinearLayout.Alignment.End));

        window.setComponent(rootPanel);

        // Cargar árbol desde settingsui.json
        loadTree(gui);

        // Mostrar ventana de forma modal
        gui.addWindowAndWait(window);
    }

    private void loadTree(MultiWindowTextGUI gui) {
        Path settingsUIPath = agent.getPaths().getConfigFolder().resolve("settingsui.json");
        try (FileReader reader = new FileReader(settingsUIPath.toFile())) {
            JsonObject uiroot = JsonParser.parseReader(reader).getAsJsonObject();
            MenuItemLanterna rootItem = new MenuItemLanterna(null, agent, uiroot);

            // Poblamos el menú izquierdo con las secciones principales
            List<AgentSettingsItemUI> sections = rootItem.getChilds();
            if (sections != null) {
                for (AgentSettingsItemUI section : sections) {
                    if (section instanceof AbstractAgentSettingsItemLanterna lanternaItem) {
                        navigationList.addItem(section.getLabel(), () -> renderSectionDetails(lanternaItem));
                    }
                }
            }
        } catch (Exception e) {
            MessageDialog.showMessageDialog(gui, "Error", "No se pudo cargar settingsui.json: " + e.getMessage());
        }
    }

    /**
     * Reconstruye el panel de la derecha al seleccionar una sección de la izquierda.
     */
    private void renderSectionDetails(AbstractAgentSettingsItemLanterna sectionNode) {
        detailPanel.removeAllComponents();

        List<AgentSettingsItemUI> children = sectionNode.getChilds();
        if (children != null) {
            for (AgentSettingsItemUI child : children) {
                if (child instanceof AbstractAgentSettingsItemLanterna item) {
                    Component comp = item.getLanternaComponent();
                    if (comp != null) {
                        detailPanel.addComponent(comp);
                        detailPanel.addComponent(new EmptySpace(new TerminalSize(1, 1))); // Separador
                    }
                }
            }
        }
    }
}
