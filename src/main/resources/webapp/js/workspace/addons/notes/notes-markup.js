/**
 * Markup del addon de notas.
 *
 * El addon construye su propio DOM dentro del panel que le cede el workspace
 * (seccion 8.1): arbol, buscador, panel central y sus dos splitters. El
 * index.html ya no contiene nada de esto.
 *
 * Los identificadores se conservan literales porque las tres piezas (notas,
 * editor de nota y busqueda) los resuelven por id global.
 */

/**
 * Rellena el panel de notas con su estructura.
 *
 * @param {HTMLElement} panel contenedor cedido por el workspace
 * @returns {void}
 */
export function renderNotes(panel) {
    panel.innerHTML = ''
        + '<div class="notes-left-pane">'
        + '  <div class="notes-tree-pane">'
        + '    <div class="notes-tree-header">'
        + '      <button id="btn-new-note" class="notes-new-button" type="button" title="Crear una nota nueva" aria-label="Crear una nota nueva">'
        + '        <img class="icon" src="images/current/new.svg" width="18" height="18" alt="">'
        + '      </button>'
        + '    </div>'
        + '    <div id="notes-tree" class="notes-tree"></div>'
        + '  </div>'
        + '  <div id="splitter-notes-h" class="splitter splitter-horizontal" role="separator" aria-orientation="horizontal" aria-label="Redimensionar panel de busqueda"></div>'
        + '  <div class="notes-search-pane">'
        + '    <div class="notes-search-scope">'
        + '      <span class="notes-search-scope-label">\u00c1mbito:</span>'
        + '      <span id="notes-search-scope" class="notes-search-scope-value">Todo</span>'
        + '      <button id="notes-search-scope-reset" class="notes-search-scope-reset" type="button" title="Buscar en todas las notas" hidden>Todo</button>'
        + '    </div>'
        + '    <div class="notes-search">'
        + '      <input id="notes-search-input" class="notes-search-input" type="search" placeholder="Buscar en las notas..." autocomplete="off">'
        + '      <button id="notes-search-go" class="notes-search-go" type="button" title="Buscar (Enter)" aria-label="Buscar">'
        + '        <img class="icon" src="images/current/search.svg" width="18" height="18" alt="">'
        + '      </button>'
        + '      <select id="notes-search-mode" class="notes-search-mode" title="Modo de busqueda">'
        + '        <option value="glob">Patron</option>'
        + '        <option value="exact">Exacta</option>'
        + '        <option value="fuzzy">Difusa</option>'
        + '        <option value="regexp">Exp.regular</option>'
        + '        <option value="tags">Etiquetas</option>'
        + '      </select>'
        + '    </div>'
        + '    <div id="notes-search-results" class="notes-search-results" hidden></div>'
        + '    <div id="notes-search-pager" class="notes-search-pager" hidden>'
        + '      <button id="notes-search-prev" class="notes-search-page-button" type="button" disabled>Anterior</button>'
        + '      <span id="notes-search-count" class="notes-search-count"></span>'
        + '      <button id="notes-search-next" class="notes-search-page-button" type="button" disabled>Siguiente</button>'
        + '    </div>'
        + '  </div>'
        + '</div>'
        + '<div id="splitter-notes" class="splitter" role="separator" aria-orientation="vertical" aria-label="Redimensionar lista de notas"></div>'
        + '<div class="notes-main-pane">'
        + '  <div class="notes-editor-meta" hidden>'
        + '    <input id="note-title" class="note-title-input" type="text" placeholder="Titulo" autocomplete="off">'
        + '    <input id="note-tags" class="note-tags-input" type="text" placeholder="tags, separados, por comas" autocomplete="off">'
        + '    <button id="btn-save-note" class="editor-toolbar-btn notes-meta-button" type="button" title="Guardar nota (Ctrl+S)" aria-label="Guardar nota" disabled>'
        + '      <img class="icon" src="images/current/save.svg" width="18" height="18" alt="">'
        + '    </button>'
        + '    <button id="btn-reload-note" class="editor-toolbar-btn notes-meta-button" type="button" title="Recargar nota desde el disco" aria-label="Recargar nota">'
        + '      <img class="icon" src="images/current/reload.svg" width="18" height="18" alt="">'
        + '    </button>'
        + '    <button id="btn-toggle-note-wrap" class="editor-toolbar-btn notes-meta-button active" type="button" title="Desactivar ajuste de linea" aria-label="Desactivar ajuste de linea" aria-pressed="true">'
        + '      <img class="icon" src="images/current/wrap-active.svg" width="18" height="18" alt="">'
        + '    </button>'
        + '    <button id="btn-close-note" class="editor-toolbar-btn notes-meta-button" type="button" title="Cerrar nota" aria-label="Cerrar nota">'
        + '      <img class="icon" src="images/current/close.svg" width="18" height="18" alt="">'
        + '    </button>'
        + '  </div>'
        + '  <div id="note-status" class="note-status" hidden></div>'
        + '  <div id="notes-editor" class="notes-editor" hidden></div>'
        + '</div>';
}
