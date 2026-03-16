package io.github.jjdelcerro.noema.ui.swing;

import io.github.jjdelcerro.noema.ui.swing.settings.MenuItemSwing;
import io.github.jjdelcerro.noema.ui.swing.settings.AgentSettingsItemSwing;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.settings.AgentSettings;
import io.github.jjdelcerro.noema.ui.AgentUIManager;
import io.github.jjdelcerro.noema.ui.AgentUISettings;
import io.github.jjdelcerro.noema.ui.common.AgentSettingsItemUI;
import io.github.jjdelcerro.noema.ui.common.FakeAgent;
import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.io.FileReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTree;
import javax.swing.SwingConstants;

public class AgentSwingSettingsImpl extends JPanel implements AgentUISettings {

  private final Agent agent;
  private final JTree tree;
  private final JPanel detailPanel;
  private AgentSettingsItemSwing rootItem;

  public AgentSwingSettingsImpl(AgentUIManager agentUIManager, Agent agent) {
    this.agent = agent;
    this.detailPanel = new JPanel(new BorderLayout());
    this.tree = new JTree();

    setLayout(new BorderLayout());
    initUI();
    loadConfiguration();
  }

  public AgentSwingSettingsImpl(AgentUIManager agentUIManager, AgentSettings settings) {
    this(agentUIManager, new FakeAgent(settings));
  }

  private void initUI() {
    tree.addTreeSelectionListener(e -> {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
      if (node == null) {
        return;
      }
      AgentSettingsItemSwing item = (AgentSettingsItemSwing) node.getUserObject();
      updateDetailPanel(item);
    });

    JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(tree), detailPanel);
    splitPane.setDividerLocation(250);
    add(splitPane, BorderLayout.CENTER);
  }

  private void updateDetailPanel(AgentSettingsItemSwing item) {
    detailPanel.removeAll();

    JPanel intermediatePanel = new JPanel(new GridBagLayout());
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.weightx = 1.0;
    gbc.weighty = 0.0; // Los elementos no se estiran verticalmente
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.anchor = GridBagConstraints.NORTHWEST; // ALINEACIÓN SIEMPRE ARRIBA
    gbc.insets = new Insets(1, 5, 1, 5);

    // 2. Determinar qué añadir al panel
    List<AgentSettingsItemSwing> componentsToAdd = new ArrayList<>();

    if (item.isLeaf()) {
      // Caso: Hoja (Input, Combo, Path, etc.)
      JComponent comp = item.getComponent();
      if (comp != null) {
        componentsToAdd.add(item);
      }
    } else {
      // Caso: Rama (Menú). Filtramos hojas hijas directas
      List<AgentSettingsItemUI> children = item.getChilds();
      if (children != null) {
        for (AgentSettingsItemUI child : children) {
          if (child instanceof AgentSettingsItemSwing swingChild && swingChild.isLeaf()) {
            if (swingChild.getComponent() != null) {
              componentsToAdd.add(swingChild);
            }
          }
        }
      }
    }

    // 3. Añadir todos los componentes al layout
    for (AgentSettingsItemSwing compItem : componentsToAdd) {
      intermediatePanel.add(compItem.getComponent(), gbc);
      gbc.gridy++;
    }

    // 4. Panel vacío al final para "empujar" todo hacia arriba (el peso 1.0 aquí es clave)
    gbc.gridy++;
    gbc.weighty = 1.0;
    gbc.fill = GridBagConstraints.BOTH;
    intermediatePanel.add(new JPanel(), gbc);

    // 5. Envolver en scroll por si acaso
    JScrollPane scrollPane = new JScrollPane(intermediatePanel);
    scrollPane.setBorder(null);
    scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
    scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    scrollPane.getVerticalScrollBar().setUnitIncrement(16);

    detailPanel.add(scrollPane, BorderLayout.CENTER);
    detailPanel.revalidate();
    detailPanel.repaint();
  }

  private void showSelectSubOptionMessage(AgentSettingsItemSwing item) {
    JLabel lbl = new JLabel("Seleccione una sub-opción para: " + item.getLabel());
    lbl.setHorizontalAlignment(SwingConstants.CENTER);
    detailPanel.add(lbl, BorderLayout.NORTH);
  }

  private void loadConfiguration() {
    Path settingsUIPath = agent.getPaths().getConfigFolder().resolve("settingsui.json");
    try (FileReader reader = new FileReader(settingsUIPath.toFile())) {
      JsonObject uiroot = JsonParser.parseReader(reader).getAsJsonObject();
      this.rootItem = new MenuItemSwing(null, agent, uiroot);

      DefaultMutableTreeNode rootNode = buildTreeNodes(rootItem);
      tree.setModel(new DefaultTreeModel(rootNode));
    } catch (Exception e) {
      // FIXME: enviar al log
      e.printStackTrace();
    }
  }

  private DefaultMutableTreeNode buildTreeNodes(AgentSettingsItemUI item) {
    DefaultMutableTreeNode node = new DefaultMutableTreeNode(item);
    if ("menu".equalsIgnoreCase(item.getType())) {
      List<AgentSettingsItemUI> childs = item.getChilds();
      if (childs != null) {
        for (AgentSettingsItemUI child : childs) {
          node.add(buildTreeNodes(child));
        }
      }
    }
    return node;
  }

  @Override
  public void showWindow() {
    Window top = SwingUtils.getTopWindow();
    showWindow(top);
  }

  public void showWindow(Window parent) {
    JDialog dialog = new JDialog(parent, "Ajustes", Dialog.ModalityType.APPLICATION_MODAL);
    dialog.getContentPane().add(this);
    dialog.setSize(1024, 600);
    dialog.setLocationRelativeTo(parent);

    // Añadir botón de cerrar en la parte inferior para el diálogo
    JButton closeBtn = new JButton("Cerrar");
    closeBtn.addActionListener(e -> dialog.dispose());
    JPanel p = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    p.add(closeBtn);
    this.add(p, BorderLayout.SOUTH);

    dialog.setVisible(true);
  }
}
