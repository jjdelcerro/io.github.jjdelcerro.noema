---
name: plan_manager
description: >
  Gestiona planes de trabajo persistentes en disco. Crea y mantiene
  `.noema-agent/plans/<nombre>/` con `specs.md` (especificación), `tasks.md`
  (lista numerada de tareas con estado) e `issues.md` (cuaderno de
  incidencias). Úsala cuando el usuario pida planificar, estructurar o retomar
  un proyecto que ocupe varios turnos o varios ficheros (migración, refactor
  amplio, implementación de una feature con varios subcambios), o cuando
  pregunte por el estado de tareas de un plan existente. NO la uses para
  cambios de un solo turno, preguntas puntuales ni ediciones aisladas.
version: 2.0.0
---

## Qué hace

Mantiene planes de trabajo en disco. Cada plan es un directorio:

```
.noema-agent/plans/<nombre>/
    state        # JSON: {"state": "creating|running|stopped|done"}
    specs.md     # Especificación técnica del plan
    tasks.md     # Lista numerada de tareas con estado
    issues.md    # Cuaderno libre de incidencias
```

Los ficheros son la fuente de verdad. No asumas de memoria el estado del
plan: consúltalo con los scripts.

## Estados

| Estado | Significado |
|---|---|
| `creating` | Borrador. Se edita `specs.md` y `tasks.md`. Sin ejecución. |
| `running` | En ejecución. Solo puede haber uno. |
| `stopped` | Pausado. Se puede reanudar o volver a `creating`. |
| `done` | Cerrado. Todas las tareas en `[x]` o `[-]`. Terminal. |

Transiciones válidas:

- `creating → running` (requiere al menos una tarea en `tasks.md`)
- `running → stopped`
- `running → done` (sin tareas pendientes ni en curso)
- `stopped → running`
- `stopped → creating`
- `done` es terminal.

`createPlan` deja el plan en `creating`. Cualquier otra transición se hace
con `setPlanState`.

## Scripts

Invocación: `run_skill_script(name: "plan_manager", script: "<nombre>", args: [...])`.

| Script | args | Devuelve |
|---|---|---|
| `createPlan` | `["nombre"]` | Crea el directorio y las plantillas. Deja el plan en `creating` y lo carga. Falla si ya existe. |
| `setPlanState` | `["nombre", "estado"]` | Ejecuta la transición. Valida precondiciones. Registra o retira el aviso proyectado según destino. |
| `getCurrentPlanName` | — | Nombre y estado del plan cargado, o "no hay plan cargado". |
| `getCurrentTasks` | — | Path a `tasks.md`, tarea en curso, siguiente, progreso. Con aviso según el estado del plan. |
| `getCurrentPlanSpecifications` | — | Path a `specs.md`. |
| `getCurrentIssues` | — | Path a `issues.md`. |
| `getSections` | `["Título A", "Título B"]` | Bloques de `specs.md` delimitados por esos encabezados. Sin args: índice de encabezados. |
| `listPlans` | — | Planes en disco con su estado. Marca `[ACTIVO]` el cargado. Demota `running → stopped` los no cargados. |

## Arranque

Si no sabes qué plan hay (sesión nueva, skill recién activada), llama a
`getCurrentPlanName`. Si no hay plan cargado, **no hagas nada por iniciativa
propia**: espera a que el usuario indique qué quiere — crear uno, retomar
uno existente, o trabajar sin plan. Si hay plan cargado, sigue el modo
correspondiente a su estado.

## Modos

### `creating` — Borrador

Facilita la conversación con el usuario sobre el plan. Cuando el usuario
proponga o pida contenido para especificación, `specs.md`, persístelo. Cuando definais tareas,
persístelas en `tasks.md` con el formato de abajo.

No inventes contenido de la especificación ni propongas estructura por
iniciativa propia salvo que el usuario lo pida. La decisión de qué contiene
un plan es del usuario; tu trabajo es persistirlo correctamente.

Cuando el usuario pida abordar el plan: `setPlanState(nombre, "running")`.

### `running` — Ejecución

1. `getCurrentTasks` para conocer el path al fichero de tareas, la tarea en curso y la siguiente.
   Abre `tasks.md` si necesitas el detalle.
2. Antes de tocar nada, localiza en `specs.md` las secciones que la tarea
   cita en `refs:`. Léelas con `getSections` (ver más abajo). No leas
   `specs.md` completo.
3. Marca la tarea actual `[~]` en `tasks.md` al empezarla.
4. Al terminarla, verifica (ver "Verificación") y marca `[x]`. Si es
   imposible, márcala `[-]` y anota el motivo en `issues.md` con `#N`.
5. Pasa a la siguiente tarea. Una sola tarea en curso a la vez.
6. Al terminar todas: `setPlanState(nombre, "done")`.

**No actives el plan por iniciativa propia.** La transición a `running` la pide el usuario.

**No mezcles tareas de fases distintas** ni adelantes trabajo de una fase
posterior. Respeta el orden de `tasks.md`.

**Si `specs.md` no cubre lo que la tarea pide, o entra en conflicto con el
código actual, para y pregunta.** No improvises.

#### Rectificaciones en `specs.md` 

Esta regla aplica solo cuando el plan está en running.

Si al ejecutar una tarea descubres que `specs.md` no se ajusta a la realidad,
**avisa al usuario, rectifícalo en el mismo movimiento y deja constancia**. Al final de la
sección afectada, añade un blockquote:

```
> Rectificación: (#N)
> <una línea de motivo>
```

- La cadena literal `> Rectificación: ` (con tilde, mayúscula, dos puntos y
  espacio) es el token de búsqueda. No la varíes: el usuario la busca en
  texto plano desde el editor.
- `#N` es la tarea que provocó la rectificación.
- El "qué se cambió" ya está en el texto. La nota solo aporta el "por qué".

Si la rectificación es aditiva, basta con el blockquote tras el párrafo. Si
deroga texto previo, aplica el cambio sobre el texto y deja la nota.

**Nunca modifiques `specs.md` sin añadir la rectificación.** Si no estás
seguro de cómo rectificar, o el cambio afecta a más de una sección, para y
pregunta al usuario.


### `stopped` — Pausado

El plan no está en ejecución. No marques tareas `[~]` ni avances el estado 
por iniciativa propia. Puedes leer los ficheros para responder preguntas, 
y editar `specs.md` o `issues.md` si el usuario lo pide explícitamente.

Cuando el usuario pida reanudar la ejecución:
1. `getCurrentIssues` y lee `issues.md`.
2. `setPlanState(nombre, "running")`.
3. Continúa en modo ejecución.

### `done` — Cerrado

Solo lectura. No modifiques nada. Si el usuario quiere un nuevo ciclo sobre
el mismo tema, `createPlan` con otro nombre.

## Formato de `tasks.md`

Una tarea por línea. Formato exacto:

```
N. [ ] descripción — refs: [Título sección A, Título sección B]
```

- `N`: entero, ID inmutable y unico de la tarea. Se asigna al crearla y no cambia
  aunque se reordene la lista. Se referencia como `#N` en `issues.md` y en
  la conversación.
- `[ ]`: estado. Válidos: `[ ]` pendiente, `[~]` en curso, `[x]` terminada,
  `[-]` abortada.
- `descripción`: qué hay que conseguir. Una línea.
- `refs: [Título, Título]`: títulos de sección de `specs.md` que gobiernan
  la tarea. Se leen con `getSections` al ejecutarla. Si la tarea no necesita
  contexto adicional, `refs: []`.

Ejemplo:

```
3. [ ] Migrar el esquema de la tabla `accounts` — refs: [Persistencia del estado, Contrato de componentes]
```

El parser de `PlanCore` es estricto con el formato. Líneas que no encajen
se ignoran. Si `setPlanState(_, "running")` falla con "no tiene tareas con
formato", revisa que las líneas empiecen por `N. [`.

## Formato de `issues.md`

Cuaderno libre. Sin estados ni checklist. Entradas que empiezan por guion,
referenciando tareas por `#N` cuando aplique:

```
- #3: abortada, el esquema en specs no contempla la columna `legacy_id`.
- #5: implementada con `ConcurrentHashMap` en vez de `HashMap` porque los tests en paralelo fallaban.
- Decisión pendiente: confirmar si `stop()` debe cerrar conexiones o solo marcarlas.
```

No hay parser. Es para que el usuario y tú leáis al reanudar.

## Lectura quirúrgica de `specs.md` con `getSections`

No leas `specs.md` completo en cada tarea. Localiza y extrae solo las
secciones que la tarea cita en `refs:`, con el script `getSections`.

```
run_skill_script(
  name:  "plan_manager",
  script: "getSections.groovy",
  args:  ["Persistencia del estado", "Contrato de componentes"]
)
```

Reglas:

- Un solo título o varios: todos en `args`, se leen en la misma llamada.
- El script delimita la sección hasta el siguiente encabezado de nivel igual
  o superior. Un encabezado de nivel 2 arrastra sus subsecciones. Pide el
  nivel más bajo que cubra lo que necesitas.
- Sin `args`, devuelve el índice completo de encabezados con línea de inicio
  y presupuesto en tokens. Úsalo cuando no conozcas el título exacto.
- Si devuelve `"Ninguna sección encontrada"`, significa que el título no
  coincide, no que la sección no exista. Prueba un título más corto, o pide
  el índice.
- Si un título matchea con varias secciones, el script las lista. Elige una
  y vuelve a llamar con el título completo.

## Verificación

Una tarea solo pasa a `[x]` cuando has contrastado la especificación con la
implementación. **No compiles ni arranques la aplicación tras cada tarea**:
la prueba de integración se agrupa al cerrar cada fase.

Antes de marcar `[x]`, dos comprobaciones baratas:

1. Todos los imports relativos del directorio afectado resuelven.
2. No queda ninguna referencia a los símbolos que la tarea ha eliminado.

Si la tarea modifica código que no puedes verificar con esas dos comprobaciones,
anótalo en `issues.md` como pendiente de verificar antes de cerrar la fase.

## Reglas

1. **Una sola tarea en curso.** No pases a la siguiente sin cerrar la anterior.
2. **IDs inmutables.** `#N` no cambia aunque reordenes la lista.
3. **No actives el plan por iniciativa propia.** `creating → running` es del usuario.
4. **No cierres un plan con tareas pendientes o en curso.** `setPlanState(_, "done")` lo rechaza.
5. **No mezcles fases** ni adelantes trabajo de una fase posterior.
6. **No modifiques `specs.md` sin rectificación.** Marca siempre.
7. **No inventes contenido de la especificación.** Si falta, para y pregunta.
8. **Los ficheros son la fuente de verdad.** Consulta, no asumas.
9. **No reabras un plan `done`.** Crea uno nuevo si hace falta.
