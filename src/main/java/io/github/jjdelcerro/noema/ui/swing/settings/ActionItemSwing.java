package io.github.jjdelcerro.noema.ui.swing.settings;

import com.google.gson.JsonObject;
import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.ui.common.AgentSettingsItemUI;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 *
 * @author jjdelcerro
 */
public class ActionItemSwing extends AbstractAgentSettingsItemSwing {
  
  public static final String NAME = "action";
  
  public ActionItemSwing(AgentSettingsItemUI parent, Agent agent, JsonObject json) {
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
  
  @Override
  public boolean isLeaf() {
    return true;
  }
}
