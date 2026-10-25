/**
 * Markup del chat.
 *
 * El chat es autosuficiente (seccion 8.3): aporta su propio renderer completo,
 * sus dos pestanas y su composer. El index.html solo le cede el hueco donde
 * montarse. Hasta la reduccion del index, este markup vivia en el documento.
 *
 * Los identificadores y las clases se conservan literales: el resto de piezas
 * del chat (pestanas, editor ampliado, exportacion) los resuelven por id global.
 */

const SHELL_CLASS = 'conversation-shell';

/**
 * Devuelve el hueco del chat, creandolo si el documento no lo trae.
 *
 * @returns {HTMLElement} contenedor del chat
 */
function ensureShell() {
    let shell = document.querySelector('.' + SHELL_CLASS);
    if (shell) {
        return shell;
    }
    shell = document.createElement('div');
    shell.className = SHELL_CLASS;
    const mainPanel = document.querySelector('.main-panel');
    if (mainPanel) {
        mainPanel.appendChild(shell);
    } else {
        document.body.appendChild(shell);
    }
    return shell;
}

/**
 * Construye el markup del chat dentro de su hueco, si aun no esta.
 *
 * Es idempotente: si el hueco ya trae los nodos, no los duplica.
 *
 * @returns {HTMLElement} el hueco del chat
 */
export function renderChatShell() {
    const shell = ensureShell();
    if (shell.querySelector('#chat-area')) {
        return shell;
    }
    shell.innerHTML = ''
        + '<div class="conversation-tabbar"></div>'
        + '<section id="chat-area" class="chat-area" data-tab-panel="chat" aria-live="polite">'
        + '  <div class="welcome-card">'
        + '    <div class="welcome-symbol">\u2726</div>'
        + '    <div>'
        + '      <div class="eyebrow">Noema</div>'
        + '      <h2>La conversacion continua.</h2>'
        + '      <p>Este terminal es un canal hacia una unica consciencia persistente. Puedes retomar el hilo cuando quieras.</p>'
        + '    </div>'
        + '  </div>'
        + '</section>'
        + '<section id="context-area" class="context-area" data-tab-panel="context" hidden aria-live="polite"></section>'
        + '<footer id="composer-area" class="composer-area">'
        + '  <div class="composer-shell">'
        + '    <textarea id="message-input" placeholder="Escribe a Noema\u2026" rows="1"></textarea>'
        + '    <button id="btn-expand-input" type="button" class="composer-expand-btn" title="Abrir el editor ampliado" aria-label="Ampliar editor">Ampliar</button>'
        + '    <button id="btn-send" class="icon-btn" aria-label="Enviar mensaje">'
        + '      <img class="icon" src="images/current/send.svg" width="18" height="18" alt="">'
        + '    </button>'
        + '  </div>'
        + '  <div class="composer-hint">Enter para nueva linea <span>\u00b7</span> Ctrl + Enter para enviar</div>'
        + '</footer>';
    return shell;
}
