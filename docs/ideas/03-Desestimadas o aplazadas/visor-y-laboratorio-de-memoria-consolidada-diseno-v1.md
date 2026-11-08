# Diseño: visor y laboratorio de puntos de guardado

**Estado:** diseño cerrado, pendiente de implementación.
**Documento de análisis previo:** `docs/ideas/01-En bruto/visor-y-laboratorio-de-memoria-consolidada-v1.md`.

## 1. Alcance

Este documento especifica la **capa de interfaz** de un addon del workspace para inspeccionar puntos de guardado y experimentar con el prompt del consolidador, y los **endpoints** que esa interfaz necesita.

Queda fuera: la implementación de los métodos de servicio que falten en la memoria episódica y en el servicio de consolidación, la mejora "Ir a nota" (backlog, ver documento de análisis) y cualquier forma de persistencia desde el addon.

## 2. Identidad del addon

| Propiedad | Valor |
|---|---|
| `id` | `checkpoints` |
| `title` | `Puntos de guardado` |
| `order` | `30` |
| `icon` | `null` |

Cumple el contrato de addon del workspace:

| Miembro | Comportamiento |
|---|---|
| `mount(panel)` | Inyecta el markup de tres columnas, verifica que existen los tres contenedores raíz, arranca la UI y dispara la carga de la lista. Devuelve promesa; rechaza si el markup no generó la estructura esperada. |
| `unmount()` | Suelta listeners, cancela peticiones en vuelo, `ready = false`. No hay nada que persistir. |
| `activate()` | Sin efecto. |
| `deactivate()` | Sin efecto: el estado de sesión sobrevive a ocultar la pestaña. |
| `getState()` | `null`. |
| `setState()` | Sin efecto. |
| `isDirty()` | `false`. |
| `confirmLeave()` | `true`. Cerrar no destruye nada irrecuperable. |

No persiste estado. La selección, el modo, el prompt editado y la última tirada viven solo en memoria y desaparecen al desmontar.

## 3. Estructura de componentes

```
addon
├── panel-lista                      índice de puntos de guardado
└── panel-memoria                    área de trabajo, dos modos
    ├── pestaña-inspeccion
    │   ├── panel-inspeccion-memoria      texto guardado
    │   └── panel-inspeccion-mensajes     turnos de origen
    └── pestaña-laboratorio
        ├── panel-laboratorio-prompt      banda plegable + botones
        ├── panel-laboratorio-memoria     texto generado
        └── panel-laboratorio-contraste
            ├── pestaña-mensajes
            └── pestaña-pensamiento
```

**Nota de nomenclatura.** El árbol original llamaba `panel-laboratorio-resultado` al panel que contiene mensajes y pensamiento. Ese nombre es equívoco: el resultado de la tirada es la memoria generada, que vive en `panel-laboratorio-memoria`; lo que hay a la derecha es el material de contraste. Se adopta `panel-laboratorio-contraste`.

**Ficheros.** Siguiendo el patrón de los addons existentes:

```
js/workspace/addons/checkpoints/
├── checkpoints-addon.js      contrato y montaje
├── checkpoints-markup.js     renderCheckpoints(panel)
├── checkpoints-ui.js         estado de sesión y orquestación
├── checkpoints-api.js        cliente de los endpoints
├── list/checkpoints-list.js  panel-lista
└── panes/
    ├── inspection-pane.js    par de inspección
    └── lab-pane.js           prompt, memoria generada y contraste
css/checkpoints.css
```

Las clases CSS llevan prefijo `cp-`. Los nodos se resuelven con `panel.querySelector`, no por id global, para no heredar la deuda de los addons antiguos y para no colisionar con `.conversation-container`, `.card`, `.role-line`, `.role-user` y `.role-assistant`, que la hoja de conversación inyecta en el DOM del contenedor donde se pinten los turnos.

## 4. Modelo de estado

Estado de sesión, en memoria, propiedad exclusiva de `checkpoints-ui.js`:

| Campo | Tipo | Descripción |
|---|---|---|
| `selectedId` | `number o null` | Punto de guardado seleccionado. El sujeto. |
| `mode` | `inspection o lab` | Modo activo del área de trabajo. |
| `contrastTab` | `messages o thinking` | Pestaña activa del panel de contraste. |
| `prompt` | `string o null` | Prompt en edición. `null` mientras no se haya cargado. |
| `promptCanonical` | `string o null` | Último prompt traído de disco. Base de comparación. |
| `thinking` | `string o null` | Pensamiento de la última tirada. `null` = no hay tirada. |
| `generated` | `string o null` | Texto generado en la última tirada. `null` = no hay tirada. |
| `busy` | `boolean` | Hay una consolidación en curso. |

No son estado: el contenido cargado de la lista, el detalle de cada punto de guardado ni el XML de turnos. Son datos de servidor cacheados por selección y se descartan al cambiar de sujeto.

**Predicados derivados.**

- `hasRun = generated !== null` — hay una tirada viva para la selección actual. Gobierna el tic de la pestaña de inspección.
- `promptDirty = prompt !== promptCanonical` — el prompt en pantalla no es el canónico. Gobierna el estado del botón de recargar.

## 5. Especificación de paneles

### 5.1 `panel-lista`

Ancho fijo ~240 px. No se mueve ni se colapsa.

- Orden descendente por `id`. Lo más reciente arriba.
- Controles en cabecera: selector de subcanal (con opción "todos") y campo de filtro por id o por número de turno.
- El filtro por id y turno se resuelve **en cliente** sobre las filas cargadas; no genera petición.
- Cada fila: identificador y tramo de turnos **de esa pasada**, no el rango acumulado. Formato `#94 · 1180–1224`.
- Fila seleccionada visualmente distinguida.
- Estado vacío: mensaje explícito cuando no hay puntos de guardado o cuando el filtro no devuelve nada. Son dos mensajes distintos.
- El identificador de la fila es copiable con un gesto.

### 5.2 `panel-inspeccion-memoria`

Texto del punto de guardado seleccionado, en solo lectura, con scroll propio. Cabecera con identificador, tramo de turnos, fecha y canal.

### 5.3 `panel-inspeccion-mensajes`

Turnos de origen, renderizados con el mismo pipeline que la pestaña Contexto del chat: XML de conversación transformado en cliente con la hoja `conversation.xsl`, produciendo tarjetas colapsables por turno. Sin render propio.

Muestra **el mismo conjunto** que el panel de contraste del laboratorio: es el input real de la tirada, no una ilustración. El alcance (`new` o `all`) se selecciona una vez y persiste al conmutar de modo.

### 5.4 `panel-laboratorio-prompt`

Banda plegable en la parte superior de la columna. **Cerrada por defecto.** Al desplegarse muestra un editor de texto con el prompt de sistema.

- Botón **Consolidar**: lanza la tirada con el prompt en pantalla.
- Botón **Recargar**: descarta la edición y trae el vigente de disco. Deshabilitado cuando `promptDirty` es falso; su estado es el único indicador de divergencia.
- Con `busy` activo, ambos botones se deshabilitan y el panel muestra que hay una operación en curso.
- El prompt nunca se envía a guardar. La vía de promoción es copiarlo, editar el fichero fuente y recargar.

### 5.5 `panel-laboratorio-memoria`

Al seleccionar un punto de guardado muestra el mismo contenido que `panel-inspeccion-memoria`. Tras una tirada muestra el texto generado. Cabecera indicando el origen del contenido: guardado o generado ahora.

### 5.6 `panel-laboratorio-contraste`

Dos pestañas, una visible cada vez.

- **Mensajes**: idéntico a `panel-inspeccion-mensajes`, mismo alcance, mismo pipeline.
- **Pensamiento**: contenido de la última tirada. Con `thinking` nulo y sin tirada, estado vacío neutro. Con tirada y sin pensamiento emitido, mensaje explícito de que esa ejecución no produjo razonamiento: no es un error y no debe presentarse como tal.

El pensamiento **se conserva al conmutar de pestaña** dentro de la misma selección y **se destruye al cambiar de sujeto**.

## 6. Transiciones

| Evento | Efecto |
|---|---|
| Montaje | Carga la lista. Sin selección. Área de trabajo en estado vacío. |
| Selección de fila | Carga detalle y turnos. Rellena `inspeccion-memoria`, `inspeccion-mensajes`, `laboratorio-memoria` y `contraste>mensajes` con el mismo contenido. Vacía `thinking` y `generated`. **Fuerza `mode = inspection`.** Si `prompt` es `null`, lo carga; si ya existe, lo conserva. |
| Conmutar a laboratorio | Sin recarga. Si `prompt` es `null`, lo pide en ese momento. |
| Conmutar a inspección | Sin recarga. Conserva tirada y pensamiento. |
| Consolidar | `busy = true`. Envía prompt en pantalla y alcance. Al responder, fija `generated` y `thinking` y `mode = lab`. Desbloquea. |
| Recargar prompt | Descarta edición, trae el vigente, actualiza `prompt` y `promptCanonical`. |
| Cambio de sujeto estando en laboratorio | Vuelve a inspección y descarta la tirada. La tirada pertenece al sujeto anterior y no se arrastra. |
| Error de red o de servidor | Desbloquea, conserva la selección, muestra el error en el panel afectado sin destruir el contenido previo. |
| Desmontaje | Cancela peticiones en vuelo y descarta todo. |

La regla del cambio de sujeto es deliberada: mantener el modo con otra selección dejaría un documento generado junto a los mensajes de otro punto de guardado, que es una trampa silenciosa.

## 7. Reglas transversales

- **No persistencia.** Ningún dato del addon sobrevive al desmontaje ni se escribe en el servidor. El único efecto lateral admitido es que una consolidación puede producir anotaciones en memoria episódica por acción de las herramientas del consolidador; el addon no las provoca por sí mismo más allá de lanzar la tirada.
- **Un solo sujeto.** El laboratorio nunca sustituye la selección. `selectedId` solo cambia por acción del usuario sobre la lista.
- **Sin degradaciones por datos incompletos.** Se asume que un punto de guardado tiene sus mensajes y su texto. Si la base de datos se ha manipulado y está rota, el addon falla; no se diseña para ese escenario.
- **Accesibilidad.** Los botones que solo llevan icono exponen `title` y `aria-label`. El panel de contraste usa `role="tablist"` con `aria-selected`.

## 8. Contratos de datos

### 8.1 Elemento de índice

```
{
  id: number,
  first: number,        // turno inicial del rango acumulado
  last: number,         // turno final
  timestamp: string,    // ISO 8601
  subchannel: string,
  previousId: number o null   // punto de guardado anterior del mismo canal
}
```

El tramo de esa pasada se deriva en el cliente: desde `previous.last + 1` hasta `last`, o desde `first` hasta `last` si no hay predecesor.

### 8.2 Detalle

```
{
  id, first, last, timestamp, subchannel,
  text: string,
  previousId: number o null,
  newTurnsFrom: number,
  newTurnsTo: number
}
```

### 8.3 Tirada

```
{
  thinking: string o null,
  text: string,
  durationMs: number,
  model: string,
  invalidCitations: [string]
}
```

## 9. Endpoints

Convenio del proyecto: recurso en plural, `{id}` en path, JSON salvo donde se indique.

| Método | Ruta | Petición | Respuesta |
|---|---|---|---|
| `GET` | `/api/checkpoints` | `subchannel?`, `offset=0`, `limit=500`, `order=desc` | `{ items: [ElementoDeIndice], total, offset, limit }` |
| `GET` | `/api/checkpoints/{id}` | — | `Detalle` · `404` si no existe |
| `GET` | `/api/checkpoints/{id}/turns.xml` | `scope=new` o `scope=all`, por defecto `new` | `application/xml`, documento raíz `conversation`, con la hoja declarada |
| `GET` | `/api/checkpoints/prompt` | — | `{ systemPrompt, path }` |
| `POST` | `/api/checkpoints/{id}/consolidate` | `{ systemPrompt?: string, scope: "new" o "all" }` | `Tirada` |

Notas de contrato:

- **`limit`** tiene default alto y el cliente lo omite: se carga todo. Existe para que bajar el default y añadir "cargar más" no rompa a los consumidores.
- **`total`** refleja el resultado del filtro de canal, no el de la paginación.
- **`turns.xml`** devuelve el mismo formato que `/api/chat/{terminalId}/context.xml` para reutilizar la hoja de estilo. Con alcance válido pero sin turnos, devuelve un documento vacío válido.
- **`consolidate` no persiste por contrato.** No escribe en la memoria episódica ni crea puntos de guardado, con independencia de lo que hagan las herramientas del consolidador durante la ejecución.
- **`systemPrompt` ausente** significa "usa el vigente leído de disco en el momento de la petición", no "usa el que el servicio cargó al arrancar".
- **Errores**: `400` parámetros inválidos, `404` recurso inexistente, `409` si ya hay una consolidación en curso, `504` si la tirada excede el tiempo máximo. El cuerpo del error es `{ error: string }`.

## 10. Decisiones pendientes

No forman parte del diseño cerrado y deben resolverse antes de implementar:

1. **Alcance por defecto de los mensajes.** Se recomienda `new`, porque es el input real de la generación; `all` mostraría más turnos de los que alimentaron la tirada y el contraste mentiría. Sin confirmar.
2. **Serialización de tiradas.** Se recomienda permitir una sola en curso y responder `409` a la segunda, dado que el consolidador tiene herramientas con efectos laterales.
3. **Cancelación.** Si una tirada larga debe poder abortarse desde la UI o solo abandonarse esperando el `504`.
4. **Timeout del cliente.** Debe ser superior al del servidor para que el usuario reciba el error del servidor y no un fallo de red genérico.

## 11. Criterios de aceptación

- Seleccionar una fila rellena los seis paneles de contenido con los datos correctos y deja el pensamiento y el texto generado vacíos.
- Conmutar de modo no dispara peticiones nuevas salvo la primera carga del prompt.
- La misma variante del prompt sobrevive a diez cambios de selección consecutivos.
- Recargar restaura el prompt canónico y el botón vuelve a estado deshabilitado hasta la siguiente edición.
- Una tirada no altera el contenido de `panel-inspeccion-memoria`.
- Cambiar de fila desde el laboratorio devuelve a inspección y descarta la tirada.
- El panel de contraste conserva el pensamiento al ir a inspección y volver, mientras no cambie el sujeto.
- El XML de turnos se pinta con la misma apariencia que la pestaña Contexto del chat sin CSS adicional.
- La lista con varios cientos de entradas responde sin paginación perceptible y el filtro por turno localiza una fila sin petición al servidor.
- Ninguna ruta de escritura modifica la memoria del agente como consecuencia de una tirada.
