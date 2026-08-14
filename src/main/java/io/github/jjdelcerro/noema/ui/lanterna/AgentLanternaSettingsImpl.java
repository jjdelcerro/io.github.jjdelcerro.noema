package io.github.jjdelcerro.noema.ui.lanterna;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.ActionListBox;
import com.googlecode.lanterna.gui2.BasicWindow;
import com.googlecode.lanterna.gui2.Borders;
import com.googlecode.lanterna.gui2.Button;
import com.googlecode.lanterna.gui2.Component;
import com.googlecode.lanterna.gui2.Direction;
import com.googlecode.lanterna.gui2.EmptySpace;
import com.googlecode.lanterna.gui2.Label;
import com.googlecode.lanterna.gui2.LinearLayout;
import com.googlecode.lanterna.gui2.MultiWindowTextGUI;
import com.googlecode.lanterna.gui2.Panel;
import com.googlecode.lanterna.gui2.Window;
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
    private MultiWindowTextGUI explicitGui;
    private Panel detailPanel;
    private ActionListBox navigationList;

    public AgentLanternaSettingsImpl(AgentUIManager manager, Agent agent) {
        this.agent = agent;
    }

    public AgentLanternaSettingsImpl(MultiWindowTextGUI gui, Agent agent) {
        this.agent = agent;
        this.explicitGui = gui;
    }

    public AgentLanternaSettingsImpl(AgentUIManager manager, AgentSettings settings) {
        this(manager, new FakeAgent(settings));
    }

    @Override
    public void showWindow() {
        MultiWindowTextGUI gui = this.explicitGui;
        if (gui == null) {
            AgentConsole console = agent.getCurrentConsole();
            if (console instanceof AgentLanternaConsoleImpl lanternaConsole) {
                gui = lanternaConsole.getGui();
            }
        }

        if (gui == null) {
            return;
        }

        TerminalSize terminalSize = gui.getScreen().getTerminalSize();

        BasicWindow window = new BasicWindow("Configuración de Noema");
        window.setHints(Arrays.asList(Window.Hint.CENTERED));
        window.setTheme(LanternaUtils.getMainTheme());

        Panel rootPanel = new Panel(new LinearLayout(Direction.VERTICAL));
        rootPanel.setTheme(LanternaUtils.getMainTheme());

        Panel bodyPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
        bodyPanel.setTheme(LanternaUtils.getMainTheme());

        int navWidth = Math.max(30, (int) (0.35 * terminalSize.getColumns()));
        int detailWidth = Math.max(40, (int) (0.50 * terminalSize.getColumns()));
        int height = Math.max(15, terminalSize.getRows() - 10);

        navigationList = new ActionListBox(new TerminalSize(navWidth, height));
        navigationList.setTheme(LanternaUtils.getMainTheme());

//        detailPanel = new Panel(new LinearLayout(Direction.VERTICAL));
        detailPanel = new ScrollPanel(new LinearLayout(Direction.VERTICAL));

        detailPanel.setPreferredSize(new TerminalSize(detailWidth, height));
        detailPanel.setTheme(LanternaUtils.getMainTheme());

        bodyPanel.addComponent(navigationList.withBorder(Borders.singleLine("Secciones")));
        bodyPanel.addComponent(detailPanel); //.withBorder(Borders.singleLine("Parámetros")));

        rootPanel.addComponent(bodyPanel);

        Button btnClose = new Button("Cerrar", window::close);
        btnClose.setTheme(LanternaUtils.getMainTheme());
        rootPanel.addComponent(btnClose, LinearLayout.createLayoutData(LinearLayout.Alignment.End));

        window.setComponent(rootPanel);

        // Cargar árbol desde settingsui.json de forma inductiva/recursiva
        loadTree(gui);

        // Mostrar ventana de forma modal
        gui.addWindowAndWait(window);
    }

    private void loadTree(MultiWindowTextGUI gui) {
        Path settingsUIPath = agent.getPaths().getConfigFolder().resolve("settingsui.json");
        try (FileReader reader = new FileReader(settingsUIPath.toFile())) {
            JsonObject uiroot = JsonParser.parseReader(reader).getAsJsonObject();
            MenuItemLanterna rootItem = new MenuItemLanterna(null, agent, uiroot);

            // Poblamos el menú de la izquierda recursivamente con sangrías ("árbol todo expandido")
            populateNavigationList(rootItem, 0);

        } catch (Exception e) {
            MessageDialog.showMessageDialog(gui, "Error", "No se pudo cargar settingsui.json: " + e.getMessage());
        }
    }

    /**
     * Recorre recursivamente los nodos de menú y los añade a la lista de la izquierda con sangrías.
     */
    private void populateNavigationList(AgentSettingsItemUI node, int depth) {
        List<AgentSettingsItemUI> children = node.getChilds();
        if (children == null) {
            return;
        }

        for (AgentSettingsItemUI child : children) {
            if (child instanceof AbstractAgentSettingsItemLanterna lanternaItem) {
                // Solo añadimos como fila seleccionable los nodos de tipo "menu"
                if ("menu".equalsIgnoreCase(child.getType())) {
                    String indent = "  ".repeat(depth);
                    String prefix = (depth > 0) ? "↳ " : "";
                    String label = indent + prefix + child.getLabel();

                    navigationList.addItem(label, () -> renderSectionDetails(lanternaItem));

                    // Recursión: añadir submenús inmediatamente debajo
                    populateNavigationList(child, depth + 1);
                }
            }
        }
    }

    /**
     * Reconstruye el panel derecho mostrando únicamente los componentes directos del nodo seleccionado.
     */
    private void renderSectionDetails(AbstractAgentSettingsItemLanterna sectionNode) {
        detailPanel.removeAllComponents();

        Label titleLabel = new Label("── Parámetros: " + sectionNode.getLabel() + " ──");
        titleLabel.setTheme(LanternaUtils.getMainTheme());
        detailPanel.addComponent(titleLabel);
        detailPanel.addComponent(new EmptySpace(new TerminalSize(1, 1)));

        List<AgentSettingsItemUI> children = sectionNode.getChilds();
        if (children != null) {
            for (AgentSettingsItemUI child : children) {
                if (child instanceof AbstractAgentSettingsItemLanterna item) {
                    // Omitir submenús para no saturar el panel derecho con secciones vacías
                    if ("menu".equalsIgnoreCase(child.getType())) {
                        continue;
                    }
                    Component comp = item.getLanternaComponent();
                    if (comp != null) {
                        comp.setLayoutData(LinearLayout.createLayoutData(LinearLayout.Alignment.Fill));
                        detailPanel.addComponent(comp);
                        detailPanel.addComponent(new EmptySpace(new TerminalSize(1, 1))); // Separador
                    }
                }
            }
        }
    }
}
