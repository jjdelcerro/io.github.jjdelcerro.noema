/**
 * Diálogo modal de cambios sin guardar.
 *
 * Se extrajo de editor-ui.js cuando el panel de notas necesitó el mismo
 * triestado. Reutiliza las clases del modal del editor (dir-modal-backdrop,
 * editor-modal-dialog y sus partes), así que se comporta igual que el cierre
 * de pestaña sucia: Escape y click en el fondo cancelan.
 *
 * @param {{title?: string, message?: string}} options
 * @returns {Promise<'save'|'discard'|'cancel'>}
 */
export function confirmDiscardDialog(options = {}) {
    const title = options.title || 'Cambios sin guardar';
    const message = options.message || 'Hay cambios sin guardar. ¿Qué quieres hacer?';

    return new Promise((resolve) => {
        const backdrop = document.createElement('div');
        backdrop.className = 'dir-modal-backdrop';

        const dialog = document.createElement('div');
        dialog.className = 'editor-modal-dialog';
        dialog.style.width = 'min(440px, calc(100vw - 32px))';
        dialog.style.height = 'auto';

        const header = document.createElement('div');
        header.className = 'editor-modal-header';
        const heading = document.createElement('h3');
        heading.textContent = title;
        header.appendChild(heading);

        const body = document.createElement('div');
        body.className = 'editor-modal-body';
        body.style.padding = '18px';
        body.style.display = 'block';
        const paragraph = document.createElement('p');
        // textContent y no innerHTML: el mensaje puede llevar el nombre de un
        // fichero o de una nota, que no es contenido de confianza.
        paragraph.style.margin = '0';
        paragraph.textContent = message;
        body.appendChild(paragraph);

        const footer = document.createElement('div');
        footer.className = 'editor-modal-footer';

        const btnCancel = document.createElement('button');
        btnCancel.type = 'button';
        btnCancel.className = 'config-paths-btn secondary-button';
        btnCancel.textContent = 'Cancelar';

        const btnDiscard = document.createElement('button');
        btnDiscard.type = 'button';
        btnDiscard.className = 'config-paths-btn secondary-button';
        btnDiscard.textContent = 'Descartar';

        const btnSave = document.createElement('button');
        btnSave.type = 'button';
        btnSave.className = 'config-paths-btn primary-button';
        btnSave.textContent = 'Guardar';

        footer.appendChild(btnCancel);
        footer.appendChild(btnDiscard);
        footer.appendChild(btnSave);

        dialog.appendChild(header);
        dialog.appendChild(body);
        dialog.appendChild(footer);
        backdrop.appendChild(dialog);
        document.body.appendChild(backdrop);

        const close = (result) => {
            document.removeEventListener('keydown', onEsc);
            if (backdrop.parentNode) {
                backdrop.parentNode.removeChild(backdrop);
            }
            resolve(result);
        };

        const onEsc = (e) => {
            if (e.key === 'Escape') {
                close('cancel');
            }
        };

        btnCancel.addEventListener('click', () => close('cancel'));
        btnDiscard.addEventListener('click', () => close('discard'));
        btnSave.addEventListener('click', () => close('save'));
        backdrop.addEventListener('click', (e) => {
            if (e.target === backdrop) {
                close('cancel');
            }
        });
        document.addEventListener('keydown', onEsc);

        btnCancel.focus();
    });
}
