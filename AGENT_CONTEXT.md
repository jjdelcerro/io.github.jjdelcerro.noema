Aquí tienes un informe técnico detallado basado en el análisis profundo del código fuente proporcionado.

---

### Informe Técnico de Proyecto: ChatAgent (io.github.jjdelcerro.chatagent)

**Versión Analizada:** 1.0.0
**Fecha de Análisis:** 22 de Febrero de 2026
**Autor del Informe:** Gemini (IA), basado en la inspección estática del código fuente.

---

## 1. Visión General

El proyecto `ChatAgent` es una implementación avanzada de un **Agente Cognitivo Autónomo y Persistente** escrito en Java. A diferencia de los chatbots convencionales o asistentes de codificación efímeros, este sistema está diseñado como un **compañero de investigación de largo recorrido**. Su filosofía central es la **autocontención** (funciona como un único JAR sin dependencias de infraestructura externa compleja) y la **persistencia narrativa**, resolviendo el problema del "olvido catastrófico" de los LLMs mediante un sistema de memoria híbrida.

El agente no solo responde a estímulos reactivos (chat), sino que posee capacidades proactivas (sensores de eventos) y una percepción del paso del tiempo, lo que le permite mantener una "única sesión" que evoluciona con el usuario.

## 2. Stack Tecnológico

El proyecto apuesta por una arquitectura "Java-Native" robusta y portable:

*   **Lenguaje:** Java 21 (Aprovechamiento de *Virtual Threads* para concurrencia ligera en servicios como `DocumentStructureExtractor` o `ShellExecuteTool`).
*   **Orquestación IA:** `LangChain4j` (v0.35.0) como abstracción para interactuar con modelos (OpenAI/OpenRouter).
*   **Persistencia (Base de Datos):** `H2 Database` (Embebida). Se utiliza de forma híbrida:
    *   Relacional para metadatos (`turnos`, `checkpoints`).
    *   Vectorial (simulada) almacenando embeddings en columnas `BLOB` y realizando cálculos de distancia coseno en memoria/Java (a través de `EmbeddingFilterImpl`).
*   **Control de Versiones & Parcheado:**
    *   `io.github.jjdelcerro.javarcs`: Implementación **nativa en Java** de RCS (Revision Control System). Esto es crítico para permitir al agente versionar sus propios cambios sin depender de binarios `git` o `rcs` del sistema operativo.
    *   `java-diff-utils`: Para aplicar parches *Unified Diff* de forma programática.
*   **Procesamiento de Documentos:** `Apache Tika` (extracción de texto de binarios), `Natty` (procesamiento de lenguaje natural para fechas), `Gson` (JSON).
*   **Interfaz de Usuario:** Soporte dual para GUI (`Swing` con `FlatLaf` y `RSyntaxTextArea`) y TUI (`JLine 3`).
*   **Gestión de Dependencias:** Maven (con `maven-shade-plugin` para generar el Fat JAR).

## 3. Estructura y Arquitectura

La arquitectura sigue un patrón modular basado en **Interfaces** y **Service Locator**, evitando la inyección de dependencias pesada (como Spring) para mantener el inicio rápido y la ligereza.

### Estructura de Paquetes
*   **`lib` (Core):** Define los contratos (`Agent`, `AgentService`, `SourceOfTruth`, `AgentTool`). Es el núcleo agnóstico.
*   **`lib.impl` (Implementación):**
    *   `persistence`: Lógica de H2, mapeo de `Turn` y `CheckPoint`.
    *   `services`: División funcional por dominios (`conversation`, `memory`, `documents`, `scheduler`, `email`, `telegram`). Cada servicio tiene su propia factoría.
*   **`ui`:** Capa de abstracción para la interfaz (Consola vs Swing), permitiendo cambiar de UI sin tocar la lógica del agente.
*   **`main`:** Punto de entrada y utilidades de inicialización (`AgentUtils`).

### Diseño Arquitectónico
1.  **Service Locator (`AgentLocator`):** Centraliza el acceso a los servicios singleton.
2.  **Hexagonal / Puertos y Adaptadores:** El núcleo del agente (`AgentImpl`) orquesta servicios que encapsulan la lógica de negocio. La persistencia (`SourceOfTruth`) es una interfaz clara que desacopla el almacenamiento del razonamiento.
3.  **Event Loop Simulado:** Aunque los LLM son síncronos, el sistema implementa una cola de eventos concurrente (`pendingEvents` en `ConversationService`) para inyectar estímulos asíncronos en el flujo de pensamiento.

## 4. Herramientas del Agente (Toolset)

El agente dispone de un arsenal exhaustivo (+30 herramientas) clasificadas por dominio. Es destacable que **todas** se ejecutan localmente en la JVM.

### Sistema de Archivos y Código (Java Nativo)
1.  `file_read`: Lectura de texto con paginación inteligente y "hints" para continuar leyendo.
2.  `file_write`: Escritura atómica de archivos (con backup automático RCS).
3.  `file_mkdir`: Creación de directorios recursiva.
4.  `file_find`: Búsqueda por patrón GLOB.
5.  `file_grep`: Búsqueda de contenido por Regex.
6.  `file_read_selectors`: Lectura masiva de múltiples archivos o patrones.
7.  `file_extract_text`: Extracción de texto de binarios (PDF, DOCX) vía Tika.
8.  `file_patch`: Aplicación de parches *Unified Diff*.
9.  `file_search_and_replace`: Reemplazo de cadenas literales (más seguro que diffs para cambios pequeños).
10. `file_history`: Consulta de historial de versiones (vía `javarcs`).
11. `file_recovery`: Reversión de archivos a versiones anteriores (vía `javarcs`).

### Conectividad y Web
12. `web_search`: Búsqueda en internet (API Brave).
13. `web_get_content`: Descarga y limpieza de HTML (Tika) con soporte de user-agent.
14. `get_weather`: Consulta climática (Open-Meteo).
15. `get_current_location`: Geolocalización por IP.
16. `get_current_time`: Fecha, hora y zona horaria actual.

### Memoria y Sistema
17. `lookup_turn`: Recuperación precisa de un evento pasado por ID (`{cite:ID}`).
18. `search_full_history`: Búsqueda semántica en toda la base de datos de turnos.
19. `pool_event`: Herramienta "virtual" para consumir la cola de eventos asíncronos.
20. `shell_execute`: Ejecución de comandos Bash (con soporte opcional de sandbox **Firejail**).
21. `shell_read_output`: Lectura paginada de salidas de comandos largos guardados en `/tmp`.

### Documentación (DocMapper)
22. `document_index`: Iniciar el proceso de ingestión de un documento.
23. `document_search`: Búsqueda híbrida (categoría + semántica).
24. `document_search_by_categories`: Filtrado por etiquetas.
25. `document_search_by_sumaries`: Búsqueda vectorial pura en resúmenes.
26. `get_document_structure`: Obtener el índice (TOC) del documento.
27. `get_partial_document`: Leer contenido real de secciones específicas.

### Comunicación y Agenda
28. `telegram_send`: Enviar mensajes proactivos al usuario.
29. `email_list_inbox`: Listar cabeceras de correo.
30. `email_read`: Leer cuerpo de correo (saneado con Tika).
31. `email_send`: Enviar correos SMTP.
32. `schedule_alarm`: Programar recordatorios en lenguaje natural (Natty).

## 5. Descripción Detallada de Mecanismos

### A. Gestión de Memoria (Híbrida y Narrativa)
El sistema rechaza el enfoque de "ventana deslizante" simple en favor de una **memoria estructurada**:

1.  **Session (Memoria de Trabajo):** Mantiene los mensajes `ChatMessage` en RAM para el contexto inmediato. Se sincroniza con el disco (`active_session.json`) para sobrevivir a reinicios. Implementa un sistema de "Backfill" para asociar mensajes en memoria con IDs persistentes una vez guardados en BD.
2.  **Episódica (Turnos - H2):** Cada interacción se guarda como un `Turn` inmutable.
    *   **Vectorización:** Se calcula un embedding del contenido combinado (User + Thinking + Model + Tool).
    *   **Optimización:** Si la salida de una herramienta supera los 2KB, se trunca en la BD (guardando un metadato JSON) para no inflar el almacenamiento, aunque se mantiene íntegra en la RAM de la sesión actual.
3.  **Semántica (CheckPoints - "El Viaje"):**
    *   Al superar un umbral de turnos (~40), el `MemoryService` invoca un LLM dedicado.
    *   **Protocolo de Compactación:** El LLM recibe el resumen anterior y los nuevos turnos en CSV. Debe generar una narrativa continua ("El Viaje") que integre lo nuevo con lo viejo.
    *   **Citas Explícitas:** El sistema obliga al LLM a mantener referencias `{cite:ID}`. Esto permite que el agente use posteriormente `lookup_turn` para "rehidratar" un recuerdo específico con precisión de bit, mitigando la alucinación.

### B. Gestión de Eventos (Proactividad)
Permite al agente reaccionar al mundo exterior (Emails, Telegram, Alarmas) rompiendo el ciclo síncrono Request-Response.

1.  **Sensores:** Hilos independientes (`EmailService`, `TelegramService`) o Timers (`SchedulerService`) detectan cambios.
2.  **Inyección (Spoofing):**
    *   Se crea un objeto `Event` y se encola.
    *   El `ConversationService` detecta el evento y lo inyecta en el historial **simulando** que el modelo ejecutó la herramienta `pool_event` y que el sistema devolvió el evento como resultado.
    *   Esto engaña "benignamente" al LLM, haciéndole creer que él solicitó la información, manteniendo la coherencia del diálogo.

### C. Percepción Temporal
El agente no vive en un "ahora" estático.

1.  **System Prompt Dinámico:** `{NOW}` se inyecta en cada interacción.
2.  **Marcas de Silencio (Time Gaps):**
    *   En `Session.getContextMessages`, se calcula la diferencia temporal entre el último mensaje y el actual.
    *   Si delta > 1 hora, se inyecta un mensaje de sistema artificial: *"Ha pasado [tiempo] desde la última interacción..."*.
    *   Esto da al agente contexto sobre si la conversación es una continuación inmediata o si ha pasado un fin de semana.

### D. Document Mapper (RAG Estructural)
Una implementación sofisticada de RAG que evita el "chunking" ciego.

1.  **Fase 1 (Estructura):** Un modelo de razonamiento lee el documento crudo (con números de línea) para generar un Árbol de Contenidos (JSON) identificando secciones lógicas (Títulos, Capítulos).
2.  **Fase 2 (Síntesis):** Itera sobre las secciones. Un modelo más rápido genera un resumen y categorías para cada sección.
3.  **Persistencia:** La estructura se guarda en un fichero `.struct` (JSON) y los metadatos/vectores en H2.
4.  **Recuperación:** Permite al agente navegar el índice (`get_document_structure`) y solicitar selectivamente el texto completo de secciones relevantes (`get_partial_document`), optimizando el uso de tokens.

### E. Seguridad y Sandbox
El sistema implementa una seguridad multicapa:

1.  **Sandbox de Archivos (`AgentAccessControlImpl`):**
    *   Todas las rutas se resuelven contra un `rootPath`.
    *   Se bloquean intentos de *Path Traversal* (`../`).
    *   Se prohíbe explícitamente la escritura en carpetas sensibles (ej: `.git`, archivos de backup `.jv`).
2.  **Confirmación Humana:**
    *   Las herramientas marcadas con `MODE_WRITE` o `MODE_EXECUTION` pausan el hilo del agente y solicitan confirmación `[Y/n]` en la consola del usuario.
3.  **Control de Versiones Automático (CI):**
    *   Antes de cualquier escritura (`file_write`, `file_patch`), el sistema usa `javarcs` para hacer un `ci -l` (check-in and lock) del archivo. Esto garantiza que siempre hay un camino de retorno ante errores del LLM.
4.  **Aislamiento de Procesos:**
    *   La herramienta `shell_execute` detecta si `firejail` está instalado. Si lo está, envuelve automáticamente los comandos en una sandbox estricta, protegiendo el sistema anfitrión.

### F. Flujo del Conversation Manager
El ciclo `executeReasoningLoop` implementa un bucle **ReAct**:

1.  Construye contexto (System Prompt + Narrativa CheckPoint + Historial reciente + Marca temporal).
2.  Invoca al LLM (`CONVERSATION` model).
3.  **Bucle de Herramientas:**
    *   Si el LLM pide ejecutar herramientas:
        *   Verifica permisos (Usuario confirma si es necesario).
        *   Ejecuta la herramienta (Java nativo).
        *   Inyecta el resultado como `ToolExecutionResultMessage`.
        *   Guarda el turno intermedio (Tool Call + Tool Result).
        *   Repite el paso 2 con el nuevo estado.
4.  Si es respuesta de texto final: Guarda el turno y se lo muestra al usuario.
5.  **Comprobación de Compactación:** Verifica si se ha superado el umbral de turnos para disparar la consolidación de memoria en segundo plano.

## 6. Construcción y Despliegue

*   **Build System:** Maven estándar.
*   **Empaquetado:** Uso crítico de `maven-shade-plugin` con `ServicesResourceTransformer`. Esto es vital para fusionar los archivos SPI (`META-INF/services`) de las librerías, permitiendo que LangChain4j y Tika descubran sus plugins en un Fat JAR único.
*   **Configuración:** Al primer arranque, genera automáticamente una estructura de carpetas `./data` con ficheros `.properties` editables para configurar APIs, modelos y credenciales.

## 7. Conclusión

El proyecto `chatagent` es una pieza de ingeniería de software notablemente madura para ser un proyecto personal. Cumple estrictamente con los requisitos de **independencia** (todo en un JAR, DB embebida, RCS nativo) y **persistencia**.

Puntos fuertes destacados:
1.  **Arquitectura de Memoria Robusta:** La distinción entre memoria episódica inmutable y memoria semántica narrativa ("El Viaje") soluciona eficazmente el problema del contexto infinito.
2.  **Seguridad por Diseño:** La integración de RCS nativo para backup automático antes de escritura es una característica brillante para un agente de desarrollo/investigación, proporcionando una red de seguridad contra código destructivo.
3.  **Enfoque "Java-Native":** La reimplementación de herramientas de sistema (diff, rcs) en Java garantiza que el agente sea verdaderamente portable entre sistemas operativos sin dependencias externas.
4.  **Proactividad:** El sistema de inyección de eventos permite que el agente "sienta" el entorno sin romper la ilusión de una sesión de chat secuencial.

Es una base sólida para un asistente personal de investigación capaz de mantener el contexto durante meses o años.
