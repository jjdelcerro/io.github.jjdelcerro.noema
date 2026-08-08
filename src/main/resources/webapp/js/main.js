 
/**
 * Punto de entrada principal de la SPA Noema Web.
 */

import { initChatUI, switchTerminal } from './chat-ui.js';
import { initConfigUI } from './config-ui.js';

const STORAGE_KEY = 'noema_terminal_id';
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