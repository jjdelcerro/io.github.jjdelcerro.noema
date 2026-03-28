
### Metadatos del Informe
*   **Proyecto Analizado:** Noema
*   **Versión Analizada:** 0.1.0 (según `pom.xml` y `AgentManager`)
*   **Fecha de Análisis:** Marzo de 2025 *(Fecha actual de generación)*
*   **Autor del Informe:** Gemini (IA), basado en la inspección estática del código fuente.

### Visión General

**Noema** es un agente conversacional autónomo diseñado como un proyecto personal y experimental. Su objetivo principal es actuar como un asistente de investigación y reflexión de larga duración, manteniendo una **sesión continua y única** a lo largo del tiempo. No está orientado específicamente al desarrollo de software, sino a la asistencia general en entornos de conocimiento e investigación.

El diseño destaca por un fuerte pragmatismo arquitectónico: evita la dependencia de infraestructuras complejas (como bases de datos vectoriales dedicadas o servicios en la nube de terceros aparte de las APIs de los LLM). Emplea un enfoque local "standalone" encapsulado en un único archivo ejecutable (Fat JAR), utilizando bases de datos embebidas (H2), cálculo de embeddings en la misma máquina virtual y herramientas de control de versiones escritas íntegramente en Java.


### Stack Tecnológico

El proyecto está construido sobre un ecosistema Java moderno y dependencias ligeras orientadas a la autonomía del aplicativo:

*   **Lenguaje:** Java 21.
*   **Gestión de Dependencias y Build:** Maven (con `maven-shade-plugin` para empaquetado).
*   **Integración con IA:** `LangChain4j` (Framework principal), `langchain4j-open-ai` (para compatibilidad con APIs tipo OpenAI/OpenRouter/Groq).
*   **Embeddings Locales:** `langchain4j-embeddings-all-minilm-l6-v2` (Modelo de vectorización en memoria, elimina la necesidad de servicios externos).
*   **Persistencia de Datos:** `H2 Database` (Motor SQL embebido) y persistencia plana en ficheros (Markdown, JSON vía `Gson`).
*   **Procesamiento de Archivos:** `Apache Tika` (Extracción de texto), `Natty` (Parseo de fechas en lenguaje natural).
*   **Control de Versiones y Diff:** Implementaciones nativas en Java ([JavaRCS](https://github.com/jjdelcerro/io.github.jjdelcerro.javarcs) y `java-diff-utils`).
*   **Interfaz de Usuario (Dual):**

    *   *Gráfica (Swing):* `FlatLaf` (Look & Feel moderno), `MigLayout`, `RSyntaxTextArea` (Editor de código), renderizado de Markdown nativo.
    *   *Consola (CLI):* `JLine3` (Terminal interactiva con soporte multilínea y atajos).
    
*   **Comunicaciones:** `java-telegram-bot-api` y `jakarta.mail`.


### Estructura de Paquetes e Interfaces/Implementación

El proyecto sigue una estricta segregación entre la definición de contratos (Interfaces) y sus implementaciones, facilitando la inyección de dependencias y el reemplazo de piezas funcionales:

*   **`io.github.jjdelcerro.noema.lib`**: Contiene la API pública del sistema. Aquí residen contratos clave como `Agent`, `AgentService`, `AgentTool`, `SourceOfTruth`, y las interfaces de los distintos servicios (`ReasoningService`, `MemoryService`, `SensorsService`, etc.).
*   **`io.github.jjdelcerro.noema.lib.impl`**: Contiene las implementaciones concretas de la API (POJOs y Lógica de Negocio). Se subdivide funcionalmente en paquetes como `services` (y dentro de este, `reasoning`, `memory`, `sensors`, `documents`, etc.), `persistence` y `settings`.
*   **`io.github.jjdelcerro.noema.ui`**: Contiene las interfaces e implementaciones de las capas de presentación (`swing`, `console`, `common`).
*   **`io.github.jjdelcerro.noema.main`**: Puntos de entrada de la aplicación (`Main`, `MainGUI`, `MainConsole`, `BootUtils`).


### Arquitectura y Diseño

El sistema está diseñado en capas concéntricas, desde el motor central de orquestación hasta los servicios periféricos de interacción con el mundo.

#### 1. El Kernel (o Core)
*   **`Agent` y `AgentManager`**: `AgentManager` actúa como factoría y Service Locator, registrando y construyendo los componentes. `Agent` es el objeto principal que mantiene el estado, provee acceso a los servicios, la configuración, la persistencia y encapsula la interacción directa con los LLMs (creación de `ChatModel`).
*   **Ciclo de Vida**: Los servicios implementan `AgentServiceFactory` para definir si pueden arrancar basándose en la configuración actual. El ciclo de vida (`start()`, `stop()`) es gestionado por `AgentImpl`, asegurando que hilos de fondo y conexiones de red se abran y cierren limpiamente (apoyado en un Shutdown Hook de la JVM).
*   **Infraestructura de Datos**: Se utiliza H2 Database particionada en dos archivos: `memory` (para el `SourceOfTruth` de la conversación) y `service` (para tareas operativas como `Scheduler` y `Documents`). La clase `SQLProvider` aísla las consultas SQL. Los vectores se almacenan como campos BLOB en H2.
*   **Topología de Archivos (`noema-agent`)**: El entorno de trabajo se estructura jerárquicamente:

    *   `var/config/`: Ficheros de configuración (`settings.json`, properties de dominios).
    *   `var/lib/`: Bases de datos H2 y logs CSV.
    *   `var/cache/` y `var/tmp/`: Archivos temporales y extracciones de Tika.
    *   `var/log/`: Archivos de traza de Log4j2.
    *   `home/`: Entorno de ejecución seguro (sandbox) para comandos de terminal.
    *   `var/skills/`, `var/identity/`: Ficheros Markdown que definen la personalidad y capacidades técnicas.

#### 2. Capacidades Horizontales (Cross-cutting Concerns)
*   **Seguridad y Control de Acceso (`AgentAccessControl`)**: Regula estrictamente a qué partes del sistema de ficheros puede acceder el agente mediante listas blancas y negras definidas en la configuración (`allowed_external_paths`, `nom_writable_paths`, `nom_readable_paths`).
*   **Gestión de Rutas (`AgentPaths`)**: Abstracción que resuelve rutas relativas asegurando que el agente trabaje siempre dentro de su Workspace, gestionando la dualidad entre rutas de proyecto locales y configuraciones globales de usuario (`~/.noema-agent`).
*   **Sistema de Configuración Jerárquica (`AgentSettings`)**: Implementado como un árbol de nodos (`AgentSettingsGroup`, `AgentSettingsString`, etc.) serializable a JSON mediante adaptadores custom de Gson. Permite definir la configuración de forma dinámica, vinculando la lógica con la interfaz gráfica (`settingsui.json`).

#### 3. Servicios de Periferia
*   **[SensorsService](docs/sensors-service.md)**: Punto de entrada de todos los estímulos externos (asíncronos). Gestiona colas de mensajes y prioriza qué evento debe ser atendido primero por el razonamiento central.
*   **`SchedulerService`**: Servicio de cronograma persistido en base de datos. Permite programar alarmas que, al vencer, inyectan un evento en el sistema de sensores.
*   **`EmailService` / `TelegramService`**: Puentes de comunicación bidireccional. Escuchan pasivamente (IMAP IDLE o Long Polling) inyectando notificaciones como eventos sensoriales, y exponen herramientas (`email_send`, `telegram_send`) para emitir respuestas.
*   **`DocumentsService`**: Motor RAG (Retrieval-Augmented Generation) especializado. Ingesta documentos, extrae su estructura jerárquica (TOC), resume secciones y las indexa vectorialmente en H2 para búsquedas semánticas posteriores.

#### 4. Servicios Cognitivos
El "motor de pensamiento" se divide en dos servicios que comparten el `SourceOfTruth` (la persistencia del historial):

*   **[ReasoningService](docs/reasoning-service.md) (Orquestación del pensamiento)**: Mantiene el bucle perpetuo de evaluación (`eventDispatcher`). Recibe eventos, actualiza el contexto a corto plazo (`Session`), invoca al modelo de lenguaje, ejecuta las peticiones de herramientas (`ToolExecutionRequest`) y decide si la sesión actual requiere ser empaquetada.
*   **`MemoryService` (Consolidación histórica)**: Encargado de la memoria a largo plazo. Cuando el contexto actual es demasiado grande, este servicio toma el historial reciente y el resumen histórico anterior, y mediante un LLM, genera un nuevo `CheckPoint` consolidado (un "Resumen" y una narrativa llamada "El Viaje").


### Herramientas del Agente (Agent Tools)

Las herramientas son clases que implementan `AgentTool` y exponen metadatos (JSON Schema) para el Function Calling del LLM.

**Gestión de Sistema y Memoria:**

*   `pool_event`: Consulta la cola de eventos sensoriales pendientes (mecanismo de inyección pasiva).
*   `lookup_turn`: Recupera un evento/turno específico de la base de datos usando su ID (ej. `ID-123`).
*   `search_full_history`: Busca semánticamente en el historial conversacional consolidado usando vectores.
*   `schedule_alarm`: Programa una notificación futura en el `SchedulerService`.
*   `sensor_status` / `sensor_stop` / `sensor_start`: Permiten al agente inspeccionar y mutar el estado de sus propios sensores (silenciar canales ruidosos temporalmente).

**Identidad y Habilidades (Skills):**

*   `list_skills`: Enumera los manuales procedimentales disponibles en el directorio `var/skills`.
*   `load_skill`: Carga el contenido completo de un protocolo técnico específico.
*   `consult_environ`: Recupera conocimiento denso del entorno o del usuario desde `var/identity/environ`.

**Operaciones sobre Archivos (File System):**

*   `file_find`: Busca archivos usando patrones *glob* (ej. `*.java`).
*   `file_grep`: Busca contenido de texto (o regex) dentro de archivos.
*   `file_read` / `file_read_selectors`: Leen el contenido de archivos. Soportan paginación para evitar saturar el contexto del LLM.
*   `file_write`: Crea o sobrescribe un archivo completo.
*   `file_patch`: Aplica cambios en formato *Unified Diff*.
*   `file_search_and_replace`: Busca un bloque de texto exacto y lo sustituye por otro.
*   `file_mkdir`: Crea directorios en el sistema de ficheros.
*   `file_extract_text`: Usa Apache Tika para extraer texto de archivos binarios/complejos (PDFs, DOCX).
*   `file_history`: Muestra el historial de revisiones ([RCS](https://github.com/jjdelcerro/io.github.jjdelcerro.javarcs) `rlog`) de un archivo.
*   `file_recovery`: Recupera (checkout) una versión antigua de un archivo desde el control de versiones local [RCS](https://github.com/jjdelcerro/io.github.jjdelcerro.javarcs).

**Ejecución de Comandos:**

*   `shell_execute`: Ejecuta comandos de terminal de forma no interactiva. Si el sistema tiene `firejail` instalado, envuelve el comando en un entorno seguro limitando accesos.
*   `shell_read_output`: Lee la salida estándar de comandos de larga duración que ha sido capturada en ficheros temporales.

**Interacción con Internet y APIs:**

*   `web_search`: Realiza búsquedas en internet (vía Brave Search API).
*   `web_get_content` / `web_get_content (Tika)`: Descargan y sanean el contenido de una URL dada.
*   `get_current_location`: Determina la ubicación aproximada del entorno base vía IP.
*   `get_weather`: Consulta la previsión meteorológica vía Open-Meteo.
*   `get_current_time`: Obtiene la hora del sistema (útil para la percepción temporal y el `Scheduler`).

**Comunicaciones:**

*   `email_list_inbox`: Lee las cabeceras de los últimos correos recibidos.
*   `email_read`: Descarga el contenido completo de un correo específico vía UID.
*   `email_send`: Redacta y envía un correo electrónico.
*   `telegram_send`: Envía un mensaje push al usuario vía Telegram.

**Documentos y RAG:**

*   `document_index`: Inicia el flujo de procesamiento de un documento nuevo.
*   `document_search` / `document_search_by_categories` / `document_search_by_sumaries`: Diferentes filtros para localizar manuales indexados.
*   `get_document_structure`: Obtiene el índice (TOC) generado de un documento.
*   `get_partial_document`: Carga secciones específicas de un documento basándose en los identificadores obtenidos en la estructura.

### Descripción de Mecanismos Principales

#### Gestión de Memoria (Sesión vs Turnos vs Puntos de Guardado)
El agente gestiona el tiempo y el contexto en tres niveles para lograr continuidad sin depender de ventanas de token ilimitadas:

1.  **Session (Corto Plazo)**: Mantiene los mensajes LangChain4j inmediatos en memoria RAM (`Session.java`).
2.  **Turnos (Registro Atómico)**: Cada interacción (hablar, usar una herramienta) genera un `Turn` inmutable. Este turno es guardado en la tabla SQL `turnos` con un ID único (`ID-123`) y se calcula su vector de *embedding*.
3.  **CheckPoints (Consolidación a Largo Plazo)**: Cuando la sesión alcanza un límite configurable (ej. 40 turnos), `ReasoningService` dispara la compactación. `MemoryService` le pide al LLM que lea el CheckPoint anterior y los nuevos turnos (formato CSV) para redactar una narrativa continuada ("El Viaje") y un resumen. La `Session` se limpia parcialmente, manteniendo solo la memoria consolidada en el *System Prompt* y la capacidad de hacer `lookup_turn` a eventos antiguos.

#### Gestión de la Identidad y Entorno
El sistema utiliza ficheros Markdown para inyectar contexto. La identidad se fragmenta:

*   **Core (`var/identity/core`)**: Habilidades estáticas y constitucionales (ej. directrices éticas, stack técnico preferido). Se inyectan directamente en el prompt del sistema si están activados en el JSON de configuración.
*   **Entorno (`var/identity/environ`)**: Utiliza un patrón de "carga perezosa" (Lazy Loading). Los archivos `.ref.md` contienen *anclas* ligeras que se incluyen en el prompt. Si el agente detecta que un tema del entorno es relevante, usa la herramienta `consult_environ` para cargar el archivo `.md` completo (conocimiento denso) sin saturar el prompt por defecto.

#### Gestión de Habilidades (Skills)
Mismo patrón que el entorno pero enfocado a tareas de procedimientos (`var/skills`). El agente usa `list_skills` para obtener un inventario de tareas procedimentales (extrayendo el nombre de los ficheros) y, cuando requiere realizar un flujo complejo de pasos, invoca `load_skill` para cargar las instrucciones precisas en su contexto temporal.

#### Gestión de Eventos y el "Engaño del Protocolo"
Los LLMs operan en un flujo sincrónico (Usuario -> IA -> Tool -> IA), pero el entorno real es asíncrono (llega un correo en cualquier momento). Para solucionar esto, `SensorsService` acumula eventos y despierta el hilo principal. Cuando el agente despierta, el código Java inyecta un `ToolExecutionRequest` simulado solicitando la herramienta `pool_event`, y le pasa el evento sensorial como `ToolExecutionResultMessage`. De este modo, el LLM cree que *él mismo* decidió mirar los sensores, manteniendo la integridad estructural de la historia del chat. Los sensores soportan naturalezas de agrupamiento (ej. acumular múltiples notificaciones similares antes de despacharlas).

#### Percepción Temporal
El agente tiene consciencia del paso del tiempo de forma pasiva. En la clase `Session`, al evaluar los mensajes para enviar al LLM, si la diferencia entre la última interacción y la actual es mayor a 1 hora, inyecta dinámicamente un evento de sensor (`SYSTEMCLOCK_SENSOR_NAME`). El modelo recibe un estímulo tipo: *"Ha pasado [tiempo] desde la última interacción..."*, permitiéndole reaccionar de manera temporalmente coherente tras largos periodos de inactividad.

#### Indexación de Documentos
La ingesta de documentos (`DocumentStructureExtractor`) se ejecuta en un hilo separado.

1. Extrae el texto con Tika y lo convierte a formato CSV basado en líneas.
2. Invoca a un LLM "Razonador" para analizar el CSV y extraer la jerarquía del documento (TOC/Índice).
3. Invoca a un LLM "Básico/Económico" iterativamente para leer cada sección del documento, generar un resumen de dos párrafos y asignarle categorías.
4. El resumen se vectoriza y se guarda en H2 junto al índice JSON en el disco, permitiendo búsquedas híbridas (vectoriales + filtro SQL de categorías).

#### Gestión de la Seguridad
*   **Control de Acceso (VFS Sandbox)**: Toda ruta (`path`) que el agente intenta leer o escribir pasa por `AgentAccessControlImpl`. Verifica manipulaciones como `../` e impide salir del Workspace a menos que la ruta esté en una `whitelist`. Prohíbe explícitamente escrituras en ficheros de control (ej. carpetas `.git`).
*   **Confirmación de Usuario**: Herramientas marcadas con `MODE_WRITE` o `MODE_EXECUTION` (modificar archivos, ejecutar shell) requieren obligatoriamente confirmación interactiva en la interfaz gráfica/consola (`console.confirm()`) antes de aplicarse.
*   **[RCS](https://github.com/jjdelcerro/io.github.jjdelcerro.javarcs) Automático (Control de Versiones)**: Previo a cualquier modificación destructiva (`file_write`, `file_patch`, `file_search_and_replace`), el sistema llama a `CheckinOptions` de la librería [JavaRCS](https://github.com/jjdelcerro/io.github.jjdelcerro.javarcs). Esto realiza un *commit* de la versión actual del archivo localmente, permitiendo al agente invocar `file_recovery` si la modificación introdujo errores.

#### Flujos en el Conversation Manager (ReasoningService)
El `eventDispatcher` es un bucle infinito que:

1. Pide un evento al `SensorsService` (bloqueante si no hay).
2. Agrega el evento a la `Session`.
3. Inicia un sub-bucle `while(!turnFinished)`.
4. Invoca al LLM enviando el contexto.
5. Si el LLM devuelve un *Tool Call*, ejecuta la herramienta (solicitando confirmación humana si procede), guarda el resultado como un `Turn` histórico de tipo herramienta, y continúa el bucle.
6. Si el LLM devuelve *Texto*, lo envía a la consola del usuario, guarda un `Turn` de tipo chat, finaliza el turno, y evalúa si toca compactar la memoria.


### Construcción y Despliegue

La aplicación está diseñada para ser extremadamente portable:

*   **Build:** Construida vía Maven, utiliza el `maven-shade-plugin` para compilar un "Fat JAR" que engloba todas las dependencias (LangChain4j, H2, FlatLaf, etc.), asegurando la inclusión correcta de metadatos SPI (`ServicesResourceTransformer`).
*   **Despliegue:** No requiere Docker, ni contenedores externos, ni bases de datos instaladas (H2 corre embebida y crea sus propios ficheros en el Workspace). Solo es necesario tener instalado Java 21.
*   **Ejecución:** Cuenta con una clase `Main` que sirve de despachador. Si se ejecuta sin parámetros, levanta la interfaz gráfica en Swing (`MainGUI`). Si se lanza con el flag `-c`, inicializa la interfaz de terminal interactiva con JLine3 (`MainConsole`). Toda la configuración inicial de URLs de LLM y API Keys se realiza mediante asistentes en la propia aplicación la primera vez que arranca.

### Conclusión

**Noema** representa una implementación fascinante y altamente pragmática del concepto de "Agente Autónomo de Reflexión Continua". En lugar de apoyarse en arquitecturas complejas y de alto coste (como servidores dedicados a bases vectoriales), implementa soluciones elegantes en software puro (búsqueda de coseno en memoria/SQL, [RCS en Java](https://github.com/jjdelcerro/io.github.jjdelcerro.javarcs)) y delega inteligentemente la carga cognitiva en la interacción asíncrona.

El diseño del sistema de memoria (Turnos atómicos -> Compactación narrativa -> Flashbacks vía `lookup_turn`) solventa con gran acierto el problema de las ventanas finitas de tokens de los LLM actuales. Además, el aislamiento por capas de la identidad, las habilidades, y su sistema de inyección de contexto "bajo demanda" demuestran un enfoque altamente optimizado, lo cual lo convierte en una herramienta sólida y segura para investigación y conversación prolongada a nivel personal.
