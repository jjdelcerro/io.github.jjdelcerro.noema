/**
 * Editor de notas: titulo, tags y cuerpo en Markdown sobre Ace.
 *
 * Modelo de campos:
 *   - Editables por el usuario: title, tags y content (Markdown).
 *   - Gestionados por el editor en su conjunto: id, created y updated.
 *
 * Este modulo no toca XML: envia y recibe JSON contra la API. La
 * serializacion (incluido el partido del CDATA) es responsabilidad de
 * NoteStore en el servidor.
 */

import { showToast } from './toast.js';
import { registerAreaView } from './area-tabs.js';
import { confirmDiscardDialog } from './discard-dialog.js';

const NOTES_ENDPOINT = '/api/notes';

let aceEditor = null;
let current = null;
let originalSignature = '';
let applying = false;
let initialized = false;
// Ajuste de linea del panel de notas. Es un unico valor para todo el panel:
// solo hay un editor, y se mantiene al cambiar de nota. No se persiste.
let wrapEnabled = true;

/**
 * Inicializa el editor de notas. Idempotente. No crea la instancia de Ace:
 * eso se hace de forma perezosa en el primer openNote(), para no pagar el
 * coste si el usuario no abre nunca la pestaña de notas.
 */
export function initNoteEditor() {
    if (initialized) {
        return;
    }
    initialized = true;

    const titleInput = document.getElementById('note-title');
    const tagsInput = document.getElementById('note-tags');
    const saveButton = document.getElementById('btn-save-note');

    if (titleInput) {
        titleInput.addEventListener('input', refreshDirtyState);
    }
    if (tagsInput) {
        tagsInput.addEventListener('input', refreshDirtyState);
    }
    if (saveButton) {
        saveButton.addEventListener('click', saveNote);
    }
    const reloadButton = document.getElementById('btn-reload-note');
    if (reloadButton) {
        reloadButton.addEventListener('click', reloadNote);
    }
    const wrapButton = document.getElementById('btn-toggle-note-wrap');
    if (wrapButton) {
        wrapButton.addEventListener('click', toggleNoteWrap);
    }
    const closeButton = document.getElementById('btn-close-note');
    if (closeButton) {
        closeButton.addEventListener('click', closeNote);
    }
    updateNoteWrapButton();

    document.addEventListener('keydown', (event) => {
        if (!(event.ctrlKey || event.metaKey) || event.key.toLowerCase() !== 's') {
            return;
        }
        const view = document.querySelector('[data-area-view="notes"]');
        if (!current || !view || view.hidden) {
            return;
        }
        event.preventDefault();
        saveNote();
    });

    registerAreaView('notes', {
        isDirty: isDirty,
        confirmLeave: confirmLeaveNotes,
        onActivate: () => {
            if (aceEditor) {
                aceEditor.resize();
            }
        }
    });
}

/**
 * Carga una nota desde el servidor y la pinta en el formulario.
 *
 * Si la referencia trae ya la nota completa (por ejemplo desde el endpoint
 * de la nota mas reciente), se evita la peticion adicional.
 *
 * @param {{id: string, title?: string, highlight?: string, note?: Object}} reference
 *        highlight, si viene, es el texto a resaltar en el cuerpo al abrir
 *        note, si viene, es la nota ya cargada
 */
export async function openNote(reference) {
    initNoteEditor();
    if (!reference || !reference.id) {
        return false;
    }
    if (!(await guardDiscard(reference.id))) {
        return false;
    }
    try {
        let note = reference.note || null;
        if (!note) {
            const response = await fetch(`${NOTES_ENDPOINT}/${encodeURIComponent(reference.id)}`);
            if (!response.ok) {
                throw new Error(`HTTP ${response.status}`);
            }
            note = await response.json();
        }
        applyNote(note, reference.highlight);
        return true;
    } catch (error) {
        console.error('Error cargando la nota:', reference.id, error);
        showToast('No se pudo cargar la nota', 'error');
        return false;
    }
}

/**
 * Indica si hay cambios sin guardar respecto a la ultima version conocida.
 *
 * @returns {boolean}
 */
export function isDirty() {
    if (!current) {
        return false;
    }
    return signature() !== originalSignature;
}

function confirmLeaveNotes() {
    if (!isDirty()) {
        return true;
    }
    return window.confirm(
        'La nota tiene cambios sin guardar. ' +
        'Si cambias de pestaña no se perderán, pero seguirán sin guardarse. ¿Continuar?'
    );
}

/**
 * Vuelca una nota en el formulario y fija la firma de referencia.
 *
 * @param {Object} note nota devuelta por GET /api/notes/{id}
 * @param {string} [highlight] texto a resaltar en el cuerpo, si viene
 */
function applyNote(note, highlight) {
    // Unica puerta de entrada de una nota al formulario: por aqui pasan el
    // arbol, la busqueda, el alta, el guardado y la recarga. Es el sitio
    // donde se despliega el panel y donde Ace aprende su tamanio.
    setNotePanelVisible(true);
    current = note;
    ensureEditor();
    // Ace mide el contenedor al crearse y al redimensionar. Si el panel
    // venia oculto, sin esto el buffer se quedaria con 0x0 de alto.
    aceEditor.resize();

    applying = true;
    try {
        aceEditor.session.setValue(note.content || '', -1);
        aceEditor.clearSelection();
        const titleInput = document.getElementById('note-title');
        const tagsInput = document.getElementById('note-tags');
        if (titleInput) {
            titleInput.value = note.title || '';
        }
        if (tagsInput) {
            tagsInput.value = (note.tags || []).join(', ');
        }
        originalSignature = signature();
    } finally {
        applying = false;
    }

    updateStatus();
    refreshDirtyState();
    revealHighlight(highlight);
}

/**
 * Selecciona y lleva a la vista la primera aparicion de un texto en el
 * cuerpo de la nota. Se usa al abrir un resultado de busqueda en modo
 * exact, donde el cliente no recibe la linea de coincidencia.
 *
 * @param {string} [text] texto buscado
 */
function revealHighlight(text) {
    const needle = String(text || '').trim();
    if (!needle || !aceEditor) {
        return;
    }
    const range = aceEditor.find(needle, {
        wrap: false,
        caseSensitive: false,
        wholeWord: false,
        regExp: false
    });
    if (!range) {
        return;
    }
    aceEditor.selection.setRange(range);
    aceEditor.scrollToLine(range.start.row, true, true, function () {});
    aceEditor.focus();
}

/**
 * Guarda la nota abierta con PUT. Tras el guardado el servidor puede haber
 * movido el fichero al mes corriente, asi que se recalcula la ruta local a
 * partir del campo updated de la respuesta.
 *
 * @returns {Promise<boolean>} true si la nota quedo guardada
 */
async function saveNote() {
    if (!current || !isDirty()) {
        return false;
    }
    const saveButton = document.getElementById('btn-save-note');
    if (saveButton) {
        saveButton.disabled = true;
    }
    try {
        const payload = {
            title: readTitle(),
            tags: readTags(),
            content: aceEditor ? aceEditor.getValue() : ''
        };
        const response = await fetch(`${NOTES_ENDPOINT}/${encodeURIComponent(current.id)}`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });
        if (!response.ok) {
            throw new Error(`HTTP ${response.status}`);
        }
        const updated = await response.json();
        applyNote(Object.assign({}, current, updated));
        showToast('Nota guardada', 'success');
        window.dispatchEvent(new CustomEvent('noema:notes-changed'));
        return true;
    } catch (error) {
        console.error('Error guardando la nota:', current.id, error);
        showToast('No se pudo guardar la nota', 'error');
        return false;
    } finally {
        refreshDirtyState();
    }
}

/**
 * Guarda la decision del usuario cuando hay cambios sin guardar.
 *
 * Se pregunta antes de cualquier operacion que vaya a machacar el buffer:
 * abrir otra nota, recargar la actual o crear una nueva. La respuesta se
 * traduce en tres resultados reales, cosa que window.confirm no permite.
 *
 * @param {string|null} nextId id de la nota que se va a abrir; si coincide con
 *        la abierta se considera continuacion y no se pregunta
 * @returns {Promise<boolean>} false si el usuario cancela la operacion
 */
async function guardDiscard(nextId) {
    if (!current || !isDirty()) {
        return true;
    }
    if (nextId && nextId === current.id) {
        return true;
    }
    const title = readTitle() || current.id;
    const decision = await confirmDiscardDialog({
        title: 'Cambios sin guardar',
        message: `La nota "${title}" tiene cambios sin guardar. ¿Qué quieres hacer?`
    });
    if (decision === 'cancel') {
        return false;
    }
    if (decision === 'save') {
        return await saveNote();
    }
    return true;
}

/**
 * Pregunta si hay cambios sin guardar. Pensado para acciones externas al
 * editor que van a crear o abrir otra nota (por ejemplo, el boton de alta).
 *
 * @returns {Promise<boolean>} false si el usuario cancela la operacion
 */
export async function confirmDiscardIfDirty() {
    return await guardDiscard(null);
}

/**
 * Vuelve a leer la nota abierta desde el servidor, descartando el buffer.
 * Pregunta antes si hay cambios sin guardar.
 *
 * @returns {Promise<boolean>} true si la nota se recargo
 */
async function reloadNote() {
    if (!current) {
        return false;
    }
    if (!(await guardDiscard(null))) {
        return false;
    }
    const id = current.id;
    try {
        const response = await fetch(`${NOTES_ENDPOINT}/${encodeURIComponent(id)}`);
        if (!response.ok) {
            throw new Error(`HTTP ${response.status}`);
        }
        const note = await response.json();
        applyNote(note);
        showToast('Nota recargada', 'success', 1800);
        return true;
    } catch (error) {
        console.error('Error recargando la nota:', id, error);
        showToast('No se pudo recargar la nota', 'error');
        return false;
    }
}

/**
 * Despliega o repliega el contenido del panel derecho de notas.
 *
 * El panel exterior (.notes-main-pane) no se oculta nunca: se queda como
 * area vacia para que el splitter siga teniendo dos zonas con tamanio y la
 * columna del arbol no cambie de ancho al abrir o cerrar una nota.
 *
 * @param {boolean} visible
 */
function setNotePanelVisible(visible) {
    const pane = document.querySelector('.notes-main-pane');
    if (!pane) {
        return;
    }
    for (const child of Array.from(pane.children)) {
        child.hidden = !visible;
    }
}

/**
 * Cierra la nota abierta y deja el panel derecho vacio.
 *
 * Si hay cambios sin guardar se pregunta con el dialogo de tres opciones,
 * igual que al abrir otra nota o al recargar. La instancia de Ace se
 * conserva: al reabrir basta con volcar la nota en el buffer, sin pagar una
 * reinstanciacion ni perder el ajuste de linea.
 *
 * @returns {Promise<boolean>} true si la nota quedo cerrada
 */
async function closeNote() {
    if (!current) {
        return false;
    }
    if (isDirty()) {
        const title = readTitle() || current.id;
        const decision = await confirmDiscardDialog({
            title: 'Cerrar nota',
            message: `La nota "${title}" tiene cambios sin guardar. ¿Qué quieres hacer?`
        });
        if (decision === 'cancel') {
            return false;
        }
        if (decision === 'save' && !(await saveNote())) {
            return false;
        }
    }
    current = null;
    originalSignature = '';
    applying = true;
    try {
        if (aceEditor) {
            aceEditor.session.setValue('', -1);
            aceEditor.clearSelection();
        }
        const titleInput = document.getElementById('note-title');
        const tagsInput = document.getElementById('note-tags');
        if (titleInput) {
            titleInput.value = '';
        }
        if (tagsInput) {
            tagsInput.value = '';
        }
    } finally {
        applying = false;
    }
    updateStatus();
    refreshDirtyState();
    setNotePanelVisible(false);
    return true;
}

/**
 * Invierte el ajuste de linea del panel de notas. El valor sobrevive al
 * cambio de nota: applyNote no lo toca.
 */
function toggleNoteWrap() {
    wrapEnabled = !wrapEnabled;
    if (aceEditor) {
        aceEditor.session.setUseWrapMode(wrapEnabled);
    }
    updateNoteWrapButton();
}

/**
 * Refleja el estado del ajuste de linea en el boton: icono encendido y
 * atributos accesibles.
 */
function updateNoteWrapButton() {
    const button = document.getElementById('btn-toggle-note-wrap');
    if (!button) {
        return;
    }
    const label = wrapEnabled ? 'Desactivar ajuste de línea' : 'Activar ajuste de línea';
    button.title = label;
    button.setAttribute('aria-label', label);
    button.setAttribute('aria-pressed', String(wrapEnabled));
    button.classList.toggle('active', wrapEnabled);
    const image = button.querySelector('img');
    if (image) {
        image.src = `images/current/${wrapEnabled ? 'wrap-active' : 'wrap'}.svg`;
    }
}

/**
 * Crea la instancia de Ace la primera vez que hace falta.
 */
function ensureEditor() {
    if (aceEditor) {
        return;
    }
    if (!window.ace) {
        throw new Error('Ace no esta disponible');
    }
    aceEditor = window.ace.edit('notes-editor');
    aceEditor.setTheme('ace/theme/dracula');
    aceEditor.session.setMode('ace/mode/markdown');
    aceEditor.session.setUseWrapMode(wrapEnabled);
    aceEditor.session.setTabSize(2);
    aceEditor.session.setUseSoftTabs(true);
    aceEditor.setOptions({
        showPrintMargin: false,
        fontSize: '13px',
        highlightActiveLine: true
    });
    aceEditor.session.on('change', refreshDirtyState);
}

function refreshDirtyState() {
    if (applying) {
        return;
    }
    const saveButton = document.getElementById('btn-save-note');
    const status = document.getElementById('note-status');
    const dirty = isDirty();
    if (saveButton) {
        saveButton.disabled = !dirty;
    }
    if (status) {
        status.classList.toggle('dirty', dirty);
    }
}

/**
 * Pinta los metadatos gestionados: id, ruta derivada del mes de updated y
 * marca temporal. La ruta se deriva en el cliente porque el servidor mueve
 * el fichero al mes corriente en cada guardado.
 */
function updateStatus() {
    const status = document.getElementById('note-status');
    if (!status) {
        return;
    }
    if (!current) {
        status.textContent = '';
        return;
    }
    const stamp = String(current.updated || current.created || '')
        .replace('T', ' ')
        .replace('Z', '');
    status.textContent = `${current.id} · ${relativePathOf(current)} · ${stamp}`;
}

function relativePathOf(note) {
    const iso = note.updated || note.created || '';
    const match = /^(\d{4})-(\d{2})/.exec(iso);
    if (!match) {
        return '';
    }
    return `${match[1]}/${match[2]}/${note.id}.xml`;
}

function readTitle() {
    const input = document.getElementById('note-title');
    return input ? input.value.trim() : '';
}

function readTags() {
    const input = document.getElementById('note-tags');
    if (!input) {
        return [];
    }
    return input.value
        .split(',')
        .map((tag) => tag.trim())
        .filter((tag) => tag.length > 0);
}

function signature() {
    return JSON.stringify({
        title: readTitle(),
        tags: readTags(),
        content: aceEditor ? aceEditor.getValue() : ''
    });
}
