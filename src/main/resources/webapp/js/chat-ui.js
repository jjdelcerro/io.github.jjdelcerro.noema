 
/**
 * Módulo de gestión de la interfaz de chat, historial y conexión SSE.
 */

import { sendMessage, fetchHistory, connectSSE } from './api.js';
import { showToast } from './toast.js';

let currentTerminalId = '';
let activeSseControl = null;

// Estado para agrupación consecutiva de mensajes
let lastBlockType = null;
let lastBlockElement = null;

// Referencias al DOM
let chatArea = null;
let messageInput = null;
let btnSend = null;
let statusDot = null;
let statusText = null;

/**
 * Inicializa las referencias del DOM y configura los listeners del chat.
 * 
 * @param {string} initialTerminalId - Identificador de terminal a activar.
 */
export function initChatUI(initialTerminalId) {
    chatArea = document.getElementById('chat-area');
    messageInput = document.getElementById('message-input');
    btnSend = document.getElementById('btn-send');
    
    const statusIndicator = document.getElementById('status-indicator');
    if (statusIndicator) {
        statusDot = statusIndicator.querySelector('.status-dot');
        statusText = document.getElementById('status-text');
    }

    // Listeners de envío
    btnSend.addEventListener('click', handleSend);
    messageInput.addEventListener('keydown', (e) => {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            handleSend();
        }
    });

    if (initialTerminalId) {
        switchTerminal(initialTerminalId);
    }
}

/**
 * Cambia el terminal activo, cierra la conexión SSE previa y carga el nuevo historial.
 * 
 * @param {string} newTerminalId 
 */
export async function switchTerminal(newTerminalId) {
    if (!newTerminalId || newTerminalId.trim() === '') {
        setConnectionStatus('disconnected');
        clearChat();
        currentTerminalId = '';
        return;
    }

    const cleanId = newTerminalId.trim();
    if (cleanId === currentTerminalId && activeSseControl) {
        return;
    }

    currentTerminalId = cleanId;

    // 1. Cerrar conexión SSE previa si existe
    if (activeSseControl) {
        activeSseControl.close();
        activeSseControl = null;
    }

    // 2. Limpiar pantalla
    clearChat();

    // 3. Descargar e inyectar historial previo vía REST
    setConnectionStatus('connecting');
    try {
        const history = await fetchHistory(currentTerminalId);
        if (Array.isArray(history)) {
            for (const item of history) {
                addMessage(mapHistoryType(item.type), item.content);
            }
        }
    } catch (error) {
        console.error('Error cargando historial:', error);
        showToast('No se pudo recuperar el historial previo', 'error');
    }

    // 4. Iniciar canal SSE para eventos en tiempo real
    activeSseControl = connectSSE(currentTerminalId, {
        onConnectionOpen: () => setConnectionStatus('connected'),
        onConnectionError: () => setConnectionStatus('connecting'),
        onResponse: (data) => addMessage('response', data.content),
        onLog: (data) => addMessage('log', data.content),
        onError: (data) => addMessage('error', data.content)
    });
}

/**
 * Limpia la vista del chat y resetea el agrupador de bloques.
 */
export function clearChat() {
    if (chatArea) {
        chatArea.innerHTML = '';
    }
    lastBlockType = null;
    lastBlockElement = null;
}

/**
 * Inyecta un mensaje en el área de chat aplicando la lógica de agrupación y formato.
 * 
 * @param {string} type - 'user', 'response', 'log' o 'error'.
 * @param {string} content - Contenido del mensaje.
 */
export function addMessage(type, content) {
    if (!chatArea || !content) return;

    const isNearBottom = chatArea.scrollHeight - chatArea.scrollTop - chatArea.clientHeight < 80;

    // Agrupación consecutiva si es del mismo tipo
    if (type === lastBlockType && lastBlockElement) {
        const paragraph = document.createElement('div');
        paragraph.className = 'message-item';
        paragraph.innerHTML = formatContent(type, content);
        lastBlockElement.appendChild(paragraph);
    } else {
        // Crear nuevo bloque de mensaje
        const block = document.createElement('div');
        block.className = `message-block ${type}`;

        const paragraph = document.createElement('div');
        paragraph.className = 'message-item';
        paragraph.innerHTML = formatContent(type, content);

        block.appendChild(paragraph);
        chatArea.appendChild(block);

        lastBlockType = type;
        lastBlockElement = block;
    }

    if (isNearBottom) {
        chatArea.scrollTop = chatArea.scrollHeight;
    }
}

/**
 * Mapea los tipos del backend a las clases de visualización del frontend.
 */
function mapHistoryType(backendType) {
    switch (backendType) {
        case 'user-message': return 'user';
        case 'response': return 'response';
        case 'log': return 'log';
        case 'error': return 'error';
        default: return 'response';
    }
}

/**
 * Formatea el contenido del mensaje según su tipo (Markdown/Sanitizado o Texto).
 */
function formatContent(type, content) {
    if (type === 'response') {
        if (window.marked && window.DOMPurify) {
            const parsed = window.marked.parse(content);
            return window.DOMPurify.sanitize(parsed);
        }
    }
    
    // Escape básico contra XSS para otros tipos de mensajes
    const temp = document.createElement('div');
    temp.textContent = content;
    return temp.innerHTML.replace(/\n/g, '<br>');
}

/**
 * Procesa el envío de un mensaje de usuario.
 */
async function handleSend() {
    const text = messageInput.value.trim();
    if (!text || !currentTerminalId) return;

    // Muestra optimista inmediata en la UI
    addMessage('user', text);
    messageInput.value = '';

    try {
        await sendMessage(currentTerminalId, text);
    } catch (error) {
        console.error('Error enviando mensaje:', error);
        showToast('Error al enviar el mensaje. Inténtelo de nuevo.', 'error');
    }
}

/**
 * Actualiza la apariencia del indicador de conexión en la cabecera.
 * 
 * @param {string} status - 'connected', 'connecting' o 'disconnected'.
 */
export function setConnectionStatus(status) {
    if (!statusDot || !statusText) return;

    statusDot.className = `status-dot ${status}`;
    switch (status) {
        case 'connected':
            statusText.textContent = 'Conectado';
            break;
        case 'connecting':
            statusText.textContent = 'Conectando...';
            break;
        case 'disconnected':
        default:
            statusText.textContent = 'Desconectado';
            break;
    }
}