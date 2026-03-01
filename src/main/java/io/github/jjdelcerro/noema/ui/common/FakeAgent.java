package io.github.jjdelcerro.noema.ui.common;

import com.google.gson.JsonObject;
import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.AgentActions;
import io.github.jjdelcerro.noema.lib.AgentConsole;
import io.github.jjdelcerro.noema.lib.AgentLocator;
import io.github.jjdelcerro.noema.lib.settings.AgentSettings;
import io.github.jjdelcerro.noema.lib.persistence.SourceOfTruth;
import io.github.jjdelcerro.noema.lib.AgentAccessControl;
import io.github.jjdelcerro.noema.lib.AgentManager;
import io.github.jjdelcerro.noema.lib.AgentPaths;
import io.github.jjdelcerro.noema.lib.AgentService;
import io.github.jjdelcerro.noema.lib.ConnectionSupplier;
import java.util.function.Supplier;

/**
 * Mínima implementación de Agent para permitir la configuración inicial sin
 * arrancar el motor de IA.
 *
 * @author jjdelcerro
 */
public class FakeAgent implements Agent {

  private final AgentSettings settings;
  private AgentConsole console;
  private final AgentActions actions;

  public FakeAgent(AgentSettings settings) {
    AgentManager agentManager = AgentLocator.getAgentManager();
    this.settings = settings;
    this.actions = new FakeAgentActions(agentManager.createActions());
    this.console = new FakeConsole();
    this.settings.load();

    AgentManager manager = AgentLocator.getAgentManager();
    for (Supplier<AgentActions.AgentAction> actionFactory : manager.getActions()) {
      AgentActions.AgentAction action = actionFactory.get();
      action.setAgent(this);
      this.actions.addAction(action);
    }
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
  public void putEvent(String channel, String status, String priority, String eventText) {
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
  public ChatModel createChatModel(String name) {
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
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public void showSession() {

  }

  @Override
  public AgentPaths getPaths() {
    return settings.getPaths();
  }

  @Override
  public int getConversationContextSize() {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  private static class FakeConsole implements AgentConsole {

    @Override
    public boolean confirm(String message) {
      throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void printSystemError(String message) {
    }

    @Override
    public void printSystemLog(String message) {
    }

    @Override
    public void printUserMessage(String message) {
    }

    @Override
    public void printModelResponse(String message) {
    }

  }

  private static class FakeAgentActions implements AgentActions {

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
