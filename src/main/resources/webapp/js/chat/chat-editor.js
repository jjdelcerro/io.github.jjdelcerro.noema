/**
 * Editor ampliado del composer de chat.
 *
 * Abre un panel modal con una instancia de Ace en modo markdown sobre una copia
 * del texto que hay en el input de la conversacion. Al aceptar vuelca el
 * contenido sobre el input; al cancelar lo deja intacto. En ambos casos el foco
 * vuelve al input con el cursor al final del texto.
 */

const ACCELERATOR_ACCEPT = 'a';
const ACCELERATOR_CANCEL = 'c';

let inputEl = null;
let overlay = null;
let aceContainer = null;
let aceEditor = null;
let keyHandler = null;
let isOpen = false;

/**
 * Crea el DOM del panel una sola vez y lo deja oculto.
 */
function ensureDom() {
    if (overlay) {
        return;
    }

    overlay = document.createElement('div');
    overlay.className = 'dir-modal-backdrop chat-editor-backdrop';
    overlay.style.display = 'none';

    const dialog = document.createElement('div');
    dialog.className = 'editor-modal-dialog chat-editor-dialog';

    const header = document.createElement('div');
    header.className = 'editor-modal-header';
    header.innerHTML = '<div>'
        + '<h3>Mensaje ampliado</h3>'
        + '<span class="editor-modal-filepath">Editor markdown de la conversacion</span>'
        + '</div>';

    const body = document.createElement('div');
    body.className = 'editor-modal-body chat-editor-body';

    aceContainer = document.createElement('div');
    aceContainer.className = 'chat-editor-ace';
    body.appendChild(aceContainer);

    const footer = document.createElement('div');
    footer.className = 'editor-modal-footer';

    const status = document.createElement('span');
    status.className = 'editor-modal-status';
    status.textContent = 'Alt+A aceptar \u00b7 Alt+C o Esc cancelar';

    const cancelBtn = document.createElement('button');
    cancelBtn.type = 'button';
    cancelBtn.className = 'config-paths-btn secondary-button chat-editor-btn';
    cancelBtn.innerHTML = '<u>C</u>ancelar';
    cancelBtn.addEventListener('click', function () {
        closeChatEditor(false);
    });

    const acceptBtn = document.createElement('button');
    acceptBtn.type = 'button';
    acceptBtn.className = 'config-paths-btn primary-button chat-editor-btn';
    acceptBtn.innerHTML = '<u>A</u>ceptar';
    acceptBtn.addEventListener('click', function () {
        closeChatEditor(true);
    });

    footer.appendChild(status);
    footer.appendChild(cancelBtn);
    footer.appendChild(acceptBtn);

    dialog.appendChild(header);
    dialog.appendChild(body);
    dialog.appendChild(footer);
    overlay.appendChild(dialog);

    // Un clic en el velo equivale a cancelar, como Escape.
    overlay.addEventListener('mousedown', function (e) {
        if (e.target === overlay) {
            closeChatEditor(false);
        }
    });

    document.body.appendChild(overlay);
}

/**
 * Crea la instancia de Ace la primera vez que hace falta y la reutiliza
 * despues. No se destruye al cerrar: solo se oculta el panel.
 */
function ensureEditor() {
    if (aceEditor) {
        return;
    }
    if (typeof window.ace === 'undefined') {
        throw new Error('Ace no esta disponible');
    }
    window.ace.config.set('useWorker', false);
    aceEditor = window.ace.edit(aceContainer, {
        theme: 'ace/theme/dracula',
        mode: 'ace/mode/markdown',
        fontSize: '13px',
        showPrintMargin: false,
        useSoftTabs: true,
        tabSize: 2,
        wrap: true,
        displayIndentGuides: false
    });
}

/**
 * Abre el panel con una copia del texto actual del input.
 */
export function openChatEditor() {
    if (!inputEl || isOpen) {
        return;
    }
    ensureDom();
    try {
        ensureEditor();
    } catch (err) {
        console.error('No se pudo abrir el editor ampliado:', err);
        return;
    }

    overlay.style.display = 'flex';
    isOpen = true;

    // El panel tiene que estar visible antes de medir: con el contenedor oculto
    // Ace calcula dimensiones cero y no pinta el texto.
    requestAnimationFrame(function () {
        if (!isOpen) {
            return;
        }
        aceEditor.resize(true);
        aceEditor.setValue(inputEl.value, -1);
        aceEditor.focus();
    });

    keyHandler = function (e) {
        if (e.altKey && !e.ctrlKey && !e.metaKey) {
            const key = (e.key || '').toLowerCase();
            if (key === ACCELERATOR_ACCEPT) {
                e.preventDefault();
                e.stopPropagation();
                closeChatEditor(true);
                return;
            }
            if (key === ACCELERATOR_CANCEL) {
                e.preventDefault();
                e.stopPropagation();
                closeChatEditor(false);
                return;
            }
        }
        if (e.key === 'Escape') {
            e.preventDefault();
            e.stopPropagation();
            closeChatEditor(false);
        }
    };
    // capture: intercepta la tecla antes de que Ace la procese.
    document.addEventListener('keydown', keyHandler, true);
}

/**
 * Cierra el panel. Con accept vuelca el texto sobre el input; en ambos casos
 * devuelve el foco al input y deja el cursor al final.
 *
 * @param {boolean} accept
 */
function closeChatEditor(accept) {
    if (!isOpen) {
        return;
    }
    const text = accept ? aceEditor.getValue() : null;
    isOpen = false;

    if (keyHandler) {
        document.removeEventListener('keydown', keyHandler, true);
        keyHandler = null;
    }
    overlay.style.display = 'none';

    if (accept && inputEl) {
        inputEl.value = text;
        // El listener 'input' de chat-ui.js recalcula la altura del textarea.
        inputEl.dispatchEvent(new Event('input', { bubbles: true }));
    }
    focusInputEnd();
}

/**
 * Devuelve el foco al input del chat con el cursor al final del texto.
 */
function focusInputEnd() {
    if (!inputEl) {
        return;
    }
    inputEl.focus();
    const end = inputEl.value.length;
    try {
        inputEl.setSelectionRange(end, end);
    } catch (err) {
        // Algunos navegadores lanzan si el elemento no admite seleccion.
    }
    inputEl.scrollTop = inputEl.scrollHeight;
}

/**
 * Engancha el modulo al textarea del composer.
 *
 * @param {HTMLTextAreaElement} input
 */
export function initChatEditor(input) {
    inputEl = input;
}