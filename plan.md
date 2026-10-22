# Plan de implementacion: Sistema de Notas WebUI

Documento de trabajo derivado de `docs/ideas/01-En bruto/sistema-de-notas-webui-v1.md`,
acotado por las decisiones tomadas en sesion. Las tareas concretas viven en `plantasks.md`.

---

## Decisiones cerradas

| Tema | Decision |
|---|---|
| Almacenamiento | Un XML por nota en `.noema-agent/notes/YYYY/MM/{id}.xml` |
| Movimiento | Al guardar se mueve al mes corriente, **sin renombrar**. La resolucion `id -> path` se hace con walk |
| Formato | XML con el contenido en `CDATA`. La secuencia `]]>` se parte en dos secciones |
| Campos editables | `title`, `tags`, `content` |
| Campos gestionados | `id`, `created`, `updated` — los mantiene el editor, no el usuario |
| Formatos de escritura | El WebUI escribe Markdown en `content`; el agente escribe XML crudo con `file_write` |
| Modos de busqueda | `exact`, `tags`, `fuzzy` |
| `fuzzy` | Delega en `FileFuzzySearchUtils`. Sin cache ni indice de embeddings |
| Parametros fijos | `limit = 10000`, `minSimilarity = 0.5`. Van en codigo, no en la UI, y no entran en la clave de cache |
| Cache | Compartida por los tres modos: `search_{SHA1(mode|root|query)}.idx` en `var/cache/notes-search/` |
| Formato de cache | 6 lineas de cabecera, separador `---`, NDJSON con Gson |
| Campos de resultado | `id`, `title`, `tags`, `path`, `updated`, `score`. **Sin `snippet`** |
| Validacion de id | Patron `^\d{8}-\d{6}$` antes de tocar disco |
| `actual/` | Era errata. No existe ese nivel |

### Consecuencias asumidas

- Sin `snippet`, un resultado de `exact` no muestra la linea donde coincidio. Si se
  quiere resaltar al abrir, es trabajo del cliente.
- El XML ensucia el modo `fuzzy`: los chunks arrastran etiquetas y CDATA. Se mitiga
  parcialmente porque el contenido va en lineas propias.

### Deudas aceptadas, no implementadas

- Invalidacion de la cache cuando el agente escribe notas con `file_write` o cuando se
  editan a mano. No se anota en `todo.md`, por decision expresa.
- Permiso de escritura del agente en `.noema-agent/notes/`. Aplazado: no se ha verificado
  si `PATH_ACCESS_WRITE` lo permite. La seccion 5 del documento lo da por hecho.

---

## Pieza 0 — Modelo y almacen

Ficheros nuevos en `src/main/java/.../ui/webclient/workspace/notes/`:

- `Note.java` — POJO con `id`, `title`, `tags` (`List<String>`), `created`, `updated`
  (`Instant`) y `content`.
- `NoteStore.java` — unica puerta al disco. Recibe en el constructor el `Path` de `notes/`.

Operaciones:

- `list(String relativeFolder)` — hijos directos de una carpeta, para el arbol perezoso.
- `find(String id)` — walk sobre el arbol que devuelve el `Path` del XML cuyo basename
  coincide con el id, o `null`.
- `read(String id)` — devuelve `Note`.
- `save(Note note)` — si el fichero existe, parsea el DOM, muta solo `title`, `tags`,
  `content` y `updated` y **preserva el resto de nodos**; si no existe, crea el DOM desde
  cero con `id` y `created`. Despues mueve el fichero a `YYYY/MM/` del mes corriente.
- `delete(String id)`.

Serializacion del CDATA: sustituir `]]>` por `]]]]><![CDATA[>` para no romper el documento.

**Criterio de aceptacion:** crear, leer, editar, mover y borrar. Verificar que un nodo
`<author>` anadido a mano sobrevive a un guardado desde la API.

---

## Pieza 1 — Endpoints REST

Handlers nuevos en `.../ui/webclient/handlers/`, registrados en `NoemaWebServer` como
seccion `7. Notas`. Las rutas literales se registran **antes** que las que llevan `{id}`,
igual que el servidor hace ya con `/api/config/<path>`.

```
GET    /api/notes/tree?path=                     HandlerGetNotesTree
GET    /api/notes/search?q=&mode=&root=&page=    HandlerGetNoteSearch
GET    /api/notes/{id}                           HandlerGetNote
POST   /api/notes                                HandlerPostNote
PUT    /api/notes/{id}                           HandlerPutNote
DELETE /api/notes/{id}                           HandlerDeleteNote
```

La validacion del id vive en un unico sitio, no repetida en cada handler. Errores en JSON
`{error: ...}`, siguiendo el patron de los handlers existentes. El arbol devuelve solo
hijos directos, replicando el contrato de `HandlerGetFilesTree`.

**Criterio de aceptacion:** los seis endpoints con `curl` contra notas reales, incluido un
id con `../` que debe devolver 400.

---

## Pieza 2 — Motor de busqueda y cache

Ficheros nuevos: `NoteSearch.java` y `NoteSearchCache.java`.

- `exact` — busca el termino en `title`, `tags` y `content`. `score = 1.0`.
- `tags` — compara etiquetas. `score = 1.0`.
- `fuzzy` — delega en `FileFuzzySearchUtils` sobre el arbol de notas con glob `**/*.xml`,
  con `limit` y `minSimilarity` fijos. `score` = similitud.

Los tres producen la misma forma de resultado y la misma cabecera de cache. El campo
`TOTAL` se acompana de `TRUNCATED=true|false` para distinguir "hay 10000" de "hay al menos
10000".

Cache en `var/cache/notes-search/`: cabecera de seis lineas (`QUERY`, `MODE`, `ROOT`,
`TIMESTAMP`, `TOTAL`, `TRUNCATED`), separador `---`, NDJSON con Gson. LRU por `TIMESTAMP`
sobre los diez `.idx` mas recientes.

**Prerrequisito:** verificar la firma exacta de `FileFuzzySearchUtils.search`. El plan
asume que acepta `limit` y `minSimilarity`.

**Criterio de aceptacion:** lanzar la misma consulta dos veces devuelve lo mismo y la
segunda no recorre disco. Borrar el `.idx` y comprobar que se regenera.

---

## Pieza 3 — Tabbar de area (sin notas todavia)

Es la pieza que toca codigo existente y por eso va sola.

- `index.html` — envolver el contenido del area en `.work-area > (.area-tabbar + .area-body)`.
  El `#splitter-workspace` queda **fuera** del wrapper, como hermano.
- `styles.css` — mover `flex: 0 0 720px` de `.workspace-panel` a `.work-area`;
  `.workspace-panel` pasa a `flex: 1 1 auto; width: 100%`. Reapuntar
  `.app-shell.workspace-collapsed .workspace-panel` a `.work-area`. Reutilizar el lenguaje
  visual de `.conversation-tabbar` para `.area-tabbar`.
- `splitters.js` — cambiar el `beforeSelector` del splitter de workspace de
  `.workspace-panel` a `.work-area`, para no romper el `nextElementSibling`.
- `main.js` — el toggle de workspace y el atajo `Alt+E` operan sobre `.work-area`.
- Persistir la pestana activa en `localStorage` como `noema_active_area`.

**Criterio de aceptacion:** arrastrar el splitter, plegar y desplegar con `Alt+E`,
recargar y que persista la pestana. La conversacion y el editor funcionan igual que antes.

---

## Pieza 4 — Panel de notas

- `.notes-panel` en `index.html`, oculto por defecto.
- `notes-ui.js` — arbol con carga perezosa, reutilizando el Web Component
  `vendor/file-tree/prod.js`, pidiendo hijos a `/api/notes/tree`.
- Boton de nueva nota.
- Contrato de vista: cada panel expone `isDirty()` y `confirmLeave()`, y la tabbar de area
  los consulta antes de conmutar.

**Criterio de aceptacion:** abrir y cerrar notas en el arbol y conmutar entre Workspace y
Notas sin recargar.

---

## Pieza 5 — Editor de notas

- Tres campos: `title`, `tags` y el cuerpo en Ace con `mode-markdown`.
- Guardar contra `PUT /api/notes/{id}`. Tras el guardado, refrescar el arbol y actualizar
  el `path` local, porque el fichero se ha movido.
- Dirty propio, comparando con el contenido original cargado, siguiendo el patron de
  `editor-ui.js`.

**Criterio de aceptacion:** editar, guardar, comprobar que el fichero se movio al mes
corriente y que al reabrir se lee bien con acentos y saltos de linea.

---

## Pieza 6 — Busqueda en la UI

- Caja de busqueda con selector de modo.
- Paginacion sobre `page`, pintando `id`, `title`, `tags`, `updated` y `score`.
- Al abrir un resultado de `exact`, resaltar la coincidencia en el editor.

**Criterio de aceptacion:** buscar en los tres modos y navegar entre paginas hasta el
ultimo resultado.

---

## Fuera de alcance

No se implementa `ImagePruningOperation`, no se toca el control de acceso, no se invalida
la cache cuando el agente escribe y no se anade nada al `todo.md`.
