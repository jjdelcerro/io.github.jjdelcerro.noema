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
import java.awt.FlowLayout;
import java.awt.Frame;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.io.FileReader;
import java.nio.file.Path;
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
    if (item != null) {
      JComponent comp = item.getComponent();
      if (comp != null) {
        // Usamos CENTER para que el componente interno decida cómo crecer
        detailPanel.add(comp, BorderLayout.CENTER);
      } else {
        JLabel lbl = new JLabel("Seleccione una sub-opción para: " + item.getLabel());
        lbl.setHorizontalAlignment(SwingConstants.CENTER);
        detailPanel.add(lbl, BorderLayout.NORTH);
      }
    }
    detailPanel.revalidate();
    detailPanel.repaint();
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
    showWindow(null);
  }

  public void showWindow(Frame parent) {
    JDialog dialog = new JDialog(parent, "Ajustes", true);
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
