/**
 * Markup de la configuracion.
 *
 * La configuracion es la configuracion global del agente: no es un area de la
 * pagina ni un addon del workspace, y su contenedor es una ventana modal
 * (seccion 8.6). Por eso no vive en el index.html: se crea la primera vez que
 * se inicializa y se reutiliza despues.
 */

const PANEL_ID = 'config-panel';

/**
 * Devuelve la ventana de configuracion, construyendola si no existe.
 *
 * @returns {HTMLElement} el panel modal
 */
export function ensureConfigPanel() {
    let panel = document.getElementById(PANEL_ID);
    if (panel) {
        return panel;
    }
    panel = document.createElement('aside');
    panel.id = PANEL_ID;
    panel.className = 'config-panel hidden';
    panel.innerHTML = ''
        + '<div class="config-header">'
        + '  <div>'
        + '    <div class="eyebrow">Noema</div>'
        + '    <h2>Configuracion</h2>'
        + '  </div>'
        + '  <button id="btn-close-config" class="config-close-button" title="Cerrar configuracion">Cerrar</button>'
        + '</div>'
        + '<div class="config-body">'
        + '  <nav id="config-tree" class="config-tree"></nav>'
        + '  <section id="config-content" class="config-content"></section>'
        + '</div>';
    document.body.appendChild(panel);
    return panel;
}
