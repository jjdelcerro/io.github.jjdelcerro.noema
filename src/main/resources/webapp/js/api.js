/**
 * Módulo de comunicación con la API REST y canal SSE de Noema.
 */

const API_BASE = window.location.origin;

/**
 * Envía un mensaje de usuario al agente de forma asíncrona.
 * 
 * @param {string} terminalId - Identificador del terminal.
 * @param {string} message - Texto del mensaje enviado.
 * @returns {Promise<{accepted: boolean}>}
 */
export async function sendMessage(terminalId, message) {
  const url = `${API_BASE}/api/chat/${encodeURIComponent(terminalId)}`;
  const response = await fetch(url, {
    method: 'POST',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify({message})
  });

  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(`Error en el envío (${response.status}): ${errorText}`);
  }

  return {accepted: true};
}

/**
 * Descarga el historial de interacciones previas de un terminal.
 * 
 * @param {string} terminalId - Identificador del terminal.
 * @returns {Promise<Array<{type: string, content: string, timestamp: number}>>}
 */
export async function fetchHistory(terminalId) {
  const url = `${API_BASE}/api/chat/${encodeURIComponent(terminalId)}/history`;
  const response = await fetch(url);

  if (!response.ok) {
    throw new Error(`Fallo recuperando historial (${response.status})`);
  }

  return await response.json();
}

/**
 * Establece el canal de eventos Server-Sent Events (SSE) en tiempo real.
 * 
 * @param {string} terminalId - Identificador del terminal.
 * @param {Object} handlers - Callbacks para el procesamiento de eventos.
 * @returns {{close: Function, getReadyState: Function}}
 */
export function connectSSE(terminalId, handlers = {}) {
  const url = `${API_BASE}/api/console/${encodeURIComponent(terminalId)}`;
  const eventSource = new EventSource(url);

  eventSource.onopen = () => {
    if (handlers.onConnectionOpen) {
      handlers.onConnectionOpen();
    }
  };

  eventSource.onerror = (error) => {
    if (handlers.onConnectionError) {
      handlers.onConnectionError(error);
    }
  };

  eventSource.addEventListener('response', (event) => {
    if (handlers.onResponse) {
      handlers.onResponse(parseEventData(event.data));
    }
  });

  eventSource.addEventListener('log', (event) => {
    if (handlers.onLog) {
      handlers.onLog(parseEventData(event.data));
    }
  });

  eventSource.addEventListener('error', (event) => {
    if (handlers.onError) {
      handlers.onError(parseEventData(event.data));
    }
  });

  return {
    close: () => eventSource.close(),
    getReadyState: () => eventSource.readyState
  };
}

function parseEventData(rawData) {
  try {
    return JSON.parse(rawData);
  } catch (e) {
    return {content: rawData, timestamp: Date.now()};
  }
}

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

/* --- Exploración de Sistema de Archivos --- */

/**
 * Consulta la lista de directorios en una ruta del servidor.
 * 
 * @param {string} [path] - Ruta absoluta opcional a consultar.
 * @returns {Promise<{currentPath: string, parentPath: string|null, canGoUp: boolean, directories: Array<{name: string, path: string}>}>}
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

/* --- Ejecución de Acciones del Agente --- */

/**
 * Ejecuta una acción registrada en el backend del agente.
 * 
 * @param {string} actionName - Identificador de la acción (ej: 'COMPACT_REASONING_SESSION').
 * @returns {Promise<{status: string, action?: string, message?: string}>}
 */
export async function callBackendAction(actionName) {
  const url = `${API_BASE}/api/actions/${encodeURIComponent(actionName)}`;
  const response = await fetch(url, {
    method: 'POST',
    headers: {'Content-Type': 'application/json'}
  });

  if (!response.ok) {
    let errorMsg = `Error ejecutando acción (${response.status})`;
    try {
      const data = await response.json();
      if (data.message) {
        errorMsg = data.message;
      }
    } catch (e) {
    }
    throw new Error(errorMsg);
  }

  return await response.json();
}

/* --- Gestión de Contenido de Ficheros (var:/ y rutas absolutas) --- */

/**
 * Obtiene el contenido textual de un fichero.
 * 
 * @param {string} filePath - Ruta del fichero (ej: 'var:/config/models.properties' o '/home/...').
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
    }
    throw new Error(errorMsg);
  }

  return await response.json();
}