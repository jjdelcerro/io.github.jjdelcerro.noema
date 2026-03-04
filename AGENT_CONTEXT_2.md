# Informe de Análisis Estático del Proyecto "Noema Agent"

---

**Versión Analizada:** 0.1.0
**Fecha de Análisis:** 2024-05-16
**Autor del Informe:** Gemini (IA), basado en la inspección estática del código fuente.

---

## Visión General

Noema Agent es un proyecto de agente de software personal, diseñado para asistir en tareas de investigación y reflexión a largo plazo. Su enfoque principal reside en la gestión de la memoria conversacional, permitiendo al agente mantener diálogos extensos y coherentes, apoyándose en un sistema de persistencia robusto para almacenar y recuperar interacciones. A pesar de no estar concebido como una herramienta de desarrollo de software, incorpora capacidades para interactuar con el sistema de archivos, ejecutar comandos de shell y consumir servicios externos a través de una arquitectura modular basada en "Agentes" y "Servicios". El proyecto prioriza una infraestructura ligera, ejecutándose principalmente a partir de un archivo JAR e interactuando con LLMs vía API, con el objetivo de ser un compañero de "charla" y reflexión.

El agente se presenta con dos interfaces de usuario principales: una interfaz gráfica de usuario (GUI) construida con Swing y una interfaz de línea de comandos (CLI) que utiliza JLine para una experiencia interactiva y con resaltado de sintaxis.

---

## Stack Tecnológico

El proyecto se basa principalmente en Java y utiliza una serie de librerías de terceros para habilitar sus funcionalidades:

*   **Lenguaje:** Java (versión 21 en el `pom.xml`).
*   **Framework de Construcción:** Maven.
*   **LLM & AI:**
    *   `langchain4j`: Núcleo del framework para la interacción con Modelos de Lenguaje Grandes (LLMs).
    *   `langchain4j-open-ai`: Integración específica con OpenAI (utilizado para la conversación y la memoria).
    *   `langchain4j-embeddings-all-minilm-l6-v2`: Implementación de embeddings vectoriales locales.
*   **Base de Datos:** H2 (utilizada para persistencia de turnos, checkpoints y metadatos de servicios).
*   **Interfaz de Usuario (CLI):**
    *   JLine 3: Para la interfaz de línea de comandos interactiva.
*   **Interfaz de Usuario (GUI):**
    *   Swing: Framework estándar de Java para interfaces gráficas.
    *   Flatlaf: Para un look & feel moderno y personalizable.
    *   MigLayout: Para la gestión flexible de la disposición de componentes en Swing.
    *   RSyntaxTextArea: Editor de texto con resaltado de sintaxis integrado.
    *   RSTaui: Utilidades para diálogos de búsqueda y reemplazo en RSyntaxTextArea.
    *   JGoodies Forms: Para la generación de formularios de configuración.
*   **Utilidades de Sistema:**
    *   Apache Commons Collections, Commons Lang3: Para colecciones y utilidades generales.
    *   Google Gson: Para la serialización/deserialización JSON.
    *   Apache Tika: Para la extracción de texto de diversos formatos de archivo (PDF, DOCX, etc.).
    *   Joestelmach Natty: Para el parseo de lenguaje natural de fechas y horas.
    *   Java-diff-utils: Para la generación y aplicación de parches.
    *   Log4j 2: Para logging del sistema.
    *   Fastjson: (Aunque no se ve en los fuentes, podría ser una dependencia o para uso interno).
    *   com.fifesoft: RSyntaxTextArea y RSTAUi.
*   **Conexiones y Servicios:**
    *   Jakarta Mail: Para la gestión de correo electrónico (IMAP, SMTP).
    *   Telegram Bot API (java-telegram-bot-api): Para la integración con Telegram.
*   **Control de Versiones (Integrado):**
    *   JavaRCS: Implementación nativa en Java para interactuar con el sistema RCS de control de versiones.
*   **Networking:**
    *   HttpClient (Java 11+): Para realizar peticiones HTTP.

---

## Estructura de Paquetes, Interfaces y Implementaciones

El proyecto sigue una arquitectura modular, donde las funcionalidades principales se exponen a través de interfaces bien definidas, y las implementaciones concretas se encuentran en subpaquetes.

### Estructura de Paquetes (Principales)

*   `io.github.jjdelcerro.noema.lib`: Contiene las interfaces centrales del framework del agente, definiciones de servicios, acciones, herramientas y la gestión de la configuración y rutas.
    *   `impl`: Implementaciones concretas de las interfaces del `lib`.
        *   `persistence`: Implementaciones para la gestión de la "Source of Truth" (H2, turnos, checkpoints).
        *   `services`: Implementaciones de los diferentes servicios que componen el agente (memoria, conversación, documentos, etc.). Cada servicio suele tener su propia factoría (`*ServiceFactory`) y herramientas asociadas (`tools`).
        *   `settings`: Implementaciones para la gestión de la configuración.
        *   `swing`: Implementaciones de UI para la configuración.
        *   `console`: Implementaciones de UI para la consola.
*   `io.github.jjdelcerro.noema.main`: Contiene los puntos de entrada principales de la aplicación (`Main`, `MainGUI`, `MainConsole`, `BootUtils`).
*   `io.github.jjdelcerro.noema.ui`: Define las interfaces de usuario abstractas (`AgentUIManager`, `AgentUISettings`, `AgentConsole`) y las implementaciones concretas (Swing y Consola).
    *   `common`: Elementos de UI comunes para la configuración.
    *   `swing`: Implementaciones de UI Swing.
    *   `console`: Implementaciones de UI de consola.

### Interfaces Clave

*   **`Agent`**: Representa el núcleo del agente, exponiendo métodos para la interacción con el modelo de lenguaje, gestión de servicios, herramientas, memoria y sistema de eventos.
*   **`AgentService`**: Interfaz base para todos los servicios que componen el agente (memoria, conversación, documentos, etc.).
*   **`AgentServiceFactory`**: Factoría para crear instancias de `AgentService`.
*   **`AgentTool`**: Interfaz para las herramientas que el agente puede ejecutar (llamadas a funciones, APIs externas, etc.).
*   **`AgentSettings`**: Define la API para acceder y gestionar la configuración del agente.
*   **`SourceOfTruth`**: Define la API para la persistencia de la memoria del agente (turnos, checkpoints).
*   **`AgentConsole`**: Define la API para la interacción con el usuario (entrada/salida).
*   **`AgentPaths`**: Define la estructura de directorios para la operación del agente.
*   **`AgentAccessControl`**: Define las políticas de seguridad para el acceso al sistema de archivos.
*   **`SensorsService`**: Define la API para la gestión de eventos sensoriales del entorno.

### Implementaciones Principales

*   **`AgentManagerImpl`**: Gestiona el registro y acceso a los `AgentServiceFactory`.
*   **`AgentImpl`**: Implementación central del `Agent`, orquestando los diferentes servicios y el ciclo de vida del agente.
*   **`ConversationServiceImpl`**: El "cerebro" del agente, responsable de gestionar el bucle de razonamiento, la planificación de la conversación y la ejecución de herramientas.
*   **`MemoryServiceImpl`**: Gestiona la consolidación y el acceso a la memoria a largo plazo del agente, creando "Puntos de Guardado".
*   **`SourceOfTruthImpl`**: Implementación de la persistencia de memoria, utilizando H2 como base de datos.
*   **`EmbeddingsService`**: Proporciona capacidades de embedding vectorial, crucial para la búsqueda semántica.
*   **`SensorsServiceImpl`**: Gestiona la entrada de información del entorno a través de diversos "sensores" (Telegram, Email, Sistema, etc.).
*   **`AgentSwingManagerImpl` / `AgentConsoleManagerImpl`**: Implementaciones específicas de la interfaz `AgentUIManager` para las interfaces gráfica y de consola, respectivamente.

---

## Arquitectura y Diseño

El diseño del Noema Agent sigue un patrón de arquitectura de **agente conversacional con memoria y capacidades de acción**, inspirado en los principios de los LLMs modernos, pero adaptado a un entorno de aplicación Java.

### Principios Arquitectónicos

1.  **Modularidad y Servicios:** El agente se compone de múltiples "Servicios" (ej. Memoria, Conversación, Documentos, Sensores, Redes) que pueden ser registrados y accedidos a través de una factoría (`AgentServiceFactory`). Esta modularidad permite la extensión y el reemplazo de componentes.
2.  **Centralización en el Agente:** La interfaz `Agent` actúa como el coordinador principal, dando acceso a todos los subcomponentes (servicios, configuración, consola, memoria, etc.).
3.  **Herramientas (Tools) como Mecanismo de Acción:** Las funcionalidades específicas que el agente puede realizar (consultar el tiempo, buscar en la web, leer archivos, enviar emails) se exponen como `AgentTool`. Estas herramientas tienen especificaciones (`ToolSpecification`) que permiten al LLM entender su propósito y parámetros, y luego el agente ejecuta la lógica correspondiente.
4.  **Memoria Persistente:** El agente mantiene una "Fuente de Verdad" (`SourceOfTruth`) que almacena el historial de interacciones (`Turn`) y resúmenes periódicos (`CheckPoint`). Esto permite mantener una memoria a largo plazo y coherente a través de sesiones.
5.  **Gestión de Eventos Sensoriales:** El sistema de `SensorsService` permite que eventos asíncronos del entorno (mensajes de Telegram, emails, eventos del sistema) sean inyectados en el flujo de conversación del agente, simulando una "percepción" del mundo exterior.
6.  **Seguridad del Sandbox:** Se implementa un control de acceso (`AgentAccessControl`) para restringir las operaciones del agente en el sistema de archivos, previniendo accesos no autorizados o `path traversal`. El uso de `Firejail` para la ejecución de comandos shell añade una capa de aislamiento adicional.
7.  **Interfaces de Usuario Flexibles:** El sistema soporta tanto una interfaz gráfica de usuario (GUI) como una interfaz de línea de comandos (CLI), ambas gestionadas por `AgentUIManager`.
8.  **Gestión de Configuración:** La configuración se centraliza en `AgentSettings`, permitiendo definir modelos, proveedores, herramientas activas y políticas de seguridad, cargada desde archivos JSON y propiedades.

### Diseño de la Memoria Conversacional

La gestión de la memoria es un punto central del diseño y se articula en tres niveles:

1.  **Memoria a Corto Plazo (Sesión):** La clase `Session` dentro de `ConversationServiceImpl` mantiene una lista de `ChatMessage` que representan el historial inmediato de la conversación. Esta lista es volátil por defecto pero se persiste a disco (`active_session.json`) para permitir la continuidad entre reinicios. La sesión también gestiona el estado de las interacciones con herramientas, los resultados y el tiempo transcurrido.
2.  **Persistencia de Turnos (`SourceOfTruth`):** Cada turno de conversación (entrada del usuario, respuesta del modelo, ejecución de herramienta) se guarda en una base de datos H2. Cada turno incluye metadatos, contenido y, si aplica, un vector de embedding (`float[]`) para búsquedas semánticas.
3.  **Puntos de Guardado (`CheckPoint`):** Periódicamente, o cuando se detecta suficiente actividad, el `MemoryServiceImpl` utiliza un LLM para generar un "Punto de Guardado". Este es un resumen narrativo y estructurado de un segmento de la conversación, con referencias explícitas a los `Turn` originales (`{cite:ID}`). Los `CheckPoints` se guardan como archivos `.md` en disco y sus metadatos en la base de datos H2. Esto permite condensar la historia y mantener un contexto manejable para el LLM, al tiempo que preserva la capacidad de "volver atrás" a detalles específicos mediante `lookup_turn`.

---

## Herramientas del Agente (Agent Tools)

El agente expone un conjunto extenso de herramientas que le permiten interactuar con el entorno y el sistema. Estas herramientas se organizan funcionalmente:

### 1. Herramientas de Sistema

*   **`pool_event` (PoolEventTool):** Permite al agente consultar de forma proactiva si hay eventos sensoriales pendientes (mensajes de Telegram, emails, etc.). Actúa como un mecanismo de "polling" para la percepción asíncrona.
*   **`lookup_turn` (LookupTurnTool):** Recupera un turno específico de la memoria a largo plazo usando su ID (`{cite:ID}`). Permite acceder a detalles de conversaciones pasadas y su contexto inmediato.
*   **`search_full_history` (SearchFullHistoryTool):** Realiza una búsqueda semántica en todo el historial de la memoria del agente para encontrar información relevante, incluso si no se recuerda la fecha exacta.
*   **`schedule_alarm` (ScheduleAlarmTool):** Permite al agente programar alarmas o avisos para el futuro utilizando lenguaje natural para especificar el tiempo.

### 2. Herramientas de Archivos (File System)

*   **`file_find` (FileFindTool):** Busca archivos en el sistema de archivos usando patrones glob y devuelve metadatos como tamaño, tipo y fecha de modificación.
*   **`file_grep` (FileGrepTool):** Busca texto o patrones regex dentro de archivos de texto en el proyecto.
*   **`file_read` (FileReadTool):** Lee el contenido de archivos de texto, con soporte para paginación (offset y limit). Utiliza caché LRU y valida la fecha de modificación.
*   **`file_write` (FileWriteTool):** Escribe o sobrescribe un archivo con contenido proporcionado, creando directorios padre si es necesario y gestionando el CI con RCS.
*   **`file_search_and_replace` (FileSearchAndReplaceTool):** Realiza reemplazos simples de texto en un archivo, asegurando la unicidad del `oldText` para evitar modificaciones accidentales.
*   **`file_patch` (FilePatchTool):** Aplica parches en formato Unified Diff a archivos, permitiendo modificaciones más complejas y seguras. También interactúa con RCS para el `checkin` previo.
*   **`file_mkdir` (FileMkdirTool):** Crea directorios (incluyendo los padres) en el sandbox del agente.
*   **`file_extract_text` (FileExtractTextTool):** Utiliza Apache Tika para extraer texto de formatos de archivo complejos (PDF, DOCX, etc.), guardando el resultado en caché.
*   **`file_recovery` (FileRecoveryTool):** Recupera versiones anteriores de un archivo desde el historial RCS (comando `co`), sobrescribiendo el archivo actual.
*   **`file_history` (FileHistoryTool):** Consulta el historial de revisiones de un archivo gestionado por RCS, devolviendo información sobre autores, fechas y mensajes de commit.

### 3. Herramientas de Ejecución (Shell)

*   **`shell_execute` (ShellExecuteTool):** Ejecuta comandos de sistema en una shell de Bash, con un enfoque en la seguridad (Firejail, restricciones de interactividad, gestión de salida en disco y confirmación humana para operaciones largas).
*   **`shell_read_output` (ShellReadOutputTool):** Permite leer la salida de comandos `shell_execute` que fueron truncados o que generaron una gran cantidad de datos, utilizando el mismo mecanismo de paginación que `file_read`.

### 4. Herramientas de Internet

*   **`web_search` (WebSearchTool):** Realiza búsquedas en internet utilizando la API de Brave Search para obtener información actualizada.
*   **`web_get_content` (WebGetTikaTool / WebGetTool):**
    *   `WebGetTikaTool`: Extrae texto legible de URLs, manejando diferentes tipos de contenido (HTML, PDF, etc.) con Apache Tika.
    *   `WebGetTool`: Obtiene el contenido HTML crudo de una URL, realizando una limpieza básica de scripts y estilos.
*   **`get_weather` (WeatherTool):** Consulta el clima actual y la previsión para una ubicación dada, utilizando la API de Open-Meteo.
*   **`get_current_location` (LocationTool):** Determina la ubicación geográfica aproximada del agente basada en su dirección IP pública.

### 5. Herramientas de Comunicación

*   **`email_list_inbox` (EmailListTool):** Lista las cabeceras de los últimos correos electrónicos relevantes (basado en remitente autorizado) para su posterior lectura.
*   **`email_read` (EmailReadTool):** Lee el contenido completo y limpio de un correo electrónico específico usando su UID.
*   **`email_send` (EmailSendTool):** Envía correos electrónicos a destinatarios especificados.
*   **`telegram_send` (TelegramTool):** Envía mensajes al usuario a través de Telegram, sirviendo como canal de notificación proactiva.

### 6. Herramientas de Documentos (Indexación y Recuperación)

*   **`document_index` (DocumentIndexTool):** Inicia el proceso de indexación de un documento (PDF, TXT, etc.), extrayendo su estructura y contenido para permitir búsquedas futuras.
*   **`document_search` (DocumentSearchTool):** Busca documentos combinando filtros por categorías y búsqueda semántica en resúmenes.
*   **`document_search_by_categories` (DocumentSearchByCategoriesTool):** Busca documentos basándose en una lista de categorías específicas.
*   **`document_search_by_summaries` (DocumentSearchBySummariesTool):** Busca documentos por significado semántico en sus resúmenes.
*   **`get_document_structure` (GetDocumentStructureTool):** Recupera el esquema jerárquico (índice) de un documento indexado en formato XML.
*   **`get_partial_document` (GetPartialDocumentTool):** Recupera secciones específicas de un documento indexado, inyectando su contenido de texto completo.

### 7. Herramientas de Percepción (Sensores)

*   **`sensor_status` (SensorStatusTool):** Permite al agente consultar el inventario de canales sensoriales, su estado (silenciado/activo) y estadísticas de actividad.
*   **`sensor_start` (SensorStartTool):** Reactiva la recepción de eventos de canales sensoriales específicos.
*   **`sensor_stop` (SensorStopTool):** Suspende temporalmente la recepción de eventos de uno o varios canales sensoriales.

---

## Construcción y Despliegue

El proyecto utiliza Maven como sistema de construcción. El `pom.xml` define las dependencias y el plugin `maven-shade-plugin` se encarga de crear un JAR ejecutable con la configuración de la clase principal (`io.github.jjdelcerro.noema.main.Main`).

El despliegue consiste en ejecutar el archivo JAR generado:
`java -jar io.github.jjdelcerro.noema.main.jar`

Para iniciar la interfaz de consola, se utiliza el argumento `-c`:
`java -jar io.github.jjdelcerro.noema.main.jar -c`

La configuración inicial se realiza a través de archivos JSON y `.properties` ubicados en la estructura de directorios del agente (`var/config` dentro del workspace, y en el directorio home del usuario para configuraciones globales). El script `packgroups.sh` parece ser un script de ayuda para empaquetar las fuentes, pero su funcionalidad específica no es central para el entendimiento de la arquitectura del agente.

---

## Visión General de los Mecanismos Principales

### Gestión de Memoria

La gestión de memoria en Noema Agent es un proceso multi-capa y multi-granularidad:

1.  **Memoria a Corto Plazo (Sesión):** La clase `Session` dentro de `ConversationServiceImpl` mantiene el historial reciente de la conversación en memoria (`List<ChatMessage>`). Esta lista se persiste a disco (`active_session.json`) para mantener la continuidad de la conversación entre ejecuciones del agente. Incluye el manejo de `UserMessage`, `AiMessage`, `ToolExecutionRequest` y `ToolExecutionResultMessage`, así como el `timestamp` de las interacciones.
    *   **Trazabilidad:** Cada mensaje se asocia con un `Turn` específico de la base de datos H2 a través de un `ChatMessageInfo` en un mapa (`turnOfMessage`).
    *   **Percepción Temporal:** Se introduce información temporal sobre el tiempo transcurrido desde la última interacción (`lastInteractionTime`), simulando una conciencia del paso del tiempo.
    *   **Compactación:** La sesión monitoriza la cantidad de turnos consolidados (`needCompaction`). Cuando se alcanza un umbral configurable (`MEMORY_COMPACTION_TURNS`), se activa el proceso de compactación.

2.  **Persistencia de Turnos:** El `SourceOfTruthImpl` es el repositorio central de la memoria a largo plazo. Utiliza una base de datos H2 para almacenar cada `Turn` de la conversación. Cada `Turn` contiene:
    *   Metadatos: ID, timestamp, tipo de contenido (`contenttype`).
    *   Contenido textual: `textUser`, `textModelThinking`, `textModel`, `toolCall`, `toolResult`.
    *   Embedding vectorial: Un `float[]` generado por `EmbeddingsService` a partir del contenido textual (`getContentForEmbedding`), almacenado como BLOB en la base de datos. Esto permite búsquedas semánticas eficientes.

3.  **Puntos de Guardado (`CheckPoint`):** El `MemoryServiceImpl` es responsable de la "compactación" de la memoria. Cuando la sesión acumula suficientes turnos, o bajo demanda, utiliza un LLM para generar un `CheckPoint`. Este `CheckPoint` es un resumen narrativo y consolidado de un segmento de la conversación, incluyendo referencias explícitas a los `Turn` originales mediante el formato `{cite:ID}`. El `MemoryServiceImpl` utiliza un LLM (configurado como "MEMORY") y un prompt específico (`prompts/memory-compact.md`) para esta tarea. Los `CheckPoints` se guardan en disco como archivos `.md` y sus metadatos en la base de datos H2. El agente mantiene una referencia al último `CheckPoint` (`activeCheckPoint`) para contextualizar las nuevas conversaciones.

### Gestión de Eventos

La gestión de eventos se centraliza en el `SensorsService`.

*   **Sensores y Naturaleza:** Los sensores (`SensorInformation`) se registran con una `SensorNature` que define cómo se procesarán sus eventos:
    *   `DISCRETE`: Eventos atómicos, procesados individualmente.
    *   `MERGEABLE`: Eventos que pueden ser concatenados (ej: fragmentos de un mismo diálogo o log).
    *   `AGGREGATABLE`: Eventos cuya frecuencia o cantidad es importante (ej: conteo de eventos de un tipo).
    *   `STATE`: Representan el estado más reciente de algo, solo la última versión importa.
    *   `USER`: Eventos generados directamente por la entrada del usuario.
*   **Registro y Caching:** Los sensores se registran (`registerSensor`) y su información se cachea. Las estadísticas (`SensorStatistics`) y la cola de eventos (`deliveryQueue`, `stateMap`) se persisten en un archivo (`sensors.json`) para recuperar el estado al reiniciar.
*   **Procesamiento:** El `SensorsServiceImpl` mantiene un bucle de despacho (`eventDispatcher`) que consume eventos de los sensores.
    *   Los eventos de `USER` se añaden directamente a la `Session` del agente.
    *   Los eventos de `Sensor` (no `USER`) se envuelven en `ToolExecutionResultMessage` simulando una llamada a la herramienta `pool_event` y se persisten en la `SourceOfTruth` como un `Turn` de tipo `tool_execution`.
    *   La `deliveryQueue` y el `stateMap` se utilizan para gestionar los eventos pendientes que esperan ser consumidos por el ciclo de conversación principal del agente.
*   **Silenciamiento:** Los sensores pueden ser silenciados (`setSilenced`), impidiendo que sus eventos lleguen a la cola de conversación pero contando sus estadísticas. Esto es útil para controlar el "ruido" de sensores menos relevantes.

### Percepción Temporal

La percepción temporal se implementa de varias maneras:

1.  **Timestamps en `Turn` y `CheckPoint`:** Cada interacción y cada punto de guardado tienen marcas de tiempo (`Timestamp`) que permiten ordenar cronológicamente los eventos.
2.  **`lastInteractionTime` en `Session`:** La clase `Session` registra cuándo fue la última interacción con el usuario.
3.  **Eventos de `SYSTEMCLOCK`:** Si la diferencia entre la hora actual y `lastInteractionTime` supera un umbral (ej. 1 hora), el `SensorsService` genera un evento artificial del tipo `SYSTEMCLOCK` (naturaleza `DISCRETE`) que se inyecta en la conversación. Este evento informa al agente sobre el paso del tiempo y es procesado como un `ChatMessage` especial.
4.  **`ScheduleAlarmTool`:** Permite programar eventos futuros que se dispararán en un momento específico, también a través de eventos sensoriales.

### Indexación de Documentos

El proceso de indexación de documentos es una funcionalidad robusta gestionada por `DocumentsServiceImpl` y orquestada por `DocumentStructureExtractor`.

1.  **Extracción de Estructura (`extract-structure.md`):** Cuando se encuentra un nuevo documento (vía `document_index` o procesamiento interno), se utiliza un LLM con un prompt específico (`prompts/documents/extract-structure.md`) para analizar el contenido del archivo (previamente convertido a CSV línea por línea). El objetivo es determinar la estructura jerárquica del documento (títulos, niveles, rangos de líneas, parentesco) y generar una representación en formato CSV.
2.  **Resumen y Categorización (`sumary-and-categorize.md`):** Para cada sección identificada en la estructura, se utiliza un segundo LLM (o el mismo con otro prompt) para generar un resumen conciso y extraer categorías relevantes.
3.  **Persistencia:** La estructura extraída (incluyendo metadatos, resúmenes y categorías) se guarda como un archivo `.struct` junto al documento original. Además, la información clave (ID, título, resumen, categorías, ruta y vector de embedding del resumen) se persiste en la base de datos H2 (`DOCUMENTS` table).
4.  **Búsqueda:** El `DocumentsServiceImpl` soporta varias formas de búsqueda:
    *   `search`: Búsqueda híbrida que combina filtros SQL por categorías con búsqueda semántica vectorial en los resúmenes.
    *   `searchByCategories`: Filtra por categorías exactas usando SQL.
    *   `searchBySummaries`: Busca por similitud semántica en los resúmenes (utilizando embeddings y búsqueda vectorial).
5.  **Recuperación:** Las herramientas `getDocumentStructure` y `getPartialDocument` permiten al agente consultar el índice del documento (en formato XML) y luego obtener el contenido de secciones específicas, respectivamente.

### Gestión de la Seguridad

La seguridad es un aspecto crucial del diseño del agente, abordado desde múltiples frentes:

1.  **Restricción de Acceso al Sistema de Ficheros (`AgentAccessControlImpl`):**
    *   **Sandbox Central:** Las operaciones de archivo se restringen a un directorio raíz (`rootPath`, típicamente el directorio del proyecto).
    *   **Whitelist de Rutas Externas:** Se pueden definir rutas externas explícitamente permitidas (`allowed_external_paths`) para acceder a recursos fuera del sandbox (ej. carpetas compartidas).
    *   **Blacklists:** Se definen rutas que no se pueden escribir (`nom_writable_paths`) o ni siquiera leer (`nom_readable_paths`).
    *   **Protección contra Path Traversal:** Se valida que cualquier ruta accedida esté contenida dentro del `rootPath` o en la lista blanca.
    *   **Restricciones Específicas:** Se prohíbe explícitamente escribir en archivos que terminen en `,jv` (copias de respaldo), dentro de directorios `.git`, o sobre archivos del sistema (`pom.xml`).
    *   **Validación de URLs:** Se aplica un filtrado básico en `AgentAccessControlImpl.isAccessible(URI)` para permitir solo URLs locales o en redes privadas conocidas.

2.  **Confirmación por el Usuario de Operaciones de Escritura:** Para cualquier herramienta marcada con `MODE_WRITE` (escritura, ejecución de comandos, modificación de archivos), el `ConversationServiceImpl` intercepta la llamada y solicita explícitamente la confirmación del usuario a través de la consola o la GUI antes de proceder.

3.  **Uso de CI Automático (`CheckinOptions` en `FileWriteTool`, `FilePatchTool`, `FileSearchAndReplaceTool`):** Antes de modificar un archivo, el agente intenta realizar un `checkin` automático a través de JavaRCS. Esto crea una revisión previa del archivo, permitiendo revertir cambios si es necesario. Si el archivo no está bajo control de versiones, se inicializa la gestión de RCS.

4.  **Sandbox de Shell (`ShellExecuteTool`):**
    *   **Firejail:** El agente intenta utilizar `firejail` para aislar la ejecución de comandos shell. `firejail` restringe el acceso al sistema de archivos (`--private` para el home, `--whitelist` para el directorio del proyecto, `--blacklist` para directorios sensibles como `data/`).
    *   **No Interactividad:** Los comandos ejecutados no son interactivos para evitar bloqueos o la necesidad de entrada del usuario. Se fomentan flags como `-y` o `--batch`.
    *   **Confirmación de Tareas Largas:** Para tareas que exceden un umbral de tiempo, el agente solicita confirmación periódica al usuario, quien puede abortar la operación.
    *   **Gestión de Salida:** La salida de comandos extensos se redirige a archivos temporales (`.out`), con un mecanismo de higiene para eliminar los antiguos y paginación para leerlos (`shell_read_output` + `file_read`).

5.  **Filtrado de Fuentes Sensoriales:** Los servicios como `EmailService` y `TelegramService` solo procesan eventos provenientes de remitentes o chats autorizados, actuando como un firewall para la entrada de información.

---

## Flujos en el Conversation Manager (`ConversationServiceImpl`)

El `ConversationServiceImpl` es el corazón del agente, orquestando el ciclo de razonamiento y acción. Sus flujos principales son:

1.  **Ciclo de Consciencia (eventDispatcher):**
    *   El agente se mantiene en un bucle infinito (`while (this.isRunning())`) escuchando eventos de los `SensorsService`.
    *   Recupera el próximo evento disponible (`sensors.getEvent()`).
    *   Si el evento es de tipo `USER`, su contenido se añade directamente a la `Session` como `UserMessage`.
    *   Si el evento es de tipo `Sensor` (no `USER`), se simula una llamada a la herramienta `pool_event` para que el agente "descubra" el evento, se registra en la `SourceOfTruth` y se añade a la `Session` como un `ToolExecutionResultMessage`.
    *   Una vez que hay un evento de usuario o un evento sensorial procesado que requiere una respuesta, el agente entra en un bucle de razonamiento interno (`while (!turnFinished)`).
    *   **Generación de Respuesta:**
        *   Se construye el contexto actual (`session.getContextMessages`) que incluye el prompt del sistema, el historial de la sesión y el último `CheckPoint` si existe.
        *   Se llama al `model.generate()` pasando el contexto y las especificaciones de las herramientas disponibles.
        *   El LLM devuelve una `AiMessage`, que puede contener:
            *   **Texto plano:** La respuesta final del agente. El turno se guarda en `SourceOfTruth` y se añade a la sesión. El bucle de razonamiento termina.
            *   **`ToolExecutionRequest`:** El agente debe ejecutar la herramienta solicitada. El resultado de la herramienta se guarda como un `Turn` (`tool_turn`) y se añade a la sesión como `ToolExecutionResultMessage`. Luego, el bucle de razonamiento continúa para que el LLM procese el resultado de la herramienta.
    *   **Compactación de Memoria:** Después de cada turno de conversación completo, se verifica si la sesión necesita compactación (`session.needCompaction()`). Si es así, se llama a `performCompaction()`.
    *   **Callback:** Si el evento original tenía un `SensorEventCallback`, se llama a `onComplete` con la respuesta final del agente.

2.  **Proceso de Compactación (`performCompaction`):**
    *   Se obtienen las marcas de sesión (`getOldestMark`, `getCompactMark`) para definir el rango de turnos a consolidar.
    *   Se recuperan los `Turn` correspondientes de la `SourceOfTruth`.
    *   El `MemoryServiceImpl` genera un nuevo `CheckPoint` a partir de estos `Turn` y el `activeCheckPoint` anterior.
    *   El nuevo `CheckPoint` se persiste en `SourceOfTruth`.
    *   Se limpian los `Turn` compactados de la `Session`.
    *   Se actualiza el `activeCheckPoint` del agente.

3.  **Gestión de Herramientas:**
    *   Las herramientas se registran inicialmente (`addTool`) y se pueden activar/desactivar a través de la configuración (`REFRESH_CONVERSATION_TOOLS`).
    *   Cuando el LLM solicita una herramienta (`ToolExecutionRequest`), el `ConversationServiceImpl` valida la autorización del usuario (para herramientas de escritura/ejecución) y luego ejecuta la herramienta correspondiente a través de su método `execute()`.
    *   El resultado de la herramienta se captura y se integra de nuevo en el ciclo de conversación.

---

## Conclusión

Noema Agent se presenta como un proyecto ambicioso y bien estructurado para un agente de software personal. La arquitectura modular, la clara separación de responsabilidades entre servicios, y el uso de patrones como la "Fuente de Verdad" y la memoria escalonada (turnos -> checkpoints) demuestran una reflexión profunda sobre los desafíos de mantener la coherencia y la utilidad de un agente conversacional a lo largo del tiempo.

La integración de herramientas para interactuar con el sistema de archivos, la red y el propio LLM, junto con un sistema de percepción sensorial para eventos asíncronos, le confiere una notable versatilidad. La atención a la seguridad, especialmente en la ejecución de comandos shell y el acceso a archivos, es un punto fuerte del diseño.

Si bien el proyecto está etiquetado como "experimental" y "personal", la profundidad de su implementación sugiere un considerable esfuerzo de desarrollo y un buen entendimiento de los principios de ingeniería de agentes basados en LLMs. La coexistencia de interfaces GUI y CLI amplía su accesibilidad.

---
