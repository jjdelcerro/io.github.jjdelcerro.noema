/**
 * Punto de entrada principal de la SPA Noema Web.
 */

import { initChatUI, switchTerminal } from './chat-ui.js';
import { initConfigUI } from './config-ui.js';

const STORAGE_KEY = 'noema_terminal_id';
const SIDEBAR_STORAGE_KEY = 'noema_sidebar_collapsed';
const DEFAULT_TERMINAL_ID = 'default';

document.addEventListener('DOMContentLoaded', () => {
    const terminalInput = document.getElementById('terminal-id');

    // 1. Recuperar terminalId guardado o usar valor por defecto
    const savedTerminalId = localStorage.getItem(STORAGE_KEY) || DEFAULT_TERMINAL_ID;

    if (terminalInput) {
        terminalInput.value = savedTerminalId;

        // Escuchar cambios en el campo terminalId
        terminalInput.addEventListener('change', () => {
            handleTerminalChange(terminalInput.value);
        });

        terminalInput.addEventListener('keydown', (e) => {
            if (e.key === 'Enter') {
                e.preventDefault();
                terminalInput.blur(); // Quita el foco y fuerza el evento change
            }
        });
    }

    // 2. Inicializar subsistemas
    initSidebarToggle();
    initChatUI(savedTerminalId);
    initConfigUI();
});

/**
 * Guarda el nuevo terminalId y notifica a la UI de chat para cambiar la sesión.
 * 
 * @param {string} rawId - Valor bruto introducido en el input.
 */
function handleTerminalChange(rawId) {
    const cleanId = rawId.trim();
    if (!cleanId) return;

    localStorage.setItem(STORAGE_KEY, cleanId);
    switchTerminal(cleanId);
}

/**
 * Configura el plegado/desplegado del panel lateral (sidebar).
 */
function initSidebarToggle() {
    const shell = document.querySelector('.app-shell');
    const btnCollapse = document.getElementById('btn-collapse-sidebar');
    const btnExpand = document.getElementById('btn-expand-sidebar');
    if (!shell || !btnCollapse || !btnExpand) {
        return;
    }

    const collapsed = localStorage.getItem(SIDEBAR_STORAGE_KEY) === 'true';
    applySidebarState(shell, btnCollapse, btnExpand, collapsed);

    btnCollapse.addEventListener('click', () => {
        localStorage.setItem(SIDEBAR_STORAGE_KEY, 'true');
        applySidebarState(shell, btnCollapse, btnExpand, true);
    });

    btnExpand.addEventListener('click', () => {
        localStorage.setItem(SIDEBAR_STORAGE_KEY, 'false');
        applySidebarState(shell, btnCollapse, btnExpand, false);
    });
}

/**
 * Aplica el estado de plegado del sidebar y sincroniza los atributos ARIA.
 * 
 * @param {HTMLElement} shell - Contenedor raíz .app-shell.
 * @param {HTMLElement} btnCollapse - Botón que pliega el sidebar.
 * @param {HTMLElement} btnExpand - Botón que lo despliega.
 * @param {boolean} collapsed - true si el sidebar debe quedar plegado.
 */
function applySidebarState(shell, btnCollapse, btnExpand, collapsed) {
    shell.classList.toggle('sidebar-collapsed', collapsed);
    const expanded = String(!collapsed);
    btnCollapse.setAttribute('aria-expanded', expanded);
    btnExpand.setAttribute('aria-expanded', expanded);
}
