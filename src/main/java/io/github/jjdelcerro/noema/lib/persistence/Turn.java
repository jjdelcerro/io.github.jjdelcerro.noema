package io.github.jjdelcerro.noema.lib.persistence;

import java.sql.Timestamp;
import java.time.LocalDateTime;

/**
 *
 * @author jjdelcerro
 */
public interface Turn {

  /**
   * Devuelve el texto concatenado que representa el contenido semántico del
   * turno.Útil para que el SourceOfTruth calcule el embedding sobre esto.
   *
   * @return
   */
  String getContentForEmbedding();

  String getContenttype();

  float[] getEmbedding();

  int getId();

  String getTextModel();

  String getTextModelThinking();

  String getTextUser();

  LocalDateTime getTimestamp();

  String getToolCall();

  String getToolResult();

  /**
   * Genera una línea CSV formateada y escapada para el protocolo de
   * compactación.
   *
   * @return
   */
  String toCSVLine();

}
