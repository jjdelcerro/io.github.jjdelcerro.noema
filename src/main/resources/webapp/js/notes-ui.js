/**
 * Modulo de gestion del panel de notas: explorador de notas guardadas y
 * creacion de notas nuevas.
 *
 * El arbol se construye de forma diferida, igual que el del workspace: las
 * raices al inicializar y los hijos de cada carpeta cuando el usuario la
 * expande por primera vez.
 *
 * La apertura de una nota se delega al editor mediante import dinamico, de
 * modo que este modulo no depende de que el editor este cargado todavia.
 */

import { showToast } from './toast.js';
import { injectTreeStyles } from './tree-styles.js';
import { treeLabel } from './tree-label.js';

const TREE_ENDPOINT = '/api/notes/tree';
const NOTES_ENDPOINT = '/api/notes';

let TreeClass = null;
let FolderClass = null;
let FileClass = null;
let treeInstance = null;

/**
 * Inicializa el panel de notas. Es idempotente y silencioso si los
 * elementos del DOM no existen (por ejemplo en paginas de prueba).
 */
export async function initNotesUI() {
    const host = document.getElementById('notes-tree');
    if (!host) {
        return;
    }

    try {
        const mod = await import('../vendor/file-tree/prod.js');
        TreeClass = mod.Tree;
        FolderClass = mod.Folder;
        FileClass = mod.File;
    } catch (error) {
        console.error('Error cargando el componente de arbol:', error);
        showToast('No se pudo cargar el explorador de notas', 'error');
        return;
    }

    const newButton = document.getElementById('btn-new-note');
    if (newButton) {
        newButton.addEventListener('click', createNewNote);
    }

    window.addEventListener('noema:notes-changed', () => {
        refreshNotesTree();
    });

    // No se abre ninguna nota al entrar en la vista: el panel derecho
    // arranca vacio y la nota se carga cuando el usuario la elige en el
    // arbol o en los resultados de busqueda.
    await rebuildTree();
}

/**
 * Reconstruye el arbol desde cero. Se usa al arrancar y cada vez que el
 * conjunto de notas cambia (alta, guardado con posible cambio de carpeta
 * o borrado).
 */
export async function refreshNotesTree() {
    await rebuildTree();
}

async function rebuildTree() {
    const host = document.getElementById('notes-tree');
    if (!host || !TreeClass) {
        return;
    }
    try {
        const roots = await fetchNotesEntries(null);
        const instance = new TreeClass();
        injectTreeStyles(instance.shadowRoot);
        instance.addEventListener('click', handleTreeClick);
        for (const item of roots) {
            instance.append(createNode(item));
        }
        host.replaceChildren(instance);
        treeInstance = instance;
    } catch (error) {
        console.error('Error construyendo el arbol de notas:', error);
        showToast('No se pudo cargar la lista de notas', 'error');
    }
}

/**
 * Gestor unico de eventos del arbol: expandir carpeta (carga diferida) o
 * abrir nota en el editor.
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

    if (action === 'click' && target.type !== 'folder' && target.noteId) {
        publishScope(target);
        event.waitUntil(openNoteFromTree(target));
        return;
    }

    // Al seleccionar una carpeta sin expandirla tambien se fija el ambito,
    // para poder acotar la busqueda a esa rama del arbol.
    if (action === 'open' && target.type === 'folder') {
        publishScope(target);
    }
}

async function openNoteFromTree(node) {
    let opened = false;
    try {
        const mod = await import('./note-editor.js');
        opened = await mod.openNote({ id: node.noteId, path: node.notePath, title: node.noteTitle });
    } catch (error) {
        console.error('Error abriendo la nota:', node.noteId, error);
        showToast('No se pudo abrir la nota', 'error');
        return;
    }
    // Si el usuario cancela el cambio de nota, el componente de arbol ya ha
    // marcado el nodo como seleccionado. Se repinta el arbol para que la
    // seleccion visible vuelva a corresponder con la nota abierta.
    if (opened === false) {
        await refreshNotesTree();
    }
}

/**
 * Avisa al modulo de busqueda del ambito seleccionado. Una carpeta aporta
 * su propia ruta; un fichero aporta la carpeta que lo contiene.
 */
function publishScope(node) {
    if (!node || !node.notePath) {
        return;
    }
    const path = node.type === 'folder' ? node.notePath : folderOf(node.notePath);
    window.dispatchEvent(new CustomEvent('noema:notes-scope', {
        detail: { path: path || '' }
    }));
}

/**
 * Carpeta contenedora de una ruta relativa.
 */
function folderOf(path) {
    const parts = path.split('/');
    parts.pop();
    return parts.join('/');
}

/**
 * Carga los hijos de una carpeta contra el endpoint del servidor.
 */
async function loadChildren(folder) {
    if (!folder.notePath) {
        return;
    }
    try {
        const children = await fetchNotesEntries(folder.notePath);
        folder.append(...children.map(createNode));
    } catch (error) {
        console.error('Error cargando hijos de:', folder.notePath, error);
        showToast('No se pudo cargar el contenido de la carpeta', 'error');
    } finally {
        folder.__loaded = true;
    }
}

/**
 * Crea una nota vacia y la abre en el editor.
 */
async function createNewNote() {
    const button = document.getElementById('btn-new-note');
    // La guardia va antes del POST: si el usuario cancela, no debe quedar una
    // nota vacia creada en disco.
    const editor = await import('./note-editor.js');
    if (!(await editor.confirmDiscardIfDirty())) {
        return;
    }
    if (button) {
        button.disabled = true;
    }
    try {
        const response = await fetch(NOTES_ENDPOINT, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                title: 'Nueva nota',
                tags: [],
                content: ''
            })
        });
        if (!response.ok) {
            throw new Error(`HTTP ${response.status}`);
        }
        const note = await response.json();
        await refreshNotesTree();
        const mod = await import('./note-editor.js');
        await mod.openNote({ id: note.id, title: note.title });
    } catch (error) {
        console.error('Error creando una nota:', error);
        showToast('No se pudo crear la nota', 'error');
    } finally {
        if (button) {
            button.disabled = false;
        }
    }
}

/**
 * Construye un nodo (Folder o File) del componente a partir de la entrada
 * JSON del endpoint. El componente muestra el nombre del nodo, asi que en
 * los ficheros se usa el titulo de la nota cuando existe.
 *
 * Se anclan id, ruta relativa y titulo como propiedades para poder
 * recuperarlos en los eventos.
 */
function createNode(item) {
    let node;
    if (item.type === 'folder') {
        node = new FolderClass(item.name);
    } else {
        // El componente codifica el nombre que recibe, asi que se le entrega
        // ya saneado (ver tree-label.js). El titulo original se conserva en
        // node.noteTitle.
        node = new FileClass([], treeLabel(item.title || item.name));
    }
    node.notePath = item.path;
    node.noteId = item.id || null;
    node.noteTitle = item.title || null;
    return node;
}

/**
 * Llama al endpoint del arbol. Sin path devuelve las raices; con path, los
 * hijos directos de esa carpeta.
 */
async function fetchNotesEntries(path) {
    const url = path
        ? `${TREE_ENDPOINT}?path=${encodeURIComponent(path)}`
        : TREE_ENDPOINT;
    const response = await fetch(url);
    if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
    }
    return await response.json();
}
