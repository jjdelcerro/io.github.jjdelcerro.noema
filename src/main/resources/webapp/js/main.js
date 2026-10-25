/**
 * Punto de entrada principal de la SPA Noema Web.
 *
 * Composition root: junta las piezas y les da el pistoletazo de salida. No
 * contiene logica de ningun componente.
 *
 * El area de trabajo ya no la monta nadie a mano: main crea el workspace,
 * registra los addons y lo monta. Es el unico sitio que conoce la lista de
 * addons existentes. Cada addon arranca sus propias piezas.
 */

import { initChatUI, switchTerminal } from './chat/chat-ui.js';
import { initConfigUI } from './config/config-ui.js';
import { initSplitters } from './splitters.js';
import { initUIChrome } from './ui-chrome.js';
import { initUIStateEvents } from './ui-state.js';
import { createWorkspace } from './workspace/workspace.js';
import { createFsExplorerAddon } from './workspace/addons/fsexplorer/fsexplorer-addon.js';
import { createNotesAddon } from './workspace/addons/notes/notes-addon.js';
import * as storage from './storage.js';

const DEFAULT_TERMINAL_ID = 'default';

document.addEventListener('DOMContentLoaded', () => {
    const terminalInput = document.getElementById('terminal-id');

    // 1. Recuperar terminalId guardado o usar valor por defecto
    const appState = storage.read('app') || {};
    const savedTerminalId = appState.terminalId || DEFAULT_TERMINAL_ID;

    if (terminalInput) {
        terminalInput.value = savedTerminalId;

        terminalInput.addEventListener('change', () => {
            handleTerminalChange(terminalInput.value);
        });

        terminalInput.addEventListener('keydown', (e) => {
            if (e.key === 'Enter') {
                e.preventDefault();
                terminalInput.blur();
            }
        });
    }

    // 2. Inicializar subsistemas.
    // El orden importa: los splitters se buscan por id en el documento y
    // setupSplitter no reintenta, asi que solo se enganchan los que ya existen
    // cuando corre initSplitters. Los paneles de los addons se construyen en
    // mount(), de modo que initWorkspace() tiene que ir antes.
    initUIChrome();
    initUIStateEvents();
    initChatUI(savedTerminalId);
    initConfigUI();
    initWorkspace();
    initSplitters();

    // 3. Liberar el estado pendiente al cerrar o recargar
    window.addEventListener('pagehide', () => {
        storage.flush();
    });
});

/**
 * Crea el workspace sobre el area de trabajo, registra los addons y lo monta.
 *
 * @returns {void}
 */
function initWorkspace() {
    const workArea = document.getElementById('work-area');
    if (!workArea) {
        return;
    }
    const workspace = createWorkspace({ container: workArea });
    workspace.register(createFsExplorerAddon());
    workspace.register(createNotesAddon());
    workspace.mount();
}

function handleTerminalChange(rawId) {
    const cleanId = rawId.trim();
    if (!cleanId) return;

    storage.update('app', { terminalId: cleanId });
    switchTerminal(cleanId);
}