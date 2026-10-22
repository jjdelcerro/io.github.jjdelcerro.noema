/**
 * Módulo de gestión de divisores arrastrables (splitters).
 *
 * Cada splitter tiene un panel "before" (redimensionable) y un panel
 * "after" (flexible). Al arrastrar el handle, se modifica el tamaño del
 * "before" y el "after" absorbe la diferencia por ser flex:1.
 *
 * Soporta dos orientaciones:
 *   - vertical (por defecto): separa columnas, arrastra en X, ajusta ancho.
 *   - horizontal: separa filas, arrastra en Y, ajusta alto.
 *
 * Persistencia por splitter en localStorage. Al redimensionar la ventana,
 * se reaplica el clamp para no romper los mínimos.
 */

const STORAGE_WORKSPACE = 'noema_workspace_width';
const STORAGE_TREE = 'noema_tree_width';
const STORAGE_NOTES_TREE = 'noema_notes_tree_width';
const STORAGE_NOTES_SEARCH = 'noema_notes_search_height';

// Mínimos (px). Ajustables en un solo sitio.
const MIN_WORKSPACE = 200;
const MIN_CONVERSATION = 400;
const MIN_TREE = 100;
const MIN_EDITOR = 100;
const MIN_NOTES_TREE = 120;
const MIN_NOTES_MAIN = 200;
const MIN_NOTES_SEARCH = 140;

// Tamaños por defecto (px). Se aplican la primera vez y con doble clic.
const DEFAULT_WORKSPACE = 720;
const DEFAULT_TREE = 240;
const DEFAULT_NOTES_TREE = 260;
const DEFAULT_NOTES_TREE_HEIGHT = 260;

const activeSplitters = [];

/**
 * Inicializa los splitters del layout: workspace/conversacion, arbol del
 * workspace/editor, lista de notas/editor de notas y arbol de notas/panel
 * de busqueda.
 */
export function initSplitters() {
    setupSplitter({
        handleId: 'splitter-workspace',
        beforeSelector: '.work-area',
        afterSelector: '.conversation-shell',
        minBefore: MIN_WORKSPACE,
        minAfter: MIN_CONVERSATION,
        defaultBefore: DEFAULT_WORKSPACE,
        storageKey: STORAGE_WORKSPACE
    });

    setupSplitter({
        handleId: 'splitter-tree',
        beforeSelector: '.workspace-tree-pane',
        afterSelector: '.workspace-editor-pane',
        minBefore: MIN_TREE,
        minAfter: MIN_EDITOR,
        defaultBefore: DEFAULT_TREE,
        storageKey: STORAGE_TREE
    });

    setupSplitter({
        handleId: 'splitter-notes',
        beforeSelector: '.notes-left-pane',
        afterSelector: '.notes-main-pane',
        minBefore: MIN_NOTES_TREE,
        minAfter: MIN_NOTES_MAIN,
        defaultBefore: DEFAULT_NOTES_TREE,
        storageKey: STORAGE_NOTES_TREE
    });

    setupSplitter({
        handleId: 'splitter-notes-h',
        beforeSelector: '.notes-tree-pane',
        afterSelector: '.notes-search-pane',
        orientation: 'horizontal',
        minBefore: MIN_NOTES_TREE,
        minAfter: MIN_NOTES_SEARCH,
        defaultBefore: DEFAULT_NOTES_TREE_HEIGHT,
        storageKey: STORAGE_NOTES_SEARCH
    });

    // Al cambiar de vista del area, los paneles ocultos pasan a tener
    // dimensiones reales: se reaplica el clamp y el tamaño guardado.
    window.addEventListener('noema:area-changed', () => {
        requestAnimationFrame(() => refreshSplitters());
    });
}

/**
 * Configura un splitter individual: arrastre, doble clic, persistencia y
 * re-clamp en resize. Silencioso si falta cualquier elemento del DOM.
 *
 * config.orientation admite 'vertical' (por defecto) u 'horizontal'.
 */
function setupSplitter(config) {
    const handle = document.getElementById(config.handleId);
    const beforeEl = document.querySelector(config.beforeSelector);
    const afterEl = document.querySelector(config.afterSelector);
    if (!handle || !beforeEl || !afterEl) {
        return;
    }

    const horizontal = config.orientation === 'horizontal';
    activeSplitters.push({ handle, beforeEl, afterEl, config, horizontal });

    // Solo aplica el tamaño inicial si el contenedor tiene dimensión real.
    // Si está oculto (panel de notas sin activar), el clamp se pospone a
    // refreshSplitters(), que se dispara al activar la vista.
    if (measure(beforeEl, horizontal) > 0) {
        const stored = parseFloat(localStorage.getItem(config.storageKey));
        const initial = Number.isFinite(stored) && stored > 0 ? stored : config.defaultBefore;
        applySize(beforeEl, clampSize(initial, beforeEl, afterEl, config, horizontal), horizontal);
    }

    let dragStart = 0;
    let dragStartSize = 0;

    handle.addEventListener('pointerdown', (event) => {
        if (event.button !== 0) {
            return;
        }
        dragStart = horizontal ? event.clientY : event.clientX;
        dragStartSize = measure(beforeEl, horizontal);
        handle.setPointerCapture(event.pointerId);
        handle.classList.add('dragging');
        document.body.classList.add('no-select');
        event.preventDefault();
    });

    handle.addEventListener('pointermove', (event) => {
        if (!handle.hasPointerCapture(event.pointerId)) {
            return;
        }
        const position = horizontal ? event.clientY : event.clientX;
        const desired = dragStartSize + (position - dragStart);
        const clamped = clampSize(desired, beforeEl, afterEl, config, horizontal);
        applySize(beforeEl, clamped, horizontal);
    });

    const endDrag = (event) => {
        if (!handle.hasPointerCapture(event.pointerId)) {
            return;
        }
        handle.releasePointerCapture(event.pointerId);
        handle.classList.remove('dragging');
        document.body.classList.remove('no-select');
        const finalSize = measure(beforeEl, horizontal);
        localStorage.setItem(config.storageKey, String(Math.round(finalSize)));
    };

    handle.addEventListener('pointerup', endDrag);
    handle.addEventListener('pointercancel', endDrag);

    handle.addEventListener('dblclick', () => {
        const clamped = clampSize(config.defaultBefore, beforeEl, afterEl, config, horizontal);
        applySize(beforeEl, clamped, horizontal);
        localStorage.setItem(config.storageKey, String(Math.round(clamped)));
    });

    window.addEventListener('resize', () => {
        const current = measure(beforeEl, horizontal);
        const clamped = clampSize(current, beforeEl, afterEl, config, horizontal);
        if (Math.abs(clamped - current) > 1) {
            applySize(beforeEl, clamped, horizontal);
        }
    });
}

/**
 * Mide el tamaño del panel en el eje del splitter.
 */
function measure(el, horizontal) {
    const rect = el.getBoundingClientRect();
    return horizontal ? rect.height : rect.width;
}

/**
 * Fija el tamaño del panel redimensionable. Se escribe el shorthand flex
 * para que el panel "after" absorba siempre la diferencia: el "before"
 * queda con grow y shrink a cero.
 */
function applySize(el, size, horizontal) {
    el.style.flex = '0 0 ' + size + 'px';
    if (horizontal) {
        el.style.height = size + 'px';
    }
}

/**
 * Limita el tamaño del panel "before" para respetar ambos mínimos.
 * Calcula el total disponible sumando los tres elementos del eje.
 */
function clampSize(desired, beforeEl, afterEl, config, horizontal) {
    const handleEl = beforeEl.nextElementSibling;
    const handleSize = (handleEl && handleEl.classList.contains('splitter'))
            ? measure(handleEl, horizontal)
            : 0;
    const total = measure(beforeEl, horizontal)
            + handleSize
            + measure(afterEl, horizontal);
    const maxBefore = total - config.minAfter - handleSize;
    return Math.max(config.minBefore, Math.min(desired, maxBefore));
}

/**
 * Reaplica el clamp y los tamaños guardados de todos los splitters.
 * Pensado para llamarse cuando un panel que estaba oculto pasa a visible
 * (por ejemplo, al abrir el workspace o la pestaña de notas), porque en
 * ese momento ya se pueden medir las dimensiones reales.
 */
export function refreshSplitters() {
    for (const entry of activeSplitters) {
        const { beforeEl, afterEl, config, horizontal } = entry;
        if (measure(beforeEl, horizontal) === 0) {
            continue;
        }
        const stored = parseFloat(localStorage.getItem(config.storageKey));
        const target = Number.isFinite(stored) && stored > 0 ? stored : config.defaultBefore;
        const clamped = clampSize(target, beforeEl, afterEl, config, horizontal);
        applySize(beforeEl, clamped, horizontal);
    }
}
