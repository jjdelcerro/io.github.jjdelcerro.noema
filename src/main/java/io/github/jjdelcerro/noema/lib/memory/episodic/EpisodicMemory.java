package io.github.jjdelcerro.noema.lib.memory.episodic;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import io.github.jjdelcerro.noema.lib.memory.consolidate.ConsolidateMemory;

/**
 *
 * TODO: Antes SourceOfTruth, habria que actualizar la documentacion con este cambio 
 * 
 * @author jjdelcerro
 */
public interface EpisodicMemory {

  public interface SubchannelActivity {
    public Timestamp getLastActivity();
    public String getSubchannel();
  }
  
  List<SubchannelActivity> getSubchannelsActivity(Timestamp oldestActivity);
  
  ConsolidateMemory createConsolidateMemory(String subchannel, int turnFirst, int turnLast, LocalDateTime timestamp, String text);

  ConsolidateMemory getLatestConsolidateMemory(String subchannel);

  Turn createTurn(LocalDateTime timestamp, String contenttype, String subchannel, String textUser, String textModelThinking, String textModel, String toolCall, String toolResult, float[] embedding);

  /**
   * Persiste un Turno en la base de datos.
   *
   * @param turn
   */
  void add(Turn turn);

  /**
   * Persiste los metadatos de un ConsolidateMemory en la base de datos.
   * @param consolidateMemory
   */
  void add(ConsolidateMemory consolidateMemory);

  /**
   * Recupera todos los turnos que aún no han sido consolidados en un
   * ConsolidateMemory.
   *
   * @param subchannel
   * @return
   */
  List<Turn> getUnconsolidatedTurns(String subchannel);

  Turn getTurnById(int id);

  List<Turn> getTurnsByIds(String subchannel, int first, int last);

  List<Turn> getTurnsByText(String subchannel, String query, int maxResults, double minSimilarity, String annotationType);

  ConsolidateMemory getConsolidateMemoryById(int id);

}
