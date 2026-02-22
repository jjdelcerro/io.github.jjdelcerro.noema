
# Informe Técnico: Arquitectura de Agente Conversacional de Memoria Híbrida

## 1. Visión General

El proyecto `io.github.jjdelcerro.chatagent` es una implementación de referencia de un **Agente Cognitivo Autónomo** desarrollado en Java 21. A diferencia de los sistemas RAG (Retrieval-Augmented Generation) convencionales o chatbots efímeros, este sistema está diseñado para la **persistencia cognitiva a largo plazo** y la **agencia operativa real** en entornos locales.

Su arquitectura resuelve el problema del "olvido catastrófico" mediante un ciclo de memoria híbrido que consolida interacciones en una narrativa coherente ("El Viaje"). Además, opera como un sistema totalmente **autocontenido (Self-Contained)**: se despliega como un único JAR que incluye todas las capacidades necesarias (base de datos, motor de búsqueda, control de versiones y utilidades de parcheado) sin requerir software externo instalado en el sistema operativo anfitrión.

## 2. Stack Tecnológico

El proyecto prioriza la portabilidad y la independencia, utilizando una arquitectura "Java-Native" para todas las operaciones críticas.

*   **Lenguaje:** Java 21 (Uso intensivo de *Virtual Threads* para concurrencia ligera).
*   **Orquestación IA:** `LangChain4j` (v0.35.0) como capa de abstracción para modelos de chat y embeddings.
*   **Persistencia:**
    *   **Base de Datos:** `H2 Database` (v2.2.224) en modo embebido. Se utiliza en configuración mixta: Tablas relacionales para metadatos + almacenamiento de Vectores en columnas `BLOB`.
    *   **Archivos:** Sistema de archivos local para almacenamiento de Checkpoints (Markdown) y logs.
*   **Operaciones de Archivos y Código (Java Nativo):**
    *   **Parcheado:** `java-diff-utils` (v4.12). Permite aplicar parches *Unified Diff* generados por el LLM directamente en memoria JVM, sin depender del comando `patch` de Unix.
    *   **Control de Versiones:** `io.github.jjdelcerro.javarcs`. Una implementación nativa en Java del sistema RCS, permitiendo al agente versionar los archivos que modifica sin instalar binarios externos.
*   **Procesamiento de Datos:**
    *   `Apache Tika`: Extracción de texto y metadatos de documentos binarios (PDF, DOCX, HTML).
    *   `Natty`: Procesador de lenguaje natural para fechas (usado en el Scheduler).
    *   `Gson`: Serialización y protocolo JSON.
*   **Interfaz de Usuario:**
    *   **GUI:** Swing con `FlatLaf` para una estética moderna y `RSyntaxTextArea` para editores de código.
    *   **TUI:** `JLine 3` para una experiencia de terminal rica.

## 3. Estructura y Arquitectura

El diseño sigue una arquitectura estrictamente modular basada en **Interfaces** y el patrón **Service Locator**, evitando la sobrecarga de frameworks de inyección de dependencias como Spring.

### Estructura de Paquetes
*   **`io.github.jjdelcerro.chatagent.lib`**: Contratos e interfaces públicas (`Agent`, `SourceOfTruth`, `AgentTool`). Es el núcleo agnóstico.
*   **`io.github.jjdelcerro.chatagent.lib.impl`**: Implementaciones concretas.
    *   `persistence`: Lógica de H2, gestión de `Turn` y `CheckPoint`.
    *   `services`: Lógica de negocio por dominios (`conversation`, `memory`, `documents`, `scheduler`, `email`, `telegram`).
*   **`io.github.jjdelcerro.chatagent.ui`**: Capa de abstracción de UI (Consola vs Swing).
*   **`io.github.jjdelcerro.chatagent.main`**: Bootstrap, detección de argumentos y configuración inicial.

### Diseño Arquitectónico
1.  **Service Locator:** `AgentLocator` centraliza el acceso a servicios singleton, facilitando el desacoplamiento.
2.  **Hexagonal (Puertos y Adaptadores):** Los servicios de negocio no dependen directamente de la infraestructura externa; usan interfaces para persistencia (`SourceOfTruth`) y comunicación.
3.  **Strategy:** El mecanismo de herramientas (`AgentTool`) permite que el LLM seleccione dinámicamente qué estrategia ejecutar (leer archivo, buscar en web, recordar) en tiempo de ejecución.
4.  **Observer/Event Loop:** Implementado para la detección de estímulos externos (Email, Telegram) e inyección en el flujo cognitivo.

## 4. Análisis Detallado de Mecanismos

### A. Gestión de Memoria (Híbrida y Determinista)
El sistema gestiona la memoria en tres niveles para equilibrar precisión, contexto y coste:

1.  **Memoria de Trabajo (Session - RAM):**
    *   Mantiene los mensajes de la conversación activa (`ChatMessage`).
    *   **Backfill:** Asocia mensajes volátiles con IDs persistentes (`TurnID`) para rastrear qué se ha guardado ya en base de datos.
    *   Persiste en `active_session.json` para sobrevivir a reinicios.
2.  **Memoria Episódica (Turnos - H2):**
    *   Cada interacción (Usuario/Modelo/Herramienta) se guarda como un `Turn` inmutable.
    *   **Vectorización:** Se calcula y almacena un embedding del contenido textual.
    *   **Optimización:** Si el resultado de una herramienta es masivo (>2KB), se trunca en la BD (guardando metadatos) para no inflar el almacenamiento, aunque se mantiene en la RAM de la sesión actual.
3.  **Memoria Semántica (CheckPoints - Narrativa):**
    *   Al superar un umbral de turnos (~40), el `MemoryService` invoca a un LLM especializado.
    *   **Protocolo de Compactación:** El LLM recibe el resumen anterior y los nuevos turnos en CSV. Genera un documento narrativo ("El Viaje") que integra los nuevos eventos.
    *   **Trazabilidad:** El LLM inserta referencias explícitas `{cite:ID}` en el texto. Esto permite que el agente use posteriormente la herramienta `lookup_turn` para recuperar el dato crudo exacto referenciado en la narrativa.

### B. Gestión de Eventos (Proactividad)
El sistema permite al agente reaccionar a estímulos asíncronos en un entorno LLM síncrono (Request-Response):

1.  **Sensores:** Hilos independientes (`EmailService`, `TelegramService`) escuchan cambios.
2.  **Cola de Eventos:** Los sensores depositan objetos `Event` en una cola concurrente en `ConversationService`.
3.  **Inyección Cognitiva:**
    *   Si el agente está ocioso, el bucle se activa.
    *   El evento se inyecta en el historial simulando ser el resultado de una herramienta virtual llamada `pool_event`.
    *   Para el LLM, parece que él solicitó ver los eventos, manteniendo la coherencia del chat y forzando una evaluación de la información.

### C. Percepción Temporal
El agente posee consciencia del paso del tiempo y contexto cronológico:

1.  **System Prompt Dinámico:** La fecha y hora actual `{NOW}` se inyectan en cada interacción.
2.  **Marcas de Silencio (Timer):**
    *   El sistema calcula el delta de tiempo entre el último mensaje y el actual.
    *   Si el tiempo > 1 hora, se inyecta artificialmente un mensaje de sistema: *"Ha pasado [tiempo] desde la última interacción..."*.
    *   Esto permite al agente ajustar su saludo o reevaluar la validez del contexto inmediato.

### D. Document Mapper (RAG Estructural)
Un sistema avanzado de ingestión de documentos que supera al RAG tradicional ("chunking plano"):

1.  **Fase 1 (Razonamiento):** Un LLM potente lee el documento crudo (con números de línea) para generar un Árbol de Contenidos (JSON) identificando secciones lógicas.
2.  **Fase 2 (Síntesis):** Se itera sobre las secciones detectadas. Un LLM más rápido genera un resumen y etiquetas para cada sección.
3.  **Recuperación:**
    *   **Búsqueda Híbrida:** SQL para filtrar por categorías + Similitud Vectorial sobre los resúmenes.
    *   **Lectura bajo demanda:** La herramienta `get_partial_document` permite al agente leer el texto original completo de una sección específica, ahorrando tokens al no leer todo el documento.

### E. Seguridad (Sandbox)
La clase `AgentAccessControlImpl` protege el sistema anfitrión:
*   Todas las rutas de archivo se resuelven contra un directorio raíz (`rootPath`).
*   Se bloquean intentos de *Path Traversal* (`../`) fuera de la raíz.
*   **Confirmación Humana:** Las herramientas marcadas con `MODE_WRITE` (escritura, borrado, parches) pausan la ejecución y solicitan confirmación explícita del usuario en la consola antes de proceder.

### F. Flujo del Conversation Manager
El núcleo de decisión (`executeReasoningLoop`) implementa un ciclo **ReAct**:

1.  Construye el contexto (System Prompt + Narrativa CheckPoint + Historial Reciente).
2.  Invoca al LLM.
3.  **Decisión:**
    *   Si es texto -> Muestra al usuario, guarda turno, comprueba compactación.
    *   Si es `ToolExecutionRequest`:
        *   Verifica permisos (Confirmación si es escritura).
        *   Ejecuta la herramienta correspondiente.
        *   Guarda el resultado en memoria.
        *   **Recursión:** Vuelve al paso 2 con el nuevo input (el resultado de la herramienta).

## 5. Herramientas del Agente

El agente cuenta con un set exhaustivo de más de 30 herramientas, todas ejecutadas localmente en la JVM:

**Operaciones de Archivos (Java Nativo):**
1.  `file_read`: Lectura de texto con paginación.
2.  `file_write`: Escritura de archivos.
3.  `file_mkdir`: Creación de directorios.
4.  `file_find`: Búsqueda de archivos (GLOB).
5.  `file_grep`: Búsqueda de contenido (Regex).
6.  `file_read_selectors`: Lectura masiva optimizada.
7.  `file_extract_text`: Extracción de texto de binarios (Tika).
8.  `file_patch`: Aplicación de parches *Unified Diff* (vía `java-diff-utils`).
9.  `file_search_and_replace`: Reemplazo de cadenas simples.
10. `file_history`: Historial de versiones (vía `javarcs`).
11. `file_recovery`: Recuperación de versiones anteriores (vía `javarcs`).

**Conectividad y Web:**
12. `web_search`: Búsqueda web (API Brave).
13. `web_get_content`: Descarga y limpieza de HTML (Tika).
14. `get_weather`: Clima (Open-Meteo).
15. `get_current_location`: Geolocalización IP.
16. `get_current_time`: Fecha y hora precisa.

**Memoria y Cognición:**
17. `lookup_turn`: Recuperación de dato histórico por ID (`{cite:ID}`).
18. `search_full_history`: Búsqueda semántica en toda la BD.

**Documentación (DocMapper):**
19. `document_index`: Indexar nuevo documento.
20. `document_search`: Búsqueda híbrida.
21. `get_document_structure`: Obtener índice del documento.
22. `get_partial_document`: Leer contenido real de sección.
23. `document_search_by_categories`: Filtro por categoría.
24. `document_search_by_sumaries`: Búsqueda vectorial pura.

**Comunicación y Sensores:**
25. `telegram_send`: Enviar mensaje Telegram.
26. `email_list_inbox`: Listar emails.
27. `email_read`: Leer email.
28. `email_send`: Enviar email.
29. `schedule_alarm`: Programar recordatorio (Natty).
30. `pool_event`: Consumir cola de eventos.

## 6. Construcción y Despliegue

*   **Build System:** Maven.
*   **Empaquetado:** Uso de `maven-shade-plugin` con `ServicesResourceTransformer`. Esto es crítico para generar un "Fat JAR" funcional, ya que fusiona los archivos SPI (`META-INF/services`) necesarios para que LangChain4j y Tika descubran sus plugins automáticamente.
*   **Configuración:** Archivos `.properties` autogenerados en `./data`.
*   **Ejecución:**
    *   GUI (Swing) por defecto.
    *   Consola (JLine) mediante el flag `-c`.
    *   Servidor web H2 embebido activo en puerto 8082 para inspección de datos.

## 7. Conclusión

El proyecto `chatagent` demuestra un nivel de madurez técnica muy superior a un "juguete". Representa una arquitectura de referencia para **Agentes Autónomos Locales en Java**.

Sus fortalezas clave son:
1.  **Autocontención y Portabilidad:** Al utilizar librerías Java nativas para operaciones complejas como `diff` y `rcs`, elimina dependencias del sistema operativo. El mismo JAR funciona idénticamente en Windows, Linux y Mac.
2.  **Memoria Robusta:** La implementación de memoria híbrida con compactación narrativa aborda eficazmente el problema del contexto limitado, permitiendo sesiones de larga duración.
3.  **Seguridad por Diseño:** El uso de sandbox para archivos y la confirmación humana para acciones destructivas lo hace viable para entornos de producción o desarrollo real.
4.  **Arquitectura Limpia:** La separación de interfaces, el uso de eventos para la proactividad y el desacoplamiento de servicios facilitan la mantenibilidad y extensión.

**Nota:** La implementación actual de búsqueda vectorial (`EmbeddingFilterImpl`) realiza cálculos en memoria (iterando sobre BLOBs recuperados de H2). Esto es óptimo para uso personal y portabilidad, pero para escalar a millones de vectores requeriría migrar a una base de datos con índice vectorial nativo (como PostgreSQL/pgvector o Qdrant).

