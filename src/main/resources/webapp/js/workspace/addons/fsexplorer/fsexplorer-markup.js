/**
 * Markup del addon del explorador.
 *
 * El addon construye su propio DOM dentro del panel que le cede el workspace
 * (seccion 8.1). El index.html deja de contener el arbol, el splitter y el
 * editor: un area del index es un hueco, no un componente.
 *
 * Los identificadores se conservan literales porque el arbol y el editor los
 * resuelven por id global. Es el mismo criterio que ya usa el toolbar del
 * editor, que crea sus botones en tiempo de ejecucion.
 */

/**
 * Rellena el panel del explorador con su estructura.
 *
 * @param {HTMLElement} panel contenedor cedido por el workspace
 * @returns {void}
 */
export function renderFsExplorer(panel) {
    panel.innerHTML = ''
        + '<div class="workspace-tree-pane">'
        + '  <div class="workspace-tree-toolbar">'
        + '    <button id="btn-refresh-tree" class="editor-toolbar-btn" type="button" title="Refrescar arbol de archivos" aria-label="Refrescar arbol de archivos">'
        + '      <img class="icon" src="images/current/reload.svg" width="18" height="18" alt="">'
        + '    </button>'
        + '  </div>'
        + '  <div id="workspace-tree-path" class="workspace-tree-path"></div>'
        + '  <div id="workspace-tree" class="workspace-tree"></div>'
        + '</div>'
        + '<div id="splitter-tree" class="splitter" role="separator" aria-orientation="vertical" aria-label="Redimensionar panel del editor"></div>'
        + '<div class="workspace-editor-pane">'
        + '  <div id="workspace-editor-tabs" class="workspace-editor-tabs" role="tablist"></div>'
        + '  <div id="workspace-editor-toolbar" class="workspace-editor-toolbar"></div>'
        + '  <div id="workspace-editor" class="workspace-editor"></div>'
        + '</div>';
}
