/**
 * Punto de entrada principal de la SPA Noema Web.
 */

import { initChatUI, switchTerminal } from './chat-ui.js';
import { initConfigUI } from './config-ui.js';
import { initWorkspaceUI } from './workspace-ui.js';
import { initSplitters, refreshSplitters } from './splitters.js';
import { initEditorUI, resizeEditor, hasDirtySessions, confirmLeaveWorkspace } from './editor-ui.js';
import { initAreaTabs, registerAreaView } from './area-tabs.js';
import { initNotesUI } from './notes-ui.js';
import { initNoteEditor } from './note-editor.js';
import { initNotesSearch } from './notes-search.js';

const STORAGE_KEY = 'noema_terminal_id';
const SIDEBAR_STORAGE_KEY = 'noema_sidebar_collapsed';
const WORKSPACE_STORAGE_KEY = 'noema_workspace_collapsed';
const DEFAULT_TERMINAL_ID = 'default';

document.addEventListener('DOMContentLoaded', () => {
    const terminalInput = document.getElementById('terminal-id');

    // 1. Recuperar terminalId guardado o usar valor por defecto
    const savedTerminalId = localStorage.getItem(STORAGE_KEY) || DEFAULT_TERMINAL_ID;

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

    // 2. Inicializar subsistemas
    initSidebarToggle();
    initWorkspaceToggle();
    initChatUI(savedTerminalId);
    initConfigUI();
    initWorkspaceUI();
    initEditorUI();
    initSplitters();
    initAreaTabs();
    registerAreaView('workspace', {
        isDirty: hasDirtySessions,
        confirmLeave: confirmLeaveWorkspace
    });
    initNoteEditor();
    initNotesUI();
    initNotesSearch();
});

function handleTerminalChange(rawId) {
    const cleanId = rawId.trim();
    if (!cleanId) return;

    localStorage.setItem(STORAGE_KEY, cleanId);
    switchTerminal(cleanId);
}

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

    const collapsed = localStorage.getItem(WORKSPACE_STORAGE_KEY) !== 'false';
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
    localStorage.setItem(WORKSPACE_STORAGE_KEY, String(collapsed));
    const expanded = String(!collapsed);
    const label = collapsed ? 'Mostrar panel de editor' : 'Ocultar panel de editor';
    btnToggle.setAttribute('aria-expanded', expanded);
    btnToggle.setAttribute('aria-label', label);
    btnToggle.setAttribute('title', label);

    if (!collapsed) {
        requestAnimationFrame(() => {
            refreshSplitters();
        });
    }
}