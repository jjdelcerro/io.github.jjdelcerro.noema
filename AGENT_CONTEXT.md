
### Metadatos del Informe
*   **Proyecto Analizado:** Noema
*   **Versión Analizada:** 0.1.0 (según `pom.xml` y `AgentManager`)
*   **Fecha de Análisis:** Mayo de 2024 *(Fecha actual de generación)*
*   **Autor del Informe:** Gemini (IA), basado en la inspección estática del código fuente.

---

### 1. Visión General

**Noema** es un agente de Inteligencia Artificial diseñado como un proyecto personal de investigación y experimentación. Su propósito principal es servir como un "compañero cognitivo" para el análisis, la reflexión de larga duración y la asistencia en tareas generales, alejándose del paradigma tradicional del "asistente de programación rápido".

La arquitectura de Noema destaca por su enfoque pragmático y autocontenido. Está diseñado para mantener una **única sesión continua a lo largo del tiempo**. Para lograr esto sin agotar los límites técnicos de los modelos de lenguaje, el sistema implementa mecanismos sofisticados de consolidación histórica, paginación de información y recuperación semántica. 

Bajo la premisa de requerir "cero infraestructura externa" (Zero-Infra), el proyecto encapsula todo su estado y capacidades vectoriales en un motor de base de datos embebido (H2) y modelos de *embeddings* locales, requiriendo únicamente la disponibilidad de la máquina virtual de Java (JVM) y acceso a las APIs de los LLMs.

### 2. Stack Tecnológico

El proyecto está construido sobre el ecosistema Java moderno, utilizando un conjunto de bibliotecas seleccionadas por su portabilidad y ligereza:

*   **Lenguaje:** Java 21 (indicado en el `pom.xml` vía `maven.compiler.target`).
*   **Orquestación LLM:** `dev.langchain4j` (Core, OpenAI API adapter para compatibilidad con OpenRouter/Groq/Chutes).
*   **Embeddings Locales:** `langchain4j-embeddings-all-minilm-l6-v2` (ejecución de vectores en memoria sin APIs externas).
*   **Persistencia y Vectores:** Base de datos **H2** en modo embebido (`AUTO_SERVER=TRUE`).
*   **Procesamiento de Documentos:** `Apache Tika` para la ingesta de PDFs, DOCX, HTML, etc.
*   **Interfaces de Usuario:**
    *   *Gráfica (GUI):* `FlatLaf` (Look and Feel oscuro), `MigLayout`, `RSyntaxTextArea` (para el editor de texto incorporado). Renderizado Markdown con `commonmark`.
    *   *Consola (CLI):* `JLine3` (Terminal, soporte multilínea, historial).
*   **Integraciones:** `java-telegram-bot-api`, `jakarta.mail` (IMAP/SMTP), `Natty` (parseo de fechas en lenguaje natural).
*   **Control de Versiones (Interno):** Implementación Java pura de Diff (`java-diff-utils`) y RCS (`io.github.jjdelcerro.javarcs`) para copias de seguridad automáticas previas a la edición.

### 3. Estructura de Paquetes y Diseño

El código sigue un patrón estricto de separación entre interfaces (contratos) e implementaciones, facilitando la inyección de dependencias y el bajo acoplamiento.

*   `io.github.jjdelcerro.noema.lib`: Contiene las **interfaces core** (`Agent`, `AgentTool`, `AgentService`, `SourceOfTruth`, `CheckPoint`, `Turn`). Define el contrato del sistema.
*   `io.github.jjdelcerro.noema.lib.impl`: Implementaciones concretas de las interfaces. Aquí residen los motores principales (`ReasoningServiceImpl`, `MemoryServiceImpl`, `SensorsServiceImpl`).
*   `io.github.jjdelcerro.noema.lib.impl.services.*`: Paquetes modulares por servicio (documentos, email, embeddings, memory, reasoning, scheduler, sensors, telegram). Cada uno provee su propia factoría y herramientas.
*   `io.github.jjdelcerro.noema.lib.settings`: Sistema jerárquico de configuración basado en JSON.
*   `io.github.jjdelcerro.noema.ui`: Abstracción de la interfaz de usuario (`AgentUIManager`, `AgentConsole`).
*   `io.github.jjdelcerro.noema.ui.swing` / `ui.console`: Implementaciones específicas para el modo ventana y el modo terminal.
*   `io.github.jjdelcerro.noema.main`: Puntos de entrada (`Main`, `MainGUI`, `MainConsole`, `BootUtils`).

### 4. Arquitectura y Diseño

La arquitectura es de tipo **Monolito Modular Basado en Servicios**. El nodo central es la interfaz `Agent`, que actúa como un *Facade* para el resto de los subsistemas.

1.  **AgentManager & Locator:** Siguen un patrón Service Locator para registrar e inicializar factorías (`AgentServiceFactory`).
2.  **Servicios (AgentService):** Cada gran bloque funcional es un servicio con su propio ciclo de vida (`start()`, `stop()`), que aporta un conjunto de herramientas (`AgentTool`) al razonamiento del agente.
3.  **Source of Truth:** Repositorio central basado en H2. Es el único punto donde se escribe el historial (Turnos y Checkpoints).
4.  **Bucle de Eventos (Event Dispatcher):** Situado en `ReasoningServiceImpl`, es un hilo perpetuo que extrae eventos de los sensores (o mensajes del usuario) y dispara el ciclo de generación del LLM.

### 5. Descripción Detallada de los Mecanismos Principales

#### 5.1. Gestión de Memoria (Sesión vs. Persistencia vs. Puntos de Guardado)
El agente gestiona una **única línea temporal persistente**, evitando el concepto de "sesiones descartables". Se divide en tres capas:

*   **Sesión Activa (Contexto a corto plazo):** Mantenida en memoria por la clase `Session` (y respaldada en `active_session.json`). Es una ventana deslizante de objetos `ChatMessage`.
*   **Persistencia de Turnos (SourceOfTruth):** Cada interacción (usuario, pensamiento del modelo, llamada a herramienta, resultado) se guarda de forma inmutable en la tabla `turnos` de H2 como un objeto `Turn`. A cada turno se le asocia automáticamente un vector semántico (Embedding) generado sobre su contenido textual.
*   **Puntos de Guardado (CheckPoints - Consolidación a largo plazo):** 
    Cuando la sesión activa alcanza un umbral de turnos (ej. 40 turnos), se dispara el proceso de **Compactación**. El `MemoryServiceImpl` invoca a un LLM secundario enviándole el historial reciente en formato CSV y el Checkpoint anterior. El LLM genera un nuevo documento con dos partes:
    1.  *Resumen Factual:* Decisiones y estado de proyectos.
    2.  *El Viaje:* Una narrativa detallada que conecta el pasado con el presente, manteniendo citas directas `{cite:ID-xxx}` a los turnos originales de la base de datos.
    Tras esto, los mensajes antiguos se eliminan de la `Session` activa, liberando tokens, pero su resumen narrativo se inyecta permanentemente como contexto del sistema.

#### 5.2. Gestión de la Identidad y del Entorno
La personalidad y el contexto del agente no están "hardcodeados", sino que se inyectan dinámicamente en el *System Prompt*.
*   **Identidad Core (Constitución):** Archivos Markdown en `var/identity/core/` (activables vía UI) que definen el "ADN técnico" y las reglas operativas (ej. frameworks preferidos, estilo de codificación).
*   **Consciencia de Entorno (Memoria Virtual):** Archivos `.ref.md` en `var/identity/environ/`. Funcionan como un "índice" de lo que el agente *sabe que sabe*. No contienen el conocimiento denso, sino punteros. Si el agente detecta que un tema del usuario coincide con una referencia, utiliza la herramienta `consult_environ` para cargar el contenido detallado de esa faceta de su identidad.

#### 5.3. Gestión de Habilidades (Skills)
Implementa un modelo de "Memoria Procedimental" bajo demanda.
En lugar de cargar todos los manuales de procedimientos en el contexto inicial (lo cual saturaría al modelo), el agente posee la herramienta `list_skills` para ver su catálogo de manuales disponibles. Si se le pide, por ejemplo, "desplegar el proyecto", consultará el catálogo y usará `load_skill` para inyectar en su contexto actual el archivo `.md` con las instrucciones precisas para esa tarea.

#### 5.4. Gestión de Eventos y Sensores
La arquitectura implementa un sofisticado sistema para soportar **proactividad y asincronía** en un modelo fundamentalmente síncrono (LLM).
*   Los subsistemas (Telegram, Email, Reloj) actúan como **Sensores** que inyectan eventos en una cola concurrente (`deliveryQueue`) dentro de `SensorsServiceImpl`.
*   Para que el LLM procese esto sin romper la estricta secuencia de mensajes (Usuario -> IA -> Tool -> ToolResult), el orquestador "engaña" al modelo. Inyecta artificialmente una petición a una herramienta ficticia llamada `pool_event`.
*   El resultado de esta herramienta es el evento externo formateado en JSON. De este modo, el LLM procesa notificaciones de Telegram, correos nuevos o alarmas del sistema como si él mismo hubiera decidido revisar sus sensores.

#### 5.5. Percepción Temporal
El agente tiene consciencia del paso del tiempo de dos formas:
1.  **Inyección en herramientas:** Todas las herramientas exponen el momento actual de ejecución.
2.  **Sensor de Silencio (`SYSTEMCLOCK`):** La clase `Session` monitoriza el tiempo entre interacciones. Si ha pasado más de 1 hora desde el último mensaje, al momento de reactivarse la conversación, inyecta automáticamente un evento indicando: *"Ha pasado X tiempo desde la última interacción..."*. Esto da contexto al modelo sobre si está continuando una charla fluida o retomando una conversación del día anterior.

#### 5.6. Indexación de Documentos (DocMapper / RAG)
Implementa un motor de recuperación de información documental en dos fases gestionado por `DocumentStructureExtractor` (ejecutado asíncronamente mediante hilos de plataforma):
1.  **Fase Estructural:** Se lee el documento línea a línea, se convierte a CSV (con número de línea) y un LLM de razonamiento extrae la jerarquía (Tabla de Contenidos - TOC), identificando niveles, títulos y líneas de inicio/fin.
2.  **Fase de Resumen:** Un LLM secundario (más rápido/barato) lee los bloques de texto de cada sección extraída, generando un resumen conciso y asignando categorías.
3.  **Persistencia:** La estructura jerárquica se guarda como JSON en disco (`.struct`), y los resúmenes se vectorizan y guardan en H2 (`DOCUMENTS`).
Para acceder, el agente usa un embudo de herramientas: busca por concepto (`document_search`), lee el índice para orientarse (`get_document_structure`) y finalmente extrae el texto exacto de las secciones relevantes (`get_partial_document`).

#### 5.7. Gestión de la Seguridad
Dado que el agente posee herramientas de edición y ejecución en el sistema anfitrión, la seguridad es estricta:
*   **AgentAccessControl (Sandbox):** Valida todas las rutas usando listas blancas (`allowed_external_paths`) y listas negras de solo lectura/prohibidas. Evita ataques de *Path Traversal*. Nunca permite modificar archivos de respaldo internos (terminados en `,jv`).
*   **Autorización de Usuario:** Cualquier herramienta que implemente `MODE_WRITE` o `MODE_EXECUTION` requiere autorización interactiva. La ejecución se pausa hasta que el usuario confirma en la UI o la Consola.
*   **Auto-CI (Control de Versiones):** Antes de que las herramientas `file_write`, `file_patch` o `file_search_and_replace` modifiquen un archivo existente, el sistema instancia silenciosamente la librería local JavaRCS (`CheckinOptions`) para crear un punto de restauración, protegiendo contra corrupciones de código generadas por el LLM. El agente incluso tiene una herramienta (`file_recovery`) para deshacer sus propios errores si se da cuenta de ellos.
*   **Firejail:** La herramienta de ejecución de Shell comprueba la existencia de `firejail`. Si está presente, encierra el comando en un contenedor Linux para evitar accesos al directorio de datos del agente.

#### 5.8. Flujos del Conversation Manager
El `ReasoningServiceImpl` ejecuta un hilo independiente (`eventDispatcher`).
1.  Espera pasivamente en un `wait()` hasta que entra un mensaje de usuario o un evento de sensor.
2.  Ensambla el contexto: Prompt del sistema (con Identidad) + Relato Histórico (Checkpoint) + Mensajes de Sesión recientes.
3.  Invoca al LLM (`model.generate`).
4.  Si el LLM pide herramientas (`hasToolExecutionRequests`), se validan los permisos, se ejecutan las clases de Java, se registran los turnos y se vuelve a invocar al LLM con el resultado.
5.  Si el LLM emite texto normal, se muestra al usuario, se guarda el turno, y se verifica si se ha alcanzado el límite para compactar la memoria.

### 6. Inventario Exhaustivo de Herramientas (AgentTools)

Las herramientas dotan al agente de capacidades operativas. Están clasificadas internamente por modos de ejecución (Lectura, Escritura, Ejecución, Web).

**Bloque 1: Sistema, Memoria y Percepción (Operativas y Cognitivas)**
*   `pool_event`: Consulta eventos externos pendientes (usada para la inyección simulada de notificaciones).
*   `schedule_alarm`: Programa avisos a futuro interpretando lenguaje natural ("in 10 minutes") usando `Natty`.
*   `sensor_status`: Muestra estadísticas e inventario de los canales sensoriales activos.
*   `sensor_stop` / `sensor_start`: Permite al agente apagar/encender interrupciones sensoriales para concentrarse.
*   `lookup_turn`: Recupera el texto exacto e inalterado de un momento histórico referenciado como `{cite:ID-XXX}`.
*   `search_full_history`: Realiza una búsqueda semántica (vectorial) sobre toda la historia de la conversación almacenada.

**Bloque 2: Gestión de la Identidad y Entorno**
*   `consult_environ`: Hace un "page-in" del contenido detallado de un módulo de identidad basándose en su referencia.
*   `list_skills`: Devuelve el catálogo de manuales de procedimiento (`.ref.md`) disponibles.
*   `load_skill`: Carga en el contexto de la conversación el contenido íntegro de un manual de procedimiento.

**Bloque 3: Acceso a Internet y Entorno Red**
*   `web_search`: Búsqueda en internet utilizando la API de Brave Search.
*   `web_get_content`: Extrae el HTML limpio de una URL específica usando expresiones regulares.
*   `web_get_content` *(WebGetTikaTool)*: Alternativa o sobrecarga que extrae contenido usando Apache Tika (procesa PDFs, DOCX alojados en URLs).
*   `get_current_location`: Determina la ubicación física del agente consultando la IP (ip-api.com).
*   `get_weather`: Obtiene el clima actual utilizando Open-Meteo API.
*   `get_current_time`: Devuelve la hora, fecha y Timezone exactos del sistema operativo.

**Bloque 4: Operaciones de Sistema de Archivos (Lectura)**
*   `file_find`: Busca archivos recursivamente basándose en patrones *glob* (ej. `**/*.java`).
*   `file_grep`: Busca cadenas de texto o regex dentro de los archivos del proyecto.
*   `file_read`: Lee el contenido de un archivo de texto con soporte de *paginación* para evitar saturación de tokens (offset/limit).
*   `file_read_selectors`: Permite leer múltiples archivos a la vez pasándoles *globs* o una lista de rutas.
*   `file_extract_text`: Usado para archivos binarios (PDFs locales). Los convierte a texto con Tika y los cachea.
*   `file_history`: Muestra el historial de versiones (logs de commits) de un archivo mediante el gestor RCS interno.

**Bloque 5: Operaciones de Sistema de Archivos (Escritura y Modificación)** *(Requieren Confirmación)*
*   `file_mkdir`: Crea directorios completos.
*   `file_write`: Escribe o sobrescribe por completo el contenido de un archivo.
*   `file_patch`: Aplica un archivo `.diff` unificado para realizar cambios precisos sobre múltiples partes de un archivo.
*   `file_search_and_replace`: Reemplaza un bloque de código específico por otro.
*   `file_recovery`: Fuerza un *checkout* de RCS para revertir un archivo a una revisión histórica anterior.

**Bloque 6: Ejecución de Comandos** *(Requieren Confirmación)*
*   `shell_execute`: Ejecuta un comando Bash. Gestiona la ejecución asíncrona, redirige la salida a un archivo temporal (`.out`) y devuelve un ID al agente. Usa `Firejail` si está instalado.
*   `shell_read_output`: Lee de forma paginada la salida estándar generada por `shell_execute`.

**Bloque 7: Comunicaciones**
*   `telegram_send`: Envía un mensaje activo al usuario a través del Bot de Telegram.
*   `email_list_inbox`: Revisa las cabeceras IMAP de los últimos correos recibidos.
*   `email_read`: Descarga y limpia (con Tika) el cuerpo de un email específico mediante su UID.
*   `email_send`: Envía un correo electrónico mediante SMTP.

**Bloque 8: Indexación Documental (DocMapper)**
*   `document_index`: Lanza el proceso asíncrono en segundo plano para estructurar un documento nuevo.
*   `document_search`: Búsqueda híbrida (filtros SQL por categoría + ranking vectorial semántico) en documentos indexados.
*   `document_search_by_categories`: Lista documentos por su categoría exacta.
*   `document_search_by_sumaries`: Búsqueda puramente vectorial sobre los resúmenes de los documentos.
*   `get_document_structure`: Devuelve al modelo el árbol XML (TOC) generado durante la indexación.
*   `get_partial_document`: Inyecta el contenido de lectura densa únicamente en las ramas solicitadas del árbol XML.

### 7. Construcción y Despliegue

*   **Sistema de Construcción:** Maven. 
*   El fichero `pom.xml` está configurado para producir un *Fat JAR* (Uber JAR) a través de `maven-shade-plugin`. Se aplican transformadores para `ServicesResourceTransformer` (vital para extensiones de Langchain4j o Log4j2) y eliminación de firmas RSA/DSA de terceros para evitar errores en tiempo de ejecución.
*   **Empaquetado de fuentes:** Se observa un script de utilidad `packsources.sh` utilizado para extraer y empaquetar el código fuente, excluyendo carpetas compiladas o temporales.
*   **Despliegue:** El sistema no requiere servicios en contenedores (Docker), ni servidores de bases de datos externos. Todo el entorno de ejecución, persistencia (H2 crea sus ficheros `memory` y `service` en `var/lib`) y configuración se despliega en el mismo directorio de ejecución, bajo la carpeta oculta (o subrayada) configurada por `AgentPaths` (por defecto `.noema-agent` / `_noema-agent`). 

### 8. Conclusión

**Noema** es un sistema de IA arquitectónicamente maduro y altamente ingenioso en sus decisiones de diseño. Destaca principalmente por:

1.  **Su solución al problema del Context Window:** En lugar de delegar el problema a proveedores externos o perder información, implementa un "compresor de recuerdos" (`MemoryManager`) que teje una narrativa histórica, manteniendo un nivel altísimo de coherencia a largo plazo mediante citas cruzadas a bases de datos locales.
2.  **Seguridad paranoica e Inteligente:** Integrar un sistema de Control de Versiones (RCS) programado puramente en Java para crear backups justo antes de que el LLM toque un archivo es una decisión brillante que mitiga uno de los mayores miedos al usar Agentes autónomos.
3.  **Inversión de Control Simulada:** La manera en que maneja los estímulos asíncronos de sensores (reloj, emails, telegram) envolviéndolos como respuestas de una herramienta ("pool_event") es una táctica excelente para sortear la limitación estática y secuencial de las APIs de los LLM actuales.

Es, en definitiva, un entorno de experimentación riguroso que demuestra cómo, orquestando bibliotecas tradicionales del ecosistema Java con abstracciones modernas (LangChain4j), se puede construir un agente verdaderamente persistente, autónomo y adaptado a las necesidades de investigación a largo plazo.
