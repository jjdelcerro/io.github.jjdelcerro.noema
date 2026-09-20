# Informe de Arquitectura y Análisis Técnico de Noema

* **Versión Analizada:** 0.1.0
* **Fecha de Análisis:** 18 de septiembre de 2026
* **Autor del Informe:** Gemini (IA), basado en la inspección estática del código fuente.

---

## 1. Visión General

Noema es un agente autónomo conversacional y analítico desarrollado en Java como un proyecto personal de investigación de arquitecturas de software. El sistema no ha sido diseñado para actuar como un asistente de generación o edición de código en entornos de desarrollo integrado, sino para constituir un interlocutor analítico y compañero de laboratorio capaz de sostener reflexiones e indagaciones prolongadas en el tiempo a través de múltiples disciplinas (científicas, técnicas, metodológicas y humanísticas).

A diferencia de las arquitecturas tradicionales basadas en sesiones efímeras o en la acumulación desbordada de transcripciones, Noema implementa una sesión persistente unificada. Esta sesión evoluciona de forma incremental mediante una estructura de memoria estratificada que previene la degradación atencional del modelo de lenguaje sin recurrir a infraestructuras externas complejas.

El sistema se rige por un principio de autosuficiencia operativa: la totalidad de sus capacidades se empaqueta en un único artefacto ejecutable (`.jar`), exigiendo únicamente un entorno de ejecución Java moderno y conectividad a interfaces de programación de modelos de lenguaje (LLM). Noema prescinde deliberadamente de servidores de bases de datos vectoriales independientes, brokers de mensajería o herramientas externas de control de versiones; componentes como la base de datos SQL embebida, la indexación y ranking vectorial MaxP, el motor de diferencias unificadas y el gestor de control de versiones [JavaRCS](https://github.com/jjdelcerro/io.github.jjdelcerro.noema/blob/master/pom.xml) están implementados íntegramente en Java dentro del propio proceso.

---

## 2. Stack Tecnológico

El proyecto está construido sobre las siguientes tecnologías y librerías clave:

* **Lenguaje y Plataforma:** Java 25 (utilizando soporte para incubación de vectores y compilación modular estricta).
* **Orquestación de Modelos de Lenguaje:** LangChain4j (versión 1.16.3 / 1.16.3-beta26), incluyendo módulos de núcleo (`langchain4j-core`), integración con proveedores compatibles con OpenAI (`langchain4j-open-ai`), protocolo de contexto de modelos (`langchain4j-mcp`) y ejecución local vía Jlama (`jlama-core` y `jlama-native` 0.8.4).
* **Modelos Pequeños y ONNX en Proceso:** ONNX Runtime GenAI (`onnxruntime-genai` 0.15.2) para la ejecución en memoria de modelos SLM (Qwen3.5-0.8B) y modelos de embeddings cuantizados.
* **Persistencia Embebida:** Base de datos relacional H2 (versión 2.2.224) ejecutada en modo embebido de proceso único con soporte multihilo (`AUTO_SERVER=TRUE`).
* **Ingesta y Extracción de Documentos:** Apache Tika 2.8.0 (`tika-core` y `tika-parsers-standard-package`) con autodetección de juegos de caracteres (`AutoDetectReader`) y extracción estructural de texto plano, PDF, DOCX, ODT y HTML.
* **Motor de Scripting y Sandboxing:** Apache Groovy 4.0.24 (`groovy` y `groovy-json`) con personalizadores de Árbol de Sintaxis Abstracta (`SecureASTCustomizer`) e interrupciones temporizadas (`TimedInterrupt`).
* **Control de Versiones Embebido:** [JavaRCS](https://github.com/jjdelcerro/io.github.jjdelcerro.noema/blob/master/pom.xml) 0.1.0-SNAPSHOT (implementación nativa en Java de RCS) complementada por `java-diff-utils` 4.12 para el cálculo y aplicación de parches unificados.
* **Servidor Web y API HTTP/SSE:** Javalin 6.1.3 sobre Jetty embebido, exponiendo endpoints REST y canales Server-Sent Events (SSE).
* **Interfaces de Usuario:**
  * Terminal de Texto (TUI): [Lanterna](https://jjdelcerro.github.io/noema/docs/05-capa-de-presentacion-e-interfaces/02-tui.html) 3.1.2.
  * Consola Interactiva (CLI): JLine 3.21.0 con soporte JNA.
  * Interfaz Gráfica (GUI): [Swing](https://jjdelcerro.github.io/noema/docs/05-capa-de-presentacion-e-interfaces/01-swing.html) modernizado mediante FlatLaf 3.4.1, MigLayout 11.3 y editor de código RSyntaxTextArea 3.6.1 con diálogos de búsqueda RSTAUI 3.3.2.
  * Frontend Web: Vanilla JavaScript ES Modules, Marked 12.0.2 y DOMPurify 3.1.2.
* **Comunicaciones y Periferia:** Telegram Bot API (`java-telegram-bot-api` 7.1.1) y Jakarta Mail / Eclipse Angus Mail 2.0.3 con soporte IMAP IDLE.
* **Procesamiento de Tiempo y Lenguaje Natural:** Natty 0.13 y PrettyTime 5.0.7.Final.
* **Serialización y Utilidades:** Google Gson 2.10.1, Apache Commons IO 2.18.0, Apache Commons Collections4 4.4, Apache Commons Lang3 y MVEL2 2.5.0.Final.

---

## 3. Estructura de Paquetes e Interfaces

El proyecto se organiza bajo el paquete raíz `io.github.jjdelcerro.noema`, separando estrictamente la especificación pública (interfaces y contratos de servicio) de los componentes de infraestructura y controladores de interfaz.

* `io.github.jjdelcerro.noema.lib`: Contratos fundamentales del agente (`Agent`, `AgentManager`, `AgentActions`, `AgentPaths`, `AgentAccessControl`, `AgentTool`, `ConnectionSupplier`, `Subagent`, `SubagentDefinition`).
* `io.github.jjdelcerro.noema.lib.settings`: Modelo jerárquico de configuración (`AgentSettings`, `AgentSettingsGroup`, `AgentSettingsItem`, `AgentSettingsString`, `AgentSettingsPaths`, `AgentSettingsCheckedList`, `AgentSettingsList`).
* `io.github.jjdelcerro.noema.lib.memory.episodic`: Contratos de almacenamiento inmutable a largo plazo (`EpisodicMemory`, `Turn`, `TurnException`).
* `io.github.jjdelcerro.noema.lib.memory.consolidate`: Abstracción de puntos de síntesis consolidada (`ConsolidateMemory`, `ConsolidateMemoryException`).
* `io.github.jjdelcerro.noema.lib.memory.recent`: Gobernanza de la memoria operativa de sesión activa (`RecentMemory`).
* `io.github.jjdelcerro.noema.lib.memory.projected`: Pipeline de transformación y proyección de contexto para el LLM (`ProjectedMemory`, `ProjectedMemoryOperation`, `ProjectedMemoryOperationFactory`).
* `io.github.jjdelcerro.noema.lib.memory.projected.operations`: Especializaciones de operaciones sobre la memoria proyectada (`PinnedTurnsOperation`).
* `io.github.jjdelcerro.noema.lib.services.reasoning`: Contrato del orquestador cognitivo principal ([ReasoningService](https://jjdelcerro.github.io/noema/docs/03-catalogo-de-servicios/01-reasoning.html)).
* `io.github.jjdelcerro.noema.lib.services.memory`: Contrato del servicio de síntesis narrativa ([MemoryConsolidationService](https://jjdelcerro.github.io/noema/docs/03-catalogo-de-servicios/02-memory-consolidation.html)).
* `io.github.jjdelcerro.noema.lib.services.sensors`: Sistema sensorial y de eventos proactivos ([SensorsService](https://jjdelcerro.github.io/noema/docs/03-catalogo-de-servicios/03-sensors.html), `SensorInformation`, `SensorNature`, `SensorEvent`, `ConsumableSensorEvent`, `SensorStatistics`).
* `io.github.jjdelcerro.noema.lib.spi`: Clases base abstractas para servicios desacoplados (`AbstractAgentService`).
* `io.github.jjdelcerro.noema.lib.impl`: Implementaciones concretas del núcleo (`AgentImpl`, `AgentManagerImpl`, `AgentPathsImpl`, `AgentAccessControlImpl`, `ChatModelImpl`, `ModelParametersImpl`, `SubagentImpl`, `SubagentDefinitionImpl`, `FileFuzzySearchUtils`, `ExpressionEvaluator`).
* `io.github.jjdelcerro.noema.lib.impl.memory`: Implementaciones de almacenamiento episódico, reciente y proyectado (`EpisodicMemoryImpl`, `TurnImpl`, `RecentMemoryImpl`, `ProjectedMemoryImpl`, `ConsolidateMemoryImpl`, adaptadores GSON).
* `io.github.jjdelcerro.noema.lib.impl.memory.projected.operations`: Factorías e implementaciones del pipeline de memoria proyectada (`TrimmingOperation`, `PendingAnnotationOperation`, `TemporalPerceptionOperation`, `PinnedTurnsOperationImpl`, `PeripheralAwarenessOperation`).
* `io.github.jjdelcerro.noema.lib.impl.scripting`: Motor de ejecución Groovy en proceso (`ScriptEngine`, `ScriptContext`, `AbstractScriptModule`) y sus módulos de capacidades (`FsModule`, `LlmModule`, `WebModule`, `AnnotationModule`, `SessionStateModule`, `SubagentsModule`).
* `io.github.jjdelcerro.noema.lib.impl.skills`: Descubrimiento, parseo y ejecución de habilidades procedimentales (`Skill`, `SkillUtils`).
* `io.github.jjdelcerro.noema.lib.impl.services.*`: Servicios de fondo y sus herramientas asociadas (`reasoning`, `memory`, `sensors`, `embeddings`, `scheduler`, `email`, `telegram`, `mcp`).
* `io.github.jjdelcerro.noema.main`: Puntos de entrada para las diferentes modalidades de ejecución (`Main`, `MainGUI`, `MainLanterna`, `MainConsole`, `MainWeb`, `BootUtils`, `NoemaWebServer`).
* `io.github.jjdelcerro.noema.ui`: Abstracciones de presentación y desacoplamiento de consola ([AgentConsole](https://jjdelcerro.github.io/noema/docs/05-capa-de-presentacion-e-interfaces/00-contrato-agentconsole-y-comunicacion.md), `AgentUIManager`, `AgentUISettings`).

---

## 4. Arquitectura y Diseño

### 4.1. El Kernel (o Core)

#### `Agent` y `AgentManager`
El contrato principal `Agent` define la interfaz operativa del agente frente al entorno de ejecución. Encapsula las referencias al control de acceso, configuración, rutas físicas, persistencia episódica, emisión de eventos y gestión de modelos de lenguaje. 

`AgentManager` actúa como el registro central y factoría abstracta del sistema. Mantiene el catálogo de factorías de servicios (`AgentServiceFactory`), factorías de operaciones de memoria proyectada (`ProjectedMemoryOperationFactory`), proveedores SQL (`SQLProvider`), acciones globales registradas y la lista concurrente de subagentes activos (`activeSubagents`). `AgentLocator` proporciona el acceso desacoplado al `AgentManager` global.

#### Ciclo de Vida
El [ciclo de vida](https://jjdelcerro.github.io/noema/docs/01-fundamentos-y-ciclo-de-vida/02-agent-paths.html) de Noema se rige por un procedimiento determinista:
* **Fase de Inicialización:** Comprobación del espacio de trabajo y despliegue inductivo de la jerarquía de archivos y configuraciones base si no existen (`setupSettings()`).
* **Fase de Registro:** `AgentImpl` instancia todos los servicios registrados en `AgentManager`, gestionando servicios compartidos heredados de instancias padre. Los sensores fundamentales (como el sensor `USER`, `SYSTEMCLOCK` y `SYSTEMNOTIFICATION`) se registran inmediatamente.
* **Fase de Extracción de Herramientas:** El agente recopila todas las instancias de `AgentTool` expuestas por los servicios registrados que puedan arrancar (`canStart()`) y las registra en el orquestador cognitivo.
* **Fase de Arranque:** Se ejecutan los métodos `start()` de los servicios de soporte, se inicia el modelo SLM local en segundo plano, se monta el hilo despachador de eventos del sistema y se vincula un `ShutdownHook` en la máquina virtual para garantizar el vaciado de búferes y cierre ordenado de bases de datos.
* **Fase de Parada:** El método `stop()` desactiva concurrentemente los despachadores, detiene las escuchas de red, consolida los estados transitorios a disco y libera los bloqueos a través de las conexiones de base de datos.

#### Infraestructura de Datos
La persistencia de Noema descansa en dos bases de datos H2 embebidas independientes proporcionadas por abstracciones `ConnectionSupplier`:
* **Base de Datos Episódica (`episodic_memory`):** Almacena el historial íntegro de turnos atómicos (`episodicmemory`) y los metadatos de los puntos de consolidación (`consolidatememory`). Los vectores de similitud semántica se conservan como objetos binarios (`BLOB`).
* **Base de Datos de Servicios (`service`):** Gestiona el estado de tareas temporizadas y alarmas (`SCHEDULER`).

El acceso SQL está desacoplado mediante la interfaz `SQLProvider`. Ésta centraliza las cadenas SQL bajo identificadores técnicos, facilitando la portabilidad sintáctica sin acoplar las consultas al código de los servicios. Para evitar contenciones en la asignación de claves primarias en un entorno mono-inquilino de alto rendimiento, la clase `Counter` inicializa contadores atómicos en memoria leyendo el `MAX(id)` de la base de datos una única vez en el arranque.

#### Topología de Archivos
Toda la persistencia de Noema se estructura bajo una carpeta oculta dentro del espacio de trabajo denominada `.noema-agent`, gobernada por [AgentPaths](https://jjdelcerro.github.io/noema/docs/01-fundamentos-y-ciclo-de-vida/02-agent-paths.html). Adicionalmente, se mantiene una ubicación global de usuario en `~/.config/noema-agent` para credenciales maestras y ajustes de sesión compartidos. La topología interna comprende:
* `var/config`: Ficheros de definición `settings.json`, `settingsui.json`, diccionarios de propiedades (`models.properties`, `providers_urls.properties`, `apikeys.properties`, `available_tools.properties`), y plantillas de directivas operativas (`prompts/reasoning-system.md`, `prompts/memory-consolidation.md`).
* `var/lib`: Bases de datos H2 (`episodic_memory.mv.db`, `service.mv.db`), archivos de memoria reciente en formato JSON (`recent_memory-<subchannel>.json`), estados persistentes de proyección (`projected_memory_<subchannel>.json`), estado de sensores (`sensors.json`), volcado secuencial de turnos en CSV (`turns.csv`) y el subdirectorio `consolidatememory/` que alberga las crónicas narrativas persistidas en Markdown (`consolidatememory-{id}-{first}-{last}.md`).
* `var/cache`: Cachés de texto extraído de binarios pesados organizados por hash SHA-256 (`file_extract_text/`).
* `var/tmp`: Salidas de comandos de shell (`.out`), ejecuciones de scripts Groovy, búferes de paginación (`.tmp`), y volcados completos de inspección de contexto enviados al LLM (`context-<subchannel>-<timestamp>.json`).
* `var/log`: Registro de eventos del agente (`noema-agente.log`).
* `var/identity/core`: Archivos normativos y de filosofía técnica (`01_identidad_base.md`, `02_protocolo_operativo.md`, `03_stack_y_normas_codigo.md`, `04_filosofia_arquitectonica.md`).
* `var/identity/environ`: Módulos densos de conocimiento de entorno y sus índices ligeros asociados (`*.ref.md`).
* `var/subagents`: Descriptores declarativos XML de subagentes especializados.
* `home`: Directorio de trabajo aislado utilizado como carpeta de usuario en caso de activación del sandbox por proceso mediante Firejail.

---

### 4.2. Capacidades Horizontales (Cross-cutting Concerns)

#### Seguridad y Control de Acceso ([AgentAccessControl](https://jjdelcerro.github.io/noema/docs/01-fundamentos-y-ciclo-de-vida/04-seguridad-y-control-de-acceso.html))
El subsistema de seguridad gobierna qué acciones y recursos están al alcance del modelo en todo momento. Implementa un confinamiento riguroso basado en el espacio de trabajo:
* **Resolución Canónica y Aislamiento:** Cualquier ruta de entrada se normaliza y se evalúa mediante `toRealPath()`. Si la ruta resultante no se encuentra dentro del directorio raíz del espacio de trabajo ni en la lista blanca de rutas externas permitidas (`allowed_external_paths`), se lanza una excepción de denegación de acceso.
* **Zonas Estrictamente Restringidas:** Queda prohibida la modificación de archivos dentro del repositorio de versiones `.git/`, la carpeta de habilidades `.claude/skills/` y los ficheros de copia histórica de [JavaRCS](https://github.com/jjdelcerro/io.github.jjdelcerro.noema/blob/master/pom.xml) que terminan en `,jv`.
* **Supervisión Humana Activa:** Si el parámetro `access_control/humanConfirmationRequired` está habilitado, cualquier herramienta catalogada con modo de operación de escritura (`MODE_WRITE`), ejecución (`MODE_EXECUTION`) o scripting (`MODE_SCRIPTING`) detiene el flujo y solicita la autorización explícita del usuario a través de la consola activa antes de interactuar con el sistema operativo.
* **Control de Red:** La conectividad externa hacia URLs web se restringe en función del parámetro `access_control/allow_internet_access`, bloqueando por defecto peticiones orientadas a direcciones de bucle local (`localhost`, `127.0.0.1`, rangos privados `192.168.*`) para mitigar ataques de falsificación de peticiones en el lado del servidor (SSRF).

#### Gestión de Rutas y Sandbox ([AgentPaths](https://jjdelcerro.github.io/noema/docs/01-fundamentos-y-ciclo-de-vida/02-agent-paths.html))
El componente de rutas encapsula la navegación física y garantiza el desacoplamiento de las rutas dependientes del sistema operativo. Implementa un esquema de búsqueda en cascada para recursos del agente (`getAgentPath`): busca primero en el espacio de trabajo local (`.noema-agent/<recurso>`) y, si no existe, recurre a la configuración global de usuario (`~/.config/noema-agent/<recurso>`). Esto permite compartir configuraciones de modelos y proveedores entre distintos directorios de trabajo manteniendo intactos los datos de cada sesión.

#### Sistema de Configuración Jerárquica ([AgentSettings](https://jjdelcerro.github.io/noema/docs/01-fundamentos-y-ciclo-de-vida/03-agent-settings.html))
La configuración se articula como un árbol de nodos en memoria (`settings.json`) manipulable mediante rutas delimitadas por barras diagonales (ej. `reasoning/provider/url`).
* **Tipado Polimórfico:** Admite cadenas escalares, listas de rutas validadas, listas de opciones complejas y listas de selección booleana múltiple (`AgentSettingsCheckedList`).
* **Evaluador de Expresiones Embebido (`ExpressionEvaluator`):** Incorpora un analizador sintáctico descendente recursivo capaz de evaluar expresiones lógicas, aritméticas y condicionales ternarias en tiempo de ejecución. Permite activar o desactivar opciones de configuración dinámicamente mediante funciones integradas como `getSetting("ruta/propiedad")` sin necesidad de ejecutar intérpretes pesados.

---

### 4.3. Servicios Cognitivos

La capa cognitiva orquesta el flujo de atención del agente y transforma las interacciones conversacionales en conocimiento estructurado. Ambos servicios comparten el acceso concurrente a la base de datos relacional y al sistema de archivos a través de `EpisodicMemory`.

#### [ReasoningService](https://jjdelcerro.github.io/noema/docs/03-catalogo-de-servicios/01-reasoning.html)
Es el núcleo central de ejecución y pensamiento. Gobierna el bucle de razonamiento en respuesta a estímulos entrantes:
* **Manejo de Subcanales:** Mantiene el aislamiento de contextos conversacionales mediante identificadores técnicos (`subchannel`), permitiendo que el agente dialogue de manera independiente con interfaces concurrentes (como la terminal local, un cliente web o un canal de mensajería) compartiendo la misma base de conocimiento de fondo.
* **Bucle de Razonamiento:** Obtiene la proyección optimizada de mensajes desde [ProjectedMemory](https://jjdelcerro.github.io/noema/docs/02-el-sistema-de-memoria/050-memoria-proyectada.html), calcula las especificaciones de herramientas autorizadas, realiza la invocación streaming al modelo LLM, intercepta las solicitudes de llamadas a herramientas (`ToolExecutionRequest`), gestiona su ejecución segura y reinyecta los resultados hasta que el modelo emite su respuesta final.
* **Control de Degradación:** Supervisa el volumen acumulado de turnos y evalúa periódicamente si se requiere transferir turnos antiguos hacia la consolidación sintética.

#### [MemoryConsolidationService](https://jjdelcerro.github.io/noema/docs/03-catalogo-de-servicios/02-memory-consolidation.html)
Es el encargado de destilar y compactar el diálogo reciente para preservar el contexto a largo plazo:
* **Protocolo de Síntesis:** Consume la lista de turnos no compactados formateada en CSV junto con el documento de consolidación previo. Invoca un modelo de lenguaje dedicado bajo las directrices de `memory-consolidation.md`.
* **Estructuración Dual:** Genera un artefacto unificado compuesto por una sección ejecutiva y factual ("Resumen") y una crónica cronológica detallada de la evolución de los argumentos ("El Viaje").
* **Validación de Citas:** Inspecciona mediante expresiones regulares todas las referencias del tipo `{cite:ID}` insertadas por el modelo en el nuevo relato. Si el modelo alucina un identificador que no existía en los turnos evaluados ni en la consolidación anterior, la referencia se invalida reemplazándola automáticamente por `{badcite:ID}`, garantizando una trazabilidad determinista hacia la base de datos episódica.

---

### 4.4. Servicios de Periferia

#### [SensorsService](https://jjdelcerro.github.io/noema/docs/03-catalogo-de-servicios/03-sensors.html)
Es el subsistema encargado de recibir estímulos asíncronos y desacoplar la llegada de eventos del ciclo de ejecución del modelo de lenguaje. Administra la cola de eventos entrantes aplicando distintas estrategias de retención según la naturaleza del canal emisor (`SensorNature`), calculando métricas de frecuencia y ofreciendo mecanismos para silenciar o reactivar canales bajo demanda. Los eventos sensoriales se inyectan en el flujo conversacional mediante una simulación de herramienta ficticia denominada `pool_event`, lo que mantiene intactas las reglas de alternancia de roles requeridas por los modelos de lenguaje.

#### [SchedulerService](https://jjdelcerro.github.io/noema/docs/03-catalogo-de-servicios/04-scheduler.html)
Proporciona al agente capacidad de percepción temporal diferida y planificación de tareas en el tiempo. Persiste alarmas en una tabla dedicada de la base de datos relacional (`SCHEDULER`), calculando esperas relativas mediante un ejecutor de un solo hilo. Emplea la librería Natty para interpretar descripciones temporales en lenguaje natural en inglés (como "in 10 minutes" o "tomorrow at 5pm") y emite eventos sensoriales discretos hacia [SensorsService](https://jjdelcerro.github.io/noema/docs/03-catalogo-de-servicios/03-sensors.html) cuando expira el tiempo programado.

#### [Scripting](https://jjdelcerro.github.io/noema/docs/04-subsistemas-de-ejecucion-y-capacidades/04-scripting.html)
Permite al agente delegar el procesamiento masivo de datos, cálculos matemáticos y transformaciones estructuradas a un entorno de ejecución Groovy en proceso. A través de la herramienta `execute_script`, el código generado por el modelo interactúa con el entorno exclusivamente a través de la fachada contextual `agent`, la cual expone métodos fluidos para recorrer archivos mediante streaming, ejecutar búsquedas semánticas o por expresiones regulares, realizar consultas secundarias acotadas al modelo LLM y consultar la web sin saturar la memoria de trabajo.

#### [EmbeddingsService](https://jjdelcerro.github.io/noema/docs/03-catalogo-de-servicios/05-embeddings.html)
Es el servicio responsable de la vectorización de texto en memoria local para la recuperación semántica del agente. Aloja modelos ONNX cuantizados (como `paraphrase-multilingual-MiniLM-L12-v2` de 384 dimensiones) y ejecuta comparaciones de similitud coseno mediante la técnica MaxP (Maximum Passage Retrieval). Esto permite indexar y recuperar turnos o fragmentos de archivos comparando la consulta contra múltiples bloques independientes de un texto largo y reteniendo la puntuación máxima, evitando la disolución de la señal semántica.

#### EmailService y TelegramService
Constituyen los efectores y receptores de comunicación con el exterior. `EmailService` opera bajo un patrón de filtrado en el que monitoriza bandejas de entrada mediante un hilo demonio con soporte IMAP IDLE, inyectando únicamente notificaciones ligeras (remitente y asunto) de remitentes autorizados para evitar el consumo de contexto, permitiendo al agente descargar el contenido saneado vía Apache Tika (`email_read`) o redactar respuestas vía SMTP (`email_send`). `TelegramService` establece un canal bidireccional mediante sondeo largo (long-polling) vinculado exclusivamente a un identificador de usuario autorizado (`chat_id`), agregando mensajes entrantes consecutivos como estímulos fusionables e integrando la herramienta de envío proactivo `telegram_send`.

#### McpService
Implementa la integración del agente con servidores externos que exponen capacidades bajo el estándar Model Context Protocol. Lee la configuración declarativa de servidores en `mcp/servers` (admitiendo transportes de subproceso estándar `stdio` con paso de variables de entorno, o transporte HTTP/SSE), inicializa clientes dedicados de LangChain4j, descubre dinámicamente las herramientas expuestas por cada servidor y las registra en el orquestador cognitivo encapsuladas en adaptadores `McpToolWrapper`.

---

## 5. Subsistemas y Mecanismos Clave

### 5.1. Gestión de Memoria y Pipeline de la Memoria Proyectada

El [modelo de memoria](https://jjdelcerro.github.io/noema/docs/02-el-sistema-de-memoria/010-vision-general-de-modelo-de-memoria.html) de Noema se organiza en tres estratos claramente delimitados para asegurar que el modelo de lenguaje opere dentro de su ventana óptima de atención:

* **[EpisodicMemory](https://jjdelcerro.github.io/noema/docs/02-el-sistema-de-memoria/020-memoria-episodica.html) (Fuente de Verdad Inmutable):** Es el registro físico histórico almacenado en H2. Cada turno, anotación y ejecución de herramienta queda asentado de forma inmutable con su marca temporal, tipo, contenido textual y vector de embedding.
* **[RecentMemory](https://jjdelcerro.github.io/noema/docs/02-el-sistema-de-memoria/040-memoria-reciente.html) (Memoria Operativa de Trabajo):** Mantiene la lista de mensajes en memoria RAM correspondiente a la ventana reciente de la conversación activa, sincronizándola en disco en formato JSON (`recent_memory-<subchannel>.json`). Implementa un algoritmo de consolidación que localiza un punto de corte equilibrado (`getConsolidateMark()`). Este algoritmo garantiza la integridad de las llamadas a herramientas al inspeccionar los mensajes posteriores y avanzar el límite si detecta respuestas consecutivas de herramientas (`ToolExecutionResultMessage`), impidiendo que queden resultados huérfanos sin su solicitud previa.
* **[ProjectedMemory](https://jjdelcerro.github.io/noema/docs/02-el-sistema-de-memoria/050-memoria-proyectada.html) (Vista Curada para el LLM):** Es la vista construida dinámicamente que se entrega al modelo en cada turno. Está formada por el prompt de sistema base, el texto consolidado más reciente ("El Viaje" y "Resumen" procedentes de [ConsolidateMemory](https://jjdelcerro.github.io/noema/docs/02-el-sistema-de-memoria/030-memoria-consolidada.html)), los mensajes de [RecentMemory](https://jjdelcerro.github.io/noema/docs/02-el-sistema-de-memoria/040-memoria-reciente.html) y el procesamiento secuencial de un **Pipeline de Operaciones Registrable**.

El pipeline de operaciones se ensambla a través de factorías registradas en `AgentManager` (`ProjectedMemoryOperationFactory`). Cada operación cuenta con una prioridad estricta y puede transformar la lista de mensajes proyectados o inyectar avisos efímeros unificados:
* `PinnedTurnsOperationImpl` (Prioridad 5): Localiza herramientas que declaran retención obligatoria (`shouldPin()`, como `activate_skill`). Si el turno original ha desaparecido de la memoria reciente debido a una consolidación, la operación reinyecta automáticamente el par solicitud-respuesta al inicio de la proyección de contexto, emitiendo además recordatorios periódicos cada cinco turnos.
* `TrimmingOperation` (Prioridad 10): Aplica poda selectiva sobre resultados de herramientas voluminosos (superiores a 1024 caracteres). Preserva intactos los últimos veinte mensajes conversacionales; para los turnos que quedan fuera de esa zona de seguridad, sustituye el cuerpo masivo por la marca `CONTENT_TRIMMED: true`, manteniendo las cabeceras de metadatos intactas para evitar la saturación atencional del modelo.
* `PendingAnnotationOperation` (Prioridad 20): Analiza si en los turnos que se aproximan a la zona de poda se consumieron recursos paginados pesados (archivos o páginas web) sin que el agente haya generado una nota de síntesis mediante `annotate_observation`. De ser así, genera un recordatorio efímero señalando los recursos en riesgo.
* `TemporalPerceptionOperation` (Prioridad 30): Calcula el intervalo transcurrido desde la última interacción del usuario y, si se supera el umbral configurado (por defecto una hora), inyecta una indicación temporal explícita para que el modelo adapte su percepción cronológica.
* `PeripheralAwarenessOperation` (Prioridad 1000): Consulta en [EpisodicMemory](https://jjdelcerro.github.io/noema/docs/02-el-sistema-de-memoria/020-memoria-episodica.html) la actividad de otros subcanales concurrentes durante los últimos días e inyecta un resumen compacto de las sesiones paralelas, dotando al agente de consciencia sobre el estado general del sistema sin saturar el contexto con transcripciones ajenas.

---

### 5.2. Gestión de la Identidad del Agente

La personalidad, metodologías y marco axiológico del agente se estructuran en dos capas desacopladas que se integran en el prompt de sistema:

* **Identidad Nuclear (ADN Técnico y Reglas Operativas):** Ubicada en `var/identity/core/`. Define el estilo cognitivo, la prohibición estricta de complacencia o muletillas artificiales, las restricciones tecnológicas (ej. normas de estilo para Java) y el protocolo de colaboración. La inclusión de estos módulos en el prompt de sistema es selectiva y se gobierna mediante la lista de selección `reasoning/identity/core` de la configuración.
* **Consciencia de Entorno (Memoria Virtual Indexada):** Ubicada en `var/identity/environ/`. Para no ocupar espacio permanente en la ventana de contexto con datos biográficos, proyectos pasados o marcos teóricos extensos, este subsistema despliega únicamente archivos de anclaje semántico ligero (`*.ref.md`) en el prompt de sistema. Estas referencias actúan como un índice; cuando el usuario aborda un tema especializado, el modelo detecta la clave en su índice e invoca obligatoriamente la herramienta `consult_environ` para cargar el documento denso correspondiente (`*.md`) únicamente durante los turnos en que sea necesario.

---

### 5.3. Gestión de Habilidades ([Skills](https://jjdelcerro.github.io/noema/docs/04-subsistemas-de-ejecucion-y-capacidades/03-skills.md))

Noema implementa un estándar de habilidades procedimentales ubicado en la carpeta del espacio de trabajo `.claude/skills/<nombre>/`:

* **Estructura:** Cada habilidad reside en un subdirectorio propio y contiene obligatoriamente un archivo `SKILL.md` con metadatos en formato YAML frontmatter (`name`, `description`, `version`) seguido de las instrucciones operativas paso a paso. Opcionalmente puede incluir un subdirectorio `scripts/` con utilidades ejecutables en Bash y un subdirectorio de referencias documentales.
* **Ciclo de Activación y Fijación:** Mediante `list_skills` el agente descubre los procedimientos disponibles. Al ejecutar `activate_skill`, el contenido de `SKILL.md` se carga en la memoria de trabajo. Debido a que esta herramienta implementa `shouldPin()`, `PinnedTurnsOperationImpl` fija las directivas en la memoria proyectada para evitar su pérdida tras consolidaciones de memoria.
* **Ejecución Asistida y Desactivación:** Durante la vigencia de la habilidad, el agente puede leer archivos complementarios mediante `read_skill_resource` y ejecutar herramientas especializadas del subdirectorio `scripts/` mediante `run_skill_script`. Una vez finalizado el protocolo técnico, el agente invoca `deactivate_skill`, lo que elimina el turno fijado de [ProjectedMemory](https://jjdelcerro.github.io/noema/docs/02-el-sistema-de-memoria/050-memoria-proyectada.html) y cesa los recordatorios periódicos.
* **Protección del Código:** [AgentAccessControl](https://jjdelcerro.github.io/noema/docs/01-fundamentos-y-ciclo-de-vida/04-seguridad-y-control-de-acceso.html) bloquea explícitamente cualquier intento de escritura o modificación sobre el directorio `.claude/skills/`, garantizando que el agente no altere sus propios procedimientos operativos durante la ejecución.

---

### 5.4. Gestión de Eventos y Señales Sensoriales

El sistema sensorial resuelve la incompatibilidad entre la naturaleza pasiva de los modelos de lenguaje (arquitectura de petición-respuesta) y la necesidad de recibir estímulos asíncronos:

* **Tipología de Señales (`SensorNature`):**
  * `DISCRETE`: Estímulos atómicos que deben procesarse de forma independiente sin combinarse (ej. temporizadores o avisos críticos).
  * `MERGEABLE`: Flujos conversacionales continuos que se concatenan con marcas de tiempo relativas en un único mensaje consolidado mientras el agente está ocupado (ej. mensajes sucesivos de Telegram).
  * `AGGREGATABLE`: Señales de alta frecuencia donde importa el volumen acumulado; el sistema mantiene un contador numérico y el intervalo de ocurrencia en lugar de almacenar cada texto.
  * `STATE`: Condiciones de estado volátiles donde sólo tiene validez la última lectura recibida, sobrescribiendo las anteriores en un mapa concurrente.
  * `USER`: Estímulos procedentes directamente del usuario interactivo.
* **Simulación de Inversión de Control:** Cuando un evento no originado por el usuario debe entregarse al agente, [SensorsService](https://jjdelcerro.github.io/noema/docs/03-catalogo-de-servicios/03-sensors.html) genera un par coordinado de mensajes: un mensaje de asistente simulando una llamada a la herramienta ficticia `pool_event` y un mensaje de resultado de herramienta (`ToolExecutionResultMessage`) conteniendo el JSON del evento. Esto permite presentar la información al modelo respetando la secuencia canónica de turnos exigida por los proveedores LLM sin recurrir a inyecciones artificiales de texto de usuario.

---

### 5.5. Gestión de la Seguridad

La contención y resiliencia de Noema descansa en cuatro barreras independientes:

* **Aislamiento en Sistema de Archivos:** [AgentAccessControl](https://jjdelcerro.github.io/noema/docs/01-fundamentos-y-ciclo-de-vida/04-seguridad-y-control-de-acceso.html) resuelve las rutas canónicas y bloquea escapes del espacio de trabajo. Rutas externas solo son accesibles si se declaran en `allowed_external_paths`. Además, existen listas negras específicas para denegar lectura (`nom_readable_paths`) o bloquear escritura (`nom_writable_paths`).
* **Confirmación Humana Previa:** Si el agente opera en modo supervisado, toda operación de mutación de archivos, llamada a la red o ejecución de shell queda en suspenso hasta que el operador humano emite su consentimiento mediante el método interactivo `confirm()` de [AgentConsole](https://jjdelcerro.github.io/noema/docs/05-capa-de-presentacion-e-interfaces/00-contrato-agentconsole-y-comunicacion.md).
* **Control de Versiones Automático Previo a Modificaciones:** Como salvaguarda ante modificaciones no deseadas, herramientas como `file_write`, `file_patch`, `file_search_and_replace` y el módulo `agent.fs.write` de [Scripting](https://jjdelcerro.github.io/noema/docs/04-subsistemas-de-ejecucion-y-capacidades/04-scripting.html) ejecutan obligatoriamente una operación de registro (`ci`) en [JavaRCS](https://github.com/jjdelcerro/io.github.jjdelcerro.javarcs) sobre el archivo preexistente antes de alterar su contenido. Si una edición corrompe un archivo, el usuario o el agente pueden consultar el historial con `file_history` y revertir el cambio mediante `file_recovery`.
* **Aislamiento de Procesos:** La ejecución de comandos Bash en `ShellExecuteTool` es no interactiva (bloquea prompts de contraseña). Si la utilidad del sistema Firejail está presente y habilitada en la configuración, el comando se ejecuta en una jaula de procesos restringida con un directorio de usuario efímero, cortando el acceso al resto del sistema operativo.

---

### 5.6. Flujos de Ejecución en [ReasoningService](https://jjdelcerro.github.io/noema/docs/03-catalogo-de-servicios/01-reasoning.html)

El ciclo de procesamiento de [ReasoningService](https://jjdelcerro.github.io/noema/docs/03-catalogo-de-servicios/01-reasoning.html) se articula a través del bucle `eventDispatcher`, ejecutado en un hilo de plataforma dedicado:

```
                  [ SensorsService / Cola de Eventos ]
                                    │
                         eventDispatcher()
                                    │
                                    ▼
                        processSingleEvent(event)
                                    │
         ┌──────────────────────────┴──────────────────────────┐
         ▼                                                     ▼
  [ Evento de Usuario ]                                [ Evento Sensorial ]
  Inyecta UserMessage                                  Inyecta par simulado:
  en RecentMemory.                                     AiMessage(pool_event) +
                                                       ToolExecutionResultMessage
                                    │
                                    ▼
┌───────────────────► BUCLE DE RAZONAMIENTO DEL TURNO
│                                   │
│   ProjectedMemory.getMessages(RecentMemory, ConsolidateMemory, Prompt)
│                                   │
│   ChatModel.generate(proyección, especificación de herramientas)
│                                   │
│   RecentMemory.add(AiMessage)
│                                   │
│   ¿El modelo solicita herramientas?
│         │
│         ├──────► SÍ:
│         │         * Evalúa confirmación humana si aplica.
│         │         * Ejecuta AgentTool.execute(jsonArgs).
│         │         * Registra Turn en EpisodicMemory (tipo herramienta).
│         │         * Inyecta ToolExecutionResultMessage en RecentMemory.
│         │         * RecentMemory.consolideTurn(toolTurn).
│         │         * Itera nuevamente el bucle de razonamiento.
│         │
│         └──────► NO:
│                   * Emite respuesta final a AgentConsole.
│                   * Registra Turn final (tipo "chat") en EpisodicMemory.
│                   * RecentMemory.consolideTurn(responseTurn).
│                   * Comprueba FinishReason. Si es TOOL_EXECUTION en texto
│                     sin llamada formal, reinyecta reintento técnico.
│                   * Concluye turno.
│                                   │
│   ¿RecentMemory.needConsolidation()?
│         │
│         └──────► SÍ: Dispara performConsolidation()
│                                   │
└───────────────────────────────────┘
```

---

### 5.7. Trabajadores Especializados ([Subagentes](https://jjdelcerro.github.io/noema/docs/04-subsistemas-de-ejecucion-y-capacidades/02-subagentes.html))

Para resolver tareas de exploración exhaustiva o procesamiento prolongado sin saturar la memoria de trabajo de la conversación principal, Noema implementa el patrón de [subagentes](https://jjdelcerro.github.io/noema/docs/04-subsistemas-de-ejecucion-y-capacidades/02-subagentes.html) desechables:

* **Definición Declarativa en XML:** Se especifican en archivos XML ubicados en `var/subagents/*.xml` (como `document_indexer.xml`). El descriptor parametriza fuertemente las entradas (`<params>`), restringe las herramientas autorizadas mediante una lista blanca cerrada (`<tools>`), fija un límite temporal (`<timeout>`) y define un rol específico (`<system_prompt>`).
* **Confinamiento e Independencia Operativa:** Cada subagente instanciado por `AgentManager` recibe un directorio temporal propio como espacio de trabajo y bases de datos H2 independientes (`sub_memory` y `sub_service`). Su consola se redirige de forma silenciosa a un archivo de registro en disco. Hereda las credenciales del agente principal pero opera de forma autónoma sin acceso a los turnos conversacionales del hilo raíz.
* **Ejecución en Dos Fases:**
  * Fase 1 (Exploración e Ingesta): El subagente recibe la orden inicial (`prompt_ini`) con sus variables resueltas, interactuando de forma iterativa con sus herramientas autorizadas.
  * Fase 2 (Síntesis y Entrega): Si el descriptor define `prompt_fin`, el orquestador inyecta esta directiva para forzar la consolidación del material procesado y la escritura del resultado final en disco.
* **Modalidad Síncrona y Asíncrona:** A través de las herramientas `launch_subagent` o el módulo `agent.subagents`, los trabajadores pueden ejecutarse de forma bloqueante (devolviendo el texto resultante) o en segundo plano en un hilo independiente. Al concluir la ejecución en segundo plano, el sistema emite automáticamente una notificación sensorial al subcanal de origen vía `SYSTEMNOTIFICATION`, procediendo al borrado del directorio temporal y cierre de recursos.

---

## 6. Catálogo Exhaustivo de Herramientas del Agente

A continuación se detalla la totalidad de las herramientas implementadas en el sistema, organizadas por familias funcionales según sus contratos de operación. La [herramientas base y paginación](https://jjdelcerro.github.io/noema/docs/04-subsistemas-de-ejecucion-y-capacidades/01-herramientas-base-y-paginacion.html) rige a todas aquellas que retornan datos extensos.

### Herramientas de Memoria y Cognición
* **`fetch_citation` (`LookupTurnTool`):** Recupera el registro histórico completo e inmutable de un turno a partir de su identificador numérico o etiqueta (ej. `123` o `ID-123`). Admite el parámetro `context_window` para devolver un bloque cronológico de hasta cinco turnos contiguos anteriores y posteriores.
* **`search_full_history` (`SearchFullHistoryTool`):** Ejecuta una búsqueda semántica basada en embeddings vectoriales sobre la totalidad de turnos almacenados en [EpisodicMemory](https://jjdelcerro.github.io/noema/docs/02-el-sistema-de-memoria/020-memoria-episodica.html). Permite filtrar por similitud coseno mínima, cantidad de resultados y categoría técnica de anotación.
* **`annotate_observation` (`AnnotateObservationTool`):** Permite al agente registrar un insight, síntesis conceptual o directiva operativa directamente en [EpisodicMemory](https://jjdelcerro.github.io/noema/docs/02-el-sistema-de-memoria/020-memoria-episodica.html) bajo el tipo `annotation`. Exige el parámetro `resource_id` si el dato procede de una lectura de archivo o web paginada, silenciando advertencias de desatención.

### Herramientas de Exploración y Manipulación del Sistema de Archivos
* **`file_read` (`FileReadTool`):** Realiza la lectura controlada de archivos de texto plano, código o configuraciones dentro del sandbox. Admite lectura fraccionada mediante parámetros de desplazamiento (`offset`) y límite de líneas (`limit`).
* **`file_find` (`FileFindTool`):** Localiza y lista archivos y directorios de forma recursiva a partir de patrones glob (ej. `**/*.java`). Genera una salida paginada con metadatos de modificación, tamaño, tipo MIME y ruta canónica.
* **`file_grep` (`FileGrepTool`):** Búsqueda textual exhaustiva dentro de un archivo o directorio mediante expresiones regulares o cadenas fijas (`plaintext`), respetando filtros glob de archivos y volcando los resultados coincidentes a un recurso temporal paginado.
* **`file_fuzzygrep` (`FileFuzzyGrepTool`):** Motor de búsqueda semántica local sobre código y texto. Recorre archivos mediante una ventana deslizante de líneas, vectoriza los fragmentos al vuelo y retorna las coincidencias ordenadas por proximidad semántica utilizando MaxP.
* **`file_write` (`FileWriteTool`):** Escribe o sobrescribe un archivo en disco de forma atómica, creando las carpetas intermedias necesarias. Antes de escribir, realiza un check-in automático en [JavaRCS](https://github.com/jjdelcerro/io.github.jjdelcerro.noema/blob/master/pom.xml) si el archivo ya existía.
* **`file_patch` (`FilePatchTool`):** Aplica modificaciones complejas sobre archivos existentes interpretando un parche en formato de diferencias unificadas (`unified diff`). Ejecuta una copia de respaldo en [JavaRCS](https://github.com/jjdelcerro/io.github.jjdelcerro.javarcs) previa a la alteración del fichero.
* **`file_search_and_replace` (`FileSearchAndReplaceTool`):** Efectúa sustituciones puntuales de bloques exactos de texto (`oldText` por `newText`), verificando que el bloque de origen sea único dentro del archivo y respaldando previamente la versión en [JavaRCS](https://github.com/jjdelcerro/io.github.jjdelcerro.javarcs).
* **`file_mkdir` (`FileMkdirTool`):** Crea directorios individuales o jerarquías completas de carpetas dentro del sandbox del espacio de trabajo.
* **`file_extract_text` (`FileExtractTextTool`):** Extrae el texto plano estructurado de archivos binarios complejos (PDF, DOCX, ODT, RTF) utilizando Apache Tika, gestionando una caché persistente indexada por hash para evitar reprocesamientos costosos.
* **`file_history` (`FileHistoryTool`):** Inspecciona el historial de revisiones de un archivo administrado por [JavaRCS](https://github.com/jjdelcerro/io.github.jjdelcerro.javarcs), devolviendo autores, fechas y comentarios de confirmación de cambios (`rlog`).
* **`file_recovery` (`FileRecoveryTool`):** Restaura una versión histórica específica de un archivo desde el almacén de [JavaRCS](https://github.com/jjdelcerro/io.github.jjdelcerro.javarcs) (`co`), sobrescribiendo el archivo de trabajo previa confirmación de seguridad.
* **`read_paginated_resource` (`ReadPaginatedResourceTool`):** Mecanismo universal de lectura paginada. Permite al agente consumir bloques subsiguientes de salidas de comandos, documentos web o búsquedas mediante identificadores de esquema (`tmp://`, `cache://`, `user://`) proporcionados en el campo `HINT` de respuestas previas.

### Herramientas de Ejecución de Comandos y Scripting
* **`shell_execute` (`ShellExecuteTool`):** Ejecuta comandos de sistema en Bash de forma estrictamente no interactiva, capturando flujos estándar y de error hacia archivos temporales gestionados con descarte LRU. Aplica confinamiento por Firejail si está disponible en el host.
* **`execute_script` (`ScriptExecuteTool`):** Interpreta y ejecuta scripts Groovy/Java en un entorno seguro en proceso. Otorga acceso a las APIs fluidas de archivos, modelo local, web, memoria y subagentes mediante el objeto raíz `agent`.

### Herramientas de Navegación e Información Web
* **`web_search` (`TavilyWebSearchTool` / `BraveWebSearchTool`):** Consulta índices de internet a través de las APIs de Tavily o Brave Search según las credenciales provistas, retornando títulos, URLs y resúmenes de contenido depurados.
* **`web_get_content` (`WebGetTikaTool` / `WebGetTool`):** Descarga el contenido de una dirección HTTP/HTTPS remota, limpia etiquetas o código embebido mediante Apache Tika y sirve el contenido textual procesado a través de la infraestructura de paginación.
* **`get_current_location` (`LocationTool`):** Determina la ubicación geográfica aproximada y huso horario del entorno anfitrión mediante resolución de la dirección IP pública.
* **`get_weather` (`WeatherTool`):** Consulta condiciones meteorológicas y predicciones en tiempo real utilizando la API abierta de Open-Meteo.
* **`get_current_time` (`TimeTool`):** Obtiene la fecha, hora formal y zona horaria del sistema, soportando conversiones explícitas de huso horario para contextualizar referencias temporales relativas.

### Herramientas de Sensores y Gestión Sensorial
* **`pool_event` (`PoolEventTool`):** Herramienta técnica que permite consultar o simular la recepción de estímulos pendientes en la cola sensorial de [SensorsService](https://jjdelcerro.github.io/noema/docs/03-catalogo-de-servicios/03-sensors.html).
* **`schedule_alarm` (`ScheduleAlarmTool`):** Programa un recordatorio en [SchedulerService](https://jjdelcerro.github.io/noema/docs/03-catalogo-de-servicios/04-scheduler.html) interpretando descripciones temporales mediante Natty para disparar un evento diferido.
* **`sensor_start` (`SensorStartTool`):** Reactiva canales sensoriales previamente silenciados para volver a recibir estímulos en el despachador.
* **`sensor_stop` (`SensorStopTool`):** Suspende temporalmente la escucha de eventos de uno o varios canales sensoriales para evitar interrupciones durante tareas de alta concentración.
* **`sensor_status` (`SensorStatusTool`):** Devuelve un informe con el inventario de sensores registrados, su estado de silencio, naturaleza de señal y estadísticas de eventos recibidos y entregados.

### Herramientas de Identidad y Entorno
* **`consult_environ` (`ConsultEnvironTool`):** Recupera bajo demanda el contenido íntegro de un módulo de conocimiento denso del entorno (`var/identity/environ/<modulo>.md`), referenciado previamente en el índice ligero del prompt de sistema.

### Herramientas de Habilidades ([Skills](https://jjdelcerro.github.io/noema/docs/04-subsistemas-de-ejecucion-y-capacidades/03-skills.md))
* **`list_skills` (`ListSkillsTool`):** Lista el catálogo de protocolos técnicos y habilidades procedimentales disponibles en `.claude/skills/`.
* **`activate_skill` (`ActivateSkillTool`):** Carga las instrucciones de un procedimiento técnico, fijándolas en [ProjectedMemory](https://jjdelcerro.github.io/noema/docs/02-el-sistema-de-memoria/050-memoria-proyectada.html) para prevenir su pérdida por consolidación.
* **`deactivate_skill` (`DeactivateSkillTool`):** Da por concluida la ejecución de una habilidad, retirándola de la memoria proyectada y cancelando los recordatorios periódicos.
* **`read_skill_resource` (`ReadSkillResourceTool`):** Lee archivos complementarios de documentación, plantillas o esquemas ubicados dentro del directorio de una habilidad específica.
* **`run_skill_script` (`RunSkillScriptTool`):** Ejecuta scripts auxiliares ubicados en la carpeta `scripts/` del directorio de una habilidad técnica.

### Herramientas de Subagentes
* **`list_subagents` (`ListSubagentsTool`):** Descubre y cataloga las recetas de subagentes declaradas en `var/subagents/`, exponiendo descripciones y parámetros requeridos.
* **`launch_subagent` (`LaunchSubagentTool`):** Pone en marcha un subagente especializado en segundo plano de manera aislada, confirmando su identificador numérico de seguimiento.

### Herramientas de Comunicación
* **`email_list_inbox` (`EmailListTool`):** Lista las cabeceras de los correos más recientes en la bandeja de entrada del servidor IMAP configurado.
* **`email_read` (`EmailReadTool`):** Descarga el mensaje identificado por su UID y lo convierte a texto limpio mediante Apache Tika.
* **`email_send` (`EmailSendTool`):** Redacta y despacha un correo electrónico hacia un destinatario utilizando el servidor SMTP autenticado.
* **`telegram_send` (`TelegramTool`):** Envía un mensaje instantáneo al chat autorizado del usuario a través de la API de Telegram Bot.

### Adaptadores Dinámicos de Protocolo
* **`McpToolWrapper`:** Componente adaptador generado dinámicamente por `McpServiceImpl` para cada función descubierta en servidores MCP conectados, mapeando esquemas de argumentos y gestionando modos de confirmación humana según el nivel de acceso declarado (`READ`, `WRITE`, `EXECUTION`, `WEB`).

---

## 7. Construcción, Empaquetado y Modos de Despliegue

### Proceso de Compilación
El proyecto se compila con Apache Maven exigiendo JDK 25:
1. En la fase `generate-resources`, el plugin `exec-maven-plugin` invoca el script `download.sh`, asegurando la presencia local de los modelos ONNX cuantizados de embeddings y del modelo de lenguaje pequeño Qwen3.5.
2. El compilador de Maven compila el código con advertencias habilitadas y vinculación de módulos vectoriales (`--add-modules jdk.incubator.vector`).
3. El plugin `maven-shade-plugin` ensambla un fat JAR ejecutable (`io.github.jjdelcerro.noema.main-0.1.0.jar`), integrando todas las dependencias y fusionando ficheros de configuración mediante `ServicesResourceTransformer`.

### Despliegue y Ejecución
El script de arranque `./noema` gestiona la carga de librerías nativas y habilita módulos del JDK:

```bash
java -agentlib:jdwp=transport=dt_socket,address=8765,server=y,suspend=n \
     --add-modules jdk.incubator.vector \
     --enable-native-access=ALL-UNNAMED \
     -jar target/io.github.jjdelcerro.noema.main-0.1.0.jar "$@"
```

La clase selectora `Main` evalúa los argumentos de entrada y lanza una de las cuatro modalidades disponibles:

* **Modo Terminal de Texto (TUI - `MainLanterna`):** Se activa por defecto o mediante `--tui` / `-t`. Utiliza la librería [Lanterna](https://jjdelcerro.github.io/noema/docs/05-capa-de-presentacion-e-interfaces/02-tui.html) para ofrecer una interfaz gráfica completa dentro del emulador de terminal. Soporta captura de ratón, navegación de historial con renderizado de Markdown coloreado (`ColoredHistoryRenderer`), cronómetro de razonamiento en tiempo real y árbol de configuración jerárquico navegable en dos paneles.
* **Modo Interfaz Gráfica de Escritorio (GUI - `MainGUI`):** Se activa mediante `--gui` / `-g` / `--swing`. Despliega una interfaz gráfica en [Swing](https://jjdelcerro.github.io/noema/docs/05-capa-de-presentacion-e-interfaces/01-swing.html) con tema oscuro FlatLaf. Ofrece burbujas independientes por mensaje, visualización de razonamiento en paneles segregados, selector rápido de herramientas activas, consola web H2 integrada y un editor de código completo con soporte de búsqueda y reemplazo (RSyntaxTextArea) para los archivos `.properties`.
* **Modo Servidor Web y Demonio Headless (`MainWeb`):** Se activa mediante `--web` / `-w` / `--serve`. Arranca el servidor Javalin embebido exponiendo la [interfaz web](https://jjdelcerro.github.io/noema/docs/05-capa-de-presentacion-e-interfaces/03-web.html) SPA (Single Page Application) en el puerto configurado (por defecto 8080). Gestiona la sesión a través de canales SSE en tiempo real para el streaming de pensamientos, respuestas y eventos, y ofrece un editor de texto modal y un explorador de directorios para administrar el sistema desde cualquier navegador sin entornos gráficos en el host.
* **Modo Línea de Comandos Básica (CLI - `MainConsole`):** Se activa mediante `--console` / `-c`. Proporciona un entorno REPL ligero basado en JLine con edición multilínea (Alt+Enter), confirmación textual de herramientas y comandos internos como `/settings` y `/quit`.

---

## 8. Conclusión

Noema se consolida como una implementación rigurosa, compacta y autosuficiente de un agente autónomo orientado a la reflexión e investigación continua. 

Su diseño destaca por resolver los retos fundamentales de los agentes modernos (saturación atencional, pérdida de contexto operativo, alucinación de recuerdos y seguridad en la ejecución) mediante soluciones de ingeniería puramente locales:
* Una **memoria estratificada y dinámica** que combina una base de datos relacional inmutable con un pipeline transformador en memoria proyectada, amnesia controlada de salidas pesadas y validación matemática de citas históricas.
* Una **política estricta de soberanía e infraestructura cero**, resolviendo internamente tareas como el cálculo de embeddings con MaxP, el versionado mediante [JavaRCS](https://github.com/jjdelcerro/io.github.jjdelcerro.noema/blob/master/pom.xml) nativo y la concurrencia de bases de datos embebidas.
* Un **sistema de seguridad multicapa** que aísla el sistema de archivos, requiere autorización previa explícita para operaciones con efectos secundarios y previene pérdidas accidentales mediante confirmaciones automáticas de versión.
* Una **arquitectura desacoplada** a través de contratos como [AgentConsole](https://jjdelcerro.github.io/noema/docs/05-capa-de-presentacion-e-interfaces/00-contrato-agentconsole-y-comunicacion.md), permitiendo que el mismo núcleo cognitivo opere de forma indistinta sobre interfaces de consola de terminal, ventanas de escritorio tradicionales o servicios web distribuidos.
