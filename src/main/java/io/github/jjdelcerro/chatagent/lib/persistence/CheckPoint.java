package io.github.jjdelcerro.chatagent.lib.persistence;

import java.sql.Timestamp;

/**
 *
 * @author jjdelcerro
 */
public interface CheckPoint {

    int getTurnFirst();

    int getTurnLast();

    /**
     * Genera el código único del CheckPoint. 
     * Formato: checkpoint-{id}-{first}-{last}
     */
    String getCode();

    int getId();

    /**
     * Obtiene el contenido textual (Resumen + El Viaje). Si no está en memoria,
     * lo lee del archivo correspondiente en disco.
     */
    String getText();

    Timestamp getTimestamp();

}
