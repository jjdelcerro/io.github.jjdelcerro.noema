

**Informe de Análisis Arquitectónico y Técnico: Proyecto "Noema"**

*   **Versión Analizada:** 0.1.0
*   **Fecha de Análisis:** Abril 2026
*   **Autor del Informe:** Gemini (IA), basado en la inspección estática del código fuente.


### 1. Visión General

"Noema" es un agente de Inteligencia Artificial autónomo, desarrollado como un proyecto personal de investigación. Su propósito principal es servir como un asistente y compañero para interacciones analíticas, reflexivas y de investigación a largo plazo en una **única línea temporal continua**.

Frente a la tendencia actual de crear agentes orientados a micro-tareas o desarrollo de software, Noema destaca por una filosofía de diseño pragmática y autocontenida: opera íntegramente en local sin requerir infraestructura externa compleja (como bases de datos vectoriales dedicadas o servidores de caché). Todo su estado, configuración y memoria se consolidan en el sistema de archivos local y en una base de datos embebida (H2), delegando la carga computacional pesada a APIs de modelos de lenguaje (LLMs) externos.

La arquitectura resuelve de manera brillante el problema de la persistencia a largo plazo mediante un sistema de "Puntos de Guardado" (Checkpoints) narrativos y compresión de contexto, permitiendo que la interacción perdure en el tiempo de forma indefinida dentro de las restricciones tecnológicas actuales.


### 2. Stack Tecnológico

El proyecto está construido sobre un ecosistema Java moderno, primando dependencias ligeras y embebibles:

*   **Lenguaje:** Java 21 (aprovechando Virtual Threads / Platform Threads y records).
*   **Gestión de Dependencias y Build:** Maven (con `maven-shade-plugin` para generar un Fat JAR).
*   **Orquestación LLM:** LangChain4j (Núcleo, integración con OpenAI/OpenRouter/Groq/Chutes y modelos de embeddings locales).
*   **Persistencia y Búsqueda Vectorial:** H2 Database (embebida, estructurada para manejar metadatos, turnos y BLOBs de vectores con cálculo de distancia coseno en cliente).
*   **Interfaz de Usuario:**

    *   **GUI:** Swing enriquecido con FlatLaf (Dark theme), RSyntaxTextArea (edición con resaltado) y flexiblidad con MigLayout.
    *   **CLI:** JLine3 (para soporte de terminal interactiva rica).
    
*   **Procesamiento de Documentos:** Apache Tika (extracción de texto de binarios/web) y CommonMark (renderizado Markdown).
*   **Control de Versiones (Interno):** `io.github.jjdelcerro.javarcs` y `java-diff-utils`. Implementación 100% Java para la gestión de respaldos históricos ([RCS](https://github.com/jjdelcerro/io.github.jjdelcerro.javarcs)) y aplicación de parches (`UnifiedDiffUtils`).
*   **Comunicaciones:** Jakarta Mail (IMAP/SMTP), Java Telegram Bot API.
*   **Utilidades:** Gson (Serialización/Deserialización), Jsoup, Natty (Parseo de fechas en lenguaje natural).


### 3. Estructura de Paquetes (Interfaces / Implementación)

El diseño del código sigue una estricta segregación de responsabilidades mediante inyección de dependencias manual y programación orientada a interfaces:

*   `io.github.jjdelcerro.noema.lib.*`: **El Contrato (API).** Contiene exclusivamente interfaces (`Agent`, `AgentService`, `AgentTool`, `SourceOfTruth`, `SensorEvent`, etc.). Define *qué* puede hacer el sistema.
*   `io.github.jjdelcerro.noema.lib.impl.*`: **La Implementación.** Clases concretas que resuelven las interfaces (`AgentImpl`, `SourceOfTruthImpl`, etc.). Contiene la lógica de negocio real.
*   `io.github.jjdelcerro.noema.lib.impl.services.*`: **Módulos de Dominio.** Agrupa la lógica por servicio (documents, email, memory, reasoning, scheduler, sensors, telegram, embeddings).
*   `io.github.jjdelcerro.noema.ui.*`: **Capa de Presentación.** Separada netamente del núcleo, con implementaciones dedicadas para Consola (`ui.console`) y entorno Gráfico (`ui.swing`).
*   `io.github.jjdelcerro.noema.main.*`: **Puntos de Entrada.** Lógica de Bootstrapping (`BootUtils`) y los lanzadores `MainGUI` y `MainConsole`.


### 4. Arquitectura y Diseño

El sistema está diseñado en capas concéntricas, desde el motor central de gestión de estado hasta los servicios periféricos que interactúan con el mundo exterior.

#### 4.1. El Kernel (o Core)
Es el corazón del sistema, encargado del ciclo de vida y la persistencia absoluta.

*   **`Agent` y `AgentManager`**: `AgentManager` actúa como factoría principal y registro de servicios. `Agent` es el director de orquesta que expone el acceso a la configuración, las acciones, la consola, la base de datos y el enrutamiento de eventos externos (`putEvent`).
*   **Ciclo de Vida**: Gestionado de forma explícita (`start()`, `stop()`). Los servicios declaran si pueden arrancar (`canStart()`) basándose en la configuración presente.
*   **Infraestructura de Datos (`SourceOfTruth`)**: Encapsula el acceso a la base de datos H2. Mantiene dos tablas maestras: `turnos` (el registro atómico inmutable de cada interacción) y `checkpoints` (metadatos de los puntos de consolidación).
*   **Topología de Archivos (`AgentPaths`)**: Define un "Sandbox" estricto. 

#### 4.2. Capacidades Horizontales (Cross-cutting Concerns)
*   **Seguridad y Control de Acceso (`AgentAccessControl`)**: Un "guardián" crítico. Verifica si un *Path* intenta escapar del sandbox (Path Traversal), si tiene permisos de escritura o ejecución. Incluye soporte nativo para requerir confirmación humana antes de acciones destructivas y obliga (si está configurado) a realizar un backup automático en [RCS](https://github.com/jjdelcerro/io.github.jjdelcerro.javarcs) antes de modificar un archivo.
*   **Gestión de Rutas (`AgentPaths`)**: Abstracción que resuelve de manera transparente si un archivo de configuración debe leerse desde el workspace local o desde la configuración global del usuario en el sistema operativo (`~/.config/noema-agent`).
*   **Sistema de Configuración (`AgentSettings`)**: Sistema jerárquico persistido en JSON. Soporta propiedades anidadas, listas marcables (`AgentSettingsCheckedList`) y evaluación de expresiones lógicas en tiempo de ejecución (mediante `ExpressionEvaluator`) para habilitar/deshabilitar opciones en la UI dinámicamente.

#### 4.3. Servicios Cognitivos y Persistencia
Estos servicios emulan el procesamiento lógico y el almacenamiento de la información.

*   **[ReasoningService](docs/reasoning-service.md) (Orquestación)**: Contiene el bucle principal de procesamiento (`eventDispatcher`). Prepara el contexto combinando el System Prompt, el último `CheckPoint`, y la sesión actual (`Session`). Llama al LLM, procesa de forma recursiva las solicitudes de herramientas (`ToolExecutionRequest`), recorta silenciosamente salidas excesivamente largas (paginación/trimming) y decide cuándo el contexto es lo suficientemente grande como para solicitar una compactación.
*   **`MemoryService` (Consolidación)**: Se encarga de transformar una lista de turnos (en formato CSV) en una narrativa coherente dividida en dos partes: "Resumen" (factual) y "El Viaje" (la evolución del pensamiento). Utiliza citas explícitas (`{cite:ID}`) para garantizar la trazabilidad inmutable hacia los registros originales de la base de datos.
*   **`EmbeddingsService`**: Un servicio transversal ejecutado en local mediante `AllMiniLmL6V2EmbeddingModel`. Vectoriza textos para búsqueda semántica. Dado que H2 no tiene índices vectoriales nativos, la clase `EmbeddingFilterImpl` carga los vectores en memoria y realiza un ranking mediante distancia coseno usando una cola de prioridad (Min-Heap).

#### 4.4. Servicios de Periferia
Módulos que dotan al agente de capacidades de I/O con el entorno.

*   **[SensorsService](docs/sensors-service.md)**: Un sofisticado gestor de "percepciones". Transforma señales de Telegram, Emails, o el paso del tiempo en eventos (`SensorEvent`). Soporta diferentes "naturalezas" para no saturar al LLM: eventos agregables (cuenta ocurrencias), fusionables (concatena logs), o de estado (solo guarda el último).
*   **`SchedulerService`**: Permite al agente programar alarmas futuras. Persiste las tareas en BBDD para sobrevivir a reinicios. Cuando el temporizador expira, inyecta un evento en el bus de sensores.
*   **`EmailService` / `TelegramService`**: Implementan un patrón *Push/Pull*. Actúan como un demonio escuchando en segundo plano. Cuando llega un mensaje, envían un *metadata ping* al `SensorsService` (Ej: "Tienes un email con ID X"). El agente, si lo considera oportuno, usa una herramienta para leer el cuerpo completo, evitando inundar su contexto de forma no solicitada.
*   **`DocumentsService`**: Implementa el RAG (Retrieval-Augmented Generation) del sistema. Analiza documentos, extrae su estructura jerárquica (TOC) y genera resúmenes y categorías usando LLMs auxiliares (DocMapper). La estructura se guarda como JSON y se inyecta al LLM principal en formato XML plegado (`<section status="collapsed">`), permitiendo al agente expandir partes específicas.

### 5. Herramientas del Agente (AgentTools)

Las herramientas son la interfaz mediante la cual el LLM interactúa con los servicios subyacentes. Están diseñadas para retornar JSON estructurado o protocolos de texto precisos (paginación).

**Memoria y Sistema**
*   `fetch_citation`: Recupera el texto exacto, contexto y metadatos de un turno histórico utilizando su ID (ej. al ver un `{cite:123}`).
*   `search_full_history`: Realiza una búsqueda vectorial sobre toda la base de datos histórica de la conversación.
*   `annotate_observation`: Permite al agente consolidar un hecho en su memoria procedimental/episódica a corto plazo para que se incluya en el próximo Checkpoint.
*   `pool_event`: Herramienta "ficticia" utilizada arquitectónicamente para inyectar eventos asíncronos en el historial de forma nativa.
*   `sensor_status` / `sensor_stop` / `sensor_start`: Gestión del ruido externo, permitiendo al agente silenciar distracciones temporalmente.
*   `schedule_alarm`: Registra un temporizador (parseando lenguaje natural con Natty).

**Identidad y Habilidades (Skills)**
*   `list_skills`: Enumera el catálogo de procedimientos disponibles (`.ref.md`).
*   `load_skill`: Hace un "page-in" cargando un protocolo de actuación pesado al contexto actual.
*   `consult_environ`: Carga módulos densos sobre información del entorno o el usuario.

**Archivos y Ejecución (Local)**
*   `file_find`: Busca archivos por glob pattern y retorna metadatos.
*   `file_grep`: Busca cadenas de texto dentro del contenido de los archivos.
*   `file_read`: Lee el contenido de un archivo de texto.
*   `file_extract_text`: Usa Tika para leer archivos complejos (PDF, DOCX) y cachea la salida.
*   `read_paginated_resource`: Herramienta vital que permite consumir bloques de texto masivos (salida de comandos, logs) mediante parámetros `offset` y `limit`.
*   `file_write`: Sobrescribe o crea un archivo entero (con auto-backup previo en [RCS](https://github.com/jjdelcerro/io.github.jjdelcerro.javarcs)).
*   `file_patch`: Aplica un *Unified Diff* (`@@ ... @@`) usando la librería interna.
*   `file_search_and_replace`: Para modificaciones precisas sin requerir diffs completos.
*   `file_mkdir`: Crea directorios.
*   `file_history`: Muestra el log de revisiones de un archivo ([RCS](https://github.com/jjdelcerro/io.github.jjdelcerro.javarcs) `rlog`).
*   `file_recovery`: Restaura un archivo a una versión antigua ([RCS](https://github.com/jjdelcerro/io.github.jjdelcerro.javarcs) `co`).
*   `shell_execute`: Ejecuta bash scripts. Captura la salida estándar progresivamente e informa al usuario periódicamente si el proceso tarda mucho. Soporta *Firejail* para ejecución segura.

**Búsqueda y Web**
*   `web_search`: Integra Tavily (y Brave) para obtener resultados de motores de búsqueda.
*   `web_get_content` (`WebGetTikaTool`): Descarga una URL y extrae el texto limpio.
*   `get_current_location`: Geolocaliza al agente basándose en su IP pública.
*   `get_current_time`: Obtiene fecha, hora y huso horario exactos.
*   `get_weather`: Obtiene clima actual mediante Open-Meteo.

**Comunicaciones**
*   `email_list_inbox`: Lee las cabeceras de los últimos correos (Push/Pull).
*   `email_read`: Lee el cuerpo limpio de un UID específico.
*   `email_send`: Redacta y envía un correo SMTP.
*   `telegram_send`: Envía notificaciones proactivas al chat del dueño.

**Ingesta de Documentos (RAG DocMapper)**
*   `document_index`: Dispara el job asíncrono para mapear un documento nuevo.
*   `document_search` / `document_search_by_categories` / `document_search_by_sumaries`: Búsqueda híbrida (SQL + Vector) en la BBDD de documentos.
*   `get_document_structure`: Devuelve el esqueleto XML de un documento mapeado.
*   `get_partial_document`: Inyecta el texto completo solo en las etiquetas XML de las secciones solicitadas.

### 6. Mecanismos Principales (Análisis Detallado)

#### 6.1. Gestión de Memoria Continua (Checkpoints)
Dado que los LLMs tienen un límite de tokens, Noema utiliza un enfoque de compresión con pérdida controlada:

1.  **Session:** Los mensajes recientes se almacenan en `active_session.json`. Cuando la cantidad de turnos supera un umbral (ej. 40), se dispara la compactación.
2.  **Compacting:** El `MemoryService` envía al LLM un volcado CSV de los turnos recientes junto con el último *CheckPoint*. El modelo redacta una nueva narrativa unificada, manteniendo las referencias `cite:ID`.
3.  **Trazabilidad:** La UI y el LLM pueden recuperar retrospectivamente el texto crudo de la BBDD usando `lookup_turn`, asegurando que la compresión no destruya detalles técnicos permanentemente.

#### 6.2. Gestión de Identidad y Habilidades (Skills)
Utiliza una arquitectura de **Paginación Cognitiva (Page-in/Page-out)** para mantener el System Prompt ligero:

*   En el disco existen archivos ligeros (`.ref.md`) que contienen solo el índice y descripción de una habilidad (ej. `deploy_plugin.ref.md`) o de la identidad (`01_personal.ref.md`).
*   El System Prompt carga dinámicamente estos `.ref.md` (las "anclas").
*   Cuando el LLM necesita ejecutar el protocolo completo, invoca `load_skill` o `consult_environ`, inyectando el manual denso (`.md`) temporalmente en la sesión activa. Al compactarse la memoria, este texto denso desaparece del contexto activo, ahorrando tokens.

#### 6.3. Gestión de Eventos y Proactividad (Engaño al Protocolo)
Los LLMs son sistemas reactivos (Request-Response). Para dotar a Noema de proactividad, el `SensorsService` emplea un truco arquitectónico:

1.  Un sensor de fondo detecta un evento (ej. un Telegram entrante).
2.  El evento entra en una cola concurrente (`deliveryQueue`).
3.  Si el agente está inactivo, despierta el `eventDispatcher`.
4.  Para inyectar el evento sin romper el contrato del historial de chat, el sistema **falsifica un mensaje de IA** (`AiMessage`) solicitando ejecutar la herramienta ficticia `pool_event`, seguido inmediatamente de un `ToolExecutionResultMessage` que contiene el JSON del evento real. Para el LLM, parece que él mismo decidió mirar a su alrededor justo cuando sucedió algo.

#### 6.4. Percepción Temporal
El sistema incluye un reloj interno. En el bucle de despacho de eventos, se calcula la diferencia `Duration.between` entre la última interacción del usuario y el momento actual. Si ha pasado más de 1 hora, se inyecta artificialmente un evento discreto (`SYSTEMCLOCK`) con el texto: *"Ha pasado X tiempo desde la última interacción..."*. Esto permite al agente tener noción del tiempo transcurrido, comportándose diferente si se le responde a los 5 minutos o a los 3 días.

#### 6.5. Indexación de Documentos (DocMapper)

Mecanismo diseñado para lidiar con manuales inmensos:

1.  Se carga el documento (texto plano extraído con Tika).
2.  Un LLM potente (Reasoning) lee el documento línea por línea en formato CSV y deduce la Tabla de Contenidos, generando un CSV con `linestart`, `level`, `title`.
3.  Un LLM más rápido y barato procesa cada sección detectada para extraer un resumen y etiquetas.
4.  Este mapa se consolida (`DocumentStructure`) y se almacena en disco (`.struct`) y en base de datos.
5.  Cuando el agente consulta el documento, se le entrega una versión XML contraída. Si usa la herramienta `get_partial_document`, el sistema lee dinámicamente desde el archivo original usando los offsets de bytes calculados, inyectando el texto solo en los nodos XML solicitados.

#### 6.6. Gestión de la Seguridad y RCS
Noema tiene permisos limitados y gobernados:

*   **Sandbox Estricto:** Toda herramienta que lee/escribe pasa por `AgentAccessControl.resolvePath()`. Previene vulnerabilidades de salto de directorio (Path Traversal).
*   **Confirmación Humana:** Si la configuración lo exige, cualquier herramienta con modo `MODE_WRITE` o `MODE_EXECUTION` suspende la ejecución hasta que el usuario hace clic en "Autorizar" en la UI de la consola.
*   **[RCS](https://github.com/jjdelcerro/io.github.jjdelcerro.javarcs) Backup:** Antes de que las herramientas `file_write`, `file_patch` o `file_search_and_replace` alteren un archivo, invocan `RCSLocator.getRCSManager().createCheckinOptions()`, forzando un guardado de versión automática usando la biblioteca 100% Java creada ad-hoc. El archivo viejo queda asegurado en ficheros locales `,jv`.

#### 6.7. Herramienta de Paginación Universal

Las herramientas como lectura de archivos, salida de Shell, o scraping Web usan la clase abstracta `AbstractPaginatedAgentTool`. Si la salida supera `MAX_LINES`, la herramienta guarda el resultado completo en un archivo temporal cacheado (`.out` o UUID.txt) y devuelve al LLM solo el primer bloque junto con un campo de metadatos `HINT: To read the next block, call 'read_paginated_resource' with args...`. El agente interpreta este Hint y llama explícitamente al siguiente bloque de forma autónoma.

### 7. Construcción y Despliegue

*   **Empaquetado:** El `pom.xml` utiliza `maven-shade-plugin` con el `ServicesResourceTransformer`. Esto es crítico para unificar correctamente los archivos de configuración de Java SPI de las múltiples dependencias (necesario para Tika, LangChain y Log4j). Excluye firmas RSA/DSA para evitar corrupciones de seguridad de JARs en el uber-jar.
*   **Ejecución:** No requiere contenedores Docker ni configuraciones de red. Un script `packsources.sh` evidencia un enfoque práctico para empaquetar el código fuente para su análisis u otros fines.
*   **Entrada de Aplicación:** Emplea un proxy en `Main` que decide arrancar la GUI (`MainGUI`) o la versión terminal (`MainConsole` - con JLine3) mediante un argumento por línea de comandos (`-c`).


### 8. Conclusión

"Noema" exhibe una arquitectura soberbia y pragmática para la experimentación con agentes autónomos a largo plazo. Sus mayores méritos residen en **cómo resuelve limitaciones inherentes de los LLMs actuales con soluciones de ingeniería de software clásicas**:

1.  Usa la delegación en sistema de ficheros (Paginación, DocMapper) para sortear las limitaciones de las ventanas de contexto.
2.  Implementa la proactividad mediante inversión de control y falsificación elegante del historial de chat (`pool_event`).
3.  Resuelve la "amnesia" mediante consolidación textual de alta calidad (Checkpoints + Turnos Atómicos).
4.  Mantiene una seguridad obsesiva con herramientas hechas a medida en Java puro ([RCS](https://github.com/jjdelcerro/io.github.jjdelcerro.javarcs), Path Traversal protections).

El diseño asume con éxito que el LLM no es el "cerebro completo", sino el "procesador del lenguaje" dentro de un bucle de control (Kernel) determinista escrito sólidamente en Java. Es un framework excepcional para pruebas avanzadas de interacción humano-máquina a largo plazo.

