
# Problema de pérdida de información por poda y compactación en Noema, y solución mediante orquestador efímero con sugerencias de anotación

## 1. Contexto y definiciones

Noema es un agente conversacional autónomo con arquitectura de memoria híbrida. La conversación se divide en **turnos** (cada interacción LLM ↔ herramientas ↔ usuario). Para gestionar la ventana de contexto del LLM, Noema implementa dos mecanismos:

- **Poda (context pruning):** Durante la preparación del contexto para cada turno, los mensajes de tipo `ToolExecutionResultMessage` que superan 1024 caracteres y se encuentran fuera de la ventana de notificación (los más antiguos) se **truncan**: se elimina el cuerpo (`---\n...`) y se añade `CONTENT_TRIMMED: true` en la cabecera. El LLM ve que hubo un resultado de herramienta pero no su contenido.  
- **Compactación (semantic compaction):** Cuando se acumulan más de 40 turnos únicos (configurable), se invoca a un LLM especializado (`MemoryManager`) para que genere un **Punto de Guardado** (Resumen + Viaje narrativo) a partir de los turnos no compactados y el checkpoint anterior. Una vez generado, los mensajes compactados se eliminan de la sesión activa y se reemplazan por el checkpoint.

La **memoria a largo plazo** del agente reside en:
- Los **turnos** almacenados en la base de datos H2 (con sus `tool_result` completos o resumidos si superan 2KB).
- Los **checkpoints** (ficheros `.md`) que contienen la narrativa consolidada.

Sin embargo, los resultados grandes de herramientas (por ejemplo, el contenido de un archivo leído) **no se persisten en su totalidad** en la BBDD si superan 2KB: se almacena un breve resumen con metadatos. Esto significa que, una vez que el mensaje original es podado o compactado, **el contenido detallado de la lectura se pierde definitivamente** a menos que el LLM haya realizado una **anotación** (`annotate_observation`) extrayendo la información relevante y fijándola en su memoria (ya que la anotación se guarda como un turno especial cuyos argumentos sí se preservan).

## 2. El problema detectado

En sesiones de trabajo típicas (por ejemplo, lectura de varios artículos, análisis de logs, etc.), el LLM suele leer múltiples recursos (archivos, páginas web) sin invocar `annotate_observation`. Las razones:

- El system prompt actual no instruye explícitamente al LLM sobre cuándo usar esta herramienta.
- El LLM confía en que el contenido estará disponible en la conversación actual, sin prever la poda o compactación futura.
- No hay ningún mecanismo que le recuerde que debe extraer y fijar información importante antes de que se pierda.

Como consecuencia, cuando la poda o compactación actúan, el LLM pierde el acceso a los contenidos de las lecturas, y el usuario observa que el agente “olvida” lo que acababa de leer.

Se identificaron varios subtipos de problema:

- **Lecturas paginadas:** Un recurso grande (ej. un log) se lee en múltiples páginas (múltiples llamadas a `read_paginated_resource`). Las primeras páginas pueden ser podadas antes de que el LLM llegue a la última. Si solo se diera una sugerencia al final, el LLM ya no tendría el contenido de las páginas anteriores para anotar.
- **Múltiples recursos:** El LLM lee varios archivos sin anotar ninguno. El orquestador debe detectar la acumulación y sugerir anotar, pero sin molestar excesivamente.
- **Falsas coberturas:** Si el LLM anota algo, el orquestador no sabe si esa anotación cubre todas las lecturas recientes o solo una parte. Se necesita un mecanismo para que el LLM indique explícitamente qué recursos (o qué llamadas) cubre su anotación.

## 3. Requisitos para la solución

- **No debe contaminar el historial de la sesión** con mensajes de sugerencia que persistirían en la BBDD y aparecerían en futuros resúmenes.
- **Debe actuar antes de que la poda elimine información útil**, por lo que las sugerencias deben aparecer mientras las páginas aún están en contexto.
- **Debe ser configurable** (umbral de páginas leídas sin anotar, límite de sugerencias por recurso, etc.).
- **Debe permitir al LLM señalar qué recursos cubre con su anotación**, para que el orquestador no siga sugiriendo sobre ellos.
- **Debe funcionar con recursos paginados y no paginados**.

## 4. Análisis de alternativas

Se exploraron varias opciones:

### 4.1. Solo instrucciones en el system prompt
- **Ventaja:** Simple.
- **Inconveniente:** El LLM puede ignorarlas; no hay garantía. Además, no resuelve el problema de la paginación.

### 4.2. Modificar el paginador para que añada un `HINT` de anotación en cada página o al final
- **Ventaja:** El LLM recibe la sugerencia junto con el contenido.
- **Inconveniente:** La sugerencia queda incrustada en el `tool_result`, que se guarda en BBDD y puede persistir (saturación del historial). Aunque la poda puede eliminarla más tarde, sigue apareciendo durante un tiempo.

### 4.3. Orquestador con inyección efímera (solución elegida)
- **El orquestador** (componente `ReasoningServiceImpl`) mantiene un estado en memoria (no persistente) por recurso: contador de páginas servidas, si se ha anotado ya, etc.
- **En cada turno**, justo antes de llamar al LLM, el orquestador puede **inyectar mensajes efímeros** en la copia del contexto que se enviará al modelo. Estos mensajes **no se añaden a la sesión** (`session.messages`), por lo que no se guardan en BBDD ni aparecen en el historial persistente.
- La inyección puede consistir en un par de mensajes simulados (ej. un `UserMessage` o un `AiMessage` con un `ToolExecutionRequest` ficticio seguido de un `ToolExecutionResultMessage` que contenga la sugerencia). El LLM los ve como parte de la conversación en ese turno, pero al no estar en la sesión, desaparecen en el siguiente turno.
- El orquestador decide cuándo inyectar basándose en:
  - El número de páginas servidas para un mismo `resource_id` desde la última anotación que incluya ese recurso (o desde el inicio si no ha anotado).
  - Si se supera un umbral (ej. 3 páginas sin anotar), inyecta una sugerencia.
  - Si el LLM finalmente ejecuta `annotate_observation` con el `resource_id` correspondiente, el orquestador resetea el contador para ese recurso.
  - Se puede limitar el número de sugerencias por recurso (ej. máximo 3) para no spamear.

### 4.4. El papel del `resource_id`
Para que el LLM pueda indicar qué recurso cubre su anotación, se decide:
- Todas las herramientas de lectura (paginadas o no) **deben devolver en su cabecera un campo `RESOURCE_ID`** (único para cada recurso lógico: archivo, URL, etc.). Este ID ya existe en las herramientas paginadas (se usa para el `HINT`). Se extiende a todas las lecturas.
- La herramienta `annotate_observation` recibe un parámetro opcional `resource_id`. Si el LLM anota basándose en la lectura de un recurso concreto, debe incluir ese ID.
- El orquestador, al ver una anotación con `resource_id`, marca ese recurso como “anotado” y deja de sugerir sobre él.

## 5. Solución detallada: Orquestador efímero con sugerencias inyectadas

### 5.1. Estado en memoria del orquestador

Dentro de `ReasoningServiceImpl` se añaden:

```java
// Mapa: resourceId -> Contador de páginas servidas sin anotación
private final Map<String, Integer> pagesServedWithoutAnnotation = new HashMap<>();

// Mapa: resourceId -> Número de sugerencias ya emitidas (para límite)
private final Map<String, Integer> suggestionsEmitted = new HashMap<>();

// Parámetros configurables (desde settings.json)
private int annotationPageThreshold = 3;      // páginas sin anotar para sugerir
private int maxSuggestionsPerResource = 3;    // máximo sugerencias por recurso
private boolean annotationSuggestionEnabled = true;
```

Estos parámetros se cargan al inicio y pueden recargarse con una acción.

### 5.2. Registro de páginas servidas

Cada vez que el paginador (o cualquier herramienta que sirva contenido paginado) devuelve una página, debe informar al orquestador. Para ello, en el método `execute` de la herramienta, antes de retornar la respuesta, se llama:

```java
ReasoningService reasoning = (ReasoningService) agent.getService(ReasoningService.NAME);
reasoning.recordPageServed(resourceId);
```

El método `recordPageServed` incrementa el contador para ese `resourceId` si el recurso no ha sido anotado previamente (ver más abajo). También puede inicializar el contador a 1 si es la primera página.

### 5.3. Registro de anotaciones

Cuando el LLM ejecuta `annotate_observation`, el orquestador (en `eventDispatcher`) detecta la llamada, extrae el argumento `resource_id` (si existe) y llama:

```java
reasoning.recordAnnotation(resourceId);
```

Este método:
- Elimina la entrada del mapa `pagesServedWithoutAnnotation` para ese `resourceId` (o la resetea a 0).
- Elimina o resetea el contador de sugerencias emitidas.

Si la anotación no incluye `resource_id`, el orquestador no puede saber qué recurso cubre, por lo que **no resetea ningún contador**. Así se incentiva al LLM a usarlo.

### 5.4. Decisión de inyectar sugerencia

Antes de cada llamada a `model.generate()`, el orquestador (en `eventDispatcher`) obtiene la lista de `context` (copia de la sesión). Luego, para cada `resourceId` que tenga un contador positivo y que no haya alcanzado el límite de sugerencias, evalúa si debe inyectar una sugerencia en *este* turno.

La condición: `pagesServedWithoutAnnotation.get(resourceId) >= annotationPageThreshold`.

Si se cumple, entonces:
- Se crea un mensaje de sugerencia (por ejemplo, un `SystemMessage` o una simulación de herramienta). Para que el LLM lo vea con la máxima claridad, se puede usar un `SystemMessage` con un texto como:

> **Sugerencia del sistema:** Has leído `N` páginas del recurso con ID `XXXX` sin anotar información. Si hay datos relevantes que deban preservarse tras la compactación, usa la herramienta `annotate_observation` con `resource_id='XXXX'` y extrae los puntos clave. Esta sugerencia desaparecerá después de este turno.

- Se añade este mensaje **al final** de la lista `context` (justo antes de enviarlo al modelo). No se añade a `session.messages`.
- Se incrementa el contador `suggestionsEmitted` para ese `resourceId`.

Para evitar repetir la sugerencia en cada turno mientras el contador siga siendo alto, se puede añadir una lógica de “enfriamiento”: solo sugerir si han pasado al menos `X` turnos desde la última sugerencia para ese recurso. O simplemente sugerir cada vez que se supere el umbral (pero limitando el número total de sugerencias por recurso). La implementación concreta puede ser sencilla: después de inyectar, se resetea el contador de páginas a 0 (o se decrementa en `annotationPageThreshold`) para que no vuelva a sugerir inmediatamente, pero se seguirán contando nuevas páginas.

### 5.5. Detección de fin de recurso (opcional)

Para refinar, el paginador podría indicar explícitamente si la página servida es la última (enviando un flag `isLastPage`). El orquestador podría usar eso para sugerir también al final, incluso si no se ha alcanzado el umbral. Pero no es estrictamente necesario si el umbral es bajo.

### 5.6. Configuración y persistencia del estado

El estado del orquestador (mapas de contadores) **no se persiste** en la BBDD; se mantiene únicamente en memoria durante la sesión. Si el agente se reinicia, se pierde. Esto es aceptable porque las sugerencias son ayudas temporales; si el agente se reinicia, la conversación también se reiniciará (o se cargará desde el último checkpoint). En cualquier caso, no hay necesidad de recordar entre sesiones.

Los parámetros (umbrales, habilitación) se guardan en `settings.json` bajo una sección, por ejemplo:

```json
"orchestrator": {
  "suggest_annotation_enabled": true,
  "annotation_page_threshold": 3,
  "max_suggestions_per_resource": 3,
  "suggestion_cooldown_turns": 5
}
```

### 5.7. Modificaciones necesarias en el código

**A. En `AbstractPaginatedAgentTool` (y en cualquier herramienta de lectura no paginada que quiera participar):**
- Asegurar que la cabecera de la respuesta incluya una línea `RESOURCE_ID: <id>` (ya existe en paginadas, pero debe estandarizarse).
- En el método `execute`, después de construir la respuesta pero antes de retornarla, llamar a `reasoning.recordPageServed(resourceId)`.

**B. En `ReasoningServiceImpl`:**
- Añadir los mapas de estado y los métodos `recordPageServed`, `recordAnnotation`.
- Añadir un método `shouldInjectSuggestion()` que evalúe si algún recurso supera el umbral y no ha excedido sugerencias.
- Modificar `eventDispatcher` para que, después de obtener `context`, consulte `shouldInjectSuggestion()` y, si es afirmativo, inyecte el mensaje correspondiente (al final de la lista, o al principio si se prefiere).
- Asegurar que los parámetros se lean de `settings` al inicio y se puedan recargar.

**C. En `annotate_observation` tool:**
- Modificar su especificación para aceptar `resource_id` (opcional) y actualizar la descripción indicando que debe usarse cuando la anotación se refiera a un recurso leído previamente.
- En el `execute`, además de devolver el éxito, se podría notificar al orquestador (aunque el orquestador ya detecta la ejecución desde `eventDispatcher` y puede extraer los argumentos). Es más limpio que el `eventDispatcher` llame a `recordAnnotation`.

**D. En el system prompt (`reasoning-system.md`):**
- Añadir instrucciones claras sobre el uso de `annotate_observation` con `resource_id`, explicando que el `RESOURCE_ID` se encuentra en la cabecera de las respuestas de lectura y que sirve para que el sistema sepa qué recurso ha sido cubierto.

## 6. Flujo de trabajo resultante (ejemplo con un log de 5 páginas)

1. El LLM solicita la primera página del log. El paginador devuelve la página 1 (con `RESOURCE_ID: log_web`) y llama a `recordPageServed("log_web")`. Contador = 1.
2. El LLM pide página 2. Contador = 2.
3. El LLM pide página 3. Contador = 3. Ahora supera el umbral (3). Antes de la siguiente llamada al LLM (en el mismo turno o al inicio del siguiente), el orquestador inyecta un mensaje efímero de sugerencia. El LLM recibe la sugerencia junto con la página 3 (o justo antes). El LLM puede decidir anotar.
4. Si anota con `resource_id="log_web"`, el orquestador detecta la ejecución y resetea el contador a 0 (y marca sugerencias emitidas). El LLM continúa.
5. Si no anota, el contador sigue en 3. El orquestador podría sugerir de nuevo tras la página 4, pero con un límite de sugerencias totales (ej. 3) para no saturar. Al llegar a la página 5 (última), si aún no ha anotado, el orquestador puede sugerir de nuevo (si no ha alcanzado el límite). En cualquier caso, si el LLM nunca anota, las sugerencias cesan tras el límite.

## 7. Ventajas de la solución

- **No contamina el historial:** Las sugerencias son efímeras, no se guardan en BBDD ni en la sesión.
- **Oportuna:** Se emiten antes de que la poda borre información, porque se basan en el número de páginas servidas (y la poda actúa sobre la antigüedad de los mensajes, pero mientras el LLM sigue leyendo páginas, las primeras aún pueden estar en contexto si no han pasado muchos turnos). La solución es heurística, pero mejora drásticamente el comportamiento actual.
- **Configurable:** Se pueden ajustar umbrales según las necesidades.
- **Explícita:** El uso de `resource_id` permite al orquestador saber exactamente qué recursos han sido cubiertos.
- **Respetuosa con el LLM:** Las sugerencias son amables y no fuerzan al LLM a anotar; solo le recuerdan la posibilidad.

## 8. Posibles limitaciones y trabajo futuro

- **Recursos no paginados pero grandes:** Herramientas como `file_read` que devuelven todo el contenido en una sola respuesta también pueden activar la sugerencia si el tamaño supera un umbral (ej. 10KB). Se puede unificar haciendo que `file_read` use internamente la paginación cuando el tamaño es grande, o simplemente que llame a `recordPageServed` una sola vez con un contador especial.
- **Anotaciones que cubren múltiples recursos:** El LLM podría querer anotar sobre varios recursos a la vez. Para ello, `annotate_observation` podría aceptar una lista de `resource_ids`. El orquestador entonces resetearía todos ellos.
- **Interacción con la compactación:** Si el LLM anota abundantemente, la compactación preservará esa información. Si no anota, el conocimiento se pierde, pero el sistema ha hecho todo lo posible por ayudarle.

## 9. Conclusión

La solución de **orquestador efímero** con inyección de sugerencias no persistentes, basada en el seguimiento de recursos y el uso de `resource_id`, resuelve el problema de pérdida de información por poda y compactación de forma limpia, sin añadir ruido al historial de la conversación. Se integra armoniosamente con la arquitectura existente de sensores y herramientas, y es completamente configurable. Su implementación requiere modificaciones localizadas en `ReasoningServiceImpl`, en las herramientas de lectura y en la herramienta `annotate_observation`, así como una pequeña actualización del system prompt.


## Anexo cambios en implementacion en "Orquestador efímero con sugerencias inyectadas".

### 1. Información disponible en los mensajes de la sesión

Cada mensaje de la sesión (tanto los de usuario, como los del modelo, como los resultados de herramientas) contiene suficiente metadatos para reconstruir el contexto necesario:

- **Mensajes de herramienta (`ToolExecutionResultMessage`)**:
  - `toolName`: indica qué herramienta se ejecutó (ej. `file_read`, `read_paginated_resource`, `web_get_content`).
  - `text`: el resultado completo, que incluye una cabecera con campos como `RESOURCE_ID:` (si la herramienta lo proporciona). También puede incluir `HINT:` para paginación.
  - `id`: identificador de la llamada (útil para correlacionar con la petición).

- **Mensajes de IA (`AiMessage`)**:
  - Pueden contener `toolExecutionRequests` que a su vez tienen `id` y `name`. Estos permiten saber qué herramienta pidió el LLM.

- **Mensajes de herramienta de anotación (`ToolExecutionResultMessage` para `annotate_observation`)**:
  - Su `text` contiene un JSON con, entre otros, el campo `resource_id` (si el LLM lo incluyó).

Por tanto, para decidir si sugerir una anotación para un recurso determinado, solo necesitamos escanear los últimos `L` mensajes (ej. 20) y extraer:

- La lista de **lecturas de recursos** (cada vez que se ejecuta una herramienta de lectura, obtenemos su `resource_id` y, si es paginada, también podemos saber si fue la última página o no, aunque no es estrictamente necesario).
- Las **anotaciones realizadas** (ejecuciones de `annotate_observation` con `resource_id`).
- El orden cronológico de estos eventos.

### 2. Algoritmo de decisión (sin estado global)

Cada vez que el orquestador se prepara para invocar al LLM (en `eventDispatcher`, justo antes de `model.generate()`) realiza los siguientes pasos:

```pseudo
function shouldInjectSuggestionForResource():
    // 1. Obtener los últimos N mensajes de la sesión (ej. N=20)
    messages = session.getMessages()  // lista en orden cronológico
    recentMessages = messages.last(N)  // o desde una posición hacia atrás

    // 2. Estructuras locales (solo para esta evaluación)
    lecturas = mapa vacío: resourceId -> {
        pageCount: int,           // número de páginas servidas de este recurso
        hasAnnotation: boolean,   // si ya se anotó este recurso recientemente
        lastSuggestionTurn: int   // no usado aquí porque no guardamos estado
    }

    anotaciones = conjunto de resourceIds que han sido anotados en los mensajes recientes

    // 3. Recorrer los mensajes en orden inverso (del más nuevo al más viejo)
    for each message in reverse(recentMessages):
        if message es ToolExecutionResultMessage y toolName es de lectura (file_read, read_paginated_resource, etc.):
            extraer resourceId de la cabecera (buscando "RESOURCE_ID:")
            if resourceId no está en lecturas:
                lecturas[resourceId] = { pageCount: 1, hasAnnotation: false }
            else:
                lecturas[resourceId].pageCount++
        if message es ToolExecutionResultMessage y toolName es "annotate_observation":
            extraer argumentos (JSON) y obtener resourceId si existe
            si resourceId no es nulo:
                anotaciones.add(resourceId)

    // 4. Filtrar lecturas que no tengan anotación correspondiente
    for each resourceId in lecturas.keys():
        if resourceId in anotaciones:
            lecturas[resourceId].hasAnnotation = true

    // 5. Decidir para qué recursos sugerir
    recursosParaSugerir = []
    for each resourceId, info in lecturas:
        if not info.hasAnnotation and info.pageCount >= umbralPaginas (ej. 3):
            recursosParaSugerir.add(resourceId)

    // 6. Opcional: limitar el número de sugerencias por recurso en un mismo ciclo
    //    Para evitar repetir en cada turno, podemos usar un "cooldown" basado en el número de veces
    //    que ya hemos sugerido en los mensajes recientes para ese recurso. Para ello, durante el escaneo
    //    también contar cuántas sugerencias (mensajes de sistema inyectados) existen. Pero como esas sugerencias
    //    no se guardan, no podemos contarlas. Por tanto, una alternativa más sencilla es:
    //    * Si ya sugerimos para un recurso en el turno actual (no se guarda), simplemente no volver a sugerir
    //      en el mismo turno (obvio). Para turnos sucesivos, como no hay memoria, cada vez que se supere el umbral
    //      se sugerirá. Eso puede generar varias sugerencias para el mismo recurso si el LLM sigue leyendo páginas
    //      sin anotar. Eso es aceptable: el LLM puede decidir anotar o ignorar, y el mensaje no persiste.
    //      Si se considera spam, se puede aumentar el umbral de páginas o limitar el número de sugerencias
    //      en un intervalo de tiempo (pero eso requeriría estado, así que lo dejamos simple).

    return recursosParaSugerir no vacío
```

### 3. Inyección de la sugerencia efímera

Si `shouldInjectSuggestionForResource()` devuelve `true` (hay al menos un recurso que supera el umbral sin anotación), el orquestador **construye un mensaje de sugerencia** y lo añade a la lista `context` que se enviará al LLM. El mensaje puede ser un `SystemMessage` (para que tenga autoridad) o una simulación de herramienta. Ejemplo:

```
SystemMessage: "Nota del sistema: Has leído varias páginas de los siguientes recursos sin extraer información relevante: resourceId1, resourceId2. Si hay datos que deban conservarse tras la compactación, usa annotate_observation con el resource_id correspondiente. Esta nota desaparecerá tras este turno."
```

O bien, si se quiere ser más específico, se puede inyectar un par de mensajes simulados como en la propuesta original, pero un `SystemMessage` es suficiente y más simple.

Importante: Este mensaje **no se añade a `session.messages`**, solo se agrega a la lista temporal `context` que se pasa a `model.generate()`. Por tanto, no persiste.

### 4. ¿Y si hay varios recursos que superan el umbral?

Se puede generar una sugerencia única que mencione todos los `resource_id` pendientes, o generar una sugerencia por recurso. Lo primero es más conciso.

### 5. Ventajas de este enfoque sobre el estado global

- **No hay estado mutable**: La decisión se calcula cada vez a partir de los mensajes almacenados. Si el agente se reinicia, la nueva sesión (cargada desde el último checkpoint o desde el archivo `active_session.json`) contiene la misma información, por lo que la decisión será coherente.
- **Sencillez de implementación**: Solo se necesita una función que escanee los últimos N mensajes (20 como máximo) y extraiga la información relevante. No hay que mantener mapas sincronizados.
- **Determinismo**: Dados los mismos mensajes, siempre se tomará la misma decisión.
- **Facilidad de depuración**: Se puede loguear el resultado del escaneo para entender por qué se sugirió o no.

### 6. Posibles inconvenientes y soluciones

- **Eficiencia**: Escanear hasta 20 mensajes en cada turno es despreciable (es una operación en memoria). No hay problema.
- **Pérdida de información sobre sugerencias previas**: Como no se guarda estado, si el LLM ignora una sugerencia, en el siguiente turno (si el umbral de páginas sigue superándose) se volverá a sugerir. Esto puede llevar a sugerencias repetitivas si el LLM nunca anota. Para mitigarlo:
  - Aumentar el umbral de páginas (ej. a 5 o 6) para que solo sugiera si la lectura es significativa.
  - O bien, detectar si el recurso ya ha recibido varias sugerencias en el pasado reciente examinando si en los mensajes ya hay **sugerencias previas** (pero como no se guardan, no podemos). Otra opción: el orquestador podría contar cuántas veces ha sugerido en el **turno actual** (solo en memoria local durante la ejecución) y limitar a una por recurso por turno, pero como el turno es una sola llamada al LLM, basta con no sugerir más de una vez. En la práctica, con un umbral razonable, el LLM solo recibirá sugerencias cada varias páginas, y si las ignora, no es catastrófico.
- **Identificación fiable de `resource_id` en herramientas no paginadas**: Asegurar que todas las herramientas de lectura (`file_read`, `web_get_content`, etc.) incluyan `RESOURCE_ID:` en su cabecera. Si no lo hacen, se puede añadir fácilmente.

### 7. Integración con la paginación y detección de “última página”

Podemos refinar el algoritmo para tener en cuenta si ya se ha alcanzado el final del recurso. Por ejemplo, en las respuestas de `read_paginated_resource`, la cabecera incluye `TOTAL_LINES` y `LINE_RANGE`. El orquestador podría detectar que `(offset+limit) >= totalLines` para saber que es la última página. Si el recurso tiene muchas páginas y el LLM no ha anotado, la sugerencia al final podría ser más apropiada. Sin embargo, no es necesario complicar: el umbral de páginas ya cubre el caso.

No obstante, para dar una ayuda más precisa, el algoritmo podría **priorizar sugerencias cuando se haya llegado al final** (aunque no se alcance el umbral de páginas). Por ejemplo, si la última página servida fue la final y el recurso no ha sido anotado, sugerir inmediatamente. Eso se puede detectar examinando la cabecera del último mensaje de lectura.

### 8. Resumen de la implementación concreta

1. **Asegurar que todas las herramientas de lectura incluyan `RESOURCE_ID:`** en su cabecera (ya lo hacen las paginadas; extender a las no paginadas).
2. **En `ReasoningServiceImpl`, crear un método `private boolean shouldInjectSuggestion(List<ChatMessage> recentMessages)`** que implemente la lógica descrita (escanear, contar páginas, detectar anotaciones, devolver true si hay algún recurso pendiente).
3. **En `eventDispatcher`, justo después de obtener `context` (la lista de mensajes a enviar), pero antes de `contextTrimmer` (o después, según se prefiera), calcular `shouldInjectSuggestion(context)` (pasándole los últimos N mensajes de la sesión, no toda la lista `context`, porque `context` puede contener el checkpoint y otras cosas). Para obtener los últimos mensajes de la sesión, usar `session.getMessages()` y tomar la cola.
4. Si devuelve `true`, crear un `SystemMessage` con el texto de sugerencia y añadirlo a la lista `context` (al final, o al principio, pero el final es más natural para no interferir con el flujo).
5. Continuar con `model.generate()`.

### 9. Conclusión final

La solución basada en escaneo del historial es más robusta, simple y alineada con la filosofía de no mantener estado innecesario. Se eliminan los mapas globales y se aprovecha la información ya persistente en la sesión. El coste computacional es mínimo y se evitan problemas de reinicio o desincronización.

