# Informe Técnico de Arquitectura y Diseño: Noema

## 1. Visión general

Noema es una plataforma de agente conversacional de ejecución autónoma y continua implementada en Java 25. Ha sido concebida como un entorno de interlocución técnica, análisis conceptual y acompañamiento en tareas de investigación de diversa índole a lo largo del tiempo. 

El diseño del sistema no está enfocado de manera exclusiva al desarrollo de software, sino a la interacción analítica prolongada y transversal. El modelo de interacción descarta la noción de sesiones de chat efímeras o segmentadas: opera sobre el principio de una única conversación acumulativa y persistente ligada a un espacio de trabajo.

Para posibilitar esta persistencia temporal sin incurrir en desbordamientos de ventanas de contexto ni en degradación atencional, Noema implementa un modelo de memoria estratificada que combina persistencia relacional local, compresión narrativa periódica mediante modelos de lenguaje, búsqueda semántica en memoria y una proyección delimitada hacia el modelo de razonamiento.

El sistema está diseñado bajo el principio de autonomía de infraestructura:
* No requiere servicios externos de bases de datos vectoriales ni middleware distribuido.
* Toda la persistencia relacional reside en bases de datos embebidas H2.
* La vectorización se procesa íntegramente en el proceso local de la JVM mediante modelos ONNX embebidos y ONNX Runtime.
* El control de versiones local preventivo y el cálculo de diferencias textuales se realiza mediante implementaciones puras en Java, integrando [JavaRCS](https://github.com/jjdelcerro/io.github.jjdelcerro.javarcs).
* La conectividad externa queda restringida a las llamadas hacia APIs de inferencia de modelos de lenguaje (OpenAI, DeepSeek, Groq, OpenRouter) o endpoints opcionales de búsqueda y mensajería.

## 2. Stack tecnológico

El proyecto utiliza un conjunto homogéneo de dependencias ejecutadas sobre la máquina virtual de Java:

* Plataforma de ejecución: Java SE 25 (utilizando funciones de incubación de vectores e integración de compilador interno para optimización).
* Orquestación de modelos de lenguaje: LangChain4j (versión 1.16.3) junto con módulos de integración con OpenAI, MCP (`langchain4j-mcp`) y bindings locales de inferencia.
* Persistencia relacional local: Motor H2 Database (versión 2.2.224) operando en modo embebido de proceso único con soporte de almacenamiento de tipos CLOB y BLOB.
* Inferencia y vectorización local: ONNX Runtime GenAI (`0.15.2`), ONNX Runtime y modelos ONNX embebidos para cálculo de similitud vectorial (`paraphrase-multilingual-MiniLM-L12-v2`, `bge-small-en-v15-q`, `all-minilm-l6-v2-q`), complementados opcionalmente con JLama Core (`0.8.4`).
* Subsistema de scripting embebido: Apache Groovy (`5.1.3`) con soporte de serialización JSON (`groovy-json`) y personalización de análisis sintáctico seguro (`SecureASTCustomizer`).
* Evaluación de expresiones de configuración: MVEL2 (`2.5.0.Final`) para el cálculo de condiciones dinámicas y reglas de activación en las interfaces de usuario.
* Extracción y manipulación documental: Apache Tika (`2.8.0`), CommonMark (`0.28.0`) con extensiones GFM (tablas, alertas, tachado, listas de tareas, atributos de imagen), Java-Diff-Utils (`4.12`) y FuzzyWuzzy (`1.4.0`).
* Análisis temporal: Natty (`0.13`) para interpretación sintáctica de expresiones temporales en lenguaje natural y PrettyTime (`5.0.7.Final`) para descripciones relativas.
* Servidor y transporte web: Javalin (`6.1.3`) sobre Jetty embebido, con soporte nativo para Server-Sent Events (SSE) y resolución estática de recursos web.
* Control de versiones local: [JavaRCS](https://github.com/jjdelcerro/io.github.jjdelcerro.javarcs) (`0.1.0-SNAPSHOT`), implementación nativa Java del sistema de control de revisiones RCS.
* Interfaces de usuario:
  * Entorno gráfico de escritorio: [Swing](https://jjdelcerro.github.io/noema/docs/05-capa-de-presentacion-e-interfaces/01-swing.html) con FlatLaf (`3.4.1`), MigLayout (`11.3`), RSyntaxTextArea (`3.6.1`) y Palantir Java Format (`2.102.0`).
  * Entorno de terminal: [Lanterna](https://jjdelcerro.github.io/noema/docs/05-capa-de-presentacion-e-interfaces/02-tui.html) (`3.1.2`) y JLine (`3.21.0`).
  * Entorno web: HTML5, CSS3 modular por áreas y JavaScript ES Modules sin frameworks pesados, con editor Ace embebido y web components para renderizado de árboles.
* Mensajería y conectividad opcional: Jakarta Mail (`2.0.3` con proveedor Angus) y Telegram Bot API (`7.1.1`).
* Registro de eventos del sistema: Log4j 2 (`2.23.1`) canalizado a través de la interfaz SLF4J.

## 3. Estructura de paquetes, interfaces e implementación

La base de código está estructurada según una separación estricta entre contratos públicos, clases base para proveedores de servicios e implementaciones de dominio.

* Paquete `io.github.jjdelcerro.noema.lib.api`: Contiene los contratos fundamentales del agente. Define interfaces para el orquestador (`Agent`), el gestor de ciclo de vida (`AgentManager`), la gestión de rutas (`AgentPaths`), la configuración jerárquica (`AgentSettings`), las restricciones de seguridad (`AgentAccessControl`), la consola de entrada y salida (`AgentConsole`), las herramientas ejecutables (`AgentTool`), las acciones de ciclo de vida (`AgentActions`), los conectores a bases de datos (`ConnectionSupplier`) y el contrato de servicio base (`AgentService`).
* Paquete `io.github.jjdelcerro.noema.lib.api.memory`: Declara las interfaces del subsistema de memoria estratificada:
  * Subpaquete `episodic`: Contrato de persistencia física `EpisodicMemory` y registros atómicos de interacción `Turn`.
  * Subpaquete `recent`: Contrato de sesión de trabajo inmediata `RecentMemory` y marcas de corte para compactación.
  * Subpaquete `consolidate`: Contrato de puntos de consolidación narrativa `ConsolidateMemory`.
  * Subpaquete `projected`: Contrato de proyección contextual `ProjectedMemory`, zonificación atencional `ContextZoning` y factorías de operaciones registrables `ProjectedMemoryOperation`.
* Paquete `io.github.jjdelcerro.noema.lib.api.model`: Contratos de abstracción para proveedores de lenguaje: `ChatModel`, `ModelParameters`, `ModelType` y retorno de flujos en tiempo real con `ModelStreamCallback`.
* Paquete `io.github.jjdelcerro.noema.lib.api.services`: Define las interfaces de los servicios del sistema, incluyendo `ReasoningService`, `MemoryConsolidationService`, `SensorsService`, `ScriptingService` y `SubagentsService`.
* Paquete `io.github.jjdelcerro.noema.lib.spi`: Clases abstractas de soporte y extensión técnica para la creación de servicios (`AbstractAgentService`), acciones (`AbstractAgentAction`) y módulos de scripting (`AbstractScriptModule`).
* Paquete `io.github.jjdelcerro.noema.lib.impl`: Implementaciones concretas del núcleo:
  * Clases operativas del motor: `AgentImpl`, `AgentManagerImpl`, `AgentPathsImpl`, `AgentSettingsImpl`, `AgentAccessControlImpl`, `AgentActionsImpl` y el evaluador de expresiones `ExpressionEvaluator`.
  * Persistencia relacional y serialización: `SQLProviderImpl` y adaptadores Gson en `GsonUtils`.
  * Módulos de servicios: Subpaquetes dedicados para `embeddings`, `reasoning`, `memory`, `sensors`, `scheduler`, `scripting`, `subagents`, `skills`, `mcp`, `email` y `telegram`.
  * Herramientas ejecutables: Distribuidas bajo los subpaquetes de cada servicio que las aporta funcionalmente (por ejemplo, `services.reasoning.tools.*`, `services.memory.tools.*`).
  * Utilidades auxiliares: Paquete `impl.utils` con soporte para recorrido de archivos (`FsUtils`), búsqueda difusa y exacta (`SearchUtils`, `FileFuzzySearchUtils`), transformaciones de XML a JSON (`ConversationJsonToXml`) e inferencia con modelos locales SLM (`SLMUtils`).
* Paquetes de interfaz de usuario (`io.github.jjdelcerro.noema.ui`):
  * `ui.console`: Implementación basada en línea de comandos clásica con terminal interactivo JLine.
  * `ui.lanterna`: Implementación de interfaz de texto en consola (TUI) con ventanas, paneles deslizantes y controles de navegación de terminal.
  * `ui.swing`: Interfaz gráfica completa de escritorio con paneles MigLayout, visualizadores Markdown y configuración por árbol.
  * `ui.webclient`: Servidor web Javalin, manejadores REST (`handlers.*`), consola Server-Sent Events (`SseAgentConsole`), operaciones de consciencia de interfaz (`state.*`) y subsistemas de editor y notas (`workspace.*`).
* Paquete de inicio (`io.github.jjdelcerro.noema.main`): Contiene los puntos de entrada ejecutables (`Main`, `MainSwing`, `MainLanterna`, `MainConsole`, `MainWeb`) y los procedimientos de inicialización básica en `BootUtils`.

## 4. Arquitectura y diseño

### 4.1. El Kernel (o Core)

El núcleo del agente articula la inicialización, la coordinación de servicios, la topología en disco y la abstracción del almacenamiento relacional.

* `Agent` y `AgentManager`: El contrato central `Agent` representa la instancia operativa asociada a un espacio de trabajo específico. Ofrece acceso a la configuración, los controladores de consola, el repositorio de memoria episódica, la asignación de bases de datos y la orquestación de herramientas. Por su parte, `AgentManager` actúa como registro central en memoria de factorías de servicios (`AgentServiceFactory`), factorías de operaciones de proyección (`ProjectedMemoryOperationFactory`), factorías de extensiones de scripting (`ScriptModuleFactory`) y catálogo de subagentes activos.
* Ciclo de vida: El [ciclo de vida](https://jjdelcerro.github.io/noema/docs/01-fundamentos-y-ciclo-de-vida/02-agent-paths.html) de la aplicación comprende las etapas de validación de configuración, configuración de topología de directorios, arranque de bases de datos, instanciación de servicios y registro de ganchos de apagado (`ShutdownHook`). Durante la inicialización (`setupServices`), el agente consulta el catálogo de factorías registradas en `AgentManager`, instanciando cada servicio o reutilizando los declarados como compartidos. Al invocar `start()`, se habilitan los sensores del sistema, se registran las herramientas asociadas a los servicios en el motor de razonamiento y se inician los bucles de eventos. La parada (`stop()`) desconecta de forma ordenada los componentes periféricos, cierra conexiones relacionales y descarga servidores en ejecución.
* Infraestructura de datos: La persistencia se desacopla mediante la interfaz `ConnectionSupplier`, que suministra conexiones transaccionales hacia dos instancias diferenciadas de bases de datos H2: una para la memoria episódica y otra para los servicios periféricos (como el planificador de alarmas). La clase `SQLProvider` desacopla las sentencias SQL de los componentes lógicos, resolviendo las consultas asociadas a cada identificador técnico.
* Topología de archivos: La estructura de almacenamiento local gobernada por [AgentPaths](https://jjdelcerro.github.io/noema/docs/01-fundamentos-y-ciclo-de-vida/02-agent-paths.html) se organiza dentro del directorio de trabajo en la carpeta oculta `.noema-agent`, estructurada en las siguientes ramas:
  * `var/config`: Ficheros de propiedades y descriptores JSON de configuración (`settings.json`, `settingsui.json`, `models.properties`, `available_tools.properties`).
  * `var/lib`: Bases de datos embebidas H2 (`episodic_memory.mv.db`, `service.mv.db`) y carpetas de almacenamiento textual de memorias consolidadas.
  * `var/cache`: Cachés volátiles generadas por herramientas de extracción documental o índices semánticos.
  * `var/tmp`: Archivos temporales de salida de procesos shell, volcados de depuración de contexto y logs de subagentes.
  * `var/log`: Archivo rotativo de eventos técnicos generado por Log4j 2.
  * `home`: Directorio de trabajo seguro utilizado como entorno de aislamiento cuando se activa el confinamiento mediante Firejail.
  * `var/globalconfig`: Ubicación de configuración de respaldo a nivel de usuario en el sistema (`~/.config/noema-agent`).

### 4.2. Capacidades Horizontales (Cross-cutting Concerns)

Las capacidades transversales garantizan el aislamiento del proceso, el cumplimiento de políticas de acceso y la parametrizaсión del agente.

* Seguridad y control de acceso: Implementado en [AgentAccessControl](https://jjdelcerro.github.io/noema/docs/01-fundamentos-y-ciclo-de-vida/04-seguridad-y-control-de-acceso.html), centraliza la validación de todas las operaciones sobre el sistema de archivos, el acceso a recursos web y la ejecución de comandos. La resolución de rutas se apoya en rutas canónicas absolutas evaluadas frente a listas blancas de rutas permitidas (`allowedExternalPaths`) y listas negras de lectura (`nomReadablePaths`) o escritura (`nomWritablePaths`). El componente incluye reglas de especificidad para permitir accesos selectivos dentro de ramas denegadas ("agujeros autorizados") e invariantes rígidos que impiden la modificación de repositorios Git, copias de control de versiones JavaRCS o la manipulación directa de la configuración interna de `.noema-agent/var`.
* Gestión de rutas y límites de aislamiento: Gobernado por [AgentPaths](https://jjdelcerro.github.io/noema/docs/01-fundamentos-y-ciclo-de-vida/02-agent-paths.html), traduce nombres simbólicos de recursos internos a rutas absolutas del disco, discriminando si deben resolverse dentro del espacio de trabajo del usuario o en la configuración global del sistema operativo. Determina los límites del entorno de ejecución delimitando qué rutas pertenecen a la zona de trabajo segura.
* Sistema de configuración jerárquica: Centralizado en [AgentSettings](https://jjdelcerro.github.io/noema/docs/01-fundamentos-y-ciclo-de-vida/03-agent-settings.html), ofrece un almacén jerárquico accesible mediante rutas delimitadas por barras (por ejemplo, `reasoning/provider/url`). El árbol está modelado mediante nodos compuestos (`AgentSettingsGroup`) que albergan cadenas de texto (`AgentSettingsString`), listas de rutas (`AgentSettingsPaths`), listas con casillas de selección (`AgentSettingsCheckedList`) o listas genéricas (`AgentSettingsList`). La configuración incluye un motor de evaluación en tiempo de diseño (`ExpressionEvaluator`) que permite condicionar dinámicamente si ciertos parámetros o herramientas se encuentran habilitados en función del valor de otros ajustes del sistema.

### 4.3. Servicios Cognitivos

Los servicios cognitivos constituyen el núcleo deliberativo y de retención histórica del agente, compartiendo la estructura de almacenamiento de [EpisodicMemory](https://jjdelcerro.github.io/noema/docs/02-el-sistema-de-memoria/020-memoria-episodica.html).

* [ReasoningService](https://jjdelcerro.github.io/noema/docs/03-catalogo-de-servicios/01-reasoning.html): Es el componente encargado de procesar los eventos que entran al sistema, orquestar el modelo de lenguaje primario y gobernar la ejecución de herramientas. Administra el concepto de subcanales (`subchannels`), lo que permite que múltiples terminales de conversación paralelos (por ejemplo, la interfaz gráfica de usuario, una consola de texto o un canal web) mantengan estados de interacción recientes independientes sobre una única base de conocimiento episódico. Implementa el ciclo iterativo de invocación al LLM, evaluando solicitudes de herramientas (`ToolExecutionRequest`), ejecutándolas contra el catálogo activo y retroalimentando el contexto hasta obtener una respuesta final o agotar los reintentos permitidos.
* [MemoryConsolidationService](https://jjdelcerro.github.io/noema/docs/03-catalogo-de-servicios/02-memory-consolidation.html): Opera como un sintetizador periódico del historial de interacción. Cuando la memoria de trabajo reciente supera el umbral estipulado de turnos o volumen de texto, este servicio recupera los turnos atómicos desde la base de datos episódica, aplica filtros de refinamiento sobre salidas operativas voluminosas y notas transitorias, y somete el bloque a un modelo de lenguaje con un protocolo estricto de consolidación. El resultado es un nuevo punto de control narrativo estructurado en un resumen factual y una crónica del razonamiento, donde cada dato crítico queda vinculado a su turno de origen mediante etiquetas de citación formal (`{cite:ID}`).

### 4.4. Servicios de Periferia

Los servicios de periferia dotan al agente de interfaces de interacción con el entorno exterior, planificación temporal y capacidades analíticas auxiliares.

* [SensorsService](https://jjdelcerro.github.io/noema/docs/03-catalogo-de-servicios/03-sensors.html): Constituye el canal de entrada de estímulos hacia el agente. Gestiona canales perceptivos declarados con diferentes naturalezas de señal (discretas, acumulativas, de estado, fusionables o interacciones directas de usuario). Los estímulos entrantes son procesados en memorias intermedias, asociados a estadísticas de frecuencia y encolados en una estructura de entrega sincronizada que alimenta al bucle de procesamiento del razonador.
* [EmbeddingsService](https://jjdelcerro.github.io/noema/docs/03-catalogo-de-servicios/05-embeddings.html): Suministra servicios de vectorización de texto en memoria sin dependencia de bases de datos externas. Aplica modelos ONNX locales cuantizados en punto flotante (`paraphrase-multilingual-MiniLM-L12-v2`). Incluye segmentación automática de documentos mediante ventana de caracteres y cálculo de similitud coseno MaxP (Maximum Passage), permitiendo la clasificación y filtrado de fragmentos de texto en memoria antes de remitirlos a la atención del modelo principal.
* [SchedulerService](https://jjdelcerro.github.io/noema/docs/03-catalogo-de-servicios/04-scheduler.html): Implementa un subsistema de temporización y alarmas persistentes. Almacena en la base de datos relacional de servicios los recordatorios programados por el agente, parsea especificaciones temporales relativas o absolutas mediante la librería Natty y gestiona un ejecutor de hilo único que despacha eventos sensoriales al canal perceptivo en el instante previsto.
* [Scripting](https://jjdelcerro.github.io/noema/docs/04-subsistemas-de-ejecucion-y-capacidades/04-scripting.html) (`ScriptingService`): Proporciona un entorno de ejecución de código Groovy en la JVM local bajo un esquema de confinamiento de seguridad. Permite al agente ejecutar cálculos numéricos masivos, transformaciones de ficheros, consultas estructuradas y agregaciones mediante fachadas de programación agrupadas bajo el objeto de contexto `agent` (`agent.fs`, `agent.llm`, `agent.web`, `agent.annotation`, `agent.state`, `agent.subagents`).
* `SubagentsService`: Gobierna la creación y ejecución de trabajadores especializados aislados ([Subagentes](https://jjdelcerro.github.io/noema/docs/04-subsistemas-de-ejecucion-y-capacidades/02-subagentes.html)). Estos trabajadores operan en hilos independientes, con directorios temporales propios, bases de datos de memoria H2 dedicadas y consolas silenciadas en disco, ejecutando recetas declarativas expresadas en ficheros XML.
* `SkillsService`: Administra el catálogo de [skills](https://jjdelcerro.github.io/noema/docs/04-subsistemas-de-ejecucion-y-capacidades/03-skills.html) (habilidades técnicas procedimentales) ubicadas en `.claude/skills` o `var/skills`. Parsea directivas formateadas con metadatos YAML y permite fijar reglas procedimentales en la memoria de trabajo mientras dura la ejecución de una tarea especializada.
* `McpService`: Actúa como cliente unificado para servidores externos que implementan el Model Context Protocol (MCP). Puede negociar capacidades tanto a través de transporte por streams estándar (`stdio`) como por protocolos web basados en HTTP/SSE, registrando dinámicamente las herramientas descubiertas en el razonador de Noema.
* `EmailService`: Proporciona integración bidireccional con correo electrónico basada en un modelo de dos fases. Mantiene un hilo demonio que monitoriza la bandeja de entrada mediante IMAP IDLE, filtrando por remitentes autorizados y notificando eventos ligeros de cabecera; a su vez, expone herramientas para que el razonador pueda realizar la lectura saneada mediante Apache Tika y el envío de respuestas vía SMTP.
* `TelegramService`: Conecta al agente con un bot de Telegram mediante sondeo prolongado (long-polling). Aplica filtrado estricto por identificador de chat de usuario para impedir accesos no autorizados, inyecta los mensajes recibidos directamente como eventos en el canal sensorial y expone una herramienta para la emisión de mensajes proactivos.

## 5. Mecanismos detallados

### 5.1. Gestión de memoria estratificada

El [modelo de memoria](https://jjdelcerro.github.io/noema/docs/02-el-sistema-de-memoria/010-vision-general-de-modelo-de-memoria.html) de Noema está dividido en cuatro capas funcionales para conciliar la conservación histórica con los límites finitos de las ventanas de atención de los modelos de lenguaje:

* [EpisodicMemory](https://jjdelcerro.github.io/noema/docs/02-el-sistema-de-memoria/020-memoria-episodica.html) (Archivo histórico inmutable): Almacena de manera permanente en H2 todos los eventos atómicos de la vida del agente organizados como turnos (`Turn`). Cada turno registra marca de tiempo, subcanal, tipo de contenido (`chat`, `tool_execution`, `annotation`, `lookup_turn`), prompt de usuario, cadena de razonamiento interno, respuesta final, datos de llamadas a herramientas (`tool_call`), resultados de herramientas (`tool_result`) y un vector de incrustación semántica generado automáticamente en formato BLOB. Para optimizar el tamaño de la base de datos sin perder trazabilidad, los resultados de herramientas que superan los 2 KB son truncados en disco almacenando metadatos del corte, conservando intacto el vector de incrustación para la búsqueda.
* [RecentMemory](https://jjdelcerro.github.io/noema/docs/02-el-sistema-de-memoria/040-memoria-reciente.html) (Memoria de trabajo activa): Contiene la secuencia viva de mensajes de conversación (`ChatMessage`) del subcanal activo en memoria RAM, persistida en un fichero JSON local (`recent_memory-<subchannel>.json`). Mapea cada índice de mensaje a su identificador de turno en la memoria episódica. Supervisa el recuento de turnos consolidados únicos y determina cuándo se alcanza el umbral configurado (`reasoning/consolidation_turns`, por defecto 40 turnos) para activar la compactación. Al consolidar, recorta atómicamente la mitad más antigua respetando la integridad de bloques de llamadas a herramientas concurrentes para evitar dejar respuestas huérfanas.
* [ConsolidateMemory](https://jjdelcerro.github.io/noema/docs/02-el-sistema-de-memoria/030-memoria-consolidada.html) (Puntos de guardado y relato continuo): Representa los estados estables de conocimiento sintetizado. Sigue un esquema de persistencia híbrido: los metadatos (identificador, rango de turnos consolidados `cm_first` a `cm_last`, fecha y subcanal) residen en la tabla `consolidatememory` de H2, mientras que el cuerpo textual reside en disco en archivos Markdown (`consolidatememory/consolidatememory-<id>-<first>-<last>.md`). El texto se compone de un resumen factual y una crónica narrativa ("El Viaje"). Toda afirmación fáctica relevante incorpora citas directas `{cite:ID}` hacia los turnos de origen archivados en la memoria episódica.
* [ProjectedMemory](https://jjdelcerro.github.io/noema/docs/02-el-sistema-de-memoria/050-memoria-proyectada.html) (Ventana de contexto proyectada): Es el componente responsable de ensamblar el contexto delimitado que se remite a la API del LLM en cada paso del razonador. Ensambla:
  * El prompt de sistema base del agente complementado con el último relato de la memoria consolidada.
  * La lista de mensajes vivos presentes en la memoria reciente.
  * Un pipeline secuencial de operaciones de memoria proyectada (`ProjectedMemoryOperation`).
  * Inyección unificada de notificaciones efímeras del sistema generadas por las operaciones anteriores.

El pipeline de operaciones de memoria proyectada es extensible y registrable en `AgentManager`. Las operaciones se ejecutan ordenadas por prioridad:
* `TrimmingOperation` (Prioridad 10): Evalúa los mensajes según la partición delimitada por `ContextZoning`. En la zona antigua o podable (`PRUNABLE`), inspecciona los resultados de herramientas operativas pesadas (como lecturas de ficheros o salidas de comandos) y reemplaza su cuerpo por una cabecera reducida con la marca `CONTENT_TRIMMED: true`, reduciendo el uso de tokens sin eliminar el turno ni la intención de la llamada.
* `PendingAnnotationOperation` (Prioridad 20): Inspecciona la zona de riesgo intermedio (`RISK`). Si detecta lecturas de datos crudos pesados que no han sido acompañadas por una nota en `AnnotateObservationTool`, inyecta un recordatorio transitorio en el sistema para que el agente extraiga conclusiones antes de que el contenido sea recortado.
* `TemporalPerceptionOperation` (Prioridad 30): Compara la marca de tiempo de la interacción previa con el momento actual. Si el lapso supera el umbral establecido (por defecto, una hora), añade un aviso de consciencia temporal sobre el tiempo transcurrido.
* `PinnedTurnsOperationImpl` (Prioridad 5): Gestiona el anclaje de turnos derivados de herramientas que declaran `shouldPin()` (como la carga de directivas de skills o mapas de arquitectura). Mantiene estos pares de solicitud y respuesta al inicio del contexto activo tras las directivas de sistema, haciéndolos inmunes a la compactación, y emite recordatorios periódicos cada cinco turnos.
* `PeripheralAwarenessOperation` (Prioridad 1000): Consulta en la base de datos episódica la actividad reciente en otros subcanales concurrentes durante los últimos siete días. Si detecta sesiones activas en paralelo, inyecta resúmenes de contexto periférico indicando qué se está tratando en otros terminales para permitir transferencias de conocimiento sin cruzar los diálogos.
* `UIAwarenessOperation` (Prioridad 30): Operación aportada por el servidor web que inyecta en el prompt la lista de ficheros abiertos en el editor, el fichero activo, la línea del cursor y la selección textual del usuario reportada por la interfaz.

La partición de la atención se gobierna a través de `ContextZoning`, dividiendo la memoria reciente en tres zonas semiabiertas [min, max):
* `PRUNABLE`: Los turnos más antiguos (primer 50% del total configurado), sujetos a la poda de cuerpos masivos.
* `RISK`: Zona intermedia (siguiente 25%), intacta pero sujeta a comprobaciones de retención.
* `SAFE`: Los turnos más recientes (último 25%), completamente intactos y exentos de intervención.

### 5.2. Gestión de la identidad del agente

La identidad del agente y su base de conocimiento contextual se configuran de manera declarativa en el sistema de archivos:

* Identidad central (`var/identity/core`): Alberga documentos normativos que definen las directivas operativas del agente (`01_identidad_base.md`, `02_protocolo_operativo.md`, `03_stack_y_normas_codigo.md`, `04_filosofia_arquitectonica.md`). Estos módulos se incorporan directamente en el prompt de sistema. Su activación puede seleccionarse de forma modular mediante la propiedad `reasoning/identity/core` en la configuración jerárquica.
* Consciencia de entorno (`var/identity/environ`): Gestiona el conocimiento denso del entorno y los proyectos mediante un sistema de dos niveles. En el prompt de sistema solo se inyectan archivos ligeros de referencia semántica con extensión `.ref.md` (resúmenes breves que actúan como descriptores de catálogo). Cuando el agente determina que necesita acceder al contenido íntegro y detallado de uno de estos módulos, invoca la herramienta `ConsultEnvironTool`, que realiza la carga bajo demanda del archivo `.md` homólogo completo desde el disco.

### 5.3. Gestión de habilidades (skills)

El subsistema de [skills](https://jjdelcerro.github.io/noema/docs/04-subsistemas-de-ejecucion-y-capacidades/03-skills.html) permite incorporar guías procedimentales estructuradas sin recargar permanentemente el prompt de sistema principal:

* Descubrimiento: `SkillsServiceImpl` escanea de forma jerárquica las carpetas `.claude/skills` en el espacio de trabajo del usuario y `var/skills` en la configuración del agente.
* Estructura: Cada habilidad se aloja en su propio directorio y contiene obligatoriamente un fichero `SKILL.md` estructurado con una cabecera YAML frontmatter (`name`, `description`, `version`) seguida del cuerpo instructivo. Puede incluir subdirectorios de documentación auxiliar (`references/`) y scripts ejecutables (`scripts/`).
* Ciclo de activación: Al invocar `ActivateSkillTool`, el contenido de la habilidad se remite al modelo y la herramienta declara `shouldPin() = true`. Esto provoca que `PinnedTurnsOperationImpl` fije la directiva procedimental en la memoria proyectada del subcanal y emita recordatorios periódicos.
* Conclusión: Cuando el procedimiento finaliza, la invocación de `DeactivateSkillTool` localiza el estado anclado mediante un predicado coincidente y lo retira de la memoria proyectada.

### 5.4. Gestión de eventos y protocolo de estímulos

El subsistema sensorial desacopla la recepción asíncrona de estímulos externos del ciclo de razonamiento del LLM:

* Clasificación de señales (`SensorNature`):
  * `DISCRETE`: Eventos atómicos e irreducibles. Cada estímulo se procesa en un turno independiente.
  * `MERGEABLE`: Flujos conversacionales o notificaciones continuas. Múltiples estímulos que llegan mientras el agente está ocupado se concatenan cronológicamente en un único bloque antes de entregarse.
  * `AGGREGATABLE`: Estímulos de alta frecuencia donde prima el volumen. Se unifican en un contador numérico con marcas de tiempo de inicio y fin.
  * `STATE`: Condiciones ambientales volátiles. Cada actualización sobrescribe a la anterior en memoria; solo se entrega el valor más reciente en el momento en que el razonador queda libre.
  * `USER`: Mensajes directos del usuario interactivo.
* Simulación de herramienta ficticia (`poll_event`): Debido a que las APIs de modelos de lenguaje imponen una alternancia rígida de turnos (`User -> Assistant -> Tool -> ToolResult`), la inyección de estímulos de sensores asíncronos en medio de una sesión activa rompería el protocolo si se introdujeran como texto arbitrario. `AbstractSensorEvent` resuelve este requisito mediante una simulación: genera un mensaje sintético `AiMessage` que contiene un `ToolExecutionRequest` dirigido a una herramienta ficticia llamada `poll_event`, emparejado inmediatamente con un `ToolExecutionResultMessage` que contiene la carga útil en formato JSON. De este modo, el motor asimila los eventos como si hubieran sido consultados de manera intencional, preservando la validez del esquema conversacional.
* Control de distracciones: Cada sensor declara si es silenciable (`silenceable`). Mediante las herramientas `sensor_stop` y `sensor_start`, el modelo o el usuario pueden silenciar canales ruidosos. Los eventos silenciados continúan actualizando los contadores estadísticos pero no se encolan para su entrega.

### 5.5. Gestión de la seguridad

El subsistema de seguridad se fundamenta en un modelo de contención y trazabilidad determinista:

* Restricción al sistema de archivos: Toda ruta física se normaliza y valida contra `AgentAccessControl` antes de cualquier operación de entrada/salida. El acceso está restringido al espacio de trabajo raíz y a las rutas explícitamente listadas en la lista blanca `allowed_external_paths`. Las solicitudes fuera de estos límites son rechazadas arrojando excepciones de seguridad. Las listas negras `nom_readable_paths` y `nom_writable_paths` permiten anular permisos puntuales. Si se requiere autorizar una subcarpeta dentro de un área bloqueada, el sistema permite habilitar excepciones cuando la ruta autorizada tiene una profundidad de segmentos estrictamente superior a la regla de bloqueo.
* Confirmación de operaciones: Cuando la propiedad `access_control/humanConfirmationRequired` está activa, cualquier herramienta configurada con modos operativos de modificación (`MODE_WRITE`), ejecución en el sistema operativo (`MODE_EXECUTION`) o interacción web externa (`MODE_WEB`) suspende la ejecución y solicita autorización interactiva al usuario a través del contrato `AgentConsole.confirm()`.
* Control de versiones preventivo: Como salvaguarda ante modificaciones indebidas de código o documentos, las herramientas de manipulación de ficheros (`FileWriteTool`, `FilePatchTool`, `FileSearchAndReplaceTool` y el módulo `FsModule`) se integran con [JavaRCS](https://github.com/jjdelcerro/io.github.jjdelcerro.javarcs). Siempre que el parámetro `access_control/enable_rcs_backup` esté habilitado, el sistema ejecuta de forma automática una operación de registro de versión (`CheckinOptions` con autoría del modelo) sobre el archivo preexistente antes de alterar un solo byte en disco. Esto permite revertir cualquier cambio no deseado mediante `FileHistoryTool` y `FileRecoveryTool`.
* Aislamiento por procesos: Si el comando del sistema `firejail` se encuentra disponible en el host Linux y se habilita `access_control/enable_firejail`, la herramienta `ShellExecuteTool` reescribe el comando para confinar la ejecución de Bash dentro de un sandbox del sistema operativo, asignando una carpeta `home` temporal propia bajo `.noema-agent/home` y bloqueando la visibilidad del resto del disco.

### 5.6. Flujos en ReasoningService

El procesamiento de interacciones se articula en torno a dos bucles:

* Bucle de atención (`eventDispatcher`): Se ejecuta en un hilo de plataforma dedicado (`Noema-Event-Dispatcher`). Permanece en espera pasiva en `SensorsService.getEvent()`. Cuando un sensor despacha un evento, el hilo despierta, fija el subcanal correspondiente y delega en `processSingleEvent()`.
* Bucle de resolución de turnos:
  1. Identificación del tipo de evento: Si es de usuario, se añade a `RecentMemory` como mensaje de usuario; si es de sensor, se inserta el par simulado de consulta y respuesta de herramienta.
  2. Construcción de proyección: Se ensambla la lista de mensajes combinando prompt de sistema, relato consolidado, mensajes recientes y el pipeline de operaciones de proyección.
  3. Llamada al modelo: Se remite la proyección a `ChatModel.generate()` junto con las especificaciones de herramientas autorizadas y activas.
  4. Procesamiento de respuesta:
     * Si el modelo genera texto, se emite a la consola y se almacena en la memoria episódica.
     * Si emite solicitudes de ejecución de herramientas (`toolExecutionRequests`), el servicio evalúa permisos, solicita confirmación si procede, ejecuta cada herramienta, registra el turno en `EpisodicMemory` como `tool_execution` y anexa el resultado a `RecentMemory`. El bucle se reitera de inmediato para que el modelo procese el resultado.
     * Si el modelo finaliza por causa de ejecución de herramientas pero omite la llamada formal en el protocolo, el sistema reinyecta un mensaje de insistencia acotado a un máximo de tres reintentos.
  5. Evaluación de compactación: Tras completar el turno, se consulta `RecentMemory.needConsolidation()`. Si se supera el límite de turnos configurado, se activa de forma síncrona el servicio de consolidación.

### 5.7. Subagentes

El subsistema de [Subagentes](https://jjdelcerro.github.io/noema/docs/04-subsistemas-de-ejecucion-y-capacidades/02-subagentes.html) permite desacoplar tareas intensivas o exploratorias del flujo de diálogo principal:

* Definición declarativa: Cada subagente se define en un descriptor XML dentro de `var/subagents/` (por ejemplo, `document_indexer.xml` o `project_mapper.xml`). Especifica nombre, descripción, parámetros tipados de entrada (`STRING`, `FILE`, `PATH`, `DIRECTORY`, `INTEGER`, `BOOLEAN`), operaciones de control de acceso (`EXTENDS_PARENT`, `ADD_ALLOWED_PATH`, `ADD_NONWRITABLE_PATH`), lista blanca restringida de herramientas, identificador de modelo asignado y plantillas de prompts de sistema e inicialización.
* Aislamiento de ejecución: Al instanciar un subagente (`SubagentImpl`), se crea una carpeta de trabajo independiente bajo `var/tmp/subagent_<name>_<uuid>`. El subagente cuenta con una jerarquía `SubagentPaths` propia, bases de datos H2 independientes para memoria episódica y servicios, y un objeto `SubagentConsole` que redirige toda la salida a un archivo de log físico en disco, operando de manera desatendida (`humanConfirmationRequired = false`).
* Ejecución en dos fases: Admite un flujo secuencial definido por `prompt_ini` (fase de exploración e ingesta de datos) y opcionalmente `prompt_fin` (fase de síntesis y entrega de resultados).
* Modalidades de ejecución:
  * Síncrona (`run`): Ejecuta el procedimiento en el hilo actual y retorna la respuesta textual final.
  * Asíncrona (`launch`): Instancia un hilo de plataforma independiente (`Thread.ofPlatform()`), registra el subagente en `AgentManager`, ejecuta las fases y, al finalizar o fallar, inyecta automáticamente una notificación en el canal del sensor `SYSTEMNOTIFICATION` del subcanal originario del agente padre.
* Limpieza: Al finalizar, el método `close()` detiene los servicios internos, cierra las conexiones relacionales H2 y elimina completamente el directorio temporal del espacio de trabajo.

## 6. Catálogo exhaustivo de herramientas del agente

A continuación se detalla la totalidad de las clases del código fuente que implementan el contrato `AgentTool`, agrupadas por bloques funcionales.

### 6.1. Herramientas de memoria e historial conversacional

* `LookupTurnTool` (Nombre técnico: `fetch_citation`): Recupera el contenido fáctico e íntegro de turnos históricos registrados en la base de datos de memoria episódica a partir de su identificador numérico o referencia `{cite:ID}`. Acepta el parámetro `code` y un entero opcional `context_window` (máximo 5) para recuperar turnos adyacentes cronológicos anteriores y posteriores. Tipo: `TYPE_MEMORY`.
* `SearchFullHistoryTool` (Nombre técnico: `search_full_history`): Ejecuta búsquedas semánticas sobre la totalidad de la memoria episódica mediante similitud coseno sobre los embeddings almacenados en BLOBs relacionales. Parámetros: `query`, `limit` (por defecto 10, máximo 50), `minsimilarity` (umbral mínimo) y `type` (filtro opcional por categoría de anotación). Implementa `trimResult` para compactar sus salidas en la memoria proyectada transformando textos largos en listas de citas `{cite:ID}`. Tipo: `TYPE_MEMORY`.
* `AnnotateObservationTool` (Nombre técnico: `annotate_observation`): Permite al agente registrar observaciones estructuradas, acuerdos normativos o conclusiones derivadas en la memoria episódica, evitando que se pierdan tras la poda de datos crudos. Parámetros: `source` (origen del dato), `note` (hecho o síntesis), `resource_id` (identificador del recurso paginado para vincular la nota y silenciar avisos de riesgo) y `category` (`DOCUMENT_SUMMARY`, `PROJECT_MAP`, `INVARIANT`, `DESIGN_DECISION`, `TASK_SCRATCHPAD`). Tipo: `TYPE_ANNOTATION`.

### 6.2. Herramientas del sistema de archivos e inspección de código

* `FileFindTool` (Nombre técnico: `file_find`): Realiza búsquedas de archivos y directorios coincidentes con un patrón glob dentro de una ruta autorizada. Salida estructurada delimitada por punto y coma (`lastModified;type;size;mime;absolutePath`). Integra [herramientas base y paginación](https://jjdelcerro.github.io/noema/docs/04-subsistemas-de-ejecucion-y-capacidades/01-herramientas-base-y-paginacion.html) mediante volcado temporal y pistas de avance `HINT`. Parámetros: `path` y `pattern`.
* `FileReadTool` (Nombre técnico: `file_read`): Lee el contenido textual de archivos del espacio de trabajo con delimitación de rango mediante `offset` y `limit` de líneas. Incorpora detección de binarios mediante Tika para impedir la lectura directa de formatos no textuales. Retorna bloques paginados con cabeceras de metadatos `LINE_RANGE` y `TOTAL_LINES`. Parámetros: `path`, `offset` y `limit`.
* `FileGrepTool` (Nombre técnico: `file_grep`): Ejecuta búsquedas de patrones textuales o expresiones regulares línea a línea sobre un fichero o estructura de carpetas. Parámetros: `path`, `query`, `filePattern` (glob de filtrado) y `mode` (`regexp` o `plaintext`). Genera salidas paginadas con formato `absolutePath;lineNumber;lineContent`.
* `FileFuzzyGrepTool` (Nombre técnico: `file_fuzzygrep`): Realiza búsquedas semánticas sobre archivos del sistema utilizando embeddings locales y una ventana deslizante de líneas en memoria. Parámetros: `path`, `query`, `filePattern`, `limit` y `minSimilarity`.
* `FileWriteTool` (Nombre técnico: `file_write`): Escribe o sobrescribe un archivo con el texto suministrado, creando automáticamente los directorios padre necesarios. Ejecuta un check-in preventivo en JavaRCS si el archivo ya existía y el backup está habilitado. Parámetros: `path` y `content`. Modo: `MODE_WRITE`.
* `FilePatchTool` (Nombre técnico: `file_patch`): Aplica parches estructurados en formato Unified Diff (`@@ ... @@`) sobre archivos de texto utilizando Java-Diff-Utils. Registra versión previa en JavaRCS antes de aplicar los cambios. Parámetros: `path` y `patch`. Modo: `MODE_WRITE`.
* `FileSearchAndReplaceTool` (Nombre técnico: `file_search_and_replace`): Sustituye bloques de texto mediante una estrategia de coincidencia en tres niveles: coincidencia exacta, coincidencia tolerante a sangría y espacios, y diagnóstico difuso con sugerencia de líneas similares si no se localiza el bloque. Realiza copia de seguridad previa en JavaRCS y genera vistas previas numeradas del cambio. Parámetros: `path`, `oldText` y `newText`. Modo: `MODE_WRITE`.
* `FileMkdirTool` (Nombre técnico: `file_mkdir`): Crea directorios y rutas intermedias en el espacio de trabajo respetando el sandbox. Parámetros: `path`. Modo: `MODE_WRITE`.
* `FileExtractTextTool` (Nombre técnico: `file_extract_text`): Extrae texto limpio desde documentos binarios formateados (PDF, DOCX, ODT) utilizando Apache Tika, almacenando el resultado procesado en `var/cache` bajo un hash SHA-256 para acelerar lecturas sucesivas. Parámetros: `path`.
* `FileViewImageTool` (Nombre técnico: `file_view_image`): Permite cargar imágenes locales (PNG, JPEG, WebP, GIF de hasta 5 MB) en la conversación. A diferencia de las herramientas textuales, construye un `ToolExecutionResultMessage` multimodal que incorpora un `ImageContent` de LangChain4j para que los modelos con capacidades de visión procesen la imagen directamente. Parámetros: `path`.
* `FileHistoryTool` (Nombre técnico: `file_history`): Consulta el historial de versiones registrado en JavaRCS para un archivo (`rlog`), devolviendo autores, marcas de tiempo y mensajes de commit. Parámetros: `path`.
* `FileRecoveryTool` (Nombre técnico: `file_recovery`): Recupera y sobrescribe el archivo de trabajo con una revisión histórica específica desde JavaRCS (`co`). Parámetros: `path` y `revision`. Modo: `MODE_WRITE`.
* `FileReadSelectorsTool` (Nombre técnico: `file_read_selectors`): Permite empaquetar y leer de una vez múltiples archivos a partir de una lista de rutas o selectores glob, generando un archivo bundle temporal cacheado con control de tiempo de vida (TTL). Parámetros: `selectors` (lista de rutas o patrones).
* `ReadPaginatedResourceTool` (Nombre técnico: `read_paginated_resource`): Herramienta general de lectura de recursos paginados generados por herramientas del sistema (prefijos `tmp://`, `cache://` y `user://`). Gestiona desplazamientos por línea (`offset`) y columna (`column`) para reanudar lecturas truncadas por presupuestos de tamaño o número de líneas. Parámetros: `resource_id`, `offset`, `column` y `limit`.

### 6.3. Herramientas de ejecución y scripting

* `ShellExecuteTool` (Nombre técnico: `shell_execute`): Ejecuta comandos no interactivos en Bash redirigiendo la salida a ficheros temporales con extensión `.out`. Si Firejail está habilitado, encapsula el comando en un sandbox de sistema operativo con directorio `home` aislado. Implementa consultas periódicas de confirmación si el proceso supera escalones de 30 segundos. Parámetros: `command`. Modo: `MODE_EXECUTION`.
* `ScriptExecuteTool` (Nombre técnico: `execute_script`): Evalúa scripts Groovy embebidos bajo el entorno confinado `ScriptEngineImpl`. Admite código en línea (`script`) o rutas a ficheros externos (`scriptFile`), e inyección de variables tipadas (`variables`). Suministra acceso a la API del agente a través del enlace de contexto `agent`. Modo: `MODE_SCRIPTING`.

### 6.4. Herramientas de arquitectura y proyectos

* `LoadProjectMapTool` (Nombre técnico: `load_project_map`): Carga el contenido completo de un documento de arquitectura `project-map.md` ubicado junto a un `pom.xml`, fijándolo de forma permanente en la memoria proyectada del subcanal mediante `PinnedTurnsOperationImpl` para que permanezca accesible sin requerir lecturas repetidas. Parámetros: `path`.
* `UnloadProjectMapTool` (Nombre técnico: `unload_project_map`): Localiza y desancla el mapa de proyecto fijado en la memoria proyectada, liberando espacio en la ventana de contexto cuando concluyen las tareas sobre el repositorio.

### 6.5. Herramientas de identidad y entorno

* `ConsultEnvironTool` (Nombre técnico: `consult_environ`): Carga bajo demanda el documento íntegro de un módulo de conocimiento del entorno o biografía del usuario (`var/identity/environ/<module_name>.md`) referenciado previamente en los resúmenes ligeros `.ref.md`. Parámetros: `module_name`.

### 6.6. Herramientas de información web y entorno físico

* `TimeTool` (Nombre técnico: `get_current_time`): Proporciona la fecha, hora y zona horaria actual del sistema en formato ISO-8601 y texto legible. Parámetros: `timezone` (opcional).
* `LocationTool` (Nombre técnico: `get_current_location`): Determina la ubicación geográfica aproximada del sistema consultando la IP pública contra el servicio `ip-api.com`.
* `WeatherTool` (Nombre técnico: `get_weather`): Consulta el clima actual y la previsión meteorológica conectando con la API pública de Open-Meteo, incluyendo geocodificación previa de ciudades a coordenadas. Parámetros: `location`, `lat`, `lon`.
* `WebGetTikaTool` (Nombre técnico: `web_get_content`): Descarga el contenido de una URL externa mediante `HttpClient` y extrae su texto limpio y metadatos utilizando Apache Tika, almacenando la salida en un recurso paginado. Parámetros: `url`.
* `WebGetTool` (Nombre técnico: `web_get_content`): Variante simplificada basada en expresiones regulares para extracción de texto de páginas web con truncado a 10.000 caracteres. Parámetros: `url`.
* `TavilyWebSearchTool` (Nombre técnico: `web_search`): Realiza búsquedas web avanzadas a través de la API externa de Tavily. Parámetros: `query`.
* `BraveWebSearchTool` (Nombre técnico: `web_search`): Realiza búsquedas en internet consumiendo la API de Brave Search. Parámetros: `query`.

### 6.7. Herramientas sensoriales y de planificación

* `PollEventTool` (Nombre técnico: `poll_event`): Utilizada como ancla de protocolo para la entrega de estímulos sensoriales asíncronos en el flujo conversacional.
* `SensorStartTool` (Nombre técnico: `sensor_start`): Reactiva la recepción de eventos en los canales sensoriales indicados. Parámetros: `channels` (lista de cadenas).
* `SensorStopTool` (Nombre técnico: `sensor_stop`): Silencia temporalmente canales sensoriales específicos para evitar interrupciones durante tareas de alta concentración. Parámetros: `channels` (lista de cadenas).
* `SensorStatusTool` (Nombre técnico: `sensor_status`): Genera un informe detallado con el inventario de sensores registrados, su estado de silencio y estadísticas de actividad.
* `ScheduleAlarmTool` (Nombre técnico: `schedule_alarm`): Registra una alarma en el servicio de planificación mediante el análisis de fechas en lenguaje natural en inglés procesadas con Natty. Parámetros: `when` y `reason`. Modo: `MODE_WRITE`.

### 6.8. Herramientas de subagentes

* `ListSubagentsTool` (Nombre técnico: `list_subagents`): Inspecciona el directorio `var/subagents/` y devuelve el catálogo de recetas de trabajadores disponibles junto con su descripción y parámetros requeridos.
* `LaunchSubagentTool` (Nombre técnico: `launch_subagent`): Instancia y lanza un subagente en segundo plano dentro de su propio entorno aislado a partir de una receta XML. Parámetros: `subagent_name` y `params` (mapa de variables clave-valor en formato JSON). Modo: `MODE_WRITE`.

### 6.9. Herramientas de habilidades (skills)

* `ListSkillsTool` (Nombre técnico: `list_skills`): Devuelve la lista de habilidades procedimentales descubiertas en el sistema (`.claude/skills` y `var/skills`) con su nombre, versión y descripción.
* `ActivateSkillTool` (Nombre técnico: `activate_skill`): Lee las instrucciones de la habilidad seleccionada y las fija en la memoria proyectada mediante anclaje de turnos persistentes. Parámetros: `name`.
* `DeactivateSkillTool` (Nombre técnico: `deactivate_skill`): Retira de la memoria proyectada las directivas asociadas a la habilidad concluida. Parámetros: `name`.
* `ReadSkillResourceTool` (Nombre técnico: `read_skill_resource`): Lee documentos y plantillas complementarias empaquetadas dentro de la carpeta de una habilidad. Parámetros: `skill_name` y `path`.
* `RunSkillScriptTool` (Nombre técnico: `run_skill_script`): Ejecuta scripts auxiliares ubicados en la carpeta `scripts/` de una habilidad específica. Parámetros: `name`, `script` y `args`.

### 6.10. Herramientas de comunicación externa

* `EmailListTool` (Nombre técnico: `email_list_inbox`): Lista las cabeceras (remitente, asunto, UID) de los últimos correos recibidos en la bandeja de entrada vía IMAP.
* `EmailReadTool` (Nombre técnico: `email_read`): Recupera el contenido completo y limpio de un correo electrónico específico mediante su identificador UID procesándolo a través de Apache Tika. Parámetros: `uid`.
* `EmailSendTool` (Nombre técnico: `email_send`): Redacta y envía un correo electrónico a través de SMTP autenticado. Parámetros: `to`, `subject` y `body`. Modo: `MODE_WRITE`.
* `TelegramTool` (Nombre técnico: `telegram_send`): Envía mensajes de texto proactivos hacia el usuario autenticado a través del bot de Telegram configurado. Parámetros: `message`. Modo: `MODE_WRITE`.

### 6.11. Herramientas de interacción con el editor web

* `EditorOpenFileTool` (Nombre técnico: `editor_open_file`): Emite una instrucción vía Server-Sent Events (`editor_open`) para que la interfaz web abra un archivo en una pestaña del editor Ace y posicione el cursor en una línea determinada. Parámetros: `path` y `line`.
* `EditorGetCurrentSelectionTool` (Nombre técnico: `editor_get_current_selection`): Devuelve el texto actualmente seleccionado por el usuario en el editor web, incluyendo la ruta del fichero y los números de línea de inicio y fin.
* `EditorInsertOrReplaceTool` (Nombre técnico: `editor_insert_or_replace`): Propone una modificación en el buffer del editor web del usuario emitiendo un evento SSE `editor_apply` para insertar o sustituir un rango de líneas sin tocar el archivo en disco. Parámetros: `line_start`, `line_end` y `text`.
* `EditorAddAnnotationTool` (Nombre técnico: `editor_add_annotation`): Envía un evento SSE (`editor_add_annotation`) para renderizar marcas visuales y avisos explicativos sobre líneas específicas en el margen (gutter) del editor Ace en el navegador. Parámetros: `line`, `line_end`, `text`, `type` (`info`, `warning`, `error`) y `scroll`.
* `EditorClearAnnotationsTool` (Nombre técnico: `editor_clear_annotations`): Ordena a la interfaz web eliminar las marcas visuales previamente dibujadas por el agente en el editor.
* `UIGetStateTool` (Nombre técnico: `ui_get_state`): Consulta el estado completo reportado por el cliente web: ficheros abiertos en pestañas, fichero activo, posición del cursor, selección y nota activa en el panel de notas.

### 6.12. Herramientas dinámicas MCP

* `McpToolWrapper`: Clase adaptadora que implementa `AgentTool` envolviendo dinámicamente cualquier herramienta expuesta por servidores externos bajo el Model Context Protocol. Adapta las especificaciones de parámetros JSON Schema al constructor de especificaciones del agente, canaliza las llamadas a través del cliente `McpClient` de LangChain4j y propaga los modos de seguridad y prefijos configurados.

## 7. Construcción y despliegue

El empaquetado y arranque del sistema se gestiona mediante Apache Maven:

* Configuración de compilación: El fichero `pom.xml` define como requisito Java 25 (`<maven.compiler.source>25</maven.compiler.source>`), con filtrado de recursos para inyectar la versión del proyecto (`0.8.0`) en `version.properties`.
* Generación del artefacto distribuible: Se emplea el plugin `maven-shade-plugin` (versión 3.2.4) para compilar un único archivo JAR ejecutable autocontenido (*fat-jar* / *uber-jar*). La configuración incluye el transformador `ServicesResourceTransformer` (imprescindible para fusionar los ficheros de configuración SPI de dependencias en `META-INF/services`) y `ManifestResourceTransformer` apuntando a la clase principal `io.github.jjdelcerro.noema.main.Main`. Se eliminan las firmas criptográficas de dependencias externas (`*.SF`, `*.DSA`, `*.RSA`) para evitar conflictos de seguridad al ejecutar el JAR empaquetado.
* Preparación de modelos locales: El ciclo de compilación integra `exec-maven-plugin` en la fase `generate-resources`, ejecutando el script `download.sh` que descarga y verifica de forma atómica en `src/main/resources/.../var/models/` los archivos ONNX y tokenizadores necesarios para `paraphrase-multilingual-MiniLM-L12-v2` y `Qwen3-0.6B`.
* Ejecución y flags de la JVM: Para la ejecución del JAR compilado se suministra el script `noema`, el cual configura las opciones de depuración remota JDWP (puerto 8765) y los flags obligatorios de la máquina virtual:
  * `--add-modules jdk.incubator.vector`: Habilita la API de vectores acelerada por hardware utilizada en los cálculos matriciales.
  * `--enable-native-access=ALL-UNNAMED`: Permite la vinculación con bibliotecas C/C++ nativas de inferencia ONNX y terminales JLine.
  * `--add-exports jdk.compiler/com.sun.tools.javac.*=ALL-UNNAMED`: Expone las estructuras internas del compilador Java utilizadas por las herramientas de formateo y análisis sintáctico.
* Modos de ejecución disponibles desde el punto de entrada `Main`:
  * `-g`, `--gui`, `--swing`: Arranca la interfaz gráfica de escritorio Swing.
  * `-t`, `--tui`: Arranca la interfaz basada en terminal interactivo Lanterna.
  * `-c`, `--console`: Arranca la consola REPL pura sobre JLine.
  * `-w`, `-s`, `--web`, `--serve`: Arranca en modo servidor desatendido mediante Javalin, exponiendo la [interfaz web](https://jjdelcerro.github.io/noema/docs/05-capa-de-presentacion-e-interfaces/03-web.html) y la API REST/SSE en el puerto configurado (por defecto 8080), permitiendo especificar un espacio de trabajo con `-w <ruta>`.

## 8. Otros detalles relevantes del diseño

* Desacoplamiento de la presentación ([AgentConsole](https://jjdelcerro.github.io/noema/docs/05-capa-de-presentacion-e-interfaces/00-contrato-agentconsole-y-comunicacion.html)): La comunicación entre el núcleo del agente y las distintas interfaces de usuario no se realiza mediante llamadas directas a ventanas o widgets, sino a través de la interfaz abstracta `AgentConsole`. Esta interfaz unifica la confirmación interactiva de acciones (`confirm`), la emisión de logs de sistema, errores, razonamientos en tiempo real y respuestas finales. Cada frontal (`AgentSwingConsoleControllerUsingMultipleJTextPane`, `AgentLanternaConsoleImpl`, `AgentConsoleImpl`, `SseAgentConsole`) implementa este contrato adaptándolo a sus mecanismos visuales específicos.
* Arquitectura del frontend web: La [interfaz web](https://jjdelcerro.github.io/noema/docs/05-capa-de-presentacion-e-interfaces/03-web.html) se estructura sobre un diseño modular sin dependencias de frameworks externos:
  * `index.html` actúa como un contenedor estructural de primer nivel (huecos limpios para sidebar, header, workspace y chat).
  * Cada subsistema funcional se implementa como un módulo ES independiente que genera su marcado bajo demanda (`chat-markup.js`, `fsexplorer-markup.js`, `notes-markup.js`, `config-markup.js`).
  * Los addons del espacio de trabajo (`fsexplorer` y `notes`) implementan un contrato común de ciclo de vida (`mount`, `unmount`, `activate`, `deactivate`, `getState`, `setState`, `isDirty`, `confirmLeave`) coordinado por un gestor genérico (`workspace.js`).
  * El estado local se centraliza en `storage.js` bajo una única clave de `localStorage` (`noema.ui.state`) con persistencia mediante debounce, separando las preferencias de interfaz del estado contextual que se remite al servidor.
* Inspección y consola H2: Durante el arranque, el procedimiento `BootUtils` despliega un servidor web embebido de H2 en el puerto configurado (`debug/h2_webport`, por defecto 8082). Esto permite que el usuario acceda desde un navegador a una consola interactiva SQL para auditar en tiempo real las tablas de turnos, eventos y memorias consolidadas.

## 9. Conclusión

Noema presenta una arquitectura deliberadamente compacta, concebida para operar como un entorno de diálogo técnico prolongado sin requerir infraestructura externa de servidores, bases de datos vectoriales dedicadas ni servicios en la nube más allá de las APIs de modelos de lenguaje.

La organización de su memoria en cuatro estratos funcionales (archivo episódico inmutable, sesión de trabajo reciente, puntos de consolidación narrativa con citas deterministas y proyección atencional por zonas) resuelve de forma estructurada la retención de conocimiento a lo largo del tiempo. Asimismo, la presencia de salvaguardas de seguridad (resolución canónica de rutas, verificación en cascada, confirmación de operaciones e integración nativa de control de versiones con JavaRCS) proporciona un marco controlado para la interacción del agente con el entorno local.

---

*Documento generado por Gemini (IA) a partir del análisis estático del código fuente, versión 0.8.0, 29 de octubre de 2026.*