
# GEMINI.md - Contexto del Proyecto: ChatAgent de Memoria Híbrida

Este documento proporciona el contexto arquitectónico y técnico para el proyecto `io.github.jjdelcerro.chatagent`, un 
sistema de agente conversacional diseñado para mantener una memoria coherente a largo plazo sin degradación cognitiva.

# Análisis del Proyecto: io.github.jjdelcerro.chatagent

## 1. Visión General

El proyecto es un **Agente Conversacional Autónomo** implementado en Java 21, diseñado para ejecutarse localmente. Su principal diferenciador respecto a implementaciones estándar de RAG (Retrieval-Augmented Generation) o Chatbots simples es su arquitectura de **Memoria Híbrida Determinista**.

El sistema no solo "charla", sino que posee **Agencia (Agency)**: capacidad para interactuar con el sistema de archivos, navegar por la web, gestionar correos electrónicos, conectarse a Telegram y planificar tareas. Está diseñado para mantener coherencia a largo plazo mediante un ciclo de vida de memoria que consolida interacciones crudas ("Turnos") en una narrativa comprimida ("CheckPoints"), evitando la saturación de la ventana de contexto del LLM.

## 2. Stack Tecnológico

El proyecto utiliza un stack moderno y ligero, evitando frameworks pesados como Spring Boot en favor de una inyección de dependencias manual y control directo de recursos.

*   **Lenguaje:** Java 21 (Uso explícito de *Virtual Threads* `Thread.ofVirtual()` para concurrencia ligera).
*   **Orquestación LLM:** `LangChain4j` (v0.35.0) para interactuar con modelos de chat y embeddings.
*   **Base de Datos:** `H2 Database` (v2.2.224). Se utiliza en modo mixto (relacional para metadatos + almacenamiento de vectores BLOB).
*   **Interfaz de Usuario:**
    *   **Consola:** `JLine 3` para una experiencia de terminal rica.
    *   **Escritorio:** `Java Swing` para la interfaz gráfica.
*   **Procesamiento de Documentos:**
    *   `Apache Tika`: Extracción de texto y metadatos de archivos binarios (PDF, DOCX, etc.).
    *   `Natty`: Procesamiento de lenguaje natural para fechas (Scheduler).
*   **Utilidades:**
    *   `Gson`: Serialización JSON.
    *   `java-diff-utils`: Aplicación de parches de código unificados.
    *   `Jakarta Mail`: Protocolos SMTP/IMAP.
    *   `Telegram Bot API`.
*   **Build System:** Maven, utilizando `maven-shade-plugin` para generar "Fat JARs" manejando correctamente los SPI (Service Provider Interfaces).

## 3. Estructura y Arquitectura

El diseño sigue una arquitectura modular basada en interfaces, separando la API pública de la implementación lógica.

### Organización de Paquetes
*   **`io.github.jjdelcerro.chatagent.lib`**: Definición de contratos (Interfaces). Es el núcleo abstracto (`Agent`, `AgentService`, `AgentTool`, `SourceOfTruth`).
*   **`io.github.jjdelcerro.chatagent.lib.impl`**: Implementaciones concretas.
    *   `persistence`: Lógica de H2 y sistema de archivos para la memoria.
    *   `services`: Subdivisión por dominios (`conversation`, `memory`, `docmapper`, `email`, `telegram`, `scheduler`).
*   **`io.github.jjdelcerro.chatagent.ui`**: Abstracción de UI (`AgentConsole`, `AgentUIManager`).
*   **`io.github.jjdelcerro.chatagent.main`**: Boostrap y configuración (`Main`, `MainGUI`, `MainConsole`).

### Patrones de Diseño Clave
1.  **Service Locator:** `AgentLocator` y `AgentManager` centralizan la creación y obtención de servicios, evitando inyección de dependencias compleja.
2.  **Hexagonal (Simplificado):** Los servicios (`ConversationService`, `MemoryService`) actúan como puertos que utilizan adaptadores para persistencia (`SourceOfTruth`) o comunicación externa (`TelegramService`).
3.  **Strategy:** Usado en `AgentTool`. El LLM decide qué herramienta usar, y el sistema despacha la ejecución a la implementación concreta.
4.  **Memento / Snapshot:** Implementado en la clase `Session` y `CheckPoint` para gestionar el estado de la conversación y permitir compactaciones sin perder consistencia.

## 4. Análisis Detallado de Mecanismos Principales

### A. Gestión de Memoria (Híbrida y Determinista)
El sistema implementa una jerarquía de memoria de tres niveles para resolver el problema del "olvido catastrófico":

1.  **Memoria de Trabajo (Session - RAM/JSON):**
    *   Mantiene los mensajes de la conversación actual (`ChatMessage`).
    *   **Backfill:** Asocia mensajes en memoria volátil con IDs persistentes (`turnOfMessage`).
    *   Se guarda en disco (`active_session.json`) para sobrevivir a reinicios.

2.  **Memoria Episódica (Turnos - H2 Database):**
    *   Cada interacción se guarda como un `Turn`.
    *   **Vectorización:** Se calcula un embedding (`all-minilm-l6-v2`) del contenido textual para búsquedas semánticas.
    *   **Política de Truncado:** Si el resultado de una herramienta excede 2KB, se trunca en la base de datos (guardando metadatos) para no inflar el almacenamiento, pero se mantiene completo en la sesión activa mientras sea relevante.

3.  **Memoria Semántica/Narrativa (CheckPoints - Markdown + H2):**
    *   **Trigger:** Cuando la sesión supera ~20 turnos (`COMPACTION_THRESHOLD`), se activa el `MemoryService`.
    *   **Proceso:** Un LLM lee el resumen anterior y los nuevos turnos CSV. Genera un documento narrativo ("El Viaje") que resume lo ocurrido.
    *   **Citación:** El LLM debe insertar referencias explícitas `{cite:ID}` en el texto narrativo. Esto permite que, en el futuro, el agente pueda usar la herramienta `lookup_turn` para "viajar en el tiempo" y recuperar el dato crudo exacto referenciado en el resumen.

### B. Gestión de Eventos (Asincronía Reactiva)
Dado que los LLMs son pasivos (solo responden a inputs), el sistema simula proactividad:

1.  **Sensores:** Hilos independientes en `EmailService` (IMAP IDLE) y `TelegramService` (Long Polling) escuchan cambios.
2.  **Cola de Eventos:** Al detectar algo, inyectan un objeto `Event` en la cola concurrente `pendingEvents` del `ConversationService`.
3.  **Inyección Cognitiva:**
    *   Si el agente está ocioso, despierta el bucle.
    *   El evento se inyecta en el historial como si el agente hubiera ejecutado una herramienta virtual llamada `pool_event`.
    *   El LLM recibe el resultado de esa herramienta (el contenido del email/mensaje) y decide cómo actuar (responder, ignorar, ejecutar otra herramienta).

### C. Percepción Temporal
El agente tiene consciencia del paso del tiempo mediante dos mecanismos en la clase `Session`:

1.  **Marcas Temporales Explícitas:** El System Prompt se inyecta con la hora actual `{NOW}`.
2.  **Detección de Silencio:** En `Session.getContextMessages`, se calcula el delta entre la `lastInteractionTime` y `LocalDateTime.now()`.
    *   Si ha pasado > 1 hora, se inyecta artificialmente un evento `timer`.
    *   El mensaje es: *"Ha pasado [tiempo] desde la última interacción con el usuario."*.
    *   Esto permite al agente saludar adecuadamente ("Buenos días" vs "Seguimos") o entender que el contexto inmediato puede haber cambiado.

### D. Document Mapper (RAG Estructural)
No es un RAG simple de "cortar en trozos y vectorizar". Implementa un **Mapeo Estructural**:

1.  **Indexado (Fase 1 - Razonamiento):** Usa un LLM potente ("Reasoning") para leer el CSV crudo del documento (números de línea) y generar un árbol JSON (Tabla de Contenidos) detectando secciones, capítulos y apéndices.
2.  **Resumen (Fase 2 - Básico):** Itera sobre las secciones detectadas. Usa un LLM más rápido para generar un resumen y categorías para *cada sección*.
3.  **Almacenamiento:** Guarda la estructura (`.struct` JSON) en disco y los metadatos/vectores de los resúmenes en H2.
4.  **Recuperación:**
    *   **Búsqueda Híbrida:** SQL para filtrar por categorías + Similitud Coseno sobre los resúmenes de las secciones.
    *   **Lectura Parcial:** La herramienta `get_partial_document` permite al agente leer el texto crudo solo de las secciones relevantes (usando `BoundedInputStream` y offsets de bytes), ahorrando tokens.

### E. Seguridad (Sandbox y Control)
La clase `AgentAccessControlImpl` protege el sistema de archivos:

1.  **Resolución de Rutas:** Todas las rutas relativas se resuelven contra un `rootPath`.
2.  **Prevención de Path Traversal:** Bloquea intentos de acceder a `../` que salgan de la raíz.
3.  **Confirmación Humana:** El `ConversationService` intercepta las herramientas marcadas como `MODE_WRITE` (ej. `file_write`, `file_patch`). Solicita confirmación al usuario (`console.confirm()`) antes de ejecutar, a menos que sea una herramienta de lectura (`MODE_READ`).

### F. Flujo del Conversation Manager
El bucle principal en `ConversationService.executeReasoningLoop` sigue el patrón ReAct:

1.  Construye contexto: System Prompt + CheckPoint Activo (Narrativa) + Historial Reciente + (Opcional: Evento Timer).
2.  Invoca al LLM (`model.generate`).
3.  **Bifurcación:**
    *   Si el LLM responde texto -> Muestra al usuario, guarda Turno, consolida sesión.
    *   Si el LLM pide `ToolExecutionRequest`:
        *   Verifica permisos (Confirmación de usuario si es escritura).
        *   Ejecuta herramienta (`tool.execute`).
        *   Guarda Turno (`tool_call` + `tool_result`).
        *   Añade resultado a la memoria de sesión.
        *   **Recurso:** Vuelve al paso 2 (el LLM recibe el resultado y razona de nuevo).
4.  Al finalizar el turno, verifica `session.needCompaction()`. Si es true, dispara la compactación asíncrona de memoria.

## 5. Herramientas del Agente (Exhaustivo)

El agente dispone de un conjunto muy amplio de herramientas (`AgentTool`), clasificadas por funcionalidad:

**Sistema de Archivos:**
1.  `file_read`: Lectura de texto plano.
2.  `file_write`: Escritura de archivos (sobrescritura/creación).
3.  `file_mkdir`: Creación de directorios (mkdir -p).
4.  `file_find`: Búsqueda de archivos por patrón GLOB.
5.  `file_grep`: Búsqueda de contenido dentro de archivos.
6.  `file_read_selectors`: Lectura masiva optimizada de múltiples archivos.
7.  `file_extract_text`: Extracción de texto de binarios (PDF, DOCX) usando Apache Tika.
8.  `file_patch`: Aplicación de parches *Unified Diff* (vital para refactorización de código).
9.  `file_search_and_replace`: Reemplazo literal de cadenas.

**Web y Conectividad:**
10. `web_search`: Búsqueda en internet (Brave Search API).
11. `web_get_content`: Descarga y limpieza de HTML a texto (versión Tika).
12. `get_weather`: Consulta de clima (Open-Meteo).
13. `get_current_location`: Geolocalización por IP.
14. `get_current_time`: Fecha y hora actual con zona horaria.

**Memoria y Cognición:**
15. `lookup_turn`: Recuperación precisa de un turno histórico por ID (`{cite:ID}`).
16. `search_full_history`: Búsqueda semántica (vectorial) en toda la base de datos de turnos.

**Documentación (DocMapper):**
17. `document_index`: Iniciar ingestión de un documento.
18. `document_search`: Búsqueda híbrida (categoría + vectorial).
19. `get_document_structure`: Obtener índice XML del documento.
20. `get_partial_document`: Leer contenido real de secciones específicas.
21. `document_search_by_categories`: Filtro SQL por categorías.
22. `document_search_by_sumaries`: Búsqueda vectorial pura en resúmenes.

**Comunicación y Sensores:**
23. `telegram_send`: Envío de mensajes a Telegram.
24. `email_list_inbox`: Listado de cabeceras de email.
25. `email_read`: Lectura de cuerpo de email (sanitizado).
26. `email_send`: Envío de emails SMTP.
27. `schedule_alarm`: Programación de eventos futuros (Natty).
28. `pool_event`: Herramienta interna para consumo de cola de eventos.

## 6. Construcción y Despliegue

*   **Gestión de Dependencias:** Maven.
*   **Empaquetado:**
    *   Usa `maven-shade-plugin`.
    *   Configuración crítica: `ServicesResourceTransformer`. Esto fusiona los archivos `META-INF/services`. Sin esto, bibliotecas modulares como **LangChain4j** (que usa SPI para cargar proveedores de modelos) y **Apache Tika** (para parsers) fallarían al ejecutarse desde el JAR.
*   **Configuración:**
    *   Archivos `settings.properties` y `settingsui.json` en la carpeta `./data`.
    *   La UI (Swing o Consola) permite modificar URLs de proveedores (OpenRouter, Groq, Localhost), API Keys y modelos dinámicamente.
*   **Ejecución:**
    *   Clase `Main`: Detecta flag `-c` para lanzar modo consola (`MainConsole`), por defecto lanza Swing (`MainGUI`).
    *   Inicializa base de datos H2 embebida en `./data/memory` y `./data/service`.

## 7. Conclusión

El proyecto `chatagent` es una implementación **robusta y avanzada** de un agente autónomo local. Destaca por:

1.  **Persistencia Cognitiva Real:** A diferencia de la mayoría de los chats con LLMs que pierden contexto o dependen de un RAG simple, este sistema "escribe su propia historia" mediante CheckPoints narrativos, permitiendo sesiones de días o semanas.
2.  **Operatividad Real:** Las herramientas de parcheado de código (`file_patch`) y la gestión documental estructurada (`docmapper`) lo habilitan como un asistente de ingeniería de software capaz de refactorizar proyectos reales.
3.  **Arquitectura Limpia:** El uso de interfaces, eventos asíncronos simulados y la separación estricta de capas demuestra un diseño de software maduro.
4.  **Independencia:** Funciona completamente local (excepto por la inferencia del LLM, que es configurable hacia Ollama/LocalAI si se desea), sin depender de servicios vectoriales en la nube (Pinecone, etc.) gracias a su implementación propia en H2/Memoria.

**Posibles Mejoras:**
*   **Escalabilidad Vectorial:** La búsqueda por coseno itera en memoria (`EmbeddingFilterImpl`). Para historiales masivos, se debería migrar a una BD con soporte vectorial nativo (ej. PGVector o H2 con extensiones).
*   **Robustez de Parches:** La herramienta de parches depende de que el LLM genere diffs perfectos. Implementar reintentos o un "LSP (Language Server Protocol)" básico mejoraría la fiabilidad en edición de código.
