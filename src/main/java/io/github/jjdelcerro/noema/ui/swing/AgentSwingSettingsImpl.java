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
import java.awt.Frame;
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
    detailPanel.setLayout(new BorderLayout());
    
    if (item != null) {
      if (item.isLeaf()) {
        // Comportamiento actual para hojas
        JComponent comp = item.getComponent();
        if (comp != null) {
          detailPanel.add(comp, BorderLayout.CENTER);
        }
      } else {
        // Nuevo comportamiento para menús: mostrar todas las hojas hijas directas
        List<AgentSettingsItemUI> children = item.getChilds();
        if (children != null && !children.isEmpty()) {
          // Filtrar solo hojas hijas directas
          List<AgentSettingsItemSwing> leafChildren = new ArrayList<>();
          for (AgentSettingsItemUI child : children) {
            if (child instanceof AgentSettingsItemSwing) {
              AgentSettingsItemSwing swingChild = (AgentSettingsItemSwing) child;
              if (swingChild.isLeaf()) {
                leafChildren.add(swingChild);
              }
            }
          }
          
          if (!leafChildren.isEmpty()) {
            // Crear panel intermedio con GridBagLayout
            JPanel intermediatePanel = new JPanel(new GridBagLayout());
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.gridx = 0;
            gbc.gridy = 0;
            gbc.weightx = 1.0;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.anchor = GridBagConstraints.NORTHWEST;
            gbc.insets = new Insets(5, 10, 5, 10);
            
            // Agregar cada componente de hoja
            for (AgentSettingsItemSwing leaf : leafChildren) {
              JComponent comp = leaf.getComponent();
              if (comp != null) {
                intermediatePanel.add(comp, gbc);
                gbc.gridy++;
              }
            }
            
            // Panel vacío para empujar todo hacia arriba
            gbc.weighty = 1.0;
            gbc.fill = GridBagConstraints.BOTH;
            intermediatePanel.add(new JPanel(), gbc);
            
            // Envolver en JScrollPane (barra vertical solo cuando sea necesario)
            JScrollPane scrollPane = new JScrollPane(intermediatePanel);
            scrollPane.setBorder(null);
            scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
            scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            detailPanel.add(scrollPane, BorderLayout.CENTER);
          } else {
            // No hay hojas hijas - mostrar mensaje original
            showSelectSubOptionMessage(item);
          }
        } else {
          // No hay hijos - mostrar mensaje original
          showSelectSubOptionMessage(item);
        }
      }
    }
    
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
