/**
 * Pestanas genericas.
 *
 * Dibuja la barra de pestanas y, opcionalmente, alterna la visibilidad de los
 * paneles. No renderiza contenido: quien lo usa monta el contenido en el panel
 * correspondiente y marca cada panel con `data-tab-panel`.
 *
 * El componente es dueno del contenedor: lo vacia al construirse, de modo que
 * el markup de la barra no vive en el HTML.
 *
 * Los paneles se buscan entre los hijos directos de `panelContainer`, cuyo
 * `dataset.tabPanel` coincida con el id del item. Si no se pasa
 * `panelContainer`, el componente solo gestiona la barra.
 *
 * Consumidores previstos: las pestanas del chat, las del area de trabajo
 * (Editor / Notas) y las del editor de documentos del explorador.
 */

export function createTabs(config) {
    const container = config.container;
    if (!container) {
        throw new Error('createTabs: falta container');
    }

    const panelContainer = config.panelContainer || null;
    const onActivate = typeof config.onActivate === 'function' ? config.onActivate : null;
    const onClose = typeof config.onClose === 'function' ? config.onClose : null;

    const items = [];
    let activeId = null;
    let destroyed = false;

    container.textContent = '';
    container.classList.add('tabbar');
    container.setAttribute('role', 'tablist');

    /**
     * Devuelve la posicion de un item en la lista.
     *
     * @param {string} id Identificador del item.
     * @returns {number} Indice, o -1 si no existe.
     */
    function indexOf(id) {
        for (let i = 0; i < items.length; i++) {
            if (items[i].id === id) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Devuelve el item con ese id.
     *
     * @param {string} id Identificador del item.
     * @returns {Object|null} Item, o null si no existe.
     */
    function findItem(id) {
        const index = indexOf(id);
        return index === -1 ? null : items[index];
    }

    /**
     * Muestra u oculta los paneles segun la pestana activa.
     *
     * @returns {void}
     */
    function applyPanelVisibility() {
        if (!panelContainer) {
            return;
        }
        for (let i = 0; i < panelContainer.children.length; i++) {
            const panel = panelContainer.children[i];
            if (panel.dataset.tabPanel === undefined) {
                continue;
            }
            panel.hidden = panel.dataset.tabPanel !== activeId;
        }
    }

    /**
     * Construye el boton de una pestana.
     *
     * @param {Object} item Entrada de la lista de items.
     * @returns {HTMLButtonElement} Boton creado.
     */
    function renderButton(item) {
        const button = document.createElement('button');
        button.type = 'button';
        button.className = 'tab-button';
        button.setAttribute('role', 'tab');
        button.setAttribute('aria-selected', 'false');
        button.tabIndex = -1;
        button.dataset.tabId = item.id;

        const title = document.createElement('span');
        title.className = 'tab-title';
        title.textContent = item.title;
        button.appendChild(title);

        if (item.closable) {
            const close = document.createElement('span');
            close.className = 'tab-close';
            close.setAttribute('aria-hidden', 'true');
            close.textContent = '\u00d7';
            close.addEventListener('click', (event) => {
                event.stopPropagation();
                requestClose(item.id);
            });
            button.appendChild(close);
        }

        button.addEventListener('click', () => {
            setActive(item.id);
        });

        return button;
    }

    /**
     * Activa una pestana.
     *
     * @param {string} id Identificador del item.
     * @returns {boolean} true si el id existe y queda activo.
     */
    function setActive(id) {
        const item = findItem(id);
        if (!item) {
            return false;
        }
        if (id === activeId) {
            return true;
        }
        activeId = id;
        for (let i = 0; i < items.length; i++) {
            const current = items[i];
            const isActive = current.id === id;
            current.button.classList.toggle('active', isActive);
            current.button.setAttribute('aria-selected', isActive ? 'true' : 'false');
            current.button.tabIndex = isActive ? 0 : -1;
        }
        applyPanelVisibility();
        if (onActivate) {
            onActivate(id);
        }
        return true;
    }

    /**
     * Devuelve el id de la pestana activa.
     *
     * @returns {string|null} Identificador, o null si no hay ninguna activa.
     */
    function getActive() {
        return activeId;
    }

    /**
     * Anade una pestana al final de la barra.
     *
     * @param {{id: string, title: string, closable: boolean}} item Datos del item.
     * @returns {HTMLButtonElement|null} Boton creado, o null si el id ya existe.
     */
    function addItem(item) {
        if (!item || !item.id || indexOf(item.id) !== -1) {
            return null;
        }
        const entry = {
            id: item.id,
            title: item.title === undefined ? String(item.id) : String(item.title),
            closable: item.closable === true,
            dirty: false,
            dirtyMark: null,
            button: null
        };
        entry.button = renderButton(entry);
        const wasEmpty = items.length === 0;
        items.push(entry);
        container.appendChild(entry.button);
        if (wasEmpty) {
            entry.button.tabIndex = 0;
        }
        return entry.button;
    }

    /**
     * Marca o desmarca una pestana como modificada.
     *
     * @param {string} id Identificador del item.
     * @param {boolean} flag true para marcar, false para limpiar.
     * @returns {void}
     */
    function setDirty(id, flag) {
        const item = findItem(id);
        if (!item) {
            return;
        }
        item.dirty = flag === true;
        if (item.dirty) {
            if (!item.dirtyMark) {
                const mark = document.createElement('span');
                mark.className = 'tab-dirty';
                mark.setAttribute('aria-hidden', 'true');
                mark.textContent = '\u2022';
                item.dirtyMark = mark;
                item.button.appendChild(mark);
            }
        } else if (item.dirtyMark) {
            if (item.dirtyMark.parentNode) {
                item.dirtyMark.parentNode.removeChild(item.dirtyMark);
            }
            item.dirtyMark = null;
        }
        item.button.classList.toggle('is-dirty', item.dirty);
    }

    /**
     * Quita una pestana de la barra. Si era la activa, activa la siguiente
     * y, si no queda ninguna, avisa con `onActivate(null)`.
     *
     * @param {string} id Identificador del item.
     * @returns {boolean} true si se ha eliminado.
     */
    function removeItem(id) {
        const index = indexOf(id);
        if (index === -1) {
            return false;
        }
        const item = items[index];
        if (item.button.parentNode) {
            item.button.parentNode.removeChild(item.button);
        }
        items.splice(index, 1);

        if (activeId === id) {
            activeId = null;
            const next = items[index] || items[index - 1] || null;
            if (next) {
                setActive(next.id);
            } else if (onActivate) {
                onActivate(null);
            }
        }
        return true;
    }

    /**
     * Gestiona la peticion de cierre. `onClose` puede cancelar devolviendo
     * false; en cualquier otro caso el componente elimina la pestana.
     *
     * @param {string} id Identificador del item.
     * @returns {void}
     */
    function requestClose(id) {
        if (onClose) {
            const result = onClose(id);
            if (result === false) {
                return;
            }
        }
        removeItem(id);
    }

    /**
     * Navegacion por teclado entre pestanas.
     *
     * @param {KeyboardEvent} event Evento de teclado.
     * @returns {void}
     */
    function onKeyDown(event) {
        const key = event.key;
        const isArrow = key === 'ArrowLeft' || key === 'ArrowRight';
        if (!isArrow && key !== 'Home' && key !== 'End') {
            return;
        }
        if (items.length === 0) {
            return;
        }
        let index = activeId === null ? 0 : indexOf(activeId);
        if (key === 'ArrowLeft') {
            index = (index - 1 + items.length) % items.length;
        } else if (key === 'ArrowRight') {
            index = (index + 1) % items.length;
        } else if (key === 'Home') {
            index = 0;
        } else {
            index = items.length - 1;
        }
        event.preventDefault();
        setActive(items[index].id);
        items[index].button.focus();
    }

    container.addEventListener('keydown', onKeyDown);

    /**
     * Deshace lo que el componente ha creado en el contenedor.
     *
     * @returns {void}
     */
    function destroy() {
        if (destroyed) {
            return;
        }
        destroyed = true;
        container.removeEventListener('keydown', onKeyDown);
        for (let i = 0; i < items.length; i++) {
            if (items[i].button.parentNode) {
                items[i].button.parentNode.removeChild(items[i].button);
            }
        }
        items.length = 0;
        activeId = null;
        container.classList.remove('tabbar');
        container.removeAttribute('role');
    }

    if (Array.isArray(config.items)) {
        for (let i = 0; i < config.items.length; i++) {
            addItem(config.items[i]);
        }
    }

    return {
        setActive,
        getActive,
        addItem,
        removeItem,
        setDirty,
        destroy
    };
}