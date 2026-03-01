package io.github.jjdelcerro.noema.ui.swing.settings;

import com.google.gson.JsonObject;
import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.settings.AgentSettingsCheckedList;
import io.github.jjdelcerro.noema.ui.common.AgentSettingsItemUI;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Optional;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;

public class CheckedListItemSwing extends AbstractAgentSettingsItemSwing {

  public static final String NAME = "checkedlist";

  private record ToolUIItem(String label, String technicalName, boolean checked) {}

  public CheckedListItemSwing(AgentSettingsItemUI parent, Agent agent, JsonObject json) {
    super(parent, agent, json);
  }

  @Override
  public JComponent getComponent() {
    JPanel p = new JPanel(new GridBagLayout());
    p.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
    GridBagConstraints gbc = new GridBagConstraints();

    // 1. Etiqueta de Título
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.weightx = 1.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.anchor = GridBagConstraints.NORTHWEST;
    p.add(new JLabel("<html><b>" + getLabel() + "</b></html>"), gbc);

    // 2. Modelo y Lista
    DefaultListModel<ToolUIItem> listModel = new DefaultListModel<>();
    refreshModel(listModel);

    JList<ToolUIItem> list = new JList<>(listModel);
    list.setCellRenderer(new CheckBoxListRenderer());
    list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    list.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        int index = list.locationToIndex(e.getPoint());
        if (index != -1) {
          ToolUIItem item = listModel.getElementAt(index);
          boolean newState = !item.checked();
          agent.getSettings().setChecked(getVariableName(), item.technicalName(), newState);
          save(); 
          listModel.set(index, new ToolUIItem(item.label(), item.technicalName(), newState));
          list.repaint();
        }
      }
    });

    JScrollPane scrollPane = new JScrollPane(list);
    scrollPane.setPreferredSize(new Dimension(400, 300));

    gbc.gridy = 1;
    gbc.weighty = 1.0; 
    gbc.fill = GridBagConstraints.BOTH;
    gbc.insets = new Insets(10, 0, 10, 0);
    p.add(scrollPane, gbc);

    // 3. Panel de Botones (Marcar/Desmarcar todas)
    JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
    
    JButton btnSelectAll = new JButton("Marcar todas");
    btnSelectAll.addActionListener(e -> setAllStates(listModel, true));
    
    JButton btnDeselectAll = new JButton("Desmarcar todas");
    btnDeselectAll.addActionListener(e -> setAllStates(listModel, false));
    
    buttonPanel.add(btnSelectAll);
    buttonPanel.add(btnDeselectAll);

    gbc.gridy = 2;
    gbc.weighty = 0.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.insets = new Insets(0, 0, 0, 0);
    p.add(buttonPanel, gbc);

    return p;
  }

  /**
   * Cambia el estado de todos los elementos tanto en la lógica como en la UI
   */
  private void setAllStates(DefaultListModel<ToolUIItem> model, boolean state) {
    for (int i = 0; i < model.getSize(); i++) {
      ToolUIItem item = model.getElementAt(i);
      // Solo actualizamos si el estado es diferente al actual
      if (item.checked() != state) {
        agent.getSettings().setChecked(getVariableName(), item.technicalName(), state);
        model.set(i, new ToolUIItem(item.label(), item.technicalName(), state));
      }
    }
    save(); // Guardamos una sola vez al final del bucle
  }

  private void refreshModel(DefaultListModel<ToolUIItem> model) {
    model.clear();
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
        model.addElement(new ToolUIItem(displayName, technicalName, isChecked));
      }
    }
  }

  private static class CheckBoxListRenderer extends JCheckBox implements ListCellRenderer<ToolUIItem> {
    @Override
    public Component getListCellRendererComponent(JList<? extends ToolUIItem> list, ToolUIItem value, int index, boolean isSelected, boolean cellHasFocus) {
      setSelected(value.checked());
      setText(value.label());
      setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());
      setForeground(isSelected ? list.getSelectionForeground() : list.getForeground());
      setFocusable(false);
      return this;
    }
  }
}
