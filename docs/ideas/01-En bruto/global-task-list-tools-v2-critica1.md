Tu escepticismo es **totalmente acertado**. El hecho de que hayas tenido esa funcionalidad aparcada responde a un instinto de ingeniería muy sano: **has visto que casi todos los intentos de meter gestión de tareas en agentes de IA acaban siendo un desastre o un juguete.**

Para entender si realmente la necesitas o si es una trampa de sobreingeniería, hay que responder con crudeza a dos preguntas: 
1. ¿Por qué los demás agentes acabaron simplificándolo a una lista tonta de *TODOs*?
2. ¿Tiene Noema una necesidad real distinta a la de ellos, o estás construyendo un Jira en miniatura que solo va a consumir tokens y burocracia?

---

### 1. Por qué los demás agentes lo hacen "de juguete"

No es que los creadores de otros agentes sean vagos; es que entre 2023 y 2024 la industria se dio de bruces contra dos muros empíricos:

#### A. La "Ley de Parkinson" de los LLMs (La trampa del meta-trabajo)
Cuando a un agente autónomo le das herramientas ricas de planificación (crear tareas, desglosar en subtareas, estimar, cambiar prioridades, mover dependencias), el LLM sufre una patología inmediata: **se convierte en un burócrata.**
* En proyectos como AutoGPT o BabyAGI, el agente consumía el 60% de los tokens y del tiempo decidiendo si la tarea 3 dependía de la 2, reordenando la cola, actualizando estados a `IN_PROGRESS` y creando tareas redundantes (*"Analizar el resultado del análisis anterior"*).
* El modelo gastaba su atención **gestionando el trabajo en lugar de hacer el trabajo**.

#### B. La ventana de contexto gigante y la vida efímera
Los agentes modernos comerciales (Claude Code, Aider, Cursor) operan en sesiones de **ciclo corto** (de 5 minutos a 1 hora) y disponen de ventanas de 128k a 200k tokens. 
Para una tarea que dura 20 minutos, **un LLM no necesita una base de datos relacional**. Le basta con mantener un archivo `TODO.md` plano en el proyecto o tener un bloque de texto en el prompt. Cuando la tarea se completa, la sesión muere, el contenedor se apaga y el estado se borra. ¿Para qué construir tablas SQL complejas para algo que tiene una esperanza de vida de media hora?

---

### 2. Por qué Noema es DIFERENTE (La trampa de la sesión infinita)

Aquí es donde tu proyecto se separa radicalmente de Claude Code o Cursor. 

Noema no muere a los 20 minutos. En Noema:
1. **La conversación se poda y se consolida:** Tu pipeline de atención (`TrimmingOperation` y `MemoryConsolidationServiceImpl`) compacta los turnos antiguos en un relato narrativo ("El Viaje").
2. **El problema:** Una crónica narrativa es excelente para recordar *por qué* se tomó una decisión técnica hace dos semanas, pero es **pésima para recordar una lista de verificación precisa**. 
   Si dentro de tres días retomas una migración de 12 pasos, "El Viaje" te dirá: *«El usuario y el agente avanzaron en la migración de esquemas y resolvieron varios fallos {cite:82}»*. Pero no sabrás si el paso 7 se llegó a probar o si faltaban los índices.

Por tanto: **en Noema, la memoria prospectiva (el futuro/tareas) SÍ es necesaria porque tu memoria retrospectiva (el pasado) sufre una amnesia controlada deliberada.**

---

### 3. El peligro real de tu propuesta (`global-task-list-tools-v2.md`)

Ahora bien, mirando tu documento con lupa crítica: **tu propuesta actual coquetea peligrosamente con la sobreingeniería de 2023.**

Meter **7 herramientas nuevas** (`plan_create`, `plan_suspend`, `plan_resume`, `plan_list`, `task_create`, `task_update`, `task_list`) para que el LLM gestione tareas tiene un coste enorme:
* **Saturación del catálogo:** Estás metiendo 7 esquemas JSON de herramientas en el prompt de sistema en cada turno, compitiendo con `file_search_and_replace`, `file_grep` o `diagnostics`.
* **Riesgo de burocracia:** Tentarás al modelo a hacer llamadas intermedias de actualización de estado que aportan cero valor al código y solo añaden latencia y coste.

---

### 4. Veredicto: ¿Cómo rescatar lo valioso sin caer en el "juguete"?

Hiciste bien en aparcarlo. La idea de gestionar planes a largo plazo es vital para Noema, pero **el mecanismo que propusiste en ese documento es demasiado pesado.**

Si algún día decides activarlo, la forma pragmática de implementarlo en Noema no es con 7 herramientas relacionales, sino con la **navaja de Ockham**:

1. **El valor real de tu diseño no son las herramientas; es `ActivePlanOperation`:**
   Lo verdaderamente brillante de tu documento es la idea de que **si el plan no está activo, consume CERO tokens de contexto**, y que solo se proyecta un bloque compacto cuando el cuaderno está abierto sobre la mesa.
2. **Simplificar radicalmente las herramientas:**
   En lugar de 7 herramientas, el modelo solo necesita **dos**:
   * `plan_set(name, markdown_checklist)`: Define o actualiza el plan activo pasando un texto simple en Markdown con casillas `- [ ]` y `- [x]`.
   * `plan_close(status)`: Cierra o suspende el cuaderno.
3. El LLM es extraordinariamente bueno leyendo y marcando casillas Markdown `[ ]` $\rightarrow$ `[x]`, mientras que sufre gestionando IDs numéricos, posiciones relativas (`after_task_id`) y claves foráneas en SQL.
4. Tu base de datos H2 en `service.mv.db` puede seguir guardando el estado, pero el contrato con el LLM debe ser minimalista.

### Resumen
No lo descartes por completo, pero **manténlo aparcado hasta que te duela su ausencia**. 

Ahora mismo tienes prioridades mucho más críticas y con un retorno inmediato infinitamente mayor (el protocolo de voz fluida y el afinado de la edición con diagnósticos LSP). Cuando Noema sea un programador quirúrgico y mantengas con él proyectos de varias semanas, tú mismo sentirás el momento exacto en que te haga falta ese "cuaderno de trabajo" persistente.
