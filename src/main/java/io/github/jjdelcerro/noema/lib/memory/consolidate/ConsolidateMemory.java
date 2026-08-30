package io.github.jjdelcerro.noema.lib.memory.consolidate;

import java.time.LocalDateTime;

/**
 *
 *
 * TODO: Antes CheckPoint, habria que actualizar la documentacion con este cambio
 *
 * @author jjdelcerro
 */
public interface ConsolidateMemory {

  int getTurnFirst();

  int getTurnLast();

  /**
   * Genera el código único del ConsolidateMemory.
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

  String getSubchannel();
  
  String getSummary();

}
