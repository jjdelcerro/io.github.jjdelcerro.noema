package io.github.jjdelcerro.noema.lib.api.memory.projected;

/**
 *
 * @author jjdelcerro
 */
public interface ContextZoning {
  
  public enum ContextZoneType {
    SAFE_ZONE,                   // Zona reciente: intacta y fuera de peligro
    PRUNING_NOTIFICATION_ACCUMULATION_ZONE, // Zona intermedia mas reciente: intacta, acumulación de podas sin avisar aún
    PRUNING_NOTIFICATION_ZONE,  // Zona intermedia mas antigua: intacta, con aviso inminente antes de la poda (deadline)
    PRUNING_ACCUMULATION_ZONE,  // Zona antigua: cuerpos que pueden ser podados.
    PRUNING_ZONE                // Zona antigua: cuerpos podados, inalterada hasta consolidar
  }
  /**
   * Rango de indices sobre una lista de mensajes proyectados.
   *
   * <p>Los rangos son semiabiertos, [min, max), de modo que las zonas
   * particionan la lista sin solaparse ni dejar huecos. Un indice pertenece a
   * una y solo una zona.</p>
   */
  public interface ContextZone {
    public ContextZoneType type();
    public boolean isEmpty();
    public int max();
    public int min();
    public boolean contains(int n);    
  }

  /**
   * Devuelve la zona de referencia, calculada como si el contexto estuviera
   * lleno hasta el limite maximo configurado.
   *
   * @param zone tipo de zona
   * @return la zona de referencia
   */
  ContextZone getZone(ContextZoneType zone);
  
  /**
   * Devuelve la zona calculada sobre el total actual.
   *
   * @param zone tipo de zona
   * @param currentMessages total actual de la magnitud que mide esa zona
   * @return la zona correspondiente a ese total
   */
  ContextZone getZone(ContextZoneType zone, int currentMessages);
  
}
