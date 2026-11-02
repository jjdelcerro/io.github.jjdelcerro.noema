
La idea y el diseño de **`plan_manager` encajan perfectamente en el concepto de *Skill***, y de hecho es uno de los mejores ejemplos de lo que un skill debe ser en esta arquitectura:

1. **Protocolo procedimental (SOP):** Define cómo comportarse ante tareas complejas multi-turno sin saturar el prompt del sistema general.
2. **Scripts auxiliares deterministas:** Descarga al LLM de tareas en las que suele fallar (mantener contadores, parsear expresiones regulares, gestionar ficheros de estado JSON).
3. **Economía de atención:** La lectura quirúrgica con `getSections` es sobresaliente y respeta al milímetro el principio de *Gestión de la Atención* de tu filosofía arquitectónica (evitar leer `specs.md` entero de 20 KB en cada turno).
4. **Aprovechamiento del motor de scripting:** Que un script de skill pueda registrar una regla dinámica en `ProjectedMemory` (`plan_awareness`) demuestra la potencia de desacoplar módulos en Groovy sin tener que picar servicios enteros en Java.

Sin embargo, tras revisar a fondo los fuentes, **hay un par de bloqueos técnicos críticos (bugs)** y varios detalles de diseño que debes ajustar para que funcione en la práctica:

---

### 1. Bloqueos críticos (Bugs que impedirán que funcione)

#### A. `AgentAccessControl` bloquea la lectura de las plantillas (`createPlan`)
En `PlanCore.groovy`:
```groovy
static final String PLANS_SUBDIR      = ".noema-agent/plans"
static final String RESOURCES_SUBDIR  = "var/skills/"+SKILL_NAME+"/resources"
...
static Path resourcesRoot(def agent)  { agent.getPaths().getAgentFolder().resolve(RESOURCES_SUBDIR).normalize() }
```
`resourcesRoot` resuelve a:  
`<workspace>/.noema-agent/var/skills/plan_manager/resources`

Cuando `createPlan` hace:
```groovy
if (!agent.fs.exists(res.resolve(t).toString())) ...
agent.fs.lines(res.resolve(t).toString()).join('\n')
```
Pasa obligatoriamente por `FsModule` -> `AgentAccessControlImpl.resolvePath`. Y en `AgentAccessControlImpl.java` tienes esta regla inmutable:
```java
if (targetPathString.contains("/." + AgentPaths.AGENT_FOLDER_NAME + "/var/")
        || targetPathString.endsWith("/." + AgentPaths.AGENT_FOLDER_NAME + "/var")) {
    throw new SecurityException("ACCESO DENEGADO: La carpeta de control interno del agente no es accesible.");
}
```
**Resultado:** `createPlan` fallará siempre con `SecurityException` o dirá que no encuentra las plantillas porque `agent.fs` tiene terminantemente prohibido leer bajo `/.noema-agent/var/`.  
*(Nota: `.noema-agent/plans` sí funciona porque no cuelga de `var/`).*

* **Solución recomendada:** En el control de acceso, o bien abres una excepción de solo-lectura para `var/skills/**/resources/`, o bien en el script utilizas el acceso a recursos del classpath / `Skill.resolveResource` en lugar de pasarlo por `agent.fs`.

---

#### B. `SkillsServiceImpl.start()` no instala `plan_manager`
En `SkillsServiceImpl.java` (línea ~215):
```java
@Override
public void start() {
  String[] resources = new String[]{
      "var/skills/software_development/SKILL.md",
      "var/skills/task_executor/SKILL.md",
      "var/skills/task_executor/scripts/setCurrentPlan.groovy",
      ...
  };
  for (String resPath : resources) {
    this.agent.installResource(resPath);
  }
}
```
Tienes registrado `task_executor` (el prototipo antiguo). Si abres un workspace nuevo o limpio, `plan_manager` **nunca se desplegará en disco**, por lo que `list_skills` ni siquiera lo verá. Tienes que actualizar esa lista con la ruta de `plan_manager`.

---

### 2. Puntos de fricción con el modelo mental de LLM

#### A. Edición manual de `tasks.md` vs. Scripts (`addTask` y `setTaskState`)
En `todo.md` tienes muy bien razonada la necesidad de `addTask` y `setTaskState`. **Debes implementarlos cuanto antes**.  
Pedirle a un LLM en `running` que haga `file_search_and_replace` o `file_patch` sobre `tasks.md` para cambiar un `[ ]` por `[~]` es una fuente constante de errores:
* Si el archivo usa espacios o tabuladores raros, el reemplazo falla.
* El LLM a veces reordena o borra tareas sin querer.
* Tu regex `TASK_PATTERN = /^(\d+)\.\s+\[([ ~x-])\]\s+(.+?)(?:\s+[—\-]+\s+refs:\s+\[([^\]]*)\])?\s*$/` es estricta (guion largo `—` o `-`, formato exacto de refs). Un modelo comercial a veces meterá `refs: Título` sin corchetes o cambiará el formato del guion, y `parseTasks` lo ignorará silenciosamente dejando al plan con 0 tareas.

Con `run_skill_script(name: "plan_manager", script: "setTaskState", args: [3, "~"])` blindas la mutación por completo.

#### B. Coherencia en la representación del estado pendiente (`""` vs `" "`)
En `PlanCore.groovy`:
```groovy
result << [
    id:    m.group(1),
    line:  lineNum,
    state: m.group(2).trim(), // <-- Si es " ", trim() lo convierte en ""
    ...
]
```
Por tanto, en `PlanCore` la tarea pendiente internamente tiene `state == ""`. Sin embargo, en `todo.md` y en la UI se habla de pasar `" "` (un espacio). Ten cuidado con esta discrepancia: estandariza que el estado sea un identificador claro en la API interna de los scripts (o si usas caracteres, asegúrate de que `trim()` no convierta el espacio en cadena vacía donde luego compares con `" "`).

---

### 3. Aspectos conceptuales: ¿Es un Skill o algo más?

#### La frontera entre Skill y Subsistema
En la literatura habitual (Claude Skills, etc.), un skill suele ser un documento Markdown pasivo con directivas y comandos de bash.
Tú has ido un paso más allá: **`plan_manager` es un auténtico subsistema con máquina de estados, persistencia JSON y ganchos en la memoria proyectada**.

¿Rompe eso el concepto de Skill? **No, lo ensancha para bien.**  
La ventaja de haberlo hecho como skill y no como un `PlanService.java` compilado en el núcleo es:
1. **Zero-overhead:** Mientras no se use, no ocupa memoria ni registros en el agente.
2. **Evolución ágil:** Puedes alterar la máquina de estados, añadir transiciones o tocar plantillas directamente en caliente editando scripts en Groovy sin recompilar Maven.
3. **Contexto delimitado:** El LLM solo ve los comandos de plan cuando activa la skill; no tiene que lidiar con 10 herramientas más en su catálogo general si solo está respondiendo dudas de soporte o explorando código.

#### Convivencia de avisos (Doble *Pinning*)
Fíjate en lo que ocurre cuando el plan entra en `running`:
1. `activate_skill("plan_manager")` ancla la skill en `PinnedTurnsOperationImpl` (emite cada 5 turnos: `[SKILL ACTIVO: plan_manager]...`).
2. `setPlanState("running")` registra `plan_awareness` en `ProjectedMemory` (emite en **cada turno**: `[PLAN EN EJECUCIÓN: "..."]`).

Esta convivencia es **correcta**: uno recuerda el *rol/herramientas* y el otro recuerda la *tarea activa en curso*. Sin embargo, asegúrate de que el mensaje de `plan_awareness` sea siempre ultra-conciso (1-2 líneas) para que no canibalice la ventana de contexto.

---

### Resumen de acciones recomendadas

1. **En `SkillsServiceImpl.java`:** Sustituye las referencias a `task_executor` por `plan_manager` (SKILL.md, lib, scripts y resources) para que se instalen en el boot.
2. **En `AgentAccessControlImpl.java` o `PlanCore.groovy`:** Resuelve el acceso a `resourcesRoot`. Si no quieres abrir `/.noema-agent/var/` en el sandbox general, añade una excepción específica para lectura de recursos de skills o lee los templates directamente desde el classpath con el ClassLoader.
3. **Pasa a código lo que tienes en `todo.md`:** Añade `addTask.groovy` y `setTaskState.groovy`. Es el paso que le falta para ser 100% robusto frente a alucinaciones de formato del LLM.
