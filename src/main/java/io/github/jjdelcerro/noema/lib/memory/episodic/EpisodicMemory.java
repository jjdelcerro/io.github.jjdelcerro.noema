package io.github.jjdelcerro.noema.lib.memory.episodic;

import io.github.jjdelcerro.noema.lib.memory.compacted.CompactedMemory;
import java.time.LocalDateTime;
import java.util.List;

/**
 *
 * TODO: Antes SourceOfTruth, habria que actualizar la documentacion con este cambio 
 * 
 * @author jjdelcerro
 */
public interface EpisodicMemory {

  CompactedMemory createCompactedMemory(String subchannel, int turnFirst, int turnLast, LocalDateTime timestamp, String text);

  CompactedMemory getLatestCompactedMemory(String subchannel);

  Turn createTurn(LocalDateTime timestamp, String contenttype, String subchannel, String textUser, String textModelThinking, String textModel, String toolCall, String toolResult, float[] embedding);

  /**
   * Persiste un Turno en la base de datos.
   *
   * @param turn
   */
  void add(Turn turn);

  /**
   * Persiste los metadatos de un CheckPoint en la base de datos.
   * @param checkpoint
   */
  void add(CompactedMemory checkpoint);

  /**
   * Recupera todos los turnos que aún no han sido consolidados en un
   * CheckPoint.
   *
   * @param subchannel
   * @return
   */
  List<Turn> getUnconsolidatedTurns(String subchannel);

  Turn getTurnById(int id);

  List<Turn> getTurnsByIds(String subchannel, int first, int last);

  List<Turn> getTurnsByText(String subchannel, String query, int maxResults, double minSimilarity, String annotationType);

  CompactedMemory getCompactedMemoryById(int id);

}
