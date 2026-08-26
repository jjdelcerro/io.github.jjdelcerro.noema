 
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
let btnExport = null;
let btnCopyConversation = null;


/**
 * Ajusta la altura del textarea dinámicamente según el contenido.
 */
function adjustTextareaHeight() {
    if (!messageInput) return;
    messageInput.style.height = 'auto';
    messageInput.style.height = `${messageInput.scrollHeight}px`;
}

/**
 * Inicializa las referencias del DOM y configura los listeners del chat.
 * 
 * @param {string} initialTerminalId - Identificador de terminal a activar.
 */
export function initChatUI(initialTerminalId) {
    chatArea = document.getElementById('chat-area');
    messageInput = document.getElementById('message-input');
    btnSend = document.getElementById('btn-send');
    btnExport = document.getElementById('btn-export');
    btnCopyConversation = document.getElementById('btn-copy-conversation');
    
    const statusIndicator = document.getElementById('status-indicator');
    if (statusIndicator) {
        statusDot = statusIndicator.querySelector('.status-dot');
        statusText = document.getElementById('status-text');
    }

    // Listeners de envío
    btnSend.addEventListener('click', handleSend);
    if (btnExport) {
        btnExport.addEventListener('click', exportConversation);
    }
    if (btnCopyConversation) {
        btnCopyConversation.addEventListener('click', copyConversation);
    }
    messageInput.addEventListener('input', adjustTextareaHeight);

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
    updateTerminalChrome(cleanId);

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
function updateTerminalChrome(terminalId) {
    const title = document.querySelector('#conversation-title span');
    const sidebarId = document.getElementById('sidebar-terminal-id');
    const sidebarName = document.getElementById('sidebar-terminal-name');
    if (title) title.textContent = terminalId;
    if (sidebarId) sidebarId.textContent = terminalId;
    if (sidebarName) sidebarName.textContent = 'Terminal activo';
}

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

    const rawContent = String(content);

    // Agrupación consecutiva si es del mismo tipo. Cada bloque conserva el
    // Markdown/texto original para copiarlo o exportarlo sin leer el HTML.
    if (type === lastBlockType && lastBlockElement) {
        const paragraph = document.createElement('div');
        paragraph.className = 'message-item';
        paragraph.innerHTML = formatContent(type, rawContent);
        lastBlockElement.insertBefore(paragraph, lastBlockElement.querySelector('.message-actions'));
        lastBlockElement.__rawContents.push(rawContent);
    } else {
        const block = document.createElement('div');
        block.className = `message-block ${type}`;
        block.__rawContents = [rawContent];

        const paragraph = document.createElement('div');
        paragraph.className = 'message-item';
        paragraph.innerHTML = formatContent(type, rawContent);

        const actions = document.createElement('div');
        actions.className = 'message-actions';

        if (type === 'log' || type === 'error') {
            block.classList.add('collapsed');
            const collapseButton = document.createElement('button');
            collapseButton.className = 'collapse-message-button';
            collapseButton.type = 'button';
            collapseButton.textContent = '›';
            collapseButton.title = 'Expandir mensaje';
            collapseButton.setAttribute('aria-label', 'Expandir mensaje');
            collapseButton.setAttribute('aria-expanded', 'false');
            collapseButton.addEventListener('click', () => toggleMessageBlock(block, collapseButton));
            actions.appendChild(collapseButton);
        }

        const copyButton = document.createElement('button');
        copyButton.className = 'copy-message-button';
        copyButton.type = 'button';
        copyButton.innerHTML = '<svg viewBox="0 0 24 24" aria-hidden="true"><rect x="9" y="5" width="10" height="13" rx="2"></rect><path d="M15 5V4a2 2 0 0 0-2-2H7a2 2 0 0 0-2 2v11a2 2 0 0 0 2 2h2"></path></svg>';
        copyButton.title = 'Copiar';
        copyButton.setAttribute('aria-label', 'Copiar mensaje como Markdown');
        copyButton.addEventListener('click', () => copyBlockAsMarkdown(block, copyButton));
        actions.appendChild(copyButton);

        if (type === 'log' || type === 'error') {
            const blockLabel = document.createElement('span');
            blockLabel.className = 'message-block-label';
            blockLabel.textContent = type === 'log' ? 'Mensajes del sistema' : 'Incidencias';
            actions.appendChild(blockLabel);
        }

        block.appendChild(paragraph);
        block.appendChild(actions);
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
function getBlockMarkdown(block) {
    return block && Array.isArray(block.__rawContents)
        ? block.__rawContents.join('\n\n')
        : '';
}

async function copyText(text) {
    if (navigator.clipboard && window.isSecureContext) {
        await navigator.clipboard.writeText(text);
        return;
    }

    const helper = document.createElement('textarea');
    helper.value = text;
    helper.style.position = 'fixed';
    helper.style.opacity = '0';
    document.body.appendChild(helper);
    helper.focus();
    helper.select();
    const copied = document.execCommand('copy');
    helper.remove();
    if (!copied) throw new Error('El navegador no permite copiar al portapapeles');
}

function toggleMessageBlock(block, button) {
    const collapsed = block.classList.toggle('collapsed');
    button.textContent = collapsed ? '›' : '⌄';
    button.title = collapsed ? 'Expandir mensaje' : 'Contraer mensaje';
    button.setAttribute('aria-label', collapsed ? 'Expandir mensaje' : 'Contraer mensaje');
    button.setAttribute('aria-expanded', String(!collapsed));
}

async function copyBlockAsMarkdown(block, button) {
    try {
        await copyText(getBlockMarkdown(block));
        const originalLabel = button.innerHTML;
        button.innerHTML = '<span class="copy-confirmation">✓</span>';
        showToast('Contenido copiado como Markdown', 'success', 2200);
        setTimeout(() => { button.innerHTML = originalLabel; }, 1600);
    } catch (error) {
        console.error('Error copiando mensaje:', error);
        showToast('No se pudo copiar el contenido', 'error');
    }
}

function blockHeading(block) {
    if (block.classList.contains('user')) return 'Usuario';
    if (block.classList.contains('response')) return 'Modelo';
    if (block.classList.contains('error')) return 'Error';
    return 'Sistema';
}

function generateConversationMarkdown() {
    const blocks = Array.from(chatArea.querySelectorAll('.message-block'));
    if (blocks.length === 0) return '';

    const exportedAt = new Date().toLocaleString('es-ES');
    const sections = [`# Conversación · ${currentTerminalId}`, `\n_Exportada el ${exportedAt}_\n`];
    for (const block of blocks) {
        const content = getBlockMarkdown(block);
        if (!content) continue;
        const heading = blockHeading(block);
        const isPlain = block.classList.contains('log') || block.classList.contains('error');
        sections.push(`## ${heading}\n\n${isPlain ? `\`\`\`text\n${content}\n\`\`\`` : content}`);
    }
    return sections.join('\n\n') + '\n';
}

export async function exportConversation() {
    if (!chatArea || !currentTerminalId) return;
    const markdown = generateConversationMarkdown();
    if (!markdown) {
        showToast('No hay mensajes que exportar', 'info');
        return;
    }

    const blob = new Blob([markdown], { type: 'text/markdown;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    const safeTerminalId = currentTerminalId.replace(/[^a-z0-9._-]+/gi, '_');
    link.href = url;
    link.download = `noema-${safeTerminalId || 'terminal'}-${new Date().toISOString().slice(0, 10)}.md`;
    document.body.appendChild(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(url);
    showToast('Conversación descargada', 'success', 2200);
}

async function copyConversation() {
    if (!chatArea || !currentTerminalId) return;
    const markdown = generateConversationMarkdown();
    if (!markdown) {
        showToast('No hay mensajes que copiar', 'info');
        return;
    }

    try {
        await copyText(markdown);
        showToast('Conversación copiada como Markdown', 'success', 2200);
    } catch (error) {
        console.error('Error copiando conversación:', error);
        showToast('No se pudo copiar la conversación', 'error');
    }
}

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
    adjustTextareaHeight();

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