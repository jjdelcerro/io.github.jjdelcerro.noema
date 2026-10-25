/**
 * API del editor: endpoints REST de analisis y formateo, y suscripcion a los
 * eventos SSE que el servidor dirige al editor. Es el unico sitio donde se
 * declaran los nombres de esos eventos (seccion 6).
 *
 * No importa editor-ui.js: la suscripcion recibe los handlers ya resueltos,
 * para no crear un ciclo con editor-actions.js.
 */

import { API_BASE, on } from '../../../../api.js';

/**
 * Eventos SSE que pertenecen al editor. El chat no los conoce.
 */
export const EDITOR_EVENTS = [
  'editor_open',
  'editor_apply',
  'editor_setup_done',
  'editor_add_annotation',
  'editor_clear_annotations'
];

/**
 * Suscribe los handlers del editor a los eventos SSE.
 *
 * @param {Object<string, Function>} handlers - Mapa evento -> handler.
 * @returns {Function} funcion de baja de todas las suscripciones
 */
export function subscribeEditorEvents(handlers) {
  const offs = [];
  EDITOR_EVENTS.forEach((eventName) => {
    const handler = handlers ? handlers[eventName] : null;
    if (typeof handler === 'function') {
      offs.push(on(eventName, handler));
    }
  });
  return () => {
    offs.forEach((off) => off());
  };
}

/**
 * Solicita diagnosticos para un fichero Java concreto.
 *
 * @param {string} filePath - Ruta absoluta del fichero.
 * @returns {Promise<{annotations: Array<{row: number, column: number, text: string, type: string}>}>}
 */
export async function postEditorDiagnostics(filePath) {
  const response = await fetch(`${API_BASE}/api/editor/diagnostics`, {
    method: 'POST',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify({file: filePath})
  });
  if (!response.ok) {
    throw new Error(`Error obteniendo diagnósticos (${response.status})`);
  }
  return await response.json();
}

/**
 * Formatea el contenido de un fichero. El contenido va en el body como
 * texto plano (sin JSON, sin escapes) y la respuesta es el contenido
 * formateado tambien como texto plano.
 *
 * @param {string} filePath - Ruta del fichero (informativa).
 * @param {string} ext - Extension sin punto (ej: 'java').
 * @param {string} content - Contenido crudo del buffer.
 * @returns {Promise<string>} Contenido formateado.
 */
export async function postEditorFormat(filePath, ext, content) {
  const url = new URL(`${API_BASE}/api/editor/format`);
  if (ext) {
    url.searchParams.set('ext', ext);
  }
  const response = await fetch(url.toString(), {
    method: 'POST',
    headers: {'Content-Type': 'text/plain; charset=UTF-8'},
    body: content
  });
  if (!response.ok) {
    throw new Error(`Error formateando (${response.status})`);
  }
  return await response.text();
}

/**
 * Ajusta/organiza los imports de un fichero Java en disco.
 *
 * @param {string} filePath - Ruta absoluta del fichero.
 * @returns {Promise<{status: string}>}
 */
export async function postEditorFiximports(filePath) {
  const response = await fetch(`${API_BASE}/api/editor/fiximports`, {
    method: 'POST',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify({file: filePath})
  });
  if (!response.ok) {
    throw new Error(`Error ajustando imports (${response.status})`);
  }
  return await response.json();
}

/**
 * Apertura de proyecto en el servidor para un terminal.
 *
 * @param {string} terminalId - Terminal destino.
 * @param {string} path - Ruta del proyecto.
 * @returns {Promise<Object>} respuesta del backend
 */
export async function postEditorSetupProject(terminalId, path) {
  const response = await fetch(`${API_BASE}/api/editor/setupproject`, {
    method: 'POST',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify({terminalId, path})
  });
  if (!response.ok) {
    let msg = `HTTP ${response.status}`;
    try {
      const data = await response.json();
      if (data.error) msg = data.error;
    } catch (e) { /* respuesta sin JSON */ }
    throw new Error(msg);
  }
  return await response.json();
}