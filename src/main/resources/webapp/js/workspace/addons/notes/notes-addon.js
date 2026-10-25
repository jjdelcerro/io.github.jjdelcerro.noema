/**
 * Addon de notas.
 *
 * Agrupa bajo un unico contrato de addon las tres piezas que hasta ahora
 * arrancaban por separado desde main: el arbol (notes-ui), el editor
 * (note-editor) y la busqueda (notes-search).
 *
 * Estado: el addon no tiene estado propio que el workspace deba agregar. La
 * nota abierta, el cursor y las pestanas de documento se publican con
 * pushComponentState bajo el componente 'notes' de ui-state, que es quien
 * persiste. Por eso getState devuelve null.
 *
 * DOM: las tres piezas resuelven sus nodos por id global
 * (document.getElementById), asi que mount se limita a comprobar que el panel
 * cedido contiene el markup esperado. La relocalizacion real del DOM llega con
 * la reduccion de index.html.
 */

import { initNotesUI } from './notes-ui.js';
import {
    initNoteEditor,
    isDirty,
    confirmLeaveNote,
    refreshNoteLayout
} from './editor/note-editor.js';
import { initNotesSearch } from './search/notes-search.js';
import { renderNotes } from './notes-markup.js';

/**
 * Crea el addon de notas.
 *
 * @returns {Object} addon segun el contrato de la seccion 8.1
 */
export function createNotesAddon() {
    let ready = false;

    return {
        id: 'notes',
        title: 'Notas',
        icon: null,
        order: 20,

        /**
         * Arranca las tres piezas sobre el panel cedido.
         *
         * @param {HTMLElement} panel contenedor del addon
         * @returns {Promise<void>} resuelve cuando el arbol esta cargado
         */
        mount(panel) {
            if (!panel) {
                return Promise.reject(new Error('notes addon: falta panel'));
            }
            renderNotes(panel);
            if (!panel.querySelector('#notes-tree')) {
                return Promise.reject(
                    new Error('notes addon: el markup no genero el arbol de notas')
                );
            }
            initNoteEditor();
            initNotesSearch();
            return Promise.resolve(initNotesUI()).then(() => {
                ready = true;
            });
        },

        /**
         * Suelta el addon. Los listeners siguen enganchados: las tres piezas
         * no exponen un teardown todavia.
         *
         * @returns {void}
         */
        unmount() {
            ready = false;
        },

        /**
         * Reajusta el editor de nota al hacerse visible.
         *
         * @returns {void}
         */
        activate() {
            if (ready) {
                refreshNoteLayout();
            }
        },

        /**
         * El editor conserva su estado mientras esta oculto.
         *
         * @returns {void}
         */
        deactivate() {
        },

        /**
         * No hay estado local propio: ver la cabecera del modulo.
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
         * @returns {boolean} true si la nota abierta tiene cambios sin guardar
         */
        isDirty() {
            return ready ? isDirty() : false;
        },

        /**
         * @returns {boolean} false para abortar el cambio de pestana
         */
        confirmLeave() {
            return ready ? confirmLeaveNote() : true;
        }
    };
}