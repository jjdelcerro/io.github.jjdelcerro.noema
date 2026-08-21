

**Informe de Análisis Arquitectónico y Técnico: Proyecto "Noema"**

**Versión Analizada:** 0.1.0
**Fecha de Análisis:** 8 de Agosto de 2026
**Autor del Informe:** Gemini (IA), basado en la inspección estática del código fuente.


## 1. Visión General

**Noema** es un proyecto personal y experimental desarrollado en Java que implementa un agente autónomo conversacional. Su propósito principal es servir como compañero de investigación y reflexión a lo largo del tiempo, manteniendo una única sesión persistente y continua. 

El proyecto destaca por su pragmatismo arquitectónico: no busca resolver el desarrollo automatizado de software, sino ofrecer un entorno autocontenido ("Zero-Infrastructure"). Todo el sistema opera localmente empaquetado en un archivo JAR, apoyándose en una base de datos relacional embebida (H2) y en el sistema de archivos del sistema operativo anfitrión. No requiere de infraestructuras externas pesadas (como bases de datos vectoriales dedicadas o buses de eventos tipo Kafka), confiando las operaciones intensivas de IA a llamadas API a Modelos de Lenguaje Grandes (LLMs).

## 2. Stack Tecnológico

El proyecto se apoya en un ecosistema de librerías sólido y actualizado:

*   **Lenguaje:** Java 25.
*   **Gestión de dependencias y construcción:** Maven (con empaquetado Fat JAR vía `maven-shade-plugin`).
*   **Orquestación LLM:** LangChain4j (Core, integraciones con OpenAI, Jlama, y modelos de embeddings locales).
*   **Persistencia:** H2 Database Engine (modo embebido `AUTO_SERVER=TRUE`).
*   **Procesamiento de datos y parsing:** Gson (JSON), Apache Tika (extracción de texto y metadatos de ficheros binarios, PDF, DOCX, web), MVEL (evaluación de expresiones lógicas en configuración), Jsoup / flexmark (Markdown a HTML).
*   **Comunicaciones:** Jakarta Mail (IMAP/SMTP), API de Telegram (`java-telegram-bot-api`), HttpClient nativo de Java 11+.
*   **Control de Versiones y Diff:** Implementación propia e integrada en Java: [RCS](https://github.com/jjdelcerro/io.github.jjdelcerro.javarcs) y `java-diff-utils`.
*   **Interfaces de Usuario:** 
    *   **GUI:** Swing enriquecido con FlatLaf y RSyntaxTextArea.
    *   **CLI:** JLine3 (con soporte para atajos, histórico y multilínea).
    *   **Web:** Javalin (servidor web embebido para UI vía Server-Sent Events e interfaz HTML/JS).

## 3. Estructura de Paquetes, Interfaces e Implementación

El código sigue un patrón estricto de separación de responsabilidades basado en el diseño por contratos:

*   `io.github.jjdelcerro.noema.lib`: Contiene exclusivamente los contratos (Interfaces) del sistema (`Agent`, `AgentService`, `AgentTool`, `AgentConsole`, `SourceOfTruth`, `CheckPoint`, etc.).
*   `io.github.jjdelcerro.noema.lib.impl`: Contiene las implementaciones físicas de dichos contratos. Dentro de este paquete, los servicios se dividen en subpaquetes (`memory`, `reasoning`, `sensors`, `documents`, etc.), los cuales a su vez exponen un subpaquete `tools` donde residen las herramientas que dicho servicio inyecta al agente.
*   `io.github.jjdelcerro.noema.main`: Contiene los puntos de entrada de la aplicación (`Main`, `MainConsole`, `MainGUI`, `NoemaWebServer`, `BootUtils`).
*   `io.github.jjdelcerro.noema.ui`: Contiene las abstracciones y adaptadores para las distintas interfaces (Consola nativa, Swing, o Web).

Esta arquitectura permite que el núcleo del agente opere de manera agnóstica a la interfaz a través de la cual el usuario interactúa.

## 4. Arquitectura y Diseño

### 4.1. El Kernel (o Core)
*   **`Agent` y `AgentManager`:** El `AgentManager` actúa como el Service Locator y la factoría principal del sistema. El `Agent` es el contenedor lógico que aglutina las configuraciones, el acceso a las interfaces, la base de conocimiento y los servicios.
*   **Ciclo de Vida:** Los servicios se registran mediante un `AgentServiceFactory`. Durante el arranque (`start()`), se evalúa si el servicio cuenta con la configuración mínima requerida (`canStart()`). En la parada del sistema (`stop()`), un *shutdown hook* garantiza que los recursos, las conexiones H2 y los estados asíncronos se vuelquen a disco limpiamente.
*   **Infraestructura de Datos:** `SQLProvider` aísla las consultas SQL nativas. La persistencia se divide en dos bases de datos físicas H2 separadas: `memory` (para el historial de turnos y checkpoints) y `service` (para tareas operativas como documentos, alarmas del scheduler, etc.).
*   **Topología de Archivos:** Todo el estado del agente se circunscribe a un directorio de trabajo (Workspace) bajo la carpeta oculta `.noema-agent`. Esta contiene:
    *   `var/config/`: Ficheros properties y el JSON principal de `settings`.
    *   `var/lib/`: Bases de datos H2 y ficheros de estado (ej. `sensors.json`).
    *   `var/cache/` y `var/tmp/`: Para descargas, parsing con Tika y salidas de consola.
    *   `var/identity/`: Configuración del ADN técnico (`core`) y contexto del mundo del usuario (`environ`).
    *   `var/skills/`: Manuales procedimentales de herramientas.
    *   `home/`: Directorio aislado para la ejecución de scripts en entorno seguro.

### 4.2. Capacidades Horizontales (Cross-cutting Concerns)
*   **[Seguridad y Control de Acceso](docs/seguridad-y-control-de-acceso.md) (`AgentAccessControl`):** Gestiona de forma estricta los límites operativos del agente. Define un Sandbox para las operaciones de lectura/escritura (`nom_writable_paths`, `nom_readable_paths`, `allowed_external_paths`). Bloquea cualquier intento de ruta relativa maliciosa (Path Traversal). Evalúa globalmente si una herramienta tiene permisos de ejecución, escritura o red antes de exponerla al LLM.
*   **[Gestión de Rutas y Sandbox](docs/gestion-de-rutas.md) (`AgentPaths`):** Abstracción que resuelve rutas relativas asegurando que siempre apunten a ubicaciones válidas dentro de la jerarquía de `.noema-agent` o la carpeta local del proyecto.
*   **Sistema de Configuración Jerárquica (`AgentSettings`):** Implementado como un árbol de nodos (String, Lists, Booleans). Permite evaluación dinámica mediante el motor MVEL. Por ejemplo, una configuración en la UI puede habilitarse o deshabilitarse basada en el valor de otra configuración (ej. bloquear herramientas de disco si el control de acceso a disco está apagado).

### 4.3. Servicios Cognitivos
Esta capa gestiona cómo el agente piensa, recuerda y se comunica con el LLM. Comparten el acceso al **SourceOfTruth** (la capa de persistencia base que graba todo de forma inmutable).

*   **Persistencia (Común):** El `SourceOfTruthImpl` guarda de forma atómica cada *Turno* de la conversación (Input de usuario, pensamiento del modelo, acción de herramienta, resultado, etc.) en H2.
*   **[ReasoningService](docs/reasoning-service.md) (Orquestación del pensamiento)** Es el motor de inferencia. Construye el "System Prompt" consolidando la Identidad, el Entorno y las Habilidades. Gestiona la `Session` (ventana de contexto en RAM) y orquesta el bucle de ejecución: *Prepara contexto -> Llama al LLM -> Si hay Tool Call, valida y ejecuta -> Inyecta resultado -> Repite hasta que el modelo decida responder al usuario*. Emplea técnicas de *context trimming* (recorte de salidas largas de herramientas y notificación de las mismas).
*   **[MemoryService](docs/memory-service.md) (Consolidación histórica y Checkpoints):** Encargado de la compresión semántica a largo plazo. Cuando los turnos exceden un límite, este servicio usa el LLM para leer los turnos pasados y redactar un **Punto de Guardado**. Este punto de guardado tiene formato Markdown y fusiona el resumen de lo ocurrido con una narrativa ("El viaje").

### 4.4. Servicios de Periferia
Manejan las capacidades sensoriales, de almacenamiento documental y de actuación sobre el mundo exterior.

*   **[SensorsService](docs/sensors-service.md)** Implementa un bus de eventos concurrente para inyectar percepciones asíncronas en el modelo. Clasifica los eventos según su naturaleza (`DISCRETE`, `MERGEABLE`, `AGGREGATABLE`, `STATE`, `USER`) para evitar saturar al agente si ocurren demasiados eventos mientras está procesando o inactivo.
*   **[SchedulerService](docs/scheduler-service.md):** Un motor de tareas programadas. Permite al agente registrar alarmas persistentes en H2. Al cumplirse el plazo, inyecta un evento en el `SensorsService` para que el agente reciba el estímulo.
*   **EmailService y TelegramService:** Adaptadores de comunicación. Escuchan pasivamente (IMAP IDLE y Long-Polling). En lugar de enviar un documento gigante al LLM, inyectan notificaciones breves ("Tienes un nuevo email del usuario"). El agente luego usa sus herramientas para leer el contenido real.
*   **DocumentsService:** Servicio RAG (Retrieval-Augmented Generation) avanzado. Al ingestar un documento (PDF, texto), utiliza un proceso asíncrono y LLMs de bajo coste para parsear la estructura jerárquica (índice) y redactar resúmenes por sección. 
*   **[EmbeddingsService](docs/embeddings-service.md):** Mantiene la vectorización local. Al no usar BBDD vectoriales de terceros, carga un modelo cuantizado en proceso (ej. `AllMiniLmL6V2`). La búsqueda vectorial (`EmbeddingFilterImpl`) se hace en memoria calculando la distancia coseno mediante un *Min-Heap* (Priority Queue) para extraer el Top-K de similitudes contra los BLOBs almacenados en H2.


## 5. Descripción Detallada de Mecanismos Principales

### Gestión de la Memoria
El sistema descarta el enfoque de mantener todo el diálogo crudo o confiar ciegamente en una base vectorial. Divide la memoria en tres fases:

1.  **Turnos:** Inmutables y almacenados en H2 con su embedding correspondiente.
2.  **Sesión (RAM):** La ventana de contexto deslizante y temporal de los eventos actuales.
3.  **Checkpoints (Puntos de Guardado):** Cuando la sesión alcanza un umbral configurado (ej. 40 turnos), se dispara el protocolo de compactación. Un modelo LLM asimila los turnos y el Checkpoint anterior, generando un nuevo documento Markdown ("Resumen" + "El Viaje"). Crucialmente, el sistema instruye al LLM para que preserve marcadores de citas (`{cite:123}`) atados a los IDs de los turnos originales, garantizando que el agente siempre sepa cómo recuperar el detalle técnico exacto del pasado.

### Identidad y Habilidades (Skills)
*   **Identidad:** Se define en ficheros `.md` dentro de `var/identity/core` (reglas operativas estables) y `var/identity/environ` (biografía o estado del mundo).
*   **Skills:** Son protocolos o manuales de instrucciones técnicos (`var/skills`). El agente tiene una herramienta para listar los títulos y resúmenes de estos manuales. Si el usuario pide una tarea (ej. "Haz un deploy"), el agente busca en el listado, localiza el manual correcto y usa otra herramienta para cargarlo en su contexto temporal de trabajo.

### Gestión de Eventos y Percepción Temporal
Los eventos externos no interrumpen al LLM, sino que se encolan.

*   **Inyección "Pool Event":** Cuando el LLM termina su turno, el motor verifica si hay eventos en cola. Si los hay, envuelve la información del evento fingiendo que el propio LLM llamó a una herramienta del sistema llamada `pool_event`. Esto mantiene intacta la pureza conversacional (User -> AI -> Tool -> Result).
*   **Percepción Temporal:** Si transcurre mucho tiempo (ej. 1 hora) sin interacción, el sistema inyecta un evento silencioso (`SYSTEMCLOCK`). Esto dota al agente del sentido del paso del tiempo ("Han pasado X horas desde la última interacción"), permitiéndole comenzar respuestas de forma natural ("Ha pasado un rato, ¿en qué estábamos?").

### Indexación de Documentos
Emplea un enfoque de **DocMapper**. 

1. `Tika` extrae el texto bruto.
2. El sistema lo convierte a formato CSV (Línea, Contenido).
3. Un LLM "razonador" analiza el CSV y extrae un XML con la jerarquía de los títulos.
4. Un LLM "básico" lee el contenido entre títulos para extraer un resumen y etiquetas (categorías).
5. Todo se guarda en H2 con embeddings. Posteriormente, el agente no busca "chunks ciegos", sino que explora el árbol estructural del documento y recupera la sección específica exacta.

### Gestión de la Seguridad
*   **Sandboxing:** Las herramientas de disco usan `AgentAccessControl` para verificar la ruta y bloquear Path Traversal (`../../`).
*   **Human-in-the-Loop:** Herramientas de escritura de disco o de comandos Bash detienen la ejecución y disparan un prompt asíncrono (popup en Swing o pregunta en consola) pidiendo autorización humana antes de continuar.
*   **RCS (Sistema de Control de Revisiones):** Antes de cualquier operación de modificación de archivos (`file_write`, `file_patch`, `file_search_and_replace`), el sistema inyecta automáticamente un comando Check-in (`ci`) a la librería interna [RCS](https://github.com/jjdelcerro/io.github.jjdelcerro.javarcs). Esto permite que el agente siempre pueda revertir errores si el LLM daña el código.
*   **Firejail:** Para `shell_execute`, si el binario `firejail` está instalado en el sistema operativo, los comandos se enjaulan con acceso exclusivo de lectura/escritura al `home` del sandbox, protegiendo al equipo anfitrión.


## 6. Catálogo de Herramientas del Agente

Las [herramientas](docs/agenttools.md) están repartidas por los diferentes servicios, heredando de `AbstractAgentTool` (o `AbstractPaginatedAgentTool` para gestionar salidas masivas).

### Sistema, Eventos y Memoria (`MemoryService` / `ReasoningService` / `SensorsService`)
*   `pool_event`: Herramienta ficticia que consulta el bus de eventos pendientes.
*   `get_current_time`: Devuelve la hora del sistema y zona horaria.
*   `schedule_alarm`: Programa una notificación asíncrona mediante Natural Language Parsing (vía Natty).
*   `sensor_start` / `sensor_stop` / `sensor_status`: Permiten al agente apagar canales (ej. silenciar Telegram) para "concentrarse" o consultar qué sensores tiene activos.
*   `lookup_turn`: Recupera un fragmento exacto del historial basándose en una cita (`{cite:ID}`).
*   `search_full_history`: Búsqueda vectorial semántica en toda la base de conocimiento pasada.
*   `annotate_observation`: Permite al agente escribir "notas" en su flujo actual para forzar la consolidación de conclusiones en el próximo ciclo de memoria.

### Identidad y Procedimientos (`ReasoningService`)
*   `list_skills`: Escanea el directorio de habilidades y devuelve el catálogo de procedimientos disponibles.
*   `load_skill`: Carga el contenido denso (`.md`) de un procedimiento específico en el contexto actual.
*   `consult_environ`: Recupera un módulo de conocimiento denso sobre el usuario o el entorno basándose en los ficheros de referencias ligeras (`.ref.md`).

### Interacción con Archivos y Código (`ReasoningService`)
*(Controladas por RCS y Sandbox)*

*   `file_find`: Búsqueda de rutas mediante patrones glob (con paginación temporal).
*   `file_grep`: Búsqueda de cadenas de texto dentro de archivos o directorios.
*   `file_read`: Lee ficheros de código o texto plano (paginado).
*   `file_read_selectors`: Lee múltiples ficheros simultáneamente agrupándolos (paginado).
*   `file_extract_text`: Usa Apache Tika para leer archivos binarios pesados (PDF, Word) almacenando la caché de la extracción.
*   `file_write`: Sobrescribe o crea archivos nuevos.
*   `file_mkdir`: Creación de rutas de directorios.
*   `file_search_and_replace`: Reemplazo seguro y quirúrgico de un bloque de texto exacto.
*   `file_patch`: Aplica un *Unified Diff* (@@) para refactorizaciones complejas (usa `java-diff-utils`).
*   `file_history`: Muestra el log de un archivo usando [RCS](https://github.com/jjdelcerro/io.github.jjdelcerro.javarcs) (`rlog`).
*   `file_recovery`: Restaura un fichero a una versión anterior usando [RCS](https://github.com/jjdelcerro/io.github.jjdelcerro.javarcs) (`co`).
*   `read_paginated_resource`: Herramienta técnica que permite al LLM ir pidiendo el siguiente *chunk* de un recurso paginado (usado por lecturas web, grep, bash, etc.).

### Ejecución de Comandos
*   `shell_execute`: Ejecuta bash. Gestiona la no interactividad, detiene la ejecución si el comando dura demasiado preguntando al usuario, e integra `firejail`.

### Búsqueda e Interacción Web
*   `web_search`: Integra APIs de búsqueda externa (Brave o Tavily).
*   `web_get_content`: Descarga una URL y aplica Tika para limpiar el HTML o parsear documentos online (paginado).
*   `get_weather`: Usa Open-Meteo para obtener previsión meteorológica.
*   `get_current_location`: Geolocalización mediante IP pública (ip-api).

### Comunicaciones y Periferia (`EmailService` / `TelegramService`)
*   `email_list_inbox`: Lee las cabeceras de la bandeja de entrada vía IMAP.
*   `email_read`: Lee el cuerpo de un email purgado y sanitizado.
*   `email_send`: Redacta y envía correos vía SMTP.
*   `telegram_send`: Envía mensajes push directos al cliente de Telegram del usuario.

### Base Documental (DocMapper en `DocumentsService`)
*   `document_index`: Dispara asíncronamente la vectorización y análisis estructural de un fichero nuevo.
*   `document_search`: Búsqueda híbrida (categorías SQL + Similitud Coseno).
*   `document_search_by_categories`: Filtrado directo SQL.
*   `document_search_by_sumaries`: Búsqueda vectorial pura sobre los resúmenes del documento.
*   `get_document_structure`: Devuelve el XML jerárquico del índice del documento generado por IA.
*   `get_partial_document`: Expande el texto de una rama concreta del XML documental sin sobrecargar el contexto.


## 7. Construcción y Despliegue

La construcción se realiza mediante **Maven**. El objetivo principal es generar un ejecutable autocontenido (*Fat JAR*) que no dependa de infraestructura instalada.

*   En el `pom.xml`, se utiliza el `maven-shade-plugin`. Es destacable el uso del `ServicesResourceTransformer`, indispensable cuando se empaquetan librerías modulares y factorías de SPI como `java.sql` o componentes de Log4j2.
*   Existen filtros (`META-INF/*.SF`, `*.DSA`, `*.RSA`) para evitar conflictos de firmas digitales originados al fusionar dependencias.
*   El punto de entrada dual `io.github.jjdelcerro.noema.main.Main` admite parámetros (ej. `-c` para iniciar en modo Consola JLine3) o arranca por defecto la GUI Swing (FlatLaf). Un hilo paralelo inicializa un servidor web Javalin (en puerto configurable, default 8080) para exponer la misma interfaz por navegador usando Server-Sent Events (SSE).


## 8. Conclusión

**Noema** es un proyecto arquitectónicamente sofisticado diseñado con mentalidad "Do It Yourself". Al prescindir intencionalmente de infraestructuras pesadas en la nube o complejas bases de datos vectoriales, obliga a resolver los problemas cognitivos mediante diseño de software clásico e ingeniería de prompts (como su sistema de Checkpoints Markdown en capas, su búsqueda M-MaxP, y su enrutador asíncrono de eventos). 

Resulta una pieza de estudio brillante en cuanto al manejo de la "ilusión de consciencia y proactividad". La simulación de interrupciones asíncronas vía `pool_event`, el control de versiones silencioso integrado mediante [RCS](https://github.com/jjdelcerro/io.github.jjdelcerro.javarcs), y el cuidado extremo en la higiene del estado conversacional, convierten a este agente en un excelente y muy seguro compañero de reflexión técnica para largas jornadas.
