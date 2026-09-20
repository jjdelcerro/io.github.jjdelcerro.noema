# Project Map

## Retrato del autor

El autor de este código piensa en español pero escribe en inglés. No es una observación trivial: es la clave de su estilo. Cada vez que el lenguaje le obliga a nombrar algo, traduce. No traduce del español al inglés con diccionario, traduce con criterio. Los términos que elige no son los que usaría un ingeniero de software anglosajón formado en la escuela de Java. Son los que usaría alguien que ha leído a Aristóteles, a Hume y a Kant antes que a Martin Fowler, y que luego ha aprendido a programar. Por eso la arquitectura de Noema no se organiza en capas, servicios y repositorios, sino en memorias: episódica, reciente, proyectada, consolidada. El autor no está construyendo una aplicación; está modelando una mente. O, más exactamente, está construyendo el andamiaje para que una mente emerja de la interacción entre un modelo estadístico y un conjunto de mecanismos de gestión de la atención.

Esa metáfora cognitiva no es decorativa. Atraviesa todo el proyecto. Cuando el autor necesita un componente que agrupe los turnos recientes de una conversación, lo llama `RecentMemory`. Cuando necesita que ese componente se destile en un relato narrativo, lo llama `ConsolidateMemory`. Cuando necesita que un operador decida qué mensajes se envían al modelo en un momento dado, lo llama `ProjectedMemory`. No son nombres técnicos; son nombres de procesos psicológicos. El autor podría haber usado `ConversationBuffer`, `SummaryStore` y `ContextBuilder`, que es lo que habría hecho un desarrollador convencional. No lo hizo. Eligió el vocabulario de la ciencia cognitiva porque piensa en el agente como un sistema que atiende, recuerda, olvida y consolida. Esa elección revela una formación que excede la ingeniería: hay lectura de psicología, de filosofía de la mente, de teoría de sistemas. El código es el lugar donde esa formación se vuelve operativa.

El segundo rasgo que emerge al leer los identificadores es la obsesión por la trazabilidad. Cada decisión, cada turno, cada anotación tiene un identificador único. Cada intervención de una herramienta deja una huella en la base de datos. Cada memoria consolidada cita los turnos exactos de los que proviene. El autor no confía en el contexto del modelo. Sabe que la ventana de atención es frágil, que el olvido es inevitable, que la única forma de que un agente no alucine es poder volver al registro original. Por eso construye un sistema de citas (`{cite:123}`) que actúa como un mecanismo de recuperación determinista. Esa desconfianza no es paranoia; es una postura de ingeniería. El autor ha interiorizado que un LLM no es una base de datos y que la memoria de un agente no puede depender de que el modelo recuerde. Hay que externalizarla, indexarla y recuperarla quirúrgicamente. Los nombres de las herramientas de memoria son elocuentes: `fetch_citation`, `search_full_history`, `annotate_observation`. No son `get_context`, `search` ni `save`. Son operaciones cognitivas deliberadas: recuperar una cita, buscar en la historia completa, anotar una observación. El autor piensa en el agente como un investigador que consulta un archivo, no como un chatbot que mantiene un buffer.

La tercera huella es la desconfianza sistemática hacia el código generado por el propio modelo. El autor ha implementado un sandbox para los scripts Groovy, ha puesto límites al número de llamadas al LLM por script, ha creado un control de acceso que restringe la escritura a ciertas rutas y la ejecución de comandos. No es una precaución genérica. Es la respuesta de alguien que ha visto demasiadas alucinaciones y ha decidido que la única forma de dormir tranquilo es que el agente no pueda hacer daño. Los nombres de las clases de seguridad son igualmente significativos: `AgentAccessControl`, `SecureASTCustomizer`, `Firejail`. El autor no usa `PermissionManager` ni `Policy`. Usa `AccessControl` porque piensa en términos de sujetos y recursos; usa `SecureASTCustomizer` porque conoce Groovy lo suficiente como para saber que la personalización del AST es la puerta de entrada. Es un autor que ha leído la documentación y ha entendido los agujeros, no solo la API.

El cuarto rasgo es la mezcla de registros. En los comentarios de código, el autor escribe en español, sin tildes, con un tono telegráfico. Usa `TODO`, `FIXME`, `OJO`, `CUIDADO`. En los nombres de las interfaces, el registro es solemne, casi académico: `EpisodicMemory`, `ConsolidateMemory`, `PeripheralAwarenessOperation`. En los nombres de las herramientas, el registro es funcional y directo: `file_read`, `file_write`, `shell_execute`. Esa variación no es descuido; es adaptación al lector. El autor sabe que un nombre de herramienta lo va a leer un LLM, y que necesita ser inequívoco. Sabe que un nombre de interfaz lo va a leer un humano, y que puede permitirse una metáfora. Sabe que un comentario lo va a leer él mismo dentro de seis meses, y que debe ser breve y en su lengua. Es un políglota de registros.

Hay contradicciones. El autor usa `Impl` para todas las implementaciones, pero también usa `Abstract` para las clases base. Usa `Factory` para las factorías, pero a veces las factorías no hacen más que instanciar una clase concreta. Usa `Manager` para gestores, pero el `AgentManager` es un registro de servicios más que un gestor de agentes. Esas inconsistencias son informativas. Revelan que el autor no sigue un manual de estilo, sino que va resolviendo cada caso con el criterio del momento. Cuando el patrón es claro, lo aplica; cuando no, improvisa. Es un autor pragmático, no dogmático.

La forma en que nombra las operaciones de memoria proyectada es especialmente reveladora. `TrimmingOperation`, `PendingAnnotationOperation`, `TemporalPerceptionOperation`, `PeripheralAwarenessOperation`, `PinnedTurnsOperation`. No son operaciones genéricas; son funciones cognitivas específicas. El autor ha descompuesto la tarea de proyectar el contexto en una serie de pasos que un psicólogo reconocería: recortar lo irrelevante, recordar lo no anotado, percibir el paso del tiempo, mantener conciencia de otros canales, fijar turnos importantes. Cada operación tiene una prioridad y se ejecuta en orden. Es un pipeline de atención. El autor no está construyendo un simple gestor de contexto; está construyendo una arquitectura de la cognición. Y la construye con nombres que un LLM puede interpretar sin ambigüedad, porque las descripciones de las herramientas son igualmente densas y precisas.

En resumen, el autor es un arquitecto de software con formación humanística, que piensa en español y escribe en inglés, que desconfía de la magia de los frameworks y de la memoria de los modelos, que modela el agente como una mente y la memoria como un proceso, y que deja en los nombres la huella de sus lecturas. Su estilo de nombrado no es una convención; es una filosofía. Para tomar decisiones nuevas, hay que preguntarse: ¿esto es una memoria, una percepción, una operación cognitiva? Si es una herramienta, el nombre debe ser un verbo en imperativo y en inglés. Si es una interfaz, el nombre debe ser un sustantivo del dominio, sin prefijos. Si es una implementación, `Impl`. Si es un servicio, `Service`. Si es una factoría, `Factory`. Si es una operación de memoria, `Operation`. Si es un evento, `Event`. Si es un sensor, `Sensor`. Si es un canal, `channel`. Si es un subcanal, `subchannel`. No hay excepciones. El autor puede perdonar una inconsistencia, pero no una traición a la metáfora.

## Vigencia

- Commit: (verificar con `git rev-parse HEAD`)
- Fecha: 2026-09-19

## Stack

- Java 25 — lenguaje base; uso de `--add-modules jdk.incubator.vector` y `--enable-native-access=ALL-UNNAMED`.
- Maven — construcción y empaquetado con `maven-shade-plugin` para fat jar.
- LangChain4j — integración con LLM, herramientas, embeddings y MCP.
- H2 — base de datos embebida para memoria episódica, consolidada y servicios.
- Gson — serialización JSON de configuración, memorias y estados.
- Swing + FlatLaf + MigLayout — interfaz gráfica de escritorio.
- Lanterna — interfaz TUI.
- JLine — consola interactiva.
- Javalin — servidor web embebido y SSE para la interfaz web.
- Groovy — motor de scripting embebido y sandbox.
- ONNX Runtime + modelos cuantizados — embeddings locales y modelo SLM.
- Apache Tika — extracción de texto de documentos binarios.
- JavaRCS — control de versiones para backups automáticos.
- Natty — parseo de fechas en lenguaje natural para alarmas.
- CommonMark — renderizado de Markdown en Swing.
- RSyntaxTextArea — editor de texto con resaltado.
- Firejail — sandbox opcional para ejecución de shell.
- Log4j2 — logging.

## Mapa de módulos

- `io.github.jjdelcerro.noema.lib` — interfaces y contratos del núcleo del agente → `Agent.java`, `AgentManager.java`, `AgentTool.java`, `AgentService.java`, `AgentPaths.java`, `AgentSettings.java`, `AgentConsole.java`, `AgentAccessControl.java`, `Subagent.java`, `SubagentDefinition.java`.
- `io.github.jjdelcerro.noema.lib.memory` — contratos de memoria → `EpisodicMemory.java`, `RecentMemory.java`, `ProjectedMemory.java`, `ConsolidateMemory.java`, `Turn.java`.
- `io.github.jjdelcerro.noema.lib.memory.projected` — operaciones de proyección → `ProjectedMemoryOperation.java`, `ProjectedMemoryOperationFactory.java`.
- `io.github.jjdelcerro.noema.lib.memory.projected.operations` — operaciones específicas → `PinnedTurnsOperation.java`.
- `io.github.jjdelcerro.noema.lib.services` — contratos de servicios → `ReasoningService.java`, `MemoryConsolidationService.java`, `SensorsService.java`.
- `io.github.jjdelcerro.noema.lib.settings` — contratos de configuración → `AgentSettings.java`, `AgentSettingsGroup.java`, `AgentSettingsItem.java`.
- `io.github.jjdelcerro.noema.lib.spi` — clases base para servicios → `AbstractAgentService.java`.
- `io.github.jjdelcerro.noema.lib.impl` — implementaciones del núcleo → `AgentImpl.java`, `AgentManagerImpl.java`, `AgentAccessControlImpl.java`, `AgentPathsImpl.java`, `AgentActionsImpl.java`, `ChatModelImpl.java`, `ModelParametersImpl.java`, `SQLProviderImpl.java`, `SubagentImpl.java`, `SubagentDefinitionImpl.java`, `AbstractAgentTool.java`, `AbstractPaginatedAgentTool.java`, `ToolSpecificationBuilder.java`, `DateUtils.java`, `SLMUtils.java`, `AgentUtils.java`, `ExpressionEvaluator.java`, `FileFuzzySearchUtils.java`.
- `io.github.jjdelcerro.noema.lib.impl.memory` — implementaciones de memoria → `recent/RecentMemoryImpl.java`, `projected/ProjectedMemoryImpl.java`, `projected/operations/*`, `episodic/EpisodicMemoryImpl.java`, `episodic/TurnImpl.java`, `episodic/Counter.java`, `consolidate/ConsolidateMemoryImpl.java`, `GsonUtils.java`.
- `io.github.jjdelcerro.noema.lib.impl.services` — implementaciones de servicios → `reasoning/ReasoningServiceImpl.java`, `reasoning/ReasoningServiceFactory.java`, `reasoning/tools/*`, `memory/MemoryConsolidationServiceImpl.java`, `memory/MemoryConsolidationServiceFactory.java`, `sensors/SensorsServiceImpl.java`, `sensors/SensorsServiceFactory.java`, `sensors/tools/*`, `sensors/nature/*`, `embeddings/EmbeddingsService.java`, `embeddings/EmbeddingsServiceFactory.java`, `embeddings/EmbeddingFilterImpl.java`, `scheduler/SchedulerServiceImpl.java`, `scheduler/SchedulerServiceFactory.java`, `scheduler/tools/ScheduleAlarmTool.java`, `email/EmailService.java`, `email/EmailServiceFactory.java`, `email/tools/*`, `telegram/TelegramService.java`, `telegram/TelegramServiceFactory.java`, `telegram/tools/TelegramTool.java`, `mcp/McpServiceImpl.java`, `mcp/McpServiceFactory.java`, `mcp/McpToolWrapper.java`.
- `io.github.jjdelcerro.noema.lib.impl.scripting` — scripting Groovy → `ScriptEngine.java`, `ScriptContext.java`, `ScriptModule.java`, `AbstractScriptModule.java`, `modules/FsModule.java`, `modules/LlmModule.java`, `modules/WebModule.java`, `modules/AnnotationModule.java`, `modules/SessionStateModule.java`, `modules/SubagentsModule.java`.
- `io.github.jjdelcerro.noema.lib.impl.skills` — sistema de skills → `Skill.java`, `SkillUtils.java`.
- `io.github.jjdelcerro.noema.main` — puntos de entrada → `Main.java`, `MainGUI.java`, `MainConsole.java`, `MainLanterna.java`, `MainWeb.java`, `BootUtils.java`, `NoemaWebServer.java`.
- `io.github.jjdelcerro.noema.ui` — interfaces de UI → `AgentUIManager.java`, `AgentUISettings.java`, `AgentUILocator.java`.
- `io.github.jjdelcerro.noema.ui.swing` — UI Swing → `MainChatPanel.java`, `WelcomePanel.java`, `SimpleTextEditor.java`, `DebugPanel.java`, `JMarkdownPanel.java`, `AgentSwingConsoleControllerUsingMultipleJTextPane.java`, `settings/*`.
- `io.github.jjdelcerro.noema.ui.lanterna` — UI TUI → `MainLanternaWindow.java`, `HistoryChatBox.java`, `ColoredHistoryRenderer.java`, `ScrollPanel.java`, `AgentLanternaConsoleImpl.java`, `settings/*`.
- `io.github.jjdelcerro.noema.ui.console` — UI consola → `AgentConsoleImpl.java`, `AgentConsoleManagerImpl.java`, `AgentConsoleSettingsImpl.java`.

## Contratos principales

- `Agent` — entidad central; implementación por defecto `AgentImpl`.
- `AgentManager` — registro y factoría de agentes, servicios, acciones y subagentes; implementación `AgentManagerImpl`.
- `AgentService` — servicio del agente; implementaciones en `AbstractAgentService` → `ReasoningServiceImpl`, `MemoryConsolidationServiceImpl`, `SensorsServiceImpl`, `EmbeddingsService`, `SchedulerServiceImpl`, `EmailService`, `TelegramService`, `McpServiceImpl`.
- `AgentServiceFactory` — factoría de servicios; implementaciones en cada servicio.
- `AgentTool` — herramienta del agente; implementaciones en `AbstractAgentTool` y `AbstractPaginatedAgentTool` → múltiples herramientas.
- `AgentConsole` — abstracción de consola; implementaciones `AgentConsoleImpl`, `SwingAgentConsole`, `LanternaConsole`, `SseAgentConsole`.
- `AgentAccessControl` — control de acceso; implementación `AgentAccessControlImpl`.
- `AgentSettings` — configuración jerárquica; implementación `AgentSettingsImpl`.
- `AgentPaths` — rutas del agente; implementación `AgentPathsImpl`.
- `EpisodicMemory` — memoria a largo plazo; implementación `EpisodicMemoryImpl`.
- `RecentMemory` — memoria de trabajo; implementación `RecentMemoryImpl`.
- `ProjectedMemory` — proyección de contexto; implementación `ProjectedMemoryImpl`.
- `ConsolidateMemory` — memoria consolidada; implementación `ConsolidateMemoryImpl`.
- `Turn` — unidad de interacción; implementación `TurnImpl`.
- `ProjectedMemoryOperation` — operación del pipeline de proyección; implementaciones en `impl.memory.projected.operations`.
- `SensorsService` — sistema sensorial; implementación `SensorsServiceImpl`.
- `ReasoningService` — orquestador del razonamiento; implementación `ReasoningServiceImpl`.
- `MemoryConsolidationService` — servicio de consolidación; implementación `MemoryConsolidationServiceImpl`.
- `Subagent` — trabajador aislado; implementación `SubagentImpl`.
- `SubagentDefinition` — definición declarativa; implementación `SubagentDefinitionImpl`.

## Modelo de datos

- `episodicmemory` — tabla principal de turnos.
  - `id INT PRIMARY KEY`
  - `timestamp TIMESTAMP`
  - `contenttype VARCHAR(50)`
  - `subchannel VARCHAR(20)`
  - `annotation_type VARCHAR(100)`
  - `text_user CLOB`
  - `text_thinking CLOB`
  - `text_model CLOB`
  - `tool_call CLOB`
  - `tool_result CLOB`
  - `embedding_blob BLOB`
  - Invariantes: `id` autoincremental, `timestamp` no nulo, `contenttype` define el tipo de turno (`chat`, `tool_execution`, `lookup_turn`, `annotation`, etc.).
- `consolidatememory` — metadatos de memorias consolidadas.
  - `id INT PRIMARY KEY`
  - `cm_first INT`
  - `cm_last INT`
  - `timestamp TIMESTAMP`
  - `subchannel VARCHAR(20)`
  - Invariantes: `cm_first` y `cm_last` definen el rango de turnos consolidados.
- `SCHEDULER` — alarmas programadas.
  - `id VARCHAR(255) PRIMARY KEY`
  - `timestamp TIMESTAMP`
  - `alarm_time TIMESTAMP`
  - `reason VARCHAR(1024)`

## Flujos dominantes

### Flujo: Mensaje de usuario → respuesta del agente

1. `MainGUI`/`MainConsole`/`MainWeb` recibe mensaje y llama a `Agent.putUsersMessage(subchannel, text, callback)`.
2. `SensorsServiceImpl.putEvent(USER, ...)` encola el evento y notifica al dispatcher.
3. `ReasoningServiceImpl.eventDispatcher()` consume el evento con `getEvent()`.
4. `ReasoningServiceImpl.processSingleEvent()` crea un `UserMessage` y lo añade a `RecentMemory`.
5. Bucle de razonamiento: `ProjectedMemory.getMessages()` construye contexto con `RecentMemory`, `ConsolidateMemory` y operaciones de proyección.
6. `ChatModelImpl.generate()` envía al LLM y recibe `AiMessage`.
7. Si hay llamadas a herramientas, `executeTool()` las ejecuta, persiste turnos en `EpisodicMemory` y añade resultados a `RecentMemory`.
8. Cuando no hay más herramientas, se persiste el turno final `chat` en `EpisodicMemory`.
9. Si `RecentMemory.needConsolidation()` es verdadero, se dispara `MemoryConsolidationServiceImpl.consolide()`.
10. El callback `onComplete()` notifica a la UI.

### Flujo: Consolidación de memoria

1. `RecentMemoryImpl.needConsolidation()` evalúa número de turnos únicos.
2. `ReasoningServiceImpl.performConsolidation()` obtiene `oldestMark` y `consolidateMark`.
3. Recupera turnos de `EpisodicMemory` entre los IDs.
4. `MemoryConsolidationServiceImpl.consolide()` construye prompt con `ConsolidateMemory` previo y CSV de turnos.
5. Llama al LLM de consolidación y obtiene texto con citas `{cite:ID}`.
6. Crea nuevo `ConsolidateMemory` y lo persiste en `EpisodicMemory` y disco.
7. Elimina los turnos consolidados de `RecentMemory`.

### Flujo: Ejecución de herramienta paginada

1. LLM invoca `file_read` o `shell_execute`.
2. `AbstractPaginatedAgentTool.execute()` resuelve `resource_id` y llama a `servePaginatedResource()`.
3. Se lee el recurso, se pagina en bloques de 1000 líneas por defecto.
4. Se devuelve cabecera con `STATUS`, `RESOURCE_ID`, `LINE_RANGE`, `TOTAL_LINES`, `HINT`.
5. El LLM puede llamar a `read_paginated_resource` con el `HINT` para obtener el siguiente bloque.

## Puntos de entrada

- `io.github.jjdelcerro.noema.main.Main` — selector de modo: `--gui`, `--console`, `--tui`, `--web`.
- `io.github.jjdelcerro.noema.main.MainGUI` — interfaz Swing.
- `io.github.jjdelcerro.noema.main.MainConsole` — consola JLine.
- `io.github.jjdelcerro.noema.main.MainLanterna` — interfaz TUI Lanterna.
- `io.github.jjdelcerro.noema.main.MainWeb` — servidor web Javalin headless.
- Endpoints REST en `NoemaWebServer`: `POST /api/chat/{terminalId}`, `GET /api/chat/{terminalId}/history`, `GET /api/console/{terminalId}` (SSE), `GET/POST /api/config/*`, `GET /api/fs/directories`, `POST /api/actions/{actionName}`, `GET/POST /api/files/content`.
- Tests: `ReasoningServiceTest`, `SensorsServiceTest`, `EmbeddingsServiceTest`, `AgentAccessControlTest`, `FileReadToolTest`, `FileGrepToolTest`, `LookupTurnToolTest`, `SearchFullHistoryToolTest`, `ReprojectionMemoryTest`, `RecentMemoryConsolidationBoundaryTest`, `TargetedRetrievalE2ETest`, `NeedleInHaystackE2ETest`.

## Build, run, test

- Compilar: `mvn clean package`
- Ejecutar GUI: `java -jar target/io.github.jjdelcerro.noema.main-*.jar --gui`
- Ejecutar TUI: `java -jar target/io.github.jjdelcerro.noema.main-*.jar --tui`
- Ejecutar consola: `java -jar target/io.github.jjdelcerro.noema.main-*.jar --console`
- Ejecutar web: `java -jar target/io.github.jjdelcerro.noema.main-*.jar --web`
- Ejecutar tests: `mvn test`
- Test individual: `mvn test -Dtest=NombreTest`
- Test E2E: `mvn test -Dtest=TargetedRetrievalE2ETest -Dtag=e2e` (requiere `~/.noema-tests.properties`).

## Estilo de nombrado de identificadores

- Interfaces: sustantivo del dominio, sin prefijo `I`. Ej: `Agent`, `AgentTool`, `EpisodicMemory`.
- Implementaciones: sufijo `Impl`. Ej: `AgentImpl`, `EpisodicMemoryImpl`.
- Clases abstractas: prefijo `Abstract`. Ej: `AbstractAgentTool`, `AbstractAgentService`.
- Factorías: sufijo `Factory`. Ej: `ReasoningServiceFactory`.
- Servicios: sufijo `Service` en interfaz y `ServiceImpl` en implementación. Ej: `ReasoningService` / `ReasoningServiceImpl`.
- Herramientas: sufijo `Tool`. Ej: `FileReadTool`, `ShellExecuteTool`.
- Operaciones de memoria: sufijo `Operation`. Ej: `TrimmingOperation`, `PinnedTurnsOperation`.
- Sensores: sufijo `SensorData`, `SensorEvent`, `SensorInformation`. Ej: `DiscreteSensorData`, `SensorEventUserImpl`.
- Utilidades: sufijo `Utils`. Ej: `DateUtils`, `SkillUtils`, `FileFuzzySearchUtils`.
- Gestores: sufijo `Manager`. Ej: `AgentManager`, `RCSManager`.
- Localizadores: sufijo `Locator`. Ej: `AgentLocator`, `AgentUILocator`.
- Métodos: camelCase, verbo en inglés. Ej: `getMessages`, `add`, `remove`, `consolideTurn`, `processSingleEvent`.
- Variables: camelCase, inglés. Ej: `recentMemory`, `projectedMessages`, `toolSpecifications`.
- Constantes: mayúsculas con guiones bajos. Ej: `DEFAULT_SUBCHANNEL`, `MAX_DB_TEXT_SIZE`.
- Paquetes: todo en minúsculas, sin guiones. Ej: `io.github.jjdelcerro.noema.lib.impl.memory.recent`.
- Comentarios: español, ASCII puro, sin tildes ni eñes.
- Nombres de herramientas expuestas al LLM: verbo en imperativo, inglés, snake_case. Ej: `file_read`, `file_write`, `shell_execute`, `annotate_observation`.

## Reglas y Patrones Arquitectónicos

- Entidad principal: `Agent`. Todo servicio, herramienta y memoria se registra o se obtiene a través de él.
- Inversión de dependencias: las interfaces viven en `lib`, las implementaciones en `impl`. Los servicios se registran mediante `AgentServiceFactory`.
- Localizador de servicios: `AgentLocator.getAgentManager()` es el punto único de acceso a factorías, acciones y subagentes.
- Patrón Factory para servicios: cada servicio tiene su `ServiceFactory` que implementa `canStart(settings)`.
- Patrón Abstract Service: `AbstractAgentService` implementa la lógica común de servicios (enabled, running, factory).
- Patrón Tool: `AgentTool` define `getSpecification()`, `execute()`, `getMode()`, `getType()`. `AbstractAgentTool` provee utilidades. `AbstractPaginatedAgentTool` añade paginación.
- Patrón Operation Pipeline: `ProjectedMemory` ejecuta una lista ordenada de `ProjectedMemoryOperation` sobre la lista de mensajes proyectados.
- Patrón Sensor: `SensorsService` recibe eventos, los clasifica por `SensorNature` y los entrega al dispatcher.
- Patrón Memento: `RecentMemoryMark` y `ProjectedMemoryState` permiten guardar y restaurar estado.
- Patrón Strategy: `SensorData` implementa diferentes estrategias de procesamiento según `SensorNature`.
- Patrón Adapter: `GsonUtils` adapta mensajes de LangChain4j a JSON.
- Patrón Wrapper: `McpToolWrapper` envuelve herramientas MCP como `AgentTool`.
- Regla: para añadir un servicio, crear interfaz en `lib.services`, implementación en `impl.services`, factoría en `impl.services`.
- Regla: para añadir una herramienta, extender `AbstractAgentTool` o `AbstractPaginatedAgentTool`, registrarla en el servicio correspondiente y añadirla a `available_tools.properties`.
- Regla: para añadir una operación de memoria proyectada, implementar `ProjectedMemoryOperation` y `ProjectedMemoryOperationFactory`, y registrarla en `AgentManagerImpl`.
- Regla: para añadir un sensor, implementar `SensorData` y registrar la naturaleza en `SensorsServiceImpl`.
- Regla: toda persistencia en base de datos usa H2 y SQL directo a través de `SQLProvider`.
- Regla: la configuración se gestiona mediante `AgentSettings` con estructura jerárquica y `settingsui.json` define la UI.
- Regla: el control de acceso se centraliza en `AgentAccessControl`; ninguna herramienta debe acceder al disco sin pasar por `resolvePathOrNull`.

## Zonas sensibles

- `ReasoningServiceImpl.processSingleEvent()`: bucle de razonamiento con múltiples llamadas al LLM, herramientas y consolidación. Cualquier cambio puede romper la secuencia de turnos.
- `RecentMemoryImpl.getConsolidateMark()`: cálculo del punto de corte para consolidación. Debe garantizar atomicidad de bloques de herramientas.
- `MemoryConsolidationServiceImpl.consolide()`: prompt complejo que depende de la estructura CSV y del formato de citas.
- `ProjectedMemoryImpl.getMessages()`: pipeline de operaciones; el orden y prioridad afectan al contexto final.
- `AgentAccessControlImpl.resolvePath()`: sandbox de rutas; un error puede permitir acceso no autorizado.
- `ScriptEngine.getCompilerConfiguration()`: sandbox de Groovy; la lista de imports prohibidos es crítica (ver `notas1.md`).
- `SensorsServiceImpl.getEvent()`: concurrencia con `wait/notify`; puede bloquear el dispatcher.
- `EpisodicMemoryImpl.add()`: inserción en H2 y cálculo de embeddings; el recorte de texto a 2KB puede perder información.
- `ChatModelImpl.generate()`: manejo de streaming y timeouts; puede quedar colgado si el stream no emite eventos.

## Trampas conocidas

- `SecureASTCustomizer` no bloquea `java.io.File` ni `java.nio.file.*` → un script Groovy puede saltarse el sandbox. (Ver `notas1.md`)
- `AgentSettingsImpl.eval()` usa MVEL; una expresión mal formada puede lanzar excepción no controlada.
- `ProjectedMemoryImpl.injectUnifiedNotification()` inyecta un `ToolExecutionResultMessage` ficticio con `pool_event`; si el LLM no espera esa herramienta, puede confundirse.
- `RecentMemoryImpl.getNewestMark()` usa `turnOfMessage.size() - 1` como índice, pero el mapa puede no tener todas las claves; puede devolver null.
- `EpisodicMemoryImpl.applyStoragePolicy()` trunca a 2KB y cambia `contenttype` a `tool_execution_summarized`; las búsquedas posteriores pueden no encontrar el texto completo.
- `MemoryConsolidationServiceImpl.extractCitationIds()` no valida que los IDs existan; luego se reemplazan por `{badcite:ID}`.
- `SubagentImpl.stop()` elimina el workspace temporal; si falla, deja basura.
- `SensorsServiceImpl.stop()` persiste estado en `sensors.json`; si el proceso muere antes, se pierde.
- `AgentImpl.getCurrentSubchannel()` usa `ReasoningServiceImpl.getCurrentSubchannel()`; si el servicio no está, devuelve default.

## Glosario del dominio

- **Turno**: unidad atómica de interacción; incluye mensaje de usuario, pensamiento del modelo, respuesta, llamada a herramienta y resultado.
- **Memoria episódica**: base de datos de todos los turnos; fuente de verdad.
- **Memoria reciente**: buffer de trabajo con los últimos mensajes no consolidados.
- **Memoria consolidada**: resumen narrativo de un rango de turnos; incluye citas a turnos originales.
- **Memoria proyectada**: contexto final que se envía al LLM; resultado de aplicar operaciones sobre memoria reciente y consolidada.
- **Subcanal**: identificador de una conversación paralela (terminal, usuario, etc.).
- **Sensor**: fuente de eventos externos (usuario, email, telegram, scheduler, notificaciones).
- **Evento**: estímulo que entra en el sistema; puede ser de usuario, discreto, fusionable, agregable o de estado.
- **pool_event**: herramienta ficticia que el agente usa para consultar eventos pendientes.
- **Skill**: protocolo técnico paso a paso que el agente puede activar.
- **Subagente**: trabajador aislado que ejecuta una receta XML en su propio sandbox.
- **Anotación**: turno especial que guarda conocimiento o directivas en memoria episódica.
- **Cita**: referencia `{cite:ID}` a un turno concreto.
- **Consolidación**: proceso de destilar turnos en un relato narrativo.
- **Proyección**: proceso de construir el contexto que ve el LLM.
- **Operación de proyección**: paso del pipeline que modifica la lista de mensajes proyectados.

## Índice de búsqueda

- Si buscas el bucle de razonamiento → `grep -r "processSingleEvent"`.
- Si buscas la consolidación de memoria → `grep -r "consolide"`.
- Si buscas herramientas → `grep -r "extends AbstractAgentTool"`.
- Si buscas servicios → `grep -r "extends AbstractAgentService"`.
- Si buscas sensores → `grep -r "SensorNature"`.
- Si buscas configuración → `grep -r "AgentSettings"`.
- Si buscas control de acceso → `grep -r "resolvePathOrNull"`.
- Si buscas scripting → `grep -r "ScriptEngine"`.
- Si buscas skills → `grep -r "SkillUtils"`.
- Si buscas subagentes → `grep -r "SubagentImpl"`.
- Si buscas interfaz web → `grep -r "NoemaWebServer"`.
- Si buscas UI Swing → `grep -r "MainChatPanel"`.
- Si buscas UI TUI → `grep -r "MainLanternaWindow"`.
- Si buscas tests → `grep -r "@Test"`.
- Si buscas embeddings → `grep -r "EmbeddingsService"`.
- Si buscas MCP → `grep -r "McpService"`.
- Si buscas scheduler → `grep -r "SchedulerService"`.
- Si buscas email → `grep -r "EmailService"`.
- Si buscas Telegram → `grep -r "TelegramService"`.
