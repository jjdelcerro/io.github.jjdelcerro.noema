package io.github.jjdelcerro.noema.lib.impl;

import com.google.gson.JsonObject;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import io.github.jjdelcerro.noema.lib.api.Agent;
import io.github.jjdelcerro.noema.lib.api.AgentAccessControl;
import io.github.jjdelcerro.noema.lib.api.AgentActions;
import io.github.jjdelcerro.noema.lib.api.AgentConsole;
import io.github.jjdelcerro.noema.lib.api.AgentPaths;
import io.github.jjdelcerro.noema.lib.api.AgentService;
import io.github.jjdelcerro.noema.lib.api.ConnectionSupplier;
import io.github.jjdelcerro.noema.lib.api.Telemetry;
import io.github.jjdelcerro.noema.lib.api.memory.episodic.EpisodicMemory;
import io.github.jjdelcerro.noema.lib.api.model.ChatModel;
import io.github.jjdelcerro.noema.lib.api.model.ModelParameters;
import io.github.jjdelcerro.noema.lib.api.services.sensors.SensorInformation;
import io.github.jjdelcerro.noema.lib.api.services.sensors.SensorNature;
import io.github.jjdelcerro.noema.lib.api.services.sensors.SensorsService;
import io.github.jjdelcerro.noema.lib.api.settings.AgentSettings;
import java.util.Collection;
import java.util.List;

/**
 *
 * @author jjdelcerro
 */
public class AgentDelegated implements Agent {

  private final Agent agent;
  
  public AgentDelegated(Agent agent) {
    this.agent = agent;
  }

  @Override
  public AgentPaths getPaths() {
    return agent.getPaths();
  }

  @Override
  public AgentActions getActions() {
    return agent.getActions();
  }

  @Override
  public AgentSettings getSettings() {
    return agent.getSettings();
  }

  @Override
  public AgentConsole getConsole(String subchannel) {
    return agent.getConsole(subchannel);
  }

  @Override
  public AgentConsole getCurrentConsole() {
    return agent.getCurrentConsole();
  }

  @Override
  public EpisodicMemory getEpisodicMemory() {
    return agent.getEpisodicMemory();
  }

  @Override
  public void putEvent(String channel, String subchannel, String status, String priority, String eventText) {
    agent.putEvent(channel, subchannel, status, priority, eventText);
  }

  @Override
  public void putUsersMessage(String subchannel, String text, SensorsService.SensorEventCallback callback) {
    agent.putUsersMessage(subchannel, text, callback);
  }

  @Override
  public SensorInformation registerSensor(String channel, String label, SensorNature nature, String description) {
    return agent.registerSensor(channel, label, nature, description);
  }

  @Override
  public AgentAccessControl getAccessControl() {
    return agent.getAccessControl();
  }

  @Override
  public void setConsole(String subchannel, AgentConsole console) {
    agent.setConsole(subchannel, console);
  }

  @Override
  public ConnectionSupplier getServicesDatabase() {
    return agent.getServicesDatabase();
  }

  @Override
  public ConnectionSupplier getEpisodicMemoryDatabase() {
    return agent.getEpisodicMemoryDatabase();
  }

  @Override
  public AgentService getService(String name) {
    return agent.getService(name);
  }

  @Override
  public Collection<AgentService> getServices() {
    return agent.getServices();
  }

  @Override
  public void setupServices() {
    agent.setupServices();
  }

  @Override
  public void start() {
    agent.start();
  }

  @Override
  public void stop() {
    agent.stop();
  }

  @Override
  public String getResourceAsString(String resname) {
    return agent.getResourceAsString(resname);
  }

  @Override
  public ChatModel createChatModel(String name) {
    return agent.createChatModel(name);
  }

  @Override
  public ModelParameters getModelParameters(String name) {
    return agent.getModelParameters(name);
  }

  @Override
  public ModelParameters getModelParameters(String providerUrl, String providerApiKey, String modelId) {
    return agent.getModelParameters(providerUrl, providerApiKey, modelId);
  }

  @Override
  public ModelParameters getModelParameters(String providerUrl, String providerApiKey, String modelId, double temperature) {
    return agent.getModelParameters(providerUrl, providerApiKey, modelId, temperature);
  }

  @Override
  public ChatModel createChatModel(ModelParameters params) {
    return agent.createChatModel(params);
  }

  @Override
  public String callChatModel(String docmapper_reasoning_llm, String extractStructureSystemPrompt, String doc_csv) {
    return agent.callChatModel(docmapper_reasoning_llm, extractStructureSystemPrompt, doc_csv);
  }

  @Override
  public JsonObject callChatModelAsJson(String docmapper_basic_llm, String summaryAndCategorizeSystemPrompt, String contents) {
    return agent.callChatModelAsJson(docmapper_basic_llm, summaryAndCategorizeSystemPrompt, contents);
  }

  @Override
  public void installResource(String resPath) {
    agent.installResource(resPath);
  }

  @Override
  public int getConversationContextSize() {
    return agent.getConversationContextSize();
  }

  @Override
  public int estimateTokenCount(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
    return agent.estimateTokenCount(messages, toolSpecifications);
  }

  @Override
  public String getCurrentSubchannel() {
    return agent.getCurrentSubchannel();
  }

  @Override
  public void addSharedService(AgentService service) {
    agent.addSharedService(service);
  }

  @Override
  public Telemetry getTelemetry() {
    return agent.getTelemetry();
  }
}
