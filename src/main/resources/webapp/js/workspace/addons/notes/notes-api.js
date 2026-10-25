/**
 * API de notas: arbol, lectura, creacion, guardado y busqueda.
 * Declara en un solo sitio las rutas de `/api/notes/*`.
 */

import { API_BASE } from '../../../api.js';

/**
 * Llama al endpoint del arbol de notas. Sin path devuelve las raices; con
 * path, los hijos directos de esa carpeta.
 *
 * @param {string} [path] - Ruta de la carpeta a expandir.
 * @returns {Promise<Array<Object>>} entradas del nivel pedido
 */
export async function fetchNotesTree(path) {
  const url = path
    ? `${API_BASE}/api/notes/tree?path=${encodeURIComponent(path)}`
    : `${API_BASE}/api/notes/tree`;
  const response = await fetch(url);
  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`);
  }
  return await response.json();
}

/**
 * Lee una nota completa por su identificador.
 *
 * @param {string} id - Identificador de la nota.
 * @returns {Promise<Object>} nota
 */
export async function fetchNote(id) {
  const response = await fetch(`${API_BASE}/api/notes/${encodeURIComponent(id)}`);
  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`);
  }
  return await response.json();
}

/**
 * Crea una nota nueva.
 *
 * @param {{title: string, tags: Array<string>, content: string}} payload
 * @returns {Promise<Object>} nota creada
 */
export async function createNote(payload) {
  const response = await fetch(`${API_BASE}/api/notes`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload)
  });
  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`);
  }
  return await response.json();
}

/**
 * Guarda los cambios de una nota existente.
 *
 * @param {string} id - Identificador de la nota.
 * @param {{title: string, tags: Array<string>, content: string}} payload
 * @returns {Promise<Object>} nota actualizada
 */
export async function updateNote(id, payload) {
  const response = await fetch(`${API_BASE}/api/notes/${encodeURIComponent(id)}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload)
  });
  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`);
  }
  return await response.json();
}

/**
 * Busqueda paginada de notas.
 *
 * @param {{query: string, mode: string, root?: string, page: number, pageSize: number}} params
 * @returns {Promise<Object>} resultado paginado
 */
export async function searchNotes(params) {
  const query = new URLSearchParams();
  query.set('q', params.query);
  query.set('mode', params.mode);
  if (params.root) {
    query.set('root', params.root);
  }
  query.set('page', String(params.page));
  query.set('pageSize', String(params.pageSize));
  const response = await fetch(`${API_BASE}/api/notes/search?${query.toString()}`);
  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`);
  }
  return await response.json();
}