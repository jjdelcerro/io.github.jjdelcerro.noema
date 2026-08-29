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
        postConfigMultivalue,
        fetchDirectories,
        callBackendAction,
        fetchFileContent,
        saveFileContent
        } from './api.js';
import { showToast } from './toast.js';

let configPanel = null;
let configTree = null;
let configContent = null;
let uiDescriptor = null;
let activeNodeElement = null;

// =============================================================================
// REGISTRO GLOBAL DE ACCIONES DE FRONTEND (WEB)
// =============================================================================

const webActionHandlers = new Map();

/**
 * Registra un manejador para una acción visual de frontend.
 * 
 * @param {string} actionName - Identificador de la acción (ej: 'OPEN_MODELS_EDITOR').
 * @param {Function} handler - Función asíncrona a ejecutar al pulsar el botón.
 */
export function registerWebAction(actionName, handler) {
  if (actionName && typeof handler === 'function') {
    webActionHandlers.set(actionName, handler);
  }
}

// 1. Abrir Consola Web H2 en nueva pestaña
registerWebAction('OPEN_H2WEBCONSOLE', async () => {
  try {
    const portVal = await fetchConfigValue('debug/h2_webport');
    const port = (portVal && typeof portVal === 'object' && portVal.value)
            ? portVal.value
            : (portVal || 8082);
    window.open(`http://${window.location.hostname}:${port}`, '_blank');
  } catch (e) {
    window.open(`http://${window.location.hostname}:8082`, '_blank');
  }
});

// 2. Editores de archivos de configuración (.properties)
registerWebAction('OPEN_MODELS_EDITOR', () => {
  openTextEditorDialog('var:/var/config/models.properties', 'Editar Modelos LLM');
});

registerWebAction('OPEN_PROVIDERS_URL_EDITOR', () => {
  openTextEditorDialog('var:/var/config/providers_urls.properties', 'Editar URLs de Proveedores');
});

registerWebAction('OPEN_PROVIDERS_APIKEY_EDITOR', () => {
  openTextEditorDialog('var:/var/config/apikeys.properties', 'Editar API Keys');
});

function isActionSupported(node) {
  if (!node || node.type !== 'action')
    return false;
  if (webActionHandlers.has(node.actionName))
    return true;
  if (node.scope === 'backend')
    return true;
  return false;
}

// =============================================================================
// INICIALIZACIÓN Y CONTROL DEL PANEL
// =============================================================================

export function initConfigUI() {
  configPanel = document.getElementById('config-panel');
  configTree = document.getElementById('config-tree');
  configContent = document.getElementById('config-content');

  const btnConfig = document.getElementById('btn-config');
  const btnMobileConfig = document.getElementById('btn-mobile-config');
  const btnClose = document.getElementById('btn-close-config');

  if (btnConfig) {
    btnConfig.addEventListener('click', toggleConfigPanel);
  }
  if (btnMobileConfig) {
    btnMobileConfig.addEventListener('click', toggleConfigPanel);
  }
  if (btnClose) {
    btnClose.addEventListener('click', closeConfigPanel);
  }
}

export function toggleConfigPanel() {
  if (!configPanel)
    return;

  if (configPanel.classList.contains('hidden')) {
    openConfigPanel();
  } else {
    closeConfigPanel();
  }
}

export async function openConfigPanel() {
  if (!configPanel)
    return;

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

export function closeConfigPanel() {
  if (configPanel) {
    configPanel.classList.add('hidden');
  }
}

// =============================================================================
// ÁRBOL DE NAVEGACIÓN
// =============================================================================

function renderTree(descriptor) {
  if (!configTree || !descriptor)
    return;

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

function createTreeNode(node, depth = 0) {
  const li = document.createElement('li');

  const label = document.createElement('span');
  label.className = 'tree-label';
  label.textContent = node.label;

  if (node.type === 'action') {
    const supported = isActionSupported(node);
    li.classList.add('leaf');
    if (!supported) {
      li.classList.add('action-disabled');
      li.title = 'No disponible en la interfaz web';
      li.setAttribute('aria-disabled', 'true');
    }
    li.appendChild(label);
    label.addEventListener('click', (e) => {
      e.stopPropagation();
      setActiveNode(li);
      renderNodeContent(node);
    });
    return li;
  }

  if (node.type === 'menu' && Array.isArray(node.childs)) {
    li.classList.add('branch');
    const row = document.createElement('div');
    row.className = 'tree-row';

    const toggle = document.createElement('button');
    toggle.type = 'button';
    toggle.className = 'branch-toggle';
    toggle.setAttribute('aria-label', `Expandir ${node.label}`);
    toggle.setAttribute('aria-expanded', 'false');
    toggle.textContent = '›';

    row.appendChild(toggle);
    row.appendChild(label);
    li.appendChild(row);

    const childrenUl = document.createElement('ul');
    childrenUl.hidden = true;
    node.childs.forEach(childNode => {
      const childLi = createTreeNode(childNode, depth + 1);
      if (childLi)
        childrenUl.appendChild(childLi);
    });
    if (childrenUl.childElementCount > 0)
      li.appendChild(childrenUl);

    toggle.addEventListener('click', (e) => {
      e.stopPropagation();
      const expanded = !childrenUl.hidden;
      childrenUl.hidden = expanded;
      li.classList.toggle('expanded', !expanded);
      toggle.setAttribute('aria-expanded', String(!expanded));
      toggle.setAttribute('aria-label', `${expanded ? 'Expandir' : 'Contraer'} ${node.label}`);
    });

    label.addEventListener('click', (e) => {
      e.stopPropagation();
      setActiveNode(li);
      renderNodeContent(node);
    });
  } else {
    li.classList.add('leaf');
    li.appendChild(label);
    label.addEventListener('click', (e) => {
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

// =============================================================================
// CONTENIDO DEL PANEL DERECHO
// =============================================================================

async function renderNodeContent(node) {
  if (!configContent)
    return;

  configContent.innerHTML = '';
  const fields = [];
  collectFields(node, fields);

  for (const field of fields) {
    const fieldEl = await renderField(field);
    if (fieldEl)
      configContent.appendChild(fieldEl);
  }
}

function collectFields(node, fields) {
  if (node.type === 'menu' && Array.isArray(node.childs)) {
    node.childs.forEach(child => collectFields(child, fields));
  } else {
    fields.push(node);
  }
}

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
      case 'action':
        createActionControl(container, fieldNode);
        break;
      default:
        return null;
    }
  } catch (error) {
    console.error(`Error renderizando campo ${fieldNode.variableName || fieldNode.actionName}:`, error);
  }

  return container;
}

// =============================================================================
// GENERADORES DE CONTROLES
// =============================================================================

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
    options = node.childs.map(c => ({key: c.label, value: c.value}));
  }

  options.forEach(opt => {
    const optionEl = document.createElement('option');
    optionEl.value = opt.value;
    optionEl.textContent = opt.label ?? opt.key;
    select.appendChild(optionEl);
  });

  try {
    const valObj = await fetchConfigValue(node.variableName);
    const currentVal = (typeof valObj === 'object' && valObj !== null && 'value' in valObj)
            ? valObj.value
            : (valObj ?? '');

    select.value = currentVal;
    select.dataset.previousValue = currentVal;
  } catch (e) {
  }

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
  }).catch(() => {
  });

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
  const wrapper = document.createElement('div');
  wrapper.className = 'config-paths-wrapper';

  const listBox = document.createElement('div');
  listBox.className = 'config-checkedlist-box';

  let savedData = [];
  try {
    const rawVal = await fetchConfigValue(node.variableName);
    if (Array.isArray(rawVal)) {
      savedData = rawVal;
    }
  } catch (e) {
    console.error(`Error cargando lista de ${node.variableName}:`, e);
  }

  let enabledStates = {};
  if (node.childEnabled) {
    const enabledQueries = [];
    domainItems.forEach(item => {
      const technicalName = item.value;
      enabledQueries.push({
        path: `${node.variableName}/${technicalName}/__enabled`,
        defaultValue: true,
        enabledExpression: node.childEnabled,
        context: {child: technicalName}
      });
    });

    try {
      enabledStates = await postConfigMultivalue(enabledQueries);
    } catch (e) {
      console.error('Error evaluando reglas de habilitación de checks:', e);
    }
  }

  const checkInputs = [];

  domainItems.forEach(item => {
    const technicalName = item.value;
    const enabledKey = `${node.variableName}/${technicalName}/__enabled`;

    let isChecked = true;
    if (Array.isArray(savedData) && savedData.length > 0) {
      const match = savedData.find(s => s && s.value === technicalName);
      if (match !== undefined && match.checked !== undefined) {
        isChecked = Boolean(match.checked);
      }
    }

    const row = document.createElement('div');
    row.className = 'config-field-checkbox';

    const chk = document.createElement('input');
    chk.type = 'checkbox';
    chk.checked = isChecked;

    const isControlEnabled = node.childEnabled ? parseBoolean(enabledStates[enabledKey], true) : true;
    chk.disabled = !isControlEnabled;
    if (chk.disabled) {
      chk.title = 'Deshabilitado por la configuración de control de acceso';
    }

    const itemLabel = document.createElement('label');
    itemLabel.textContent = item.label ?? item.key;
    if (chk.disabled) {
      itemLabel.title = chk.title;
    }

    chk.addEventListener('change', async () => {
      const checked = chk.checked;
      try {
        await setConfigChecked(node.variableName, technicalName, checked);
      } catch (error) {
        console.error(error);
        chk.checked = !checked;
        showToast(`No se pudo actualizar ${item.label ?? item.key}`, 'error');
      }
    });

    row.appendChild(chk);
    row.appendChild(itemLabel);
    listBox.appendChild(row);

    checkInputs.push({input: chk, technicalName});
  });

  const actionsBar = document.createElement('div');
  actionsBar.className = 'config-paths-actions';

  const btnSelectAll = document.createElement('button');
  btnSelectAll.type = 'button';
  btnSelectAll.className = 'config-paths-btn secondary-button';
  btnSelectAll.textContent = 'Marcar todas';

  const btnDeselectAll = document.createElement('button');
  btnDeselectAll.type = 'button';
  btnDeselectAll.className = 'config-paths-btn secondary-button';
  btnDeselectAll.textContent = 'Desmarcar todas';

  async function setAllStates(targetState) {
    for (const item of checkInputs) {
      if (!item.input.disabled && item.input.checked !== targetState) {
        item.input.checked = targetState;
        try {
          await setConfigChecked(node.variableName, item.technicalName, targetState);
        } catch (e) {
          console.error(e);
        }
      }
    }
  }

  btnSelectAll.addEventListener('click', () => setAllStates(true));
  btnDeselectAll.addEventListener('click', () => setAllStates(false));

  actionsBar.appendChild(btnSelectAll);
  actionsBar.appendChild(btnDeselectAll);

  wrapper.appendChild(listBox);
  wrapper.appendChild(actionsBar);
  container.appendChild(wrapper);
}

function parseBoolean(value, defaultValue) {
  if (typeof value === 'boolean')
    return value;
  if (typeof value === 'string') {
    if (value.toLowerCase() === 'true')
      return true;
    if (value.toLowerCase() === 'false')
      return false;
  }
  return value == null ? defaultValue : Boolean(value);
}

// =============================================================================
// CONTROL DE ACCIONES (FRONTEND, BACKEND Y UNSUPPORTED)
// =============================================================================

function createActionControl(container, node) {
  const button = document.createElement('button');
  button.type = 'button';

  const actionName = node.actionName;
  const isFrontend = webActionHandlers.has(actionName);
  const isBackend = node.scope === 'backend';

  if (isFrontend) {
    button.className = 'primary-button';
    button.textContent = node.label || `Ejecutar ${actionName}`;
    button.addEventListener('click', async () => {
      try {
        const handler = webActionHandlers.get(actionName);
        await handler();
      } catch (err) {
        console.error(`Error ejecutando acción web '${actionName}':`, err);
        showToast(`Error al ejecutar '${node.label}': ${err.message}`, 'error');
      }
    });
  } else if (isBackend) {
    button.className = 'primary-button';
    button.textContent = node.label || `Ejecutar ${actionName}`;
    button.addEventListener('click', async () => {
      button.disabled = true;
      const originalText = button.textContent;
      button.textContent = 'Ejecutando...';

      try {
        await callBackendAction(actionName);
        showToast(`Acción '${node.label}' ejecutada con éxito`, 'success');
      } catch (err) {
        console.error(`Error ejecutando acción de servidor '${actionName}':`, err);
        showToast(`Error al ejecutar '${node.label}': ${err.message}`, 'error');
      } finally {
        button.disabled = false;
        button.textContent = originalText;
      }
    });
  } else {
    button.className = 'config-paths-btn secondary-button';
    button.disabled = true;
    button.title = 'Acción no disponible en el cliente web';
    button.textContent = `${node.label} (No disponible en web)`;
  }

  container.appendChild(button);
}

// =============================================================================
// LISTA DE RUTAS (PATHS)
// =============================================================================

async function createPathsControl(container, node) {
  const listWrapper = document.createElement('div');
  listWrapper.className = 'config-paths-wrapper';

  let pathsArray = [];
  let selectedIndex = -1;

  try {
    const val = await fetchConfigValue(node.variableName);
    if (Array.isArray(val)) {
      pathsArray = val;
    }
  } catch (e) {
    console.error('Error cargando lista de rutas:', e);
  }

  const listBox = document.createElement('div');
  listBox.className = 'config-paths-list-box';

  const actionsBar = document.createElement('div');
  actionsBar.className = 'config-paths-actions';

  const btnAdd = document.createElement('button');
  btnAdd.type = 'button';
  btnAdd.className = 'config-paths-btn primary-button';
  btnAdd.textContent = 'Añadir Ruta...';

  const btnRemove = document.createElement('button');
  btnRemove.type = 'button';
  btnRemove.className = 'config-paths-btn secondary-button';
  btnRemove.textContent = 'Eliminar Seleccionado';
  btnRemove.disabled = true;

  actionsBar.appendChild(btnAdd);
  actionsBar.appendChild(btnRemove);

  function renderList() {
    listBox.innerHTML = '';
    btnRemove.disabled = (selectedIndex < 0 || selectedIndex >= pathsArray.length);

    if (pathsArray.length === 0) {
      const emptyMsg = document.createElement('div');
      emptyMsg.className = 'config-paths-empty';
      emptyMsg.textContent = '(Lista vacía)';
      listBox.appendChild(emptyMsg);
      return;
    }

    pathsArray.forEach((pathStr, index) => {
      const itemEl = document.createElement('div');
      itemEl.className = 'config-paths-list-item';
      if (index === selectedIndex) {
        itemEl.classList.add('selected');
      }
      itemEl.textContent = pathStr;

      itemEl.addEventListener('click', () => {
        selectedIndex = (selectedIndex === index) ? -1 : index;
        renderList();
      });

      listBox.appendChild(itemEl);
    });
  }

  btnAdd.addEventListener('click', async () => {
    try {
      const selectedPath = await openDirectoryPickerDialog();
      if (selectedPath && selectedPath.trim() !== '') {
        const cleanPath = selectedPath.trim();
        if (!pathsArray.includes(cleanPath)) {
          pathsArray.push(cleanPath);
          selectedIndex = pathsArray.length - 1;
          renderList();
          await syncPaths();
        } else {
          showToast('La ruta ya está incluida en la lista', 'info');
        }
      }
    } catch (error) {
      console.error('Error al explorar carpetas:', error);
      showToast('No se pudo abrir el explorador de carpetas', 'error');
    }
  });

  btnRemove.addEventListener('click', async () => {
    if (selectedIndex >= 0 && selectedIndex < pathsArray.length) {
      pathsArray.splice(selectedIndex, 1);
      selectedIndex = -1;
      renderList();
      await syncPaths();
    }
  });

  async function syncPaths() {
    try {
      await setConfigList(node.variableName, pathsArray);
    } catch (error) {
      console.error(error);
      showToast('No se pudo actualizar la lista de rutas', 'error');
    }
  }

  renderList();
  listWrapper.appendChild(listBox);
  listWrapper.appendChild(actionsBar);
  container.appendChild(listWrapper);
}

// =============================================================================
// MODAL: EXPLORADOR DE DIRECTORIOS
// =============================================================================

export function openDirectoryPickerDialog(startPath = '') {
  return new Promise((resolve) => {
    let currentPath = startPath;
    let parentPath = null;
    let canGoUp = false;
    let selectedSubdirPath = null;
    let directories = [];

    const backdrop = document.createElement('div');
    backdrop.className = 'dir-modal-backdrop';

    const dialog = document.createElement('div');
    dialog.className = 'dir-modal-dialog';

    const header = document.createElement('div');
    header.className = 'dir-modal-header';
    header.innerHTML = `
            <h3>Seleccionar Carpeta</h3>
            <button type="button" class="config-close-button" title="Cerrar">✕</button>
        `;

    const navBar = document.createElement('div');
    navBar.className = 'dir-modal-nav';

    const btnUp = document.createElement('button');
    btnUp.type = 'button';
    btnUp.className = 'dir-modal-btn-up';
    btnUp.textContent = '⇡ Subir';

    const pathDisplay = document.createElement('div');
    pathDisplay.className = 'dir-modal-path-display';

    navBar.appendChild(btnUp);
    navBar.appendChild(pathDisplay);

    const listBox = document.createElement('div');
    listBox.className = 'dir-modal-list';

    const footer = document.createElement('div');
    footer.className = 'dir-modal-footer';

    const btnCancel = document.createElement('button');
    btnCancel.type = 'button';
    btnCancel.className = 'config-paths-btn secondary-button';
    btnCancel.textContent = 'Cancelar';

    const btnSelect = document.createElement('button');
    btnSelect.type = 'button';
    btnSelect.className = 'config-paths-btn primary-button';
    btnSelect.textContent = 'Seleccionar esta carpeta';

    footer.appendChild(btnCancel);
    footer.appendChild(btnSelect);

    dialog.appendChild(header);
    dialog.appendChild(navBar);
    dialog.appendChild(listBox);
    dialog.appendChild(footer);
    backdrop.appendChild(dialog);
    document.body.appendChild(backdrop);

    async function loadPath(targetPath) {
      listBox.innerHTML = '<div class="dir-modal-empty">Cargando carpetas...</div>';
      selectedSubdirPath = null;

      try {
        const data = await fetchDirectories(targetPath);
        currentPath = data.currentPath;
        parentPath = data.parentPath;
        canGoUp = data.canGoUp;
        directories = data.directories || [];

        pathDisplay.textContent = currentPath;
        pathDisplay.title = currentPath;
        btnUp.disabled = !canGoUp;

        renderDirectoryItems();
      } catch (err) {
        console.error(err);
        listBox.innerHTML = `<div class="dir-modal-empty" style="color:#da6575;">No se pudo acceder a la ruta</div>`;
      }
    }

    function renderDirectoryItems() {
      listBox.innerHTML = '';

      if (directories.length === 0) {
        const empty = document.createElement('div');
        empty.className = 'dir-modal-empty';
        empty.textContent = '(No hay subcarpetas accesibles)';
        listBox.appendChild(empty);
        return;
      }

      directories.forEach((dir) => {
        const item = document.createElement('div');
        item.className = 'dir-modal-item';
        if (selectedSubdirPath === dir.path) {
          item.classList.add('selected');
        }

        item.innerHTML = `
                    <span class="folder-icon">📁</span>
                    <span class="folder-name">${dir.name}</span>
                `;

        item.addEventListener('click', () => {
          selectedSubdirPath = (selectedSubdirPath === dir.path) ? null : dir.path;
          renderDirectoryItems();
        });

        item.addEventListener('dblclick', () => {
          loadPath(dir.path);
        });

        listBox.appendChild(item);
      });
    }

    btnUp.addEventListener('click', () => {
      if (canGoUp && parentPath) {
        loadPath(parentPath);
      }
    });

    function closeDialog(result) {
      document.removeEventListener('keydown', handleKeyDown);
      if (backdrop.parentNode) {
        backdrop.parentNode.removeChild(backdrop);
      }
      resolve(result);
    }

    function handleKeyDown(e) {
      if (e.key === 'Escape') {
        closeDialog(null);
      }
    }
    document.addEventListener('keydown', handleKeyDown);

    header.querySelector('.config-close-button').addEventListener('click', () => closeDialog(null));
    btnCancel.addEventListener('click', () => closeDialog(null));

    btnSelect.addEventListener('click', () => {
      const finalPath = selectedSubdirPath || currentPath;
      closeDialog(finalPath);
    });

    loadPath(startPath);
  });
}

// =============================================================================
// MODAL: EDITOR DE TEXTO WEB
// =============================================================================

export function openTextEditorDialog(filePath, customTitle = '') {
  return new Promise(async (resolve) => {
    const displayTitle = customTitle || `Editor - ${filePath.replace(/^var:\//, '')}`;

    const backdrop = document.createElement('div');
    backdrop.className = 'dir-modal-backdrop';

    const dialog = document.createElement('div');
    dialog.className = 'editor-modal-dialog';

    const header = document.createElement('div');
    header.className = 'editor-modal-header';
    header.innerHTML = `
            <div>
                <h3>${displayTitle}</h3>
                <span class="editor-modal-filepath" title="${filePath}">${filePath}</span>
            </div>
            <button type="button" class="config-close-button" title="Cerrar">✕</button>
        `;

    const body = document.createElement('div');
    body.className = 'editor-modal-body';

    const textarea = document.createElement('textarea');
    textarea.className = 'editor-modal-textarea';
    textarea.spellcheck = false;
    textarea.placeholder = 'Cargando contenido del archivo...';
    textarea.disabled = true;

    body.appendChild(textarea);

    const footer = document.createElement('div');
    footer.className = 'editor-modal-footer';

    const statusLabel = document.createElement('span');
    statusLabel.className = 'editor-modal-status';

    const btnCancel = document.createElement('button');
    btnCancel.type = 'button';
    btnCancel.className = 'config-paths-btn secondary-button';
    btnCancel.textContent = 'Cancelar';

    const btnSave = document.createElement('button');
    btnSave.type = 'button';
    btnSave.className = 'config-paths-btn primary-button';
    btnSave.textContent = 'Guardar';
    btnSave.disabled = true;

    footer.appendChild(statusLabel);
    footer.appendChild(btnCancel);
    footer.appendChild(btnSave);

    dialog.appendChild(header);
    dialog.appendChild(body);
    dialog.appendChild(footer);
    backdrop.appendChild(dialog);
    document.body.appendChild(backdrop);

    textarea.addEventListener('keydown', (e) => {
      if (e.key === 'Tab') {
        e.preventDefault();
        const start = textarea.selectionStart;
        const end = textarea.selectionEnd;
        textarea.value = textarea.value.substring(0, start) + '    ' + textarea.value.substring(end);
        textarea.selectionStart = textarea.selectionEnd = start + 4;
      } else if ((e.ctrlKey || e.metaKey) && e.key === 's') {
        e.preventDefault();
        handleSave();
      }
    });

    try {
      const data = await fetchFileContent(filePath);
      textarea.value = data.content ?? '';
      textarea.disabled = false;
      btnSave.disabled = false;
      textarea.focus();
      statusLabel.textContent = `Líneas: ${textarea.value.split('\n').length}`;
    } catch (err) {
      console.error('Error cargando archivo:', err);
      textarea.value = '';
      textarea.placeholder = `Error al abrir el archivo: ${err.message}`;
      statusLabel.textContent = 'Error de lectura';
      showToast(`No se pudo abrir ${filePath}: ${err.message}`, 'error');
    }

    async function handleSave() {
      if (btnSave.disabled)
        return;

      btnSave.disabled = true;
      btnSave.textContent = 'Guardando...';
      statusLabel.textContent = 'Guardando cambios...';

      try {
        await saveFileContent(filePath, textarea.value);
        showToast('Archivo guardado correctamente', 'success');
        closeDialog(true);
      } catch (err) {
        console.error('Error guardando archivo:', err);
        showToast(`Error al guardar: ${err.message}`, 'error');
        btnSave.disabled = false;
        btnSave.textContent = 'Guardar';
        statusLabel.textContent = 'Error al guardar';
      }
    }

    function closeDialog(saved) {
      document.removeEventListener('keydown', handleGlobalKeyDown);
      if (backdrop.parentNode) {
        backdrop.parentNode.removeChild(backdrop);
      }
      resolve(saved);
    }

    function handleGlobalKeyDown(e) {
      if (e.key === 'Escape') {
        closeDialog(false);
      }
    }
    document.addEventListener('keydown', handleGlobalKeyDown);

    header.querySelector('.config-close-button').addEventListener('click', () => closeDialog(false));
    btnCancel.addEventListener('click', () => closeDialog(false));
    btnSave.addEventListener('click', handleSave);
  });
}
