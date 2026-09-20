# Project Map

## Vigencia

- Commit: n/a (fuente local de trabajo)
- Versión: 0.1.0 (`pom.xml`)
- Fecha: 2026-09-18

## Identidad

Noema es un agente de software autónomo y proactivo diseñado para interactuar de forma continua con un entorno de trabajo técnico y su desarrollador. Combina ejecución desacoplada de herramientas en sandbox (sistema de archivos, shell bajo Firejail, control de versiones local JavaRCS y scripts Groovy) con un bucle sensorial asíncrono y reactivo. Su arquitectura cognitiva sustituye el paradigma de ventana de contexto infinita por una espiral de memoria jerárquica: retiene la verdad histórica inmutable en base de datos relacional y vectorial embebida (H2), sintetiza la memoria a largo plazo en relatos narrativos continuos con citas explícitas (`{cite:ID}`), y cura dinámicamente la memoria de trabajo proyectada hacia el LLM.

## Stack

- Java 25 (`--enable-native-access`, `--add-modules jdk.incubator.vector`) — Plataforma base — Permite computación vectorial acelerada para embeddings y operaciones nativas ONNX en el mismo proceso.
- LangChain4j (1.16.3 / beta26) — Abstracción de modelos y herramientas — Proporciona clientes unificados para proveedores OpenAI/OpenRouter, soporte de streaming con reasoning tokens, Jlama y MCP.
- H2 Database (2.2.224) — Almacenamiento SQL y BLOB vectorial embebido (`AUTO_SERVER=TRUE`) — Garantiza persistencia episódica sin dependencias externas y permite inspección concurrente en depuración.
- Apache Groovy (4.0.24) — Motor de scripting embebido con sandbox (`SecureASTCustomizer`) — Permite delegar cálculos masivos, agregaciones y consultas semánticas locales en código cliente en lugar de en el LLM.
- JavaRCS (`io.github.jjdelcerro.javarcs`) — Control de versiones delta histórico en ficheros `,jv` — Crea respaldos incrementales antes de modificaciones destructivas sobre archivos del usuario.
- Javalin (6.1.3) + SSE — Servidor web HTTP y streaming en tiempo real — Soporta modo headless desacoplado y alimenta la interfaz SPA sin requerir contenedores de servlets externos.
- Lanterna (3.1.2) + JLine 3 (3.21.0) — Frontend TUI y consola REPL interactiva — Proporciona interfaces ricas en terminal para sesiones remotas SSH o entornos headless.
- FlatLaf (3.4.1) + RSyntaxTextArea (3.6.1) — Frontend gráfico Swing de escritorio — Ofrece interfaz visual con resaltado sintáctico y editor de configuraciones integrado.
- Apache Tika (2.8.0) — Extracción de texto y detección MIME — Normaliza el contenido textual de documentos estructurados y binarios antes de la ingesta en memoria.
- ONNX Runtime GenAI (`io.github.inference4j`) — Modelos locales en proceso (SLM Qwen3.5-0.8B y MiniLM) — Ejecución offline de embeddings semánticos y subconsultas sin coste ni latencia de red.

## Mapa de módulos

- `io.github.jjdelcerro.noema.lib`: Contratos base, ciclo de vida del agente, control de acceso y parámetros del sistema → `Agent.java`, `AgentManager.java`, `AgentAccessControl.java`, `AgentTool.java`, `Subagent.java`.
- `io.github.jjdelcerro.noema.lib.memory`: Contratos de los subsistemas de memoria episódica, reciente, consolidada y proyectada → `EpisodicMemory.java`, `Turn.java`, `RecentMemory.java`, `ConsolidateMemory.java`, `ProjectedMemory.java`.
- `io.github.jjdelcerro.noema.lib.services`: Contratos de los servicios autónomos del agente → `ReasoningService.java`, `SensorsService.java`, `MemoryConsolidationService.java`.
- `io.github.jjdelcerro.noema.lib.settings`: Árbol de configuración desacoplado y evaluador dinámico → `AgentSettings.java`, `AgentSettingsGroup.java`, `AgentSettingsCheckedList.java`.
- `io.github.jjdelcerro.noema.lib.impl`: Implementación del agregado central del agente, fábricas de infraestructura y modelo de chat → `AgentImpl.java`, `AgentManagerImpl.java`, `AgentAccessControlImpl.java`, `ChatModelImpl.java`, `AgentPathsImpl.java`.
- `io.github.jjdelcerro.noema.lib.impl.memory.episodic`: Persistencia relacional y vectorial del histórico completo de turnos en H2 → `EpisodicMemoryImpl.java`, `TurnImpl.java`, `Counter.java`.
- `io.github.jjdelcerro.noema.lib.impl.memory.recent`: Gestión de ventana conversacional activa serializada en JSON → `RecentMemoryImpl.java`.
- `io.github.jjdelcerro.noema.lib.impl.memory.consolidate`: Persistencia híbrida (metadatos en H2, relatos narrativos en `.md`) de puntos de guardado → `ConsolidateMemoryImpl.java`.
- `io.github.jjdelcerro.noema.lib.impl.memory.projected`: Pipeline de operaciones de filtrado y curación del contexto previo al LLM → `ProjectedMemoryImpl.java`.
- `io.github.jjdelcerro.noema.lib.impl.memory.projected.operations`: Transformaciones cognitivas sobre la memoria proyectada → `TrimmingOperation.java`, `PendingAnnotationOperation.java`, `PinnedTurnsOperationImpl.java`, `TemporalPerceptionOperation.java`, `PeripheralAwarenessOperation.java`.
- `io.github.jjdelcerro.noema.lib.impl.services.reasoning`: Despacho de eventos, bucle deliberativo y registro de herramientas del agente → `ReasoningServiceImpl.java`, `ReasoningServiceFactory.java`.
- `io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.file`: Herramientas de consulta, inspección, parcheo y ejecución segura en sistema de archivos → `FileReadTool.java`, `FileWriteTool.java`, `FileGrepTool.java`, `FileFuzzyGrepTool.java`, `FilePatchTool.java`, `ShellExecuteTool.java`, `ReadPaginatedResourceTool.java`.
- `io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.skills`: Carga y ejecución de directivas procedimentales encapsuladas en `.claude/skills` → `ActivateSkillTool.java`, `DeactivateSkillTool.java`, `ListSkillsTool.java`, `RunSkillScriptTool.java`, `ReadSkillResourceTool.java`.
- `io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.subagent`: Coordinación e invocación de subagentes declarativos aislados → `LaunchSubagentTool.java`, `ListSubagentsTool.java`.
- `io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.scripting`: Ejecución de código Groovy embebido en la JVM → `ScriptExecuteTool.java`.
- `io.github.jjdelcerro.noema.lib.impl.services.sensors`: Bus sensorial multicanal con acumulación y despacho según la naturaleza del estímulo → `SensorsServiceImpl.java`, `DiscreteSensorData.java`, `MergeableSensorData.java`, `StateSensorData.java`, `UserSensorData.java`.
- `io.github.jjdelcerro.noema.lib.impl.services.memory`: Motor de compactación cognitiva de memoria a largo plazo e introspección histórica → `MemoryConsolidationServiceImpl.java`, `LookupTurnTool.java`, `SearchFullHistoryTool.java`, `AnnotateObservationTool.java`.
- `io.github.jjdelcerro.noema.lib.impl.services.embeddings`: Vectorización local ONNX, serialización y filtrado Top-K con chunking MaxP → `EmbeddingsService.java`, `EmbeddingFilterImpl.java`.
- `io.github.jjdelcerro.noema.lib.impl.services.mcp`: Conexión de clientes Model Context Protocol sobre transporte Stdio o HTTP/SSE → `McpServiceImpl.java`, `McpToolWrapper.java`.
- `io.github.jjdelcerro.noema.lib.impl.services.scheduler`: Planificación diferida persistente de alarmas sobre base de datos → `SchedulerServiceImpl.java`, `ScheduleAlarmTool.java`.
- `io.github.jjdelcerro.noema.lib.impl.services.email`: Sensor proactivo IMAP IDLE y cliente SMTP saneado por Tika → `EmailService.java`, `EmailSendTool.java`, `EmailReadTool.java`, `EmailListTool.java`.
- `io.github.jjdelcerro.noema.lib.impl.services.telegram`: Sensor reactivo y efector de notificaciones mediante Telegram Bot API → `TelegramService.java`, `TelegramTool.java`.
- `io.github.jjdelcerro.noema.lib.impl.scripting`: Entorno de evaluación Groovy con fachadas de sistema y limitación de I/O → `ScriptEngine.java`, `ScriptContext.java`, `FsModule.java`, `LlmModule.java`, `WebModule.java`, `AnnotationModule.java`.
- `io.github.jjdelcerro.noema.main`: Orquestación de arranque, despacho de interfaces de usuario y servidor web → `Main.java`, `MainWeb.java`, `MainLanterna.java`, `MainGUI.java`, `MainConsole.java`, `NoemaWebServer.java`, `BootUtils.java`.
- `io.github.jjdelcerro.noema.ui`: Fábrica de interfaces y consolas de usuario unificadas → `AgentUILocator.java`, `AgentUIManager.java`, `AgentConsole.java`.
- `io.github.jjdelcerro.noema.ui.lanterna`: Implementación de interfaz de usuario de texto (TUI) para consola → `MainLanternaWindow.java`, `HistoryChatBox.java`, `AgentLanternaSettingsImpl.java`.
- `io.github.jjdelcerro.noema.ui.swing`: Implementación de interfaz de escritorio Swing con pestañas de configuración y chat → `MainChatPanel.java`, `AgentSwingSettingsImpl.java`, `SimpleTextEditor.java`.
- `webapp`: Interfaz web SPA desacoplada consumidora de la API Javalin y SSE → `index.html`, `js/main.js`, `js/chat-ui.js`, `js/config-ui.js`, `js/api.js`.

## Contratos principales

- `Agent` (`AgentImpl`, `SubagentImpl`, `FakeAgent`): Núcleo de orquestación, gestión de servicios compartidos, ciclo de vida (`start`/`stop`), acceso a modelos de chat y sensores.
- `AgentManager` (`AgentManagerImpl`): Registro central de factorías de servicios, proveedores SQL, operaciones de memoria proyectada, subagentes activos y acciones de ciclo de vida.
- `AgentAccessControl` (`AgentAccessControlImpl`): Guardián de seguridad; valida rutas de lectura/escritura en el sandbox, aplica whitelist/blacklist y restringe ejecución por flags de configuración.
- `AgentService` (`AbstractAgentService`): Contrato de ciclo de vida de subsistemas acoplados al agente (`canStart`, `start`, `stop`, herramientas y parámetros del modelo).
  - `ReasoningService` (`ReasoningServiceImpl`): Bucle principal deliberativo, orquestación LLM, registro de herramientas y despacho de eventos.
  - `SensorsService` (`SensorsServiceImpl`): Ingesta, buffer y despacho de estímulos multicanal clasificados por `SensorNature`.
  - `MemoryConsolidationService` (`MemoryConsolidationServiceImpl`): Fusión en espiral de turnos episódicos en puntos de guardado narrativos con verificación de citas.
  - `SchedulerService` (`SchedulerServiceImpl`): Programación persistente y reprogramación de alarmas cronológicas.
  - `EmbeddingsService` (`EmbeddingsService`): Motor de vectorización en memoria y cálculo de distancia coseno MaxP.
  - `McpService` (`McpServiceImpl`): Gestor de conexiones cliente con servidores MCP externos.
  - `EmailService` (`EmailService`): Escucha pasiva de correos entrantes autorizados y despacho de envíos.
  - `TelegramService` (`TelegramService`): Canal bidireccional de mensajería con filtrado de `chatId`.
- `AgentTool` (`AbstractAgentTool` → `AbstractPaginatedAgentTool`): Definición de herramientas para el LLM con especificación de esquema, modo de acceso (`READ`, `WRITE`, `EXECUTION`, `SCRIPTING`) y política de recorte de salida.
- `EpisodicMemory` (`EpisodicMemoryImpl`, `FakeEpisodicMemory`): Repositorio maestro e inmutable de eventos pasados (`Turn`) y metadatos de consolidación sobre base de datos H2.
- `RecentMemory` (`RecentMemoryImpl`, `FakeRecentMemory`): Estado activo de la conversación por subcanal; rastrea el umbral de compactación y permite poda atómica de turnos.
- `ProjectedMemory` (`ProjectedMemoryImpl`): Proyección curada e inmutable de mensajes entregada al LLM tras evaluar el pipeline de operaciones de contexto.
- `ProjectedMemoryOperation` (`ProjectedMemoryOperationFactory`):
  - `PinnedTurnsOperationImpl`: Fija y reinyecta llamadas/respuestas de herramientas marcadas con `shouldPin()` y emite recordatorios periódicos.
  - `TrimmingOperation`: Poda el contenido de respuestas pesadas de herramientas cuando superan la ventana de atención reciente.
  - `PendingAnnotationOperation`: Detecta recursos leídos en zona de riesgo que no han sido consolidados con `annotate_observation` y genera advertencias efímeras.
  - `TemporalPerceptionOperation`: Notifica pausas temporales prolongadas entre interacciones.
  - `PeripheralAwarenessOperation`: Inyecta consciencia de actividad en otros subcanales concurrentes.
- `AgentSettings` (`AgentSettingsImpl` extiende `AgentSettingsGroupImpl`): Árbol jerárquico de ajustes en disco (`settings.json`) con evaluación de expresiones booleanas dinámicas (`ExpressionEvaluator`).
- `AgentConsole`: Canal abstracto de visualización y confirmación humana interactiva (`MainChatPanel`, `AgentLanternaConsoleImpl`, `AgentConsoleImpl`, `ServerAgentConsole`, `SseAgentConsole`).
- `Subagent` (`SubagentImpl`): Trabajador subordinado ejecutor de recetas XML (`SubagentDefinition`) en sandbox temporal aislado con base de datos propia.

## Modelo de datos

- Base de datos H2 Episódica (`.noema-agent/var/lib/episodic_memory.db`):
  - Tabla `episodicmemory`: Registro inmutable de cada turno del sistema.
    - `id INT PRIMARY KEY`: Identificador secuencial autoincremental gestionado por `Counter`.
    - `timestamp TIMESTAMP`: Fecha y hora de ocurrencia del evento.
    - `contenttype VARCHAR(50)`: Discriminador semántico (`chat`, `tool_execution`, `tool_execution_summarized`, `annotation`, `lookup_turn`).
    - `subchannel VARCHAR(20)`: Canal o terminal de procedencia (`default`, identificadores de sesión).
    - `annotation_type VARCHAR(100)`: Categoría opcional de clasificación para notas episódicas.
    - `text_user CLOB`, `text_thinking CLOB`, `text_model CLOB`: Cadenas textuales de prompt, razonamiento intermedio y respuesta final.
    - `tool_call CLOB`: JSON descriptivo de la invocación de herramienta.
    - `tool_result CLOB`: Resultado de ejecución de herramienta (truncado a 2KB si excede `MAX_DB_TEXT_SIZE`).
    - `embedding_blob BLOB`: Vector serializado en binario generado por el modelo de embeddings.
  - Tabla `consolidatememory`: Metadatos de puntos de guardado.
    - `id INT PRIMARY KEY`: Identificador único de consolidación.
    - `cm_first INT`, `cm_last INT`: Rango inclusivo de turnos cubiertos por el punto de guardado.
    - `timestamp TIMESTAMP`: Momento de consolidación.
    - `subchannel VARCHAR(20)`: Subcanal al que pertenece la consolidación.
- Base de datos H2 de Servicios (`.noema-agent/var/lib/service.db`):
  - Tabla `SCHEDULER`: Registro de alarmas programadas.
    - `id VARCHAR(255) PRIMARY KEY`: Clave única en formato `ALARM-<num>`.
    - `timestamp TIMESTAMP`: Momento de creación.
    - `alarm_time TIMESTAMP`: Momento programado para la ejecución.
    - `reason VARCHAR(1024)`: Motivo o texto de la alarma.
- Ficheros y almacenamiento estructurado en disco (`.noema-agent/var/`):
  - `lib/consolidatememory/consolidatememory-{id}-{first}-{last}.md`: Contenido textual completo del punto de guardado (secciones "Resumen" y "El Viaje").
  - `lib/recent_memory-{subchannel}.json`: Estado de la sesión conversacional activa (lista de mensajes y mapa de índices a turnos).
  - `lib/projected_memory_{subchannel}.json`: Estado persistente de operaciones de contexto (último turno notificado, turnos anclados).
  - `lib/sensors.json`: Memento con estadísticas de sensores, eventos encolados sin consumir y mapa de estados vigentes.
  - `config/settings.json`: Configuración de ajustes locales del workspace.
  - `config/settingsui.json`: Descriptor dinámico del árbol de configuración y formularios para la UI.
  - `config/*.properties`: Tablas de dominios para combos (`models.properties`, `providers_urls.properties`, `apikeys.properties`, `available_tools.properties`).
  - `var/tmp/`: Almacén de intercambio de herramientas paginadas (`out_*.out`, `find_*.tmp`, `grep_*.tmp`, `fuzzygrep_*.tmp`).
  - `,jv` (ficheros ocultos junto a archivos modificados): Registro de revisiones deltas de JavaRCS antes de escrituras destructivas.

## Flujos dominantes

### Flujo: Despacho e interacción de turno conversacional

1. El usuario envía un mensaje desde cualquiera de las interfaces activas (Web SSE, Swing, Lanterna o CLI) invocando `AgentImpl.putUsersMessage(subchannel, text, callback)`.
2. `SensorsServiceImpl.putEvent()` empaqueta el texto como `SensorEventUserImpl` (`SensorNature.USER`), actualiza estadísticas sensoriales y despierta el hilo del despachador mediante `sensorLock.notifyAll()`.
3. El hilo continuo `ReasoningServiceImpl.eventDispatcher()` obtiene el evento mediante `sensors.getEvent()` y delega en `processSingleEvent(event)`.
4. `RecentMemoryImpl.add()` recibe el mensaje de usuario. `ProjectedMemoryImpl.getMessages()` ensambla el contexto:
   - Añade el prompt de sistema base (`reasoning-system.md`) enriquecido con módulos activos de identidad (`var/identity/core/` y `var/identity/environ/`).
   - Inyecta el texto del último punto de guardado disponible (`ConsolidateMemoryImpl.getText()`).
   - Concatena los mensajes de trabajo de `RecentMemoryImpl`.
   - Ejecuta secuencialmente el pipeline de operaciones ordenado por prioridad: ancla turnos de skills (`PinnedTurnsOperationImpl`), poda salidas extensas de herramientas (`TrimmingOperation`), evalúa recursos sin anotar (`PendingAnnotationOperation`), verifica lapsos de tiempo (`TemporalPerceptionOperation`) y agrega consciencia de subcanales periféricos (`PeripheralAwarenessOperation`).
5. `ChatModelImpl.generate()` serializa la petición a formato compatible OpenAI y la transmite al modelo de lenguaje con soporte de streaming hacia `AgentConsole`.
6. Si el modelo devuelve solicitudes de herramientas (`hasToolExecutionRequests()`):
   - `ReasoningServiceImpl.executeTool()` evalúa la política de permisos mediante `AgentAccessControlImpl.isToolAllowed()`.
   - Si la herramienta modifica el entorno (`MODE_WRITE`, `MODE_EXECUTION`) y `humanConfirmationRequired` es verdadero, se solicita confirmación en `AgentConsole.confirm()`.
   - Se ejecuta la herramienta y se crea un turno en `EpisodicMemoryImpl.add()` con `contenttype: tool_execution` (o `annotation` / `lookup_turn`).
   - El resultado se inyecta en `RecentMemoryImpl` y el bucle vuelve a invocar `getModel().generate()`.
7. Si el modelo devuelve una respuesta textual final:
   - Se crea y persiste el turno de tipo `chat` en `EpisodicMemoryImpl`.
   - `RecentMemoryImpl.consolideTurn()` vincula los mensajes de la iteración al ID del turno persistido.
   - Si `RecentMemoryImpl.needConsolidation()` detecta que el número de turnos supera el umbral configurado (`reasoning/consolidation_turns`), se dispara la consolidación de memoria.
   - Se persiste el estado de memoria en disco (`recentMemory.save()`, `projectedMemory.save()`).
   - Se invoca `callback.onComplete()` para liberar el bloqueo visual en la interfaz de usuario.

### Flujo: Consolidación y compactación en espiral de memoria (Context Spiral)

1. Al superar el umbral de turnos en `RecentMemoryImpl.needConsolidation()`, `ReasoningServiceImpl` invoca `performConsolidation()`.
2. `RecentMemoryImpl.getConsolidateMark()` calcula un índice de corte cercano al 50% de los mensajes asegurando no fracturar bloques contiguos de herramientas ni llamadas paralelas.
3. Se recuperan los turnos ordenados entre `mark1` y `mark2` desde la base de datos vía `EpisodicMemoryImpl.getTurnsByIds()`.
4. `MemoryConsolidationServiceImpl.consolide()` recupera el último punto de guardado activo y construye un prompt structured en formato CSV con el esquema `code,timestamp,contenttype,text_user,text_model_thinking,text_model,tool_call,tool_result`.
5. Se invoca al modelo de consolidación (`MEMORY_MODEL_ID`) bajo las instrucciones de `memory-consolidation.md` para producir un nuevo documento compuesto por las secciones obligatorias `# Resumen` y `# El Viaje`.
6. `MemoryConsolidationServiceImpl` analiza el texto generado mediante expresiones regulares, extrae todas las referencias `{cite:ID}` y sustituye por `{badcite:ID}` cualquier ID alucinado que no pertenezca al conjunto de entrada válido.
7. Se instancia un nuevo `ConsolidateMemoryImpl`, se persiste el contenido textual en `.noema-agent/var/lib/consolidatememory/consolidatememory-{id}-{first}-{last}.md` y se registran los metadatos en la tabla H2 `consolidatememory`.
8. `RecentMemoryImpl.remove(mark1, mark2)` purga físicamente los mensajes antiguos ya consolidados del historial reciente en RAM y actualiza el archivo JSON en disco.

## Puntos de entrada

- CLI y selector de modo: `io.github.jjdelcerro.noema.main.Main`
  - `--console` / `-c`: Lanza `MainConsole` con REPL de JLine 3.
  - `--gui` / `-g` / `--swing`: Lanza `MainGUI` con ventana FlatLaf Swing.
  - `--web` / `-w` / `-s` / `--serve`: Lanza `MainWeb` como demonio headless Javalin.
  - `--tui` / `-t` (por defecto): Lanza `MainLanterna` con consola TUI basada en Lanterna.
- Servidor Web REST y SSE: `NoemaWebServer` (puerto configurable vía `server/port`, por defecto `8080`):
  - `POST /api/chat/{terminalId}`: Encolado de mensajes de usuario.
  - `GET /api/chat/{terminalId}/history`: Recuperación de historial no consolidado.
  - `GET /api/console/{terminalId}` (SSE): Streaming de respuestas, thinking, logs e incidencias.
  - `GET /api/config/ui`: Descriptor visual `settingsui.json`.
  - `GET /api/config/domains/{domainName}`: Carga de opciones `.properties`.
  - `POST /api/config/multivalue`: Evaluación batch de reglas de visibilidad y valores.
  - `GET / POST /api/config/<path>`: Lectura y modificación de parámetros de configuración.
  - `GET /api/fs/directories`: Navegador de carpetas locales del servidor.
  - `GET / POST /api/files/content`: Lector y escritor web para ficheros del agente (`var:/` o rutas absolutas).
  - `POST /api/actions/{actionName}`: Disparador de acciones del ciclo de vida (`COMPACT_REASONING_SESSION`, etc.).
- Suite de pruebas clave:
  - `TargetedRetrievalE2ETest`: Arbitraje de herramientas de búsqueda frente a paginación masiva.
  - `NeedleInHaystackE2ETest`: Resiliencia de memoria bajo ingesta de miles de líneas y compactación forzada.
  - `RecentMemoryConsolidationBoundaryTest`: Pruebas de frontera en el corte atómico de turnos con herramientas múltiples.
  - `FileFuzzySearchUtilsTest`: Precisión y ventanas deslizantes de la búsqueda semántica local.

## Build, run, test

```bash
# Compilación completa y empaquetado del Uber-JAR sombreado
mvn clean package

# Ejecución de la suite completa de pruebas unitarias y de integración
mvn test

# Ejecución de un test unitario o de integración específico
mvn test -Dtest=FileGrepToolTest
mvn test -Dtest=RecentMemoryConsolidationBoundaryTest
mvn test -Dtest=FileFuzzySearchUtilsTest

# Ejecución de pruebas End-to-End con LLMs reales (requiere ~/.noema-tests.properties)
mvn test -Dtest=TargetedRetrievalE2ETest
mvn test -Dtest=NeedleInHaystackE2ETest

# Ejecución del agente empaquetado mediante el script lanzador
./noema --tui
./noema --gui
./noema --web
./noema --console

# Depuración con socket JDWP remoto activo en puerto 8765
./noema --debug --web
```

## Convenciones

- Aislamiento de configuración: Los ajustes del agente residen exclusivamente en `.noema-agent/` dentro de la carpeta del proyecto. Ajustes transversales del usuario residen en `~/.config/noema-agent/`.
- Gestión de rutas relativas: Se resuelven estrictamente contra el workspace raíz mediante `AgentPaths` y se normalizan con barras inclinadas hacia adelante (`/`).
- Protocolo de herramientas de lectura paginada: Todas las herramientas que extienden `AbstractPaginatedAgentTool` devuelven una cabecera delimitada por `---`. La cabecera incluye `STATUS`, `RESOURCE_ID`, `EMPTY`, `LINE_RANGE`, `TOTAL_LINES` y un campo `HINT` con la invocación exacta para continuar leyendo mediante `read_paginated_resource`.
- Prevención de pérdida de datos en escritura: Cualquier herramienta que sobrescriba o aplique parches a ficheros (`FileWriteTool`, `FilePatchTool`, `FileSearchAndReplaceTool`, `FsModule`) realiza un check-in preventivo en JavaRCS si el archivo preexistía.
- Modos de acceso y permisos: Las herramientas declaran su modo (`MODE_READ`, `MODE_WRITE`, `MODE_WEB`, `MODE_EXECUTION`, `MODE_SCRIPTING`). `AgentAccessControlImpl` inhabilita herramientas en tiempo de ejecución si la configuración del workspace bloquea esa modalidad.
- Identidad de Sensores: Los sensores declaran una naturaleza (`SensorNature`):
  - `DISCRETE`: Eventos independientes atómicos (cada uno genera un turno).
  - `MERGEABLE`: Estímulos continuos (se concatenan en un solo bloque con marcas de tiempo).
  - `AGGREGATABLE`: Volumen de eventos (se cuenta el número de ocurrencias).
  - `STATE`: Condiciones volátiles (solo prevalece el último valor recibido).
  - `USER`: Entrada de usuario prioritaria.

## Decisiones arquitectónicas

- Sustitución de ventanas infinitas por el Principio de la Espiral de Contexto: En lugar de saturar la ventana de atención con decenas de miles de tokens, el historial se sintetiza periódicamente en un relato narrativo cronológico que enlaza eventos detallados mediante citas `{cite:ID}`. Se gana fidelidad cognitiva y se reducen costes de inferencia.
- Inversión de Control Simulada para Sensores: Los LLMs son pasivos y no admiten interrupciones asíncronas. Todo evento sensorial externo (Telegram, Email, reloj, finalización de subagentes) se inyecta en la memoria reciente simulando que el modelo realizó una consulta a una herramienta ficticia llamada `pool_event`, preservando la alternancia estricta del protocolo de mensajes (`User` → `AI` → `ToolExecutionResult`).
- Memoria Proyectada Desacoplada: La sesión física en disco (`RecentMemoryImpl`) no se envía directamente al modelo. Se pasa por una capa de proyección en memoria (`ProjectedMemoryImpl`) que ejecuta operaciones de amnesia selectiva (`TrimmingOperation`), recordatorios periódicos (`PinnedTurnsOperation`) y advertencias de no-consolidación (`PendingAnnotationOperation`).
- Delegación Algorítmica en Groovy Embebido: El LLM tiene prohibido realizar sumas, análisis estadísticos o filtrado masivo en su atención. Para ello cuenta con `execute_script`, que evalúa código Groovy en la JVM con acceso a streaming de archivos (`agent.fs.lines`), búsqueda semántica y consultas a modelos pequeños en proceso.
- Ejecución segura de comandos con Firejail: Cuando el binario `firejail` está disponible en el host Linux, `ShellExecuteTool` confina la ejecución restringiendo el acceso de escritura al workspace, aislando la carpeta `home` del agente en un sandbox virtual y bloqueando el acceso a `.noema-agent/var/lib`.

## Zonas sensibles

- Atomicidad de corte en `RecentMemoryImpl.getConsolidateMark()`: Si el punto de corte cae entre un `AiMessage` que disparó llamadas a herramientas en paralelo y sus correspondientes `ToolExecutionResultMessage`, la memoria reciente quedará corrupta y los proveedores de LLM rechazarán la conversación con un error HTTP 400. La lógica de avance forzado para consumir resultados contiguos es crítica.
- Sandbox de Groovy en `ScriptEngine.java`: `SecureASTCustomizer` bloquea importaciones de `System`, `Runtime` y `ProcessBuilder`, pero dado que `java.io.*` está disponible por defecto en Groovy, un script generado por el LLM podría instanciar `new File(...)` evadiendo `AgentAccessControlImpl` si no se añaden restricciones adicionales en el AST (documentado en `notas1.md`).
- Orden de enrutamiento en `NoemaWebServer.java`: Javalin evalúa rutas en orden de registro. Endpoints específicos como `/api/config/ui`, `/api/config/multivalue` o `/api/config/domains/{domainName}` deben registrarse obligatoriamente antes que el comodín `/api/config/<path>`.
- Sincronización de dependencias Jackson: La coexistencia de Apache Tika 2.8.0 y LangChain4j 1.16.3 provoca conflictos binarios si no se fijan explícitamente las versiones de `jackson-databind`, `jackson-core` y `jackson-annotations` en `2.17.2`.

## Trampas conocidas

- `Session` vs `RecentMemory` / `SourceOfTruth` vs `EpisodicMemory`: Existieron refactorizaciones terminológicas en el núcleo. La interfaz pública de historial activo es `RecentMemory` (antes `Session`), y el repositorio H2 es `EpisodicMemory` (antes `SourceOfTruth`). Quedan rastros documentales y comentarios que mencionan los nombres antiguos.
- `search_full_history` vs `fetch_citation`: Las herramientas devuelven directamente el contenido íntegro del turno almacenado en la columna `text`. Si el modelo ejecuta `search_full_history`, no necesita invocar posteriormente `fetch_citation` sobre los códigos devueltos.
- Prefijos de Resource ID (`tmp://`, `cache://`, `user://`): En `AbstractPaginatedAgentTool`, los recursos efímeros (`tmp://`) tienen ciclo de vida acotado y pueden ser eliminados por la política LRU de `ShellExecuteTool` o reinicios del proceso. Si el modelo intenta paginar un recurso expirado, recibirá un mensaje de error indicándole que debe regenerar el recurso original.
- Herramientas deshabilitadas por configuración: Desmarcar una herramienta en la UI no la desregistra de `ReasoningServiceImpl`, sino que conmuta su flag interno `active = false` o la bloquea mediante `AgentAccessControlImpl.isToolAllowed()`, evitando que su esquema JSON sea emitido al LLM.

## Glosario del dominio

- *Turno (`Turn`):* Unidad atómica e inmutable de interacción o evento persistida en la tabla `episodicmemory` de H2, identificada por un número entero secuencial (`code`).
- *Subcanal (`subchannel`):* Identificador del terminal o hilo conversacional concurrente (por ejemplo, `default`, sesiones web o canales remotos).
- *ConsolidateMemory:* Punto de guardado a largo plazo que fusiona el resumen ejecutivo previo con la crónica narrativa ("El Viaje"), verificando citas históricas.
- *Memoria Reciente (`RecentMemory`):* Estructura conversacional en memoria y JSON local que retiene los últimos turnos sin consolidar de un subcanal específico.
- *Memoria Proyectada (`ProjectedMemory`):* Colección inmutable y curada de mensajes construida al vuelo para ser transmitida al LLM en cada petición.
- *Trimming:* Proceso de amnesia selectiva que reemplaza el cuerpo extenso de una herramienta antigua por la cabecera `CONTENT_TRIMMED: true` para ahorrar espacio en la ventana de contexto.
- *Pinned Turn:* Par de petición y resultado de herramienta marcado con `shouldPin()` que se mantiene protegido al inicio de la proyección contextual para no perder directivas técnicas activas.
- *Friso Histórico:* Principio narrativo por el cual la actualización de un punto de guardado debe fundirse orgánicamente con el texto anterior, impidiendo distinguir dónde terminaba el pasado y dónde comenzaba el nuevo lote conversacional.

## Índice de búsqueda

- Si buscas cómo se ensambla el contexto final enviado al modelo de lenguaje: `ProjectedMemoryImpl.java`
- Si buscas la lógica de corte atómico para compactar memoria: `RecentMemoryImpl.java:getConsolidateMark`
- Si buscas el prompt y directivas del proceso de compactación cognitiva: `memory-consolidation.md`
- Si buscas el guardián de permisos y aislamiento del sandbox de archivos: `AgentAccessControlImpl.java`
- Si buscas el bucle principal de consciencia y despacho de eventos: `ReasoningServiceImpl.java:eventDispatcher`
- Si buscas la ingestión y acumulación multicanal de eventos sensoriales: `SensorsServiceImpl.java`
- Si buscas las herramientas de manipulación de archivos y paginación: `AbstractPaginatedAgentTool.java`
- Si buscas la integración y llamadas al motor local de embeddings y similitud MaxP: `EmbeddingsService.java`
- Si buscas el aislamiento y ejecución de subagentes declarativos: `SubagentImpl.java`
- Si buscas las reglas de seguridad del entorno Groovy embebido: `ScriptEngine.java` y `ScriptContext.java`
- Si buscas las rutas REST y eventos Server-Sent Events de la interfaz web: `NoemaWebServer.java`

## Estado actual

- Versión: 0.1.0 activa.
- Módulos operativos: Razonamiento, Memoria episódica H2, Consolidación cognitiva, Sensores (Reloj, Notificaciones, Telegram, Email, Planificador), Subagentes declarativos XML, Skills procedimentales, Scripting Groovy, Soporte MCP y Web UI SPA.
- Deuda técnica y tareas abiertas:
  - Robustecer el sandbox AST en `ScriptEngine.java` bloqueando tipos de E/S directa (`java.io.File`, `java.nio.file.*`) para forzar todo acceso a través de `agent.fs`.
  - Rehidratar adecuadamente los subcanales en servicios de fondo (`SchedulerServiceImpl`, `EmailService`, `TelegramService`), que actualmente inyectan eventos apuntando al canal `"default"`.
  - Terminar la implementación del método `ConsolidateMemoryImpl.getSummary()` para alimentar resúmenes rápidos a `PeripheralAwarenessOperation`.
  - Investigar la tolerancia a fallos del modelo de chat cuando el primer mensaje de una conversación es un evento simulado de `pool_event`.
