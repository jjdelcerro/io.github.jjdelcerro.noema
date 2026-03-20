package io.github.jjdelcerro.noema.lib.impl.services.sensors;

import io.github.jjdelcerro.noema.lib.impl.services.sensors.persistence.SensorStatisticsGsonAdapter;
import io.github.jjdelcerro.noema.lib.impl.services.sensors.persistence.SensorEventGsonAdapter;
import io.github.jjdelcerro.noema.lib.impl.services.sensors.persistence.SensorsMemento;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.AgentServiceFactory;
import io.github.jjdelcerro.noema.lib.AgentTool;
import io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.events.PoolEventTool;
import io.github.jjdelcerro.noema.lib.impl.services.sensors.nature.aggregate.AggregateSensorData;
import io.github.jjdelcerro.noema.lib.impl.services.sensors.nature.discrete.DiscreteSensorData;
import io.github.jjdelcerro.noema.lib.impl.services.sensors.nature.mergeable.MergeableSensorData;
import io.github.jjdelcerro.noema.lib.impl.services.sensors.nature.state.StateSensorData;
import io.github.jjdelcerro.noema.lib.impl.services.sensors.nature.user.UserSensorData;
import io.github.jjdelcerro.noema.lib.impl.services.sensors.persistence.SensorInformationGsonAdapter;
import io.github.jjdelcerro.noema.lib.impl.services.sensors.tools.SensorStartTool;
import io.github.jjdelcerro.noema.lib.impl.services.sensors.tools.SensorStatusTool;
import io.github.jjdelcerro.noema.lib.impl.services.sensors.tools.SensorStopTool;
import io.github.jjdelcerro.noema.lib.services.sensors.ConsumableSensorEvent;
import io.github.jjdelcerro.noema.lib.services.sensors.SensorData;
import io.github.jjdelcerro.noema.lib.services.sensors.SensorEvent;
import io.github.jjdelcerro.noema.lib.services.sensors.SensorEventState;
import io.github.jjdelcerro.noema.lib.services.sensors.SensorInformation;
import io.github.jjdelcerro.noema.lib.services.sensors.SensorNature;
import io.github.jjdelcerro.noema.lib.services.sensors.SensorStatistics;
import io.github.jjdelcerro.noema.lib.services.sensors.SensorsService;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.BiFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("UseSpecificCatch")
public class SensorsServiceImpl implements SensorsService {

  private static final Logger LOGGER = LoggerFactory.getLogger(SensorsServiceImpl.class);

  public static final String SYSTEMCLOCK_SENSOR_NAME = "SYSTEMCLOCK";
  private static final String SYSTEMCLOCK_SENSOR_LABEL = "Reloj del Sistema";
  private static final String SYSTEMCLOCK_SENSOR_DESCRIPTION = "Sensor interno de paso del tiempo"; 

  private final Map<String, SensorData> registeredSensors;
  private final BlockingQueue<SensorEvent> deliveryQueue;
  private final Map<String, ConsumableSensorEvent> stateMap;
  private SensorData currentSensor;
  private final Map<SensorNature, BiFunction<SensorInformation, SensorStatistics, SensorData>> sensorDataFactory;
  private boolean running;
  private final SensorsServiceFactory factory;
  private final Agent agent;

  // Mapa temporal para guardar estadísticas cargadas del disco hasta que se registre el sensor real
  private final Map<String, SensorStatistics> rehydratedStats;
  private final Map<String, SensorInformation> knownSensors;

  private final Object sensorLock = new Object();

  public SensorsServiceImpl(SensorsServiceFactory factory, Agent agent) {
    this.factory = factory;
    this.agent = agent;
    this.rehydratedStats = new HashMap<>();
    this.knownSensors = new HashMap<>();
    this.running = false;
    this.registeredSensors = new HashMap<>();
    this.deliveryQueue = new LinkedBlockingQueue<>();
    this.stateMap = new HashMap<>();
    this.sensorDataFactory = new EnumMap<>(SensorNature.class);

    sensorDataFactory.put(SensorNature.DISCRETE, DiscreteSensorData::new);
    sensorDataFactory.put(SensorNature.MERGEABLE, MergeableSensorData::new);
    sensorDataFactory.put(SensorNature.AGGREGATABLE, AggregateSensorData::new);
    sensorDataFactory.put(SensorNature.STATE, StateSensorData::new);
    sensorDataFactory.put(SensorNature.USER, UserSensorData::new);
    
    SensorInformation sensorSystemClock = this.createSensorInformation(
            SYSTEMCLOCK_SENSOR_NAME,
            SYSTEMCLOCK_SENSOR_LABEL,
            SensorNature.DISCRETE, 
            SYSTEMCLOCK_SENSOR_DESCRIPTION
    );
    this.registerSensor(sensorSystemClock);

  }

  @Override
  public SensorInformation createSensorInformation(String channel, String label, SensorNature nature, String description) {
    return new SensorInformationImpl(channel, label, nature, description, true);
  }

  @Override
  public SensorInformation createSensorInformation(String channel, String label, SensorNature nature, String description, boolean silenceable) {
    return new SensorInformationImpl(channel, label, nature, description, silenceable);
  }

  /**
   * Busca una instancia de información.Si no existe, crea una minimalista
 (placeholder).Si el sensor se registra después con información rica, se
 actualizará en el cache.
   * @param channel
   * @param nature
   * @return 
   */
  public synchronized SensorInformation getOrPlaceholderInfo(String channel, SensorNature nature) {
    return knownSensors.computeIfAbsent(channel, c
            -> new SensorInformationImpl(c, c, nature, "Rehidratado desde persistencia", true)
    );
  }

  @Override
  public synchronized void registerSensor(SensorInformation info) {
    if (registeredSensors.containsKey(info.getChannel())) {
      return;
    }
    knownSensors.put(info.getChannel(), info);

    String channel = info.getChannel();

    // Usamos remove() para limpiar el mapa temporal a medida que los sensores se activan
    SensorStatistics stats = rehydratedStats.remove(channel);

    if (stats == null) {
      stats = new SensorStatisticsImpl();
      LOGGER.debug("Sensor '{}' registrado como nuevo (sin antecedentes).", channel);
    } else {
      LOGGER.info("Sensor '{}' rehidratado con éxito. Recuperando estado (Silenced: {}, Eventos previos: {})",
              channel, stats.isSilenced(), stats.getTotalEventsActive());
    }
    // Le pasamos el objeto 'stats' (ya sea nuevo o recuperado)
    SensorData data = sensorDataFactory.get(info.getNature()).apply(info, stats);
    registeredSensors.put(channel, data);
  }

  @Override
  public synchronized void putEvent(String channel, String text, String priority, String status, LocalDateTime timestamp) {
    this.putEvent(channel, text, priority, status, timestamp, null);
  }

  @Override
  public void putEvent(String channel, String text, String priority, String status, LocalDateTime timestamp, SensorEventCallback callback) {
    synchronized (sensorLock) {
      if (!running) {
        return;
      }

      SensorData data = registeredSensors.get(channel);
      if (data == null) {
        return;
      }

      SensorStatistics stats = data.getStatistics();
      if (stats.isSilenced()) {
        stats.incrementSilencedEvents();
        stats.updateLastEventTimestamp(timestamp);
        return;
      }

      stats.incrementActiveEvents();
      stats.updateLastEventTimestamp(timestamp);

      // Procesamos el evento en el buffer
      if (currentSensor != null && (!currentSensor.getSensorInformation().getChannel().equals(channel)
              || data.getSensorInformation().getNature() == SensorNature.DISCRETE)) {
        flushCurrentSensor();
      }

      currentSensor = data;
      data.process(text, priority, status, timestamp, callback);

      if (data.getSensorInformation().getNature() == SensorNature.STATE) {
        SensorEvent event = data.flushEvent();
        stateMap.put(channel, (ConsumableSensorEvent) event);
      }

      // Avisamos que hay nuevo evento disponible
      sensorLock.notifyAll();
    }
  }

  public boolean isEmpty() {
    synchronized (sensorLock) {
      if (!running) {
        return true;
      }
      return deliveryQueue.isEmpty() && stateMap.isEmpty()
              && (currentSensor == null || !currentSensor.hasPendingEvent());
    }
  }

  public synchronized ConsumableSensorEvent peekEvent() {
    if (!running) {
      return null;
    }
    flushCurrentSensor(); //FIXME: esto no es correcto.
    return findOldestCandidate(false);
  }

  public ConsumableSensorEvent getEvent() {
    synchronized (sensorLock) {
      // Esperamos mientras el servicio corra Y el cuerpo esté "vacio" de estímulos
      while (running && isEmpty()) {
        try {
          sensorLock.wait();
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          return null;
        }
      }

      // Si despertamos porque el servicio se detuvo, salimos
      if (!running) {
        return null;
      }

      // Al haber despertado y seguir activos, procesamos la señal
      flushCurrentSensor();
      ConsumableSensorEvent event = findOldestCandidate(true);

      if (event != null) {
        event.setDeliveryTimestamp(LocalDateTime.now());
        SensorData data = registeredSensors.get(event.getChannel());
        if (data != null) {
          data.getStatistics().updateLastDeliveryTimestamp(event.getDeliveryTimestamp());
        }
      }
      return event;
    }
  }

  private void flushCurrentSensor() {
    if (currentSensor != null && currentSensor.hasPendingEvent()) {
      deliveryQueue.add(currentSensor.flushEvent());
      currentSensor = null;
    }
  }

  /**
   * Lógica de Fusión Maestra: Compara la cima de la cola cronológica con el
   * mapa de estados.
   */
  private ConsumableSensorEvent findOldestCandidate(boolean remove) {
    SensorEvent oldestInQueue = deliveryQueue.peek();
    SensorEventState oldestInMap = findOldestStateInMap();

    if (oldestInQueue == null && oldestInMap == null) {
      return null;
    }

    // Si solo hay uno de los dos, devolvemos ese
    if (oldestInQueue == null) {
      return remove ? stateMap.remove(oldestInMap.getChannel()) : (ConsumableSensorEvent) oldestInMap;
    }
    if (oldestInMap == null) {
      return remove ? (ConsumableSensorEvent) deliveryQueue.poll() : (ConsumableSensorEvent) oldestInQueue;
    }

    // Si hay ambos, comparamos marcas de tiempo
    if (oldestInQueue.getStartTimestamp().isBefore(oldestInMap.getStartTimestamp())) { // TODO: Comprobar si no he puesto la comparacion al reves
      return remove ? (ConsumableSensorEvent) deliveryQueue.poll() : (ConsumableSensorEvent) oldestInQueue;
    } else {
      return remove ? stateMap.remove(oldestInMap.getChannel()) : (ConsumableSensorEvent) oldestInMap;
    }
  }

  private SensorEventState findOldestStateInMap() {
    return (SensorEventState) stateMap.values().stream()
            .min(Comparator.comparing(SensorEvent::getStartTimestamp))
            .orElse((ConsumableSensorEvent) null);
  }

  @Override
  public List<SensorInformation> getAllRegisteredSensors() {
    return new ArrayList<>(registeredSensors.values().stream().map(SensorData::getSensorInformation).toList());
  }

  @Override
  public SensorStatistics getSensorStatistics(String channel) {
    SensorData data = registeredSensors.get(channel);
    return data != null ? data.getStatistics() : null;
  }

  @Override
  public synchronized void setSilenced(String channel, boolean silenced) {
    SensorData data = registeredSensors.get(channel);
    if (data != null && data.getSensorInformation().isSilenceable()) {
      data.getStatistics().setSilenced(silenced);

      // Si acabamos de silenciar el canal y es el 'currentSensor', 
      // forzamos un flush para no perder lo que tuviera en el buffer antes de silenciarse.
      if (silenced && currentSensor != null && currentSensor.getSensorInformation().getChannel().equals(channel)) {
        flushCurrentSensor();
      }
    }
  }

  @Override
  public boolean isSilenced(String channel) {
    SensorData data = registeredSensors.get(channel);
    return data != null && data.getStatistics().isSilenced();
  }

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public void start() {
    Path persistencePath = agent.getPaths().getDataFolder().resolve("sensors.json");

    if (Files.exists(persistencePath)) {
      try {
        String json = Files.readString(persistencePath, StandardCharsets.UTF_8);
        Gson gson = createPersistentGson();

        // 1. Parseamos el JSON como un árbol de elementos (JsonObject)
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();

        // 2. PASO CRÍTICO: Rehidratar PRIMERO el infoCache.
        // Esto asegura que cuando el SensorEventGsonAdapter sea invocado por GSON 
        // para procesar la cola, ya tenga las identidades de los sensores cargadas.
        if (root.has("infos")) {
          Type infoMapType = new TypeToken<Map<String, SensorInformation>>() {
          }.getType();
          Map<String, SensorInformation> savedInfos = gson.fromJson(root.get("infos"), infoMapType);
          if (savedInfos != null) {
            this.knownSensors.putAll(savedInfos);
          }
        }

        // 3. Una vez preparado el caché de identidades, deserializamos el Memento completo
        SensorsMemento memento = gson.fromJson(root, SensorsMemento.class);

        if (memento != null) {
          // Restauramos la cola de percepciones pendientes
          if (memento.deliveryQueue != null) {
            deliveryQueue.addAll(memento.deliveryQueue);
          }

          // Restauramos los últimos estados conocidos
          if (memento.stateMap != null) {
            stateMap.putAll(memento.stateMap);
          }

          // Restauramos el historial de estadísticas
          if (memento.statisticsMap != null) {
            rehydratedStats.putAll(memento.statisticsMap);
          }

          LOGGER.info("Estado sensorial rehidratado: {} eventos en cola, {} estados, {} estadísticas de sensores.",
                  deliveryQueue.size(), stateMap.size(), rehydratedStats.size());
        }

        // Opcional: Limpieza preventiva del archivo
        // Files.deleteIfExists(persistencePath);
      } catch (Exception e) {
        LOGGER.warn("Error crítico rehidratando el estado sensorial. Se iniciará vacío.", e);
      }
    }

    // El servicio ya es plenamente consciente de su pasado y está listo para recibir el presente
    this.running = true;
  }

  @Override
  public boolean canStart() {
    return true;
  }

  @Override
  public boolean isRunning() {
    return this.running;
  }

  @Override
  public Agent.ModelParameters getModelParameters(String name) {
    return null;
  }

  @Override
  public List<AgentTool> getTools() {
    AgentTool[] tools = new AgentTool[]{
      new PoolEventTool(this.agent),
      new SensorStartTool(this.agent),
      new SensorStopTool(this.agent),
      new SensorStatusTool(this.agent)
    };
    return Arrays.asList(tools);
  }

  @Override
  public AgentServiceFactory getFactory() {
    return this.factory;
  }

  @Override
  public synchronized void stop() {
    if (!running) {
      return;
    }
    synchronized (sensorLock) {
      this.running = false;

      flushCurrentSensor();

      SensorsMemento memento = new SensorsMemento();

      memento.infos = new HashMap<>(this.knownSensors);

      // Capturamos la cola de entrega (convertimos a List para el memento)
      memento.deliveryQueue = new ArrayList<>();
      deliveryQueue.drainTo(memento.deliveryQueue);

      // Capturamos el mapa de estados actuales
      memento.stateMap = new HashMap<>(this.stateMap);

      // Recolectamos las estadísticas de todos los sensores registrados
      memento.statisticsMap = new HashMap<>();
      for (Map.Entry<String, SensorData> entry : registeredSensors.entrySet()) {
        memento.statisticsMap.put(entry.getKey(), entry.getValue().getStatistics());
      }

      Path persistencePath = agent.getPaths().getDataFolder().resolve("sensors.json");
      try {
        Files.createDirectories(persistencePath.getParent());
        String json = createPersistentGson().toJson(memento);
        Files.writeString(persistencePath, json, StandardCharsets.UTF_8);

        LOGGER.info("Estado sensorial guardado exitosamente en: " + persistencePath);
      } catch (IOException e) {
        LOGGER.error("Error crítico persistiendo el estado sensorial", e);
      }
      sensorLock.notifyAll(); // Despierta al getEvent() para que vea que running es false y salga
    }
  }

  private Gson createPersistentGson() {
    return new GsonBuilder()
            .registerTypeHierarchyAdapter(SensorEvent.class, new SensorEventGsonAdapter(this))
            .registerTypeHierarchyAdapter(SensorStatistics.class, new SensorStatisticsGsonAdapter())
            .registerTypeHierarchyAdapter(SensorInformation.class, new SensorInformationGsonAdapter())
            .setPrettyPrinting()            
            .create();
  }
  
  public ConsumableSensorEvent createSensorEvent(String channel, String text, String priority, String status, LocalDateTime timestamp, SensorsService.SensorEventCallback callback) {
    AbstractSensorData sensor = (AbstractSensorData) this.registeredSensors.get(channel);
    return sensor.createSensorEvent(text, priority, status, timestamp, callback);
  }
}
