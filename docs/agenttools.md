## AgentTools y sistema de paginación

### 1. Introducción: el agente necesita músculos

Un agente conversacional que solo habla es de utilidad limitada. Para ser realmente autónomo, Noema debe poder **actuar sobre el mundo**: leer y escribir archivos, ejecutar comandos, consultar APIs externas, enviar correos o programar alarmas. Esta capacidad de acción se materializa a través de las **herramientas** (`AgentTool`). Son los “músculos” que el LLM puede activar bajo demanda, extendiendo su alcance más allá del procesamiento de texto.

El sistema de herramientas de Noema no es una colección dispersa de funciones. Responde a un diseño unificado que incluye:

- Un **contrato común** (`AgentTool`) que toda herramienta debe cumplir.
- Un **registro central** gestionado por `ReasoningService`, que expone al modelo solo las herramientas activas y permitidas.
- Un **mecanismo de seguridad** (modos `READ`, `WRITE`, `EXECUTION`, `WEB`) que, combinado con `AgentAccessControl`, permite o deniega operaciones.
- Un **sistema de paginación universal** (`AbstractPaginatedAgentTool`) para manejar salidas masivas (archivos de miles de líneas, resultados de comandos extensos) sin saturar la ventana de contexto del LLM.

Este documento describe la arquitectura de las herramientas, cómo se declaran, registran y ejecutan, y profundiza en el subsistema de paginación, una de las piezas más ingeniosas de Noema para sortear las limitaciones de tokens de los modelos actuales.

### 2. El contrato `AgentTool`: nombre, especificación, modos y tipos

Toda herramienta implementa la interfaz `AgentTool`. Los métodos principales son:

- **`ToolSpecificationBuilder getSpecification()`**: define los metadatos que el LLM necesita para invocar la herramienta: nombre, descripción y esquema JSON de los parámetros (usando `ToolSpecificationBuilder`). Este builder permite añadir parámetros de tipo string, entero, número o array de strings de forma declarativa.

- **`String execute(String jsonArguments)`**: contiene la lógica de negocio. Recibe los argumentos en formato JSON (que deben parsearse, normalmente con Gson) y devuelve un resultado en texto (normalmente otro JSON o un mensaje legible).

- **`int getMode()`**: clasifica la herramienta según su peligrosidad:
  - `MODE_READ`: solo consulta información (ej: leer un archivo, consultar el tiempo). No requiere confirmación humana.
  - `MODE_WRITE`: modifica el sistema de archivos (escribir, parchear, crear directorios).
  - `MODE_EXECUTION`: ejecuta comandos en el shell.
  - `MODE_WEB`: realiza peticiones a internet (búsquedas, descargas).
  Los modos `WRITE`, `EXECUTION` y `WEB` (según configuración) activan la confirmación humana.

- **`int getType()`**: distingue entre herramientas operativas (`TYPE_OPERATIONAL`) y herramientas de memoria (`TYPE_MEMORY`). La distinción afecta al tipo de turno registrado en la base de datos (`tool_execution` vs `lookup_turn`), lo que influye en la posterior compactación narrativa.

- **`boolean isAvailableByDefault()`**: indica si la herramienta debe aparecer activada la primera vez que se inicia el agente. El usuario puede luego activarla o desactivarla desde la configuración.

Cada herramienta recibe una referencia al `Agent` central en su constructor, lo que le permite acceder a servicios (`getService()`), al control de acceso, a las rutas del sandbox, a la consola, etc.

### 3. Registro y gestión de herramientas (ReasoningService, activación)

`ReasoningService` es el propietario del catálogo de herramientas. Durante su arranque, recorre todos los servicios registrados (`AgentService.getTools()`) y añade cada herramienta mediante `addTool()`. Internamente mantiene un mapa `availableTools` que asocia el nombre técnico de la herramienta con un objeto `AvailableAgentTool` (que contiene la herramienta y un flag `active`).

La activación de cada herramienta sigue estas reglas:
1. Por defecto, se usa `isAvailableByDefault()` de la propia herramienta.
2. Posteriormente, `refresh_available_tools()` lee la configuración de `reasoning/active_tools` (una `AgentSettingsCheckedList`) y actualiza el flag `active` para las herramientas que aparecen en la lista. Las que no aparecen mantienen su valor por defecto.

Cuando el LLM solicita ejecutar una herramienta, `ReasoningService` solo considera aquellas que están activas y que `AgentAccessControl.isToolAllowed()` permite (según las políticas globales de escritura, ejecución o acceso a internet). Esta doble validación garantiza que el modelo no pueda utilizar una capacidad que el usuario ha desactivado.

### 4. Ejecución de herramientas: seguridad y flujo

La ejecución de una herramienta ocurre dentro del bucle `eventDispatcher` de `ReasoningService`, de forma síncrona y bloqueante. El flujo es:

1. El modelo devuelve un `AiMessage` con `toolExecutionRequests`.
2. Por cada solicitud, se busca la herramienta en `availableTools`.
3. Si la herramienta no está activa, se devuelve un mensaje de error.
4. Si el modo es `WRITE`, `EXECUTION` o `WEB` (y la política global lo requiere), se pide confirmación al usuario mediante `AgentConsole.confirm()`. El mensaje incluye el nombre de la herramienta y los argumentos. Si el usuario deniega, la ejecución se aborta y se notifica al modelo.
5. Se invoca `tool.execute(jsonArguments)`. La ejecución puede tardar segundos o minutos (por ejemplo, un comando shell o una descarga pesada). Durante ese tiempo, el hilo del `eventDispatcher` permanece bloqueado.
6. El resultado (texto) se envuelve en un `ToolExecutionResultMessage` y se añade a la sesión. Además, se persiste un turno de tipo `tool_execution` (o `lookup_turn` si la herramienta es de memoria).
7. El modelo, en la siguiente iteración del bucle, recibirá ese resultado y podrá decidir si continúa con más herramientas o si da una respuesta final al usuario.

Este diseño es intencionadamente simple: no hay paralelismo ni reintentos automáticos. La transparencia y el control humano son prioritarios frente al rendimiento.

### 5. Catálogo de herramientas por dominio

Noema dispone en la actualidad de más de veinte herramientas, agrupadas lógicamente por su función. A continuación se enumeran con una breve descripción de cada una.

**Herramientas de memoria y sistema**
- `fetch_citation` (`LookupTurnTool`): recupera un turno específico a partir de su ID (ej: `{cite:123}`). Permite obtener el contexto exacto de una conversación pasada.
- `search_full_history`: realiza una búsqueda semántica en todo el historial mediante embeddings. Devuelve los turnos más relevantes para una consulta.
- `annotate_observation`: permite al agente guardar una nota o resumen que se incluirá en el próximo checkpoint. Útil para fijar hechos importantes.
- `pool_event`: herramienta "ficticia" usada internamente por el `SensorsService` para inyectar eventos asíncronos en el historial.
- `sensor_status`: consulta el estado de los sensores (activos, silenciados, estadísticas).
- `sensor_stop` / `sensor_start`: permite al agente silenciar o reactivar canales sensoriales (ej: silenciar Telegram durante una tarea concentrada).
- `schedule_alarm`: programa una alarma en el futuro usando lenguaje natural (inglés). Cuando se dispara, inyecta un evento sensorial.
- `list_skills`: lista los índices de habilidades procedimentales disponibles (archivos `.ref.md` en `var/skills`).
- `load_skill`: carga el manual completo de una habilidad (archivo `.md`) para ejecutar un protocolo paso a paso.
- `consult_environ`: recupera un módulo de conocimiento denso del entorno (biografía, proyectos) desde `var/identity/environ`.

**Herramientas de archivo (lectura y escritura)**
- `file_find`: busca archivos y directorios usando patrones glob. Los resultados se devuelven paginados.
- `file_grep`: realiza una búsqueda de texto (case‑insensitive) en archivos o directorios, devolviendo las líneas coincidentes (paginado).
- `file_read`: lee el contenido de un archivo de texto. Si es binario, avisa y recomienda `file_extract_text`. Soporta paginación.
- `file_write`: escribe o sobrescribe un archivo. Crea los directorios padres si no existen. Antes de modificar, si el archivo ya existe, hace un commit automático al sistema RCS.
- `file_mkdir`: crea directorios (comportamiento `mkdir -p`).
- `file_patch`: aplica un parche en formato unified diff (`@@ ... @@`). Útil para refactorizaciones complejas.
- `file_search_and_replace`: reemplaza un bloque de texto exacto por otro. Más simple y seguro que `file_patch` para cambios pequeños.
- `file_extract_text`: extrae el texto de archivos binarios (PDF, DOCX, etc.) usando Apache Tika. El resultado se cachea en `var/cache` y se sirve paginado.
- `file_history`: muestra el historial de revisiones RCS de un archivo (similar a `rlog`).
- `file_recovery`: restaura una versión anterior de un archivo desde el historial RCS (similar a `co -r`).

**Herramientas de ejecución**
- `shell_execute`: ejecuta un comando en Bash. Captura la salida estándar y de error, la almacena en un archivo temporal y la sirve paginada. Incluye confirmación humana obligatoria y soporte opcional para `firejail`.

**Herramientas web y utilidades**
- `web_search`: busca en internet mediante Tavily (requiere API key). También existe un adaptador para Brave, aunque menos usado.
- `web_get_content`: descarga una URL y extrae el texto limpio usando Apache Tika. Soporta paginación.
- `get_current_location`: obtiene la geolocalización aproximada basada en la IP pública (usando ip-api.com).
- `get_current_time`: devuelve la fecha, hora y zona horaria actual del sistema.
- `get_weather`: consulta el clima actual usando Open-Meteo (sin API key). Puede geocodificar una ciudad o usar coordenadas.

**Herramientas de comunicación**
- `email_list_inbox`: lista las cabeceras de los últimos correos (UID, remitente, asunto). Es una operación ligera para no saturar el contexto.
- `email_read`: lee el cuerpo completo de un correo a partir de su UID, limpiando HTML y extrayendo texto con Tika.
- `email_send`: envía un correo electrónico vía SMTP.
- `telegram_send`: envía un mensaje al usuario a través de Telegram (requiere chat ID autorizado).

**Herramientas de documentos (RAG estructural)**
- `document_index`: inicia el procesamiento de un documento (PDF, DOCX, etc.) para extraer su estructura, resúmenes y categorías. Es asíncrono y notifica al agente cuando termina.
- `document_search`: búsqueda híbrida (categorías + similitud semántica sobre resúmenes).
- `document_search_by_categories`: filtra documentos por categorías exactas.
- `document_search_by_sumaries`: busca en los resúmenes por significado.
- `get_document_structure`: devuelve el índice jerárquico del documento en formato XML, con secciones colapsadas.
- `get_partial_document`: inyecta el texto completo solo en las secciones solicitadas, permitiendo al agente leer partes concretas sin cargar todo el documento.

### 6. El problema de las salidas masivas

Cuando una herramienta ejecuta una operación que produce una cantidad ingente de texto (por ejemplo, leer un archivo de 50 000 líneas, ejecutar un comando que genera varios megabytes de log o extraer el texto de un PDF de 300 páginas), enviar toda esa salida directamente al LLM tiene dos problemas graves:

- **Saturación de contexto**: la ventana de tokens del modelo se llena rápidamente, dejando poco espacio para la conversación.
- **Coste económico**: si se usa un modelo de pago por token, transmitir textos masivos resulta prohibitivo.

La solución habitual (truncar y perder información) no es satisfactoria. Noema aborda el problema con un **sistema de paginación universal**: la herramienta escribe la salida completa en un archivo temporal o en caché, pero solo envía al LLM un **pequeño fragmento inicial** junto con una **instrucción (HINT)** para que el modelo pueda solicitar bloques sucesivos bajo demanda. De esta forma, el LLM decide cuánto leer y cuándo, manteniendo el control sobre su propio contexto.

### 7. Paginación universal: el patrón `AbstractPaginatedAgentTool`

La clase base `AbstractPaginatedAgentTool` encapsula toda la lógica de paginación. Las herramientas que pueden generar salidas grandes (como `file_read`, `shell_execute`, `web_get_content`, `file_extract_text`, `file_find` o `file_grep`) heredan de ella.

El patrón se compone de varios elementos:

- **Escritura completa en un recurso temporal**: la herramienta genera su salida y la almacena en un archivo (normalmente en `var/tmp` o `var/cache`). El nombre del archivo es único (por ejemplo, `out_<uuid>.out`).
- **Obtención de un identificador de recurso**: el método `getIdFromPath(Path)` transforma la ruta absoluta en una URI simbólica (`tmp://...`, `cache://...` o `user://...`). Este ID es seguro (no revela rutas absolutas) y permite que `read_paginated_resource` localice el archivo más tarde.
- **Servicio paginado**: `servePaginatedResource(resourceId, offset, limit)` lee las líneas del archivo desde `offset` con un máximo de `limit` líneas (por defecto 1000). Calcula el número total de líneas (con caché LRU para evitar leer el archivo dos veces) y construye una respuesta con cabecera y contenido.
- **Caché de recuento de líneas**: `lineCountCache` (un `LRUMap` de 30 entradas) almacena el número de líneas y la fecha de modificación del archivo para evitar recalcularlo en cada petición.

### 8. Protocolo de respuesta: cabecera, HINT y contenido

El formato de respuesta de cualquier herramienta paginada es **texto plano con una estructura rígida** que el LLM debe aprender a interpretar. La respuesta consta de dos partes separadas estrictamente por `---` en una línea propia:

```
STATUS: ok
EMPTY: false
LINE_RANGE: 0-999
TOTAL_LINES: 50000
HINT: To read the next block, call 'read_paginated_resource' with args: {"resource_id": "tmp://out_abc123.out", "offset": 1000, "limit": 1000}
---
(contenido de las primeras 1000 líneas)
```

Los campos de cabecera son:

- `STATUS`: `ok` o `error`.
- `EMPTY`: `true` si no hay contenido (archivo vacío).
- `LINE_RANGE`: líneas incluidas en este bloque (inicio-fin, 0‑indexed).
- `TOTAL_LINES`: total de líneas del recurso (solo si es mayor que el bloque).
- `HINT`: si hay más líneas por leer, aparece esta línea con la llamada exacta a `read_paginated_resource` que el modelo debe ejecutar para obtener el siguiente bloque. El parámetro `resource_id` es el identificador simbólico.
- Luego el separador `---` y el contenido textual.

Si el recurso se agota o el bloque es el último, no aparece `HINT`. En caso de error, la cabecera incluye `STATUS: error` y una descripción del problema.

Este protocolo es **autónomo**: el modelo no necesita recordar offsets ni inventar parámetros; el `HINT` contiene todo lo necesario para la siguiente llamada.

### 9. Identificadores de recurso: `tmp://`, `cache://`, `user://`

Para evitar exponer rutas absolutas del sistema de archivos al LLM (por seguridad y para simplificar la API), los recursos paginados se identifican mediante esquemas simbólicos:

- **`tmp://`**: apunta a archivos en `agent.getPaths().getTempFolder()` (normalmente `var/tmp`). Ejemplo: `tmp://out_abc123.out`.
- **`cache://`**: apunta a archivos en `agent.getPaths().getCacheFolder()` (normalmente `var/cache`). Usado para resultados cacheados de extracción de textos o documentos.
- **`user://`**: apunta a archivos dentro del workspace del usuario (permite lectura de archivos del proyecto, previa validación por `AgentAccessControl`). El path es absoluto pero normalizado, ej: `user:///home/usuario/proyecto/src/main.java`.

El método `getIdFromPath()` crea estos identificadores, y `getPathFromId()` realiza la conversión inversa, comprobando que la ruta no escape de las carpetas esperadas (protección contra path traversal). De este modo, el modelo nunca ve rutas completas del sistema, solo referencias simbólicas que el propio `AbstractPaginatedAgentTool` resuelve de forma segura.

### 10. La herramienta `read_paginated_resource`

`ReadPaginatedResourceTool` es la única herramienta que debe invocarse para leer bloques adicionales de un recurso paginado. Su especificación es intencionadamente simple:

- `resource_id` (obligatorio): el identificador simbólico obtenido del `HINT`.
- `offset` (opcional, por defecto 0): línea inicial (0‑indexed).
- `limit` (opcional, por defecto 1000): número máximo de líneas a leer.

Su implementación simplemente llama a `servePaginatedResource()` con los argumentos recibidos. No contiene lógica de negocio adicional. El LLM **no debe usar esta herramienta por iniciativa propia**; solo cuando recibe un `HINT` explícito. El prompt del sistema (en la descripción de la herramienta) incluye esta instrucción, reforzada por el protocolo de respuesta.

### 11. Trimado de resultados (`trimResult`) y conservación de contexto

Aunque la paginación resuelve el problema de las salidas muy grandes, incluso el primer bloque de 1000 líneas puede ser excesivo si se acumulan varios en la sesión. Para evitarlo, `AbstractPaginatedAgentTool` implementa `trimResult(String result, TrimResultType)`, que es invocado por `ReasoningService` cuando el contexto se acerca a su límite.

El algoritmo funciona así:

- Cada herramienta devuelve una respuesta con el formato cabecera+`---`+contenido.
- Cuando el `ReasoningService` detecta que la sesión acumula muchos mensajes (o que el contexto se está llenando), itera sobre los mensajes de tipo `ToolExecutionResultMessage` y llama a `trimResult()`.
- `trimResult()` examina la cabecera: si encuentra la marca `CONTENT_TRIMMED_IN_THE_FOLLOWING_TURNS`, añade `CONTENT_TRIMMED: true` a la cabecera y **elimina todo el contenido** (deja solo el separador). Si solo se debe notificar al modelo (para que sepa que se ha recortado), se añade la anotación y se conserva el contenido completo en ese turno; en turnos posteriores se elimina.
- El LLM recibe así un mensaje de tipo "el contenido de esta herramienta ha sido truncado para ahorrar contexto, pero ya fue procesado anteriormente". Esto evita que el modelo lo intente leer de nuevo.

Este mecanismo es agresivo: sacrifica la posibilidad de releer la salida antigua a cambio de mantener el contexto manejable. La alternativa (no recortar y forzar una compactación) también es posible, pero el `trimResult` ofrece una capa adicional de control fino.

### 12. Limitaciones y decisiones de diseño

El sistema de herramientas y paginación, aunque potente, tiene limitaciones que deben conocerse:

- **Herramientas bloqueantes**: la ejecución de una herramienta detiene todo el agente. Si una herramienta tarda mucho (ej: `shell_execute` con un comando que dura minutos), el agente no responde a nuevos eventos hasta que finaliza. Esto es aceptable para tareas largas si el usuario es consciente, pero no es adecuado para agentes que requieran alta interactividad.

- **Sin paralelismo**: no se pueden ejecutar varias herramientas a la vez ni interrumpir una herramienta a mitad de ejecución (salvo por el usuario, que puede abortar comandos mediante confirmación escalonada en `shell_execute`).

- **La paginación depende de que el modelo siga las instrucciones**: si el LLM ignora el `HINT` y no llama a `read_paginated_resource`, el agente se quedará sin la información posterior. En la práctica, los modelos actuales (especialmente GPT-4 y Claude) respetan bien estas convenciones si el prompt es claro.

- **Los recursos temporales pueden acumularse**: aunque `AbstractPaginatedAgentTool` implementa un LRU para las salidas de shell, y los IDs de recurso se basan en rutas dentro de `tmp` y `cache`, no hay una limpieza global sistemática. En sesiones muy largas, el directorio `var/tmp` puede llenarse de archivos huérfanos.

- **`trimResult` es irreversible**: una vez que el contenido se recorta, se pierde para siempre. El modelo no puede volver a pedir el bloque original porque el archivo temporal ya no se corresponde con el contexto. Esta decisión es deliberada para ahorrar memoria, pero podría sorprender al LLM si intenta re‑leer un resultado antiguo.

- **No hay mecanismo de "streaming" de herramientas**: la salida se genera por completo antes de enviar el primer bloque. Para comandos muy largos, el usuario no ve resultados parciales hasta que finaliza la ejecución y se envía el primer bloque paginado.

### 13. Conclusión

El sistema de herramientas de Noema es un ejemplo de **pragmatismo arquitectónico**: las herramientas se declaran de forma declarativa, se gestionan de manera centralizada y se ejecutan con un modelo de seguridad simple pero eficaz. El subsistema de paginación universal, basado en `AbstractPaginatedAgentTool`, permite manejar salidas masivas sin saturar la ventana de contexto, delegando en el LLM la decisión de cuánto leer. Esta combinación dota al agente de una agencia real (puede tocar el mundo) sin renunciar a la viabilidad técnica dentro de las limitaciones actuales de los modelos de lenguaje. Noema no solo conversa: actúa, y sus herramientas son los músculos que lo hacen posible.
