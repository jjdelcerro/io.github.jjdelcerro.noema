
Buenos síntomas todos, y todos apuntan a la misma causa. La encontré.

## Causa raíz

En `buildStateSnapshot()` (dentro de `editor-ui.js`) estoy leyendo la posición del cursor así:

```js
const cursor = entry.session.getCursorPosition();
const selection = entry.session.getSelectionRange();
```

**`getCursorPosition` y `getSelectionRange` no existen en `EditSession`.** Son métodos del `Editor`, no de la sesión. En Ace, `entry.session` es solo el modelo (texto, modo, marcadores); el cursor y la selección viven en `editor`.

Resultado: **cada vez que se llama a `scheduleStatePush()` lanza un `TypeError`**. Y `scheduleStatePush()` se llama desde:

- El listener `editor.on('change')` ? **en cada tecla que pulsas**.
- El listener `editor.on('changeSelection')` ? **en cada movimiento de cursor o selección**.
- `activateTab`, `doSaveFile`, `handleFixImports`, etc.

Cuando ese `TypeError` se lanza **dentro del listener de Ace**, interrumpe el pipeline interno de Ace a mitad de la actualización. De ahí todos los síntomas raros:

- **No se ve lo que escribes**: el `change` handler revienta antes de que Ace termine de repintar.
- **Shift+flecha no selecciona**: el `changeSelection` handler revienta a mitad.
- **PgUp/PgDn mueve el cursor pero no refresca**: mismo, el handler explota.
- **Ctrl+F siempre "no encontrado"**: el estado interno de Ace está corrupto tras decenas de excepciones.
- **Las ❌ en el árbol**: el `openFile` que devolvemos a `waitUntil` rechaza (porque `activateTab` ? `scheduleStatePush` ? `buildStateSnapshot` revienta) y el componente file-tree pinta un `❌` en el `<li>` marcándolo con la clase `error`.
- **Fiximports falla**: mismo camino. La llamada al endpoint va bien, pero al llegar a `scheduleStatePush` revienta y el catch lo reporta como error.

Un solo bug produce todos los demás.

## Fix

En `editor-ui.js`, sustituye el cuerpo de `buildStateSnapshot` por:

```js
function buildStateSnapshot() {
    const openFiles = [];
    for (const [path, entry] of sessions) {
        openFiles.push({ path, dirty: entry.dirty });
    }

    let cursorLine = null;
    let selectionStartLine = null;
    let selectionEndLine = null;

    if (activePath && editor) {
        const cursor = editor.getCursorPosition();
        const selection = editor.getSelectionRange();
        cursorLine = cursor.row;
        selectionStartLine = selection.start.row;
        selectionEndLine = selection.end.row;
    }

    return {
        terminalId: localStorage.getItem(TERMINAL_STORAGE_KEY) || 'default',
        openFiles,
        activeFile: activePath,
        cursorLine,
        selectionStartLine,
        selectionEndLine
    };
}
```

Dos cambios concretos:

- `entry.session.getCursorPosition()` ? `editor.getCursorPosition()`
- `entry.session.getSelectionRange()` ? `editor.getSelectionRange()`

Y he quitado la comprobación `if (entry)` porque el cursor y la selección viven en el editor, no en la sesión: lo único que importa es que haya `activePath` y que el editor exista.

## Un detalle defensivo que ańadiría

En el mismo fichero, los listeners del editor:

```js
editor.on('change', () => {
    if (suspendDirtyTracking || !activePath) {
        return;
    }
    ...
});
```

Deberían envolverse en try/catch. No para ocultar errores, sino para que un fallo puntual en nuestra lógica **nunca** rompa el pipeline de Ace:

```js
editor.on('change', () => {
    try {
        if (suspendDirtyTracking || !activePath) {
            return;
        }
        const entry = sessions.get(activePath);
        if (!entry) {
            return;
        }
        const current = entry.session.getValue();
        const dirty = current !== entry.originalContent;
        if (entry.dirty !== dirty) {
            entry.dirty = dirty;
            updateTabDirty(activePath, dirty);
            updateToolbarState();
        }
        scheduleStatePush();
    } catch (error) {
        console.error('Error en listener change:', error);
    }
});

editor.on('changeSelection', () => {
    try {
        scheduleStatePush();
    } catch (error) {
        console.error('Error en listener changeSelection:', error);
    }
});
```

Es la lección de hoy: **los listeners que se enganchan a eventos de Ace no deben lanzar nunca**. Ace no los protege.

## Botón de recargar

Ańádelo a la toolbar como un botón más. En `createToolbar()`:

```js
toolbarEl.appendChild(makeToolbarButton('btn-editor-reload', SVG_RELOAD, 'Recargar desde disco', handleReload));
```

Y el icono (junto a los otros):

```js
const SVG_RELOAD = '<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><polyline points="23 4 23 10 17 10"/><path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/></svg>';
```

Y el handler:

```js
async function handleReload() {
    const path = activePath;
    if (!path) {
        return;
    }
    const entry = sessions.get(path);
    if (!entry) {
        return;
    }
    if (entry.dirty) {
        const result = await confirmCloseDirty(path);
        if (result === 'cancel') {
            return;
        }
        // 'save' o 'discard': en ambos casos vamos a sobrescribir el buffer.
    }
    try {
        const url = `/api/files/content?file=${encodeURIComponent(path)}`;
        const response = await fetch(url);
        if (!response.ok) {
            throw new Error(`HTTP ${response.status}`);
        }
        const data = await response.json();
        const newContent = data.content ?? '';

        suspendDirtyTracking = true;
        try {
            entry.session.setValue(newContent, -1);
            entry.originalContent = newContent;
            entry.dirty = false;
        } finally {
            suspendDirtyTracking = false;
        }

        updateTabDirty(path, false);
        updateToolbarState();
        scheduleStatePush({ immediate: true });
        showToast('Recargado desde disco', 'success', 2000);
    } catch (error) {
        console.error('Error recargando:', error);
        showToast(`No se pudo recargar: ${error.message}`, 'error');
    }
}
```

El comportamiento: si el fichero está sucio, pregunta (mismo diálogo que al cerrar). Si el usuario dice "Guardar", guarda primero y luego recarga; si dice "Descartar", descarta el buffer y trae lo de disco. En ambos casos el estado final es coherente.

## Verificar después del fix

1. **Ctrl+F** debe encontrar texto.
2. **Escribir** debe verse reflejado en el sitio correcto.
3. **Shift+flechas** debe seleccionar.
4. **PgUp/PgDn** debe refrescar.
5. **Las ❌ en el árbol** deben desaparecer.
6. **Fiximports** debe funcionar (si el error era el mismo, que apostaría que sí).
7. **Recargar** debe funcionar.

Si tras esto Fiximports sigue fallando, necesito el mensaje de error concreto (el toast o la consola). Con la captura que decías que adjuntabas no me llegó nada, así que si sigue el problema, pásame el texto del error.

Y una lección para mí: **cualquier listener enganchado a Ace tiene que ir envuelto en try/catch**. No vuelvo a meter otro sin protección.
