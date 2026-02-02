
# Informe de Análisis Técnico: io.github.jjdelcerro.chatagent

## 1. Visión General

El proyecto **ChatAgent** es un **Agente Conversacional Autónomo** de alto rendimiento desarrollado en **Java 21**. Su objetivo principal es trascender las limitaciones de los chatbots tradicionales (que pierden el contexto en conversaciones largas) mediante la implementación de un sistema de **Memoria Híbrida Determinista**.

El agente no solo "responde" preguntas; mantiene una narrativa coherente a largo plazo ("El Viaje"), respalda sus afirmaciones en fuentes verificables para evitar alucinaciones y opera con un alto grado de privacidad al procesar datos sensibles (como embeddings) localmente.

Es una pieza de ingeniería de software robusta que aplica patrones de diseño avanzados como **RAG (Retrieval-Augmented Generation)** Narrativo y **ReAct** (Reason + Act), evitando frameworks pesados de inyección de dependencias en favor de una arquitectura manual y modular.

---

## 2. Stack Tecnológico

El proyecto selecciona tecnologías modernas y específicas para maximizar la eficiencia y el control:

*   **Lenguaje:** Java 21 (aprovechando características como Virtual Threads y record classes implícitas en su diseño).
*   **Orquestación de IA:** LangChain4j (versión 0.35.0).
    *   Proporciona la abstracción para conectar con modelos (OpenAI/OpenRouter) y el motor de herramientas.
*   **Modelado de Lenguaje y Embeddings:**
    *   **Modelo de Chat:** Configurable vía propiedades (OpenAI/OpenRouter).
    *   **Embeddings:** `all-minilm-l6-v2` ejecutado localmente mediante **ONNX Runtime**. Esto garantiza privacidad total en la vectorización de la memoria y latencia mínima.
*   **Persistencia de Datos:**
    *   **Base de Datos:** H2 Database (motor SQL embebido). Almacena metadatos de turnos, checkpoints y los vectores de embedding en formato BLOB.
    *   **Almacenamiento de Ficheros:** Sistema de archivos nativo para CheckPoints (Markdown) y logs (CSV).
*   **Utilidades:**
    *   **Apache Tika:** Para extracción limpia de texto de documentos complejos (PDF, HTML).
    *   **Gson:** Para serialización JSON (sesión activa).
    *   **JLine 3:** Para la interfaz de línea de comandos (CLI) interactiva.
    *   **Java Diff Utils:** Para aplicar parches de código de forma determinista.
*   **Comunicación Externa:**
    *   **Telegram Bot API:** Para interacción vía mensajería.
    *   **Jakarta Mail:** Para gestión de correos electrónicos.
*   **Build:** Maven 3.x con el plugin `maven-shade-plugin` para la creación de un *Fat JAR* autocontenido.

---

## 3. Estructura de Paquetes e Interfaces/Implementación

El proyecto sigue una separación estricta entre contratos (API) y lógica concreta (Impl), lo que facilita las pruebas y el intercambio de implementaciones.

### 3.1. `io.github.jjdelcerro.chatagent.lib`
Define el contrato central del sistema.
*   **`Agent`**: (Inferido) Punto de entrada principal del orquestador.
*   **`AgentSettings`**: Interfaz para la gestión de configuraciones (API Keys, URLs). Permite carga y guardado dinámico de propiedades.
*   **`AgentConsole`**: Abstracción de la salida (stdout/stderr), permitiendo inyectar implementaciones mock o UI gráficas en el futuro.
*   **`AgenteTool`**: La interfaz fundamental para las capacidades del agente. Define métodos para obtener la especificación OpenAI (`getSpecification`) y ejecutar la lógica (`execute`). Clasifica las herramientas por tipo (MEMORIA, OPERACIONAL) y modo (LECTURA, ESCRITURA, WEB, EJECUCIÓN).

### 3.2. `io.github.jjdelcerro.chatagent.lib.persistence`
Define las entidades fundamentales de la memoria a largo plazo.
*   **`Turn`**: Representa una unidad atómica de interacción (Prompt del usuario + Pensamiento del modelo + Respuesta + Llamada a herramienta + Resultado). Contiene métodos para obtener el texto concatenado necesario para el embedding (`getContentForEmbedding`) y serialización a CSV (`toCSVLine`).
*   **`CheckPoint`**: Representa una "fotografía" del estado mental del agente en un punto del tiempo. Fusiona múltiples turnos en un resumen narrativo. Su contenido textual reside en disco (lazy loading), mientras que sus metadatos están en BD.
*   **`SourceOfTruth`**: El repositorio principal. Define métodos para crear, añadir y recuperar turnos y checkpoints. Ofrece búsquedas por ID, por texto (semántica) y recuperación de turnos no consolidados.

### 3.3. `io.github.jjdelcerro.chatagent.lib.impl.persistence`
Contiene la implementación concreta sobre H2 y el sistema de archivos.
*   **`SourceOfTruthImpl`**: Implementa la lógica de base de datos, cálculo de embeddings locales y serialización.
*   **`TurnImpl` / `CheckPointImpl`**: POJOS inmutables que implementan las interfaces de persistencia.
*   **`Counter`**: Clase utilitaria para gestionar IDs autoincrementales de forma segura en memoria, inicializados desde el máximo valor actual en la base de datos.

---

## 4. Arquitectura y Diseño

La arquitectura se centra en la **resiliencia de la memoria** y la **privacidad**.

### 4.1. Gestión de Memoria Híbrida (El Núcleo Determinista)

Este es el mecanismo más sofisticado del proyecto. La memoria no es un simple historial de chat; es una estructura tripartita:

1.  **Turnos (Atomic Events):**
    *   Cada interacción (input del usuario, reasoning del modelo, tool call, tool output) se guarda en la tabla `turnos` de H2.
    *   **Embeddings:** Se calculan localmente con `all-minilm-l6-v2`. El vector se serializa a un BLOB en la BD.
    *   **Política de Almacenamiento (`SourceOfTruthImpl`):** Para evitar inflar la base de datos, si el resultado de una herramienta es muy grande (ej. un fichero de código), se guarda un resumen/JSON truncado en la columna `tool_result` de H2, pero el **texto completo se persiste en un archivo CSV (`turns.csv`)**. Esto permite indexación rápida en BD sin perder datos brutos.

2.  **Sesión Activa (RAM + JSON):**
    *   Se gestiona en memoria para acceso rápido durante la conversación en curso.
    *   Se persiste en `active_session.json` para recuperación ante fallos inesperados.

3.  **Compactación y CheckPoints (Narrativa Evolutiva):**
    *   Cuando la sesión supera un umbral (ej. 20 turnos), el `MemoryManager` interviene.
    *   Utiliza un prompt específico para pedirle a un LLM que fusione el historial previo con el nuevo checkpoint.
    *   El resultado (Resumen + "El Viaje" o contexto acumulado) se guarda en un fichero `.md` dentro de la carpeta `/checkpoints` y se registra en la tabla `checkpoints` de H2.
    *   Esto permite al agente "olvidar" el detalle de los turnos intermedios pero conservar el conocimiento esencial, evitando el desbordamiento de contexto (context window overflow) sin perder información crítica.

4.  **Recuperación Híbrida:**
    *   **Semántica:** `getTurnsByText` realiza una búsqueda vectorial manual sobre todos los BLOBs de la BD (calculando similitud del coseno en Java). Esto permite encontrar información relacionada aunque no coincidan las palabras exactas.
    *   **Determinista:** `LookupTurnTool` permite al agente invocar un recuerdo exacto mediante su ID (`{cite:ID}`), obligando al modelo a fundamentar sus respuestas en datos reales y reduciendo alucinaciones.

### 4.2. Gestión de Eventos (Sensores Asíncronos)

El agente es reactivo y proactivo.

*   Los servicios externos (**Telegram** y **Email**) actúan como sensores.
*   Cuando llega un mensaje, no se procesa inmediatamente como si fuera el usuario en la consola. En su lugar, se inyecta en una cola concurrente vía `putEvent`.
*   El `ConversationManager` (orquestador) tiene un mecanismo `pool_event` que simula que el evento es una "respuesta de herramienta" dentro del flujo de chat.
*   Esto permite que el LLM procese interrupciones o información externa dentro de su flujo lógico natural de razonamiento y acción, sin romper el protocolo del historial de chat.

### 4.3. Seguridad (Sandboxing)

La seguridad es crítica, especialmente al dar al agente acceso al sistema de archivos.

*   **`PathAccessControlImpl` (Inferido):** Actúa como un cortafuegos.
*   **Prevención de Jailbreak:** Resuelve rutas canónicas y verifica que todo acceso esté dentro del directorio raíz del proyecto o rutas explícitamente permitidas.
*   **Control de Escritura:** Bloquea escrituras en directorios sensibles como `.git`.

---

## 5. Flujos en el Conversation Manager (Inferido)

Aunque el código completo del `ConversationManager` no se muestra en el snippet, su diseño puede inferirse de las herramientas y la persistencia:

1.  **Inicio:** Carga el último `CheckPoint` y los `UnconsolidatedTurns` en la memoria de trabajo.
2.  **Input del Usuario:** Se añade a la sesión actual.
3.  **Razonamiento (ReAct):** El LLM decide si responder o usar una herramienta.
4.  **Ejecución de Herramienta:**
    *   Si es una herramienta de **Lectura** (ej. `FileSearchAndReplaceTool`), se ejecuta y el resultado se añade al historial como un `Turn`.
    *   Si es una herramienta de **Escritura** (ej. `FilePatchTool`), se ejecuta bajo la supervisión de `PathAccessControl`.
    *   Si es un **Evento Externo**, se inyecta en el flujo.
5.  **Ciclo de Memoria:** Tras cada interacción significativa, el `Turn` se persiste en `SourceOfTruth`. Si se supera el umbral de contexto, se dispara la compactación.
6.  **Respuesta Final:** El modelo genera la respuesta basada en el contexto recuperado (RAG).

---

## 6. Herramientas del Agente (Tools)

El agente cuenta con un arsenal modular implementado en `lib.impl.tools`:

*   **File System:**
    *   `FilePatchTool`: Aplica parches de código usando `java-diff-utils`. Esencial para desarrollo de software.
    *   `FileGrepTool`: Búsqueda de contenido en ficheros.
    *   `FileSearchAndReplaceTool`: Modificación directa de texto.
    *   `FileExtractTextTool`: Usa Apache Tika para leer PDFs/Docs.
*   **Web:**
    *   `WebSearchTool`: Usa Brave Search para información actualizada.
    *   `WebGetTikaTool`: Limpia el HTML de páginas web para extraer texto legible.
*   **Memoria:**
    *   `SearchFullHistoryTool`: Búsqueda semántica.
    *   `LookupTurnTool`: Acceso determinista por ID.
*   **Comunicación:**
    *   Herramientas para enviar y leer correos y Telegrams.

---

## 7. Construcción y Despliegue

*   **Gestor:** Maven.
*   **Empaquetado:** Se utiliza el plugin `maven-shade-plugin` para crear un Uber-JAR (`io.github.jjdelcerro.chatagent.main-1.0.0.jar`).
*   **Configuración Crítica:** El `pom.xml` incluye el `ServicesResourceTransformer`. Esto es vital porque LangChain4j y Apache Tika utilizan SPI (Service Provider Interface). Sin este transformador, al mergear todos los JARs en uno solo, los ficheros `META-INF/services/` se sobrescribirían, provocando fallos de runtime.
*   **Filtros:** Se excluyen archivos de firma de seguridad (`.SF`, `.DSA`, `.RSA`) de dependencias de terceros para evitar errores de validación de firma en el JAR final.

---

## 8. Conclusiones

El proyecto `io.github.jjdelcerro.chatagent` es una implementación de referencia de cómo construir un agente de IA **serio, persistente y seguro**.

**Puntos Fuertes:**

1.  **Memoria a Prueba de Fallos:** La combinación de base de datos SQL + archivos CSV + checkpoints narrativos asegura que la información no se pierda y que el contexto se gestione eficientemente a largo plazo.
2.  **Privacidad por Diseño:** La vectorización local elimina la necesidad de enviar datos privados a APIs de terceros para el proceso de indexación.
3.  **Determinismo:** La obligación de citar fuentes mediante IDs (`{cite:ID}`) reduce significativamente las alucinaciones típicas de los LLM.
4.  **Eficiencia:** El uso de Java 21, hilos virtuales (implícitos en el diseño moderno) y una base de datos ligera como H2 lo hace ideal para ejecutarse en equipos de desarrollo local o servidores modestos.

**Detalles Técnicos Destacados:**

*   La implementación manual de búsqueda vectorial (`SourceOfTruthImpl.getTurnsByText`) sobre H2 demuestra un nivel de control técnico bajo, evitando dependencias de bases vectoriales complejas para prototipos, aunque esto podría ser un cuello de botella si la escala de turnos supera los miles/millones sin paginación optimizada (actualmente carga todos los BLOBs en memoria para calcular el score).
*   La arquitectura de seguridad basada en `PathAccessControl` es robusta frente a intentos de escape del sandbox.

En resumen, es un sistema **"Enterprise-Ready" en miniatura**, preparado para gestionar proyectos de desarrollo de software reales mediante asistencia de IA.
