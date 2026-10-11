# Análisis Arquitectónico y Técnico: Proyecto Noema

## Visión General

Noema es un proyecto personal y experimental de ingeniería de software concebido para operar como un agente conversacional y analítico de propósito general. A diferencia de los agentes diseñados exclusivamente para el desarrollo de software o la resolución de tareas efímeras, Noema está construido para actuar como un compañero de investigación persistente. Su diseño permite sostener reflexiones, debates y análisis técnicos prolongados a lo largo del tiempo.

El pilar fundamental del proyecto es la eliminación del concepto de "sesiones". El sistema opera bajo un paradigma de continuidad histórica absoluta, reteniendo toda la interacción a largo plazo mediante una gestión de retención histórica apoyada en bases de datos locales. Fiel a una filosofía de cero fricción y soberanía tecnológica, el proyecto no requiere infraestructura externa (ni bases de datos vectoriales en la nube, ni brokers de mensajería externos). Todo el estado, la memoria semántica, el control de versiones y el motor de ejecución se empaquetan y resuelven localmente en la máquina del usuario, utilizando librerías nativas o implementaciones propias.

## Stack Tecnológico

El ecosistema tecnológico ha sido seleccionado para priorizar la ejecución local, la autonomía y la mínima dependencia de servicios gestionados:

*   **Plataforma Base:** Java (preparado para JDK 25), utilizando características avanzadas como `--add-modules jdk.incubator.vector` y `--enable-native-access=ALL-UNNAMED` para acelerar el procesamiento matemático local.
*   **Orquestación de IA:** LangChain4j como abstracción principal para la comunicación con proveedores de modelos de lenguaje (OpenAI, DeepSeek, Groq, OpenRouter).
*   **Inferencia y Embeddings Locales:** ONNX Runtime (`onnxruntime-genai`) y Jlama para la ejecución de Modelos de Lenguaje Pequeños (SLM) y el cálculo de vectores semánticos en el mismo proceso de la JVM.
*   **Persistencia:** Base de datos relacional embebida H2 para el almacenamiento transaccional de eventos, memoria a largo plazo y persistencia vectorial.
*   **Gestión de Datos y Serialización:** Gson para manipulación JSON y Apache Tika para la extracción robusta de texto desde binarios (PDF, DOCX, HTML).
*   **Motor de Ejecución Dinámica:** Apache Groovy, fuertemente confinado para la ejecución de scripts en tiempo de ejecución.
*   **Control de Versiones Local:** Implementación nativa [JavaRCS](https://github.com/jjdelcerro/io.github.jjdelcerro.javarcs) y `java-diff-utils` para garantizar copias de seguridad automáticas previas a cualquier mutación de archivos.
*   **Capa de Presentación:** Múltiples interfaces que comparten un mismo contrato:
    *   [Interface swing (GUI)](https://jjdelcerro.github.io/noema/docs/05-capa-de-presentacion-e-interfaces/01-swing.html) (FlatLaf, MigLayout, RSyntaxTextArea).
    *   [Interface Lanterna (TUI)](https://jjdelcerro.github.io/noema/docs/05-capa-de-presentacion-e-interfaces/02-tui.html) para terminales de texto avanzados.
    *   [Interface web](https://jjdelcerro.github.io/noema/docs/05-capa-de-presentacion-e-interfaces/03-web.html) soportada por Javalin con Server-Sent Events (SSE).
    *   Consola interactiva clásica mediante JLine.

## Estructura de Paquetes, Interfaces e Implementación

La arquitectura sigue el principio de Inversión de Dependencias, separando estrictamente los contratos públicos de sus implementaciones concretas:

*   `io.github.jjdelcerro.noema.lib.api`: Contiene las abstracciones centrales (`Agent`, `AgentManager`, `AgentTool`, `AgentService`). Define las interfaces de configuración, memorias y factorías, asegurando que los componentes no se acoplen a implementaciones específicas.
*   `io.github.jjdelcerro.noema.lib.api.spi`: Define clases base abstractas (como `AbstractAgentService` o `AbstractAgentAction`) para facilitar la creación de nuevos módulos.
*   `io.github.jjdelcerro.noema.lib.impl`: Aloja el núcleo operativo (`AgentImpl`, `AgentManagerImpl`).
    *   `impl.memory`: Resoluciones concretas de la estratificación del historial y el pipeline de proyección.
    *   `impl.services`: Implementaciones de la lógica de negocio divididas por dominio (razonamiento, consolidación, sensores, embeddings, scripting, etc.).
    *   `impl.tools`: Clases base abstractas para herramientas, destacando el soporte integrado para paginación.
*   `io.github.jjdelcerro.noema.ui`: Contratos de la capa de presentación (`AgentConsole`, `AgentUIManager`).
*   `io.github.jjdelcerro.noema.main`: Puntos de entrada de la aplicación y utilidades de inicialización (`BootUtils`).

## Arquitectura y Diseño

### 1. El Kernel (o Core)

El núcleo del sistema está diseñado como un orquestador agnóstico que inicializa, localiza y conecta los diferentes subsistemas.

*   **`Agent` y `AgentManager`:** La interfaz `Agent` es el contrato principal que expone los recursos del sistema al resto de componentes. El `AgentManager` actúa como un patrón *Service Locator* y *Factory*, administrando el registro de servicios, herramientas y operaciones dinámicas sin acoplamiento duro.
*   **Ciclo de Vida:** Gestionado principalmente en `BootUtils` y `AgentImpl`. El proceso incluye la resolución canónica de rutas, la instanciación de las conexiones a base de datos, el registro de factorías y la inicialización de los servicios. Al finalizar, un *ShutdownHook* garantiza el cierre ordenado de las bases de datos y la persistencia de estados en memoria.
*   **Infraestructura de Datos:** Mediante la abstracción `SQLProvider`, el agente gestiona dos conexiones separadas a H2: una para la memoria a largo plazo y otra para la persistencia de servicios (como el programador de tareas). Se emplean mecanismos como la clase `Counter` para garantizar la asignación atómica de identificadores numéricos en un entorno embebido.
*   **Topología de Archivos:** Todo el estado del sistema se localiza utilizando [AgentPaths](https://jjdelcerro.github.io/noema/docs/01-fundamentos-y-ciclo-de-vida/02-agent-paths.html). Por defecto, la información reside en un directorio `.noema-agent/` que contiene:
    *   `var/config/`: Ajustes JSON y plantillas de instrucciones (prompts).
    *   `var/lib/`: Ficheros binarios de H2 y volcados de texto del historial consolidado.
    *   `var/cache/` y `var/tmp/`: Almacenamiento efímero para extracciones y volcados de depuración.
    *   `var/identity/`: Ficheros de texto plano que definen el comportamiento y el contexto del agente.
    *   `home/`: Entorno asilado para la ejecución opcional de comandos.

### 2. Capacidades Horizontales (Cross-cutting Concerns)

*   **Seguridad y Control de Acceso:** El componente [AgentAccessControl](https://jjdelcerro.github.io/noema/docs/01-fundamentos-y-ciclo-de-vida/04-seguridad-y-control-de-acceso.html) es la barrera defensiva del sistema. Intercepta toda petición de acceso a disco, resolviendo la ruta de manera canónica para evitar ataques de salto de directorio (*path traversal*). Administra listas de rutas permitidas, denegadas y de solo lectura. Además, intercepta la ejecución de herramientas para exigir la confirmación del usuario antes de operaciones destructivas o de comunicación con el exterior.
*   **Gestión de Rutas y Sandbox:** Apoyado en `AgentPaths`, asegura que las operaciones por defecto se mantengan confinadas al directorio de trabajo del proyecto, previniendo alteraciones accidentales del sistema anfitrión.
*   **Sistema de Configuración Jerárquica:** A través de [AgentSettings](https://jjdelcerro.github.io/noema/docs/01-fundamentos-y-ciclo-de-vida/03-agent-settings.html), el sistema expone un árbol de preferencias persistido en JSON. Incorpora la clase `ExpressionEvaluator` para permitir que ciertas capacidades o herramientas se habiliten o deshabiliten dinámicamente evaluando expresiones lógicas y matemáticas en tiempo real.

### 3. Servicios Cognitivos

Estos servicios conforman el motor lógico y el sistema de retención de información del agente. Su diseño iterativo permite que el agente funcione indefinidamente sin perder el hilo de la investigación.

*   **[ReasoningService](https://jjdelcerro.github.io/noema/docs/03-catalogo-de-servicios/01-reasoning.html):** Es el orquestador del bucle de eventos lógicos. Consume estímulos entrantes, ensambla el contexto, invoca a los modelos de lenguaje, gestiona la intercepción y ejecución segura de herramientas, e intercala los resultados en la memoria. Administra el concepto de `subchannels` para mantener hilos de conversación completamente paralelos e independientes, simulando una consciencia multiterminal.
*   **[MemoryConsolidationService](https://jjdelcerro.github.io/noema/docs/03-catalogo-de-servicios/02-memory-consolidation.html):** Actúa como un proceso de compactación asíncrono. Monitorea el tamaño de la conversación activa y, al alcanzar un umbral crítico, utiliza un modelo de lenguaje secundario para sintetizar los turnos recientes junto con el resumen anterior. Este servicio genera un relato continuo que mantiene referencias numéricas exactas a los eventos originales, previniendo la fatiga del contexto.

*Nota:* La persistencia física que da soporte a estos servicios está implementada en `EpisodicMemoryImpl`, la cual registra de forma inmutable cada turno y su correspondiente vector semántico.

### 4. Servicios de Periferia

Módulos especializados que gestionan la interacción de Noema con el mundo exterior, el tiempo y la ejecución de tareas secundarias.

*   **[SensorsService](https://jjdelcerro.github.io/noema/docs/03-catalogo-de-servicios/03-sensors.html):** Funciona como el bus de eventos asíncronos del agente. Recibe estímulos externos (correos, temporizadores, mensajes) y los encola. Implementa estrategias de fusión inteligente según la naturaleza del sensor (agregando conteos para evitar spam, fusionando textos secuenciales, o descartando estados obsoletos) antes de entregarlos al motor de razonamiento.
*   **[SchedulerService](https://jjdelcerro.github.io/noema/docs/03-catalogo-de-servicios/04-scheduler.html):** Dota al agente de percepción del tiempo futuro. Permite programar alarmas o recordatorios que se persisten en base de datos. Cuando el temporizador expira, inyecta un evento en el bus de sensores.
*   **[Scripting](https://jjdelcerro.github.io/noema/docs/04-subsistemas-de-ejecucion-y-capacidades/04-scripting.html) (ScriptingService):** Provee un entorno de ejecución dinámico embebido basado en Groovy. Permite al agente ejecutar cálculos, iterar archivos grandes o aplicar filtros de forma local sin requerir llamadas costosas al LLM para tareas puramente algorítmicas (implementando un patrón de Recursive Language Models).
*   **Comunicaciones (EmailService / TelegramService):** Actúan de forma dual. Como efectores, exponen herramientas de envío de mensajes. Como sensores, mantienen escuchas activas (IMAP IDLE o Long Polling) para detectar interacciones externas, inyectando eventos ligeros en el sistema para que el agente decida si requiere descargar y leer el contenido completo.
*   **[EmbeddingsService](https://jjdelcerro.github.io/noema/docs/03-catalogo-de-servicios/05-embeddings.html):** Motor de cálculo vectorial ejecutado localmente mediante ONNX. Permite la vectorización de textos y la búsqueda semántica rápida (Top-K) sobre la memoria o los archivos locales, aplicando una estrategia de *MaxP* fragmentando documentos extensos en pequeños bloques para no diluir el significado.
*   **McpService:** Integración del Model Context Protocol, permitiendo conectar servidores externos para dotar al agente de herramientas descubiertas dinámicamente en tiempo de ejecución.
*   **SubagentsService:** Sistema de delegación de tareas pesadas en segundo plano mediante la orquestación de trabajadores aislados definidos en XML.
*   **SkillsService:** Motor de descubrimiento y carga de flujos procedimentales y directivas técnicas predefinidas.

## Herramientas del Agente

Las herramientas (clases que implementan `AgentTool` o la variante con soporte de fragmentación en [Herramientas base y paginación](https://jjdelcerro.github.io/noema/docs/04-subsistemas-de-ejecucion-y-capacidades/01-herramientas-base-y-paginacion.html)) son los actuadores del sistema. 

*   **Manipulación y Búsqueda en el Sistema de Archivos:**
    *   `file_find`: Búsqueda de rutas mediante patrones glob.
    *   `file_read`: Lectura de archivos de texto acotada por líneas.
    *   `file_read_selectors`: Combinación de múltiples archivos en un único volcado.
    *   `read_paginated_resource`: Herramienta universal para continuar la lectura secuencial de salidas largas.
    *   `file_grep`: Búsqueda mediante expresiones regulares.
    *   `file_fuzzygrep`: Búsqueda semántica en archivos basada en vectores ONNX locales.
    *   `file_write`: Sobrescritura de archivos.
    *   `file_mkdir`: Creación de directorios.
    *   `file_patch`: Aplicación de parches en formato Unified Diff.
    *   `file_search_and_replace`: Localización y reemplazo exacto o tolerante a indentación de bloques de texto.
    *   `file_extract_text`: Extracción de contenido estructurado desde formatos binarios complejos (PDF, DOCX) utilizando Apache Tika.
    *   `file_history` y `file_recovery`: Exploración de la bitácora y recuperación de versiones previas apoyadas en el motor RCS interno.
*   **Sistema, Memoria y Percepción Temporal:**
    *   `poll_event`: Herramienta técnica inyectada para consultar la cola de eventos sensoriales pendientes.
    *   `fetch_citation`: Recuperación exacta de un turno histórico de la base de datos mediante su ID único.
    *   `search_full_history`: Búsqueda semántica sobre la base de datos de eventos pasados.
    *   `annotate_observation`: Registro directo de reglas, hallazgos o conclusiones en la memoria a largo plazo.
    *   `schedule_alarm`: Programación de eventos futuros en el sistema.
    *   `get_current_time`: Consulta del reloj interno y zonas horarias.
    *   `consult_environ`: Inyección bajo demanda de conocimiento denso sobre el entorno del usuario.
    *   `sensor_stop`, `sensor_start`, `sensor_status`: Herramientas de modulación de atención para detener o reiniciar canales de ruido.
*   **Ejecución Computacional:**
    *   `execute_script`: Delegación de lógica procedimental o procesamiento masivo al motor Groovy seguro.
    *   `shell_execute`: Ejecución de comandos del sistema operativo (con soporte opcional de confinamiento vía Firejail).
*   **Interacción Web e Internet:**
    *   `web_search`: Búsqueda en la web (integraciones modulares para Tavily o Brave).
    *   `web_get_content`: Descarga de URLs y sanitización de HTML/Documentos para extraer texto limpio.
    *   `get_current_location` y `get_weather`: Consultas de entorno físico y meteorológico por IP.
*   **Comunicaciones Externas:**
    *   `email_list_inbox`, `email_read`, `email_send`: Interfaz completa para la gestión de correo como secretaria virtual.
    *   `telegram_send`: Envío de notificaciones salientes a un chat autorizado.
*   **Orquestación de [Subagentes](https://jjdelcerro.github.io/noema/docs/04-subsistemas-de-ejecucion-y-capacidades/02-subagentes.html) y Procedimientos:**
    *   `list_subagents` y `launch_subagent`: Descubrimiento y despacho asíncrono de trabajadores en segundo plano.
    *   `list_skills`, `activate_skill`, `deactivate_skill`, `read_skill_resource`, `run_skill_script`: Gestión del ciclo de vida de los protocolos técnicos de actuación.
*   **Herramientas de Desarrollo:**
    *   `load_project_map` y `unload_project_map`: Gestión del documento de arquitectura persistente en la memoria de trabajo.

## Mecanismos Principales (Análisis Detallado)

### 1. Gestión de Memoria Estratificada

El sistema implementa una [Visión general del modelo de memoria](https://jjdelcerro.github.io/noema/docs/02-el-sistema-de-memoria/010-vision-general-de-modelo-de-memoria.html) diseñada para sostener la coherencia lógica en investigaciones de larga duración. Se organiza en capas para evitar la sobresaturación del límite de tokens del LLM:

*   **[EpisodicMemory](https://jjdelcerro.github.io/noema/docs/02-el-sistema-de-memoria/020-memoria-episodica.html):** Almacenamiento inmutable en H2. Registra cada turno conversacional, la ejecución de cada herramienta y su correspondiente representación vectorial.
*   **[RecentMemory](https://jjdelcerro.github.io/noema/docs/02-el-sistema-de-memoria/040-memoria-reciente.html):** Buffer temporal en RAM (respaldado en disco) que retiene los intercambios más recientes. Cuando alcanza un umbral definido, los turnos se trasladan asíncronamente al servicio de consolidación.
*   **[ConsolidateMemory](https://jjdelcerro.github.io/noema/docs/02-el-sistema-de-memoria/030-memoria-consolidada.html):** Documento narrativo que sintetiza el pasado. En lugar de un mero resumen, genera una crónica que incluye etiquetas de cita (ej: `{cite:87}`) que permiten al agente realizar "flashbacks" deterministas consultando la base de datos real en caso de duda.
*   **[ProjectedMemory](https://jjdelcerro.github.io/noema/docs/02-el-sistema-de-memoria/050-memoria-proyectada.html):** Es el constructo final que se envía al LLM en cada interacción. Funciona como un *pipeline* transformacional dinámico y registrable que procesa la información antes de la inferencia, utilizando clases que implementan `ProjectedMemoryOperation`:
    *   *TrimmingOperation:* Recorta salidas voluminosas de herramientas antiguas, dejando una advertencia de que el dato fue podado por eficiencia.
    *   *PendingAnnotationOperation:* Si el agente consumió texto masivo sin tomar notas (`annotate_observation`), inyecta una advertencia efímera para forzar la retención de conclusiones.
    *   *TemporalPerceptionOperation:* Inyecta notificaciones sobre el paso del tiempo real en ausencias prolongadas.
    *   *PeripheralAwarenessOperation:* Mantiene informado al agente sobre la actividad ocurrida en otros subcanales concurrentes.
    *   *PinnedTurnsOperation:* Fija permanentemente en el contexto los resultados de herramientas críticas (como mapas de proyecto o *skills* activos) protegiéndolos de la compactación.

### 2. Gestión de la Identidad del Agente

La identidad técnica y operativa de Noema no está escrita rígidamente en el código fuente, sino que se compone ensamblando ficheros Markdown desde el directorio `var/identity/`. Se fragmenta en dos bloques:
*   **Core:** Determina la constitución fundamental (reglas de código, restricciones tecnológicas, paradigma filosófico) inyectada estáticamente en el prompt principal.
*   **Entorno (Environ):** Archivos `.ref.md` que funcionan como anclas ligeras en el prompt. Si el modelo detecta un tema mencionado en las referencias, utiliza la herramienta `consult_environ` para cargar el documento denso completo bajo demanda (Page-in).

### 3. Gestión de Habilidades ([skills](https://jjdelcerro.github.io/noema/docs/04-subsistemas-de-ejecucion-y-capacidades/03-skills.md))

Representan procedimientos técnicos empaquetados. Un skill se define en un archivo Markdown con metadatos. Al utilizar la herramienta `activate_skill`, el agente ancla las instrucciones del procedimiento en la memoria proyectada mediante una operación del pipeline (`PinnedTurnsOperation`). Esto obliga al agente a operar bajo restricciones procedimentales estrictas hasta que el protocolo concluye y ejecuta `deactivate_skill`.

### 4. Gestión de Eventos y Proactividad

Para romper la naturaleza puramente pasiva de los LLMs, el sistema implementa un enrutamiento asíncrono. Eventos provenientes de temporizadores o comunicaciones de red se almacenan en el bus del `SensorsService`. 
Cuando el orquestador principal se encuentra ocioso, inyecta un engaño protocolario: crea un turno artificial donde simula que el propio modelo decidió invocar una herramienta llamada `pool_event`, devolviendo el evento en formato JSON como resultado de esa herramienta. Esto permite notificar al modelo de sucesos externos respetando estrictamente la estructura conversacional esperada por las APIs.

### 5. Gestión de la Seguridad

Dado su carácter experimental y sus amplias capacidades, Noema aplica contención en múltiples frentes:
*   **Aislamiento de Ficheros:** El [AgentAccessControl](https://jjdelcerro.github.io/noema/docs/01-fundamentos-y-ciclo-de-vida/04-seguridad-y-control-de-acceso.html) normaliza todas las rutas a su forma absoluta y canónica, verificando la pertenencia a las listas blancas o listas de solo-lectura, impidiendo escapes del espacio de trabajo.
*   **Check-in Automático (RCS):** Antes de que las herramientas `file_write`, `file_patch` o `file_search_and_replace` modifiquen el disco, el agente invoca silenciosamente a JavaRCS para registrar una instantánea inmutable del estado anterior, permitiendo regresiones seguras.
*   **Autorización Expresa:** Cualquier herramienta clasificada como escritura, ejecución en shell o acceso web pausa el bucle de razonamiento y eleva una solicitud a la interfaz del usuario [AgentConsole y la comunicacion Core-UI](https://jjdelcerro.github.io/noema/docs/05-capa-de-presentacion-e-interfaces/00-contrato-agentconsole-y-comunicacion.md) para requerir confirmación explícita.
*   **Sandbox de Código:** El intérprete Groovy embebido cuenta con restricciones en el Árbol de Sintaxis Abstracta (`SecureASTCustomizer`) e interrupciones por tiempo para mitigar bucles infinitos o la instanciación de clases prohibidas.
*   **Aislamiento de Procesos:** Si el anfitrión Linux dispone de la utilidad `firejail`, las ejecuciones de shell se lanzan en un contenedor estricto con un directorio temporal simulado.

### 6. Flujos en el Reasoning Service

El corazón operativo reside en el método `processSingleEvent` del [ReasoningService](https://jjdelcerro.github.io/noema/docs/03-catalogo-de-servicios/01-reasoning.html). La secuencia es:
1.  Recepción de un estímulo entrante (del usuario o de un sensor).
2.  Ensamblado del contexto aplicando las operaciones del pipeline de la `ProjectedMemory`.
3.  Llamada bloqueante/streaming al proveedor del LLM.
4.  Si el modelo retorna texto directo, se envía al usuario, se persiste en la base de datos episódica y se evalúa el umbral de consolidación.
5.  Si el modelo solicita la ejecución de herramientas, el servicio las extrae, evalúa los permisos de seguridad, ejecuta el código subyacente y reinyecta los resultados (`ToolExecutionResultMessage`) repitiendo el proceso desde el paso 2 hasta que el modelo decide finalizar el turno.

### 7. Subagentes

El componente de [Subagentes](https://jjdelcerro.github.io/noema/docs/04-subsistemas-de-ejecucion-y-capacidades/02-subagentes.html) permite la ejecución asíncrona de tareas de fondo de largo aliento. Definidos declarativamente mediante recetas XML (que indican parámetros, herramientas permitidas y un prompt de dos fases), estos trabajadores se instancian en un hilo separado con su propia base de datos temporal y espacio de trabajo aislado. Operan de manera independiente y, al finalizar, notifican los resultados al canal de la conversación principal mediante el sensor de notificaciones del sistema, autodestruyendo su entorno al terminar.

## Construcción y Despliegue

La compilación se gobierna mediante Apache Maven. El proyecto está empaquetado como un Fat JAR utilizando el `maven-shade-plugin`. Es de vital importancia en este proceso el uso del `ServicesResourceTransformer` para combinar correctamente las firmas del Service Provider Interface (SPI) requeridas por módulos como Apache Tika y LangChain4j. 

El despliegue local es autosuficiente y carece de dependencias en la nube (más allá del acceso a las APIs de los modelos de lenguaje). El punto de entrada principal (`Main.java`) actúa como un enrutador que inicializa el sistema con la interfaz de usuario deseada, permitiendo invocar la variante gráfica (`--gui`), de terminal modal (`--tui`), clásica interactiva (`--console`), o desplegar el servidor REST/SSE sin cabeza (`--web`).

## Conclusión

El proyecto Noema constituye un ejercicio arquitectónico avanzado en la construcción de agentes de IA persistentes. Supera los enfoques convencionales centrados en ventanas de tokens cada vez más masivas mediante la implementación de una ingeniería de la atención rigurosa. 

Al adoptar un sistema estratificado y mecanismos proactivos (como forzar al agente a tomar notas, resumir asíncronamente y recuperar contexto mediante búsquedas exactas o semánticas limitadas), el sistema logra mitigar las alucinaciones y la fatiga del contexto en periodos de ejecución indefinidos. Todo ello integrado en un diseño de software sólido que blinda la seguridad mediante *sandboxing*, exige confirmación asíncrona y persigue una autonomía tecnológica basada en la delegación algorítmica local y el control estricto de las operaciones sobre el sistema anfitrión.

---

*Documento generado por Gemini (IA) a partir del análisis estático del código fuente, versión 0.8.0, 11 de octubre de 2026.*
