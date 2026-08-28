package io.github.jjdelcerro.noema.lib.services.reasoning;

import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.AgentService;
import io.github.jjdelcerro.noema.lib.AgentTool;
import io.github.jjdelcerro.noema.lib.memory.projected.ProjectedMemory;
import java.util.List;

/**
 *
 * @author jjdelcerro
 */
public interface ReasoningService extends AgentService {

  String REASONING_MODEL_ID = "reasoning/provider/model_id";
  String REASONING_PROVIDER_API_KEY = "reasoning/provider/api_key";
  String REASONING_PROVIDER_URL = "reasoning/provider/url";
  String MEMORY_COMPACTION_TURNS = "reasoning/compaction_turns";
  String MEMORY_COMPACTION_TOKENS = "reasoning/compaction_tokens";
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

  Agent.ChatModel getModel();

  String getModelName();

  boolean isToolActive(String name);

  void setToolActive(String name, boolean active);
  
  public ProjectedMemory getProjectedMemory(String subchannel);
  
}
