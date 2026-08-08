# Informe de Estado del Proyecto: Noema

* **Versión Analizada:** 0.1.0 (Basado en `pom.xml`)
* **Fecha de Análisis:** 8 de Agosto de 2026
* **Autor del Informe:** Gemini (IA), basado en la inspección estática del código fuente.


## 1. Evaluación General

Noema se presenta como un agente conversacional autónomo diseñado para funcionar como asistente de investigación y acompañamiento a largo plazo. La arquitectura refleja un enfoque pragmático y monolítico que elimina la dependencia de infraestructuras complejas (como bases de datos vectoriales dedicadas o servicios en la nube para persistencia), optando por un almacenamiento local basado en H2 y el sistema de archivos (archivos planos y Markdown). 

El código muestra un diseño limpio basado en la Inversión de Dependencias y el patrón *Service Locator* (`AgentManager`, `AgentLocator`), prescindiendo deliberadamente de frameworks pesados como Spring. La orquestación del flujo de eventos (`eventDispatcher`), la interacción con el modelo de lenguaje (vía LangChain4j), y el sistema de herramientas con paginación (`AbstractPaginatedAgentTool`) demuestran un nivel de madurez alto para un proyecto personal. La gestión de una sesión única y continua se resuelve de manera ingeniosa mediante la compactación narrativa de turnos en puntos de control (`CheckPoint`), permitiendo mantener la coherencia histórica sin desbordar los límites de tokens del LLM.

## 2. Análisis de Completitud por Bloques Funcionales

### A. Núcleo y Arquitectura (90% Completo)
*   **Inyección de Dependencias:** Implementada de forma manual y robusta. El uso de factorías (`AgentServiceFactory`) y la inicialización secuencial en `BootUtils` y `AgentImpl` garantizan un arranque determinista.
*   **Ciclo de Vida:** Controlado correctamente (`start()`, `stop()`). Se gestionan cierres limpios con *Shutdown Hooks* para preservar el estado de la base de datos y la sesión en RAM.
*   **Configuración:** Altamente dinámica. El sistema evalúa expresiones lógicas (con un parser personalizado `ExpressionEvaluator`) para habilitar o deshabilitar opciones en tiempo real desde `settings.json` y `settingsui.json`.
*   **Faltante:** Un mecanismo formal de recuperación ante caídas abruptas (crash recovery) a mitad de la escritura de un bloque paginado o durante una compactación crítica.
*   **Limitaciones:** La arquitectura actual está fuertemente acoplada a un único espacio de trabajo (*workspace*) por instancia de ejecución. No admite multi-tenancy ni procesamiento paralelo de sesiones independientes.

### B. Motor de Conversación y Herramientas (85% Completo)
*   **Bucle ReAct:** Implementado en `ReasoningServiceImpl` a través del `eventDispatcher`. Consume eventos de forma secuencial y bloqueante, construyendo el contexto y llamando al modelo iterativamente hasta resolver la intención.
*   **Herramientas:** Catálogo extenso y bien estructurado (`AgentTool`).
    *   *Sistema de archivos:* Lectura, escritura, parcheo (con `UnifiedDiffUtils`), y soporte de control de versiones locales mediante [RCS](https://github.com/jjdelcerro/io.github.jjdelcerro.javarcs) (`FileHistoryTool`, `FileRecoveryTool`).
    *   *Web:* Búsquedas mediante Tavily/Brave, extracción de contenido con Tika, geolocalización y clima.
    *   *Integraciones:* Cliente de Telegram (vía *long-polling*) y Email (IMAP IDLE y SMTP).
    *   *Paginación:* Excelente implementación en `AbstractPaginatedAgentTool` usando archivos temporales/caché y un ID simbólico (`tmp://`, `cache://`) para evitar desbordamientos de tokens en salidas masivas.
*   **Faltante:** Ejecución paralela de herramientas independientes (actualmente el bucle se bloquea en cada ejecución).
*   **Limitaciones:** Las operaciones de larga duración (como un comando shell pesado) bloquean el hilo principal de procesamiento de eventos, pausando la reactividad del agente ante nuevos estímulos (como mensajes entrantes de Telegram).

### C. Gestión de Memoria (80% Completo)
*   **Persistencia:** Utiliza una base de datos H2 (`SourceOfTruthImpl`) para almacenar los turnos (`Turn`) serializando los embeddings como BLOBs, y ficheros `.md` para los relatos consolidados.
*   **Compactación:** Implementada en `MemoryServiceImpl`. Extrae bloques antiguos de la sesión activa y utiliza un LLM para generar un resumen narrativo con citas rastreables (`{cite:ID}`). 
*   **Recuperación:** Herramientas como `LookupTurnTool` y `SearchFullHistoryTool` permiten al agente consultar el historial. La similitud del coseno se calcula en memoria (`EmbeddingFilterImpl`) tras escanear los BLOBs.
*   **Faltante:** Troceado automático de turnos de consulta masiva (`lookup_turn`) cuando el CSV resultante excede la ventana de contexto del modelo de compactación (identificado como un `TODO` en el código).
*   **Limitaciones:** La búsqueda semántica realiza un escaneo completo (*Full Table Scan*) de la base de datos H2 para calcular distancias vectoriales en la JVM. Aunque funciona para el alcance personal, degradará el rendimiento si el historial crece a decenas de miles de turnos.

### D. Document Mapper / RAG (75% Completo)
*   **Ingesta:** El `DocumentStructureExtractor` procesa documentos asíncronamente extrayendo jerarquías, resúmenes por sección y categorías usando llamadas a un LLM "básico".
*   **Estructura:** Se persiste en archivos `.struct` (JSON). Permite al agente solicitar un esquema XML del documento y luego hacer *pull* de secciones específicas (`GetPartialDocumentTool`).
*   **Faltante:** Lógica para actualizar automáticamente el índice si el documento original es modificado por el usuario o por el propio agente en el sistema de archivos.
*   **Limitaciones:** La vectorización (`EmbeddingsService`) se basa en un modelo local estático (384 dimensiones) que no admite ajuste fino para dominios hiper-específicos, aunque es ideal para el requisito de no depender de infraestructuras externas.

### E. Interfaces de Usuario (90% Completo)
*   **Consola:** Implementación robusta basada en JLine3 (`MainConsole`), con soporte para lectura multilínea.
*   **Swing:** Interfaz rica (`MainGUI`, `MainChatPanel`) basada en FlatLaf. Renderiza Markdown en tiempo real (`JMarkdownPanel`), gestiona diálogos de configuración dinámicos y un panel de depuración con soporte de evaluación MVEL en caliente.
*   **Web:** `NoemaWebServer` implementado con Javalin. Expone endpoints REST y Server-Sent Events (SSE) para reflejar la consola de manera remota.
*   **Faltante:** Renderizado del historial de [RCS](https://github.com/jjdelcerro/io.github.jjdelcerro.javarcs) en formato visual dentro de las UIs, actualmente dependiente de la salida texto de las herramientas.
*   **Limitaciones:** La UI Web actúa principalmente como un espejo del estado del terminal; el servidor asume despliegues locales (CORS abierto) sin autenticación para el acceso web.

## 3. Valoración de la seguridad

El proyecto demuestra una conciencia de seguridad superior a la media para herramientas experimentales. El `AgentAccessControlImpl` actúa como un *Sandbox* efectivo:

1.  **Prevención de Jailbreak:** Las rutas se resuelven contra la raíz del espacio de trabajo. Cualquier intento de *Path Traversal* es interceptado y denegado a menos que la ruta esté en una lista blanca explícita (`allowed_external_paths`).
2.  **Protección de Integridad:** Se bloquea la escritura en carpetas `.git` y en los archivos de respaldo `,jv` del sistema [RCS](https://github.com/jjdelcerro/io.github.jjdelcerro.javarcs).
3.  **Human-in-the-Loop (HITL):** Cualquier herramienta marcada como `MODE_WRITE` o `MODE_EXECUTION` bloquea el flujo solicitando confirmación explícita del usuario mediante `AgentConsole.confirm()`.
4.  **Aislamiento de procesos:** La herramienta `ShellExecuteTool` es capaz de detectar y utilizar `firejail` para enjaular la ejecución de comandos.

A pesar de esto, si un LLM es engañado (Prompt Injection) para formular comandos destructivos (ej. `rm -rf`), el sistema confía enteramente en que el humano rechace la confirmación.

## 4. Valoración de la Documentación

La documentación adjunta (archivos `.md`) es de calidad sobresaliente. Describe detalladamente la motivación arquitectónica (inversión de dependencias, sistema de archivos como estado único, paginación universal). Los documentos sirven como un registro claro de decisiones de diseño (ADR - *Architecture Decision Records*). El código refleja con alta fidelidad lo descrito en estos documentos, lo que indica que la documentación se ha mantenido actualizada a la par que la implementación técnica.

## 5. Resumen de Deuda Técnica Identificada

1.  **Bloqueo en el Bucle Principal:** El `eventDispatcher` se detiene completamente esperando la ejecución de herramientas lentas o la confirmación humana. Un evento de Telegram que llegue durante este bloqueo quedará encolado hasta que el humano responda o el comando termine.
2.  **Gestión de Vectores en H2:** La búsqueda semántica requiere iterar sobre todos los BLOBs y convertirlos a `float[]` para el cálculo. Es una solución ingeniosa que respeta el requisito de infraestructura nula, pero es ineficiente por diseño (`O(N)`).
3.  **Reintentos Hardcodeados:** En `ReasoningServiceImpl`, si el modelo devuelve la intención de ejecutar una herramienta pero no provee la estructura JSON correcta, el sistema inyecta un mensaje literal `"(reintenta la llamada a la herramienta sin ninguna explicacion)"`. Esto es frágil ante cambios de comportamiento de los LLMs.

## 6. Próximos Hitos (Roadmap Sugerido)

Basado en la lectura del código, los próximos pasos lógicos para estabilizar el "juguete" sin perder su esencia local serían:

*   **Asincronía de Herramientas Lentas:** Desacoplar la ejecución de herramientas marcadas como `MODE_EXECUTION` a un hilo secundario, permitiendo al agente procesar eventos sensoriales (ej. cancelar la tarea en curso respondiendo por chat) mientras el comando se ejecuta.
*   **Manejo Híbrido de Contexto:** Incorporar una heurística que evalúe el costo en tokens estimados para forzar la compactación de la sesión, en lugar de depender únicamente del contador de turnos (actualmente 40).
*   **Caché Semántica:** Implementar una capa de almacenamiento en memoria para los vectores de `SourceOfTruth`, evitando tener que deserializar los BLOBs desde H2 en cada búsqueda semántica.

## 7. Resumen del Estado

| Área | Estado | Calidad del Código | Riesgo |
| :--- | :---: | :---: | :---: |
| **Arquitectura Core** | 🟢 Estable | Alta (DI manual, limpia) | Bajo |
| **Persistencia** | 🟡 En Refinamiento | Media-Alta | Medio (Cuello de botella en BLOBs) |
| **Integración LLM** | 🟢 Estable | Alta (LangChain4j) | Bajo |
| **Herramientas** | 🟢 Avanzado | Alta (Manejo de paginación) | Bajo |
| **Seguridad** | 🟡 Funcional | Alta (Sandbox + HITL) | Medio (Dependencia humana total) |
| **Interfaz Usuario** | 🟢 Avanzado | Alta (Swing reactivo, SSE) | Bajo |
| **Documentación** | 🟢 Excelente | Alta | Nulo |

**Conclusión**

El proyecto Noema es una implementación altamente pragmática y bien estructurada. Cumple estrictamente con sus restricciones autoimpuestas de depender únicamente del sistema de archivos local y bases de datos embebidas. Las soluciones aportadas para la ventana de contexto (compactación narrativa y herramientas paginadas) y para el control de versiones local (usando [RCS](https://github.com/jjdelcerro/io.github.jjdelcerro.javarcs)) demuestran un profundo conocimiento de los límites de los LLMs. Es un entorno de experimentación robusto y completamente operativo para investigaciones a largo plazo.
