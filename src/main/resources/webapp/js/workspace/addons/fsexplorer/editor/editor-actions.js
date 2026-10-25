/**
 * Módulo interno del editor: los cuatro flujos de acción sobre ficheros.
 *
 * Cada función hace la llamada al endpoint y devuelve el resultado. La
 * mutación de estado (sesión, pestañas, dirty, toasts) es responsabilidad
 * del llamante (editor-ui.js).
 *
 * No importa nunca de editor-ui.js (evitar dependencia circular).
 */

import { saveFileContent, fetchFileContent } from '../fsexplorer-api.js';
import {
  postEditorDiagnostics,
  postEditorFormat,
  postEditorFiximports
} from './editor-api.js';

/**
 * Guarda el contenido en disco.
 *
 * @param {{filePath: string, content: string}} params
 * @returns {Promise<Object>} Respuesta del endpoint.
 */
export async function saveActiveFile({ filePath, content }) {
  return await saveFileContent(filePath, content);
}

/**
 * Solicita diagnósticos para un fichero (trabaja sobre disco).
 *
 * @param {{filePath: string}} params
 * @returns {Promise<{annotations: Array}>}
 */
export async function runDiagnostics({ filePath }) {
  return await postEditorDiagnostics(filePath);
}

/**
 * Formatea el contenido del buffer. Devuelve el texto formateado.
 *
 * @param {{filePath: string, ext: string, content: string}} params
 * @returns {Promise<string>}
 */
export async function formatActiveFile({ filePath, ext, content }) {
  return await postEditorFormat(filePath, ext, content);
}

/**
 * Ajusta imports sobre disco y devuelve el contenido resultante, que el
 * llamante debe aplicar a la sesión para reflejar el cambio.
 *
 * @param {{filePath: string}} params
 * @returns {Promise<string>} Contenido nuevo leído de disco.
 */
export async function fixImports({ filePath }) {
  await postEditorFiximports(filePath);
  const data = await fetchFileContent(filePath);
  return data.content ?? '';
}