/**
 * Arbol de archivos del explorador (Web Component @webreflection/file-tree).
 *
 * Este modulo NO conoce al editor. Cuando el usuario pulsa un fichero avisa
 * al propietario del arbol mediante el callback onOpenFile que recibe en
 * initWorkspaceUI; quien decide si eso abre un fichero es el addon del
 * explorador, que compone arbol y editor. Ver la seccion 11.2.
 *
 * El arbol se construye de forma diferida: las raices al inicializar y al
 * pulsar refrescar, y los hijos de cada carpeta cuando el usuario la
 * expande por primera vez.
 *
 * El boton de refrescar recrea la instancia del componente en lugar de
 * vaciarla: asi se olvida la marca __loaded de cada carpeta y desaparecen
 * los nodos de ficheros borrados o renombrados. Contrapartida asumida: se
 * pierde el estado de expansion.
 */

import { showToast } from '../../../../toast.js';
import { injectTreeStyles } from '../../../../common/tree-styles.js';
import { treeLabel } from '../../../../common/tree-label.js';
import { fetchTreeEntries } from '../fsexplorer-api.js';

let TreeClass = null;
let FolderClass = null;
let FileClass = null;
let treeInstance = null;
let treeHost = null;
let pathLabel = null;
let openFileHandler = null;

/**
 * Inicializa el arbol de archivos. Es idempotente y silencioso si los
 * elementos del DOM no existen (por ejemplo en paginas de prueba).
 *
 * @param {{onOpenFile?: function({path: string, name: string}): any}} [options]
 *        onOpenFile recibe la seleccion de un fichero; el arbol no decide
 *        que hacer con ella
 * @returns {Promise<void>}
 */
export async function initWorkspaceUI(options = {}) {
    openFileHandler = (typeof options.onOpenFile === 'function') ? options.onOpenFile : null;
    treeHost = document.getElementById('workspace-tree');
    if (!treeHost) {
        return;
    }
    pathLabel = document.getElementById('workspace-tree-path');

    try {
        const mod = await import('../../../../../vendor/file-tree/prod.js');
        TreeClass = mod.Tree;
        FolderClass = mod.Folder;
        FileClass = mod.File;
    } catch (error) {
        console.error('Error cargando el componente de arbol:', error);
        showToast('No se pudo cargar el explorador de archivos', 'error');
        return;
    }

    const refreshButton = document.getElementById('btn-refresh-tree');
    if (refreshButton) {
        refreshButton.addEventListener('click', handleRefreshClick);
    }

    try {
        await rebuildTree();
    } catch (error) {
        console.error('Error inicializando el explorador de archivos:', error);
        showToast('No se pudo cargar el explorador de archivos', 'error');
    }
}

/**
 * Reconstruye el arbol desde cero contra el endpoint. Desecha la instancia
 * anterior en lugar de vaciarla, de modo que las carpetas vuelven a pedir
 * sus hijos al servidor y los nodos de ficheros ya inexistentes no quedan
 * colgados. La expansion previa se pierde.
 */
async function rebuildTree() {
    treeInstance = new TreeClass();
    injectTreeStyles(treeInstance.shadowRoot);
    treeInstance.addEventListener('click', handleTreeClick);
    treeHost.replaceChildren(treeInstance);
    clearPathLabel();

    const roots = await fetchTreeEntries(null);
    for (const item of roots) {
        treeInstance.append(createNode(item));
    }
}

/**
 * Refresco manual. Deshabilita el boton mientras dura la peticion para
 * evitar reconstrucciones solapadas.
 */
async function handleRefreshClick(event) {
    const button = event.currentTarget;
    button.disabled = true;
    try {
        await rebuildTree();
    } catch (error) {
        console.error('Error refrescando el arbol de archivos:', error);
        showToast('No se pudo refrescar el arbol de archivos', 'error');
    } finally {
        button.disabled = false;
    }
}

/**
 * Gestor unico de eventos del arbol. Diferencia entre expandir carpeta
 * (carga diferida de hijos), seleccionar fichero (apertura en el editor)
 * y mostrar la ruta del nodo pulsado en el breadcrumb.
 */
async function handleTreeClick(event) {
    const detail = event.detail;
    if (!detail || !detail.target) {
        return;
    }
    const action = detail.action;
    const target = detail.target;

    showPath(target);

    if (action === 'open' && target.type === 'folder' && !target.__loaded) {
        event.waitUntil(loadChildren(target));
        return;
    }

    if (action === 'click' && target.type !== 'folder') {
        event.waitUntil(notifySelection(target));
    }
}

/**
 * Avisa al propietario del arbol de que se ha seleccionado un fichero. El
 * arbol no abre nada: emite la seleccion y quien la recibe decide.
 *
 * @param {Object} node nodo del componente con absolutePath y displayName
 * @returns {Promise<void>}
 */
async function notifySelection(node) {
    if (!openFileHandler) {
        return;
    }
    try {
        await openFileHandler({
            path: node.absolutePath,
            name: node.displayName
        });
    } catch (error) {
        console.error('Error abriendo el fichero seleccionado:', node.absolutePath, error);
        showToast('No se pudo abrir el fichero', 'error');
    }
}

/**
 * Carga los hijos de una carpeta contra el endpoint del servidor y los
 * inyecta en el nodo. Marca la carpeta como cargada para no repetir.
 */
async function loadChildren(folder) {
    if (!folder.absolutePath) {
        return;
    }
    try {
        const children = await fetchTreeEntries(folder.absolutePath);
        folder.append(...children.map(createNode));
    } catch (error) {
        console.error('Error cargando hijos de:', folder.absolutePath, error);
        showToast('No se pudo cargar el contenido de la carpeta', 'error');
    } finally {
        folder.__loaded = true;
    }
}

/**
 * Pinta la ruta del nodo pulsado en el breadcrumb. Las raices virtuales
 * pueden no traer path, en cuyo caso se cae al nombre original del item.
 */
function showPath(node) {
    if (!pathLabel) {
        return;
    }
    const text = node.absolutePath || node.displayName || '';
    pathLabel.textContent = text;
    pathLabel.title = text;
}

/** Vacia el breadcrumb: tras un refresco no hay nada seleccionado. */
function clearPathLabel() {
    if (!pathLabel) {
        return;
    }
    pathLabel.textContent = '';
    pathLabel.removeAttribute('title');
}

/**
 * Construye un nodo (Folder o File) del componente a partir de la entrada
 * JSON del endpoint. Ancla la ruta absoluta y el nombre original como
 * propiedades para poder recuperarlos en los eventos (el path del
 * componente va URL-encoded).
 */
function createNode(item) {
    let node;
    if (item.type === 'directory') {
        node = new FolderClass(item.name);
    } else {
        // Un nombre de fichero con espacios se pintaria como "Mi%20nota.txt";
        // ver tree-label.js. La ruta real sigue en node.absolutePath.
        node = new FileClass([], treeLabel(item.name));
    }
    node.absolutePath = item.path;
    node.displayName = item.name;
    return node;
}

