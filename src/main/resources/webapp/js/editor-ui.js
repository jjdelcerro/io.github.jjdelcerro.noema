/**
 * Módulo de gestión del editor Ace: sesiones múltiples, pestañas y ciclo
 * de apertura/cierre de ficheros.
 *
 * Un único `ace.edit()` en el DOM, N `EditSession` en memoria, una por
 * fichero abierto. Al cambiar de pestaña se intercambia la sesión, con lo
 * que cada fichero conserva su historial de undo, cursor y scroll.
 */

import { showToast } from './toast.js';

const CONTENT_ENDPOINT = '/api/files/content';
const WRAP_STORAGE_KEY = 'noema_editor_wrap';

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

let editor = null;
let tabsEl = null;
let toolbarEl = null;
let editorContainerEl = null;
let resizeObserver = null;
const sessions = new Map();
const tabElements = new Map();
let activePath = null;
let wrapEnabled = false;

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
    editor.setReadOnly(true); // provisional hasta que llegue el guardado

    const emptySession = window.ace.createEditSession('', 'ace/mode/text');
    emptySession.setUseWrapMode(wrapEnabled);
    editor.setSession(emptySession);

    createToolbar();

    // Ace no detecta cambios de tamaño del contenedor por sí solo. El
    // ResizeObserver cubre todos los casos: apertura del workspace,
    // arrastre de splitters, cambio de ventana, etc.
    if (typeof ResizeObserver !== 'undefined' && !resizeObserver) {
        resizeObserver = new ResizeObserver(() => {
            if (editor) {
                editor.resize();
            }
        });
        resizeObserver.observe(editorContainerEl);
    }
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
        const url = `${CONTENT_ENDPOINT}?file=${encodeURIComponent(filePath)}`;
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
    sessions.set(filePath, session);

    createTabElement(filePath);
    activateTab(filePath);

    // El panel puede haber estado oculto cuando Ace midió por primera vez;
    // un resize al siguiente frame corrige la disposición.
    requestAnimationFrame(() => {
        if (editor) {
            editor.resize();
        }
    });
}

/**
 * Cierra una pestaña y libera su sesión. Si era la activa, salta a la
 * última restante o deja el editor vacío.
 */
export function closeFile(filePath) {
    const tabEl = tabElements.get(filePath);
    if (tabEl) {
        tabEl.remove();
    }
    tabElements.delete(filePath);
    sessions.delete(filePath);

    if (activePath !== filePath) {
        return;
    }

    const remaining = Array.from(sessions.keys());
    if (remaining.length === 0) {
        activePath = null;
        const emptySession = window.ace.createEditSession('', 'ace/mode/text');
        emptySession.setUseWrapMode(wrapEnabled);
        editor.setSession(emptySession);
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

    const wrapBtn = document.createElement('button');
    wrapBtn.type = 'button';
    wrapBtn.id = 'btn-editor-wrap';
    wrapBtn.className = 'editor-toolbar-btn';
    wrapBtn.innerHTML = '<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><line x1="3" y1="6" x2="21" y2="6"/><path d="M3 12h15a3 3 0 1 1 0 6h-3"/><polyline points="18 15 15 18 18 21"/><line x1="3" y1="18" x2="9" y2="18"/></svg>';
    wrapBtn.addEventListener('click', toggleWrap);
    toolbarEl.appendChild(wrapBtn);

    updateWrapButtonState();
}

function toggleWrap() {
    wrapEnabled = !wrapEnabled;
    localStorage.setItem(WRAP_STORAGE_KEY, String(wrapEnabled));
    sessions.forEach((session) => session.setUseWrapMode(wrapEnabled));
    updateWrapButtonState();
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

// --- Internos ---

function activateTab(filePath) {
    if (activePath === filePath) {
        return;
    }
    const session = sessions.get(filePath);
    if (!session) {
        return;
    }
    editor.setSession(session);
    activePath = filePath;

    tabElements.forEach((el, path) => {
        el.classList.toggle('active', path === filePath);
    });

    const tabEl = tabElements.get(filePath);
    if (tabEl && tabEl.scrollIntoView) {
        tabEl.scrollIntoView({ block: 'nearest', inline: 'nearest' });
    }
}

function createTabElement(filePath) {
    const tabEl = document.createElement('div');
    tabEl.className = 'editor-tab';
    tabEl.setAttribute('role', 'tab');
    tabEl.dataset.path = filePath;

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

function basename(path) {
    if (!path) {
        return '';
    }
    const idx = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
    return idx >= 0 ? path.slice(idx + 1) : path;
}

function modeForPath(filePath) {
    const name = basename(filePath);
    const dot = name.lastIndexOf('.');
    if (dot < 0) {
        return 'ace/mode/text';
    }
    const ext = name.slice(dot + 1).toLowerCase();
    return MODE_BY_EXTENSION[ext] || 'ace/mode/text';
}
