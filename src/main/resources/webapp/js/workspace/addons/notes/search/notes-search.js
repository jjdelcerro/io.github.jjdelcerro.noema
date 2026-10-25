/**
 * Modulo de busqueda de notas.
 *
 * La busqueda vive en el panel inferior de la columna izquierda, debajo del
 * arbol. Arbol y resultados COEXISTEN: buscar no oculta el explorador, de
 * modo que se puede seguir navegando mientras se ven los resultados.
 *
 * El ambito lo fija la ruta del ultimo nodo seleccionado en el arbol; el
 * boton "Todo" lo devuelve al conjunto completo de notas. Ese ambito se
 * envia al servidor como parametro root, de modo que la busqueda se limita
 * a esa carpeta y sus descendientes.
 *
 * La busqueda NO es automatica. Solo la disparan Enter y el boton Buscar. Ni
 * el cambio de modo ni el cambio de ambito relanzan la consulta: solo fijan
 * el estado y descartan los resultados anteriores, que ya no corresponden a
 * los criterios vigentes. Las respuestas fuera de secuencia se descartan.
 */

import { searchNotes } from '../notes-api.js';

const PAGE_SIZE = 50;

const state = {
    query: '',
    mode: 'glob',
    root: '',
    page: 0,
    result: null
};

let initialized = false;
let requestSeq = 0;

/**
 * Inicializa la busqueda. Es idempotente y silenciosa si los elementos del
 * DOM no existen.
 */
export function initNotesSearch() {
    if (initialized) {
        return;
    }
    const input = document.getElementById('notes-search-input');
    if (!input) {
        return;
    }
    initialized = true;

    // Sin debounce: teclear no lanza nada. Solo Enter y el boton Buscar.
    input.addEventListener('keydown', (event) => {
        if (event.key === 'Enter') {
            event.preventDefault();
            runSearch(0);
        }
        if (event.key === 'Escape') {
            event.preventDefault();
            clearSearch();
        }
    });

    const go = document.getElementById('notes-search-go');
    if (go) {
        go.addEventListener('click', () => {
            runSearch(0);
        });
    }

    const mode = document.getElementById('notes-search-mode');
    if (mode) {
        // El DOM manda. Si el select muestra "Patron", el estado no puede
        // quedarse en su valor de arranque: sin esta linea, la primera
        // busqueda tras recargar sale con otro modo y el backend ejecuta un
        // matcher distinto del que anuncia la interfaz.
        state.mode = mode.value || state.mode;
        mode.addEventListener('change', () => {
            state.mode = mode.value || state.mode;
            resetResults();
        });
    }

    const prev = document.getElementById('notes-search-prev');
    if (prev) {
        prev.addEventListener('click', () => {
            if (state.page > 0) {
                runSearch(state.page - 1);
            }
        });
    }

    const next = document.getElementById('notes-search-next');
    if (next) {
        next.addEventListener('click', () => {
            runSearch(state.page + 1);
        });
    }

    const reset = document.getElementById('notes-search-scope-reset');
    if (reset) {
        reset.addEventListener('click', () => {
            setScope('');
        });
    }

    // El arbol avisa de cada seleccion para fijar el ambito.
    window.addEventListener('noema:notes-scope', (event) => {
        const detail = event.detail || {};
        setScope(detail.path || '');
    });

    // Si cambian las notas, los resultados pintados pueden haber quedado
    // viejos; se descartan en lugar de relanzar la consulta por detras.
    window.addEventListener('noema:notes-changed', () => {
        resetResults();
    });
}

/**
 * Fija el ambito de busqueda y descarta los resultados anteriores. No
 * relanza la consulta: eso solo ocurre con Enter o con el boton Buscar.
 *
 * @param {string} path ruta relativa del nodo seleccionado, o cadena vacia
 *                      para buscar en todas las notas
 */
export function setNotesSearchScope(path) {
    setScope(path);
}

function setScope(path) {
    state.root = path || '';
    const value = document.getElementById('notes-search-scope');
    const reset = document.getElementById('notes-search-scope-reset');
    if (value) {
        value.textContent = state.root || 'Todo';
        value.title = state.root || 'Todas las notas';
    }
    if (reset) {
        reset.hidden = !state.root;
    }
    resetResults();
}

/**
 * Descarta los resultados pintados y el paginador, conservando el texto de
 * la caja y el modo elegido. Se usa cuando cambian los criterios (modo,
 * ambito) o el corpus: lo que habia ya no corresponde a nada vigente.
 */
function resetResults() {
    const results = document.getElementById('notes-search-results');
    const pager = document.getElementById('notes-search-pager');
    state.page = 0;
    state.result = null;
    requestSeq++;
    if (results) {
        results.replaceChildren();
        results.hidden = true;
    }
    if (pager) {
        pager.hidden = true;
    }
}

/**
 * Lanza la busqueda de la pagina indicada. Descarta respuestas de peticiones
 * anteriores para que una respuesta lenta no pise a una reciente.
 */
async function runSearch(page) {
    const input = document.getElementById('notes-search-input');
    if (!input) {
        return;
    }
    const query = input.value.trim();
    state.query = query;
    state.page = page < 0 ? 0 : page;

    if (!query) {
        clearSearch();
        return;
    }

    const seq = ++requestSeq;
    setBusy(true);
    try {
        const result = await searchNotes({
            query: state.query,
            mode: state.mode,
            root: state.root,
            page: state.page,
            pageSize: PAGE_SIZE
        });
        if (seq !== requestSeq) {
            return;
        }
        state.result = result;
        render();
    } catch (error) {
        if (seq !== requestSeq) {
            return;
        }
        console.error('Error buscando notas:', error);
        state.result = null;
        renderError();
    } finally {
        if (seq === requestSeq) {
            setBusy(false);
        }
    }
}

/**
 * Pinta el resultado. El arbol NO se oculta: ambos paneles conviven.
 */
function render() {
    const results = document.getElementById('notes-search-results');
    const pager = document.getElementById('notes-search-pager');
    if (!results) {
        return;
    }
    const result = state.result;
    results.replaceChildren();

    if (!result || !result.items || result.items.length === 0) {
        const empty = document.createElement('div');
        empty.className = 'notes-search-empty';
        empty.textContent = 'Sin resultados.';
        results.append(empty);
        results.hidden = false;
        if (pager) {
            pager.hidden = true;
        }
        return;
    }

    for (const item of result.items) {
        results.append(createResultButton(item));
    }
    results.hidden = false;
    updatePager();
    if (pager) {
        pager.hidden = false;
    }
}

function createResultButton(item) {
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'notes-search-item';
    button.dataset.noteId = item.id;

    const title = document.createElement('span');
    title.className = 'notes-search-item-title';
    title.textContent = item.title || item.id;
    button.append(title);

    const meta = document.createElement('span');
    meta.className = 'notes-search-item-meta';
    meta.textContent = buildMeta(item);
    button.append(meta);

    button.addEventListener('click', () => {
        openResult(item, button);
    });
    return button;
}

/**
 * Segunda linea del resultado: fecha, tags y, solo en modo difuso, score.
 */
function buildMeta(item) {
    const parts = [];
    if (item.updated) {
        parts.push(item.updated.substring(0, 10));
    }
    if (item.tags && item.tags.length > 0) {
        parts.push(item.tags.join(', '));
    }
    if (state.mode === 'fuzzy' && typeof item.score === 'number') {
        parts.push(item.score.toFixed(2));
    }
    return parts.join(' \u00b7 ');
}

function updatePager() {
    const result = state.result;
    const count = document.getElementById('notes-search-count');
    const prev = document.getElementById('notes-search-prev');
    const next = document.getElementById('notes-search-next');
    if (!result || !count) {
        return;
    }
    const pageLength = result.items ? result.items.length : 0;
    const from = pageLength === 0 ? 0 : state.page * PAGE_SIZE + 1;
    const to = state.page * PAGE_SIZE + pageLength;
    const total = result.total || 0;
    let label = `${from}-${to} de ${total}`;
    if (result.truncated) {
        label += ' (limite alcanzado)';
    }
    count.textContent = label;
    if (prev) {
        prev.disabled = state.page <= 0;
    }
    if (next) {
        next.disabled = to >= total;
    }
}

function renderError() {
    const results = document.getElementById('notes-search-results');
    const pager = document.getElementById('notes-search-pager');
    if (!results) {
        return;
    }
    results.replaceChildren();
    const error = document.createElement('div');
    error.className = 'notes-search-empty';
    error.textContent = 'No se pudo completar la busqueda.';
    results.append(error);
    results.hidden = false;
    if (pager) {
        pager.hidden = true;
    }
}

/**
 * Limpia la caja y los resultados, y devuelve el foco al arbol.
 */
function clearSearch() {
    const input = document.getElementById('notes-search-input');
    const results = document.getElementById('notes-search-results');
    const pager = document.getElementById('notes-search-pager');
    if (input) {
        input.value = '';
    }
    state.query = '';
    state.page = 0;
    state.result = null;
    requestSeq++;
    if (results) {
        results.replaceChildren();
        results.hidden = true;
    }
    if (pager) {
        pager.hidden = true;
    }
    setBusy(false);
}

function setBusy(busy) {
    const input = document.getElementById('notes-search-input');
    if (input) {
        input.setAttribute('aria-busy', busy ? 'true' : 'false');
    }
}

/**
 * Abre la nota seleccionada en el editor. En modo exacto se pasa el termino
 * buscado para que el editor resalte la primera coincidencia en el cuerpo.
 */
async function openResult(item, button) {
    document.querySelectorAll('.notes-search-item.active').forEach((node) => {
        node.classList.remove('active');
    });
    button.classList.add('active');
    try {
        const mod = await import('../editor/note-editor.js');
        const highlight = state.mode === 'exact' ? state.query.trim() : null;
        await mod.openNote({
            id: item.id,
            path: item.path,
            title: item.title,
            highlight: highlight
        });
    } catch (error) {
        console.error('Error abriendo la nota desde la busqueda:', item.id, error);
    }
}
