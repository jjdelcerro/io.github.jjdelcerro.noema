# Plan de Implementación: Sistema Sensorial de Agentes (SNA/SNC)

## 1. Introducción

Este plan detalla los pasos para implementar el Sistema Sensorial de Agentes, siguiendo el diseño propuesto en "diseno-de-arquitectura-sistema-sensorial-de-agentes-v2.md". El objetivo es crear todas las nuevas clases e interfaces dentro de los paquetes designados, postergando la integración con componentes existentes para una fase posterior.

## 2. Creación de la Estructura de Paquetes

Se crearán los siguientes directorios para albergar las nuevas clases e interfaces:

*   `src/main/java/io/github/jjdelcerro/noema/lib/services/sensors`
*   `src/main/java/io.github.jjdelcerro.noema/lib/impl/services/sensors`

## 3. Implementación del Subsistema Sensorial

Todas las clases e interfaces listadas a continuación se crearán exclusivamente en los paquetes definidos en el punto 2.

### 3.1. Definiciones Base: Identidad y Naturaleza de los Sentidos

#### A. `SensorNature` (Enum)
*   **Ubicación:** `io.github.jjdelcerro.noema.lib.services.sensors`
*   **Descripción:** Enumerado que define las estrategias de procesamiento de señal.
*   **Miembros:** `DISCRETE`, `MERGEABLE`, `AGGREGATABLE`, `STATE`.

#### B. `SensorInformation` (Interface)
*   **Ubicación:** `io.github.jjdelcerro.noema.lib.services.sensors`
*   **Descripción:** Contrato que define la identidad y metadatos de un sensor.
*   **Métodos:**
    *   `String getChannel()`: Identificador técnico único.
    *   `String getLabel()`: Nombre legible por humanos.
    *   `String getDescription()`: Explicación semántica.
    *   `SensorNature getNature()`: Naturaleza del sensor.

#### C. `SensorInformationImpl` (Clase)
*   **Ubicación:** `io.github.jjdelcerro.noema.lib.impl.services.sensors`
*   **Descripción:** Implementación de `SensorInformation`. Clase POJO que almacena los atributos de un sensor.
*   **Atributos:** `channel`, `label`, `description`, `nature`.
*   **Constructor:** Para inicializar todos los atributos.

### 3.2. Modelo de Eventos: `SensorEvent`

#### A. `SensorEvent` (Interface)
*   **Ubicación:** `io.github.jjdelcerro.noema.lib.services.sensors`
*   **Descripción:** Interfaz base para todos los eventos sensoriales.
*   **Métodos:**
    *   `String getChannel()`: Canal de origen.
    *   `String getContents()`: Contenido del evento (texto o JSON).
    *   `int getPriority()`: Prioridad del evento.
    *   `String getStatus()`: Estado del evento.
    *   `long getStartTimestamp()`: Marca de tiempo del primer estímulo.
    *   `long getEndTimestamp()`: Marca de tiempo del último estímulo (o del único estímulo).
    *   `long getDeliveryTimestamp()`: Marca de tiempo de entrega al SNC.

#### B. `AbstractSensorEvent` (Clase Abstracta)
*   **Ubicación:** `io.github.jjdelcerro.noema.lib.impl.services.sensors`
*   **Descripción:** Clase base común para las implementaciones de `SensorEvent`, gestionando los campos compartidos.
*   **Atributos protegidos:** `channel`, `priority`, `status`, `startTimestamp`, `endTimestamp`, `deliveryTimestamp`.
*   **Constructor:** Para inicializar estos atributos.
*   **Implementación de getters** de `SensorEvent` para los atributos protegidos.

#### C. Interfaces de Eventos Especializados
*   **Ubicación:** `io.github.jjdelcerro.noema.lib.services.sensors`
*   **`SensorEventDiscrete`**: Extiende `SensorEvent`.
*   **`SensorEventMergeable`**: Extiende `SensorEvent`.
*   **`SensorEventAggregate`**: Extiende `SensorEvent`, añade `long getCount()`.
*   **`SensorEventState`**: Extiende `SensorEvent`.

#### D. Implementaciones de Eventos Especializados
*   **Ubicación:** `io.github.jjdelcerro.noema.lib.impl.services.sensors.nature`
*   Todas extenderán `AbstractSensorEvent` y implementarán la interfaz correspondiente.
*   **`discrete.SensorEventDiscreteImpl`**:
    *   **Atributos:** `String contents`.
    *   **Constructor:** Recibe `channel`, `contents`, `priority`, `status`, `timestamp`. Setea `startTimestamp` y `endTimestamp` al mismo `timestamp`.
*   **`mergeable.SensorEventMergeableImpl`**:
    *   **Atributos:** `StringBuilder contentsBuilder`.
    *   **Métodos específicos:**
        *   `void append(String text, long timestamp)`: Concatena texto y actualiza `endTimestamp`.
        *   `String getContents()`: Devuelve `contentsBuilder.toString()`.
    *   **Constructor:** Recibe `channel`, `firstText`, `priority`, `status`, `timestamp`. Inicializa `contentsBuilder` y `startTimestamp`, `endTimestamp`.
*   **`aggregate.SensorEventAggregateImpl`**:
    *   **Atributos:** `long count`.
    *   **Métodos específicos:**
        *   `void increment()`: Incrementa `count`.
        *   `long getCount()`: Devuelve `count`.
    *   **Constructor:** Recibe `channel`, `priority`, `status`, `timestamp`. Inicializa `count` a 1 y `startTimestamp`, `endTimestamp`.
*   **`state.SensorEventStateImpl`**:
    *   **Atributos:** `String contents`.
    *   **Constructor:** Recibe `channel`, `contents`, `priority`, `status`, `timestamp`. Setea `startTimestamp` y `endTimestamp` al mismo `timestamp`.

### 3.3. Metacognición Sensorial: `SensorStatistics`

#### A. `SensorStatistics` (Interface)
*   **Ubicación:** `io.github.jjdelcerro.noema.lib.services.sensors`
*   **Descripción:** Define los métodos para consultar la salud y el rendimiento de un sensor.
*   **Métodos:**
    *   `long getTotalEventsActive()`: Eventos procesados mientras activo.
    *   `long getTotalEventsMuted()`: Eventos ignorados mientras silenciado.
    *   `long getLastEventTimestamp()`: Marca de tiempo del último evento recibido.
    *   `long getLastDeliveryTimestamp()`: Marca de tiempo de la última entrega.
    *   `double getAverageFrequency(long period)`: Frecuencia media en un periodo.
    *   `boolean isMuted()`: Si el sensor está silenciado.
    *   `void setMuted(boolean muted)`: Establece el estado de silencio.
    *   `void incrementActiveEvents()`: Incrementa el contador de eventos activos.
    *   `void incrementMutedEvents()`: Incrementa el contador de eventos silenciados.
    *   `void updateLastEventTimestamp(long timestamp)`: Actualiza la marca de tiempo del último evento.
    *   `void updateLastDeliveryTimestamp(long timestamp)`: Actualiza la marca de tiempo de la última entrega.

#### B. `SensorStatisticsImpl` (Clase)
*   **Ubicación:** `io.github.jjdelcerro.noema.lib.impl.services.sensors`
*   **Descripción:** Implementación de `SensorStatistics`. Gestiona acumuladores y ventanas temporales de actividad.
*   **Atributos:** `totalEventsActive`, `totalEventsMuted`, `lastEventTimestamp`, `lastDeliveryTimestamp`, `muted` (boolean).
*   **Lógica de Frecuencia:** Implementación de `getAverageFrequency` (puede ser simplificada inicialmente o usar un enfoque de ventanas deslizantes como se describe en el documento).
*   **Constructor:** Para inicializar los contadores a 0.

### 3.4. Procesamiento Autónomo: `SensorData`

#### A. `SensorData` (Interface)
*   **Ubicación:** `io.github.jjdelcerro.noema.lib.services.sensors`
*   **Descripción:** Lógica interna de procesamiento para un tipo de sensor.
*   **Métodos:**
    *   `void process(String text, int priority, String status, long timestamp)`: Método de entrada para el estímulo.
    *   `boolean isMyEvent(String channel)`: Comprueba si el evento pertenece a este sensor.
    *   `SensorEvent flushEvent()`: Emite el evento acumulado y reinicia el buffer.
    *   `SensorStatistics getStatistics()`: Expone las métricas del sensor.
    *   `SensorInformation getSensorInformation()`: Devuelve la información del sensor.
    *   `boolean hasPendingEvent()`: Indica si hay un evento en el buffer.

#### B. Implementaciones de `SensorData`
*   **Ubicación:** `io.github.jjdelcerro.noema.lib.impl.services.sensors.nature`
*   Todas tendrán un atributo `SensorInformation` y `SensorStatistics`.
*   **`discrete.DiscreteSensorData`**:
    *   **Atributos:** `SensorEventDiscreteImpl currentEvent`.
    *   **`process`**: Crea un `SensorEventDiscreteImpl` y lo asigna a `currentEvent`.
    *   **`flushEvent`**: Devuelve `currentEvent` y lo setea a `null`.
    *   **`hasPendingEvent`**: `return currentEvent != null`.
*   **`mergeable.MergeableSensorData`**:
    *   **Atributos:** `SensorEventMergeableImpl currentEvent`.
    *   **`process`**: Si `currentEvent` es `null`, crea uno nuevo. Si no, llama a `currentEvent.append()`.
    *   **`flushEvent`**: Devuelve `currentEvent` y lo setea a `null`.
    *   **`hasPendingEvent`**: `return currentEvent != null`.
*   **`aggregate.AggregateSensorData`**:
    *   **Atributos:** `long count`.
    *   **`process`**: Si `currentEvent` es `null`, crea uno nuevo. Si no, llama a `currentEvent.increment()`.
    *   **`flushEvent`**: Devuelve `currentEvent` y lo setea a `null`.
    *   **`hasPendingEvent`**: `return currentEvent != null`.
*   **`state.StateSensorData`**:
    *   **Atributos:** `SensorEventStateImpl currentEvent`.
    *   **`process`**: Crea un nuevo `SensorEventStateImpl` y lo asigna a `currentEvent`, reemplazando el anterior.
    *   **`flushEvent`**: Devuelve `currentEvent` y lo setea a `null`.
    *   **`hasPendingEvent`**: `return currentEvent != null`.

### 3.5. Orquestación del Flujo: `SensorsService`

#### A. `SensorsService` (Interface)
*   **Ubicación:** `io.github.jjdelcerro.noema.lib.services.sensors`
*   **Descripción:** Punto de entrada del sistema sensorial.
*   **Métodos:**
    *   `SensorInformation createSensorInformation(String channel, String label, SensorNature nature, String description)`: Factoría para crear información de sensor.
    *   `void registerSensor(SensorInformation info)`: Registra un sensor en el servicio.
    *   `void putEvent(String channel, String text, int priority, String status, long timestamp)`: Ingesta un evento.
    *   `List<SensorEvent> getPendingEvents()`: Devuelve los eventos listos para ser entregados al SNC.
    *   `List<SensorInformation> getAllRegisteredSensors()`: Lista todos los sensores registrados.
    *   `SensorStatistics getSensorStatistics(String channel)`: Devuelve las estadísticas de un sensor.

#### B. `SensorsServiceImpl` (Clase)
*   **Ubicación:** `io.github.jjdelcerro.noema.lib.impl.services.sensors`
*   **Descripción:** Implementación de `SensorsService`.
*   **Atributos:**
    *   `Map<String, SensorData> registeredSensors`: Sensores registrados, indexados por `channel`.
    *   `BlockingQueue<SensorEvent> deliveryQueue`: Cola concurrente para eventos listos.
    *   `Map<String, SensorEventState> stateMap`: Mapa para eventos de estado, indexados por `channel`.
    *   `SensorData currentSensor`: Puntero al sensor actualmente acumulando eventos.
    *   `Map<SensorNature, Supplier<SensorData>> sensorDataFactory`: Mapa de factoría para `SensorData`.
*   **Constructor:** Inicializa `sensorDataFactory` con las implementaciones correspondientes a cada `SensorNature`.
*   **`createSensorInformation`**: Crea y devuelve una instancia de `SensorInformationImpl`.
*   **`registerSensor`**: Añade el sensor a `registeredSensors`, creando la instancia de `SensorData` apropiada usando `sensorDataFactory`.
*   **`putEvent` (synchronized):**
    1.  Localiza `SensorData` por `channel`.
    2.  Actualiza `SensorStatistics` (`lastEventTimestamp`, `totalEventsActive`/`totalEventsMuted`).
    3.  Si el sensor está silenciado, finaliza.
    4.  **Mecanismo de "Cierre por Interrupción"**: Si el `channel` no es el `currentSensor.getSensorInformation().getChannel()` (o `currentSensor` es `null`) O si el evento es `DISCRETE`:
        *   Si `currentSensor` no es `null` y `currentSensor.hasPendingEvent()`, llama a `currentSensor.flushEvent()` y añade el resultado a `deliveryQueue`.
        *   Setea `currentSensor` a `registeredSensors.get(channel)`.
    5.  Llama a `currentSensor.process(text, priority, status, timestamp)`.
    6.  Si la naturaleza es `STATE`, actualiza `stateMap`.
*   **`getPendingEvents` (synchronized):**
    1.  Fuerza un `flush()` al `currentSensor` si tiene eventos pendientes.
    2.  Realiza la "Fusión Maestra Cronológica" de `deliveryQueue` y `stateMap`, vaciando ambos y devolviendo la lista ordenada de `SensorEvent`.
    3.  Actualiza `lastDeliveryTimestamp` para cada sensor.
*   **`getAllRegisteredSensors` y `getSensorStatistics`**: Simples recuperaciones de los mapas internos.

### 3.6. Herramientas del Agente

#### A. `PoolEventTool` (Clase)
*   **Ubicación:** `io.github.jjdelcerro.noema.lib.impl.services.sensors.tools` (Implementación de `AgentTool`)
*   **Descripción:** Herramienta que el LLM invoca para consultar eventos pendientes.
*   **Constructor:** Recibe una instancia de `SensorsService`.
*   **Método `execute()`:** Llama a `sensorsService.getPendingEvents()` y devuelve el resultado en formato JSON (serializando la lista de `SensorEvent`).

#### B. `SensorStatusTool` (Clase)
*   **Ubicación:** `io.github.jjdelcerro.noema.lib.impl.services.sensors.tools` (Implementación de `AgentTool`)
*   **Descripción:** Herramienta para que el LLM obtenga el estado y las estadísticas de los sensores.
*   **Constructor:** Recibe una instancia de `SensorsService`.
*   **Método `execute()`:** Llama a `sensorsService.getAllRegisteredSensors()` y `sensorsService.getSensorStatistics()` para cada uno, y devuelve un informe consolidado en formato JSON o texto legible.

## 4. Generación de documentacion detallada de las tareas pendientes 

LLegado a este punto generaras un documento detallado con las tareas pendientes, especialmente las que haya que realizar para la integracion del nuevo modelo de sensores en el resto del proyecto.

Esta documentacion la dejaras en un fichero en "tmp/tareas-pendientes.md".

## 5. Modificaciones de Clases Existentes (Fase de Integración Posterior)

**NO IMPLEMENTES NADA DE ESTA SECCION SIN INSTRUCCIONES EXPLICITAS PARA ELLO**

Las siguientes modificaciones son necesarias para integrar el subsistema sensorial en el agente y habilitar su funcionalidad, pero se abordarán en una fase de integración una vez que el subsistema sensorial esté completamente implementado y testeado de forma aislada.

### 5.1. `AgentService` (Interface)
*   **Modificación:** Añadir el método `void stop()`.

### 5.2. `ConversationService`
*   **Modificación:** Inyectar una instancia de `SensorsService`.
*   **Modificación:** Implementar el "Bucle de Revisión Sensorial" (`checkSensoryMailbox()`) que se ejecutaría al final de cada turno de conversación.
*   **Modificación:** Modificar la lógica de manejo de entrada para inyectar artificialmente la llamada a `pool_event()` y su resultado (`ToolExecutionResultMessage`) cuando `SensorsService` tenga eventos pendientes y el agente esté en reposo (`idle`).
*   **Modificación:** Ajustar el bucle principal para manejar el estado `isBusy` y la inyección de eventos.

### 5.3. Clases de Persistencia (`AgentSettings`, etc.)
*   **Modificación:** Añadir lógica para que el `SensorsService` pueda persistir su estado (sensores silenciados, estadísticas, etc.) en `settings.json` al llamar a su `stop()` y restaurarlo al inicializarse. Esto podría requerir añadir nuevas claves o secciones al `settings.json` y actualizar las clases que manejan la lectura/escritura de estas configuraciones.

### 5.4. Clases de Inicialización (`BootUtils`, `AgentManagerImpl`, etc.)
*   **Modificación:** Crear y registrar la instancia de `SensorsService` en el `AgentLocator`/`AgentManager`.
*   **Modificación:** Asegurarse de que el `stop()` del `SensorsService` se invoca durante el apagado del agente.
*   **Modificación:** Realizar el registro inicial de los `Supplier<SensorData>` en el `SensorsService`.

## 6. Pruebas

De momento, no implementaremos pruebas unitarias, lo pospondremos para otro momento.

---
