package io.github.jjdelcerro.noema.lib.impl.services.sensors.persistence;

import io.github.jjdelcerro.noema.lib.services.sensors.ConsumableSensorEvent;
import io.github.jjdelcerro.noema.lib.services.sensors.SensorEvent;
import io.github.jjdelcerro.noema.lib.services.sensors.SensorInformation;
import io.github.jjdelcerro.noema.lib.services.sensors.SensorStatistics;
import java.util.List;
import java.util.Map;

/**
 * Snapshot completo del estado del SensorsService para persistencia.
 */
public class SensorsMemento {

  public Map<String, SensorInformation> infos;

  // Eventos que estaban en la cola de entrega
  public List<SensorEvent> deliveryQueue;

  // Últimos eventos de los sensores de estado
  public Map<String, ConsumableSensorEvent> stateMap;

  // Estadísticas y configuración de silencio de todos los sensores conocidos
  public Map<String, SensorStatistics> statisticsMap;
}
