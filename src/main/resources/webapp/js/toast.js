 
/**
 * Módulo de utilidades para la gestión de avisos emergentes (toasts).
 */

const DEFAULT_DURATION = 5000;

/**
 * Muestra una notificación emergente temporal en el contenedor global.
 * 
 * @param {string} message - Texto del aviso a mostrar.
 * @param {string} [type='error'] - Tipo de aviso: 'error', 'info', 'success'.
 * @param {number} [duration=5000] - Tiempo de permanencia en milisegundos.
 */
export function showToast(message, type = 'error', duration = DEFAULT_DURATION) {
    const container = document.getElementById('toast-container');
    if (!container) {
        console.error('No se encontró el contenedor #toast-container');
        return;
    }

    const toast = document.createElement('div');
    toast.className = `toast ${type}`;

    const textSpan = document.createElement('span');
    textSpan.textContent = message;

    const closeBtn = document.createElement('button');
    closeBtn.className = 'icon-button';
    closeBtn.style.padding = '2px';
    closeBtn.style.color = 'inherit';
    closeBtn.textContent = '✕';
    closeBtn.title = 'Cerrar';
    closeBtn.addEventListener('click', () => removeToast(toast));

    toast.appendChild(textSpan);
    toast.appendChild(closeBtn);
    container.appendChild(toast);

    if (duration > 0) {
        setTimeout(() => {
            removeToast(toast);
        }, duration);
    }
}

/**
 * Aplica la animación de desvanecimiento y elimina el elemento del DOM.
 * 
 * @param {HTMLElement} toast - Elemento del toast a eliminar.
 */
function removeToast(toast) {
    if (!toast || !toast.parentNode) {
        return;
    }
    toast.classList.add('fade-out');
    toast.addEventListener('transitionend', () => {
        if (toast.parentNode) {
            toast.parentNode.removeChild(toast);
        }
    }, { once: true });
}