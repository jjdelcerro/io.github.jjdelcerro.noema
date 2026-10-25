/**
 * Módulo de comunicación con la API REST y canal SSE de Noema.
 */

export const API_BASE = window.location.origin;

/**
 * Envía un mensaje de usuario al agente de forma asíncrona.
 * 
 * @param {string} terminalId - Identificador del terminal.
 * @param {string} message - Texto del mensaje enviado.
 * @returns {Promise<{accepted: boolean}>}
 */

/* --- Canal SSE y registro de eventos --- */

/**
 * Eventos que el servidor emite por el canal SSE. `kind` dice que se le
 * entrega al handler: el objeto parseado, su campo content, o nada.
 */
const SSE_EVENTS = [
  {name: 'thinking', kind: 'payload'},
  {name: 'response', kind: 'payload'},
  {name: 'log', kind: 'payload'},
  {name: 'error', kind: 'payload'},
  {name: 'stream_end', kind: 'none'},
  {name: 'editor_open', kind: 'content'},
  {name: 'editor_apply', kind: 'content'},
  {name: 'editor_setup_done', kind: 'content'},
  {name: 'editor_add_annotation', kind: 'content'},
  {name: 'editor_clear_annotations', kind: 'content'}
];

/**
 * Handlers registrados por nombre de evento. El registro pertenece al ciclo
 * de vida del componente que lo pide, no al del canal: sobrevive a una
 * reconexion y solo cae cuando el componente llama a la funcion de baja.
 */
const sseListeners = new Map();

let eventSource = null;

/**
 * Registra un handler para un evento del canal SSE.
 *
 * @param {string} eventName - 'thinking', 'response', 'editor_open', 'open', 'connection-error', ...
 * @param {Function} handler - Recibe el payload ya transformado del evento.
 * @returns {Function} funcion de baja, idempotente
 */
export function on(eventName, handler) {
  if (!eventName || typeof handler !== 'function') {
    return () => {};
  }
  let bucket = sseListeners.get(eventName);
  if (!bucket) {
    bucket = new Set();
    sseListeners.set(eventName, bucket);
  }
  bucket.add(handler);
  return () => {
    bucket.delete(handler);
    if (bucket.size === 0) {
      sseListeners.delete(eventName);
    }
  };
}

/**
 * Entrega un evento a sus handlers. Un handler que revienta no impide que
 * los demas reciban el evento.
 *
 * @param {string} eventName - Nombre del evento.
 * @param {*} payload - Dato ya transformado.
 */
function emit(eventName, payload) {
  const bucket = sseListeners.get(eventName);
  if (!bucket) {
    return;
  }
  Array.from(bucket).forEach((handler) => {
    try {
      handler(payload);
    } catch (error) {
      console.error('Error en el handler de ' + eventName + ':', error);
    }
  });
}

/**
 * Aplica la transformacion declarada para el evento.
 *
 * @param {string} kind - 'payload', 'content' o 'none'.
 * @param {string} rawData - Cuerpo crudo del evento.
 * @returns {*} Dato entregable al handler.
 */
function transformData(kind, rawData) {
  if (kind === 'none') {
    return null;
  }
  const parsed = parseEventData(rawData);
  if (kind === 'content') {
    return parsed.content;
  }
  return parsed;
}

/**
 * Abre el canal SSE. Si habia uno abierto lo cierra antes; los handlers
 * registrados no se tocan.
 *
 * @param {string} terminalId - Identificador del terminal.
 * @returns {{close: Function, getReadyState: Function}} control del canal
 */
export function connectSSE(terminalId) {
  closeSSE();

  const url = API_BASE + '/api/console/' + encodeURIComponent(terminalId);
  eventSource = new EventSource(url);

  eventSource.onopen = () => emit('open', null);
  eventSource.onerror = (error) => emit('connection-error', error);

  SSE_EVENTS.forEach((spec) => {
    eventSource.addEventListener(spec.name, (event) => {
      emit(spec.name, transformData(spec.kind, event.data));
    });
  });

  return {
    close: closeSSE,
    getReadyState: () => (eventSource ? eventSource.readyState : 2)
  };
}

/**
 * Cierra el canal SSE si esta abierto.
 */
export function closeSSE() {
  if (eventSource) {
    eventSource.close();
    eventSource = null;
  }
}

function parseEventData(rawData) {
  try {
    return JSON.parse(rawData);
  } catch (e) {
    return {content: rawData, timestamp: Date.now()};
  }
}
/* --- Estado de la UI --- */

/**
 * Envía el estado actual del editor (push). Sin lógica en servidor: se
 * almacena el último snapshot por terminalId.
 *
 * @param {Object} payload - Snapshot construido en editor-ui.js.
 * @returns {Promise<Object>}
 */
export async function postUIState(payload) {
  const response = await fetch(`${API_BASE}/api/ui/state`, {
    method: 'POST',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify(payload)
  });
  if (!response.ok) {
    throw new Error(`Error enviando estado del editor (${response.status})`);
  }
  return await response.json();
}
