package io.github.jjdelcerro.noema.lib.memory.projected;

import dev.langchain4j.data.message.ChatMessage;
import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.AgentTool;
import io.github.jjdelcerro.noema.lib.memory.recent.RecentMemory;
import java.time.LocalDateTime;
import java.util.List;
import io.github.jjdelcerro.noema.lib.memory.consolidate.ConsolidateMemory;

public interface ProjectedMemory {

  /**
   * Devuelve la lista inmutable y curada de mensajes lista para ser enviada al
   * LLM.
   *
   * @param recentMemory
   * @param consolidateMemory
   * @param systemPrompt
   * @return
   */
  public List<ChatMessage> getMessages(
          RecentMemory recentMemory,
          ConsolidateMemory consolidateMemory,
          String systemPrompt
  );

  public LocalDateTime getLastInteractionTime();
  
  public void setLastInteractionTime(LocalDateTime lastInteractionTime);
  
  public void setLastInteractionTurn(long turnid);
  
  public long getLastInteractionTurn();
  
  public AgentTool getTool(String name);
  
  public void save();
  
  public ProjectedMemoryOperation getOperation(String name);
  
  public Agent getAgent();
  
  public String getSubchannel();
}
