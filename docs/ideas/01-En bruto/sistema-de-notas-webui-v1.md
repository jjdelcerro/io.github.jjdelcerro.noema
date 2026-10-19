# Especificación Técnica y Funcional: Sistema de Notas (WebUI)
**Proyecto Noema**  
*Documento de Diseño y Arquitectura de Software*

---

## Visión General

El subsistema de **Notas** es una extensión integrada en el WebUI de Noema concebida para la captura rápida, persistencia estructurada y recuperación (exacta, por etiquetas y semántica) de ideas, apuntes y fragmentos técnicos. 

El diseño descarta el uso de bases de datos externas o motores propietarios: las notas residen como **ficheros XML en disco** bajo el directorio del agente (`.noema-agent/notes/`), garantizando la soberanía de los datos, la ausencia de *vendor lock-in* y permitiendo que el propio Agente Noema acceda a ellas de manera natural mediante sus herramientas estándar del sistema de archivos (`file_read`, `file_grep`, `file_fuzzygrep`).

---

## 1. Topología de Archivos y Formato de Datos

### 1.1. Ubicación y Estructura en Disco
Todas las notas se almacenan en una jerarquía de carpetas basada en **Año y Mes**:

```text
.noema-agent/
└── notes/
    └── YYYY/
        └── MM/
            ├── YYYYMMDD-HHmmss.xml
            └── YYYYMMDD-HHmmss.xml
```

* **Ruta base:** `.noema-agent/notes/`. Al situarse fuera de `var/`, no está restringido por las protecciones del núcleo privado, lo que permite al agente consultar y modificar notas con permisos estándar.
* **Profundidad en disco:** Se limita estrictamente a `Año (YYYY) / Mes (MM)` para evitar fragmentación excesiva de directorios en el sistema de archivos.

### 1.2. Nomenclatura e Identificador Único (`idnota`)
* El nombre del archivo y el identificador único (`idnota`) se generan a partir del timestamp del momento de su creación con el formato:  
  `YYYYMMDD-HHmmss` (ejemplo: `20261019-160700`).
* El `idnota` es **inmutable**: aunque la nota se mueva de carpeta en el futuro, conserva el mismo identificador y nombre de archivo.

### 1.3. Regla de Movimiento Temporal (Ciclo de Vida Activo)
Para reflejar el estado "vivo" de las notas y evitar que ideas activas queden enterradas en el pasado:
* Al crear una nota (`POST`), se guarda en la carpeta correspondiente al año y mes actual.
* Al actualizar una nota (`PUT`), el backend comprueba si la carpeta física donde reside coincide con el año y mes en curso:
  * **Si el mes/año ha cambiado:** El archivo XML se **mueve físicamente** a la carpeta del mes actual (`notes/YYYY/MM/actual`).
  * El atributo `created` se mantiene inalterado; el atributo `updated` se actualiza al timestamp actual.
  * *Resultado:* Las carpetas de meses pasados actúan automáticamente como archivo histórico de notas inactivas.

### 1.4. Especificación del Formato de Archivo XML
Cada nota es un archivo XML bien formado. Para permitir almacenar cualquier tipo de contenido (código Java, caracteres XML como `<`, `>`, `&`, markdown complejo), el cuerpo de la nota se encapsula obligatoriamente en una sección `<![CDATA[ ... ]]>`.

#### Estructura XML:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<note id="20261019-160700" created="2026-10-15T10:00:00Z" updated="2026-10-19T16:15:00Z">
    <title>Optimización buffer memoria H2</title>
    <tags>
        <tag>h2</tag>
        <tag>rendimiento</tag>
        <tag>arquitectura</tag>
    </tags>
    <content><![CDATA[
# Optimización buffer memoria H2

Revisar el tamaño de página en `SQLProviderImpl`. Actualmente el consumo en caliente es elevado:

```java
if (size < threshold) {
    allocateBuffer();
}
```
    ]]></content>
</note>
```

#### Restricciones de validación:
1. `<title>`: Cadena de texto obligatoria (no vacía).
2. `<tags>`: Contenedor opcional. Si contiene elementos `<tag>`, deben estar normalizados en minúsculas y sin espacios al inicio/final.
3. `<content>`: Texto Markdown puro envuelto en `<![CDATA[ ... ]]>`.

---

## 2. Arquitectura de la Interfaz Web (WebUI)

La interfaz se organiza en un esquema de **3 paneles** gobernado por dos divisores ajustables (*splitters*): un divisor vertical principal que separa navegación/búsqueda (izquierda) del editor (derecha), y un divisor horizontal en la columna izquierda.

```text
+----------------------------------------------+-------------------------------------------------------------+
|  [IZQUIERDA - ARRIBA] ÁRBOL                  | [DERECHA] EDITOR / VISUALIZADOR                            |
|                                              |                                                            |
|  [ + Nueva Nota ]                            | Título: [ Optimización buffer H2 ]                         |
|  --------------------------------------------| Tags:   [ h2, rendimiento, arquitectura ]                  |
|  ▼ 2026                                      | [ Guardar (Ctrl+S) ]  [ 👁️ Previsualizar ]   [ 🗑️ Borrar ] |
|    ▼ 10 - Octubre                            | ---------------------------------------------------------- |
|      ▼ Semana 42 (12 al 18 de Octubre)       |                                                            |
|        • Optimización buffer H2 (*)          |                                                            |
|        • Ajustes tokenizer ONNX              |  Cuerpo en Ace Editor (mode-markdown)                      |
|      ► Semana 43 (19 al 25 de Octubre)       |  o Vista previa renderizada con marked.js                  |
+----------------------------------------------+                                                            |
|  [IZQUIERDA - ABAJO] BÚSQUEDA Y RESULTADOS   |                                                            |
|                                              |                                                            |
|  Ámbito: [ 📁 /2026/10/ ]      [✖ Todo]     |                                                            |
|  [ Texto o tag a buscar...             ]     |                                                            |
|  [ 🔍 Exacta ]  [ 🏷️ Tags ]  [ 🧠 Semántica] |                                                            |
|  --------------------------------------------+                                                            |
|  Resultados (25 de 42) - Orden: updated DESC |                                                            |
|  • Optimización buffer H2 (19/10 16:15)      |                                                            |
|    Tags: [h2] [rendimiento]                  |                                                            |
+----------------------------------------------+------------------------------------------------------------+
```

### 2.1. Panel Superior Izquierdo: Navegación y Árbol de Notas
* **Botón `[ + Nueva Nota ]`:** Situado en la cabecera. Limpia el formulario y el editor Ace, genera un estado fresco y posiciona el foco en el campo de título.
* **Estructura del Árbol (Semanas del Año):**
  * Agrupación: `Año` -> `Mes` -> `Semana del Año`.
  * Cálculo de semanas: Basado en las semanas naturales del año según el estándar ISO (`WeekFields.ISO.weekOfWeekBasedYear()`).
  * Etiqueta visual de la semana: `Semana XX (del DD al DD de Mes)`.
  * Nodos hoja: Muestran el texto del `<title>` de la nota. Si el título está vacío, muestran los primeros 30 caracteres del contenido.
* **Selección:** Al hacer clic en un nodo de nota, se comprueba el estado de cambios sin guardar (*dirty state*). Si procede, se solicita confirmación y se carga en el editor. Al mismo tiempo, se actualiza el selector de ámbito en el panel de búsqueda inferior.

### 2.2. Panel Inferior Izquierdo: Búsqueda y Resultados
* **Selector de Ámbito (Scope):** Muestra la ruta del último elemento seleccionado en el árbol (ej. `2026/10/`). Incluye un botón `[✖ Todo]` que restablece el ámbito a la raíz (`/`) para buscar en todas las notas sin necesidad de navegar en el árbol.
* **Caja de búsqueda y Modos:**
  * `[ 🔍 Exacta ]`: Búsqueda textual literal (case-insensitive) sobre título y contenido.
  * `[ 🏷️ Tags ]`: Filtrado estricto por coincidencias en las etiquetas `<tag>`.
  * `[ 🧠 Semántica ]`: Búsqueda vectorial (*fuzzy*) calculada al vuelo contra el contenido de las notas mediante el modelo local ONNX (`EmbeddingsService`).
* **Lista de Resultados:**
  * Ordenada estrictamente por fecha de actualización descendente (`updated` DESC).
  * Cada elemento muestra: Título, fecha formateada, píldoras visuales de los tags y un extracto (*snippet*) de coincidencia contextual.
  * Al hacer clic en un resultado: Se carga en el editor derecho (respetando el control de cambios sin guardar) y se actualiza el ámbito.

### 2.3. Panel Derecho: Editor y Visualizador
* **Fila 1 (Cabecera):**
  * Input de texto para el `<title>`.
  * Botón `[ Guardar ]` con indicador visual de cambios pendientes (ej. asterisco `*`).
  * Botón toggle `[ 👁️ Ver / Editar ]`: Alterna entre el editor de código y el HTML renderizado.
  * Botón `[ 🗑️ Borrar ]`: Invoca el borrado previa confirmación en diálogo modal.
* **Fila 2 (Metadatos):**
  * Input para `Tags`: Texto plano con etiquetas separadas por comas (ej. `h2, buffer, optimizacion`).
* **Fila 3 (Cuerpo):**
  * **Modo Edición:** Instancia de **Ace Editor** configurada con `mode-markdown.js` y el tema de la aplicación (`theme-dracula.js`).
  * **Modo Previsualización:** Contenedor sanitizado mediante `purify.min.js` y renderizado con `marked.min.js`.

### 2.4. Ergonomía y Atajos de Teclado
* **Carga Inicial por Defecto:** Al abrir el WebUI, se solicita el árbol y se carga automáticamente en el editor la última nota modificada (`latestNoteId`). Si la libreta está vacía, se inicia en modo nueva nota.
* **`Ctrl+S` / `Cmd+S`:** Interceptado a nivel global en el editor e inputs (`e.preventDefault()`) para ejecutar el guardado sin abrir el diálogo nativo del navegador.
* **`Ctrl+Alt+N`:** Atajo global para invocar "+ Nueva Nota".
* **Control de Modificaciones (*Dirty State*):** Si el usuario modifica el título, tags o cuerpo y pulsa sobre otra nota en el árbol o en los resultados de búsqueda, un diálogo modal interrumpe la navegación:
  > *"Tienes cambios sin guardar en '[Título]'. ¿Deseas guardarlos antes de continuar?"*  
  > `[ Guardar y cambiar ]` `[ Descartar ]` `[ Cancelar ]`

---

## 3. Especificación de Endpoints HTTP (API REST)

Todos los endpoints están integrados bajo el servidor Javalin de Noema (`NoemaWebServer`).

### 3.1. `GET /api/notes/tree`
Genera la jerarquía para renderizar el árbol de navegación.
* **Parámetros:** Ninguno.
* **Respuesta `200 OK` (JSON):**
```json
{
  "latestNoteId": "20261019-160700",
  "tree": [
    {
      "year": 2026,
      "months": [
        {
          "month": 10,
          "name": "Octubre",
          "weeks": [
            {
              "weekNumber": 42,
              "label": "Semana 42 (12 al 18 de Octubre)",
              "notes": [
                {
                  "id": "20261019-160700",
                  "title": "Optimización buffer memoria H2",
                  "updated": "2026-10-19T16:15:00Z",
                  "path": "2026/10/20261019-160700.xml"
                }
              ]
            }
          ]
        }
      ]
    }
  ]
}
```

---

### 3.2. `GET /api/notes/{id}`
Obtiene el contenido completo y metadatos de una nota.
* **Parámetros de ruta:** `{id}` (nombre del archivo sin extensión).
* **Respuesta `200 OK` (JSON):**
```json
{
  "id": "20261019-160700",
  "title": "Optimización buffer memoria H2",
  "tags": ["h2", "rendimiento", "arquitectura"],
  "content": "# Optimización buffer memoria H2\n\nRevisar el tamaño...",
  "created": "2026-10-15T10:00:00Z",
  "updated": "2026-10-19T16:15:00Z",
  "path": "2026/10/20261019-160700.xml"
}
```
* **Errores:** `404 Not Found` si el archivo no existe en el árbol de notas.

---

### 3.3. `POST /api/notes`
Crea una nueva nota en disco.
* **Payload (JSON):**
```json
{
  "title": "Ajustes del parser Groovy",
  "tags": ["scripting", "seguridad"],
  "content": "Detalles del SecureASTCustomizer..."
}
```
* **Comportamiento:**
  1. Genera el ID con el timestamp UTC actual: `YYYYMMDD-HHmmss`.
  2. Determina la ruta: `.noema-agent/notes/YYYY/MM/`.
  3. Fija `created` y `updated` al timestamp actual.
  4. Serializa el XML envolviendo `content` en `<![CDATA[...]]>`.
  5. Invalida la caché de búsqueda.
* **Respuesta `201 Created` (JSON):** Objeto completo de la nota creada con su `id` y `path`.

---

### 3.4. `PUT /api/notes/{id}`
Actualiza el contenido, metadatos y ubicación física de una nota existente.
* **Payload (JSON):**
```json
{
  "title": "Ajustes del parser Groovy (Revisado)",
  "tags": ["scripting", "seguridad", "ast"],
  "content": "Contenido actualizado..."
}
```
* **Comportamiento:**
  1. Localiza el fichero `{id}.xml` en el árbol.
  2. Compara el directorio actual del archivo con el mes/año presente.
  3. Si difieren, **mueve** el archivo a `.noema-agent/notes/YYYY/MM/actual/{id}.xml`.
  4. Conserva el valor de `created` original y actualiza `updated` a la fecha actual.
  5. Sobrescribe el XML.
  6. Invalida la caché de búsqueda.
* **Respuesta `200 OK` (JSON):** Objeto de la nota actualizado, reflejando el nuevo `updated` y su nuevo `path`.

---

### 3.5. `DELETE /api/notes/{id}`
Elimina una nota permanentemente del sistema de archivos.
* **Parámetros de ruta:** `{id}`.
* **Comportamiento:**
  1. Localiza y elimina el archivo físico `{id}.xml`.
  2. Invalida la caché de búsqueda.
* **Respuesta `204 No Content`.**

---

### 3.6. `GET /api/notes/search`
Ejecuta búsquedas paginadas con soporte de caché.
* **Query Parameters:**
  * `mode` (requerido): `exact` | `tag` | `fuzzy`.
  * `query` (requerido): Texto o etiqueta a buscar.
  * `root` (opcional): Subruta sobre la que limitar la búsqueda (ej. `2026/10`). Por defecto vacía (raíz de notas).
  * `limit` (opcional, default `25`): Cantidad máxima de resultados por página.
  * `offset` (opcional, default `0`): Desplazamiento para paginación.
* **Respuesta `200 OK` (JSON):**
```json
{
  "total": 42,
  "limit": 25,
  "offset": 0,
  "hasMore": true,
  "results": [
    {
      "id": "20261019-160700",
      "title": "Optimización buffer memoria H2",
      "tags": ["h2", "rendimiento"],
      "path": "2026/10/20261019-160700.xml",
      "updated": "2026-10-19T16:15:00Z",
      "snippet": "...revisar el tamaño de página en SQLProvider...",
      "score": 0.94
    }
  ]
}
```

---

## 4. Motor de Búsqueda y Sistema de Caché en Disco

Dado que no se utiliza una base de datos indexada en segundo plano, las búsquedas sobre múltiples archivos XML pueden suponer I/O de disco reiterativo. Para permitir una paginación reactiva sin retener miles de objetos en el Heap de la JVM, se implementa una caché temporal en disco.

### 4.1. Modos de Ejecución al Vuelo
* **`exact`:** Recorre los archivos XML bajo `root`, extrae título/contenido y busca subcadenas literales (sin distinción de mayúsculas).
* **`tag`:** Comprueba la existencia exacta (case-insensitive) del tag en los nodos `<tag>` del XML.
* **`fuzzy`:** Calcula el embedding vectorial de la `query` mediante ONNX local y evalúa al vuelo la similitud coseno contra los textos bajo `root`.

Todos los resultados encontrados (hasta un tope de **10.000 coincidencias**) se ordenan en memoria por `updated` DESC antes de persistir la caché.

### 4.2. Estructura del Fichero de Caché (Cabecera + NDJSON)
* **Directorio de caché:** `.noema-agent/var/cache/notes-search/`.
* **Identificador de fichero:**  
  `hash = SHA1(mode + "|" + root + "|" + query.trim().toLowerCase())`  
  Nombre: `search_{hash}.idx`.

#### Formato interno:
Consta de **6 líneas de cabecera fija** seguidas de una línea por cada resultado en formato JSON compacto (NDJSON):

```text
QUERY=optimizacion buffer
MODE=exact
ROOT=2026/10
TIMESTAMP=1760888000
TOTAL=42
---
{"id":"20261019-160700","title":"Optimizacion buffer H2","tags":["h2","rendimiento"],"path":"2026/10/20261019-160700.xml","updated":"2026-10-19T16:15:00Z","snippet":"...tamano de pagina...","score":1.0}
{"id":"20261012-091522","title":"Ajustes H2","tags":["h2"],"path":"2026/10/20261012-091522.xml","updated":"2026-10-12T10:00:00Z","snippet":"...buffer pool...","score":1.0}
```

### 4.3. Algoritmo de Paginación de Bajo Consumo (Streaming)
Cuando se solicita una página subsecuente (`offset > 0`):
1. El backend detecta la existencia de `search_{hash}.idx`.
2. Lee las primeras 6 líneas para extraer `TOTAL`.
3. Abre un `Stream<String>` secuencial saltando las líneas necesarias sin cargarlas en RAM:
   ```java
   int skipCount = 6 + offset;
   try (Stream<String> lines = Files.lines(cacheFile)) {
       List<SearchResult> page = lines.skip(skipCount)
                                      .limit(limit)
                                      .map(line -> gson.fromJson(line, SearchResult.class))
                                      .toList();
   }
   ```
4. Coste de memoria: Exclusivamente los elementos devueltos en la página solicitada.

### 4.4. Política de Retención LRU (Máximo 10 Búsquedas)
* Cada vez que se consulta o genera un `.idx`, se actualiza su marca de último acceso (`lastModified`).
* Si el directorio contiene más de 10 archivos `.idx`, se ordenan por fecha de último acceso y se eliminan los más antiguos hasta mantener exactamente 10.

### 4.5. Política de Invalidación
Para evitar inconsistencias tras crear, editar o borrar notas:
* Cualquier mutación (`POST`, `PUT`, `DELETE`) vacía de forma sincrónica el directorio `.noema-agent/var/cache/notes-search/`.
* La próxima búsqueda reconstruirá la caché fresca con los datos actualizados.

---

## 5. Interacción con el Agente Noema

Al residir en `.noema-agent/notes/`, las notas forman parte del espacio visible del sistema de archivos local para el agente. Esto habilita los siguientes casos de uso sin desarrollos adicionales en el Core:

* **Inspección de notas:** Mediante la herramienta `file_read`, el agente puede consultar cualquier nota directamente si el usuario le indica su identificador o título.
* **Búsqueda por el agente:** Las herramientas `file_find`, `file_grep` y `file_fuzzygrep` pueden orientarse al directorio `.noema-agent/notes/` para que el agente recupere notas relevantes durante una sesión de razonamiento.
* **Creación asistida:** El agente puede utilizar `file_write` para redactar notas estructuradas en el formato XML acordado cuando se le pida expresamente que guarde una síntesis o decisión técnica.

---

## 6. Extras y Propuestas Adicionales
*(Esta sección contiene funcionalidades complementarias sugeridas que aportan valor al flujo de trabajo, distinguiéndose de lo estrictamente acordado).*

### [EXTRA 1] Acción "Enviar Nota al Chat" (UI)
* **Descripción:** En la barra de herramientas del editor, añadir un botón con el icono del agente (`[ 🤖 Discutir con Noema ]`).
* **Comportamiento:** Inyecta automáticamente el contenido de la nota activa en el campo de entrada del chat principal (`chat-ui.js`) con un prefijo tipo: *"He estado revisando esta nota: [Título]. ¿Podemos profundizar en...?"*.

### [EXTRA 4] Autocompletado de Tags Existentes
* **Descripción:** En el `<input>` de tags del editor, ofrecer sugerencias tipo *typeahead* basadas en los tags existentes en el árbol `GET /api/notes/tree`. Evita que el usuario cree tags duplicados por erratas tipográficas (ej. `h2` vs `h-2`).
  Tendriamos que mantener un fichero con los tags existentes actualizado cada vez de se graba o borra una nota, y un endpoint para que el ui lo consulte.
  
  