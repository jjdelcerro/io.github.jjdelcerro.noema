package io.github.jjdelcerro.chatagent.ui.swing;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jjdelcerro.chatagent.lib.Agent;
import io.github.jjdelcerro.chatagent.lib.AgentConsole;
import io.github.jjdelcerro.chatagent.ui.AgentUIManager;
import io.github.jjdelcerro.chatagent.ui.AgentUISettings;
import io.github.jjdelcerro.chatagent.ui.common.AbstractAgentSettingsItem;
import io.github.jjdelcerro.chatagent.ui.common.AgentSettingsItem;
import io.github.jjdelcerro.chatagent.ui.common.FakeAgent;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.io.File;
import java.io.FileReader;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.ListSelectionModel;
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

  public AgentSwingSettingsImpl(AgentUIManager agentUIManager, File dataFolder, AgentConsole console) {
    this(agentUIManager, new FakeAgent(dataFolder, console));
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
        detailPanel.add(comp, BorderLayout.NORTH);
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
    File settingsUIFile = new File(agent.getDataFolder(), "settingsui.json");
    try (FileReader reader = new FileReader(settingsUIFile)) {
      JsonObject uiroot = JsonParser.parseReader(reader).getAsJsonObject();
      this.rootItem = new MenuItem(null, agent, uiroot);

      DefaultMutableTreeNode rootNode = buildTreeNodes(rootItem);
      tree.setModel(new DefaultTreeModel(rootNode));
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private DefaultMutableTreeNode buildTreeNodes(AgentSettingsItem item) {
    DefaultMutableTreeNode node = new DefaultMutableTreeNode(item);
    if ("menu".equalsIgnoreCase(item.getType())) {
      List<AgentSettingsItem> childs = item.getChilds();
      if (childs != null) {
        for (AgentSettingsItem child : childs) {
          node.add(buildTreeNodes(child));
        }
      }
    }
    return node;
  }

  @Override
  public void showWindow() {
    // Envolvemos el panel en un JDialog modal
//    Window parent = SwingUtilities.getWindowAncestor(this);
    Window parent = ((AgentSwingConsoleController) (this.agent.getConsole())).getRoot();
    JDialog dialog = new JDialog((Frame) (parent instanceof Frame ? parent : null), "Ajustes", true);
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

  // =========================================================================
  // INTERFAZ Y CLASES ESTÁTICAS DE ITEMS
  // =========================================================================
  public interface AgentSettingsItemSwing extends AgentSettingsItem {

    JComponent getComponent();

    boolean isLeaf();
  }

  private static abstract class AbstractAgentSettingsItemSwing
          extends AbstractAgentSettingsItem
          implements AgentSettingsItemSwing {

    public AbstractAgentSettingsItemSwing(AgentSettingsItem parent, Agent agent, JsonObject json) {
      super(parent, agent, json);
    }

    @Override
    public boolean isLeaf() {
      return getChilds() == null || getChilds().isEmpty();
    }

    @Override
    protected AgentSettingsItem createItem(AgentSettingsItem parent, Agent agent, JsonObject json) {
      String type = json.get("type").getAsString().toLowerCase();
      return switch (type) {
        case "menu" ->
          new MenuItem(parent, agent, json);
        case "inputstring" ->
          new InputStringItem(parent, agent, json);
        case "selectoption" ->
          new SelectOptionItem(parent, agent, json);
        case "combo" ->
          new ComboOptionItem(parent, agent, json);
        case "action" ->
          new ActionItem(parent, agent, json);
        default ->
          new ValueItem(parent, agent, json);
      };
    }
  }

  public static class ActionItem extends AbstractAgentSettingsItemSwing {

    public ActionItem(AgentSettingsItem parent, Agent agent, JsonObject json) {
      super(parent, agent, json);
    }

    @Override
    public JComponent getComponent() {
      JPanel p = new JPanel(new GridBagLayout());
      p.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
      GridBagConstraints gbc = new GridBagConstraints();
      gbc.gridx = 0;
      gbc.gridy = 0;
      gbc.weightx = 1.0;
      gbc.fill = GridBagConstraints.HORIZONTAL;
      gbc.anchor = GridBagConstraints.NORTHWEST;

      // Etiqueta descriptiva
      p.add(new JLabel("<html><b>" + getLabel() + "</b></html>"), gbc);

      // Botón de acción
      // Usamos el texto "Ejecutar" por defecto, o podemos ser creativos.
      // Para mantenerlo simple y funcional:
      JButton btn = new JButton("Ejecutar Acción");

      btn.addActionListener(e -> {
        String actionName = getActionName();
        if (actionName != null && !actionName.isEmpty()) {
          // Ejecutamos la acción en el hilo actual (EDT) como solicitaste.
          // La acción recibe los settings actuales.
          boolean result = agent.getActions().call(actionName, agent.getSettings());

          if (!result) {
            // Feedback mínimo en caso de fallo lógico (opcional)
            agent.getConsole().printSystemError("La acción '" + actionName + "' devolvió false.");
          }
        } else {
          agent.getConsole().printSystemError("Error: El botón '" + getLabel() + "' no tiene 'actionName' definido.");
        }
      });

      gbc.gridy = 1;
      gbc.insets = new Insets(10, 0, 0, 0);
      gbc.fill = GridBagConstraints.NONE; // El botón no se estira todo el ancho
      p.add(btn, gbc);

      // Panel espaciador para empujar todo hacia arriba (coherencia visual con otros items)
      gbc.gridy = 2;
      gbc.weighty = 1.0;
      gbc.fill = GridBagConstraints.BOTH;
      p.add(new JPanel(), gbc);

      return p;
    }
  }

  public static class MenuItem extends AbstractAgentSettingsItemSwing {

    public MenuItem(AgentSettingsItem parent, Agent agent, JsonObject json) {
      super(parent, agent, json);
    }

    @Override
    public JComponent getComponent() {
      return null;
    }
  }

  public static class InputStringItem extends AbstractAgentSettingsItemSwing {

    private String oldValue;

    public InputStringItem(AgentSettingsItem parent, Agent agent, JsonObject json) {
      super(parent, agent, json);
    }

    @Override
    public JComponent getComponent() {
      JPanel p = new JPanel(new GridBagLayout());
      p.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
      GridBagConstraints gbc = new GridBagConstraints();
      gbc.gridx = 0;
      gbc.gridy = 0;
      gbc.weightx = 1.0; // Ocupa todo el ancho
      gbc.fill = GridBagConstraints.HORIZONTAL;
      gbc.anchor = GridBagConstraints.NORTHWEST;

      p.add(new JLabel("<html><b>" + getLabel() + "</b></html>"), gbc);

      String current = agent.getSettings().getProperty(getVariableName());
      JTextField field = new JTextField(current != null ? current : "", 30);

      // Acción de guardado unificada
      Runnable saveLogic = () -> {
        String newValue = field.getText().trim();
        if (!newValue.equals(oldValue)) {
          agent.getSettings().setProperty(getVariableName(), newValue);
          save();
          oldValue = newValue;
          // Opcional: un log o cambio visual sutil en el borde para indicar guardado
        }
      };

      field.addFocusListener(new java.awt.event.FocusAdapter() {
        @Override
        public void focusGained(java.awt.event.FocusEvent e) {
          oldValue = field.getText();
          field.selectAll(); // Facilita la edición rápida
        }

        @Override
        public void focusLost(java.awt.event.FocusEvent e) {
          saveLogic.run();
        }
      });

      // También guardamos si el usuario pulsa Enter
      field.addActionListener(e -> saveLogic.run());

      gbc.gridy = 1;
      gbc.fill = GridBagConstraints.HORIZONTAL;
      gbc.insets = new Insets(10, 0, 0, 0);
      p.add(field, gbc);

      // EL TRUCO: Un panel vacío con weighty = 1.0 que empuja todo hacia arriba
      gbc.gridy = 2;
      gbc.weighty = 1.0;
      gbc.fill = GridBagConstraints.BOTH;
      p.add(new JPanel(), gbc);

      return p;
    }
  }

  public static class SelectOptionItem extends AbstractAgentSettingsItemSwing {

    public SelectOptionItem(AgentSettingsItem parent, Agent agent, JsonObject json) {
      super(parent, agent, json);
    }

    @Override
    public JComponent getComponent() {
      JPanel p = new JPanel(new BorderLayout(10, 10));
      p.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
      p.add(new JLabel("<html><b>" + getLabel() + "</b></html>"), BorderLayout.NORTH);
      DefaultListModel<AgentSettingsItem> model = new DefaultListModel<>();
      for (AgentSettingsItem child : getChilds()) {
        model.addElement(child);
      }

      JList<AgentSettingsItem> list = new JList<>(model);
      list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

      // Pre-seleccionar actual
      String current = agent.getSettings().getProperty(getVariableName());
      for (int i = 0; i < model.size(); i++) {
        if (model.get(i).getValue().equals(current)) {
          list.setSelectedIndex(i);
          break;
        }
      }

      list.addListSelectionListener(e -> {
        if (!e.getValueIsAdjusting()) {
          AgentSettingsItem selected = list.getSelectedValue();
          if (selected != null) {
            agent.getSettings().setProperty(getVariableName(), selected.getValue());
            save();
          }
        }
      });

      p.add(new JScrollPane(list), BorderLayout.CENTER);
      p.setPreferredSize(new Dimension(400, 250));
      return p;
    }
  }

  public static class ComboOptionItem extends AbstractAgentSettingsItemSwing {

    public ComboOptionItem(AgentSettingsItem parent, Agent agent, JsonObject json) {
      super(parent, agent, json);
    }

    @Override
    public JComponent getComponent() {
      JPanel p = new JPanel(new GridBagLayout());
      p.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
      GridBagConstraints gbc = new GridBagConstraints();
      gbc.gridx = 0;
      gbc.gridy = 0;
      gbc.weightx = 1.0;
      gbc.fill = GridBagConstraints.HORIZONTAL;

      p.add(new JLabel("<html><b>" + getLabel() + "</b></html>"), gbc);

      // Cargamos las opciones (childs)
      DefaultComboBoxModel<AgentSettingsItem> model = new DefaultComboBoxModel<>();
      for (AgentSettingsItem child : getChilds()) {
        model.addElement(child);
      }

      JComboBox<AgentSettingsItem> combo = new JComboBox<>(model);
      combo.setEditable(true);

      // Seleccionar el valor actual si existe
      String current = agent.getSettings().getProperty(getVariableName());
      if (current != null) {
        combo.setSelectedItem(current);
      }

      combo.addActionListener(e -> {
        Object selected = combo.getSelectedItem();
        String newValue = null;

        if (selected instanceof AgentSettingsItem item) {
          newValue = item.getValue();
        } else if (selected instanceof String s) {
          newValue = s;
        }

        if (newValue != null && !newValue.isEmpty()) {
          agent.getSettings().setProperty(getVariableName(), newValue);
          save();
        }
      });

      gbc.gridy = 1;
      gbc.insets = new Insets(10, 0, 0, 0);
      p.add(combo, gbc);

      gbc.gridy = 2;
      gbc.weighty = 1.0;
      p.add(new JPanel(), gbc);
      return p;
    }
  }

  public static class ValueItem extends AbstractAgentSettingsItemSwing {

    public ValueItem(AgentSettingsItem parent, Agent agent, JsonObject json) {
      super(parent, agent, json);
    }

    @Override
    public JComponent getComponent() {
      return null;
    }
  }

}
