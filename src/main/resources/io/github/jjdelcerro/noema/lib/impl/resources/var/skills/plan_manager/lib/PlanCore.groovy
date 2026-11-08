package lib

import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import java.nio.file.Path
import java.nio.file.Paths

class PlanCore {

  static final String SKILL_NAME    = "plan_manager"
  static final List<String> VALID_STATES = ["creating", "running", "stopped", "done"]
  static final String PLANS_SUBDIR      = ".noema-agent/plans"
  static final String RESOURCES_SUBDIR  = "var/skills/"+SKILL_NAME+"/resources"
  static final String AWARENESS_NAME    = "plan_awareness"
  static final int    AWARENESS_PRIORITY = 40
  static final List<String> TEMPLATES   = ["specs.md", "tasks.md", "issues.md"]
  static final String TASK_PATTERN =
    /^(\d+)\.\s+\[([ ~x-])\]\s+(.+?)(?:\s+[—\-]+\s+refs:\s+\[([^\]]*)\])?\s*$/

  private final def agent
  private final def context

  PlanCore(agent, context) {
    if (agent == null || context == null) {
      throw new IllegalArgumentException("PlanCore requiere agent y context")
    }
    this.agent = agent
    this.context = context
  }
    
  // ============ Paths ============
  Path plansRoot()            { agent.getPaths().getWorkspaceFolder().resolve(PLANS_SUBDIR).normalize() }
  Path planDir(String n)    { plansRoot().resolve(n).normalize() }
  Path statePath(String n)  { planDir(n).resolve("state") }
  Path tasksPath(String n)  { planDir(n).resolve("tasks.md") }
  Path specsPath(String n)  { planDir(n).resolve("specs.md") }
  Path issuesPath(String n) { planDir(n).resolve("issues.md") }
  Path resourcesRoot()        { agent.getPaths().getAgentFolder().resolve(RESOURCES_SUBDIR).normalize() }

  // ============ State I/O ============
  Map readState(String name) {
    String p = statePath(name).toString()
    if (!context.fs.exists(p)) return null
    String text = context.fs.lines(p).join('\n').trim()
    if (!text) return null
    try { return new JsonSlurper().parseText(text) as Map }
    catch (Exception e) { return null }
  }

  void writeState(String name, String state) {
    context.fs.write(statePath(name).toString(), JsonOutput.toJson([state: state]))
  }

  boolean planExists(String name) {
    return readState(name) != null
  }

  // ============ Task parsing ============

  List<Map> parseTasks(String name) {
    String p = tasksPath(name).toString()
    List<Map> result = []
    if (!context.fs.exists(p)) return result
    context.fs.forEachLine(p) { line, lineNum ->
      String t = line.trim()
      def m = (t =~ TASK_PATTERN)
      if (!m.matches()) return
      String refsRaw = m.group(4)
      List<String> refs = refsRaw
      ? refsRaw.split(',').collect { it.trim() }.findAll { it }
    : []
      result << [
        id:    m.group(1),
        line:  lineNum,
        state: m.group(2),
        desc:  m.group(3),
        refs:  refs
      ]
    }
    return result
  }  
  
  
  // ============ Lifecycle ============
  String createPlan(String name) {
    String err = validateName(name); if (err) return err
    if (planExists( name)) {
      Map s = readState(name)
      return "ERROR: el plan '${name}' ya existe (estado: ${s?.state ?: '?'}). Usa setPlanState('${name}', '<nuevo-estado>') para cambiarlo."
    }
    Path res = resourcesRoot()
    for (String t : TEMPLATES) {
      if (!context.fs.exists(res.resolve(t).toString())) {
        return "ERROR: falta la plantilla '${t}' en ${res}."
      }
    }
    for (String t : TEMPLATES) {
      String content = context.fs.lines(res.resolve(t).toString()).join('\n')
      content = content.replace('{{PLAN_NAME}}', name)
      context.fs.write(planDir(name).resolve(t).toString(), content)
    }
    writeState(name, "creating")
    context.state.currentPlan = name
    return """Plan '${name}' creado como BORRADOR (estado: creating).
- Especificación: ${specsPath(name)}
- Tareas:         ${tasksPath(name)}
- Incidencias:    ${issuesPath(name)}

Rellena specs.md en orden (Análisis → Diseño → Fases) y después tasks.md.
Cuando el usuario pida abordar el plan: setPlanState('${name}', 'running')."""
  }

  String setPlanState(String name, String target) {
    String err = validateName(name); if (err) return err
    if (!(target in VALID_STATES)) {
      return "ERROR: estado '${target}' inválido. Válidos: ${VALID_STATES.join(', ')}."
    }
    Map cur = readState(name)
    if (cur == null) {
      return "ERROR: el plan '${name}' no existe. Créalo con createPlan('${name}')."
    }
    String from = cur.state
    if (from == target) {
      return "ERROR: el plan '${name}' ya está en estado '${target}'."
    }

    switch (target) {
    case "creating":
      if (!(from in ["running", "stopped", "creating"]))
      return "ERROR: solo se puede volver a 'creating' desde 'stopped' o 'running'. Actual: '${from}'."
      break
    case "running":
      if (!(from in ["creating", "stopped", "running"]))
      return "ERROR: no se puede pasar a '${target}' desde '${from}'."
      if (parseTasks(name).isEmpty())
      return "ERROR: el plan '${name}' no tiene tareas con formato 'N. [ ] descripción' en tasks.md."
      // TODO (segunda vuelta): validar que no haya otro plan 'running'.
      break
    case "stopped":
      if (!(from in ["creating", "stopped", "running"]))
      return "ERROR: no se puede pasar a '${target}' desde '${from}'."
      break
    case "done":
      if (!(from in ["done", "stopped", "running"]))
      return "ERROR: no se puede pasar a '${target}' desde '${from}'."
      def tasks      = parseTasks(name)
      def pending    = tasks.findAll { it.state == " " }
      def inProgress = tasks.findAll { it.state == "~" }
      if (!pending.isEmpty() || !inProgress.isEmpty())
      return "ERROR: quedan ${pending.size()} pendientes y ${inProgress.size()} en curso. Termínalas o cancélalas antes de cerrar."
      break
    }

    writeState(name, target)
    switch (target) {
    case "creating":
      context.state.currentPlan = name
      registerAwareness()
      break
    case "running":
      context.state.currentPlan = name
      registerAwareness()
      break
    case "stopped":
    case "done":
      removeAwareness()
      if (context.state.currentPlan == name) context.state.currentPlan = null
      break
    }
    return "Plan '${name}' → ${target}."
  }

  // ============ Readers ============
  String getCurrentPlanName() {
    String name = context.state.currentPlan
    if (!name) return "No hay ningún plan cargado. Usa listPlans() para ver los disponibles."
    Map s = readState(name)
    if (s == null) {
      context.state.currentPlan = null
      return "ERROR: el plan cargado '${name}' no existe en disco. Limpiado de memoria."
    }
    return "Plan cargado: '${name}' (estado: ${s.state})."
  }

  String getCurrentTasks() {
    String name = context.state.currentPlan
    if (!name) return "ERROR: no hay ningún plan cargado. Usa listPlans() primero."
    Map s = readState(name)
    if (s == null) return "ERROR: el plan '${name}' no existe en disco."
    Path p = tasksPath(name)
    if (!context.fs.exists(p.toString())) return "ERROR: tasks.md no existe para '${name}'."

    def tasks      = parseTasks(name)
    def inProgress = tasks.findAll { it.state == "~" }
    def pending    = tasks.findAll { it.state == " " }
    int completed  = tasks.count { it.state == "x" }
    int aborted    = tasks.count { it.state == "-" }

    def sb = new StringBuilder()
    sb.append("Fichero: ${p}\n")
    switch (s.state) {
    case "creating": sb.append("BORRADOR — rellena tasks.md con tareas 'N. [ ] descripción'.\n"); break
    case "stopped":  sb.append("PLAN PARADO — setPlanState('${name}', 'running') para reanudar antes de editar.\n"); break
    case "done":     sb.append("PLAN CERRADO — solo lectura.\n"); break
      // running: sin mensaje
    }    
    if (inProgress.isEmpty()) {
      sb.append("En curso:  (ninguna)\n")
    } else {
      inProgress.each { sb.append("En curso:  #${it.id}. ${it.desc} ${it.refs}\n") }
      if (inProgress.size() > 1)
      sb.append("AVISO: ${inProgress.size()} tareas en curso. Solo se permite una.\n")
    }
    if (pending.isEmpty()) {
      sb.append("Siguiente: (ninguna)\n")
    } else {
      sb.append("Siguiente: #${pending[0].id}. ${pending[0].desc} ${pending[0].refs}\n")
    }
    sb.append("Progreso:  ${completed}/${tasks.size()} completadas, ${aborted} abortadas")
    return sb.toString()
  }

  String getCurrentPlanSpecifications() {
    String name = context.state.currentPlan
    if (!name) return "ERROR: no hay ningún plan cargado. Usa listPlans() primero."
    Path p = specsPath(name)
    return context.fs.exists(p.toString()) ? p.toString() : "ERROR: specs.md no existe para '${name}'."
  }

  String getCurrentIssues() {
    String name = context.state.currentPlan
    if (!name) return "ERROR: no hay ningún plan cargado. Usa listPlans() primero."
    Path p = issuesPath(name)
    return context.fs.exists(p.toString()) ? p.toString() : "ERROR: issues.md no existe para '${name}'."
  }

  String listPlans() {
    Path root = plansRoot()
    if (!context.fs.exists(root.toString())) return "No hay planes. Crea uno con createPlan('nombre')."

    def stateFiles = context.fs.find(root.toString(), "**/state").toList()
    if (stateFiles.isEmpty()) return "No hay planes en disco."

    String loaded = context.state.currentPlan
    def entries = []
    stateFiles.each { sf ->
      def sp = Paths.get(sf.toString())
      String planName = sp.getParent().getFileName().toString()
      Map s = readState(planName)
      if (s == null) return
      entries << [name: planName, state: s.state, demoted: false]
    }

    // Demota running no cargados → stopped. No toca done ni creating.
    entries.each { e ->
      if (e.state == "running" && e.name != loaded) {
        writeState(e.name, "stopped")
        e.state   = "stopped"
        e.demoted = true
      }
    }
    entries.sort { it.name }

    def sb = new StringBuilder()
    sb.append("Planes en disco (${entries.size()}):\n")
    entries.each { e ->
      String flag = (e.name == loaded) ? " [ACTIVO]" : ""
      String note = e.demoted ? " (detenido por listPlans al no estar cargado)" : ""
      sb.append("  - ${e.name}: ${e.state}${flag}${note}\n")
    }
    sb.append(loaded ? "\nPlan cargado en memoria: ${loaded}"
    : "\nNingún plan cargado en memoria.")
    return sb.toString()
  }

  String getSections(List<String> titles) {
    String name = context.state.currentPlan
    if (!name) return "ERROR: no hay ningún plan cargado. Usa listPlans() primero."
    Path p = specsPath(name)
    if (!context.fs.exists(p.toString())) return "ERROR: specs.md no existe para '${name}'."

    List<String> lines = []
    context.fs.lines(p.toString()).each { lines << it }
    if (lines.isEmpty()) return "ERROR: specs.md está vacío para '${name}'."

    def headLevel = { String s ->
      int k = 0
      while (k < s.length() && s.charAt(k) == '#') k++
      (k > 0 && k < s.length() && Character.isWhitespace(s.charAt(k))) ? k : 0
    }

    def sectionEnd = { int start ->
      int level = headLevel(lines[start])
      for (int i = start + 1; i < lines.size(); i++) {
        int h = headLevel(lines[i])
        if (h > 0 && h <= level) return i
      }
      lines.size()
    }

    def headings = []
    for (int i = 0; i < lines.size(); i++) {
      int level = headLevel(lines[i])
      if (level > 0) headings << [index: i, level: level, text: lines[i].trim()]
    }

    if (!titles || titles.isEmpty()) {
      def sb = new StringBuilder()
      sb.append("Fichero: ${p} (${lines.size()} líneas, ${headings.size()} encabezados)\n")
      headings.each { h ->
        int end = sectionEnd(h.index)
        def body = lines.subList(h.index, end)
        int chars = body.sum { it.length() + 1 } as int
        int tok = Math.round(chars / 4.0)
        sb.append(String.format("L%-4d %-60s %3d lin ~%4d tok\n",
            h.index + 1, h.text, end - h.index, tok))
      }
      return sb.toString()
    }

    def sb = new StringBuilder()
    sb.append("Fichero: ${p}\n")
    int found = 0
    titles.each { rawTitle ->
      String title = rawTitle.toString()
      def matches = headings.findAll { it.text.toLowerCase().contains(title.toLowerCase()) }
      sb.append("\n")
      if (matches.isEmpty()) {
        sb.append("### NO ENCONTRADA: ${title}\n")
        return
      }
      if (matches.size() > 1) {
        sb.append("### AMBIGUA: ${title} (${matches.size()} candidatas)\n")
        matches.each { sb.append("    L${it.index + 1}  ${it.text}\n") }
        return
      }
      def h = matches[0]
      int end = sectionEnd(h.index)
      def body = lines.subList(h.index, end)
      int chars = body.sum { it.length() + 1 } as int
      int tok = Math.round(chars / 4.0)
      sb.append("### ${h.text} (L${h.index + 1}-${end}, ${body.size()} líneas, ~${tok} tok)\n")
      body.each { sb.append(it).append("\n") }
      found++
    }

    if (found == 0) {
      return "ERROR: Ninguna sección encontrada para: ${titles.join(', ')}"
    }
    return sb.toString()
  }  
  
  String addTask(List<String> args) {
    String name = context.state.currentPlan
    if (!name) return "ERROR: no hay ningún plan cargado. Usa listPlans() primero."
    Map s = readState(name)
    if (s == null) return "ERROR: el plan '${name}' no existe en disco."
    if (s.state != "creating") {
      String hint = (s.state == "stopped") ? " Usa setPlanState('${name}', 'creating') para reabrirlo como borrador." : ""
      return "ERROR: solo se pueden añadir tareas en 'creating'. Plan en '${s.state}'.${hint}"
    }
    if (!args || args.isEmpty())
    return "ERROR: args = ['descripción', 'Título sección A', 'Título sección B', ...]"
    String desc = args[0].toString().trim()
    if (!desc) return "ERROR: la descripción de la tarea no puede estar vacía."
    List<String> refs = (args.size() > 1)
    ? args.subList(1, args.size()).collect { it.toString().trim() }.findAll { it }
  : []

    Path p = tasksPath(name)
    def existing = context.fs.exists(p.toString()) ? parseTasks(name) : []
    int nextId = existing.isEmpty() ? 1 : (existing.collect { it.id.toInteger() }.max() + 1)
    String newId = nextId.toString()
    String line  = formatTaskLine(newId, " ", desc, refs)

    List<String> lines = []
    if (context.fs.exists(p.toString())) {
      context.fs.lines(p.toString()).each { lines << it }
    } else {
      lines << "# Tareas: ${name}"
    }
    if (!lines.isEmpty() && lines.last().trim() != "") lines << ""
    lines << line
    context.fs.write(p.toString(), lines)

    // Aviso (no bloqueante) si las refs no aparecen aún en specs.md
    List<String> missing = []
    if (!refs.isEmpty()) {
      Path specsP = specsPath(name)
      if (context.fs.exists(specsP.toString())) {
        String specText = context.fs.lines(specsP.toString()).join('\n')
        refs.each { r -> if (!specText.contains(r)) missing << r }
      } else {
        missing = refs
      }
    }

    def sb = new StringBuilder()
    sb.append("Añadida tarea #${newId}:\n")
    sb.append("  ${line}\n")
    if (!missing.isEmpty()) {
      sb.append("AVISO: refs no encontradas en specs.md: ${missing.join(', ')}. ")
      sb.append("(si aún no has escrito esas secciones, ignora este aviso)")
    }
    return sb.toString()
  }

  String setTaskState(String taskId, String rawState) {
    String name = context.state.currentPlan
    if (!name) return "ERROR: no hay ningún plan cargado. Usa listPlans() primero."
    Map s = readState(name)
    if (s == null) return "ERROR: el plan '${name}' no existe en disco."
    if (s.state != "running")
    return "ERROR: solo se cambian estados de tareas en 'running'. Plan en '${s.state}'."
    if (!taskId?.trim()) return "ERROR: falta el ID de la tarea."

    // Normalizar el estado destino. Acepta " ", "" y "_" como "pendiente".
    String to = (rawState == null) ? "" : rawState.toString()
    if (to.trim().isEmpty() || to == "_") to = " "
    if (!(to in ["~", "x", "-", " ",]))
    return "ERROR: estado '${rawState}' inválido. Válidos: '~', 'x', '-', ' ' (espacio = pendiente)."

    Path p = tasksPath(name)
    if (!context.fs.exists(p.toString())) return "ERROR: tasks.md no existe para '${name}'."

    def tasks = parseTasks(name)
    String tid = taskId.trim()
    def task = tasks.find { it.id == tid }
    if (task == null) return "ERROR: no existe la tarea #${tid} en tasks.md."

    String from = task.state
    String err = validateTransition(from, to)
    if (err) return err

    if (to == "~") {
      def others = tasks.findAll { it.state == "~" && it.id != task.id }
      if (!others.isEmpty())
      return "ERROR: ya hay una tarea en curso (#${others[0].id}). Ciérrala antes de abrir otra."
    }

    // Reescribir solo la línea afectada, por número de línea (no por texto).
    List<String> lines = []
    context.fs.lines(p.toString()).each { lines << it }
    int idx = task.line - 1
    lines[idx] = formatTaskLine(task.id, to, task.desc, task.refs)
    context.fs.write(p.toString(), lines)

    def after = parseTasks(name)
    int completed = after.count { it.state == "x" }
    int aborted   = after.count { it.state == "-" }
    def inProgress = after.findAll { it.state == "~" }
    def pending    = after.findAll { it.state == " " }

    def sb = new StringBuilder()
    sb.append("Tarea #${task.id} → [${to}]:")
    sb.append(" ${lines[idx]}\n")
        
    if (inProgress.isEmpty()) {
      sb.append("En curso:  (ninguna)\n")
    } else {
      inProgress.each { sb.append("En curso:  #${it.id}. ${it.desc} ${it.refs}\n") }
    }
    if (pending.isEmpty()) {
      sb.append("Siguiente: (ninguna)\n")
    } else {
      sb.append("Siguiente: #${pending[0].id}. ${pending[0].desc} ${pending[0].refs}\n")
    }    
    
    sb.append("Progreso: ${completed}/${after.size()} completadas, ${aborted} abortadas.")
    if (after.every { it.state == "x" || it.state == "-" }) {
      sb.append("\nTodas las tareas cerradas. Puedes cerrar el plan con setPlanState('${name}', 'done').")
    }
    return sb.toString()
  }

  private String validateTransition(String from, String to) {
    if (from == to) return "ERROR: la tarea ya está en estado ${stateLabel(to)}."
    switch (from) {
    case " ":
      if (to == "~" || to == "-") return null
      if (to == "x") return "ERROR: no se puede cerrar una tarea sin haberla abierto. Pásala primero por '~'."
      break
    case "~":
      return null
    case "x":
      return "ERROR: una tarea cerrada no se reabre. Si hay que rehacer, vuelve a 'creating' y añade una nueva."
    case "-":
      return "ERROR: una tarea abortada es terminal."
    }
    return "ERROR: transición no permitida de ${stateLabel(from)} a ${stateLabel(to)}."
  }

  private String stateLabel(String s) {
    switch (s) {
    case " ": return "[ ] pendiente"
    case "~": return "[~] en curso"
    case "x": return "[x] completada"
    case "-": return "[-] abortada"
    }
    return "?"
  }  
  
  // ============ Helpers ============
  String validateName(String name) {
    if (!name || !name.trim()) return "ERROR: indica el nombre del plan."
    if (name == "." || name == ".." || name.contains("/") || name.contains("\\"))
    return "ERROR: nombre inválido (sin separadores de ruta ni '..')."
    return null
  }

  String formatTaskLine(String id, String state, String desc, List<String> refs) {
    String d = (desc ?: "").trim()
    String r = " — refs: [${(refs ?: []).join(', ')}]"
    return "${id}. [${state}] ${d}${r}"
  }

  void registerAwareness() {
    removeAwareness()
    context.memory.registerProjectedOperation(AWARENESS_NAME, AWARENESS_PRIORITY) { mem, notifications ->
      try {
        String name = context.state.currentPlan
        if (!name) return
        Map s = readState(name)
        if (s != null && s.state == "running") 
          return notifications.add("""[PLAN "${name}" EN EJECUCIÓN]
Cuando termines la tarea en curso, actualiza tasks.md y pasa a la siguiente.""")
        if (s == null || s.state == "creating") 
          return notifications.add("""[PLAN "${name}" EN CONSTRUCCION]
Plan en construcción apolla al usuario en la definición de las specificaciones y tareas del plan.""")
        if (s != null ) 
          return notifications.add("""[PLAN "${name}": ${s.state}]""")
      } catch (Exception ignored) {
      }
    }
  }

  void removeAwareness() {
    context.memory.removeOperation(AWARENESS_NAME)
  }
}
