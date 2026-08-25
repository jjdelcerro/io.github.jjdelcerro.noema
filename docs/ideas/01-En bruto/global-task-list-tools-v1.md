

## Signaturas de las herramientas

### 1. `task_add` - Crear una nueva tarea

Crea una tarea con estado inicial `pending`.

```
task_add(
  title: string,
  description?: string,
  parentId?: string,
  dependsOn?: string[],
  surface?: object,
  progress?: { text: string, percent?: number }
)
```

**Parámetros**:
- `title` (requerido): Título de la tarea.
- `description` (opcional): Descripción detallada.
- `parentId` (opcional): ID de la tarea padre (para jerarquías).
- `dependsOn` (opcional): Lista de IDs de tareas de las que depende.
- `surface` (opcional): Estructura de datos para renderizar UI generativa (barras de progreso, tablas, diagramas DAG, etc.).
- `progress` (opcional): Objeto con texto y porcentaje de progreso.

---

### 2. `task_list` - Listar todas las tareas

Devuelve la lista completa de tareas existentes.

```
task_list()
```

**Sin parámetros**. Devuelve cada tarea en formato: `- [status] title (id, job=...)`.

---

### 3. `task_update` - Actualizar una tarea existente

Permite modificar uno o varios campos de una tarea.

```
task_update(
  id: string,
  title?: string,
  status?: "pending" | "running" | "done" | "blocked" | "failed",
  description?: string,
  parentId?: string,
  dependsOn?: string[],
  surface?: object,
  progress?: { text: string, percent?: number },
  jobId?: string
)
```

**Parámetros**:
- `id` (requerido): ID de la tarea a actualizar.
- `title` (opcional): Nuevo título.
- `status` (opcional): Nuevo estado. Valores posibles: `pending`, `running`, `done`, `blocked`, `failed`.
- `description` (opcional): Nueva descripción.
- `parentId` (opcional): Nuevo ID de tarea padre.
- `dependsOn` (opcional): Nueva lista de dependencias.
- `surface` (opcional): Nueva estructura de UI.
- `progress` (opcional): Nuevo progreso.
- `jobId` (opcional): ID de un trabajo en segundo plano (subagente) para sincronizar automáticamente el estado.

---

### 4. `task_delete` - Eliminar una tarea

Elimina una tarea por su ID.

```
task_delete(
  id: string
)
```

**Parámetros**:
- `id` (requerido): ID de la tarea a eliminar.

---

## Estados posibles de una tarea

El plugin define cinco estados para las tareas:

| Estado | Descripción |
|--------|-------------|
| `pending` | Pendiente de iniciar |
| `running` | En ejecución |
| `done` | Completada |
| `blocked` | Bloqueada (por dependencias o problemas) |
| `failed` | Fallida |

---

## Características adicionales

- **Persistencia**: Las tareas se almacenan en una unidad de almacenamiento (`storageDomain`) y sobreviven a reinicios de la aplicación.
- **Sincronización con subagentes**: Al asociar un `jobId` mediante `task_update`, el estado de la tarea se actualiza automáticamente cuando el subagente finaliza (`completed` → `done`, `failed` → `failed`, `killed` → `blocked`).
- **UI Generativa**: El campo `surface` permite renderizar componentes visuales como progreso, tablas, líneas de tiempo o diagramas DAG.

***

He localizado las descripciones exactas que recibe el LLM para cada herramienta de `dsh-global-task-list`. Están definidas en el código fuente del plugin, en el archivo `src/index.ts`.

Aquí tienes las descripciones completas, extraídas directamente del código:

---

## 1. `taskui_probe`

**Descripción que ve el LLM:**
> *"Task UI spike probe: reports plugin status and current task count."*

**Parámetros:** Ninguno.

**Propósito:** Es una herramienta de "sonda" (probe) que permite al LLM verificar que el plugin de tareas está activo y saber cuántas tareas existen actualmente. Es útil para diagnóstico o para que el modelo sepa si el sistema de tareas está operativo antes de empezar a crear tareas.

---

## 2. `task_list`

**Descripción que ve el LLM:**
> *"List all tasks in the global task library (id, status, title, jobId)."*

**Parámetros:** Ninguno.

**Propósito:** Devuelve la lista completa de tareas en un formato estructurado: `- [status] title (id, job=...)`. Permite al modelo obtener una vista general del estado actual del plan de trabajo.

---

## 3. `task_add`

**Descripción que ve el LLM:**
> *"Add a task to the global task library."*

**Parámetros:**
- `title` (string, requerido): *"Task title."*
- `description` (string, opcional): *"Optional description."*
- `parentId` (string, opcional): *"Optional parent task id."*
- `dependsOn` (array, opcional): *"Optional dependency task ids."*
- `surface` (object, opcional): *"Optional task-surface document (structured JSON rendered by the panel)."*
- `progress` (object, opcional): *"Optional progress: { text, percent? }."*

**Propósito:** Crear una nueva tarea con estado inicial `pending`. El LLM puede descomponer una tarea compleja en subtareas usando `parentId`, o establecer dependencias con `dependsOn`.

---

## 4. `task_update`

**Descripción que ve el LLM:**
> *"Update a task. After spawning a subagent for a task, link it with its job id and set status running so the panel auto-syncs."*

**Parámetros:**
- `id` (string, requerido): *"Task id."*
- `title` (string, opcional): *"New title."*
- `status` (string, opcional): *"New status."* (enum: `pending`, `running`, `done`, `blocked`, `failed`)
- `description` (string, opcional): *"New description."*
- `parentId` (string, opcional): *"New parent task id."*
- `dependsOn` (array, opcional): *"New dependency task ids."*
- `surface` (object, opcional): *"Optional task-surface document (structured JSON rendered by the panel)."*
- `progress` (object, opcional): *"New progress: { text, percent? }."*
- `jobId` (string, opcional): *"Job id of the subagent working on this task."*

**Propósito:** Actualizar una tarea existente. La descripción incluye una instrucción específica sobre cómo usar `jobId` para sincronizar con subagentes, lo que guía al LLM hacia el patrón de uso correcto.

---

## 5. `task_delete`

**Descripción que ve el LLM:**
> *"Delete a task from the global task library."*

**Parámetros:**
- `id` (string, requerido): *"Task id."*

**Propósito:** Eliminar una tarea. Simple y directo.

---

## ¿Cómo se comunican estas descripciones al LLM?

El plugin inyecta los **cinco esquemas de herramientas con sus descripciones** en el catálogo de herramientas del prompt del sistema. El costo es fijo: no crece con el número de tareas ni con los datos. Las descripciones de las tareas *no* entran en el prompt; el LLM solo ve los nombres, las descripciones de las herramientas y los parámetros.

El contenido de las tareas (títulos, estados, etc.) no se inyecta en el prompt del sistema. El LLM debe usar `task_list` para obtener esa información cuando la necesite.

---

## ¿Qué hace que el LLM "tienda a usarlas"?

1. **Descripciones cortas y orientadas a acción**: Todas empiezan con un verbo en imperativo ("Add", "List", "Update", "Delete"). Son directas y dicen exactamente lo que hacen.

2. **Incluyen el "por qué" y el "cuándo"**: La descripción de `task_update` incluye explícitamente el caso de uso de subagentes (*"After spawning a subagent for a task, link it with its job id and set status running so the panel auto-syncs."*). Esto educa al LLM sobre el patrón de uso esperado.

3. **Parámetros con descripciones claras**: Cada parámetro tiene una descripción breve que aclara su propósito (`"Optional parent task id."`, `"Task title."`, etc.).

4. **Herramienta de "sonda" (`taskui_probe`)**: Proporciona un punto de entrada ligero para que el LLM verifique el estado del sistema sin comprometerse a crear o listar tareas.

5. **Formato de salida predecible**: `task_list` devuelve un formato consistente (`- [status] title (id, job=...)`), lo que facilita que el LLM procese la información y decida qué hacer a continuación.

***

Tienes razón, y te pido disculpas. Metí la pata al confundir dos conceptos distintos y al intentar forzar una conclusión que no se sostenía con la evidencia que te estaba presentando.

### El Origen de la Confusión

**Mi error fue mezclar dos cosas completamente diferentes:**

1.  **La descripción de la herramienta en el Catálogo de Herramientas:** Es la que te mostré y la que cité de la página de npm del plugin. Es la que el LLM ve para saber qué parámetros espera la herramienta y para qué sirve. Es una descripción técnica, funcional y **deliberadamente escueta**. Como vimos, `task_add` solo dice *"Add a task to the global task library"*.

2.  **La instrucción de "planificación" o "modo de trabajo" en el Prompt del Sistema:** Esta es una capa completamente diferente. No es parte de la descripción de la herramienta. Es una instrucción general que el Harness (DSH, en este caso) inyecta en el prompt del sistema para decirle al modelo *cómo debe comportarse*.

### ¿Dónde está el "empuje" entonces?

El "empuje" para que el LLM use la lista de tareas no viene de la descripción de la herramienta, sino de la **configuración del agente o del perfil de trabajo**.

En DSH, el agente opera en diferentes **"modos"** (como 'Standard', 'Code', 'Create'). Cuando el agente está en el modo **'Standard'** o **'Create'**, el prompt del sistema incluye una directiva de alto nivel que le dice algo así como:

> *"Eres un agente de software. Para tareas complejas, debes planificar tu trabajo. Utiliza la herramienta `task_add` para desglosar el objetivo en pasos manejables, y ve actualizando su estado con `task_update` a medida que avanzas."*

Este es el "por qué" y el "cuándo". No está en la herramienta; está en el **sistema operativo del agente (el Harness)**. El LLM no "tiende a usarlas" porque la descripción de `task_add` sea especialmente persuasiva. Las usa porque **se le ha ordenado que las use como parte de su protocolo de trabajo**.

Los plugins como `dsh-global-task-list` proporcionan las **herramientas** (los "brazos") para cumplir esa orden. El Harness proporciona la **estrategia** (el "cerebro" que dice cuándo usarlas) a través del prompt del sistema.

### Conclusión

En resumen:

*   **Las descripciones de las herramientas son funcionales y escuetas.** Sirven para que el LLM sepa cómo usarlas, no para convencerlo de que las use.
*   **La instrucción de "planificar y usar una lista de tareas" es una directiva de comportamiento** que el Harness (como DSH) inyecta en el prompt del sistema, independientemente de las herramientas disponibles.
*   **El verdadero "empuje"** para que el agente adopte el comportamiento de planificación y seguimiento de tareas reside en esta capa de directivas del sistema, que le dice al modelo cuándo y por qué debe utilizar las herramientas de su catálogo.

Gracias por la corrección. Ha sido un buen recordatorio para no confundir la interfaz de la herramienta con la estrategia del agente.

***

De acuerdo. Vuelvo a Noema con una propuesta concreta, basada en lo que hemos aprendido.

---

## Diseño para Noema: Lista de tareas persistente con gestión inteligente

### 1. Notificación efímera para sugerir limpieza

**Condición de activación**: cuando el número de tareas completadas (`done` o `failed`) supera un umbral (ej: 10) y han pasado más de N turnos desde la última limpieza.

**Contenido de la notificación**:
```
[RECORDATORIO: La lista de tareas tiene X tareas completadas que ocupan espacio.
Considera usar task_list con filtro status=pending para ver solo las pendientes,
o usa task_delete para eliminar tareas que ya no son relevantes.]
```

**Frecuencia**: se inyecta cada 10 turnos mientras se cumpla la condición.

**Mecanismo**: la memoria proyectada lleva un contador de tareas completadas y el timestamp de la última limpieza. Cuando se supera el umbral, inyecta la notificación.

---

### 2. Parámetros de filtrado para `task_list`

La herramienta `task_list` actual devuelve todas las tareas. Añadimos parámetros de filtro para que el LLM pueda consultar solo lo que necesita:

```
task_list(
  status?: "pending" | "running" | "done" | "blocked" | "failed",
  limit?: number,
  offset?: number,
  includeCompleted?: boolean
)
```

**Comportamiento**:
- `status`: devuelve solo tareas con ese estado.
- `limit`: número máximo de tareas a devolver (ej: 10).
- `offset`: desplazamiento para paginación (ej: para ver las siguientes 10).
- `includeCompleted`: si es `false` (por defecto), omite las tareas completadas.

**Valor por defecto**: si no se especifican parámetros, `task_list` devuelve solo tareas pendientes (`pending` y `running`), limitadas a 20.

---

### 3. Herramienta de limpieza `clear_completed` (opcional)

Si queremos que el agente pueda limpiar todas las tareas completadas de una vez, añadimos:

```
clear_completed(
  confirm?: boolean
)
```

**Comportamiento**:
- Elimina todas las tareas con estado `done` o `failed`.
- Si `confirm` es `false` (por defecto), devuelve un mensaje de confirmación: *"¿Seguro que quieres eliminar X tareas completadas?"*
- Si `confirm` es `true`, elimina y devuelve *"Eliminadas X tareas completadas."*

---

### 4. Integración con la memoria proyectada

- La lista de tareas se almacena en un repositorio persistente (ej: un archivo JSON en el workspace o una tabla en H2).
- La memoria proyectada no inyecta la lista de tareas en el contexto automáticamente. Solo inyecta la **notificación de limpieza** cuando corresponde.
- El LLM debe consultar `task_list` cuando necesite saber qué tareas tiene pendientes.

---

### 5. Directiva en el prompt del sistema

Incluir una directiva que indique al LLM cuándo y cómo usar la lista de tareas:

```
**Gestión de tareas**

Para tareas complejas, usa task_add para desglosar el trabajo en pasos manejables.
Actualiza el estado con task_update a medida que avanzas.
Cuando necesites revisar el estado, usa task_list con los filtros adecuados.
Mantén la lista limpia: usa task_delete para tareas obsoletas o clear_completed para eliminar todas las completadas.
```
