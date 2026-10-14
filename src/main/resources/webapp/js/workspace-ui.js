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
 * Inyecta estilos correctivos en el shadow root del arbol:
 *   - Oculta el sufijo "0 bytes" en ficheros (el tamano real no lo
 *     conocemos: el componente lo calcula a partir de un Blob vacio).
 *   - Redefine las variables --svg-* con colores concretos, porque los
 *     SVG originales usan currentColor y dentro de un background-image
 *     eso resuelve siempre a negro (invisible sobre fondo oscuro).
 *
 * Las custom properties heredan a traves del shadow DOM; el color de un
 * SVG cargado como background-image no. De ahi redefinirlas aqui en vez
 * de desde styles.css.
 */
function injectTreeStyles(shadowRoot) {
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
        node = new FileClass([], item.name);
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