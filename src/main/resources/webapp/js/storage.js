/**
 * Estado local del WebUI.
 *
 * Una sola clave de `localStorage` con un objeto de secciones, una por componente.
 * Sustituye a las siete claves sueltas que cada modulo leia y escribia por su cuenta.
 *
 * El objeto completo se mantiene en memoria y esa copia es la fuente de lectura: asi
 * un `write` seguido de un `read` devuelve lo recien escrito aunque la persistencia
 * todavia no se haya volcado. El volcado a `localStorage` va con debounce para que un
 * arrastre de splitter escriba una vez al soltar y no en cada `pointermove`.
 *
 * No hay compatibilidad con las claves antiguas (`noema_terminal_id`,
 * `noema_sidebar_collapsed`, etc.): se dejan en el navegador sin leer, y su limpieza
 * es responsabilidad del usuario.
 *
 * Que va aqui y que va al servidor: el navegador guarda lo que solo afecta a la
 * comodidad del usuario (anchos, colapsados, pestanas activas). El estado que el
 * agente necesita para razonar va al servidor mediante `ui-state.js`; este modulo no
 * lo toca.
 */

const STORAGE_KEY = 'noema.ui.state';
const DEBOUNCE_MS = 250;

/**
 * Objeto completo en memoria. `null` hasta la primera lectura.
 * @type {Object|null}
 */
let cache = null;

/**
 * Temporizador del volcado pendiente.
 * @type {number|null}
 */
let timer = null;

/**
 * Crea el estado vacio.
 *
 * @returns {Object} Objeto sin secciones.
 */
function emptyState() {
    return {};
}

/**
 * Carga el estado desde `localStorage` la primera vez y lo cachea.
 *
 * Si la clave no existe, el JSON esta corrupto o el almacenamiento no es accesible
 * (modo privado, politicas del navegador), devuelve un estado vacio en lugar de
 * propagar el error: perder las preferencias es molesto, pero no debe impedir que la
 * aplicacion arranque.
 *
 * @returns {Object} El objeto de estado completo.
 */
function load() {
    if (cache !== null) {
        return cache;
    }

    let raw = null;
    try {
        raw = window.localStorage.getItem(STORAGE_KEY);
    } catch (e) {
        raw = null;
    }

    if (!raw) {
        cache = emptyState();
        return cache;
    }

    try {
        const parsed = JSON.parse(raw);
        const isPlainObject = parsed !== null
            && typeof parsed === 'object'
            && !Array.isArray(parsed);
        cache = isPlainObject ? parsed : emptyState();
    } catch (e) {
        cache = emptyState();
    }

    return cache;
}

/**
 * Vuelca el estado en memoria a `localStorage` de forma inmediata.
 *
 * Cancela el debounce pendiente. Es la operacion que se invoca al cerrar u ocultar
 * la pagina, donde esperar 250 ms significa perder la ultima escritura.
 *
 * @returns {void}
 */
export function flush() {
    if (timer !== null) {
        window.clearTimeout(timer);
        timer = null;
    }

    try {
        const serialized = JSON.stringify(load());
        window.localStorage.setItem(STORAGE_KEY, serialized);
    } catch (e) {
        // Cuota agotada o almacenamiento bloqueado. La copia en memoria sigue siendo
        // valida durante esta sesion, asi que se ignora.
    }
}

/**
 * Programa un volcado con debounce.
 *
 * @returns {void}
 */
function scheduleFlush() {
    if (timer !== null) {
        window.clearTimeout(timer);
    }
    timer = window.setTimeout(flush, DEBOUNCE_MS);
}

/**
 * Devuelve la seccion indicada.
 *
 * @param {string} section Nombre de la seccion (`app`, `workspace`, `fsexplorer`, ...).
 * @returns {*} El valor guardado, o `null` si no existe.
 */
export function read(section) {
    const state = load();
    const value = state[section];
    return value === undefined ? null : value;
}

/**
 * Reemplaza la seccion indicada.
 *
 * El valor se guarda por referencia: el llamador no debe mutarlo despues.
 *
 * @param {string} section Nombre de la seccion.
 * @param {*} value Valor nuevo.
 * @returns {void}
 */
export function write(section, value) {
    const state = load();
    state[section] = value;
    scheduleFlush();
}

/**
 * Fusiona un parche en la seccion indicada, a un solo nivel.
 *
 * Si la seccion no existe o no es un objeto, se parte de un objeto vacio. Las claves
 * del parche sobrescriben las existentes; las que no aparecen se conservan.
 *
 * @param {string} section Nombre de la seccion.
 * @param {Object} patch Claves a fusionar.
 * @returns {void}
 */
export function update(section, patch) {
    const state = load();
    const current = state[section];
    const base = current !== null
        && typeof current === 'object'
        && !Array.isArray(current) ? current : {};

    state[section] = Object.assign({}, base, patch);
    scheduleFlush();
}

/**
 * Vuelca de forma sincrona cuando la pagina se oculta o se descarga.
 *
 * `pagehide` cubre la navegacion normal; `visibilitychange` cubre el caso de movil,
 * donde `pagehide` puede no llegar nunca.
 */
window.addEventListener('pagehide', flush);
document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'hidden') {
        flush();
    }
});