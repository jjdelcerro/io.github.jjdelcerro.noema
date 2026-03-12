
# Especificación técnica de la implementación de SensorsService

<nota>
En ningún punto del documento hay un diagrama de flujo o pseudocódigo del camino feliz completo, desde que un estímulo externo entra al sistema hasta que el LLM lo recibe. La arquitectura está bien descrita por componentes, pero un lector nuevo tendría que reconstruir mentalmente ese flujo leyendo todo el documento. Una sección introductoria corta que trace ese camino de principio a fin —aunque sea en prosa— haría el documento mucho más accesible antes de entrar en los detalles de cada componente.
</nota>

### Punto 0: Arquitectura del Sistema Sensorial (Mapa de Componentes)

### 0.1. El Orquestador (`SensorsServiceImpl`)

El `SensorsServiceImpl` es el componente central y único punto de entrada (*facade*) para la gestión sensorial del agente. Su responsabilidad técnica es actuar como el **centro de control neurálgico** que desacopla la periferia (los sensores físicos o virtuales que emiten señales) del sistema de razonamiento (el `ReasoningService`).

Como orquestador, el servicio asume cuatro funciones críticas:

*   **Gestión del Registro de Sensores**: Mantiene un mapa interno (`registeredSensors`) que actúa como catálogo de todas las fuentes de datos activas. Es el responsable de validar que cualquier señal entrante provenga de un canal autorizado y de dirigirla al procesador (`SensorData`) adecuado, basado en la naturaleza del sensor (`SensorNature`).
*   **Arbitraje de Concurrencia**: Al ser el punto donde convergen múltiples hilos de ejecución (hilos de escucha de Telegram, listeners de Email, cronjobs de sistema), el servicio garantiza la integridad de los datos mediante la gestión del `sensorLock`. Este monitor central es el que impide que la llegada simultánea de eventos distintos corrompa el estado interno o genere inconsistencias en los buffers.
*   **Gestión del Ciclo de Vida y Persistencia**: Controla el estado operativo del servicio (`running`). Es el único componente que orquestará la serialización y deserialización del estado sensorial completo mediante el `SensorsMemento`, asegurando que, al reiniciar el agente, la configuración de los sensores (qué está activo, qué está silenciado) y las estadísticas históricas se restauren sin pérdida de fidelidad.
*   **Enrutamiento y Entrega**: Actúa como el administrador de las dos estructuras de datos de salida: la `deliveryQueue` (FIFO para eventos secuenciales) y el `stateMap` (para el estado actual de los sensores volátiles). Su lógica interna de `getEvent()` se encarga de realizar la **Fusión Maestra** necesaria para presentar al consumidor (el LLM) una realidad unificada, cronológica y, sobre todo, libre de ruido técnico.

En resumen, `SensorsServiceImpl` no procesa los datos en sí mismos —esa es responsabilidad de las implementaciones de `SensorData`—, sino que **coordina la orquestación temporal y lógica** para que el flujo de eventos que llega al sistema sea predecible y coherente.

### 0.2. La Identidad (`SensorInformation`)

La interfaz `SensorInformation` define el **contrato de identidad** de cada canal sensorial dentro del sistema. Actúa como el descriptor de metadatos que permite al orquestador tratar a cada fuente de datos no como un simple flujo de bytes, sino como una entidad con propósito, comportamiento y capacidades definidas. Su propósito principal es desacoplar el origen de la señal (el "qué se mide") del algoritmo de procesamiento aplicado (la "naturaleza del sentido").

Un objeto `SensorInformation` consta de cuatro propiedades inmutables que configuran la "ficha técnica" del sensor:

*   **Identificador del Canal (`channel`)**: Es la clave primaria y única dentro del mapa de sensores. Cualquier flujo de datos que desee ser procesado por el SNA debe estar etiquetado con este identificador. Es el *handle* mediante el cual el `SensorsService` localiza el procesador y las estadísticas asociadas.
*   **Etiqueta Legible (`label`)**: Representa el nombre amigable del sentido. Aunque no es funcionalmente relevante para el procesamiento de datos, es la etiqueta que se presenta en los paneles de administración y es el identificador humano en las interfaces de usuario (tanto CLI como GUI).
*   **Descripción Semántica (`description`)**: Es el metadato descriptivo que se inyecta en el *prompt* del sistema cuando el LLM requiere contexto sobre el entorno. Este campo no influye en la ejecución del código, pero es la pieza clave que permite al agente "entender" qué tipo de realidad está observando (ej: "Sensor de errores de red del clúster" vs "Feed de mensajes de usuario").
*   **Naturaleza del Sensor (`nature`)**:
    El campo `nature` es, técnicamente, la instrucción de configuración más crítica para el orquestador. Define el **contrato de comportamiento** del sensor ante el Sistema Nervioso Autónomo. Esta propiedad no es solo informativa; determina qué estrategia de procesamiento (`SensorData`) se asignará y qué tipo de paquete de datos (`SensorEvent`) se generará. Las naturalezas soportadas son:

    *   **`DISCRETE`**: Estímulos atómicos e independientes. El orquestador los trata como eventos únicos que deben entregarse sin agregación ni fusión, garantizando su entrega inmediata y sin pérdida de detalle.
    *   **`MERGEABLE`**: Estímulos secuenciales (narrativos). Instruye al orquestador a concatenar los mensajes entrantes en un buffer temporal, preservando la cronología y entregándolos como un bloque semántico unificado.
    *   **`AGGREGATABLE`**: Estímulos de alta frecuencia o redundantes. Ordena al orquestador aplicar una estrategia de contabilidad: solo el volumen y la frecuencia importan, por lo que el sistema contabiliza las ocurrencias en lugar de almacenar cada mensaje.
    *   **`STATE`**: Estímulos que representan una condición. Instruye al orquestador a mantener únicamente el valor más reciente del canal, eliminando cualquier rastro de estados previos que no hayan sido entregados aún.
    *   **`USER`**: Estímulos provenientes de la interacción directa. Instruye al sistema para tratar la entrada como una instrucción consciente y prioritaria, inyectándola directamente en el flujo de conversación sin pasar por las capas de digestión estadística o agregación.

    Esta clasificación transforma un objeto de información estática en una **instrucción de comportamiento** operativa para el orquestador.


Adicionalmente, esta identidad incluye el flag `silenceable`, que determina si el canal es susceptible de ser filtrado por la voluntad del agente o si, por el contrario, su reporte es ininterrumpible.

En la jerarquía del sistema, `SensorInformation` es el **primer eslabón** en el ciclo de vida de un sentido. Sin una instancia registrada de esta interfaz, el `SensorsService` rechaza cualquier entrada de datos, asegurando que el sistema solo procese percepciones que hayan sido previamente validadas y categorizadas. Es, en esencia, la configuración que permite al agente navegar por su propia estructura sensorial de manera estructurada y consciente.

### 0.3. La Lógica de Procesamiento (`SensorData`)

La interfaz `SensorData` define el contrato para el **motor de procesamiento** que digiere los estímulos del entorno antes de su entrega al SNC. Mientras que `SensorInformation` es la etiqueta que identifica el sensor, `SensorData` es el "procesador especializado" que sabe qué hacer con los datos recibidos. La arquitectura delega en estas implementaciones la responsabilidad exclusiva de aplicar la estrategia de procesamiento (digestión) adecuada a cada señal.

*   **Implementaciones por Naturaleza Sensorial**: El núcleo de la lógica reside en el hecho de que **a cada `SensorNature` le corresponde una implementación concreta de `SensorData`**. 

    *   `DiscreteSensorData`
    *   `MergeableSensorData`
    *   `AggregateSensorData`
    *   `StateSensorData`
    *   `UserSensorData` 
    
    Son las clases que contienen la lógica específica de transformación. Este diseño asegura que el `SensorsService` sea **abierto a la extensión pero cerrado a la modificación**: si el sistema requiere un nuevo comportamiento sensorial, basta con crear una nueva implementación de `SensorData` y registrarla en la factoría, sin necesidad de alterar el código del orquestador.

*   **Gestión del Estado Vivo (Buffers de Trabajo)**: Cada implementación de `SensorData` es un contenedor de estado. Mantiene un **Buffer de Trabajo** interno —cuyo tipo de dato varía según la naturaleza—, que persiste mientras el sensor está activo. Por ejemplo:

    *   El `MergeableSensorData` encapsula un `StringBuilder` para la concatenación narrativa.
    *   El `AggregateSensorData` gestiona un contador de tipo `long`.
    *   El `StateSensorData` mantiene una referencia al objeto del último estado válido.
    *   Esta encapsulación asegura que el "ruido" de la gestión de memoria (acumular, contar, limpiar buffers) esté totalmente oculto al resto del sistema.

*   **Motor de Reglas de Ingesta (`process`)**: El método `process()` es la implementación de la regla de negocio para ese sensor. Es aquí donde ocurre la magia de la digestión: la clase concreta decide si el estímulo entrante debe generar un nuevo evento, si debe ser absorbido por el buffer actual, o si debe disparar un *Flush* inmediato. Este método es la "fisiología" que traduce la entrada cruda en una acción dentro del buffer.

*   **Ciclo de Vida del Evento (`flushEvent`)**: Cada clase concreta conoce la estructura de su `SensorEvent` correspondiente. Cuando el orquestador solicita un `flush`, el procesador no solo devuelve los datos, sino que **instancia el tipo correcto de evento** (ej. un `SensorEventAggregateImpl` si es un sensor de conteo). Esto garantiza que el SNC reciba siempre un objeto tipado que ya conoce cómo debe ser interpretado.

*   **Autogestión de Estadísticas**: Cada procesador mantiene una relación 1:1 con su objeto `SensorStatistics`. Esta vinculación estrecha permite que, en el mismo instante en que el procesador digiere un estímulo, se actualicen las métricas de salud, asegurando que la introspección sensorial sea precisa y no un proceso desfasado.



### 0.4. La Unidad de Información (`SensorEvent`)

Si `SensorData` es el procesador que digiere la señal, el `SensorEvent` es el **resultado final**, el paquete estandarizado de información que el Sistema Nervioso Autónomo (SNA) entrega al Sistema Nervioso Central (SNC) para su razonamiento. Su diseño es crucial para que el agente pueda razonar sobre estímulos externos sin perder la trazabilidad temporal ni la fidelidad semántica.

El `SensorEvent` actúa como un **contrato de comunicación** que garantiza que cualquier dato, independientemente de su origen, tenga un formato predecible para el modelo de lenguaje. Sus características técnicas principales son:

*   **Identidad y Trazabilidad**: Cada evento lleva consigo su origen (`SensorInformation`), lo que permite al agente saber con precisión qué parte de su sistema sensorial ha generado la señal. Además, el sistema garantiza la **trazabilidad temporal** mediante tres marcas de tiempo críticas:

    *   `startTimestamp`: El momento exacto en que se inició el primer estímulo de la ráfaga.
    *   `endTimestamp`: El momento del último estímulo antes del sellado.
    *   `deliveryTimestamp`: La marca de tiempo en la que el SNA finalmente entregó el evento al SNC.
    Esta distinción permite al agente realizar cálculos de latencia o entender la duración real de un fenómeno percibido.

*   **Polimorfismo de Contenido**: El `SensorEvent` no es un objeto rígido. Su estructura se especializa según la naturaleza de la fuente:

    *   **`SensorEventDiscrete`**: Encapsula estímulos únicos y atómicos. Es el evento estándar para notificaciones directas.
    *   **`SensorEventMergeable`**: Contiene un buffer acumulado de texto que preserva la cronología de mensajes, permitiendo que el SNC perciba una conversación fluida en lugar de turnos fragmentados.
    *   **`SensorEventAggregate`**: En lugar de texto, expone un contador (`count`), permitiendo que el agente trabaje con la **intensidad** del evento en lugar del detalle individual.
    *   **`SensorEventState`**: Ofrece el valor más reciente de un estado, actuando como una "fotografía" de la variable en el momento de la entrega.
    *   **`SensorEventUser`**: Representa un estímulo originado por la interacción directa del usuario. A diferencia de los eventos de máquina, este evento **no requiere un par de herramientas (ficticia + resultado)** para ser entregado, sino que se inyecta directamente como un `UserMessage` puro en la sesión del agente. Es el único evento que el SNC no interpreta como un "dato del entorno", sino como una "instrucción directa".

*   **Estandarización para el LLM (`ConsumableSensorEvent`)**: Esta es la faceta más técnica del evento. A través de la interfaz `ConsumableSensorEvent`, el objeto es capaz de **auto-representarse** en el formato que el LLM espera. 

    *   Provee un método `toJson()` que serializa el evento para el prompt.
    *   Implementa `getChatMessage()` y `getResponseMessage()`, que son los métodos que permiten al orquestador inyectar el evento en el flujo de conversación mediante la "mentira necesaria" del `pool_event`. 

*   **Inmutabilidad Lógica**: Aunque durante su construcción en el `SensorData` el evento puede ser mutable, en el momento en que es entregado a la `DeliveryQueue`, se considera un objeto **inmutable**. Esto es fundamental para evitar efectos secundarios: una vez que el SNC comienza a razonar sobre un evento, el SNA no puede alterar su contenido, garantizando una base de datos de razonamiento estable.

En esencia, `SensorEvent` es la **moneda de cambio** del sistema sensorial. Es el objeto que logra el puente entre la señal física —el log, el mensaje, la alerta— y el concepto abstracto que el cerebro del agente utilizará para planificar su próxima acción.

### 0.5. El Registro de Salud (`SensorStatistics`)

La clase `SensorStatisticsImpl` y su interfaz asociada `SensorStatistics` constituyen el componente de **monitorización interna** de cada sensor. Su propósito es cuantificar la actividad del sistema sensorial de forma independiente al contenido de los mensajes, proporcionando datos sobre la "salud operativa" de cada canal. Es el componente que permite al `SensorsService` (y en última instancia al agente) tomar decisiones basadas en métricas de uso.

*   **Responsabilidad Técnica**: Actúa como un registro de eventos y estados ligado indisolublemente a un `SensorData`. Cada procesador de señales posee su propia instancia de estadísticas, lo que permite que el `SensorsService` tenga una visión granular de lo que ocurre en cada canal sensorial individual.
*   **Contadores de Actividad**: Mantiene el estado persistente de dos indicadores fundamentales: `totalEventsActive` y `totalEventsSilenced`. Estos contadores no miden el contenido de la información, sino el **volumen de tráfico**. La distinción entre eventos activos y silenciados es vital: permite al sistema identificar qué parte del flujo sensorial está siendo ignorada voluntariamente por el agente frente a la que está siendo efectivamente procesada.
*   **Trazabilidad Temporal de la Actividad**: Almacena las marcas de tiempo de `lastEventTimestamp` (cuando ocurrió el último evento) y `lastDeliveryTimestamp` (cuando el SNA entregó el evento al SNC). Estos datos son la base técnica para calcular la latencia del sistema: si la diferencia entre ambos es elevada, el agente puede identificar un cuello de botella en su propio procesamiento o una sobrecarga en su capacidad de razonamiento.
*   **Gestión del Estado de Silencio (`silenced`)**: El registro incluye un flag booleano que actúa como un **interruptor de bajo nivel** dentro del `SensorsService`. Cuando `isSilenced()` es `true`, el procesador de datos asociado ignora cualquier estímulo entrante antes de que este alcance la capa de procesamiento (`process()`), protegiendo la memoria y el contexto del agente de información que el SNC ha decidido, mediante su voluntad, no atender.
*   **Persistencia y Rehidratación**: La clase es compatible con el `SensorsMemento` mediante el `SensorStatisticsGsonAdapter`. Esto garantiza que los contadores, los estados de silencio y las marcas temporales no se pierdan tras un reinicio. Al rehidratarse, el sistema recupera su "memoria biográfica" sensorial, permitiendo que las métricas de salud (como la frecuencia media) tengan un histórico real desde el primer segundo en que el agente vuelve a estar operativo.

### 0.6. Persistencia y Rehidratación (`SensorsMemento`)

El `SensorsMemento` es el objeto de transferencia de estado (*Data Transfer Object*) que encapsula la "fotografía" completa del `SensorsService` para su persistencia en el sistema de archivos (`sensors.json`). Su función es evitar la amnesia del sistema sensorial ante reinicios de la JVM, garantizando que el agente retome su percepción exactamente donde la dejó.

*   **Responsabilidad Técnica**: Actúa como un **contenedor de serialización** que agrupa los tres estados críticos del servicio sensorial:

    *   **Registro de identidades (`infos`)**: El mapa de `SensorInformation` que define qué canales existen. Es la primera parte en ser reconstruida durante la rehidratación para que el resto del sistema entienda a quién pertenecen los datos.
    *   **Estado de entrega (`deliveryQueue`)**: Snapshot de los `SensorEvent` que quedaron pendientes de entrega en el momento del apagado (los eventos "a medio camino" que fueron sellados pero no consumidos).
    *   **Estado de sensores (`stateMap`)**: Fotografía de los últimos valores válidos para sensores de naturaleza `STATE`, asegurando que no se pierda la última "foto" conocida de la realidad.
    *   **Mapa de salud (`statisticsMap`)**: Preservación del estado de silencio (`silenced`) y el histórico contable (`SensorStatistics`) de cada canal.

*   **El Protocolo de Rehidratación (Reconstrucción en dos fases)**: El proceso de carga no es una simple lectura de JSON, sino una **reconstrucción estructurada**:

    1.  **Fase de Identidad**: El sistema procesa primero los metadatos (`infos`). Esto es fundamental porque los adaptadores de GSON (`SensorEventGsonAdapter`) necesitan consultar el catálogo de sensores activos para reconstruir correctamente los eventos de la cola.
    2.  **Fase de Rehidratación de Estado**: Una vez recuperada la identidad, el sistema deserializa el memento. Aquí se reinyectan los eventos en la `deliveryQueue` y se restauran las métricas en `rehydratedStats`. Este mapa temporal es clave: mantiene las estadísticas en un limbo hasta que el sensor correspondiente (ej. un sensor de temperatura) se registra formalmente en el ciclo de arranque, momento en el cual el servicio vincula las estadísticas guardadas con el procesador recién creado.

*   **Atomicidad y Seguridad**: El proceso de guardado utiliza un patrón de **escritura atómica** (escritura en `.tmp` seguida de un `Files.move`). Esto previene la corrupción del estado sensorial en caso de que el proceso se interrumpa bruscamente durante el apagado. 

*   **Integridad del Grafo Sensorial**: Gracias al `SensorsMemento`, la identidad, el volumen de actividad y los estados actuales forman un **grafo persistente**. Al arrancar, el agente no solo recupera datos; recupera su relación con el entorno, permitiéndole saber qué canales estaban "sordos" (silenciados) y cuáles estaban "ruidosos" (frecuencia de eventos) antes de la interrupción.

### 0.7. Topología del Sistema Sensorial (Mapa de Relaciones)

Esta sección resume la estructura estática del sistema, consolidando las relaciones de propiedad y jerarquía que permiten el funcionamiento del `SensorsService`:

*   **Jerarquía de Posesión**:

    *   `SensorsServiceImpl` es el nodo raíz, poseyendo un mapa de `SensorData` (`registeredSensors`).
    *   Cada `SensorData` es un contenedor que **posee** exactamente una `SensorInformation` (su identidad) y una `SensorStatistics` (su estado de salud).
    *   Cada `SensorData` **produce** (o gestiona el buffer de) un tipo específico de `SensorEvent`.

*   **Flujo de Referencias**:

    *   El `SensorEvent` mantiene una referencia a su `SensorInformation` de origen, permitiendo la trazabilidad desde el evento hasta el sentido que lo generó.
    *   El `SensorsService` mantiene una referencia a todas las estadísticas de los sensores, incluso de aquellos que han sido registrados pero están temporalmente inactivos (vía `rehydratedStats`).

*   **Visualización del Grafo**:

    *   El sistema puede visualizarse como un grafo donde el `SensorsService` es el centro. Los nodos `SensorData` son las "estaciones de procesamiento" conectadas a la periferia. Los flujos de datos (`SensorEvent`) son aristas temporales que conectan el `SensorData` con la `DeliveryQueue` o el `StateMap`.


### 1. Dinámica de Procesamiento: El Ciclo de Vida del Estímulo

Este punto describe cómo el `SensorsService` gestiona el movimiento de los datos desde que un hilo externo dispara una señal hasta que esta se consolida en la `deliveryQueue` o el `stateMap`. Aquí es donde la arquitectura estática definida en el Punto 0 cobra vida mediante la concurrencia y los bloqueos de estado.

#### 1.1. Ingesta Atómica: El protocolo `putEvent`
Cuando un componente externo (ej. `TelegramService`) invoca `putEvent()`, el servicio garantiza la integridad del sistema mediante el `sensorLock`. Esta operación no es una simple escritura en cola, sino una **transición de estado**:

1.  **Protección de la Fisiología**: El `sensorLock` asegura que ningún otro hilo pueda alterar el `currentSensor` mientras se evalúa el nuevo estímulo.
2.  **Validación Sensorial**: Se consulta el estado `silenced` del sensor. Si está activo, el estímulo es descartado en la frontera, protegiendo al sistema de procesar datos que el SNC (LLM) ha decidido ignorar.
3.  **Resolución de Discontinuidad**: El orquestador compara el canal entrante con el `currentSensor`. Si detecta un cambio (o si el evento es de naturaleza `DISCRETE`), dispara el protocolo de `flush()` sobre el buffer previo. Esto garantiza que la "cubeta" anterior se selle antes de que la nueva comience a llenarse, evitando la mezcla de contextos semánticos.

#### 1.2. Digestión en Tiempo Real: El rol de `SensorData`
Una vez validada la ingesta, el control se transfiere al `SensorData` correspondiente. Es aquí donde ocurre la transformación dinámica:

*   **Mutación del Buffer**: El procesador invoca su método `process()`. Dependiendo de su naturaleza, el `SensorData` muta su buffer interno (concatena texto, incrementa un contador o sobrescribe un valor).
*   **Gestión de Memoria Eficiente**: El sistema evita instanciar objetos `SensorEvent` prematuramente. El dato reside en el buffer interno del procesador hasta que un evento de `Flush` o una consulta del SNC (`getEvent`) fuerzan la creación del objeto de entrega. Esto minimiza drásticamente el *garbage collection* en condiciones de alta carga.

#### 1.3. Arbitraje y Entrega: La Fusión Maestra
La entrega no es un proceso de "vaciado" directo, sino un arbitraje dinámico. Cuando el SNC solicita un evento, el servicio no entrega simplemente el primero de la `deliveryQueue`; ejecuta la **Fusión Maestra**:

*   **Sincronización**: Se fuerza un `flush()` final para asegurar que cualquier dato "en cocción" en el `currentSensor` pase a la cola de entrega.
*   **Selección Cronológica**: Se comparan las marcas de tiempo (`startTimestamp`) del evento más antiguo en la `deliveryQueue` contra el `stateMap`. El servicio elige siempre el estímulo más antiguo, garantizando que el LLM reciba la realidad en el orden causal exacto en que ocurrió.
*   **Cierre de Entrega**: Al extraer el evento, se le asigna el `deliveryTimestamp`. Este valor es fundamental para que el SNC pueda calcular el "lag" de percepción, permitiéndole razonar sobre la frescura de la información recibida.

#### 1.4. Reactividad del Orquestador: La señal de sincronía
El flujo se completa mediante un patrón *Producer-Consumer* gestionado por el monitor `sensorLock`. 
*   **Estado de Espera**: Si el `ReasoningService` solicita un evento y la `deliveryQueue` está vacía, el hilo consumidor entra en estado de `wait()`.
*   **Despertar Activo**: En cuanto `putEvent()` consolida un nuevo evento, se invoca `notifyAll()`. Esto garantiza una reactividad inmediata: el sistema sensorial "despierta" al cerebro exactamente cuando hay trabajo útil que realizar, eliminando la necesidad de sondeos (*polling*) costosos y manteniendo el agente en un estado de reposo eficiente mientras no hay estímulos que procesar.


### 2. Estrategias de Digestión: Taxonomía de Procesamiento (`SensorNature`)

Una vez que el `SensorsService` recibe un estímulo y lo enruta al `SensorData` correspondiente, entra en juego la **lógica de digestión**. Esta capa es la encargada de transformar los datos crudos en eventos con significado semántico, siguiendo una estrategia predefinida por su `SensorNature`. Cada naturaleza define un **comportamiento de mutación** del estado interno (el buffer) del procesador.

#### 2.1. Procesamiento Discreto (`DISCRETE`)
*   **Comportamiento**: Transmisión sin mediación.
*   **Lógica**: Al invocar `process()`, el sensor no realiza acumulación ni análisis. La señal se encapsula inmediatamente en un `SensorEventDiscreteImpl`.
*   **Impacto en el flujo**: Es la señal de menor latencia. El orquestador detecta el evento y dispara el `flush()` casi simultáneamente, entregando al SNC una pieza de información atómica que es valiosa por su unicidad.

#### 2.2. Procesamiento Fusionable (`MERGEABLE`)
*   **Comportamiento**: Acumulación narrativa.
*   **Lógica**: El procesador mantiene un `StringBuilder` privado. Cuando `process()` recibe un nuevo texto, lo añade al buffer junto con su marca de tiempo, pero no solicita un `flush()`.
*   **Impacto en el flujo**: Esta estrategia permite que un hilo de conversación de 20 mensajes entre al sistema como una única unidad narrativa. El SNA "esconde" la fragmentación del mundo real para que el SNC reciba un bloque coherente de texto cronológico, evitando saturar el historial del LLM con turnos triviales.

#### 2.3. Procesamiento Agregable (`AGGREGATABLE`)
*   **Comportamiento**: Cuantificación escalar.
*   **Lógica**: El procesador mantiene un contador `long` interno. Cada llamada a `process()` solo ejecuta `count++`.
*   **Impacto en el flujo**: Es la estrategia de mayor eficiencia operativa. Ante un entorno ruidoso (ej: miles de peticiones de red por minuto), el SNA no satura el canal de entrega con eventos individuales; simplemente incrementa una cifra. Solo cuando el SNC solicita el evento, el procesador inyecta un resumen estadístico: *«Se han detectado X eventos de este tipo»*. Es la forma más potente de reducir el ruido de sistemas de telemetría sin perder la señal de actividad.

#### 2.4. Procesamiento de Estado (`STATE`)
*   **Comportamiento**: Sustitución absoluta (El presente invalida el pasado).
*   **Lógica**: El procesador no utiliza la `DeliveryQueue`. En su lugar, sobrescribe directamente el valor en el `stateMap` del `SensorsService`.
*   **Impacto en el flujo**: Garantiza que, sin importar cuánto tiempo pase entre una consulta y otra, el agente siempre tenga la fotografía más reciente. Es ideal para variables ambientales que cambian constantemente pero cuya historia pasada es irrelevante para la toma de decisiones inmediata (ej: niveles de batería, temperatura actual).

#### 2.5. Procesamiento de Interacción (`USER`)
*   **Comportamiento**: Inyección directa a la consciencia.
*   **Lógica**: Este tipo de sensor puentea las optimizaciones de agregación o fusión. Cada entrada del usuario se procesa como un evento prioritario, convirtiéndose en un `UserMessage` que el `ReasoningService` integrará obligatoriamente en el siguiente turno de razonamiento.
*   **Impacto en el flujo**: A diferencia de los otros procesadores, este **nunca se silencia**. Es el único estímulo que garantiza una respuesta inmediata del agente, rompiendo cualquier ciclo de pensamiento en curso para priorizar la interacción humana.

### 3. El Mecanismo de "Cierre Forzado" (`Flush` y `currentSensor`)

El `Flush` es el protocolo de seguridad que transforma un buffer de trabajo volátil en un `SensorEvent` inmutable y listo para el razonamiento. Este mecanismo es el que asegura que el SNC (LLM) siempre reciba "paquetes sellados" y nunca fragmentos en proceso de construcción.

#### 3.1. El Puntero `currentSensor` como exclusividad
El `SensorsService` mantiene una referencia única llamada `currentSensor`. Este puntero es el mecanismo de control de flujo que impide que varios sensores intenten entregar datos al SNC de forma simultánea. 
*   **Gestión de exclusividad**: Solo el procesador referenciado por `currentSensor` tiene permiso para añadir datos a su buffer. 
*   **Regla de interrupción**: Si llega un estímulo desde un canal diferente al que apunta `currentSensor`, el orquestador dispara inmediatamente un `flush()` sobre el procesador actual antes de redirigir la "exclusividad" al nuevo sensor. Este cambio de foco garantiza que no exista solapamiento narrativo: el LLM nunca recibirá un evento parcial de Telegram mezclado con uno de Email.

#### 3.2. Disparadores del Flush (Protocolo de Clausura)
El `flushEvent()` no es una operación arbitraria; es un acto administrativo que se dispara ante tres condiciones de contorno:

*   **Discontinuidad Semántica**: Ocurre cuando el orquestador detecta un cambio de contexto (ej: un evento de una naturaleza distinta o de un canal diferente). El `flush()` actúa aquí como un "punto y aparte", cerrando el bloque anterior para que el buffer del procesador pueda ser vaciado.
*   **Satisfacción del SNC**: Cuando el `ReasoningService` consulta la cola de eventos, exige una entrega inmediata. El servicio central recorre todos los `SensorData` registrados y les ordena un `flush()` forzado. Esto garantiza que cualquier evento que estuviera "a medio cocer" en la memoria volátil del procesador sea enviado a la `deliveryQueue` para ser procesado en el turno actual.
*   **Naturaleza DISCRETE**: Dado que estos eventos no tienen dimensión temporal ni narrativa, el procesador aplica un `flush()` implícito en el mismo instante en que se ejecuta `process()`. El buffer nunca llega a retener datos; nace cerrado y listo para la entrega.

#### 3.3. La Operación de Sellado: Del Buffer al Evento
La ejecución del `flush()` es un proceso transaccional que sigue cuatro pasos técnicos ininterrumpibles:

1.  **Extract**: El procesador `SensorData` extrae la información bruta (el texto acumulado o el contador) de su buffer privado.
2.  **Sello Temporal (`endTimestamp`)**: Se registra el instante preciso en el que se cierra el buffer. Esta es la marca que define el límite superior del evento, cerrando el rango cronológico que el SNC utilizará para sus cálculos de latencia.
3.  **Instanciación**: El procesador crea una instancia inmutable de `SensorEvent` (p. ej. `SensorEventMergeableImpl`). En este momento, el evento deja de ser una "variable de trabajo" y se convierte en un objeto de datos persistente.
4.  **Entrega y Reinicio**: El objeto resultante se encola en la `deliveryQueue`. Inmediatamente después, el procesador limpia su buffer interno (ej: `StringBuilder.setLength(0)`), devolviendo el componente a su estado basal y liberando el `currentSensor` para el siguiente evento.

#### 3.4. Garantía de Inmutabilidad
Una vez que el `flush()` concluye y el `SensorEvent` reside en la `deliveryQueue`, su contenido ya no puede cambiar. Si el procesador recibiera un nuevo estímulo un milisegundo después, este iniciaría un **nuevo evento** con un nuevo `startTimestamp`. Esto es vital para el razonamiento: el SNC opera sobre una línea de tiempo compuesta de "instantes sellados", evitando cualquier efecto *jitter* o distorsión en el historial narrativo que el agente gestiona.


### 4. Fusión Maestra: Arbitraje Cronológico

El arbitraje cronológico es la fase en la que el `SensorsService` resuelve la entropía de eventos asíncronos para ofrecerle al SNC (el LLM) una narrativa lineal y causalmente correcta. Dado que los estímulos llegan de fuentes independientes (ej. un log del sistema, un mensaje de Telegram, una alarma de Scheduler), el orden físico de llegada a la `deliveryQueue` no siempre garantiza el orden lógico de los hechos.

#### 4.1. El Conflicto de los Dos Almacenes
El arbitraje debe lidiar con una estructura dual:
*   **`deliveryQueue`**: Contiene eventos secuenciales (Discretos, Fusionados o Agregados) que ya han sido sellados por un `flush()`. Su orden relativo es cronológico dentro del mismo canal.
*   **`stateMap`**: Almacena el estado actual de los sensores de naturaleza `STATE`. Estos eventos no están en una línea de tiempo fija, sino que son "flotantes": su validez es siempre el presente absoluto.

La "Fusión Maestra" es el algoritmo que ejecuta el método `getEvent()` para decidir qué estímulo es el legítimo "siguiente" en la corriente de consciencia del agente.

#### 4.2. Algoritmo de Selección: `findOldestCandidate`
Para resolver qué mensaje presentar, el sistema ejecuta una comparación directa basada en el `startTimestamp` (el momento exacto en que se inició el estímulo). El arbitraje funciona bajo dos reglas estrictas:

1.  **Prioridad por Antigüedad**: Se compara la marca de tiempo de la cabeza de la `deliveryQueue` (el evento sellado más antiguo) con las marcas de tiempo de todos los valores presentes en el `stateMap`.
2.  **Resolución de Conflictos**:
    *   Si el evento de la `deliveryQueue` tiene un `startTimestamp` anterior a cualquier estado del `stateMap`, el sistema lo extrae. Esto garantiza que la narrativa de los hechos secuenciales se entregue sin alteraciones.
    *   Si un estado del `stateMap` es más antiguo que el primer evento de la cola, el sistema extrae dicho estado. Esto permite que el agente reconozca cambios en su entorno (ej. una actualización de variable de sistema) en el momento exacto en que ocurrieron, incluso si el evento fue "adelantado" por un mensaje de chat posterior.

#### 4.3. El Árbitro de la Causalidad
La Fusión Maestra no es solo un reordenamiento de datos; es la **garantía de causalidad** del sistema.
*   **Prevención de la Inversión Temporal**: Si permitiéramos que eventos más recientes (ej: una confirmación de herramienta) se entregaran antes que el evento que los originó (ej: una solicitud del usuario), el LLM perdería la capacidad de entender la relación causa-efecto. El árbitro asegura que el agente siempre perciba el mundo en un flujo coherente de hechos.
*   **Transparencia Sensorial**: Al entregar eventos desde el `stateMap` basados en su `startTimestamp`, el sistema permite que el agente entienda que un valor de estado (como una temperatura) cambió *durante* el transcurso de una conversación, otorgándole una consciencia temporal del entorno que va más allá de la simple recepción de datos.

#### 4.4. Inyección del `deliveryTimestamp`
Una vez que el árbitro elige el evento ganador, el `SensorsService` le asigna un `deliveryTimestamp`. Este es el momento exacto en que la información "cruza el umbral" hacia la consciencia del agente. 
*   **Uso del SNC**: Esta marca es crítica para que el LLM pueda calcular la **frescura de la percepción**. Al comparar el `startTimestamp` (cuándo pasó) con el `deliveryTimestamp` (cuándo me entero), el agente puede razonar sobre su propia latencia: *"Este evento ocurrió hace 5 segundos, la información es fresca"* frente a *"Este evento ocurrió hace 10 minutos, es posible que el entorno haya cambiado"*.

La Fusión Maestra es, por tanto, el motor que convierte un caos de señales asíncronas en una **narrativa de hechos** donde la causalidad es innegociable. Sin este arbitraje, el agente no percibiría un entorno, sino una colección desordenada de datos.


### 5. Metacognición: Estadísticas y Salud (`SensorStatistics`)

El `SensorsService` no solo procesa datos; mantiene un registro introspectivo sobre la calidad y el rendimiento de sus propios canales. La clase `SensorStatistics` funciona como un **cuadro de mandos fisiológico**, permitiendo que el agente no solo "vea" el mundo, sino que comprenda su propia capacidad de percepción. Esta metainformación es el insumo necesario para que el SNC (el LLM) pueda ejercer un control estratégico sobre su atención.

#### 5.1. Cuantificación de la Actividad Sensorial
El sistema mantiene un histórico granular de la actividad mediante dos contadores fundamentales:
*   **`totalEventsActive`**: Mide el volumen de señal procesada. Es el indicador de carga de trabajo de cada sensor, permitiendo al agente identificar cuáles de sus sentidos están siendo más solicitados.
*   **`totalEventsMuted`**: Esta métrica es la base de la **inteligencia selectiva**. Registra cuántos eventos fueron descartados por estar el sensor silenciado. Es un dato crítico para la toma de decisiones: si el volumen de eventos silenciados crece desmesuradamente, el agente puede inferir que está "perdiendo el rastro" de un canal que quizás debería volver a monitorizar.

#### 5.2. Análisis de Frecuencia y Salud del Entorno
Más allá del conteo bruto, el sistema calcula la **frecuencia media** de cada sensor. Esta métrica transforma el dato temporal en un indicador de salud:
*   **Detección de Anomalías**: Un incremento drástico en la frecuencia de un canal (ej. un sensor de sistema pasando de 0 eventos a 500 por segundo) permite al agente detectar estados de "Estrés" sensorial.
*   **Monitoreo de Latencia de Entrega**: Al cruzar la marca del evento (`lastEventTimestamp`) con la marca de entrega (`lastDeliveryTimestamp`), el servicio calcula el *lag* de procesado. Si este delta aumenta, el agente es consciente de que su propia capacidad de razonamiento está saturada, ya que no logra consumir los eventos a la velocidad que el SNA los digiere.

#### 5.3. El Estado de Silencio (`silenced`)
El flag `silenced` no es solo una variable booleana; es la **interfaz de voluntad** entre el SNC y el SNA.
*   Cuando el agente decide concentrarse, ejecuta una instrucción que modifica este estado en `SensorStatistics`. A partir de ese momento, el servicio sensorial modifica su comportamiento: los datos crudos ya no se procesan, sino que se computan únicamente como "eventos ignorados".
*   Este mecanismo permite al agente proteger su ventana de contexto del LLM frente a fuentes que, aunque interesantes, son irrelevantes para la tarea actual, aplicando un filtro de atención consciente que es, a la vez, estadísticamente rastreable.

#### 5.4. Integración en la Consciencia: `sensor_status`
Toda esta telemetría es expuesta al agente mediante la herramienta `sensor_status`. Esto no es una mera consulta técnica; es una **consulta de autodiagnóstico**. 

*   El agente utiliza este informe para evaluar si su "cuerpo" está operando dentro de los parámetros esperados.
*   Si el agente recibe una alerta del usuario, puede consultar `sensor_status()` para comprobar si algún sentido estaba desactivado o si la tasa de errores (agregados en las estadísticas) justifica el problema. 

En esencia, `SensorStatistics` cierra el bucle de retroalimentación: el sistema sensorial no solo entrega información al cerebro, sino que también entrega información sobre **cómo de bien está funcionando ese proceso de entrega**. Esto dota al agente de una capacidad de **ajuste de prioridades** basada en datos, transformando la percepción pasiva en una estrategia de atención activa.



