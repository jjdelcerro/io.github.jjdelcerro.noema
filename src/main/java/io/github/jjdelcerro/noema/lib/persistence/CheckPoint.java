package io.github.jjdelcerro.noema.lib.persistence;

import java.sql.Timestamp;
import java.time.LocalDateTime;

/**
 *
 * @author jjdelcerro
 */
public interface CheckPoint {

  int getTurnFirst();

  int getTurnLast();

  /**
   * Genera el código único del CheckPoint.Formato:
   * checkpoint-{id}-{first}-{last}
   *
   * @return
   */
  String getCode();

  int getId();

  /**
   * Obtiene el contenido textual (Resumen + El Viaje).Si no está en memoria, lo
   * lee del archivo correspondiente en disco.
   *
   * @return
   */
  String getText();

  LocalDateTime getTimestamp();

}
