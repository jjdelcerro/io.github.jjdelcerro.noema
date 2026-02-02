package io.github.jjdelcerro.chatagent.ui.common;

import io.github.jjdelcerro.chatagent.lib.Agent;
import io.github.jjdelcerro.chatagent.lib.AgentActions;
import io.github.jjdelcerro.chatagent.lib.AgentConsole;
import io.github.jjdelcerro.chatagent.lib.AgentLocator;
import io.github.jjdelcerro.chatagent.lib.AgentSettings;
import io.github.jjdelcerro.chatagent.lib.persistence.SourceOfTruth;
import java.io.File;
import java.nio.file.Path;
import io.github.jjdelcerro.chatagent.lib.AgentAccessControl;

/**
 * Mínima implementación de Agent para permitir la configuración inicial
 * sin arrancar el motor de IA.
 * 
 * @author jjdelcerro
 */
public class FakeAgent implements Agent {

    private final File dataFolder;
    private final AgentSettings settings;
    private AgentConsole console;
    private final AgentActions actions = new FakeActions();

    public FakeAgent(File settingsFile, AgentConsole console) {
        this.dataFolder = settingsFile.getParentFile();
        this.settings = AgentLocator.getAgentManager().createSettings();
        this.console = console;
        this.settings.load(settingsFile); // Para que sepa donde guardar
    }

    @Override
    public File getDataFolder() {
        return dataFolder;
    }

    @Override
    public AgentActions getActions() {
        return actions;
    }

    @Override
    public AgentSettings getSettings() {
        return settings;
    }

    @Override
    public AgentConsole getConsole() {
        return console;
    }

    @Override
    public SourceOfTruth getSourceOfTruth() {
        throw new UnsupportedOperationException("FakeAgent no tiene SourceOfTruth.");
    }

    @Override
    public String processTurn(String input) {
        throw new UnsupportedOperationException("FakeAgent no puede procesar turnos.");
    }

    @Override
    public void putEvent(String channel, String priority, String eventText) {
        // No hace nada
    }

    @Override
    public AgentAccessControl getAccessControl() {
      throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }

  @Override
  public void setConsole(AgentConsole console) {
    this.console = console;
  }

    private static class FakeActions implements AgentActions {
        @Override
        public void addAction(String name, AgentAction action) {
            // No hace nada
        }

        @Override
        public boolean call(String name, AgentSettings settings) {
            // Siempre devuelve éxito para no bloquear la UI
            return true;
        }
    }
}
