* Versión Analizada: 0.1.0
* Fecha de Análisis: 30 de Agosto de 2026
* Autor del Informe: Gemini (IA), basado en la inspección estática del código fuente.

## Visión General

Noema es un proyecto de agente autónomo diseñado como un compañero de investigación y reflexión a largo plazo. A diferencia de los agentes orientados a la resolución rápida de tareas de desarrollo de software, Noema está concebido para mantener una única conversación continua que evoluciona con el tiempo. 

Su filosofía arquitectónica se basa en la autonomía, la privacidad y la mínima fricción de infraestructura. No requiere despliegues complejos en la nube ni bases de datos externas; todo su estado, configuración y persistencia se gestionan localmente mediante archivos y una base de datos embebida. El sistema aborda el problema de la degradación del contexto en interacciones prolongadas mediante una sofisticada estratificación de la información, consolidando el pasado en narrativas ("El Viaje") y proyectando dinámicamente solo lo estrictamente necesario hacia el modelo de lenguaje (LLM).

## Stack Tecnológico

El proyecto está construido sobre un ecosistema robusto y autocontenido en el entorno Java:

*   **Lenguaje y Plataforma:** Java (diseñado para compilar en JDK 25), gestionado con Maven.
*   **Integración LLM:** LangChain4j, utilizado como abstracción principal para la comunicación con proveedores de modelos de lenguaje (OpenAI, DeepSeek, Groq, OpenRouter) y modelos locales (Jlama).
*   **Persistencia:** H2 Database Engine en modo embebido para el almacenamiento estructurado y vectorial.
*   **Procesamiento de Datos:** Apache Tika para la extracción de texto de documentos binarios (PDF, DOCX) y web; Gson para la serialización JSON.
*   **Motor de Scripting:** Apache Groovy, embebido y securizado para la ejecución de lógica programática dinámica.
*   **Control de Versiones Interno:** [JavaRCS](https://github.com/jjdelcerro/io.github.jjdelcerro.javarcs), una implementación pura en Java del sistema de control de revisiones, utilizada para respaldos automáticos.
*   **Interfaces de Usuario:**
    *   [Interface swing (GUI)](https://jjdelcerro.github.io/noema/docs/05-capa-de-presentacion-e-interfaces/01-swing.html): Construida con FlatLaf y MigLayout.
    *   [Interface Lanterna (TUI)](https://jjdelcerro.github.io/noema/docs/05-capa-de-presentacion-e-interfaces/02-tui.html): Para entornos de terminal ricos.
    *   Consola Clásica: Implementada con JLine3.
    *   [Interface web](https://jjdelcerro.github.io/noema/docs/05-capa-de-presentacion-e-interfaces/03-web.html): Servidor embebido basado en Javalin con frontend en HTML/JS/CSS puro.

## Estructura de Paquetes

El código fuente sigue una clara separación entre contratos, implementaciones y capas de presentación:

*   `io.github.jjdelcerro.noema.lib`: Contiene las interfaces públicas, contratos de servicios, modelos de datos y definiciones de la API del agente. Es el núcleo abstracto del sistema.
*   `io.github.jjdelcerro.noema.lib.impl`: Aloja las implementaciones concretas de los servicios, la persistencia, las herramientas y la lógica de negocio.
*   `io.github.jjdelcerro.noema.ui`: Define las abstracciones para la capa de presentación.
*   `io.github.jjdelcerro.noema.ui.*` (swing, lanterna, console): Implementaciones específicas de cada paradigma de interfaz de usuario.
*   `io.github.jjdelcerro.noema.main`: Clases de arranque (`Main`, `BootUtils`) que ensamblan las dependencias y lanzan el agente en el modo seleccionado.

## Arquitectura y Diseño

La arquitectura de Noema es modular y orientada a servicios, gobernada por un localizador central y un ciclo de vida bien definido.

### 1. El Kernel (o Core)

*   **Agent y AgentManager:** La interfaz `Agent` es la fachada principal que expone el estado, la memoria y los servicios. El `AgentManager` actúa como un patrón *Service Locator* y *Factory*, registrando y proveyendo las implementaciones concretas de los subsistemas.
*   **Arranque y ciclo de vida:** El proceso de inicio, gobernado por `BootUtils`, establece las conexiones a la base de datos H2, instancia el gestor de configuración, registra los servicios y arranca el bucle de eventos. El cierre ordenado se garantiza mediante *shutdown hooks* de la JVM.
*   **Infraestructura de Datos:** `SQLProvider` abstrae las consultas SQL, permitiendo adaptar la persistencia (actualmente fuertemente acoplada a H2 por el uso de arrays para vectores).
*   **Topología de Archivos:** Todo el estado del agente reside en un espacio de trabajo local (por defecto `.noema-agent`). Esta carpeta contiene subdirectorios estandarizados: `var/config` (ajustes y prompts), `var/lib` (bases de datos H2), `var/tmp` (archivos efímeros), `var/cache` y `home` (sandbox para ejecución de comandos).

### 2. Capacidades Horizontales (Cross-cutting Concerns)

*   **Seguridad y control de acceso:** El [AgentAccessControl](https://jjdelcerro.github.io/noema/docs/01-fundamentos-y-ciclo-de-vida/04-seguridad-y-control-de-acceso.html) es un componente crítico. Define un *sandbox* estricto, gestionando listas blancas y negras de rutas de lectura y escritura. Intercepta las llamadas a herramientas para validar permisos de red, ejecución de shell y escritura en disco. Además, obliga a la confirmación humana para acciones destructivas e inyecta respaldos automáticos (RCS) antes de modificar archivos.
*   **Jerarquía de archivos:** El componente [AgentPaths](https://jjdelcerro.github.io/noema/docs/01-fundamentos-y-ciclo-de-vida/02-agent-paths.html) resuelve las rutas relativas y absolutas, garantizando que el agente opere siempre dentro de los límites físicos de su espacio de trabajo o de las rutas explícitamente autorizadas.
*   **Configuración jerárquica:** El [AgentSettings](https://jjdelcerro.github.io/noema/docs/01-fundamentos-y-ciclo-de-vida/03-agent-settings.html) maneja un árbol de configuración en formato JSON. Soporta evaluación dinámica de expresiones (vía MVEL/Groovy) para habilitar o deshabilitar capacidades en caliente.

### 3. Servicios Cognitivos

Estos servicios conforman el "motor de pensamiento" del agente, gestionando cómo procesa la información y cómo la retiene a lo largo del tiempo.

*   **Visión general del modelo de memoria:** La persistencia cognitiva de Noema está estratificada para evitar el colapso del contexto. No se envía todo el historial al LLM; en su lugar, la información fluye a través de diferentes estados de compresión y relevancia.
*   **[ReasoningService](https://jjdelcerro.github.io/noema/docs/03-catalogo-de-servicios/01-reasoning.html):** Es el orquestador del pensamiento. Mantiene un bucle continuo que extrae eventos de los sensores, ensambla el contexto proyectado, invoca al LLM, ejecuta las herramientas solicitadas y registra los resultados. Maneja el concepto de `subchannels` para mantener conversaciones paralelas e independientes.
*   **[MemoryConsolidationService](https://jjdelcerro.github.io/noema/docs/03-catalogo-de-servicios/02-memory-consolidation.html):** Actúa en segundo plano. Cuando la memoria reciente alcanza un umbral, este servicio toma los turnos crudos y el resumen anterior, y utiliza un LLM para generar una nueva narrativa consolidada ("El Viaje"). Este proceso asegura que el agente mantenga una comprensión profunda de la historia sin arrastrar el peso de los tokens originales.

### 4. Servicios de Periferia

Gestionan la interacción del agente con el mundo exterior y la ejecución de tareas especializadas.

*   **[SensorsService](https://jjdelcerro.github.io/noema/docs/03-catalogo-de-servicios/03-sensors.html):** Un bus de eventos asíncrono. Los estímulos externos (mensajes, alarmas) se encolan aquí. El servicio clasifica los eventos por su naturaleza (discretos, fusionables, de estado o agregables) para optimizar cómo se presentan al motor de razonamiento.
*   **[SchedulerService](https://jjdelcerro.github.io/noema/docs/03-catalogo-de-servicios/04-scheduler.html):** Permite al agente programar alarmas y recordatorios futuros. Persiste las tareas en H2 y las inyecta en el bus de sensores cuando se cumple el plazo.
*   **[EmbeddingsService](https://jjdelcerro.github.io/noema/docs/03-catalogo-de-servicios/05-embeddings.html):** Proporciona vectorización local utilizando modelos ONNX (ej. AllMiniLmL6V2). Permite la búsqueda semántica rápida sobre el historial almacenado en H2 sin depender de APIs externas de embedding.
*   **Comunicaciones (Email / Telegram):** Servicios bidireccionales. Actúan como sensores (escuchando mediante IMAP IDLE o Long Polling) inyectando notificaciones, y como efectores (herramientas) permitiendo al agente enviar mensajes o leer correos completos bajo demanda.
*   **Model Context Protocol (MCP):** Un servicio que permite conectar a Noema con servidores MCP externos (vía stdio o HTTP/SSE), descubriendo y envolviendo sus herramientas dinámicamente para que el agente las utilice.

## Mecanismos Principales (Análisis Detallado)

### Gestión de Memoria

El sistema de memoria es la piedra angular de Noema, diseñado bajo el principio de la "espiral de contexto". Se divide en cuatro capas:

1.  **[EpisodicMemory](https://jjdelcerro.github.io/noema/docs/02-el-sistema-de-memoria/020-memoria-episodica.html):** La fuente de la verdad. Almacena en la base de datos H2 cada `Turn` (interacción, pensamiento, llamada a herramienta) de forma inmutable, junto con su vector de embedding para búsquedas futuras.
2.  **[RecentMemory](https://jjdelcerro.github.io/noema/docs/02-el-sistema-de-memoria/040-memoria-reciente.html):** Un buffer en RAM (respaldado en JSON) que contiene los mensajes de la conversación actual que aún no han sido consolidados.
3.  **[ConsolidateMemory](https://jjdelcerro.github.io/noema/docs/02-el-sistema-de-memoria/030-memoria-consolidada.html):** El resultado del `MemoryConsolidationService`. Es un documento Markdown que fusiona el resumen anterior con los turnos recientes, manteniendo referencias exactas (`{cite:ID}`) a la memoria episódica.
4.  **[ProjectedMemory](https://jjdelcerro.github.io/noema/docs/02-el-sistema-de-memoria/050-memoria-proyectada.html):** Es la vista dinámica y efímera que se envía al LLM en cada turno. Se construye ensamblando el prompt del sistema, la memoria consolidada y la memoria reciente. 

**El Pipeline de Operaciones de la Memoria Proyectada:**
Antes de enviar el contexto al LLM, la `ProjectedMemory` pasa por un pipeline de transformaciones registrables (`ProjectedMemoryOperationFactory`) que alteran dinámicamente el contexto:
*   *TrimmingOperation:* Recorta resultados masivos de herramientas (ej. lecturas de archivos gigantes) dejando solo una advertencia de que el contenido fue asimilado en turnos anteriores.
*   *PendingAnnotationOperation:* Detecta si el agente ha leído recursos grandes sin tomar notas (`annotate_observation`) y genera una advertencia efímera (notificación del sistema) para forzarlo a consolidar el conocimiento.
*   *TemporalPerceptionOperation:* Inyecta notificaciones sobre el paso del tiempo si ha habido una pausa larga en la conversación.
*   *PinnedTurnsOperation:* Fija llamadas a herramientas críticas (como la activación de un *skill*) en la parte superior del contexto para que sus reglas no se pierdan tras una consolidación.
*   *PeripheralAwarenessOperation:* Informa al agente sobre la actividad reciente en otros subcanales (terminales), dándole consciencia de sus conversaciones paralelas.

### Gestión de la Identidad del Agente

La identidad no está codificada en el código fuente, sino que se ensambla dinámicamente en el prompt del sistema a partir de archivos Markdown. Se divide en:
*   **Core (`var/identity/core`):** Constituye el ADN técnico y las reglas operativas inmutables. Los módulos se activan o desactivan desde la configuración.
*   **Entorno (`var/identity/environ`):** Archivos `.ref.md` que actúan como un índice ligero de conocimientos sobre el usuario o el proyecto. Si el agente detecta un tema relevante en este índice, utiliza la herramienta `consult_environ` para cargar el documento denso completo bajo demanda.

### Gestión de Eventos y Proactividad

El agente no es puramente reactivo. El `SensorsService` encola eventos del mundo exterior (mensajes de Telegram, correos, alarmas del planificador). El `ReasoningService` consume esta cola. Para mantener la ilusión de control y la coherencia del formato de chat del LLM, el orquestador inyecta estos eventos simulando que el modelo ejecutó una herramienta ficticia llamada `pool_event`. Esto evita inyecciones de prompt directas y permite al agente reaccionar orgánicamente a estímulos asíncronos.

### Flujos en el Reasoning Service

El bucle principal (`processSingleEvent`) sigue un flujo estricto:
1. Recibe un evento (del usuario o de un sensor).
2. Proyecta la memoria (aplica el pipeline de operaciones).
3. Invoca al LLM.
4. Si el LLM devuelve texto, responde al usuario, registra el turno en la `EpisodicMemory` y evalúa si es necesaria una consolidación.
5. Si el LLM solicita herramientas, las ejecuta (pasando por el `AgentAccessControl`), registra los resultados como nuevos turnos episódicos, y vuelve al paso 2 para que el LLM analice los resultados.

### [Subagentes](https://jjdelcerro.github.io/noema/docs/04-subsistemas-de-ejecucion-y-capacidades/02-subagentes.html)

Los subagentes son trabajadores desechables que se ejecutan de forma asíncrona en segundo plano. Se definen mediante recetas XML (`var/subagents/*.xml`) que especifican sus prompts, herramientas permitidas y parámetros. Cuando el agente principal lanza un subagente, este opera en un *workspace* temporal aislado. Al finalizar, el subagente inyecta un evento de notificación en el canal original con el resultado de su trabajo, permitiendo delegar tareas pesadas (como indexación masiva) sin bloquear la conversación principal.

### [Scripting](https://jjdelcerro.github.io/noema/docs/04-subsistemas-de-ejecucion-y-capacidades/04-scripting.html) y Habilidades ([skills](https://jjdelcerro.github.io/noema/docs/04-subsistemas-de-ejecucion-y-capacidades/03-skills.md))

Noema implementa el paradigma de *Recursive Language Models* (RLM) mediante la herramienta `execute_script`. El agente puede escribir y ejecutar código Groovy en un entorno embebido y altamente securizado (`SecureASTCustomizer`, `TimedInterrupt`). El script tiene acceso a un objeto `agent` (`ScriptContext`) que expone fachadas para iterar archivos sin agotar la memoria, hacer subconsultas al LLM, o registrar anotaciones.

Los **Skills** son paquetes procedimentales (`.claude/skills/`) que contienen directivas Markdown y scripts auxiliares. El agente puede "activar" un skill, lo que ancla sus reglas en la memoria proyectada (`PinnedTurnsOperation`) hasta que la tarea finaliza, guiando su comportamiento para procedimientos técnicos complejos.

## Herramientas del Agente

Las herramientas (clases que implementan `AgentTool`) son los efectores de Noema. Se dividen en los siguientes bloques funcionales:

*   **Sistema y Memoria:**
    *   `pool_event`: Consulta interna de eventos pendientes.
    *   `fetch_citation`: Recupera un turno exacto de la memoria episódica usando su ID.
    *   `search_full_history`: Búsqueda semántica vectorial en todo el historial.
    *   `annotate_observation`: Registra un hallazgo o directiva directamente en la memoria a largo plazo.
    *   `consult_environ`: Carga módulos densos de identidad/entorno.
    *   `schedule_alarm`: Programa un evento futuro en el planificador.
    *   `sensor_stop` / `sensor_start` / `sensor_status`: Gestión del flujo de atención y silenciamiento de canales.
*   **[Herramientas base y paginación](https://jjdelcerro.github.io/noema/docs/04-subsistemas-de-ejecucion-y-capacidades/01-herramientas-base-y-paginacion.html) (Archivos):**
    *   `file_read`: Lectura de archivos de texto (paginada).
    *   `read_paginated_resource`: Herramienta universal para continuar leyendo bloques de recursos largos.
    *   `file_find`: Búsqueda de archivos por patrón glob.
    *   `file_grep`: Búsqueda de texto o regex dentro de archivos.
    *   `file_read_selectors`: Lectura combinada de múltiples archivos.
    *   `file_write`: Creación o sobrescritura de archivos.
    *   `file_mkdir`: Creación de directorios.
    *   `file_search_and_replace`: Reemplazo exacto de bloques de texto.
    *   `file_patch`: Aplicación de parches en formato Unified Diff.
    *   `file_extract_text`: Extracción de texto de binarios (PDF, DOCX) vía Tika.
    *   `file_history` / `file_recovery`: Interacción con el historial de versiones local (JavaRCS).
*   **Ejecución y Scripting:**
    *   `shell_execute`: Ejecución de comandos Bash (con soporte para aislamiento vía Firejail).
    *   `execute_script`: Ejecución de código Groovy en el sandbox de la JVM.
*   **Internet y Web:**
    *   `web_search`: Búsqueda en internet (soporta proveedores Tavily y Brave).
    *   `web_get_content`: Descarga y extracción de texto de URLs.
    *   `get_weather`: Consulta meteorológica (Open-Meteo).
    *   `get_current_location`: Geolocalización por IP.
    *   `get_current_time`: Reloj del sistema con soporte de zonas horarias.
*   **Comunicaciones:**
    *   `email_list_inbox` / `email_read` / `email_send`: Gestión de correo electrónico como secretaria virtual.
    *   `telegram_send`: Envío proactivo de mensajes al usuario.
*   **Subagentes y Skills:**
    *   `list_subagents` / `launch_subagent`: Descubrimiento y orquestación de trabajadores asíncronos.
    *   `list_skills` / `activate_skill` / `deactivate_skill` / `read_skill_resource` / `run_skill_script`: Gestión del ciclo de vida de los procedimientos técnicos empaquetados.

## Construcción y Despliegue

El proyecto se compila utilizando Maven. El archivo `pom.xml` está configurado para generar un único artefacto ejecutable (Fat JAR) mediante el `maven-shade-plugin`. 
Un aspecto crítico de la construcción es el uso de `ServicesResourceTransformer`, necesario para fusionar correctamente los archivos `META-INF/services/` requeridos por librerías modulares como LangChain4j y Apache Tika.

El punto de entrada de la aplicación es `io.github.jjdelcerro.noema.main.Main`, el cual actúa como un enrutador que permite lanzar el agente en diferentes modalidades dependiendo de los argumentos de línea de comandos:
*   GUI (Swing/FlatLaf).
*   TUI (Lanterna).
*   Consola interactiva (JLine).
*   Servidor Web *headless* (Javalin).

La comunicación entre el núcleo del agente y estas interfaces se abstrae a través del contrato [AgentConsole y la comunicacion Core-UI](https://jjdelcerro.github.io/noema/docs/05-capa-de-presentacion-e-interfaces/00-contrato-agentconsole-y-comunicacion.md), permitiendo que el agente sea agnóstico respecto a cómo se renderizan sus pensamientos y respuestas.

## Conclusión

Noema presenta una arquitectura pragmática y altamente cohesionada para la experimentación con agentes autónomos persistentes. Su mayor innovación reside en el rechazo a la dependencia de infraestructuras externas complejas y en su enfoque para la gestión del estado cognitivo. 

En lugar de delegar la memoria a la fuerza bruta de ventanas de tokens masivas, Noema implementa una "espiral de contexto" donde la información se destila, se ancla semánticamente en una base de datos local y se proyecta dinámicamente. La combinación de este modelo de memoria con capacidades de *scripting* seguro (RLM) y delegación asíncrona (Subagentes) da como resultado un sistema capaz de mantener coherencia a largo plazo, operar sobre sistemas de archivos reales de forma segura y reaccionar a estímulos externos, todo ello encapsulado en un único proceso de la JVM.
