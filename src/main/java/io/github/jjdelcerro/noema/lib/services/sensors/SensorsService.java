package io.github.jjdelcerro.noema.lib.services.sensors;

import io.github.jjdelcerro.noema.lib.AgentService;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Entry point for the sensory system.
 */
public interface SensorsService extends AgentService {

  public static final String NAME = "Sensors";

  public static final String PRIORITY_LOW = "baja";
  public static final String PRIORITY_NORMAL = "normal";
  public static final String PRIORITY_HIGH = "alta";

  public interface SensorEventCallback {

    public void onComplete(String response);
  }

  SensorInformation createSensorInformation(String channel, String label, SensorNature nature, String description);

  SensorInformation createSensorInformation(String channel, String label, SensorNature nature, String description, boolean silenceable);

  void registerSensor(SensorInformation info);

  void putEvent(String channel, String text, String priority, String status, LocalDateTime timestamp);

  void putEvent(String channel, String text, String priority, String status, LocalDateTime timestamp, SensorEventCallback callback);

  List<SensorInformation> getAllRegisteredSensors();

  SensorStatistics getSensorStatistics(String channel);

  /**
   * Cambia el estado de silencio de un canal. Si está silenciado, los eventos
   * entrantes se contarán en las estadísticas pero no llegarán a la cola.
   */
  void setSilenced(String channel, boolean muted);

  /**
   * Consulta si un canal está actualmente silenciado.
   */
  boolean isSilenced(String channel);
}
