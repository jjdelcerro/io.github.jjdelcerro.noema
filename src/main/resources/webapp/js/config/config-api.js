/**
 * API de configuracion: descriptor UI, dominios, valores, listas y checkedlist.
 * El transporte vive en api.js.
 */

import { API_BASE } from '../api.js';


/* --- Endpoints de Configuración --- */

/**
 * Obtiene el descriptor UI de configuración (settingsui.json).
 */
export async function fetchConfigUI() {
  const response = await fetch(`${API_BASE}/api/config/ui`);
  if (!response.ok) {
    throw new Error(`Error obteniendo descriptor UI (${response.status})`);
  }
  return await response.json();
}

/**
 * Obtiene un diccionario de opciones para un dominio de configuración.
 * 
 * @param {string} domainName - Nombre del dominio (ej: 'LLM_MODELS').
 */
export async function fetchConfigDomain(domainName) {
  const url = `${API_BASE}/api/config/domains/${encodeURIComponent(domainName)}`;
  const response = await fetch(url);
  if (!response.ok) {
    throw new Error(`Fallo obteniendo dominio '${domainName}' (${response.status})`);
  }
  return await response.json();
}

/**
 * Obtiene el valor o estructura de una ruta de configuración.
 * 
 * @param {string} path - Ruta del parámetro en la jerarquía.
 */
export async function fetchConfigValue(path) {
  const encodedPath = encodePath(path);
  const response = await fetch(`${API_BASE}/api/config/${encodedPath}`);
  if (!response.ok) {
    throw new Error(`Fallo leyendo configuración '${path}' (${response.status})`);
  }
  return await response.json();
}

/**
 * Actualiza un valor simple en la configuración.
 * 
 * @param {string} path - Ruta del parámetro.
 * @param {any} value - Nuevo valor a guardar.
 */
export async function setConfigValue(path, value) {
  const encodedPath = encodePath(path);
  const response = await fetch(`${API_BASE}/api/config/${encodedPath}`, {
    method: 'POST',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify({value})
  });
  if (!response.ok) {
    throw new Error(`Fallo guardando '${path}' (${response.status})`);
  }
  return await response.json();
}

/**
 * Actualiza una lista de rutas (paths).
 * 
 * @param {string} path - Ruta del parámetro de lista.
 * @param {Array<string>} values - Array de rutas a guardar.
 */
export async function setConfigList(path, values) {
  const encodedPath = encodePath(path);
  const response = await fetch(`${API_BASE}/api/config/${encodedPath}/list`, {
    method: 'POST',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify(values)
  });
  if (!response.ok) {
    throw new Error(`Fallo guardando lista '${path}' (${response.status})`);
  }
  return await response.json();
}

/**
 * Actualiza el estado booleano de un ítem en una checkedlist.
 * 
 * @param {string} basePath - Ruta base de la lista.
 * @param {string} itemKey - Clave técnica del ítem.
 * @param {boolean} checked - Nuevo estado.
 */
export async function setConfigChecked(basePath, itemKey, checked) {
  const fullPath = `${basePath}/${itemKey}`;
  return await setConfigValue(fullPath, checked);
}

/**
 * Ejecuta una consulta batch de valores y evaluaciones de reglas.
 * 
 * @param {Array<{path: string, defaultValue: any, context?: Object}>} queryArray 
 */
export async function postConfigMultivalue(queryArray) {
  const response = await fetch(`${API_BASE}/api/config/multivalue`, {
    method: 'POST',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify(queryArray)
  });
  if (!response.ok) {
    throw new Error(`Fallo en consulta multivalue (${response.status})`);
  }
  return await response.json();
}

/**
 * Codifica la ruta dividiéndola en segmentos.
 */
function encodePath(path) {
  return path.split('/').map(segment => encodeURIComponent(segment)).join('/');
}