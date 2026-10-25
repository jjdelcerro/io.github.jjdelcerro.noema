/**
 * Workspace: contenedor de addons.
 *
 * No conoce en tiempo de construccion que addons existen. Cada addon se
 * registra a si mismo con register y el workspace le crea su pestana segun su
 * campo order. El workspace no monta el addon hasta que se llama a mount.
 *
 * El workspace NO agrega el estado de sus addons: cada addon persiste el suyo
 * en su seccion de storage. Aqui solo se recuerda que pestana estaba activa.
 *
 * El contenedor cedido puede llegar ya poblado (una barra y un cuerpo de
 * paneles escritos en el HTML). En ese caso se reutilizan en vez de crear
 * otros, y cada addon recibe el panel que ya exista con su data-tab-panel.
 *
 * Emite dos eventos en window, con el prefijo del bus de aplicacion (ยง9):
 *
 * - noema:addon-registered  detail { addonId }
 * - noema:addon-activated   detail { addonId }
 */

import { createTabs } from '../ui-tabs.js';
import * as storage from '../storage.js';

const SECTION = 'workspace';
const FIELD = 'activeTab';

/**
 * Localiza la barra y el cuerpo de paneles dentro del contenedor, o los crea
 * si el contenedor llega vacio.
 *
 * @param {HTMLElement} container contenedor cedido por main
 * @returns {{tabbar: HTMLElement, panelHost: HTMLElement}}
 */
function resolveHosts(container) {
    let tabbar = container.querySelector('.workspace-tabbar, .area-tabbar');
    let panelHost = container.querySelector('.workspace-panels, .area-body');

    if (!panelHost) {
        panelHost = document.createElement('div');
        panelHost.className = 'workspace-panels';
        container.appendChild(panelHost);
    }
    if (!tabbar) {
        tabbar = document.createElement('div');
        tabbar.className = 'workspace-tabbar';
        container.insertBefore(tabbar, panelHost);
    }
    return { tabbar: tabbar, panelHost: panelHost };
}

/**
 * Crea un workspace sobre un contenedor.
 *
 * @param {{container: HTMLElement}} options contenedor cedido por main
 * @returns {Object} API publica del workspace
 */
export function createWorkspace(options) {
    const container = options ? options.container : null;
    if (!container) {
        throw new Error('createWorkspace: falta container');
    }

    const hosts = resolveHosts(container);
    const tabbar = hosts.tabbar;
    const panelHost = hosts.panelHost;

    const addons = [];
    let tabs = null;
    let mounted = false;
    let activeId = null;
    let pendingActivation = null;

    rebuildTabs();

    /**
     * Devuelve la entrada de registro del addon con ese id.
     *
     * @param {string} id identificador del addon
     * @returns {Object|null} entrada, o null si no esta registrado
     */
    function findEntry(id) {
        for (let i = 0; i < addons.length; i++) {
            if (addons[i].id === id) {
                return addons[i];
            }
        }
        return null;
    }

    /**
     * Devuelve el panel del addon, sin crearlo.
     *
     * @param {string} id identificador del addon
     * @returns {HTMLElement|null} panel, o null si no hay ninguno
     */
    function findPanel(id) {
        for (let i = 0; i < panelHost.children.length; i++) {
            if (panelHost.children[i].dataset.tabPanel === id) {
                return panelHost.children[i];
            }
        }
        return null;
    }

    /**
     * Devuelve el panel del addon creandolo si no existe.
     *
     * @param {string} id identificador del addon
     * @returns {HTMLElement} panel listo para ceder al addon
     */
    function ensurePanel(id) {
        const existing = findPanel(id);
        if (existing) {
            return existing;
        }
        const panel = document.createElement('div');
        panel.className = 'workspace-panel';
        panel.dataset.tabPanel = id;
        panel.hidden = true;
        panelHost.appendChild(panel);
        return panel;
    }

    /**
     * Reconstruye la barra respetando el orden de los addons. Se usa cada vez
     * que se registra uno, porque createTabs anade al final y no reordena.
     *
     * @returns {void}
     */
    function rebuildTabs() {
        const previous = tabs ? tabs.getActive() : null;
        if (tabs) {
            tabs.destroy();
        }
        tabs = createTabs({
            container: tabbar,
            panelContainer: panelHost,
            items: addons.map((entry) => ({
                id: entry.id,
                title: entry.title === undefined ? entry.id : entry.title,
                closable: false
            })),
            onActivate: handleActivate
        });
        if (previous && findEntry(previous)) {
            tabs.setActive(previous);
        }
    }

    /**
     * Registra un addon. Crea su pestana y lo deja sin montar.
     *
     * @param {Object} addon addon segun el contrato de la seccion 8.1
     * @returns {boolean} true si se ha registrado
     */
    function register(addon) {
        if (!addon || !addon.id) {
            return false;
        }
        if (findEntry(addon.id)) {
            return false;
        }
        const order = typeof addon.order === 'number' ? addon.order : addons.length;
        addons.push({ id: addon.id, order: order, title: addon.title, addon: addon });
        addons.sort((left, right) => left.order - right.order);

        rebuildTabs();

        window.dispatchEvent(new CustomEvent('noema:addon-registered', {
            detail: { addonId: addon.id }
        }));
        return true;
    }

    /**
     * Monta el workspace y todos los addons registrados. Los paneles ya
     * escritos en el HTML se reutilizan; los que falten se crean.
     *
     * @returns {Promise<void>} resuelve cuando todos los addons han montado
     */
    function mount() {
        if (mounted) {
            return Promise.resolve();
        }
        mounted = true;

        const pending = [];
        for (let i = 0; i < addons.length; i++) {
            const entry = addons[i];
            pending.push(Promise.resolve(entry.addon.mount(ensurePanel(entry.id))));
        }

        const stored = (storage.read(SECTION) || {})[FIELD];
        const initial = (pendingActivation && findEntry(pendingActivation))
                ? pendingActivation
                : ((stored && findEntry(stored)) ? stored : (addons.length > 0 ? addons[0].id : null));
        pendingActivation = null;

        return Promise.all(pending).then(() => {
            if (initial) {
                tabs.setActive(initial);
            }
        });
    }

    /**
     * Activa una pestana. Consulta isDirty/confirmLeave del addon que se
     * abandona y aborta si el usuario cancela.
     *
     * @param {string} id identificador del addon
     * @returns {boolean} true si queda activo
     */
    function activate(id) {
        if (!findEntry(id)) {
            return false;
        }
        if (!mounted) {
            pendingActivation = id;
            return true;
        }
        const current = activeId ? findEntry(activeId) : null;
        if (current && id !== activeId) {
            const dirty = typeof current.addon.isDirty === 'function' && current.addon.isDirty();
            if (dirty) {
                const allowed = (typeof current.addon.confirmLeave === 'function')
                        ? current.addon.confirmLeave() !== false
                        : window.confirm('Hay cambios sin guardar. Descartarlos?');
                if (!allowed) {
                    return false;
                }
            }
        }
        return tabs.setActive(id);
    }

    /**
     * Devuelve el id de la pestana activa.
     *
     * @returns {string|null}
     */
    function getActive() {
        return activeId;
    }

    /**
     * Estado propio del workspace. No agrega el de los addons.
     *
     * @returns {{activeTab: string|null}}
     */
    function getState() {
        return { activeTab: activeId };
    }

    /**
     * Restaura la pestana activa. Si el workspace aun no esta montado, la
     * activacion queda pendiente hasta mount.
     *
     * @param {{activeTab: string}} state estado previo
     * @returns {void}
     */
    function setState(state) {
        if (!state || !state.activeTab) {
            return;
        }
        if (findEntry(state.activeTab)) {
            activate(state.activeTab);
        } else {
            pendingActivation = state.activeTab;
        }
    }

    /**
     * Desmonta todos los addons en orden inverso y libera el contenedor.
     *
     * @returns {void}
     */
    function unmount() {
        for (let i = addons.length - 1; i >= 0; i--) {
            const entry = addons[i];
            if (typeof entry.addon.unmount === 'function') {
                entry.addon.unmount();
            }
        }
        if (tabs) {
            tabs.destroy();
            tabs = null;
        }
        addons.length = 0;
        activeId = null;
        mounted = false;
    }

    /**
     * Efecto lateral de la activacion: ganchos de los addons, persistencia y
     * evento. La presentacion ya la ha aplicado ui-tabs.
     *
     * @param {string|null} id identificador del addon activo
     * @returns {void}
     */
    function handleActivate(id) {
        const previous = activeId;
        activeId = id;

        if (previous && previous !== id) {
            const leaving = findEntry(previous);
            if (leaving && typeof leaving.addon.deactivate === 'function') {
                leaving.addon.deactivate();
            }
        }

        if (id) {
            const entering = findEntry(id);
            if (entering && typeof entering.addon.activate === 'function') {
                entering.addon.activate();
            }
            storage.update(SECTION, { activeTab: id });
            window.dispatchEvent(new CustomEvent('noema:addon-activated', {
                detail: { addonId: id }
            }));
        }
    }

    return {
        register: register,
        mount: mount,
        activate: activate,
        getActive: getActive,
        getState: getState,
        setState: setState,
        unmount: unmount
    };
}