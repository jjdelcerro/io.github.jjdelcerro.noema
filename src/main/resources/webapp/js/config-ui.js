 
/**
 * Módulo de gestión del panel de configuración dinámico.
 */

import {
    fetchConfigUI,
    fetchConfigDomain,
    fetchConfigValue,
    setConfigValue,
    setConfigList,
    setConfigChecked,
    postConfigMultivalue
} from './api.js';

import { showToast } from './toast.js';

let configPanel = null;
let configTree = null;
let configContent = null;
let uiDescriptor = null;
let activeNodeElement = null;

/**
 * Inicializa las referencias del DOM y vincula los eventos del panel.
 */
export function initConfigUI() {
    configPanel = document.getElementById('config-panel');
    configTree = document.getElementById('config-tree');
    configContent = document.getElementById('config-content');

    const btnConfig = document.getElementById('btn-config');
    const btnClose = document.getElementById('btn-close-config');

    if (btnConfig) {
        btnConfig.addEventListener('click', toggleConfigPanel);
    }
    if (btnClose) {
        btnClose.addEventListener('click', closeConfigPanel);
    }
}

/**
 * Muestra u oculta el panel de configuración.
 */
export function toggleConfigPanel() {
    if (!configPanel) return;

    if (configPanel.classList.contains('hidden')) {
        openConfigPanel();
    } else {
        closeConfigPanel();
    }
}

/**
 * Abre el panel, descarga descriptor UI si es necesario y renderiza el árbol.
 */
export async function openConfigPanel() {
    if (!configPanel) return;

    configPanel.classList.remove('hidden');

    if (!uiDescriptor) {
        try {
            uiDescriptor = await fetchConfigUI();
            renderTree(uiDescriptor);
        } catch (error) {
            console.error('Error cargando descriptor UI:', error);
            showToast('No se pudo cargar la interfaz de configuración', 'error');
        }
    }
}

/**
 * Cierra el panel de configuración.
 */
export function closeConfigPanel() {
    if (configPanel) {
        configPanel.classList.add('hidden');
    }
}

/**
 * Renderiza el árbol de navegación a partir de la estructura del descriptor.
 */
function renderTree(descriptor) {
    if (!configTree || !descriptor) return;

    configTree.innerHTML = '';
    const rootUl = document.createElement('ul');

    if (descriptor.childs) {
        descriptor.childs.forEach(childNode => {
            const li = createTreeNode(childNode);
            if (li) {
                rootUl.appendChild(li);
            }
        });
    }

    configTree.appendChild(rootUl);
}

/**
 * Crea recursivamente un elemento de nodo del árbol.
 */
function createTreeNode(node) {
    if (node.type === 'action') {
        return null; // Ignorar acciones
    }

    const li = document.createElement('li');
    li.textContent = node.label;

    if (node.type === 'menu' && Array.isArray(node.childs)) {
        const validChildren = node.childs.filter(c => c.type !== 'action');
        if (validChildren.length === 0) {
            return null; // Omitir ramas compuestas únicamente de acciones
        }

        li.classList.add('branch');
        li.addEventListener('click', (e) => {
            e.stopPropagation();
            setActiveNode(li);
            renderNodeContent(node);
        });
    } else {
        li.classList.add('leaf');
        li.addEventListener('click', (e) => {
            e.stopPropagation();
            setActiveNode(li);
            renderNodeContent(node);
        });
    }

    return li;
}

function setActiveNode(element) {
    if (activeNodeElement) {
        activeNodeElement.classList.remove('active');
    }
    element.classList.add('active');
    activeNodeElement = element;
}

/**
 * Renderiza el panel de contenido según el nodo seleccionado.
 */
async function renderNodeContent(node) {
    if (!configContent) return;

    configContent.innerHTML = '';

    if (node.type === 'menu' && Array.isArray(node.childs)) {
        // Renderizar todos los parámetros no-action del grupo
        for (const child of node.childs) {
            if (child.type !== 'action') {
                const fieldEl = await renderField(child);
                if (fieldEl) configContent.appendChild(fieldEl);
            }
        }
    } else if (node.type !== 'action') {
        // Renderizar una única hoja
        const fieldEl = await renderField(node);
        if (fieldEl) configContent.appendChild(fieldEl);
    }
}

/**
 * Fabrica y retorna el elemento HTML para un parámetro individual.
 */
async function renderField(fieldNode) {
    const container = document.createElement('div');
    container.className = 'config-field';

    const label = document.createElement('label');
    label.textContent = fieldNode.label;
    container.appendChild(label);

    try {
        switch (fieldNode.type) {
            case 'inputstring':
                createInputStringControl(container, fieldNode);
                break;
            case 'combo':
            case 'selectoption':
                await createSelectControl(container, fieldNode);
                break;
            case 'checkbox':
                createCheckboxControl(container, fieldNode);
                break;
            case 'checkedlist':
                await createCheckedListControl(container, fieldNode);
                break;
            case 'paths':
                await createPathsControl(container, fieldNode);
                break;
            default:
                return null;
        }
    } catch (error) {
        console.error(`Error renderizando campo ${fieldNode.variableName}:`, error);
    }

    return container;
}

/* --- Generadores de Controles por Tipo --- */

async function createInputStringControl(container, node) {
    const input = document.createElement('input');
    input.type = 'text';

    try {
        const valObj = await fetchConfigValue(node.variableName);
        const currentVal = (typeof valObj === 'object' && valObj !== null && 'value' in valObj) 
            ? valObj.value 
            : (valObj ?? '');
        
        input.value = currentVal;
        input.dataset.previousValue = currentVal;
    } catch (e) {
        input.value = '';
    }

    input.addEventListener('change', async () => {
        const newVal = input.value;
        const oldVal = input.dataset.previousValue;

        try {
            await setConfigValue(node.variableName, newVal);
            input.dataset.previousValue = newVal;
        } catch (error) {
            console.error(error);
            input.value = oldVal;
            showToast(`No se pudo actualizar ${node.label}`, 'error');
        }
    });

    container.appendChild(input);
}

async function createSelectControl(container, node) {
    const select = document.createElement('select');

    let options = [];
    if (typeof node.childs === 'string') {
        options = await fetchConfigDomain(node.childs);
    } else if (Array.isArray(node.childs)) {
        options = node.childs.map(c => ({ key: c.label, value: c.value }));
    }

    options.forEach(opt => {
        const optionEl = document.createElement('option');
        optionEl.value = opt.value;
        optionEl.textContent = opt.key;
        select.appendChild(optionEl);
    });

    try {
        const valObj = await fetchConfigValue(node.variableName);
        const currentVal = (typeof valObj === 'object' && valObj !== null && 'value' in valObj) 
            ? valObj.value 
            : (valObj ?? '');
        
        select.value = currentVal;
        select.dataset.previousValue = currentVal;
    } catch (e) {}

    select.addEventListener('change', async () => {
        const newVal = select.value;
        const oldVal = select.dataset.previousValue;

        try {
            await setConfigValue(node.variableName, newVal);
            select.dataset.previousValue = newVal;
        } catch (error) {
            console.error(error);
            select.value = oldVal;
            showToast(`No se pudo actualizar ${node.label}`, 'error');
        }
    });

    container.appendChild(select);
}

function createCheckboxControl(container, node) {
    container.className = 'config-field config-field-checkbox';
    
    const input = document.createElement('input');
    input.type = 'checkbox';

    fetchConfigValue(node.variableName).then(valObj => {
        const currentVal = typeof valObj === 'object' && valObj !== null && 'value' in valObj
            ? valObj.value
            : Boolean(valObj);
        input.checked = Boolean(currentVal);
    }).catch(() => {});

    input.addEventListener('change', async () => {
        const checked = input.checked;
        try {
            await setConfigValue(node.variableName, checked);
        } catch (error) {
            console.error(error);
            input.checked = !checked;
            showToast(`No se pudo actualizar ${node.label}`, 'error');
        }
    });

    container.appendChild(input);
}

async function createCheckedListControl(container, node) {
    const domainItems = await fetchConfigDomain(node.childs);
    const listContainer = document.createElement('div');
    listContainer.className = 'config-paths-list';

    const queries = domainItems.map(item => ({
        path: `${node.variableName}/${item.key}`,
        defaultValue: false
    }));

    let states = {};
    try {
        states = await postConfigMultivalue(queries);
    } catch (e) {}

    domainItems.forEach(item => {
        const row = document.createElement('div');
        row.className = 'config-field-checkbox';

        const chk = document.createElement('input');
        chk.type = 'checkbox';
        chk.checked = Boolean(states[`${node.variableName}/${item.key}`]);

        const itemLabel = document.createElement('label');
        itemLabel.textContent = item.value; // Propiedad value como texto descriptivo

        chk.addEventListener('change', async () => {
            const checked = chk.checked;
            try {
                await setConfigChecked(node.variableName, item.key, checked);
            } catch (error) {
                console.error(error);
                chk.checked = !checked;
                showToast(`No se pudo actualizar ${item.value}`, 'error');
            }
        });

        row.appendChild(chk);
        row.appendChild(itemLabel);
        listContainer.appendChild(row);
    });

    container.appendChild(listContainer);
}

async function createPathsControl(container, node) {
    const listContainer = document.createElement('div');
    listContainer.className = 'config-paths-list';

    let pathsArray = [];
    try {
        const val = await fetchConfigValue(node.variableName);
        if (Array.isArray(val)) {
            pathsArray = val;
        }
    } catch (e) {}

    function renderRows() {
        listContainer.innerHTML = '';
        pathsArray.forEach((pathStr, index) => {
            const itemRow = document.createElement('div');
            itemRow.className = 'config-path-item';

            const input = document.createElement('input');
            input.type = 'text';
            input.value = pathStr;

            input.addEventListener('change', () => {
                pathsArray[index] = input.value.trim();
                syncPaths();
            });

            const btnDel = document.createElement('button');
            btnDel.className = 'icon-button';
            btnDel.textContent = '✕';
            btnDel.addEventListener('click', () => {
                pathsArray.splice(index, 1);
                renderRows();
                syncPaths();
            });

            itemRow.appendChild(input);
            itemRow.appendChild(btnDel);
            listContainer.appendChild(itemRow);
        });

        const btnAdd = document.createElement('button');
        btnAdd.className = 'primary-button';
        btnAdd.style.alignSelf = 'flex-start';
        btnAdd.textContent = '+ Añadir ruta';
        btnAdd.addEventListener('click', () => {
            pathsArray.push('');
            renderRows();
        });

        listContainer.appendChild(btnAdd);
    }

    async function syncPaths() {
        const cleanArray = pathsArray.filter(p => p.trim() !== '');
        try {
            await setConfigList(node.variableName, cleanArray);
        } catch (error) {
            console.error(error);
            showToast(`No se pudo actualizar la lista de rutas`, 'error');
        }
    }

    renderRows();
    container.appendChild(listContainer);
}