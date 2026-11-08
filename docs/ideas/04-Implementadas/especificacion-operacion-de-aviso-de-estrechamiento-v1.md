# Especificación: Operación de aviso de estrechamiento

## 0. Por qué surge

La paginación como primitiva de sistema resolvió el problema de que una herramienta de ingesta volcara un volumen arbitrario de datos en la memoria reciente. Pero introdujo un efecto secundario: el LLM puede leer un recurso de 12.000 líneas página a página sin ser consciente de que tiene a su disposición mecanismos para acceder solo a un subconjunto. El resultado es que el LLM consume atención y tokens releyendo datos que quizá no necesita, precisamente la fuga que describe el material de referencia sobre economía de contexto.

El arnés no puede decidir por el LLM qué filtrar. No sabe si la salida es un log, un poema o un contrato. Cualquier filtro automático por contenido es una apuesta que, cuando falla, destruye información sin aviso. Lo que sí puede hacer el arnés es **informar al LLM de que existe una opción de estrechamiento** y dejar que decida. Eso es lo que cubre esta operación.

La operación es, por tanto, un empujón informativo. No filtra, no trunca, no interpreta. Solo hace explícita una posibilidad que el LLM puede ignorar sin consecuencias.

## 1. Descripción breve

Operación del pipeline de proyección de contexto que detecta turnos de ingesta posteriores a un watermark y evalúa para cada uno una serie de detectores configurables. Si el recurso asociado al turno supera los umbrales de tamaño o dispara algún detector de nivel, inyecta una notificación efímera en la memoria proyectada informando del volumen, de los conteos detectados y de la sintaxis de las herramientas de estrechamiento disponibles. La operación puede desactivarse por configuración.

No modifica la memoria reciente, no modifica la memoria episódica, no altera el buffer del recurso, no filtra ni trunca nada. Su única salida es una notificación efímera.

## 2. Especificación detallada

### 2.1 Ubicación en la arquitectura

- **Estrato afectado:** memoria proyectada, exclusivamente.
- **Fase del pipeline:** operaciones de advertencia, tras las operaciones de anclaje y poda, antes de las de percepción temporal y consciencia periférica. El orden importa: si un turno de ingesta ha sido podado antes de que esta operación lo inspeccione, ya no hay nada que avisar.
- **Frecuencia:** se ejecuta en cada proyección. Escanea todos los turnos posteriores al watermark y aplica las reglas a cada uno que sea un resultado de herramienta de ingesta. Si ningún turno nuevo lo es, no emite nada pero el watermark avanza igualmente.

### 2.2 Estado

La operación mantiene un único campo persistente entre proyecciones: `last_processed_turn_id`, el ID del último turno evaluado en la proyección anterior.

En cada ejecución:

1. Localiza `last_processed_turn_id` en la memoria reciente.
2. Si lo encuentra, evalúa todos los turnos **posteriores** a él hasta el más reciente.
3. Si no lo encuentra (porque ha sido podado, consolidado o la sesión se reinició), evalúa **todos** los turnos de la memoria reciente.
4. Actualiza `last_processed_turn_id` al ID del turno más reciente de la memoria reciente, haya emitido aviso o no.

La regla "haya emitido aviso o no" es importante: el watermark avanza siempre, no solo cuando se emite una notificación. Si no avanza, la proyección siguiente reevaluaría los mismos turnos y reemitiría avisos.

El watermark es estado del arnés, no de la memoria. No vive en la episódica, no vive en la reciente, no se proyecta. Es un campo interno de la operación que sobrevive entre proyecciones mientras dure la sesión. Si la sesión se reinicia, se pierde, y eso es correcto: la memoria reciente también se reinicia y no hay nada que evaluar.

### 2.3 Detectores

La operación no emite aviso basándose en un único criterio, sino en la combinación de detectores configurables. Cada detector es independiente y puede activarse o desactivarse por separado.

#### Detector de tamaño

- Condición: `total_lines(resource) > threshold_lines` o `total_chars(resource) > threshold_chars`.
- Umbrales configurables, con valores por defecto conservadores (por ejemplo, 500 líneas o 20.000 caracteres).
- El dato lo proporciona el buffer del recurso, que ya lo calcula al crearse.

#### Detectores de nivel

La operación mantiene una **lista de detectores de nivel**. Cada detector es una entrada independiente con los siguientes campos:

| Campo | Tipo | Descripción |
|---|---|---|
| `tokens` | lista de cadenas | Patrones literales a contar (por ejemplo, `["ERROR", "FATAL"]`) |
| `threshold` | entero | Número mínimo de apariciones totales para que el detector dispare |
| `hint` | cadena | Texto que se incluye en la notificación si el detector dispara |
| `enabled` | booleano | Activa o desactiva este detector concreto |
| `mime_exclude` | lista de cadenas | MIME types para los que este detector no se aplica |
| `mime_include` | lista de cadenas | Si no está vacía, solo se aplica a estos MIME types |

Semántica:

- El conteo se realiza **una sola vez**, al crear el buffer del recurso, y se almacena en su metadata. La operación de aviso solo lee el resultado; no recalcula.
- Un detector dispara si `sum(count(token) for token in tokens) > threshold`, **y** el MIME type del recurso pasa el filtro MIME (ver 2.3.3).
- Si varios detectores disparan, **todos** se presentan en la notificación, en el orden en que aparecen en la lista de configuración. El LLM, con el contexto de la tarea y del recurso, decide cuál es más relevante.
- El `hint` es una política declarada por el usuario, no una inferencia del arnés. El arnés no decide sugerir `ERROR`; el usuario ha configurado ese detector y ha decidido qué texto presentar cuando dispare.
- El conteo es literal, sensible a mayúsculas por defecto. No se admiten expresiones regulares en `tokens`.

#### Filtro MIME por detector

Cada detector de nivel puede declarar dos listas de MIME types: `mime_exclude` y `mime_include`. La evaluación sigue estas reglas:

1. **Orden de la lista.** Las entradas de cada lista se evalúan en el orden en que aparecen. **Primera coincidencia gana**, sea exacta o wildcard.
2. **Listas vacías.** Si `mime_exclude` está vacía, no se excluye nada. Si `mime_include` está vacía, no se restringe nada (todo pasa).
3. **Precedencia entre listas.** Primero se evalúa `mime_exclude`. Si el MIME coincide con alguna entrada, el detector no se aplica. Si no coincide, se evalúa `mime_include`. Si `mime_include` está vacía, el detector se aplica. Si tiene entradas y el MIME coincide con alguna, se aplica. Si tiene entradas y el MIME no coincide con ninguna, no se aplica.
4. **Wildcards.** Las entradas admiten `*` y `?` en cualquier posición. `text/*` casa con todos los tipos de texto; `*/*` casa con cualquier tipo; `text/x-jav?-source` casa con `text/x-java-source` y variantes de un carácter.
5. **Case sensitivity.** Los MIME types y las entradas de las listas se normalizan a minúsculas antes de comparar.

Casos cubiertos con las dos listas:

- **Sin configurar:** ambas vacías → todos los MIME pasan.
- **Solo exclusión:** `mime_exclude` con valores, `mime_include` vacía → todo pasa menos los excluidos.
- **Solo inclusión:** `mime_include` con valores, `mime_exclude` vacía → solo pasan los incluidos.
- **Ambas activas:** primero se aplica la exclusión, luego la inclusión.

La implementación del matching puede delegarse en `FilenameUtils.wildcardMatch` de Apache Commons IO, normalizando ambos lados a minúsculas.

#### Sin detector de contenido semántico

La operación no intenta clasificar el contenido del recurso (log, código, prosa, datos). No infiere el tipo de documento. No decide qué es relevante. El MIME type es un hecho objetivo que el arnés ya calcula con Tika; la operación se limita a usarlo como condición, no como interpretación.

### 2.4 Construcción de la notificación

La notificación es un bloque de texto breve, estructurado en tres partes:

1. **Encabezado identificativo.** Una etiqueta reconocible (por ejemplo, `[LECTURA EXTENSA]`) y el `resource_id` del recurso, para que el LLM pueda referenciarlo en llamadas posteriores.
2. **Cuerpo informativo.** Volumen del recurso (líneas o caracteres) y, si algún detector de nivel disparó, un bloque por cada uno con su `hint` y sus conteos.
3. **Recordatorio de sintaxis.** Una línea con la forma de invocación de las herramientas de estrechamiento disponibles sobre el recurso. La lista es neutral: se enumeran las capacidades (búsqueda por patrón, búsqueda difusa, recorte por cola), no se sugiere un patrón concreto.

La notificación no incluye:

- Interpretaciones del contenido ("parece un log", "los errores están al final").
- Patrones concretos sugeridos por el arnés ("usa `ERROR`"). Los `hint` solo aparecen si el usuario los ha configurado.
- Prescripciones de estrategia ("deberías filtrar").
- Ordenación por severidad. Los detectores se presentan en el orden de configuración.

### 2.5 Ejemplo

Sin detectores de nivel disparados:

```text
[LECTURA EXTENSA] Recurso r_7f3a: 12.400 líneas.
Sintaxis de estrechamiento disponible:
  grep_resource(resource_id, pattern)
  tail_resource(resource_id, n)
  fuzzy_search_resource(resource_id, query)
```

Con dos detectores disparados:

```text
[LECTURA EXTENSA] Recurso r_7f3a: 12.400 líneas.
Niveles detectados:
  - ERROR: 4 apariciones. Hint: "Considera filtrar por ERROR para localizar fallos."
  - WARN: 128 apariciones. Hint: "Considera filtrar por WARN si buscas advertencias."
Sintaxis de estrechamiento disponible:
  grep_resource(resource_id, pattern)
  tail_resource(resource_id, n)
  fuzzy_search_resource(resource_id, query)
```

El LLM lee los conteos y los hints configurados por el usuario, y decide. El arnés no ha decidido por él.

### 2.6 Configuración

La operación expone los siguientes parámetros de configuración:

| Parámetro | Tipo | Valor por defecto | Efecto |
|---|---|---|---|
| `enabled` | booleano | `true` | Activa o desactiva la operación completa |
| `threshold_lines` | entero | 500 | Umbral del detector de tamaño por líneas |
| `threshold_chars` | entero | 20000 | Umbral del detector de tamaño por caracteres |
| `size_detector_enabled` | booleano | `true` | Activa el detector de tamaño |
| `level_detectors` | lista | ver abajo | Lista de detectores de nivel |
| `include_syntax_hint` | booleano | `true` | Incluye el recordatorio de sintaxis en la notificación |

Cada entrada de `level_detectors` tiene la estructura descrita en 2.3: `tokens`, `threshold`, `hint`, `enabled`, `mime_exclude`, `mime_include`.

Ejemplo de configuración por defecto:

```yaml
level_detectors:
  - tokens: ["ERROR", "FATAL"]
    threshold: 1
    hint: "Considera filtrar por errores para localizar fallos."
    enabled: true
    mime_exclude:
      - "text/x-java-source"
      - "text/x-python"
    mime_include: []
  - tokens: ["WARN", "WARNING"]
    threshold: 20
    hint: "Considera filtrar por advertencias si buscas avisos."
    enabled: true
    mime_exclude:
      - "text/x-java-source"
      - "text/x-python"
    mime_include: []
```

La configuración puede declararse a nivel global del arnés o sobreescribirse por herramienta de ingesta. Por ejemplo, `file_read` puede tener umbrales distintos a `execute_command`.

### 2.7 Interacción con el buffer de recurso

La operación presupone la existencia del buffer temporal descrito en la sección de ingesta: cuando una herramienta de ingesta produce una salida paginable, el arnés la almacena en un buffer efímero con un `resource_id` unívoco, un TTL y una metadata que incluye el volumen total, el MIME type y, si los detectores de nivel están activos, el conteo por token.

La operación de aviso:

- **Lee** del buffer la metadata (`total_lines`, `total_chars`, `mime_type`, conteos de nivel).
- **No escribe** en el buffer.
- **No retiene** el buffer más allá de su TTL natural.
- **No depende** de que el buffer siga existiendo para emitir el aviso: si el buffer se ha liberado pero el turno con la cabecera del recurso sigue en memoria reciente, el aviso puede emitirse igualmente con los datos que la cabecera contenga.

La consecuencia práctica es que el LLM puede leer el aviso, decidir estrechar y ejecutar `grep_resource` sobre el buffer mientras esté vivo. Si el buffer ha expirado, la herramienta de estrechamiento fallará y el LLM deberá re-ejecutar la ingesta. Eso es aceptable y no responsabilidad de esta operación.

### 2.8 Casos límite

- **Llamadas paralelas.** Todos los turnos de tool-result posteriores al watermark se evalúan en la misma proyección. Si varios son de ingesta y superan los umbrales, todos reciben su aviso correspondiente.
- **Recurso con una sola página.** El turno es de ingesta, pero el recurso no supera el umbral. No se emite aviso.
- **Turno de ingesta seguido de respuesta del LLM.** El turno de ingesta se evalúa cuando llega. La respuesta del LLM no es de ingesta y no se evalúa. El watermark avanza a ella.
- **Watermark no encontrado en memoria reciente.** Se evalúan todos los turnos del buffer. No hay reemisión porque los turnos del buffer son posteriores al watermark desaparecido.
- **Memoria reciente vacía.** El watermark se resetea a nulo.
- **Operación desactivada.** El watermark no avanza. Al reactivarse, se evalúa todo lo acumulado desde la última vez que estuvo activa. Es el comportamiento correcto: el usuario querrá ver avisos de lo que se perdió mientras estaba apagada.
- **Recurso rehidratado.** Entra como turno nuevo, posterior al watermark, y se evalúa. Puede recibir aviso aunque ya lo tuviera antes.
- **Detectores de nivel sin coincidencias.** Si el conteo total de un detector no supera su umbral, no se incluye. Si ningún detector dispara, solo se incluye el volumen.
- **MIME type filtrado.** Si el MIME type del recurso coincide con una entrada de `mime_exclude` de un detector, ese detector no se aplica. Si `mime_include` tiene entradas y el MIME no coincide con ninguna, el detector no se aplica.
- **MIME type desconocido.** Si Tika no puede determinar el MIME type, se trata como `application/octet-stream`. Los filtros MIME se evalúan con ese valor.
- **Salida binaria o no textual.** El conteo de tokens se realiza sobre la representación textual; si esta no es significativa, los detectores de nivel simplemente no encontrarán coincidencias y el aviso quedará reducido al tamaño. No hay caso especial.

## 3. Qué NO hace esta operación

Conviene dejarlo explícito para evitar que un lector futuro la confunda con un mecanismo de filtrado:

- No filtra la salida de la herramienta.
- No trunca el contenido.
- No decide qué es relevante.
- No interpreta el contenido del recurso.
- No re-ejecuta la herramienta.
- No modifica el buffer.
- No modifica la memoria episódica.
- No prescribe patrones de búsqueda por iniciativa propia. Los `hint` solo aparecen si el usuario los ha configurado.
- No ordena los detectores por severidad. Se presentan en el orden de configuración.
- No mantiene estado más allá del watermark.
- No sustituye a la paginación ni a la poda ni a la destilación forzada.

Es una operación informativa. Su única salida es una notificación efímera en la memoria proyectada.

## 4. Criterios de aceptación

Para considerar la implementación correcta:

1. Si hay turnos nuevos posteriores al watermark y alguno es de ingesta con recurso que supera el umbral de tamaño, se emite notificación para ese turno.
2. Si hay varios turnos de ingesta nuevos, cada uno recibe su notificación si procede.
3. Si no hay turnos nuevos, no se emite nada y el watermark no cambia.
4. Si hay turnos nuevos pero ninguno es de ingesta, no se emite nada y el watermark avanza.
5. El watermark avanza siempre al turno más reciente tras cada ejecución.
6. Si el watermark no se encuentra en memoria reciente, se evalúan todos los turnos del buffer.
7. Si un detector de nivel dispara, su `hint` y sus conteos se incluyen en la notificación. Si varios disparan, todos se incluyen en el orden de configuración.
8. Si ningún detector dispara, solo se incluye el volumen.
9. Desactivar la operación detiene la emisión de avisos y congela el watermark.
10. La notificación no contiene patrones sugeridos por el arnés ni interpretaciones del contenido.
11. La operación no modifica ningún estrato de memoria ni el buffer del recurso.
12. La operación no mantiene estado más allá del watermark.
13. El coste de la operación en cada proyección es proporcional al número de turnos nuevos, no al tamaño de los recursos.
14. Con `mime_exclude` y `mime_include` vacías en un detector, este se aplica a todos los recursos.
15. Con `mime_exclude` configurada y `mime_include` vacía, el detector se aplica a todos los recursos excepto a los que coincidan con alguna entrada de exclusión.
16. Con `mime_include` configurada y `mime_exclude` vacía, el detector se aplica solo a los recursos cuyo MIME coincida con alguna entrada de inclusión.
17. En el matching MIME, primera coincidencia gana según el orden de la lista. Los wildcards `*` y `?` se interpretan según `wildcardMatch`.
18. La comparación de MIME types es case-insensitive.

## 5. Relación con otras piezas

- **Paginación como primitiva:** proporciona el `resource_id` y el volumen total que esta operación consume. Sin paginación, esta operación no tiene de dónde leer.
- **Buffer de recurso:** proporciona la metadata (MIME type, conteos de nivel) y el sustrato sobre el que el LLM ejecutará el estrechamiento. Sin buffer, el aviso sigue teniendo sentido pero el estrechamiento requiere re-ejecución.
- **Detección de MIME con Tika:** proporciona el MIME type del recurso, calculado en el momento de crear el buffer. La operación no recalcula el MIME; lo lee de la metadata.
- **Destilación forzada:** hermana en espíritu (ambas empujan al LLM a decidir antes de que sea tarde), distinta en disparador (datos a punto de ser podados vs. lectura activa recién abierta) y en estrato (ninguna de las dos escribe en episódica).
- **Directivas del prompt:** complementarias. La operación empuja en el momento; las directivas establecen el hábito general. Ninguna sustituye a la otra.
- **Trimming de memoria proyectada:** esta operación actúa antes de que el turno envejezca. El trimming actúa cuando el turno sale de la zona de seguridad. Son fases distintas del mismo ciclo de vida del turno.
