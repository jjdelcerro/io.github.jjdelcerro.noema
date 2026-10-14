/**
 * Módulo de gestión de divisores arrastrables (splitters).
 *
 * Cada splitter tiene un panel "before" (redimensionable) y un panel
 * "after" (flexible). Al arrastrar el handle, se modifica el ancho del
 * "before" y el "after" absorbe la diferencia por ser flex:1.
 *
 * Persistencia por splitter en localStorage. Al redimensionar la ventana,
 * se reaplica el clamp para no romper los mínimos.
 */

const STORAGE_WORKSPACE = 'noema_workspace_width';
const STORAGE_TREE = 'noema_tree_width';

// Mínimos (px). Ajustables en un solo sitio.
const MIN_WORKSPACE = 200;
const MIN_CONVERSATION = 400;
const MIN_TREE = 100;
const MIN_EDITOR = 100;

// Anchos por defecto (px). Se aplican la primera vez y con doble clic.
const DEFAULT_WORKSPACE = 720;
const DEFAULT_TREE = 240;

const activeSplitters = [];

/**
 * Inicializa los dos splitters del layout.
 */
export function initSplitters() {
    setupSplitter({
        handleId: 'splitter-workspace',
        beforeSelector: '.workspace-panel',
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
}

/**
 * Configura un splitter individual: arrastre, doble clic, persistencia y
 * re-clamp en resize. Silencioso si falta cualquier elemento del DOM.
 */
function setupSplitter(config) {
    const handle = document.getElementById(config.handleId);
    const beforeEl = document.querySelector(config.beforeSelector);
    const afterEl = document.querySelector(config.afterSelector);
    if (!handle || !beforeEl || !afterEl) {
        return;
    }

    activeSplitters.push({ handle, beforeEl, afterEl, config });

    // Solo aplica el ancho inicial si el contenedor tiene dimension real.
    // Si esta oculto (panel del workspace plegado al arrancar), el clamp se
    // pospone a refreshSplitters(), que se llama al abrirlo.
    if (beforeEl.getBoundingClientRect().width > 0) {
        const stored = parseFloat(localStorage.getItem(config.storageKey));
        const initial = Number.isFinite(stored) && stored > 0 ? stored : config.defaultBefore;
        applyWidth(beforeEl, clampWidth(initial, beforeEl, afterEl, config.minBefore, config.minAfter));
    }

    let dragStartX = 0;
    let dragStartWidth = 0;

    handle.addEventListener('pointerdown', (event) => {
        if (event.button !== 0) {
            return;
        }
        dragStartX = event.clientX;
        dragStartWidth = beforeEl.getBoundingClientRect().width;
        handle.setPointerCapture(event.pointerId);
        handle.classList.add('dragging');
        document.body.classList.add('no-select');
        event.preventDefault();
    });

    handle.addEventListener('pointermove', (event) => {
        if (!handle.hasPointerCapture(event.pointerId)) {
            return;
        }
        const delta = event.clientX - dragStartX;
        const desired = dragStartWidth + delta;
        const clamped = clampWidth(desired, beforeEl, afterEl, config.minBefore, config.minAfter);
        applyWidth(beforeEl, clamped);
    });

    const endDrag = (event) => {
        if (!handle.hasPointerCapture(event.pointerId)) {
            return;
        }
        handle.releasePointerCapture(event.pointerId);
        handle.classList.remove('dragging');
        document.body.classList.remove('no-select');
        const finalWidth = beforeEl.getBoundingClientRect().width;
        localStorage.setItem(config.storageKey, String(Math.round(finalWidth)));
    };

    handle.addEventListener('pointerup', endDrag);
    handle.addEventListener('pointercancel', endDrag);

    handle.addEventListener('dblclick', () => {
        const clamped = clampWidth(config.defaultBefore, beforeEl, afterEl, config.minBefore, config.minAfter);
        applyWidth(beforeEl, clamped);
        localStorage.setItem(config.storageKey, String(Math.round(clamped)));
    });

    window.addEventListener('resize', () => {
        const current = beforeEl.getBoundingClientRect().width;
        const clamped = clampWidth(current, beforeEl, afterEl, config.minBefore, config.minAfter);
        if (Math.abs(clamped - current) > 1) {
            applyWidth(beforeEl, clamped);
        }
    });
}

/**
 * Aplica el ancho al panel redimensionable. Usa flex-basis para no romper
 * el modelo flex del layout.
 */
function applyWidth(el, width) {
    el.style.flexBasis = width + 'px';
}

/**
 * Limita el ancho del panel "before" para respetar ambos mínimos.
 * Calcula el total disponible sumando los tres elementos de la fila.
 */
function clampWidth(desired, beforeEl, afterEl, minBefore, minAfter) {
    const handleEl = beforeEl.nextElementSibling;
    const handleWidth = (handleEl && handleEl.classList.contains('splitter'))
            ? handleEl.getBoundingClientRect().width
            : 0;
    const total = beforeEl.getBoundingClientRect().width
            + handleWidth
            + afterEl.getBoundingClientRect().width;
    const maxBefore = total - minAfter - handleWidth;
    return Math.max(minBefore, Math.min(desired, maxBefore));
}

/**
 * Reaplica el clamp y los anchos guardados de todos los splitters.
 * Pensado para llamarse cuando un panel que estaba oculto pasa a visible
 * (por ejemplo, al abrir el workspace), porque en ese momento ya se
 * pueden medir las dimensiones reales.
 */
export function refreshSplitters() {
    for (const entry of activeSplitters) {
        const { beforeEl, afterEl, config } = entry;
        if (beforeEl.getBoundingClientRect().width === 0) {
            continue;
        }
        const stored = parseFloat(localStorage.getItem(config.storageKey));
        const target = Number.isFinite(stored) && stored > 0 ? stored : config.defaultBefore;
        const clamped = clampWidth(target, beforeEl, afterEl, config.minBefore, config.minAfter);
        applyWidth(beforeEl, clamped);
    }
}

