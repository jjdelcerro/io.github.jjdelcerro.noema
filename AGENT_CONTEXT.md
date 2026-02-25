
# Informe Técnico del Proyecto: Noema (Agente Cognitivo Autónomo)


**Versión Analizada:** 0.1.0

**Fecha de Análisis:** 25 de Febrero de 2025

**Autor del Informe:** Gemini (IA), basado en la inspección estática del código fuente.


## 1. Visión General

**Noema** es una implementación de un Agente Cognitivo Autónomo desarrollado en **Java 21**, diseñado bajo la premisa de ser un compañero de investigación y reflexión de larga duración. A diferencia de los asistentes de codificación (como GitHub Copilot) o los chatbots efímeros, Noema está arquitecturado para mantener una **única sesión continua** a lo largo del tiempo, construyendo una narrativa biográfica ("El Viaje") que evoluciona con cada interacción.

El proyecto destaca por su filosofía de **autocontención y mínima infraestructura**. No requiere bases de datos vectoriales externas (como Pinecone o Weaviate) ni contenedores Docker complejos; todo reside en un único artefacto JAR y una base de datos embebida (H2), apoyándose exclusivamente en APIs de LLMs externos para la inferencia. Es notable el esfuerzo por implementar funcionalidades críticas (como el control de versiones y el sistema de diferencias) de forma nativa en Java, eliminando dependencias del sistema operativo host.

## 2. Stack Tecnológico

El proyecto utiliza un conjunto de tecnologías moderno pero conservador, priorizando la robustez y la portabilidad:

*   **Lenguaje:** Java 21 (Uso de características modernas como *Records*, *Virtual Threads* para concurrencia ligera en indexación y procesos de shell).
*   **Orquestación IA:** `LangChain4j` (v0.35.0). Se utiliza como capa de abstracción para interactuar con modelos de chat (OpenAI/OpenRouter) y modelos de *embeddings* (ONNX local).
*   **Persistencia:**

    *   **H2 Database (Embebida):** Utilizada en modo mixto. Relacional para metadatos de turnos y documentos, y almacenamiento de `BLOBs` para los vectores (embeddings), realizando el cálculo de similitud (distancia coseno) en memoria o mediante alias SQL definidos en Java.
    *   **Sistema de Archivos:** Almacenamiento de `CheckPoints` (narrativa) y estructuras de documentos (`.struct`) en formato JSON/Markdown.
*   **Procesamiento de Datos:**

    *   **Apache Tika:** Extracción de texto y metadatos de documentos binarios y HTML.
    *   **Natty:** Procesamiento de lenguaje natural para fechas (parsing de "tomorrow at 5pm").
    *   **Gson:** Serialización/Deserialización JSON.
*   **Interfaz de Usuario (UI):**

    *   **Swing:** Interfaz gráfica moderna utilizando `FlatLaf` (Look and Feel) y `RSyntaxTextArea` para editores de código y visualización.
    *   **JLine 3:** Interfaz de consola robusta (REPL) con soporte de edición de línea y autocompletado.
*   **Herramientas Base:**

    *   `io.github.jjdelcerro.javarcs`: Implementación propia de RCS (Revision Control System) para control de versiones local.
    *   `java-diff-utils`: Generación y aplicación de parches Unified Diff.
*   **Build System:** Maven, utilizando `maven-shade-plugin` para la generación del "fat-jar".

## 3. Arquitectura y Diseño

El diseño sigue una arquitectura modular basada en servicios, desacoplada mediante interfaces y un patrón de **Service Locator** (`AgentLocator` / `AgentManager`). No utiliza frameworks de Inyección de Dependencias pesados (como Spring), lo que favorece un arranque rápido y menor consumo de memoria.

### Estructura de Paquetes
La separación de responsabilidades es clara:

*   **`io.github.jjdelcerro.noema.lib`**: Define los contratos nucleares (`Agent`, `AgentService`, `AgentTool`, `SourceOfTruth`). Es el API del dominio.
*   **`...lib.impl`**: Implementaciones concretas.

    *   **`persistence`**: Capa de acceso a datos (DAO sobre H2, mapeo de objetos `Turn` y `CheckPoint`).
    *   **`services`**: Lógica de negocio dividida por dominios cognitivos (`conversation`, `memory`, `documents`, `scheduler`, `email`, `telegram`, `embeddings`).
*   **`...ui`**: Abstracción de la interfaz (Consola vs Swing), permitiendo que el núcleo del agente sea agnóstico al entorno de ejecución.

### Diseño del Flujo de Razonamiento (ConversationService)
El núcleo es un bucle **ReAct (Reasoning + Acting)** implementado en `ConversationService`.

1.  **Contexto:** Se construye un prompt que incluye el *System Prompt*, el último *CheckPoint* (resumen narrativo) y los mensajes recientes de la sesión en memoria.
2.  **Inferencia:** Se llama al LLM.
3.  **Evaluación:**

    *   Si es texto: Se presenta al usuario.
    *   Si es una **Tool Execution Request**: El sistema intercepta la petición, verifica permisos (confirmación de usuario si es escritura/ejecución), ejecuta la herramienta en Java, y re-inyecta el resultado en el historial como un mensaje de sistema/tool.
4.  **Recursividad:** El resultado de la herramienta alimenta una nueva inferencia hasta que el modelo decide responder al usuario.

## 4. Herramientas del Agente (Toolset)

El agente dispone de un arsenal exhaustivo (+30 herramientas) clasificadas por servicio. Destaca la capacidad de introspección y la manipulación segura del entorno.

**Sistema de Archivos y Código:**

1.  `file_read`: Lectura paginada de archivos de texto (con soporte de *offsets* y *limits* para archivos grandes).
2.  `file_write`: Escritura atómica de archivos (dispara CI automático).
3.  `file_mkdir`: Creación recursiva de directorios.
4.  `file_find`: Búsqueda de archivos por patrón GLOB.
5.  `file_grep`: Búsqueda de contenido (grep) dentro de archivos.
6.  `file_read_selectors`: Lectura masiva ("bundle") de múltiples rutas o patrones.
7.  `file_extract_text`: Extracción de texto de binarios (PDF, DOCX) vía Tika.
8.  `file_patch`: Aplicación de parches *Unified Diff*.
9.  `file_search_and_replace`: Reemplazo literal de bloques de texto (alternativa determinista a diff).
10. `file_history`: Consulta del historial de versiones (RCS log).
11. `file_recovery`: Restauración de versiones anteriores (RCS checkout).

**Ejecución y Shell:**

12. `shell_execute`: Ejecución de comandos Bash (con soporte de *sandbox* vía `firejail` y captura asíncrona de STDOUT/STDERR a archivos temporales).
13. `shell_read_output`: Lectura paginada de las salidas de comandos guardadas en disco.

**Conectividad y Contexto:**

14. `web_search`: Búsqueda en internet (API Brave Search).
15. `web_get_content`: Descarga y limpieza de contenido web (HTML a Texto vía Tika).
16. `get_weather`: Consulta climática (Open-Meteo).
17. `get_current_location`: Geolocalización aproximada por IP.
18. `get_current_time`: Fecha, hora y zona horaria del sistema.

**Memoria y Cognición:**

19. `lookup_turn`: Recuperación precisa de un evento pasado por su ID (`ID-XXXX`).
20. `search_full_history`: Búsqueda semántica en toda la base de datos de vectores de turnos históricos.

**Documentación (RAG Estructural):**

21. `document_index`: Inicia el proceso de mapeo y comprensión de un documento largo.
22. `document_search`: Búsqueda híbrida (categoría + semántica) sobre resúmenes de documentos.
23. `get_document_structure`: Obtener el índice jerárquico (TOC) de un documento indexado.
24. `get_partial_document`: Leer secciones específicas de un documento basándose en su estructura.

**Comunicación y Agenda:**

25. `telegram_send`: Envío de mensajes a Telegram.
26. `email_list_inbox`: Listado de cabeceras de correo.
27. `email_read`: Lectura de cuerpo de correo saneado.
28. `email_send`: Envío SMTP.
29. `schedule_alarm`: Programación de recordatorios en lenguaje natural.
30. `pool_event`: Herramienta "virtual" para consultar la cola de eventos proactivos.

## 5. Análisis Detallado de Mecanismos Clave

### A. Gestión de Memoria Híbrida
El sistema evita la "ventana deslizante ciega" mediante una arquitectura de tres niveles:

1.  **Session (Memoria de Trabajo):** Mantiene los objetos `ChatMessage` en RAM para el contexto inmediato. Se persiste en `active_session.json` y utiliza un mecanismo de "Backfill" para asociar mensajes en memoria con IDs de turnos persistidos en base de datos.
2.  **Episódica (Turnos - H2):** Cada interacción Usuario-Modelo-Herramienta se cristaliza en un objeto `Turn` inmutable.

    *   Se genera un *embedding* del contenido concatenado.
    *   Se almacena en H2.
    *   **Recorte Inteligente:** Si la salida de una herramienta es excesiva (ej. leer un archivo de 10MB), se guarda truncada en la base de datos (para ahorrar espacio y tokens en búsquedas futuras) pero se mantiene íntegra en la sesión activa mientras dure.
3.  **Semántica (CheckPoints - "El Viaje"):**

    *   Al superar un umbral de turnos (definido en settings, por defecto 40), el `MemoryService` dispara un **Protocolo de Compactación**.
    *   Un LLM especializado (Modelo de Memoria) recibe el *CheckPoint* anterior y los nuevos turnos en formato CSV.
    *   Genera un nuevo documento narrativo que fusiona el pasado con el presente, manteniendo citas explícitas (`{cite:ID}`) a los turnos originales. Esto permite que el agente "recuerde" hechos de hace meses sin tenerlos en la ventana de contexto, pudiendo hacer *drill-down* con `lookup_turn` si necesita el detalle exacto.

### B. Gestión de Eventos (Proactividad Simulada)
El agente rompe el esquema pasivo *Request-Response* mediante un patrón de **Inversión de Control Simulada**.
*   **Sensores:** Hilos independientes (`EmailService`, `TelegramService`, `SchedulerService`) monitorean el mundo exterior.
*   **Inyección:** Cuando ocurre un evento (ej. llega un email), se encola en `pendingEvents`.
*   **Engaño al Protocolo:** El `ConversationService` inyecta artificialmente en el historial un par de mensajes:

    1.  `AiMessage`: Una solicitud ficticia de la herramienta `pool_event`.
    2.  `ToolExecutionResultMessage`: El contenido del evento (JSON/Texto).
*   Esto hace creer al LLM que él decidió consultar sus eventos, manteniendo la coherencia del diálogo y permitiéndole reaccionar (responder al email, avisar al usuario) dentro de su ciclo cognitivo estándar.

### C. Percepción Temporal
El agente es consciente del paso del tiempo y de los silencios:

*   **Prompt Dinámico:** La variable `{NOW}` se inyecta en cada turno.
*   **Marcas de Silencio (Time Gaps):** En `Session.getContextMessages`, se calcula la diferencia temporal entre el último mensaje y el actual. Si supera 1 hora, se inyecta un mensaje de sistema: *"Ha pasado [X tiempo] desde la última interacción..."*. Esto da al agente la capacidad de distinguir entre una pausa para café y el inicio de una nueva jornada, ajustando su saludo o contexto.

### D. Document Mapper (RAG Estructural)
En lugar de trocear documentos arbitrariamente (*chunking*), Noema implementa un proceso de entendimiento en dos fases:

1.  **Extracción de Estructura:** Un LLM de razonamiento analiza el documento crudo (con números de línea) y genera un árbol jerárquico (TOC) en JSON/XML.
2.  **Síntesis y Categorización:** Se itera sobre las secciones detectadas. Un LLM más rápido genera un resumen y etiquetas para cada sección.
3.  **Uso:** El agente puede buscar sobre los resúmenes (vectorial) o navegar el índice (`get_document_structure`) y solicitar solo el texto completo de las secciones relevantes (`get_partial_document`), optimizando dramáticamente el uso de tokens.

### E. Gestión de la Seguridad
El diseño asume que el LLM puede equivocarse o ser manipulado (*prompt injection*), por lo que implementa defensas en capas:

1.  **Sandbox de Archivos (`AgentAccessControl`):** Todas las rutas se resuelven contra un `rootPath`. Se bloquean intentos de *Path Traversal* (`../`) y se prohíbe escritura en directorios sensibles (`.git`, copias de seguridad internas).
2.  **Confirmación Humana:** Las herramientas con `MODE_WRITE` o `MODE_EXECUTION` pausan el hilo del agente y solicitan confirmación explícita (`[Y/n]`) en la consola del usuario antes de proceder.
3.  **CI Automático (JavaRCS):** Antes de cualquier modificación de archivo (`file_write`, `file_patch`), el sistema ejecuta automáticamente un *check-in* (`ci -l`) usando la librería `javarcs`. Esto crea un punto de restauración granular, permitiendo deshacer cambios desastrosos incluso si el usuario los autorizó por error.
4.  **Aislamiento de Procesos:** La herramienta `shell_execute` detecta si `firejail` está instalado en el sistema host. Si lo está, envuelve los comandos en una jaula que restringe el acceso a la red y al sistema de archivos, protegiendo la integridad del agente.

## 6. Construcción y Despliegue

El proyecto utiliza **Maven**. Un aspecto crítico en el `pom.xml` es la configuración del `maven-shade-plugin`. Se utiliza `ServicesResourceTransformer` para fusionar correctamente los archivos SPI (`META-INF/services`) de las dependencias. Esto es vital porque librerías como **LangChain4j** y **Apache Tika** dependen fuertemente de SPI para cargar sus módulos; sin esto, el "fat-jar" fallaría en tiempo de ejecución al no encontrar los proveedores de servicios.

La inicialización (`BootUtils`) despliega una estructura de carpetas `data/` con configuraciones editables (`settings.properties`, prompts) en el primer arranque, facilitando la personalización sin recompilar.

## 7. Conclusión

El proyecto **Noema** es una pieza de ingeniería de software notablemente robusta para ser un proyecto personal. Demuestra una comprensión profunda no solo de la interacción con LLMs, sino de los desafíos sistémicos de crear un agente persistente: gestión de estado, seguridad, tolerancia a fallos y mantenimiento del contexto a largo plazo.

Su enfoque de **"Java-Nativo"** (reimplementando RCS y Diff) y su arquitectura de **Memoria Narrativa ("El Viaje")** lo convierten en una plataforma sólida para la investigación personal, cumpliendo estrictamente con los requisitos de independencia de infraestructura y continuidad de sesión. La ausencia de bases de datos vectoriales externas complejas se suple con ingenio algorítmico (búsqueda vectorial en memoria/H2), lo que lo hace extremadamente portátil y ligero.
