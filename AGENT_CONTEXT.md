**Información del Análisis**

*   **Versión Analizada:** 0.1.0 (según definición en `pom.xml` y `AgentManager`)
*   **Fecha de Análisis:** 04-03-2026
*   **Autor del Informe:** Gemini (IA), basado en la inspección estática exhaustiva del código fuente proporcionado.

---

# Informe de Análisis Estático: Proyecto "Noema"

## 1. Visión General

**Noema** es un sistema de agente autónomo implementado en Java, concebido como un proyecto personal. Su objetivo principal no es la generación automatizada de código (desarrollo de software), sino actuar como un compañero conversacional de larga duración enfocado en la investigación, reflexión y el análisis en múltiples ámbitos. 

El diseño del proyecto se fundamenta en mantener una **sesión única y continua en el tiempo**. Carece del concepto tradicional de "abrir/guardar chats"; en su lugar, evoluciona junto al usuario. Por restricciones autoimpuestas de diseño y arquitectura, el sistema está ideado para funcionar sin infraestructura adicional compleja: opera mediante la ejecución de un único archivo JAR, almacenamiento de datos local integrado y conexión a modelos de lenguaje (LLMs) a través de APIs externas.

## 2. Stack Tecnológico

El proyecto está construido sobre un ecosistema maduro y autocontenido en el entorno Java:

*   **Lenguaje:** Java 21.
*   **Gestión de Dependencias y Construcción:** Maven (construcción de "Fat JAR" mediante `maven-shade-plugin`).
*   **Núcleo de IA:** LangChain4j (0.35.0) para la integración con LLMs (OpenAI, OpenRouter, Groq) y gestión de mensajes.
*   **Bases de Datos Integrada:** H2 Database Engine, actuando como almacenamiento transaccional local sin requerir instalación de servidor.
*   **Vectores y Embeddings:** Operaciones de álgebra lineal en memoria (`all-minilm-l6-v2` vía ONNX local en LangChain4j), con persistencia de vectores como BLOBs en H2.
*   **Interfaces de Usuario:**
    *   *GUI:* Swing con FlatLaf (Dark) para una estética moderna, MigLayout para gestión de diseño, y RSyntaxTextArea para edición de código/texto. Conversión interna de Markdown a HTML (commonmark).
    *   *Consola:* JLine3 para una experiencia REPL rica (soporte multilinea, atajos de teclado).
*   **Utilidades y Formatos:** Gson (procesamiento JSON estricto), Apache Tika (extracción de texto de documentos y detección MIME), Natty (procesamiento de fechas en lenguaje natural), Java Diff Utils.
*   **Control de Versiones Local:** Implementación personalizada en Java puro de RCS (`io.github.jjdelcerro.javarcs`).
*   **Comunicaciones:** Telegram Bot API y Jakarta Mail (Angus).

## 3. Estructura de Paquetes, Interfaces e Implementación

El código sigue una arquitectura modular y orientada a interfaces que separa claramente el contrato del comportamiento interno.

*   `io.github.jjdelcerro.noema.lib`: Contiene las **interfaces core**. Es el contrato público del sistema (`Agent`, `AgentTool`, `AgentService`, `SourceOfTruth`, `AgentSettings`, `AgentConsole`).
*   `io.github.jjdelcerro.noema.lib.impl`: Contiene las implementaciones directas (`AgentImpl`) y la lógica base de inyección de recursos.

    *   `persistence`: Implementación del repositorio central (`SourceOfTruthImpl`), entidades inmutables (`TurnImpl`) y abstracciones de memoria híbrida (`CheckPointImpl`).
    *   `services`: El núcleo funcional dividido en dominios (Conversation, Documents, Email, Embeddings, Memory, Scheduler, Sensors, Telegram). Cada uno cuenta con una fábrica (`ServiceFactory`) y una implementación de la interfaz `AgentService`.
    *   `settings`: Implementación del árbol de configuración (con jerarquía y tipos específicos).
    
*   `io.github.jjdelcerro.noema.main`: Puntos de entrada de la aplicación (`Main`, `MainGUI`, `MainConsole`, `BootUtils`).
*   `io.github.jjdelcerro.noema.ui`: Abstracción de la capa de presentación (GUI vs Consola), permitiendo que el core del agente sea agnóstico a la forma en que se comunican los datos al usuario.

## 4. Arquitectura y Diseño

La arquitectura es fuertemente modular, basándose en un patrón de orquestación central centralizado en la interfaz `Agent`. 

1.  **Orientación a Servicios:** Las capacidades del agente se dividen en módulos (`AgentService`). Cada servicio gestiona su propio estado, tablas en base de datos si las requiere, y registra dinámicamente sus herramientas (`AgentTool`) en el orquestador conversacional.
2.  **Inversión de Control Simulada (Sensor/Efector):** Dado que los LLM tienen una naturaleza pasiva (petición/respuesta), el sistema implementa una arquitectura donde los hilos secundarios (escuchas de Telegram, demonio IMAP, temporizadores) inyectan eventos en una cola. El agente consulta esta cola, "engañando" al LLM haciéndole creer que solicitó la información de los sensores (mediante `pool_event`).
3.  **Filosofía Local First:** Todo el conocimiento, índices y memoria persisten en el directorio de trabajo (`.noema-agent`). 

## 5. Mecanismos Principales

### 5.1. Gestión de Memoria

El sistema rechaza la idea de enviar historiales masivos al LLM de forma incontrolada, estructurando el recuerdo en tres capas temporales:

*   **Session (Memoria de Trabajo):** Una lista en memoria de `ChatMessage` que representa el contexto inmediato. Mantiene los últimos turnos y resultados de herramientas.
*   **Persistencia de Turnos (SourceOfTruth):** Cada interacción (del usuario, la respuesta del agente, o el resultado de una herramienta) es atómica y se persiste en H2 (`TurnImpl`). Se genera automáticamente un embedding vectorial para cada turno, permitiendo su recuperación semántica futura.
*   **Puntos de Guardado (CheckPoints) y Compactación:** Cuando la *Session* alcanza un umbral definido por el usuario (ej. 40 turnos), se dispara la *Compactación*. Un LLM de razonamiento especializado (`MemoryManager`) lee el CheckPoint anterior y los turnos nuevos (en formato CSV) y genera un nuevo documento narrativo que consta de:

    1.  Un *Resumen* factual.
    2.  *El Viaje*: Una crónica narrativa detallada que mantiene la evolución de ideas, integrando marcadores técnicos (ej. `{cite:ID-123}`).
    Una vez compactado, los mensajes crudos se eliminan de la *Session*, pero el relato consolidado (CheckPoint) pasa a formar parte constante del contexto del sistema. El agente puede usar herramientas (`lookup_turn`) para resolver las citas y "recordar" el dato crudo si lo necesita.

### 5.2. Gestión de Eventos y Sensores

La proactividad del sistema depende del `SensorsServiceImpl`. Define una semántica para las percepciones del entorno:

*   **Canales y Naturalezas:** Los eventos provienen de canales (Ej. "Telegram", "Email", "SystemClock"). Tienen naturalezas específicas que dictan cómo se encolan:

    *   *DISCRETE:* Cada evento es único (ej. alertas de sistema).
    *   *MERGEABLE:* Si llegan varios estímulos del mismo canal antes de ser procesados, se concatenan (útil para flujos de chat rápido).
    *   *AGGREGATABLE:* Eventos repetitivos se agrupan aumentando un contador (reduce ruido).
    *   *STATE:* Solo importa el último estado reportado.
    
*   **Consumo:** El orquestador (`ConversationServiceImpl`) corre en un bucle virtual permanente. Cuando recibe una notificación del sensor, inyecta en el historial del LLM un par de mensajes falsos simulando la ejecución de la herramienta `pool_event`, adjuntando el JSON con la percepción.

### 5.3. Percepción Temporal

El tiempo es tratado como un estímulo externo de primera clase:

*   **Reloj del Sistema:** Un sensor especial (`SYSTEMCLOCK`) interviene automáticamente. Si ha pasado más de 1 hora desde la última interacción con el usuario, el sistema inyecta un evento informando al agente del tiempo transcurrido (ej. "Ha pasado 1 hora y media desde la última interacción...").
*   **Marcas de Tiempo en Eventos:** Todo evento inyectado en `pool_event` incluye el tiempo exacto del estímulo y la hora actual del sistema.

### 5.4. Indexación de Documentos (DocMapper)

Implementa una variante estructurada del patrón RAG (Retrieval-Augmented Generation):

1.  **Extracción de Estructura:** Utiliza Apache Tika para extraer texto en bruto. Un LLM de "razonamiento" mapea este texto para deducir una tabla de contenidos jerárquica (secciones, niveles, líneas de inicio).
2.  **Generación de Conocimiento:** Un segundo LLM (más rápido/básico) analiza cada sección para generar un resumen y entre 3 y 5 etiquetas (categorías).
3.  **Persistencia:** La estructura se guarda en un JSON (`.struct`), y los metadatos (con el vector de similitud del resumen) se almacenan en H2.
4.  **Consulta Dinámica:** El agente no recibe el documento completo. Recibe el árbol (TOC) y los resúmenes mediante XML (vía `get_document_structure`). Si una sección le interesa, puede solicitar la inyección del texto completo de esa sección (vía `get_partial_document`).

### 5.5. Gestión de la Seguridad

El proyecto asume que un agente con acceso al sistema de archivos es intrínsecamente peligroso e implementa un modelo de contención robusto:

*   **Restricción de Acceso (Sandbox):** `AgentAccessControlImpl` verifica todas las rutas. Por defecto, solo puede acceder a la carpeta de trabajo del proyecto. Se configuran listas blancas (directorios permitidos externamente) y listas negras (rutas prohibidas para lectura o escritura).
*   **Confirmación de Usuario:** Toda herramienta con modo `MODE_WRITE` o `MODE_EXECUTION` invoca `console.confirm()`. El usuario debe autorizar expresamente la operación mostrando los argumentos exactos propuestos por el LLM antes de su ejecución.
*   **Inmutabilidad y RCS:** Antes de sobrescribir, editar o parchear un archivo de texto, las herramientas (como `FileWriteTool`, `FilePatchTool`, `FileSearchAndReplaceTool`) utilizan una librería JavaRCS embebida para realizar un `checkin` automático del estado actual. El agente puede revertir errores mediante `FileRecoveryTool` o consultar cambios con `FileHistoryTool`.
*   **Contención de Ejecución:** `ShellExecuteTool` intercepta llamadas a la terminal. Si detecta la herramienta de sistema linux `firejail`, encapsula la ejecución en un contenedor que proporciona aislamiento del sistema de archivos (Blacklist para la carpeta de datos del agente, Private home).

### 5.6. Flujos del Conversation Manager

El bucle central (`eventDispatcher`) en `ConversationServiceImpl` funciona ininterrumpidamente:

1.  Espera pasivamente en `sensors.getEvent()`.
2.  Al recibir un evento, si es de usuario, se añade al contexto. Si es de sistema, se inyecta simulando `pool_event`.
3.  Llama al LLM para generar una respuesta.
4.  Si el LLM pide ejecutar herramientas, se procesan secuencialmente. Los resultados se añaden al registro persistente (H2) y a la sesión, y se vuelve a invocar al LLM con el nuevo contexto.
5.  El ciclo se detiene cuando el LLM emite una respuesta de texto plano hacia el usuario.
6.  Se verifica si la sesión ha superado el umbral para disparar la compactación de memoria (CheckPoint).

## 6. Herramientas del Agente (Exhaustivo)

El agente expone un amplio catálogo de herramientas (`AgentTool`) estructuradas semánticamente para gobernar su entorno físico y cognitivo:

### Operacionales / Gestión del Sistema

*   **`pool_event`**: Herramienta intrínseca que el orquestador simula haber llamado para inyectar percepciones asíncronas en el flujo de pensamiento.
*   **`get_current_time`**: Devuelve fecha, hora y huso horario actual. Esencial para situarse temporalmente.

### Memoria y Cognición

*   **`lookup_turn`**: Recupera un bloque exacto del historial a partir de su identificador inmutable (`ID-123`). Ideal para desenrollar referencias `{cite:ID}` del Checkpoint.
*   **`search_full_history`**: Realiza una búsqueda vectorial por similitud de cosenos sobre todos los turnos almacenados en la persistencia local.

### Lectura y Exploración del Sistema de Archivos

*   **`file_find`**: Búsqueda mediante patrones *glob* (ej. `**/*.java`). Devuelve metadatos, tamaño y tipo MIME.
*   **`file_grep`**: Búsqueda de coincidencias exactas o regex dentro del contenido de los archivos de texto.
*   **`file_read`**: Lee contenido en texto plano. Incluye un mecanismo de **paginación automática** (offset/limit) y un sistema de *hints* para guiar al modelo si el archivo es demasiado grande.
*   **`file_read_selectors`**: Permite la lectura combinada de múltiples ficheros mediante selectores *glob*.
*   **`file_extract_text`**: Utiliza Apache Tika para extraer texto inteligible de archivos binarios (PDF, DOCX) con paginación.
*   **`file_history`**: Se integra con el motor RCS subyacente para listar el log de revisiones de un archivo modificado previamente por el agente.

### Escritura y Modificación de Archivos (Protegidas por Control de Acceso y RCS)

*   **`file_mkdir`**: Creación de directorios (crea la estructura de rutas completa).
*   **`file_write`**: Creación o sobrescritura total de un archivo de texto.
*   **`file_search_and_replace`**: Edición precisa que busca un bloque de texto exacto y lo sustituye por otro. 
*   **`file_patch`**: Aplica modificaciones estructurales utilizando el formato estandarizado *Unified Diff*.
*   **`file_recovery`**: Usa el motor RCS para deshacer modificaciones y devolver un archivo a una revisión histórica concreta.

### Ejecución y Shell

*   **`shell_execute`**: Lanza comandos bash no interactivos. Diseñado con hilos de recolección para volcar salidas masivas a archivos temporales `.out`. Protegido vía `firejail` si está disponible.
*   **`shell_read_output`**: Complemento de la anterior. Dado el ID de una ejecución, permite paginar y leer el archivo temporal `.out` generado por el comando.

### Internet y Búsqueda

*   **`web_search`**: Utiliza la API de Brave Search para consultar internet. Limpia los JSON de salida para maximizar la ventana de contexto.
*   **`web_get_content`**: Extrae texto limpio de URLs (limpia HTML de scripts y estilos).
*   **`get_weather`**: Accede a la API gratuita Open-Meteo para consultas climáticas.
*   **`get_current_location`**: Identificación geográfica aproximada mediante la API pública de IP.

### Comunicaciones y Sensores

*   **`telegram_send`**: Envía un mensaje directo al usuario vía bot de Telegram. (El servicio de Telegram actúa simultáneamente como Sensor para leer).
*   **`email_list_inbox`**: Consulta las cabeceras (Subject, From, UID) del correo IMAP configurado.
*   **`email_read`**: Descarga el cuerpo de un email específico y lo sanitiza a texto plano mediante Apache Tika.
*   **`email_send`**: Envía un correo vía SMTP.
*   **`sensor_stop` / `sensor_start`**: Permite al agente apagar o encender el "ruido" de ciertos canales (ej. silenciar el email si requiere concentración).
*   **`sensor_status`**: Provee telemetría interna indicando qué sensores están activos y sus frecuencias de activación.

### Gestión de Documentos Estructurados (DocMapper)

*   **`document_index`**: Detona el proceso asíncrono de análisis estructural de un fichero (Tika + LLM).
*   **`document_search` / `document_search_by_categories` / `document_search_by_sumaries`**: Suite de búsqueda híbrida combinando filtrado SQL por categorías y similitud vectorial de resúmenes.
*   **`get_document_structure`**: Devuelve la tabla de contenidos generada en formato XML.
*   **`get_partial_document`**: Recupera el XML estructural inyectando únicamente el texto completo en las secciones de interés indicadas.

## 7. Construcción y Despliegue

La construcción del proyecto está diseñada para evitar dependencias de servidor.

*   Se utiliza Maven con el `maven-shade-plugin` para empaquetar todas las dependencias (LangChain4j, H2, Swing, Tika, etc.) en un único "Fat JAR".
*   El plugin está configurado cuidadosamente con un `ServicesResourceTransformer` (vital para la correcta resolución de los provedores SPI utilizados en el ecosistema Java) y el filtro estándar para exclusión de firmas (`META-INF/*.SF`).
*   Al iniciarse el `.jar`, el sistema detecta si debe arrancar la interfaz gráfica (Swing) o la de consola (JLine) dependiendo de los argumentos (`-c`).
*   La persistencia (`.noema-agent`) se crea automáticamente en el directorio de trabajo, asegurando la portabilidad del entorno del agente.

## 8. Conclusión

El proyecto **Noema** representa un enfoque altamente sofisticado para el diseño de agentes cognitivos personales. Sobresale particularmente en su rechazo a las bases de datos vectoriales pesadas en favor de un enfoque pragmático embebido (H2 + ONNX local), y en su meticuloso sistema de **compactación de memoria**. La narrativa estructurada ("El Viaje" y "Resumen") soluciona el problema de degradación de contexto presente en las soluciones que acumulan historial a fuerza bruta.

La integración de **inmutabilidad controlada** sobre el sistema de archivos local mediante un motor de control de versiones embebido (RCS) y el modelo de contención (`firejail`) refleja un entendimiento profundo de los riesgos de ejecutar código LLM en entornos reales. 

En definitiva, su arquitectura fuertemente orientada a eventos proactivos (Sensores), separada del ciclo síncrono del LLM, posiciona a este sistema no como un simple bot de respuestas, sino como un ente persistente capaz de observar silenciosamente el paso del tiempo y reaccionar de forma autónoma a los estímulos de su entorno.
