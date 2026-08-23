

**Informe de Análisis Arquitectónico y Técnico: Proyecto "Noema"**

*   **Versión Analizada:** 0.1.0 (Extraída del `pom.xml`)
*   **Fecha de Análisis:** 23 de Agosto de 2026
*   **Autor del Informe:** Gemini (IA), basado en la inspección estática del código fuente.

---

## Visión General

El proyecto **Noema** es un agente autónomo conversacional diseñado como un compañero de investigación y reflexión a largo plazo. Desarrollado como un proyecto personal, su objetivo no es la automatización masiva de desarrollo de software, sino servir de asistente interactivo capaz de mantener una única sesión persistente y continua en el tiempo. 

Para lograr esto, Noema descarta el concepto clásico de "múltiples chats" en favor de una única línea temporal. Aborda la limitación natural de la ventana de tokens de los LLM mediante un sofisticado mecanismo de compactación narrativa y poda selectiva, garantizando que el agente siempre tenga contexto sin importar la longevidad de la interacción.

Filosóficamente, el sistema es local, pragmático y con dependencias de infraestructura mínimas. Funciona de manera autónoma con solo el empaquetado Java y acceso a un LLM vía API (o modelos locales embebidos), delegando la persistencia a bases de datos relacionales embebidas y empleando bibliotecas nativas propias para el control de versiones local.

## Stack Tecnológico

El proyecto se sustenta en un ecosistema de librerías Java modernas:

*   **Lenguaje:** Java 25 (configurado en Maven).
*   **Gestión de LLM y RAG:** LangChain4j (versión 1.16.3). Actúa como capa de abstracción para conectar con proveedores como OpenAI, DeepSeek, Groq, OpenRouter o modelos locales (Jlama).
*   **Persistencia:** Base de datos relacional embebida H2 (v2.2.224) para el almacenamiento de metadatos, turnos de conversación e índices vectoriales.
*   **Interfaz de Usuario (Múltiple):**
    *   *Desktop (GUI):* Swing apoyado en FlatLaf para un tema oscuro moderno, y RSyntaxTextArea para la edición de código.
    *   *Terminal (TUI):* Lanterna para ventanas en modo texto.
    *   *Consola Interactiva (CLI):* JLine3 para el manejo de REPL, autocompletado y edición.
    *   *Web:* Javalin para exponer un servidor local con eventos SSE (Server-Sent Events) y una SPA en HTML/JS.
*   **Manejo de Documentos y Texto:** Apache Tika para extracción de texto; commonmark-java para el renderizado de Markdown; Jsoup para limpieza HTML; y Natty para el parseo de fechas en lenguaje natural.
*   **Control de Versiones y Diff:** Implementación nativa [JavaRCS](https://github.com/jjdelcerro/io.github.jjdelcerro.javarcs) y java-diff-utils.
*   **Utilidades:** MVEL para la evaluación dinámica de expresiones lógicas en la configuración; Gson para serialización JSON; Log4j2 + SLF4J para trazabilidad.

## Estructura de Paquetes e Interfaces/Implementación

El proyecto sigue una clara separación entre los contratos (interfaces) y su implementación lógica, promoviendo un bajo acoplamiento:

*   `io.github.jjdelcerro.noema.main`: Puntos de entrada (`Main`, `MainConsole`, `MainGUI`, `MainLanterna`, `NoemaWebServer`), encargados del bootstrapping y selección del entorno de presentación.
*   `io.github.jjdelcerro.noema.lib`: Contratos base del sistema (`Agent`, `AgentManager`, `AgentService`, interfaces de configuración y abstracciones de persistencia).
*   `io.github.jjdelcerro.noema.lib.services.*`: Contratos de los dominios específicos (sensores, memoria, razonamiento, documentos).
*   `io.github.jjdelcerro.noema.lib.impl.*`: Implementaciones reales de la lógica de negocio y de los servicios. Oculta la complejidad de H2, LangChain4j y la concurrencia.
*   `io.github.jjdelcerro.noema.ui.*`: Lógica de la capa de presentación, separada por la tecnología subyacente (Swing, Lanterna, Console), permitiendo el patrón de [Comunicación Core-UI](docs/comunicacion-core-ui.md).

## Arquitectura y Diseño

El agente está diseñado de manera modular. A continuación, se detalla la estructura organizativa de sus componentes.

### 1. El Kernel (o Core)

El núcleo del sistema gestiona la topología general y el arranque de todos los componentes.

*   **`Agent` y `AgentManager`:** El `Agent` representa el contexto en ejecución. En lugar de utilizar frameworks pesados, el `AgentManager` centraliza la [Inicialización e inyección de dependencias](docs/inicializacion-e-inyeccion-de-dependencias.md) de forma manual y predecible, registrando las factorías de servicios y facilitando las referencias cruzadas.
*   **Ciclo de Vida:** Gestionado explícitamente a través de los métodos `start()` y `stop()`. El Kernel inicializa las bases de datos, carga la configuración y arranca secuencialmente los servicios permitidos.
*   **Infraestructura de Datos:** A través de la interfaz `ConnectionSupplier`, el núcleo inyecta conexiones JDBC. La clase `SQLProvider` abstrae las consultas específicas, permitiendo gestionar de forma limpia el esquema relacional en H2.
*   **Topología de Archivos:** Todo el estado del agente reside en un *sandbox* local gestionado por [AgentPaths](docs/gestion-de-rutas.md). La jerarquía bajo la carpeta `.noema-agent` aisla recursos:
    *   `var/config`: Configuraciones, propiedades y *prompts*.
    *   `var/lib`: Bases de datos (memoria y servicios), y archivos de *checkpoints*.
    *   `var/tmp` y `var/cache`: Archivos volátiles para paginación de resultados masivos y cachés de extracción de documentos.
    *   `var/identity` y `var/skills`: Definición de la constitución operativa y catálogos procedimentales.

### 2. Capacidades Horizontales (Cross-cutting Concerns)

Módulos que atraviesan transversalmente todo el sistema, proveyendo utilidades fundamentales.

*   **Seguridad y Control de Acceso:** El componente [AgentAccessControl](docs/seguridad-y-control-de-acceso.md) ejerce como guardián. Define qué rutas pueden ser leídas o escritas, gestiona los permisos para ejecución de shell y peticiones de red, y fuerza la confirmación humana mediante diálogos síncronos ante operaciones destructivas. Adicionalmente, integra `firejail` para el aislamiento de comandos.
*   **Sistema de Configuración Jerárquica:** Representado por `AgentSettings` y su serialización en `settings.json`. Permite la recarga en caliente de políticas y credenciales. Su interfaz de usuario (`settingsui.json`) es dinámica, utilizando expresiones MVEL para habilitar o deshabilitar opciones condicionalmente.

### 3. Servicios Cognitivos y Persistencia

Este bloque gestiona la capacidad de pensar, recordar y procesar información en una línea temporal unificada.

*   **Orquestación:** El [ReasoningService](docs/reasoning-service.md) mantiene el hilo de ejecución principal (`eventDispatcher`). Extrae eventos de la periferia, ensambla el contexto dinámico, consulta al LLM, y enruta la ejecución de herramientas requeridas.
*   **Compactación:** Para evitar agotar el límite de tokens, el [MemoryCompactionService](docs/memory-service.md) es invocado periódicamente. Utiliza un LLM para transformar los turnos más antiguos en un `CheckPoint` narrativo (un texto compuesto por un "Resumen" y una historia detallada llamada "El Viaje").
*   **Vectores Locales:** El [EmbeddingsService](docs/embeddings-service.md) carga un modelo ONNX local en memoria (por defecto *all-MiniLM-L6-v2*) para calcular vectores densos. Esto permite el filtrado por similitud del coseno directamente en Java sin dependencias de infraestructura.
*   **Abstracciones de Memoria:**
    *   `EpisodicMemory`: Interfaz contra la base de datos H2 que guarda cada interacción inmutable (`Turn`) y los `CompactedMemory` (metadatos de los puntos de control).
    *   `RecentMemory`: La memoria de trabajo en RAM, que mantiene los mensajes LangChain4j de los últimos intercambios y decide cuándo es necesaria la compactación.
    *   `ProjectedMemory`: Genera la ventana de contexto final para el LLM. Aplica algoritmos de recorte (truncado de salidas grandes) e inyecta advertencias efímeras si el agente olvida extraer conocimiento clave de los textos leídos.

### 4. Servicios de Periferia

Subsistemas que conectan al agente con el mundo exterior o fuentes de eventos asíncronos.

*   **Gestión de Eventos:** El [SensorsService](docs/sensors-service.md) actúa como el bus de entrada asíncrona. Normaliza las percepciones (mensajes, alarmas, estados) clasificándolas por su naturaleza (`DISCRETE`, `MERGEABLE`, `AGGREGATABLE`, `STATE`, `USER`), resolviendo posibles conflictos cronológicos en su cola de entrega.
*   **Planificación:** El [SchedulerService](docs/scheduler-service.md) provee la capacidad de programar tareas diferidas (alarmas). Persiste las solicitudes en base de datos y, llegado el momento, inyecta un evento en la cola sensorial para que el agente reaccione.
*   **Comunicaciones:**
    *   `TelegramService`: Escucha proactivamente mensajes entrantes filtrados por un `chat_id` autorizado y provee capacidad de respuesta.
    *   `EmailService`: Monitoriza bandejas IMAP e inyecta avisos ligeros de nuevos correos; adicionalmente permite enviar correos SMTP y leer cuerpos de mensaje bajo demanda.
*   **Manejo Documental:** El `DocumentsService` encapsula el proceso RAG (Retrieval-Augmented Generation). Transforma archivos brutos mediante Tika en esquemas jerárquicos (índices), calcula embeddings de los resúmenes y permite la lectura de secciones específicas para no saturar al LLM.
*   **Protocolo de Modelos (MCP):** El `McpService` descubre dinámicamente clientes MCP (por STDIO o HTTP/SSE), envolviendo sus funciones remotas como herramientas estándar dentro del ecosistema de Noema.

---

## Herramientas del Agente ([AgentTools](docs/agenttools.md))

Noema posee un catálogo extenso de capacidades que el modelo de lenguaje puede invocar para interactuar con su entorno. Se enumeran a continuación categorizadas por su dominio:

*   **Identidad y Memoria Interna**
    *   `consult_environ`: Carga módulos de conocimiento denso o biográfico del entorno del usuario.
    *   `list_skills`: Enumera el catálogo de habilidades y flujos de trabajo disponibles.
    *   `load_skill`: Recupera el manual técnico completo de una habilidad concreta.
    *   `fetch_citation` (`LookupTurnTool`): Recupera el contenido íntegro y el contexto de un turno histórico a partir de un identificador de cita (ej. `{cite:123}`).
    *   `search_full_history`: Ejecuta búsqueda semántica local sobre todo el histórico de la conversación.
    *   `annotate_observation`: Guarda conclusiones, notas o resúmenes de manera explícita para que se integren en la consolidación de la memoria a largo plazo.
*   **Gestión Operativa y de Eventos**
    *   `pool_event`: Herramienta ficticia utilizada estructuralmente para simular que el agente consulta su cola de eventos externos.
    *   `schedule_alarm`: Programa notificaciones futuras parseando fechas en lenguaje natural.
    *   `sensor_status`: Muestra estadísticas, salud y configuración de los canales de entrada.
    *   `sensor_stop` / `sensor_start`: Permite al agente ignorar temporalmente ciertos canales (ej. silenciar notificaciones).
    *   `get_current_time`: Informa de la fecha y hora del sistema para cálculo de temporalidades.
*   **Interacción con Sistema de Archivos**
    *   `file_find`: Busca recursivamente archivos por patrones *glob*.
    *   `file_grep`: Busca contenido de texto (expresiones) dentro de un directorio o fichero.
    *   `file_read`: Lee y sirve el contenido de ficheros de texto plano.
    *   `read_paginated_resource`: Herramienta universal que sirve bloques de contenido masivo (ficheros, descargas, logs) usando *offsets* a partir de un identificador `tmp://` o `cache://`.
    *   `file_write`: Crea o sobrescribe un archivo en disco.
    *   `file_mkdir`: Crea directorios físicos en la ruta indicada.
    *   `file_patch`: Modifica archivos aplicando un bloque *Unified Diff*.
    *   `file_search_and_replace`: Reemplaza un texto exacto por otro en un documento.
    *   `file_extract_text`: Extrae el texto de archivos binarios (PDF, DOCX) utilizando Apache Tika.
    *   `file_history`: Muestra el registro de control de revisiones local (commits y fechas) de un fichero.
    *   `file_recovery`: Restaura el estado de un fichero a una versión anterior específica.
    *   `file_read_selectors`: Crea un *bundle* combinando el contenido de múltiples archivos basado en patrones *glob*.
*   **Ejecución de Comandos**
    *   `shell_execute`: Lanza procesos en el terminal del sistema operativo, capturando su salida de error y estándar hacia un fichero temporal paginado.
*   **Web y Red**
    *   `web_search`: Búsqueda de información en Internet utilizando el proveedor Tavily.
    *   `web_search` (variante Brave): Implementación alternativa para el motor Brave Search.
    *   `web_get_content`: Descarga código HTML de una URL y lo limpia utilizando Tika para extraer solo el texto relevante.
    *   `get_weather`: Obtiene previsiones meteorológicas y clima actual vía Open-Meteo.
    *   `get_current_location`: Determina la geolocalización basada en la IP externa del host.
*   **Comunicaciones**
    *   `email_list_inbox`: Lee las cabeceras (asunto, remitente, UID) de los últimos correos recibidos.
    *   `email_read`: Descarga y limpia el cuerpo completo de un correo específico por su UID.
    *   `email_send`: Redacta y transmite un correo mediante protocolo SMTP.
    *   `telegram_send`: Envía mensajes push al usuario mediante la API de Telegram.
*   **Gestión Documental Estructurada (RAG)**
    *   `document_index`: Encola un documento para su análisis estructural asíncrono.
    *   `document_search`: Búsqueda híbrida que cruza categorías estrictas con similitud vectorial en resúmenes.
    *   `document_search_by_categories`: Filtra directamente los documentos por su tipología.
    *   `document_search_by_sumaries`: Ejecuta exclusivamente búsqueda semántica sobre los resúmenes documentales.
    *   `get_document_structure`: Recupera el índice jerárquico XML de un documento mapeado.
    *   `get_partial_document`: Inyecta el contenido completo en formato XML solo para las secciones específicas que el modelo decida expandir.
*   **Gestión de Subagentes**
    *   `list_subagents`: Lista el catálogo de recetas de trabajadores en segundo plano disponibles en el sistema.
    *   `launch_subagent`: Inicia de forma asíncrona una tarea especializada aislada.

---

## Mecanismos Principales Detallados

### Gestión de Memoria

El agente utiliza un flujo de estado continuo, sin fragmentación en sesiones pasadas. Cuando la ventana de trabajo en RAM (`RecentMemory`) alcanza un límite paramétrico (ej. 40 turnos), el orquestador congela el último bloque de mensajes y lo delega al servicio de compactación. 
El LLM encargado de la compactación recibe el punto de guardado anterior y los nuevos turnos en formato CSV, y redacta una narrativa fluida denominada "El Viaje". Este resumen no pierde trazabilidad: mantiene identificadores rígidos (`{cite:123}`) atados a los registros inmutables de la base de datos H2. 
Adicionalmente, herramientas con salida masiva sufren una recesión cognitiva (`trimResult`): se elimina su cuerpo del historial activo para ahorrar tokens, dejando solo metadatos y advirtiendo al modelo de que debe invocar la herramienta de anotación (`annotate_observation`) para cristalizar los conceptos clave antes de que la información se pode.

### Gestión de la Identidad del Agente

La constitución del agente no reside en su código, sino en archivos de texto editables en tiempo de ejecución. 
*   **Core:** Instrucciones inmutables (metodologías, personalidad técnica) inyectadas directamente en el `SystemPrompt` en la construcción de cada contexto.
*   **Entorno (Environ):** Archivos separados en dos capas. Un archivo `.ref.md` ligero que actúa como índice y se inyecta permanentemente, advirtiendo al agente de la existencia de información externa; y un archivo `.md` denso que solo se carga en la memoria de trabajo si el agente invoca `consult_environ` para estudiarlo en profundidad.

### Gestión de Habilidades (Skills)

El sistema de habilidades (`var/skills/`) replica la técnica del entorno. Se presentan al modelo los descriptores ligeros de las tareas que puede realizar (ej. "Refactorizar backend", "Desplegar servidor"). Ante peticiones complejas, el LLM puede utilizar `list_skills` para identificar su catálogo, y `load_skill` para adquirir de manera efímera el paso a paso del procedimiento manual en su memoria de trabajo, garantizando alta precisión operativa sin mantener promtps sobrecargados.

### Gestión de Eventos

Rompiendo el paradigma pasivo "Request-Response" de los LLM, la aplicación abstrae las notificaciones (Telegram, correos, alarmas, notificaciones del propio sistema) en una cola unificada concurrente gestionada por el servicio sensorial.
Cuando un estímulo llega, el orquestador intercepta el bucle de razonamiento. Inyecta en el historial conversacional un mensaje artificial simulando que el modelo ejecutó la herramienta `pool_event`, seguido del mensaje de resultado que contiene el evento real. Esto preserva la ilusión de agencia y la estructura estricta del historial, permitiendo que el LLM reaccione proactivamente a eventos asíncronos como si hubiese sondeado el entorno por voluntad propia.

### Indexación de Documentos

Noema implementa un RAG estructural en lugar de un RAG por fragmentación semántica ciega (chunking simple). 
Al invocar `document_index`, un hilo asíncrono mapea el archivo. Si la estructura jerárquica no puede deducirse por reglas, se utiliza un LLM dedicado (`DOCMAPPER_REASONING_LLM`) para analizar el CSV paginado y devolver un JSON con la estructura (secciones, títulos, niveles lógicos). 
Posteriormente, otro modelo más ligero (`DOCMAPPER_BASIC_LLM`) resume el texto contenido en cada sección delimitada y le asigna etiquetas/categorías. Finalmente, los resúmenes se vectorizan usando el servicio local de embeddings. 
El LLM primario puede entonces navegar este árbol: primero buscando conceptualmente (`document_search`), luego solicitando el esqueleto del documento en XML (`get_document_structure`) y por último expandiendo para su lectura detallada solo los nodos específicos que necesita investigar (`get_partial_document`).

### Gestión de la Seguridad

Dado el nivel de autonomía, el control de riesgos es vital:
*   **Restricción de Acceso:** La resolución de rutas asegura que ninguna lectura/escritura escape del *Workspace* activo, o de la lista blanca de rutas externas configuradas explícitamente (`allowed_external_paths`). Protege activamente la escritura en zonas sensibles (como ficheros ocultos `.git` o copias de seguridad de versión).
*   **Confirmación Humana:** Todas las herramientas marcan su potencial de riesgo (`MODE_WRITE`, `MODE_EXECUTION`). Al ser invocadas por el modelo, el hilo principal se bloquea levantando un diálogo síncrono en la interfaz gráfica (o un *prompt* en terminal). Si el usuario deniega la operación, la herramienta devuelve un texto de fallo formal que se inyecta de nuevo en el historial, permitiendo al LLM rectificar su estrategia.
*   **Integración RCS Automática:** Antes de que las herramientas `file_write`, `file_patch` o `file_search_and_replace` alteren un documento, el sistema lanza de forma invisible un comando de *checkin* contra la librería [JavaRCS](https://github.com/jjdelcerro/io.github.jjdelcerro.javarcs). Esto encapsula el estado previo del archivo, posibilitando auditorías de historial (`file_history`) y revesiones absolutas del estado (`file_recovery`) por parte del agente o el usuario.

### Flujos en el Conversation Manager

El orquestador de razonamiento corre en un bucle perenne (`eventDispatcher`). Su ciclo iterativo:
1.  Espera bloqueado en la cola sensorial.
2.  Extrae un evento, evaluando su marca temporal para ordenar lógicamente alarmas diferidas contra mensajes de red.
3.  Carga el último `CheckPoint` en caché y los mensajes de la memoria de trabajo.
4.  Aplica transformaciones de amnesia selectiva: recorta resultados grandes antiguos y verifica si hay "recursos sin anotar" para inyectar advertencias efímeras.
5.  Despacha el *prompt* hacia el LLM.
6.  Si el LLM devuelve peticiones de herramientas, las ejecuta secuencialmente, inyecta los resultados y vuelve al paso 3.
7.  Si devuelve texto plano, lo transmite a la interfaz gráfica, sella el turno en la persistencia y comprueba el umbral para disparar un evento de compactación narrativa.

### Subagentes

Para tareas de procesamiento que consumirían demasiados turnos en el bucle principal (ej. recorrer un directorio inmenso, indexar varios manuales, procesar extracciones pesadas), el modelo puede utilizar `launch_subagent`. 
A partir de un descriptor XML (`var/subagents/`), el sistema crea un espacio de trabajo efímero independiente y levanta una instancia secundaria aislada del agente. Este subagente ejecuta su instrucción en un hilo de plataforma en segundo plano. Cuando completa su objetivo (o falla por un *timeout*), inyecta los resultados a través del bus de eventos de sistema (`SYSTEMNOTIFICATION`) de vuelta al historial conversacional del agente principal, permitiéndole operar de forma asíncrona sin bloquear la interacción con el usuario humano.

---

## Construcción y Despliegue

La solución está empaquetada de manera integral facilitando su distribución.
Se apoya en Apache Maven y define la compatibilidad a nivel de lenguaje con Java 25. 
El plugin `maven-shade-plugin` agrupa todas las dependencias transitivas (motores LLM, drivers H2, librerías de UI de FlatLaf y RSyntaxTextArea, parseadores, etc.) en un único Uber-JAR. Transforma adecuadamente el manifiesto de la aplicación y agrupa las firmas de servicios garantizando que todos los proveedores dinámicos (como SLF4J y servicios de Tika) estén correctamente instanciados en ejecución. 
No se requiere infraestructura adicional ni dependencias en el host más allá de contar con la Máquina Virtual de Java correspondiente. 

## Conclusión

El proyecto Noema es un ejercicio de arquitectura pragmática excepcional. Rechaza deliberadamente soluciones sobredimensionadas como bases de datos vectoriales dedicadas o microservicios externos en favor de un sistema monolítico, portátil e introspectivo basado fundamentalmente en la persistencia por sistema de archivos local y SQL embebido.

Destaca profundamente su abordaje del mayor desafío de los LLMs modernos: el límite de ventana de contexto. Sustituir un patrón de retención de texto ciego por un sistema activo de compactación narrativa —dotando al agente de herramientas para auditar su propio historial, recuperar recuerdos encapsulados y escribir memorándums preventivos— resulta en un paradigma cognitivo brillante. Todo esto, respaldado por un control de seguridad granular que pone siempre al humano como guardián último, transforma lo que podría ser un mero script de invocaciones a API en un genuino "compañero digital" continuo y predecible.
