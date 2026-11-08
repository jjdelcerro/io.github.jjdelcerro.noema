# Especificación Técnica: Gestión de Tareas y Planes de Ejecución en Noema


---

## 1. Análisis

### 1.1. Motivación

En sesiones de trabajo prolongadas sobre proyectos complejos, los modelos de lenguaje tienden a sufrir pérdida de inercia operativa y dispersión de objetivos. Cuando un agente se enfrenta a un procedimiento compuesto por múltiples tareas secuenciales, surgen patrones de fallo recurrentes:

*   **Pérdida del hilo:** Tras completar una tarea intermedia y recibir la confirmación de la herramienta correspondiente, el agente suele detener su ejecución y esperar pasivamente una nueva orden del usuario, olvidando que formaba parte de un plan más amplio.
*   **Falta de disciplina metodológica:** Si las directivas operativas se introducen únicamente en el diálogo o se dejan a la memoria de trabajo del modelo, la síntesis progresiva del servicio de consolidación o el recorte de la ventana de contexto terminan diluyendo las restricciones técnicas (por ejemplo: la obligatoriedad de verificar antes de dar por buena una tarea, o trabajar estrictamente en orden secuencial).
*   **Corrupción del estado por escrituras encadenadas:** Si se exige al modelo que mantenga sincronizados manualmente varios ficheros de control (por ejemplo, cambiar el estado de una tarea y redactar simultáneamente una incidencia en otro documento), la tasa de error por alucinación o abandono de uno de los pasos es inasumible.

El intento de resolver esto mediante instrucciones más largas o reiterativas en el mensaje de sistema resulta ineficaz. La solución requiere un mecanismo arquitectónico determinista que descargue la memoria del modelo y mantenga el estado anclado físicamente en disco y proyectado en su contexto turno a turno.

### 1.2. Idea Conceptual

El sistema se fundamenta en la segregación estricta de tres artefactos esenciales:

*   **Artefacto A (El Qué / Especificaciones):** Documento técnico que describe el objetivo, la arquitectura de la solución, los contratos afectados y los criterios de diseño. Es una referencia de lectura.
*   **Artefacto B (El Cómo / Protocolo de Trabajo):** Conjunto inmutable de directivas procedimentales que dictan cómo interactuar con el plan (trabajar de uno en uno, estados permitidos, reglas de verificación previa al cierre). Se modela como una *Skill* procedural (`task_executor`).
*   **Artefacto C (El Estado / Checklist de Tareas):** Documento mutable que contiene exclusivamente la lista ordenada de tareas y su estado actual (`[ ]` pendiente, `[~]` en curso, `[x]` completada, `[-]` abortada).

#### Principios operativos del diseño:

*   **El fichero físico como única fuente de verdad:** El agente no retiene en su memoria interna el avance del plan; lee y modifica el fichero `tasks.md`. Ante cualquier reinicio o pérdida de contexto, el estado persiste intacto en disco.
*   **Desacoplamiento de rutas mediante scripts auxiliares:** El modelo no debe calcular rutas relativas ni absolutas complejas. Una colección de scripts Groovy empaquetados dentro de la propia *skill* asume la responsabilidad de localizar, leer y estructurar el acceso a los ficheros del plan.
*   **Brújula mediante Memoria Proyectada:** Al fijar un plan activo, se registra una operación dinámica en la memoria proyectada del agente. Esta operación inyecta al final de cada turno un aviso efímero que actúa como recordatorio constante del plan en vigor. Al ser proyectado, no contamina la memoria episódica ni sobrecarga la base de datos histórica.
*   **Polimorfismo puro en el Core:** No se introducen subsistemas ajenos al ciclo de vida del agente. Una operación de recordatorio dinámico no es más que una implementación de `AbstractProjectedMemoryOperation` delegada en una *closure* de Groovy.

---

## 2. Diseño de la Skill (`task_executor`)

La *skill* `task_executor` proporciona al agente las directivas metodológicas para ejecutar planes de trabajo estructurados y expone las utilidades necesarias para interactuar con ellos.

### 2.1. Ubicación y Estructura

Se ubica en la carpeta estándar de habilidades del sistema o del espacio de trabajo:

*   Manifiesto y protocolo: `var/skills/task_executor/SKILL.md`
*   Scripts auxiliares: `var/skills/task_executor/scripts/*.groovy`

Los planes creados y gestionados por esta habilidad residirán en el directorio del agente:

*   Directorio base: `.noema-agent/plans/<nombre_plan>/`
*   Archivos contenidos por plan:
    *   `tasks.md`: Lista de tareas con casillas de verificación.
    *   `specs.md`: Especificaciones técnicas y decisiones de diseño asociadas.

### 2.2. Propuesta de Contenido para `SKILL.md`

```markdown
---
name: task_executor
description: Protocolo estricto para la ejecución metódica y secuencial de planes de tareas basados en especificaciones técnicas. Úsalo cuando debas abordar proyectos estructurados divididos en tareas concretas sin perder el contexto de avance ni saltarte pasos de validación.
version: 1.0.0
---

## Principios de Ejecución de Tareas

1. **Una sola tarea activa:** Está terminantemente prohibido asumir más de una tarea simultáneamente. Solo puede haber una tarea en estado `[~]` en todo el documento.
2. **Secuencialidad estricta:** Las tareas deben abordarse en el orden en que aparecen listadas. No comiences una tarea posterior sin haber cerrado formalmente la anterior.
3. **El fichero como fuente de verdad:** Tu estado de avance reside exclusivamente en el fichero `tasks.md` del plan activo. No asumas de memoria qué tareas has realizado; consúltalo siempre a través de los scripts auxiliares.
4. **Verificación antes de cerrar:** Una tarea solo pasa a `[x]` cuando has comprobado físicamente en los fuentes o en el sistema que el cambio se ajusta a lo especificado y no introduce regresiones.
5. **Cierre de plan explícito:** Cuando todas las tareas alcancen el estado `[x]` o `[-]`, debes desactivar formalmente el plan.

## Estados de una Tarea

* `[ ]` : Tarea pendiente. No iniciada.
* `[~]` : Tarea en curso. Solo una puede ostentar este estado a la vez.
* `[x]` : Tarea finalizada y verificada satisfactoriamente.
* `[-]` : Tarea descartada, cancelada o abortada por imposibilidad técnica.

## Procedimiento de Trabajo

Para cada paso del plan, debes seguir estrictamente este ciclo:

1. **Localización:** Ejecuta `run_skill_script` con `getCurrentTasks.groovy` para conocer la tarea en curso o determinar cuál es la siguiente tarea pendiente.
2. **Contextualización:** Si requieres detalles arquitectónicos o requisitos concretos, consulta la especificación con `getCurrentPlanSpecifications.groovy` y lee las secciones pertinentes.
3. **Transición a Activa:** Utiliza `file_search_and_replace` sobre el fichero `tasks.md` para cambiar la tarea seleccionada de `[ ]` a `[~]`.
4. **Implementación:** Realiza los cambios necesarios en el proyecto respetando las normas arquitectónicas habituales.
5. **Verificación:** Comprueba que la tarea cumple su propósito sin compilar ni arrancar de forma innecesaria salvo que esté expresamente pautado.
6. **Transición a Completada:** Utiliza `file_search_and_replace` sobre `tasks.md` para cambiar la tarea de `[~]` a `[x]`.
7. **Continuidad:** Vuelve inmediatamente al paso 1 sin esperar confirmación del usuario, salvo que el procedimiento exija una validación humana explícita.

## Scripts Auxiliares Disponibles

Utiliza la herramienta `run_skill_script` con el parámetro `name: "task_executor"` para invocar cualquiera de los siguientes scripts en `script`:

* `setCurrentPlan.groovy`: Establece el plan activo, crea sus ficheros si no existen y activa el recordatorio continuo en tu memoria de trabajo. Recibe como argumento en `args` una lista con el nombre del plan (ej: `["migracion-db"]`).
* `getCurrentPlanName.groovy`: Devuelve el nombre del plan en vigor.
* `getCurrentTasks.groovy`: Analiza el fichero de tareas del plan activo y devuelve su ruta, la tarea actualmente en curso y la siguiente pendiente.
* `getCurrentPlanSpecifications.groovy`: Devuelve la ruta absoluta al documento `specs.md` del plan actual.
* `clearCurrentPlan.groovy`: Desactiva el plan actual y retira los avisos de la memoria proyectada.
```

---

## 3. Scripts de la Skill

Todos los scripts se implementan en lenguaje Apache Groovy y se benefician del contexto expuesto a través de `agent` (`agent.fs`, `agent.state`, `agent.memory`).

### 3.1. `setCurrentPlan.groovy`

Inicializa el entorno del plan, persiste su nombre en la sesión y añade la operación dinámica en la memoria proyectada.

```groovy
// var/skills/task_executor/scripts/setCurrentPlan.groovy

def planName = (binding.hasVariable('args') && args && args[0]) ? args[0].toString().trim() : null

if (!planName) {
    return "ERROR: Debe indicarse el nombre del plan. Uso: args = ['nombre-del-plan']"
}

def basePath = ".noema-agent/plans/${planName}"
def tasksPath = "${basePath}/tasks.md"
def specsPath = "${basePath}/specs.md"

// 1. Inicialización de la estructura en disco si no existe previamente
if (!agent.fs.find(tasksPath)) {
    def tasksTemplate = """# Plan: ${planName}

## Tareas

- [ ] 1. Especificar los requisitos y alcance en specs.md
"""
    agent.fs.write(tasksPath, tasksTemplate)
}

if (!agent.fs.find(specsPath)) {
    def specsTemplate = """# Especificación Técnica: ${planName}

## 1. Alcance y Objetivos

## 2. Decisiones de Diseño
"""
    agent.fs.write(specsPath, specsTemplate)
}

// 2. Almacenamiento del plan en el estado volátil de la sesión
agent.state.currentPlan = planName

// 3. Registro de la operación de recordatorio en la Memoria Proyectada
agent.memory.registerOperation("plan_awareness", 40) { mem, notifications ->
    def planName = agent.state.currentPlan
    if (planName) {
            notifications.add(
        """[PLAN EN EJECUCIÓN: "${planName}"]
        Directiva: Trabaja de forma secuencial una tarea a la vez. Al concluir la tarea actual, márcala como [x], consulta la siguiente con getCurrentTasks y procede.
        """)
        }
    }

return """Plan '${planName}' establecido correctamente.
- Fichero de tareas: ${tasksPath}
- Fichero de especificaciones: ${specsPath}
Aviso de supervisión activado en la memoria proyectada."""
```

### 3.2. `getCurrentPlanName.groovy`

Consulta el plan activo en el estado de la sesión.

```groovy
// var/skills/task_executor/scripts/getCurrentPlanName.groovy

def planName = agent.state.currentPlan
if (!planName) {
    return "No hay ningún plan activo actualmente."
}
return planName
```

### 3.3. `getCurrentPlanSpecifications.groovy`

Resuelve y retorna la ruta del documento de especificaciones técnicas.

```groovy
// var/skills/task_executor/scripts/getCurrentPlanSpecifications.groovy

def planName = agent.state.currentPlan
if (!planName) {
    return "ERROR: No hay ningún plan configurado. Ejecuta primero setCurrentPlan.groovy."
}

def specsPath = ".noema-agent/plans/${planName}/specs.md"
return specsPath
```

### 3.4. `getCurrentTasks.groovy`

Realiza una lectura estructurada de `tasks.md` e informa con precisión de las tareas en curso y de la siguiente pendiente, ahorrando turnos y tokens de lectura completa.

```groovy
// var/skills/task_executor/scripts/getCurrentTasks.groovy

def planName = agent.state.currentPlan
if (!planName) {
    return "ERROR: No hay ningún plan configurado. Ejecuta primero setCurrentPlan.groovy."
}

def tasksPath = ".noema-agent/plans/${planName}/tasks.md"

def inProgress = []
def pending = []
int totalCompleted = 0
int totalAborted = 0

agent.fs.forEachLine(tasksPath) { line, lineNumber ->
    def trimmed = line.trim()
    if (trimmed.contains("[~]")) {
        inProgress << "L${lineNumber}: ${trimmed}"
    } else if (trimmed.contains("[ ]")) {
        pending << "L${lineNumber}: ${trimmed}"
    } else if (trimmed.contains("[x]")) {
        totalCompleted++
    } else if (trimmed.contains("[-]")) {
        totalAborted++
    }
}

def report = new StringBuilder()
report.append("Plan: ${planName}\n")
report.append("Fichero: ${tasksPath}\n\n")

if (!inProgress.isEmpty()) {
    report.append("TAREA EN CURSO:\n")
    inProgress.each { report.append("  ").append(it).append("\n") }
    report.append("\n")
} else {
    report.append("ESTADO: No hay ninguna tarea en curso.\n")
    if (!pending.isEmpty()) {
        report.append("Siguiente tarea a iniciar: ").append(pending.first()).append("\n\n")
    } else {
        report.append("Todas las tareas han sido completadas o canceladas.\n\n")
    }
}

report.append("Progreso: ${totalCompleted} completadas, ${inProgress.size()} en curso, ${pending.size()} pendientes, ${totalAborted} abortadas.")
return report.toString()
```

### 3.5. `clearCurrentPlan.groovy`

Elimina la operación proyectada y limpia las variables de estado.

```groovy
// var/skills/task_executor/scripts/clearCurrentPlan.groovy

def planName = agent.state.currentPlan
if (!planName) {
    return "No hay ningún plan activo que desactivar."
}

agent.memory.removeOperation("plan_awareness")
agent.state.currentPlan = null

return "Plan '${planName}' desactivado. La operación de aviso proyectado ha sido retirada."
```

---

## 4. Modificaciones en el Código del Agente (Java Core)

Para dar soporte a este mecanismo sin crear deuda técnica ni añadir capas artificiales, se requieren cambios muy localizados en el motor Java.

### 4.1. Soporte de Groovy en `RunSkillScriptTool` y `Skill`

Actualmente, `RunSkillScriptTool` delega en `Skill.createScriptProcess()`, que únicamente prepara un proceso externo de sistema operativo mediante `ProcessBuilder("bash", ...)`.

*   **Archivo:** `io.github.jjdelcerro.noema.lib.impl.services.skills.tools.RunSkillScriptTool.java`
*   **Modificación:**
    *   Detectar si el nombre del script termina en `.groovy`.
    *   Si es `.groovy`, no invocar procesos externos de Bash. Obtener el servicio de *scripting* a través de `agent.getService(ScriptingService.NAME)`.
    *   Instanciar un `ScriptEngine` mediante `createScriptEngine()`.
    *   Inyectar los argumentos opcionales en el motor: `engine.setVariable("args", scriptArgs)`.
    *   Ejecutar el contenido del fichero del script leído desde la carpeta `scripts/` del *skill*.
    *   Capturar el resultado devuelto y enviarlo a través de `servePaginatedResource` o como `ToolResult` estándar.
*   **Archivo:** `io.github.jjdelcerro.noema.lib.impl.services.skills.Skill.java`
    *   Añadir un método de conveniencia para resolver la ruta o contenido de un script (ej. `public Path getScriptPath(String scriptName)`).

### 4.2. Creación de `ClosureProjectedMemoryOperation`

Implementación genérica de una operación proyectada cuyo procesamiento delega en una función o clausura dinámica.

*   **Paquete nuevo o existente:** `io.github.jjdelcerro.noema.lib.impl.memory.projected.operations`
*   **Nueva Clase:** `ClosureProjectedMemoryOperation extends AbstractProjectedMemoryOperation`
*   **Diseño:**
    *   Constructor: Recibe `Agent agent`, `String name`, `int priority` y `groovy.lang.Closure<?> closure`.
    *   Interfaz auxiliar para la notificación:
        ```java
        @FunctionalInterface
        public interface NotificationConsumer {
            void addNotification(String text);
        }
        ```
    *   Método `process(ProjectedMemory memory, List<ChatMessage> projectedMessages, List<String> notifications)`:
        *   Instancia el consumidor adaptado: `NotificationConsumer consumer = notifications::add;`.
        *   Invoca la clausura pasando el consumidor: `closure.call(consumer);`.
    *   Métodos `getState()` y `restoreState()`: Retornan `null` y permanecen vacíos, ya que su ciclo de vida está gobernado por el script de sesión.

### 4.3. Gestión Dinámica de Operaciones en `ProjectedMemory`

Actualmente, `ProjectedMemoryImpl` solo inicializa sus operaciones en el constructor leyendo las factorías registradas en `AgentManager`. Debe permitirse añadir y retirar operaciones en tiempo de ejecución.

*   **Interfaz:** `io.github.jjdelcerro.noema.lib.api.memory.projected.ProjectedMemory.java`
    *   Añadir: `void addOperation(ProjectedMemoryOperation operation);`
    *   Añadir: `boolean removeOperation(String name);`
*   **Implementación:** `io.github.jjdelcerro.noema.lib.impl.memory.projected.ProjectedMemoryImpl.java`
    *   `addOperation`: Añade a la lista interna `operations` y reordena la colección por prioridad (`this.operations.sort(Comparator.comparingInt(ProjectedMemoryOperation::getPriority));`).
    *   `removeOperation`: Elimina de `this.operations` cualquier elemento que coincida con `name` (ignorando mayúsculas/minúsculas).

### 4.4. Módulo de Scripting `MemoryModule`

Exponer de forma segura la capacidad de manipular la memoria proyectada al motor Groovy a través del objeto contextual `agent`.

*   **Nuevo archivo:** `io.github.jjdelcerro.noema.lib.impl.services.scripting.modules.MemoryModule.java`
*   **Diseño:**
    *   Extiende de `AbstractScriptModule`. Nombre del módulo: `"memory"`.
    *   Factoría: `MemoryScriptModuleFactory` registrada en `AgentManagerImpl` (o en `ScriptContextImpl`).
    *   Métodos expuestos:
        *   `public void registerOperation(String name, int priority, groovy.lang.Closure<?> closure)`: Resuelve la `ProjectedMemory` correspondiente al subcanal actual a través de `ReasoningService`, crea una instancia de `ClosureProjectedMemoryOperation` y llama a `projectedMemory.addOperation(...)`.
        *   `public void removeOperation(String name)`: Resuelve la `ProjectedMemory` del subcanal actual y ejecuta `projectedMemory.removeOperation(name)`.
        *   `public String help()`: Documenta los métodos del módulo para el autodescubrimiento del agente.

### 4.5. Verificación de Políticas de Acceso (`AgentAccessControlImpl`)

*   **Archivo:** `io.github.jjdelcerro.noema.lib.impl.AgentAccessControlImpl.java`
*   **Verificación:**
    *   Asegurar que la ruta `.noema-agent/plans/` sea tratada como un directorio regular de lectura/escritura dentro del espacio de trabajo.
    *   La regla inmutable actual bloquea exclusivamente los caminos que contengan `/.noema-agent/var/`. Dado que los planes residen en `.noema-agent/plans/`, quedan fuera de la zona restringida de control interno y no requerirán alteraciones en los filtros de seguridad.

---

## 5. Resumen del Flujo de Ejecución

1.  El usuario decide iniciar un plan estructurado e indica el nombre del proyecto o iniciativa.
2.  El agente activa el protocolo procedimental invocando la herramienta `activate_skill(name: "task_executor")`. Esto fija inmutablemente las normas de ejecución en su memoria de trabajo.
3.  El agente inicializa el plan invocando `run_skill_script(name: "task_executor", script: "setCurrentPlan.groovy", args: ["nombre_plan"])`.
4.  El script crea los ficheros `tasks.md` y `specs.md` en `.noema-agent/plans/nombre_plan/` si no existían, fija la variable en la sesión e instancia en `ProjectedMemory` la operación `ClosureProjectedMemoryOperation`.
5.  En cada turno subsiguiente de conversación, `ProjectedMemoryImpl` procesa la operación, inyectando un aviso unificado (`poll_event`) con la ruta del plan y las tareas pendientes.
6.  El agente utiliza `getCurrentTasks.groovy` para obtener el estado y continúa trabajando en la tarea activa.
7.  Al agotarse las tareas, el agente o el usuario invocan `clearCurrentPlan.groovy`, retirando la operación proyectada y liberando la memoria del modelo para nuevas actividades.




# Apendice I

## Lo que está bien (y no es poco)

**1. El fichero como fuente de verdad.** Que el estado del plan viva en `tasks.md` y no en el contexto del LLM es la decisión correcta. Sobrevive a la consolidación de memoria, al pruning y a la compactación. Es lo único que garantiza que el plan no se pierda cuando el contexto se evapore.

**2. El recordatorio por memoria proyectada.** Usar `registerProjectedOperation` para inyectar `[PLAN EN EJECUCIÓN: X]` en cada turno es exactamente el primitivo adecuado. Es el mismo mecanismo que usan `PinnedTurnsOperation` o `PeripheralAwarenessOperation` para contrarrestar el olvido del LLM. Coherente con la arquitectura.

**3. Separación `specs.md` / `tasks.md`.** El LLM puede consultar la especificación sin arrastrar la lista de tareas en contexto. Buena higiene.

**4. Un solo `[~]` a la vez.** El invariante "una tarea activa" es el correcto para trabajo secuencial. Obliga a que el LLM "cierre" antes de "abrir".

**5. Los scripts no hacen I/O directo.** Usan `agent.fs`, `agent.state`, `agent.memory` — respetan el sandbox. Y como `MemoryModule` solo se registra en `run_skill_script`, saben que tienen que invocarse por ahí.

**6. El priority 40 del `plan_awareness`.** Encaja bien: corre después de trimming/pending/temporal pero antes de peripheral_awareness. La notificación llega al LLM, no se la come nadie.

---

## La brecha principal: es una **convención**, no un **protocolo**

El `SKILL.md` declara reglas:
- "Solo una tarea activa a la vez"
- "Secuencialidad estricta"
- "Una tarea solo pasa a `[x]` cuando la has comprobado"

Pero **nada las hace cumplir**. La transición de estado se hace con `file_search_and_replace` sobre `tasks.md`, que es una operación libre. El LLM puede:

- Poner dos `[~]` a la vez.
- Saltarse el `[~]` y pasar de `[ ]` a `[x]` directamente.
- Editar `tasks.md` a mano con `file_write` y romper el formato.
- No llamar nunca a `getCurrentTasks` y trabajar de memoria.

El `plan_awareness` recuerda el plan, pero no verifica el estado. `getCurrentTasks.groovy` lista los `[~]` que encuentra, pero si hay dos no los señala como error:

```groovy
if (!inProgress.isEmpty()) {
    report.append("TAREA EN CURSO:\n")
    inProgress.each { report.append("  ").append(it).append("\n") }  // múltiples, sin avisar
}
```

**La consecuencia práctica:** el "estado" del plan puede divergir de lo que las reglas dicen que debería ser. El LLM no se entera; el usuario tampoco; el plan sigue adelante con un invariante roto.

### La corrección natural: un script `setTaskState.groovy`

La transición de estado debería ser una operación atómica y validada, no un `file_search_and_replace` sobre el fichero completo. Algo como:

```groovy
// setTaskState.groovy
// args = [taskId, targetState]  →  taskId="3", targetState="~" | "x" | "-"
```

Con precondiciones:
- Si `targetState == "~"`: fallar si ya hay otro `[~]`.
- Si `targetState == "x"`: fallar si la tarea no estaba en `[~]`.
- Si `targetState == "-"`: aceptar desde `[ ]` o `[~]`, previa confirmación textual del motivo.

Y postcondiciones:
- Reescribir `tasks.md` de una pasada (no `file_search_and_replace` con contexto).
- Devolver `{ok, task, state, progress}` con `progress = "3 de 10"`.

Esto convierte el "protocolo" en código, no en una petición al LLM. Y de paso resuelve el falso positivo de `contains("[ ]")`: el parser es explícito, no heurístico.

El `SKILL.md` cambiaría en dos pasos:
- Paso 3 del procedimiento: `setTaskState("~")` en vez de `file_search_and_replace`.
- Paso 6: `setTaskState("x")` en vez del `file_search_and_replace`.

**Coste:** ~40 líneas de Groovy y un cambio de dos líneas en el SKILL.md. **Beneficio:** el invariante deja de ser una convención y pasa a ser verificable.

---

## La durabilidad del plan activo

`agent.state.currentPlan` es un `ConcurrentHashMap` estático en `ScriptContextImpl`. Tras un reinicio:

- El plan sigue en disco (`tasks.md`, `specs.md`).
- `agent.state.currentPlan` es `null`.
- El `plan_awareness` no se ha registrado (nunca se persistió).
- El LLM llama a `getCurrentPlanName` → "No hay ningún plan activo".
- El LLM no tiene ni idea de que hay un plan a medias.

Es decir: **el estado durable y el volátil se desincronizan de forma silenciosa**. El plan queda huérfano hasta que el usuario vuelva a decir "recupera el plan X".

### La corrección: un marcador durable de "plan activo"

Un fichero mínimo — `.noema-agent/plans/.active`, o un JSON con `{"name": "X"}` — resuelve el problema:

- `setCurrentPlan`: escribe el marcador **además** de `agent.state.currentPlan`.
- `getCurrentPlanName`: si `agent.state.currentPlan` es null, lee el marcador. Si existe, devuelve el nombre (y, opcionalmente, re-registra `plan_awareness` si no está ya).
- `clearCurrentPlan`: borra el marcador.

Con eso, el flujo sobrevive a reinicios: el LLM llama a `getCurrentPlanName`, obtiene el plan, y el recordatorio vuelve a activarse. Y la lógica de "re-registrar si el marcador existe" vive en `getCurrentPlanName`, no se duplica.

Un detalle: al re-registrar `plan_awareness` desde `getCurrentPlanName` hay que ser idempotente. Ya tienes el bucle `while (removeOperation("plan_awareness")) {}` en `setCurrentPlan`, así que el patrón es reutilizable.

---

## Ciclo de vida del recordatorio: **leak en `deactivate_skill`**

Este es el gap que no salta a la vista leyendo el skill aislado, pero aparece al mirar cómo `deactivate_skill` funciona por dentro.

`Skill.deactivate`:

```java
PinnedTurnsOperation operation = (PinnedTurnsOperation) projectedMemory.getOperation(PinnedTurnsOperation.OPERATION_NAME);
operation.removePinnedTurn(this::isMatchingPinnedTurn);
```

`isMatchingPinnedTurn` solo quita el turno fijado de `activate_skill`. **No toca** las operaciones registradas vía `agent.memory.registerProjectedOperation`. Es decir:

- `deactivate_skill("task_executor")` → retira las directivas fijadas del contexto proyectado, **pero `plan_awareness` sigue ejecutándose** y sigue inyectando `[PLAN EN EJECUCIÓN: X]` en cada turno.
- Si el usuario reactiva y desactiva varias veces, se acumulan operaciones `plan_awareness` (recuerda: `ProjectedMemoryImpl.addOperation` hace `append`, no `put`; el idempotente `while(removeOperation(...))` en `setCurrentPlan` solo limpia *al iniciar un plan nuevo*, no al desactivar el skill).

Tres formas de arreglarlo, en orden de menor a mayor cambio:

**a) Convención de teardown por skill.** `DeactivateSkillTool` busca `scripts/on_deactivate.groovy` dentro del directorio del skill y lo ejecuta si existe. El skill aporta el suyo (`agent.memory.removeOperation("plan_awareness")` + limpiar `agent.state.currentPlan`). Cambio localizado en `DeactivateSkillTool`, extensible a otras skills.

**b) `plan_awareness` autoverifica.** El closure comprueba al principio si el skill sigue activo (p. ej., mirando si hay un turno fijado de `activate_skill` con nombre `task_executor`). Si no, se auto-desregistra. Requiere que el closure sepa hacer esa comprobación, lo cual hoy no está soportado por la API de `SimpleScriptProjectedMemoryOperation`.

**c) `agent.memory` gana un `onDeactivate(closure)`.** API nueva en `MemoryModule`: la skill registra un callback de limpieza asociado a su ciclo de vida. El más limpio, pero es la opción con más superficie.

La opción (a) es la que menos toca la arquitectura y la que más se parece a lo que el resto del sistema hace con las skills (todo lo que hace una skill vive en `scripts/`).

---

## Detalles menores pero relevantes

**El texto del recordatorio podría ser más útil.** Ahora dice:

```
[PLAN EN EJECUCIÓN: "X"]
Directiva: Trabaja de forma secuencial una tarea a la vez. Al concluir la tarea actual, márcala como [x], consulta la siguiente con getCurrentTasks y procede.
```

No cambia con el progreso. Un LLM que ve esto 40 turnos seguidos lo ignora. Mejor:

```
[PLAN EN EJECUCIÓN: "X" — 3 de 10 tareas]
Tarea en curso: T3. Migrar el esquema de DB
Siguiente: T4. Actualizar los DAOs
Cuando cierres T3, llama a setTaskState("3", "x") y a getCurrentTasks.
```

La diferencia: el recordatorio contiene el estado actual, no solo una orden genérica. El LLM no necesita llamar a `getCurrentTasks` cada vez para saber qué hacer — solo para confirmar y actualizar. Y la marca "3 de 10" hace visible el progreso, lo que ayuda al LLM a no trabajar en círculos.

**Conflicto con `software_development`.** Si ambas skills están activas, `software_development` dice *"propón, espera aprobación, luego parchea"*. `task_executor` dice *"procede a la siguiente tarea sin esperar"*. El LLM tiene que reconciliarlas. La skill no menciona esta interacción. Una línea en el `SKILL.md`:

> Cuando una tarea implique cambios de código, aplica también las reglas de la skill `software_development`: relee antes de parchear, un parche por fichero, no encadenes si algo no cuadra. La aprobación del plan cubre la secuencia de tareas, no la implementación de cada una.

**El parser de `tasks.md`.** `contains("[ ]")` / `contains("[~]")` matchea en cualquier parte de la línea. Una descripción como `- [ ] 1. Implementar filtro "[x]" opcional` se clasificaría mal. Con el `setTaskState.groovy` propuesto esto deja de importar (el parser es explícito), pero si no se adopta, conviene restringir el `contains` al prefijo `"- ["`.

**`getCurrentTasks` no comprueba existencia del fichero** — ya cubierto en la revisión anterior. Si el usuario borra `tasks.md`, `forEachLine` lanza excepción cruda. Mejor un `if (!agent.fs.find(tasksPath)) return "ERROR: tasks.md no encontrado"` al principio.

**`find` en `setCurrentPlan` es O(n).** `agent.fs.find(tasksPath)` con un literal en vez de un glob: recorre todo el workspace hasta encontrar el match. Para workspaces grandes son segundos. `FsModule` no expone `exists`; un `FsModule.exists(rawPath)` (o usar `lines()` en try/catch) sería O(1) y más honesto semánticamente.

**Estado `[bloqueada]`.** No hay marcador para "tarea bloqueada". El LLM tiene que decidir entre dejarla `[~]` (bloquea la pipeline) o `[-]` (la descarta, aunque no esté descartada). Un `[!]` con la convención `- [!] 3. Migrar esquema // bloqueada: falta migración de T2` cubriría el caso. Si prefieres no añadir complejidad, al menos documenta qué debe hacer el LLM cuando una tarea se bloquea.

---

## Veredicto

El diseño es bueno y está bien alineado con la arquitectura del resto del sistema (memoria proyectada como canal de recordatorio, estado durable en ficheros, sandbox via `agent.fs`). Es la primera skill que usa `agent.memory` de verdad, y lo hace con el primitivo correcto.

Las dos correcciones que cambiarían la categoría de "convención" a "protocolo" son:

1. **`setTaskState.groovy`** para hacer las transiciones atómicas y verificadas. Sin esto, la skill depende de la disciplina del LLM, que es lo contrario de lo que una skill debería ser.
2. **Marcador durable del plan activo** + re-registro del `plan_awareness` desde `getCurrentPlanName`. Sin esto, el flujo se rompe silenciosamente en cada reinicio.

Y una tercera, más de higiene pero que se va a notar en cuanto se use la skill varias veces:

3. **Limpieza en `deactivate_skill`** para que `plan_awareness` no sobreviva a la desactivación del skill.

Los tres son cambios pequeños y localizados. Con ellos, la skill pasa de "una convención bien intencionada" a "un protocolo verificable", que es lo que el `SKILL.md` promete.
