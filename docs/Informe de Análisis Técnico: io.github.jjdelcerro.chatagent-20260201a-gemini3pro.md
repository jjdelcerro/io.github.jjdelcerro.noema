
# Informe Técnico: ChatAgent de Memoria Híbrida Determinista

## 1. Visión General

El proyecto `io.github.jjdelcerro.chatagent` implementa un **Agente Conversacional Autónomo** diseñado para resolver uno de los problemas fundamentales de los LLMs actuales: la ventana de contexto limitada y la degradación de la memoria en conversaciones largas.

La propuesta de valor central es su **Arquitectura de Memoria Híbrida Determinista**. A diferencia de los sistemas RAG (Retrieval-Augmented Generation) tradicionales que fragmentan documentos arbitrariamente, este agente estructura su memoria en dos capas:
1.  **Memoria a Corto Plazo (Sesión):** Mantiene el contexto inmediato en RAM y JSON.
2.  **Memoria a Largo Plazo (Source of Truth):** Consolida la historia en una base de datos local (H2) y archivos Markdown narrativos ("El Viaje").

El sistema está diseñado para ser **privado** (base de datos y embeddings locales), **auditable** (cada dato recuperado cita su fuente por ID) y **autónomo** (capaz de usar herramientas, leer correos, usar Telegram y manipular el sistema de archivos).


## 2. Stack Tecnológico

El proyecto evita el uso de frameworks de inyección de dependencias pesados (como Spring Boot), optando por una arquitectura "Plain Java" modular y eficiente.

*   **Lenguaje:** **Java 21**. Se aprovecha el uso de *Virtual Threads* (implícito en la versión y el tipo de cargas I/O) y características modernas del lenguaje.
*   **Orquestación de IA:** **LangChain4j (0.35.0)**. Se utiliza como capa de abstracción para conectar con proveedores de LLM (OpenAI/OpenRouter) y para gestionar la ejecución de herramientas (`ToolSpecification`).
*   **Vectorización (Embeddings):** **ONNX Runtime** con el modelo `all-minilm-l6-v2`. Los embeddings se generan localmente (in-process), eliminando latencia de red y fugas de privacidad para la indexación de recuerdos.
*   **Persistencia:**
    *   **H2 Database:** Base de datos SQL embebida. Se usa para almacenar metadatos de los "Turnos", contadores y, crucialmente, los vectores de embeddings (como objetos `BLOB`).
    *   **File System:** Almacenamiento físico de los `CheckPoints` (resúmenes narrativos en Markdown) y logs CSV.
    *   **JSON:** Serialización del estado de la sesión activa (`active_session.json`) usando **Gson**.
*   **Interfaz de Usuario (CLI):** **JLine 3**. Proporciona una terminal rica con historial, edición multilínea y menús interactivos.
*   **Procesamiento de Documentos:** **Apache Tika**. Utilizado para la extracción de texto limpio desde formatos binarios (PDF, DOCX) o HTML sucio.
*   **Utilidades:**
    *   **Java Diff Utils:** Para aplicar parches de código de forma quirúrgica (`FilePatchTool`).
    *   **Jakarta Mail** y **Telegram Bot API**: Para capacidades de comunicación asíncrona.


## 3. Estructura de Paquetes y Diseño

El diseño sigue estrictamente los principios de **Inversión de Dependencias (DIP)**, separando las interfaces (contratos) de las implementaciones.

### Paquetes Principales

*   **`io.github.jjdelcerro.chatagent.lib` (API):** Define el núcleo del dominio.
    *   `Agent`: Interfaz principal del sistema.
    *   `AgentSettings`, `AgentConsole`, `PathAccessControl`: Abstracciones de infraestructura.
*   **`io.github.jjdelcerro.chatagent.lib.persistence` (Modelo de Dominio):**
    *   `Turn`: Unidad atómica de interacción (Usuario + Pensamiento + Respuesta + Herramienta).
    *   `CheckPoint`: Punto de consolidación narrativa.
    *   `SourceOfTruth`: Repositorio central de la verdad (Dao).
*   **`io.github.jjdelcerro.chatagent.lib.impl` (Implementación):**
    *   Contiene la lógica de negocio (`AgentImpl`), los gestores cognitivos (`ConversationManagerImpl`, `MemoryManagerImpl`) y la persistencia (`SourceOfTruthImpl`).
*   **`io.github.jjdelcerro.chatagent.lib.impl.tools`:** Implementaciones concretas de las capacidades del agente, organizadas por dominio (`file`, `web`, `mail`, `memory`, `telegram`).
*   **`io.github.jjdelcerro.chatagent.ui.console`:** Implementación de la interfaz de usuario basada en terminal.

### Patrones de Diseño Detectados

1.  **Repository Pattern:** `SourceOfTruth` actúa como repositorio para Turnos y CheckPoints.
2.  **Factory Pattern:** Métodos estáticos `from()` en `TurnImpl` y `CheckPointImpl` para la creación de instancias.
3.  **Strategy Pattern:** En `AgentActions`, permitiendo cambiar configuraciones (modelos/proveedores) en caliente.
4.  **Observer/Listener Pattern:** En `TelegramTool` y `EmailService` para la detección asíncrona de eventos.


## 4. Análisis Detallado de Mecanismos Principales

### A. Gestión de Memoria (El Núcleo Determinista)
Este es el componente más complejo. Se divide en tres estados:

1.  **Memoria Volátil (Sesión):**
    *   Gestionada por `Session.java`.
    *   Mantiene una lista de `ChatMessage` (User, System, AI, ToolExecution).
    *   **Mecanismo de Backfill:** Asigna IDs de base de datos a los mensajes en memoria una vez que han sido persistidos (`consolideTurn`).

2.  **Persistencia Atómica (Turnos):**
    *   Cada interacción se convierte en un objeto `Turn`.
    *   Se guarda en la tabla `turnos` de H2.
    *   **Política de Almacenamiento:** Si el resultado de una herramienta es demasiado grande (>2KB, definido en `MAX_DB_TEXT_SIZE`), se trunca en la base de datos para no degradar el rendimiento, pero se mantiene completo en el archivo de sesión activo.
    *   **Embedding:** Se calcula un vector sobre el contenido semántico del turno (`textUser + textModel + toolResult`) usando ONNX localmente.

3.  **Memoria a Largo Plazo (Compactación):**
    *   Gestionada por `MemoryManagerImpl`.
    *   **Trigger:** Cuando la sesión supera 20 turnos (`COMPACTION_THRESHOLD`), se activa el proceso.
    *   **Proceso:**
        1.  Toma el último `CheckPoint` (resumen narrativo previo).
        2.  Toma los nuevos `Turn` sin consolidar.
        3.  Envía ambos a un LLM especializado (DeepSeek R1/OpenAI) con el prompt `prompt-compact-memorymanager.md`.
        4.  Genera un nuevo documento Markdown que combina "Resumen Ejecutivo" y "El Viaje" (narrativa detallada).
    *   **Resultado:** La sesión se limpia (se borran los mensajes antiguos) y se sustituyen por el nuevo CheckPoint inyectado en el System Prompt.

### B. Gestión de Eventos (Sistema Sensor/Efector)
El agente no es puramente reactivo (Request-Response). Implementa un bucle de eventos para manejar estímulos externos (Telegram, Email).

*   **Inyección Asíncrona:** Método `Agent.putEvent(channel, priority, text)`. Es *thread-safe*.
*   **Simulación de Herramienta ("pool_event"):**
    *   Los LLMs no tienen concepto de "notificación push".
    *   El `ConversationManagerImpl` encola los eventos en `pendingEvents`.
    *   Cuando el agente está libre, "engaña" al modelo inyectando un mensaje `ToolExecutionResultMessage` ficticio con el nombre `pool_event`.
    *   Esto hace que el modelo crea que él solicitó consultar sus notificaciones, permitiéndole reaccionar ("He recibido un correo de X, voy a leerlo") manteniendo la coherencia del historial de chat.

### C. Seguridad (Sandbox y Path Access)
El acceso al sistema de archivos es crítico para un agente que escribe código.

*   **Clase:** `PathAccessControlImpl`.
*   **Funcionamiento:**
    *   Define una raíz del proyecto (`rootPath`).
    *   Cada vez que una herramienta (`FileWriteTool`, `FileReadTool`) intenta acceder a un fichero, se invoca `resolvePath`.
    *   Se verifica que `targetPath.startsWith(rootPath)`.
    *   **Protección explícita:** Bloquea escrituras en carpetas sensibles como `.git`.
    *   Previene ataques de *Path Traversal* (`../../etc/passwd`).

### D. Flujos del Conversation Manager
El `ConversationManagerImpl` orquesta el bucle de razonamiento (ReAct Loop):

1.  **Entrada:** Recibe texto del usuario o un evento asíncrono.
2.  **Construcción de Contexto:** Carga el último `CheckPoint` + Historial de Sesión + System Prompt Dinámico (con fecha/hora actual).
3.  **Generación LLM:** Envía la solicitud al modelo.
4.  **Detección de Herramientas:**
    *   Si el modelo pide ejecutar una herramienta (`ToolExecutionRequest`), el manager la intercepta.
    *   **Confirmación Humana:** Si la herramienta es de escritura (`MODE_WRITE`), solicita confirmación al usuario por consola (`AgentConsole.confirm`).
    *   Ejecuta la herramienta y captura la salida.
    *   Crea un `Turn` de tipo `tool_execution`.
    *   Vuelve al paso 3 (recursión) con el resultado inyectado.
5.  **Respuesta Final:** Si el modelo genera texto plano, se muestra al usuario y se guarda el `Turn`.
6.  **Compactación:** Verifica si es necesario consolidar la memoria.


## 5. Herramientas del Agente

El set de herramientas es robusto y orientado a tareas de ingeniería de software:

*   **Sistemas de Archivos:**
    *   `FilePatchTool`: Aplica parches `unified diff`. Es más seguro y preciso que reescribir archivos enteros.
    *   `FileSearchAndReplaceTool`: Para cambios simples de strings.
    *   `FileReadSelectorsTool`: Permite leer múltiples archivos o patrones glob (`src/**/*.java`) de una sola vez, optimizando tokens.
*   **Memoria:**
    *   `LookupTurnTool`: Permite al agente citar un recuerdo exacto por ID (`{cite:ID-123}`).
    *   `SearchFullHistoryTool`: Búsqueda semántica (vectorial) sobre toda la base de datos histórica.
*   **Web & External:**
    *   `WebSearchTool` (Brave Search API) y `WebGetTikaTool` (Scraping limpio).
    *   `WeatherTool`, `LocationTool`, `TimeTool` (Contexto ambiental).
*   **Comunicación:**
    *   `EmailService`: Implementa un patrón complejo con IMAP IDLE para escuchar correos y Tika para limpiarlos.

## 6. Construcción y Despliegue

*   **Build System:** Maven.
*   **Empaquetado:** Genera un **Fat JAR** (Uber-Jar) usando `maven-shade-plugin`.
*   **Detalle Crítico:** Configura `ServicesResourceTransformer`. Esto es vital porque librerías como LangChain4j y Tika usan **SPI** (Service Provider Interface) en `META-INF/services`. Sin este transformador, al fusionar los JARs, se sobrescribirían los archivos de servicios, rompiendo la detección automática de implementaciones (ej: parsers de Tika o proveedores de chat).


## 7. Conclusión

El proyecto **ChatAgent** es una implementación avanzada de un sistema agéntico en Java. No es un simple script de chat; es una arquitectura de software completa.

**Puntos Fuertes:**

1.  **Independencia de Frameworks:** Al usar "Plain Java" y JDBC/H2 directo, el sistema es ligero, rápido de arrancar y fácil de depurar.
2.  **Memoria Determinista:** La combinación de "El Viaje" (narrativa) con la base de datos de "Turnos" soluciona elegantemente el problema del olvido catastrófico sin perder el detalle técnico.
3.  **Seguridad por Diseño:** El Sandboxing de archivos y la ejecución local de embeddings demuestran una preocupación real por la privacidad y seguridad.
4.  **Capacidades Reales:** Herramientas como el `FilePatchTool` o la integración bidireccional de Email/Telegram lo hacen apto para tareas de asistencia real, no solo conversacional.

**Áreas Potenciales de Mejora (Observaciones):**

*   **Gestión de Errores en Acciones:** La clase `AgentActionsImpl` y la UI de configuración carecen de manejo robusto de errores si el usuario introduce valores inválidos en tiempo de ejecución.
*   **Concurrencia:** Aunque se usa `synchronized`, la gestión de eventos concurrentes con la base de datos H2 podría beneficiarse de un pool de conexiones más robusto si la carga aumenta.
*   **Escalabilidad Vectorial:** H2 y la búsqueda lineal/simple de vectores funcionan bien para uso personal, pero para historiales masivos se necesitaría una base de datos vectorial dedicada (como Qdrant o pgvector).

En resumen, es un proyecto de ingeniería de software sólido que aplica conceptos de vanguardia en IA (Agentes, RAG, Memoria a Largo Plazo) sobre un stack tecnológico clásico y robusto.
