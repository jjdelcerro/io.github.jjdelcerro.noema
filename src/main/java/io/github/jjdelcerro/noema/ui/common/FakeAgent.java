package io.github.jjdelcerro.noema.ui.common;

import com.google.gson.JsonObject;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.AgentActions;
import io.github.jjdelcerro.noema.lib.AgentConsole;
import io.github.jjdelcerro.noema.lib.AgentLocator;
import io.github.jjdelcerro.noema.lib.settings.AgentSettings;
import io.github.jjdelcerro.noema.lib.AgentAccessControl;
import io.github.jjdelcerro.noema.lib.AgentManager;
import io.github.jjdelcerro.noema.lib.AgentPaths;
import io.github.jjdelcerro.noema.lib.AgentService;
import io.github.jjdelcerro.noema.lib.ConnectionSupplier;
import io.github.jjdelcerro.noema.lib.services.sensors.SensorInformation;
import io.github.jjdelcerro.noema.lib.services.sensors.SensorNature;
import io.github.jjdelcerro.noema.lib.services.sensors.SensorsService;
import java.util.List;
import java.util.function.Supplier;
import io.github.jjdelcerro.noema.lib.persistence.EpisodicMemory;

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

  public FakeAgent(AgentSettings settings, AgentConsole console) {
    AgentManager agentManager = AgentLocator.getAgentManager();
    this.settings = settings;
    this.actions = new FakeAgentActions(agentManager.createActions());
    this.console = console==null?new FakeConsole():console;
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
  public AgentConsole getConsole(String subchannel) {
    return console;
  }

  @Override
  public EpisodicMemory getEpisodicMemory() {
    throw new UnsupportedOperationException("FakeAgent no tiene EpisodicMemory.");
  }

  @Override
  public void putEvent(String channel, String subchannel, String status, String priority, String eventText) {
    // No hace nada
  }

  @Override
  public AgentAccessControl getAccessControl() {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public void setConsole(String subchannel, AgentConsole console) {
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
  public AgentPaths getPaths() {
    return settings.getPaths();
  }

  @Override
  public int getConversationContextSize() {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public void stop() {

  }

  @Override
  public void putUsersMessage(String subchannel, String text, SensorsService.SensorEventCallback callback) {

  }
  @Override
  public SensorInformation registerSensor(String channel, String label, SensorNature nature, String description) {
    return null;
  }

  @Override
  public int estimateTokenCount(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
    return 0;
  }

    @Override
    public AgentConsole getCurrentConsole() {
        return this.console;
    }

    @Override
    public String getCurrentSubchannel() {
        return DEFAULT_SUBCHANNEL;
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

    @Override
    public void printSystemLog(String message, Format format) {
    }

    @Override
    public void printModelReasoning(String message) {
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
