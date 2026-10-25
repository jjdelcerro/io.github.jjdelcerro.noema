/**
 * API del chat: envio de mensajes, historial, contexto proyectado y acciones
 * del agente. El transporte (canal SSE, ficheros y estado del editor) vive en
 * api.js, que es el unico modulo que conoce la direccion base.
 */

import { API_BASE } from '../api.js';

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
 * Descarga el XML de contexto proyectado de la conversación para un terminal.
 * 
 * @param {string} terminalId - Identificador del terminal.
 * @returns {Promise<string>} Contenido del documento XML como texto.
 */
export async function fetchContextXml(terminalId) {
  const url = `${API_BASE}/api/chat/${encodeURIComponent(terminalId)}/context.xml`;
  const response = await fetch(url);

  if (!response.ok) {
    throw new Error(`Fallo recuperando contexto XML (${response.status})`);
  }

  return await response.text();
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