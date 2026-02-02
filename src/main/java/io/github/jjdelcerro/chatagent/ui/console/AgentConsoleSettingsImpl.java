package io.github.jjdelcerro.chatagent.ui.console;

import io.github.jjdelcerro.chatagent.ui.common.FakeAgent;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jjdelcerro.chatagent.lib.Agent;
import io.github.jjdelcerro.chatagent.lib.AgentConsole;
import io.github.jjdelcerro.chatagent.ui.AgentUIManager;
import io.github.jjdelcerro.chatagent.ui.AgentUISettings;
import io.github.jjdelcerro.chatagent.ui.common.AbstractAgentSettingsItem;
import io.github.jjdelcerro.chatagent.ui.common.AgentSettingsItem;
import java.io.File;
import java.io.FileReader;
import java.util.List;
import org.jline.reader.LineReader;

/**
 *
 * @author jjdelcerro
 */
public class AgentConsoleSettingsImpl implements AgentUISettings {

  private final MenuItem root;

  private interface AgentSettingsItemConsole extends AgentSettingsItem {

    AgentSettingsItemConsole show();  // Muestra el UI
    AgentSettingsItemConsole input(); // Espera la entrada del usuario y devuelve el siguiente item
  }

  private static abstract class AbstractAgentSettingsItemConsole
          extends AbstractAgentSettingsItem
          implements AgentSettingsItemConsole {

    protected AbstractAgentSettingsItemConsole(AgentSettingsItem parent, Agent agent, JsonObject item) {
      super(parent, agent, item);
    }

    @Override
    protected AgentSettingsItem createItem(AgentSettingsItem parent, Agent agent, JsonObject jsonItem) {
          switch (jsonItem.get("type").getAsString().toLowerCase()) {
            case "menu":
              return new MenuItem(this, this.agent, jsonItem);
            case "inputstring":
              return  new InputStringItem(this, this.agent, jsonItem);
            case "selectoption":
              return  new SelectOptionItem(this, this.agent, jsonItem);
            case "value":
            default:
              return  new ValueItem(this, this.agent, jsonItem);
          }
    }
  }

  private static class MenuItem extends AbstractAgentSettingsItemConsole {

    public MenuItem(AgentSettingsItemConsole parent, Agent agent, JsonObject item) {
      super(parent, agent, item);
    }

    @Override
    public AgentSettingsItemConsole show() {
      AgentConsoleImpl console = (AgentConsoleImpl) agent.getConsole();
      console.println("\n--- " + getLabel() + " ---");
      List<AgentSettingsItem> childs = getChilds();
      if (childs != null) {
        for (int i = 0; i < childs.size(); i++) {
          AgentSettingsItem child = childs.get(i);
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
    public AgentSettingsItemConsole input() {
      AgentConsoleImpl console = (AgentConsoleImpl) agent.getConsole();
      LineReader reader = console.getLineReader();
      String input = reader.readLine("Seleccione una opcion: ");
      try {
        int choice = Integer.parseInt(input.trim());
        if (choice == 0) {
          return (AgentSettingsItemConsole) getParent();
        }
        List<AgentSettingsItem> childs = getChilds();
        if (childs != null && choice > 0 && choice <= childs.size()) {
          return (AgentSettingsItemConsole) childs.get(choice - 1);
        }
      } catch (Exception e) {
        // Ignorar errores de parseo
      }
      return this;
    }

  }

  private static class InputStringItem extends AbstractAgentSettingsItemConsole {

    public InputStringItem(AgentSettingsItem parent, Agent agent, JsonObject item) {
      super(parent, agent, item);
    }

    @Override
    public AgentSettingsItemConsole show() {
      AgentConsoleImpl console = (AgentConsoleImpl) agent.getConsole();
      String current = agent.getSettings().getProperty(getVariableName());
      console.println("\nModificando: " + getLabel());
      console.println("Valor actual: " + (current == null ? "(vacio)" : current));
      return this;
    }

    @Override
    public AgentSettingsItemConsole input() {
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
      return (AgentSettingsItemConsole) getParent();
    }

  }

  private static class SelectOptionItem extends AbstractAgentSettingsItemConsole {

    public SelectOptionItem(AgentSettingsItem parent, Agent agent, JsonObject item) {
      super(parent, agent, item);
    }

    @Override
    public AgentSettingsItemConsole show() {
      AgentConsoleImpl console = (AgentConsoleImpl) agent.getConsole();
      console.println("\n--- " + getLabel() + " ---");
      List<AgentSettingsItem> childs = getChilds();
      if (childs != null) {
        for (int i = 0; i < childs.size(); i++) {
          console.println("[" + (i + 1) + "] " + childs.get(i).getLabel());
        }
      }
      console.println("[0] Cancelar");
      return this;
    }

    @Override
    public AgentSettingsItemConsole input() {
      AgentConsoleImpl console = (AgentConsoleImpl) agent.getConsole();
      LineReader reader = console.getLineReader();
      String input = reader.readLine("Seleccione una opcion: ");
      try {
        int choice = Integer.parseInt(input.trim());
        if (choice == 0) {
          return (AgentSettingsItemConsole) getParent();
        }
        List<AgentSettingsItem> childs = getChilds();
        if (childs != null && choice > 0 && choice <= childs.size()) {
          AgentSettingsItem selected = childs.get(choice - 1);
          agent.getSettings().setProperty(getVariableName(), selected.getValue());
          if (getActionName() != null) {
            agent.getActions().call(getActionName(), agent.getSettings());
          }
          agent.getSettings().save();
          return (AgentSettingsItemConsole) getParent();
        }
      } catch (Exception e) {
        // Ignorar
      }
      return this;
    }

  }

  private static class ValueItem extends AbstractAgentSettingsItemConsole {

    public ValueItem(AgentSettingsItem parent, Agent agent, JsonObject item) {
      super(parent, agent, item);
    }

    @Override
    public AgentSettingsItemConsole show() {
      return this;
    }

    @Override
    public AgentSettingsItemConsole input() {
      return (AgentSettingsItemConsole) getParent();
    }

  }

  public AgentConsoleSettingsImpl(AgentUIManager agentUIManager, Agent agent) {
    File settingsUIFile = new File(agent.getDataFolder(), "settingsui.json");
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

  public void showWindow() {
    AgentSettingsItemConsole current = this.root;
    while (current != null) {
      current = current.show();
      current = current.input();
    }
  }

}
