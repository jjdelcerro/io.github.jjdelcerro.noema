package io.github.jjdelcerro.noema.ui.swing.settings;

import com.google.gson.JsonObject;
import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.ui.common.AgentSettingsItemUI;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import org.apache.commons.lang3.StringUtils;

/**
 *
 * @author jjdelcerro
 */
public class SelectOptionItemSwing extends AbstractAgentSettingsItemSwing {
  
  public static final String NAME = "selectoption";
  
  public SelectOptionItemSwing(AgentSettingsItemUI parent, Agent agent, JsonObject json) {
    super(parent, agent, json);
  }

  @Override
  public JComponent getComponent() {
    JPanel p = new JPanel(new BorderLayout(10, 10));
    p.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
    JLabel label = new JLabel("<html><b>" + getLabel() + "</b></html>");
    if (this.isRequired() && StringUtils.isBlank(this.getValue())) {
      label.setForeground(Color.RED.darker());
    }
    p.add(label, BorderLayout.NORTH);
    DefaultListModel<AgentSettingsItemUI> model = new DefaultListModel<>();
    for (AgentSettingsItemUI child : getChilds()) {
      model.addElement(child);
    }
    JList<AgentSettingsItemUI> list = new JList<>(model);
    list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    // Pre-seleccionar actual
    String current = agent.getSettings().getPropertyAsString(getVariableName());
    for (int i = 0; i < model.size(); i++) {
      if (model.get(i).getValue().equals(current)) {
        list.setSelectedIndex(i);
        break;
      }
    }
    list.addListSelectionListener(e -> {
      if (!e.getValueIsAdjusting()) {
        AgentSettingsItemUI selected = list.getSelectedValue();
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
  
  @Override
  public boolean isLeaf() {
    return true;
  }
}
