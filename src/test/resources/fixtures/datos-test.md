
## Servicio de Memoria (`MemoryService`)

### 1. Introducción: el problema de la ventana de contexto

Los modelos de lenguaje actuales, por muy grandes que sean sus ventanas
de contexto (128k, 1M tokens o más), tienen un límite inherente: no
pueden retener una conversación de forma indefinida. Tarde o temprano,
los turnos más antiguos quedan fuera del alcance del modelo, y el agente
sufre una forma de "amnesia". La solución ingenua —descartar lo
antiguo— destruye información valiosa. La solución compleja —almacenar
todo en una base de datos vectorial y recuperar fragmentos bajo
demanda— es viable, pero añade latencia y no preserva la continuidad
narrativa.

Noema aborda este problema con un enfoque diferente: **la compactación
narrativa**. En lugar de buscar fragmentos, **resume** la historia
pasada en un texto denso pero legible, el **Punto de Guardado
(CheckPoint)**. Este resumen se inyecta en el prompt del sistema junto
con los turnos recientes, proporcionando al modelo una visión global de
la conversación sin ocupar todo el espacio de contexto. La clave está en
que el resumen no es un simple extracto; incluye referencias explícitas
(`{cite:ID}`) a los turnos originales, permitiendo al agente recuperar el
detalle exacto cuando sea necesario.

`MemoryService` es el componente responsable de esta transformación. Su
misión es tomar un bloque de turnos (decenas o cientos) y generar un
nuevo Punto de Guardado que consolide la información de forma fiel,
trazable y narrativamente coherente. No se limita a comprimir datos;
interpreta el diálogo, identifica sus núcleos temáticos y redacta una
crónica que captura tanto los hechos como la evolución del pensamiento.

### 2. Arquitectura general: componentes y flujo

`MemoryService` es un servicio más dentro del ecosistema del agente,
registrado con el nombre `"Memory"`. Sus componentes principales son:

- **`MemoryServiceImpl`**: la implementación concreta. Gestiona la
  lógica de compactación, la carga de prompts y la interacción con el LLM
  específico para memoria.
- **`SourceOfTruth`**: proporciona los turnos a consolidar (mediante
  `getTurnsByIds()`) y persiste los nuevos puntos de guardado
  (`add(CheckPoint)`).
- **`Agent.ChatModel`**: un modelo de lenguaje independiente (puede ser
  el mismo o distinto al de razonamiento), configurable mediante claves
  específicas en `settings.json`.
- **Prompts**: archivos Markdown (`memory-compact.md`) que definen el
  protocolo de compactación: estilo narrativo, manejo de citas,
  interpretación de herramientas, etc.
- **`CheckPoint`**: el objeto resultante, con metadatos en base de datos
  y contenido textual en disco.

El flujo se inicia en `ReasoningService`. Cuando la sesión acumula
suficientes turnos (por defecto 40), o cuando el usuario fuerza la
compactación manual, se invoca `performCompaction()`. Este método:

1. Obtiene las marcas de inicio y fin del bloque a compactar (métodos de
   `Session`).
2. Recupera los turnos correspondientes mediante
   `sourceOfTruth.getTurnsByIds(first, last)`.
3. Llama a `MemoryService.compact(previousCheckPoint, turns)`.
4. El servicio genera un nuevo `CheckPoint` y lo devuelve.
5. Se persiste el nuevo checkpoint, se eliminan los mensajes compactados
   de la sesión y se actualiza el puntero `activeCheckPoint`.

La separación de responsabilidades es clara: `ReasoningService` decide
*cuándo* compactar; `MemoryService` sabe *cómo* hacerlo.

### 3. El contrato de `MemoryService`: el método `compact()`

La interfaz `MemoryService` expone un único método público relevante
para la compactación:

```java
CheckPoint compact(CheckPoint previous, List<Turn> newTurns);
```

- **`previous`**: el punto de guardado más reciente (puede ser `null`
  si es la primera compactación). Su texto contiene el resumen acumulado
  hasta ese momento.
- **`newTurns`**: lista de turnos nuevos (no consolidados) que se deben
  integrar. Los turnos vienen ordenados cronológicamente por `id`.
- **Devuelve**: un nuevo `CheckPoint` transitorio con ID `-1` (aún no
  persistido). Contiene el texto generado (dos secciones: "Resumen" y "El
  Viaje") y los rangos de turnos que abarca (`turnFirst`, `turnLast`).

La implementación en `MemoryServiceImpl` realiza los siguientes pasos:
- Valida que `newTurns` no esté vacío.
- Construye el conjunto de IDs de turno "válidos" (los del checkpoint
  anterior más los de `newTurns`) para posterior validación de citas.
- Construye el `userPrompt` concatenando el checkpoint anterior (si
  existe) y el CSV de nuevos turnos.
- Invoca al modelo LLM con el `systemPrompt` (definido en
  `memory-compact.md`) y el `userPrompt`.
- Extrae del texto generado todas las referencias `{cite:ID}` y las
  valida contra el conjunto de IDs válidos. Las inválidas se convierten
  en `{badcite:ID}`.
- Calcula los rangos: `firstId` es `previous.getTurnFirst()` si existe,
  o `newTurns.getFirst().getId()`; `lastId` es
  `newTurns.getLast().getId()`.
- Crea un nuevo `CheckPoint` (con ID `-1` y el texto generado) y lo
  retorna.

Nótese que el checkpoint devuelto aún no está persistido; será
`SourceOfTruth` quien lo añada a la base de datos y guarde el archivo
de texto en disco.

### 4. El protocolo de generación de puntos de guardado (prompt)

El prompt del sistema para `MemoryService` reside en
`var/config/prompts/memory-compact.md` y es uno de los documentos más
extensos y detallados de Noema. Define el **Protocolo de Generación de
Puntos de Guardado**. Sus secciones principales son:

- **Objetivos y datos de entrada**: especifica que el `MemoryManager`
  recibe un CSV de turnos (con columnas como `code`, `timestamp`,
  `contenttype`, `text_user`, `text_model`, `tool_call`, `tool_result`) y
  opcionalmente un punto de guardado anterior.

- **Principios Fundamentales**:
  - *Coherencia Narrativa*: el nuevo punto debe leerse como continuación
    natural del anterior.
  - *Trazabilidad Determinista*: cada hecho significativo debe llevar una
    cita `{cite:ID}` al turno original.
  - *Fidelidad de Referencia*: todas las citas deben pertenecer al
    conjunto de IDs de entrada. No se pueden inventar.
  - *Espiral de Contexto*: la memoria no es una línea recta, sino una
    espiral donde cada nueva conversación reinterpreta el pasado.

- **Directiva de Estilo de Citación**: las citas deben ir **integradas
  en la narrativa**, no al final como una lista. Ejemplo: "El usuario
  explicó que el sistema aprendía del texto {cite:6}".

- **Interpretación de eventos técnicos**:
  - Herramientas operativas (`tool_execution`,
    `tool_execution_summarized`): se debe narrar la acción y su
    resultado, no transcribir el JSON.
  - Herramientas de memoria (`lookup_turn`): representan un "flashback".
    Hay que describir el acto de recordar y rehidratar la información
    recuperada.
  
- **Modos de funcionamiento**:
  - *Modo 1 (Creación)*: solo se dispone de la nueva conversación. Se
    genera el primer punto de guardado desde cero.
  - *Modo 2 (Actualización)*: se dispone del punto anterior y de la nueva
    conversación. Se deben fusionar ambos en una narrativa única.
  
- **Detalle del Resumen y El Viaje**:
  - *Resumen*: ejecutivo, factual, decisiones clave, estado de proyectos.
  - *El Viaje*: narrativo, cronológico, captura el proceso de
    razonamiento y la evolución de las ideas.
  
- **Verificación de calidad**: el MemoryManager debe auto-evaluarse contra
  sesgos como el "sesgo de novedad" (dar más peso a la conversación nueva)
  y asegurar un balance conceptual.

Este prompt es el resultado de una evolución pragmática; contiene
instrucciones muy detalladas porque se ha observado que los LLMs tienden
a ser demasiado concisos o a perder la trazabilidad. El prompt actual
intenta guiarlos hacia un estilo narrativo denso pero fiel.

> **Nota sobre la "pérdida de nitidez":**  
> Es frecuente que quienes examinan el sistema por primera vez crean que
> los puntos de guardado pierden información valiosa con el tiempo
> (ejemplos concretos, matices lingüísticos, citas literales). En
> realidad, la compactación **sacrifica deliberadamente la literalidad
> para ganar densidad semántica**, pero preserva la trazabilidad mediante
> citas `{cite:ID}`. Cada afirmación significativa del resumen lleva
> asociada una o varias citas que permiten al agente recuperar el turno
> original completo usando la herramienta `fetch_citation`. Esta
> estrategia se complementa con la **Directiva anti-alucinación** del
> `ReasoningService`, que prohíbe al modelo inventar detalles si existe
> una cita y le obliga a consultar la fuente original cuando necesita
> precisión. Así, el agente opera con resúmenes densos y, solo cuando es
> necesario, profundiza bajo demanda sin saturar el contexto.

### 5. Construcción del prompt de usuario: el CSV de turnos

El método `buildUserPrompt()` genera el mensaje que se envía al LLM junto
con el prompt del sistema. Su estructura es:

1. **Modo de operación**: "MODO DE OPERACIÓN: 2 (Actualización)" si hay
   punto anterior; "1 (Creación Inicial)" si no.
2. **Punto de guardado anterior** (si existe): se incluye el texto
   completo del checkpoint previo, delimitado por `=== DOCUMENTO DE
   PUNTO DE GUARDADO ANTERIOR ===`.
3. **Nuevos turnos en CSV**: una cabecera con las columnas (`code,`
   `timestamp,contenttype,text_user,text_model_thinking,text_model,`
   `tool_call,tool_result`) seguida de una línea por cada turno,
   generada por `turn.toCSVLine()`.

El formato CSV es simple: las comillas dobles se escapan duplicándolas
(`"` -> `""`). El LLM debe leer este CSV y entender que la columna
`code` contiene el ID que usará para las citas, y que `contenttype` le
indica cómo interpretar cada fila (chat, tool_execution, lookup_turn,
etc.).

Un detalle importante: los turnos de tipo `lookup_turn` (resultados de
herramientas de memoria) contienen en `tool_result` un JSON con los
turnos históricos recuperados. Estos turnos **no** deben volver a
compactarse como si fueran nuevos eventos; en su lugar, el MemoryManager
debe tratarlos como "recuerdos" y utilizarlos para enriquecer la
narrativa, manteniendo sus citas originales. El prompt incluye
instrucciones específicas para este caso.

### 6. El modelo LLM de compactación: configuración y carga

`MemoryService` no está obligado a usar el mismo modelo de lenguaje que
`ReasoningService`. De hecho, se recomienda utilizar un modelo diferente
(quizás más económico o especializado en resúmenes) para la compactación.
La configuración se realiza mediante tres claves en `settings.json`:

```json
"memory": {
  "provider": {
    "url": "https://api.deepseek.com/v1",
    "model_id": "deepseek-reasoner",
    "api_key": "sk-..."
  }
}
```

Estas claves se leen mediante `getModelParameters(MemoryService.ID)`, que
devuelve un `ModelParametersImpl` con la URL, API key e identificador del
modelo. La temperatura se fija a `0.7` (un poco de creatividad pero sin
desviarse demasiado). El método `start()` del servicio crea el modelo
invocando `agent.createChatModel(MemoryService.ID)`.

El servicio también registra dos acciones que permiten recargar el modelo
en caliente:
- `CHANGE_MEMORY_PROVIDER`: cuando se cambia la URL o la API key.
- `CHANGE_MEMORY_MODEL`: cuando se cambia el identificador del modelo.

Así, el usuario puede ajustar el modelo de compactación sin reiniciar el
agente, aunque la nueva configuración solo afectará a futuras
compactaciones.

### 7. Validación de citas y corrección de errores

Uno de los problemas más comunes al generar resúmenes con LLMs es la
**alucinación de citas**: el modelo inventa un `{cite:123}` que no
corresponde a ningún turno real, o mezcla IDs. Para mitigarlo,
`MemoryService` implementa un paso de validación posterior al texto
generado:

1. Se extraen todas las referencias `{cite:...}` del texto mediante una
   expresión regular.
2. Se construye un conjunto `validTurnIds` que contiene:
   - Los IDs de los turnos del checkpoint anterior (extraídos también
     mediante regex del texto de ese checkpoint).
   - Los IDs de los turnos en `newTurns`.
   - Además, si algún turno es de tipo `lookup_turn` o `tool_execution`,
     se escanea su `tool_result` en busca de citas adicionales (pues el
     resultado de una búsqueda puede contener citas históricas).
3. Para cada cita encontrada en el texto generado, se verifica que su ID
   pertenezca a `validTurnIds`. Si no es así, se reemplaza por
   `{badcite:ID}`.

Este paso es fundamental porque evita que el agente intente recuperar un
turno inexistente (lo que causaría un error en `fetch_citation`). En la
práctica, los modelos grandes raramente alucinan citas, pero los modelos
más pequeños o de código abierto pueden hacerlo; la validación añade una
capa de robustez.

### 8. Integración con `ReasoningService`: cuándo y cómo se compacta

El `ReasoningService` es el cliente principal de `MemoryService`. La
coordinación se realiza en el método `eventDispatcher`, al final del
procesamiento de cada turno:

```java
if (this.session.needCompaction()) {
    performCompaction();
}
```

`needCompaction()` compara el número de turnos únicos consolidados en la
sesión con un umbral configurable (`reasoning/compaction_turns`, por
defecto 40). Si se supera, se dispara la compactación.

El método `performCompaction()` realiza la siguiente secuencia:

1. Obtiene `mark1 = session.getOldestMark()` (el mensaje más antiguo
   consolidado) y `mark2 = session.getCompactMark()` (aproximadamente la
   mitad de la sesión, ajustada para no romper un turno).
2. Recupera los turnos de `SourceOfTruth` entre `mark1.getTurnId()` y
   `mark2.getTurnId()`.
3. Invoca `memory.compact(activeCheckPoint, compactTurns)`.
4. Persiste el nuevo checkpoint con `sourceOfTruth.add(newCheckPoint)`.
5. Elimina de la sesión los mensajes compactados mediante
   `session.remove(mark1, mark2)`.
6. Actualiza `activeCheckPoint = newCheckPoint`.

Además, se exponen dos acciones de depuración:
- `COMPACT_REASONING_SESSION`: compacta aproximadamente el 50% más
  antiguo de la sesión.
- `COMPACT_REASONING_FULL_SESSION`: compacta todos los turnos
  consolidados (desde el más antiguo hasta el más reciente), generando
  un único checkpoint que abarca toda la historia.

La compactación es una operación **bloqueante**: mientras se genera el
nuevo punto de guardado (lo que puede tomar varios segundos o decenas de
segundos dependiendo del modelo), el agente no procesa nuevos eventos.
Esto es aceptable porque la compactación ocurre solo ocasionalmente y,
al ser parte del turno que acaba de terminar, no interfiere con la
interactividad inmediata.

### 9. Persistencia y formato de los puntos de guardado (CheckPoints)

Un `CheckPoint` se divide en dos partes:

- **Metadatos** (tabla `checkpoints` en H2):
  - `id`: entero autoincremental.
  - `cp_first`: ID del primer turno que abarca (puede ser el primer
    turno de la historia, no solo del bloque consolidado).
  - `cp_last`: ID del último turno abarcado.
  - `timestamp`: momento de creación.

- **Contenido textual** (archivo `.md` en `var/lib/checkpoints/`):
  - Nombre del archivo: `checkpoint-{id}-{first}-{last}.md`.
  - El texto contiene dos secciones claramente separadas por cabeceras
    Markdown (aunque el prompt no exige un formato fijo, la práctica
    común es incluir `## Resumen` y `## El Viaje`).

La clase `CheckPointImpl` implementa un **lazy loading**: el contenido
textual solo se carga desde el disco cuando se invoca `getText()`.
Durante la creación, se guarda el texto en la caché y se persiste a
disco mediante `saveTextToDisk()` antes de retornar el objeto. Los
metadatos se guardan en la base de datos en el momento en que
`SourceOfTruth.add(checkpoint)` es llamado.

Esta separación permite que los checkpoints ocupen poco espacio en la
base de datos (solo los metadatos) y sean fácilmente inspeccionables con
un editor de texto. El usuario puede incluso editar manualmente un
checkpoint si desea corregir o ajustar el resumen (aunque esto debe
hacerse con cuidado para no romper las referencias de cita).

### 10. Herramientas que aporta el servicio

`MemoryService` no solo consolida la memoria a largo plazo mediante el
método `compact()`, sino que también expone al agente un conjunto de
herramientas (`AgentTool`) que le permiten **interactuar activamente con
su propio historial**. Estas herramientas están disponibles en el
catálogo de capacidades del agente y pueden ser invocadas por el LLM
durante el razonamiento.

Las tres herramientas registradas por `MemoryService` son:

#### 10.1. `fetch_citation` (LookupTurnTool)

**Propósito:** recuperar el texto exacto de un turno específico junto con
su contexto inmediato, a partir de una referencia numérica.

**Uso típico:** cuando el modelo encuentra una cita `{cite:123}` en el
resumen de un punto de guardado, debe ejecutar esta herramienta para
obtener los detalles completos de aquel momento, incluyendo los turnos
anteriores y posteriores (mediante el parámetro `context_window`).

**Parámetros:**
- `code` (obligatorio): el ID del turno o cita (ej: `"123"`).
- `context_window` (opcional, valor por defecto 2, máximo 5): número de
  turnos adicionales a recuperar antes y después del turno objetivo.

**Modo:** `MODE_READ` – solo consulta, no modifica estado.

**Tipo:** `TYPE_MEMORY` – sus resultados se registran como `lookup_turn`
en la base de datos.

#### 10.2. `search_full_history`

**Propósito:** buscar en todo el historial conversacional (desde el
primer turno hasta el último) por similitud semántica, utilizando los
embeddings almacenados en la base de datos. Es la herramienta de
recuperación por significado, ideal cuando el modelo no recuerda una
referencia concreta pero sabe de qué trata.

**Uso típico:** cuando el contexto inmediato es insuficiente y el
modelo tiene la sensación de haber hablado antes de un tema, invoca esta
herramienta con una consulta descriptiva.

**Parámetros:**
- `query` (obligatorio): texto que describe el concepto o tema a buscar.
- `limit` (opcional, valor por defecto 10, máximo 50): número máximo de
  resultados a devolver.

**Modo:** `MODE_READ`.

**Tipo:** `TYPE_MEMORY`.

#### 10.3. `annotate_observation`

**Propósito:** permitir al agente fijar una nota, resumen o insight
relevante extraído de una lectura o interacción, preservándolo en su
memoria episódica. A diferencia de las herramientas anteriores, esta no
recupera información del pasado, sino que **la escribe** para el futuro.

**Uso típico:** después de leer un archivo extenso (con `file_read`),
ejecutar un comando (con `shell_execute`) o recibir una explicación
detallada del usuario, el modelo puede invocar `annotate_observation`
para consolidar los puntos clave, evitando que se pierdan cuando el
contenido original sea podado del contexto o compactado. El sistema de
compactación incluirá estas anotaciones como hechos consolidados en los
puntos de guardado.

**Parámetros:**
- `source` (obligatorio): origen de la información (nombre de archivo,
  URL, o `"instrucción del usuario"`).
- `note` (obligatorio): texto con los hechos, conclusiones o resumen que
  el agente desea fijar.
- `resource_id` (opcional): identificador de un recurso paginado asociado
  (por ejemplo, el `resource_id` devuelto por `file_read`). Se utiliza
  para que el razonamiento pueda detectar qué recursos ya han sido
  anotados.

**Modo:** `MODE_READ` – aunque escribe información en la base de datos
(el turno de anotación), no modifica el sistema de archivos ni ejecuta
comandos, por lo que no requiere confirmación humana.

**Tipo:** `TYPE_OPERATIONAL` (por razones técnicas se registra como
operativa y no como `TYPE_MEMORY`, pero su función es claramente
episódica).

### 11. Limitaciones y desafíos actuales

A pesar de su diseño cuidadoso, `MemoryService` tiene varias limitaciones
conocidas que se documentan aquí para transparencia y para guiar futuras
mejoras:

- **Compactación bloqueante**: el agente se detiene por completo mientras
  se genera el checkpoint. Para conversaciones muy largas o con modelos
  lentos, esto podría suponer una pausa de varios segundos. Una posible
  mejora sería realizar la compactación en un hilo separado, pero entonces
  habría que gestionar la concurrencia de la sesión.

- **Umbral basado solo en número de turnos**: actualmente la compactación
  se activa al alcanzar un número fijo de turnos (40). No se tiene en
  cuenta el tamaño en tokens de esos turnos. Si los turnos incluyen textos
  muy largos (por ejemplo, salidas de herramientas con miles de líneas), el
  contexto podría saturarse antes del umbral. Una mejora pendiente es
  combinar ambos criterios.

- **Tratamiento de herramientas de memoria (`lookup_turn`)**: el código
  contiene un TODO explícito: "FIXME: probablemente habría que implementar
  el troceado de los turnos generando más de un punto de guardado, cuando
  estos no entren en el contexto del LLM encargado de compactarlos". En la
  práctica, si un `lookup_turn` recupera muchos turnos antiguos, el CSV
  resultante puede ser inmenso y no caber en el contexto del modelo de
  compactación. Actualmente no hay manejo de este caso.

- **Alucinaciones de citas**: aunque se valida post-hoc, la corrección
  convierte la cita en `{badcite:ID}`, lo que el agente interpretará como
  un error. Sería mejor prevenir la alucinación desde el prompt, pero no
  siempre es suficiente.

- **Idioma y estilo**: el prompt actual está en español, y se asume que el
  modelo de compactación lo entiende y responde en el mismo idioma. Para
  entornos multilingües habría que parametrizar el idioma.

- **Costo computacional**: generar un checkpoint implica una llamada al
  LLM que puede consumir cientos o miles de tokens, además del tiempo de
  procesamiento. En conversaciones muy largas, la compactación puede ser
  costosa. Se podría considerar el uso de un modelo más pequeño y rápido
  para esta tarea.

- **El "Viaje" como espiral de contexto**: la directiva de crear una
  narrativa que integre pasado y presente en una espiral es ambiciosa. En
  la práctica, muchos checkpoints generados por modelos actuales tienden a
  ser más bien resúmenes lineales. Alcanzar la calidad narrativa deseada
  requiere prompts muy cuidadosos y, probablemente, modelos de
  razonamiento potentes.

A pesar de estas limitaciones, `MemoryService` cumple su cometido
fundamental: permite que Noema mantenga conversaciones de cientos o miles
de turnos sin saturar la ventana de contexto, preservando la información
esencial y ofreciendo trazabilidad hacia los detalles originales. Es un
componente central en la arquitectura de memoria híbrida del agente.



# Especificación técnica de la implementación de SensorsService

### 1. Arquitectura del Sistema Sensorial (Mapa de Componentes)

### 1.1. El Orquestador (`SensorsServiceImpl`)

El `SensorsServiceImpl` es el componente central y único punto de
entrada (*facade*) para la gestión sensorial del agente. Su
responsabilidad técnica es actuar como el **centro de control
neurálgico** que desacopla la periferia (los sensores físicos o
virtuales que emiten señales) del sistema de razonamiento (el
`ReasoningService`).

Como orquestador, el servicio asume cuatro funciones críticas:

*   **Gestión del Registro de Sensores**: Mantiene un mapa interno
    (`registeredSensors`) que actúa como catálogo de todas las fuentes
    de datos activas. Es el responsable de validar que cualquier señal
    entrante provenga de un canal autorizado y de dirigirla al procesador
    (`SensorData`) adecuado, basado en la naturaleza del sensor
    (`SensorNature`).
*   **Arbitraje de Concurrencia**: Al ser el punto donde convergen
    múltiples hilos de ejecución (hilos de escucha de Telegram,
    listeners de Email, cronjobs de sistema), el servicio garantiza la
    integridad de los datos mediante la gestión del `sensorLock`. Este
    monitor central es el que impide que la llegada simultánea de
    eventos distintos corrompa el estado interno o genere
    inconsistencias en los buffers.
*   **Gestión del Ciclo de Vida y Persistencia**: Controla el estado
    operativo del servicio (`running`). Es el único componente que
    orquestará la serialización y deserialización del estado sensorial
    completo mediante el `SensorsMemento`, asegurando que, al reiniciar
    el agente, la configuración de los sensores (qué está activo, qué
    está silenciado) y las estadísticas históricas se restauren sin
    pérdida de fidelidad.
*   **Enrutamiento y Entrega**: Actúa como el administrador de las dos
    estructuras de datos de salida: la `deliveryQueue` (FIFO para eventos
    secuenciales) y el `stateMap` (para el estado actual de los sensores
    volátiles). Su lógica interna de `getEvent()` se encarga de realizar
    la **Fusión Maestra** necesaria para presentar al consumidor (el
    LLM) una realidad unificada, cronológica y, sobre todo, libre de
    ruido técnico.

En resumen, `SensorsServiceImpl` no procesa los datos en sí mismos, esa
es responsabilidad de las implementaciones de `SensorData`—, sino que
**coordina la orquestación temporal y lógica** para que el flujo de
eventos que llega al sistema sea predecible y coherente.

### 1.2. La Identidad (`SensorInformation`)

La interfaz `SensorInformation` define el **contrato de identidad** de
cada canal sensorial dentro del sistema. Actúa como el descriptor de
metadatos que permite al orquestador tratar a cada fuente de datos no
como un simple flujo de bytes, sino como una entidad con propósito,
comportamiento y capacidades definidas. Su propósito principal es
desacoplar el origen de la señal (el "qué se mide") del algoritmo de
procesamiento aplicado (la "naturaleza del sentido").

Un objeto `SensorInformation` consta de cuatro propiedades inmutables
que configuran la "ficha técnica" del sensor:

*   **Identificador del Canal (`channel`)**: Es la clave primaria y
    única dentro del mapa de sensores. Cualquier flujo de datos que
    desee ser procesado por el SNA debe estar etiquetado con este
    identificador. Es el *handle* mediante el cual el `SensorsService`
    localiza el procesador y las estadísticas asociadas.
*   **Etiqueta Legible (`label`)**: Representa el nombre amigable del
    sentido. Aunque no es funcionalmente relevante para el procesamiento
    de datos, es la etiqueta que se presenta en los paneles de
    administración y es el identificador humano en las interfaces de
    usuario (tanto CLI como GUI).
*   **Descripción Semántica (`description`)**: Es el metadato
    descriptivo que se inyecta en el *prompt* del sistema cuando el LLM
    requiere contexto sobre el entorno. Este campo no influye en la
    ejecución del código, pero es la pieza clave que permite al agente
    "entender" qué tipo de realidad está observando (ej: "Sensor de
    errores de red del clúster" vs "Feed de mensajes de usuario").
*   **Naturaleza del Sensor (`nature`)**:
    El campo `nature` es, técnicamente, la instrucción de configuración
    más crítica para el orquestador. Define el **contrato de
    comportamiento** del sensor ante el Sistema Nervioso Autónomo. Esta
    propiedad no es solo informativa; determina qué estrategia de
    procesamiento (`SensorData`) se asignará y qué tipo de paquete de
    datos (`SensorEvent`) se generará. Las naturalezas soportadas son:

    *   **`DISCRETE`**: Estímulos atómicos e independientes. El
        orquestador los trata como eventos únicos que deben entregarse
        sin agregación ni fusión, garantizando su entrega inmediata y sin
        pérdida de detalle.
    *   **`MERGEABLE`**: Estímulos secuenciales (narrativos). Instruye al
        orquestador a concatenar los mensajes entrantes en un buffer
        temporal, preservando la cronología y entregándolos como un
        bloque semántico unificado.
    *   **`AGGREGATABLE`**: Estímulos de alta frecuencia o redundantes.
        Ordena al orquestador aplicar una estrategia de contabilidad:
        solo el volumen y la frecuencia importan, por lo que el sistema
        contabiliza las ocurrencias en lugar de almacenar cada mensaje.
    *   **`STATE`**: Estímulos que representan una condición. Instruye al
        orquestador a mantener únicamente el valor más reciente del
        canal, eliminando cualquier rastro de estados previos que no
        hayan sido entregados aún.
    *   **`USER`**: Estímulos provenientes de la interacción directa.
        Instruye al sistema para tratar la entrada como una instrucción
        consciente y prioritaria, inyectándola directamente en el flujo
        de conversación sin pasar por las capas de digestión estadística
        o agregación.

    Esta clasificación transforma un objeto de información estática en
    una **instrucción de comportamiento** operativa para el orquestador.

Adicionalmente, esta identidad incluye el flag `silenceable`, que
determina si el canal es susceptible de ser filtrado por la voluntad del
agente o si, por el contrario, su reporte es ininterrumpible.

En la jerarquía del sistema, `SensorInformation` es el **primer eslabón**
en el ciclo de vida de un sentido. Sin una instancia registrada de esta
interfaz, el `SensorsService` rechaza cualquier entrada de datos,
asegurando que el sistema solo procese percepciones que hayan sido
previamente validadas y categorizadas. Es, en esencia, la configuración
que permite al agente navegar por su propia estructura sensorial de
manera estructurada y consciente.

### 1.3. La Lógica de Procesamiento (`SensorData`)

La interfaz `SensorData` define el contrato para el **motor de
procesamiento** que digiere los estímulos del entorno antes de su entrega
al SNC. Mientras que `SensorInformation` es la etiqueta que identifica
el sensor, `SensorData` es el "procesador especializado" que sabe qué
hacer con los datos recibidos. La arquitectura delega en estas
implementaciones la responsabilidad exclusiva de aplicar la estrategia
de procesamiento (digestión) adecuada a cada señal.

*   **Implementaciones por Naturaleza Sensorial**: El núcleo de la
    lógica reside en el hecho de que **a cada `SensorNature` le
    corresponde una implementación concreta de `SensorData`**. 

    *   `DiscreteSensorData`
    *   `MergeableSensorData`
    *   `AggregateSensorData`
    *   `StateSensorData`
    *   `UserSensorData` 
    
    Son las clases que contienen la lógica específica de transformación.
    Este diseño asegura que el `SensorsService` sea **abierto a la
    extensión pero cerrado a la modificación**: si el sistema requiere
    un nuevo comportamiento sensorial, basta con crear una nueva
    implementación de `SensorData` y registrarla en la factoría, sin
    necesidad de alterar el código del orquestador.

*   **Gestión del Estado Vivo (Buffers de Trabajo)**: Cada
    implementación de `SensorData` es un contenedor de estado. Mantiene un
    **Buffer de Trabajo** interno, cuyo tipo de dato varía según la
    naturaleza—, que persiste mientras el sensor está activo. Por
    ejemplo:

    *   El `MergeableSensorData` encapsula un `StringBuilder` para la
        concatenación narrativa.
    *   El `AggregateSensorData` gestiona un contador de tipo `long`.
    *   El `StateSensorData` mantiene una referencia al objeto del
        último estado válido.
    *   Esta encapsulación asegura que el "ruido" de la gestión de
        memoria (acumular, contar, limpiar buffers) esté totalmente
        oculto al resto del sistema.

*   **Motor de Reglas de Ingesta (`process`)**: El método `process()` es
    la implementación de la regla de negocio para ese sensor. Es aquí
    donde ocurre la magia de la digestión: la clase concreta decide si el
    estímulo entrante debe generar un nuevo evento, si debe ser absorbido
    por el buffer actual, o si debe disparar un *Flush* inmediato. Este
    método es la "fisiología" que traduce la entrada cruda en una acción
    dentro del buffer.

*   **Ciclo de Vida del Evento (`flushEvent`)**: Cada clase concreta
    conoce la estructura de su `SensorEvent` correspondiente. Cuando el
    orquestador solicita un `flush`, el procesador no solo devuelve los
    datos, sino que **instancia el tipo correcto de evento** (ej. un
    `SensorEventAggregateImpl` si es un sensor de conteo). Esto
    garantiza que el SNC reciba siempre un objeto tipado que ya conoce
    cómo debe ser interpretado.

*   **Autogestión de Estadísticas**: Cada procesador mantiene una
    relación 1:1 con su objeto `SensorStatistics`. Esta vinculación
    estrecha permite que, en el mismo instante en que el procesador
    digiere un estímulo, se actualicen las métricas de salud, asegurando
    que la introspección sensorial sea precisa y no un proceso
    desfasado.

### 1.4. La Unidad de Información (`SensorEvent`)

Si `SensorData` es el procesador que digiere la señal, el `SensorEvent`
es el **resultado final**, el paquete estandarizado de información que el
Sistema Nervioso Autónomo (SNA) entrega al Sistema Nervioso Central (SNC)
para su razonamiento. Su diseño es crucial para que el agente pueda
razonar sobre estímulos externos sin perder la trazabilidad temporal ni
la fidelidad semántica.

El `SensorEvent` actúa como un **contrato de comunicación** que
garantiza que cualquier dato, independientemente de su origen, tenga un
formato predecible para el modelo de lenguaje. Sus características
técnicas principales son:

*   **Identidad y Trazabilidad**: Cada evento lleva consigo su origen
    (`SensorInformation`), lo que permite al agente saber con precisión
    qué parte de su sistema sensorial ha generado la señal. Además, el
    sistema garantiza la **trazabilidad temporal** mediante tres marcas
    de tiempo críticas:

    *   `startTimestamp`: El momento exacto en que se inició el primer
        estímulo de la ráfaga.
    *   `endTimestamp`: El momento del último estímulo antes del sellado.
    *   `deliveryTimestamp`: La marca de tiempo en la que el SNA
        finalmente entregó el evento al SNC.
    Esta distinción permite al agente realizar cálculos de latencia o
    entender la duración real de un fenómeno percibido.

*   **Polimorfismo de Contenido**: El `SensorEvent` no es un objeto
    rígido. Su estructura se especializa según la naturaleza de la fuente:

    *   **`SensorEventDiscrete`**: Encapsula estímulos únicos y atómicos.
        Es el evento estándar para notificaciones directas.
    *   **`SensorEventMergeable`**: Contiene un buffer acumulado de
        texto que preserva la cronología de mensajes, permitiendo que el
        SNC perciba una conversación fluida en lugar de turnos
        fragmentados.
    *   **`SensorEventAggregate`**: En lugar de texto, expone un contador
        (`count`), permitiendo que el agente trabaje con la **intensidad**
        del evento en lugar del detalle individual.
    *   **`SensorEventState`**: Ofrece el valor más reciente de un
        estado, actuando como una "fotografía" de la variable en el
        momento de la entrega.
    *   **`SensorEventUser`**: Representa un estímulo originado por la
        interacción directa del usuario. A diferencia de los eventos de
        máquina, este evento **no requiere un par de herramientas
        (ficticia + resultado)** para ser entregado, sino que se inyecta
        directamente como un `UserMessage` puro en la sesión del agente.
        Es el único evento que el SNC no interpreta como un "dato del
        entorno", sino como una "instrucción directa".

*   **Estandarización para el LLM (`ConsumableSensorEvent`)**: Esta es la
    faceta más técnica del evento. A través de la interfaz
    `ConsumableSensorEvent`, el objeto es capaz de **auto-representarse**
    en el formato que el LLM espera. 

    *   Provee un método `toJson()` que serializa el evento para el
        prompt.
    *   Implementa `getChatMessage()` y `getResponseMessage()`, que son
        los métodos que permiten al orquestador inyectar el evento en el
        flujo de conversación mediante la "mentira necesaria" del
        `pool_event`. 

*   **Inmutabilidad Lógica**: Aunque durante su construcción en el
    `SensorData` el evento puede ser mutable, en el momento en que es
    entregado a la `DeliveryQueue`, se considera un objeto **inmutable**.
    Esto es fundamental para evitar efectos secundarios: una vez que el
    SNC comienza a razonar sobre un evento, el SNA no puede alterar su
    contenido, garantizando una base de datos de razonamiento estable.

En esencia, `SensorEvent` es la **moneda de cambio** del sistema
sensorial. Es el objeto que logra el puente entre la señal física, el log,
el mensaje, la alerta, y el concepto abstracto que el cerebro del agente
utilizará para planificar su próxima acción.

### 1.5. El Registro de Salud (`SensorStatistics`)

La clase `SensorStatisticsImpl` y su interfaz asociada `SensorStatistics`
constituyen el componente de **monitorización interna** de cada sensor.
Su propósito es cuantificar la actividad del sistema sensorial de forma
independiente al contenido de los mensajes, proporcionando datos sobre la
"salud operativa" de cada canal. Es el componente que permite al
`SensorsService` (y en última instancia al agente) tomar decisiones
basadas en métricas de uso.

*   **Responsabilidad Técnica**: Actúa como un registro de eventos y
    estados ligado indisolublemente a un `SensorData`. Cada procesador de
    señales posee su propia instancia de estadísticas, lo que permite que
    el `SensorsService` tenga una visión granular de lo que ocurre en cada
    canal sensorial individual.
*   **Contadores de Actividad**: Mantiene el estado persistente de dos
    indicadores fundamentales: `totalEventsActive` y
    `totalEventsSilenced`. Estos contadores no miden el contenido de la
    información, sino el **volumen de tráfico**. La distinción entre
    eventos activos y silenciados es vital: permite al sistema
    identificar qué parte del flujo sensorial está siendo ignorada
    voluntariamente por el agente frente a la que está siendo
    efectivamente procesada.
*   **Trazabilidad Temporal de la Actividad**: Almacena las marcas de
    tiempo de `lastEventTimestamp` (cuando ocurrió el último evento) y
    `lastDeliveryTimestamp` (cuando el SNA entregó el evento al SNC).
    Estos datos son la base técnica para calcular la latencia del
    sistema: si la diferencia entre ambos es elevada, el agente puede
    identificar un cuello de botella en su propio procesamiento o una
    sobrecarga en su capacidad de razonamiento.
*   **Gestión del Estado de Silencio (`silenced`)**: El registro incluye
    un flag booleano que actúa como un **interruptor de bajo nivel**
    dentro del `SensorsService`. Cuando `isSilenced()` es `true`, el
    procesador de datos asociado ignora cualquier estímulo entrante antes
    de que este alcance la capa de procesamiento (`process()`),
    protegiendo la memoria y el contexto del agente de información que el
    SNC ha decidido, mediante su voluntad, no atender.
*   **Persistencia y Rehidratación**: La clase es compatible con el
    `SensorsMemento` mediante el `SensorStatisticsGsonAdapter`. Esto
    garantiza que los contadores, los estados de silencio y las marcas
    temporales no se pierdan tras un reinicio. Al rehidratarse, el
    sistema recupera su "memoria biográfica" sensorial, permitiendo que
    las métricas de salud (como la frecuencia media) tengan un histórico
    real desde el primer segundo en que el agente vuelve a estar
    operativo.

### 1.6. Persistencia y Rehidratación (`SensorsMemento`)

El `SensorsMemento` es el objeto de transferencia de estado (*Data
Transfer Object*) que encapsula la "fotografía" completa del
`SensorsService` para su persistencia en el sistema de archivos
(`sensors.json`). Su función es evitar la amnesia del sistema sensorial
ante reinicios de la JVM, garantizando que el agente retome su percepción
exactamente donde la dejó.

*   **Responsabilidad Técnica**: Actúa como un **contenedor de
    serialización** que agrupa los tres estados críticos del servicio
    sensorial:

    *   **Registro de identidades (`infos`)**: El mapa de
        `SensorInformation` que define qué canales existen. Es la primera
        parte en ser reconstruida durante la rehidratación para que el
        resto del sistema entienda a quién pertenecen los datos.
    *   **Estado de entrega (`deliveryQueue`)**: Snapshot de los
        `SensorEvent` que quedaron pendientes de entrega en el momento del
        apagado (los eventos "a medio camino" que fueron sellados pero no
        consumidos).
    *   **Estado de sensores (`stateMap`)**: Fotografía de los últimos
        valores válidos para sensores de naturaleza `STATE`, asegurando
        que no se pierda la última "foto" conocida de la realidad.
    *   **Mapa de salud (`statisticsMap`)**: Preservación del estado de
        silencio (`silenced`) y el histórico contable (`SensorStatistics`)
        de cada canal.

*   **El Protocolo de Rehidratación (Reconstrucción en dos fases)**: El
    proceso de carga no es una simple lectura de JSON, sino una
    **reconstrucción estructurada**:

    1.  **Fase de Identidad**: El sistema procesa primero los metadatos
        (`infos`). Esto es fundamental porque los adaptadores de GSON
        (`SensorEventGsonAdapter`) necesitan consultar el catálogo de
        sensores activos para reconstruir correctamente los eventos de la
        cola.
    2.  **Fase de Rehidratación de Estado**: Una vez recuperada la
        identidad, el sistema deserializa el memento. Aquí se reinyectan
        los eventos en la `deliveryQueue` y se restauran las métricas en
        `rehydratedStats`. Este mapa temporal es clave: mantiene las
        estadísticas en un limbo hasta que el sensor correspondiente (ej.
        un sensor de temperatura) se registra formalmente en el ciclo de
        arranque, momento en el cual el servicio vincula las estadísticas
        guardadas con el procesador recién creado.

*   **Atomicidad y Seguridad**: El proceso de guardado utiliza un patrón
    de **escritura atómica** (escritura en `.tmp` seguida de un
    `Files.move`). Esto previene la corrupción del estado sensorial en
    caso de que el proceso se interrumpa bruscamente durante el apagado. 

*   **Integridad del Grafo Sensorial**: Gracias al `SensorsMemento`, la
    identidad, el volumen de actividad y los estados actuales forman un
    **grafo persistente**. Al arrancar, el agente no solo recupera datos;
    recupera su relación con el entorno, permitiéndole saber qué canales
    estaban "sordos" (silenciados) y cuáles estaban "ruidosos"
    (frecuencia de eventos) antes de la interrupción.

### 1.7. Topología del Sistema Sensorial (Mapa de Relaciones)

Esta sección resume la estructura estática del sistema, consolidando las
relaciones de propiedad y jerarquía que permiten el funcionamiento del
`SensorsService`:

*   **Jerarquía de Posesión**:

    *   `SensorsServiceImpl` es el nodo raíz, poseyendo un mapa de
        `SensorData` (`registeredSensors`).
    *   Cada `SensorData` es un contenedor que **posee** exactamente una
        `SensorInformation` (su identidad) y una `SensorStatistics` (su
        estado de salud).
    *   Cada `SensorData` **produce** (o gestiona el buffer de) un tipo
        específico de `SensorEvent`.

*   **Flujo de Referencias**:

    *   El `SensorEvent` mantiene una referencia a su `SensorInformation`
        de origen, permitiendo la trazabilidad desde el evento hasta el
        sentido que lo generó.
    *   El `SensorsService` mantiene una referencia a todas las
        estadísticas de los sensores, incluso de aquellos que han sido
        registrados pero están temporalmente inactivos (vía
        `rehydratedStats`).

*   **Visualización del Grafo**:

    *   El sistema puede visualizarse como un grafo donde el
        `SensorsService` es el centro. Los nodos `SensorData` son las
        "estaciones de procesamiento" conectadas a la periferia. Los
        flujos de datos (`SensorEvent`) son aristas temporales que
        conectan el `SensorData` con la `DeliveryQueue` o el `StateMap`.

### 2. Dinámica de Procesamiento: El Ciclo de Vida del Estímulo

Este punto describe cómo el `SensorsService` gestiona el movimiento de los
datos desde que un hilo externo dispara una señal hasta que esta se
consolida en la `deliveryQueue` o el `stateMap`. Aquí es donde la
arquitectura estática definida en el Punto 0 cobra vida mediante la
concurrencia y los bloqueos de estado.

#### 2.1. Ingesta Atómica: El protocolo `putEvent`
Cuando un componente externo (ej. `TelegramService`) invoca `putEvent()`,
el servicio garantiza la integridad del sistema mediante el `sensorLock`.
Esta operación no es una simple escritura en cola, sino una **transición
de estado**:

1.  **Protección de la Fisiología**: El `sensorLock` asegura que ningún
    otro hilo pueda alterar el `currentSensor` mientras se evalúa el nuevo
    estímulo.
2.  **Validación Sensorial**: Se consulta el estado `silenced` del
    sensor. Si está activo, el estímulo es descartado en la frontera,
    protegiendo al sistema de procesar datos que el SNC (LLM) ha decidido
    ignorar.
3.  **Resolución de Discontinuidad**: El orquestador compara el canal
    entrante con el `currentSensor`. Si detecta un cambio (o si el evento
    es de naturaleza `DISCRETE`), dispara el protocolo de `flush()` sobre
    el buffer previo. Esto garantiza que la "cubeta" anterior se selle
    antes de que la nueva comience a llenarse, evitando la mezcla de
    contextos semánticos.

#### 2.2. Digestión en Tiempo Real: El rol de `SensorData`
Una vez validada la ingesta, el control se transfiere al `SensorData`
correspondiente. Es aquí donde ocurre la transformación dinámica:

*   **Mutación del Buffer**: El procesador invoca su método `process()`.
    Dependiendo de su naturaleza, el `SensorData` muta su buffer interno
    (concatena texto, incrementa un contador o sobrescribe un valor).
*   **Gestión de Memoria Eficiente**: El sistema evita instanciar objetos
    `SensorEvent` prematuramente. El dato reside en el buffer interno del
    procesador hasta que un evento de `Flush` o una consulta del SNC
    (`getEvent`) fuerzan la creación del objeto de entrega. Esto minimiza
    drásticamente el *garbage collection* en condiciones de alta carga.

#### 2.3. Arbitraje y Entrega: La Fusión Maestra
La entrega no es un proceso de "vaciado" directo, sino un arbitraje
dinámico. Cuando el SNC solicita un evento, el servicio no entrega
simplemente el primero de la `deliveryQueue`; ejecuta la **Fusión
Maestra**:

*   **Sincronización**: Se fuerza un `flush()` final para asegurar que
    cualquier dato "en cocción" en el `currentSensor` pase a la cola de
    entrega.
*   **Selección Cronológica**: Se comparan las marcas de tiempo
    (`startTimestamp`) del evento más antiguo en la `deliveryQueue`
    contra el `stateMap`. El servicio elige siempre el estímulo más
    antiguo, garantizando que el LLM reciba la realidad en el orden
    causal exacto en que ocurrió.
*   **Cierre de Entrega**: Al extraer el evento, se le asigna el
    `deliveryTimestamp`. Este valor es fundamental para que el SNC pueda
    calcular el "lag" de percepción, permitiéndole razonar sobre la
    frescura de la información recibida.

#### 2.4. Reactividad del Orquestador: La señal de sincronía
El flujo se completa mediante un patrón *Producer-Consumer* gestionado
por el monitor `sensorLock`. 
*   **Estado de Espera**: Si el `ReasoningService` solicita un evento y la
    `deliveryQueue` está vacía, el hilo consumidor entra en estado de
    `wait()`.
*   **Despertar Activo**: En cuanto `putEvent()` consolida un nuevo
    evento, se invoca `notifyAll()`. Esto garantiza una reactividad
    inmediata: el sistema sensorial "despierta" al cerebro exactamente
    cuando hay trabajo útil que realizar, eliminando la necesidad de
    sondeos (*polling*) costosos y manteniendo el agente en un estado de
    reposo eficiente mientras no hay estímulos que procesar.

### 3. Estrategias de Digestión: Taxonomía de Procesamiento (`SensorNature`)

Una vez que el `SensorsService` recibe un estímulo y lo enruta al
`SensorData` correspondiente, entra en juego la **lógica de digestión**.
Esta capa es la encargada de transformar los datos crudos en eventos con
significado semántico, siguiendo una estrategia predefinida por su
`SensorNature`. Cada naturaleza define un **comportamiento de mutación**
del estado interno (el buffer) del procesador.

#### 3.1. Procesamiento Discreto (`DISCRETE`)
*   **Comportamiento**: Transmisión sin mediación.
*   **Lógica**: Al invocar `process()`, el sensor no realiza acumulación
    ni análisis. La señal se encapsula inmediatamente en un
    `SensorEventDiscreteImpl`.
*   **Impacto en el flujo**: Es la señal de menor latencia. El
    orquestador detecta el evento y dispara el `flush()` casi
    simultáneamente, entregando al SNC una pieza de información atómica
    que es valiosa por su unicidad.

#### 3.2. Procesamiento Fusionable (`MERGEABLE`)
*   **Comportamiento**: Acumulación narrativa.
*   **Lógica**: El procesador mantiene un `StringBuilder` privado. Cuando
    `process()` recibe un nuevo texto, lo añade al buffer junto con su
    marca de tiempo, pero no solicita un `flush()`.
*   **Impacto en el flujo**: Esta estrategia permite que un hilo de
    conversación de 20 mensajes entre al sistema como una única unidad
    narrativa. El SNA "esconde" la fragmentación del mundo real para que
    el SNC reciba un bloque coherente de texto cronológico, evitando
    saturar el historial del LLM con turnos triviales.

#### 3.3. Procesamiento Agregable (`AGGREGATABLE`)
*   **Comportamiento**: Cuantificación escalar.
*   **Lógica**: El procesador mantiene un contador `long` interno. Cada
    llamada a `process()` solo ejecuta `count++`.
*   **Impacto en el flujo**: Es la estrategia de mayor eficiencia
    operativa. Ante un entorno ruidoso (ej: miles de peticiones de red por
    minuto), el SNA no satura el canal de entrega con eventos
    individuales; simplemente incrementa una cifra. Solo cuando el SNC
    solicita el evento, el procesador inyecta un resumen estadístico: *«Se
    han detectado X eventos de este tipo»*. Es la forma más potente de
    reducir el ruido de sistemas de telemetría sin perder la señal de
    actividad.

#### 3.4. Procesamiento de Estado (`STATE`)
*   **Comportamiento**: Sustitución absoluta (El presente invalida el
    pasado).
*   **Lógica**: El procesador no utiliza la `DeliveryQueue`. En su lugar,
    sobrescribe directamente el valor en el `stateMap` del
    `SensorsService`.
*   **Impacto en el flujo**: Garantiza que, sin importar cuánto tiempo
    pase entre una consulta y otra, el agente siempre tenga la fotografía
    más reciente. Es ideal para variables ambientales que cambian
    constantemente pero cuya historia pasada es irrelevante para la toma
    de decisiones inmediata (ej: niveles de batería, temperatura actual).

#### 3.5. Procesamiento de Interacción (`USER`)
*   **Comportamiento**: Inyección directa a la consciencia.
*   **Lógica**: Este tipo de sensor puentea las optimizaciones de
    agregación o fusión. Cada entrada del usuario se procesa como un
    evento prioritario, convirtiéndose en un `UserMessage` que el
    `ReasoningService` integrará obligatoriamente en el siguiente turno
    de razonamiento.
*   **Impacto en el flujo**: A diferencia de los otros procesadores, este
    **nunca se silencia**. Es el único estímulo que garantiza una
    respuesta inmediata del agente, rompiendo cualquier ciclo de
    pensamiento en curso para priorizar la interacción humana.

### 4. El Mecanismo de "Cierre Forzado" (`Flush` y `currentSensor`)

El `Flush` es el protocolo de seguridad que transforma un buffer de
trabajo volátil en un `SensorEvent` inmutable y listo para el
razonamiento. Este mecanismo es el que asegura que el SNC (LLM) siempre
reciba "paquetes sellados" y nunca fragmentos en proceso de construcción.

#### 4.1. El Puntero `currentSensor` como exclusividad
El `SensorsService` mantiene una referencia única llamada `currentSensor`.
Este puntero es el mecanismo de control de flujo que impide que varios
sensores intenten entregar datos al SNC de forma simultánea. 
*   **Gestión de exclusividad**: Solo el procesador referenciado por
    `currentSensor` tiene permiso para añadir datos a su buffer. 
*   **Regla de interrupción**: Si llega un estímulo desde un canal
    diferente al que apunta `currentSensor`, el orquestador dispara
    inmediatamente un `flush()` sobre el procesador actual antes de
    redirigir la "exclusividad" al nuevo sensor. Este cambio de foco
    garantiza que no exista solapamiento narrativo: el LLM nunca recibirá
    un evento parcial de Telegram mezclado con uno de Email.

#### 4.2. Disparadores del Flush (Protocolo de Clausura)
El `flushEvent()` no es una operación arbitraria; es un acto
administrativo que se dispara ante tres condiciones de contorno:

*   **Discontinuidad Semántica**: Ocurre cuando el orquestador detecta un
    cambio de contexto (ej: un evento de una naturaleza distinta o de un
    canal diferente). El `flush()` actúa aquí como un "punto y aparte",
    cerrando el bloque anterior para que el buffer del procesador pueda
    ser vaciado.
*   **Satisfacción del SNC**: Cuando el `ReasoningService` consulta la cola
    de eventos, exige una entrega inmediata. El servicio central recorre
    todos los `SensorData` registrados y les ordena un `flush()` forzado.
    Esto garantiza que cualquier evento que estuviera "a medio cocer" en
    la memoria volátil del procesador sea enviado a la `deliveryQueue` para
    ser procesado en el turno actual.
*   **Naturaleza DISCRETE**: Dado que estos eventos no tienen dimensión
    temporal ni narrativa, el procesador aplica un `flush()` implícito en
    el mismo instante en que se ejecuta `process()`. El buffer nunca llega
    a retener datos; nace cerrado y listo para la entrega.

#### 4.3. La Operación de Sellado: Del Buffer al Evento
La ejecución del `flush()` es un proceso transaccional que sigue cuatro
pasos técnicos ininterrumpibles:

1.  **Extract**: El procesador `SensorData` extrae la información bruta
    (el texto acumulado o el contador) de su buffer privado.
2.  **Sello Temporal (`endTimestamp`)**: Se registra el instante preciso
    en el que se cierra el buffer. Esta es la marca que define el límite
    superior del evento, cerrando el rango cronológico que el SNC
    utilizará para sus cálculos de latencia.
3.  **Instanciación**: El procesador crea una instancia inmutable de
    `SensorEvent` (p. ej. `SensorEventMergeableImpl`). En este momento, el
    evento deja de ser una "variable de trabajo" y se convierte en un
    objeto de datos persistente.
4.  **Entrega y Reinicio**: El objeto resultante se encola en la
    `deliveryQueue`. Inmediatamente después, el procesador limpia su buffer
    interno (ej: `StringBuilder.setLength(0)`), devolviendo el componente
    a su estado basal y liberando el `currentSensor` para el siguiente
    evento.

#### 4.4. Garantía de Inmutabilidad
Una vez que el `flush()` concluye y el `SensorEvent` reside en la
`deliveryQueue`, su contenido ya no puede cambiar. Si el procesador
recibiera un nuevo estímulo un milisegundo después, este iniciaría un
**nuevo evento** con un nuevo `startTimestamp`. Esto es vital para el
razonamiento: el SNC opera sobre una línea de tiempo compuesta de
"instantes sellados", evitando cualquier efecto *jitter* o distorsión en
el historial narrativo que el agente gestiona.

### 5. Fusión Maestra: Arbitraje Cronológico

El arbitraje cronológico es la fase en la que el `SensorsService` resuelve
la entropía de eventos asíncronos para ofrecerle al SNC (el LLM) una
narrativa lineal y causalmente correcta. Dado que los estímulos llegan de
fuentes independientes (ej. un log del sistema, un mensaje de Telegram,
una alarma de Scheduler), el orden físico de llegada a la `deliveryQueue`
no siempre garantiza el orden lógico de los hechos.

#### 5.1. El Conflicto de los Dos Almacenes
El arbitraje debe lidiar con una estructura dual:
*   **`deliveryQueue`**: Contiene eventos secuenciales (Discretos,
    Fusionados o Agregados) que ya han sido sellados por un `flush()`. Su
    orden relativo es cronológico dentro del mismo canal.
*   **`stateMap`**: Almacena el estado actual de los sensores de
    naturaleza `STATE`. Estos eventos no están en una línea de tiempo fija,
    sino que son "flotantes": su validez es siempre el presente absoluto.

La "Fusión Maestra" es el algoritmo que ejecuta el método `getEvent()`
para decidir qué estímulo es el legítimo "siguiente" en la corriente de
consciencia del agente.

#### 5.2. Algoritmo de Selección: `findOldestCandidate`
Para resolver qué mensaje presentar, el sistema ejecuta una comparación
directa basada en el `startTimestamp` (el momento exacto en que se inició
el estímulo). El arbitraje funciona bajo dos reglas estrictas:

1.  **Prioridad por Antigüedad**: Se compara la marca de tiempo de la
    cabeza de la `deliveryQueue` (el evento sellado más antiguo) con las
    marcas de tiempo de todos los valores presentes en el `stateMap`.
2.  **Resolución de Conflictos**:
    *   Si el evento de la `deliveryQueue` tiene un `startTimestamp`
        anterior a cualquier estado del `stateMap`, el sistema lo extrae.
        Esto garantiza que la narrativa de los hechos secuenciales se
        entregue sin alteraciones.
    *   Si un estado del `stateMap` es más antiguo que el primer evento de
        la cola, el sistema extrae dicho estado. Esto permite que el
        agente reconozca cambios en su entorno (ej. una actualización de
        variable de sistema) en el momento exacto en que ocurrieron,
        incluso si el evento fue "adelantado" por un mensaje de chat
        posterior.

#### 5.3. El Árbitro de la Causalidad
La Fusión Maestra no es solo un reordenamiento de datos; es la **garantía
de causalidad** del sistema.
*   **Prevención de la Inversión Temporal**: Si permitiéramos que eventos
    más recientes (ej: una confirmación de herramienta) se entregaran antes
    que el evento que los originó (ej: una solicitud del usuario), el LLM
    perdería la capacidad de entender la relación causa-efecto. El árbitro
    asegura que el agente siempre perciba el mundo en un flujo coherente
    de hechos.
*   **Transparencia Sensorial**: Al entregar eventos desde el `stateMap`
    basados en su `startTimestamp`, el sistema permite que el agente
    entienda que un valor de estado (como una temperatura) cambió *durante*
    el transcurso de una conversación, otorgándole una consciencia temporal
    del entorno que va más allá de la simple recepción de datos.

#### 5.4. Inyección del `deliveryTimestamp`
Una vez que el árbitro elige el evento ganador, el `SensorsService` le
asigna un `deliveryTimestamp`. Este es el momento exacto en que la
información "cruza el umbral" hacia la consciencia del agente. 
*   **Uso del SNC**: Esta marca es crítica para que el LLM pueda calcular
    la **frescura de la percepción**. Al comparar el `startTimestamp`
    (cuándo pasó) con el `deliveryTimestamp` (cuándo me entero), el agente
    puede razonar sobre su propia latencia: *"Este evento ocurrió hace 5
    segundos, la información es fresca"* frente a *"Este evento ocurrió
    hace 10 minutos, es posible que el entorno haya cambiado"*.

La Fusión Maestra es, por tanto, el motor que convierte un caos de señales
asíncronas en una **narrativa de hechos** donde la causalidad es
innegociable. Sin este arbitraje, el agente no percibiría un entorno,
sino una colección desordenada de datos.

### 6. Metacognición: Estadísticas y Salud (`SensorStatistics`)

El `SensorsService` no solo procesa datos; mantiene un registro
introspectivo sobre la calidad y el rendimiento de sus propios canales. La
clase `SensorStatistics` funciona como un **cuadro de mandos
fisiológico**, permitiendo que el agente no solo "vea" el mundo, sino que
comprenda su propia capacidad de percepción. Esta metainformación es el
insumo necesario para que el SNC (el LLM) pueda ejercer un control
estratégico sobre su atención.

#### 6.1. Cuantificación de la Actividad Sensorial
El sistema mantiene un histórico granular de la actividad mediante dos
contadores fundamentales:
*   **`totalEventsActive`**: Mide el volumen de señal procesada. Es el
    indicador de carga de trabajo de cada sensor, permitiendo al agente
    identificar cuáles de sus sentidos están siendo más solicitados.
*   **`totalEventsMuted`**: Esta métrica es la base de la **inteligencia
    selectiva**. Registra cuántos eventos fueron descartados por estar el
    sensor silenciado. Es un dato crítico para la toma de decisiones: si
    el volumen de eventos silenciados crece desmesuradamente, el agente
    puede inferir que está "perdiendo el rastro" de un canal que quizás
    debería volver a monitorizar.

#### 6.2. Análisis de Frecuencia y Salud del Entorno
Más allá del conteo bruto, el sistema calcula la **frecuencia media** de
cada sensor. Esta métrica transforma el dato temporal en un indicador de
salud:
*   **Detección de Anomalías**: Un incremento drástico en la frecuencia de
    un canal (ej. un sensor de sistema pasando de 0 eventos a 500 por
    segundo) permite al agente detectar estados de "Estrés" sensorial.
*   **Monitoreo de Latencia de Entrega**: Al cruzar la marca del evento
    (`lastEventTimestamp`) con la marca de entrega (`lastDeliveryTimestamp`),
    el servicio calcula el *lag* de procesado. Si este delta aumenta, el
    agente es consciente de que su propia capacidad de razonamiento está
    saturada, ya que no logra consumir los eventos a la velocidad que el
    SNA los digiere.

#### 6.3. El Estado de Silencio (`silenced`)
El flag `silenced` no es solo una variable booleana; es la **interfaz de
voluntad** entre el SNC y el SNA.
*   Cuando el agente decide concentrarse, ejecuta una instrucción que
    modifica este estado en `SensorStatistics`. A partir de ese momento, el
    servicio sensorial modifica su comportamiento: los datos crudos ya no
    se procesan, sino que se computan únicamente como "eventos ignorados".
*   Este mecanismo permite al agente proteger su ventana de contexto del
    LLM frente a fuentes que, aunque interesantes, son irrelevantes para la
    tarea actual, aplicando un filtro de atención consciente que es, a la
    vez, estadísticamente rastreable.

#### 6.4. Integración en la Consciencia: `sensor_status`
Toda esta telemetría es expuesta al agente mediante la herramienta
`sensor_status`. Esto no es una mera consulta técnica; es una **consulta
de autodiagnóstico**. 

*   El agente utiliza este informe para evaluar si su "cuerpo" está
    operando dentro de los parámetros esperados.
*   Si el agente recibe una alerta del usuario, puede consultar
    `sensor_status()` para comprobar si algún sentido estaba desactivado o
    si la tasa de errores (agregados en las estadísticas) justifica el
    problema. 

En esencia, `SensorStatistics` cierra el bucle de retroalimentación: el
sistema sensorial no solo entrega información al cerebro, sino que también
entrega información sobre **cómo de bien está funcionando ese proceso de
entrega**. Esto dota al agente de una capacidad de **ajuste de prioridades**
basada en datos, transformando la percepción pasiva en una estrategia de
atención activa.

## Seguridad y Control de Acceso (`AgentAccessControl`)

### 1. Introducción

Noema no es un simple conversador; tiene la capacidad de leer y escribir
archivos, ejecutar comandos en el sistema operativo y conectarse a
servicios externos. Esta autonomía es necesaria para que el agente
resulte útil, pero también introduce riesgos evidentes: un error del
modelo, una alucinación o una instrucción maliciosa podrían tener
consecuencias no deseadas sobre el sistema de archivos o la privacidad
del usuario.

Para gestionar este dilema, Noema incorpora un subsistema de seguridad
explícito centrado en la clase `AgentAccessControl`. Su misión es doble:
por un lado, **definir qué operaciones están permitidas** en función del
contexto y la configuración; por otro, **someter las operaciones
peligrosas a confirmación humana** antes de ejecutarlas. Actúa como un
guardián que filtra todas las acciones del agente, asegurando que la
autonomía se ejerza siempre dentro de unos límites controlados.

El diseño parte de una premisa pragmática: la seguridad no consiste en
impedir que el agente actúe, sino en garantizar que cada acción con
posibles efectos destructivos cuente con la supervisión explícita del
usuario. De este modo, Noema puede ofrecer capacidades avanzadas
(escribir archivos, ejecutar scripts) sin renunciar a la confianza del
operador humano.

### 2. El modelo de permisos: modos de acceso y políticas

Toda herramienta (`AgentTool`) declara, mediante el método `getMode()`,
uno de los siguientes modos de operación:

- **`MODE_READ`**: operaciones de solo lectura (leer un archivo,
  consultar una API, buscar en el historial). No alteran el estado del
  sistema y se consideran seguras.
- **`MODE_WRITE`**: operaciones que modifican el sistema de archivos
  (escribir, parchear, crear directorios). Pueden destruir información
  si se usan incorrectamente.
- **`MODE_EXECUTION`**: ejecución de comandos en el shell del sistema. El
  más peligroso, pues permite cualquier acción que el usuario pueda
  realizar desde la terminal.
- **`MODE_WEB`**: acceso a internet (búsquedas, descargas). Aunque no
  suele ser destructivo, puede comprometer la privacidad o consumir
  recursos.

Estos modos se combinan con políticas globales que el usuario puede
configurar en `settings.json` bajo la sección `access_control`:

```json
"access_control": {
  "humanConfirmationRequired": true,
  "allow_disk_write": false,
  "allow_shell_execution": false,
  "allow_internet_access": false,
  "enable_rcs_backup": true,
  "enable_firejail": false,
  ...
}
```

`AgentAccessControl` expone métodos como `isAllowedDiskWrite()`,
`isAllowedShellExecution()` e `isAllowedInternetAccess()`. Si una
herramienta intenta ejecutarse en un modo que está deshabilitado
globalmente, `isToolAllowed()` devuelve `false` y `ReasoningService` ni
siquiera la ofrecerá al modelo (o la ejecución se denegará). Esta doble
capa (declaración local + política global) permite un control muy fino:
el usuario puede, por ejemplo, permitir lectura de archivos pero prohibir
cualquier escritura, o activar la ejecución de shell solo cuando
realmente confíe en el agente.

### 3. El sandbox de archivos

El control de acceso al sistema de archivos se basa en un mecanismo de
**resolución de rutas** implementado en `resolvePath(String rawPath,
AccessMode mode)`. El proceso es el siguiente:

1. **Normalización y absoluto**: la ruta introducida (puede ser relativa
   o absoluta) se resuelve contra la raíz del workspace
   (`getWorkspaceFolder()`). Se normaliza y se convierte a ruta real
   (`toRealPath()`) para eliminar `..` y enlaces simbólicos maliciosos.

2. **Comprobación de jailbreak**: se verifica que la ruta resultante esté
   dentro del workspace o dentro de alguna de las rutas externas
   autorizadas (lista blanca configurable mediante
   `allowed_external_paths`). Si no es así, se lanza una excepción de
   seguridad.

3. **Restricciones específicas de escritura**: si el modo es
   `PATH_ACCESS_WRITE`, se aplican reglas adicionales:
   - No se puede escribir sobre archivos con extensión `,jv` (los backups
     de RCS). Son de solo lectura para preservar la integridad del
     historial de versiones.
   - No se puede escribir dentro de la carpeta `.git` (evita corromper
     repositorios de control de versiones).
   - Se comprueba si la ruta está en `nom_writable_paths` (lista de rutas
     no escribibles configurada por el usuario).

4. **Comprobaciones de lectura**: incluso para `PATH_ACCESS_READ`, se
   verifica que la ruta no esté en `nom_readable_paths` (lista de rutas
   prohibidas, como archivos de configuración sensibles del agente).

Si alguna de estas condiciones falla, se lanza una `SecurityException`.
Para situaciones en las que no se desea interrumpir el flujo (por ejemplo,
al listar archivos), existe `resolvePathOrNull()` que devuelve `null` en
lugar de lanzar excepción.

Este diseño impide eficazmente los ataques de *path traversal* (ejemplo:
`../../etc/passwd`). Además, es extensible: el usuario puede añadir nuevas
rutas a la lista blanca (como su carpeta `Documentos`) y restringir otras
que considere peligrosas.

### 4. Confirmación humana

El filtro más importante es la **confirmación humana**. Cuando una
herramienta con modo `MODE_WRITE` o `MODE_EXECUTION` está a punto de
ejecutarse, y la política global `humanConfirmationRequired` está activa,
`AgentAccessControl` (o más bien el `ReasoningService` antes de invocar la
herramienta) solicita autorización al usuario mediante
`AgentConsole.confirm()`.

El mensaje incluye el nombre de la herramienta y los argumentos que se
van a utilizar. Por ejemplo:

```
El agente quiere ejecutar la herramienta: file_write
Argumentos: {"path": "config.json", "content": "{\"key\": \"value\"}"}
¿Autorizar? (s/n):
```

El usuario puede responder afirmativa o negativamente. Si deniega, la
herramienta no se ejecuta y se devuelve un mensaje de error que el LLM
recibe como resultado de su llamada. El agente puede entonces explicar
que la operación no fue autorizada y, opcionalmente, proponer una
alternativa.

La confirmación es **bloqueante**: el hilo del `eventDispatcher` se
detiene hasta que el usuario responda. Esto es intencionado, pues el
agente no debe continuar razonando mientras una acción peligrosa está
pendiente de decisión. En la interfaz gráfica, se muestra un diálogo
modal; en la consola, se espera entrada por teclado.

Este mecanismo sitúa al usuario en la posición de **supervisor último**.
Incluso si el agente, por error o engaño, intenta borrar un archivo
crítico, el humano tiene la oportunidad de detenerlo. Es una salvaguarda
rudimentaria pero efectiva, especialmente en una fase de prototipo donde
el comportamiento del LLM no es totalmente fiable.

### 5. Backup automático con RCS

Antes de que cualquier herramienta modifique un archivo existente
(escritura, parche, búsqueda y reemplazo), se invoca al sistema RCS
embebido (JavaRCS) para hacer un **check-in automático** de la versión
actual. El código típico es:

```java
if (Files.exists(filePath)) {
    RCSManager rcsmanager = RCSLocator.getRCSManager();
    CheckinOptions opciones = rcsmanager.createCheckinOptions(filePath);
    opciones.setAuthor(getReasoningService().getModelName());
    opciones.setInit(true);
    RCSCommand ci = rcsmanager.create(opciones);
    ci.execute(opciones);
}
```

El resultado es que, junto al archivo original, se genera un fichero de
historial (normalmente con extensión `,jv`) que contiene todas las
versiones anteriores. El LLM puede recuperar una versión antigua mediante
las herramientas `file_history` (para listar revisiones) y `file_recovery`
(para restaurar una revisión concreta).

Esta funcionalidad, que se activa mediante `enable_rcs_backup` (por
defecto `true`), constituye una **red de seguridad** frente a errores del
LLM. Si el agente escribe un contenido erróneo o corrompe un archivo, el
usuario o el propio agente puede deshacer el cambio. Además, fomenta la
experimentación: el usuario puede permitir escrituras sin temor a perder
información valiosa.

### 6. Ejecución de comandos

La herramienta `shell_execute` es la más sensible, pues permite ejecutar
cualquier comando en el sistema. Por ello, incorpora capas de protección
adicionales:

- **Confirmación humana obligatoria**: su modo es `MODE_EXECUTION`, y
  siempre requiere autorización explícita, independientemente de otras
  políticas.

- **Sandboxing con firejail**: si el sistema tiene instalado `firejail`
  y `enable_firejail` está activo, el comando se envuelve en un entorno
  restringido. El directorio home del agente se aísla, el acceso al
  sistema de archivos se limita a una lista blanca (el workspace y poco
  más), y se bloquean ciertas capacidades de red. La herramienta detecta
  automáticamente si `firejail` está disponible y muestra una advertencia
  si no lo está.

- **Timeout y control de procesos**: el comando se lanza en un proceso
  separado y se supervisa. Cada 30 segundos se pregunta al usuario si
  desea continuar esperando, permitiéndole abortar comandos que se
  eternicen.

- **Captura de salida**: la salida estándar y de error se redirigen a un
  archivo temporal en `var/tmp`. La salida se sirve paginada mediante
  `AbstractPaginatedAgentTool`, evitando saturar la ventana de contexto
  del LLM.

- **Desactivación global**: el usuario puede prohibir completamente la
  ejecución de comandos mediante `allow_shell_execution: false`. En ese
  caso, la herramienta ni siquiera aparecerá en el catálogo de
  capacidades del agente.

Estas medidas reducen drásticamente el riesgo de que un comando malicioso
o erróneo cause daños. No obstante, la responsabilidad última sigue
recayendo en el usuario, que debe autorizar cada ejecución
conscientemente.

### 7. Recarga en caliente y listas dinámicas

La configuración de seguridad puede modificarse sin reiniciar el agente
gracias a la acción `RELOAD_ACCESS_CONTROL`. Cuando el usuario cambia
alguna de las listas (rutas blancas, rutas prohibidas, flags booleanos)
en `settings.json`, puede ejecutar esta acción desde el menú de
depuración. `AgentAccessControlImpl` vuelve a leer todas las propiedades y
actualiza sus estructuras internas (listas de rutas, flags). Esto
permite, por ejemplo, autorizar temporalmente la escritura en disco para
una tarea específica y revocarla después, todo ello sin detener al
agente.

Las listas se gestionan mediante `AgentSettingsPaths` y
`AgentSettingsCheckedList`, lo que facilita su edición desde la interfaz
gráfica de configuración. El usuario puede añadir o eliminar rutas
externas permitidas, marcar directorios como de solo lectura, o
establecer exclusiones completas, todo desde una UI amigable.

### 8. Integración con el subsistema de herramientas

`AgentAccessControl` no actúa de forma aislada; está integrado en los
puntos críticos del flujo de ejecución:

- **En `ReasoningService`**: antes de ejecutar cualquier herramienta, se
  invoca a `accessControl.isToolAllowed(tool)`. Si devuelve `false`, la
  herramienta se considera no disponible (no se ofrece al modelo) o su
  ejecución se deniega con un mensaje de error.

- **En `AbstractAgentTool` y sus descendientes**: los métodos
  `resolvePathOrNull()` y `resolvePath()` utilizan
  `agent.getAccessControl()` para validar cada acceso a archivo. De este
  modo, todas las herramientas de lectura/escritura comparten la misma
  política de sandbox.

- **En `AgentPaths`**: aunque no depende directamente de
  `AgentAccessControl`, la raíz del workspace (`getWorkspaceFolder()`) es
  el punto de partida para la resolución de rutas. Ambas clases colaboran
  estrechamente.

- **En `ShellExecuteTool`**: además de consultar `isToolAllowed()`,
  verifica `isFirejailEnabled()` y utiliza `getSandboxHomeFolder()` para
  configurar el entorno aislado.

Esta integración garantiza que no haya ningún "camino secreto" para
eludir los controles de seguridad. Cada operación de lectura, escritura
o ejecución pasa por el guardián.

### 9. Limitaciones y decisiones deliberadas

El sistema de seguridad de Noema no pretende ser infranqueable ni
adecuado para entornos multiusuario. Está diseñado para un agente de
escritorio que opera en una sola máquina bajo la supervisión directa del
usuario. Por ello, presenta limitaciones asumidas:

- **Sin control de acceso basado en roles**: no hay distinción entre
  distintos tipos de usuarios (administrador, invitado). Solo existe el
  usuario que ejecuta el agente.

- **Sin sandboxing a nivel de red**: la política `allow_internet_access`
  es un veto global. No se pueden permitir ciertos dominios y denegar
  otros, ni restringir por puertos o protocolos.

FIXME: Repasar lo del sandboxing a nivel de red, no tengo claro si es
correcto.

- **Dependencia de `firejail` externo**: el sandbox de comandos solo
  funciona si `firejail` está instalado en el sistema. Noema no
  proporciona su propio contenedor ni mecanismos de aislamiento más
  ligeros.

- **Confirmación humana bloqueante**: no hay timeout. Si el usuario se
  ausenta, el agente quedará detenido indefinidamente esperando
  respuesta. Esto puede ser problemático en tareas automáticas que
  requieran supervisión.

- **Protección limitada contra ataques de inyección**: si el LLM recibe
  un prompt malicioso que le hace invocar herramientas con argumentos
  peligrosos, el filtro de rutas y la confirmación humana pueden
  detenerlo, pero no hay análisis semántico de los argumentos (por
  ejemplo, detectar `rm -rf /`).

Estas limitaciones son aceptables para un prototipo de investigación. En
un escenario de producción o de alta seguridad se requerirían medidas
adicionales (como listas de comandos permitidos, análisis heurístico de
la salida del modelo o ejecución en contenedores completos).

### 10. Conclusión

`AgentAccessControl` no es un sistema de seguridad industrial, pero
proporciona las barreras necesarias para prevenir daños accidentales y
mantener al usuario en control. Su diseño combina tres principios
fundamentales:

- **Declaración explícita del peligro**: cada herramienta etiqueta su
  modo, y el sistema aplica políticas coherentes.
- **Defensa en profundidad**: sandbox de archivos + confirmación
  humana + backups automáticos + sandbox de comandos.
- **Transparencia y control**: el usuario puede inspeccionar y modificar
  todas las políticas en caliente, y es consultado antes de cualquier
  acción irreversible.

Gracias a este diseño, Noema puede ofrecer capacidades avanzadas
(escritura de archivos, ejecución de comandos) sin generar una sensación
de inseguridad constante. El usuario sabe que, en última instancia, la
decisión es suya. Y si algo sale mal, los backups RCS permiten deshacer
el cambio. Es un equilibrio pragmático que refleja bien la filosofía
general del proyecto: **autonomía con supervisión, poder con
responsabilidad**.

## Servicio de Planificación (`SchedulerService`)

### 1. Introducción: la necesidad de planificación temporal

Uno de los rasgos distintivos de un agente autónomo es su capacidad para
*actuar en el futuro*. Noema no solo reacciona a estímulos inmediatos;
también puede programar recordatorios, alarmas o ejecuciones diferidas
como respuesta a una instrucción del usuario: *“Avísame dentro de diez
minutos”*, *“Recuérdame revisar el correo a las cinco”*, o incluso
*“Ejecuta este script mañana a primera hora”*.

Para satisfacer esta necesidad, Noema incorpora `SchedulerService`, un
componente ligero pero persistente que permite al agente registrar
eventos temporales y garantizar que se disparen en el momento preciso,
incluso si la aplicación se detiene y se reinicia entre la programación y
el disparo. El servicio se apoya en una base de datos H2 embebida para
almacenar las alarmas pendientes y en `SensorsService` para inyectar el
aviso como un evento más dentro del flujo de percepción del agente.

El diseño busca el mínimo indispensable: no hay dependencias externas
(como servicios de cron del sistema operativo), y la precisión es la
suficiente para un asistente conversacional (del orden de segundos). La
simplicidad es la clave.

### 2. Arquitectura general: componentes y flujo

El `SchedulerService` se compone de cuatro elementos fundamentales:

- **`SchedulerServiceImpl`**: la implementación concreta del servicio.
  Gestiona el ciclo de vida, la persistencia y el hilo de ejecución.
- **Tabla `SCHEDULER`** (H2): almacén de las alarmas pendientes. Cada fila
  contiene un identificador único (`id`), la marca de tiempo de creación
  (`timestamp`), el momento programado (`alarm_time`) y un texto
  descriptivo (`reason`).
- **`ScheduledExecutorService`**: un planificador de Java (un solo hilo)
  que ejecuta la tarea de disparo en el momento exacto.
- **`SensorsService`**: destino final de la alarma. Cuando se alcanza el
  tiempo, se invoca `agent.putEvent()` para inyectar un evento en el bus
  sensorial del agente.

El flujo típico es:
1. El LLM, mediante la herramienta `schedule_alarm`, solicita programar
   una alarma.
2. `SchedulerService` parsea la fecha, guarda la alarma en la base de
   datos y reprograma la próxima tarea pendiente.
3. Cuando llega el momento, el `ScheduledExecutorService` ejecuta el
   código que genera un evento y lo envía a `SensorsService`.
4. El agente, en su bucle de razonamiento, recibirá ese evento como un
   estímulo más y actuará en consecuencia (por ejemplo, enviando un
   mensaje al usuario).

Este diseño desacopla la planificación de la reacción: `SchedulerService`
solo sabe cuándo y qué notificar, pero no cómo responder. La respuesta
final queda delegada al `ReasoningService` y al modelo de lenguaje.

### 3. Persistencia: la tabla `SCHEDULER` y el contador de IDs

Las alarmas se almacenan en la base de datos H2 de servicios (la misma
que usa el agente para otros fines, como el registro de documentos). La
tabla se crea durante el arranque del servicio mediante la sentencia
SQL:

```sql
CREATE TABLE IF NOT EXISTS SCHEDULER (
    id VARCHAR(255) PRIMARY KEY,
    timestamp TIMESTAMP,
    alarm_time TIMESTAMP,
    reason VARCHAR(1024)
);
```

Cada alarma recibe un identificador único con el formato `ALARM-<num>`,
donde `<num>` es un entero autoincremental gestionado por la clase
`Counter`. Este contador, al iniciarse, consulta el valor máximo de `id`
en la tabla y arranca desde ahí. Por ejemplo, si ya existen alarmas con
id `ALARM-1`, `ALARM-2`, el siguiente será `ALARM-3`.

La columna `alarm_time` almacena el momento exacto (con precisión de
milisegundo) en que debe dispararse la alarma. La columna `reason`
contiene una descripción textual (la razón que proporcionó el agente al
programarla) y se incluirá en el evento sensorial cuando llegue el
momento.

### 4. Ciclo de vida del servicio: inicio, parada y resincronización

El servicio se inicia junto con el agente, siempre que su fábrica
(`SchedulerServiceFactory`) lo permita (actualmente siempre devuelve
`true`, pues no requiere configuración externa). El método `start()`
realiza las siguientes operaciones:

1. Crea un `ScheduledExecutorService` de un solo hilo (de plataforma, no
   virtual, por razones de estabilidad).
2. Registra un nuevo sensor en `SensorsService` con el nombre
   `SCHEDULER`, naturaleza `DISCRETE` (cada alarma se entrega como un
   evento atómico).
3. Conecta a la base de datos H2 de servicios y crea la tabla `SCHEDULER`
   si no existe.
4. Inicializa el contador de IDs (leyendo el máximo id actual).
5. Invoca `rescheduleNextAlarm()` para recuperar la alarma más próxima
   (si existe) y programar su ejecución.
6. Marca el servicio como `running`.

Cuando el agente se detiene, se invoca `stop()`: se cancela la tarea
futura actual (`currentScheduledTask.cancel(false)`) y se pone el flag
`running` a `false`. No se borran las alarmas pendientes, de modo que al
reiniciar el agente se recuperarán automáticamente.

La resincronización al arranque es especialmente importante: si el agente
estaba apagado durante el momento en que debía dispararse una alarma,
`rescheduleNextAlarm()` seleccionará la siguiente alarma futura. Las que
ya vencieron mientras el agente no estaba activo no se ejecutarán (se
consideran perdidas). Para evitar esta pérdida, se podría modificar el
servicio para que, al arrancar, ejecute inmediatamente todas las alarmas
con `alarm_time` anterior a `now`, pero el diseño actual asume que el
agente no permanece detenido mucho tiempo o que el usuario prefiere no
recibir notificaciones atrasadas.

### 5. Programación de una alarma: la herramienta `schedule_alarm`

El LLM accede a la planificación a través de la herramienta
`schedule_alarm`. Su especificación incluye dos parámetros:

- `reason` (texto obligatorio): la descripción de la alarma (ej:
  "Revisar correo").
- `when` (texto obligatorio): descripción temporal en **inglés** (ej:
  "tomorrow at 5pm", "in 10 minutes").

**¿Por qué inglés?** El parser de fechas utilizado es Natty, una librería
Java que entiende expresiones flexibles pero solo de forma fiable en
inglés. Por tanto, se pide al modelo que, si recibe una instrucción en
español, traduzca la expresión temporal al inglés antes de invocar la
herramienta. Esto es una limitación asumida; en el futuro podría
reemplazarse por un parser más polivalente.

La implementación de la herramienta (`ScheduleAlarmTool.execute()`)
realiza los siguientes pasos:

1. Extrae los argumentos JSON.
2. Invoca `dateParser.parse(when)` para obtener una lista de fechas
   candidatas.
3. Toma la primera fecha detectada y la convierte a `LocalDateTime` (zona
   horaria del sistema).
4. Llama a `SchedulerService.schedule(alarmLDT, reason)`.
5. Devuelve una respuesta JSON confirmando la programación e incluyendo el
   `id` generado y la hora exacta interpretada.

Ejemplo de respuesta exitosa:

```json
{
  "status": "scheduled",
  "id": "ALARM-42",
  "reason": "Revisar correo",
  "alarm_time": "2025-05-20T17:00:00",
  "note": "Alarma programada en el sistema."
}
```

Si el parseo falla (ej: "en algún momento"), se devuelve un error y el
agente debe pedir al usuario que reformule la fecha.

### 6. El bucle de planificación: `rescheduleNextAlarm()` y `schedule_alarm()` interno

El corazón del servicio es el método `rescheduleNextAlarm()`. Su lógica
es:

```java
private void rescheduleNextAlarm() {
    if (currentScheduledTask != null && !currentScheduledTask.isDone()) {
        currentScheduledTask.cancel(false);
    }
    // SELECT id, reason, alarm_time FROM SCHEDULER
    // WHERE alarm_time > now ORDER BY alarm_time LIMIT 1
    // Si existe, calcular delay = alarm_time - now
    // schedule_alarm(id, reason, alarmTime)
}
```

Este método se invoca:
- En `start()`, para recuperar la alarma más próxima existente
  (resincronización).
- Después de **insertar** una nueva alarma (en `schedule()`).
- Después de **eliminar** una alarma ya disparada (en el callback de la
  tarea).

El método `schedule_alarm` (privado, no confundir con la herramienta) es
quien realmente programa la tarea en el `ScheduledExecutorService`:

```java
long delay = Duration.between(LocalDateTime.now(), alarmTime).toMillis();
if (delay < 0) delay = 0;

currentScheduledTask = scheduler.schedule(() -> {
    sendEvent(alarmTime, reason);
    removeAlarm(id);
    rescheduleNextAlarm();
}, delay, TimeUnit.MILLISECONDS);
```

Cuando la tarea se ejecuta, envía el evento, borra la alarma de la base
de datos y vuelve a programar la siguiente (si existe). Nótese que el
`rescheduleNextAlarm()` también cancela la tarea actual antes de programar
la nueva, garantizando que solo haya una tarea pendiente en todo momento.

### 7. Disparo de la alarma: generación del evento sensorial

El método `sendEvent` construye un objeto JSON con la información de la
alarma y lo inyecta en el `SensorsService` mediante `agent.putEvent()`:

```java
String notify = gson.toJson(Map.of(
    "alarm_time", when.toString(),
    "reason", reason
));
agent.putEvent(SENSOR_NAME, "ALARM TRIGGERED", PRIORITY_NORMAL, notify);
```

El `SensorsService`, a su vez, transforma esta llamada en un
`SensorEventDiscrete` (porque la naturaleza del sensor `SCHEDULER` es
`DISCRETE`) y lo encola para que el `ReasoningService` lo recoja en su
próximo ciclo. Cuando el agente recibe el evento, el campo `contents`
contendrá el JSON anterior, que el LLM podrá interpretar.

De esta forma, el agente puede reaccionar a la alarma con total
naturalidad: puede enviar un mensaje al usuario, ejecutar una tarea, o
incluso programar otra alarma. La lógica de *qué hacer* queda
completamente delegada al modelo.

### 8. Eliminación y limpieza de alarmas

Una vez que una alarma se ha disparado, se elimina de la base de datos
mediante `removeAlarm(id)`. Esto evita que vuelva a ejecutarse en futuros
reinicios. No hay actualmente una herramienta que permita al LLM cancelar
una alarma programada (aunque podría añadirse fácilmente con una nueva
herramienta que ejecute un `DELETE FROM SCHEDULER WHERE id = ...`).

Si el agente se detiene antes de que se dispare una alarma, la tarea
pendiente se pierde (la `ScheduledFuture` no se serializa), pero la
alarma sigue en la base de datos. Al reiniciar, `rescheduleNextAlarm()` la
recuperará y la reprogramará con el tiempo restante (la diferencia entre
la hora actual y `alarm_time`). Esto garantiza la persistencia a largo
plazo.

### 9. Concurrencia y diseño de hilos

El servicio utiliza un `ScheduledExecutorService` con un solo hilo
(creado mediante `Executors.newSingleThreadScheduledExecutor()`).
Originalmente se probó con hilos virtuales (`Thread.ofVirtual().factory()`),
pero se revirtió a hilos de plataforma por problemas de estabilidad en
tiempo de depuración. Dado que solo hay una tarea de planificación activa
a la vez y el trabajo dentro de la tarea (enviar evento y borrar de BD)
es mínimo, la diferencia de rendimiento es irrelevante.

El único punto delicado es la cancelación de la tarea actual cuando se
programa una nueva alarma más cercana. Como `rescheduleNextAlarm()` y
`schedule` se ejecutan en el hilo del agente (normalmente el
`eventDispatcher`), y la tarea programada se ejecuta en el hilo del
`ScheduledExecutorService`, no hay condiciones de carrera entre la
cancelación y la ejecución porque la cancelación se hace siempre antes de
crear la nueva tarea, y el `scheduler` está diseñado para que `cancel()`
no interrumpa una tarea que ya ha comenzado (se usa
`mayInterruptIfRunning = false`).

### 10. Limitaciones y posibles mejoras

El `SchedulerService` cumple su cometido básico, pero adolece de varias
limitaciones que sería deseable abordar en versiones futuras:

- **Sin recurrencia**: las alarmas son únicas. No se pueden programar
  eventos periódicos ("cada día a las 8:00") ni basados en cron. Tampoco
  hay soporte para cancelación o modificación de una alarma existente.
- **Solo inglés en `when`**: Natty funciona bien en inglés, pero no
  entiende español ni otros idiomas. El modelo debe traducir, lo que
  añade un paso y riesgo de error.
- **Precisión limitada**: el `ScheduledExecutorService` de Java está
  sujeto a la granularidad del temporizador del sistema operativo
  (normalmente milisegundos, pero en sistemas cargados puede haber
  desviaciones de varios segundos). Para recordatorios conversacionales
  es suficiente; para tareas de milisegundo no.
- **Sin interfaz de usuario**: no hay forma de listar las alarmas
  pendientes desde la UI ni de cancelarlas. El usuario depende de que el
  agente recuerde lo que programó.
- **Notificaciones perdidas si el agente está apagado**: si el agente se
  detiene justo cuando debía dispararse una alarma, al reiniciar esa
  alarma ya ha vencido y se pierde. Una mejora sería ejecutar al arranque
  todas las alarmas vencidas (quizá con un límite de tiempo).
- **Dependencia de la base de datos H2**: actualmente sí, pero es una
  dependencia ligera y embebida. No se prevé cambiar.

A pesar de estas carencias, el servicio es funcional y suficiente para
demostrar la capacidad de planificación temporal de Noema. La mayoría de
las mejoras (recurrencias, cancelación, mejor parser de fechas) pueden
añadirse sin romper la arquitectura existente.


# Inicialización e inyección de dependencias

### 1. Filosofía de Ensamblaje: Inyección de dependencias manual y
ausencia de frameworks

A diferencia de las aplicaciones empresariales típicas en Java, Noema
**no utiliza frameworks de inyección de dependencias (DI)** como Spring
Boot o CDI. Esta es una decisión arquitectónica deliberada que busca
maximizar la transparencia, el pragmatismo y la facilidad de depuración.

En proyectos donde el ciclo de vida de los componentes es complejo y
secuencial (arrancar la base de datos antes de cargar el historial,
registrar herramientas antes de despertar al LLM), la "magia" de la
inyección por reflexión puede oscurecer el flujo real de ejecución. En
Noema, si quieres saber cuándo y cómo se instancia un servicio, basta
con hacer `Ctrl+Click` en tu IDE. 

El sistema utiliza un patrón híbrido:
1. **Inyección por Constructor:** Los componentes principales reciben
   sus dependencias vitales (como `AgentSettings` o `AgentPaths`)
   directamente en el constructor.
2. **Service Locator Localizado:** La clase `Agent` actúa como el
   contexto central. Las herramientas y acciones reciben la instancia de
   `Agent` y, a través de ella, solicitan los servicios específicos que
   necesitan mediante `agent.getService("NombreDelServicio")`.

### 2. Puntos de Entrada y Selección de Entorno (`Main`, `MainGUI`,
`MainConsole`)

El ciclo de vida de la aplicación comienza de forma muy limpia. La clase
`Main` actúa como un simple proxy de enrutamiento que lee los
argumentos de la línea de comandos (específicamente `-c`) para decidir
qué entorno de presentación levantar:

*   **`MainGUI` (Entorno Gráfico):** Inicializa el *Look & Feel*
    (FlatLaf oscuro) y levanta el `WelcomePanel`. Este panel es crítico
    porque fuerza al usuario a seleccionar una carpeta de trabajo
    (Workspace) antes de que el agente exista. Una vez seleccionado y
    validada la configuración básica, se instancia la interfaz de chat y
    se lanza la creación del agente de forma asíncrona
    (`Thread.ofPlatform()`).
*   **`MainConsole` (Entorno Terminal):** Inicializa un entorno REPL
    rico usando **JLine3** (soportando autocompletado y atajos de
    teclado). Si detecta que la configuración del workspace es inválida
    o faltan parámetros críticos, levanta el diálogo de configuración
    Swing de forma excepcional antes de iniciar el agente.

En ambos casos, la inicialización de la interfaz de usuario precede a la
inicialización del "cerebro" del agente.

### 3. El Registro de Componentes: `AgentLocator` y `AgentManager`

Para que el agente sepa de qué piezas dispone sin recurrir al escaneo de
classpath (classpath scanning), existe un catálogo estricto y
centralizado.

*   `AgentLocator`: Es el único Singleton estático real del sistema.
    Expone el acceso global al `AgentManager`.
*   `AgentManagerImpl`: Es el "catálogo maestro" de Noema. En su
    constructor, se registran manualmente y en orden todas las **fábricas
    de servicios** (`AgentServiceFactory`) del ecosistema:
    `EmbeddingsServiceFactory`, `SensorsServiceFactory`,
    `ReasoningServiceFactory`, `MemoryServiceFactory`, etc. 

También actúa como factoría principal de configuraciones, bases de datos
y la instancia base del `Agent`. Si un servicio no está registrado en el
`AgentManagerImpl`, simplemente no existe en el universo de Noema.

### 4. Bootstrapping del Núcleo: La clase `BootUtils`

Antes de que el agente pueda razonar, necesita un entorno físico
preparado. La clase `BootUtils` encapsula este trabajo "sucio" de
fontanería a través de su método `init(AgentSettings settings)`:

1.  **Configuración de Logs:** Lee la ruta de logs del `AgentPaths` y
    reconfigura Log4j2 en caliente (`Configurator.reconfigure()`) para
    que los volcados vayan a `var/log/noema-agente.log` dentro del
    workspace seleccionado.
2.  **Arranque del Servidor H2:** Escribe dinámicamente un archivo
    `.h2.server.properties` y levanta el servidor web embebido de H2.
    Esto expone las bases de datos en un puerto (por defecto 8082) para
    permitir la inspección en tiempo real.
3.  **Conexiones a Base de Datos:** Crea las dos instancias de
    `ConnectionSupplier` que abstraen las URLs JDBC de las dos bases de
    datos (Memoria y Servicios) forzando el parámetro
    `AUTO_SERVER=TRUE`.
4.  **Instanciación:** Llama a `AgentManager.createAgent(...)` pasando
    las conexiones listas, la configuración y el manejador de la
    consola.

El resultado de `BootUtils.init()` es un objeto `Agent` completamente
ensamblado, pero **dormido**.

### 5. Ensamblaje e Inicialización: `AgentImpl.start()`

El despertar del agente ocurre cuando se invoca `agent.start()`. Este
método orquesta una coreografía muy específica para garantizar que no
haya condiciones de carrera durante el inicio:

1.  **Creación de Servicios:** Itera sobre todas las fábricas
    registradas en el `AgentManager` y crea las instancias de los
    servicios (aún sin iniciarlos).
2.  **Registro de Sensores Base:** Instancia y registra manualmente el
    sensor primario `USER` (el canal de interacción humana) dentro del
    `SensorsService`.
3.  **Extracción de Herramientas:** Recorre los servicios evaluando si
    pueden arrancar en base a la configuración (`canStart()`). A los que
    sí pueden, les pide su catálogo de herramientas (`getTools()`) y se
    las inyecta al `ReasoningService`.
4.  **Encendido Global:** Llama al método `start()` de cada servicio
    habilitado (el `Scheduler` lee la BBDD, el `Reasoning` levanta el
    hilo del despachador de eventos, etc.).
5.  **Shutdown Hook:** Como último paso, registra un *Hook* de apagado en
    la JVM (`Runtime.getRuntime().addShutdownHook`) que garantiza que,
    ante un cierre brusco (Ctrl+C), se llame a `agent.stop()`,
    permitiendo al orquestador guardar la memoria volátil a disco y
    cerrar la base de datos limpiamente.

### 6. Receta Práctica: Cómo añadir un nuevo servicio al Agente

Gracias a este diseño determinista, extender el agente con un nuevo
servicio o integración requiere pasos explícitos y rastreables. Si deseas
añadir, por ejemplo, un `SpotifyService`, este es el checklist:

1.  **El Contrato:** Crea la interfaz `SpotifyService` (que extienda de
    `AgentService`) en `io.github.jjdelcerro.noema.lib`.
2.  **La Implementación:** Crea `SpotifyServiceImpl` en el paquete de
    implementación. Aquí definirás su `start()`, `stop()`, y sus
    herramientas `getTools()` (ej. `SpotifyPlayTool`).
3.  **La Factoría:** Crea `SpotifyServiceFactory` implementando
    `AgentServiceFactory`. En su método `canStart(AgentSettings settings)`
    debes validar si el usuario ha configurado las API Keys necesarias
    en `settings.json`.
4.  **El Registro (El Wiring real):** Abre `AgentManagerImpl.java` y, en
    su constructor, añade la línea: 
    `this.registerService(new SpotifyServiceFactory());`
5.  **Consumo:** A partir de ahora, cualquier otra herramienta o
    componente puede acceder a tu servicio haciendo:
    `SpotifyService spotify = (SpotifyService)`
    `agent.getService(SpotifyService.NAME);`

# Gestión de Rutas (AgentPaths)

## 1. Introducción: el sistema de archivos como estado

Frente a la tendencia habitual de externalizar toda la persistencia en
bases de datos o servicios en la nube, Noema adopta una postura
deliberadamente minimalista y transparente: **el sistema de archivos
local es el depositario principal del estado del agente**. Todos los
componentes —configuración, memoria conversacional, índices vectoriales,
logs, cachés y hasta los resúmenes narrativos (CheckPoints)— se almacenan
como archivos planos o bases de datos embebidas (H2) dentro de un
directorio sandbox. El resultado es un agente que se puede inspeccionar,
respaldar y depurar con las herramientas estándar del sistema operativo
(editores de texto, `grep`, `diff`, control de versiones), sin necesidad
de consolas de administración propietarias.

El corazón de esta arquitectura es la clase `AgentPaths`, que actúa como
un **sistema de coordenadas** para todo el sandbox. Define una jerarquía
bien conocida de carpetas bajo el workspace elegido por el usuario
(normalmente `.noema-agent`) y, de forma complementaria, una ruta de
configuración global en el directorio `~/.config/noema-agent`. Esta
dualidad permite que el agente pueda ejecutarse en modo portátil (toda la
configuración dentro del proyecto) o en modo usuario (compartiendo ajustes
globales entre distintos workspaces), según lo que se necesite en cada
momento.

El diseño asume una premisa fuerte: **el sistema de archivos es confiable
y está disponible**. Noema no abstrae el acceso a disco detrás de una capa
de virtualización compleja; simplemente lo acepta como una fuente de
verdad sincrónica, bloqueante y determinista. Esto simplifica
drásticamente el modelo de persistencia —no hay transacciones
distribuidas, ni bases de datos vectoriales dedicadas, ni servicios de
caché externos— y resulta sorprendentemente adecuado para un agente de
escritorio que opera en una única línea temporal continua. Cualquier
efecto colateral (un archivo que no se puede escribir, una carpeta que no
existe, un permiso denegado) se propaga inmediatamente al sistema de
control de acceso, que decidirá si la operación puede continuar o debe
abortar.

En las secciones siguientes se desglosa cómo `AgentPaths` organiza este
universo de archivos, cómo resuelve rutas entre el workspace y la
configuración global, y cómo se integra con el resto de servicios del
agente para ofrecer una experiencia coherente y depurable.

## 2. La topología del sandbox: carpetas clave

El sandbox de Noema se organiza bajo dos grandes raíces: la **carpeta de
trabajo** (workspace) elegida por el usuario y, complementariamente, la
**carpeta de configuración global** en el directorio personal
(`~/.config/noema-agent`). Dentro de la primera se crea una subcarpeta
oculta llamada `.noema-agent` que contiene toda la estructura operativa
del agente. `AgentPaths` proporciona métodos específicos para acceder a
cada una de las ubicaciones que forman esta topología:

*   **`getWorkspaceFolder()`**: la raíz del proyecto o espacio de
    trabajo. Sobre esta ruta se construye todo el sandbox. Si el usuario
    no selecciona ninguna, se usa el directorio actual de ejecución.

*   **`getAgentFolder()`**: la carpeta `.noema-agent` dentro del
    workspace. Es el punto de entrada real del agente y la base para
    todas las subcarpetas siguientes.

*   **`getConfigFolder()`** (`var/config`): almacena la configuración del
    agente en formato JSON (`settings.json`), los archivos de propiedades
    de proveedores LLM, los prompts del sistema (directorio `prompts/`) y
    la definición de la interfaz de usuario (`settingsui.json`). El
    agente despliega aquí sus recursos por primera vez mediante
    `installResource()`.

*   **`getDataFolder()`** (`var/lib`): es el repositorio central del
    estado persistente. Contiene las bases de datos embebidas H2
    (`memory.mv.db` para los turnos y checkpoints, `service.mv.db` para
    el planificador y otros servicios), el archivo `active_session.json`
    (volcado de la conversación activa) y la subcarpeta `checkpoints/`,
    donde se guardan los resúmenes narrativos generados por
    `MemoryService` (archivos `.md`). Aquí reside, en definitiva, la
    memoria a largo plazo del agente.

*   **`getCacheFolder()`** (`var/cache`): almacena datos derivados que
    pueden regenerarse si es necesario, pero que se conservan para
    ahorrar tiempo de cómputo. Por ejemplo, los textos extraídos de
    documentos PDF/DOCX por `file_extract_text` se guardan aquí con un
    hash del archivo original. También se alojan las estructuras JSON de
    documentos mapeados (`.struct`). La caché es segura de eliminar sin
    pérdida de funcionalidad.

*   **`getTempFolder()`** (`var/tmp`): alberga archivos temporales y
    volátiles, como las salidas paginadas de comandos (`shell_execute`),
    los resultados intermedios de búsquedas web, los volcados de
    contexto para depuración (`last_context.json`) y otros recursos que
    el agente necesita para fragmentar respuestas largas. A diferencia de
    la caché, estos archivos tienen una vida útil corta y se limpian
    periódicamente.

*   **`getLogFolder()`** (`var/log`): contiene los archivos de log
    generados por Log4j2 (`noema-agente.log` y sus rotaciones). Es la
    primera parada para diagnosticar problemas de ejecución, errores de
    API o fallos inesperados en los hilos del agente.

*   **`getSandboxHomeFolder()`** (`var/home`): un directorio que actúa
    como **home virtual** para el agente cuando ejecuta comandos de shell
    a través de `firejail`. Permite aislar el sistema de archivos real
    del agente, ofreciendo un entorno controlado y seguro para scripts
    que no deberían acceder a la configuración o las bases de datos.

*   **`getGlobalConfigFolder()`** (`~/.config/noema-agent`): complementa
    al workspace. Almacena los mismos tipos de recursos que
    `getConfigFolder()` (prompts, identidad, habilidades), pero
    compartidos entre distintos proyectos. Si un recurso no existe en el
    workspace, el agente lo busca aquí, lo que permite centralizar
    configuraciones comunes sin duplicarlas.

Cada uno de estos accesos devuelve un objeto `Path` absoluto y
normalizado. La creación de toda la jerarquía se realiza con un único
método `setupHierarchy()`, que garantiza que todos los directorios
existan antes de que el agente comience a funcionar. Esta estructura plana
y predecible es la base sobre la que se construyen servicios como
`SourceOfTruth` (que escribe en `var/lib`), `SensorsService` (que
persiste su estado en `sensors.json` dentro de `var/lib`) o las
herramientas de archivo (`file_read`, `file_write`), que operan siempre
dentro de los límites de este sandbox a menos que se autoricen rutas
externas explícitamente.

## 3. El ciclo de vida del espacio de trabajo

El espacio de trabajo no es una entidad estática; nace, se configura y se
mantiene a lo largo de la vida del agente mediante un protocolo explícito
gobernado por `AgentPaths`. Todo comienza cuando el usuario selecciona una
carpeta raíz (workspace) en el diálogo de bienvenida o desde la
configuración. Con esa ruta, `AgentManager` crea una instancia de
`AgentPaths` y, a continuación, invoca el método `setupHierarchy()`.

La responsabilidad de `setupHierarchy()` es doble. Primero, **crea
físicamente la estructura de directorios** (`.noema-agent/var/config`,
`var/lib`, `var/cache`, `var/tmp`, `var/log`, `home`) utilizando
`Files.createDirectories()`. Esta operación es idempotente: si las
carpetas ya existen, no hace nada; si faltan, las genera con los permisos
por defecto del sistema. Segundo, **establece el punto de anclaje** para
que el resto del agente pueda referirse a estas rutas sin necesidad de
conocer la ubicación concreta del workspace. Este momento de “toma de
tierra” ocurre antes de que se cargue cualquier servicio o se persista
ningún estado.

Una vez establecida la jerarquía, el workspace se considera **inmutable en
cuanto a su topología** durante toda la ejecución del agente. Ningún
servicio puede añadir nuevos directorios raíz o cambiar el destino de las
carpetas existentes. Lo que sí puede cambiar es el contenido dentro de
ellas: los servicios escriben y leen archivos, el agente rota logs, el
planificador actualiza su base de datos, etc. Pero la estructura básica
—dónde está la configuración, dónde la memoria, dónde los temporales—
permanece fija. Esta rigidez es deliberada: simplifica el modelo de
persistencia y evita que el agente pierda el rastro de sus propios datos.

El ciclo de vida termina cuando el agente se detiene. En ese momento no se
destruye el workspace; al contrario, se preserva íntegro para la próxima
ejecución. El único acto de limpieza que realiza `AgentPaths` es la
eliminación opcional de archivos temporales muy antiguos (gestionada por
las herramientas que los crean, no por el propio paths). Al reiniciar,
`setupHierarchy()` vuelve a ejecutarse, encuentra todo ya creado y
continúa. La persistencia del workspace es, por tanto, el mecanismo que
permite la **continuidad entre sesiones**: el agente despierta en el mismo
estado de archivos que dejó al dormir, con sus turnos, checkpoints,
configuración y logs intactos.

Finalmente, cabe destacar que el workspace puede ser **relocalizado** en
cualquier momento. Si el usuario cierra la aplicación, mueve la carpeta a
otra ubicación y vuelve a abrir Noema seleccionando la nueva ruta, el
agente reanudará su actividad sin pérdida de información. Esto es posible
porque todas las rutas se resuelven siempre desde el workspace activo, y no
hay referencias absolutas embebidas en los archivos de estado (las bases de
datos H2 utilizan rutas relativas al archivo `.mv.db`, y los checkpoints
referencian IDs numéricos, no rutas). Esta flexibilidad es una consecuencia
directa de la decisión de tratar el sistema de archivos como el estado
único y de no depender de servicios externos con localizaciones fijas.

## 4. Resolución de rutas: entre lo local y lo global

Uno de los problemas más molestos en aplicaciones que gestionan
configuración es decidir dónde almacenar los archivos: ¿junto al ejecutable
(portable) o en el directorio personal del usuario (global)? `AgentPaths`
resuelve esta tensión implementando una **resolución en dos niveles** que
combina lo mejor de ambos mundos sin necesidad de opciones de instalación.

El mecanismo es sencillo pero potente. Cuando el agente necesita localizar
un recurso (por ejemplo, un prompt del sistema, un módulo de identidad o un
fichero de configuración), consulta primero el workspace local
(`workspace/.noema-agent/...`). Si el archivo existe allí, lo utiliza. Si
no, recurre a la carpeta de configuración global
(`~/.config/noema-agent/...`). Este comportamiento se implementa en
métodos como `getAgentPath(String name)` y `getConfigPath(String name)`,
que devuelven la primera ubicación donde el recurso está presente.

¿Qué ventajas aporta esta estrategia?

- **Portabilidad por defecto**: si el usuario copia todo el workspace a
  otro equipo o a un pendrive, el agente sigue funcionando porque todos los
  recursos necesarios están autocontenidos. No hay dependencias ocultas en
  el sistema de archivos del usuario.

- **Reutilización de configuración global**: al mismo tiempo, el usuario
  puede mantener ajustes comunes (como listas de API keys, modelos
  preferidos o habilidades personalizadas) en su directorio
  `~/.config/noema-agent`. Estos estarán disponibles para cualquier
  workspace que no los sobrescriba localmente.

- **Actualización sin fricción**: al desplegar una nueva versión de Noema,
  los recursos preinstalados en el JAR se copian al workspace solo si no
  existen. Si el usuario ha modificado un prompt localmente, esa versión
  prevalece. Si no, el agente toma la versión global o la recién instalada.

El único método que rompe ligeramente esta regla es
`listAgentPath(String name)`, que devuelve la **unión** de los archivos
encontrados tanto en el workspace como en la configuración global. Esto es
útil para, por ejemplo, enumerar todas las habilidades disponibles
(`.ref.md`) sin importar dónde estén físicamente almacenadas.

Un detalle importante: esta resolución **no es recursiva**. No se busca en
subcarpetas del workspace si el recurso no existe localmente; la búsqueda
es directa y en un solo nivel. Si un recurso debe estar presente sí o sí
(como `settings.json` durante el arranque), el agente lo desplegará desde
el JAR a la ubicación local durante la fase de `setupSettings()`. En otros
casos, como los módulos de identidad o las habilidades, el agente asume
que o bien existen localmente, o bien no existen y no se mostrarán.

En la práctica, esta dualidad es invisible para el desarrollador de
herramientas y para el LLM: ambos trabajan con rutas relativas simples (ej:
`var/config/prompts/reasoning-system.md`), y `AgentPaths` se encarga de
resolverlas al primer punto donde el archivo esté accesible. El resultado
es un sistema de archivos virtual pero determinista, que permite tanto la
portabilidad total como la centralización de la configuración común.

## 5. Acceso a recursos de configuración e identidad

Más allá de proporcionar rutas a directorios genéricos, `AgentPaths`
facilita el acceso directo a los recursos que definen la personalidad y el
comportamiento del agente. Estos recursos se organizan en tres grandes
familias dentro de la jerarquía del sandbox:

**Configuración operativa (`var/config`)**  
Aquí residen los archivos que determinan cómo se conecta el agente a los
LLMs, qué herramientas están activas y cómo se comporta el sistema en
general. Los métodos `getConfigFolder()` y `getConfigPath(String name)`
permiten localizar ficheros como:
- `settings.json`: el corazón de la configuración jerárquica del agente.
- `models.properties`, `providers_urls.properties`,
  `providers_apikeys.properties`: dominios externos que alimentan los
  combos y listas de selección en la UI de configuración.
- `settingsui.json`: la definición de la interfaz de usuario de ajustes
  (árbol de menús y componentes).
- La subcarpeta `prompts/`, que contiene los archivos Markdown con las
  instrucciones del sistema (`reasoning-system.md`, `memory-compact.md`,
  etc.). Estos prompts se cargan en caliente y pueden editarse sin
  recompilar.

**Identidad y conocimiento del entorno (`var/identity`)**  
Esta carpeta almacena la "personalidad" y el conocimiento biográfico o
técnico del agente. Se divide en dos subdirectorios:
- `core/`: contiene la constitución operativa del agente (normas,
  metodologías, principios). Los archivos aquí se inyectan directamente en
  el prompt del sistema según los módulos que el usuario tenga activados
  en la configuración.
- `environ/`: alberga el conocimiento denso del entorno (biografía del
  usuario, proyectos, intereses). De forma ingeniosa, Noema no carga estos
  archivos completos en el prompt; en su lugar, utiliza archivos `.ref.md`
  ligeros que actúan como índices. Cuando el agente necesita información
  detallada de un módulo, invoca la herramienta `consult_environ`, que a
  través de `AgentPaths` localiza y carga el archivo `.md` correspondiente.

**Habilidades procedimentales (`var/skills`)**  
Análogamente al entorno, las habilidades siguen un patrón de carga bajo
demanda. En `skills/` coexisten:
- Archivos `.ref.md` (referencias ligeras) que describen el propósito de
  cada habilidad y son listados por la herramienta `list_skills`.
- Archivos `.md` completos que contienen el protocolo paso a paso, que se
  cargan mediante `load_skill` cuando el agente decide ejecutar esa
  capacidad.

`AgentPaths` expone dos métodos convenientes para trabajar con estos
recursos: `getAgentPath(String name)` (devuelve la primera ocurrencia del
recurso en la jerarquía local/global) y `listAgentPath(String name)`
(devuelve todos los recursos de una subcarpeta, fusionando las
contribuciones locales y globales). El método `getResourceAsString` de
`Agent` es el que realmente utiliza `AgentPaths` para leer el contenido de
estos archivos y devolverlo como texto, con la lógica adicional de
desplegar los recursos desde el JAR si no existen.

Este diseño permite una **personalización profunda** del agente sin
modificar el código fuente: el usuario puede añadir nuevos módulos de
identidad, crear nuevas habilidades o ajustar los prompts del sistema
simplemente creando o editando archivos en las carpetas correspondientes.
La separación entre índices ligeros y contenido denso, además, es clave
para la estrategia de gestión de contexto del LLM: el agente solo carga el
conocimiento que realmente necesita en cada turno, manteniendo el prompt
del sistema liviano.

## 6. Persistencia y seguridad del sandbox

`AgentPaths` no solo define dónde se guardan los archivos, sino que también
sienta las bases de un **modelo de persistencia seguro y predecible**. La
seguridad aquí opera en dos niveles: por un lado, la prevención de fugas
del sandbox (path traversal); por otro, la protección de archivos
sensibles frente a modificaciones accidentales o maliciosas del propio
agente.

**Aislamiento por raíz**: toda operación de lectura o escritura que realiza
cualquier herramienta del agente pasa por `AgentAccessControl`, que utiliza
la raíz del workspace (obtenida mediante `getWorkspaceFolder()`) como punto
de anclaje. Si una herramienta intenta acceder a una ruta que no está
dentro de esta raíz ni en las listas blancas explícitamente configuradas, el
acceso se deniega con una excepción de seguridad. `AgentPaths` no aplica
estas políticas por sí mismo, pero proporciona las coordenadas necesarias
para que el control de acceso pueda evaluarlas correctamente.

**Archivos especiales no modificables**: el método `resolvePath` de
`AgentAccessControl` incluye reglas adicionales sobre qué partes del
sandbox están protegidas. Por ejemplo, los archivos con extensión `,jv`
(las copias de respaldo generadas por el sistema RCS) son **de solo
lectura** para el agente. El LLM puede listarlos o leerlos, pero nunca
modificarlos ni eliminarlos directamente, preservando así la integridad
del historial de versiones. Del mismo modo, la carpeta
`.noema-agent/var/lib` es de solo escritura para la base de datos H2 y el
sistema de checkpoints, pero herramientas como `file_write` no pueden
sobrescribir esos ficheros porque están fuera de las rutas de trabajo
permitidas por defecto.

**Persistencia atómica**: aunque no es una responsabilidad directa de
`AgentPaths`, los servicios que escriben en el sandbox (como
`SourceOfTruth` al guardar `active_session.json` o `SensorsService` al
persistir `sensors.json`) utilizan un patrón de escritura atómica: primero
se escribe en un archivo temporal dentro de la misma carpeta
(`archivo.tmp`), y luego se renombra atómicamente al destino final.
`AgentPaths` facilita este patrón proporcionando métodos para obtener las
rutas de las carpetas `tempFolder` y `dataFolder`, de modo que los
temporales y los destinos compartan el mismo sistema de archivos y puedan
moverse de forma atómica.

**Limpieza y rotación**: la responsabilidad de limpiar archivos obsoletos
recae en los servicios que los crean, no en `AgentPaths`. Por ejemplo,
`ShellExecuteTool` registra sus salidas en un mapa LRU y elimina los
archivos `*.out` más antiguos cuando superan un límite configurable.
`WebGetTikaTool` almacena en caché los contenidos extraídos, pero confía en
que el usuario o un proceso externo pueda purgar `var/cache` si es
necesario. `AgentPaths` no implementa políticas de retención, pero
proporciona los medios para localizar estos archivos y, en el futuro,
podría incorporar un servicio de limpieza transversal.

**Consecuencias para el usuario**: esta aproximación convierte al sandbox
en una **cápsula autocontenida pero inspeccionable**. Si un usuario desea
respaldar todo el estado de Noema, le basta con copiar la carpeta
`.noema-agent` del workspace y, opcionalmente, la carpeta
`~/.config/noema-agent`. Si algo falla, puede examinar los logs en
`var/log`, revisar los checkpoints en `var/lib/checkpoints/*.md` o incluso
editar `active_session.json` para modificar la conversación en curso (con
el riesgo que ello conlleva). El agente no oculta su estado tras formatos
propietarios ni servicios remotos: todo está al alcance de un editor de
texto y de las herramientas UNIX clásicas. Esta transparencia es una de las
señas de identidad arquitectónicas de Noema.

## 7. Integración con el resto de servicios

`AgentPaths` no es un componente aislado; su verdadero valor se manifiesta
al ser inyectado en el resto del ecosistema de Noema. La instancia de
`AgentPaths` se crea en el momento del arranque (a través de
`AgentManager.createAgentPaths()`) y se asocia al objeto `AgentSettings`,
que a su vez es accesible desde el `Agent` central. Cualquier servicio o
herramienta que necesite conocer una ruta absoluta del sandbox obtiene
primero la referencia al agente y luego invoca
`agent.getPaths().getXxxFolder()`.

**En `SourceOfTruth`**: el repositorio de memoria utiliza
`agent.getPaths().getDataFolder()` para determinar dónde ubicar las bases
de datos H2 (`memory.mv.db`, `service.mv.db`) y la subcarpeta
`checkpoints/` donde se guardan los puntos de control narrativo. También
escribe el archivo `turns.csv` (depuración) en esa misma ubicación. Sin
`AgentPaths`, `SourceOfTruth` tendría que adivinar o recibir rutas por
configuración, lo que complicaría su diseño.

**En `ReasoningService` y `Session`**: el servicio de razonamiento necesita
persistir el estado de la conversación activa (`active_session.json`) y
volcar el contexto actual para depuración (`last_context.json`). Ambos
archivos se almacenan en `getDataFolder()` y `getTempFolder()`
respectivamente, recuperados a través de las rutas que proporciona
`AgentPaths`. Además, el prompt del sistema construido dinámicamente se
escribe en `var/tmp/reasoning-system-prompt.md`, permitiendo al
desarrollador inspeccionar qué instrucciones está recibiendo el LLM en cada
momento.

**En `SensorsService`**: el estado sensorial (sensores registrados, cola
de eventos, estadísticas) se serializa a `sensors.json` dentro de
`getDataFolder()`. Al reiniciar el agente, `SensorsServiceImpl` lee este
archivo desde la misma ubicación y rehidrata su estado interno, gracias a
que `AgentPaths` le proporciona la ruta exacta donde debe buscarlo.

**En las herramientas del agente (`AgentTool`)**: muchas herramientas
necesitan operar con el sistema de archivos. Por ejemplo:
- `FileReadTool`, `FileWriteTool` y `FilePatchTool` utilizan
  `agent.getAccessControl().resolvePath()` que internamente se basa en
  `agent.getPaths().getWorkspaceFolder()` como raíz del sandbox.
- `ShellExecuteTool` necesita conocer `getSandboxHomeFolder()` para
  configurar el entorno de `firejail` y `getTempFolder()` para almacenar
  la salida paginada de los comandos.
- `WebGetTikaTool` y `FileExtractTextTool` almacenan sus cachés en
  `getCacheFolder()`, utilizando `AgentPaths` para resolver rutas
  relativas a IDs de recurso.
- El sistema de paginación (`AbstractPaginatedAgentTool`) convierte
  identificadores como `tmp://...` o `cache://...` en rutas absolutas
  resolviéndolos contra `getTempFolder()` y `getCacheFolder()`,
  respectivamente. `AgentPaths` actúa aquí como un pequeño sistema de
  archivos virtual.

**En la UI de configuración**: los componentes Swing y de consola necesitan
localizar `settingsui.json` (la definición de la interfaz) y los archivos
de dominio (`.properties`). Utilizan
`agent.getPaths().getConfigFolder().resolve(...)` para construirlos, lo
que permite que la interfaz se adapte dinámicamente al workspace activo.

**En `BootUtils`**: durante la inicialización, se arranca el servidor web
de H2 y se genera el archivo `.h2.server.properties` en
`getConfigFolder()`, de modo que la consola web pueda conectarse a las
bases de datos del workspace correcto.

Este patrón de **inyección por agregación** (el agente posee las rutas, y
todos los servicios acceden a través de él) evita el uso de variables
globales o singletons. Cualquier componente que necesite conocer una
ubicación en disco puede obtenerla de forma predecible, sin acoplamientos
ocultos. Además, facilita las pruebas unitarias: se puede crear un
`AgentPaths` con un workspace temporal, inyectarlo en un agente simulado y
verificar que los servicios escriben donde deben.

En conjunto, `AgentPaths` actúa como el **sistema de coordenadas del
sandbox** y el **pegamento persistente** entre todos los subsistemas de
Noema. Sin él, cada servicio tendría que gestionar sus propias rutas, con
el consiguiente riesgo de fragmentación, errores de ubicación y dificultad
para mantener la portabilidad.

## 8. Limitaciones y diseño deliberado

Como toda decisión arquitectónica, el enfoque de `AgentPaths` tiene
limitaciones que no son fruto del descuido, sino de compensaciones
conscientes entre simplicidad, portabilidad y funcionalidad. Es importante
exponerlas para que quien lea este documento entienda por qué ciertas cosas
no se hacen de otra manera.

**Ausencia de un sistema de archivos virtual**: `AgentPaths` no abstrae el
acceso a disco tras una capa de red o almacenamiento en la nube. Todas las
rutas son locales al sistema operativo donde se ejecuta Noema. Esto implica
que el agente no puede (sin modificaciones) operar sobre archivos remotos
(S3, NFS, etc.) más allá de lo que el propio sistema operativo permita
montar de forma transparente. La decisión es intencionada: añadir una capa
virtual habría multiplicado la complejidad del control de acceso, la
paginación y la gestión de cachés, sin aportar un beneficio claro para el
caso de uso principal (asistente de escritorio).

**Rutas absolutas resueltas en tiempo real**: Noema no almacena rutas
canónicas ni utiliza enlaces simbólicos persistentes. Cada vez que una
herramienta necesita acceder a un archivo, resuelve la ruta desde el
workspace actual llamando a `resolvePath()`. Esto hace que mover el
workspace de ubicación sea trivial (no hay rutas absolutas embebidas en los
archivos de estado), pero también implica que referir un archivo por su
ubicación en un momento posterior puede fallar si el workspace se ha
reubicado entre sesiones. En la práctica, esto rara vez ocurre porque el
workspace se selecciona al arrancar y no cambia durante la ejecución.

**Sin soporte para variables de entorno ni rutas dinámicas**: `AgentPaths`
no expande variables como `${HOME}` o `~`. Todas las rutas se toman
literalmente. Esto mantiene el código simple y predecible, pero obliga al
usuario a escribir rutas completas si necesita acceder a directorios fuera
del sandbox (aunque esas rutas deben estar explícitamente autorizadas en la
lista blanca). Una posible mejora futura sería añadir un modesto resolutor
de variables, pero hasta ahora no ha sido necesario.

**Dependencia de la fiabilidad del sistema de archivos**: Noema asume que
las operaciones de creación, lectura, escritura y borrado son atómicas y
confiables. Si el disco falla, se llena o los permisos cambian
inesperadamente, el agente puede fallar de formas impredecibles. No hay un
mecanismo de reintentos complejo ni una capa de abstracción que oculte
estos errores. La filosofía es que el sistema de archivos subyacente debe
ser robusto, y que el agente debe fallar rápido y limpiamente si no lo es.

**Sin gestión de cuotas ni limpieza automática global**: Como se mencionó
antes, `AgentPaths` no supervisa el tamaño de `var/cache`, `var/tmp` o
`var/log`. Es responsabilidad de cada servicio implementar sus propias
políticas de retención. En la práctica, `AbstractPaginatedAgentTool` limpia
recursos antiguos mediante un LRU, `ShellExecuteTool` rota sus salidas, y
Log4j2 rota los logs. Sin embargo, no hay un recolector de basura
transversal que garantice que, por ejemplo, la caché de documentos no
crezca indefinidamente. En un uso prolongado, el usuario puede necesitar
limpiar manualmente estas carpetas.

**No soporta múltiples workspaces simultáneos**: Una instancia de
`AgentPaths` está ligada a un único workspace. No es posible que el agente
opere sobre dos proyectos distintos al mismo tiempo sin reiniciarse. Esto
es coherente con el diseño de "línea temporal única" de Noema, pero limita
su uso como agente que coordina información entre varios repositorios
independientes.

**Resolución de recursos limitada a dos niveles**: La búsqueda binaria
(primero local, luego global) es suficiente para los casos de uso
actuales, pero no es extensible a una jerarquía de más niveles (por
ejemplo, recursos específicos de proyecto, luego del usuario, luego del
sistema). Tampoco permite "sobrescribir" parcialmente recursos (modificar
solo una línea de un prompt complejo sin copiar el archivo entero). Para
necesidades más avanzadas, habría que rediseñar este subsistema.

Estas limitaciones no son defectos, sino **consecuencias de aplicar el
principio de mínima potencia** al problema de la gestión de rutas. Noema
prioriza la transparencia, la depuración sencilla y la portabilidad sobre
la flexibilidad absoluta. Para un agente de investigación y acompañamiento
personal, este equilibrio ha demostrado ser más que suficiente. En el
improbable caso de que se necesite escalar a entornos distribuidos o a
sistemas de archivos exóticos, siempre quedará la opción de reemplazar
`AgentPaths` por una implementación más sofisticada sin alterar el resto
de la arquitectura.

## 9. Conclusión: una base sólida pero modesta

`AgentPaths` es, en apariencia, un componente trivial: unas pocas docenas
de líneas que construyen rutas y crean directorios. Sin embargo, su
diseño refleja las prioridades arquitectónicas de todo Noema:
**transparencia, portabilidad y control explícito**.

Al tratar el sistema de archivos como el estado único y poner todas las
rutas al alcance de la mano mediante una API mínima pero completa,
`AgentPaths` permite que el resto de servicios se centren en su lógica de
negocio sin preocuparse por dónde persisten sus datos. Un desarrollador que
necesite depurar `SourceOfTruth` sabe exactamente dónde buscar los
archivos de la base de datos H2; alguien que quiera ajustar el
comportamiento del agente puede editar los prompts en `var/config/prompts/`
sin recompilar; un usuario que desee trasladar su agente a otro equipo
simplemente copia la carpeta `.noema-agent`.

Al mismo tiempo, `AgentPaths` es modesto en sus ambiciones. No intenta
resolver problemas que Noema no tiene (sistemas de archivos distribuidos,
cuotas, jerarquías complejas). Su resolución en dos niveles (local/global)
es suficiente para la gran mayoría de los casos de uso, y su decisión de
no cachear rutas ni expandir variables mantiene el código simple y el
comportamiento determinista.

La verdadera fortaleza de `AgentPaths` no reside en su sofisticación, sino
en cómo se integra con el resto del ecosistema. Desde `AgentAccessControl`
(que lo usa como base del sandbox) hasta `Session` (que escribe
`active_session.json` en `getDataFolder()`), pasando por
`AbstractPaginatedAgentTool` (que resuelve `tmp://` y `cache://` contra
las carpetas correspondientes), cada componente confía en `AgentPaths`
para orientarse en el sistema de archivos. Esta confianza mutua es lo que
permite que Noema funcione como un todo coherente, sin fugas de
abstracción ni configuraciones redundantes.

En resumen, `AgentPaths` no es la parte más brillante ni compleja de Noema,
pero es quizás la que mejor ejemplifica su filosofía de diseño: **hacer lo
suficiente para que el resto pueda hacer su trabajo, y nada más**. Para un
agente que aspira a perdurar en el tiempo dentro del sistema de archivos de
un usuario corriente, ese equilibrio entre funcionalidad y simplicidad es
una virtud, no una carencia.

# Especificación técnica de la implementación de ReasoningService

## 1. Introducción: el cerebro del agente

El `ReasoningService` es el núcleo de control del agente Noema. Si el
sistema sensorial (`SensorsService`) es su percepción del entorno, y la
memoria a largo plazo (`MemoryService`) su capacidad de recordar, el
`ReasoningService` es el centro que integra ambas, toma decisiones, ejecuta
acciones y mantiene la continuidad de la conversación. Es, en definitiva,
el cerebro del agente.

Su responsabilidad principal es orquestar un **bucle perpetuo de
consciencia**: un hilo dedicado que nunca duerme (salvo cuando el agente se
detiene) y que constantemente espera estímulos, mensajes del usuario,
notificaciones de Telegram, correos entrantes, alarmas programadas, o
incluso el simple paso del tiempo, para procesarlos. Cada estímulo, ya sea
un mensaje directo del usuario (que se inyecta en la conversación como un
`UserMessage` nativo) o una señal del entorno (como una notificación de
Telegram o el paso del tiempo, que se introducen mediante un mecanismo
simulado de `pool_event`), desencadena una o varias rondas de
razonamiento, durante las cuales el servicio construye el contexto,
consulta al modelo de lenguaje, ejecuta las herramientas que este solicite
y registra cada paso en la base de datos de la conversación
(`SourceOfTruth`).

Para cumplir esta función, el `ReasoningService` integra varios
subsistemas:

* **El modelo de lenguaje (`ChatModel`)**. Es el proveedor de
  razonamiento, configurable en caliente (proveedor, URL, clave,
  identificador del modelo) mediante acciones que el propio agente puede
  ejecutar. El servicio mantiene una instancia activa y la utiliza para
  todas las consultas generativas.
* **La sesión activa (`Session`)**. Es la memoria de trabajo del agente.
  Conserva en RAM los mensajes recientes de la conversación (tanto los del
  usuario como los del modelo, incluyendo las llamadas a herramientas y
  sus resultados), y los estructura para enviarlos al modelo junto con el
  prompt de sistema y el resumen de la memoria consolidada. La `Session`
  es además la responsable de inyectar una percepción temporal pasiva: si
  ha pasado más de una hora desde la última interacción, añade un mensaje
  sintético que informa al modelo del tiempo transcurrido.
* **El registro de herramientas (`AgentTool`)**. El servicio mantiene un
  catálogo de todas las capacidades que el agente puede invocar (lectura y
  escritura de archivos, ejecución de comandos, búsquedas web, envío de
  correos, etc.). Cada herramienta se registra con un nombre único, una
  especificación para el modelo (que incluye descripción y esquema de
  parámetros) y un estado de activación que puede modificarse por
  configuración. Durante el razonamiento, si el modelo decide usar una
  herramienta, el servicio la ejecuta, solicita confirmación al usuario
  si la operación es peligrosa, y devuelve el resultado para continuar el
  ciclo.
* **La persistencia (`SourceOfTruth`)**. Cada interacción atómica, un
  mensaje de usuario, una respuesta del modelo, una ejecución de
  herramienta, se persiste como un `Turn` en una base de datos H2. El
  `ReasoningService` no solo usa estos turnos para reconstruir la sesión
  tras un reinicio, sino que también los emplea como materia prima para la
  compactación de la memoria a largo plazo.
* **La compactación (`MemoryService`)**. Cuando la sesión acumula
  demasiados turnos (el umbral es configurable, por defecto 40), el
  servicio invoca a `MemoryService` para que consolide los mensajes más
  antiguos en un `CheckPoint`: un resumen narrativo que preserva la
  esencia de la conversación sin ocupar espacio en la ventana de contexto.
  Una vez generado, el nuevo `CheckPoint` se persiste y los mensajes
  compactados se eliminan de la sesión activa.

El `ReasoningService` se ejecuta como un servicio más dentro del `Agent`.
Su ciclo de vida es sencillo: al arrancar, carga las herramientas, instala
los recursos de identidad (prompt de sistema, manuales de entorno y
habilidades), crea el modelo de lenguaje y lanza el hilo del bucle de
eventos. Al detenerse, simplemente marca una bandera que provoca la salida
ordenada del hilo, permitiendo que la sesión activa se persista
correctamente en disco.

Desde fuera, el servicio expone una interfaz mínima: permite añadir
herramientas, consultar su estado, activarlas o desactivarlas, y recuperar
métricas sobre el tamaño del contexto. Sin embargo, la mayor parte de su
funcionalidad es interna y está encapsulada en el bucle `eventDispatcher` y
en la colaboración con `Session`.

En el contexto global de Noema, el `ReasoningService` es el componente que
dota al agente de **continuidad conversacional** (no olvida lo que acaba de
decir), **capacidad de acción** (puede tocar el mundo real a través de
herramientas) y **autonomía** (procesa estímulos sin intervención humana,
salvo cuando la seguridad lo exige). Su diseño busca un equilibrio
pragmático entre la potencia de los modelos de lenguaje actuales y las
restricciones de un entorno de escritorio local, sin depender de
infraestructuras cloud ni servicios externos más allá de las APIs de los
propios LLMs.

## 2. Arquitectura del servicio (mapa de componentes)

El `ReasoningService` no actúa en solitario. Su correcto funcionamiento
depende de una constelación de componentes que colaboran estrechamente,
cada uno con una responsabilidad bien definida. Esta sección describe los
bloques que constituyen el servicio y las relaciones entre ellos.

### 2.1. `ReasoningServiceImpl`: el orquestador

`ReasoningServiceImpl` es la implementación concreta del servicio. Actúa
como el punto central de control, el “director de orquesta” que coordina a
todos los demás actores. Su responsabilidad abarca:

* **Ciclo de vida**: gestiona el arranque (`start`) y la parada (`stop`)
  del servicio, lanzando y deteniendo el hilo del `eventDispatcher` que
  sostiene el bucle de consciencia.
* **Configuración del modelo**: mantiene una instancia de `ChatModel` (el
  proveedor de lenguaje) y la recrea cuando cambian los parámetros de
  conexión (URL, clave, identificador del modelo) mediante las acciones
  `CHANGE_REASONING_PROVIDER` y `CHANGE_REASONING_MODEL`.
* **Registro de herramientas**: posee un mapa (`availableTools`) donde
  cada herramienta (`AgentTool`) se almacena junto con un flag que indica
  si está activa. Este mapa es la fuente de verdad para saber qué
  capacidades puede usar el agente.
* **Construcción del prompt de sistema**: el método
  `getBaseSystemPrompt()` ensambla el mensaje inicial que define la
  personalidad y las reglas del agente, combinando el prompt base
  (almacenado como recurso Markdown), los módulos de identidad activos
  (que el usuario puede seleccionar en la configuración) y los índices de
  referencia del entorno (los archivos `.ref.md`). El resultado se cachea
  y se escribe en un fichero temporal para facilitar la depuración.
* **Orquestación del bucle**: el método `eventDispatcher` contiene el
  bucle principal que consume eventos, los procesa, invoca al modelo,
  ejecuta herramientas y coordina la compactación. Es el corazón del
  servicio.
* **Interfaz de gestión**: expone métodos para consultar y modificar el
  estado de las herramientas (`getAvailableTools`, `isToolActive`,
  `setToolActive`) y para obtener métricas sobre el contexto
  (`estimateSystemPromptTokenCount`, `estimateToolsTokenCount`,
  `estimateMessagesTokenCount`).

### 2.2. `Session`: la memoria de trabajo

`Session` es el contenedor de la conversación activa. No es un simple
almacén de mensajes, sino un gestor inteligente que mantiene la coherencia
entre lo que ocurre en RAM y lo que se ha persistido en disco.

Sus responsabilidades principales son:

* **Almacenamiento de mensajes**: mantiene una lista ordenada de objetos
  `ChatMessage` (de LangChain4j) que constituyen el historial inmediato
  de la conversación. Esta lista incluye mensajes de usuario, respuestas
  del modelo, llamadas a herramientas y resultados de herramientas.
* **Trazabilidad con la persistencia**: a través del mapa
  `turnOfMessage`, asocia cada mensaje en la lista con el identificador
  del `Turn` que lo originó. Esto permite, entre otras cosas, saber qué
  parte del historial ya ha sido compactada y qué parte aún está
  pendiente.
* **Construcción del contexto**: el método `getContextMessages()` es el
  responsable de ensamblar el bloque de texto que se enviará al LLM en cada
  consulta. Combina el prompt de sistema (proporcionado por el
  `ReasoningService`), el resumen del último `CheckPoint` (si existe), y
  los mensajes de la sesión activa. Además, inyecta la percepción temporal
  pasiva: si ha transcurrido más de una hora desde la última interacción,
  añade un mensaje sintético que informa al modelo del tiempo transcurrido.
* **Gestión de la compactación**: proporciona los métodos
  `getOldestMark()`, `getCompactMark()` y `remove()` que permiten aislar
  el bloque de mensajes que será compactado y eliminarlo de la sesión una
  vez que `MemoryService` haya generado un nuevo `CheckPoint`.
* **Persistencia de la sesión**: se serializa a disco (en
  `active_session.json`) tras cada modificación, utilizando un mecanismo
  de escritura atómica (archivo temporal + movimiento) para garantizar que
  no se corrompa en caso de fallo. Esto permite que el agente recupere su
  estado exacto tras un reinicio.

### 2.3. `AgentTool`: las capacidades del agente

Las herramientas son los músculos del agente: cada una encapsula una
capacidad específica que el LLM puede invocar para interactuar con el
mundo exterior. El `ReasoningService` no conoce los detalles de
implementación de cada herramienta; solo necesita su nombre, su
especificación (para presentarla al modelo) y un método `execute` que
devuelve un resultado en texto.

Cada herramienta implementa la interfaz `AgentTool` y define:

* **Metadatos**: nombre único, descripción legible por el modelo, y un
  esquema JSON que describe sus parámetros (generado a partir de
  anotaciones o definición manual).
* **Modo de operación**: puede ser `MODE_READ` (operaciones seguras que no
  modifican el estado), `MODE_WRITE` (operaciones que modifican archivos
  o configuración) o `MODE_EXECUTION` (ejecución de comandos externos).
  Esta clasificación determina si el `ReasoningService` debe solicitar
  confirmación al usuario antes de ejecutarla.
* **Estado de activación**: cada herramienta tiene un flag `active` que
  puede modificarse por configuración, permitiendo deshabilitar
  temporalmente capacidades que no se desea que el agente utilice.

El `ReasoningService` mantiene un registro de todas las herramientas
disponibles, sincroniza su estado con la configuración del usuario y,
durante el bucle, ejecuta aquellas que el modelo solicita, gestionando la
confirmación humana cuando es necesario.

### 2.4. `SourceOfTruth`: la persistencia inmutable

`SourceOfTruth` es el repositorio que almacena de forma duradera cada uno
de los pasos de la conversación. No es un componente interno del
`ReasoningService`, sino un servicio independiente que este utiliza para
persistir los turnos y recuperar los puntos de control.

Su modelo de datos se organiza en torno a dos entidades:

* **`Turn`**: representa una unidad atómica de interacción. Puede ser un
  mensaje del usuario, una respuesta del modelo, una llamada a herramienta
  o el resultado de una herramienta. Cada turno tiene un identificador
  único, una marca de tiempo, un tipo (chat, tool_execution, lookup_turn,
  etc.), y los campos de texto relevantes (entrada, salida, etc.). Los
  turnos se almacenan en tablas SQL dentro de una base de datos H2
  embebida.
* **`CheckPoint`**: representa un punto de consolidación de la memoria a
  largo plazo. Contiene un resumen narrativo de un segmento de la
  conversación (generado por `MemoryService`) y un texto de "El Viaje" que
  preserva la cronología de los eventos. Los puntos de control se
  encadenan: cada nuevo `CheckPoint` parte del anterior y añade los turnos
  transcurridos desde entonces.

El `ReasoningService` utiliza `SourceOfTruth` para tres operaciones:

* **Persistir turnos**: cada vez que se produce una interacción (un
  mensaje, una ejecución de herramienta), se crea un `Turn` y se añade a
  la base de datos.
* **Recuperar turnos para compactación**: cuando la sesión alcanza el
  umbral, el servicio solicita los turnos comprendidos entre dos marcas
  para pasarlos a `MemoryService`.
* **Obtener el último punto de control**: al arrancar, se recupera el
  `CheckPoint` más reciente para incluirlo en el contexto inicial.

### 2.5. `MemoryService`: la compactación narrativa

`MemoryService` es el encargado de la memoria a largo plazo. Su función es
transformar una secuencia de turnos (que pueden ser decenas o cientos) en
un resumen narrativo compacto que preserve la esencia de la conversación
sin ocupar espacio en la ventana de contexto.

El `ReasoningService` no conoce los detalles de cómo se genera ese resumen.
Solo interactúa con `MemoryService` en un momento específico del ciclo:
cuando la sesión ha acumulado suficientes turnos y se decide compactar. La
invocación es simple:

* Se le pasa el último `CheckPoint` existente (o `null` si es la primera
  compactación) y la lista de turnos que se deben consolidar.
* `MemoryService` utiliza un LLM (puede ser el mismo modelo o uno más
  económico) para generar un nuevo `CheckPoint` que contiene un resumen
  actualizado y una narrativa cronológica ("El Viaje").
* El `ReasoningService` recibe el nuevo `CheckPoint`, lo persiste a través
  de `SourceOfTruth` y lo convierte en el `activeCheckPoint` para futuros
  contextos.

Esta separación de responsabilidades permite que la lógica de compactación
pueda evolucionar (cambiando el prompt, el modelo utilizado, o incluso la
estrategia de resumen) sin afectar al orquestador principal.

### 2.6. `AgentAccessControl` y `AgentConsole`: seguridad e interacción
humana

Aunque no son componentes internos del `ReasoningService`, su papel en la
ejecución segura es crucial.

* **`AgentAccessControl`**: define qué operaciones están permitidas según
  el contexto. Por ejemplo, puede restringir el acceso a determinadas rutas
  del sistema de archivos o impedir la ejecución de comandos en ciertos
  directorios. Antes de ejecutar una herramienta, el `ReasoningService`
  consulta a este controlador para saber si la herramienta está permitida
  en el estado actual del agente.
* **`AgentConsole`**: es la interfaz para la interacción con el usuario.
  No es una consola física, sino una abstracción que puede tener
  implementaciones diferentes: una versión gráfica (Swing), una versión
  de terminal (JLine) o incluso una versión "tonta" para entornos
  headless. El `ReasoningService` la utiliza para mostrar mensajes del
  sistema y, sobre todo, para solicitar confirmación antes de ejecutar
  herramientas que modifiquen el estado (escritura de archivos, ejecución
  de comandos). Al ser una interfaz, el servicio queda desacoplado de la
  tecnología de presentación concreta.

### 2.7. Relaciones entre componentes

El flujo de control entre estos componentes sigue un patrón claro:

1. El `eventDispatcher` (dentro de `ReasoningServiceImpl`) espera un
   evento de `SensorsService`.
2. El evento se convierte en mensajes y se añade a `Session`.
3. Se construye el contexto llamando a `Session.getContextMessages()`, que
   puede incluir el `activeCheckPoint` obtenido de `SourceOfTruth`.
4. El contexto se envía al `ChatModel`, que devuelve una respuesta.
5. Si la respuesta contiene solicitudes de herramientas, se ejecutan
   (consultando a `AgentAccessControl` y solicitando confirmación a
   `AgentConsole` si es necesario), los resultados se añaden a `Session` y
   se persisten como turnos en `SourceOfTruth`.
6. Si la respuesta es texto, se muestra en `AgentConsole`, se persiste el
   turno correspondiente y se cierra el ciclo.
7. Si `Session.needCompaction()` lo indica, se invoca a `MemoryService`
   para generar un nuevo `CheckPoint`, que se persiste en `SourceOfTruth`
   y se convierte en el nuevo `activeCheckPoint`, mientras
   `Session.remove()` elimina los mensajes compactados.

Esta estructura de componentes con responsabilidades bien delimitadas
permite que el `ReasoningService` sea, al mismo tiempo, el centro
neurálgico del agente y un módulo relativamente sencillo de entender y
modificar, porque cada pieza hace una cosa y la hace bien.

## 3. Ciclo de vida y concurrencia

El `ReasoningService` es un servicio gestionado por `Agent`, que sigue el
protocolo estándar de ciclo de vida de todos los servicios de Noema: se
instancia a través de una fábrica (`AgentServiceFactory`), se verifica que
puede arrancar (`canStart()`) y finalmente se inicia (`start()`) o se
detiene (`stop()`) según las necesidades del agente.

### 3.1. Arranque del servicio

Cuando el agente se pone en marcha recorre los servicios registrados y
llama a `start()` sobre aquellos que están habilitados. Para el
`ReasoningService`, este momento es crítico porque determina la capacidad
del agente para pensar y actuar.

El método `start()` ejecuta una secuencia ordenada de operaciones:

1. **Instalación de recursos**. Copia al espacio de trabajo del agente los
   archivos necesarios para su funcionamiento: el prompt de sistema base
   (`reasoning-system.md`), los módulos de identidad (`core`), los
   índices de referencia del entorno (`environ`) y la lista de habilidades
   (`skills`). Estos recursos se almacenan en `var/config/prompts/` y
   `var/identity/`, y son la materia prima con la que se construirá la
   personalidad del agente.

2. **Registro de acciones.**  
   Añade al sistema de acciones del agente los comportamientos que
   permiten:

   - Modificar la configuración del modelo en caliente:  
     - `CHANGE_REASONING_PROVIDER` (cuando se cambia la URL o la API
       key).  
     - `CHANGE_REASONING_MODEL` (cuando se cambia el identificador del
       modelo).

   - Forzar operaciones de mantenimiento sobre la sesión activa:  
     - `COMPACT_REASONING_SESSION` – compacta aproximadamente el 50% más
       antiguo del historial de la sesión.  
     - `COMPACT_REASONING_FULL_SESSION` – compacta **todo** el historial
       consolidado (desde el turno más antiguo hasta el más reciente).  
     - `REFRESH_REASONING_TOOLS` – recarga el estado de activación de las
       herramientas desde la configuración, permitiendo habilitar o
       deshabilitar capacidades sin reiniciar el agente.

   Esto permite que el propio agente (o un usuario avanzado) pueda, por
   ejemplo, cambiar de proveedor de IA, forzar una compactación parcial o
   total del historial, o actualizar el catálogo de herramientas activas,
   todo ello sin necesidad de reiniciar el agente.

3. **Sincronización de herramientas**. Invoca a `refresh_available_tools()`
   para que el estado de activación de cada herramienta (definido en la
   configuración del usuario) se refleje en el mapa interno. Las
   herramientas que no aparecen en la configuración conservan su estado
   por defecto (definido por la propia herramienta al ser registrada).

4. **Creación del modelo de lenguaje**. Construye la instancia de
   `ChatModel` a partir de los parámetros de conexión (URL, clave API,
   identificador del modelo) almacenados en la configuración del agente.
   Este modelo será el motor de razonamiento para toda la sesión.

5. **Lanzamiento del hilo de eventos**. Crea un hilo de plataforma (no
   virtual) con el nombre `Noema-Event-Dispatcher` y lo pone en marcha,
   ejecutando el método `eventDispatcher()`. Este hilo se convierte en el
   corazón latido del agente: mientras el servicio está activo, nunca se
   detiene.

Una vez completados estos pasos, el flag `running` se establece a `true` y
el servicio imprime un mensaje en la consola indicando que está operativo,
junto con el nombre del modelo de lenguaje que está utilizando.

### 3.2. El hilo del `eventDispatcher`

El hilo del `eventDispatcher` es el único punto de ejecución activo del
servicio. Su diseño es deliberadamente simple: un bucle infinito que,
mientras `running` sea verdadero, consume eventos y los procesa. No hay
concurrencia interna: cada evento se procesa hasta completar todas las
rondas de razonamiento que requiera, antes de pasar al siguiente.

Este modelo de **un solo hilo secuencial** tiene varias ventajas:

- **Simplicidad**: no hay que gestionar sincronizaciones complejas entre
  múltiples hilos que comparten la sesión.
- **Determinismo**: el orden de procesamiento de los eventos es el orden
  en que se extraen de la cola sensorial, garantizado por el
  `SensorsService`.
- **Estabilidad**: se evitan problemas de concurrencia que podrían llevar
  a estados inconsistentes en la sesión o en la persistencia.

La elección de un hilo de plataforma en lugar de un hilo virtual responde a
consideraciones prácticas: aunque el código se escribió inicialmente con
hilos virtuales, se encontraron problemas durante la depuración que
llevaron a revertir a hilos de plataforma. En cualquier caso, dado que solo
hay un hilo principal el uso de hilos virtuales no aportaría una ventaja
significativa en este contexto.

### 3.3. Parada del servicio

Cuando el agente se detiene (por cierre de la aplicación o por una acción
explícita), se invoca al método `stop()` del `ReasoningService`. Este
método simplemente establece el flag `running` a `false`. No interrumpe el
hilo de eventos ni fuerza una salida inmediata.

El propio bucle del `eventDispatcher` está diseñado para comprobar
`running` en cada iteración. En el momento en que la condición deja de
cumplirse, el hilo abandona el bucle y finaliza de forma natural. Esto
garantiza que cualquier evento que se estuviera procesando en ese momento
se complete antes de la parada, evitando estados intermedios o corrupción
de la sesión.

La persistencia de la sesión activa no depende del `stop()`, sino que se
guarda en disco tras cada modificación (dentro de los métodos `add()`,
`consolideTurn()` y `remove()` de `Session`). Por tanto, aunque el agente
se detenga abruptamente (por un fallo de la JVM o un corte de energía), la
última versión persistida de la sesión es siempre la anterior a la
operación que se estaba ejecutando. El mecanismo de escritura atómica
(archivo temporal + movimiento) asegura que nunca se quede un archivo
parcialmente escrito.

### 3.4. Consideraciones sobre concurrencia externa

Aunque el bucle principal es secuencial, hay puntos en los que el
`ReasoningService` interactúa con otros componentes que pueden estar
operando en hilos diferentes:

- **`SensorsService.getEvent()`**: esta llamada puede bloquear el hilo
  hasta que llegue un nuevo evento. Internamente, el servicio sensorial
  utiliza mecanismos de sincronización (`wait/notify`) que permiten que el
  hilo del `eventDispatcher` duerma eficientemente cuando no hay trabajo
  que hacer.
- **Ejecución de herramientas**: cuando el LLM solicita una herramienta,
  la ejecución se realiza de forma síncrona dentro del mismo hilo. Si una
  herramienta tarda mucho tiempo (por ejemplo, un procesamiento pesado o
  una espera de red), el bucle se bloquea hasta que retorna. Esto es
  intencionado: el agente no debe procesar nuevos eventos mientras está
  ejecutando una acción, para preservar la coherencia del historial.
- **Confirmación humana**: antes de ejecutar herramientas peligrosas, se
  solicita confirmación a `AgentConsole`. Este método puede ser bloqueante
  si la consola requiere interacción del usuario (por ejemplo, un diálogo
  modal en Swing). Durante ese tiempo, el hilo del `eventDispatcher`
  también permanece bloqueado, lo que es correcto porque el agente no
  puede continuar hasta que el usuario autorice o deniegue la operación.
- **Compactación asíncrona**: aunque la compactación se dispara desde el
  hilo principal, internamente `MemoryService` puede realizar operaciones
  pesadas (varias llamadas al LLM) que también bloquean el bucle. Esto es
  una decisión de diseño deliberada: la compactación es parte del
  procesamiento del turno y debe completarse antes de pasar al siguiente
  evento. Si en el futuro se optara por hacerla asíncrona, habría que
  rediseñar la gestión de la sesión para evitar que se sigan añadiendo
  mensajes mientras se compacta.

En resumen, el modelo de concurrencia del `ReasoningService` es
deliberadamente simple: un solo hilo, un solo evento cada vez, sin
concurrencia interna. Esta simplicidad es una de las claves de su
robustez: no hay condiciones de carrera, ni estados inconsistentes, ni
necesidad de mecanismos de sincronización complejos. Todo el flujo es
determinista y predecible, lo que facilita tanto el desarrollo como la
depuración.

## 4. El núcleo del orquestador: el bucle `eventDispatcher`

El método `eventDispatcher` es el corazón palpitante del
`ReasoningService`. Es un bucle infinito que se ejecuta en su propio hilo
desde el momento en que el servicio arranca hasta que se detiene. Su
función es simple en apariencia, consumir eventos y procesarlos, pero su
implementación concentra la lógica más crítica del agente: la orquestación
de la conversación, la gestión de herramientas y la coordinación con la
memoria.

### 4.1. La estructura general

El bucle se organiza en torno a un único punto de bloqueo: la llamada a
`sensors.getEvent()`. Mientras no haya estímulos que atender, el hilo
permanece en espera, consumiendo recursos mínimos. Cuando llega un evento,
el flujo se desencadena y no se detiene hasta que se ha completado el
procesamiento completo de ese estímulo, incluyendo todas las rondas de
razonamiento y ejecución de herramientas que sean necesarias.

La estructura simplificada del bucle es la siguiente:

1. Obtener el siguiente evento sensorial (bloqueante).
2. Si el evento es de usuario, inyectarlo directamente como `UserMessage`.
   Si es del entorno, inyectarlo mediante el mecanismo simulado de
   `pool_event`.
3. Persistir el turno de observación (el estímulo que acaba de llegar).
4. Entrar en un bucle interno que se repetirá hasta que el turno actual se
   considere "terminado".
5. Construir el contexto completo (prompt de sistema, checkpoint
   histórico, mensajes de la sesión).
6. Optimizar el contexto (recorte de resultados largos y anotaciones
   pendientes).
7. Consultar al modelo de lenguaje, proporcionándole la lista de
   herramientas activas.
8. Evaluar la respuesta del modelo:
   - Si solicita ejecutar herramientas: ejecutarlas (con confirmación
     humana si es necesario), persistir los resultados, añadirlos a la
     sesión y continuar el bucle interno.
   - Si responde con texto: mostrarlo en consola, persistir el turno
     final, y salir del bucle interno.
9. Al salir del bucle interno, verificar si la sesión ha alcanzado el
   umbral de compactación. Si es así, ejecutar la compactación.
10. Volver al paso 1.

### 4.2. El punto de entrada: la espera de eventos

El bucle comienza con una llamada a `sensors.getEvent()`. Este método
pertenece al `SensorsService`, que actúa como la puerta de entrada de todos
los estímulos externos: mensajes del usuario, notificaciones de Telegram,
correos entrantes, alarmas programadas, e incluso el paso del tiempo (a
través de un sensor de reloj interno).

La implementación de `getEvent()` está diseñada para ser bloqueante: si no
hay eventos disponibles, el hilo se duerme hasta que el `SensorsService`
recibe un nuevo estímulo y lo notifica. Este mecanismo de espera activa
pero eficiente permite que el agente no consuma CPU cuando no hay trabajo
que hacer, reaccionando en cambio de forma inmediata cuando algo ocurre.

El evento devuelto no es un dato crudo, sino un objeto
`ConsumableSensorEvent` que ya sabe cómo transformarse en mensajes de
LangChain4j (tanto el mensaje que se añadirá al historial como, en el caso
de sensores del entorno, el mensaje de respuesta simulado de la herramienta
`pool_event`).

### 4.3. La inyección del evento en la sesión: dos caminos

Una vez obtenido el evento, el `eventDispatcher` toma una decisión crucial
basada en su naturaleza:

**Eventos de usuario (`SensorEventUser`)**

Estos eventos representan la intervención directa del interlocutor
humano. El flujo los trata con la máxima naturalidad: el evento se
convierte en un `UserMessage` (el tipo de mensaje estándar que el modelo
espera recibir cuando alguien le habla) y se añade directamente a la
sesión. No hay simulación, no hay capas de indirección. Desde la
perspectiva del modelo, es como si el usuario hubiera escrito su mensaje
en el chat.

**Eventos del entorno (el resto de naturalezas sensoriales)**

Cuando el evento no es de usuario, es decir, cualquier estímulo que no
proviene de la interacción directa con el interlocutor humano, el
tratamiento es diferente. El evento ya ha sido diseñado para saber cómo
presentarse ante el modelo de lenguaje. A través de sus métodos
`getChatMessage()` y `getResponseMessage()`, cada evento sabe qué par de
mensajes debe inyectar en la sesión para que el modelo perciba el estímulo
como si hubiera sido generado por una acción propia del agente.

El `eventDispatcher` no necesita conocer los detalles de esa
transformación. Simplemente añade ambos mensajes a la sesión en el orden
en que deben aparecer en el historial:

1. Primero, `event.getChatMessage()`, que suele ser un `AiMessage` que
   simula una llamada a la herramienta `pool_event`. Este mensaje aparece
   en el historial como si el propio agente hubiera decidido consultar sus
   sensores.

2. Inmediatamente después, `event.getResponseMessage()`, que es un
   `ToolExecutionResultMessage` que contiene el contenido del estímulo
   (el texto de la notificación, la alarma, etc.). Este mensaje aparece
   como el resultado de la llamada a `pool_event`.

De esta forma, cuando el modelo recibe el contexto completo, encuentra en
su historial una secuencia coherente: primero un registro de que él mismo
ejecutó una herramienta para consultar sus sensores, y luego el resultado
de esa consulta con la información del estímulo recibido. La asincronía
del mundo real queda oculta bajo esta capa de simulación, y el modelo puede
procesar el evento como si hubiera sido él quien lo solicitó.

Una vez añadidos los mensajes, se persiste un `Turn` de tipo
`tool_execution` que documenta el evento como si se tratara de una
ejecución real de la herramienta `pool_event`. Este turno contiene en sus
campos tanto el mensaje de llamada (simulado) como el resultado obtenido,
manteniendo así la trazabilidad completa del estímulo percibido.

### 4.4. La persistencia del turno de observación

Independientemente del tipo de evento, inmediatamente después de añadirlo
a la sesión se crea y persiste un `Turn` que documenta el estímulo
recibido. Este turno tiene tipo `tool_execution` para los eventos del
entorno (ya que se registra como una ejecución simulada de `pool_event`) o
`chat` para los eventos de usuario.

La persistencia temprana del turno de observación es importante por dos
razones:

- **Trazabilidad**: queda constancia en la base de datos de que el agente
  percibió ese estímulo en un momento concreto, independientemente de lo
  que ocurra después.
- **Compactación futura**: cuando se consolide la memoria, estos turnos
  de observación formarán parte de la narrativa que se resume.

### 4.5. El bucle interno: procesando hasta cerrar el turno

Una vez que el estímulo está en la sesión, comienza el bucle interno. Su
objetivo es alcanzar un estado en el que el modelo haya generado una
respuesta de texto (no una llamada a herramienta) y se pueda considerar
que el turno actual ha terminado.

Cada iteración del bucle interno sigue estos pasos:

**Construcción del contexto**

Se invoca a `session.getContextMessages()`, que devuelve una lista de
mensajes lista para enviar al modelo. Esta lista incluye:

- El prompt de sistema (la identidad del agente, sus reglas operativas y
  los índices de referencia del entorno).
- El resumen del último `CheckPoint` (si existe), que aporta memoria a
  largo plazo.
- Todos los mensajes acumulados en la sesión activa (incluyendo el evento
  que desencadenó el turno, las interacciones previas, y los resultados
  de herramientas ejecutadas en iteraciones anteriores del mismo turno).

Además, si ha pasado más de una hora desde la última interacción, el método
`getContextMessages()` inyecta un mensaje sintético de sensor de tiempo,
informando al modelo del lapso transcurrido. Este es el mecanismo que dota
al agente de percepción temporal pasiva.

**Preparación y optimización del contexto**

Una vez construida la lista completa de mensajes que se enviará al modelo
(`getContextMessages()`), el servicio aplica una serie de transformaciones
destinadas a **optimizar el uso de la ventana de contexto** y **guiar al
modelo hacia prácticas que preserven información relevante**. Estas
transformaciones se ejecutan antes de la consulta al LLM y son las
siguientes:

a. **Recorte (podado) de resultados de herramientas excesivamente
largos**  
   El servicio recorre los mensajes del historial identificando aquellos
   que son resultados de herramientas (`ToolExecutionResultMessage`). Si
   el contenido textual de un resultado supera un umbral de tamaño (por
   defecto, 1 KB), se aplica un recorte:

   - Se elimina el cuerpo del mensaje, conservando únicamente la cabecera
     con metadatos (incluyendo un campo `CONTENT_TRIMMED: true` según la
     política definida por cada herramienta).
   - Esto evita que resultados muy extensos, como la salida de un comando
     `shell_execute` o el contenido de un archivo voluminoso, saturen el
     contexto, especialmente cuando ya han sido procesados en turnos
     anteriores.

b. **Sugerencia de anotación para recursos pendientes**  
   Tras el recorte, el servicio analiza los resultados de herramientas
   que aún permanecen en el contexto y detecta aquellos que corresponden a
   recursos paginados (identificados por un `resource_id`). Para cada
   recurso que ha sido **leído** (a través de herramientas como
   `file_read`, `web_get_content` o `shell_execute`) pero que **aún no ha
   sido anotado** mediante la herramienta `annotate_observation`, se genera
   un recordatorio.

   Este recordatorio se inyecta en el contexto como un evento simulado
   (una llamada a `pool_event` seguida de un mensaje de resultado) que
   indica al modelo:

   - Qué recursos están pendientes de anotación.
   - Que debe utilizar `annotate_observation` con el `resource_id`
     correspondiente para extraer y consolidar la información relevante
     antes de que esos resultados desaparezcan del contexto por
     compactación o por recortes futuros.

De esta forma, el servicio no solo reduce el ruido, sino que **guía al
modelo para que preserve activamente el conocimiento valioso** en su
memoria episódica, a través de las anotaciones que luego formarán parte de
los puntos de guardado.

**Consulta al modelo**

Con el contexto construido y la lista de herramientas activas, se llama a
`model.generate()`. El modelo de lenguaje (configurado con los parámetros
de conexión oportunos) devuelve una respuesta que puede ser de dos tipos:
texto plano, o una o más solicitudes de ejecución de herramientas.

**Manejo de herramientas**

Si el modelo solicita ejecutar herramientas, el `eventDispatcher` itera
sobre cada solicitud. Por cada una:

- Se busca la herramienta en el registro `availableTools`.
- Si la herramienta está activa y permitida por `AgentAccessControl`, se
  procede a ejecutarla.
- Si el modo de la herramienta es `MODE_WRITE` o `MODE_EXECUTION`, se
  solicita confirmación al usuario a través de `AgentConsole.confirm()`.
  Si el usuario deniega, la ejecución se aborta y se devuelve un mensaje
  de error.
- La herramienta se ejecuta (síncronamente, dentro del mismo hilo) y se
  obtiene un resultado en texto.
- Se crea un `Turn` de tipo `tool_execution` (o `lookup_turn` si la
  herramienta es de memoria) que documenta la llamada y su resultado.
- Se añade un `ToolExecutionResultMessage` a la sesión, que el modelo verá
  en la siguiente iteración del bucle interno.

Una vez procesadas todas las solicitudes de herramientas, el bucle interno
continúa. El modelo recibirá en la siguiente iteración tanto el resultado
de las herramientas ejecutadas como cualquier otro mensaje que se haya
añadido mientras tanto.

**Manejo de la respuesta textual**

Si el modelo responde con texto (y no hay solicitudes de herramientas
pendientes), se ha alcanzado el final del turno. El texto se muestra en la
consola a través de `AgentConsole.printModelResponse()`, se persiste un
`Turn` de tipo `chat` que contiene la respuesta, y se añade el mensaje a
la sesión (aunque en realidad ya se añadió cuando se recibió la respuesta
del modelo). El flag `turnFinished` se establece a `true` y se sale del
bucle interno.

**Reintentos por herramientas no formalizadas**

Hay un caso especial contemplado en el código: cuando el modelo devuelve
un `FinishReason.TOOL_EXECUTION` pero no hay solicitudes de herramientas
en la respuesta. Esto puede ocurrir con algunos modelos que anuncian que
van a usar una herramienta pero no la formalizan correctamente en el
formato esperado. En ese caso, el bucle inyecta un mensaje de usuario con
el texto "(reintenta la llamada a la herramienta sin ninguna explicación)"
y continúa, incrementando un contador de reintentos. Si se superan tres
reintentos, se aborta el turno con una excepción.

### 4.6. La compactación al final del turno

Una vez que el bucle interno ha terminado (es decir, el modelo ha
entregado una respuesta textual y se ha cerrado el turno), el
`eventDispatcher` evalúa si la sesión necesita compactación mediante
`session.needCompaction()`.

Este método compara el número de turnos únicos acumulados en la sesión con
un umbral configurable (por defecto, 40 turnos). Si se ha superado el
umbral, se invoca a `performCompaction()`, que inicia el proceso de
consolidación de la memoria a largo plazo:

- Se obtienen las marcas de inicio y fin del bloque a compactar
  (`getOldestMark()` y `getCompactMark()`).
- Se recuperan de `SourceOfTruth` los turnos comprendidos entre esas
  marcas.
- Se llama a `MemoryService.compact()` pasándole el último `CheckPoint`
  existente y la lista de turnos. `MemoryService` utiliza un LLM para
  generar un nuevo `CheckPoint` que resume la conversación.
- El nuevo `CheckPoint` se persiste en `SourceOfTruth`.
- Se limpia la sesión, eliminando los mensajes que ya han sido
  compactados (`session.remove()`).
- El `activeCheckPoint` se actualiza al nuevo valor.

La compactación ocurre dentro del mismo hilo del `eventDispatcher`,
bloqueando el procesamiento de nuevos eventos mientras se realiza. Esto es
una decisión de diseño: compactar es parte del procesamiento del turno que
acaba de terminar, y no deberían llegar nuevos estímulos hasta que la
memoria esté consolidada.

### 4.8. Manejo de errores y callback final

El bucle principal está envuelto en un bloque `try-catch` que captura
cualquier excepción no manejada (incluyendo `Throwable`). Si ocurre un
error crítico, se registra en el log, se muestra un mensaje de error en la
consola y el bucle continúa. La filosofía es que el agente debe seguir
funcionando incluso ante fallos inesperados, sin colapsar. La sesión y los
turnos ya están persistidos, por lo que no hay pérdida de información.

Finalmente, si el evento que se procesó tenía asociado un callback
(`event.getCallback()`), se invoca al finalizar, pasándole el texto de la
respuesta final del LLM. Esto permite que los componentes externos que
inyectaron el evento (por ejemplo, una interfaz de usuario) puedan
reaccionar cuando el agente ha terminado de procesarlo.

### 4.9. Resumen del flujo

En conjunto, el `eventDispatcher` implementa un ciclo de vida completo para
cada estímulo que llega al agente:

1. **Captura**: se espera un evento sensorial.
2. **Inyección**: se transforma en mensajes de LangChain4j, diferenciando
   entre usuario (directo) y entorno (simulado).
3. **Persistencia**: se guarda el turno de observación.
4. **Razonamiento**: se consulta al modelo en un bucle que puede
   repetirse varias veces si se ejecutan herramientas.
5. **Respuesta**: se muestra el texto final al usuario.
6. **Consolidación**: si es necesario, se compacta la memoria a largo
   plazo.
7. **Reinicio**: se vuelve a esperar el siguiente evento.

Este flujo, aunque secuencial, es capaz de manejar la asincronía del mundo
real gracias a la abstracción que proporciona `SensorsService` y al
mecanismo de simulación de `pool_event`. El resultado es un agente que
percibe su entorno, razona sobre él, actúa, y mantiene una conversación
coherente a lo largo del tiempo, todo ello en un solo hilo de ejecución.

## 5. Gestión de herramientas

Las herramientas son el mecanismo mediante el cual el agente trasciende la
pura conversación y actúa sobre el mundo: lee y escribe archivos, ejecuta
comandos, consulta APIs externas, envía correos, programa alarmas, o
recupera información de su propia memoria. El `ReasoningService` actúa como
el gestor de estas capacidades, manteniendo un catálogo actualizado,
sincronizando su estado con la configuración del usuario, y orquestando su
ejecución cuando el modelo las solicita.

### 5.1. Registro y catálogo de herramientas

Cuando el servicio arranca, invoca a `getTools()` para obtener la lista de
todas las herramientas que el agente puede utilizar. Esta lista se
construye instanciando cada herramienta y pasándole la referencia al
`Agent` (para que puedan acceder a configuración, rutas, persistencia,
etc.). Algunas herramientas solo se añaden si la configuración proporciona
las claves API necesarias (por ejemplo, `TavilyWebSearchTool` solo se
incluye si hay una clave de Tavily configurada).

Cada herramienta se registra en un mapa interno (`availableTools`) junto
con un flag `active`. Este flag determina si la herramienta está
disponible para que el modelo la invoque. Por defecto, su valor se
inicializa con `isAvailableByDefault()`, un método que cada herramienta
implementa para indicar si debería estar activa en el primer arranque.

### 5.2. Sincronización con la configuración del usuario

El estado de activación de las herramientas no es estático. El usuario
puede decidir, a través de la interfaz de configuración del agente, qué
herramientas quiere tener habilitadas en cada momento. Esta preferencia se
almacena en la configuración persistente del agente (`settings.json`) bajo
la clave `reasoning/active_tools`, como una lista de elementos con nombre
técnico de la herramienta y un flag booleano.

El método `refresh_available_tools()` se encarga de sincronizar el mapa
interno con esta configuración. Recorre la lista persistida y, para cada
herramienta que aparece en ella, ajusta su flag `active` al valor
almacenado. Las herramientas que no figuran en la configuración conservan
su estado por defecto.

Esta sincronización se ejecuta durante el arranque del servicio y también
cuando se dispara la acción `REFRESH_REASONING_TOOLS`, permitiendo que los
cambios en la configuración surtan efecto sin necesidad de reiniciar el
agente.

### 5.3. Exposición al modelo

Cuando el `eventDispatcher` construye el contexto para enviar al LLM,
necesita proporcionar una lista de especificaciones de herramientas
(`ToolSpecification`) que el modelo puede invocar. Esta lista se genera a
partir del mapa `availableTools`, filtrando:

- Las herramientas que están activas (`active == true`).
- Las herramientas que están permitidas por `AgentAccessControl` en el
  contexto actual.

Cada herramienta sabe cómo generar su propia especificación a través del
método `getSpecification()`, que devuelve un objeto con el nombre, la
descripción y el esquema JSON de sus parámetros. LangChain4j se encarga
luego de serializar estas especificaciones en el formato que el modelo de
lenguaje espera (por ejemplo, el formato de function calling de OpenAI).

### 5.4. Ejecución de herramientas

Cuando el modelo responde con una o más solicitudes de ejecución
(`ToolExecutionRequest`), el `eventDispatcher` itera sobre ellas y, para
cada una, invoca al método `executeTool()`.

Este método realiza una secuencia de operaciones:

**Localización de la herramienta**

Busca en el mapa `availableTools` la herramienta cuyo nombre coincida con
el solicitado. Si no existe, devuelve un mensaje de error.

**Validación de seguridad**

Si la herramienta tiene un modo (`getMode()`) distinto de `MODE_READ` (es
decir, `MODE_WRITE` o `MODE_EXECUTION`), y el control de acceso del agente
requiere confirmación humana (`isHumanConfirmationRequired()`), se
solicita autorización al usuario a través de `AgentConsole.confirm()`.

Este paso es crítico: el usuario puede denegar la ejecución, en cuyo caso
la herramienta no se ejecuta y se devuelve un mensaje indicando la
denegación. El modelo recibe ese mensaje como resultado de su llamada y
puede reaccionar en consecuencia (por ejemplo, explicando al usuario que
necesita permiso).

**Ejecución**

Si la validación supera, se invoca al método `execute()` de la
herramienta, pasándole los argumentos en formato JSON (que la herramienta
debe parsear internamente). La ejecución es síncrona y bloquea el hilo del
`eventDispatcher` hasta que retorna. Esto es intencionado: el agente no
debe procesar nuevos estímulos mientras está ocupado realizando una acción
que puede ser costosa o que modifica el estado del sistema.

**Resultado**

El método devuelve una cadena de texto que puede ser:
- El resultado exitoso de la operación (por ejemplo, el contenido de un
  archivo leído, la confirmación de que se envió un correo).
- Un mensaje de error si algo falló durante la ejecución.
- Un mensaje de denegación si el usuario no autorizó la operación.

Este texto se convierte en un `ToolExecutionResultMessage` que se añade a
la sesión y se persiste como un `Turn`. En la siguiente iteración del bucle
interno, el modelo recibirá este resultado y podrá decidir si necesita
ejecutar más herramientas o si ya puede responder al usuario.

### 5.5. Herramientas de memoria: un caso particular

Dentro del catálogo de herramientas, hay un subconjunto que se considera
"de memoria" (tipo `TYPE_MEMORY`). Estas herramientas (como `lookup_turn`
o `search_full_history`) no modifican el estado externo, sino que
recuperan información de la propia base de datos de la conversación.

El `ReasoningService` las trata de forma especial en un solo aspecto:
cuando se persiste el turno de ejecución de una herramienta de memoria, se
le asigna el tipo `lookup_turn` en lugar de `tool_execution`. Esta
distinción es puramente semántica y facilita la consulta posterior del
historial, pero no afecta al flujo de ejecución.

### 5.6. Herramientas y la configuración de identidad

Hay un grupo de herramientas que no operan sobre el sistema de archivos ni
sobre redes externas, sino sobre la propia identidad del agente:
`ConsultEnvironTool`, `ListSkillsTool`, `LoadSkillTool`. Estas herramientas
permiten al modelo acceder bajo demanda a la información densa que no se
inyecta en el prompt de sistema por defecto (para ahorrar tokens). Su
registro y activación siguen las mismas reglas que cualquier otra
herramienta, pero su función es específicamente la de extender la
"consciencia" del agente con conocimiento contextual que solo se carga
cuando resulta relevante.

### 5.7. Herramientas y el ciclo de vida de la sesión

Un aspecto importante es que las herramientas no tienen estado propio que
persista entre invocaciones (salvo que ellas mismas gestionen su propia
persistencia). Cada ejecución es independiente y recibe todos los
parámetros necesarios en la llamada. Esto simplifica el modelo de
concurrencia y evita efectos secundarios no deseados entre distintas
rondas de razonamiento.

La única excepción a esta regla son las herramientas que modifican el
sistema de archivos: sus efectos persisten, obviamente, pero el
`ReasoningService` no guarda ningún estado adicional sobre ellas. La
responsabilidad de mantener la coherencia recae en la propia herramienta,
que utiliza el sistema RCS integrado para mantener un historial de cambios
y que invoca al `AgentAccessControl` para acceder a los recursos.

En conjunto, el sistema de herramientas de Noema equilibra dos necesidades
contrapuestas: por un lado, ofrecer al modelo un amplio abanico de
capacidades para que pueda ser útil; por otro, mantener la seguridad y el
control en manos del usuario, que puede desactivar herramientas que no
desea utilizar y debe confirmar explícitamente cualquier operación que
pueda tener efectos destructivos.

## 6. La sesión activa (`Session`)

La `Session` es el componente que materializa la memoria de trabajo del
agente. Mientras que `SourceOfTruth` almacena la conversación de forma
inmutable y permanente, y `MemoryService` consolida el pasado lejano en
resúmenes narrativos, la `Session` mantiene vivo el presente inmediato:
los mensajes que acaban de intercambiarse, las herramientas que se han
ejecutado en el turno actual, y toda la información que el modelo necesita
tener a mano para responder con coherencia.

Su diseño responde a una tensión fundamental: el modelo de lenguaje tiene
una ventana de contexto limitada (medida en tokens), pero la conversación
puede extenderse indefinidamente. La `Session` es el punto donde se
gestiona esa limitación, reteniendo solo lo necesario y colaborando con
`MemoryService` para compactar el resto cuando se alcanza un umbral.

### 6.1. Estructura interna

La `Session` mantiene dos estructuras de datos que evolucionan en paralelo:

**La lista de mensajes (`messages`)**

Es una secuencia ordenada de objetos `ChatMessage` de LangChain4j. Esta
lista es la fuente de verdad para el historial inmediato que se enviará al
modelo. Contiene todo tipo de mensajes:

- `UserMessage`: entradas del usuario (directas) o simulaciones de llamadas
  a herramientas (`pool_event`).
- `AiMessage`: respuestas del modelo, incluyendo tanto texto como
  solicitudes de ejecución de herramientas.
- `ToolExecutionResultMessage`: resultados de herramientas ejecutadas.
- `SystemMessage`: ocasionalmente, aunque en la `Session` no se almacena el
  prompt de sistema (se construye dinámicamente en cada consulta).

Cada vez que se produce una interacción (un mensaje del usuario, una
respuesta del modelo, un resultado de herramienta), se añade un nuevo
elemento al final de esta lista.

**El mapa de trazabilidad (`turnOfMessage`)**

Este mapa asocia cada posición en la lista de mensajes con el
identificador del `Turn` persistido que originó ese mensaje. No todos los
mensajes tienen un turno asociado inmediatamente: cuando se añade un
mensaje por primera vez, aún no se ha persistido el turno correspondiente.
Es en el momento de `consolideTurn()` cuando se establece la asociación.

La clave del mapa es el índice en la lista (`Integer`), y el valor es un
objeto `ChatMessageInfo` que contiene, por ahora, únicamente el `turnId`.
Este diseño permite:

- Saber qué parte del historial ya ha sido persistida y qué parte es aún
  efímera.
- Identificar, durante la compactación, qué mensajes comparten el mismo
  turno para no romper bloques semánticos.
- Trazar desde un mensaje en memoria hasta su registro inmutable en la base
  de datos.

### 6.2. El ciclo de vida de un mensaje: backfill

Cuando se añade un mensaje a la sesión (mediante `add()`), simplemente se
coloca al final de la lista. El mapa `turnOfMessage` no se actualiza en ese
momento porque aún no se ha persistido el turno.

La asociación se establece más tarde, cuando se llama a
`consolideTurn(Turn turn)`. Este método recibe el turno que acaba de
persistirse en `SourceOfTruth` y realiza una operación de **backfill**:
recorre la lista de mensajes desde el final hacia atrás, asignando el
`turnId` a todos los mensajes que aún no tienen un turno asociado, hasta
que encuentra uno que ya lo tiene.

Este mecanismo es clave para entender la relación entre la sesión y la
persistencia. Por ejemplo, cuando el modelo responde con texto, ocurre lo
siguiente:

1. Se añade el `AiMessage` con el texto a la sesión (`add()`).
2. Se persiste el turno correspondiente en `SourceOfTruth`.
3. Se llama a `consolideTurn()` con ese turno, que asignará el `turnId`
   al `AiMessage` recién añadido y también a cualquier mensaje anterior
   que pudiera haber quedado sin consolidar (por ejemplo, si una
   herramienta se ejecutó y su resultado aún no tenía turno asignado).

De esta forma, la sesión mantiene siempre una trazabilidad completa hacia
los turnos persistidos, aunque la consolidación ocurra de forma diferida.

### 6.3. Construcción del contexto para el modelo

El método `getContextMessages()` es el responsable de ensamblar el bloque
de mensajes que se enviará al LLM en cada consulta. Su implementación
refleja la estrategia de gestión de contexto del agente:

**Prompt de sistema**

Si se proporciona un `systemPrompt` (que normalmente es el resultado de
`getBaseSystemPrompt()`), se añade como primer elemento de la lista. Este
prompt contiene la identidad del agente, las reglas operativas, y los
índices de referencia del entorno. Es el mismo para todas las consultas de
una sesión, aunque puede cambiar si se recarga la configuración.

**CheckPoint histórico**

Si existe un `activeCheckPoint` (es decir, la memoria consolidada de
conversaciones anteriores), se añade un `SystemMessage` que contiene su
resumen. Este resumen se presenta como un bloque de texto que comienza con
"--- INICIO DEL RELATO ---" y termina con "--- FIN DEL RELATO ---",
dejando claro al modelo que se trata de información consolidada del
pasado, no de la conversación inmediata.

> **Importante**: Los puntos de guardado no se concatenan. Cada nuevo
> checkpoint reemplaza al anterior en el contexto activo del agente. El
> prompt del sistema solo incluye el checkpoint más reciente, que ya
> contiene la esencia consolidada de toda la conversación previa. Los
> checkpoints antiguos se conservan en disco para trazabilidad, pero no se
> inyectan en el contexto.

**Mensajes de la sesión**

A continuación se añaden todos los mensajes almacenados en la lista
`messages`. Estos representan el historial inmediato, desde el último
punto de compactación hasta el momento actual.

**Percepción temporal pasiva**

Antes de devolver la lista completa, `getContextMessages()` comprueba si ha
pasado más de una hora desde la última interacción (almacenada en
`lastInteractionTime`). Si es así, y además el último mensaje de la sesión
es de tipo `UserMessage` (es decir, la última actividad fue del usuario),
se inyecta un evento de sensor de reloj. Este evento se añade como dos
mensajes consecutivos: un `AiMessage` simulando la llamada a `pool_event` y
un `ToolExecutionResultMessage` con el texto "Ha pasado [tiempo] desde la
última interacción con el usuario". El modelo recibe así una señal
temporal que le permite contextualizar su respuesta (por ejemplo, saludar
al usuario tras una larga ausencia).

### 6.4. Compactación: marcas y eliminación

La `Session` proporciona la infraestructura para que el `ReasoningService`
pueda identificar qué parte del historial debe compactarse y eliminarse.
Para ello expone tres métodos fundamentales:

**`getOldestMark()`**

Devuelve una `SessionMark` correspondiente al mensaje más antiguo de la
sesión que tiene un `turnId` asociado. Es el punto de inicio del bloque a
compactar. Si no hay ningún mensaje consolidado, devuelve `null`.

**`getCompactMark()`**

Determina el punto de corte para la compactación. La estrategia actual es
sencilla: toma la mitad de la lista de mensajes (`size() / 2`) y ajusta
hacia atrás hasta encontrar un mensaje consolidado. Luego avanza hasta el
final del bloque del mismo `turnId` para no romper la secuencia de un mismo
turno (que puede constar de varios mensajes: llamada a herramienta,
resultado, etc.). Este punto de corte asegura que la compactación afecte
aproximadamente a la mitad más antigua de la sesión.

**`remove(SessionMark mark1, SessionMark mark2)`**

Elimina de la sesión todos los mensajes comprendidos entre `mark1` y
`mark2` (inclusive). La operación es delicada porque hay que reindexar el
mapa `turnOfMessage` para los mensajes que quedan. El método:

- Ordena las marcas para asegurar que `mark1` es el índice menor.
- Calcula el desplazamiento (`offset = idx2 - idx1 + 1`).
- Crea un nuevo mapa donde los mensajes anteriores al corte conservan su
  índice original.
- Los mensajes posteriores al corte se insertan en el nuevo mapa con su
  índice reducido en `offset`.
- Finalmente, elimina físicamente los mensajes de la lista `messages` y
  sustituye el mapa antiguo por el nuevo.

Esta operación es atómica desde la perspectiva de la sesión: una vez
ejecutada, los mensajes compactados desaparecen y no volverán a formar
parte del contexto.

**`getNewestMark()`**

Devuelve una `SessionMark` correspondiente al **mensaje consolidado más
reciente** de la sesión (el de mayor índice que tiene un `turnId`
asociado). Esta marca se utiliza exclusivamente en la acción de depuración
`COMPACT_REASONING_FULL_SESSION`, que fuerza una compactación **total** de
todo el historial consolidado, desde el mensaje más antiguo
(`getOldestMark()`) hasta el más reciente (`getNewestMark()`). A
diferencia de `getCompactMark()`, que selecciona aproximadamente la mitad
de la sesión para una compactación incremental, `getNewestMark()` abarca
el bloque completo, permitiendo al usuario (o al desarrollador) consolidar
toda la conversación en un único punto de guardado, por ejemplo, antes de
reiniciar el agente o para liberar memoria de trabajo por completo.

### 6.5. Umbral de compactación

La decisión de cuándo compactar se basa en `needCompaction()`. Este
método:

- Recoge todos los valores únicos de `turnId` en el mapa `turnOfMessage`.
- Si el número de turnos únicos supera un umbral configurable, devuelve
  `true`.

El umbral se lee de la configuración bajo la clave
`memory/compaction_turns`. Si no está definido, se establece un valor por
defecto de 40 turnos. La elección de un umbral basado en número de turnos,
y no en tokens, es una simplificación intencionada. Es una limitación
conocida: en conversaciones con herramientas que devuelven grandes
volúmenes de texto, el contexto podría saturarse antes de alcanzar el
umbral de turnos. Una mejora futura podría combinar ambos criterios.

### 6.6. Persistencia de la sesión

La sesión no es solo un objeto en memoria: se serializa a disco tras cada
modificación. El archivo `active_session.json` en el directorio de datos del
agente contiene una representación completa de la lista de mensajes y el
mapa de trazabilidad.

El mecanismo de guardado utiliza un patrón de escritura atómica para evitar
corrupción:

1. Se escribe el estado en un archivo temporal
   (`active_session.json.tmp`).
2. Se mueve (renombra) el temporal al archivo definitivo mediante
   `Files.move()` con la opción `ATOMIC_MOVE`.

Si la JVM falla durante la escritura, el archivo temporal puede quedar
incompleto, pero el definitivo conserva la versión anterior válida. Esto
garantiza que, al reiniciar, el agente recupere siempre un estado
consistente, aunque pueda perder la última operación que no llegó a
completarse.

La serialización utiliza Gson con adaptadores personalizados para manejar
los tipos polimórficos de LangChain4j (`ChatMessage` y `Content`). Cada
mensaje se guarda con un campo `type` que indica su clase concreta,
permitiendo la deserialización correcta.

### 6.7. Acceso externo

La `Session` no es un componente público del agente; es interna al
`ReasoningService`. Sin embargo, el servicio expone sus mensajes a través
del método `getMessages()` (utilizado principalmente para depuración) y
proporciona la funcionalidad de contexto a través de `getContextMessages()`.
El resto de la interacción con la sesión ocurre exclusivamente dentro del
`eventDispatcher`, siguiendo el flujo descrito en la sección anterior.

En resumen, la `Session` es el puente entre la inmediatez de la
conversación y la persistencia duradera. Su diseño permite mantener en
memoria solo lo necesario para el siguiente turno, compactar el pasado
cuando se acumula demasiado, y recuperar el estado exacto tras un reinicio,
todo ello sin que el modelo de lenguaje tenga que gestionar explícitamente
los límites de su propia ventana de contexto.

## 7. `SourceOfTruth`: el repositorio permanente

Si `Session` es la memoria de trabajo del agente, `SourceOfTruth` es su
**archivo histórico inmutable**. Este componente centraliza toda la
persistencia duradera: los turnos de conversación, los puntos de control
(checkpoints) y los embeddings vectoriales asociados. Su implementación se
apoya en una base de datos H2 embebida y en el sistema de archivos para el
contenido textual extenso.

### 7.1 Tablas y política de almacenamiento

La base de datos `memory.mv.db` (ubicada en `var/lib/`) contiene dos tablas
principales:

- **`turnos`**: registra cada interacción atómica (mensajes de usuario,
  respuestas del modelo, ejecuciones de herramientas). Sus columnas más
  relevantes son:
  - `id`: entero autoincremental.
  - `timestamp`: momento del evento.
  - `contenttype`: tipo de turno (`chat`, `tool_execution`,
    `tool_execution_summarized`, `lookup_turn`).
  - `text_user`, `text_model_thinking`, `text_model`: los textos
    intercambiados.
  - `tool_call`, `tool_result`: JSON de la llamada a herramienta y su
    resultado.
  - `embedding_blob`: representación binaria del embedding semántico
    (BLOB).

  Una política importante es el **truncado de resultados largos**: si
  `tool_result` supera los 2 KB, se reemplaza por un objeto JSON con
  metadatos (`"original_size_chars": ...`). Esto evita que la base de
  datos almacene textos masivos que rara vez se recuperan completos.

- **`checkpoints`**: almacena únicamente los metadatos de los puntos de
  guardado (`id`, `cp_first`, `cp_last`, `timestamp`). El contenido
  textual (resumen + relato) se guarda en archivos `.md` independientes
  dentro de `var/lib/checkpoints/`. Este diseño híbrido mantiene la base
  de datos ligera mientras el contenido narrativo sigue siendo fácilmente
  inspeccionable.

### 7.2 Gestión de IDs y contadores

Tanto los turnos como los checkpoints utilizan un `Counter` que se
inicializa consultando `SELECT MAX(id)` de la tabla correspondiente. Así se
garantiza que, incluso si la base de datos ha sido manipulada externamente,
los nuevos identificadores sigan la secuencia correcta. El método `get()`
del contador es `synchronized` y simplemente incrementa el valor en
memoria, lo que es suficiente para una aplicación de un solo proceso.

### 7.3 Persistencia de embeddings

Los vectores de embedding (generados por `EmbeddingsService`) se
serializan a `byte[]` mediante `toBytes()` y se almacenan en la columna
`embedding_blob`. Durante la recuperación, se deserializan con
`fromBytes()`. Este proceso es transparente para el resto del sistema.

### 7.4 Búsqueda semántica en el historial

El método `getTurnsByText(String query, int maxResults)` implementa la
búsqueda por similitud que utiliza la herramienta `search_full_history`.
Dado que H2 no dispone de índices vectoriales nativos, la estrategia es
**escaneo completo más ranking en cliente**:

1. Se vectoriza la consulta mediante `EmbeddingsService`.
2. Se recorren todos los turnos que tienen `embedding_blob` no nulo.
3. Para cada uno, se calcula la similitud coseno y se mantienen los
   `maxResults` mejores mediante `EmbeddingFilter` (un min-heap).
4. Se devuelven los turnos ordenados de mayor a menor similitud.

Para volúmenes moderados de turnos (miles) el rendimiento es aceptable,
pero para conversaciones extremadamente largas (decenas de miles) puede
ser un cuello de botella. El código incluye comentarios sobre la
posibilidad de migrar a PostgreSQL con `pgvector` en el futuro.

### 7.5 CSV de depuración

Además de la base de datos, cada turno se vuelca en el archivo `turns.csv`
dentro de `var/lib/`. Este CSV (con cabecera y escapado de comillas) no es
utilizado por la lógica del agente, pero resulta muy útil para depurar el
contenido exacto de la conversación o para alimentar herramientas externas
de análisis.

### 7.6 Creación y guardado de checkpoints

Cuando `MemoryService` genera un nuevo punto de guardado, `SourceOfTruth`:

- Asigna un nuevo ID (mediante el contador de checkpoints).
- Inserta los metadatos en la tabla `checkpoints`.
- Invoca a `CheckPointImpl.saveTextToDisk()` para escribir el contenido
  textual (resumen + viaje) en un archivo `.md` con nombre
  `checkpoint-{id}-{first}-{last}.md`.

La lectura posterior del contenido se realiza bajo demanda (lazy
loading), evitando cargar en memoria todos los checkpoints al arrancar el
agente.

### 7.8 Integración con el bucle de razonamiento

El `ReasoningService` utiliza `SourceOfTruth` en tres momentos críticos:

1. **Persistencia de cada interacción**: tras recibir un mensaje de
   usuario o ejecutar una herramienta, se crea un `Turn` y se añade al
   repositorio.
2. **Recuperación para compactación**: al compactar, se solicitan los
   turnos comprendidos entre dos marcas (`getTurnsByIds`).
3. **Obtención del último checkpoint**: durante el arranque y tras cada
   compactación, se recupera el checkpoint más reciente para inyectarlo
   en el contexto.

En ningún caso `SourceOfTruth` modifica turnos ya escritos; la
inmutabilidad es una propiedad fundamental del historial.

### 7.9 Limitaciones conocidas

- **Sin índices vectoriales nativos**: el escaneo completo puede volverse
  lento a gran escala.
- **Truncado irreversible de resultados largos**: si una herramienta
  devuelve un texto de 2 MB, solo se conservan los metadatos; el
  contenido original se pierde (aunque estuvo disponible en el contexto del
  LLM durante el turno activo). Esto es deliberado para ahorrar espacio.
- **La base H2 no es distribuida**: para entornos con múltiples instancias
  del agente no funcionaría; Noema está diseñado para un solo proceso.

A pesar de estas limitaciones, `SourceOfTruth` cumple su cometido de forma
robusta y transparente, siendo uno de los pilares que permiten la
**continuidad indefinida** de la conversación.

## 8. Compactación de memoria

La compactación es el mecanismo mediante el cual el agente traslada
información de la memoria de trabajo (la sesión activa) a la memoria a
largo plazo (los puntos de control). Responde a una limitación fundamental
de los modelos de lenguaje actuales: su ventana de contexto es finita. Por
muy grande que sea (y las ventanas de millones de tokens ya existen),
siempre habrá un límite. La compactación no intenta eliminar ese límite,
sino gestionarlo de forma inteligente, preservando lo esencial y
descartando lo redundante.

En Noema, la compactación es un proceso colaborativo entre el
`ReasoningService`, que detecta cuándo es necesaria y proporciona los datos
de entrada, y el `MemoryService`, que realiza la transformación narrativa.
Esta separación de responsabilidades permite que la lógica de compactación
pueda evolucionar independientemente del bucle principal.

### 8.1. Cuándo se dispara la compactación

La compactación no ocurre en un momento arbitrario. Se dispara al final de
cada turno, después de que el modelo haya entregado una respuesta textual y
se haya cerrado la interacción. En ese punto, el `eventDispatcher` evalúa
`session.needCompaction()`.

El criterio actual es simple: la sesión necesita compactación cuando el
número de turnos únicos acumulados en ella supera un umbral configurable.
Este umbral se almacena en la configuración bajo la clave
`reasoning/compaction_turns`, con un valor por defecto de 40 turnos.

La elección de un umbral basado en número de turnos (y no en tokens
estimados) es una simplificación deliberada. En la práctica, funciona
razonablemente bien para la mayoría de las conversaciones, pero tiene
limitaciones conocidas: si un turno incluye una herramienta que devuelve
grandes volúmenes de texto (por ejemplo, el contenido de un archivo
extenso), el contexto puede saturarse antes de alcanzar el umbral. Una
mejora futura podría combinar ambos criterios.

### 8.2. El proceso de compactación

Cuando se cumple la condición, el `ReasoningService` invoca a
`performCompaction()`. Este método ejecuta una secuencia de operaciones
cuidadosamente ordenada:

**1. Obtención de las marcas de sesión**

Se recuperan dos marcas de la sesión:
- `oldestMark`: el mensaje más antiguo consolidado (el que tiene un
  `turnId` asociado).
- `compactMark`: el punto de corte, que se calcula aproximadamente en la
  mitad de la sesión, ajustado para no romper un turno por la mitad.

Si alguna de estas marcas es `null`, significa que no hay suficientes
mensajes consolidados para compactar, y el proceso aborta.

**2. Recuperación de los turnos a compactar**

Con los identificadores de turno de ambas marcas, se consulta a
`SourceOfTruth` para obtener todos los turnos comprendidos en ese rango. La
lista incluye tanto los turnos de usuario como los de ejecución de
herramientas y respuestas del modelo.

**3. Generación del nuevo punto de control**

Se invoca a `MemoryService.compact()`, pasándole:
- El último punto de control existente (`activeCheckPoint`, que puede ser
  `null` si es la primera compactación).
- La lista de turnos recuperados.

`MemoryService` utiliza un modelo de lenguaje (puede ser el mismo que el
agente o uno más económico) para generar un nuevo `CheckPoint`. Este
contiene dos elementos:
- Un resumen narrativo que captura la esencia de la conversación
  compactada.
- Un texto de "El Viaje" que preserva la cronología de los eventos con
  mayor detalle.

La generación del punto de control es una operación potencialmente costosa,
ya que implica una o varias llamadas al LLM. Se ejecuta dentro del hilo del
`eventDispatcher`, bloqueando el procesamiento de nuevos eventos hasta que
finaliza. Esto es intencionado: la compactación es parte del turno que
acaba de terminar, y no deben llegar nuevos estímulos hasta que la memoria
esté consolidada.

**4. Persistencia del nuevo punto de control**

El `CheckPoint` generado se añade a `SourceOfTruth` mediante `add()`. La
base de datos H2 almacena el punto de control junto con su marca de tiempo
y el identificador del turno más reciente que incluye.

**5. Limpieza de la sesión**

Con el nuevo punto de control ya persistido, se invoca a
`session.remove(oldestMark, compactMark)`. Este método elimina de la
sesión todos los mensajes comprendidos entre las dos marcas, liberando
memoria y reduciendo el tamaño del contexto que se enviará en futuras
consultas.

**6. Actualización del puntero activo**

Finalmente, `activeCheckPoint` se actualiza al nuevo punto de control. En
la siguiente construcción de contexto, `getContextMessages()` incluirá
este resumen en lugar del anterior.

De esta forma, el checkpoint anterior queda reemplazado en el contexto
activo; los checkpoints antiguos se conservan en disco solo con fines de
trazabilidad, pero nunca se envían al modelo.

### 8.3. El papel de `MemoryService`

El `ReasoningService` no conoce los detalles de cómo se genera el punto de
control. Esta separación es deliberada: permite que la estrategia de
compactación pueda modificarse sin afectar al orquestador principal.

`MemoryService` expone un único método relevante para este proceso:

```java
CheckPoint compact(CheckPoint previous, List<Turn> turns)
```

### 8.4. Implicaciones para el modelo

Desde la perspectiva del modelo de lenguaje, la compactación es invisible.
Cuando se construye el contexto, el punto de control aparece como un bloque
de texto con el formato:

```
--- INICIO DEL RELATO ---
[contenido del resumen]
--- FIN DEL RELATO ---
```

El modelo recibe esta información como un mensaje de sistema, junto con los
mensajes de la sesión activa. No sabe que el resumen es el resultado de una
compactación; simplemente lo trata como contexto histórico. Esto mantiene
la simplicidad del prompt y evita que el modelo tenga que adaptarse a un
formato especial.

## 9. Percepción temporal pasiva

El agente no solo reacciona a estímulos explícitos, mensajes del usuario,
notificaciones, alarmas, sino que también es consciente del paso del
tiempo. Esta percepción temporal no depende de un sensor activo que emita
eventos periódicos; es un mecanismo pasivo que se activa cuando el agente
va a construir el contexto para el modelo, justo antes de cada consulta al
LLM.

Su objetivo es simple pero potente: si ha transcurrido un lapso
significativo desde la última interacción, el agente informa al modelo de
esa circunstancia. De esta forma, cuando el usuario retoma una
conversación que había quedado suspendida horas o días atrás, el modelo
puede contextualizar su respuesta, saludar adecuadamente, o retomar el hilo
con la conciencia de que ha pasado tiempo.

### 9.1. El mecanismo de inyección

La percepción temporal se materializa dentro del método
`getContextMessages()` de `Session`. Durante la construcción del contexto
que se enviará al modelo, el método realiza las siguientes comprobaciones:

1. **Consulta la última interacción**: mantiene un campo
   `lastInteractionTime` que se actualiza cada vez que se construye el
   contexto (es decir, cada vez que el agente está a punto de razonar).

2. **Comprueba el tipo del último mensaje**: solo inyecta la percepción
   temporal si el último mensaje en la sesión es un `UserMessage`. Esto
   asegura que el sensor de tiempo se active después de una interacción
   humana, no después de una respuesta del propio agente o de una
   ejecución de herramienta.

3. **Calcula el tiempo transcurrido**: si la diferencia entre el momento
   actual y `lastInteractionTime` supera una hora (el umbral está fijado
   en 60 minutos), se procede a la inyección.

4. **Genera el mensaje temporal**: se crea un evento de sensor simulado con
   el texto "Ha pasado [tiempo] desde la última interacción con el
   usuario", donde `[tiempo]` se expresa en un formato legible (por
   ejemplo, "2 horas", "3 días").

5. **Añade el evento a la sesión**: al igual que cualquier otro evento
   sensorial, este se añade como dos mensajes consecutivos: un `AiMessage`
   que simula una llamada a `pool_event` (para mantener la coherencia del
   historial) y un `ToolExecutionResultMessage` que contiene el texto del
   evento.

Una vez añadidos estos mensajes, el contexto que recibe el modelo incluye
la información temporal como si el propio agente hubiera consultado sus
sensores y hubiera obtenido esa lectura.

### 9.2. Por qué es pasiva

El término "pasiva" distingue este mecanismo de un sensor activo que
emitiría eventos periódicos independientemente de la actividad del agente.
Un enfoque activo requeriría:

- Un hilo separado que generara eventos cada cierto tiempo.
- Gestión de concurrencia para no interferir con el bucle principal.
- Decidir qué hacer con esos eventos si el agente está procesando otro
  estímulo.

El enfoque pasivo evita toda esta complejidad. No hay hilos adicionales, no
hay colas de eventos saturándose con ticks de reloj, no hay riesgo de que
el modelo reciba decenas de notificaciones de tiempo mientras el usuario no
está interactuando. La percepción temporal solo ocurre cuando el agente va
a responder, y solo si el usuario ha estado ausente.

### 9.3. El formato del mensaje

El texto inyectado es deliberadamente simple y directo: "Ha pasado X desde
la última interacción con el usuario". No se añade información adicional
sobre la hora actual, la fecha, o cualquier otro metadato temporal que el
modelo podría deducir de su propio conocimiento del mundo (o de otras
herramientas como `get_current_time` si las tuviera activas).

La elección del formato busca dos cosas:

- **Minimizar tokens**: el mensaje añade muy poco overhead al contexto.
- **Dejar la interpretación al modelo**: es el LLM quien decide cómo
  reaccionar ante esa información. Puede optar por saludar, por retomar un
  tema anterior, por preguntar si el usuario ha tenido un buen descanso, o
  simplemente ignorarlo si no es relevante.

### 9.4. El umbral de una hora

La elección de una hora como umbral es empírica. Es suficientemente larga
como para no activarse en pausas breves dentro de una conversación fluida,
pero suficientemente corta como para que el modelo pueda detectar ausencias
significativas.

El umbral no es actualmente configurable, aunque podría serlo en el futuro
si se identifican casos de uso que requieran una sensibilidad temporal
diferente (por ejemplo, un agente de monitorización que necesita ser
consciente de lapsos de minutos, o un asistente personal que solo necesita
marcar ausencias de días).

### 9.5. Relación con el sensor de reloj del sistema

Además de este mecanismo pasivo, el agente dispone de un sensor activo
(`SYSTEMCLOCK_SENSOR_NAME`) que puede inyectar eventos de tiempo cuando se
cumplen condiciones específicas (por ejemplo, una alarma programada). La
diferencia fundamental es:

- El **sensor de reloj activo** se utiliza para despertar al agente en un
  momento concreto y ejecutar una acción programada (por ejemplo,
  "recuérdame revisar el correo en 30 minutos").
- La **percepción temporal pasiva** solo añade contexto cuando el agente ya
  va a responder, informándole de que ha pasado tiempo desde la última
  interacción humana.

Ambos mecanismos coexisten y se complementan. El primero da al agente
capacidad de acción autónoma en momentos concretos; el segundo le da
conciencia situacional sobre el contexto temporal de la conversación.

### 9.6. Implicaciones para la experiencia de usuario

Desde la perspectiva del usuario, este mecanismo contribuye a la sensación
de que el agente "está presente" incluso cuando no se le habla. Si se
retoma una conversación horas después, el agente puede saludar con
naturalidad, retomar el hilo, o incluso comentar el tiempo transcurrido
sin que el usuario tenga que recordarle dónde se quedaron.

Es un pequeño detalle, pero refuerza la ilusión de continuidad y
consciencia que caracteriza a un agente autónomo frente a un simple
procesador de comandos. El usuario no necesita decir "sigo donde estábamos
ayer"; el agente ya lo sabe porque ha percibido el paso del tiempo.

## 10. Seguridad y control de acceso

El agente Noema tiene la capacidad de ejecutar comandos en el sistema,
modificar archivos y acceder a recursos externos. Estas capacidades son
necesarias para que sea útil, pero también representan riesgos
potenciales. El sistema de seguridad está diseñado para equilibrar dos
objetivos: dar al agente suficiente autonomía para realizar tareas
complejas, y mantener al usuario en control de las operaciones que
podrían tener efectos destructivos o invasivos.

La seguridad se implementa en dos niveles: un control de acceso
estructural que define qué está permitido en cada contexto, y un mecanismo
de confirmación humana que requiere autorización explícita para
operaciones sensibles.

### 10.1. `AgentAccessControl`: la política de permisos

`AgentAccessControl` es el componente que define qué recursos puede tocar
el agente y en qué condiciones. No es parte del `ReasoningService`, sino un
servicio independiente que este consulta antes de ejecutar cualquier
herramienta.

Su responsabilidad principal es gestionar el **acceso al sistema de
archivos**. El agente opera dentro de un espacio de trabajo (workspace) que
contiene su configuración, sus bases de datos y sus archivos de trabajo.
Por defecto, todas las operaciones de lectura y escritura están
restringidas a este espacio. Sin embargo, muchas tareas útiles requieren
acceder a archivos fuera del workspace: leer un documento en el directorio
del usuario, escribir un informe en el escritorio, etc.

Para ello, `AgentAccessControl` mantiene listas de rutas permitidas:

- **Rutas de lectura permitidas**: directorios o archivos específicos a los
  que el agente puede acceder aunque estén fuera del workspace.
- **Rutas de escritura permitidas**: directorios donde el agente puede crear
  o modificar archivos fuera del workspace.
- **Rutas explícitamente prohibidas**: incluso dentro de áreas permitidas,
  ciertas rutas pueden estar bloqueadas (por ejemplo, directorios de
  sistema críticos).

La configuración de estas listas se almacena en `settings.json` y puede
ser modificada por el usuario. Esto permite, por ejemplo, dar al agente
acceso a la carpeta `Documentos` para leer archivos, pero prohibirle
escribir en `Documentos/Finanzas` si se considera una zona sensible.

Además del control de rutas, `AgentAccessControl` puede restringir
herramientas específicas en función del contexto, aunque esta capacidad
está menos desarrollada en la implementación actual.

### 10.2. El modo de las herramientas

Cada herramienta implementa el método `getMode()`, que devuelve uno de tres
valores:

- **`MODE_READ`**: operaciones que solo leen información y no modifican el
  estado del sistema. Leer un archivo, consultar una API, buscar en el
  historial. Estas herramientas no requieren confirmación humana (aunque
  pueden estar restringidas por `AgentAccessControl`).
- **`MODE_WRITE`**: operaciones que modifican archivos o configuración.
  Escribir un archivo, aplicar un parche, mover o eliminar. Estas
  herramientas requieren confirmación humana.
- **`MODE_EXECUTION`**: operaciones que ejecutan comandos en el sistema
  operativo. Son las más peligrosas y siempre requieren confirmación
  humana.

Esta clasificación es declarativa: es el desarrollador de la herramienta
quien asigna el modo basándose en lo que la herramienta hace. Un error en
esta clasificación podría llevar a que una herramienta peligrosa se ejecute
sin confirmación, por lo que la revisión de los modos es parte del control
de calidad del código.

### 10.3. Confirmación humana

Cuando el `eventDispatcher` recibe una solicitud de ejecución de
herramienta, y esa herramienta tiene un modo distinto de `MODE_READ`, se
activa el mecanismo de confirmación:

1. **Verificación de requisito**: se consulta a
   `AgentAccessControl.isHumanConfirmationRequired()`. Si esta condición
   es `false` (por ejemplo, en entornos headless o en modo de confianza
   total), la confirmación se omite.

2. **Solicitud al usuario**: se invoca a `AgentConsole.confirm()` con un
   mensaje que describe la herramienta y los argumentos que se van a
   ejecutar. El mensaje tiene un formato claro: "El agente quiere ejecutar
   la herramienta: [nombre]\nArgumentos: [argumentos]\n¿Autorizar?"

3. **Espera de respuesta**: la llamada a `confirm()` es bloqueante. La
   implementación de `AgentConsole` determina cómo se presenta la solicitud
   al usuario: puede ser un diálogo modal en la interfaz gráfica, una
   pregunta en la línea de comandos, o incluso una respuesta automática en
   entornos de prueba.

4. **Decisión**: si el usuario responde afirmativamente, la herramienta se
   ejecuta. Si deniega, se devuelve un mensaje de error que se inyecta en la
   conversación como resultado de la herramienta, y el modelo recibe ese
   mensaje en lugar del resultado real.

La confirmación no es un simple "sí/no". El mensaje incluye los argumentos
exactos que la herramienta va a utilizar, lo que permite al usuario evaluar
el riesgo. Por ejemplo, si la herramienta `file_write` va a sobrescribir un
archivo importante, el usuario puede ver la ruta y decidir si lo permite.

### 10.4. La abstracción `AgentConsole`

La confirmación humana depende de `AgentConsole`, una interfaz que desacopla
al `ReasoningService` del mecanismo concreto de interacción con el usuario.
`AgentConsole` define métodos para:

- Mostrar mensajes del sistema (`printSystemLog`, `printSystemError`).
- Mostrar respuestas del modelo (`printModelResponse`).
- Solicitar confirmación (`confirm`).

Las implementaciones de `AgentConsole` pueden ser muy diferentes:

- **`SwingConsole`**: muestra los mensajes en una ventana gráfica con áreas
  de texto y utiliza diálogos modales para las confirmaciones.
- **`TerminalConsole`**: imprime en la salida estándar y lee de la entrada
  estándar (usando JLine3 para manejar edición multilínea).
- **`HeadlessConsole`**: implementación "tonta" que registra los mensajes
  pero no interactúa con el usuario, devolviendo respuestas
  predeterminadas (por ejemplo, denegar todas las confirmaciones).

Esta abstracción es fundamental: el `ReasoningService` no necesita saber
si está ejecutándose en un entorno gráfico, en una terminal o sin interfaz.
Simplemente llama a `console.confirm()` y la implementación concreta
resuelve cómo interactuar con el humano.

### 10.5. Seguridad en las operaciones de archivo

Además de los mecanismos generales, las herramientas de manipulación de
archivos incorporan capas adicionales de seguridad:

**Control de versiones automático**: antes de modificar un archivo, las
herramientas (`file_write`, `file_patch`, `file_search_and_replace`)
invocan al sistema RCS (Revision Control System) integrado para hacer un
commit de la versión actual. Esto permite recuperar el estado anterior si la
modificación tiene efectos no deseados, y proporciona un historial de
cambios completo.

**Validación de rutas**: todas las rutas que el agente intenta leer o
escribir pasan por `AgentAccessControl.resolvePath()`, que:
- Normaliza la ruta (resuelve `..`, elimina duplicados).
- Verifica que no intente salir del workspace a menos que esté en una lista
  de permitidas.
- Comprueba que la ruta no esté en la lista de prohibidas.
- Aplica restricciones adicionales según la operación (lectura vs
  escritura).

**Prohibición de escritura en áreas críticas**: ciertas rutas están
bloqueadas por completo, independientemente de las listas de permitidas.
Por ejemplo, las carpetas de configuración del agente (`var/config`,
`var/identity`) no pueden ser modificadas por herramientas de escritura
para evitar que el agente altere su propia personalidad sin supervisión.

### 10.6. Ejecución de comandos: el entorno restringido

La herramienta `shell_execute` es particularmente sensible porque permite
ejecutar cualquier comando en el sistema operativo. Para mitigar riesgos,
incorpora varias protecciones:

- **Confirmación humana obligatoria**: es una herramienta
  `MODE_EXECUTION`, por lo que siempre requiere autorización explícita.
- **Sandboxing con firejail**: si el sistema tiene instalado `firejail`, la
  herramienta envuelve el comando en un entorno restringido que limita el
  acceso a archivos, red y procesos.
- **Captura de salida**: la salida estándar y de error se capturan en
  archivos temporales, evitando que el comando pueda interactuar
  directamente con el terminal del usuario.
- **Timeout configurable**: los comandos tienen un límite de tiempo de
  ejecución para evitar que un proceso colgado bloquee al agente.

### 10.7. Filosofía de seguridad

El enfoque de seguridad de Noema se puede resumir en unos pocos principios:

- **Confianza por defecto, confirmación por excepción**: las operaciones de
  lectura no requieren confirmación; las escrituras y ejecuciones sí. Esto
  permite que el agente sea autónomo en tareas seguras sin molestar al
  usuario.
- **El usuario es el árbitro final**: ninguna operación peligrosa puede
  ejecutarse sin autorización explícita, y el usuario tiene la opción de
  denegar en cada caso.
- **Trazabilidad**: todas las herramientas registran lo que hacen, y el
  sistema de control de versiones permite deshacer cambios. Incluso si el
  usuario autoriza una operación que resulta dañina, hay camino de retorno.
- **Separación de poderes**: el `ReasoningService` no toma decisiones de
  seguridad; consulta a `AgentAccessControl` y a `AgentConsole`. Esto
  permite cambiar las políticas sin tocar el núcleo del agente.

Esta arquitectura reconoce una realidad fundamental: un agente autónomo,
por muy bien diseñado que esté, puede cometer errores o ser manipulado. La
seguridad no consiste en impedir que actúe, sino en asegurar que cada
acción que pueda tener consecuencias irreversibles cuente con la
supervisión humana. Es un equilibrio entre autonomía y control que, hasta
ahora, ha demostrado ser práctico y efectivo.

## 11. Puntos de diseño y limitaciones conocidas

El `ReasoningService` de Noema es el resultado de un proceso iterativo de
diseño, donde cada decisión ha buscado un equilibrio entre funcionalidad,
simplicidad y robustez. Como en cualquier sistema complejo, algunas de esas
decisiones introducen limitaciones que merecen ser documentadas
explícitamente. Esta sección recoge tanto los principios que guiaron el
diseño como las áreas donde se sabe que el sistema actual podría mejorar.

### 11.1. El modelo de un solo hilo

**Decisión de diseño**: el `eventDispatcher` se ejecuta en un único hilo
de plataforma, procesando los eventos de forma secuencial y bloqueante.

**Justificación**: esta arquitectura elimina toda complejidad de
concurrencia. No hay condiciones de carrera, no hay necesidad de
sincronización entre múltiples hilos que acceden a la sesión, y el flujo de
ejecución es completamente determinista. Cada evento se procesa hasta
completar todas las rondas de razonamiento antes de pasar al siguiente, lo
que garantiza que la sesión nunca queda en un estado intermedio.

**Limitación**: la ejecución de herramientas que son lentas (por ejemplo,
una búsqueda web que tarda varios segundos) bloquea todo el agente. Durante
ese tiempo, no se atienden nuevos eventos. En la práctica, esto rara vez es
un problema porque el agente no puede hacer dos cosas a la vez de todos
modos, pero podría serlo si se implementaran herramientas de larga duración
que requirieran procesamiento en paralelo.

### 11.2. Compactación basada en número de turnos

**Decisión de diseño**: el umbral de compactación se mide en número de
turnos (40 por defecto), no en tokens estimados.

**Justificación**: medir tokens requeriría estimar el tamaño de cada
mensaje antes de compactar, lo que añade complejidad y llamadas
adicionales al modelo de lenguaje. El número de turnos es un proxy
razonablemente bueno para la longitud de la conversación en la mayoría de
los casos, y es mucho más simple de implementar.

**Limitación**: conversaciones con herramientas que devuelven grandes
volúmenes de texto (por ejemplo, leer un archivo de miles de líneas) pueden
saturar la ventana de contexto mucho antes de alcanzar los 40 turnos. Por el
contrario, conversaciones muy largas pero con mensajes muy cortos podrían
acumular muchos más turnos antes de necesitar compactación. Una mejora
futura sería combinar ambos criterios, compactando cuando se supere un
umbral de turnos **o** un umbral de tokens estimados.

### 11.3. La simulación de `pool_event` y el TODO pendiente

**Decisión de diseño**: los eventos del entorno se inyectan en la sesión
mediante un par de mensajes que simulan una llamada a la herramienta
`pool_event` (un `AiMessage` seguido de un `ToolExecutionResultMessage`).
Esto mantiene la coherencia del historial desde la perspectiva del modelo.

**Justificación**: el modelo de lenguaje opera en un flujo síncrono
(usuario -> IA -> herramienta -> IA). Los eventos asíncronos del entorno no
encajan en este modelo. La simulación resuelve el problema haciendo que
cada evento parezca el resultado de una decisión del propio agente.

**Limitación conocida**: el código contiene un `TODO` que advierte de un
posible fallo cuando el primer mensaje que se envía al LLM es una llamada
simulada a `pool_event`. En ciertas condiciones (probablemente
relacionadas con la inicialización del modelo o con la ausencia de un
mensaje de usuario previo), esta llamada podría fallar. No se ha
reproducido sistemáticamente, pero la advertencia permanece como una
espina clavada que eventualmente habrá que investigar.

### 11.4. Reintentos de herramientas no formalizadas

**Decisión de diseño**: cuando el modelo devuelve
`FinishReason.TOOL_EXECUTION` pero no hay solicitudes de herramientas en la
respuesta, el bucle inyecta un mensaje de usuario que pide reintentar la
llamada, con un límite de tres intentos.

**Justificación**: algunos modelos de lenguaje, especialmente los de
código abierto o configuraciones no estándar, pueden anunciar que van a
usar una herramienta pero no generarla en el formato esperado por
LangChain4j. El reintento es un parche pragmático para mantener la
conversación fluyendo sin que el agente se quede bloqueado.

**Limitación**: es una solución artesanal que no resuelve la causa raíz.
Depende de que el modelo entienda el mensaje de reintento, lo que no
siempre ocurre. Además, tres reintentos pueden ser insuficientes o
excesivos según el modelo. Un enfoque más robusto requeriría un análisis más
fino del formato de respuesta del modelo.

### 11.5. El uso de hilos de plataforma en lugar de virtuales

**Decisión de diseño**: el `eventDispatcher` se ejecuta en un hilo de
plataforma (`Thread.ofPlatform()`), no en un hilo virtual
(`Thread.ofVirtual()`).

**Justificación**: inicialmente se utilizaron hilos virtuales, pero se
encontraron problemas durante la depuración (posiblemente relacionados con
la integración con Swing o con el propio depurador). Se revirtió a hilos
de plataforma para estabilidad. Dado que solo hay un hilo principal y unos
pocos hilos auxiliares efímeros, la ventaja de los hilos virtuales en este
contexto es marginal.

**Limitación**: no es una limitación funcional, sino una decisión
pragmática. Si en el futuro se introdujeran múltiples hilos de
procesamiento o herramientas que requirieran un gran número de hilos
concurrentes, habría que reconsiderar esta elección.

### 11.6. La dependencia de `AgentConsole` para confirmaciones

**Decisión de diseño**: la confirmación humana se realiza a través de
`AgentConsole.confirm()`, una interfaz que puede tener diferentes
implementaciones.

**Justificación**: es una abstracción limpia que desacopla al
`ReasoningService` de la interfaz de usuario concreta. Permite que el mismo
código funcione en modo gráfico, en terminal o en entornos headless.

**Limitación**: la confirmación es bloqueante. Mientras el usuario decide,
el agente no procesa nuevos eventos. Esto es correcto desde la perspectiva
de seguridad, pero puede ser frustrante si el usuario tarda en responder.
No hay un mecanismo de timeout que permita al agente continuar después de
un tiempo de espera.

### 11.7. El prompt de sistema se reconstruye en cada consulta

**Decisión de diseño**: `getBaseSystemPrompt()` se invoca cada vez que se
construye el contexto, aunque el resultado se cachea en
`lastestSystemPrompt`.

**Justificación**: el prompt de sistema puede cambiar en caliente si el
usuario modifica la configuración de identidad (activando o desactivando
módulos). Reconstruirlo desde cero cada vez asegura que el agente use
siempre la configuración más reciente.

**Limitación**: la reconstrucción tiene un costo, aunque es pequeño
(concatenación de cadenas, lectura de archivos). 

### 11.8. Ausencia de monitorización de tokens en tiempo real

**Decisión de diseño**: el servicio estima el tamaño del contexto
(`estimateMessagesTokenCount()`, `estimateToolsTokenCount()`) pero no
utiliza esta información para decisiones en tiempo real (por ejemplo, para
compactar antes de que el contexto exceda un límite).

**Justificación**: la estimación de tokens es una operación que implica
llamar al modelo de lenguaje (LangChain4j proporciona métodos para ello,
pero internamente pueden requerir tokenizadores específicos). Hacerlo en
cada iteración añadiría overhead. Además, el límite de contexto de los
modelos actuales es lo suficientemente grande (128K o 1M tokens) como para
que el umbral de 40 turnos sea un límite más restrictivo en la práctica.

**Limitación**: con modelos de ventana pequeña (por ejemplo, 8K tokens) o
con herramientas que devuelven grandes cantidades de texto, esta
estrategia puede fallar. Es una mejora pendiente para entornos más
restrictivos.

### 11.9. La persistencia de la sesión es por modificación, no por tiempo

**Decisión de diseño**: la sesión se guarda en disco cada vez que se
modifica (al añadir un mensaje, al consolidar un turno, al eliminar
mensajes compactados).

**Justificación**: garantiza que, tras cualquier interrupción, el estado
recuperado sea el último antes de la operación actual. El uso de escritura
atómica evita corrupción.

**Limitación**: en sesiones muy largas con cientos de mensajes, el archivo
`active_session.json` puede crecer considerablemente, y cada escritura es
una operación de E/S que ralentiza el procesamiento. Una mejora posible
sería utilizar un formato más compacto (por ejemplo, un diario de
operaciones) o diferir las escrituras a intervalos regulares.



## Servicio de Embeddings (`EmbeddingsService`)

### 1. Introducción: el poder de la representación vectorial

Los modelos de lenguaje no entienden el texto como una secuencia de
caracteres; lo transforman en **vectores numéricos** (embeddings) que
capturan su significado semántico. Dos textos similares (aunque usen
palabras distintas) producirán vectores cercanos en el espacio
multidimensional. Esta propiedad es la base de la **búsqueda por
similitud semántica**, una capacidad fundamental para un agente que
necesita recuperar información relevante de su historial o de documentos
sin depender de palabras clave exactas.

Noema integra esta funcionalidad a través de `EmbeddingsService`. Su
cometido es doble: por un lado, proporciona la infraestructura para
**vectorizar texto** (convertir frases en vectores de números reales);
por otro, ofrece herramientas para **comparar vectores** (similitud
coseno) y **recuperar los más similares** a una consulta (búsqueda
top-K). Todo ello se ejecuta completamente en local, sin llamadas a APIs
externas, utilizando un modelo de embeddings ligero y de código abierto.

El servicio es transversal: lo utilizan `SourceOfTruth` para la búsqueda
semántica en el historial de conversación (herramienta
`search_full_history`) y `DocumentsService` para la búsqueda en los
resúmenes de documentos indexados. Sin embeddings, Noema estaría limitado
a búsquedas por palabra clave o expresiones regulares, que son mucho
menos flexibles.

### 2. Arquitectura general: modelo local y utilidades asociadas

`EmbeddingsService` es un servicio más del agente, registrado con el
nombre `"Embeddings"`. Sus componentes principales son:

- **`EmbeddingsServiceImpl`**: implementación concreta. Gestiona la
  carga del modelo, ofrece métodos de vectorización, serialización y
  similitud.
- **Modelo de embeddings**: una instancia de
  `AllMiniLmL6V2EmbeddingModel`, de LangChain4j. Es un modelo de 384
  dimensiones, entrenado por sentence-transformers, optimizado para CPU y
  de tamaño reducido (unos 80 MB en disco).
- **`EmbeddingFilter`**: interfaz que define el contrato para búsquedas
  top-K. Permite añadir candidatos y recuperar los más similares.
- **`EmbeddingFilterImpl`**: implementación con una cola de prioridad
  (min-heap) que mantiene los K elementos con mayor similitud a la
  query.
- **Utilidades de conversión**: métodos `toBytes()` y `fromBytes()` para
  serializar `float[]` a `byte[]` y viceversa, necesarios para almacenar
  vectores en las columnas BLOB de H2.

El servicio se inicia en cuanto el agente arranca (su fábrica siempre
devuelve `true`). Al hacerlo, carga el modelo en memoria, lo que puede
tomar unos segundos la primera vez (la descarga de pesos ocurre
automáticamente). Una vez cargado, permanece residente durante toda la
sesión.

### 3. El modelo de embeddings: ligero, local y sin API externa

Noema no depende de proveedores externos para los embeddings. La
elección recayó en `AllMiniLmL6V2EmbeddingModel` por varias razones:

- **Local**: se ejecuta íntegramente en la JVM, sin necesidad de
  conexión a internet ni de API keys. Esto garantiza la privacidad de los
  datos y la portabilidad.
- **Ligero**: produce vectores de 384 dimensiones (frente a las 1536 de
  OpenAI o las 768 de otros modelos). Suficiente para tareas de similitud
  semántica moderada, con un consumo de memoria y CPU razonable.
- **Open source**: basado en el modelo `all-MiniLM-L6-v2` de
  sentence-transformers, con licencia Apache 2.0.
- **Integración sencilla**: LangChain4j proporciona el `EmbeddingModel`
  listo para usar, sin configuración adicional.

El modelo se instancia en `start()` mediante `new
AllMiniLmL6V2EmbeddingModel()`. LangChain4j se encarga de descargar los
pesos (la primera vez) a una caché local. Posteriormente, el modelo se
carga desde disco. El servicio no gestiona la descarga; LangChain4j lo
hace internamente.

Actualmente no se utiliza la integración con Jlama para embeddings,
aunque las dependencias están presentes. El código también contiene
comentarios sobre cómo implementar una función `COSINE_DISTANCE` en H2
(usando un alias de Java), pero no está activa porque H2 no soporta
índices vectoriales nativos.

### 4. Vectorización de texto: el método `embed()`

El método público más importante es `embed(String text)`. Su
implementación es directa:

```java
public synchronized float[] embed(String text) {
    if (StringUtils.isBlank(text)) {
        return null;
    }
    float[] vector = embeddingModel.embed(text).content().vector();
    return vector;
}
```

- Normaliza el texto (si está vacío o es null, retorna null).
- Invoca al modelo de LangChain4j, que devuelve un `EmbeddingResponse`.
- Extrae el vector como `float[]`.

Para casos en los que se necesita el vector serializado (por ejemplo,
para guardar en la base de datos), se proporciona `embedAsBytes()`, que
llama a `embed()` y luego a `toBytes()`.

El método es `synchronized` porque el modelo de LangChain4j puede no ser
thread-safe (depende de la implementación). En la práctica, la
concurrencia es baja (solo se invoca durante la persistencia de turnos o
búsquedas), por lo que no supone un cuello de botella.

### 5. Serialización de vectores: `toBytes()` y `fromBytes()`

H2 (y otras bases de datos) no tienen un tipo nativo para `float[]`, pero
pueden almacenar BLOBs (Binary Large Objects). Para ello,
`EmbeddingsService` ofrece dos métodos de conversión:

- **`toBytes(float[] vector)`**: convierte un array de floats en un
  array de bytes. Utiliza `ByteBuffer.allocate(vector.length * 4)` (cada
  float son 4 bytes), obtiene un `FloatBuffer` y escribe los valores. El
  orden de bytes es el nativo de la máquina (little-endian en la mayoría
  de los casos), pero al ser siempre la misma JVM no hay problemas de
  interoperabilidad.

- **`fromBytes(byte[] bytes)`**: realiza la operación inversa. Envuelve
  el array de bytes en un `ByteBuffer`, obtiene un `FloatBuffer` y lee
  los valores en un nuevo `float[]`. Si `bytes` es null, retorna null.

Esta serialización se utiliza en `SourceOfTruthImpl` para guardar el
embedding de cada turno en la columna `embedding_blob` y para
recuperarlo después. Ejemplo al persistir:

```java
byte[] blobBytes = (vector != null) ? embedding.toBytes(vector) : null;
ps.setBytes(9, blobBytes);
```

Y al leer:

```java
float[] dbVec = embedding.fromBytes(rs.getBytes("embedding_blob"));
```

No se aplica compresión, porque los vectores de 384 dimensiones ocupan
apenas 1.5 KB cada uno. Para miles de turnos, el espacio total es
manejable.

### 6. Similitud coseno: el corazón de la búsqueda semántica

La medida de similitud entre dos vectores se calcula mediante la
**similitud coseno**, implementada en el método
`cosineSimilarity(float[] vectorA, float[] vectorB)`:

```java
double dotProduct = 0.0;
double normA = 0.0;
double normB = 0.0;
for (int i = 0; i < vectorA.length; i++) {
    dotProduct += vectorA[i] * vectorB[i];
    normA += Math.pow(vectorA[i], 2);
    normB += Math.pow(vectorB[i], 2);
}
return (normA == 0 || normB == 0)
    ? 0.0
    : dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
```

El resultado es un valor entre -1 y 1:
- **1.0**: vectores idénticos (misma dirección). Textos semánticamente
  equivalentes.
- **0.0**: ortogonales, sin relación semántica.
- **-1.0**: opuestos (raro en embeddings de texto, pero posible si los
  significados son antitéticos).

Esta medida se usa para ordenar los resultados de búsqueda. El código
también contiene (comentado) una función `cosineDistance` (1 -
similitud), que sería útil si se implementara una función SQL en H2 para
filtrar por distancia, pero actualmente no se utiliza.

### 7. Búsqueda top-K con `EmbeddingFilter`

Para evitar tener que calcular la similitud de todos los candidatos en
cada búsqueda (lo cual sería costoso), `EmbeddingsService` proporciona
`EmbeddingFilter`, una interfaz que permite acumular candidatos y
mantener solo los K más relevantes. La implementación concreta es
`EmbeddingFilterImpl`.

Su lógica interna es un **min-heap** (cola de prioridad) que ordena los
elementos por su puntuación de similitud (de menor a mayor). La cabecera
del heap es el peor de los K mejores. Cuando se añade un nuevo candidato:

- Si el heap tiene menos de K elementos, se añade directamente.
- Si tiene K y la similitud del nuevo candidato es mayor que la del peor
  elemento actual, se elimina el peor y se añade el nuevo.
- Si la similitud es menor, se descarta.

El método `add()` devuelve la similitud calculada (útil para
depuración). Una vez añadidos todos los candidatos, `get()` devuelve la
lista ordenada de mayor a menor similitud (invirtiendo el heap, que da el
orden inverso).

También se puede pasar un `minScore` para filtrar candidatos que no
alcancen un umbral mínimo de similitud (por defecto `Double.NaN`, que no
filtra). Esto es útil para búsquedas que requieran una relevancia mínima.

`EmbeddingFilterImpl` se utiliza tanto en
`SourceOfTruth.getTurnsByText()` como en `DocumentsService.search()`.

### 8. Integración con `SourceOfTruth`: búsqueda híbrida

El método `SourceOfTruth.getTurnsByText(String query, int maxResults)` es
el que permite al agente buscar en todo el historial mediante la
herramienta `search_full_history`. Su implementación es un ejemplo
perfecto del uso de `EmbeddingsService`:

1. Obtiene una referencia al servicio de embeddings.
2. Crea un `EmbeddingFilter` para la query con el límite de resultados.
3. Ejecuta una consulta SQL que selecciona todos los turnos que tienen
   `embedding_blob` no nulo.
4. Para cada turno, recupera el blob, lo deserializa a `float[]`
   mediante `embedding.fromBytes()` y lo añade al filtro con
   `search.add(dbVec, turn)`.
5. Finalmente, obtiene la lista de turnos más similares con
   `search.get()`.

Esta estrategia es un **escaneo completo** de la tabla (sin índices).
Para una base de datos con miles de turnos, el coste es aceptable (unos
pocos milisegundos por búsqueda). Para decenas de miles, puede comenzar a
ser lento. Noema asume que el historial de un usuario individual no
crecerá a millones de turnos (al menos en esta fase de prototipo).

El código incluye comentarios sobre cómo se podría migrar a PostgreSQL
con `pgvector` para tener índices reales, pero actualmente no es una
prioridad.

### 9. Integración con Documentos: búsqueda en resúmenes

`DocumentsService` también utiliza `EmbeddingsService` para la búsqueda
semántica en los resúmenes de documentos indexados. El proceso es muy
similar al de los turnos, pero con algunas diferencias:

- Los documentos se almacenan en la tabla `DOCUMENTS` con una columna
  `summary_embedding` BLOB.
- El método `search()` (híbrido) permite combinar un filtro por
  categorías (SQL) con una búsqueda semántica en los resúmenes.
- `EmbeddingFilter` se utiliza de igual modo: se crea con la query, se
  recorren los documentos que pasan el filtro de categorías, se calcula
  la similitud y se mantienen los mejores.

De esta forma, el agente puede encontrar documentos relevantes tanto por
su categoría explícita como por el contenido semántico de su resumen,
sin necesidad de que el usuario conozca las palabras exactas que
aparecen en el texto.

### 10. Limitaciones y posibles mejoras

A pesar de su utilidad, `EmbeddingsService` tiene varias limitaciones que
deben tenerse en cuenta:

- **Modelo de baja dimensión (384)**: es suficiente para similitud
  semántica básica, pero para conceptos muy sutiles o dominios
  especializados, un modelo de mayor dimensión (como `all-mpnet-base-v2`
  con 768 dimensiones o los de OpenAI con 1536) ofrecería mejor
  precisión. El coste sería mayor memoria y tiempo de cálculo.

- **Escaneo completo sin índices**: para bases de datos grandes (decenas
  de miles de turnos o documentos), cada búsqueda puede volverse lenta.
  La solución natural sería migrar a una base de datos con soporte nativo
  de índices vectoriales (pgvector, Milvus, etc.). El código ya tiene
  comentarios al respecto.

- **Sin caché de embeddings de consultas**: si el usuario repite la
  misma búsqueda varias veces, se recalcula el embedding de la query cada
  vez. Una caché trivial podría ahorrar este coste.

- **Búsqueda síncrona y bloqueante**: las búsquedas se ejecutan en el
  mismo hilo del `eventDispatcher`. Si la tabla es muy grande, el agente
  se detiene hasta que termina. Para búsquedas muy pesadas, se podría
  considerar asincronía.

- **Serialización sin compresión**: aunque cada vector ocupa poco, para
  millones de turnos el espacio en disco podría ser significativo. Una
  compresión ligera (por ejemplo, cuantización a 8 bits) reduciría el
  almacenamiento a costa de precisión.

- **Modelo cargado en memoria permanentemente**: los embeddings están
  siempre en RAM, consumiendo entre 100 y 300 MB según la implementación
  de LangChain4j. No se puede descargar el modelo para liberar recursos
  si no se usa.

A pesar de estas limitaciones, `EmbeddingsService` cumple sobradamente su
propósito en el contexto de Noema: proporcionar búsqueda semántica en un
agente local, con un modelo gratuito, sin dependencias externas, y con
un rendimiento aceptable para volúmenes de datos moderados (miles de
turnos, cientos de documentos). Es una pieza clave para que el agente
"recuerde" y "encuentre" información relevante.

# Comunicación Core-UI (Capa de Presentación)

### 1. Filosofía de Separación: Inversión de Dependencias

Uno de los principios arquitectónicos más estrictos de Noema es la
separación absoluta entre el "cerebro" del agente (el Kernel) y su
representación visual. El motor de razonamiento y los servicios no
saben —ni deben saber— si se están ejecutando en un entorno gráfico con
ventanas, en una terminal de texto plano o en un servidor sin interfaz
(headless).

Para lograr esto, Noema utiliza el principio de **Inversión de
Dependencias**: 
* El Kernel expone sus necesidades de salida a través de una interfaz
  mínima: `AgentConsole`.
* La Capa de Presentación (UI) conoce y depende de la interfaz `Agent`,
  actuando como un cliente externo que inyecta estímulos y consume
  resultados, respetando escrupulosamente el ciclo de vida asíncrono y el
  modelo de hilos del motor.

Esta frontera limpia permite cambiar de la interfaz Swing a la de
terminal simplemente inyectando una implementación distinta durante el
arranque, sin alterar una sola línea de lógica de negocio.

### 2. De la UI al Núcleo: `putUsersMessage` y el Callback de Respuesta

Cuando el usuario escribe un mensaje y pulsa *Enter*, la interfaz no
invoca directamente al modelo de lenguaje. En su lugar, el mensaje sigue
el mismo camino que cualquier otra percepción del entorno, integrándose
en el flujo sensorial.

La UI llama a `agent.putUsersMessage(texto, callback)`. Bajo el capó,
este método:
1. Empaqueta el texto como un evento de naturaleza `USER`.
2. Lo inyecta en la cola del `SensorsService`.
3. Esto activa el `sensorLock.notifyAll()`, que despierta inmediatamente
   al hilo del `eventDispatcher` (el bucle de consciencia del agente) que
   estaba dormido.

Para que la UI sepa cuándo el agente ha terminado de pensar (ya que el
procesamiento es asíncrono y puede implicar la ejecución de múltiples
herramientas), se pasa un `SensorEventCallback`. Una vez que el
`ReasoningService` consolida la respuesta final del turno, invoca
`callback.onComplete()`. Es en este momento cuando la interfaz (por
ejemplo, `MainChatPanel`) detiene el cronómetro de "pensando...", oculta
el botón de *Stop* y vuelve a habilitar el área de texto.

### 3. Del Núcleo a la UI: El Contrato `AgentConsole`

Cuando el agente necesita comunicarse con el humano, lo hace disparando
métodos sobre la interfaz `AgentConsole`: `printSystemLog`,
`printModelResponse`, o `printSystemError`. El núcleo se desentiende de
cómo se renderiza esta información.

* **En el entorno gráfico (Swing):** La clase
  `AgentSwingConsoleControllerUsingMultipleJTextPane` intercepta estas
  llamadas. En lugar de volcar texto plano, instancia "burbujas"
  visuales dinámicas (`JBubbleTextPanel`). Si el mensaje es del modelo
  (`printModelResponse`), crea un `JMarkdownPanel` que procesa el texto
  en tiempo real usando `commonmark-java`, renderizándolo como HTML rico
  (tablas, negritas, código con colores). Todo esto se encola de forma
  segura en el *Event Dispatch Thread (EDT)* mediante
  `SwingUtilities.invokeLater()`.
* **En el entorno de terminal (CLI):** La clase `AgentConsoleImpl` toma
  esas mismas llamadas y simplemente las formatea (ej. prefijando `>>>`
  para logs del sistema o `Model:` para respuestas) escribiéndolas
  directamente en el `terminal.writer()` de JLine3.

### 4. El Bloqueo Síncrono: Confirmaciones Humanas (`confirm()`)

El mecanismo de seguridad más importante de Noema —la confirmación antes
de ejecutar operaciones peligrosas— impone un desafío arquitectónico: el
agente debe detenerse por completo hasta que el humano responda.

Cuando `ReasoningService` detecta una herramienta de escritura o
ejecución de comandos, invoca `console.confirm(mensaje)`. **Esta llamada
es deliberadamente síncrona y bloqueante**. El hilo del
`eventDispatcher` se congela a la espera de un booleano.

* **Resolución en Swing:** La UI invoca a la utilidad
  `SwingUtils.getTopWindow()` para encontrar cuál es la ventana o
  diálogo modal que está actualmente en primer plano (top) y lanza un
  `JOptionPane.showConfirmDialog` sobre ella. Al cerrarse el cuadro de
  diálogo, se devuelve `true` o `false` al agente, que reanuda su
  ejecución.
* **Resolución en Consola:** Se invoca
  `lineReader.readLine("... (s/n): ")`. El prompt de la terminal captura
  la entrada por teclado, la normaliza y devuelve el control al hilo
  bloqueado.

### 5. UI Dinámica y Reactiva: `AgentUISettings` y Evaluador de Expresiones

Construir menús de configuración acoplados en código Java hace que
mantener las opciones del agente sea tedioso. Noema resuelve esto
generando sus diálogos de ajustes dinámicamente a partir de un archivo
JSON (`settingsui.json`). 

Este JSON define la estructura de árbol, los campos (combos, rutas,
listas marcables) y los dominios de datos. Las implementaciones
`AgentSwingSettingsImpl` y `AgentConsoleSettingsImpl` leen este esquema y
construyen los controles visuales correspondientes "al vuelo".

Para ir un paso más allá, la interfaz es **reactiva**. Ciertos elementos
deben habilitarse o deshabilitarse según el estado de otros (por ejemplo,
prohibir marcar la herramienta `shell_execute` si en la sección de
seguridad se ha prohibido la ejecución de comandos). En lugar de
"hardcodear" esta lógica, el JSON incluye propiedades como `childEnabled`
con expresiones lógicas en texto plano. La clase `ExpressionEvaluator`
(un pequeño parser recursivo descendente escrito a medida) interpreta
estas expresiones en tiempo de ejecución, actualizando el estado de los
*checkboxes* instantáneamente sin requerir librerías pesadas. *(Nota:
Adicionalmente, el proyecto integra MVEL, pero este se reserva para el
potente entorno interactivo del `DebugPanel`)*.

### 6. Ensamblaje Visual: `AgentUIManager` y `AgentUILocator`

Para cerrar el círculo de la Inversión de Dependencias, la aplicación
necesita arrancar el motor visual antes que el Kernel. Esto se logra
mediante un patrón Service Locator dedicado a la UI: el
`AgentUILocator`.

En el punto de entrada absoluto de la aplicación (`MainGUI` o
`MainConsole`), lo primero que se ejecuta es el registro del gestor
visual:
`AgentUILocator.registerAgentUIManager(new AgentSwingManagerImpl(console));`

Más adelante, cuando la clase `BootUtils.init()` prepara el terreno para
ensamblar el motor del agente, no necesita saber si está en Swing o
JLine. Simplemente pide la consola invocando
`AgentUILocator.getAgentUIManager().createConsole()` y se la inyecta al
núcleo. Esta arquitectura garantiza que las librerías gráficas y las del
núcleo permanezcan en compartimentos estancos, facilitando el testing y
la evolución de ambas capas por separado.

## Las herramientas (AgentTools) y su sistema de paginación

### Introducción: el agente necesita herramientas

Un agente autonomo que solo habla es de utilidad limitada. Para ser
realmente autónomo, Noema debe poder **actuar sobre el mundo**: leer y
escribir archivos, ejecutar comandos, consultar APIs externas, enviar
correos o programar alarmas. Esta capacidad de acción se materializa a
través de las **herramientas** (`AgentTool`), extendiendo su alcance más
allá del procesamiento de texto.

El sistema de herramientas de Noema no es una colección dispersa de
funciones. Responde a un diseño unificado que incluye:

- Un **contrato común** (`AgentTool`) que toda herramienta debe cumplir.
- Un **registro central** gestionado por `ReasoningService`, que expone
  al modelo solo las herramientas activas y permitidas.
- Un **mecanismo de seguridad** (modos `READ`, `WRITE`, `EXECUTION`,
  `WEB`) que, combinado con `AgentAccessControl`, permite o deniega
  operaciones.
- Un **sistema de paginación general** (`AbstractPaginatedAgentTool`) para
  manejar salidas masivas (archivos de miles de líneas, resultados de
  comandos extensos) sin saturar la ventana de contexto del LLM.

Este documento describe la arquitectura de las herramientas, cómo se
declaran, registran y ejecutan, asi como el subsistema de paginación, una
de las piezas más ingeniosas de Noema para sortear las limitaciones de
tokens de los modelos actuales.

### La clase `AgentTool`: nombre, especificación, modos y tipos

Toda herramienta implementa la interfaz `AgentTool`. Los métodos
principales son:

- **`ToolSpecificationBuilder getSpecification()`**: define los
  metadatos que el LLM necesita para invocar la herramienta: nombre,
  descripción y esquema JSON de los parámetros (usando
  `ToolSpecificationBuilder`). Este builder permite añadir parámetros de
  tipo string, entero, número o array de strings de forma declarativa.

- **`String execute(String jsonArguments)`**: contiene la lógica de
  negocio. Recibe los argumentos en formato JSON (que deben parsearse,
  normalmente con Gson) y devuelve un resultado en texto (normalmente
  otro JSON o un mensaje legible).

- **`int getMode()`**: clasifica la herramienta según su peligrosidad:

  - `MODE_READ`: solo consulta información (ej: leer un archivo,
    consultar el tiempo). No requiere confirmación humana.
  - `MODE_WRITE`: modifica el sistema de archivos (escribir, parchear,
    crear directorios).
  - `MODE_EXECUTION`: ejecuta comandos en el shell.
  - `MODE_WEB`: realiza peticiones a internet (búsquedas, descargas).
  Los modos `WRITE`, `EXECUTION` y `WEB` (según configuración) activan la
  confirmación humana.

- **`int getType()`**: distingue entre herramientas operativas
  (`TYPE_OPERATIONAL`) y herramientas de memoria (`TYPE_MEMORY`). La
  distinción afecta al tipo de turno registrado en la base de datos
  (`tool_execution` vs `lookup_turn`), lo que influye en la posterior
  compactación narrativa.

- **`boolean isAvailableByDefault()`**: indica si la herramienta debe
  aparecer activada la primera vez que se inicia el agente. El usuario
  puede luego activarla o desactivarla desde la configuración.

Cada herramienta recibe una referencia a `Agent` en su constructor, lo
que le permite acceder a los servicios (`getService()`), al control de
acceso, a las rutas del sandbox, a la consola, etc.

### Registro y gestión de herramientas (ReasoningService, activación)

`ReasoningService` es el propietario del catálogo de herramientas.
Durante su arranque, recorre todos los servicios registrados
(`AgentService.getTools()`) y añade cada herramienta mediante
`addTool()`. Internamente mantiene un mapa de `availableTools` que
asocia el nombre técnico de la herramienta con un objeto
`AvailableAgentTool` (que contiene la herramienta y un flag `active`).

La activación de cada herramienta sigue estas reglas:

1. Por defecto, se usa `isAvailableByDefault()` de la propia herramienta.
2. Posteriormente, `refresh_available_tools()` lee la configuración de
   `reasoning/active_tools` (una `AgentSettingsCheckedList`) y actualiza
   el flag `active` para las herramientas que aparecen en la lista. Las
   que no aparecen mantienen su valor por defecto.

Cuando se envia una solicitud al LLM, solo se le anuncian las
herramientas que están activas y que `AgentAccessControl.isToolAllowed()`
permite. Asi mismo, cuando el LLM solicita ejecutar una herramienta,
`ReasoningService` solo considera aquellas que están activas y que
`AgentAccessControl.isToolAllowed()` permite (según las políticas
globales de escritura, ejecución o acceso a internet). Esta doble
validación garantiza que el modelo no pueda utilizar una capacidad que el
usuario ha desactivado.

### Ejecución de herramientas: seguridad y flujo

La ejecución de una herramienta ocurre dentro del bucle `eventDispatcher`
de `ReasoningService`, de forma síncrona y bloqueante. El flujo es:

1. El modelo devuelve un `AiMessage` con `toolExecutionRequests`.
2. Por cada solicitud, se busca la herramienta en `availableTools`.
3. Si la herramienta no está activa, se devuelve un mensaje de error.
4. Si el modo es `WRITE`, `EXECUTION` o `WEB` (y la política global lo
   requiere), se pide confirmación al usuario mediante
   `AgentConsole.confirm()`. El mensaje incluye el nombre de la
   herramienta y los argumentos. Si el usuario deniega, la ejecución se
   aborta y se notifica al modelo.
5. Se invoca `tool.execute(jsonArguments)`. La ejecución puede tardar
   segundos o minutos (por ejemplo, un comando shell o una descarga
   pesada). Durante ese tiempo, el hilo del `eventDispatcher` permanece
   bloqueado.
6. El resultado (texto) se envuelve en un `ToolExecutionResultMessage` y
   se añade a la sesión. Además, se persiste un turno de tipo
   `tool_execution` (o `lookup_turn` si la herramienta es de memoria).
7. El modelo, en la siguiente iteración del bucle, recibirá ese
   resultado y podrá decidir si continúa con más herramientas o si da
   una respuesta final al usuario.

Este diseño es intencionadamente simple: no hay paralelismo ni
reintentos automáticos. En este momento, la transparencia y el control
humano son prioritarios frente al rendimiento.

### Catálogo de herramientas por dominio

Hay más de veinte herramientas, agrupadas lógicamente por su función. A
continuación se enumeran con una breve descripción de cada una.

**Herramientas de memoria y sistema**

- `fetch_citation` (`LookupTurnTool`): recupera un turno específico a
  partir de su ID (ej: `{cite:123}`). Permite obtener el contexto exacto
  de una conversación pasada.
- `search_full_history`: realiza una búsqueda semántica en todo el
  historial mediante embeddings. Devuelve los turnos más relevantes para
  una consulta.
- `annotate_observation`: permite al agente guardar una nota o resumen
  que se incluirá en el próximo checkpoint. Útil para fijar hechos
  importantes.
- `pool_event`: herramienta "ficticia" usada internamente por el
  `SensorsService` para inyectar eventos asíncronos en el historial.
- `sensor_status`: consulta el estado de los sensores (activos,
  silenciados, estadísticas).
- `sensor_stop` / `sensor_start`: permite al agente silenciar o
  reactivar canales sensoriales (ej: silenciar Telegram durante una
  tarea concentrada).
- `schedule_alarm`: programa una alarma en el futuro usando lenguaje
  natural (inglés). Cuando se dispara, inyecta un evento sensorial.
- `list_skills`: lista los índices de habilidades procedimentales
  disponibles (archivos `.ref.md` en `var/skills`).
- `load_skill`: carga el manual completo de una habilidad (archivo
  `.md`) para ejecutar un protocolo paso a paso.
- `consult_environ`: recupera un módulo de conocimiento denso del entorno
  (biografía, proyectos) desde `var/identity/environ`.

**Herramientas de archivo (lectura y escritura)**

- `file_find`: busca archivos y directorios usando patrones glob. Los
  resultados se devuelven paginados.
- `file_grep`: realiza una búsqueda de texto (case‑insensitive) en
  archivos o directorios, devolviendo las líneas coincidentes
  (paginado).
- `file_read`: lee el contenido de un archivo de texto. Si es binario,
  avisa y recomienda `file_extract_text`. Soporta paginación.
- `file_write`: escribe o sobrescribe un archivo. Crea los directorios
  padres si no existen. Antes de modificar, si el archivo ya existe, hace
  un commit automático al sistema RCS.
- `file_mkdir`: crea directorios (comportamiento `mkdir -p`).
- `file_patch`: aplica un parche en formato unified diff (`@@ ... @@`).
  Útil para refactorizaciones complejas.
- `file_search_and_replace`: reemplaza un bloque de texto exacto por
  otro. Más simple y seguro que `file_patch` para cambios pequeños.
- `file_extract_text`: extrae el texto de archivos binarios (PDF, DOCX,
  etc.) usando Apache Tika. El resultado se cachea en `var/cache` y se
  sirve paginado.
- `file_history`: muestra el historial de revisiones RCS de un archivo
  (similar a `rlog`).
- `file_recovery`: restaura una versión anterior de un archivo desde el
  historial RCS (similar a `co -r`).

**Herramientas de ejecución**

- `shell_execute`: ejecuta un comando en Bash. Captura la salida
  estándar y de error, la almacena en un archivo temporal y la sirve
  paginada. Incluye confirmación humana obligatoria y soporte opcional
  para `firejail`.

**Herramientas web y utilidades**

- `web_search`: busca en internet mediante Tavily (requiere API key).
  También existe un adaptador para Brave, aunque menos usado.
- `web_get_content`: descarga una URL y extrae el texto limpio usando
  Apache Tika. Soporta paginación.
- `get_current_location`: obtiene la geolocalización aproximada basada
  en la IP pública (usando ip-api.com).
- `get_current_time`: devuelve la fecha, hora y zona horaria actual del
  sistema.
- `get_weather`: consulta el clima actual usando Open-Meteo (sin API
  key). Puede geocodificar una ciudad o usar coordenadas.

**Herramientas de comunicación**

- `email_list_inbox`: lista las cabeceras de los últimos correos (UID,
  remitente, asunto). Es una operación ligera para no saturar el
  contexto.
- `email_read`: lee el cuerpo completo de un correo a partir de su UID,
  limpiando HTML y extrayendo texto con Tika.
- `email_send`: envía un correo electrónico vía SMTP.
- `telegram_send`: envía un mensaje al usuario a través de Telegram
  (requiere chat ID autorizado).

**Herramientas de documentos (RAG estructural)**

- `document_index`: inicia el procesamiento de un documento (PDF, DOCX,
  etc.) para extraer su estructura, resúmenes y categorías. Es asíncrono
  y notifica al agente cuando termina.
- `document_search`: búsqueda híbrida (categorías + similitud semántica
  sobre resúmenes).
- `document_search_by_categories`: filtra documentos por categorías
  exactas.
- `document_search_by_sumaries`: busca en los resúmenes por significado.
- `get_document_structure`: devuelve el índice jerárquico del documento
  en formato XML, con secciones colapsadas.
- `get_partial_document`: inyecta el texto completo solo en las
  secciones solicitadas, permitiendo al agente leer partes concretas sin
  cargar todo el documento.

### El problema de las salidas masivas

Cuando una herramienta ejecuta una operación que produce una cantidad
ingente de texto (por ejemplo, leer un archivo de 50 000 líneas, ejecutar
un comando que genera varios megabytes de log o extraer el texto de un
PDF de 300 páginas), enviar toda esa salida directamente al LLM tiene dos
problemas graves:

- **Saturación de contexto**: la ventana de tokens del modelo se llena
  rápidamente, dejando poco espacio para la conversación.
- **Coste económico**: si se usa un modelo de pago por token, transmitir
  textos masivos resulta prohibitivo.

La solución habitual (truncar y perder información) no es satisfactoria.
Noema aborda el problema con un **sistema de paginación general**. La
herramienta escribe la salida completa en un archivo temporal o en
caché, pero solo envía al LLM un **pequeño fragmento inicial** junto con
una **instrucción (HINT)** para que el modelo pueda solicitar bloques
sucesivos bajo demanda. De esta forma, el LLM decide cuánto leer y
cuándo, manteniendo el control sobre su propio contexto.

### Paginación general: el patrón `AbstractPaginatedAgentTool`

La clase base `AbstractPaginatedAgentTool` encapsula toda la lógica de
paginación. Las herramientas que pueden generar salidas grandes (como
`file_read`, `shell_execute`, `web_get_content`, `file_extract_text`,
`file_find` o `file_grep`) heredan de ella.

El patrón se compone de varios elementos:

- **Escritura completa en un recurso temporal**: la herramienta genera
  su salida y la almacena en un archivo (normalmente en `var/tmp` o
  `var/cache`). El nombre del archivo es único (por ejemplo,
  `out_<uuid>.out`).
- **Obtención de un identificador de recurso**: el método
  `getIdFromPath(Path)` transforma la ruta absoluta en una URI simbólica
  (`tmp://...`, `cache://...` o `user://...`). Este ID es seguro (no
  revela rutas absolutas para temporales o cache) y permite que
  `read_paginated_resource` localice el archivo más tarde.
- **Servicio paginado**: `servePaginatedResource(resourceId, offset,
  limit)` lee las líneas del archivo desde `offset` con un máximo de
  `limit` líneas (por defecto 1000). Calcula el número total de líneas
  (con caché LRU para evitar leer el archivo dos veces) y construye una
  respuesta con cabecera y contenido.
- **Caché de recuento de líneas**: `lineCountCache` (un `LRUMap` de 30
  entradas) almacena el número de líneas y la fecha de modificación del
  archivo para evitar recalcularlo en cada petición.

### 8. Protocolo de respuesta: cabecera, HINT y contenido

El formato de respuesta de cualquier herramienta paginada es **texto
plano con una estructura rígida** que el LLM debe aprender a
interpretar. La respuesta consta de dos partes separadas estrictamente
por `---` en una línea propia:

```
STATUS: ok
EMPTY: false
LINE_RANGE: 0-999
TOTAL_LINES: 50000
HINT: To read the next block, call 'read_paginated_resource' with args:
  {"resource_id": "tmp://out_abc123.out", "offset": 1000, "limit": 1000}
---
(contenido de las primeras 1000 líneas)
```

Los campos de cabecera son:

- `STATUS`: `ok` o `error`.
- `EMPTY`: `true` si no hay contenido (archivo vacío).
- `LINE_RANGE`: líneas incluidas en este bloque (inicio-fin, 0‑indexed).
- `TOTAL_LINES`: total de líneas del recurso (solo si es mayor que el
  bloque).
- `HINT`: si hay más líneas por leer, aparece esta línea con la llamada
  exacta a `read_paginated_resource` que el modelo debe ejecutar para
  obtener el siguiente bloque. El parámetro `resource_id` es el
  identificador simbólico.
- Luego el separador `---` y el contenido textual.

Si el recurso se agota o el bloque es el último, no aparece `HINT`. En
caso de error, la cabecera incluye `STATUS: error` y una descripción del
problema.

Este protocolo es **autónomo**: el modelo no necesita recordar offsets ni
inventar parámetros; el `HINT` contiene todo lo necesario para la
siguiente llamada.

### 9. Identificadores de recurso: `tmp://`, `cache://`, `user://`

Para evitar exponer rutas absolutas del sistema de archivos al LLM (por
seguridad y para simplificar la API), los recursos paginados se
identifican mediante esquemas simbólicos:

- **`tmp://`**: apunta a archivos en `agent.getPaths().getTempFolder()`
  (normalmente `var/tmp`). Ejemplo: `tmp://out_abc123.out`.
- **`cache://`**: apunta a archivos en `agent.getPaths().getCacheFolder()`
  (normalmente `var/cache`). Usado para resultados cacheados de
  extracción de textos o documentos.
- **`user://`**: apunta a archivos dentro del workspace del usuario
  (permite lectura de archivos del proyecto, previa validación por
  `AgentAccessControl`). El path es absoluto pero normalizado, ej:
  `user:///home/usuario/proyecto/src/main.java`.

El método `getIdFromPath()` crea estos identificadores, y
`getPathFromId()` realiza la conversión inversa, comprobando que la
ruta no escape de las carpetas esperadas (protección contra path
traversal). De este modo, el modelo nunca ve rutas completas del
sistema, solo referencias simbólicas que el propio
`AbstractPaginatedAgentTool` resuelve de forma segura.

### 10. La herramienta `read_paginated_resource`

`ReadPaginatedResourceTool` es la única herramienta que debe invocarse
para leer bloques adicionales de un recurso paginado. Su especificación
es intencionadamente simple:

- `resource_id` (obligatorio): el identificador simbólico obtenido del
  `HINT`.
- `offset` (opcional, por defecto 0): línea inicial (0‑indexed).
- `limit` (opcional, por defecto 1000): número máximo de líneas a leer.

Su implementación simplemente llama a `servePaginatedResource()` con
los argumentos recibidos. No contiene lógica de negocio adicional. El
LLM **no debe usar esta herramienta por iniciativa propia**; solo cuando
recibe un `HINT` explícito. El prompt del sistema (en la descripción de
la herramienta) incluye esta instrucción, reforzada por el protocolo de
respuesta.

### 11. Podado de resultados (`trimResult`) y conservación de contexto

Aunque la paginación resuelve el problema de las salidas muy grandes,
incluso el primer bloque de 1000 líneas puede ser excesivo si se
acumulan varios en la sesión. Para evitarlo, `AbstractPaginatedAgentTool`
implementa `trimResult(String result, TrimResultType)`, que es invocado
por `ReasoningService` cuando el contexto se acerca a su límite.

El algoritmo funciona así:

- Cada herramienta devuelve una respuesta con el formato
  cabecera+`---`+contenido.
- Cuando el `ReasoningService` detecta que la sesión acumula muchos
  mensajes (o que el contexto se está llenando), itera sobre los
  mensajes de tipo `ToolExecutionResultMessage` y llama a `trimResult()`.
- `trimResult()` examina la cabecera: si encuentra la marca
  `CONTENT_TRIMMED_IN_THE_FOLLOWING_TURNS`, añade `CONTENT_TRIMMED: true`
  a la cabecera y **elimina todo el contenido** (deja solo el separador).
  Si solo se debe notificar al modelo (para que sepa que se ha
  recortado), se añade la anotación y se conserva el contenido completo
  en ese turno; en turnos posteriores se elimina.
- El LLM recibe así un mensaje de tipo "el contenido de esta herramienta
  ha sido truncado para ahorrar contexto, pero ya fue procesado
  anteriormente". Esto evita que el modelo lo intente leer de nuevo.

Este mecanismo es agresivo: sacrifica la posibilidad de releer la
salida antigua a cambio de mantener el contexto manejable. La
alternativa (no recortar y forzar una compactación) también es posible,
pero el `trimResult` ofrece una capa adicional de control fino.

### 12. Limitaciones y decisiones de diseño

El sistema de herramientas y paginación, aunque potente, tiene
limitaciones que deben conocerse:

- **Herramientas bloqueantes**: la ejecución de una herramienta detiene
  todo el agente. Si una herramienta tarda mucho (ej: `shell_execute` con
  un comando que dura minutos), el agente no responde a nuevos eventos
  hasta que finaliza. Esto es aceptable para tareas largas si el usuario
  es consciente, pero no es adecuado para agentes que requieran alta
  interactividad.

- **Sin paralelismo**: no se pueden ejecutar varias herramientas a la
  vez ni interrumpir una herramienta a mitad de ejecución (salvo por el
  usuario, que puede abortar comandos mediante confirmación escalonada en
  `shell_execute`).

- **La paginación depende de que el modelo siga las instrucciones**: si
  el LLM ignora el `HINT` y no llama a `read_paginated_resource`, el
  agente se quedará sin la información posterior. En la práctica, los
  modelos actuales (especialmente GPT-4 y Claude) respetan bien estas
  convenciones si el prompt es claro.

- **Los recursos temporales pueden acumularse**: aunque
  `AbstractPaginatedAgentTool` implementa un LRU para las salidas de
  shell, y los IDs de recurso se basan en rutas dentro de `tmp` y
  `cache`, no hay una limpieza global sistemática. En sesiones muy
  largas, el directorio `var/tmp` puede llenarse de archivos huérfanos.

- **`trimResult` es irreversible**: una vez que el contenido se recorta,
  se pierde para siempre. El modelo no puede volver a pedir el bloque
  original porque el archivo temporal ya no se corresponde con el
  contexto. Esta decisión es deliberada para ahorrar memoria, pero
  podría sorprender al LLM si intenta re‑leer un resultado antiguo.

- **No hay mecanismo de "streaming" de herramientas**: la salida se
  genera por completo antes de enviar el primer bloque. Para comandos
  muy largos, el usuario no ve resultados parciales hasta que finaliza la
  ejecución y se envía el primer bloque paginado.

### 13. Conclusión

El sistema de herramientas de Noema es un ejemplo de **pragmatismo
arquitectónico**: las herramientas se declaran de forma declarativa, se
gestionan de manera centralizada y se ejecutan con un modelo de
seguridad simple pero eficaz. El subsistema de paginación universal,
basado en `AbstractPaginatedAgentTool`, permite manejar salidas masivas
sin saturar la ventana de contexto, delegando en el LLM la decisión de
cuánto leer. Esta combinación dota al agente de una agencia real (puede
tocar el mundo) sin renunciar a la viabilidad técnica dentro de las
limitaciones actuales de los modelos de lenguaje. Noema no solo
conversa: actúa, y sus herramientas son los músculos que lo hacen
posible.

