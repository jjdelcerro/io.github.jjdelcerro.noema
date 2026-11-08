
# Estado del Panel de Espacio de Trabajo y Editor — Noema Web

## 1. Contexto

La spec `especificacion-tecnica-de-diseno-panel-de-espacio-de-trabajo-y-editor-integrado-para-noema-web-v1.md` proponía evolucionar la UI web desde un modelo de "chat con visor de contexto" hacia un entorno de trabajo con tres paneles (sidebar / workspace / conversación), editor Ace integrado, árbol de ficheros, guardado con `Ctrl+S`, y en una fase posterior integración cognitiva con el agente (consciencia del editor, apertura remota de ficheros, diagnósticos MCP).

La spec se dividió en bloques. Se decidió empezar por el esqueleto: **layout + árbol + editor básico + multi-pestaña**, dejando fuera —temporalmente— guardado, integración con el splitter, integración cognitiva con el agente y diagnósticos.

## 2. Lo implementado

### 2.1. Backend

- **`GET /api/files/tree`** en `NoemaWebServer`.
  - Sin `path`: devuelve las raíces virtuales. Actualmente se genera a partir de `AgentAccessControl.getAllowedPaths()` con la ruta completa como nombre (pendiente de renombrar a algo legible).
  - Con `path`: hijos directos, ordenados (directorios primero, luego alfabético).
  - Filtro de higiene: excluye `.git`, `,jv` y `.noema-agent/var`.
  - Pendiente: filtro de `.noema-agent` completo y ruido del IDE (`.classpath`, `.project`, `.factorypath`).

### 2.2. Frontend — layout

- **Tres paneles**: sidebar (colapsable) / workspace (colapsable) / conversación.
- **`main-column` y `main-header`**: el header pasó a ser de la columna completa, no solo de la conversación.
- **Toggle del workspace**: botón en `.header-left`, atajo `Alt+E`, persistencia en `localStorage` (`noema_workspace_collapsed`). Estado por defecto: colapsado.
- **Dos splitters arrastrables** (`js/splitters.js`):
  - `#splitter-workspace` entre workspace y conversación.
  - `#splitter-tree` entre árbol y editor.
  - Clamp con mínimos, persistencia en `localStorage`, doble clic para volver al ancho por defecto.

### 2.3. Frontend — árbol

- **Componente `@webreflection/file-tree`** (vendored en `vendor/file-tree/prod.js`).
- Integración en `js/workspace-ui.js`:
  - Carga raíces al inicializar.
  - Carga diferida de hijos al expandir carpeta.
  - Click en fichero → delegado a `editor-ui.js`.
- La ruta absoluta se ancla como propiedad `node.absolutePath` porque el `path` interno del componente va URL-encoded.

### 2.4. Frontend — editor

- **Ace Editor** (vendored en `vendor/ace/`), cargado con `<script>` al final del `<body>`. Doce modos + tema `dracula`.
- **`js/editor-ui.js`**:
  - Una instancia `ace.edit()` en `.workspace-editor`.
  - Multi-pestaña con `ace.createEditSession()`. Cada fichero conserva undo, cursor y scroll.
  - Barra de pestañas dinámica en `#workspace-editor-tabs`.
  - `closeFile`, `getActivePath`, `resizeEditor`.
- Detección de modo por extensión. Desconocidos → `ace/mode/text`.
- `useWorker: false` (no se han vendoreado workers).
- Estado: **read-only** hasta que llegue el guardado.

## 3. Estado actual (funciona)

- Layout de tres paneles, con sidebar y workspace colapsables.
- Árbol carga raíces y expande con carga diferida.
- **Click en fichero abre pestaña** en la barra del editor.
- Toggle del workspace (`Alt+E`) funciona.

## 4. Bugs conocidos (no resueltos)

### 4.1. El contenido del fichero no aparece en el editor

Al hacer clic en un fichero del árbol:

- Se crea la pestaña correspondiente arriba (`editor-tab`).
- **El contenido del fichero NO se muestra.** El área del editor queda vacía.

**Hipótesis (sin verificar):** Ace mide su contenedor `0×0` porque `initEditorUI()` se ejecuta cuando el workspace está colapsado (`display: none`). Cuando el workspace se abre, Ace no recalcula su layout. Se intentó añadir un `ResizeObserver` sobre `.workspace-editor`, pero el síntoma no ha cambiado. **No se ha confirmado si el `ResizeObserver` llega a dispararse** ni si `editor.resize()` corrige la disposición una vez el contenedor tiene tamańo real.

### 4.2. El splitter árbol-editor no se mueve hasta hacer clic en un fichero

- El splitter `#splitter-tree` **no responde al arrastre** inicialmente.
- **Tras abrir un fichero**, el splitter empieza a responder.
- **Hipótesis (sin verificar):** el `.workspace-editor-pane` tiene ancho 0 mientras Ace no lo ocupa, así que `clampWidth` calcula `maxBefore ≈ 5` y bloquea el movimiento. Al abrir un fichero, Ace establece su layout y el editor-pane adquiere ancho real, desbloqueando el clamp. **No confirmado.**

### 4.3. Los cambios propuestos para 4.1 y 4.2 no han surtido efecto

Se han aplicado:

- `ResizeObserver` en `editor-ui.js`.
- `refreshSplitters()` en `splitters.js`, invocado en `rAF` al abrir el workspace.
- Diferir el clamp inicial de splitters cuando el panel está oculto.

**Resultado:** el comportamiento es idéntico al anterior. **No sabemos si los cambios se aplicaron correctamente, si el `ResizeObserver` dispara, o si hay otro problema que los eclipsa.**

## 5. Lo que NO está hecho (fuera de esta fase)

- **Guardado** con `Ctrl+S`, marca *dirty* (`●`), `readOnly` por política (`nom_writable_paths`).
- **Integración del editor con el splitter** vía `resizeEditor()` desde `splitters.js`. Aunque el `ResizeObserver` debería cubrirlo, no se ha validado.
- **Consciencia del editor por el agente** (`EditorAwarenessOperation` en la memoria proyectada).
- **Tool `editor_open_file`** y evento SSE `editor_open`.
- **Endpoint `POST /api/editor/state`**.
- **Fase 2 de la spec**: diagnósticos MCP, anotaciones en Ace.
- **Saneo de `styles.css`** (tokens) y migración de estilos del XSL.
- **Nombres legibles de raíces del árbol** (`workspace` en vez de ruta absoluta).

## 6. Archivos tocados en esta fase

| Fichero | Estado |
|---|---|
| `index.html` | Reestructurado: `main-column`, `main-header`, tres paneles, pestañas, scripts de Ace |
| `styles.css` | Nuevos bloques: `.workspace-panel`, `.workspace-editor-tabs`, `.editor-tab`, `.splitter`, `.main-column`, `.main-panel` |
| `js/main.js` | Imports y arranque de splitters, editor, workspace |
| `js/workspace-ui.js` | Integración con file-tree y delegación al editor |
| `js/editor-ui.js` | **Nuevo.** Gestión de Ace y pestañas |
| `js/splitters.js` | **Nuevo.** Divisores arrastrables |
| `NoemaWebServer.java` | `handleGetFilesTree` + `getFilesystemRootNodes` (stub ampliado con `AgentAccessControl`) |
| `vendor/fetch-vendor.sh` | Añadido file-tree y Ace |
| `vendor/file-tree/` | Nuevo (descargado) |
| `vendor/ace/` | Nuevo (descargado) |

## 7. Próximos pasos sugeridos

Ordenado por prioridad, sin pretender ser exhaustivo:

1. **Diagnosticar los dos bugs abiertos con DevTools abierto**, no por inspección de código:
   - En el árbol, tras hacer clic en un fichero, mirar en consola si hay errores.
   - Inspeccionar `.workspace-editor` en el inspector y comprobar sus dimensiones reales (`width`, `height`).
   - Verificar si el `<div class="ace_editor">` interno está presente y con qué dimensiones.
   - Comprobar si el `ResizeObserver` dispara (un `console.log` dentro del callback basta).
2. **Asegurar que `initEditorUI` se ejecuta con el contenedor visible**, o forzar un `editor.resize()` tras la primera apertura del workspace.
3. **Proteger `clampWidth`** contra `afterEl` con ancho 0 (tratar como "mínimo editorial" en vez de 0).
4. **Revisar `workspace-ui.js`** por si quedaran referencias a `textareaEl` u otros restos de la versión anterior.

## 8. Notas para retomar

- **Los scripts de Ace se cargan siempre** (eager). Se decidió así por simplicidad; el coste es ~650 KB que viajan aunque el editor nunca se abra. Aceptable en local, revisable si se despliega.
- **El guardado está diferido** hasta que se integre el splitter y se haya validado el editor. El editor está en `readOnly` provisional.
- **Los estilos del árbol (`file-tree`) son los del componente**, encapsulados en shadow DOM. Personalizarlos requiere inyectar un `CSSStyleSheet` vía `shadowRoot.adoptedStyleSheets.push(...)`. Aparcado.
- **La spec original sigue siendo la referencia** para lo que falta. Este documento describe el estado real, no reemplaza a la spec.

  
  
  