package io.github.jjdelcerro.noema.ui.swing.settings;

import com.google.gson.JsonObject;
import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.ui.common.AgentSettingsItemUI;
import javax.swing.JComponent;

/**
 *
 * @author jjdelcerro
 */
public class ValueItemSwing extends AbstractAgentSettingsItemSwing {

  public static final String NAME = "value";
  
  public ValueItemSwing(AgentSettingsItemUI parent, Agent agent, JsonObject json) {
    super(parent, agent, json);
  }

  @Override
  public JComponent getComponent() {
    return null;
  }
  
}
