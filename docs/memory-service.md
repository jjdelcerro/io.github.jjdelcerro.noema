
## Servicio de Memoria (`MemoryService`)

### 1. Introducción: el problema de la ventana de contexto

Los modelos de lenguaje actuales, por muy grandes que sean sus ventanas de contexto (128k, 1M tokens o más), tienen un límite inherente: no pueden retener una conversación de forma indefinida. Tarde o temprano, los turnos más antiguos quedan fuera del alcance del modelo, y el agente sufre una forma de "amnesia". La solución ingenua —descartar lo antiguo— destruye información valiosa. La solución compleja —almacenar todo en una base de datos vectorial y recuperar fragmentos bajo demanda— es viable, pero añade latencia y no preserva la continuidad narrativa.

Noema aborda este problema con un enfoque diferente: **la compactación narrativa**. En lugar de buscar fragmentos, **resume** la historia pasada en un texto denso pero legible, el **Punto de Guardado (CheckPoint)**. Este resumen se inyecta en el prompt del sistema junto con los turnos recientes, proporcionando al modelo una visión global de la conversación sin ocupar todo el espacio de contexto. La clave está en que el resumen no es un simple extracto; incluye referencias explícitas (`{cite:ID}`) a los turnos originales, permitiendo al agente recuperar el detalle exacto cuando sea necesario.

`MemoryService` es el componente responsable de esta transformación. Su misión es tomar un bloque de turnos (decenas o cientos) y generar un nuevo Punto de Guardado que consolide la información de forma fiel, trazable y narrativamente coherente. No se limita a comprimir datos; interpreta el diálogo, identifica sus núcleos temáticos y redacta una crónica que captura tanto los hechos como la evolución del pensamiento.

### 2. Arquitectura general: componentes y flujo

`MemoryService` es un servicio más dentro del ecosistema del agente, registrado con el nombre `"Memory"`. Sus componentes principales son:

- **`MemoryServiceImpl`**: la implementación concreta. Gestiona la lógica de compactación, la carga de prompts y la interacción con el LLM específico para memoria.
- **`SourceOfTruth`**: proporciona los turnos a consolidar (mediante `getTurnsByIds()`) y persiste los nuevos puntos de guardado (`add(CheckPoint)`).
- **`Agent.ChatModel`**: un modelo de lenguaje independiente (puede ser el mismo o distinto al de razonamiento), configurable mediante claves específicas en `settings.json`.
- **Prompts**: archivos Markdown (`memory-compact.md`) que definen el protocolo de compactación: estilo narrativo, manejo de citas, interpretación de herramientas, etc.
- **`CheckPoint`**: el objeto resultante, con metadatos en base de datos y contenido textual en disco.

El flujo se inicia en `ReasoningService`. Cuando la sesión acumula suficientes turnos (por defecto 40), o cuando el usuario fuerza la compactación manual, se invoca `performCompaction()`. Este método:

1. Obtiene las marcas de inicio y fin del bloque a compactar (métodos de `Session`).
2. Recupera los turnos correspondientes mediante `sourceOfTruth.getTurnsByIds(first, last)`.
3. Llama a `MemoryService.compact(previousCheckPoint, turns)`.
4. El servicio genera un nuevo `CheckPoint` y lo devuelve.
5. Se persiste el nuevo checkpoint, se eliminan los mensajes compactados de la sesión y se actualiza el puntero `activeCheckPoint`.

La separación de responsabilidades es clara: `ReasoningService` decide *cuándo* compactar; `MemoryService` sabe *cómo* hacerlo.

### 3. El contrato de `MemoryService`: el método `compact()`

La interfaz `MemoryService` expone un único método público relevante para la compactación:

```java
CheckPoint compact(CheckPoint previous, List<Turn> newTurns);
```

- **`previous`**: el punto de guardado más reciente (puede ser `null` si es la primera compactación). Su texto contiene el resumen acumulado hasta ese momento.
- **`newTurns`**: lista de turnos nuevos (no consolidados) que se deben integrar. Los turnos vienen ordenados cronológicamente por `id`.
- **Devuelve**: un nuevo `CheckPoint` transitorio con ID `-1` (aún no persistido). Contiene el texto generado (dos secciones: "Resumen" y "El Viaje") y los rangos de turnos que abarca (`turnFirst`, `turnLast`).

La implementación en `MemoryServiceImpl` realiza los siguientes pasos:
- Valida que `newTurns` no esté vacío.
- Construye el conjunto de IDs de turno "válidos" (los del checkpoint anterior más los de `newTurns`) para posterior validación de citas.
- Construye el `userPrompt` concatenando el checkpoint anterior (si existe) y el CSV de nuevos turnos.
- Invoca al modelo LLM con el `systemPrompt` (definido en `memory-compact.md`) y el `userPrompt`.
- Extrae del texto generado todas las referencias `{cite:ID}` y las valida contra el conjunto de IDs válidos. Las inválidas se convierten en `{badcite:ID}`.
- Calcula los rangos: `firstId` es `previous.getTurnFirst()` si existe, o `newTurns.getFirst().getId()`; `lastId` es `newTurns.getLast().getId()`.
- Crea un nuevo `CheckPoint` (con ID `-1` y el texto generado) y lo retorna.

Nótese que el checkpoint devuelto aún no está persistido; será `SourceOfTruth` quien lo añada a la base de datos y guarde el archivo de texto en disco.

### 4. El protocolo de generación de puntos de guardado (prompt)

El prompt del sistema para `MemoryService` reside en `var/config/prompts/memory-compact.md` y es uno de los documentos más extensos y detallados de Noema. Define el **Protocolo de Generación de Puntos de Guardado**. Sus secciones principales son:

- **Objetivos y datos de entrada**: especifica que el `MemoryManager` recibe un CSV de turnos (con columnas como `code`, `timestamp`, `contenttype`, `text_user`, `text_model`, `tool_call`, `tool_result`) y opcionalmente un punto de guardado anterior.

- **Principios Fundamentales**:
  - *Coherencia Narrativa*: el nuevo punto debe leerse como continuación natural del anterior.
  - *Trazabilidad Determinista*: cada hecho significativo debe llevar una cita `{cite:ID}` al turno original.
  - *Fidelidad de Referencia*: todas las citas deben pertenecer al conjunto de IDs de entrada. No se pueden inventar.
  - *Espiral de Contexto*: la memoria no es una línea recta, sino una espiral donde cada nueva conversación reinterpreta el pasado.

- **Directiva de Estilo de Citación**: las citas deben ir **integradas en la narrativa**, no al final como una lista. Ejemplo: "El usuario explicó que el sistema aprendía del texto {cite:6}".

- **Interpretación de eventos técnicos**:
  - Herramientas operativas (`tool_execution`, `tool_execution_summarized`): se debe narrar la acción y su resultado, no transcribir el JSON.
  - Herramientas de memoria (`lookup_turn`): representan un "flashback". Hay que describir el acto de recordar y rehidratar la información recuperada.
  
- **Modos de funcionamiento**:
  - *Modo 1 (Creación)*: solo se dispone de la nueva conversación. Se genera el primer punto de guardado desde cero.
  - *Modo 2 (Actualización)*: se dispone del punto anterior y de la nueva conversación. Se deben fusionar ambos en una narrativa única.
  
- **Detalle del Resumen y El Viaje**:
  - *Resumen*: ejecutivo, factual, decisiones clave, estado de proyectos.
  - *El Viaje*: narrativo, cronológico, captura el proceso de razonamiento y la evolución de las ideas.
  
- **Verificación de calidad**: el MemoryManager debe auto-evaluarse contra sesgos como el "sesgo de novedad" (dar más peso a la conversación nueva) y asegurar un balance conceptual.

Este prompt es el resultado de una evolución pragmática; contiene instrucciones muy detalladas porque se ha observado que los LLMs tienden a ser demasiado concisos o a perder la trazabilidad. El prompt actual intenta guiarlos hacia un estilo narrativo denso pero fiel.

> **Nota sobre la "pérdida de nitidez":**  
> Es frecuente que quienes examinan el sistema por primera vez crean que los puntos de guardado pierden información valiosa con el tiempo (ejemplos concretos, matices lingüísticos, citas literales). En realidad, la compactación **sacrifica deliberadamente la literalidad para ganar densidad semántica**, pero preserva la trazabilidad mediante citas `{cite:ID}`. Cada afirmación significativa del resumen lleva asociada una o varias citas que permiten al agente recuperar el turno original completo usando la herramienta `fetch_citation`. Esta estrategia se complementa con la **Directiva anti-alucinación** del `ReasoningService`, que prohíbe al modelo inventar detalles si existe una cita y le obliga a consultar la fuente original cuando necesita precisión. Así, el agente opera con resúmenes densos y, solo cuando es necesario, profundiza bajo demanda sin saturar el contexto.



### 5. Construcción del prompt de usuario: el CSV de turnos

El método `buildUserPrompt()` genera el mensaje que se envía al LLM junto con el prompt del sistema. Su estructura es:

1. **Modo de operación**: "MODO DE OPERACIÓN: 2 (Actualización)" si hay punto anterior; "1 (Creación Inicial)" si no.
2. **Punto de guardado anterior** (si existe): se incluye el texto completo del checkpoint previo, delimitado por `=== DOCUMENTO DE PUNTO DE GUARDADO ANTERIOR ===`.
3. **Nuevos turnos en CSV**: una cabecera con las columnas (`code,timestamp,contenttype,text_user,text_model_thinking,text_model,tool_call,tool_result`) seguida de una línea por cada turno, generada por `turn.toCSVLine()`.

El formato CSV es simple: las comillas dobles se escapan duplicándolas (`"` -> `""`). El LLM debe leer este CSV y entender que la columna `code` contiene el ID que usará para las citas, y que `contenttype` le indica cómo interpretar cada fila (chat, tool_execution, lookup_turn, etc.).

Un detalle importante: los turnos de tipo `lookup_turn` (resultados de herramientas de memoria) contienen en `tool_result` un JSON con los turnos históricos recuperados. Estos turnos **no** deben volver a compactarse como si fueran nuevos eventos; en su lugar, el MemoryManager debe tratarlos como "recuerdos" y utilizarlos para enriquecer la narrativa, manteniendo sus citas originales. El prompt incluye instrucciones específicas para este caso.

### 6. El modelo LLM de compactación: configuración y carga

`MemoryService` no está obligado a usar el mismo modelo de lenguaje que `ReasoningService`. De hecho, se recomienda utilizar un modelo diferente (quizás más económico o especializado en resúmenes) para la compactación. La configuración se realiza mediante tres claves en `settings.json`:

```json
"memory": {
  "provider": {
    "url": "https://api.deepseek.com/v1",
    "model_id": "deepseek-reasoner",
    "api_key": "sk-..."
  }
}
```

Estas claves se leen mediante `getModelParameters(MemoryService.ID)`, que devuelve un `ModelParametersImpl` con la URL, API key e identificador del modelo. La temperatura se fija a `0.7` (un poco de creatividad pero sin desviarse demasiado). El método `start()` del servicio crea el modelo invocando `agent.createChatModel(MemoryService.ID)`.

El servicio también registra dos acciones que permiten recargar el modelo en caliente:
- `CHANGE_MEMORY_PROVIDER`: cuando se cambia la URL o la API key.
- `CHANGE_MEMORY_MODEL`: cuando se cambia el identificador del modelo.

Así, el usuario puede ajustar el modelo de compactación sin reiniciar el agente, aunque la nueva configuración solo afectará a futuras compactaciones.

### 7. Validación de citas y corrección de errores

Uno de los problemas más comunes al generar resúmenes con LLMs es la **alucinación de citas**: el modelo inventa un `{cite:123}` que no corresponde a ningún turno real, o mezcla IDs. Para mitigarlo, `MemoryService` implementa un paso de validación posterior al texto generado:

1. Se extraen todas las referencias `{cite:...}` del texto mediante una expresión regular.
2. Se construye un conjunto `validTurnIds` que contiene:
   - Los IDs de los turnos del checkpoint anterior (extraídos también mediante regex del texto de ese checkpoint).
   - Los IDs de los turnos en `newTurns`.
   - Además, si algún turno es de tipo `lookup_turn` o `tool_execution`, se escanea su `tool_result` en busca de citas adicionales (pues el resultado de una búsqueda puede contener citas históricas).
3. Para cada cita encontrada en el texto generado, se verifica que su ID pertenezca a `validTurnIds`. Si no es así, se reemplaza por `{badcite:ID}`.

Este paso es fundamental porque evita que el agente intente recuperar un turno inexistente (lo que causaría un error en `fetch_citation`). En la práctica, los modelos grandes raramente alucinan citas, pero los modelos más pequeños o de código abierto pueden hacerlo; la validación añade una capa de robustez.

### 8. Integración con `ReasoningService`: cuándo y cómo se compacta

El `ReasoningService` es el cliente principal de `MemoryService`. La coordinación se realiza en el método `eventDispatcher`, al final del procesamiento de cada turno:

```java
if (this.session.needCompaction()) {
    performCompaction();
}
```

`needCompaction()` compara el número de turnos únicos consolidados en la sesión con un umbral configurable (`reasoning/compaction_turns`, por defecto 40). Si se supera, se dispara la compactación.

El método `performCompaction()` realiza la siguiente secuencia:

1. Obtiene `mark1 = session.getOldestMark()` (el mensaje más antiguo consolidado) y `mark2 = session.getCompactMark()` (aproximadamente la mitad de la sesión, ajustada para no romper un turno).
2. Recupera los turnos de `SourceOfTruth` entre `mark1.getTurnId()` y `mark2.getTurnId()`.
3. Invoca `memory.compact(activeCheckPoint, compactTurns)`.
4. Persiste el nuevo checkpoint con `sourceOfTruth.add(newCheckPoint)`.
5. Elimina de la sesión los mensajes compactados mediante `session.remove(mark1, mark2)`.
6. Actualiza `activeCheckPoint = newCheckPoint`.

Además, se exponen dos acciones de depuración:
- `COMPACT_REASONING_SESSION`: compacta aproximadamente el 50% más antiguo de la sesión.
- `COMPACT_REASONING_FULL_SESSION`: compacta todos los turnos consolidados (desde el más antiguo hasta el más reciente), generando un único checkpoint que abarca toda la historia.

La compactación es una operación **bloqueante**: mientras se genera el nuevo punto de guardado (lo que puede tomar varios segundos o decenas de segundos dependiendo del modelo), el agente no procesa nuevos eventos. Esto es aceptable porque la compactación ocurre solo ocasionalmente y, al ser parte del turno que acaba de terminar, no interfiere con la interactividad inmediata.

### 9. Persistencia y formato de los puntos de guardado (CheckPoints)

Un `CheckPoint` se divide en dos partes:

- **Metadatos** (tabla `checkpoints` en H2):
  - `id`: entero autoincremental.
  - `cp_first`: ID del primer turno que abarca (puede ser el primer turno de la historia, no solo del bloque consolidado).
  - `cp_last`: ID del último turno abarcado.
  - `timestamp`: momento de creación.

- **Contenido textual** (archivo `.md` en `var/lib/checkpoints/`):
  - Nombre del archivo: `checkpoint-{id}-{first}-{last}.md`.
  - El texto contiene dos secciones claramente separadas por cabeceras Markdown (aunque el prompt no exige un formato fijo, la práctica común es incluir `## Resumen` y `## El Viaje`).

La clase `CheckPointImpl` implementa un **lazy loading**: el contenido textual solo se carga desde el disco cuando se invoca `getText()`. Durante la creación, se guarda el texto en la caché y se persiste a disco mediante `saveTextToDisk()` antes de retornar el objeto. Los metadatos se guardan en la base de datos en el momento en que `SourceOfTruth.add(checkpoint)` es llamado.

Esta separación permite que los checkpoints ocupen poco espacio en la base de datos (solo los metadatos) y sean fácilmente inspeccionables con un editor de texto. El usuario puede incluso editar manualmente un checkpoint si desea corregir o ajustar el resumen (aunque esto debe hacerse con cuidado para no romper las referencias de cita).


### 10. Herramientas que aporta el servicio

`MemoryService` no solo consolida la memoria a largo plazo mediante el método `compact()`, sino que también expone al agente un conjunto de herramientas (`AgentTool`) que le permiten **interactuar activamente con su propio historial**. Estas herramientas están disponibles en el catálogo de capacidades del agente y pueden ser invocadas por el LLM durante el razonamiento.

Las tres herramientas registradas por `MemoryService` son:

#### 10.1. `fetch_citation` (LookupTurnTool)

**Propósito:** recuperar el texto exacto de un turno específico junto con su contexto inmediato, a partir de una referencia numérica.

**Uso típico:** cuando el modelo encuentra una cita `{cite:123}` en el resumen de un punto de guardado, debe ejecutar esta herramienta para obtener los detalles completos de aquel momento, incluyendo los turnos anteriores y posteriores (mediante el parámetro `context_window`).

**Parámetros:**
- `code` (obligatorio): el ID del turno o cita (ej: `"123"`).
- `context_window` (opcional, valor por defecto 2, máximo 5): número de turnos adicionales a recuperar antes y después del turno objetivo.

**Modo:** `MODE_READ` – solo consulta, no modifica estado.

**Tipo:** `TYPE_MEMORY` – sus resultados se registran como `lookup_turn` en la base de datos.

#### 10.2. `search_full_history`

**Propósito:** buscar en todo el historial conversacional (desde el primer turno hasta el último) por similitud semántica, utilizando los embeddings almacenados en la base de datos. Es la herramienta de recuperación por significado, ideal cuando el modelo no recuerda una referencia concreta pero sabe de qué trata.

**Uso típico:** cuando el contexto inmediato es insuficiente y el modelo tiene la sensación de haber hablado antes de un tema, invoca esta herramienta con una consulta descriptiva.

**Parámetros:**
- `query` (obligatorio): texto que describe el concepto o tema a buscar.
- `limit` (opcional, valor por defecto 10, máximo 50): número máximo de resultados a devolver.

**Modo:** `MODE_READ`.

**Tipo:** `TYPE_MEMORY`.

#### 10.3. `annotate_observation`

**Propósito:** permitir al agente fijar una nota, resumen o insight relevante extraído de una lectura o interacción, preservándolo en su memoria episódica. A diferencia de las herramientas anteriores, esta no recupera información del pasado, sino que **la escribe** para el futuro.

**Uso típico:** después de leer un archivo extenso (con `file_read`), ejecutar un comando (con `shell_execute`) o recibir una explicación detallada del usuario, el modelo puede invocar `annotate_observation` para consolidar los puntos clave, evitando que se pierdan cuando el contenido original sea podado del contexto o compactado. El sistema de compactación incluirá estas anotaciones como hechos consolidados en los puntos de guardado.

**Parámetros:**
- `source` (obligatorio): origen de la información (nombre de archivo, URL, o `"instrucción del usuario"`).
- `note` (obligatorio): texto con los hechos, conclusiones o resumen que el agente desea fijar.
- `resource_id` (opcional): identificador de un recurso paginado asociado (por ejemplo, el `resource_id` devuelto por `file_read`). Se utiliza para que el razonamiento pueda detectar qué recursos ya han sido anotados.

**Modo:** `MODE_READ` – aunque escribe información en la base de datos (el turno de anotación), no modifica el sistema de archivos ni ejecuta comandos, por lo que no requiere confirmación humana.

**Tipo:** `TYPE_OPERATIONAL` (por razones técnicas se registra como operativa y no como `TYPE_MEMORY`, pero su función es claramente episódica).


### 11. Limitaciones y desafíos actuales

A pesar de su diseño cuidadoso, `MemoryService` tiene varias limitaciones conocidas que se documentan aquí para transparencia y para guiar futuras mejoras:

- **Compactación bloqueante**: el agente se detiene por completo mientras se genera el checkpoint. Para conversaciones muy largas o con modelos lentos, esto podría suponer una pausa de varios segundos. Una posible mejora sería realizar la compactación en un hilo separado, pero entonces habría que gestionar la concurrencia de la sesión.

- **Umbral basado solo en número de turnos**: actualmente la compactación se activa al alcanzar un número fijo de turnos (40). No se tiene en cuenta el tamaño en tokens de esos turnos. Si los turnos incluyen textos muy largos (por ejemplo, salidas de herramientas con miles de líneas), el contexto podría saturarse antes del umbral. Una mejora pendiente es combinar ambos criterios.

- **Tratamiento de herramientas de memoria (`lookup_turn`)**: el código contiene un TODO explícito: "FIXME: probablemente habría que implementar el troceado de los turnos generando más de un punto de guardado, cuando estos no entren en el contexto del LLM encargado de compactarlos". En la práctica, si un `lookup_turn` recupera muchos turnos antiguos, el CSV resultante puede ser inmenso y no caber en el contexto del modelo de compactación. Actualmente no hay manejo de este caso.

- **Alucinaciones de citas**: aunque se valida post-hoc, la corrección convierte la cita en `{badcite:ID}`, lo que el agente interpretará como un error. Sería mejor prevenir la alucinación desde el prompt, pero no siempre es suficiente.

- **Idioma y estilo**: el prompt actual está en español, y se asume que el modelo de compactación lo entiende y responde en el mismo idioma. Para entornos multilingües habría que parametrizar el idioma.

- **Costo computacional**: generar un checkpoint implica una llamada al LLM que puede consumir cientos o miles de tokens, además del tiempo de procesamiento. En conversaciones muy largas, la compactación puede ser costosa. Se podría considerar el uso de un modelo más pequeño y rápido para esta tarea.

- **El "Viaje" como espiral de contexto**: la directiva de crear una narrativa que integre pasado y presente en una espiral es ambiciosa. En la práctica, muchos checkpoints generados por modelos actuales tienden a ser más bien resúmenes lineales. Alcanzar la calidad narrativa deseada requiere prompts muy cuidadosos y, probablemente, modelos de razonamiento potentes.

A pesar de estas limitaciones, `MemoryService` cumple su cometido fundamental: permite que Noema mantenga conversaciones de cientos o miles de turnos sin saturar la ventana de contexto, preservando la información esencial y ofreciendo trazabilidad hacia los detalles originales. Es un componente central en la arquitectura de memoria híbrida del agente.