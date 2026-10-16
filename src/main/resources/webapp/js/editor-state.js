/**
 * Módulo interno del editor: gestión del push de estado al servidor.
 *
 * El estado es efímero (snapshot del editor en un momento dado) y no
 * transaccional. Fallos silenciosos: el siguiente push lleva el estado
 * más reciente.
 *
 * No importa nunca de editor-ui.js (evitar dependencia circular).
 */

import { postEditorState } from './api.js';

const DEBOUNCE_MS = 350;

let lastSentState = null;
let pendingState = null;
let debounceTimer = null;

/**
 * Encola un snapshot para enviar. Con `immediate: true` se envía en el
 * acto (cancelando cualquier debounce pendiente). Sin él, se aplica un
 * debounce trailing: cada llamada reinicia el temporizador.
 *
 * @param {Object} state - Snapshot construido por editor-ui.js.
 * @param {{immediate?: boolean}} [options]
 */
export function pushState(state, options = {}) {
  pendingState = state;

  if (options.immediate) {
    flushState();
    return;
  }

  if (debounceTimer !== null) {
    clearTimeout(debounceTimer);
  }
  debounceTimer = setTimeout(flushState, DEBOUNCE_MS);
}

/**
 * Envía el snapshot pendiente (si lo hay y si difiere del último enviado).
 */
async function flushState() {
  if (debounceTimer !== null) {
    clearTimeout(debounceTimer);
    debounceTimer = null;
  }
  if (!pendingState) {
    return;
  }

  const toSend = pendingState;
  pendingState = null;

  if (lastSentState && statesEqual(toSend, lastSentState)) {
    return;
  }
  lastSentState = toSend;

  try {
    await postEditorState(toSend);
  } catch (error) {
    // Silencioso: el próximo push llevará el estado más reciente.
    console.warn('No se pudo enviar el estado del editor:', error);
  }
}

/**
 * Compara dos snapshots para evitar envíos redundantes. Solo se comparan
 * los campos que forman parte del contrato.
 */
function statesEqual(a, b) {
  if (a.activeFile !== b.activeFile) return false;
  if (a.cursorLine !== b.cursorLine) return false;
  if (a.selectionStartLine !== b.selectionStartLine) return false;
  if (a.selectionEndLine !== b.selectionEndLine) return false;
  if (a.selectionText !== b.selectionText) return false;   
  if (!a.openFiles || !b.openFiles) return false;
  if (a.openFiles.length !== b.openFiles.length) return false;
  for (let i = 0; i < a.openFiles.length; i++) {
    if (a.openFiles[i].path !== b.openFiles[i].path) return false;
    if (a.openFiles[i].dirty !== b.openFiles[i].dirty) return false;
  }
  return true;
}
/**
 * Registra el listener que envía el último snapshot pendiente al cerrar
 * la pestaña del navegador. Usa `sendBeacon`, que el navegador no cancela
 * al cerrar. Si falla, el cierre no se cancela.
 */
export function initStateBeforeUnload() {
  window.addEventListener('beforeunload', () => {
    if (!pendingState) {
      return;
    }
    try {
      const blob = new Blob([JSON.stringify(pendingState)], { type: 'application/json' });
      navigator.sendBeacon('/api/editor/state', blob);
      pendingState = null;
    } catch (error) {
      // Silencioso
    }
  });
}

