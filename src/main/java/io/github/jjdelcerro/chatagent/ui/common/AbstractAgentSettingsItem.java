package io.github.jjdelcerro.chatagent.ui.common;

import com.google.gson.JsonArray;
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

      JsonElement childsElement = this.item.get("childs");
      List<AgentSettingsItem> theChilds = new ArrayList<>();

      if (childsElement.isJsonArray()) {
        // Es una lista de objetos directa
        for (JsonElement e : childsElement.getAsJsonArray()) {
          theChilds.add(this.createItem(this, agent, (JsonObject) e));
        }
      } else if (childsElement.isJsonPrimitive() && childsElement.getAsJsonPrimitive().isString()) {
        // Es una referencia a un dominio
        /*
          TODO: Posible mejora, dominios en archivos externos

          Si en el futuro la lista de modelos crece mucho, podrías incluso hacer 
          que si childs empieza por "file:", cargue un JSON externo. 
          Pero con la solución del nodo "domains" en el raíz, ya resuelves el 90% 
          del problema de mantenimiento.
        */
        String domainName = childsElement.getAsString();
        JsonArray domainArray = findDomainInTree(domainName);

        if (domainArray != null) {
          for (JsonElement e : domainArray) {
            theChilds.add(this.createItem(this, agent, (JsonObject) e));
          }
        } else {
          agent.getConsole().println("WARN: Dominio no encontrado: " + domainName);
        }
      }
      this.childs = theChilds;
    }
    return this.childs;
  }

  /**
   * Busca recursivamente hacia arriba en el árbol de items hasta encontrar la
   * definición del dominio solicitado.
   */
  private JsonArray findDomainInTree(String name) {
    AgentSettingsItem current = this;
    while (current != null) {
      // Necesitamos acceder al JsonObject original. 
      // Como estamos en AbstractAgentSettingsItem, podemos acceder a 'this.item'
      if (current instanceof AbstractAgentSettingsItem abstractItem) {
        JsonObject json = abstractItem.item;
        if (json.has("domains")) {
          JsonObject domains = json.getAsJsonObject("domains");
          if (domains.has(name)) {
            return domains.getAsJsonArray(name);
          }
        }
      }
      current = current.getParent();
    }
    return null;
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
