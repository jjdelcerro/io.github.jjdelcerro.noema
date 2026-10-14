# Especificación Técnica de Diseño: Panel de Espacio de Trabajo y Editor Integrado para Noema Web



---

## 1. Resumen Ejecutivo y Objetivos

El propósito de esta ampliación es evolucionar la interfaz web de Noema desde un modelo de *"chat con visor de contexto"* hacia un **entorno de trabajo y co-programación interactivo** (en la línea de entornos como Cursor, Windsurf o los Artifacts de Claude).

### Objetivos Principales
1. **Supervisión sin fricción**: Permitir al desarrollador examinar el árbol del proyecto y el código modificado por el agente en paralelo a la conversación, eliminando la necesidad de alternar constantemente entre pestañas o salir al IDE.
2. **Co-edición y guardado directo**: Proveer un editor ligero en el navegador que permita realizar ajustes manuales rápidos con soporte de guardado inmediato (`Ctrl + S`).
3. **Consciencia situacional bidireccional**: 
   * Que el agente pueda forzar la apertura de un archivo y saltar a una línea concreta para señalársela al usuario.
   * Que el agente conozca de forma pasiva y efímera qué archivo y qué líneas tiene el usuario seleccionadas en pantalla (resolviendo el problema de las referencias implícitas o *deíxis* como *"¿por qué falla esto?"*).

---

## 2. Arquitectura Visual y Disposición de Paneles (Layout)

La ventana principal (`.app-shell`) pasa de una estructura de dos paneles a un esquema de **tres paneles horizontales**:

```text
+--------------------+--------------------------------------+--------------------------------------+
| Terminal Sidebar   | Panel Editor (Workspace)             | Panel de Conversación                |
| (Colapsable)       | (Colapsable, cerrado por defecto)    | (Siempre visible / Principal)        |
|                    |                                      |                                      |
| - Marca            | +------------------+---------------+ | - Header: Título, Pestañas, Tools    |
| - Terminal activo  | | Árbol Archivos   | Tabs Editor   | |   [Conversación] [Contexto Proy.]    |
| - Acciones globales| | (@webreflection/ | Breadcrumbs   | |                                      |
| - Configuración    | |  file-tree)      | Lienzo Ace    | | - Historial de Chat / Contexto XML   |
|                    | +------------------+---------------+ | - Composer (Input de mensaje)        |
+--------------------+--------------------------------------+--------------------------------------+
```

### 2.1. Comportamiento y Ergonomía del Sidebar de Terminales
* **Ubicación**: Extremo izquierdo (ancho base: 260px).
* **Control de colapso**: 
  * Se añade un botón de colapso (`«` o icono de panel) en la cabecera/pie del sidebar.
  * Al cerrarse, su ancho pasa a `0px` con animación de transición CSS.
  * Aparece un botón discreto de restauración (`☰` / `◧`) en el extremo izquierdo de la cabecera de la conversación para reabrirlo.
* **Persistencia**: El estado abierto/cerrado se guarda en `localStorage` (`noema_sidebar_collapsed`) para evitar saltos entre recargas.

### 2.2. Comportamiento del Panel de Editor
* **Ubicación**: Entre el sidebar de terminales y la conversación.
* **Estado por defecto**: **Colapsado por defecto**. Al iniciar sesión, la interfaz muestra exclusivamente el chat para mantener la experiencia limpia y ligera.
* **Mecanismos de apertura**:
  1. **Manual**: Mediante un botón toggle en la cabecera de la conversación (`[ ◫ Editor ]`) o el atajo de teclado global `Alt + E`.
  2. **Proactiva / Remota**: Si el agente ejecuta la herramienta `editor_open_file`, el panel se despliega automáticamente en pantalla sin requerir intervención del usuario.
* **Ajuste visual**: Al cambiar la visibilidad del panel, se invoca `editor.resize()` en Ace Editor para recalcular la disposición de líneas y el cursor sin parpadeos.

---

## 3. Subcomponentes del Panel de Editor

El panel del editor se divide internamente en dos áreas: un explorador de archivos a la izquierda (~220–260px) y el área de edición a la derecha.

```text
+------------------------------+--------------------------------------------------------+
| Explorador (@webreflection)  | Pestañas: [Main.java  x] [pom.xml * x]                 |
|                              | Migas:    📁 workspace › src › main › java › Main.java |
| > workspace/                 +--------------------------------------------------------+
|   > src/                     | 1 | package io.github.jjdelcerro.noema;                |
|     > main/                  | 2 |                                                    |
|       - Main.java            | 3 | public class Main {                                |
|   - pom.xml                  | 4 |     public static void main(String[] args) {       |
| > external_data/             | 5 |         ...                                        |
+------------------------------+--------------------------------------------------------+
```

### 3.1. Árbol de Archivos (`@webreflection/file-tree`)
* **Integración**: Componente Web vanilla sin dependencias empaquetado en `vendor/` vía `fetch-vendor.sh`.
* **Carga Perezosa (Lazy Loading)**:
  * Al montar el árbol, se consulta el endpoint de raíces virtuales.
  * Los nodos de tipo directorio no cargan sus hijos por adelantado. Al recibir el evento `open`, se dispara la petición al backend para obtener los hijos de esa subcarpeta concreta y se añaden dinámicamente al nodo.
* **Eventos de interacción**:
  * Clic en directorio: alterna el despliegue y dispara la carga si no estaba cargado.
  * Clic en fichero: solicita su contenido y abre (o enfoca) su pestaña correspondiente en el editor.

### 3.2. Barra de Migas de Pan (Breadcrumbs)
* Franja horizontal compacta situada entre las pestañas y el editor de código.
* Muestra la ruta completa jerárquica del archivo activo (ej. `workspace › src › main › java › Main.java`).
* Proporciona contexto espacial inmediato sin saturar el ancho de las pestañas individuales.

### 3.3. Sistema Multi-Pestaña y Sesiones de Ace Editor
* **Uso de `EditSession`**:
  * Existe **una única instancia** de `ace.edit(...)` en el DOM.
  * Por cada archivo abierto se crea y almacena en memoria una instancia de `ace.createEditSession(content, mode)`.
  * Al cambiar de pestaña, se ejecuta `editor.setSession(openFiles.get(path).session)`.
  * **Ventaja crítica**: Se mantiene de forma totalmente independiente para cada fichero el historial de deshacer/rehacer (`Ctrl + Z`), la posición de scroll y la posición del cursor.
* **Gestión de Pestañas**:
  * Cada pestaña contiene: nombre corto del fichero, botón de cierre (`✕`) y tooltip nativo (`title`) con la ruta absoluta.
  * **Indicador de cambios pendientes**: Si el usuario edita el documento, se marca un estado *dirty* y se visualiza un círculo (`●`) en la pestaña.

### 3.4. Motor de Edición (Ace Editor)
* **Empaquetado**: Ficheros estáticos minificados en `vendor/` descargados con `fetch-vendor.sh` (`ace.js`, tema oscuro tipo Dracula/OneDark, y modos `mode-java.js`, `mode-xml.js`, `mode-json.js`, `mode-markdown.js`, `mode-sh.js`, `mode-groovy.js`).
* **Detección de sintaxis**: Según la extensión del archivo, se asocia el modo correspondiente (`ace/mode/java`, etc.).
* **Comando Guardar (`Ctrl + S`)**:
  * Ace intercepta el atajo de teclado nativo.
  * Envía el contenido actual al endpoint `POST /api/files/content`.
  * Limpia el indicador *dirty* y muestra un aviso emergente (*toast*) de éxito.
* **Respeto de Políticas de Seguridad**:
  * Si el archivo pertenece a las rutas bloqueadas para escritura (`nom_writable_paths`), la sesión se configura como `editor.setReadOnly(true)` y se pinta un icono de candado (🔒) en la pestaña.

---

## 4. Contratos y Endpoints del Backend (`NoemaWebServer`)

La comunicación cliente-servidor se basa en tres endpoints REST y un evento de canal SSE.

### 4.1. `GET /api/files/tree?path={optionalPath}`
Proporciona la estructura jerárquica del sistema de archivos por niveles.

* **Caso 1: Sin parámetro `path` (Raíz)**:
  * Devuelve la lista de raíces virtuales accesibles:
    1. Una carpeta virtual llamada `workspace` vinculada a `agent.getPaths().getWorkspaceFolder()`.
    2. Una carpeta por cada ruta configurada en `agent.getAccessControl().getAllowedPaths()` (excluyendo el propio workspace para evitar redundancias).
  * Salida JSON:
    ```json
    [
      { "name": "workspace", "path": "/ruta/real/al/proyecto", "type": "directory" },
      { "name": "documentacion_externa", "path": "/ruta/real/a/docs", "type": "directory" }
    ]
    ```
* **Caso 2: Con parámetro `path` (Subcarpeta)**:
  * **Validación de seguridad**: Verifica obligatoriamente `agent.getAccessControl().isPathAccessible(path, PATH_ACCESS_READ)`. Si está fuera del sandbox o en `nom_readable_paths`, devuelve HTTP `403 Forbidden`.
  * **Filtrado de higiene**: Oculta archivos de control interno de Noema (`/.noema-agent/var/`), respaldos de JavaRCS (`,jv`) y metadatos de Git (`.git`).
  * **Salida**: Lista de hijos directos ordenada (directorios primero, luego archivos alfabéticamente).

### 4.2. Endpoints de Contenido (Existentes)
* **`GET /api/files/content?file={path}`**: Lee y retorna el contenido UTF-8 del archivo tras validar permisos de lectura.
* **`POST /api/files/content`**: Recibe `{ file: path, content: text }`, valida permisos en `AgentAccessControl` (verificando que no esté en `nom_writable_paths`) y persiste el archivo en disco.

### 4.3. `POST /api/editor/state` (Sincronización Reactiva de Telemetría)
Permite al navegador comunicar el estado de visualización actual del usuario al servidor.
* **Llamada desde el frontend**: Disparada con técnica de *debounce* (~300ms) al cambiar de pestaña activa o al modificarse la selección de texto en Ace Editor.
* **Payload**:
  ```json
  {
    "terminalId": "default",
    "openFiles": ["src/Main.java", "pom.xml"],
    "activeFile": "src/Main.java",
    "selection": {
      "startLine": 45,
      "endLine": 58,
      "text": "public void execute() { ... }"
    }
  }
  ```
* **Gestión en el servidor**: `NoemaWebServer` almacena este snapshot en un mapa en memoria concurrente indexado por `terminalId`. La consulta posterior de cualquier herramienta o componente cognitivo es instantánea (0 ms, sin bloqueos de red).

---

## 5. Integración Cognitiva con el Agente

La integración entre el editor y el razonador de Noema opera en tres niveles complementarios:

### 5.1. Nivel 1: Acción Proactiva del Agente (Efector hacia la UI)
* **Herramienta `editor_open_file`**:
  * **Parámetros**: `path` (obligatorio), `line` (opcional).
  * **Mecanismo**: El backend recibe la orden del LLM y emite un evento a través del canal SSE existente (`activeSseControl`):
    ```json
    event: "editor_open"
    data: { "path": "src/Main.java", "line": 45 }
    ```
  * **Reacción del cliente web**: Despliega el panel del editor si estaba colapsado, enfoca la pestaña correspondiente y ejecuta `editor.gotoLine(line)`.

### 5.2. Nivel 2: Herramientas de Consulta Explícita (Bajo demanda)
* **`editor_get_state`**: Devuelve la lista de pestañas abiertas y la ruta del archivo activo en pantalla.
* **`editor_get_selection`**: Devuelve el fragmento de texto seleccionado y sus números de línea. Útil cuando el modelo necesita confirmar el bloque completo tras una instrucción del usuario.

### 5.3. Nivel 3: Consciencia Periférica Efímera (`EditorAwarenessOperation`)
Implementa una nueva operación dentro del pipeline de memoria proyectada (`ProjectedMemoryOperation`):

```text
Pipeline de Proyección:
[Prompt Base + Consolidada] -> [Turnos Recientes] -> [Operations Pipeline] -> [Unified Notification] -> LLM
                                                                 │
                                                    EditorAwarenessOperation
                                                                 │
                                         "[CONSCIENCIA DE EDITOR] Archivo activo: Main.java (Ln 45-58)"
```

* **Prioridad**: `PRIORITY = 25` (alineada con las operaciones sensoriales y temporales).
* **Comportamiento**:
  * Consulta el estado del editor almacenado en memoria para el subcanal actual.
  * Si hay un archivo activo o selección, inyecta un aviso sintetizado en la lista de `notifications`:
    ```text
    [CONSCIENCIA DE EDITOR]
    Archivo en pantalla: src/main/java/io/github/jjdelcerro/noema/lib/impl/AgentImpl.java
    Pestañas abiertas: [AgentImpl.java, pom.xml]
    Líneas seleccionadas por el usuario: 142-168
    ```
* **Ventajas Críticas**:
  * **Amnesia gestionada**: La notificación es efímera. Existe solo durante la generación del turno actual; no se persiste en `EpisodicMemory` ni en `RecentMemory`.
  * **Resolución de deíxis**: El usuario puede escribir *"¿Qué opinas de este método?"* y el modelo sabrá exactamente a qué archivo y líneas se refiere sin turnos intermedios de aclaración.
  * **Economía de tokens**: Se transmiten únicamente metadatos y rangos; el contenido completo solo se lee si el modelo lo solicita mediante herramientas de archivo.

---

## 6. Políticas de Seguridad y Aislamiento (`AgentAccessControl`)

El editor web debe ceñirse a las mismas restricciones operativas que rigen para las herramientas del agente:

1. **Frontera de Sandbox**: La raíz de navegación no es el disco completo del sistema operativo, sino el conjunto cerrado formado por `workspaceFolder` y `allowedExternalPaths`.
2. **Rutas No Modificables**: Si un archivo coincide con `nom_writable_paths`, el editor bloquea la edición (`readOnly`) y el endpoint `POST /api/files/content` rechaza cualquier intento de sobreescritura con HTTP `403`.
3. **Invariantes Inmutables**:
   * Denegación absoluta de lectura y navegación sobre la carpeta de base de datos y control interno (`.noema-agent/var/`).
   * Denegación de escritura sobre el historial de respaldos de JavaRCS (`*,jv`) y el directorio `.git/`.

---

## 7. Fase 2: Diagnóstico Asíncrono en Segundo Plano vía MCP (`aide`)

Como ampliación posterior a la implementación del editor base, se contempla enriquecer las pestañas con **diagnósticos de compilación, sintaxis y advertencias en tiempo real**.

### 7.1. Arquitectura de Delegación
Noema ya mantiene un cliente MCP activo conectado a `aide` y registra sus herramientas en `ReasoningService`.
* Se creará un endpoint `GET /api/files/diagnostics?file={path}` en `NoemaWebServer`.
* El servidor localizará la herramienta de diagnóstico registrada (ej. `aide.diagnostics` o `javalsp.diagnostics`) en `ReasoningService` mediante `getAvailableTool(...)`.
* Ejecutará la herramienta pasando la ruta canónica y devolverá el resultado JSON estructurado.

### 7.2. Estrategia de Disparo y Asincronía
Para evitar degradar la velocidad de navegación del árbol de archivos:
* **Filtro de extensiones**: La petición solo se lanza para archivos con código fuente soportado (`.java`, `.py`, `.ts`, `.rs`, etc.), ignorando Markdown, JSON, imágenes o logs.
* **Momento de ejecución**:
  * **Principalmente al guardar (`onSave`)**: Tras pulsar `Ctrl + S`, se dispara la verificación en segundo plano para comprobar si la modificación introdujo errores.
  * **Opcional bajo demanda**: Un botón *"🔍 Diagnosticar"* en la barra de migas para solicitar el análisis de forma explícita.
* **Prevención de condiciones de carrera**:
  * En el cliente web, los diagnósticos recibidos se aplican directamente sobre la `EditSession` del archivo correspondiente (`fileSession.setAnnotations(...)`), no sobre el editor global.
  * Si el usuario cambia rápidamente de pestaña mientras el servidor calcula el diagnóstico, las anotaciones quedan registradas en la sesión correcta en memoria y se visualizan intactas cuando el usuario vuelve a seleccionarla.

---

## 8. Resumen de Fases de Implementación

| Fase | Ámbito | Tareas Clave |
| :--- | :--- | :--- |
| **Fase 1: Layout y Navegación** | Frontend / CSS | Implementar colapso de `.terminal-sidebar`. Crear estructura de panel para el editor a la izquierda de la conversación con botón toggle (`Alt + E`). |
| **Fase 1: Árbol y Endpoints** | Backend / Javalin | Implementar `GET /api/files/tree` con raíces virtuales y carga por niveles respetando `AgentAccessControl`. Integrar `@webreflection/file-tree`. |
| **Fase 1: Edición y Guardado** | Frontend / Ace | Integrar Ace Editor en `vendor/`. Implementar gestión multi-pestaña con `EditSession`. Configurar atajo `Ctrl + S` conectado a `POST /api/files/content`. |
| **Fase 1: Telemetría y Agente** | Agente / SSE | Implementar evento SSE `editor_open` para la tool `editor_open_file`. Añadir `POST /api/editor/state` y la operación `EditorAwarenessOperation` en memoria proyectada. |
| **Fase 2: Diagnóstico MCP** | Backend / Editor | Conectar endpoint `GET /api/files/diagnostics` con la herramienta de `aide`. Mapear severidades e inyectar marcas visuales con `session.setAnnotations()` en Ace. |



## Anexo A: Explotación del Potencial del Motor RCS en el Editor Web

### A.1. Motivación y Alcance

La especificación principal del panel de editor contempla el guardado directo (`Ctrl + S`) contra el endpoint `POST /api/files/content`, replicando el comportamiento clásico de un IDE ligero. Sin embargo, Noema ya dispone de un motor de control de versiones local (JavaRCS) que opera silenciosamente antes de cualquier mutación disparada por el agente. Ignorar ese motor en el flujo de edición del usuario genera una asimetría: el agente protege cada escritura, el humano no.

Este anexo describe cómo integrar el editor en el mismo sistema de versionado que ya custodia las operaciones del agente, y cómo esa integración desbloquea capacidades que van más allá del simple "guardar" — convirtiendo al RCS en un canal de trazabilidad compartida entre usuario y agente.

El objetivo no es construir un Git embebido, sino **unificar bajo una única línea temporal tres mecanismos de deshacer que hoy viven aislados**, y aprovechar esa unificación como sustrato de consciencia situacional.

### A.2. Los Tres Horizontes de "Deshacer"

En el momento en que el editor se conecta a RCS, confluyen tres mecanismos con escalas temporales y semánticas radicalmente distintas:

| Horizonte | Mecanismo | Persistencia | Granularidad | Supervivencia |
| :--- | :--- | :--- | :--- | :--- |
| **Inmediato** | Undo nativo de Ace (`EditSession`) | RAM (por sesión) | Carácter | Se pierde al cerrar pestaña |
| **Corto** | Check-in RCS disparado por el editor | Disco (workspace) | Snapshot de archivo | Sobrevive a crashes y recargas |
| **Largo** | Check-in RCS disparado por herramientas del agente | Disco (workspace) | Snapshot de archivo | Ligado a un turno del razonador |

Hoy estos tres horizontes son estancos. La propuesta de este anexo es **coserlos en un mismo almacén indexado por metadatos**, de modo que el usuario perciba una única línea temporal reversible, mientras el agente conserva la información semántica que necesita para razonar sobre el histórico.

### A.3. La Timeline Unificada

El cambio estructural consiste en que `POST /api/files/content` deje de escribir directamente y pase a delegar en el mismo mecanismo que ya utilizan `file_write`, `file_patch` y `file_search_and_replace`. Es decir: **toda mutación de disco pasa por un check-in RCS previo**, independientemente de su origen.

Para que el log resultante sea legible y útil, cada revisión debe ir acompañada de metadatos explícitos:

```json
{
  "revision": "rev_045",
  "timestamp": "2026-10-12T14:22:17Z",
  "author": "user-editor",
  "hash": "sha256:4a7b...",
  "terminalId": "default",
  "context": {
    "type": "manual-edit",
    "message": "Ajuste de indentación en bucle principal",
    "triggeredBy": "ctrl-s"
  }
}
```

Y en el caso de escrituras originadas por el agente:

```json
{
  "revision": "rev_046",
  "timestamp": "2026-10-12T14:25:03Z",
  "author": "agent",
  "hash": "sha256:9c1f...",
  "terminalId": "default",
  "context": {
    "type": "agent-write",
    "turnId": 8421,
    "tool": "file_patch",
    "reasoning_excerpt": "Aplicando parche sugerido para corregir el NPE reportado."
  }
}
```

**JavaRCS no necesita ser modificado.** Si el motor no admite metadatos nativos, un sidecar JSON en `var/lib/rcs_metadata/` indexado por identificador de revisión resuelve el problema sin tocar el núcleo.

### A.4. Reglas de Coalescing

Un usuario que itera sobre un archivo puede generar decenas de guardados en pocos minutos. Sin política de agrupación, la timeline se vuelve un muro de ruido y el propio valor del histórico se diluye.

Se establecen tres reglas:

1. **Supresión por hash idéntico**: si el contenido no ha cambiado respecto a la última revisión, no se genera nueva entrada. Esto evita ruido por `Ctrl + S` repetido sin modificación real.
2. **Coalescing temporal en ráfaga**: guardados consecutivos del mismo autor con diferencia inferior a un umbral configurable (por defecto, 60 segundos) se agrupan bajo una sola revisión, actualizando la marca de tiempo de cierre. La UI puede desplegar la revisión agrupada para ver los snapshots intermedios si el usuario lo necesita.
3. **Corte por cambio de autor**: la aparición de una escritura del agente cierra cualquier agrupación de usuario en curso. Esto garantiza que la interacción humano-agente quede fielmente reflejada en la timeline.

Las tres reglas se aplican exclusivamente a la **vista** de la timeline. Los snapshots físicos de RCS se conservan todos; el coalescing es una capa de presentación y de metadatos.

### A.5. Semántica del Restore

La restauración de una revisión anterior admite dos modalidades, con implicaciones muy distintas:

**Modalidad A — Restaurar a la sesión (recomendada por defecto).**
El contenido histórico se inyecta en la `EditSession` activa mediante `session.setValue(contenido, -1)`. El segundo argumento `-1` es crítico: preserva el historial de undo de Ace, de modo que **la propia restauración se convierte en una operación más del `Ctrl + Z`**. El archivo en disco no se modifica hasta que el usuario decida guardar. La pestaña pasa a estado *dirty* con un indicador específico (`⟲`).

**Modalidad B — Restaurar y sobrescribir.**
El contenido se escribe directamente en disco generando una nueva revisión RCS. Útil para revertir cambios evidentemente erróneos sin pasar por el editor, pero más sorprendente y menos reversible desde la propia UI.

La UI debe presentar ambas como acciones diferenciadas. La primera con un clic simple sobre la entrada del historial; la segunda tras una confirmación explícita.

**Consecuencia arquitectónica relevante**: la modalidad A permite encadenar sin fricción el undo de Ace y el historial RCS. El usuario puede escribir → guardar → arrepentirse → restaurar la revisión anterior → seguir editando → y todo el flujo permanece bajo un único `Ctrl + Z` coherente.

### A.6. El Problema de las Escrituras Externas

Un archivo abierto en el editor puede ser modificado en disco por el agente, por otra pestaña del navegador o por un proceso externo. Sin mecanismo de notificación, la `EditSession` queda silenciosamente obsoleta. Este problema ya existe hoy sin editor, pero se materializa en conflictos reales cuando el usuario puede editar.

Se propone:

1. **Evento SSE `editor_file_changed`**: cualquier mutación sobre un archivo actualmente abierto en algún cliente dispara un evento por el canal `activeSseControl`.
   ```json
   event: "editor_file_changed"
   data: { "path": "src/Main.java", "revision": "rev_046", "author": "agent" }
   ```
2. **Estados de pestaña en el cliente**:
   - **Limpia**: sin cambios pendientes, contenido sincronizado con disco.
   - **Dirty** (`●`): edición local no guardada.
   - **Stale** (`⟳`): el archivo subyacente cambió en disco desde la última carga.
   - **Dirty + Stale** (`●⟳`): conflicto real, requiere resolución.
3. **Resolución de conflicto al guardar**: si la pestaña está en estado stale y el usuario pulsa `Ctrl + S`, no se escribe directamente. Se abre un diálogo con tres opciones:
   - *Recargar del disco*: descarta los cambios locales (con confirmación).
   - *Conservar mi versión*: sobrescribe, generando nueva revisión marcada como `conflict-resolved-in-favor-of: user-editor`.
   - *Ver diff*: se genera con `java-diff-utils` y se muestra en modal o en vista dividida dentro de Ace.

Toda resolución de conflicto se registra en los metadatos de la revisión resultante, permitiendo al agente auditar posteriormente cómo se gestionó.

### A.7. Consciencia Situacional Ampliada

El estado *stale* de las pestañas abiertas debe propagarse al `EditorAwarenessOperation` descrito en la sección 5.3 de la especificación principal. La notificación efímera inyectada en la memoria proyectada se amplía:

```text
[CONSCIENCIA DE EDITOR]
Archivo en pantalla: src/Main.java
Pestañas abiertas: [Main.java, pom.xml]
Líneas seleccionadas por el usuario: 142-168
⚠ Main.java puede estar desactualizado: el archivo fue modificado en disco por el agente en el turno #8421.
```

Esta ampliación cumple dos funciones:

- **Función defensiva**: el LLM sabe que el usuario está mirando código potencialmente obsoleto y puede advertirle antes de responder a preguntas contextuales.
- **Función resolutiva**: el LLM puede ofrecer recargar el archivo o proponer una reconciliación, sin esperar a que el usuario detecte el conflicto por su cuenta.

### A.8. RCS como Canal de Comunicación Bidireccional

Con la timeline unificada, el histórico de revisiones deja de ser un mecanismo de seguridad y se convierte en un **medio de comunicación asíncrona entre usuario y agente a través del código**:

- El agente puede consultar `file_history` y detectar que el usuario revirtió manualmente una de sus ediciones. El siguiente turno puede iniciar con esa consciencia y preguntar por la razón.
- El usuario puede inspeccionar qué revisión corresponde a cada intervención del agente, viendo el `reasoning_excerpt` asociado para entender la motivación.
- La restauración desde la UI del usuario es visible al agente en el siguiente `file_history`. La restauración desde una herramienta del agente es visible en la pestaña de historial del usuario en el siguiente evento SSE.

El resultado es que **ambos actores comparten una misma narrativa técnica sobre el archivo**, sin necesidad de que el usuario documente explícitamente qué cambió ni de que el agente pida permiso para cada operación reversible.

### A.9. Contratos REST Adicionales

| Endpoint | Método | Función |
| :--- | :--- | :--- |
| `GET /api/files/history?file={path}` | GET | Lista revisiones con metadatos, paginado y filtrable por autor. |
| `GET /api/files/diff?file={path}&rev={id}` | GET | Devuelve diff unificado contra revisión previa o contra working copy. |
| `POST /api/files/restore` | POST | Recibe `{ file, rev, mode: "session" \| "disk" }`. Aplica modalidad A o B. |
| `GET /api/files/stale?terminalId={id}` | GET | Lista de archivos abiertos en el terminal cuya versión en disco difiere de la cargada. |

Todos ellos pasan obligatoriamente por `AgentAccessControl` para validación de rutas y respeto de `nom_writable_paths`.

### A.10. Consideraciones de Alcance

**¿Aplica a archivos nunca tocados por el agente?** Se propone sí, con una salvedad: la primera revisión de un archivo editado solo desde el editor se marca como `origin: user-editor`, y su presencia en la timeline queda supeditada a que el archivo sea posteriormente abierto desde la UI en modo edición. Esto evita poblar RCS con archivos que el usuario solo consultó.

**¿Afecta al sandbox de Firejail?** No. RCS opera sobre el workspace del proyecto, no sobre el home aislado del contenedor de shell. Los mecanismos son ortogonales.

**¿Impacto en rendimiento?** El check-in previo a cada guardado introduce una latencia adicional mínima (del orden de milisegundos en archivos de tamaño típico). El coalescing por hash evita check-ins redundantes. Para archivos muy voluminosos (por encima del umbral configurable), se puede optar por snapshots diferenciales o compresión previa, aunque probablemente sea innecesario en el rango operativo habitual.

### A.11. Fase de Implementación Propuesta

| Fase | Ámbito | Tareas Clave |
| :--- | :--- | :--- |
| **A.1: Check-in unificado** | Backend | Redirigir `POST /api/files/content` a través del motor RCS. Añadir sidecar de metadatos. Aplicar reglas de coalescing por hash. |
| **A.2: Timeline UI** | Frontend | Pestaña "Historial" en el panel de editor. Listado con autor, timestamp y extracto. Integración con `java-diff-utils` para vista de diff. |
| **A.3: Restore** | Frontend / Backend | Implementar modalidades A y B. Integrar `setValue(..., -1)` para preservar undo. Endpoint `POST /api/files/restore`. |
| **A.4: Consciencia de conflicto** | Backend / SSE | Evento `editor_file_changed`. Estados de pestaña `stale` y `dirty+stale`. Ampliación de `EditorAwarenessOperation` con advertencias. |
| **A.5: Resolución asistida** | Frontend | Diálogo de conflicto con tres vías. Registro de resolución en metadatos de la nueva revisión. |

---

## Anexo B: Ciclo de Normalización de Código en el Editor Web

### B.1. Motivación

El flujo de trabajo habitual al recibir código generado por un LLM presenta una secuencia reconocible: los imports suelen estar mal (faltan, sobran o están desordenados), el formateo es inconsistente y, en ocasiones, hay errores de compilación que el propio agente no ha detectado. La rutina de normalización que un desarrollador aplica en su IDE antes de aceptar el código sigue un orden concreto: ajustar imports, comprobar diagnósticos, corregir lo trivial, formatear y guardar. Solo si los errores no son triviales se devuelve el problema al LLM para una nueva iteración.

Este anexo describe cómo replicar ese ciclo íntegramente dentro del editor web de Noema, eliminando la necesidad de alternar con el IDE para las tareas de normalización. El objetivo no es construir un IDE completo, sino cubrir las cuatro operaciones que realmente se usan sobre código generado: **fix imports, diagnóstico, formato y guardado**.

### B.2. Premisas del Modelo

El diseño parte de tres restricciones que simplifican notablemente la implementación y que conviene fijar por escrito:

1. **El editor solo carga archivos existentes en disco.** No se crean archivos nuevos desde el editor. El agente puede crear archivos mediante sus herramientas, y el usuario puede cargarlos después desde el árbol. La creación de archivos permanece como responsabilidad exclusiva del agente o del usuario fuera del editor.
2. **Fix imports y diagnóstico operan sobre disco.** No sobre el buffer. Son operaciones que consultan el LSP y, en el caso de fix imports, escriben el resultado en disco, disparando el correspondiente check-in RCS.
3. **Formato y guardado operan sobre el buffer.** El formateo actúa sobre el contenido que el usuario tiene en la `EditSession` y no toca disco hasta que el usuario decide guardar.

Esta separación refleja fielmente el flujo del IDE y mantiene la coherencia con el resto de la arquitectura de Noema: las operaciones que mutan disco pasan por RCS, las que no, no.

### B.3. El Ciclo de Trabajo

El ciclo que el editor debe soportar es el siguiente:

```text
1. El LLM edita un archivo en disco (vía file_write, file_patch, etc.)
   ↓
2. El usuario carga el archivo en el editor (o el agente lo abre vía editor_open_file)
   ↓
3. Fix Imports  ─────────────►  LSP aplica source.organizeImports sobre disco
   │                              ├─ Check-in RCS
   │                              └─ El editor recarga el contenido
   ↓
4. Diagnosticar  ───────────►  MCP aide devuelve errores/advertencias
   │                              └─ El editor marca con session.setAnnotations()
   ↓
5. ¿Errores triviales?
   ├─ Sí → el usuario corrige en el buffer
   │        ↓
   │      Formatear  ────────►  Palantir procesa el buffer
   │        ↓
   │      Guardar  ──────────►  POST /api/files/content + RCS
   │
   └─ No → se comunica al LLM para nueva iteración
```

Cada paso es una operación independiente disparada por el usuario. No hay automatización encadenada; el control permanece en manos del desarrollador, que decide cuándo avanzar y cuándo detenerse.

### B.4. Contratos de los Endpoints

#### B.4.1. `POST /api/editor/fix-imports`

Aplica la acción de código `source.organizeImports` sobre el archivo indicado, utilizando el language server activo a través del `McpService`.

**Petición:**
```json
{
  "path": "src/main/java/io/github/jjdelcerro/noema/lib/impl/AgentImpl.java"
}
```

**Proceso en el servidor:**
1. Validar `path` contra `AgentAccessControl` (lectura y escritura).
2. Verificar que el archivo existe en disco. Si no existe, devolver HTTP `404`.
3. Invocar la herramienta `lsp_apply_code_action` de `aide` con `kind: "source.organizeImports"` sobre el rango completo del archivo.
4. El LSP aplica el `WorkspaceEdit` sobre disco. Esto genera una escritura efectiva, por lo que se dispara check-in RCS (ver B.5).
5. Leer el contenido resultante del disco.
6. Devolver el contenido en la respuesta.

**Respuesta:**
```json
{
  "path": "src/main/java/.../AgentImpl.java",
  "content": "package io.github.jjdelcerro.noema.lib.impl;\n\nimport ...;\n\npublic class AgentImpl ...",
  "changed": true,
  "revision": "rev_047",
  "diagnostics": [
    { "severity": "warning", "line": 42, "message": "Variable 'x' is never used" }
  ]
}
```

El campo `diagnostics` es opcional. Si `mcp-aide` devuelve diagnósticos asociados a la acción (como hace `safe_edit`), se incluyen aquí para que el editor los marque directamente sin necesidad de una segunda llamada al endpoint de diagnóstico.

**Caso sin cambios:** si el archivo ya tenía los imports correctos, el LSP no genera ediciones y el servidor devuelve `changed: false` con el contenido original. No se dispara RCS en este caso, porque no hay mutación real.

#### B.4.2. `GET /api/files/diagnostics?file={path}`

Obtiene los diagnósticos actuales del archivo desde el language server, sin modificar nada.

**Proceso en el servidor:**
1. Validar `path` contra `AgentAccessControl` (solo lectura).
2. Invocar la herramienta de diagnóstico de `aide` vía MCP.
3. Mapear la respuesta al formato interno de anotaciones de Ace.
4. Devolver la lista.

**Respuesta:**
```json
{
  "path": "src/main/java/.../AgentImpl.java",
  "diagnostics": [
    {
      "severity": "error",
      "startLine": 142,
      "startColumn": 8,
      "endLine": 142,
      "endColumn": 24,
      "message": "cannot find symbol: method execute()",
      "source": "jdt"
    },
    {
      "severity": "warning",
      "startLine": 168,
      "startColumn": 12,
      "endLine": 168,
      "endColumn": 20,
      "message": "unused variable 'result'",
      "source": "jdt"
    }
  ]
}
```

El cliente aplica estas anotaciones con `fileSession.setAnnotations(...)` sobre la `EditSession` correspondiente, no sobre el editor global. Así, si el usuario cambia de pestaña durante la petición, las anotaciones quedan asociadas al archivo correcto y se muestran cuando vuelva a seleccionarlo.

**Consideración sobre buffer sucio:** si el archivo tiene ediciones sin guardar en el buffer, el diagnóstico refleja el estado del disco y no incluirá las correcciones que el usuario haya hecho. Conviene que el cliente advierta de esta situación (por ejemplo, con un tooltip en el botón o un aviso en el resultado) para evitar confusión.

#### B.4.3. `POST /api/editor/format`

Aplica Palantir Java Format al contenido recibido y devuelve el resultado formateado. Opera sobre el buffer, no sobre disco.

**Petición:**
```json
{
  "path": "src/main/java/.../AgentImpl.java",
  "content": "package io.github...\n\npublic class AgentImpl { ... }"
}
```

**Proceso en el servidor:**
1. Validar `path` contra `AgentAccessControl` (solo para verificar que el archivo es accesible; no se escribe).
2. Invocar el binario de Palantir vía subproceso, pasando el contenido por `stdin`.
3. Leer la salida por `stdout`.
4. Si el proceso falla (código no parseable), devolver HTTP `422` con el mensaje de error del formateador.
5. Si el contenido no cambia tras el formateo, devolver `changed: false`.
6. Devolver el contenido formateado.

**Respuesta:**
```json
{
  "content": "package io.github...\n\npublic class AgentImpl {\n    ...\n}",
  "changed": true
}
```

**Flags de Palantir relevantes:**

- `--skip-sorting-imports`: evita que Palantir reordene imports, que ya han sido gestionados por el LSP.
- `--skip-removing-unused-imports`: idem para eliminación.
- `--aosp`: estilo AOSP en lugar del estilo por defecto, si el usuario lo prefiere.
- `--reflow-long-strings`: útil si el LLM genera cadenas largas.

Por defecto, el endpoint debería invocar Palantir con los dos flags `--skip-*` activados, dado que los imports se gestionan en un paso separado. Esto garantiza que el formato no reintroduzca cambios en la zona de imports ya normalizada.

#### B.4.4. Combinación de pasos

No se contempla un endpoint combinado (`normalize`) en esta fase. El flujo es explícito y el usuario controla cada paso. Si más adelante se detecta que la secuencia fix-imports → diagnóstico → formato se ejecuta siempre junta, se puede añadir un endpoint que los encadene, pero eso rompe la semántica de "el usuario revisa entre pasos" que es precisamente la que se quiere preservar.

### B.5. Integración con RCS

La única operación del ciclo que muta disco es `fix-imports`. Como tal, debe pasar por el mecanismo de check-in RCS descrito en el Anexo A:

1. Antes de aplicar el `WorkspaceEdit`, el servidor invoca el check-in sobre el archivo.
2. La revisión resultante se marca con metadatos que identifican el origen:
   ```json
   {
     "author": "user-editor",
     "context": {
       "type": "code-action",
       "action": "source.organizeImports",
       "triggeredBy": "editor-web",
       "terminalId": "default"
     }
   }
   ```
3. Tras el check-in, se aplica la acción LSP, que escribe el archivo.
4. Se registra una nueva revisión con el contenido resultante.

El formateo y el guardado posteriores siguen el flujo estándar ya definido: el formateo no toca disco, el guardado genera su propia revisión RCS con `author: "user-editor"` y `context.type: "manual-edit"`.

Esta separación permite auditar con precisión qué cambios provienen de una acción automática del LSP y cuáles de una edición manual del usuario, manteniendo la trazabilidad que se persigue en la timeline unificada del Anexo A.

### B.6. Integración en el Editor

#### B.6.1. Barra de acciones

Sobre el área de edición, junto a las pestañas y la barra de migas, se añade una barra compacta con los siguientes controles:

| Control | Atajo | Acción |
| :--- | :--- | :--- |
| 🔧 Fix Imports | `Ctrl + Alt + I` | `POST /api/editor/fix-imports` |
| 🔍 Diagnosticar | `Ctrl + Alt + D` | `GET /api/files/diagnostics` |
| ✨ Formatear | `Ctrl + Alt + F` | `POST /api/editor/format` |
| 💾 Guardar | `Ctrl + S` | `POST /api/files/content` (existente) |

El orden de los botones refleja el orden lógico del ciclo. No hay botón de "Normalizar todo" porque el flujo explícito es precisamente el que se quiere preservar: el usuario revisa entre pasos.

#### B.6.2. Comportamiento ante cada operación

**Fix Imports:**
- El cliente envía la petición.
- Al recibir la respuesta, actualiza la `EditSession` con `session.setValue(nuevoContenido, -1)` para preservar el undo stack.
- Llama a `session.markClean()` porque buffer y disco coinciden.
- Si la respuesta incluye `diagnostics`, aplica las anotaciones inmediatamente.

**Diagnosticar:**
- El cliente envía la petición.
- Al recibir la respuesta, aplica las anotaciones con `fileSession.setAnnotations(...)`.
- Muestra un contador de errores y advertencias en la barra de estado del editor.

**Formatear:**
- El cliente envía el contenido actual del buffer.
- Al recibir la respuesta, actualiza la `EditSession` con `setValue(nuevoContenido, -1)`.
- **No** llama a `markClean()`, porque el buffer sigue sin guardar.
- La pestaña permanece marcada como *dirty*.

**Guardar:**
- Flujo existente. Escribe a disco, dispara RCS, marca la pestaña como limpia.

#### B.6.3. Indicadores visuales

- **Pestaña dirty (`●`)**: ediciones pendientes sin guardar.
- **Pestaña con errores (`✗`)**: el archivo tiene diagnósticos de severidad `error`.
- **Pestaña con advertencias (`⚠`)**: el archivo tiene diagnósticos de severidad `warning` o inferior.
- **Pestaña limpia**: sin cambios pendientes, sin diagnósticos relevantes.

Los indicadores de diagnóstico se limpian al aplicar nuevas anotaciones tras un fix-imports o un diagnóstico explícito, y se recalculan cuando el usuario guarda y vuelve a diagnosticar.

### B.7. Decisiones de Diseño Pendientes

Dos aspectos del flujo requieren confirmación antes de la implementación:

**Orden entre formateo y correcciones manuales.** El flujo descrito sitúa el formateo después de las correcciones manuales del usuario. Esto implica que el botón "Formatear" se usa normalmente cuando el buffer ya está *dirty*. Sin embargo, existe la posibilidad de formatear antes de corregir, lo que dejaría el código visualmente limpio para facilitar la lectura y la corrección. La decisión afecta a los atajos y a la disposición de los botones.

**Comportamiento ante ediciones externas.** Si el LLM modifica un archivo que el usuario tiene abierto en el editor, el buffer queda obsoleto sin que el usuario lo sepa. Las opciones son:
- Recargar automáticamente (pierde ediciones pendientes del buffer).
- Notificar al usuario y esperar su decisión (mantiene el control, pero añade fricción).
- Marcar la pestaña como *stale* y esperar a que el usuario actúe (modelo ya descrito en el Anexo A).

Este caso conecta directamente con el evento `editor_file_changed` del Anexo A, y probablemente la solución deba ser coherente con lo que allí se decida.

### B.8. Fase de Implementación Propuesta

| Fase | Ámbito | Tareas Clave |
| :--- | :--- | :--- |
| **B.1: Fix Imports** | Backend / MCP | Implementar `POST /api/editor/fix-imports` invocando `lsp_apply_code_action` con `source.organizeImports`. Integrar con RCS. Devolver contenido y diagnósticos opcionales. |
| **B.2: Diagnóstico** | Backend / MCP / Frontend | Implementar `GET /api/files/diagnostics`. Mapear severidades a anotaciones de Ace. Aplicar con `fileSession.setAnnotations()`. |
| **B.3: Formato** | Backend / Frontend | Implementar `POST /api/editor/format` invocando Palantir con flags `--skip-*`. Integrar `setValue(..., -1)` en el cliente. |
| **B.4: UI y atajos** | Frontend | Añadir barra de acciones sobre el editor. Configurar atajos `Ctrl + Alt + I/D/F`. Indicadores de pestaña para dirty y diagnósticos. |
| **B.5: Decisiones abiertas** | Diseño | Confirmar orden formato/correcciones y comportamiento ante ediciones externas. |

---

