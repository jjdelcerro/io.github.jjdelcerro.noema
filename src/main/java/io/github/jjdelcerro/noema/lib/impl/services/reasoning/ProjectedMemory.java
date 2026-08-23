package io.github.jjdelcerro.noema.lib.impl.services.reasoning;

import dev.langchain4j.data.message.ChatMessage;
import io.github.jjdelcerro.noema.lib.persistence.CompactedMemory;
import java.time.LocalDateTime;
import java.util.List;

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
  
  void save();
  
}
