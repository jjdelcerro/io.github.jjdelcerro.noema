package io.github.jjdelcerro.noema.lib.persistence;

import java.time.LocalDateTime;
import java.util.List;

/**
 *
 * @author jjdelcerro
 */
public interface SourceOfTruth {

  CheckPoint createCheckPoint(String subchannel, int turnFirst, int turnLast, LocalDateTime timestamp, String text);

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
  void add(CheckPoint checkpoint);

  CheckPoint getLatestCheckPoint(String subchannel);

  /**
   * Recupera todos los turnos que aún no han sido consolidados en un
   * CheckPoint.
   *
   * @return
   */
  List<Turn> getUnconsolidatedTurns(String subchannel);

  Turn getTurnById(int id);

  List<Turn> getTurnsByIds(String subchannel, int first, int last);

  List<Turn> getTurnsByText(String subchannel, String query, int maxResults);

  CheckPoint getCheckPointById(int id);

}
