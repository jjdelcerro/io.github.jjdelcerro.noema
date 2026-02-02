
# Análisis del Proyecto: io.github.jjdelcerro.chatagent

## 1. Visión General
El proyecto es un **Agente Conversacional Autónomo** de alto rendimiento desarrollado en **Java 21**. Su propósito principal es implementar un sistema de **Memoria Híbrida Determinista**. A diferencia de los asistentes simples que pierden el hilo tras conversaciones largas, este agente utiliza una arquitectura que combina una narrativa evolutiva ("El Viaje") con una base de datos de eventos atómicos ("Turnos"), permitiendo una memoria a largo plazo auditable, privada y resistente a la degradación cognitiva.

## 2. Stack Tecnológico
El proyecto destaca por evitar frameworks pesados, optando por una arquitectura modular y eficiente:

*   **Lenguaje:** Java 21 (aprovechando Virtual Threads y mejoras en colecciones).
*   **Orquestación de IA:** **LangChain4j (0.35.0)** para la abstracción de modelos (OpenAI/OpenRouter) y el motor de ejecución de herramientas.
*   **Embeddings Locales:** **ONNX Runtime** con el modelo `all-minilm-l6-v2`. Esto garantiza que la vectorización de la memoria ocurra localmente por privacidad y velocidad.
*   **Persistencia:** 
    *   **H2 Database:** Base de datos SQL embebida para metadatos y almacenamiento de vectores (BLOBs).
    *   **File System:** Almacenamiento de CheckPoints en Markdown y logs en CSV.
*   **Procesamiento de Documentos:** **Apache Tika** para extraer texto limpio de PDFs, HTMLs y archivos complejos.
*   **Interfaz de Usuario:** **JLine 3** para una CLI interactiva rica con soporte multilínea.
*   **Comunicación:** APIs de **Telegram** y **Jakarta Mail**.


## 3. Estructura de Paquetes y Diseño
El proyecto sigue un patrón de separación clara entre contrato e implementación:

*   **`lib` (API/Interfaces):** Define el núcleo del sistema (`Agent`, `AgentSettings`, `PathAccessControl`).
*   **`lib.persistence`:** Define las entidades de memoria (`Turn`, `CheckPoint`, `SourceOfTruth`).
*   **`lib.impl`:** Contiene la lógica de negocio. Es destacable el uso de inyección de dependencias manual y el patrón **ReAct (Reason + Act)**.
*   **`lib.impl.tools`:** Organizado por dominios (file, mail, memory, telegram, web).
*   **`ui.console`:** Implementación específica de la interfaz para terminales.


## 4. Arquitectura y Mecanismos Principales

### A. Gestión de Memoria (El Núcleo Determinista)
Es el componente más innovador del proyecto. Se divide en:

1.  **Turnos (Turns):** Unidades mínimas de información. Se guardan en la DB H2 con su vector de embedding. Si el resultado de una herramienta es muy grande, el sistema aplica una "política de almacenamiento" que guarda metadatos en la DB pero mantiene el contenido completo en el historial de sesión.
2.  **Sesión Activa (`Session.java`):** Gestiona los mensajes en memoria RAM y los persiste en un JSON (`active_session.json`). Mantiene la coherencia entre los mensajes del protocolo de chat y los IDs de los turnos en la base de datos.
3.  **Compactación (Compaction):** Cuando la sesión supera los 20 turnos, el `MemoryManager` entra en acción. Utiliza un modelo de lenguaje con un prompt específico (`prompt-compact-memorymanager.md`) para fusionar el historial actual con el anterior, creando un **CheckPoint**.
4.  **Recuperación Híbrida:** 
    *   **Búsqueda Semántica:** `SearchFullHistoryTool` realiza una búsqueda vectorial manual (similitud del coseno) sobre los BLOBs de H2.
    *   **Recuperación Determinista:** `LookupTurnTool` permite al agente "invocar" un recuerdo exacto mediante su ID (`{cite:ID}`).

### B. Gestión de Eventos (Sensores y Percepción)
El agente no es solo reactivo. Implementa un sistema de **estímulos externos asíncronos**:
*   Los servicios de **Telegram** y **Email** actúan como sensores.
*   A través de `putEvent`, inyectan información en una cola concurrente.
*   El `ConversationManager` simula una respuesta de herramienta (`pool_event`) para "engañar" al LLM, permitiéndole procesar el evento externo dentro de su flujo lógico sin romper el protocolo del historial de chat.

### C. Seguridad (Sandbox)
La clase `PathAccessControlImpl` actúa como un cortafuegos para el sistema de archivos:
*   **Jailbreak Prevention:** Resuelve las rutas y verifica que siempre estén bajo el directorio raíz del proyecto o rutas permitidas explícitamente.
*   **Modos de Acceso:** Diferencia entre lectura y escritura, bloqueando escrituras en directorios críticos como `.git`.


## 5. Herramientas del Agente (Tools)
El agente posee un set de capacidades muy orientado al desarrollo de software y la gestión de información:

*   **File System:** `FilePatchTool` (usa DiffUtils para aplicar parches de código), `FileSearchAndReplaceTool`, `FileGrepTool`, y `FileExtractTextTool` (vía Tika).
*   **Web:** `WebSearchTool` (Brave Search) y `WebGetTikaTool` (para leer contenido web limpio).
*   **Memoria:** Herramientas específicas para navegar por su propia historia.
*   **Comunicación:** Envío y lectura de correos y mensajes de Telegram.


## 6. Construcción y Despliegue
*   **Maven:** Gestiona las dependencias.
*   **Shade Plugin:** Genera un **Fat JAR** (Uber-JAR).
*   **Detalle Crítico:** Se utiliza `ServicesResourceTransformer` en el pom.xml. Esto es vital para que las librerías que usan SPI (como LangChain4j o Tika) no pierdan sus definiciones al combinar los JARs.


## 7. Conclusión
Este proyecto representa una pieza de ingeniería de software robusta y bien pensada. No se limita a ser una interfaz de chat; es una implementación práctica de **RAG Narrativo**. 

**Puntos Fuertes:**

*   **Determinismo:** Obliga al modelo a citar fuentes, reduciendo alucinaciones.
*   **Privacidad:** Embeddings y base de datos locales.
*   **Extensibilidad:** Es muy fácil añadir nuevas herramientas siguiendo la interfaz `AgenteTool`.
*   **Resiliencia:** El sistema de compactación asegura que el agente pueda trabajar en proyectos de larga duración sin olvidar decisiones críticas tomadas al inicio.

