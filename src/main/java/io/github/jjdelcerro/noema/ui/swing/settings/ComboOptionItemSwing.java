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

    // Obtenemos el valor actual de la configuración
    String current = agent.getSettings().getPropertyAsString(getVariableName());

    // Si es obligatorio y está vacío, marcamos la etiqueta en rojo
    if (this.isRequired() && org.apache.commons.lang3.StringUtils.isBlank(current)) {
      label.setForeground(Color.RED.darker());
    }
    p.add(label, gbc);

    // Cargamos las opciones del dominio (childs) en el modelo
    DefaultComboBoxModel<AgentSettingsItemUI> model = new DefaultComboBoxModel<>();
    for (AgentSettingsItemUI child : getChilds()) {
      model.addElement(child);
    }

    JComboBox<AgentSettingsItemUI> combo = new JComboBox<>(model);
    combo.setEditable(true);

    // --- LÓGICA DE SELECCIÓN INICIAL ---
    if (org.apache.commons.lang3.StringUtils.isNotBlank(current)) {
      // Si hay un valor guardado, buscamos el objeto correspondiente en el modelo
      boolean found = false;
      for (int i = 0; i < model.getSize(); i++) {
        if (model.getElementAt(i).getValue().equals(current)) {
          combo.setSelectedIndex(i);
          found = true;
          break;
        }
      }
      // Si el valor no está en la lista de sugerencias (valor manual), lo escribimos tal cual
      if (!found) {
        combo.setSelectedItem(current);
      }
    } else {
      // CLAVE: Si no hay configuración, forzamos el combo a estar vacío
      combo.setSelectedIndex(-1);
      if (combo.getEditor().getEditorComponent() instanceof javax.swing.text.JTextComponent txt) {
        txt.setText("");
      }
    }

    // Listener para guardar cambios cuando el usuario selecciona o escribe
    combo.addActionListener(e -> {
      Object selected = combo.getSelectedItem();
      String newValue = null;

      if (selected instanceof AgentSettingsItemUI itemUI) {
        newValue = itemUI.getValue();
      } else if (selected instanceof String s) {
        newValue = s;
      }

      if (newValue != null) {
        agent.getSettings().setProperty(getVariableName(), newValue);
        save(); // Método heredado que guarda en disco y dispara acciones
      }
    });

    gbc.gridy = 1;
    gbc.insets = new Insets(10, 0, 0, 0);
    p.add(combo, gbc);

    return p;
  }
  
  @Override
  public boolean isLeaf() {
    return true;
  }
}
