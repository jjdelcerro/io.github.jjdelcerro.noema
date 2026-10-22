/**
 * Módulo de gestión del panel de trabajo (workspace): explorador de
 * archivos (Web Component @webreflection/file-tree) y delegación al
 * editor para la apertura de ficheros.
 *
 * El árbol se construye de forma diferida: las raíces al inicializar y los
 * hijos de cada carpeta cuando el usuario la expande por primera vez.
 */

import { showToast } from './toast.js';
import { openFile as openEditorFile } from './editor-ui.js';
import { injectTreeStyles } from './tree-styles.js';
import { treeLabel } from './tree-label.js';

const TREE_ENDPOINT = '/api/files/tree';

let TreeClass = null;
let FolderClass = null;
let FileClass = null;
let treeInstance = null;

/**
 * Inicializa el panel del workspace. Es idempotente y silencioso si los
 * elementos del DOM no existen (por ejemplo en páginas de prueba).
 */
export async function initWorkspaceUI() {
    const host = document.getElementById('workspace-tree');
    if (!host) {
        return;
    }

    try {
        const mod = await import('../vendor/file-tree/prod.js');
        TreeClass = mod.Tree;
        FolderClass = mod.Folder;
        FileClass = mod.File;

        treeInstance = new TreeClass();
        injectTreeStyles(treeInstance.shadowRoot);
        treeInstance.addEventListener('click', handleTreeClick);
        host.replaceChildren(treeInstance);

        const roots = await fetchTreeEntries(null);
        for (const item of roots) {
            treeInstance.append(createNode(item));
        }
    } catch (error) {
        console.error('Error inicializando el explorador de archivos:', error);
        showToast('No se pudo cargar el explorador de archivos', 'error');
    }
}

/**
 * Gestor único de eventos del árbol. Diferencia entre expandir carpeta
 * (carga diferida de hijos) y seleccionar fichero (apertura en el editor).
 */
async function handleTreeClick(event) {
    const detail = event.detail;
    if (!detail || !detail.target) {
        return;
    }
    const action = detail.action;
    const target = detail.target;

    if (action === 'open' && target.type === 'folder' && !target.__loaded) {
        event.waitUntil(loadChildren(target));
        return;
    }

    if (action === 'click' && target.type !== 'folder') {
        event.waitUntil(openEditorFile(target.absolutePath));
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
 * Construye un nodo (Folder o File) del componente a partir de la entrada
 * JSON del endpoint. Ancla la ruta absoluta como propiedad para poder
 * recuperarla en los eventos (el path del componente va URL-encoded).
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
    return node;
}

/**
 * Llama al endpoint del árbol. Sin path devuelve raíces virtuales; con
 * path, los hijos directos de esa carpeta.
 */
async function fetchTreeEntries(path) {
    const url = path
        ? `${TREE_ENDPOINT}?path=${encodeURIComponent(path)}`
        : TREE_ENDPOINT;
    const response = await fetch(url);
    if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
    }
    return await response.json();
}