/**
 * Estilos compartidos por los arboles del WebUI (@webreflection/file-tree).
 *
 * Se inyectan en el shadow root de cada instancia del componente para:
 *
 *   - Ocultar el sufijo "0 bytes" en ficheros (el tamano real no lo
 *     conocemos: el componente lo calcula a partir de un Blob vacio).
 *   - Redefinir las variables --svg-* con colores concretos, porque los
 *     SVG originales usan currentColor y dentro de un background-image
 *     eso resuelve siempre a negro (invisible sobre fondo oscuro).
 *
 * Las custom properties heredan a traves del shadow DOM; el color de un
 * SVG cargado como background-image no. De ahi redefinirlas aqui en vez
 * de desde styles.css.
 */

/**
 * Inyecta la hoja de estilos correctiva en el shadow root del arbol.
 *
 * @param {ShadowRoot|null} shadowRoot
 */
export function injectTreeStyles(shadowRoot) {
    if (!shadowRoot) {
        return;
    }
    const css = new CSSStyleSheet();
    css.replaceSync(`
        :host {
            --svg-folder: url("data:image/svg+xml,<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' width='24' height='24' stroke='%239296a5' stroke-width='1' stroke-linecap='round' stroke-linejoin='round' fill='none' shape-rendering='geometricPrecision'><path d='M2.707 7.454V5.62C2.707 4.725 3.469 4 4.409 4h4.843c.451 0 .884.17 1.204.474l.49.467c.126.12.296.186.473.186h8.399c.94 0 1.55.695 1.55 1.59v.737m-18.661 0h-.354a.344.344 0 00-.353.35l.508 11.587c.015.34.31.609.668.609h17.283c.358 0 .652-.269.667-.61L22 7.805a.344.344 0 00-.353-.35h-.278m-18.662 0h18.662'/></svg>");
            --svg-file: url("data:image/svg+xml,<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' width='24' height='24' stroke='%239296a5' stroke-width='1' stroke-linecap='round' stroke-linejoin='round' fill='none' shape-rendering='geometricPrecision'><path d='M13 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V9z'/><path d='M13 2v7h7'/></svg>");
            --svg-text: url("data:image/svg+xml,<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' height='24' width='24' stroke='%239296a5' stroke-width='1' stroke-linecap='round' stroke-linejoin='round' fill='none' shape-rendering='geometricPrecision'><path d='M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z'/><path d='M14 2v6h6M16 13H8M16 17H8M10 9H8'/></svg>");
            --svg-closed: url("data:image/svg+xml,<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' width='24' height='24' stroke='%239296a5' stroke-width='1.5' stroke-linecap='round' stroke-linejoin='round' fill='none' shape-rendering='geometricPrecision'><path d='M12 8v8'/><path d='M8 12h8'/></svg>");
            --svg-opened: url("data:image/svg+xml,<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' width='24' height='24' stroke='%239296a5' stroke-width='1.5' stroke-linecap='round' stroke-linejoin='round' fill='none' shape-rendering='geometricPrecision'><path d='M8 12h8'/></svg>");
        }
        :host li.file > button::after,
        :host li.text > button::after {
            content: none;
        }
    `);
    shadowRoot.adoptedStyleSheets.push(css);
}