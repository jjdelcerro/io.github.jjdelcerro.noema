package io.github.jjdelcerro.noema.ui.swing.settings;

import com.google.gson.JsonObject;
import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.ui.common.AgentSettingsItemUI;
import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.apache.commons.lang3.StringUtils;

/**
 *
 * @author jjdelcerro
 */
public class ComboOptionItemSwing extends AbstractAgentSettingsItemSwing {

  public static final String NAME = "combo";
  
  public ComboOptionItemSwing(AgentSettingsItemUI parent, Agent agent, JsonObject json) {
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
    JLabel label = new JLabel("<html><b>" + getLabel() + "</b></html>");
    if (this.isRequired() && StringUtils.isBlank(this.getValue())) {
      label.setForeground(Color.RED.darker());
    }
    p.add(label, gbc);
    // Cargamos las opciones (childs)
    DefaultComboBoxModel<AgentSettingsItemUI> model = new DefaultComboBoxModel<>();
    for (AgentSettingsItemUI child : getChilds()) {
      model.addElement(child);
    }
    JComboBox<AgentSettingsItemUI> combo = new JComboBox<>(model);
    combo.setEditable(true);
    // Seleccionar el valor actual si existe
    String current = agent.getSettings().getPropertyAsString(getVariableName());
    if (current != null) {
      combo.setSelectedItem(current);
    }
    combo.addActionListener(e -> {
      Object selected = combo.getSelectedItem();
      String newValue = null;
      if (selected instanceof AgentSettingsItemUI item) {
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
