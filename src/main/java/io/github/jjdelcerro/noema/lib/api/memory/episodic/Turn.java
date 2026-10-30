package io.github.jjdelcerro.noema.lib.api.memory.episodic;

import java.time.LocalDateTime;

/**
 *
 * @author jjdelcerro
 */
public interface Turn {

  /**
   * Devuelve el texto concatenado que representa el contenido semántico del
   * turno.Útil para que el EpisodicMemory calcule el embedding sobre esto.
   *
   * @return
   */
  String getContentForEmbedding();

  String getContenttype();

  String getSubchannel();
  
  float[] getEmbedding();

  int getId();

  String getTextModel();

  String getTextModelThinking();

  String getTextUser();

  LocalDateTime getTimestamp();

  String getToolCall();

  String getToolResult();

  /**
   * Devuelve una copia del turno con el toolCall sustituido. El resto de
   * campos, incluido el identificador, se conservan del original. El turno
   * sobre el que se invoca no se modifica.
   *
   * @param toolCall Nuevo valor del toolCall.
   * @return Una instancia nueva del turno.
   */
  Turn withToolCall(String toolCall);

  /**
   * Devuelve una copia del turno con el toolResult sustituido. El resto de
   * campos, incluido el identificador, se conservan del original. El turno
   * sobre el que se invoca no se modifica.
   *
   * @param toolResult Nuevo valor del toolResult.
   * @return Una instancia nueva del turno.
   */
  Turn withToolResult(String toolResult);

  String getAnnotationType();
  
  /**
   * Genera una línea CSV formateada y escapada para el protocolo de
   * compactación.
   *
   * @return
   */
  String toCSVLine();
  
  String toXML();

  public String getCSVHeader();

}
