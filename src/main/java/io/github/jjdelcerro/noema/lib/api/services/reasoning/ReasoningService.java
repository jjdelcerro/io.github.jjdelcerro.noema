package io.github.jjdelcerro.noema.lib.api.services.reasoning;

import io.github.jjdelcerro.noema.lib.api.Agent;
import io.github.jjdelcerro.noema.lib.api.AgentService;
import io.github.jjdelcerro.noema.lib.api.AgentTool;
import io.github.jjdelcerro.noema.lib.api.model.ChatModel;
import io.github.jjdelcerro.noema.lib.api.memory.projected.ProjectedMemory;
import java.util.List;

/**
 *
 * @author jjdelcerro
 */
public interface ReasoningService extends AgentService {

  String REASONING_MODEL_ID = "reasoning/provider/model_id";
  String REASONING_PROVIDER_API_KEY = "reasoning/provider/api_key";
  String REASONING_PROVIDER_URL = "reasoning/provider/url";
  String MEMORY_CONSOLIDATION_TURNS = "reasoning/consolidation_turns";
  String MEMORY_CONSOLIDATION_TOKENS = "reasoning/consolidation_tokens";
  String ACTIVE_TOOLS = "reasoning/active_tools";

  String NAME = "Reasoning";
  String ID = "REASONING";

  void addTool(AgentTool tool);

  int estimateMessagesTokenCount(String subchannel);

  int estimateToolsTokenCount(String subchannel);

  int estimateSystemPromptTokenCount(String subchannel);

  int getTurnsCount(String subchannel);

  AgentTool getAvailableTool(String name);

  List<AgentTool> getAvailableTools();

  ChatModel getModel();

  String getModelName();

  boolean isToolActive(String name);

  void setToolActive(String name, boolean active);

  public ProjectedMemory getProjectedMemory(String subchannel);

  public String getProjectedMemoryAsJson(String subchannel);
}
