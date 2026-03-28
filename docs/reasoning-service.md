
# Especificación técnica de la implementación de ReasoningService

## 1. Introducción: el cerebro del agente

El `ReasoningService` es el núcleo de control del agente Noema. Si el sistema sensorial (`SensorsService`) es su percepción del entorno, y la memoria a largo plazo (`MemoryService`) su capacidad de recordar, el `ReasoningService` es el centro que integra ambas, toma decisiones, ejecuta acciones y mantiene la continuidad de la conversación. Es, en definitiva, el cerebro del agente.

Su responsabilidad principal es orquestar un **bucle perpetuo de consciencia**: un hilo dedicado que nunca duerme (salvo cuando el agente se detiene) y que constantemente espera estímulos, mensajes del usuario, notificaciones de Telegram, correos entrantes, alarmas programadas, o incluso el simple paso del tiempo, para procesarlos. Cada estímulo, ya sea un mensaje directo del usuario (que se inyecta en la conversación como un `UserMessage` nativo) o una señal del entorno (como una notificación de Telegram o el paso del tiempo, que se introducen mediante un mecanismo simulado de `pool_event`), desencadena una o varias rondas de razonamiento, durante las cuales el servicio construye el contexto, consulta al modelo de lenguaje, ejecuta las herramientas que este solicite y registra cada paso en la base de datos de la conversación (`SourceOfTruth`).

Para cumplir esta función, el `ReasoningService` integra varios subsistemas:

* **El modelo de lenguaje (`ChatModel`)**. Es el proveedor de razonamiento, configurable en caliente (proveedor, URL, clave, identificador del modelo) mediante acciones que el propio agente puede ejecutar. El servicio mantiene una instancia activa y la utiliza para todas las consultas generativas.
* **La sesión activa (`Session`)**. Es la memoria de trabajo del agente. Conserva en RAM los mensajes recientes de la conversación (tanto los del usuario como los del modelo, incluyendo las llamadas a herramientas y sus resultados), y los estructura para enviarlos al modelo junto con el prompt de sistema y el resumen de la memoria consolidada. La `Session` es además la responsable de inyectar una percepción temporal pasiva: si ha pasado más de una hora desde la última interacción, añade un mensaje sintético que informa al modelo del tiempo transcurrido.
* **El registro de herramientas (`AgentTool`)**. El servicio mantiene un catálogo de todas las capacidades que el agente puede invocar (lectura y escritura de archivos, ejecución de comandos, búsquedas web, envío de correos, etc.). Cada herramienta se registra con un nombre único, una especificación para el modelo (que incluye descripción y esquema de parámetros) y un estado de activación que puede modificarse por configuración. Durante el razonamiento, si el modelo decide usar una herramienta, el servicio la ejecuta, solicita confirmación al usuario si la operación es peligrosa, y devuelve el resultado para continuar el ciclo.
* **La persistencia (`SourceOfTruth`)**. Cada interacción atómica, un mensaje de usuario, una respuesta del modelo, una ejecución de herramienta, se persiste como un `Turn` en una base de datos H2. El `ReasoningService` no solo usa estos turnos para reconstruir la sesión tras un reinicio, sino que también los emplea como materia prima para la compactación de la memoria a largo plazo.
* **La compactación (`MemoryService`)**. Cuando la sesión acumula demasiados turnos (el umbral es configurable, por defecto 40), el servicio invoca a `MemoryService` para que consolide los mensajes más antiguos en un `CheckPoint`: un resumen narrativo que preserva la esencia de la conversación sin ocupar espacio en la ventana de contexto. Una vez generado, el nuevo `CheckPoint` se persiste y los mensajes compactados se eliminan de la sesión activa.

El `ReasoningService` se ejecuta como un servicio más dentro del `Agent`. Su ciclo de vida es sencillo: al arrancar, carga las herramientas, instala los recursos de identidad (prompt de sistema, manuales de entorno y habilidades), crea el modelo de lenguaje y lanza el hilo del bucle de eventos. Al detenerse, simplemente marca una bandera que provoca la salida ordenada del hilo, permitiendo que la sesión activa se persista correctamente en disco.

Desde fuera, el servicio expone una interfaz mínima: permite añadir herramientas, consultar su estado, activarlas o desactivarlas, y recuperar métricas sobre el tamaño del contexto. Sin embargo, la mayor parte de su funcionalidad es interna y está encapsulada en el bucle `eventDispatcher` y en la colaboración con `Session`.

En el contexto global de Noema, el `ReasoningService` es el componente que dota al agente de **continuidad conversacional** (no olvida lo que acaba de decir), **capacidad de acción** (puede tocar el mundo real a través de herramientas) y **autonomía** (procesa estímulos sin intervención humana, salvo cuando la seguridad lo exige). Su diseño busca un equilibrio pragmático entre la potencia de los modelos de lenguaje actuales y las restricciones de un entorno de escritorio local, sin depender de infraestructuras cloud ni servicios externos más allá de las APIs de los propios LLMs.


## 2. Arquitectura del servicio (mapa de componentes)

El `ReasoningService` no actúa en solitario. Su correcto funcionamiento depende de una constelación de componentes que colaboran estrechamente, cada uno con una responsabilidad bien definida. Esta sección describe los bloques que constituyen el servicio y las relaciones entre ellos.

### 2.1. `ReasoningServiceImpl`: el orquestador

`ReasoningServiceImpl` es la implementación concreta del servicio. Actúa como el punto central de control, el “director de orquesta” que coordina a todos los demás actores. Su responsabilidad abarca:

* **Ciclo de vida**: gestiona el arranque (`start`) y la parada (`stop`) del servicio, lanzando y deteniendo el hilo del `eventDispatcher` que sostiene el bucle de consciencia.
* **Configuración del modelo**: mantiene una instancia de `ChatModel` (el proveedor de lenguaje) y la recrea cuando cambian los parámetros de conexión (URL, clave, identificador del modelo) mediante las acciones `CHANGE_REASONING_PROVIDER` y `CHANGE_REASONING_MODEL`.
* **Registro de herramientas**: posee un mapa (`availableTools`) donde cada herramienta (`AgentTool`) se almacena junto con un flag que indica si está activa. Este mapa es la fuente de verdad para saber qué capacidades puede usar el agente.
* **Construcción del prompt de sistema**: el método `getBaseSystemPrompt()` ensambla el mensaje inicial que define la personalidad y las reglas del agente, combinando el prompt base (almacenado como recurso Markdown), los módulos de identidad activos (que el usuario puede seleccionar en la configuración) y los índices de referencia del entorno (los archivos `.ref.md`). El resultado se cachea y se escribe en un fichero temporal para facilitar la depuración.
* **Orquestación del bucle**: el método `eventDispatcher` contiene el bucle principal que consume eventos, los procesa, invoca al modelo, ejecuta herramientas y coordina la compactación. Es el corazón del servicio.
* **Interfaz de gestión**: expone métodos para consultar y modificar el estado de las herramientas (`getAvailableTools`, `isToolActive`, `setToolActive`) y para obtener métricas sobre el contexto (`estimateSystemPromptTokenCount`, `estimateToolsTokenCount`, `estimateMessagesTokenCount`).

### 2.2. `Session`: la memoria de trabajo

`Session` es el contenedor de la conversación activa. No es un simple almacén de mensajes, sino un gestor inteligente que mantiene la coherencia entre lo que ocurre en RAM y lo que se ha persistido en disco.

Sus responsabilidades principales son:

* **Almacenamiento de mensajes**: mantiene una lista ordenada de objetos `ChatMessage` (de LangChain4j) que constituyen el historial inmediato de la conversación. Esta lista incluye mensajes de usuario, respuestas del modelo, llamadas a herramientas y resultados de herramientas.
* **Trazabilidad con la persistencia**: a través del mapa `turnOfMessage`, asocia cada mensaje en la lista con el identificador del `Turn` que lo originó. Esto permite, entre otras cosas, saber qué parte del historial ya ha sido compactada y qué parte aún está pendiente.
* **Construcción del contexto**: el método `getContextMessages()` es el responsable de ensamblar el bloque de texto que se enviará al LLM en cada consulta. Combina el prompt de sistema (proporcionado por el `ReasoningService`), el resumen del último `CheckPoint` (si existe), y los mensajes de la sesión activa. Además, inyecta la percepción temporal pasiva: si ha transcurrido más de una hora desde la última interacción, añade un mensaje sintético que informa al modelo del tiempo transcurrido.
* **Gestión de la compactación**: proporciona los métodos `getOldestMark()`, `getCompactMark()` y `remove()` que permiten aislar el bloque de mensajes que será compactado y eliminarlo de la sesión una vez que `MemoryService` haya generado un nuevo `CheckPoint`.
* **Persistencia de la sesión**: se serializa a disco (en `active_session.json`) tras cada modificación, utilizando un mecanismo de escritura atómica (archivo temporal + movimiento) para garantizar que no se corrompa en caso de fallo. Esto permite que el agente recupere su estado exacto tras un reinicio.

### 2.3. `AgentTool`: las capacidades del agente

Las herramientas son los músculos del agente: cada una encapsula una capacidad específica que el LLM puede invocar para interactuar con el mundo exterior. El `ReasoningService` no conoce los detalles de implementación de cada herramienta; solo necesita su nombre, su especificación (para presentarla al modelo) y un método `execute` que devuelve un resultado en texto.

Cada herramienta implementa la interfaz `AgentTool` y define:

* **Metadatos**: nombre único, descripción legible por el modelo, y un esquema JSON que describe sus parámetros (generado a partir de anotaciones o definición manual).
* **Modo de operación**: puede ser `MODE_READ` (operaciones seguras que no modifican el estado), `MODE_WRITE` (operaciones que modifican archivos o configuración) o `MODE_EXECUTION` (ejecución de comandos externos). Esta clasificación determina si el `ReasoningService` debe solicitar confirmación al usuario antes de ejecutarla.
* **Estado de activación**: cada herramienta tiene un flag `active` que puede modificarse por configuración, permitiendo deshabilitar temporalmente capacidades que no se desea que el agente utilice.

El `ReasoningService` mantiene un registro de todas las herramientas disponibles, sincroniza su estado con la configuración del usuario y, durante el bucle, ejecuta aquellas que el modelo solicita, gestionando la confirmación humana cuando es necesario.

### 2.4. `SourceOfTruth`: la persistencia inmutable

`SourceOfTruth` es el repositorio que almacena de forma duradera cada uno de los pasos de la conversación. No es un componente interno del `ReasoningService`, sino un servicio independiente que este utiliza para persistir los turnos y recuperar los puntos de control.

Su modelo de datos se organiza en torno a dos entidades:

* **`Turn`**: representa una unidad atómica de interacción. Puede ser un mensaje del usuario, una respuesta del modelo, una llamada a herramienta o el resultado de una herramienta. Cada turno tiene un identificador único, una marca de tiempo, un tipo (chat, tool_execution, lookup_turn, etc.), y los campos de texto relevantes (entrada, salida, etc.). Los turnos se almacenan en tablas SQL dentro de una base de datos H2 embebida.
* **`CheckPoint`**: representa un punto de consolidación de la memoria a largo plazo. Contiene un resumen narrativo de un segmento de la conversación (generado por `MemoryService`) y un texto de "El Viaje" que preserva la cronología de los eventos. Los puntos de control se encadenan: cada nuevo `CheckPoint` parte del anterior y añade los turnos transcurridos desde entonces.

El `ReasoningService` utiliza `SourceOfTruth` para tres operaciones:

* **Persistir turnos**: cada vez que se produce una interacción (un mensaje, una ejecución de herramienta), se crea un `Turn` y se añade a la base de datos.
* **Recuperar turnos para compactación**: cuando la sesión alcanza el umbral, el servicio solicita los turnos comprendidos entre dos marcas para pasarlos a `MemoryService`.
* **Obtener el último punto de control**: al arrancar, se recupera el `CheckPoint` más reciente para incluirlo en el contexto inicial.

### 2.5. `MemoryService`: la compactación narrativa

`MemoryService` es el encargado de la memoria a largo plazo. Su función es transformar una secuencia de turnos (que pueden ser decenas o cientos) en un resumen narrativo compacto que preserve la esencia de la conversación sin ocupar espacio en la ventana de contexto.

El `ReasoningService` no conoce los detalles de cómo se genera ese resumen. Solo interactúa con `MemoryService` en un momento específico del ciclo: cuando la sesión ha acumulado suficientes turnos y se decide compactar. La invocación es simple:

* Se le pasa el último `CheckPoint` existente (o `null` si es la primera compactación) y la lista de turnos que se deben consolidar.
* `MemoryService` utiliza un LLM (puede ser el mismo modelo o uno más económico) para generar un nuevo `CheckPoint` que contiene un resumen actualizado y una narrativa cronológica ("El Viaje").
* El `ReasoningService` recibe el nuevo `CheckPoint`, lo persiste a través de `SourceOfTruth` y lo convierte en el `activeCheckPoint` para futuros contextos.

Esta separación de responsabilidades permite que la lógica de compactación pueda evolucionar (cambiando el prompt, el modelo utilizado, o incluso la estrategia de resumen) sin afectar al orquestador principal.

### 2.6. `AgentAccessControl` y `AgentConsole`: seguridad e interacción humana

Aunque no son componentes internos del `ReasoningService`, su papel en la ejecución segura es crucial.

* **`AgentAccessControl`**: define qué operaciones están permitidas según el contexto. Por ejemplo, puede restringir el acceso a determinadas rutas del sistema de archivos o impedir la ejecución de comandos en ciertos directorios. Antes de ejecutar una herramienta, el `ReasoningService` consulta a este controlador para saber si la herramienta está permitida en el estado actual del agente.
* **`AgentConsole`**: es la interfaz para la interacción con el usuario. No es una consola física, sino una abstracción que puede tener implementaciones diferentes: una versión gráfica (Swing), una versión de terminal (JLine) o incluso una versión "tonta" para entornos headless. El `ReasoningService` la utiliza para mostrar mensajes del sistema y, sobre todo, para solicitar confirmación antes de ejecutar herramientas que modifiquen el estado (escritura de archivos, ejecución de comandos). Al ser una interfaz, el servicio queda desacoplado de la tecnología de presentación concreta.

### 2.7. Relaciones entre componentes

El flujo de control entre estos componentes sigue un patrón claro:

1. El `eventDispatcher` (dentro de `ReasoningServiceImpl`) espera un evento de `SensorsService`.
2. El evento se convierte en mensajes y se añade a `Session`.
3. Se construye el contexto llamando a `Session.getContextMessages()`, que puede incluir el `activeCheckPoint` obtenido de `SourceOfTruth`.
4. El contexto se envía al `ChatModel`, que devuelve una respuesta.
5. Si la respuesta contiene solicitudes de herramientas, se ejecutan (consultando a `AgentAccessControl` y solicitando confirmación a `AgentConsole` si es necesario), los resultados se añaden a `Session` y se persisten como turnos en `SourceOfTruth`.
6. Si la respuesta es texto, se muestra en `AgentConsole`, se persiste el turno correspondiente y se cierra el ciclo.
7. Si `Session.needCompaction()` lo indica, se invoca a `MemoryService` para generar un nuevo `CheckPoint`, que se persiste en `SourceOfTruth` y se convierte en el nuevo `activeCheckPoint`, mientras `Session.remove()` elimina los mensajes compactados.

Esta estructura de componentes con responsabilidades bien delimitadas permite que el `ReasoningService` sea, al mismo tiempo, el centro neurálgico del agente y un módulo relativamente sencillo de entender y modificar, porque cada pieza hace una cosa y la hace bien.

## 3. Ciclo de vida y concurrencia

El `ReasoningService` es un servicio gestionado por `Agent`, que sigue el protocolo estándar de ciclo de vida de todos los servicios de Noema: se instancia a través de una fábrica (`AgentServiceFactory`), se verifica que puede arrancar (`canStart()`) y finalmente se inicia (`start()`) o se detiene (`stop()`) según las necesidades del agente.

### 3.1. Arranque del servicio

Cuando el agente se pone en marcha recorre los servicios registrados y llama a `start()` sobre aquellos que están habilitados. Para el `ReasoningService`, este momento es crítico porque determina la capacidad del agente para pensar y actuar.

El método `start()` ejecuta una secuencia ordenada de operaciones:

1. **Instalación de recursos**. Copia al espacio de trabajo del agente los archivos necesarios para su funcionamiento: el prompt de sistema base (`reasoning-system.md`), los módulos de identidad (`core`), los índices de referencia del entorno (`environ`) y la lista de habilidades (`skills`). Estos recursos se almacenan en `var/config/prompts/` y `var/identity/`, y son la materia prima con la que se construirá la personalidad del agente.

2. **Registro de acciones**. Añade al sistema de acciones del agente los comportamientos que permiten modificar la configuración del modelo en caliente (`CHANGE_REASONING_PROVIDER`, `CHANGE_REASONING_MODEL`) y forzar operaciones de mantenimiento (`COMPACT_REASONING`, `REFRESH_REASONING_TOOLS`). Esto permite que el propio agente (o un usuario avanzado) pueda, por ejemplo, cambiar de proveedor de IA sin necesidad de reiniciar.

3. **Sincronización de herramientas**. Invoca a `refresh_available_tools()` para que el estado de activación de cada herramienta (definido en la configuración del usuario) se refleje en el mapa interno. Las herramientas que no aparecen en la configuración conservan su estado por defecto (definido por la propia herramienta al ser registrada).

4. **Creación del modelo de lenguaje**. Construye la instancia de `ChatModel` a partir de los parámetros de conexión (URL, clave API, identificador del modelo) almacenados en la configuración del agente. Este modelo será el motor de razonamiento para toda la sesión.

5. **Lanzamiento del hilo de eventos**. Crea un hilo de plataforma (no virtual) con el nombre `Noema-Event-Dispatcher` y lo pone en marcha, ejecutando el método `eventDispatcher()`. Este hilo se convierte en el corazón latido del agente: mientras el servicio está activo, nunca se detiene.

Una vez completados estos pasos, el flag `running` se establece a `true` y el servicio imprime un mensaje en la consola indicando que está operativo, junto con el nombre del modelo de lenguaje que está utilizando.

### 3.2. El hilo del `eventDispatcher`

El hilo del `eventDispatcher` es el único punto de ejecución activo del servicio. Su diseño es deliberadamente simple: un bucle infinito que, mientras `running` sea verdadero, consume eventos y los procesa. No hay concurrencia interna: cada evento se procesa hasta completar todas las rondas de razonamiento que requiera, antes de pasar al siguiente.

Este modelo de **un solo hilo secuencial** tiene varias ventajas:

- **Simplicidad**: no hay que gestionar sincronizaciones complejas entre múltiples hilos que comparten la sesión.
- **Determinismo**: el orden de procesamiento de los eventos es el orden en que se extraen de la cola sensorial, garantizado por el `SensorsService`.
- **Estabilidad**: se evitan problemas de concurrencia que podrían llevar a estados inconsistentes en la sesión o en la persistencia.

La elección de un hilo de plataforma en lugar de un hilo virtual responde a consideraciones prácticas: aunque el código se escribió inicialmente con hilos virtuales, se encontraron problemas durante la depuración que llevaron a revertir a hilos de plataforma. En cualquier caso, dado que solo hay un hilo principal el uso de hilos virtuales no aportaría una ventaja significativa en este contexto.

### 3.3. Parada del servicio

Cuando el agente se detiene (por cierre de la aplicación o por una acción explícita), se invoca al método `stop()` del `ReasoningService`. Este método simplemente establece el flag `running` a `false`. No interrumpe el hilo de eventos ni fuerza una salida inmediata.

El propio bucle del `eventDispatcher` está diseñado para comprobar `running` en cada iteración. En el momento en que la condición deja de cumplirse, el hilo abandona el bucle y finaliza de forma natural. Esto garantiza que cualquier evento que se estuviera procesando en ese momento se complete antes de la parada, evitando estados intermedios o corrupción de la sesión.

La persistencia de la sesión activa no depende del `stop()`, sino que se guarda en disco tras cada modificación (dentro de los métodos `add()`, `consolideTurn()` y `remove()` de `Session`). Por tanto, aunque el agente se detenga abruptamente (por un fallo de la JVM o un corte de energía), la última versión persistida de la sesión es siempre la anterior a la operación que se estaba ejecutando. El mecanismo de escritura atómica (archivo temporal + movimiento) asegura que nunca se quede un archivo parcialmente escrito.

### 3.4. Consideraciones sobre concurrencia externa

Aunque el bucle principal es secuencial, hay puntos en los que el `ReasoningService` interactúa con otros componentes que pueden estar operando en hilos diferentes:

- **`SensorsService.getEvent()`**: esta llamada puede bloquear el hilo hasta que llegue un nuevo evento. Internamente, el servicio sensorial utiliza mecanismos de sincronización (`wait/notify`) que permiten que el hilo del `eventDispatcher` duerma eficientemente cuando no hay trabajo que hacer.
- **Ejecución de herramientas**: cuando el LLM solicita una herramienta, la ejecución se realiza de forma síncrona dentro del mismo hilo. Si una herramienta tarda mucho tiempo (por ejemplo, un procesamiento pesado o una espera de red), el bucle se bloquea hasta que retorna. Esto es intencionado: el agente no debe procesar nuevos eventos mientras está ejecutando una acción, para preservar la coherencia del historial.
- **Confirmación humana**: antes de ejecutar herramientas peligrosas, se solicita confirmación a `AgentConsole`. Este método puede ser bloqueante si la consola requiere interacción del usuario (por ejemplo, un diálogo modal en Swing). Durante ese tiempo, el hilo del `eventDispatcher` también permanece bloqueado, lo que es correcto porque el agente no puede continuar hasta que el usuario autorice o deniegue la operación.
- **Compactación asíncrona**: aunque la compactación se dispara desde el hilo principal, internamente `MemoryService` puede realizar operaciones pesadas (varias llamadas al LLM) que también bloquean el bucle. Esto es una decisión de diseño deliberada: la compactación es parte del procesamiento del turno y debe completarse antes de pasar al siguiente evento. Si en el futuro se optara por hacerla asíncrona, habría que rediseñar la gestión de la sesión para evitar que se sigan añadiendo mensajes mientras se compacta.

En resumen, el modelo de concurrencia del `ReasoningService` es deliberadamente simple: un solo hilo, un solo evento cada vez, sin concurrencia interna. Esta simplicidad es una de las claves de su robustez: no hay condiciones de carrera, ni estados inconsistentes, ni necesidad de mecanismos de sincronización complejos. Todo el flujo es determinista y predecible, lo que facilita tanto el desarrollo como la depuración.


## 4. El núcleo del orquestador: el bucle `eventDispatcher`

El método `eventDispatcher` es el corazón palpitante del `ReasoningService`. Es un bucle infinito que se ejecuta en su propio hilo desde el momento en que el servicio arranca hasta que se detiene. Su función es simple en apariencia, consumir eventos y procesarlos, pero su implementación concentra la lógica más crítica del agente: la orquestación de la conversación, la gestión de herramientas y la coordinación con la memoria.

### 4.1. La estructura general

El bucle se organiza en torno a un único punto de bloqueo: la llamada a `sensors.getEvent()`. Mientras no haya estímulos que atender, el hilo permanece en espera, consumiendo recursos mínimos. Cuando llega un evento, el flujo se desencadena y no se detiene hasta que se ha completado el procesamiento completo de ese estímulo, incluyendo todas las rondas de razonamiento y ejecución de herramientas que sean necesarias.

La estructura simplificada del bucle es la siguiente:

1. Obtener el siguiente evento sensorial (bloqueante).
2. Si el evento es de usuario, inyectarlo directamente como `UserMessage`. Si es del entorno, inyectarlo mediante el mecanismo simulado de `pool_event`.
3. Persistir el turno de observación (el estímulo que acaba de llegar).
4. Entrar en un bucle interno que se repetirá hasta que el turno actual se considere "terminado".
5. Construir el contexto completo (prompt de sistema, checkpoint histórico, mensajes de la sesión).
6. Consultar al modelo de lenguaje, proporcionándole la lista de herramientas activas.
7. Evaluar la respuesta del modelo:
   - Si solicita ejecutar herramientas: ejecutarlas (con confirmación humana si es necesario), persistir los resultados, añadirlos a la sesión y continuar el bucle interno.
   - Si responde con texto: mostrarlo en consola, persistir el turno final, y salir del bucle interno.
8. Al salir del bucle interno, verificar si la sesión ha alcanzado el umbral de compactación. Si es así, ejecutar la compactación.
9. Volver al paso 1.

### 4.2. El punto de entrada: la espera de eventos

El bucle comienza con una llamada a `sensors.getEvent()`. Este método pertenece al `SensorsService`, que actúa como la puerta de entrada de todos los estímulos externos: mensajes del usuario, notificaciones de Telegram, correos entrantes, alarmas programadas, e incluso el paso del tiempo (a través de un sensor de reloj interno).

La implementación de `getEvent()` está diseñada para ser bloqueante: si no hay eventos disponibles, el hilo se duerme hasta que el `SensorsService` recibe un nuevo estímulo y lo notifica. Este mecanismo de espera activa pero eficiente permite que el agente no consuma CPU cuando no hay trabajo que hacer, reaccionando en cambio de forma inmediata cuando algo ocurre.

El evento devuelto no es un dato crudo, sino un objeto `ConsumableSensorEvent` que ya sabe cómo transformarse en mensajes de LangChain4j (tanto el mensaje que se añadirá al historial como, en el caso de sensores del entorno, el mensaje de respuesta simulado de la herramienta `pool_event`).

### 4.3. La inyección del evento en la sesión: dos caminos

Una vez obtenido el evento, el `eventDispatcher` toma una decisión crucial basada en su naturaleza:

**Eventos de usuario (`SensorEventUser`)**

Estos eventos representan la intervención directa del interlocutor humano. El flujo los trata con la máxima naturalidad: el evento se convierte en un `UserMessage` (el tipo de mensaje estándar que el modelo espera recibir cuando alguien le habla) y se añade directamente a la sesión. No hay simulación, no hay capas de indirección. Desde la perspectiva del modelo, es como si el usuario hubiera escrito su mensaje en el chat.


**Eventos del entorno (el resto de naturalezas sensoriales)**

Cuando el evento no es de usuario, es decir, cualquier estímulo que no proviene de la interacción directa con el interlocutor humano, el tratamiento es diferente. El evento ya ha sido diseñado para saber cómo presentarse ante el modelo de lenguaje. A través de sus métodos `getChatMessage()` y `getResponseMessage()`, cada evento sabe qué par de mensajes debe inyectar en la sesión para que el modelo perciba el estímulo como si hubiera sido generado por una acción propia del agente.

El `eventDispatcher` no necesita conocer los detalles de esa transformación. Simplemente añade ambos mensajes a la sesión en el orden en que deben aparecer en el historial:

1. Primero, `event.getChatMessage()`, que suele ser un `AiMessage` que simula una llamada a la herramienta `pool_event`. Este mensaje aparece en el historial como si el propio agente hubiera decidido consultar sus sensores.

2. Inmediatamente después, `event.getResponseMessage()`, que es un `ToolExecutionResultMessage` que contiene el contenido del estímulo (el texto de la notificación, la alarma, etc.). Este mensaje aparece como el resultado de la llamada a `pool_event`.

De esta forma, cuando el modelo recibe el contexto completo, encuentra en su historial una secuencia coherente: primero un registro de que él mismo ejecutó una herramienta para consultar sus sensores, y luego el resultado de esa consulta con la información del estímulo recibido. La asincronía del mundo real queda oculta bajo esta capa de simulación, y el modelo puede procesar el evento como si hubiera sido él quien lo solicitó.

Una vez añadidos los mensajes, se persiste un `Turn` de tipo `tool_execution` que documenta el evento como si se tratara de una ejecución real de la herramienta `pool_event`. Este turno contiene en sus campos tanto el mensaje de llamada (simulado) como el resultado obtenido, manteniendo así la trazabilidad completa del estímulo percibido.

### 4.4. La persistencia del turno de observación

Independientemente del tipo de evento, inmediatamente después de añadirlo a la sesión se crea y persiste un `Turn` que documenta el estímulo recibido. Este turno tiene tipo `tool_execution` para los eventos del entorno (ya que se registra como una ejecución simulada de `pool_event`) o `chat` para los eventos de usuario.

La persistencia temprana del turno de observación es importante por dos razones:

- **Trazabilidad**: queda constancia en la base de datos de que el agente percibió ese estímulo en un momento concreto, independientemente de lo que ocurra después.
- **Compactación futura**: cuando se consolide la memoria, estos turnos de observación formarán parte de la narrativa que se resume.

### 4.5. El bucle interno: procesando hasta cerrar el turno

Una vez que el estímulo está en la sesión, comienza el bucle interno. Su objetivo es alcanzar un estado en el que el modelo haya generado una respuesta de texto (no una llamada a herramienta) y se pueda considerar que el turno actual ha terminado.

Cada iteración del bucle interno sigue estos pasos:

**Construcción del contexto**

Se invoca a `session.getContextMessages()`, que devuelve una lista de mensajes lista para enviar al modelo. Esta lista incluye:

- El prompt de sistema (la identidad del agente, sus reglas operativas y los índices de referencia del entorno).
- El resumen del último `CheckPoint` (si existe), que aporta memoria a largo plazo.
- Todos los mensajes acumulados en la sesión activa (incluyendo el evento que desencadenó el turno, las interacciones previas, y los resultados de herramientas ejecutadas en iteraciones anteriores del mismo turno).

Además, si ha pasado más de una hora desde la última interacción, el método `getContextMessages()` inyecta un mensaje sintético de sensor de tiempo, informando al modelo del lapso transcurrido. Este es el mecanismo que dota al agente de percepción temporal pasiva.

**Consulta al modelo**

Con el contexto construido y la lista de herramientas activas, se llama a `model.generate()`. El modelo de lenguaje (configurado con los parámetros de conexión oportunos) devuelve una respuesta que puede ser de dos tipos: texto plano, o una o más solicitudes de ejecución de herramientas.

**Manejo de herramientas**

Si el modelo solicita ejecutar herramientas, el `eventDispatcher` itera sobre cada solicitud. Por cada una:

- Se busca la herramienta en el registro `availableTools`.
- Si la herramienta está activa y permitida por `AgentAccessControl`, se procede a ejecutarla.
- Si el modo de la herramienta es `MODE_WRITE` o `MODE_EXECUTION`, se solicita confirmación al usuario a través de `AgentConsole.confirm()`. Si el usuario deniega, la ejecución se aborta y se devuelve un mensaje de error.
- La herramienta se ejecuta (síncronamente, dentro del mismo hilo) y se obtiene un resultado en texto.
- Se crea un `Turn` de tipo `tool_execution` (o `lookup_turn` si la herramienta es de memoria) que documenta la llamada y su resultado.
- Se añade un `ToolExecutionResultMessage` a la sesión, que el modelo verá en la siguiente iteración del bucle interno.

Una vez procesadas todas las solicitudes de herramientas, el bucle interno continúa. El modelo recibirá en la siguiente iteración tanto el resultado de las herramientas ejecutadas como cualquier otro mensaje que se haya añadido mientras tanto.

**Manejo de la respuesta textual**

Si el modelo responde con texto (y no hay solicitudes de herramientas pendientes), se ha alcanzado el final del turno. El texto se muestra en la consola a través de `AgentConsole.printModelResponse()`, se persiste un `Turn` de tipo `chat` que contiene la respuesta, y se añade el mensaje a la sesión (aunque en realidad ya se añadió cuando se recibió la respuesta del modelo). El flag `turnFinished` se establece a `true` y se sale del bucle interno.

**Reintentos por herramientas no formalizadas**

Hay un caso especial contemplado en el código: cuando el modelo devuelve un `FinishReason.TOOL_EXECUTION` pero no hay solicitudes de herramientas en la respuesta. Esto puede ocurrir con algunos modelos que anuncian que van a usar una herramienta pero no la formalizan correctamente en el formato esperado. En ese caso, el bucle inyecta un mensaje de usuario con el texto "(reintenta la llamada a la herramienta sin ninguna explicación)" y continúa, incrementando un contador de reintentos. Si se superan tres reintentos, se aborta el turno con una excepción.

### 4.6. La compactación al final del turno

Una vez que el bucle interno ha terminado (es decir, el modelo ha entregado una respuesta textual y se ha cerrado el turno), el `eventDispatcher` evalúa si la sesión necesita compactación mediante `session.needCompaction()`.

Este método compara el número de turnos únicos acumulados en la sesión con un umbral configurable (por defecto, 40 turnos). Si se ha superado el umbral, se invoca a `performCompaction()`, que inicia el proceso de consolidación de la memoria a largo plazo:

- Se obtienen las marcas de inicio y fin del bloque a compactar (`getOldestMark()` y `getCompactMark()`).
- Se recuperan de `SourceOfTruth` los turnos comprendidos entre esas marcas.
- Se llama a `MemoryService.compact()` pasándole el último `CheckPoint` existente y la lista de turnos. `MemoryService` utiliza un LLM para generar un nuevo `CheckPoint` que resume la conversación.
- El nuevo `CheckPoint` se persiste en `SourceOfTruth`.
- Se limpia la sesión, eliminando los mensajes que ya han sido compactados (`session.remove()`).
- El `activeCheckPoint` se actualiza al nuevo valor.

La compactación ocurre dentro del mismo hilo del `eventDispatcher`, bloqueando el procesamiento de nuevos eventos mientras se realiza. Esto es una decisión de diseño: compactar es parte del procesamiento del turno que acaba de terminar, y no deberían llegar nuevos estímulos hasta que la memoria esté consolidada.

### 4.8. Manejo de errores y callback final

El bucle principal está envuelto en un bloque `try-catch` que captura cualquier excepción no manejada (incluyendo `Throwable`). Si ocurre un error crítico, se registra en el log, se muestra un mensaje de error en la consola y el bucle continúa. La filosofía es que el agente debe seguir funcionando incluso ante fallos inesperados, sin colapsar. La sesión y los turnos ya están persistidos, por lo que no hay pérdida de información.

Finalmente, si el evento que se procesó tenía asociado un callback (`event.getCallback()`), se invoca al finalizar, pasándole el texto de la respuesta final del LLM. Esto permite que los componentes externos que inyectaron el evento (por ejemplo, una interfaz de usuario) puedan reaccionar cuando el agente ha terminado de procesarlo.

### 4.9. Resumen del flujo

En conjunto, el `eventDispatcher` implementa un ciclo de vida completo para cada estímulo que llega al agente:

1. **Captura**: se espera un evento sensorial.
2. **Inyección**: se transforma en mensajes de LangChain4j, diferenciando entre usuario (directo) y entorno (simulado).
3. **Persistencia**: se guarda el turno de observación.
4. **Razonamiento**: se consulta al modelo en un bucle que puede repetirse varias veces si se ejecutan herramientas.
5. **Respuesta**: se muestra el texto final al usuario.
6. **Consolidación**: si es necesario, se compacta la memoria a largo plazo.
7. **Reinicio**: se vuelve a esperar el siguiente evento.

Este flujo, aunque secuencial, es capaz de manejar la asincronía del mundo real gracias a la abstracción que proporciona `SensorsService` y al mecanismo de simulación de `pool_event`. El resultado es un agente que percibe su entorno, razona sobre él, actúa, y mantiene una conversación coherente a lo largo del tiempo, todo ello en un solo hilo de ejecución.

## 5. Gestión de herramientas

Las herramientas son el mecanismo mediante el cual el agente trasciende la pura conversación y actúa sobre el mundo: lee y escribe archivos, ejecuta comandos, consulta APIs externas, envía correos, programa alarmas, o recupera información de su propia memoria. El `ReasoningService` actúa como el gestor de estas capacidades, manteniendo un catálogo actualizado, sincronizando su estado con la configuración del usuario, y orquestando su ejecución cuando el modelo las solicita.

### 5.1. Registro y catálogo de herramientas

Cuando el servicio arranca, invoca a `getTools()` para obtener la lista de todas las herramientas que el agente puede utilizar. Esta lista se construye instanciando cada herramienta y pasándole la referencia al `Agent` (para que puedan acceder a configuración, rutas, persistencia, etc.). Algunas herramientas solo se añaden si la configuración proporciona las claves API necesarias (por ejemplo, `TavilyWebSearchTool` solo se incluye si hay una clave de Tavily configurada).

Cada herramienta se registra en un mapa interno (`availableTools`) junto con un flag `active`. Este flag determina si la herramienta está disponible para que el modelo la invoque. Por defecto, su valor se inicializa con `isAvailableByDefault()`, un método que cada herramienta implementa para indicar si debería estar activa en el primer arranque.

### 5.2. Sincronización con la configuración del usuario

El estado de activación de las herramientas no es estático. El usuario puede decidir, a través de la interfaz de configuración del agente, qué herramientas quiere tener habilitadas en cada momento. Esta preferencia se almacena en la configuración persistente del agente (`settings.json`) bajo la clave `reasoning/active_tools`, como una lista de elementos con nombre técnico de la herramienta y un flag booleano.

El método `refresh_available_tools()` se encarga de sincronizar el mapa interno con esta configuración. Recorre la lista persistida y, para cada herramienta que aparece en ella, ajusta su flag `active` al valor almacenado. Las herramientas que no figuran en la configuración conservan su estado por defecto.

Esta sincronización se ejecuta durante el arranque del servicio y también cuando se dispara la acción `REFRESH_REASONING_TOOLS`, permitiendo que los cambios en la configuración surtan efecto sin necesidad de reiniciar el agente.

### 5.3. Exposición al modelo

Cuando el `eventDispatcher` construye el contexto para enviar al LLM, necesita proporcionar una lista de especificaciones de herramientas (`ToolSpecification`) que el modelo puede invocar. Esta lista se genera a partir del mapa `availableTools`, filtrando:

- Las herramientas que están activas (`active == true`).
- Las herramientas que están permitidas por `AgentAccessControl` en el contexto actual.

Cada herramienta sabe cómo generar su propia especificación a través del método `getSpecification()`, que devuelve un objeto con el nombre, la descripción y el esquema JSON de sus parámetros. LangChain4j se encarga luego de serializar estas especificaciones en el formato que el modelo de lenguaje espera (por ejemplo, el formato de function calling de OpenAI).

### 5.4. Ejecución de herramientas

Cuando el modelo responde con una o más solicitudes de ejecución (`ToolExecutionRequest`), el `eventDispatcher` itera sobre ellas y, para cada una, invoca al método `executeTool()`.

Este método realiza una secuencia de operaciones:

**Localización de la herramienta**

Busca en el mapa `availableTools` la herramienta cuyo nombre coincida con el solicitado. Si no existe, devuelve un mensaje de error.

**Validación de seguridad**

Si la herramienta tiene un modo (`getMode()`) distinto de `MODE_READ` (es decir, `MODE_WRITE` o `MODE_EXECUTION`), y el control de acceso del agente requiere confirmación humana (`isHumanConfirmationRequired()`), se solicita autorización al usuario a través de `AgentConsole.confirm()`.

Este paso es crítico: el usuario puede denegar la ejecución, en cuyo caso la herramienta no se ejecuta y se devuelve un mensaje indicando la denegación. El modelo recibe ese mensaje como resultado de su llamada y puede reaccionar en consecuencia (por ejemplo, explicando al usuario que necesita permiso).

**Ejecución**

Si la validación supera, se invoca al método `execute()` de la herramienta, pasándole los argumentos en formato JSON (que la herramienta debe parsear internamente). La ejecución es síncrona y bloquea el hilo del `eventDispatcher` hasta que retorna. Esto es intencionado: el agente no debe procesar nuevos estímulos mientras está ocupado realizando una acción que puede ser costosa o que modifica el estado del sistema.

**Resultado**

El método devuelve una cadena de texto que puede ser:
- El resultado exitoso de la operación (por ejemplo, el contenido de un archivo leído, la confirmación de que se envió un correo).
- Un mensaje de error si algo falló durante la ejecución.
- Un mensaje de denegación si el usuario no autorizó la operación.

Este texto se convierte en un `ToolExecutionResultMessage` que se añade a la sesión y se persiste como un `Turn`. En la siguiente iteración del bucle interno, el modelo recibirá este resultado y podrá decidir si necesita ejecutar más herramientas o si ya puede responder al usuario.

### 5.5. Herramientas de memoria: un caso particular

Dentro del catálogo de herramientas, hay un subconjunto que se considera "de memoria" (tipo `TYPE_MEMORY`). Estas herramientas (como `lookup_turn` o `search_full_history`) no modifican el estado externo, sino que recuperan información de la propia base de datos de la conversación.

El `ReasoningService` las trata de forma especial en un solo aspecto: cuando se persiste el turno de ejecución de una herramienta de memoria, se le asigna el tipo `lookup_turn` en lugar de `tool_execution`. Esta distinción es puramente semántica y facilita la consulta posterior del historial, pero no afecta al flujo de ejecución.

### 5.6. Herramientas y la configuración de identidad

Hay un grupo de herramientas que no operan sobre el sistema de archivos ni sobre redes externas, sino sobre la propia identidad del agente: `ConsultEnvironTool`, `ListSkillsTool`, `LoadSkillTool`. Estas herramientas permiten al modelo acceder bajo demanda a la información densa que no se inyecta en el prompt de sistema por defecto (para ahorrar tokens). Su registro y activación siguen las mismas reglas que cualquier otra herramienta, pero su función es específicamente la de extender la "consciencia" del agente con conocimiento contextual que solo se carga cuando resulta relevante.

### 5.7. Herramientas y el ciclo de vida de la sesión

Un aspecto importante es que las herramientas no tienen estado propio que persista entre invocaciones (salvo que ellas mismas gestionen su propia persistencia). Cada ejecución es independiente y recibe todos los parámetros necesarios en la llamada. Esto simplifica el modelo de concurrencia y evita efectos secundarios no deseados entre distintas rondas de razonamiento.

La única excepción a esta regla son las herramientas que modifican el sistema de archivos: sus efectos persisten, obviamente, pero el `ReasoningService` no guarda ningún estado adicional sobre ellas. La responsabilidad de mantener la coherencia recae en la propia herramienta, que utiliza el sistema RCS integrado para mantener un historial de cambios y que invoca al `AgentAccessControl` para acceder a los recursos.

En conjunto, el sistema de herramientas de Noema equilibra dos necesidades contrapuestas: por un lado, ofrecer al modelo un amplio abanico de capacidades para que pueda ser útil; por otro, mantener la seguridad y el control en manos del usuario, que puede desactivar herramientas que no desea utilizar y debe confirmar explícitamente cualquier operación que pueda tener efectos destructivos.

## 6. La sesión activa (`Session`)

La `Session` es el componente que materializa la memoria de trabajo del agente. Mientras que `SourceOfTruth` almacena la conversación de forma inmutable y permanente, y `MemoryService` consolida el pasado lejano en resúmenes narrativos, la `Session` mantiene vivo el presente inmediato: los mensajes que acaban de intercambiarse, las herramientas que se han ejecutado en el turno actual, y toda la información que el modelo necesita tener a mano para responder con coherencia.

Su diseño responde a una tensión fundamental: el modelo de lenguaje tiene una ventana de contexto limitada (medida en tokens), pero la conversación puede extenderse indefinidamente. La `Session` es el punto donde se gestiona esa limitación, reteniendo solo lo necesario y colaborando con `MemoryService` para compactar el resto cuando se alcanza un umbral.

### 6.1. Estructura interna

La `Session` mantiene dos estructuras de datos que evolucionan en paralelo:

**La lista de mensajes (`messages`)**

Es una secuencia ordenada de objetos `ChatMessage` de LangChain4j. Esta lista es la fuente de verdad para el historial inmediato que se enviará al modelo. Contiene todo tipo de mensajes:

- `UserMessage`: entradas del usuario (directas) o simulaciones de llamadas a herramientas (`pool_event`).
- `AiMessage`: respuestas del modelo, incluyendo tanto texto como solicitudes de ejecución de herramientas.
- `ToolExecutionResultMessage`: resultados de herramientas ejecutadas.
- `SystemMessage`: ocasionalmente, aunque en la `Session` no se almacena el prompt de sistema (se construye dinámicamente en cada consulta).

Cada vez que se produce una interacción (un mensaje del usuario, una respuesta del modelo, un resultado de herramienta), se añade un nuevo elemento al final de esta lista.

**El mapa de trazabilidad (`turnOfMessage`)**

Este mapa asocia cada posición en la lista de mensajes con el identificador del `Turn` persistido que originó ese mensaje. No todos los mensajes tienen un turno asociado inmediatamente: cuando se añade un mensaje por primera vez, aún no se ha persistido el turno correspondiente. Es en el momento de `consolideTurn()` cuando se establece la asociación.

La clave del mapa es el índice en la lista (`Integer`), y el valor es un objeto `ChatMessageInfo` que contiene, por ahora, únicamente el `turnId`. Este diseño permite:

- Saber qué parte del historial ya ha sido persistida y qué parte es aún efímera.
- Identificar, durante la compactación, qué mensajes comparten el mismo turno para no romper bloques semánticos.
- Trazar desde un mensaje en memoria hasta su registro inmutable en la base de datos.

### 6.2. El ciclo de vida de un mensaje: backfill

Cuando se añade un mensaje a la sesión (mediante `add()`), simplemente se coloca al final de la lista. El mapa `turnOfMessage` no se actualiza en ese momento porque aún no se ha persistido el turno.

La asociación se establece más tarde, cuando se llama a `consolideTurn(Turn turn)`. Este método recibe el turno que acaba de persistirse en `SourceOfTruth` y realiza una operación de **backfill**: recorre la lista de mensajes desde el final hacia atrás, asignando el `turnId` a todos los mensajes que aún no tienen un turno asociado, hasta que encuentra uno que ya lo tiene.

Este mecanismo es clave para entender la relación entre la sesión y la persistencia. Por ejemplo, cuando el modelo responde con texto, ocurre lo siguiente:

1. Se añade el `AiMessage` con el texto a la sesión (`add()`).
2. Se persiste el turno correspondiente en `SourceOfTruth`.
3. Se llama a `consolideTurn()` con ese turno, que asignará el `turnId` al `AiMessage` recién añadido y también a cualquier mensaje anterior que pudiera haber quedado sin consolidar (por ejemplo, si una herramienta se ejecutó y su resultado aún no tenía turno asignado).

De esta forma, la sesión mantiene siempre una trazabilidad completa hacia los turnos persistidos, aunque la consolidación ocurra de forma diferida.

### 6.3. Construcción del contexto para el modelo

El método `getContextMessages()` es el responsable de ensamblar el bloque de mensajes que se enviará al LLM en cada consulta. Su implementación refleja la estrategia de gestión de contexto del agente:

**Prompt de sistema**

Si se proporciona un `systemPrompt` (que normalmente es el resultado de `getBaseSystemPrompt()`), se añade como primer elemento de la lista. Este prompt contiene la identidad del agente, las reglas operativas, y los índices de referencia del entorno. Es el mismo para todas las consultas de una sesión, aunque puede cambiar si se recarga la configuración.

**CheckPoint histórico**

Si existe un `activeCheckPoint` (es decir, la memoria consolidada de conversaciones anteriores), se añade un `SystemMessage` que contiene su resumen. Este resumen se presenta como un bloque de texto que comienza con "--- INICIO DEL RELATO ---" y termina con "--- FIN DEL RELATO ---", dejando claro al modelo que se trata de información consolidada del pasado, no de la conversación inmediata.

**Mensajes de la sesión**

A continuación se añaden todos los mensajes almacenados en la lista `messages`. Estos representan el historial inmediato, desde el último punto de compactación hasta el momento actual.

**Percepción temporal pasiva**

Antes de devolver la lista completa, `getContextMessages()` comprueba si ha pasado más de una hora desde la última interacción (almacenada en `lastInteractionTime`). Si es así, y además el último mensaje de la sesión es de tipo `UserMessage` (es decir, la última actividad fue del usuario), se inyecta un evento de sensor de reloj. Este evento se añade como dos mensajes consecutivos: un `AiMessage` simulando la llamada a `pool_event` y un `ToolExecutionResultMessage` con el texto "Ha pasado [tiempo] desde la última interacción con el usuario". El modelo recibe así una señal temporal que le permite contextualizar su respuesta (por ejemplo, saludar al usuario tras una larga ausencia).

### 6.4. Compactación: marcas y eliminación

La `Session` proporciona la infraestructura para que el `ReasoningService` pueda identificar qué parte del historial debe compactarse y eliminarse. Para ello expone tres métodos fundamentales:

**`getOldestMark()`**

Devuelve una `SessionMark` correspondiente al mensaje más antiguo de la sesión que tiene un `turnId` asociado. Es el punto de inicio del bloque a compactar. Si no hay ningún mensaje consolidado, devuelve `null`.

**`getCompactMark()`**

Determina el punto de corte para la compactación. La estrategia actual es sencilla: toma la mitad de la lista de mensajes (`size() / 2`) y ajusta hacia atrás hasta encontrar un mensaje consolidado. Luego avanza hasta el final del bloque del mismo `turnId` para no romper la secuencia de un mismo turno (que puede constar de varios mensajes: llamada a herramienta, resultado, etc.). Este punto de corte asegura que la compactación afecte aproximadamente a la mitad más antigua de la sesión.

**`remove(SessionMark mark1, SessionMark mark2)`**

Elimina de la sesión todos los mensajes comprendidos entre `mark1` y `mark2` (inclusive). La operación es delicada porque hay que reindexar el mapa `turnOfMessage` para los mensajes que quedan. El método:

- Ordena las marcas para asegurar que `mark1` es el índice menor.
- Calcula el desplazamiento (`offset = idx2 - idx1 + 1`).
- Crea un nuevo mapa donde los mensajes anteriores al corte conservan su índice original.
- Los mensajes posteriores al corte se insertan en el nuevo mapa con su índice reducido en `offset`.
- Finalmente, elimina físicamente los mensajes de la lista `messages` y sustituye el mapa antiguo por el nuevo.

Esta operación es atómica desde la perspectiva de la sesión: una vez ejecutada, los mensajes compactados desaparecen y no volverán a formar parte del contexto.

### 6.5. Umbral de compactación

La decisión de cuándo compactar se basa en `needCompaction()`. Este método:

- Recoge todos los valores únicos de `turnId` en el mapa `turnOfMessage`.
- Si el número de turnos únicos supera un umbral configurable, devuelve `true`.

El umbral se lee de la configuración bajo la clave `memory/compaction_turns`. Si no está definido, se establece un valor por defecto de 40 turnos. La elección de un umbral basado en número de turnos, y no en tokens, es una simplificación intencionada. Es una limitación conocida: en conversaciones con herramientas que devuelven grandes volúmenes de texto, el contexto podría saturarse antes de alcanzar el umbral de turnos. Una mejora futura podría combinar ambos criterios.

### 6.6. Persistencia de la sesión

La sesión no es solo un objeto en memoria: se serializa a disco tras cada modificación. El archivo `active_session.json` en el directorio de datos del agente contiene una representación completa de la lista de mensajes y el mapa de trazabilidad.

El mecanismo de guardado utiliza un patrón de escritura atómica para evitar corrupción:

1. Se escribe el estado en un archivo temporal (`active_session.json.tmp`).
2. Se mueve (renombra) el temporal al archivo definitivo mediante `Files.move()` con la opción `ATOMIC_MOVE`.

Si la JVM falla durante la escritura, el archivo temporal puede quedar incompleto, pero el definitivo conserva la versión anterior válida. Esto garantiza que, al reiniciar, el agente recupere siempre un estado consistente, aunque pueda perder la última operación que no llegó a completarse.

La serialización utiliza Gson con adaptadores personalizados para manejar los tipos polimórficos de LangChain4j (`ChatMessage` y `Content`). Cada mensaje se guarda con un campo `type` que indica su clase concreta, permitiendo la deserialización correcta.

### 6.7. Acceso externo

La `Session` no es un componente público del agente; es interna al `ReasoningService`. Sin embargo, el servicio expone sus mensajes a través del método `getMessages()` (utilizado principalmente para depuración) y proporciona la funcionalidad de contexto a través de `getContextMessages()`. El resto de la interacción con la sesión ocurre exclusivamente dentro del `eventDispatcher`, siguiendo el flujo descrito en la sección anterior.

En resumen, la `Session` es el puente entre la inmediatez de la conversación y la persistencia duradera. Su diseño permite mantener en memoria solo lo necesario para el siguiente turno, compactar el pasado cuando se acumula demasiado, y recuperar el estado exacto tras un reinicio, todo ello sin que el modelo de lenguaje tenga que gestionar explícitamente los límites de su propia ventana de contexto.

## 7. Compactación de memoria

La compactación es el mecanismo mediante el cual el agente traslada información de la memoria de trabajo (la sesión activa) a la memoria a largo plazo (los puntos de control). Responde a una limitación fundamental de los modelos de lenguaje actuales: su ventana de contexto es finita. Por muy grande que sea (y las ventanas de millones de tokens ya existen), siempre habrá un límite. La compactación no intenta eliminar ese límite, sino gestionarlo de forma inteligente, preservando lo esencial y descartando lo redundante.

En Noema, la compactación es un proceso colaborativo entre el `ReasoningService`, que detecta cuándo es necesaria y proporciona los datos de entrada, y el `MemoryService`, que realiza la transformación narrativa. Esta separación de responsabilidades permite que la lógica de compactación pueda evolucionar independientemente del bucle principal.

### 7.1. Cuándo se dispara la compactación

La compactación no ocurre en un momento arbitrario. Se dispara al final de cada turno, después de que el modelo haya entregado una respuesta textual y se haya cerrado la interacción. En ese punto, el `eventDispatcher` evalúa `session.needCompaction()`.

El criterio actual es simple: la sesión necesita compactación cuando el número de turnos únicos acumulados en ella supera un umbral configurable. Este umbral se almacena en la configuración bajo la clave `reasoning/compaction_turns`, con un valor por defecto de 40 turnos.

La elección de un umbral basado en número de turnos (y no en tokens estimados) es una simplificación deliberada. En la práctica, funciona razonablemente bien para la mayoría de las conversaciones, pero tiene limitaciones conocidas: si un turno incluye una herramienta que devuelve grandes volúmenes de texto (por ejemplo, el contenido de un archivo extenso), el contexto puede saturarse antes de alcanzar el umbral. Una mejora futura podría combinar ambos criterios.

### 7.2. El proceso de compactación

Cuando se cumple la condición, el `ReasoningService` invoca a `performCompaction()`. Este método ejecuta una secuencia de operaciones cuidadosamente ordenada:

**1. Obtención de las marcas de sesión**

Se recuperan dos marcas de la sesión:
- `oldestMark`: el mensaje más antiguo consolidado (el que tiene un `turnId` asociado).
- `compactMark`: el punto de corte, que se calcula aproximadamente en la mitad de la sesión, ajustado para no romper un turno por la mitad.

Si alguna de estas marcas es `null`, significa que no hay suficientes mensajes consolidados para compactar, y el proceso aborta.

**2. Recuperación de los turnos a compactar**

Con los identificadores de turno de ambas marcas, se consulta a `SourceOfTruth` para obtener todos los turnos comprendidos en ese rango. La lista incluye tanto los turnos de usuario como los de ejecución de herramientas y respuestas del modelo.

**3. Generación del nuevo punto de control**

Se invoca a `MemoryService.compact()`, pasándole:
- El último punto de control existente (`activeCheckPoint`, que puede ser `null` si es la primera compactación).
- La lista de turnos recuperados.

`MemoryService` utiliza un modelo de lenguaje (puede ser el mismo que el agente o uno más económico) para generar un nuevo `CheckPoint`. Este contiene dos elementos:
- Un resumen narrativo que captura la esencia de la conversación compactada.
- Un texto de "El Viaje" que preserva la cronología de los eventos con mayor detalle.

La generación del punto de control es una operación potencialmente costosa, ya que implica una o varias llamadas al LLM. Se ejecuta dentro del hilo del `eventDispatcher`, bloqueando el procesamiento de nuevos eventos hasta que finaliza. Esto es intencionado: la compactación es parte del turno que acaba de terminar, y no deben llegar nuevos estímulos hasta que la memoria esté consolidada.

**4. Persistencia del nuevo punto de control**

El `CheckPoint` generado se añade a `SourceOfTruth` mediante `add()`. La base de datos H2 almacena el punto de control junto con su marca de tiempo y el identificador del turno más reciente que incluye.

**5. Limpieza de la sesión**

Con el nuevo punto de control ya persistido, se invoca a `session.remove(oldestMark, compactMark)`. Este método elimina de la sesión todos los mensajes comprendidos entre las dos marcas, liberando memoria y reduciendo el tamaño del contexto que se enviará en futuras consultas.

**6. Actualización del puntero activo**

Finalmente, `activeCheckPoint` se actualiza al nuevo punto de control. En la siguiente construcción de contexto, `getContextMessages()` incluirá este resumen en lugar del anterior.

### 7.3. El papel de `MemoryService`

El `ReasoningService` no conoce los detalles de cómo se genera el punto de control. Esta separación es deliberada: permite que la estrategia de compactación pueda modificarse sin afectar al orquestador principal.

`MemoryService` expone un único método relevante para este proceso:

```java
CheckPoint compact(CheckPoint previous, List<Turn> turns)
```

### 7.4. Implicaciones para el modelo

Desde la perspectiva del modelo de lenguaje, la compactación es invisible. Cuando se construye el contexto, el punto de control aparece como un bloque de texto con el formato:

```
--- INICIO DEL RELATO ---
[contenido del resumen]
--- FIN DEL RELATO ---
```

El modelo recibe esta información como un mensaje de sistema, junto con los mensajes de la sesión activa. No sabe que el resumen es el resultado de una compactación; simplemente lo trata como contexto histórico. Esto mantiene la simplicidad del prompt y evita que el modelo tenga que adaptarse a un formato especial.

## 8. Percepción temporal pasiva

El agente no solo reacciona a estímulos explícitos, mensajes del usuario, notificaciones, alarmas, sino que también es consciente del paso del tiempo. Esta percepción temporal no depende de un sensor activo que emita eventos periódicos; es un mecanismo pasivo que se activa cuando el agente va a construir el contexto para el modelo, justo antes de cada consulta al LLM.

Su objetivo es simple pero potente: si ha transcurrido un lapso significativo desde la última interacción, el agente informa al modelo de esa circunstancia. De esta forma, cuando el usuario retoma una conversación que había quedado suspendida horas o días atrás, el modelo puede contextualizar su respuesta, saludar adecuadamente, o retomar el hilo con la conciencia de que ha pasado tiempo.

### 8.1. El mecanismo de inyección

La percepción temporal se materializa dentro del método `getContextMessages()` de `Session`. Durante la construcción del contexto que se enviará al modelo, el método realiza las siguientes comprobaciones:

1. **Consulta la última interacción**: mantiene un campo `lastInteractionTime` que se actualiza cada vez que se construye el contexto (es decir, cada vez que el agente está a punto de razonar).

2. **Comprueba el tipo del último mensaje**: solo inyecta la percepción temporal si el último mensaje en la sesión es un `UserMessage`. Esto asegura que el sensor de tiempo se active después de una interacción humana, no después de una respuesta del propio agente o de una ejecución de herramienta.

3. **Calcula el tiempo transcurrido**: si la diferencia entre el momento actual y `lastInteractionTime` supera una hora (el umbral está fijado en 60 minutos), se procede a la inyección.

4. **Genera el mensaje temporal**: se crea un evento de sensor simulado con el texto "Ha pasado [tiempo] desde la última interacción con el usuario", donde `[tiempo]` se expresa en un formato legible (por ejemplo, "2 horas", "3 días").

5. **Añade el evento a la sesión**: al igual que cualquier otro evento sensorial, este se añade como dos mensajes consecutivos: un `AiMessage` que simula una llamada a `pool_event` (para mantener la coherencia del historial) y un `ToolExecutionResultMessage` que contiene el texto del evento.

Una vez añadidos estos mensajes, el contexto que recibe el modelo incluye la información temporal como si el propio agente hubiera consultado sus sensores y hubiera obtenido esa lectura.

### 8.2. Por qué es pasiva

El término "pasiva" distingue este mecanismo de un sensor activo que emitiría eventos periódicos independientemente de la actividad del agente. Un enfoque activo requeriría:

- Un hilo separado que generara eventos cada cierto tiempo.
- Gestión de concurrencia para no interferir con el bucle principal.
- Decidir qué hacer con esos eventos si el agente está procesando otro estímulo.

El enfoque pasivo evita toda esta complejidad. No hay hilos adicionales, no hay colas de eventos saturándose con ticks de reloj, no hay riesgo de que el modelo reciba decenas de notificaciones de tiempo mientras el usuario no está interactuando. La percepción temporal solo ocurre cuando el agente va a responder, y solo si el usuario ha estado ausente.

### 8.3. El formato del mensaje

El texto inyectado es deliberadamente simple y directo: "Ha pasado X desde la última interacción con el usuario". No se añade información adicional sobre la hora actual, la fecha, o cualquier otro metadato temporal que el modelo podría deducir de su propio conocimiento del mundo (o de otras herramientas como `get_current_time` si las tuviera activas).

La elección del formato busca dos cosas:

- **Minimizar tokens**: el mensaje añade muy poco overhead al contexto.
- **Dejar la interpretación al modelo**: es el LLM quien decide cómo reaccionar ante esa información. Puede optar por saludar, por retomar un tema anterior, por preguntar si el usuario ha tenido un buen descanso, o simplemente ignorarlo si no es relevante.

### 8.4. El umbral de una hora

La elección de una hora como umbral es empírica. Es suficientemente larga como para no activarse en pausas breves dentro de una conversación fluida, pero suficientemente corta como para que el modelo pueda detectar ausencias significativas.

El umbral no es actualmente configurable, aunque podría serlo en el futuro si se identifican casos de uso que requieran una sensibilidad temporal diferente (por ejemplo, un agente de monitorización que necesita ser consciente de lapsos de minutos, o un asistente personal que solo necesita marcar ausencias de días).

### 8.5. Relación con el sensor de reloj del sistema

Además de este mecanismo pasivo, el agente dispone de un sensor activo (`SYSTEMCLOCK_SENSOR_NAME`) que puede inyectar eventos de tiempo cuando se cumplen condiciones específicas (por ejemplo, una alarma programada). La diferencia fundamental es:

- El **sensor de reloj activo** se utiliza para despertar al agente en un momento concreto y ejecutar una acción programada (por ejemplo, "recuérdame revisar el correo en 30 minutos").
- La **percepción temporal pasiva** solo añade contexto cuando el agente ya va a responder, informándole de que ha pasado tiempo desde la última interacción humana.

Ambos mecanismos coexisten y se complementan. El primero da al agente capacidad de acción autónoma en momentos concretos; el segundo le da conciencia situacional sobre el contexto temporal de la conversación.

### 8.6. Implicaciones para la experiencia de usuario

Desde la perspectiva del usuario, este mecanismo contribuye a la sensación de que el agente "está presente" incluso cuando no se le habla. Si se retoma una conversación horas después, el agente puede saludar con naturalidad, retomar el hilo, o incluso comentar el tiempo transcurrido sin que el usuario tenga que recordarle dónde se quedaron.

Es un pequeño detalle, pero refuerza la ilusión de continuidad y consciencia que caracteriza a un agente autónomo frente a un simple procesador de comandos. El usuario no necesita decir "sigo donde estábamos ayer"; el agente ya lo sabe porque ha percibido el paso del tiempo.

## 9. Seguridad y control de acceso

El agente Noema tiene la capacidad de ejecutar comandos en el sistema, modificar archivos y acceder a recursos externos. Estas capacidades son necesarias para que sea útil, pero también representan riesgos potenciales. El sistema de seguridad está diseñado para equilibrar dos objetivos: dar al agente suficiente autonomía para realizar tareas complejas, y mantener al usuario en control de las operaciones que podrían tener efectos destructivos o invasivos.

La seguridad se implementa en dos niveles: un control de acceso estructural que define qué está permitido en cada contexto, y un mecanismo de confirmación humana que requiere autorización explícita para operaciones sensibles.

### 9.1. `AgentAccessControl`: la política de permisos

`AgentAccessControl` es el componente que define qué recursos puede tocar el agente y en qué condiciones. No es parte del `ReasoningService`, sino un servicio independiente que este consulta antes de ejecutar cualquier herramienta.

Su responsabilidad principal es gestionar el **acceso al sistema de archivos**. El agente opera dentro de un espacio de trabajo (workspace) que contiene su configuración, sus bases de datos y sus archivos de trabajo. Por defecto, todas las operaciones de lectura y escritura están restringidas a este espacio. Sin embargo, muchas tareas útiles requieren acceder a archivos fuera del workspace: leer un documento en el directorio del usuario, escribir un informe en el escritorio, etc.

Para ello, `AgentAccessControl` mantiene listas de rutas permitidas:

- **Rutas de lectura permitidas**: directorios o archivos específicos a los que el agente puede acceder aunque estén fuera del workspace.
- **Rutas de escritura permitidas**: directorios donde el agente puede crear o modificar archivos fuera del workspace.
- **Rutas explícitamente prohibidas**: incluso dentro de áreas permitidas, ciertas rutas pueden estar bloqueadas (por ejemplo, directorios de sistema críticos).

La configuración de estas listas se almacena en `settings.json` y puede ser modificada por el usuario. Esto permite, por ejemplo, dar al agente acceso a la carpeta `Documentos` para leer archivos, pero prohibirle escribir en `Documentos/Finanzas` si se considera una zona sensible.

Además del control de rutas, `AgentAccessControl` puede restringir herramientas específicas en función del contexto, aunque esta capacidad está menos desarrollada en la implementación actual.

### 9.2. El modo de las herramientas

Cada herramienta implementa el método `getMode()`, que devuelve uno de tres valores:

- **`MODE_READ`**: operaciones que solo leen información y no modifican el estado del sistema. Leer un archivo, consultar una API, buscar en el historial. Estas herramientas no requieren confirmación humana (aunque pueden estar restringidas por `AgentAccessControl`).
- **`MODE_WRITE`**: operaciones que modifican archivos o configuración. Escribir un archivo, aplicar un parche, mover o eliminar. Estas herramientas requieren confirmación humana.
- **`MODE_EXECUTION`**: operaciones que ejecutan comandos en el sistema operativo. Son las más peligrosas y siempre requieren confirmación humana.

Esta clasificación es declarativa: es el desarrollador de la herramienta quien asigna el modo basándose en lo que la herramienta hace. Un error en esta clasificación podría llevar a que una herramienta peligrosa se ejecute sin confirmación, por lo que la revisión de los modos es parte del control de calidad del código.

### 9.3. Confirmación humana

Cuando el `eventDispatcher` recibe una solicitud de ejecución de herramienta, y esa herramienta tiene un modo distinto de `MODE_READ`, se activa el mecanismo de confirmación:

1. **Verificación de requisito**: se consulta a `AgentAccessControl.isHumanConfirmationRequired()`. Si esta condición es `false` (por ejemplo, en entornos headless o en modo de confianza total), la confirmación se omite.

2. **Solicitud al usuario**: se invoca a `AgentConsole.confirm()` con un mensaje que describe la herramienta y los argumentos que se van a ejecutar. El mensaje tiene un formato claro: "El agente quiere ejecutar la herramienta: [nombre]\nArgumentos: [argumentos]\n¿Autorizar?"

3. **Espera de respuesta**: la llamada a `confirm()` es bloqueante. La implementación de `AgentConsole` determina cómo se presenta la solicitud al usuario: puede ser un diálogo modal en la interfaz gráfica, una pregunta en la línea de comandos, o incluso una respuesta automática en entornos de prueba.

4. **Decisión**: si el usuario responde afirmativamente, la herramienta se ejecuta. Si deniega, se devuelve un mensaje de error que se inyecta en la conversación como resultado de la herramienta, y el modelo recibe ese mensaje en lugar del resultado real.

La confirmación no es un simple "sí/no". El mensaje incluye los argumentos exactos que la herramienta va a utilizar, lo que permite al usuario evaluar el riesgo. Por ejemplo, si la herramienta `file_write` va a sobrescribir un archivo importante, el usuario puede ver la ruta y decidir si lo permite.

### 9.4. La abstracción `AgentConsole`

La confirmación humana depende de `AgentConsole`, una interfaz que desacopla al `ReasoningService` del mecanismo concreto de interacción con el usuario. `AgentConsole` define métodos para:

- Mostrar mensajes del sistema (`printSystemLog`, `printSystemError`).
- Mostrar respuestas del modelo (`printModelResponse`).
- Solicitar confirmación (`confirm`).

Las implementaciones de `AgentConsole` pueden ser muy diferentes:

- **`SwingConsole`**: muestra los mensajes en una ventana gráfica con áreas de texto y utiliza diálogos modales para las confirmaciones.
- **`TerminalConsole`**: imprime en la salida estándar y lee de la entrada estándar (usando JLine3 para manejar edición multilínea).
- **`HeadlessConsole`**: implementación "tonta" que registra los mensajes pero no interactúa con el usuario, devolviendo respuestas predeterminadas (por ejemplo, denegar todas las confirmaciones).

Esta abstracción es fundamental: el `ReasoningService` no necesita saber si está ejecutándose en un entorno gráfico, en una terminal o sin interfaz. Simplemente llama a `console.confirm()` y la implementación concreta resuelve cómo interactuar con el humano.

### 9.5. Seguridad en las operaciones de archivo

Además de los mecanismos generales, las herramientas de manipulación de archivos incorporan capas adicionales de seguridad:

**Control de versiones automático**: antes de modificar un archivo, las herramientas (`file_write`, `file_patch`, `file_search_and_replace`) invocan al sistema RCS (Revision Control System) integrado para hacer un commit de la versión actual. Esto permite recuperar el estado anterior si la modificación tiene efectos no deseados, y proporciona un historial de cambios completo.

**Validación de rutas**: todas las rutas que el agente intenta leer o escribir pasan por `AgentAccessControl.resolvePath()`, que:
- Normaliza la ruta (resuelve `..`, elimina duplicados).
- Verifica que no intente salir del workspace a menos que esté en una lista de permitidas.
- Comprueba que la ruta no esté en la lista de prohibidas.
- Aplica restricciones adicionales según la operación (lectura vs escritura).

**Prohibición de escritura en áreas críticas**: ciertas rutas están bloqueadas por completo, independientemente de las listas de permitidas. Por ejemplo, las carpetas de configuración del agente (`var/config`, `var/identity`) no pueden ser modificadas por herramientas de escritura para evitar que el agente altere su propia personalidad sin supervisión.

### 9.6. Ejecución de comandos: el entorno restringido

La herramienta `shell_execute` es particularmente sensible porque permite ejecutar cualquier comando en el sistema operativo. Para mitigar riesgos, incorpora varias protecciones:

- **Confirmación humana obligatoria**: es una herramienta `MODE_EXECUTION`, por lo que siempre requiere autorización explícita.
- **Sandboxing con firejail**: si el sistema tiene instalado `firejail`, la herramienta envuelve el comando en un entorno restringido que limita el acceso a archivos, red y procesos.
- **Captura de salida**: la salida estándar y de error se capturan en archivos temporales, evitando que el comando pueda interactuar directamente con el terminal del usuario.
- **Timeout configurable**: los comandos tienen un límite de tiempo de ejecución para evitar que un proceso colgado bloquee al agente.

### 9.7. Filosofía de seguridad

El enfoque de seguridad de Noema se puede resumir en unos pocos principios:

- **Confianza por defecto, confirmación por excepción**: las operaciones de lectura no requieren confirmación; las escrituras y ejecuciones sí. Esto permite que el agente sea autónomo en tareas seguras sin molestar al usuario.
- **El usuario es el árbitro final**: ninguna operación peligrosa puede ejecutarse sin autorización explícita, y el usuario tiene la opción de denegar en cada caso.
- **Trazabilidad**: todas las herramientas registran lo que hacen, y el sistema de control de versiones permite deshacer cambios. Incluso si el usuario autoriza una operación que resulta dañina, hay camino de retorno.
- **Separación de poderes**: el `ReasoningService` no toma decisiones de seguridad; consulta a `AgentAccessControl` y a `AgentConsole`. Esto permite cambiar las políticas sin tocar el núcleo del agente.

Esta arquitectura reconoce una realidad fundamental: un agente autónomo, por muy bien diseñado que esté, puede cometer errores o ser manipulado. La seguridad no consiste en impedir que actúe, sino en asegurar que cada acción que pueda tener consecuencias irreversibles cuente con la supervisión humana. Es un equilibrio entre autonomía y control que, hasta ahora, ha demostrado ser práctico y efectivo.


## 10. Puntos de diseño y limitaciones conocidas

El `ReasoningService` de Noema es el resultado de un proceso iterativo de diseño, donde cada decisión ha buscado un equilibrio entre funcionalidad, simplicidad y robustez. Como en cualquier sistema complejo, algunas de esas decisiones introducen limitaciones que merecen ser documentadas explícitamente. Esta sección recoge tanto los principios que guiaron el diseño como las áreas donde se sabe que el sistema actual podría mejorar.

### 10.1. El modelo de un solo hilo

**Decisión de diseño**: el `eventDispatcher` se ejecuta en un único hilo de plataforma, procesando los eventos de forma secuencial y bloqueante.

**Justificación**: esta arquitectura elimina toda complejidad de concurrencia. No hay condiciones de carrera, no hay necesidad de sincronización entre múltiples hilos que acceden a la sesión, y el flujo de ejecución es completamente determinista. Cada evento se procesa hasta completar todas las rondas de razonamiento antes de pasar al siguiente, lo que garantiza que la sesión nunca queda en un estado intermedio.

**Limitación**: la ejecución de herramientas que son lentas (por ejemplo, una búsqueda web que tarda varios segundos) bloquea todo el agente. Durante ese tiempo, no se atienden nuevos eventos. En la práctica, esto rara vez es un problema porque el agente no puede hacer dos cosas a la vez de todos modos, pero podría serlo si se implementaran herramientas de larga duración que requirieran procesamiento en paralelo.

### 10.2. Compactación basada en número de turnos

**Decisión de diseño**: el umbral de compactación se mide en número de turnos (40 por defecto), no en tokens estimados.

**Justificación**: medir tokens requeriría estimar el tamaño de cada mensaje antes de compactar, lo que añade complejidad y llamadas adicionales al modelo de lenguaje. El número de turnos es un proxy razonablemente bueno para la longitud de la conversación en la mayoría de los casos, y es mucho más simple de implementar.

**Limitación**: conversaciones con herramientas que devuelven grandes volúmenes de texto (por ejemplo, leer un archivo de miles de líneas) pueden saturar la ventana de contexto mucho antes de alcanzar los 40 turnos. Por el contrario, conversaciones muy largas pero con mensajes muy cortos podrían acumular muchos más turnos antes de necesitar compactación. Una mejora futura sería combinar ambos criterios, compactando cuando se supere un umbral de turnos **o** un umbral de tokens estimados.

### 10.3. La simulación de `pool_event` y el TODO pendiente

**Decisión de diseño**: los eventos del entorno se inyectan en la sesión mediante un par de mensajes que simulan una llamada a la herramienta `pool_event` (un `AiMessage` seguido de un `ToolExecutionResultMessage`). Esto mantiene la coherencia del historial desde la perspectiva del modelo.

**Justificación**: el modelo de lenguaje opera en un flujo síncrono (usuario -> IA -> herramienta -> IA). Los eventos asíncronos del entorno no encajan en este modelo. La simulación resuelve el problema haciendo que cada evento parezca el resultado de una decisión del propio agente.

**Limitación conocida**: el código contiene un `TODO` que advierte de un posible fallo cuando el primer mensaje que se envía al LLM es una llamada simulada a `pool_event`. En ciertas condiciones (probablemente relacionadas con la inicialización del modelo o con la ausencia de un mensaje de usuario previo), esta llamada podría fallar. No se ha reproducido sistemáticamente, pero la advertencia permanece como una espina clavada que eventualmente habrá que investigar.

### 10.4. Reintentos de herramientas no formalizadas

**Decisión de diseño**: cuando el modelo devuelve `FinishReason.TOOL_EXECUTION` pero no hay solicitudes de herramientas en la respuesta, el bucle inyecta un mensaje de usuario que pide reintentar la llamada, con un límite de tres intentos.

**Justificación**: algunos modelos de lenguaje, especialmente los de código abierto o configuraciones no estándar, pueden anunciar que van a usar una herramienta pero no generarla en el formato esperado por LangChain4j. El reintento es un parche pragmático para mantener la conversación fluyendo sin que el agente se quede bloqueado.

**Limitación**: es una solución artesanal que no resuelve la causa raíz. Depende de que el modelo entienda el mensaje de reintento, lo que no siempre ocurre. Además, tres reintentos pueden ser insuficientes o excesivos según el modelo. Un enfoque más robusto requeriría un análisis más fino del formato de respuesta del modelo.

### 10.5. El uso de hilos de plataforma en lugar de virtuales

**Decisión de diseño**: el `eventDispatcher` se ejecuta en un hilo de plataforma (`Thread.ofPlatform()`), no en un hilo virtual (`Thread.ofVirtual()`).

**Justificación**: inicialmente se utilizaron hilos virtuales, pero se encontraron problemas durante la depuración (posiblemente relacionados con la integración con Swing o con el propio depurador). Se revirtió a hilos de plataforma para estabilidad. Dado que solo hay un hilo principal y unos pocos hilos auxiliares efímeros, la ventaja de los hilos virtuales en este contexto es marginal.

**Limitación**: no es una limitación funcional, sino una decisión pragmática. Si en el futuro se introdujeran múltiples hilos de procesamiento o herramientas que requirieran un gran número de hilos concurrentes, habría que reconsiderar esta elección.

### 10.6. La dependencia de `AgentConsole` para confirmaciones

**Decisión de diseño**: la confirmación humana se realiza a través de `AgentConsole.confirm()`, una interfaz que puede tener diferentes implementaciones.

**Justificación**: es una abstracción limpia que desacopla al `ReasoningService` de la interfaz de usuario concreta. Permite que el mismo código funcione en modo gráfico, en terminal o en entornos headless.

**Limitación**: la confirmación es bloqueante. Mientras el usuario decide, el agente no procesa nuevos eventos. Esto es correcto desde la perspectiva de seguridad, pero puede ser frustrante si el usuario tarda en responder. No hay un mecanismo de timeout que permita al agente continuar después de un tiempo de espera.

### 10.7. El prompt de sistema se reconstruye en cada consulta

**Decisión de diseño**: `getBaseSystemPrompt()` se invoca cada vez que se construye el contexto, aunque el resultado se cachea en `lastestSystemPrompt`.

**Justificación**: el prompt de sistema puede cambiar en caliente si el usuario modifica la configuración de identidad (activando o desactivando módulos). Reconstruirlo desde cero cada vez asegura que el agente use siempre la configuración más reciente.

**Limitación**: la reconstrucción tiene un costo, aunque es pequeño (concatenación de cadenas, lectura de archivos). 

### 10.8. Ausencia de monitorización de tokens en tiempo real

**Decisión de diseño**: el servicio estima el tamaño del contexto (`estimateMessagesTokenCount()`, `estimateToolsTokenCount()`) pero no utiliza esta información para decisiones en tiempo real (por ejemplo, para compactar antes de que el contexto exceda un límite).

**Justificación**: la estimación de tokens es una operación que implica llamar al modelo de lenguaje (LangChain4j proporciona métodos para ello, pero internamente pueden requerir tokenizadores específicos). Hacerlo en cada iteración añadiría overhead. Además, el límite de contexto de los modelos actuales es lo suficientemente grande (128K o 1M tokens) como para que el umbral de 40 turnos sea un límite más restrictivo en la práctica.

**Limitación**: con modelos de ventana pequeña (por ejemplo, 8K tokens) o con herramientas que devuelven grandes cantidades de texto, esta estrategia puede fallar. Es una mejora pendiente para entornos más restrictivos.

### 10.9. La persistencia de la sesión es por modificación, no por tiempo

**Decisión de diseño**: la sesión se guarda en disco cada vez que se modifica (al añadir un mensaje, al consolidar un turno, al eliminar mensajes compactados).

**Justificación**: garantiza que, tras cualquier interrupción, el estado recuperado sea el último antes de la operación actual. El uso de escritura atómica evita corrupción.

**Limitación**: en sesiones muy largas con cientos de mensajes, el archivo `active_session.json` puede crecer considerablemente, y cada escritura es una operación de E/S que ralentiza el procesamiento. Una mejora posible sería utilizar un formato más compacto (por ejemplo, un diario de operaciones) o diferir las escrituras a intervalos regulares.

