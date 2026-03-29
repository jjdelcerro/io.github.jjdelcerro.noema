package io.github.jjdelcerro.noema.ui.common;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.AgentLocator;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author jjdelcerro
 */
public abstract class AbstractAgentSettingsItemUI implements AgentSettingsItemUI {

  public static final Logger LOGGER = LoggerFactory.getLogger(AbstractAgentSettingsItemUI.class);
  
  protected final Agent agent;
  protected final JsonObject item;
  private final AgentSettingsItemUI parent;
  private List<AgentSettingsItemUI> childs;

  protected AbstractAgentSettingsItemUI(AgentSettingsItemUI parent, Agent agent, JsonObject item) {
    this.parent = parent;
    this.agent = agent;
    this.item = item;
    this.childs = null;
  }

  @Override
  public AgentSettingsItemUI getParent() {
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
  public boolean isRequired() {
    JsonElement x = this.item.get("required");
    if( x == null ) {
      return false;
    }
    return x.getAsBoolean();
  }

  @Override
  public boolean isEnabled() {
    JsonElement x = this.item.get("required");
    if( x == null ) {
      return true;
    }
    if( x instanceof JsonPrimitive) {
      if( ((JsonPrimitive) x).isBoolean() ) {
        return x.getAsBoolean();
      }
      if( ((JsonPrimitive) x).isString()) {
        return (boolean) this.agent.getSettings().eval(x.getAsString(), true);
      }
    }
    return true;
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
  public List<AgentSettingsItemUI> getChilds() {
    if (this.childs == null) {
      if (!this.item.has("childs")) {
        return null;
      }

      JsonElement childsElement = this.item.get("childs");
      List<AgentSettingsItemUI> theChilds = new ArrayList<>();

      if (childsElement.isJsonArray()) {
        // Es una lista de objetos directa
        for (JsonElement e : childsElement.getAsJsonArray()) {
          theChilds.add(this.createItem(this, agent, (JsonObject) e));
        }
      } else if (childsElement.isJsonPrimitive() && childsElement.getAsJsonPrimitive().isString()) {
        // Es una referencia a un dominio
        String domainName = childsElement.getAsString();
        JsonArray domainArray = findDomainInTree(domainName);

        if (domainArray != null) {
          for (JsonElement e : domainArray) {
            theChilds.add(this.createItem(this, agent, (JsonObject) e));
          }
        } else {
          agent.getConsole().printSystemLog("WARN: Dominio no encontrado: " + domainName);
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
    AgentSettingsItemUI current = this;
    while (current != null) {
      if (current instanceof AbstractAgentSettingsItemUI abstractItem) {
        JsonObject json = abstractItem.item;
        if (json.has("domains")) {
          JsonObject domains = json.getAsJsonObject("domains");
          if (domains.has(name)) {
            JsonElement element = domains.get(name);

            // CASO 1: Lista definida inline en el JSON
            if (element.isJsonArray()) {
              return element.getAsJsonArray();
            }

            // CASO 2: Referencia a fichero externo (String)
            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
              return loadDomainFromProperties(element.getAsString());
            }
          }
        }
      }
      current = current.getParent();
    }
    return null;
  }

  /**
   * Carga un fichero .properties, ordena las claves y genera un JsonArray
   * compatible.
   */
  private JsonArray loadDomainFromProperties(String relativePath) {
    try {
      Path path = agent.getPaths().getConfigPath(relativePath).toAbsolutePath();
      if (!Files.exists(path)) {
        agent.getConsole().printSystemError("Fichero de dominio no encontrado: " + path.toString());
        return new JsonArray(); // Devolvemos lista vacía para no romper la UI
      }

//      LOGGER.info("file="+file.getAbsolutePath());
//      LOGGER.info("Contents:\n"+FileUtils.readFileToString(file, StandardCharsets.UTF_8));

      Properties props = new Properties();
      try (Reader reader = new InputStreamReader(
              new java.io.FileInputStream(path.toFile()), StandardCharsets.UTF_8)) {
        props.load(reader);
      } catch (java.io.IOException e) {
        agent.getConsole().printSystemError("Error leyendo dominio externo (" + relativePath + "): " + e.getMessage());
        return new JsonArray();
      }

      // Ordenar alfabéticamente por la clave (Label)
      List<String> sortedKeys = new ArrayList<>(props.stringPropertyNames());
      Collections.sort(sortedKeys);

//      LOGGER.info("Keys: "+StringUtils.join(sortedKeys,","));

      JsonArray result = new JsonArray();
      for (String label : sortedKeys) {
        String value = props.getProperty(label);
        label = label.replace('_',' ');
        JsonObject option = new JsonObject();
        option.addProperty("type", "value");
        option.addProperty("label", label); // La clave del properties es lo que ve el usuario
        option.addProperty("value", value); // El valor es lo que se guarda en settings

        result.add(option);
      }
      return result;
    } catch (Exception ex) {
      LOGGER.warn("Can't load domain from '"+relativePath+"'.",ex);
      return null;
    }
  }

  @Override
  public String toString() {
    if( this.isRequired() && StringUtils.isBlank(this.getValue()) ) {
      return "<html><b>"+this.getLabel()+"</b></html>";
    }
    return getLabel();
  }

  protected void save() {
    agent.getSettings().save();
    if (getActionName() != null) {
      agent.getActions().call(getActionName(), agent.getSettings());
    }
  }

  protected abstract AgentSettingsItemUI createItem(AgentSettingsItemUI parent, Agent agent, JsonObject jsonItem);
}
