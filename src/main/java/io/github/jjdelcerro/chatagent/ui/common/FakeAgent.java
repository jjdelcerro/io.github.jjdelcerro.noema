package io.github.jjdelcerro.chatagent.ui.common;

import com.google.gson.JsonObject;
import dev.langchain4j.model.openai.OpenAiChatModel;
import io.github.jjdelcerro.chatagent.lib.Agent;
import io.github.jjdelcerro.chatagent.lib.AgentActions;
import io.github.jjdelcerro.chatagent.lib.AgentConsole;
import io.github.jjdelcerro.chatagent.lib.AgentLocator;
import io.github.jjdelcerro.chatagent.lib.AgentSettings;
import io.github.jjdelcerro.chatagent.lib.persistence.SourceOfTruth;
import java.io.File;
import io.github.jjdelcerro.chatagent.lib.AgentAccessControl;
import io.github.jjdelcerro.chatagent.lib.AgentManager;
import io.github.jjdelcerro.chatagent.lib.AgentService;
import io.github.jjdelcerro.chatagent.lib.ConnectionSupplier;
import java.util.function.Supplier;

/**
 * Mínima implementación de Agent para permitir la configuración inicial sin
 * arrancar el motor de IA.
 *
 * @author jjdelcerro
 */
public class FakeAgent implements Agent {

  private final File agentFolder;
  private final AgentSettings settings;
  private AgentConsole console;
  private final AgentActions actions;

  public FakeAgent(File agentFolder, AgentConsole console) {
    this.agentFolder = agentFolder;
    AgentManager agentManager = AgentLocator.getAgentManager();
    this.settings = agentManager.createSettings();
    this.actions = new FakeAgentActions(agentManager.createActions());
    this.console = console;
    this.settings.load(new File(agentFolder,"data/settings.properties"));

    AgentManager manager = AgentLocator.getAgentManager();
    for (Supplier<AgentActions.AgentAction> actionFactory : manager.getActions()) {
      AgentActions.AgentAction action = actionFactory.get();
      action.setAgent(this);
      this.actions.addAction(action);
    }
  }

  @Override
  public File getAgentFolder() {
    return this.agentFolder;
  }

  @Override
  public File getDataFolder() {
    return new File(this.agentFolder,"data");
  }

  @Override
  public File getDataFolder(String name) {
    File file = this.getDataFolder().toPath().resolve(name).normalize().toFile();
    return file;
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
    throw new UnsupportedOperationException("Not supported yet."); 
  }

  @Override
  public void setConsole(AgentConsole console) {
    this.console = console;
  }

  @Override
  public AgentService getService(String name) {
    return null;
  }

  @Override
  public void start() {
  }

  @Override
  public String getResourceAsString(String resname) {
    throw new UnsupportedOperationException("Not supported yet."); 
  }

  @Override
  public OpenAiChatModel createChatModel(String name) {
    throw new UnsupportedOperationException("Not supported yet."); 
  }

  @Override
  public ModelParameters getModelParameters(String name) {
    throw new UnsupportedOperationException("Not supported yet."); 
  }

  @Override
  public String callChatModel(String docmapper_reasoning_llm, String extractStructureSystemPrompt, String doc_csv) {
    throw new UnsupportedOperationException("Not supported yet."); 
  }

  @Override
  public JsonObject callChatModelAsJson(String docmapper_basic_llm, String summaryAndCategorizeSystemPrompt, String contents) {
    throw new UnsupportedOperationException("Not supported yet."); 
  }

  @Override
  public ConnectionSupplier getMemoryDatabase() {
    throw new UnsupportedOperationException("Not supported yet."); 
  }

  @Override
  public ConnectionSupplier getServicesDatabase() {
    throw new UnsupportedOperationException("Not supported yet."); 
  }

  @Override
  public void installResource(String resPath) {
    throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
  }

  @Override
  public void showSession() {

  }

  private class FakeAgentActions implements AgentActions {

    private final AgentActions delegate;

    public FakeAgentActions(AgentActions delegate) {
      this.delegate = delegate;
    }
    
    @Override
    public void addAction(AgentAction action) {
      this.delegate.addAction(action);
    }

    @Override
    public boolean call(String name, AgentSettings settings) {
      delegate.call(name, settings);
      return true;
    }
    
  }
}
