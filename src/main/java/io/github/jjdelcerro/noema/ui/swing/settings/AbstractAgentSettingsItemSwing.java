package io.github.jjdelcerro.noema.ui.swing.settings;

import com.google.gson.JsonObject;
import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.ui.common.AbstractAgentSettingsItemUI;
import io.github.jjdelcerro.noema.ui.common.AgentSettingsItemUI;


/**
 *
 * @author jjdelcerro
 */
abstract class AbstractAgentSettingsItemSwing extends AbstractAgentSettingsItemUI implements AgentSettingsItemSwing {
  
  public AbstractAgentSettingsItemSwing(AgentSettingsItemUI parent, Agent agent, JsonObject json) {
    super(parent, agent, json);
  }

  @Override
  public boolean isLeaf() {
    return getChilds() == null || getChilds().isEmpty();
  }

  @Override
  protected AgentSettingsItemUI createItem(AgentSettingsItemUI parent, Agent agent, JsonObject json) {
    String type = json.get("type").getAsString().toLowerCase();
    switch (type) {
      case MenuItemSwing.NAME:
        return new MenuItemSwing(parent, agent, json);
      case InputStringItemSwing.NAME:
        return  new InputStringItemSwing(parent, agent, json);
      case SelectOptionItemSwing.NAME:
        return  new SelectOptionItemSwing(parent, agent, json);
      case ComboOptionItemSwing.NAME:
        return  new ComboOptionItemSwing(parent, agent, json);
      case ActionItemSwing.NAME:
        return  new ActionItemSwing(parent, agent, json);
      case PathsItemSwing.NAME:
        return  new PathsItemSwing(parent, agent, json);
      case CheckedListItemSwing.NAME:
        return  new CheckedListItemSwing(parent, agent, json);
      case ValueItemSwing.NAME:
      default:
        return  new ValueItemSwing(parent, agent, json);
    }
  }
  
}
