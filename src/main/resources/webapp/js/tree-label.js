/**
 * Etiqueta segura para los nodos del componente de arbol (@webreflection/file-tree).
 *
 * El componente aplica encodeURIComponent al nombre de los ficheros antes de
 * pintarlo, de modo que un titulo con espacios ("Mi nota") aparece como
 * "Mi%20nota" y uno con tildes ("Configuracion") como "Configuraci%C3%B3n".
 * Las carpetas no sufren el problema porque usan el nombre original para el
 * texto del boton.
 *
 * En lugar de parchear el componente vendorizado se sanea la etiqueta aqui:
 * el resultado solo contiene caracteres que encodeURIComponent deja intactos
 * (A-Z a-z 0-9 - _ . ! ~ * ' ( )), por lo que es un punto fijo de esa funcion
 * y el componente no puede volver a codificarlo. Los diacriticos se eliminan
 * previamente para que el texto siga siendo legible en castellano.
 *
 * Es una decision de PRESENTACION: el arbol no muestra el titulo literal de la
 * nota. Las propiedades ancladas al nodo (noteTitle, absolutePath, etc.)
 * conservan siempre el valor original, asi que la apertura del fichero o de la
 * nota no se ve afectada.
 */

// Complemento exacto del conjunto que encodeURIComponent no escapa.
const UNSAFE_LABEL_CHARS = /[^A-Za-z0-9\-_.!~*'()]+/g;
const COMBINING_MARKS = /[\u0300-\u036f]/g;

/**
 * Convierte un titulo o nombre de fichero en una etiqueta que el componente
 * de arbol devolvera intacta.
 *
 * @param {string} text Texto original.
 * @returns {string} Etiqueta saneada, nunca vacia.
 */
export function treeLabel(text) {
    const cleaned = String(text == null ? '' : text)
        .normalize('NFD')
        .replace(COMBINING_MARKS, '')
        .trim()
        .replace(UNSAFE_LABEL_CHARS, '_')
        .replace(/^_+|_+$/g, '');

    return cleaned || '_';
}
