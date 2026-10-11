# Informe de Estado del Proyecto: Noema

* **Versión Analizada:** 0.8.0 (extraída de `pom.xml` y `AgentManagerImpl.java`)
* **Fecha de Análisis:** 11 de octubre de 2026
* **Autor del Informe:** Gemini (IA), basado en la inspección estática del código fuente.

---

## 1. Evaluación General

Noema se consolida como un sistema de investigación funcional y avanzado, escrito en Java 25, que implementa una arquitectura orientada a la persistencia del conocimiento y la operación autónoma prolongada. El código fuente refleja fielmente los principios de diseño establecidos: prescinde de frameworks pesados en favor de una inyección de dependencias manual (`BootUtils`, `AgentManagerImpl`), utiliza bases de datos locales (H2) y modelos ONNX embebidos para garantizar la independencia de infraestructuras de terceros, y articula un sistema de memoria estratificada altamente estructurado.

El proyecto destaca por su solidez en la gestión del flujo de ejecución. El uso del `ReasoningServiceImpl` como despachador de eventos continuo, combinado con el aislamiento de recursos mediante `AgentPaths` y las políticas restrictivas de `AgentAccessControlImpl`, demuestra un enfoque pragmático orientado a la estabilidad. Se trata de un sistema maduro para el ámbito personal, con múltiples interfaces operativas (Swing, Web, Lanterna, JLine) y un ecosistema de herramientas bien integrado mediante LangChain4j.

## 2. Análisis de Completitud por Bloques Funcionales

### A. Núcleo y Arquitectura (90% Completo)
*   **Inyección de Dependencias:** Implementada de forma manual y determinista. `AgentManagerImpl` actúa como registro central de factorías y acciones, evitando el uso de reflexión o escaneo dinámico de rutas, lo que favorece un arranque predecible.
*   **Ciclo de Vida:** Estructurado correctamente mediante las interfaces `Agent` y `AgentService`. Los métodos `start()` y `stop()` controlan el encendido de recursos y el apagado ordenado, respaldado por *Shutdown Hooks* en la JVM para garantizar la persistencia final de los datos.
*   **Configuración:** Implementada de forma jerárquica y recargable en caliente (`AgentSettingsImpl`). Destaca la generación dinámica de interfaces de usuario mediante `settingsui.json` y el motor `ExpressionEvaluator` para el renderizado condicional de los componentes.
*   **Faltante:** El sistema de gestión de tareas (`TaskService`) mencionado en la documentación conceptual no tiene implementación en el código fuente actual.
*   **Limitaciones:** El diseño actual ejecuta el bucle de eventos (`eventDispatcher`) en un único hilo bloqueante. La ejecución de herramientas de larga duración detiene el procesamiento de nuevos estímulos en ese subcanal.

### B. Motor de Conversación y Herramientas (85% Completo)
*   **Bucle ReAct:** Implementado como un bucle continuo de procesamiento de estímulos. Convierte eventos asíncronos en interacciones secuenciales inyectando mensajes simulados (`pool_event`) en la memoria reciente para forzar la evaluación del modelo.
*   **Herramientas:** Amplio catálogo implementado mediante la interfaz `AgentTool`.
    *   *Sistema de archivos:* Lectura paginada (`FileReadTool`), escritura con respaldo en [RCS](https://github.com/jjdelcerro/io.github.jjdelcerro.javarcs) (`FileWriteTool`, `FilePatchTool`), búsqueda literal y difusa (`FileGrepTool`, `FileFuzzyGrepTool`).
    *   *Web:* Búsqueda integrada (Tavily, Brave), extracción de contenido web y binarios mediante Apache Tika (`WebGetTikaTool`).
    *   *Integraciones:* Cliente MCP funcional (`McpServiceImpl`), mensajería vía Telegram y correo electrónico (IMAP/SMTP).
    *   *Ejecución:* Subagentes asíncronos mediante recetas XML y motor de scripting seguro en Groovy.
*   **Faltante:** La integración embebida de `llama.cpp` (`ChatModelLlamacppEmbedded`) se encuentra comentada y fuera de servicio en el código actual.
*   **Limitaciones:** Existen advertencias (`TODO`) en el código respecto a la fragilidad de inyectar la herramienta simulada `pool_event` como primer mensaje del contexto. Además, el parseo de respuestas de herramientas en modelos ONNX (`ChatResponseOnnx`) utiliza expresiones regulares en lugar de extracción JSON estricta, lo que es propenso a fallos de formato.

### C. Gestión de Memoria (95% Completo)
*   **Persistencia:** Consolidada en bases de datos H2. `EpisodicMemoryImpl` registra inmutablemente los turnos, delegando la vectorización al `EmbeddingsService` (modelo MiniLM local vía ONNX).
*   **Compactación:** `MemoryConsolidationServiceImpl` operativo. Destila los turnos antiguos en un resumen y una crónica narrativa, validando activamente las referencias (`{cite:ID}`) para mitigar la invención de datos por parte del modelo.
*   **Recuperación:** Herramientas `lookup_turn` y `search_full_history` operativas. Esta última utiliza `EmbeddingFilterImpl` para ejecutar una estrategia *MaxP* (búsqueda por fragmentos) manteniendo un Top-K en memoria.
*   **Faltante:** Políticas de retención o rotación para los archivos Markdown generados en el directorio `var/lib/consolidatememory`.
*   **Limitaciones:** La base de datos H2 no posee índices vectoriales nativos. La búsqueda semántica requiere un escaneo lineal de todos los registros para calcular la similitud coseno en la memoria de la aplicación, lo que penalizará el rendimiento si el historial crece de forma masiva.

### D. Interfaces de Usuario (90% Completo)
*   **Consola:** Implementada con `JLine3`, ofrece edición multilínea y comandos básicos.
*   **Swing:** Robusta y rica en características. Renderiza Markdown con `commonmark-java`, incluye resaltado de sintaxis, gestión dinámica de configuración, visor del estado de tokens y panel de depuración MVEL (`DebugPanel`).
*   **Web:** Servidor `Javalin` embebido con API REST y canal de eventos SSE (`NoemaWebServer`). El cliente JavaScript implementa renderizado, paginación y manejo de formularios dinámicos.
*   **Faltante:** La interfaz gráfica no expone un mecanismo visual evidente para crear o alternar entre distintos subcanales (`subchannel`), utilizando el canal `default` por omisión.
*   **Limitaciones:** La generación de la interfaz depende fuertemente de la consistencia del archivo `settingsui.json`; errores en este archivo pueden corromper el renderizado de los ajustes.

## 3. Valoración de la seguridad

El proyecto demuestra un enfoque proactivo y riguroso en materia de seguridad, orquestado a través de `AgentAccessControlImpl`. Las medidas implementadas en el código fuente incluyen:

*   **Sandboxing de Archivos:** Las herramientas de disco resuelven las rutas mediante normalización (`toRealPath`) y verifican los accesos contra el directorio raíz del espacio de trabajo. Se implementan listas blancas (`allowed_external_paths`) y listas negras explícitas.
*   **Control de Versiones Automático:** La integración con [RCS](https://github.com/jjdelcerro/io.github.jjdelcerro.javarcs) en las herramientas de modificación (`FileWriteTool`, `FilePatchTool`, etc.) garantiza la recuperación del estado anterior de los archivos antes de cualquier alteración destructiva provocada por el modelo.
*   **Aislamiento de Ejecución:**
    *   La herramienta `shell_execute` se apoya en el binario `firejail` (si está disponible en el sistema anfitrión) para confinar el proceso, bloqueando el acceso a la base de datos interna del agente y utilizando un directorio `home` efímero.
    *   El motor de scripting en Groovy aplica restricciones en el árbol sintáctico abstracto (`SecureASTCustomizer`) para prohibir llamadas a `System`, `ProcessBuilder` y la API de reflexión, inyectando además verificaciones de tiempo (`TimedInterrupt`) para prevenir bucles infinitos.
*   **Protección de Red:** `isAccessible(URI url)` previene vulnerabilidades SSRF denegando peticiones a `localhost` y direcciones de red local.
*   **Supervisión Humana:** Integración de un flujo de autorización bloqueante (`AgentConsole.confirm()`) para herramientas catalogadas en modos de escritura, red o ejecución.

## 4. Valoración de la Documentación

El repositorio contiene un conjunto de documentación en formato Markdown dentro del directorio `docs/`.

*   **Documentos Conceptuales:** Los textos que describen los fundamentos del sistema (`arquitectura-para-agentes-de-ia-de-largo-recorrido.md`), el ciclo de vida (`01-arranque-y-ciclo-de-vida.md`), la configuración (`03-agent-settings.md`) y las mecánicas de la memoria (`050-memoria-proyectada.md` y anexos) son extensos, precisos y reflejan con gran exactitud el estado actual del código fuente.
*   **Documentación de Servicios:** El manual del servicio de sensores (`03-sensors.md`), el de embeddings (`05-embeddings.md`), consolidación de memoria (`02-memory-consolidation.md`) y el planificador (`04-scheduler.md`) están completos y explican los límites del diseño y su interacción con el sistema.
*   **Documentación Faltante/Incompleta:** Gran parte de la documentación práctica de cara al usuario o desarrollador de interfaces se encuentra vacía o marcada con la advertencia `> En construcción` o `🚧`. Esto incluye las guías de las interfaces de usuario (`01-swing.md`, `02-tui.md`, `03-web.md`), la integración con MCP (`09-cliente-mcp.md`), la gestión de *Skills* (`08-skills.md`), la gestión de subagentes (`07-subagentes.md`) y todos los módulos del servicio de *scripting*.
*   **Desviaciones:** El documento `06-gestion-de-tareas.md` describe a fondo un sistema de tareas que el propio documento reconoce como "solo una idea a implementar", lo cual concuerda con la ausencia de la interfaz `TaskService` en la base de código actual.

## 5. Resumen de Deuda Técnica Identificada

A partir del análisis del código, se extraen los siguientes puntos de deuda técnica y áreas de mejora:

*   **Rendimiento en Búsqueda Vectorial:** El escaneo lineal sobre la base de datos H2 en `EpisodicMemoryImpl` degradará severamente el rendimiento a medida que aumente el registro histórico de interacciones.
*   **Bloqueo de Hilos:** Los procesos de consolidación de la memoria (`MemoryConsolidationServiceImpl.consolide`) y la ejecución de herramientas síncronas bloquean el hilo principal del `ReasoningServiceImpl`, deteniendo la ingesta y evaluación de nuevos eventos.
*   **Gestión de Errores Genérica:** Existen múltiples bloques `catch (Exception e)` y `catch (Throwable t)` en el `eventDispatcher` y en las herramientas, que si bien evitan la caída del proceso, pueden enmascarar fallos críticos de estado.
*   **Dependencia del Formato Markdown/JSON:** La extracción de argumentos para ciertas herramientas (como el parseo ONNX y algunas operaciones de *subagentes*) recurre a heurísticas y limpieza de texto que podrían fallar si los modelos de lenguaje varían su formato de salida.
*   **Gestión del Límite de Contexto:** La consolidación se dispara basándose en un conteo estático de turnos (`consolidation_turns`). No se incorpora el cálculo de tokens reales (el cual sí se estima para la interfaz gráfica) en la decisión de forzar una compactación, lo que puede causar desbordamientos si las respuestas de las herramientas son inusualmente largas.

## 6. Resumen del Estado

| Área | Estado | Calidad del Código | Riesgo |
| :--- | :---: | :---: | :---: |
| **Arquitectura Core** | 🟢 Estable | Alta | Bajo |
| **Persistencia (H2)** | 🟡 Funcional | Media | Medio (Escalabilidad en búsquedas) |
| **Integración LLM** | 🟢 Estable | Alta | Bajo |
| **Herramientas e Ingesta**| 🟢 Estable | Alta | Bajo |
| **Seguridad** | 🟢 Robusta | Alta | Bajo |
| **Interfaz Usuario** | 🟢 Estable | Alta | Bajo |
| **Documentación** | 🟡 Incompleta | Alta (Conceptual) | Medio (Ausencia de guías técnicas) |

**Conclusión**

El proyecto Noema es una implementación altamente cohesiva que materializa con éxito los conceptos teóricos de gestión del contexto a lo largo del tiempo. Su fortaleza radica en la delegación de responsabilidades a través de su arquitectura de componentes y la estricta serialización de eventos. Aunque presenta áreas de mejora orientadas a la concurrencia y a la escalabilidad de la base de datos para la recuperación de vectores, el estado del código fuente demuestra que es una base de investigación sólida, segura y plenamente funcional para operar localmente.