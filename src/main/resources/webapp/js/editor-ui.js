/**
 * Módulo de gestión del editor Ace: fachada pública del editor.
 *
 * Toda la app que necesite algo del editor importa de aquí. Los módulos
 * internos (editor-actions.js, editor-state.js) no se importan desde
 * fuera de esta fachada.
 *
 * Un único `ace.edit()` en el DOM, N `EditSession` en memoria, una por
 * fichero abierto. Cada sesión conserva su historial de undo, cursor y
 * scroll. La fachada mantiene el estado de dirty y lo publica.
 */

import { showToast } from './toast.js';
import {
    saveActiveFile,
    runDiagnostics,
    formatActiveFile,
    fixImports
} from './editor-actions.js';
import { pushState, initStateBeforeUnload } from './editor-state.js';
import { postEditorSetupProject } from './api.js';

const WRAP_STORAGE_KEY = 'noema_editor_wrap';
const TERMINAL_STORAGE_KEY = 'noema_terminal_id';

const MODE_BY_EXTENSION = {
    java: 'ace/mode/java',
    xml: 'ace/mode/xml',
    json: 'ace/mode/json',
    md: 'ace/mode/markdown',
    markdown: 'ace/mode/markdown',
    sh: 'ace/mode/sh',
    bash: 'ace/mode/sh',
    groovy: 'ace/mode/groovy',
    properties: 'ace/mode/properties',
    yaml: 'ace/mode/yaml',
    yml: 'ace/mode/yaml',
    html: 'ace/mode/html',
    htm: 'ace/mode/html',
    css: 'ace/mode/css',
    js: 'ace/mode/javascript',
    mjs: 'ace/mode/javascript'
};

// Mapa de extensiones soportadas por acción. '.*' aplica a cualquier
// fichero. Admite patrones literales y expresiones regulares.
const ACTION_EXTENSIONS = {
    save:        ['.*'],
    diagnostics: ['java'],
    format:      ['java'],
    fiximports:  ['java']
};

// Iconos (SVG inline, mismo patrón que el resto de la UI).
const SVG_WRAP = '<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><line x1="3" y1="6" x2="21" y2="6"/><path d="M3 12h15a3 3 0 1 1 0 6h-3"/><polyline points="18 15 15 18 18 21"/><line x1="3" y1="18" x2="9" y2="18"/></svg>';
const SVG_SAVE = '<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2z"/><polyline points="17 21 17 13 7 13 7 21"/><polyline points="7 3 7 8 15 8"/></svg>';
const SVG_DIAGNOSTICS = '<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>';
const SVG_FORMAT = '<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><line x1="3" y1="6" x2="21" y2="6"/><line x1="7" y1="12" x2="21" y2="12"/><line x1="3" y1="18" x2="21" y2="18"/></svg>';
const SVG_FIXIMPORTS = '<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><polyline points="16 3 21 3 21 8"/><line x1="4" y1="20" x2="21" y2="3"/><polyline points="21 16 21 21 16 21"/><line x1="15" y1="15" x2="21" y2="21"/><line x1="4" y1="4" x2="9" y2="9"/></svg>';
const SVG_RELOAD = '<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><polyline points="23 4 23 10 17 10"/><path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/></svg>';
const SVG_SETUP = '<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z"/></svg>';

let editor = null;
let tabsEl = null;
let toolbarEl = null;
let editorContainerEl = null;
let resizeObserver = null;
// Map<path, {session, originalContent, dirty}>
const sessions = new Map();
const tabElements = new Map();
let activePath = null;
let wrapEnabled = false;
let suspendDirtyTracking = false;
let setupInProgress = false;

/**
 * Inicializa Ace sobre el contenedor del panel del editor. Idempotente y
 * silencioso si los elementos del DOM no existen.
 */
export function initEditorUI() {
    tabsEl = document.getElementById('workspace-editor-tabs');
    toolbarEl = document.getElementById('workspace-editor-toolbar');
    editorContainerEl = document.getElementById('workspace-editor');
    if (!tabsEl || !editorContainerEl) {
        return;
    }
    if (typeof window.ace === 'undefined') {
        console.error('Ace no se ha cargado (comprueba los <script> en index.html)');
        showToast('El editor no está disponible', 'error');
        return;
    }

    // Sin workers: no los tenemos en vendor y no son necesarios.
    window.ace.config.set('useWorker', false);

    wrapEnabled = localStorage.getItem(WRAP_STORAGE_KEY) === 'true';

    editor = window.ace.edit(editorContainerEl, {
        theme: 'ace/theme/dracula',
        mode: 'ace/mode/text',
        fontSize: '12px',
        showPrintMargin: false,
        useSoftTabs: true,
        tabSize: 4,
        wrap: false,
        displayIndentGuides: true
    });

    const emptySession = window.ace.createEditSession('', 'ace/mode/text');
    emptySession.setUseWrapMode(wrapEnabled);
    editor.setSession(emptySession);

    createToolbar();
    registerKeyboardShortcuts();
    registerEditorListeners();
    initStateBeforeUnload();

    if (typeof ResizeObserver !== 'undefined' && !resizeObserver) {
        resizeObserver = new ResizeObserver(() => {
            if (editor) {
                editor.resize();
            }
        });
        resizeObserver.observe(editorContainerEl);
    }

    updateToolbarState();
}

/**
 * Abre un fichero en el editor: lo activa si ya estaba abierto, o lo carga
 * desde el endpoint y crea pestaña + sesión nuevas.
 */
export async function openFile(filePath) {
    if (!editor || !filePath) {
        return;
    }

    if (sessions.has(filePath)) {
        activateTab(filePath);
        return;
    }

    let content = '';
    try {
        const url = `/api/files/content?file=${encodeURIComponent(filePath)}`;
        const response = await fetch(url);
        if (!response.ok) {
            let message = `HTTP ${response.status}`;
            try {
                const data = await response.json();
                if (data.error) {
                    message = data.error;
                }
            } catch (parseError) {
                // respuesta sin JSON; conservamos el mensaje genérico
            }
            throw new Error(message);
        }
        const data = await response.json();
        content = data.content ?? '';
    } catch (error) {
        console.error('Error cargando archivo:', filePath, error);
        showToast(`No se pudo abrir: ${error.message}`, 'error');
        return;
    }

    const mode = modeForPath(filePath);
    const session = window.ace.createEditSession(content, mode);
    session.setUseWrapMode(wrapEnabled);
    sessions.set(filePath, { session, originalContent: content, dirty: false, diagAnnotations: [], agentAnnotations: [] });

    createTabElement(filePath);
    activateTab(filePath);

    requestAnimationFrame(() => {
        if (editor) {
            editor.resize();
        }
    });
}

/**
 * Cierra una pestaña. Si tiene cambios sin guardar, muestra un diálogo
 * de confirmación antes de proceder.
 */
export async function closeFile(filePath) {
    const entry = sessions.get(filePath);
    if (!entry) {
        return;
    }

    if (entry.dirty) {
        const result = await confirmCloseDirty(filePath);
        if (result === 'cancel') {
            return;
        }
        if (result === 'save') {
            try {
                await doSaveFile(filePath);
                showToast('Fichero guardado', 'success', 2200);
            } catch (error) {
                console.error('Error guardando antes de cerrar:', error);
                showToast(`No se pudo guardar: ${error.message}`, 'error');
                return;
            }
        }
        // 'discard': continuar con el cierre
    }

    const tabEl = tabElements.get(filePath);
    if (tabEl) {
        tabEl.remove();
    }
    tabElements.delete(filePath);
    sessions.delete(filePath);

    if (activePath !== filePath) {
        scheduleStatePush();
        return;
    }

    const remaining = Array.from(sessions.keys());
    if (remaining.length === 0) {
        activePath = null;
        const emptySession = window.ace.createEditSession('', 'ace/mode/text');
        emptySession.setUseWrapMode(wrapEnabled);
        editor.setSession(emptySession);
        updateToolbarState();
        scheduleStatePush({ immediate: true });
        return;
    }
    activateTab(remaining[remaining.length - 1]);
}

/**
 * Devuelve la ruta del fichero activo, o null si no hay ninguno.
 */
export function getActivePath() {
    return activePath;
}

/**
 * Fuerza a Ace a recalcular su disposición. Pensado para llamarse cuando el
 * contenedor cambia de tamaño desde fuera.
 */
export function resizeEditor() {
    if (editor) {
        editor.resize();
    }
}

// --- Toolbar ---

function createToolbar() {
    if (!toolbarEl) {
        return;
    }
    toolbarEl.replaceChildren();

    toolbarEl.appendChild(makeToolbarButton('btn-editor-wrap', SVG_WRAP, 'Ajuste de línea', toggleWrap));
    toolbarEl.appendChild(makeToolbarButton('btn-editor-save', SVG_SAVE, 'Guardar', handleSave));
    toolbarEl.appendChild(makeToolbarButton('btn-editor-format', SVG_FORMAT, 'Formatear', handleFormat));
    toolbarEl.appendChild(makeToolbarButton('btn-editor-diagnostics', SVG_DIAGNOSTICS, 'Diagnóstico', handleDiagnostics));
    toolbarEl.appendChild(makeToolbarButton('btn-editor-fiximports', SVG_FIXIMPORTS, 'Ajustar imports', handleFixImports));
    toolbarEl.appendChild(makeToolbarButton('btn-editor-setup', SVG_SETUP, 'Preparar proyecto (LSP)', handleSetupProject));
    toolbarEl.appendChild(makeToolbarButton('btn-editor-reload', SVG_RELOAD, 'Recargar desde disco', handleReload));
    
    updateWrapButtonState();
    updateToolbarState();
}

function makeToolbarButton(id, svg, label, handler) {
    const btn = document.createElement('button');
    btn.type = 'button';
    btn.id = id;
    btn.className = 'editor-toolbar-btn';
    btn.innerHTML = svg;
    btn.title = label;
    btn.setAttribute('aria-label', label);
    btn.addEventListener('click', handler);
    return btn;
}

function updateToolbarState() {
    const path = activePath;
    const entry = path ? sessions.get(path) : null;
    const ext = path ? extensionOf(path) : '';
    const dirty = entry ? entry.dirty : false;

    setBtnEnabled('btn-editor-save',
        !!entry && dirty && matchesExtensions(ext, ACTION_EXTENSIONS.save));

    setBtnEnabled('btn-editor-diagnostics',
        !!entry && !dirty && matchesExtensions(ext, ACTION_EXTENSIONS.diagnostics));

    setBtnEnabled('btn-editor-format',
        !!entry && matchesExtensions(ext, ACTION_EXTENSIONS.format));

    setBtnEnabled('btn-editor-fiximports',
        !!entry && !dirty && matchesExtensions(ext, ACTION_EXTENSIONS.fiximports));
        
    setBtnEnabled('btn-editor-setup', !!entry && !setupInProgress);        
}

function setBtnEnabled(id, enabled) {
    const btn = document.getElementById(id);
    if (btn) {
        btn.disabled = !enabled;
    }
}

function matchesExtensions(ext, patterns) {
    if (!ext) {
        return false;
    }
    for (const pattern of patterns) {
        if (pattern === '.*') {
            return true;
        }
        if (pattern instanceof RegExp) {
            if (pattern.test(ext)) {
                return true;
            }
        } else if (pattern === ext) {
            return true;
        }
    }
    return false;
}

function updateWrapButtonState() {
    const btn = document.getElementById('btn-editor-wrap');
    if (!btn) {
        return;
    }
    btn.classList.toggle('active', wrapEnabled);
    const label = wrapEnabled ? 'Desactivar ajuste de línea' : 'Activar ajuste de línea';
    btn.title = label;
    btn.setAttribute('aria-label', label);
    btn.setAttribute('aria-pressed', String(wrapEnabled));
}

function toggleWrap() {
    wrapEnabled = !wrapEnabled;
    localStorage.setItem(WRAP_STORAGE_KEY, String(wrapEnabled));
    sessions.forEach((entry) => entry.session.setUseWrapMode(wrapEnabled));
    updateWrapButtonState();
}

// --- Acciones ---

async function handleSave() {
    const path = activePath;
    if (!path) {
        return;
    }
    const entry = sessions.get(path);
    if (!entry || !entry.dirty) {
        return;
    }
    try {
        await doSaveFile(path);
        showToast('Fichero guardado', 'success', 2200);
    } catch (error) {
        console.error('Error guardando:', error);
        showToast(`No se pudo guardar: ${error.message}`, 'error');
    }
}

async function doSaveFile(path) {
    const entry = sessions.get(path);
    if (!entry) {
        return;
    }
    const content = entry.session.getValue();
    await saveActiveFile({ filePath: path, content });

    entry.originalContent = content;
    entry.dirty = false;
    updateTabDirty(path, false);
    updateToolbarState();
    scheduleStatePush({ immediate: true });
}

/**
 * Compone y aplica las anotaciones visibles de una sesión. Es el ÚNICO punto
 * que llama a `setAnnotations`: mezcla las de diagnóstico (las escribe el
 * cliente) con las que pinta el agente, para que ninguna sobrescriba a la
 * otra. Si no se centraliza aquí, el diagnóstico borraría las marcas del
 * agente y viceversa.
 */
function applyAnnotations(entry) {
    if (!entry || !entry.session) {
        return;
    }
    const diag = entry.diagAnnotations || [];
    const agent = entry.agentAnnotations || [];
    entry.session.setAnnotations(diag.concat(agent));
}

async function handleDiagnostics() {
    const path = activePath;
    if (!path) {
        return;
    }
    const entry = sessions.get(path);
    if (!entry || entry.dirty) {
        return;
    }
    try {
        const result = await runDiagnostics({ filePath: path });
        const annotations = (result && result.annotations) || [];
        entry.diagAnnotations = annotations;
        applyAnnotations(entry);
        const msg = annotations.length === 0
            ? 'Sin diagnósticos'
            : `${annotations.length} anotación${annotations.length === 1 ? '' : 'es'}`;
        showToast(`Diagnóstico: ${msg}`, 'info', 2200);
    } catch (error) {
        console.error('Error en diagnóstico:', error);
        showToast(`Error en diagnóstico: ${error.message}`, 'error');
    }
}

async function handleFormat() {
    const path = activePath;
    if (!path) {
        return;
    }
    const entry = sessions.get(path);
    if (!entry) {
        return;
    }
    try {
        const content = entry.session.getValue();
        const ext = extensionOf(path);
        const formatted = await formatActiveFile({ filePath: path, ext, content });
        if (formatted !== content) {
            // setValue con -1 mantiene el cursor. El cambio dispara el
            // listener global y marca dirty automáticamente.
            entry.session.setValue(formatted, -1);
        }
        showToast('Formateado', 'success', 2000);
    } catch (error) {
        console.error('Error formateando:', error);
        showToast(`Error al formatear: ${error.message}`, 'error');
    }
}

async function handleFixImports() {
    const path = activePath;
    if (!path) {
        return;
    }
    const entry = sessions.get(path);
    if (!entry || entry.dirty) {
        return;
    }
    try {
        const newContent = await fixImports({ filePath: path });

        // Suspender el dirty tracking mientras aplicamos el contenido
        // recibido: el buffer y el disco quedan sincronizados, no hay
        // nada que guardar.
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
        showToast('Imports ajustados', 'success', 2200);
    } catch (error) {
        console.error('Error ajustando imports:', error);
        showToast(`Error ajustando imports: ${error.message}`, 'error');
    }
}

// --- Atajos de teclado ---

function registerKeyboardShortcuts() {
    editor.commands.addCommand({
        name: 'noema-save',
        bindKey: { win: 'Ctrl-S', mac: 'Command-S' },
        exec: () => { handleSave(); },
        readOnly: true
    });

    editor.commands.addCommand({
        name: 'noema-format',
        bindKey: { win: 'Shift-Alt-F', mac: 'Shift-Alt-F' },
        exec: () => {
            const btn = document.getElementById('btn-editor-format');
            if (btn && !btn.disabled) {
                handleFormat();
            }
        }
    });

    editor.commands.addCommand({
        name: 'noema-fiximports',
        bindKey: { win: 'Shift-Ctrl-I', mac: 'Shift-Command-I' },
        exec: () => {
            const btn = document.getElementById('btn-editor-fiximports');
            if (btn && !btn.disabled) {
                handleFixImports();
            }
        }
    });
}

// --- Listeners del editor ---

function registerEditorListeners() {
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
}

// --- Pestañas ---

function activateTab(filePath) {
    if (activePath === filePath) {
        return;
    }
    const entry = sessions.get(filePath);
    if (!entry) {
        return;
    }
    editor.setSession(entry.session);
    activePath = filePath;

    tabElements.forEach((el, path) => {
        el.classList.toggle('active', path === filePath);
    });

    const tabEl = tabElements.get(filePath);
    if (tabEl && tabEl.scrollIntoView) {
        tabEl.scrollIntoView({ block: 'nearest', inline: 'nearest' });
    }

    updateToolbarState();
    scheduleStatePush({ immediate: true });
}

function createTabElement(filePath) {
    const tabEl = document.createElement('div');
    tabEl.className = 'editor-tab';
    tabEl.setAttribute('role', 'tab');
    tabEl.dataset.path = filePath;

    const dirtyDot = document.createElement('span');
    dirtyDot.className = 'editor-tab-dirty';
    dirtyDot.textContent = '●';
    dirtyDot.style.display = 'none';
    tabEl.appendChild(dirtyDot);

    const nameEl = document.createElement('span');
    nameEl.className = 'editor-tab-name';
    nameEl.textContent = basename(filePath);
    nameEl.title = filePath;
    tabEl.appendChild(nameEl);

    const closeBtn = document.createElement('button');
    closeBtn.type = 'button';
    closeBtn.className = 'editor-tab-close';
    closeBtn.textContent = '✕';
    closeBtn.setAttribute('aria-label', `Cerrar ${basename(filePath)}`);
    closeBtn.addEventListener('click', (event) => {
        event.stopPropagation();
        closeFile(filePath);
    });
    tabEl.appendChild(closeBtn);

    tabEl.addEventListener('click', () => activateTab(filePath));

    tabsEl.appendChild(tabEl);
    tabElements.set(filePath, tabEl);
}

function updateTabDirty(filePath, dirty) {
    const tabEl = tabElements.get(filePath);
    if (!tabEl) {
        return;
    }
    tabEl.classList.toggle('dirty', dirty);
    const dot = tabEl.querySelector('.editor-tab-dirty');
    if (dot) {
        dot.style.display = dirty ? '' : 'none';
    }
}

// --- Diálogo de cierre sucio ---

function confirmCloseDirty(filePath) {
    return new Promise((resolve) => {
        const backdrop = document.createElement('div');
        backdrop.className = 'dir-modal-backdrop';

        const dialog = document.createElement('div');
        dialog.className = 'editor-modal-dialog';
        dialog.style.width = 'min(440px, calc(100vw - 32px))';
        dialog.style.height = 'auto';

        const header = document.createElement('div');
        header.className = 'editor-modal-header';
        header.innerHTML = '<h3>Cambios sin guardar</h3>';

        const body = document.createElement('div');
        body.className = 'editor-modal-body';
        body.style.padding = '18px';
        body.style.display = 'block';
        body.innerHTML = `<p style="margin:0;">El fichero <strong>${escapeHtml(basename(filePath))}</strong> tiene cambios sin guardar. ¿Qué quieres hacer?</p>`;

        const footer = document.createElement('div');
        footer.className = 'editor-modal-footer';

        const btnCancel = document.createElement('button');
        btnCancel.type = 'button';
        btnCancel.className = 'config-paths-btn secondary-button';
        btnCancel.textContent = 'Cancelar';

        const btnDiscard = document.createElement('button');
        btnDiscard.type = 'button';
        btnDiscard.className = 'config-paths-btn secondary-button';
        btnDiscard.textContent = 'Descartar';

        const btnSave = document.createElement('button');
        btnSave.type = 'button';
        btnSave.className = 'config-paths-btn primary-button';
        btnSave.textContent = 'Guardar';

        footer.appendChild(btnCancel);
        footer.appendChild(btnDiscard);
        footer.appendChild(btnSave);

        dialog.appendChild(header);
        dialog.appendChild(body);
        dialog.appendChild(footer);
        backdrop.appendChild(dialog);
        document.body.appendChild(backdrop);

        const close = (result) => {
            document.removeEventListener('keydown', onEsc);
            if (backdrop.parentNode) {
                backdrop.parentNode.removeChild(backdrop);
            }
            resolve(result);
        };

        const onEsc = (e) => {
            if (e.key === 'Escape') {
                close('cancel');
            }
        };

        btnCancel.addEventListener('click', () => close('cancel'));
        btnDiscard.addEventListener('click', () => close('discard'));
        btnSave.addEventListener('click', () => close('save'));
        backdrop.addEventListener('click', (e) => {
            if (e.target === backdrop) {
                close('cancel');
            }
        });
        document.addEventListener('keydown', onEsc);
    });
}

// --- Estado (push) ---

function scheduleStatePush(options = {}) {
    const snapshot = buildStateSnapshot();
    pushState(snapshot, options);
}

function buildStateSnapshot() {
    const openFiles = [];
    for (const [path, entry] of sessions) {
        openFiles.push({ path, dirty: entry.dirty });
    }

    let cursorLine = null;
    let selectionStartLine = null;
    let selectionEndLine = null;
    let selectionText = null;

    if (activePath && editor) {
        const cursor = editor.getCursorPosition();
        const selection = editor.getSelectionRange();
        cursorLine = cursor.row;
        selectionStartLine = selection.start.row;
        selectionEndLine = selection.end.row;
        // Solo enviamos el texto si hay una seleccion real (no un simple cursor).
        if (selection.start.row !== selection.end.row
                || selection.start.column !== selection.end.column) {
            selectionText = editor.getSelectedText();
        }
    }

    return {
        terminalId: localStorage.getItem(TERMINAL_STORAGE_KEY) || 'default',
        openFiles,
        activeFile: activePath,
        cursorLine,
        selectionStartLine,
        selectionEndLine,
        selectionText
    };
}

// --- Utilidades ---

function basename(path) {
    if (!path) {
        return '';
    }
    const idx = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
    return idx >= 0 ? path.slice(idx + 1) : path;
}

function extensionOf(filePath) {
    const name = basename(filePath);
    const dot = name.lastIndexOf('.');
    if (dot < 0) {
        return '';
    }
    return name.slice(dot + 1).toLowerCase();
}

function modeForPath(filePath) {
    const ext = extensionOf(filePath);
    return MODE_BY_EXTENSION[ext] || 'ace/mode/text';
}

function escapeHtml(str) {
    const temp = document.createElement('div');
    temp.textContent = str;
    return temp.innerHTML;
}

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

/**
 * Maneja el evento SSE 'editor_open' emitido por el agente: abre el
 * fichero indicado en el editor (o lo activa si ya estaba abierto) y,
 * opcionalmente, posiciona el cursor en la linea indicada (1-based).
 */
export async function handleEditorOpen(payload) {
    if (!payload || !payload.file) {
        return;
    }
    await openFile(payload.file);
    if (payload.line != null && editor) {
        const entry = sessions.get(payload.file);
        if (entry) {
            editor.setSession(entry.session);
            editor.gotoLine(payload.line, 0, true);
            editor.focus();
        }
    }
}

/**
 * Maneja el evento SSE 'editor_apply' emitido por el agente: aplica una
 * mutacion al buffer del fichero ACTIVO. No guarda en disco.
 *
 * Contrato:
 *   - lineEnd == null -> inserta 'text' en lineStart (como linea nueva,
 *     desplazando el resto).
 *   - lineEnd != null -> reemplaza el rango [lineStart, lineEnd] (1-based,
 *     inclusive) con 'text'. Si 'text' es vacio o nulo, borra el rango.
 *
 * Si no hay fichero activo, descarta el evento y avisa con un toast.
 */
export function handleEditorApply(payload) {
    if (!payload || payload.lineStart == null) {
        return;
    }
    if (!activePath || !editor) {
        showToast('El agente propuso un cambio pero no hay fichero activo en el editor', 'error');
        return;
    }
    const entry = sessions.get(activePath);
    if (!entry) {
        return;
    }

    const session = entry.session;
    const startRow = payload.lineStart - 1;
    const text = payload.text != null ? payload.text : '';
    const lastRow = session.getLength() - 1;

    if (startRow < 0 || startRow > lastRow + 1) {
        showToast(`Línea fuera de rango: ${payload.lineStart}`, 'error');
        return;
    }

    try {
        if (payload.lineEnd == null) {
            // Insercion: nueva linea antes de startRow
            session.insert({ row: startRow, column: 0 }, text + '\n');
        } else {
            const endRow = payload.lineEnd - 1;
            if (endRow < startRow || endRow > lastRow) {
                showToast('Rango de líneas inválido', 'error');
                return;
            }
            const Range = window.ace.require('ace/range').Range;
            // Si el rango no llega a la ultima linea, extendemos hasta el
            // inicio de la siguiente para incluir tambien el salto de linea
            // final (evita dejar una linea vacia residual al borrar).
            const rangeEnd = endRow < lastRow
                    ? { row: endRow + 1, column: 0 }
                    : { row: endRow, column: session.getLine(endRow).length };
            const range = new Range(startRow, 0, rangeEnd.row, rangeEnd.column);
            const replacement = text === '' ? '' : text + '\n';
            session.replace(range, replacement);
        }
        editor.focus();
    } catch (error) {
        console.error('Error aplicando editor_apply:', error);
        showToast('No se pudo aplicar el cambio propuesto por el agente', 'error');
    }
}

/**
 * Convierte una marca pedida por el agente ({line, lineEnd?, text, type?}) al
 * formato de anotación de Ace. Es aquí donde se traduce de 1-based (contrato
 * del agente) a 0-based (coordenadas de Ace).
 */
function toAceAnnotation(session, item) {
    const row = Math.max(0, (item.line || 1) - 1);
    const annotation = {
        row: row,
        column: 0,
        text: item.text || '',
        type: item.type || 'info'
    };
    if (item.lineEnd != null) {
        const endRow = Math.max(row, item.lineEnd - 1);
        annotation.startRow = row;
        annotation.startColumn = 0;
        annotation.endRow = endRow;
        const lastLine = session.getLine(endRow);
        annotation.endColumn = lastLine ? lastLine.length : 0;
    }
    return annotation;
}

/**
 * Maneja el evento SSE 'editor_add_annotation': pinta una marca del agente
 * sobre el fichero ACTIVO. No modifica el buffer ni el disco.
 *
 * Las marcas se acumulan en entry.agentAnnotations, separadas de las de
 * diagnóstico (entry.diagAnnotations): applyAnnotations las mezcla al pintar.
 */
export function handleEditorAddAnnotation(payload) {
    if (!payload || payload.line == null) {
        return;
    }
    if (!activePath || !editor) {
        showToast('El agente señaló una línea pero no hay fichero activo en el editor', 'error');
        return;
    }
    const entry = sessions.get(activePath);
    if (!entry) {
        return;
    }
    if (!entry.agentAnnotations) {
        entry.agentAnnotations = [];
    }
    entry.agentAnnotations.push(toAceAnnotation(entry.session, payload));
    applyAnnotations(entry);

    if (payload.scroll !== false) {
        editor.scrollToLine(Math.max(0, payload.line - 1), true, true);
    }
}

/**
 * Maneja el evento SSE 'editor_clear_annotations': elimina todas las marcas
 * pintadas por el agente en el fichero ACTIVO. Las de diagnóstico se
 * conservan.
 */
export function handleEditorClearAnnotations() {
    if (!activePath) {
        return;
    }
    const entry = sessions.get(activePath);
    if (!entry) {
        return;
    }
    entry.agentAnnotations = [];
    applyAnnotations(entry);
}

async function handleSetupProject() {
    if (!activePath || setupInProgress) {
        return;
    }
    setupInProgress = true;
    updateToolbarState();
    updateSetupButtonState();

    const terminalId = localStorage.getItem(TERMINAL_STORAGE_KEY) || 'default';
    try {
        await postEditorSetupProject(terminalId, activePath);
        // Éxito del envío (202). El resultado real llega por SSE.
        // No quitamos el estado "working" aquí: lo hará onEditorSetupDone.
    } catch (error) {
        console.error('Error lanzando setup-project:', error);
        showToast(`No se pudo lanzar el setup: ${error.message}`, 'error');
        setupInProgress = false;
        updateToolbarState();
        updateSetupButtonState();
    }
}

function updateSetupButtonState() {
    const btn = document.getElementById('btn-editor-setup');
    if (!btn) return;
    btn.classList.toggle('working', setupInProgress);
    const label = setupInProgress ? 'Preparando proyecto...' : 'Preparar proyecto (LSP)';
    btn.title = label;
    btn.setAttribute('aria-label', label);
}

export function handleEditorSetupDone(payload) {
    setupInProgress = false;
    updateToolbarState();
    updateSetupButtonState();

    if (!payload) return;
    if (payload.status === 'ok') {
        showToast('Proyecto preparado para el LSP', 'success', 3000);
    } else {
        showToast(`Setup del proyecto fallido: ${payload.message || 'error desconocido'}`, 'error', 5000);
    }
}

