/**
 * Addon del explorador de ficheros.
 *
 * Compone las dos mitades que hasta ahora se conocian entre si: el arbol
 * (workspace-ui) y el editor (editor-ui). Rompe el acoplamiento prohibido en
 * la seccion 11.2: el arbol ya no importa openFile, solo informa de la
 * seleccion y es este addon quien decide abrir el fichero.
 *
 * Estado: el addon no agrega estado. Las pestanas abiertas y el fichero activo
 * los persiste editor-ui bajo la seccion fsexplorer de storage.
 *
 * DOM: igual que en notas, arbol y editor resuelven sus nodos por id global.
 * mount comprueba que el panel cedido contiene el arbol y delega en las dos
 * inicializaciones.
 */

import { initWorkspaceUI } from './tree/workspace-ui.js';
import {
    initEditorUI,
    openFile,
    hasDirtySessions,
    confirmLeaveWorkspace,
    resizeEditor,
    handleEditorOpen,
    handleEditorApply,
    handleEditorSetupDone,
    handleEditorAddAnnotation,
    handleEditorClearAnnotations
} from './editor/editor-ui.js';
import { subscribeEditorEvents } from './editor/editor-api.js';
import { renderFsExplorer } from './fsexplorer-markup.js';

/**
 * Crea el addon del explorador.
 *
 * @returns {Object} addon segun el contrato de la seccion 8.1
 */
export function createFsExplorerAddon() {
    let ready = false;
    let offEditorEvents = null;

    /**
     * Recibe la seleccion del arbol y decide abrirla en el editor.
     *
     * @param {{path: string, name: string}} selection nodo seleccionado
     * @returns {Promise<void>}
     */
    function handleSelection(selection) {
        if (!selection || !selection.path) {
            return Promise.resolve();
        }
        return openFile(selection.path);
    }

    return {
        id: 'fsexplorer',
        title: 'Explorador',
        icon: null,
        order: 10,

        /**
         * Arranca el arbol y el editor sobre el panel cedido.
         *
         * @param {HTMLElement} panel contenedor del addon
         * @returns {Promise<void>} resuelve cuando el arbol esta cargado
         */
        mount(panel) {
            if (!panel) {
                return Promise.reject(new Error('fsexplorer addon: falta panel'));
            }
            renderFsExplorer(panel);
            if (!panel.querySelector('#workspace-tree')) {
                return Promise.reject(
                    new Error('fsexplorer addon: el markup no genero el arbol')
                );
            }
            initEditorUI();
            offEditorEvents = subscribeEditorEvents({
                editor_open: handleEditorOpen,
                editor_apply: handleEditorApply,
                editor_setup_done: handleEditorSetupDone,
                editor_add_annotation: handleEditorAddAnnotation,
                editor_clear_annotations: handleEditorClearAnnotations
            });
            return Promise.resolve(initWorkspaceUI({
                onOpenFile: handleSelection
            })).then(() => {
                ready = true;
            });
        },

        /**
         * Suelta el addon. Arbol y editor no exponen teardown todavia.
         *
         * @returns {void}
         */
        unmount() {
            if (offEditorEvents) {
                offEditorEvents();
                offEditorEvents = null;
            }
            ready = false;
        },

        /**
         * Reajusta Ace al hacerse visible el panel.
         *
         * @returns {void}
         */
        activate() {
            if (ready) {
                resizeEditor();
            }
        },

        /**
         * El editor conserva sus sesiones mientras esta oculto.
         *
         * @returns {void}
         */
        deactivate() {
        },

        /**
         * No hay estado local propio: lo persiste editor-ui.
         *
         * @returns {null}
         */
        getState() {
            return null;
        },

        /**
         * Simetrico de getState: nada que restaurar.
         *
         * @returns {void}
         */
        setState() {
        },

        /**
         * @returns {boolean} true si hay ficheros con cambios sin guardar
         */
        isDirty() {
            return ready ? hasDirtySessions() : false;
        },

        /**
         * @returns {boolean} false para abortar el cambio de pestana
         */
        confirmLeave() {
            return ready ? confirmLeaveWorkspace() : true;
        }
    };
}