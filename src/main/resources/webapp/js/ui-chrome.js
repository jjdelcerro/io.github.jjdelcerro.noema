/**
 * Chrome del area lateral: colapso de la barra lateral y del panel de espacio de trabajo.
 *
 * Extraccion literal de las funciones que vivian en main.js. El area lateral
 * queda congelada, asi que este modulo no rediseña nada: solo saca de main el
 * acceso a .app-shell, a las clases, a los atributos aria-* y a storage.
 */

import { refreshSplitters } from './splitters.js';
import * as storage from './storage.js';

/**
 * Inicializa el chrome del area lateral.
 *
 * @returns {void}
 */
export function initUIChrome() {
    initSidebarToggle();
    initWorkspaceToggle();
    initStatusIndicator();
}

/**
 * Escucha el estado de conexion que publica el chat y lo pinta en la cabecera.
 *
 * El chat no conoce el DOM del header: solo anuncia su estado semantico con
 * `noema:status`. La traduccion a texto y a clase CSS vive aqui, que es el
 * modulo dueno del chrome.
 *
 * @returns {void}
 */
function initStatusIndicator() {
    const indicator = document.getElementById('status-indicator');
    const dot = indicator ? indicator.querySelector('.status-dot') : null;
    const text = document.getElementById('status-text');
    if (!dot || !text) {
        return;
    }

    const LABELS = {
        connected: 'Conectado',
        connecting: 'Conectando...',
        disconnected: 'Desconectado'
    };

    window.addEventListener('noema:status', (event) => {
        const status = (event.detail || {}).status || 'disconnected';
        dot.className = 'status-dot ' + status;
        text.textContent = LABELS[status] || LABELS.disconnected;
    });
}

function initSidebarToggle() {
    const shell = document.querySelector('.app-shell');
    const btnCollapse = document.getElementById('btn-collapse-sidebar');
    const btnExpand = document.getElementById('btn-expand-sidebar');
    if (!shell || !btnCollapse || !btnExpand) {
        return;
    }

    const collapsed = (storage.read('app') || {}).sidebarCollapsed === true;
    applySidebarState(shell, btnCollapse, btnExpand, collapsed);

    btnCollapse.addEventListener('click', () => {
        storage.update('app', { sidebarCollapsed: true });
        applySidebarState(shell, btnCollapse, btnExpand, true);
    });

    btnExpand.addEventListener('click', () => {
        storage.update('app', { sidebarCollapsed: false });
        applySidebarState(shell, btnCollapse, btnExpand, false);
    });
}

function applySidebarState(shell, btnCollapse, btnExpand, collapsed) {
    shell.classList.toggle('sidebar-collapsed', collapsed);
    const expanded = String(!collapsed);
    btnCollapse.setAttribute('aria-expanded', expanded);
    btnExpand.setAttribute('aria-expanded', expanded);
}

function initWorkspaceToggle() {
    const shell = document.querySelector('.app-shell');
    const btnToggle = document.getElementById('btn-toggle-workspace');
    if (!shell || !btnToggle) {
        return;
    }

    const collapsed = (storage.read('app') || {}).workspaceCollapsed !== false;
    applyWorkspaceState(shell, btnToggle, collapsed);

    btnToggle.addEventListener('click', () => {
        const nowCollapsed = !shell.classList.contains('workspace-collapsed');
        applyWorkspaceState(shell, btnToggle, nowCollapsed);
    });

    document.addEventListener('keydown', (e) => {
        if (e.altKey && (e.key === 'e' || e.key === 'E')) {
            e.preventDefault();
            btnToggle.click();
        }
    });
}

function applyWorkspaceState(shell, btnToggle, collapsed) {
    shell.classList.toggle('workspace-collapsed', collapsed);
    storage.update('app', { workspaceCollapsed: collapsed });
    const expanded = String(!collapsed);
    const label = collapsed ? 'Mostrar panel de espacio de trabajo' : 'Ocultar panel de espacio de trabajo';
    btnToggle.setAttribute('aria-expanded', expanded);
    btnToggle.setAttribute('aria-label', label);
    btnToggle.setAttribute('title', label);

    if (!collapsed) {
        requestAnimationFrame(() => {
            refreshSplitters();
        });
    }
}