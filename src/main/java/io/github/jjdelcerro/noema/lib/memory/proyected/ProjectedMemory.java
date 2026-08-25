package io.github.jjdelcerro.noema.lib.memory.proyected;

import dev.langchain4j.data.message.ChatMessage;
import io.github.jjdelcerro.noema.lib.AgentTool;
import io.github.jjdelcerro.noema.lib.memory.recent.RecentMemory;
import io.github.jjdelcerro.noema.lib.memory.compacted.CompactedMemory;
import io.github.jjdelcerro.noema.lib.memory.proyected.operations.PinnedTurnsOperation.PinnedTurnState;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Predicate;

public interface ProjectedMemory {

  /**
   * Devuelve la lista inmutable y curada de mensajes lista para ser enviada al
   * LLM.
   *
   * @param recentMemory
   * @param compactedMemory
   * @param systemPrompt
   * @return
   */
  public List<ChatMessage> getMessages(
          RecentMemory recentMemory,
          CompactedMemory compactedMemory,
          String systemPrompt
  );

  public LocalDateTime getLastInteractionTime();
  
  public void setLastInteractionTime(LocalDateTime lastInteractionTime);
  
  public void setLastInteractionTurn(long turnid);
  
  public long getLastInteractionTurn();
  
  public AgentTool getTool(String name);
  
  public void save();
  
  public ProjectedMemoryOperation getOperation(String name);
  
}
