# Informe de Estado del Proyecto: Noema

* **Versión Analizada:** 0.1.0 (Extraída del `pom.xml`)
* **Fecha de Análisis:** Abril 2026
* **Autor del Informe:** Gemini (IA), basado en la inspección estática del código fuente.


## 1. Evaluación General

El proyecto "Noema" es un framework avanzado para la creación de un agente conversacional de larga duración, diseñado bajo una estricta filosofía de ejecución local y dependencias mínimas. A partir del análisis del código fuente (Java 21), se evidencia una arquitectura de software altamente madura y pragmática. 

El sistema no intenta delegar toda la lógica al Modelo de Lenguaje (LLM), sino que utiliza el LLM como un procesador semántico dentro de un orquestador determinista muy robusto. Destaca especialmente su enfoque para resolver las limitaciones inherentes de los LLMs actuales (como el límite de la ventana de contexto) mediante soluciones de ingeniería de software clásicas: paginación de recursos en disco (`AbstractPaginatedAgentTool`), consolidación narrativa con trazabilidad de citas (`MemoryServiceImpl`), y un sistema de inyección de eventos asíncronos (`SensorsServiceImpl`) que dota al agente de proactividad sin romper el paradigma de petición-respuesta (Request-Response) de las APIs de los modelos.

El proyecto es plenamente funcional como prueba de concepto avanzada, con un núcleo sólido, aunque algunos servicios periféricos (como la integración con Telegram o Email) se encuentran en un estado más experimental.

## 2. Análisis de Completitud por Bloques Funcionales

### A. Núcleo y Arquitectura (95% Completo)
*   **Inyección de Dependencias:** Implementada de forma manual y efectiva mediante un patrón *Service Locator* (`AgentLocator`, `AgentManagerImpl`). Desacopla perfectamente las interfaces (`AgentService`) de sus implementaciones.
*   **Ciclo de Vida:** Gestión explícita y robusta (`start()`, `stop()`, `canStart()`). Los servicios se registran mediante factorías y el apagado está protegido por *Shutdown Hooks* en la JVM para garantizar la persistencia del estado.
*   **Configuración:** Sistema jerárquico muy avanzado (`AgentSettingsImpl`). Soporta persistencia en JSON, listas con estado (`AgentSettingsCheckedList`) y, notablemente, un evaluador de expresiones lógicas propio (`ExpressionEvaluator`) para habilitar/deshabilitar opciones dinámicamente en la interfaz.
*   **Faltante:** Mayor granularidad en la recarga en caliente de ciertos servicios sin necesidad de reiniciar el bucle principal.
*   **Limitaciones:** Al ser un diseño *single-tenant* (orientado a un único usuario local), la arquitectura asume acceso exclusivo a los recursos del sistema.

### B. Motor de Conversación y Herramientas (90% Completo)
*   **Bucle ReAct:** Implementado en `ReasoningServiceImpl.eventDispatcher()`. Es un bucle síncrono que gestiona eficientemente la llamada al modelo, la ejecución de herramientas, el manejo de errores y la inyección de eventos externos simulando llamadas a la herramienta `pool_event`.
*   **Herramientas:** Catálogo extenso y bien estructurado, heredando de `AbstractAgentTool`.

    *   *Sistema de archivos:* Altamente completo. Incluye lectura, escritura, búsqueda (glob y grep), creación de directorios y aplicación de parches (`UnifiedDiffUtils`).
    *   *Paginación:* Brillante implementación en `AbstractPaginatedAgentTool` y `ReadPaginatedResourceTool`. Permite al agente consumir salidas masivas (logs, webs, extracciones Tika) mediante un sistema de punteros y offsets, protegiendo el contexto.
    *   *Control de Versiones:* Integración nativa con [RCS](https://github.com/jjdelcerro/io.github.jjdelcerro.javarcs) para historial y recuperación de archivos.
    *   *Web:* Búsqueda mediante Tavily y Brave, geolocalización por IP, clima y extracción de texto web con Apache Tika.
    *   *Integraciones:* Servicios de Email y Telegram presentes, pero con implementaciones básicas.
    
*   **Faltante:** Las herramientas de comunicación (Email/Telegram) requieren mayor robustez en el manejo de errores de red y reconexiones.
*   **Limitaciones:** El bucle de razonamiento se ejecuta en un único hilo de plataforma. La ejecución de una herramienta lenta (ej. descarga web pesada) bloquea el procesamiento de nuevos eventos sensoriales hasta que finaliza.

### C. Gestión de Memoria (85% Completo)
*   **Persistencia:** Sólida. Utiliza H2 embebido (`SourceOfTruthImpl`). Guarda cada interacción como un `Turn` inmutable, incluyendo el cálculo y almacenamiento de embeddings como BLOBs.
*   **Compactación:** Implementada en `MemoryServiceImpl`. Cuando la `Session` supera un umbral de turnos, un LLM genera un `CheckPoint` compuesto por un resumen y una narrativa cronológica que mantiene referencias exactas (`{cite:ID}`) a los turnos originales.
*   **Recuperación:** Herramientas `lookup_turn` y `search_full_history`. La búsqueda semántica se realiza en memoria (`EmbeddingFilterImpl`) calculando la distancia coseno, lo cual es muy eficiente para el alcance de un proyecto personal.
*   **Faltante:** Implementación completa de la rehidratación automática de contexto al recuperar turnos antiguos (mencionado en el código como TODO).
*   **Limitaciones:** La compactación es síncrona y bloquea el hilo principal del agente mientras el LLM genera el resumen. El umbral de compactación se basa en cantidad de turnos y no en el conteo real de tokens, lo que podría saturar el contexto si los turnos contienen textos muy largos.

### D. Document Mapper / RAG (60% Completo)
*   **Ingesta:** Implementada en `DocumentStructureExtractor`. Utiliza un enfoque de tres pasadas (extracción de estructura, resumen por nodos y categorización) delegando en LLMs.
*   **Estructura:** Guarda el resultado como un archivo `.struct` (JSON) y permite su representación como XML plegable (`DocumentStructure.toXML()`) para que el agente decida qué secciones expandir mediante `get_partial_document`.
*   **Faltante:** El código muestra que es funcional, pero carece de manejo avanzado de errores para documentos mal formateados o PDFs complejos.
*   **Limitaciones:** La extracción de estructura depende en gran medida de la capacidad del LLM para seguir el formato CSV estricto solicitado en el prompt, lo cual puede ser frágil con modelos de menor capacidad.

### E. Interfaces de Usuario (85% Completo)
*   **Consola:** Implementación funcional usando JLine3 (`MainConsole`), con soporte para autocompletado y atajos de teclado.
*   **Swing:** Interfaz gráfica muy pulida (`MainGUI`, `MainChatPanel`). Utiliza FlatLaf para un tema oscuro moderno, MigLayout para la disposición, y RSyntaxTextArea para la edición de texto y visualización de Markdown. Incluye renderizado de burbujas de chat diferenciadas por rol y un panel dinámico de configuración (`AgentSwingSettingsImpl`).
*   **Faltante:** Mayor integración visual de los estados de los sensores (ej. mostrar gráficamente si el agente está "silenciado" o procesando eventos en segundo plano).
*   **Limitaciones:** La interfaz Swing está fuertemente acoplada a la máquina local, lo que es coherente con el diseño del proyecto, pero impide su uso remoto sin herramientas de escritorio remoto.


## 3. Valoración de la seguridad

La seguridad es uno de los puntos más fuertes y mejor diseñados del proyecto, evidenciando una mentalidad defensiva:

1.  **Sandbox Estricto:** La clase `AgentAccessControlImpl` centraliza toda la resolución de rutas. Previene activamente ataques de *Path Traversal* y verifica listas blancas (rutas externas permitidas) y listas negras (rutas de solo lectura o prohibidas).
2.  **Confirmación Humana (Human-in-the-loop):** Las herramientas declaran su nivel de riesgo (`MODE_READ`, `MODE_WRITE`, `MODE_EXECUTION`). Cualquier acción destructiva suspende la ejecución y solicita confirmación explícita al usuario a través de la interfaz (`AgentConsole.confirm()`).
3.  **Inmutabilidad Histórica:** El agente no puede modificar los archivos de respaldo generados por el sistema de control de versiones.
4.  **Respaldos Automáticos:** Antes de que herramientas como `file_write`, `file_patch` o `file_search_and_replace` modifiquen un archivo, el sistema fuerza un *check-in* automático utilizando la librería [RCS](https://github.com/jjdelcerro/io.github.jjdelcerro.javarcs), garantizando que cualquier error del LLM pueda ser revertido.
5.  **Aislamiento de Procesos:** La ejecución de comandos de shell (`ShellExecuteTool`) incluye soporte para envolver los procesos en `Firejail` si está disponible en el sistema anfitrión.

## 4. Valoración de la Documentación

La documentación presente en el directorio `docs/` (especialmente en `docs/ideas/`) es extensa, rica en reflexiones arquitectónicas y detalla profundamente el "por qué" de las decisiones de diseño. 

Sin embargo, desde la perspectiva del análisis estático, la documentación presenta un riesgo de desincronización con el código real. Muchos documentos están redactados como propuestas, borradores o discusiones conceptuales (ej. notas sobre cómo implementar el sistema sensorial o la memoria temática). Aunque el archivo `AGENT_CONTEXT.md` ofrece una excelente visión general, el propio autor reconoce que el proyecto es un "organismo vivo", por lo que el código fuente es la única fuente de verdad absoluta. La documentación es sobresaliente para entender la filosofía del proyecto, pero debe leerse con precaución respecto a los detalles exactos de implementación actual.

## 5. Resumen de Deuda Técnica Identificada

A partir de la inspección del código, se identifican las siguientes áreas de mejora técnica:

*   **Bloqueo del Hilo Principal:** El `ReasoningServiceImpl.eventDispatcher()` se ejecuta en un único hilo. Operaciones pesadas como la compactación de memoria (`MemoryServiceImpl.compact()`) o la ejecución de herramientas de red bloquean la percepción de nuevos eventos.
*   **Gestión de Errores en el Bucle ReAct:** Existe un `TODO` en el código que señala posibles fallos si el primer mensaje enviado al LLM es una llamada simulada a `pool_event`.
*   **Cálculo de Umbrales de Memoria:** La compactación se dispara por el número de turnos (`compaction_turns`) en lugar de por el conteo real de tokens, lo que es una heurística imprecisa que podría llevar a exceder el límite de la API del LLM.
*   **Servicios Periféricos Incompletos:** Las implementaciones de `TelegramService` y `EmailService` carecen de la robustez necesaria para manejar desconexiones de red prolongadas o errores de autenticación de forma resiliente.
*   **Clasificación de Herramientas:** La herramienta `annotate_observation` está clasificada temporalmente como operativa (`TYPE_OPERATIONAL`) en lugar de memoria (`TYPE_MEMORY`) para aprovechar un efecto secundario de la persistencia, lo cual es un parche arquitectónico reconocido en los comentarios del código.

## 6. Próximos Hitos (Roadmap Sugerido)

Basado en el estado actual del código, los siguientes pasos lógicos para la evolución del proyecto serían:

1.  **Asincronía en la Compactación:** Refactorizar `MemoryServiceImpl` para que la generación de *CheckPoints* se realice en un hilo secundario, permitiendo al agente seguir interactuando (quizás con una advertencia de "consolidando memoria") mientras se procesa el resumen.
2.  **Refinamiento del Document Mapper:** Estabilizar el `DocumentsServiceImpl`, añadiendo manejo de reintentos si el LLM falla al generar el CSV estructural, y optimizar la inyección de texto parcial en el XML.
3.  **Transición a Conteo de Tokens:** Integrar el estimador de tokens (`estimateMessagesTokenCount`) como disparador principal para la compactación de la `Session`, reemplazando el conteo estático de turnos.
4.  **Robustecimiento de Sensores:** Mejorar los servicios de Email y Telegram implementando políticas de reconexión (backoff exponencial) y manejo de excepciones de red más detallado.
5.  **Rehidratación Completa de Memoria:** Implementar la lógica pendiente para que, cuando el agente use `lookup_turn`, el `MemoryService` detecte esta acción y reincorpore explícitamente ese recuerdo en el siguiente *CheckPoint*.

## 7. Resumen del Estado

| Área | Estado | Calidad del Código | Riesgo |
| :--- | :---: | :---: | :---: |
| **Arquitectura Core** | 🟢 95% | Alta | Bajo |
| **Persistencia** | 🟡 85% | Media-Alta | Medio (Bloqueos síncronos) |
| **Integración LLM** | 🟢 90% | Alta | Bajo |
| **Herramientas** | 🟢 90% | Alta | Bajo |
| **Seguridad** | 🟢 90% | Alta | Bajo |
| **Interfaz Usuario** | 🟡 85% | Alta | Bajo |
| **Documentación** | 🟡 70% | Alta (Conceptual) / Media (Actualización) | Medio (Desincronización) |

**Conclusión**

"Noema" es un proyecto personal de una calidad técnica excepcional. Demuestra un profundo entendimiento no solo de cómo interactuar con Modelos de Lenguaje, sino de cómo construir el software que los rodea para mitigar sus debilidades. La implementación de la paginación en disco para proteger el contexto, el uso de [RCS](https://github.com/jjdelcerro/io.github.jjdelcerro.javarcs) para la seguridad del sistema de archivos, y la falsificación elegante del historial para lograr proactividad (`pool_event`) son soluciones de ingeniería brillantes. Aunque presenta deuda técnica típica de un proyecto en evolución (especialmente en la sincronía de tareas pesadas), los cimientos arquitectónicos son lo suficientemente sólidos para soportar experimentación avanzada a largo plazo.
