# Especificación Técnica y de Diseño: Sistema de Planes y Tareas en Noema

* **Documento:** Propuesta de Arquitectura para Gestión Deliberativa de Objetivos
* **Módulo:** `io.github.jjdelcerro.noema.lib.services.tasks` / `impl.services.tasks`
* **Ámbito:** Kernel, Persistencia en `service.mv.db`, Pipeline de Memoria Proyectada y Scripting

---

## 1. Visión y Fundamentos Cognitivos

### 1.1. La naturaleza de Noema frente a los agentes de ciclo corto
La mayoría de los agentes autónomos contemporáneos operan bajo el paradigma de **ciclo corto**: nacen para resolver un problema puntual (ej. un *issue* de código), ejecutan un bucle ciego de herramientas hasta vaciar una cola de tareas y se destruyen.

Noema no es un agente de ciclo corto. Es un **compañero de laboratorio de sesión persistente unificada**:
* Sostiene diálogos e indagaciones prolongadas que alternan entre diseño técnico, depuración, debate epistemológico o simple descanso.
* Convive con interrupciones del usuario, cambios de foco imprevistos y reinicios de la máquina virtual a lo largo de días o semanas.

### 1.2. El problema del secuestro atencional (*Goal Fixation*)
Inyectar continuamente tareas pendientes en el contexto del LLM provoca una patología común: el **secuestro de la atención**. Si el usuario regresa tras una pausa preguntando por una cuestión conceptual o un problema urgente, un agente que recibe recordatorios constantes de tareas pendientes responderá con prisa o intentará reconducir la conversación hacia las tareas incompletas, ignorando la intención real del usuario.

### 1.3. La metáfora del cuaderno de trabajo: Planes y Tareas
En la cognición humana, una lista de tareas no es una señal que parpadea incesantemente ante los ojos; es **un cuaderno de trabajo sobre la mesa**:
* **El Plan (`Plan`):** Representa el contexto de trabajo o iniciativa global (ej. *"Migración a Java 25"*, *"Auditoría de seguridad"*). Contiene la lista ordenada de pasos.
* **La Tarea (`Task`):** Es la unidad atómica de ejecución dentro de ese plan.
* **Activación y Suspensión:** Cuando el usuario y el agente trabajan en una iniciativa, el plan está **activo** (el cuaderno está abierto). Cuando el usuario cambia de tema, desconecta o interrumpe la sesión, el plan se **suspende** (el cuaderno se cierra y se guarda en un cajón).

### 1.4. Invariante cardinal: Un único plan activo por subcanal
Para evitar dispersión cognitiva, sobrecoste de tokens e interferencias en el razonamiento del modelo:
$$\text{MaxPlanesActivos}(\text{subchannel}) \le 1$$
* En un `subchannel` determinado solo puede existir **un plan con estado `ACTIVE` a la vez**.
* Si se inicia o reanuda otro plan, el anterior pasa automáticamente a `SUSPENDED`.

---

## 2. Modelo de Datos y Persistencia (H2)

Toda la estructura de planes y tareas reside en la base de datos de servicios del agente (`service.mv.db`, obtenida vía `agent.getServicesDatabase()`), completamente segregada de la base de datos histórica (`episodic_memory.mv.db`).

### 2.1. Esquema DDL en H2

```sql
-- Catálogo de iniciativas / planes
CREATE TABLE IF NOT EXISTS plans (
    id INT PRIMARY KEY,
    subchannel VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description CLOB,
    status VARCHAR(20) NOT NULL, -- ACTIVE, SUSPENDED, COMPLETED, CANCELED
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    closed TIMESTAMP
);

-- Pasos y unidades de trabajo atómicas vinculadas a un plan
CREATE TABLE IF NOT EXISTS tasks (
    id INT PRIMARY KEY,
    plan_id INT NOT NULL,
    parent_id INT,
    position INT DEFAULT 0,
    status VARCHAR(20) NOT NULL, -- PENDING, IN_PROGRESS, BLOCKED, DONE, FAILED, CANCELED
    title VARCHAR(255) NOT NULL,
    description CLOB,
    subagent_id INT,
    result CLOB,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    closed TIMESTAMP,
    FOREIGN KEY (plan_id) REFERENCES plans(id)
);

CREATE INDEX IF NOT EXISTS idx_plans_subchannel_status ON plans(subchannel, status);
CREATE INDEX IF NOT EXISTS idx_tasks_plan_position ON tasks(plan_id, position);
```

### 2.2. Decisiones de diseño en los campos

* **Estados del Plan:**
  * `ACTIVE`: El plan en curso. Sus tareas abiertas se proyectan en la memoria.
  * `SUSPENDED`: Pausado. Silencio absoluto en el contexto conversacional.
  * `COMPLETED`: Todos los objetivos se cumplieron.
  * `CANCELED`: La iniciativa se descartó.
* **Estados de la Tarea:**
  * Vivas / Abiertas: `PENDING`, `IN_PROGRESS`, `BLOCKED`.
  * Terminales / Cerradas: `DONE`, `FAILED`, `CANCELED`.
* **El campo `closed` (`TIMESTAMP`, *nullable*):**
  * Presente tanto en `plans` como en `tasks`.
  * Si es `NULL`, el registro está vivo. Si tiene valor temporal, está concluido.
  * Evita consultas con cláusulas complejas (`NOT IN ('DONE', ...)`): basta con comprobar `closed IS NULL`.
* **El campo `position` (`INT`) frente a listas enlazadas en SQL:**
  * Las listas enlazadas en base de datos (`previous_task_id`) son frágiles ante borrados y exigen consultas recursivas.
  * Se mantiene un entero `position` reindexado en memoria por Java (apenas 10-20 filas por plan, coste menor a 1 ms).
* **Omisión meditada de `origin_turn_id`:**
  * El LLM no conoce ni debe conocer los IDs físicos de turno en H2.
  * La llamada a la herramienta (`task_create`) ya queda asentada automáticamente en `episodicmemory` como un `Turn` de tipo `tool_execution` con su propia marca temporal y parámetros. La trazabilidad ya está resuelta por la arquitectura base sin generar dependencias cruzadas.

---

## 3. Arquitectura del Servicio (`TaskService`)

El servicio implementa el contrato estándar de servicios desacoplados de Noema (`AbstractAgentService`), constituyendo la única fuente de verdad para la lógica deliberativa.

### 3.1. Contrato: `TaskService.java`
Ubicación: `io.github.jjdelcerro.noema.lib.services.tasks.TaskService`

```java
package io.github.jjdelcerro.noema.lib.services.tasks;

import io.github.jjdelcerro.noema.lib.AgentService;
import java.util.List;

public interface TaskService extends AgentService {
  String NAME = "Tasks";

  // --- Ciclo de Vida del Plan ---
  Plan createPlan(String subchannel, String name, String description);
  Plan resumePlan(String subchannel, String nameOrId);
  Plan suspendActivePlan(String subchannel);
  Plan getActivePlan(String subchannel);
  List<Plan> getPlans(String subchannel, boolean includeClosed);
  Plan closePlan(int planId, PlanStatus terminalStatus);

  // --- Ciclo de Vida de Tareas ---
  Task createTask(int planId, String title, String description, Integer parentId, Integer afterTaskId);
  Task updateTask(int taskId, TaskStatus status, String result, String title, String description, Integer subagentId);
  Task getTask(int taskId);
  List<Task> getActiveTasks(int planId);
  List<Task> getAllTasks(int planId, boolean includeClosed);
}
```

### 3.2. Responsabilidades de `TaskServiceImpl`
1. **Gobernanza del plan activo único:** Al invocar `createPlan` o `resumePlan`, cualquier plan previo con estado `ACTIVE` en ese `subchannel` se actualiza de forma atómica a `SUSPENDED`.
2. **Asignación atómica de posiciones:** Cuando una tarea se crea con `afterTaskId = null`, se coloca en `max(position) + 1`. Si se especifica `afterTaskId`, desplaza las posiciones posteriores en una sola transacción.
3. **Gestión determinista de marcas de cierre:** Si una tarea o plan transiciona a `DONE`, `FAILED` o `CANCELED`, el servicio asigna `closed = LocalDateTime.now()`. Si se reabre a `PENDING` o `IN_PROGRESS`, asigna `closed = null`.

---

## 4. Catálogo de Herramientas para el Modelo (`AgentTool`)

Las herramientas se dividen en dos niveles cognitivos: **Estratégicas** (Planes) y **Tácticas** (Tareas).

### 4.1. Herramientas de Planificación Estratégica

#### `plan_create`
* **Modo:** `MODE_WRITE` | **Tipo:** `TYPE_OPERATIONAL`
* **Descripción para el LLM:** *"Crea un nuevo plan de trabajo y lo establece como el plan activo del subcanal. Si existía otro plan activo, se suspenderá automáticamente."*
* **Parámetros:**
  * `name` (*string*, requerido): Nombre del plan en kebab-case (ej. `"migracion-db"`).
  * `description` (*string*, opcional): Alcance y objetivos globales.

#### `plan_suspend`
* **Modo:** `MODE_WRITE` | **Tipo:** `TYPE_OPERATIONAL`
* **Descripción para el LLM:** *"Pausa el plan de trabajo actual. Úsala cuando el usuario cambie de tema, decida posponer el trabajo o solicite atender otra tarea distinta sin perder el progreso realizado."*
* **Parámetros:** Ninguno.

#### `plan_resume`
* **Modo:** `MODE_WRITE` | **Tipo:** `TYPE_OPERATIONAL`
* **Descripción para el LLM:** *"Reanuda un plan de trabajo previamente suspendido y lo convierte en el plan activo. Sus tareas pendientes volverán a proyectarse en tu contexto."*
* **Parámetros:**
  * `name` (*string*, requerido): Nombre o ID del plan a reactivar.

#### `plan_list`
* **Modo:** `MODE_READ` | **Tipo:** `TYPE_OPERATIONAL`
* **Descripción para el LLM:** *"Lista los planes de trabajo existentes en este canal y su estado (ACTIVE, SUSPENDED, COMPLETED, CANCELED). Úsala para recordar qué proyectos quedaron a medias."*
* **Parámetros:**
  * `include_closed` (*boolean*, opcional, default `false`).

---

### 4.2. Herramientas Tácticas de Tareas

> **Principio de Mínima Carga Cognitiva:** Dado el invariante de un único plan activo, las herramientas de tareas **no requieren que el modelo especifique `plan_id`**. El sistema vincula la operación al plan activo del subcanal de forma automática.

#### `task_create`
* **Modo:** `MODE_WRITE` | **Tipo:** `TYPE_OPERATIONAL`
* **Descripción para el LLM:** *"Añade una nueva tarea al plan activo. Por defecto se añade al final de la secuencia; usa after_task_id si necesitas insertarla entre tareas existentes."*
* **Parámetros:**
  * `title` (*string*, requerido): Objetivo directo y conciso (1 línea).
  * `description` (*string*, opcional): Detalles técnicos o criterios de aceptación.
  * `after_task_id` (*integer*, opcional): ID de la tarea tras la cual debe ubicarse. Si se omite, se encola al final del plan activo.
  * `parent_id` (*integer*, opcional): ID de la tarea padre si es un desglose.

> **Resolución de llamadas concurrentes en el mismo turno:** Cuando el modelo traza un plan inicial, emite múltiples llamadas paralelas a `task_create`. Al ejecutarse secuencialmente en el bucle de `ReasoningServiceImpl`, la primera se asigna como última, la segunda se encola tras la recién creada y la tercera tras la anterior, ordenando el plan limpiamente sin exigir al LLM conocer los IDs con antelación.

> **Nota para revisar**. Creo que habria que incluir en la creacion/modificacion de tareas la posibilidad de indicar el plan, ya que sin haber ningun plan activo podriamos estar trabajando en la elaboracion de un plan de trabajo, y solo al terminar con el lo activariamos.


#### `task_update`
* **Modo:** `MODE_WRITE` | **Tipo:** `TYPE_OPERATIONAL`
* **Descripción para el LLM:** *"Actualiza el estado, progreso o resultado de una tarea del plan activo."*
* **Parámetros:**
  * `id` (*integer*, requerido): ID de la tarea.
  * `status` (*string*, opcional): `"PENDING"`, `"IN_PROGRESS"`, `"BLOCKED"`, `"DONE"`, `"FAILED"`, `"CANCELED"`.
  * `result` (*string*, opcional): Breve resumen del resultado obtenido o motivo del descarte/fallo.
  * `subagent_id` (*integer*, opcional): ID del subagente si la tarea fue delegada en segundo plano.
  * `title` / `description` (*string*, opcional): Ajustes en el enunciado.

#### `task_list`
* **Modo:** `MODE_READ` | **Tipo:** `TYPE_OPERATIONAL`
* **Descripción para el LLM:** *"Consulta tareas en base de datos. Úsala para revisar tareas cerradas, examinar descripciones completas o auditar otros planes."*
* **Parámetros:**
  * `task_id` (*integer*, opcional): Obtiene el desglose exhaustivo de una sola tarea.
  * `include_closed` (*boolean*, opcional, default `false`).
  * `plan_name` (*string*, opcional): Para auditar planes no activos.

---

## 5. Integración con el Pipeline de Memoria Proyectada

La gestión atencional de los planes se implementa mediante una operación dedicada en el pipeline: `ActivePlanOperation`.

### 5.1. Mecánica de `ActivePlanOperation`
* **Prioridad:** `15` (se ejecuta inmediatamente después de `PinnedTurnsOperation` [5] y `TrimmingOperation` [10], antes de las percepciones secundarias).
* **Algoritmo de ejecución en cada turno:**
  ```java
  public void process(ProjectedMemory memory, List<ChatMessage> projectedMessages, List<String> notifications) {
      TaskService taskService = (TaskService) memory.getAgent().getService(TaskService.NAME);
      if (taskService == null || !taskService.isRunning()) return;

      String subchannel = memory.getSubchannel();
      Plan activePlan = taskService.getActivePlan(subchannel);

      // CASO A: Plan suspendido o inexistente -> CERO consumo de contexto
      if (activePlan == null) {
          return;
      }

      // CASO B: Hay un plan activo -> Inyección concisa del foco
      List<Task> openTasks = taskService.getActiveTasks(activePlan.getId());
      if (openTasks.isEmpty()) {
          notifications.add(String.format(
              "[PLAN ACTIVO: '%s' - Todas las tareas completadas. Concluye con plan_suspend o añade nuevos pasos]", 
              activePlan.getName()));
          return;
      }

      StringBuilder sb = new StringBuilder();
      sb.append("## Plan de Trabajo Activo: ").append(activePlan.getName()).append("\n");
      for (Task t : openTasks) {
          sb.append("- [").append(t.getStatus()).append("] #").append(t.getId())
            .append(": ").append(t.getTitle()).append("\n");
      }
      sb.append("(Usa 'plan_suspend' si el usuario cambia de tema o se interrumpe la sesión)");
      
      notifications.add(sb.toString().trim());
  }
  ```

### 5.2. Preservación ante la desconexión y pausas temporales
* Si el usuario apaga el sistema y vuelve al día siguiente, `TemporalPerceptionOperation` detecta el paso del tiempo.
* Si el plan se dejó activo pero la conversación arranca con un simple saludo del usuario, el agente puede responder con naturalidad sin sentirse obligado a ejecutar el plan de inmediato, o sugerir brevemente su suspensión si el usuario aborda un asunto completamente ajeno.

---

## 6. Fachada de Scripting Groovy (`TasksModule`)

El sistema expone las capacidades del servicio a los scripts ejecutados mediante `execute_script` a través del módulo `agent.tasks`.

### 6.1. Métodos expuestos en `agent.tasks`

```groovy
// Inspección y estado
def plan = agent.tasks.activePlan()             // Devuelve el Plan activo o null
def planes = agent.tasks.listPlans()            // List<Plan>
def tareas = agent.tasks.active()               // List<Task> del plan en curso

// Control de flujo de planes
agent.tasks.startPlan("refactor-red", "Refactorizar capa HTTP")
agent.tasks.suspendPlan()
agent.tasks.resumePlan("migracion-db")

// Manipulación masiva de tareas (automatizaciones)
agent.tasks.create("Compilar módulo", [description: "mvn clean compile", after: 10])
agent.tasks.update(12, [status: "DONE", result: "Compilación limpia"])
```

### 6.2. Caso de uso paradigmático
Un script de auditoría que analiza 50 archivos fuente puede desglosar tareas pendientes en bucle sin consumir turnos del LLM ni saturar la conversación con llamadas a herramientas:

```groovy
agent.tasks.startPlan("correccion-linter", "Subsanar advertencias de estilo")

agent.fs.grep('@Deprecated', 'src/').each { match ->
    agent.tasks.create("Revisar deprecación en ${match.file}:${match.line}")
}
```

---

## 7. Integración con Subagentes y Sensores

La arquitectura conecta de manera natural la delegación de tareas con los trabajadores aislados (`SubagentImpl`):

1. **Vinculación:** Cuando el agente lanza un subagente para una tarea compleja, asocia su identificador:
   ```json
   task_update(id: 15, status: "IN_PROGRESS", subagent_id: 3)
   ```
2. **Cierre determinista:** Cuando el hilo del subagente concluye su ejecución, emite un evento `SYSTEMNOTIFICATION` al `SensorsService` del subcanal de origen.
3. El servicio de tareas intercepta la resolución del subagente y actualiza la tarea vinculada:
   * Si el subagente finalizó con éxito: `status = DONE`, `result = extractLastResponse()`.
   * Si el subagente falló por timeout o excepción: `status = FAILED`, `result = getLastError()`.
   * En ambos casos, sella `closed = NOW()` sin que el modelo principal tenga que tramitar manualmente el cierre.

---

## 8. Resumen de Archivos y Modificaciones en el Proyecto

| Módulo / Capa | Archivo a Crear o Modificar | Responsabilidad |
| :--- | :--- | :--- |
| **Contratos Núcleo** | `io.github.jjdelcerro.noema.lib.services.tasks.TaskService` | Interfaz pública del servicio de planes y tareas. |
| **Contratos Núcleo** | `...lib.services.tasks.Plan` y `Task` | Modelos inmutables de plan y tarea. |
| **Implementación** | `...impl.services.tasks.TaskServiceImpl` | Lógica de persistencia SQL en H2, reordenación y cierre. |
| **Implementación** | `...impl.services.tasks.TaskServiceFactory` | Factoría registrada en `AgentManagerImpl`. |
| **Herramientas** | `...impl.services.tasks.tools.*` | Clases `PlanCreateTool`, `PlanSuspendTool`, `PlanResumeTool`, `PlanListTool`, `TaskCreateTool`, `TaskUpdateTool`, `TaskListTool`. |
| **Memoria** | `...impl.memory.projected.operations.ActivePlanOperation` | Operación en el pipeline que inyecta el plan activo en el contexto. |
| **Scripting** | `...impl.scripting.modules.TasksModule` | Fachada Groovy accesible bajo `agent.tasks`. |
| **Configuración** | `available_tools.properties` | Declaración de los identificadores de herramientas expuestas al modelo. |
