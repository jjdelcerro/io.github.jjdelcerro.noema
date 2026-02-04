
# GEMINI.md - Contexto del Proyecto: ChatAgent de Memoria Híbrida

Este documento proporciona el contexto arquitectónico y técnico para el proyecto `io.github.jjdelcerro.chatagent`, un sistema de agente conversacional diseñado para mantener una memoria coherente a largo plazo sin degradación cognitiva.

# Análisis del Proyecto: io.github.jjdelcerro.chatagent

## 1. Visión General
El proyecto `io.github.jjdelcerro.chatagent` implementa un **Agente Conversacional Autónomo** diseñado para operar en entornos locales con un enfoque prioritario en la **persistencia de memoria a largo plazo** y la **capacidad operativa sobre el sistema de archivos**.

A diferencia de los chats stateless convencionales, este sistema implementa una arquitectura de "Memoria Híbrida Determinista". Esto significa que el agente no solo "recuerda" vectores semánticos, sino que mantiene una narrativa histórica auditada y citada (determinismo), permitiéndole trabajar en proyectos de larga duración (días o semanas) sin perder el contexto de las decisiones tomadas anteriormente.

El sistema es modular, permitiendo el intercambio de proveedores de LLM (OpenRouter, OpenAI, Local via Ollama) y cuenta con interfaces duales (Consola y Swing).

## 2. Stack Tecnológico

El proyecto está construido sobre tecnologías modernas del ecosistema Java, evitando frameworks pesados de inyección de dependencias (como Spring) en favor de una arquitectura ligera y controlada manualmente.

*   **Lenguaje:** **Java 21**. Hace uso intensivo de **Virtual Threads** (Project Loom) para operaciones de I/O bloqueantes (lectura de archivos, llamadas HTTP, indexado), lo que garantiza alta concurrencia con bajo consumo de recursos.
*   **Orquestación de IA:** **LangChain4j (0.35.0)**. Se utiliza como capa de abstracción para los modelos de chat y embedding.
*   **Base de Datos:** **H2 Database**. Base de datos SQL embebida. Se utiliza de forma híbrida:
    *   Relacional: Para metadatos de turnos, checkpoints y documentos.
    *   Vectorial: Almacena los embeddings como `BLOB` y realiza búsquedas vectoriales mediante implementación propia de similitud del coseno en memoria (evitando depender de bases de datos vectoriales externas).
*   **Motor de Embeddings:** **ONNX Runtime** con el modelo `all-minilm-l6-v2` ejecutándose localmente (CPU).
*   **Interfaz de Usuario:**
    *   **JLine 3:** Para la interfaz de consola avanzada (autocompletado, historial).
    *   **Swing:** Para la interfaz gráfica de escritorio.
*   **Procesamiento de Documentos:** **Apache Tika** (extracción de texto) y **Natty** (parseo de fechas en lenguaje natural).
*   **Herramientas Auxiliares:** `java-diff-utils` (para aplicar parches de código), `Gson` (JSON), `Jakarta Mail` (Email) y `Telegram Bot API`.
*   **Build System:** Maven, utilizando `maven-shade-plugin` para generar un "Fat JAR" autónomo.

## 3. Estructura y Diseño Arquitectónico

El proyecto sigue una arquitectura hexagonal simplificada (Puertos y Adaptadores), separando claramente las interfaces del núcleo (`lib`) de sus implementaciones (`lib.impl`).

### Estructura de Paquetes
*   `io.github.jjdelcerro.chatagent.lib`: Define los contratos (Interfaces) del sistema (`Agent`, `SourceOfTruth`, `MemoryManager`, `AgentConsole`).
*   `io.github.jjdelcerro.chatagent.lib.persistence`: Entidades de dominio (`Turn`, `CheckPoint`) y excepciones.
*   `io.github.jjdelcerro.chatagent.lib.impl`: Implementación de la lógica de negocio.
    *   `persistence`: Implementación sobre H2 y sistema de archivos.
    *   `docmapper`: Lógica de ingestión y estructurado de documentos.
    *   `tools`: Implementaciones de las herramientas (organizadas por dominio: `file`, `web`, `memory`, `mail`, etc.).
*   `io.github.jjdelcerro.chatagent.ui`: Abstracción de la interfaz de usuario.
*   `io.github.jjdelcerro.chatagent.main`: Puntos de entrada (`Main`, `MainConsole`, `MainGUI`) y bootstrap.

### Patrones de Diseño
*   **Source of Truth (Repositorio):** Centraliza el acceso a datos. Garantiza que la BD y el sistema de archivos estén sincronizados.
*   **ReAct (Reason + Act):** Implementado manualmente en `ConversationManagerImpl`. El bucle `executeReasoningLoop` gestiona la interacción cíclica: Pensamiento -> Llamada a Herramienta -> Resultado -> Pensamiento.
*   **Inyección de Dependencias Manual:** No hay contenedores IoC. Las dependencias se inyectan vía constructores, orquestadas por `AgentImpl` y `AgentLocator`.
*   **Strategy:** Usado en las herramientas (`AgenteTool`), permitiendo que el agente seleccione dinámicamente qué ejecutar.
*   **Memento/Snapshot:** Implementado en la gestión de `Session` y `CheckPoint` para guardar estados de la conversación.

## 4. Análisis Detallado de Mecanismos Principales

### A. Gestión de Memoria (El Núcleo del Sistema)
El sistema implementa una **Memoria Híbrida** dividida en tres capas temporales:

1.  **Memoria a Corto Plazo (Session):**
    *   Clase: `Session.java`.
    *   Persistencia: Archivo `active_session.json`.
    *   Funcionamiento: Mantiene los `ChatMessage` en RAM para el contexto inmediato del LLM. Gestiona la correlación entre los mensajes del chat y los IDs de la base de datos.
    *   **Consolidación:** Cuando se completa un turno (User -> AI -> Tools -> Final Answer), se marca como consolidado y se persiste en la BD.

2.  **Memoria a Largo Plazo Atómica (Turns):**
    *   Clase: `TurnImpl` / `SourceOfTruthImpl`.
    *   Persistencia: Tabla `turnos` en H2 + Vectores (BLOB).
    *   **Política de Almacenamiento:** Si el resultado de una herramienta es muy grande (>2KB), se trunca en la base de datos (guardando un JSON con metadatos del tamaño) pero se mantiene completo en el historial de sesión inmediato. Esto evita "bloat" en la BD.
    *   **Búsqueda:** Permite recuperación vectorial (`SearchFullHistoryTool`) o por ID directo (`LookupTurnTool`).

3.  **Memoria Narrativa Consolidada (CheckPoints):**
    *   Clase: `MemoryManagerImpl` / `CheckPointImpl`.
    *   **Mecanismo de Compactación:** Cuando la sesión supera un umbral (~20 turnos), se activa el `MemoryManager`.
    *   **Proceso:**
        1.  Toma el CheckPoint anterior (Resumen + "El Viaje").
        2.  Toma los nuevos turnos en formato CSV.
        3.  Invoca a un LLM específico ("MEMORY_MODEL") con el prompt `prompt-compact-memorymanager.md`.
        4.  **Resultado:** Genera un nuevo documento Markdown que actualiza la narrativa ("El Viaje"), integrando los nuevos eventos y citando las fuentes (`{cite:ID}`).
    *   **Determinismo:** El prompt fuerza al modelo a citar los IDs de los turnos, permitiendo auditoría y evitando alucinaciones sobre el pasado.

### B. Gestión de Eventos (Asincronía en un modelo Síncrono)
Los LLMs son pasivos (Request-Response). Para dotar al agente de "sentidos" (Email, Telegram, Alarmas), se implementa un patrón de **Inversión de Control Simulada**:

1.  **Sensores:** Hilos independientes (ej. `EmailService` con IMAP IDLE o `TelegramTool` con Long-Polling) escuchan el mundo exterior.
2.  **Cola de Eventos:** Al detectar algo, inyectan un evento en `ConversationManagerImpl.pendingEvents` mediante `putEvent()`.
3.  **Inyección en el Flujo:**
    *   Si el agente está ocioso, se despierta el bucle principal.
    *   **El Truco:** Se construye un mensaje artificial de tipo `ToolExecutionResultMessage` asociado a una herramienta ficticia llamada `pool_event`.
    *   Esto "engaña" al LLM haciéndole creer que *él* solicitó consultar los eventos, integrando el estímulo externo en el historial de chat de forma coherente y permitiéndole reaccionar (ej. responder un mail) inmediatamente.

### C. Document Mapper (Ingestión de Conocimiento)
El sistema `DocMapper` convierte documentos no estructurados en conocimiento estructurado consultable.

1.  **Indexado (Proceso Asíncrono):**
    *   Clase: `DocumentMapper`. Usa `TextContent` para leer línea a línea.
    *   **Paso 1 (Estructura):** Usa un **Reasoning LLM** (ej. DeepSeek R1) con el prompt `prompt-extract-structure.md` para analizar el CSV crudo de líneas y generar una Tabla de Contenidos (JSON) con jerarquía, títulos y rangos de líneas.
    *   **Paso 2 (Semántica):** Itera sobre las secciones detectadas y usa un **Basic LLM** (ej. GLM-4) para generar resúmenes y categorías (`prompt-sumary-and-categorize.md`).
    *   **Persistencia:** Guarda metadatos y embeddings de los resúmenes en la tabla `DOCUMENTS` y la estructura completa en un archivo `.struct` (JSON).

2.  **Recuperación:**
    *   Ofrece herramientas para buscar por categorías (SQL), por significado en resúmenes (Vectorial) o recuperar la estructura XML completa para que el agente decida qué leer (`GetPartialDocumentTool`).

### D. Seguridad (Sandbox)
La seguridad es crítica dado que el agente tiene herramientas de escritura en disco (`FileWriteTool`, `FilePatchTool`).

*   Clase: `AgentAccessControlImpl`.
*   **Path Traversal Prevention:** Resuelve todas las rutas contra el directorio raíz del proyecto. Si `target.startsWith(rootPath)` es falso, lanza una `SecurityException`.
*   **Listas Negras:** Bloquea explícitamente la escritura en directorios sensibles como `.git`.
*   **Confirmación Humana:** Herramientas críticas (escritura, ejecución de comandos) pueden requerir confirmación interactiva en consola (`console.confirm()`), interceptada en `ConversationManagerImpl`.

### E. Flujo del Conversation Manager
Es el cerebro orquestador (`ConversationManagerImpl`):

1.  **Entrada:** Recibe texto de usuario o resultado de herramienta.
2.  **Construcción de Contexto:** Combina:
    *   System Prompt (con fecha/hora actual).
    *   CheckPoint activo (Resumen + Narrativa).
    *   Historial de Sesión reciente.
3.  **Inferencia:** Llama al LLM (`model.generate`).
4.  **Despacho de Herramientas:**
    *   Si el LLM pide ejecutar herramienta (`ToolExecutionRequest`), busca la implementación en `toolDispatcher`.
    *   Ejecuta la herramienta (verificando seguridad).
    *   Crea un `Turn` de tipo `tool_execution` y lo guarda en H2.
    *   Añade el resultado al historial y **repite el bucle** (Recursión/Bucle ReAct).
5.  **Respuesta Final:** Si el LLM genera texto, se guarda el turno y se devuelve al usuario.
6.  **Mantenimiento:** Verifica `session.needCompaction()` para disparar la compactación de memoria en segundo plano si es necesario.

## 5. Herramientas del Agente

El set de herramientas es muy completo y orientado a un "Developer Assistant":

*   **Sistema de Archivos:**
    *   `FilePatchTool`: Aplica parches `diff` unificados. Vital para refactorizaciones de código.
    *   `FileSearchAndReplaceTool`: Para cambios simples y precisos.
    *   `FileReadSelectorsTool`: Lectura inteligente de múltiples archivos vía Globs.
    *   `FileExtractTextTool`: Usa Apache Tika para leer PDFs, DOCX, etc.
*   **Web & Entorno:**
    *   `WebSearchTool`: Usa Brave Search API.
    *   `WebGetTikaTool`: Descarga y limpia HTML a texto plano.
    *   `WeatherTool` / `LocationTool` / `TimeTool`: Contexto ambiental.
*   **Memoria:**
    *   `LookupTurnTool`: "Viaje en el tiempo" para recordar un evento exacto por ID.
    *   `SearchFullHistoryTool`: Búsqueda semántica en toda la base de datos.
*   **Comunicación:**
    *   `TelegramTool`: Envío proactivo de mensajes.
    *   `EmailSendTool` / `EmailReadTool`: Gestión de correo.

## 6. Construcción y Despliegue

*   **Gestión de Dependencias:** Maven estándar.
*   **Shadowing:** El `pom.xml` utiliza `maven-shade-plugin` configurado con `ServicesResourceTransformer`. Esto es **crítico** porque librerías como LangChain4j y Tika usan SPI (Service Provider Interface) en `META-INF/services`. Si no se fusionan estos archivos al crear el Uber-JAR, las implementaciones no se cargarían.
*   **Ejecución:** Detecta flags (`-c`) para arrancar en modo consola o GUI (`MainGUI`).

## 7. Conclusión

El proyecto `io.github.jjdelcerro.chatagent` es una implementación **robusta y sofisticada** de un agente RAG (Retrieval-Augmented Generation) avanzado.

**Puntos Fuertes:**
1.  **Independencia y Privacidad:** Al usar H2 local, embeddings ONNX locales y permitir modelos locales (Ollama) o vía API estándar, elimina el vendor lock-in de plataformas como OpenAI Assistants API o bases de datos vectoriales en la nube (Pinecone).
2.  **Memoria Determinista:** La arquitectura de "El Viaje" (CheckPoints narrativos con citas) soluciona el problema de la "pérdida de contexto" en conversaciones largas de forma mucho más efectiva que el simple truncado de ventana o RAG ingenuo.
3.  **Capacidad de "Agencia":** No es un chatbot pasivo. El sistema de eventos y la integración profunda con el sistema de archivos (parches, lectura recursiva) lo convierten en un verdadero asistente de trabajo autónomo.
4.  **Ingeniería de Software Sólida:** Uso correcto de Virtual Threads, gestión de errores, separación de responsabilidades y patrones de diseño claros.

