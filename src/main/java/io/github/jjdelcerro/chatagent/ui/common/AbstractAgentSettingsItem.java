package io.github.jjdelcerro.chatagent.ui.common;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.jjdelcerro.chatagent.lib.Agent;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author jjdelcerro
 */
public abstract class AbstractAgentSettingsItem implements AgentSettingsItem {

  protected final Agent agent;
  protected final JsonObject item;
  private final AgentSettingsItem parent;
  private List<AgentSettingsItem> childs;

  protected AbstractAgentSettingsItem(AgentSettingsItem parent, Agent agent, JsonObject item) {
    this.parent = parent;
    this.agent = agent;
    this.item = item;
    this.childs = null;
  }

  @Override
  public AgentSettingsItem getParent() {
    return this.parent;
  }

  @Override
  public String getType() {
    return this.item.get("type").getAsString();
  }

  @Override
  public String getLabel() {
    return this.item.get("label").getAsString();
  }

  @Override
  public String getVariableName() {
    if (!this.item.has("variableName")) {
      return null;
    }
    return this.item.get("variableName").getAsString();
  }

  @Override
  public String getActionName() {
    if (!this.item.has("actionName")) {
      return null;
    }
    return this.item.get("actionName").getAsString();
  }

  @Override
  public String getValue() {
    if (!this.item.has("value")) {
      return null;
    }
    return this.item.get("value").getAsString();
  }

  @Override
  public List<AgentSettingsItem> getChilds() {
    if (this.childs == null) {
      if (!this.item.has("childs")) {
        return null;
      }
      List<AgentSettingsItem> theChilds = new ArrayList<>();
      for (JsonElement e : this.item.getAsJsonArray("childs")) {
        AgentSettingsItem i = this.createItem(parent, agent, (JsonObject) e);
        theChilds.add(i);
      }
      this.childs = theChilds;
    }
    return this.childs;
  }

  @Override
  public String toString() {
    return getLabel();
  }

  protected void save() {
    agent.getSettings().save();
    if (getActionName() != null) {
      agent.getActions().call(getActionName(), agent.getSettings());
    }
  }

  protected abstract AgentSettingsItem createItem(AgentSettingsItem parent, Agent agent, JsonObject jsonItem);
}
