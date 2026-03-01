package io.github.jjdelcerro.noema.ui.swing.settings;

import com.google.gson.JsonObject;
import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.ui.common.AgentSettingsItemUI;
import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import org.apache.commons.lang3.StringUtils;

/**
 *
 * @author jjdelcerro
 */
public class InputStringItemSwing extends AbstractAgentSettingsItemSwing {

  public static final String NAME = "inputstring";
  
  private String oldValue;

  public InputStringItemSwing(AgentSettingsItemUI parent, Agent agent, JsonObject json) {
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
    JLabel label = new JLabel("<html><b>" + getLabel() + "</b></html>");
    if (this.isRequired() && StringUtils.isBlank(this.getValue())) {
      label.setForeground(Color.RED.darker());
    }
    p.add(label, gbc);
    String current = agent.getSettings().getPropertyAsString(getVariableName());
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
