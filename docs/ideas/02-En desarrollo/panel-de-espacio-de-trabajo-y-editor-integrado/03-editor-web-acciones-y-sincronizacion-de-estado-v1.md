
# Editor Web: Acciones y Sincronización de Estado

## 1. Objetivo y alcance

Dotar al editor Ace embebido de cuatro acciones (Guardar, Diagnóstico, Formatear, Ajustar imports) y de un mecanismo de sincronización de estado con el servidor, que el agente podrá consultar en fases posteriores.

Los endpoints nuevos se crean en esta fase con implementaciones dummy que devuelven respuestas coherentes, para poder validar el UI end-to-end. La lógica real de cada servicio se rellenará más adelante sin tocar el frontend.

**Fuera de alcance en esta fase:** integración cognitiva con el agente (`EditorAwarenessOperation`, tool `editor_open_file`, SSE `editor_open`), implementación real de diagnósticos/formato/fiximports, y personalización de estilos del searchbox de Ace.

---

## 2. Arquitectura general

### 2.1. Disposición de la toolbar

El panel del editor (`workspace-editor-pane`) apila tres filas verticalmente:

```
[ pestañas, scroll horizontal ................................... ]
[ wrap ] [ guardar ] [ diagnóstico ] [ formato ] [ fiximports ]   ← toolbar, alineada a la derecha
[ editor Ace                                                              ]
```

- **Fila 1 — Pestańas** (`#workspace-editor-tabs`): ancho completo, scroll horizontal cuando hay muchas. Sin botones compartiendo espacio.
- **Fila 2 — Toolbar** (`#workspace-editor-toolbar`): botones alineados a la derecha. **Ya existe** con el botón de wrap; los cuatro botones nuevos se ańaden aquí.
- **Fila 3 — Editor** (`#workspace-editor`): instancia única de Ace.

Razón de mantenerlas separadas: si la toolbar comparte fila con las pestańas, ambas compiten por el ancho cuando el panel se estrecha o cuando hay muchas pestańas abiertas, y una acaba empujando a la otra. En editores que las fusionan, la toolbar salta de fila de forma impredecible al abrir la enésima pestańa. Separadas, cada una crece por su lado sin interferir.


### 2.2. Mapa de extensiones soportadas

Estructura en `editor-ui.js` que asocia cada acción con una lista de extensiones soportadas. Admite patrones y expresiones regulares:

```js
const ACTION_EXTENSIONS = {
    save:        ['.*'],          // cualquier fichero
    diagnostics: ['java'],
    format:      ['java'],
    fiximports:  ['java']
};
```

Decisión: cuando una extensión no aplica, el botón se **deshabilita**, no se oculta. El usuario debe saber que la acción existe y que no aplica al fichero activo.

### 2.3. Mecanismo de estado de botones

Función `updateToolbarState()` que se llama:
- Al cambiar de pestaña activa.
- Al cambiar el estado *dirty* del fichero activo.
- Al arrancar la toolbar.

Consulta la extensión del fichero activo y su estado *dirty*, y aplica `disabled` a cada botón según corresponda.

---

## 3. Contratos de endpoints

### 3.1. `POST /api/files/content` (ya existe)

Sin cambios. Usado por el botón Guardar.

### 3.2. `POST /api/editor/state` (nuevo)

Push del estado actual del editor desde el cliente al servidor. Sin lógica de negocio en el servidor: almacena el último snapshot en un mapa en memoria indexado por `terminalId`.

**Request:**
```json
{
  "terminalId": "default",
  "openFiles": [
    { "path": "/ruta/absoluta/A.java", "dirty": true },
    { "path": "/ruta/absoluta/B.md",   "dirty": false }
  ],
  "activeFile": "/ruta/absoluta/A.java",
  "cursorLine": 45,
  "selectionStartLine": 45,
  "selectionEndLine": 58
}
```

**Response:** `200 {}`. No devuelve información útil al cliente.

**Persistencia:** mapa en memoria `Map<String, EditorState>`. Sin persistencia en disco. Se pierde al reiniciar el servidor, lo cual es aceptable porque el estado es efímero por naturaleza.

### 3.3. `POST /api/editor/diagnostics` (nuevo, dummy)

Solicita diagnósticos (errores, warnings) para un fichero Java concreto.

**Request:** `{ "file": "/ruta/absoluta/A.java" }`

**Response (formato definitivo):**
```json
{
  "annotations": [
    { "row": 12, "column": 8, "text": "Símbolo no encontrado", "type": "error" },
    { "row": 20, "column": 0, "text": "Variable no usada", "type": "warning" }
  ]
}
```

Campos alineados con `session.setAnnotations` de Ace (`row` y `column` son 0-indexados). El servidor adaptará la salida del LSP o herramienta MCP a este formato.

**Comportamiento en esta fase:** devolver dos anotaciones de ejemplo. El frontend las pinta con `session.setAnnotations(annotations)` sobre la sesión correspondiente.

**Trabaja sobre disco, no sobre buffer.** Requiere que no haya cambios pendientes en el editor (el botón está deshabilitado si `dirty === true`). No fuerza recarga posterior: es una operación de solo lectura.

### 3.4. `POST /api/editor/format` (nuevo, passthrough)

Formatea el contenido del buffer del editor.

**Request:**
- URL: `POST /api/editor/format?ext=java`
- Body: `text/plain; charset=UTF-8` con el contenido crudo del buffer.
- Sin JSON, sin escapes. El contenido va tal cual.

**Response:**
- `200 OK`
- Body: `text/plain; charset=UTF-8` con el contenido formateado.

**Comportamiento en esta fase:** el servidor devuelve exactamente lo que recibe (passthrough).

**El cliente tras aplicar la respuesta:**
- Reemplaza el contenido de la sesión con `session.setValue(newContent, -1)` para preservar el historial de undo.
- **Marca la pestaña como *dirty*.** El usuario aún no ha guardado el cambio.

### 3.5. `POST /api/editor/fiximports` (nuevo, dummy)

Organiza los imports del fichero Java en disco.

**Request:** `{ "file": "/ruta/absoluta/A.java" }`

**Response:** `{ "status": "ok" }`

**Comportamiento en esta fase:** devuelve OK sin hacer nada.

**Trabaja sobre disco, no sobre buffer.** Requiere que no haya cambios pendientes (el botón está deshabilitado si `dirty === true`).

**El cliente tras recibir OK:**
- Recarga el contenido del fichero desde `GET /api/files/content`.
- Reemplaza el contenido de la sesión con `session.setValue(newContent, -1)`.
- **No marca *dirty*.** Disco y editor quedan sincronizados.

---

## 4. Comportamiento de la UI

### 4.1. Dirty tracking

Estado *dirty* por sesión. Se calcula comparando el contenido actual de la sesión con el contenido original que se cargó. Se actualiza en el listener `editor.on('change')`.

- La pestaña muestra `●` junto al nombre si el fichero está sucio.
- El botón Guardar se habilita solo si el fichero activo está sucio.
- Los botones Diagnóstico y Fiximports se deshabilitan si el fichero activo está sucio.

### 4.2. Estado de los botones

| Botón | Habilitado cuando |
|---|---|
| Guardar | Extensión soportada Y fichero activo *dirty* |
| Diagnóstico | Extensión `java` Y fichero activo no *dirty* |
| Formato | Extensión `java` (independiente de *dirty*) |
| Fiximports | Extensión `java` Y fichero activo no *dirty* |
| Wrap | Siempre |

### 4.3. Atajos de teclado

| Atajo | Acción |
|---|---|
| `Ctrl+S` | Guardar (guarda + push state) |
| `Shift+Alt+F` | Formatear |
| `Shift+Ctrl+I` | Ajustar imports |
| `Ctrl+F` | Buscar (ya implementado con `ext-searchbox`) |
| `Ctrl+H` | Reemplazar (ya implementado con `ext-searchbox`) |

Diagnóstico no lleva atajo por ahora.

### 4.4. Acción Guardar

1. Comprueba que el fichero activo existe y está *dirty*.
2. Llama a `POST /api/files/content` con `{file, content}`.
3. Si OK:
   - Llama a `POST /api/editor/state` (push inmediato, sin debounce).
   - Limpia el estado *dirty* de la sesión y de la pestaña.
   - Muestra toast de éxito.
4. Si error: toast de error, no limpia *dirty*.

Sin restricciones sobre qué ficheros se pueden modificar. El editor es responsabilidad del usuario.

### 4.5. Cierre de pestaña con cambios pendientes

Si la pestaña a cerrar está *dirty*, mostrar diálogo de confirmación:

> El fichero *X* tiene cambios sin guardar. ¿Guardar antes de cerrar?
> [Guardar] [Descartar] [Cancelar]

- **Guardar**: ejecuta el flujo de Guardar, y si OK cierra la pestaña.
- **Descartar**: cierra la pestaña sin guardar.
- **Cancelar**: no cierra.

---

## 5. Sincronización de estado (push)

### 5.1. Cuándo enviar

Cuatro disparadores, dos con envío inmediato y dos con debounce:

**Inmediato:**
- Cambio de pestaña activa.
- Tras un Guardar exitoso.

**Con debounce (~300-400 ms, trailing edge):**
- `editor.selection.on('changeSelection')` ? cubre cursor y selección.
- `editor.on('change')` ? cubre cambios de contenido (y por tanto *dirty*).

**Trailing edge:** cada tecla o cambio reinicia el temporizador. No se envía hasta que pasan N ms sin actividad.

### 5.2. Filtro por diferencia

Mantener dos snapshots: `lastSentState` y `currentState`. Antes de enviar, comparar. Si son iguales, no enviar. Esto evita pushes redundantes cuando el cursor se mueve dentro de la misma línea, cuando el fichero cambia *dirty* y vuelve a limpio sin diferencia efectiva, etc.

### 5.3. Flush al cerrar la pestaña del navegador

En `beforeunload`, si hay un push pendiente, enviarlo con `navigator.sendBeacon('/api/editor/state', blob)`.

`sendBeacon` es una API del navegador que permite enviar datos al cerrar la página sin que el navegador cancele la petición, cosa que sí ocurre con `fetch`. Si falla, el cierre no se cancela. No hay reintento.

### 5.4. Fallos del push

Silenciosos. Sin retry, sin toast. El siguiente push lleva el estado más reciente y ya. Es un push de estado, no una transacción.

---

## 6. Estrategia de implementación

Tres fases. Cada una deja el sistema en un estado coherente y probado.

### Fase A ? Base: dirty tracking + Guardar + state push

Es el cimiento. Sin esto, el resto de botones operan sobre una UI que no comunica su estado.

1. **Backend:** endpoint `POST /api/editor/state` con almacén en memoria (dummy funcional, no lógica real de negocio).
2. **Frontend ? dirty tracking:** listener `editor.on('change')`, comparación con el contenido original, marca `●` en pestaña, gestión del estado *dirty*.
3. **Frontend ? Guardar:** botón en la toolbar, `Ctrl+S`, flujo completo (guardar + push state + limpiar *dirty*).
4. **Frontend ? state push:** disparadores (cambio de pestaña, guardar, debounce de cursor/selección, debounce de contenido), filtro por diferencia, `sendBeacon` en `beforeunload`.
5. **Frontend ? cierre de pestaña sucia:** diálogo de confirmación.

Al terminar la Fase A, el editor permite editar, guardar y avisar al servidor del estado del usuario. Todo lo demás se apoya en esto.

### Fase B ? Botones dependientes de extensión + Diagnóstico + Formato

1. **Frontend ? mapa de extensiones:** estructura `ACTION_EXTENSIONS` y función `updateToolbarState()`.
2. **Frontend ? toolbar completa:** tres botones más (diagnóstico, formato, fiximports) con estado inicial deshabilitado.
3. **Backend ? `POST /api/editor/diagnostics`:** dummy que devuelve dos anotaciones de ejemplo.
4. **Frontend ? Diagnóstico:** llamada al endpoint, aplicación con `session.setAnnotations()`.
5. **Backend ? `POST /api/editor/format`:** passthrough.
6. **Frontend ? Formato:** enviar buffer, recibir formateado, aplicar con `setValue(content, -1)`, marcar *dirty*.

Al terminar la Fase B, dos de los tres botones Java-only funcionan de punta a punta.

### Fase C ? Fiximports

1. **Backend ? `POST /api/editor/fiximports`:** dummy que devuelve `{status: "ok"}`.
2. **Frontend ? Fiximports:** llamar, y si OK recargar el fichero desde `GET /api/files/content`, reemplazar la sesión sin marcar *dirty*.

Al terminar la Fase C, la toolbar está completa y funcional con endpoints dummy, lista para que el backend implemente la lógica real sin tocar el frontend.

---

## 7. Decisiones tomadas y su justificación

- **Diagnóstico y Fiximports trabajan sobre disco:** los servicios que los implementarán (LSP, formateador, organizador de imports) operan sobre ficheros, no sobre buffers en memoria. Por eso se deshabilitan cuando hay cambios pendientes ? el diagnóstico sería sobre un fichero desactualizado.
- **Formato trabaja sobre el buffer:** el formateador puede operar sobre cualquier texto, así que enviamos el contenido del editor y recibimos el formateado. No depende del disco.
- **Formato devuelve `text/plain`, no JSON:** el contenido crudo no requiere escaping. `Content-Type: text/plain; charset=UTF-8` en request y response.
- **`fiximports` recarga el fichero, `diagnostics` no:** el primero modifica el fichero en disco y el editor debe reflejarlo; el segundo solo lee y el buffer ya está sincronizado (porque el botón está deshabilitado si hay cambios).
- **Botones deshabilitados, no ocultos:** el usuario debe saber que la acción existe y que no aplica al fichero activo.
- **Dos servicios para Guardar y State:** aunque en la práctica se llamen seguidos, son responsabilidades distintas. Mantenerlos separados facilita el día que se quiera, por ejemplo, enviar state sin guardar, o guardar sin notificar al servidor.
- **`setValue(content, -1)` al reemplazar contenido programáticamente:** preserva el historial de undo. El usuario puede deshacer un formato que no le gusta.
- **Filtro por diferencia en el push de state:** evita ruido cuando el cursor se mueve dentro de la misma línea o cuando el *dirty* oscila sin diferencia efectiva.
- **Debounce trailing edge:** la clave es "enviar tras N ms sin actividad", no "enviar a los N ms de la primera tecla". Cada tecla reinicia el temporizador.
- **`sendBeacon` en `beforeunload`:** garantiza que el último estado llega al servidor si el usuario cierra la pestaña. No cancela el cierre si falla.

---

## 8. Pendientes y fuera de alcance

- **Informar de la selección sin llamar a guardar:** ya cubierto por el push de state, pero conviene confirmar que la selección se envía con la frecuencia adecuada. El push ya está diseñado con ese fin.
- **Apertura remota de ficheros y posicionamiento del cursor:** desde el servidor (agente) al cliente vía SSE (`editor_open` con `{path, line}`). Fuera de esta fase.
- **Integración cognitiva con el agente** (`EditorAwarenessOperation`): inyecta en la memoria proyectada del agente lo que el usuario tiene abierto y seleccionado. Fuera de esta fase.
- **Estilos del searchbox de Ace:** el diálogo de búsqueda que ya funciona tiene su propio CSS embebido, poco alineado con el tema de Noema. Adaptación cosmética pendiente.
- **Cierre de pestaña sucia:** aunque está en la Fase A, conviene no perderlo de vista. Es el único punto de fricción con el usuario que aparece antes que el resto.

## 9. Ficheros afectados

### 9.1. Backend

**`NoemaWebServer.java`**

- Registro de las cuatro rutas nuevas en `start()`:
  - `POST /api/editor/state`
  - `POST /api/editor/diagnostics`
  - `POST /api/editor/format`
  - `POST /api/editor/fiximports`
- Cuatro handlers, uno por endpoint. En esta fase, con implementaciones dummy:
  - `handlePostEditorState`: parsea el JSON y lo guarda en un `Map<String, JsonObject>` indexado por `terminalId`. Devuelve `200 {}`.
  - `handlePostEditorDiagnostics`: recibe `{file}`, devuelve `{annotations: [{row, column, text, type}, ...]}` con dos anotaciones de ejemplo. Documentar el formato en Javadoc, indicando que es el contrato con el frontend y que la lógica real adaptará la salida del LSP/MCP a este formato.
  - `handlePostEditorFormat`: lee el body como `text/plain; charset=UTF-8`, devuelve el mismo texto. Forzar `Content-Type: text/plain; charset=UTF-8` en la respuesta. La extensión llega en `?ext=java`, pero no se usa en esta fase.
  - `handlePostEditorFiximports`: recibe `{file}`, devuelve `{status: "ok"}`.
- Un campo nuevo en la clase para el mapa del estado (o un pequeño DTO si se prefiere tiparlo).

Nada más se toca del backend. `GET /api/files/content` y `POST /api/files/content` siguen tal cual.

### 9.2. Frontend

**`index.html`**

- Sin cambios. La estructura de la toolbar ya existe (`#workspace-editor-toolbar`). Los cuatro botones se crean desde JS, no en HTML.

**`styles.css`**

- Ampliar el bloque `.editor-toolbar-btn` con un estado `:disabled`: color atenuado, `cursor: not-allowed`, sin hover, sin `background`.
- Nada más. Reutilizamos la clase existente para los cuatro botones nuevos.

**`js/editor-ui.js`** ? fachada pública del editor

Se mantiene como **único punto de entrada** al editor. Quien necesite algo del editor importa de aquí; nunca de los módulos internos.

Responsabilidades tras el refactor:

- `initEditorUI`, `openFile`, `closeFile`, `getActivePath`, `resizeEditor` (API pública, sin cambios).
- Gestión de pestańas: `createTabElement`, `activateTab`, `closeFile`.
- **Dirty tracking** (nuevo): comparar contenido de la sesión con el contenido original, mantener el flag por sesión, actualizar el `●` de la pestańa.
- **Toolbar** (nuevo): crear los cuatro botones, `updateToolbarState()` que decide qué botón está habilitado según extensión y dirty.
- **Atajos de teclado** (nuevo): `Ctrl+S`, `Shift+Alt+F`, `Shift+Ctrl+I`, enganchados a los flujos correspondientes.
- **Diálogo de cierre de pestańa sucia** (nuevo).
- Orquestación de las acciones: llama a `editor-actions.js` y `editor-state.js`, pasa lo que cada uno necesite.

**`js/editor-actions.js`** ? nuevo, interno

Contiene los cuatro flujos de acción. **No importa nunca de `editor-ui.js`** (evitaría dependencia circular). Recibe de `editor-ui.js` lo que necesite (sesión, path del fichero, contenido) como parámetros.

- `saveActiveFile(...)`: `POST /api/files/content` + push de estado + limpiar dirty + toast.
- `runDiagnostics(...)`: `POST /api/editor/diagnostics` + `session.setAnnotations(...)`.
- `formatActiveFile(...)`: `POST /api/editor/format` con el buffer + `setValue(content, -1)` + marcar dirty.
- `fixImports(...)`: `POST /api/editor/fiximports` + recargar vía `GET /api/files/content` + `setValue(content, -1)` sin marcar dirty.

**`js/editor-state.js`** ? nuevo, interno

Toda la maquinaria del push de estado. **No importa nunca de `editor-ui.js`**. Recibe el snapshot ya construido desde `editor-ui.js` y se limita a decidir si hay que enviarlo, cuándo y cómo.

- Snapshots `lastSentState` y `currentState`.
- Filtro por diferencia: si son iguales, no enviar.
- Debounce trailing edge (~300-400 ms) para los disparadores de cursor y de contenido.
- Envío inmediato (`flush`) para los disparadores de cambio de pestańa y guardado.
- `sendBeacon` en `beforeunload` si hay un push pendiente.
- Fallos silenciosos: sin retry, sin toast.

**`js/api.js`**

- Cuatro funciones nuevas siguiendo el patrón existente:
  - `postEditorState(payload)`
  - `postEditorDiagnostics(filePath)`
  - `postEditorFormat(filePath, ext, content)` ? aquí con `Content-Type: text/plain`, no JSON.
  - `postEditorFiximports(filePath)`
- `saveFileContent` y `fetchFileContent` ya existen, se reutilizan tal cual.

**Alternativa considerada y descartada:** hacer las llamadas con `fetch` directo desde `editor-actions.js`, sin pasar por `api.js`. Estilo menos limpio, pero coherente con cómo `editor-ui.js` hace hoy el `GET /api/files/content` a pelo. Decisión: mantener la disciplina y añadirlas a `api.js`.

### 9.3. Ficheros que **no** se tocan

- `chat-ui.js`, `config-ui.js`, `workspace-ui.js`, `splitters.js`, `toast.js`, `main.js`.
- `conversation.xsl`.
- Todo `vendor/`.
- `index.html`.

### 9.4. Reglas de dependencia entre módulos del editor

- `editor-ui.js` **puede** importar de `editor-actions.js` y de `editor-state.js`.
- `editor-actions.js` y `editor-state.js` **no pueden** importar de `editor-ui.js`.
- Ninguno de los tres importa de `workspace-ui.js`, `chat-ui.js` ni `config-ui.js`.
- `workspace-ui.js` **solo** puede llamar a `openFile` de `editor-ui.js`, como ya hace.

El objetivo es que la fachada sea una frontera real: si algún día el editor se reescribe con otro motor, los dos módulos internos se tiran y se rehacen, y el resto de la app no se entera.
