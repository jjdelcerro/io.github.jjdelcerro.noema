# Informe de Estado del Proyecto: Noema

* **Versión Analizada:** 0.1.0
* **Fecha de Análisis:** 28 de Marzo de 2026
* **Autor del Informe:** Gemini (IA), basado en la inspección estática del código fuente.

---

## 1. Evaluación General

Noema es un agente conversacional autónomo diseñado como un proyecto personal enfocado en la investigación y reflexión a largo plazo. Su arquitectura destaca por estar completamente autocontenida en el ecosistema Java, cumpliendo con la restricción de no requerir infraestructura externa más allá del propio ejecutable y la conexión a las APIs de los modelos de lenguaje (LLMs). 

El proyecto implementa un modelo de sesión única y continua. Para lograr esto sin desbordar los límites de los LLMs, utiliza un sistema de consolidación de eventos y un almacenamiento local (H2) combinado con cálculo de embeddings en memoria. Asimismo, presenta un diseño muy modular basado en servicios (`Memory`, `Reasoning`, `Sensors`, `Documents`, etc.) y un control de acceso estricto que lo hace seguro para operar sobre el sistema de archivos local. Destaca especialmente su enfoque para simular proactividad mediante un sistema de "sensores" que encolan eventos externos (Telegram, Email, temporizadores) y los inyectan en el flujo de razonamiento.

## 2. Análisis de Completitud por Bloques Funcionales

### A. Núcleo y Arquitectura (85% Completo)
*   **Inyección de Dependencias:** Implementada mediante un patrón *Service Locator* manual (`AgentLocator` y `AgentManagerImpl`). Adecuado y funcional para evitar frameworks pesados como Spring, cumpliendo la premisa de ligereza.
*   **Ciclo de Vida:** Bien definido. Los servicios implementan una interfaz común con métodos `start()`, `stop()` y `canStart()`, gestionados centralmente. Se hace un buen uso de *Shutdown Hooks* para cerrar conexiones a bases de datos de forma segura.
*   **Configuración:** Sólida. Basada en un archivo `settings.json` jerárquico, respaldado por un árbol de interfaces en Java y adaptadores GSON personalizados que permiten la generación de interfaces de usuario automáticas.
*   **Faltante:** Mecanismos de recuperación o reinicio en caliente ante caídas de la conexión con el LLM o bloqueos en la base de datos local.
*   **Limitaciones:** El enrutamiento manual de dependencias puede volverse tedioso si el número de servicios sigue creciendo, aunque es perfectamente manejable en el alcance actual.

### B. Motor de Conversación y Herramientas (90% Completo)
*   **Bucle ReAct:** Implementado en `ReasoningServiceImpl#eventDispatcher()`. Gestiona eficazmente la invocación de herramientas, la recuperación ante respuestas mal formateadas (hasta 3 reintentos) y la inyección de eventos asíncronos.
*   **Herramientas:** Muy completas y bien categorizadas.
    *   *Sistema de archivos:* Lectura, escritura, creación de directorios, búsqueda por patrones y expresiones regulares, parches (Unified Diff) y reemplazo de texto. Integración completa y nativa con un sistema RCS en Java puro para control de versiones y recuperación.
    *   *Web:* Búsqueda (Tavily, Brave), extracción de contenido limpio (Tika) con soporte de paginación, clima y geolocalización.
    *   *Integraciones:* Telegram (Push/Pull de mensajes completos), Email (Push de cabeceras mediante IMAP IDLE, Pull de cuerpos con Tika) y planificador de alarmas (Scheduler).
*   **Faltante:** Refinamiento en algunas herramientas (marcadas con `FIXME` en el código, como la separación de límites de búsqueda y retorno en `SearchFullHistoryTool`).
*   **Limitaciones:** La herramienta de extracción web (`WebGetTikaTool`) procesa contenido estático. Sitios que dependan fuertemente de renderizado JavaScript del lado del cliente (SPA) podrían no extraerse correctamente.

### C. Gestión de Memoria (80% Completo)
*   **Persistencia:** Utiliza una base de datos embebida H2 configurada con soporte para BLOBs para guardar el historial completo de interacciones (`Turn`) y los resúmenes consolidados (`CheckPoint`).
*   **Compactación:** Funcional y bien diseñada mediante `MemoryServiceImpl`. Cuando la sesión activa alcanza un umbral de turnos, invoca a un LLM secundario para generar un resumen ejecutivo y una narrativa estructurada ("El Viaje"), manteniendo la coherencia cronológica y las referencias (`{cite:ID}`).
*   **Recuperación:** Uso de un modelo ONNX local (`AllMiniLmL6V2`) para generar embeddings. La búsqueda vectorial se realiza directamente en memoria mediante una cola de prioridad (`EmbeddingFilterImpl`), calculando la similitud del coseno. Esto cumple magistralmente con el requisito de no depender de bases de datos vectoriales externas.
*   **Faltante:** Lógica robusta para trocear resultados inmensos de herramientas antes de guardarlos en la base de datos (actualmente hay un truncado básico que avisa al modelo de que la salida se recortó en BD).
*   **Limitaciones:** El cálculo de la distancia del coseno en memoria contra todos los registros de la base de datos (o un subconjunto filtrado) escalará linealmente (O(N)). Para un proyecto personal es aceptable, pero penalizará el rendimiento si la base de conocimiento crece hasta decenas de miles de registros.

### D. Document Mapper / RAG (75% Completo)
*   **Ingesta:** Proceso asíncrono que utiliza dos modelos: uno de razonamiento para inferir la jerarquía y estructura del documento (TOC) desde el texto bruto, y otro más rápido para generar resúmenes y categorías por cada sección.
*   **Estructura:** Crea un archivo `.struct` (JSON) paralelo al documento original y persiste metadatos y vectores en la tabla `DOCUMENTS` de H2.
*   **Faltante:** Tolerancia a fallos más estricta si el modelo de razonamiento falla al devolver el formato CSV esperado durante la extracción de la estructura. 
*   **Limitaciones:** El modelo requiere cargar documentos completos (línea por línea) para extraer la estructura inicial, lo que puede ser limitante en modelos con ventanas de contexto pequeñas si el documento es masivo.

### E. Interfaces de Usuario (85% Completo)
*   **Consola:** Implementada con `JLine3`, soportando historial, multilínea y autocompletado básico.
*   **Swing:** Interfaz rica usando `FlatLaf` (tema oscuro). Incluye un panel lateral de configuración autogenerado a partir del JSON de configuración, un editor de texto (`RSyntaxTextArea`) para visualizar archivos y parches, y un renderizador Markdown personalizado (`JMarkdownPanel`).
*   **Faltante:** Feedback visual en tiempo real (streaming de tokens) en la interfaz gráfica. Actualmente, la UI muestra un temporizador y bloquea el input hasta que llega la respuesta completa.
*   **Limitaciones:** El sistema de renderizado Markdown de Swing puede requerir optimizaciones si se manejan mensajes extremadamente largos con bloques de código muy complejos.

## 3. Valoración de la seguridad

Sorprendentemente alta para un proyecto personal. La clase `AgentAccessControlImpl` establece un entorno restrictivo (Sandbox) muy sólido:

*   Previene ataques de *Path Traversal* (jailbreak del directorio de trabajo).
*   Maneja listas de rutas permitidas (Whitelist) y listas de solo lectura (Blacklist).
*   Protege sus propios archivos de control de versiones (`.jv`).
*   Permite requerir confirmación humana explícita antes de ejecutar operaciones de escritura, ejecución en shell o acceso web.
*   Incluye integración opcional con `Firejail` en sistemas Linux para aislar por completo la ejecución de comandos shell (`ShellExecuteTool`), lo cual es una medida de seguridad excepcional para un agente autónomo.

## 4. Valoración de la Documentación

La documentación del código (Javadoc) es escasa y se limita a descripciones de interfaces clave. Sin embargo, el proyecto brilla en su **documentación dirigida a la IA** (*Prompt Engineering*). Los archivos Markdown en `var/config/prompts/` (`memory-compact.md`, `reasoning-system.md`, etc.) están redactados de forma impecable, con instrucciones directas, ejemplos y restricciones lógicas muy bien estructuradas. El código es autoexplicativo, con nombres de variables y métodos bien elegidos.

## 5. Resumen de Deuda Técnica Identificada

1.  **Manejo de Concurrencia:** Hay comentarios que indican un cambio reciente de `Thread.ofVirtual()` a `Thread.ofPlatform()`. Si el objetivo a largo plazo es eficiencia, estabilizar el uso de hilos virtuales de Java 21 sería ideal.
2.  **Hardcoding en parseo:** Algunas extracciones web (`WebGetTool`, aunque marcada para no usar) utilizan expresiones regulares frágiles para limpiar HTML en lugar de depender exclusivamente de Tika.
3.  **Límites de Búsqueda:** Existen comentarios (`FIXME`) en herramientas como `SearchFullHistoryTool` indicando que falta separar el parámetro de límite de búsqueda de documentos del límite de resultados a devolver.
4.  **Gestión de Descripciones:** Algunos sensores (ej. Telegram, Email) tienen descripciones *placeholder* (`"Telegram"`, `"email"`) que deberían ser más detalladas para que el LLM entienda mejor el propósito de esos canales al usar `sensor_status`.

## 6. Próximos Hitos (Roadmap Sugerido)

1.  **Refinamiento de la Paginación Semántica:** Mejorar la inyección de fragmentos de herramientas en la base de datos para no perder información crítica cuando la salida de una herramienta supera el límite de guardado local.
2.  **Streaming en UI:** Conectar el `StreamingResponseHandler` (que ya está preparado en `ChatModelImpl`) con el `JMarkdownPanel` para renderizar las respuestas del agente en tiempo real.
3.  **Gestión de Ventana de Contexto Dinámica:** Implementar en `Session.java` un chequeo de tokens consumidos en el turno actual para forzar una consolidación proactiva si la conversación se acerca peligrosamente al límite de tokens del modelo activo.

## 7. Resumen del Estado

| Área | Estado | Calidad del Código | Riesgo |
| :--- | :---: | :---: | :---: |
| **Arquitectura Core** | 🟢 Estable | Alta | Bajo |
| **Persistencia** | 🟡 Funcional | Media-Alta | Medio (Escalabilidad de búsqueda local O(N)) |
| **Integración LLM** | 🟢 Estable | Alta | Bajo |
| **Herramientas** | 🟢 Amplias | Alta | Bajo |
| **Seguridad** | 🟢 Robusta | Alta | Bajo |
| **Interfaz Usuario** | 🟡 Funcional | Media-Alta | Bajo |
| **Documentación** | 🟡 Mejorable | Media | Bajo (Uso personal) |

**Conclusión**

El proyecto Noema presenta un estado de desarrollo sorprendentemente maduro para ser una iniciativa personal. Su diseño arquitectónico resuelve con elegancia el problema de mantener una sesión continua en el tiempo sin depender de infraestructuras de terceros, demostrando que la combinación de bases de datos relacionales locales (H2) junto con modelos de embeddings incrustados y algoritmos de ranking en memoria es una aproximación viable y pragmática. El uso de un RCS en Java nativo y las medidas de seguridad perimetral implementadas le otorgan una gran fiabilidad para ser utilizado como asistente de investigación e interacción sobre el sistema operativo.
