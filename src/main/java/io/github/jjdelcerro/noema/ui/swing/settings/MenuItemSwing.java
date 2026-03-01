package io.github.jjdelcerro.noema.ui.swing.settings;

import com.google.gson.JsonObject;
import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.ui.common.AgentSettingsItemUI;
import javax.swing.JComponent;

/**
 *
 * @author jjdelcerro
 */
public class MenuItemSwing extends AbstractAgentSettingsItemSwing {
  
  public static final String NAME = "menu";

  public MenuItemSwing(AgentSettingsItemUI parent, Agent agent, JsonObject json) {
    super(parent, agent, json);
  }

  @Override
  public JComponent getComponent() {
    return null;
  }
  
}
