/**
 * Pestañas del area de trabajo (Editor / Notas).
 *
 * Conmutan la vista visible dentro de .area-body, mantienen aria-selected y
 * persisten la eleccion en localStorage bajo noema_active_area.
 *
 * Cada vista puede registrarse con registerAreaView para exponer tres
 * ganchos opcionales:
 *
 * - isDirty(): true si hay cambios sin guardar
 * - confirmLeave(): false para abortar el cambio de pestaña
 * - onActivate(): se llama al pasar a ser la vista activa
 *
 * El contrato se consulta antes de abandonar una vista, igual que hace la
 * barra de pestañas de la conversacion con el editor.
 */

const STORAGE_KEY = 'noema_active_area';
const DEFAULT_AREA = 'workspace';
const AREAS = ['workspace', 'notes'];

const views = new Map();
let activeArea = DEFAULT_AREA;
let initialized = false;

/**
 * Inicializa la tabbar de area. Idempotente.
 */
export function initAreaTabs() {
    if (initialized) {
        return;
    }
    initialized = true;

    for (const name of AREAS) {
        const tab = document.querySelector(`.area-tabbar .tab-button[data-area="${name}"]`);
        if (!tab) {
            continue;
        }
        tab.addEventListener('click', () => {
            switchArea(name);
        });
        tab.addEventListener('keydown', (event) => {
            if (event.key === 'Enter' || event.key === ' ') {
                event.preventDefault();
                switchArea(name);
            }
        });
    }

    const stored = localStorage.getItem(STORAGE_KEY);
    const initial = AREAS.includes(stored) ? stored : DEFAULT_AREA;
    applyArea(initial);
}

/**
 * Registra los ganchos de una vista.
 *
 * @param {string} name identificador de la vista
 * @param {{isDirty?: Function, confirmLeave?: Function, onActivate?: Function}} handlers
 */
export function registerAreaView(name, handlers) {
    if (!AREAS.includes(name) || !handlers) {
        return;
    }
    views.set(name, handlers);
}

/**
 * Devuelve el identificador de la vista activa.
 *
 * @returns {string}
 */
export function getActiveArea() {
    return activeArea;
}

/**
 * Cambia de vista. Consulta isDirty()/confirmLeave() de la vista que se
 * abandona y aborta si el usuario cancela.
 *
 * @param {string} name vista destino
 * @returns {boolean} true si el cambio se ha aplicado
 */
export function switchArea(name) {
    if (!AREAS.includes(name) || name === activeArea) {
        return false;
    }
    const current = views.get(activeArea);
    if (current && typeof current.isDirty === 'function' && current.isDirty()) {
        const allowed = (typeof current.confirmLeave === 'function')
                ? current.confirmLeave() !== false
                : window.confirm('Hay cambios sin guardar. ¿Descartarlos?');
        if (!allowed) {
            return false;
        }
    }
    applyArea(name);
    return true;
}

function applyArea(name) {
    activeArea = name;
    localStorage.setItem(STORAGE_KEY, name);

    for (const candidate of AREAS) {
        const tab = document.querySelector(`.area-tabbar .tab-button[data-area="${candidate}"]`);
        const view = document.querySelector(`[data-area-view="${candidate}"]`);
        const active = candidate === name;
        if (tab) {
            tab.classList.toggle('active', active);
            tab.setAttribute('aria-selected', active ? 'true' : 'false');
        }
        if (view) {
            view.hidden = !active;
        }
    }

    const handlers = views.get(name);
    if (handlers && typeof handlers.onActivate === 'function') {
        handlers.onActivate();
    }

    window.dispatchEvent(new CustomEvent('noema:area-changed', { detail: { area: name } }));
}
