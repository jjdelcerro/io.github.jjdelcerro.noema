
## `addTask`

**Args.** `[descripción, ref1, ref2, ...]` — lista plana, consistente con `getSections`. El primer elemento es la descripción; el resto, títulos de sección de `specs.md`. Si no hay refs, `args = ["descripción"]`. Alternativa con separador (`[desc, "A;B"]`) es más robusta a comas en la descripción pero menos natural. Yo me quedo con lista plana: si la descripción lleva comas, el LLM no las va a meter en `args` porque las comas están dentro de un solo elemento de la lista.

**Estado del plan.** Solo `creating`. En `stopped` rechazar con un mensaje que remita a `setPlanState(_, "creating")` — la máquina de estados ya permite `stopped → creating`, y es coherente: reestructurar tareas es reabrir el borrador. En `running` y `done`, rechazar sin paliativos.

**ID.** `max(existing IDs) + 1`. Con lista vacía empieza en 1. Si el LLM borró una tarea a mano y queda un hueco (`1, 3`), el siguiente es `4`, no se reutiliza `2`. IDs inmutables implica que no se reciclan.

**Validación de refs.** Ninguna. Si el usuario dice "usa esta sección" y todavía está escribiendo `specs.md`, la sección puede no existir aún. Un warning en el retorno (`"añadida #4 (refs no verificadas: X)"`) sí, pero sin bloquear.

**Retorno.** El ID asignado y la línea resultante, para que el LLM pueda mostrarla o referenciarla:

```
Añadida tarea #4 en tasks.md:
4. [ ] Migrar tabla accounts — refs: [Persistencia del estado, Contrato de componentes]
```

## `setTaskState`

**Args.** `[id, state]` con `state` en caracteres: `"~"`, `"x"`, `"-"`, `" "` (espacio, para volver a pendiente). Coherente con lo que el LLM ve en el fichero: si ve `[~]`, usa `"~"`. Nombres como `"in_progress"` obligan a una tabla de traducción mental.

**Estado del plan.** Solo `running`. En `creating` las tareas se editan a mano (o vía `addTask`) y no hay transiciones de estado. En `stopped` y `done`, rechazar. Esto preserva el invariante de que el estado de las tareas solo se mueve durante la ejecución.

**Transiciones válidas.** Estricto:

| desde → hacia | `~` | `x` | `-` | ` ` |
|---|---|---|---|---|
| ` ` (pendiente) | sí (si no hay otra `~`) | no | sí | — |
| `~` | — | sí | sí | sí (abandonar sin cerrar) |
| `x` | no | — | no | no |
| `-` | no | no | — | no |

Las dos decisiones discutibles:

- **`  → x` (cerrar sin empezar)**: no. Obliga a pasar por `~`, aunque sea un instante. Eso garantiza que el LLM ha "abierto" la tarea, lo que fuerza a leer `specs.md` antes (porque el SKILL dice "al marcar `[~]`, lee las refs"). Si permites `  → x`, el LLM puede cerrar sin leer nada.
- **`x → ` (reabrir)**: no en `running`. Una tarea cerrada es un hecho consumado; si estaba mal, se rectifica `specs.md` y se añade una nueva tarea que la corrige. Reabrir `x` invita a reescribir historia. Si el usuario quiere rehacer, vuelve a `creating` y ahí puede editar todo.

**Implementación.** Leer el fichero entero con `parseTasks`, localizar por `line` la tarea con ese `id`, sustituir solo esa línea en el array, y `write` el fichero completo. Más robusto que `searchAndReplace` (que falla si el texto no es único) y el coste de escribir un fichero pequeño es despreciable. La copia RCS automática que hace `write` te da historial gratis.

**Retorno.** Confirmación con la línea resultante y, si aplica, el progreso:

```
Tarea #3 → [x]:
3. [x] Migrar tabla accounts — refs: [...]
Progreso: 3/10 completadas, 0 abortadas.
```

En `setTaskState(_, "x")` conviene además comprobar si era la última pendiente y, si lo es, sugerir `setPlanState(_, "done")` en el retorno. No hacerlo automáticamente — la decisión de cerrar el plan es del usuario — pero sí mencionarlo.

## Lo que cambia en `PlanCore`

Dos métodos nuevos (`addTask`, `setTaskState`) y un helper privado (`formatTaskLine`) que comparte `addTask` con `createPlan`/`parseTasks` para que el formato de línea esté en un solo sitio. `parseTasks` ya devuelve `line` y `id`, así que `setTaskState` lo consume tal cual.

## Lo que cambia en `SKILL.md`

Tres líneas:

- Añadir los dos scripts a la tabla.
- En modo `creating`: "cuando el usuario defina tareas, añádelas con `addTask`, no a mano. El script asigna el ID y respeta el formato."
- En modo `running`: "marca el estado de las tareas con `setTaskState(id, '~'|'x'|'-'|' ')`, no editando `tasks.md` a mano."

Con esto, el LLM deja de escribir `tasks.md` directamente. Toda la manipulación pasa por `PlanCore`, y el parser estricto deja de ser una fuente de fallos silenciosos: si algo va mal, `addTask` o `setTaskState` devuelven error, no corrupción.

## Lo que NO cambia

`getCurrentTasks`, `listPlans`, `createPlan`, `getSections` — siguen igual. `parseTasks` gana la rama de refs que ya tiene. La plantilla de `tasks.md` puede quedarse vacía (solo título): el LLM ya no la edita.

---

# Anexo plan push/pop 

**Conceptualmente encaja bien.** El flujo real es "estoy en A, descubro que necesito hacer B primero, hago B, vuelvo a A". Hoy eso son dos pares de `setPlanState` manuales y recordar el orden tú. Un stack lo automatiza. Para proyectos con prerrequisitos descubiertos sobre la marcha, es la forma natural.

**Lo importante: es una capa encima de la máquina de estados, no un reemplazo.** `pushPlan(B)` internamente hace `setPlanState(A, "stopped")` + `setPlanState(B, "running")` y apunta `A` en una pila. `popPlan()` hace lo inverso. `PlanCore` no cambia; solo se añaden dos métodos y un fichero `.noema-agent/plans/.stack`. Eso significa que **puedes posponerlo sin coste**: implementa la validación de "un solo `running`" en la segunda vuelta tal como está planeada, y cuando quieras el stack, lo añades encima.

**Cuatro decisiones que tendrás que cerrar cuando lo abordes:**

1. **¿`suspended` como estado nuevo, o reusar `stopped`?** Mi voto: reusar `stopped`. El stack ya guarda el orden; no necesitas distinguir "parado por el usuario" de "parado porque hay otro encima". Menos estado, menos transiciones, mismos casos cubiertos.

2. **¿Auto-pop al cerrar el plan?** Si `setPlanState(top, "done")` y hay algo en la pila, ¿resucitas automáticamente el anterior? Mi voto: no. `popPlan()` explícito. Si el LLM cierra A sin darse cuenta de que había B en la pila, prefieres que te pregunte a que reactive B por su cuenta.

3. **Interacción con `listPlans`.** La regla actual ("`running` no cargado → `stopped`") sigue siendo correcta, pero necesitas que `listPlans` muestre también la pila: `"A: stopped (en pila, debajo de B)"`. Si no, el LLM ve dos `stopped` y no sabe cuál retomar.

4. **Plan desaparecido de la pila.** Si `popPlan` encuentra que el plan de abajo se borró del disco, debe saltarlo (o fallar con mensaje claro, y dejar la pila consistente). Un stack que se corrompe al primer borrado es peor que no tener stack.

**Mi recomendación:** no lo diseñes ahora, pero **redacta la validación de "un solo running" pensando en que el stack es posible**. Concretamente: el check no debe ser "no hay otro plan running en disco", sino "el plan que voy a activar es el que `agent.state.currentPlan` dice", o si prefieres una regla más simple: "solo se puede activar un plan si no hay otro cargado". La primera deja la puerta abierta al stack (el stack controla qué está cargado); la segunda la cierra. Es una decisión de una línea de código con consecuencias arquitectónicas. Merece la pena pensarla, aunque el código se escriba en la segunda vuelta.

Y un apunte que se me ocurrió al hilo: si haces push/pop, `plan_awareness` no necesita cambios. Ya lee `agent.state.currentPlan`, que es el top de la pila. El aviso siempre muestra el plan activo, que es lo correcto.
