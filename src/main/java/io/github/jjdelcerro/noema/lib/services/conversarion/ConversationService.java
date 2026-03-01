package io.github.jjdelcerro.noema.lib.services.conversarion;

import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.AgentService;
import io.github.jjdelcerro.noema.lib.AgentTool;
import java.util.List;

/**
 *
 * @author jjdelcerro
 */
public interface ConversationService extends AgentService {

  String CONVERSATION_MODEL_ID = "conversation/provider/model_id";
  String CONVERSATION_PROVIDER_API_KEY = "conversation/provider/api_key";
  String CONVERSATION_PROVIDER_URL = "conversation/provider/url";
  String MEMORY_COMPACTION_TURNS = "conversation/compaction_turns";
  String MEMORY_COMPACTION_TOKENS = "conversation/compaction_tokens";
  String ACTIVE_TOOLS = "conversation/active_tools";
  
  
  String NAME = "Conversation";

  void addTool(AgentTool tool);

  int estimateMessagesTokenCount();

  int estimateToolsTokenCount();

  AgentTool getAvailableTool(String name);

  List<AgentTool> getAvailableTools();

  Agent.ChatModel getModel();

  String getModelName();

  boolean isToolActive(String name);

  void setToolActive(String name, boolean active);
  
}
