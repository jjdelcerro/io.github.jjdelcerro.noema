/**
 * API del explorador de ficheros: arbol, directorios y contenido de ficheros.
 * Reune el transporte de `/api/files/*` y el explorador de directorios de
 * `/api/fs/*`, que hasta ahora vivian repartidos entre api.js y dos copias
 * locales mas (workspace-ui.js y editor-ui.js).
 */

import { API_BASE } from '../../../api.js';

/**
 * Consulta la lista de directorios en una ruta del servidor.
 *
 * @param {string} [path] - Ruta absoluta opcional a consultar.
 * @returns {Promise<{currentPath: string, parentPath: ?string, canGoUp: boolean,
 *                    directories: Array<{name: string, path: string}>}>}
 */
export async function fetchDirectories(path) {
  const url = new URL(`${API_BASE}/api/fs/directories`);
  if (path) {
    url.searchParams.set('path', path);
  }
  const response = await fetch(url.toString());
  if (!response.ok) {
    throw new Error(`Error explorando directorios (${response.status})`);
  }
  return await response.json();
}

/**
 * Llama al endpoint del arbol. Sin path devuelve raices virtuales; con path,
 * los hijos directos de esa carpeta.
 *
 * @param {string} [path] - Ruta de la carpeta a expandir.
 * @returns {Promise<Array<Object>>} entradas del nivel pedido
 */
export async function fetchTreeEntries(path) {
  const url = path
    ? `${API_BASE}/api/files/tree?path=${encodeURIComponent(path)}`
    : `${API_BASE}/api/files/tree`;
  const response = await fetch(url);
  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`);
  }
  return await response.json();
}

/**
 * Obtiene el contenido textual de un fichero.
 *
 * @param {string} filePath - Ruta del fichero (ej: 'var:/config/x.properties' o '/home/...').
 * @returns {Promise<{file: string, content: string}>}
 */
export async function fetchFileContent(filePath) {
  const url = new URL(`${API_BASE}/api/files/content`);
  url.searchParams.set('file', filePath);

  const response = await fetch(url.toString());
  if (!response.ok) {
    let errorMsg = `Error leyendo fichero (${response.status})`;
    try {
      const data = await response.json();
      if (data.error) {
        errorMsg = data.error;
      }
    } catch (e) {
      // respuesta sin JSON; conservamos el mensaje generico
    }
    throw new Error(errorMsg);
  }

  return await response.json();
}

/**
 * Guarda el contenido textual de un fichero.
 *
 * @param {string} filePath - Ruta del fichero a guardar.
 * @param {string} content - Contenido textual a escribir (UTF-8).
 * @returns {Promise<{status: string, file: string}>}
 */
export async function saveFileContent(filePath, content) {
  const url = `${API_BASE}/api/files/content`;
  const response = await fetch(url, {
    method: 'POST',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify({file: filePath, content})
  });

  if (!response.ok) {
    let errorMsg = `Error guardando fichero (${response.status})`;
    try {
      const data = await response.json();
      if (data.error) {
        errorMsg = data.error;
      }
    } catch (e) {
      // respuesta sin JSON; conservamos el mensaje generico
    }
    throw new Error(errorMsg);
  }

  return await response.json();
}