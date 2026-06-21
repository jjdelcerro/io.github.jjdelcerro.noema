# Problema de pérdida de información por poda y compactación en Noema, y solución mediante orquestador efímero con sugerencias de anotación (basada en escaneo del historial)

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
- **No debe mantener estado global mutable** que pueda desincronizarse o complicar la recuperación de sesiones. La decisión de sugerir debe poder calcularse en cada momento a partir únicamente del historial de la sesión.

## 4. Análisis de alternativas

Se exploraron varias opciones:

### 4.1. Solo instrucciones en el system prompt
- **Ventaja:** Simple.
- **Inconveniente:** El LLM puede ignorarlas; no hay garantía. Además, no resuelve el problema de la paginación.

### 4.2. Modificar el paginador para que añada un `HINT` de anotación en cada página o al final
- **Ventaja:** El LLM recibe la sugerencia junto con el contenido.
- **Inconveniente:** La sugerencia queda incrustada en el `tool_result`, que se guarda en BBDD y puede persistir (saturación del historial). Aunque la poda puede eliminarla más tarde, sigue apareciendo durante un tiempo.

### 4.3. Orquestador con estado global en memoria
- **Mecanismo:** El orquestador mantiene mapas (`pagesServedWithoutAnnotation`, `suggestionsEmitted`) para cada recurso, actualizándolos al servir páginas y al detectar anotaciones.
- **Ventaja:** Control preciso sobre cuándo sugerir.
- **Inconveniente:** Estado mutable que hay que sincronizar con la sesión; problemas al reiniciar el agente (los mapas se pierden); posible desincronización si se cargan sesiones antiguas; complejidad adicional.

### 4.4. Orquestador sin estado: decisión basada en escaneo del historial (solución elegida)
- **Mecanismo:** Cada vez que se necesita decidir si sugerir, se recorren los últimos N mensajes de la sesión (ej. 20) y se extrae:
  - Las lecturas de recursos (herramientas de lectura) con su `resource_id` y número de páginas servidas (contando apariciones).
  - Las anotaciones realizadas (ejecuciones de `annotate_observation` con `resource_id`).
- Con esta información local se determina si hay recursos que hayan superado un umbral de páginas sin haber sido anotados.
- La sugerencia se inyecta de forma efímera (solo en la lista `context` de ese turno, sin persistir).
- **Ventajas:** Sin estado global; determinista; fácil de reiniciar; escaneo de pocos mensajes (20) es muy eficiente; se adapta a cualquier sesión guardada.

## 5. Solución detallada: Orquestador efímero con escaneo del historial

### 5.1. Información disponible en los mensajes de la sesión

Cada mensaje de la sesión (tanto los de usuario, como del modelo, como los resultados de herramientas) contiene suficiente metadatos para reconstruir el contexto necesario:

- **Mensajes de herramienta (`ToolExecutionResultMessage`)**:
  - `toolName`: indica qué herramienta se ejecutó (ej. `file_read`, `read_paginated_resource`, `web_get_content`).
  - `text`: el resultado completo, que incluye una cabecera con campos como `RESOURCE_ID:` (si la herramienta lo proporciona). También puede incluir `HINT:` para paginación.
  - `id`: identificador de la llamada (útil para correlacionar).

- **Mensajes de IA (`AiMessage`)**:
  - Pueden contener `toolExecutionRequests` que a su vez tienen `id` y `name`. Estos permiten saber qué herramienta pidió el LLM.

- **Mensajes de herramienta de anotación (`ToolExecutionResultMessage` para `annotate_observation`)**:
  - Su `text` contiene un JSON con, entre otros, el campo `resource_id` (si el LLM lo incluyó).

Por tanto, para decidir si sugerir una anotación para un recurso determinado, solo necesitamos escanear los últimos `L` mensajes (ej. 20) y extraer las lecturas y anotaciones.

### 5.2. Algoritmo de decisión (sin estado global)

Cada vez que el orquestador se prepara para invocar al LLM (en `eventDispatcher`, justo antes de `model.generate()`) realiza los siguientes pasos:

```java
private boolean shouldInjectSuggestion() {
    // 1. Obtener los últimos N mensajes de la sesión (ej. N=20)
    List<ChatMessage> recentMessages = session.getRecentMessages(N);
    
    // 2. Estructuras locales para esta evaluación
    Map<String, Integer> pageCount = new HashMap<>();
    Set<String> annotatedResources = new HashSet<>();
    
    // 3. Recorrer los mensajes en orden inverso (del más nuevo al más viejo)
    for (ChatMessage msg : reverse(recentMessages)) {
        if (msg instanceof ToolExecutionResultMessage tres) {
            String toolName = tres.toolName();
            String text = tres.text();
            
            // Extraer resource_id de la cabecera (buscar "RESOURCE_ID: ")
            String resourceId = extractResourceId(text);
            if (resourceId == null) continue;
            
            if (isReadingTool(toolName)) {
                // Herramienta de lectura: contar página
                pageCount.put(resourceId, pageCount.getOrDefault(resourceId, 0) + 1);
            } else if (toolName.equals("annotate_observation")) {
                // Anotación: extraer resource_id del JSON en el texto (si existe)
                String annotatedId = extractResourceIdFromAnnotation(text);
                if (annotatedId != null) {
                    annotatedResources.add(annotatedId);
                }
            }
        }
    }
    
    // 4. Filtrar: recursos que no han sido anotados y superan el umbral de páginas
    int threshold = getPageThreshold(); // ej. 3, configurable
    for (Map.Entry<String, Integer> entry : pageCount.entrySet()) {
        String rid = entry.getKey();
        int pages = entry.getValue();
        if (!annotatedResources.contains(rid) && pages >= threshold) {
            return true;
        }
    }
    return false;
}
```

**Nota:** El método `extractResourceId` busca en la cabecera la línea que comienza con `RESOURCE_ID:` (asumiendo que todas las herramientas de lectura la incluyen). `extractResourceIdFromAnnotation` parsea el JSON del argumento `resource_id`.

### 5.3. Inyección de la sugerencia efímera

Si `shouldInjectSuggestion()` devuelve `true`, el orquestador construye un mensaje de sugerencia y lo añade a la lista `context` que se enviará al LLM. El mensaje puede ser un `SystemMessage` (para que tenga autoridad) o una simulación de herramienta. Se elige `SystemMessage` por simplicidad:

```
SystemMessage: "Nota del sistema: Has leído varias páginas de recursos sin extraer información relevante. Si hay datos que deban conservarse tras la compactación, usa la herramienta 'annotate_observation' con el parámetro 'resource_id' correspondiente (el valor que aparece en las respuestas de lectura como 'RESOURCE_ID: ...'). Esta nota desaparecerá después de este turno."
```

Este mensaje **no se añade a `session.messages`**, solo se agrega a la lista temporal `context` que se pasa a `model.generate()`. Por tanto, no persiste.

**Lugar de inyección:** Justo después de obtener `context = session.getContextMessages(...)` y antes de `contextTrimmer` (o después, pero lo ideal es al final de la lista para no interferir con el flujo natural). Por ejemplo:

```java
List<ChatMessage> context = session.getContextMessages(activeCheckPoint, getBaseSystemPrompt());
if (shouldInjectSuggestion()) {
    context.add(SystemMessage.from(getSuggestionText()));
}
context = contextTrimmer(context);  // si procede
```

### 5.4. Configuración y parámetros

Los parámetros se almacenan en `settings.json` bajo una sección, por ejemplo:

```json
"orchestrator": {
  "suggest_annotation_enabled": true,
  "annotation_page_threshold": 3,
  "max_messages_to_scan": 20
}
```

- `suggest_annotation_enabled`: permite desactivar la funcionalidad.
- `annotation_page_threshold`: número de páginas leídas de un mismo recurso sin anotación para sugerir.
- `max_messages_to_scan`: cuántos mensajes hacia atrás se examinan (valor recomendado 20, que es la ventana de retención antes de la poda).

### 5.5. Modificaciones necesarias en el código

**A. En todas las herramientas de lectura (`FileReadTool`, `WebGetTikaTool`, `ReadPaginatedResourceTool`, etc.):**
- Asegurar que la cabecera de la respuesta incluya una línea `RESOURCE_ID: <id>`. Las herramientas paginadas ya lo hacen (para el HINT), pero debe estandarizarse.
- Para herramientas no paginadas (`file_read` cuando devuelve todo el contenido de una vez), también se debe añadir `RESOURCE_ID:` (por ejemplo, el path absoluto normalizado). Esto permite que el orquestador identifique el recurso.

**B. En `ReasoningServiceImpl`:**
- Añadir el método `shouldInjectSuggestion()` descrito anteriormente, que accede a `session.getMessages()`.
- Añadir los métodos auxiliares `extractResourceId(String toolResultText)` (busca `RESOURCE_ID:`) y `extractResourceIdFromAnnotation(String toolResultText)` (parsea JSON).
- Modificar `eventDispatcher` para que, después de obtener `context`, consulte `shouldInjectSuggestion()` y, si es true, añada el `SystemMessage`.

**C. En `annotate_observation` tool:**
- Asegurar que su especificación incluya el parámetro opcional `resource_id`. Actualizar la descripción para indicar que debe usarse cuando la anotación se refiera a un recurso leído previamente, proporcionando el mismo `resource_id` que aparece en las respuestas de lectura.

**D. En el system prompt (`reasoning-system.md`):**
- Añadir instrucciones claras sobre el uso de `annotate_observation` con `resource_id`, explicando que el `RESOURCE_ID` se encuentra en la cabecera de las respuestas de lectura y que sirve para que el sistema sepa qué recurso ha sido cubierto.

### 5.6. Flujo de trabajo resultante (ejemplo con un log de 5 páginas)

1. El LLM solicita la primera página del log. El paginador devuelve la página 1 con `RESOURCE_ID: log_web`. Esta respuesta se guarda en la sesión.
2. El LLM pide página 2, luego página 3. Cada respuesta lleva el mismo `RESOURCE_ID`.
3. En el turno en que el LLM recibe la página 3 (o en el siguiente turno, dependiendo de cuándo se evalúe `shouldInjectSuggestion`), el orquestador escanea los últimos mensajes (incluyendo las tres respuestas de lectura) y detecta que `log_web` tiene 3 páginas y no hay ninguna anotación con ese `resource_id` en los mensajes recientes. Supera el umbral (3). Entonces inyecta un `SystemMessage` efímero sugiriendo anotar.
4. El LLM recibe la sugerencia junto con la página 3 (o justo después). Puede decidir anotar.
5. Si anota con `resource_id="log_web"`, esa anotación se guarda como un `ToolExecutionResultMessage`. En futuros escaneos, el orquestador verá que el recurso ya está anotado y no volverá a sugerir (aunque sigan apareciendo más páginas del mismo recurso).
6. Si no anota, el orquestador seguirá viendo que el recurso tiene 3+ páginas sin anotación y podría sugerir de nuevo en turnos posteriores (cada vez que se cumpla la condición). Para evitar repetir excesivamente, se puede aumentar el umbral (ej. a 5) o simplemente aceptar que el LLM recuerde la sugerencia y actúe. Como las sugerencias son efímeras y no saturan el historial, no es un problema grave.

### 5.7. Manejo de recursos no paginados pero grandes

Para `file_read` de un archivo que no activa paginación (por ser pequeño, o porque la herramienta no está paginada), se puede considerar que una sola respuesta equivale a una "página". El orquestador contará esa única aparición. Si el umbral es 3, no sugerirá a menos que el mismo recurso se lea varias veces (lo cual es poco frecuente). Para estos casos, se podría establecer un umbral específico para recursos no paginados (ej. 1 lectura sin anotación) o simplemente confiar en que el LLM anotará si es relevante. Otra opción es unificar todas las lecturas: que `file_read` también use internamente la paginación cuando el archivo supere un tamaño (por ejemplo, 10KB), forzando así múltiples páginas y activando el mecanismo.

### 5.8. Limitaciones y trabajo futuro

- **Identificación fiable de `resource_id`:** Es crucial que todas las herramientas de lectura incluyan `RESOURCE_ID:` en su cabecera. Si alguna falta, el orquestador no podrá agrupar páginas. Se debe auditar y estandarizar.
- **Anotaciones que cubren múltiples recursos:** Actualmente, `annotate_observation` solo acepta un `resource_id`. Para cubrir varios, el LLM podría hacer múltiples anotaciones o se podría modificar la herramienta para aceptar una lista. Esto se puede añadir en el futuro si es necesario.
- **Sugerencias repetitivas:** Si el LLM ignora sistemáticamente las sugerencias, el orquestador podría seguir sugiriendo cada pocas páginas. Para mitigarlo, se puede implementar un contador de sugerencias basado en el propio historial (por ejemplo, contar cuántas veces ha aparecido un `SystemMessage` de sugerencia en los mensajes recientes, aunque sean efímeros no se guardan, así que no es posible). La solución más simple es confiar en que el LLM eventualmente anote o aumentar el umbral.
- **Interacción con la compactación:** Si el LLM anota abundantemente, la compactación preservará esa información. Si no anota, el conocimiento se pierde, pero el sistema ha hecho todo lo posible por ayudarle.

## 6. Ventajas de la solución basada en escaneo del historial

- **Sin estado global:** La decisión se calcula cada vez a partir de los mensajes almacenados, lo que la hace determinista y fácil de reiniciar.
- **No contamina el historial:** Las sugerencias son efímeras, no se guardan en BBDD ni en la sesión.
- **Oportuna:** Se emiten cuando se detecta un número significativo de páginas leídas sin anotar, antes de que la poda pueda eliminar información.
- **Configurable:** Umbrales y alcance ajustables mediante `settings.json`.
- **Eficiente:** Escanear hasta 20 mensajes en cada turno es una operación mínima.
- **Facilita la depuración:** Se puede loguear el resultado del escaneo para entender las decisiones.

## 7. Conclusión

La solución de **orquestador efímero con decisión basada en escaneo del historial** resuelve el problema de pérdida de información por poda y compactación de forma limpia, sin añadir estado global mutable. Se integra armoniosamente con la arquitectura existente de Noema, requiere modificaciones localizadas y es completamente configurable. Su implementación permitirá que el LLM reciba recordatorios oportunos para anotar información relevante, preservando así el conocimiento extraído de lecturas extensas a través de las compactaciones.
