
# GEMINI.md - Contexto del Proyecto: ChatAgent de Memoria Híbrida

Este documento proporciona el contexto arquitectónico y técnico para el proyecto `io.github.jjdelcerro.chatagent`, un 
sistema de agente conversacional diseñado para mantener una memoria coherente a largo plazo sin degradación cognitiva.

# Análisis del Proyecto: io.github.jjdelcerro.chatagent

## 1. Visión General
El proyecto implementa un **Agente Conversacional Autónomo** diseñado para ejecutarse en entornos 
locales (On-Premise/Localhost). Su principal propuesta de valor es una arquitectura de memoria persistente 
y determinista que permite al agente mantener el contexto en proyectos de larga duración (días o semanas) 
sin sufrir la degradación habitual de la ventana de contexto de los LLMs.

El sistema no es un simple chatbot; es un **Asistente de Desarrollo y Operaciones** con capacidad de 
agencia ("Agency"). Puede leer/escribir ficheros, gestionar correos, conectarse a Telegram, navegar por 
la web e ingerir documentación técnica, todo orquestado mediante un bucle de razonamiento ReAct (Reason + Act).

Destaca por su independencia de servicios en la nube para el almacenamiento (usa H2 embebido) y su capacidad 
de cambiar dinámicamente entre proveedores de LLM (OpenRouter, Ollama, OpenAI).

## 2. Stack Tecnológico

El proyecto utiliza **Java 21** como base, aprovechando características modernas como los 
Virtual Threads* (Project Loom) para la concurrencia.

*   **Core:** Java 21 (OpenJDK).
*   **Orquestación LLM:** LangChain4j (v0.35.0).
*   **Base de Datos:** H2 Database (v2.2.224). Se usa en modo embebido mixto:
    *   Relacional para metadatos y logs.
    *   Almacenamiento de vectores (Embeddings) como BLOBs.
*   **Búsqueda Vectorial:** Implementación propia de Similitud del Coseno en memoria (evitando bases de datos vectoriales externas).
*   **Embeddings:** ONNX Runtime con el modelo `all-minilm-l6-v2` ejecutándose en CPU local.
*   **UI/UX:**
    *   **Swing:** Para la interfaz gráfica de escritorio.
    *   **JLine 3:** Para la interfaz de consola interactiva.
*   **Procesamiento de Documentos:** Apache Tika (extracción de texto) y Natty (parseo de fechas).
*   **Utilidades:** `java-diff-utils` (para aplicar parches de código), Gson (JSON), Jakarta Mail.
*   **Build:** Maven con `maven-shade-plugin` para generar "Fat JARs".

## 3. Estructura y Diseño Arquitectónico

El proyecto evita frameworks de Inyección de Dependencias pesados (como Spring Boot) en favor de 
una arquitectura modular con inyección manual y patrón *Service Locator*.

### Organización de Paquetes
*   `io.github.jjdelcerro.chatagent.lib`: **API Pública**. Contiene las interfaces (`Agent`, `SourceOfTruth`, `AgentService`).
*   `io.github.jjdelcerro.chatagent.lib.impl`: **Núcleo Lógico**. Implementaciones de los servicios y persistencia.
    *   `persistence`: Acceso a datos (H2 + FileSystem).
    *   `services`: Dividido por dominios (`conversation`, `memory`, `docmapper`, `email`, `telegram`, `scheduler`).
*   `io.github.jjdelcerro.chatagent.ui`: Abstracción de la interfaz de usuario (`AgentConsole`, `AgentUIManager`).
*   `io.github.jjdelcerro.chatagent.main`: Puntos de entrada y *Bootstrap*.

### Patrones de Diseño Identificados
1.  **Service Locator:** `AgentLocator` centraliza el acceso al `AgentManager`, desacoplando la creación de agentes de su uso.
2.  **Hexagonal / Ports & Adapters:** Separación clara entre el dominio (Turnos, CheckPoints) y la infraestructura (H2, Files).
3.  **Strategy:** Usado intensivamente en `AgentTool`. El agente selecciona qué herramienta ejecutar basándose en la especificación semántica.
4.  **Memento:** La clase `Session` gestiona el estado volátil de la conversación y permite "viajar" o compactar el estado.
5.  **Inversión de Control Simulada:** Para gestionar eventos asíncronos (Email/Telegram) dentro de un bucle síncrono de LLM.

## 4. Análisis Detallado de Mecanismos Principales

### A. Gestión de Memoria (Arquitectura Híbrida)
El sistema resuelve el problema del "olvido catastrófico" mediante tres capas de persistencia:

1.  **Memoria a Corto Plazo (Session):**
    *   Mantiene los mensajes (`ChatMessage`) en RAM para el contexto inmediato.
    *   Persiste en disco (`active_session.json`) para recuperar la sesión tras reinicios.
    *   Implementa un mecanismo de "backfill" para asociar mensajes en memoria con IDs de turnos en base de datos.

2.  **Memoria Histórica Atómica (Turns):**
    *   Cada interacción (Usuario -> AI -> Herramienta) se guarda como un `Turn` en la tabla `turnos` de H2.
    *   **Vectorización:** Se calcula el embedding del contenido textual y se guarda como BLOB.
    *   **Política de Truncado:** Si el resultado de una herramienta excede 2KB, se trunca en la BD (guardando metadatos JSON) pero se mantiene completo en la sesión activa. Esto evita que la BD crezca descontroladamente con volcados de archivos grandes.

3.  **Memoria Narrativa (CheckPoints):**
    *   Cuando la sesión supera un umbral (~20 turnos), el `MemoryManager` compacta la historia.
    *   Usa un LLM para generar un documento Markdown ("El Viaje") que resume lo ocurrido, citando explícitamente los IDs de los turnos (`{cite:ID}`).
    *   Esto permite al agente "recordar" hechos pasados leyendo un resumen narrativo y, si necesita detalles, usar la herramienta `LookupTurnTool` para recuperar el dato crudo exacto usando el ID citado.

### B. Gestión de Eventos (Asincronía)
El agente posee "sentidos" pasivos (Email, Telegram, Scheduler). Dado que el LLM solo actúa cuando se le invoca:

1.  Hilos demonio independientes escuchan eventos (ej. IMAP IDLE).
2.  Al detectar un evento, lo inyectan en una cola concurrente `pendingEvents`.
3.  Si el agente está ocioso, se despierta el bucle principal.
4.  Se inyecta un mensaje artificial `ToolExecutionResultMessage` de una herramienta virtual llamada `pool_event`.
5.  Esto engaña al LLM haciéndole creer que acaba de consultar sus notificaciones, permitiéndole reaccionar proactivamente.

### C. Document Mapper (Ingestión de Conocimiento)
Implementa un sistema RAG (Retrieval-Augmented Generation) avanzado para documentos grandes:

1.  **Indexado Estructural:** Usa un LLM de razonamiento (ej. DeepSeek R1) para analizar el CSV crudo del documento y generar una Tabla de Contenidos (JSON) jerárquica.
2.  **Resumen Semántico:** Itera sobre las secciones detectadas y usa un LLM básico para generar resúmenes y categorías.
3.  **Recuperación Híbrida:**
    *   Permite búsquedas SQL por categorías (`DocumentSearchByCategoriesTool`).
    *   Permite búsqueda vectorial sobre los resúmenes (`DocumentSearchBySumariesTool`).
    *   Permite leer solo secciones específicas (`GetPartialDocumentTool`), ahorrando tokens.

### D. Seguridad (Sandbox)
El control de acceso (`AgentAccessControlImpl`) es crítico ya que el agente puede escribir en disco:

*   **Path Traversal:** Resuelve todas las rutas contra el directorio raíz. Bloquea cualquier intento de acceder a `../` fuera del proyecto.
*   **Confirmación Humana:** El `ConversationService` intercepta las llamadas a herramientas de escritura (`MODE_WRITE`) y solicita confirmación interactiva al usuario (`console.confirm()`) antes de ejecutar.
*   **Protección de Metadatos:** Reglas hardcodeadas impiden escribir en carpetas sensibles como `.git`.

### E. Conversation Manager (Flujo ReAct)
Es el cerebro del sistema:
1.  Construye el contexto: System Prompt + CheckPoint Activo (Narrativa) + Historial Reciente.
2.  Invoca al LLM.
3.  Si el LLM pide ejecutar herramienta -> Ejecuta -> Guarda `Turn` -> Añade resultado al historial -> **Repite el bucle**.
4.  Si el LLM responde texto final -> Muestra al usuario -> Guarda `Turn`.
5.  Verifica si es necesaria la compactación de memoria.

## 5. Herramientas del Agente

El agente dispone de un arsenal exhaustivo de herramientas (`AgentTool`), organizadas por dominio:

### Sistema de Archivos
1.  **`file_read`**: Lee un archivo completo.
2.  **`file_write`**: Escribe/Sobrescribe archivo (crea directorios padre automáticamente).
3.  **`file_patch`**: Aplica parches *Unified Diff*. Vital para refactorización de código segura.
4.  **`file_search_and_replace`**: Reemplazo exacto de cadenas de texto.
5.  **`file_find`**: Búsqueda de archivos por patrón GLOB (ej. `**/*.java`).
6.  **`file_grep`**: Búsqueda de contenido dentro de archivos (grep simple).
7.  **`file_mkdir`**: Creación de directorios.
8.  **`file_read_selectors`**: Lectura masiva de múltiples archivos mediante lista de patrones.
9.  **`file_extract_text`**: Usa Apache Tika para extraer texto de binarios (PDF, DOCX).

### Web y Conectividad
10. **`web_search`**: Búsqueda en internet usando Brave Search API.
11. **`web_get_content`**: Descarga y limpieza de HTML a texto (versión simple y versión Tika).
12. **`get_weather`**: Consulta clima (Open-Meteo).
13. **`get_current_location`**: Geolocalización por IP.
14. **`get_current_time`**: Fecha y hora actual.

### Memoria y Cognición
15. **`lookup_turn`**: Recupera un turno pasado por su ID (Time Travel).
16. **`search_full_history`**: Búsqueda semántica en toda la base de datos de turnos.

### Documentación (RAG)
17. **`document_index`**: Inicia el proceso de ingestión de un fichero.
18. **`document_search`**: Búsqueda híbrida (categoría + semántica).
19. **`get_document_structure`**: Obtiene el índice XML de un documento procesado.
20. **`get_partial_document`**: Recupera el texto completo de secciones específicas.
21. **`document_search_by_categories`**: Filtrado SQL.
22. **`document_search_by_sumaries`**: Búsqueda vectorial en resúmenes.

### Comunicación y Eventos
23. **`telegram_send`**: Envía mensajes proactivos al usuario.
24. **`email_list_inbox`**: Lista cabeceras de correos recientes.
25. **`email_read`**: Lee el cuerpo de un correo por UID.
26. **`email_send`**: Envía correos SMTP.
27. **`schedule_alarm`**: Programa recordatorios en lenguaje natural (Natty).
28. **`pool_event`**: Herramienta interna para consultar la cola de eventos.

## 6. Construcción y Despliegue

*   **Configuración:** Usa `settings.properties` y `settingsui.json` para definir URLs de proveedores, API Keys y modelos. La UI permite modificar esto en tiempo de ejecución.
*   **Empaquetado:** El `pom.xml` configura `maven-shade-plugin` con `ServicesResourceTransformer`. Esto es **fundamental** para fusionar los ficheros `META-INF/services`, ya que LangChain4j y Tika usan SPI (Service Provider Interface) para cargar sus módulos. Sin esto, el JAR ejecutable fallaría.
*   **Ejecución:**
    *   Clase `Main`: Detecta argumentos.
    *   `-c`: Lanza `MainConsole` (JLine).
    *   Defecto: Lanza `MainGUI` (Swing).

## 7. Conclusión

El proyecto `io.github.jjdelcerro.chatagent` es una implementación **avanzada y pragmática** de un agente autónomo.

**Puntos Fuertes:**

1.  **Arquitectura de Memoria:** La combinación de "Narrativa Determinista" (CheckPoints) + "Evidencia Cruda" 
    (Turns/Vectors) es superior a los enfoques RAG ingenuos para mantener la coherencia a largo plazo.
2.  **Autonomía Local:** Al no depender de bases de datos vectoriales en la nube ni orquestadores externos, 
    ofrece privacidad y baja latencia (excepto por la llamada al LLM, que es configurable).
3.  **Capacidad Operativa:** Las herramientas de parcheado (`file_patch`) y lectura inteligente de 
    documentos (`docmapper`) lo posicionan como una herramienta real de ingeniería asistida, no solo un chat.
4.  **Diseño Robusto:** El uso de Virtual Threads para I/O y la separación de capas demuestra madurez técnica.

**Áreas de Atención:**

1.  **Escalabilidad de Vectores:** La búsqueda por coseno se hace iterando en memoria (`SourceOfTruthImpl`). 
    Para historiales masivos (>100k turnos), esto podría degradar el rendimiento, requiriendo migrar a H2 
    con soporte vectorial o una BD externa.
2.  **Dependencia del Prompt:** La calidad de la memoria depende fuertemente de que el modelo siga el 
    protocolo de "El Viaje" definido en `prompt-compact-memorymanager.md`. Modelos pequeños (<8B) podrían 
    tener dificultades para seguir instrucciones tan complejas.
