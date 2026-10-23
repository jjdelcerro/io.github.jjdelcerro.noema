/**
 * Estado de la interfaz: push de componentes al servidor.
 *
 * Cada componente (editor, notas) viaja por su cuenta: el servidor aplica solo
 * los que llegan en cada POST y conserva el resto. Los eventos de cursor y de
 * seleccion son muy frecuentes, asi que el envio se agrupa con un unico
 * debounce y se descarta cuando el snapshot no ha cambiado.
 *
 * Este modulo no importa nada de la UI: evita dependencias circulares.
 */

import { postUIState } from './api.js';

const DEBOUNCE_MS = 350;
const TERMINAL_STORAGE_KEY = 'noema_terminal_id';
const ENDPOINT = '/api/ui/state';

/** Componentes pendientes de enviar, indexados por nombre. */
const pending = {};

/** Ultimo snapshot confirmado por componente, serializado. */
const lastSent = {};

let flushTimer = null;

/**
 * Devuelve el identificador del terminal actual.
 *
 * @returns {string} Identificador del terminal.
 */
export function getTerminalId() {
  return localStorage.getItem(TERMINAL_STORAGE_KEY) || 'default';
}

/**
 * Encola el estado de un componente.
 *
 * @param {string} component - Nombre del componente ('editor' o 'notes').
 * @param {object|null} snapshot - Estado completo del componente.
 * @param {{immediate?: boolean}} [options] - immediate fuerza el envio sin debounce.
 */
export function pushComponentState(component, snapshot, options = {}) {
  const serialized = JSON.stringify(snapshot === undefined ? null : snapshot);
  if (serialized === lastSent[component]) {
    return;
  }
  pending[component] = serialized;
  if (options.immediate) {
    flushComponentState();
  } else {
    scheduleFlush();
  }
}

/**
 * Programa el envio agrupado.
 */
function scheduleFlush() {
  if (flushTimer !== null) {
    clearTimeout(flushTimer);
  }
  flushTimer = setTimeout(() => {
    flushTimer = null;
    flushComponentState();
  }, DEBOUNCE_MS);
}

/**
 * Envia los componentes pendientes. Un fallo de red se traga: cada snapshot es
 * completo, de modo que el siguiente cambio vuelve a mandar el estado entero.
 */
async function flushComponentState() {
  if (flushTimer !== null) {
    clearTimeout(flushTimer);
    flushTimer = null;
  }
  const components = takePending();
  if (components === null) {
    return;
  }
  try {
    await postUIState({ terminalId: getTerminalId(), components });
    Object.keys(components).forEach((name) => {
      lastSent[name] = JSON.stringify(components[name]);
    });
  } catch (error) {
    // Sin ruido en consola: el siguiente cambio reenviara el estado completo.
  }
}

/**
 * Extrae y vacia los componentes pendientes.
 *
 * @returns {object|null} Objeto de componentes o null si no hay nada pendiente.
 */
function takePending() {
  const names = Object.keys(pending);
  if (names.length === 0) {
    return null;
  }
  const components = {};
  names.forEach((name) => {
    components[name] = JSON.parse(pending[name]);
    delete pending[name];
  });
  return components;
}

/**
 * Engancha el envio final al cerrar o recargar la pagina, para no perder el
 * ultimo estado pendiente.
 */
export function initUIStateEvents() {
  window.addEventListener('beforeunload', () => {
    const components = takePending();
    if (components === null) {
      return;
    }
    const payload = JSON.stringify({ terminalId: getTerminalId(), components });
    const blob = new Blob([payload], { type: 'application/json' });
    navigator.sendBeacon(ENDPOINT, blob);
  });
}
