package io.github.jjdelcerro.chatagent.ui.console;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jjdelcerro.chatagent.lib.Agent;
import io.github.jjdelcerro.chatagent.lib.AgentConsole;
import io.github.jjdelcerro.chatagent.ui.AgentUIManager;
import io.github.jjdelcerro.chatagent.ui.AgentUISettings;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import org.jline.reader.LineReader;

/**
 *
 * @author jjdelcerro
 */
public class AgentConsoleSettingsImpl implements AgentUISettings {

  private final MenuItem root;
  
  private interface Item {
    String getType();
    String getLabel();
    String getVariableName();
    String getActionName();
    String getValue();
    List<Item> getChilds();
    Item getParent();
    
    Item show();  // Muestra el UI
    Item input(); // Espera la entrada del usuario y devuelve el siguiente item
  }
  
  private static abstract class AbstractItem implements Item {

    protected final Agent agent;
    protected final JsonObject item;
    private final Item parent;
    private List<Item> childs;

    protected AbstractItem(Item parent, Agent agent, JsonObject item) {
      this.parent = parent;
      this.agent = agent;
      this.item = item;
      this.childs = null;
    }

    @Override
    public Item getParent() {
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
      if(!this.item.has("variableName") ) {
        return null;
      }
      return this.item.get("variableName").getAsString();
    }

    @Override
    public String getActionName() {
      if(!this.item.has("actionName") ) {
        return null;
      }
      return this.item.get("actionName").getAsString();
    }
    
    @Override
    public String getValue() {
      if(!this.item.has("value") ) {
        return null;
      }
      return this.item.get("value").getAsString();
    }

    @Override
    public List<Item> getChilds() {
      if( this.childs == null ) {
        if(!this.item.has("childs") ) {
          return null;
        }
        List<Item> theChilds = new ArrayList<>();
        for (JsonElement e : this.item.getAsJsonArray("childs")) {
          JsonObject x = (JsonObject) e;
          Item i = null;
          switch(x.get("type").getAsString().toLowerCase()) {
            case "menu":
              i = new MenuItem(this, this.agent, x);
              break;
            case "inputstring":
              i = new InputStringItem(this, this.agent, x);
              break;
            case "selectoption":
              i = new SelectOptionItem(this, this.agent, x);
              break;
            case "value":
            default:
              i = new ValueItem(this, this.agent, x);
          }
          theChilds.add(i);
        }
        this.childs = theChilds;
      }
      return this.childs;
    }
    
  }
  
  private static class MenuItem extends AbstractItem {
    
    public MenuItem(Item parent, Agent agent, JsonObject item) {
      super(parent, agent, item);
    }

    @Override
    public Item show() {
      AgentConsoleImpl console = (AgentConsoleImpl) agent.getConsole();
      console.println("\n--- " + getLabel() + " ---");
      List<Item> childs = getChilds();
      if (childs != null) {
          for (int i = 0; i < childs.size(); i++) {
              Item child = childs.get(i);
              String value = "";
              if (child.getVariableName() != null) {
                  String v = agent.getSettings().getProperty(child.getVariableName());
                  value = " [" + (v == null ? "no definido" : v) + "]";
              }
              console.println("[" + (i + 1) + "] " + child.getLabel() + value);
          }
      }
      console.println("[0] " + (getParent() == null ? "Salir" : "Volver"));
      return this;
    }

    @Override
    public Item input() {
      AgentConsoleImpl console = (AgentConsoleImpl) agent.getConsole();
      LineReader reader = console.getLineReader();
      String input = reader.readLine("Seleccione una opcion: ");
      try {
          int choice = Integer.parseInt(input.trim());
          if (choice == 0) {
              return getParent();
          }
          List<Item> childs = getChilds();
          if (childs != null && choice > 0 && choice <= childs.size()) {
              return childs.get(choice - 1);
          }
      } catch (Exception e) {
          // Ignorar errores de parseo
      }
      return this;
    }
    
  }
  
  private static class InputStringItem extends AbstractItem {
    
    public InputStringItem(Item parent, Agent agent, JsonObject item) {
      super(parent, agent, item);
    }

    @Override
    public Item show() {
      AgentConsoleImpl console = (AgentConsoleImpl) agent.getConsole();
      String current = agent.getSettings().getProperty(getVariableName());
      console.println("\nModificando: " + getLabel());
      console.println("Valor actual: " + (current == null ? "(vacio)" : current));
      return this;
    }

    @Override
    public Item input() {
      AgentConsoleImpl console = (AgentConsoleImpl) agent.getConsole();
      LineReader reader = console.getLineReader();
      String input = reader.readLine("Nuevo valor (deja en blanco para cancelar): ");
      if (input != null && !input.trim().isEmpty()) {
          agent.getSettings().setProperty(getVariableName(), input.trim());
          if (getActionName() != null) {
              agent.getActions().call(getActionName(), agent.getSettings());
          }
          agent.getSettings().save();
      }
      return getParent();
    }
    
  }
  
  private static class SelectOptionItem extends AbstractItem {
    
    public SelectOptionItem(Item parent, Agent agent, JsonObject item) {
      super(parent, agent, item);
    }

    @Override
    public Item show() {
      AgentConsoleImpl console = (AgentConsoleImpl) agent.getConsole();
      console.println("\n--- " + getLabel() + " ---");
      List<Item> childs = getChilds();
      if (childs != null) {
          for (int i = 0; i < childs.size(); i++) {
              console.println("[" + (i + 1) + "] " + childs.get(i).getLabel());
          }
      }
      console.println("[0] Cancelar");
      return this;
    }

    @Override
    public Item input() {
      AgentConsoleImpl console = (AgentConsoleImpl) agent.getConsole();
      LineReader reader = console.getLineReader();
      String input = reader.readLine("Seleccione una opcion: ");
      try {
          int choice = Integer.parseInt(input.trim());
          if (choice == 0) {
              return getParent();
          }
          List<Item> childs = getChilds();
          if (childs != null && choice > 0 && choice <= childs.size()) {
              Item selected = childs.get(choice - 1);
              agent.getSettings().setProperty(getVariableName(), selected.getValue());
              if (getActionName() != null) {
                  agent.getActions().call(getActionName(), agent.getSettings());
              }
              agent.getSettings().save();
              return getParent();
          }
      } catch (Exception e) {
          // Ignorar
      }
      return this;
    }
    
  }
  
  private static class ValueItem extends AbstractItem {
    
    public ValueItem(Item parent, Agent agent, JsonObject item) {
      super(parent, agent, item);
    }

    @Override
    public Item show() {
        return this;
    }

    @Override
    public Item input() {
        return getParent();
    }
    
  }
  
  public AgentConsoleSettingsImpl(AgentUIManager agentUIManager, Agent agent) {
    File settingsUIFile = new File(agent.getDataFolder(),"settingsui.json");
    JsonObject uiroot = null;
    try (FileReader reader = new FileReader(settingsUIFile)) {
        uiroot = JsonParser.parseReader(reader).getAsJsonObject();
    } catch (Exception e) {
        // Fallback minimo si el fichero no existe o es corrupto
        uiroot = new JsonObject();
        uiroot.addProperty("type", "menu");
        uiroot.addProperty("label", "Configuracion (Fichero no encontrado)");
    }
    this.root = new MenuItem(null, agent, uiroot);
  }
  
  public AgentConsoleSettingsImpl(AgentUIManager agentUIManager, File dataFolder, AgentConsole console) {
    this(agentUIManager, new FakeAgent(dataFolder, console));
  }
  
  public void show() {
      Item current = this.root;
      while (current != null) {
          current = current.show();
          current = current.input();
      }
  }
  
}
