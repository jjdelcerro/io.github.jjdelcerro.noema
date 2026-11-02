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


  // ============ Paths ============
  static Path plansRoot(def agent)            { agent.getPaths().getWorkspaceFolder().resolve(PLANS_SUBDIR).normalize() }
  static Path planDir(def agent, String n)    { plansRoot(agent).resolve(n).normalize() }
  static Path statePath(def agent, String n)  { planDir(agent, n).resolve("state") }
  static Path tasksPath(def agent, String n)  { planDir(agent, n).resolve("tasks.md") }
  static Path specsPath(def agent, String n)  { planDir(agent, n).resolve("specs.md") }
  static Path issuesPath(def agent, String n) { planDir(agent, n).resolve("issues.md") }
  static Path resourcesRoot(def agent)        { agent.getPaths().getAgentFolder().resolve(RESOURCES_SUBDIR).normalize() }

  // ============ State I/O ============
  static Map readState(def agent, String name) {
    String p = statePath(agent, name).toString()
    if (!agent.fs.exists(p)) return null
    String text = agent.fs.lines(p).join('\n').trim()
    if (!text) return null
    try { return new JsonSlurper().parseText(text) as Map }
    catch (Exception e) { return null }
  }

  static void writeState(def agent, String name, String state) {
    agent.fs.write(statePath(agent, name).toString(), JsonOutput.toJson([state: state]))
  }

  static boolean planExists(def agent, String name) {
    return readState(agent, name) != null
  }

  // ============ Task parsing ============

  static List<Map> parseTasks(def agent, String name) {
    String p = tasksPath(agent, name).toString()
    List<Map> result = []
    if (!agent.fs.exists(p)) return result
    agent.fs.forEachLine(p) { line, lineNum ->
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
        state: m.group(2).trim(),
        desc:  m.group(3),
        refs:  refs
      ]
    }
    return result
  }  
  
  
  // ============ Lifecycle ============
  static String createPlan(def agent, String name) {
    String err = validateName(name); if (err) return err
    if (planExists(agent, name)) {
      Map s = readState(agent, name)
      return "ERROR: el plan '${name}' ya existe (estado: ${s?.state ?: '?'}). Usa setPlanState('${name}', '<nuevo-estado>') para cambiarlo."
    }
    Path res = resourcesRoot(agent)
    for (String t : TEMPLATES) {
      if (!agent.fs.exists(res.resolve(t).toString())) {
        return "ERROR: falta la plantilla '${t}' en ${res}."
      }
    }
    for (String t : TEMPLATES) {
      String content = agent.fs.lines(res.resolve(t).toString()).join('\n')
      content = content.replace('{{PLAN_NAME}}', name)
      agent.fs.write(planDir(agent, name).resolve(t).toString(), content)
    }
    writeState(agent, name, "creating")
    agent.state.currentPlan = name
    return """Plan '${name}' creado como BORRADOR (estado: creating).
- Especificación: ${specsPath(agent, name)}
- Tareas:         ${tasksPath(agent, name)}
- Incidencias:    ${issuesPath(agent, name)}

Rellena specs.md en orden (Análisis → Diseño → Fases) y después tasks.md.
Cuando el usuario pida abordar el plan: setPlanState('${name}', 'running')."""
  }

  static String setPlanState(def agent, String name, String target) {
    String err = validateName(name); if (err) return err
    if (!(target in VALID_STATES)) {
      return "ERROR: estado '${target}' inválido. Válidos: ${VALID_STATES.join(', ')}."
    }
    Map cur = readState(agent, name)
    if (cur == null) {
      return "ERROR: el plan '${name}' no existe. Créalo con createPlan('${name}')."
    }
    String from = cur.state
    if (from == target) {
      return "ERROR: el plan '${name}' ya está en estado '${target}'."
    }

    switch (target) {
    case "creating":
      if (from != "stopped")
      return "ERROR: solo se puede volver a 'creating' desde 'stopped'. Actual: '${from}'."
      break
    case "running":
      if (!(from in ["creating", "stopped"]))
      return "ERROR: no se puede pasar a 'running' desde '${from}'."
      if (parseTasks(agent, name).isEmpty())
      return "ERROR: el plan '${name}' no tiene tareas con formato 'N. [ ] descripción' en tasks.md."
      // TODO (segunda vuelta): validar que no haya otro plan 'running'.
      break
    case "stopped":
      if (from != "running")
      return "ERROR: solo se puede parar un plan en 'running'. Actual: '${from}'."
      break
    case "done":
      if (from != "running")
      return "ERROR: solo se puede cerrar un plan en 'running'. Actual: '${from}'."
      def tasks      = parseTasks(agent, name)
      def pending    = tasks.findAll { it.state == "" }
      def inProgress = tasks.findAll { it.state == "~" }
      if (!pending.isEmpty() || !inProgress.isEmpty())
      return "ERROR: quedan ${pending.size()} pendientes y ${inProgress.size()} en curso. Termínalas o cancélalas antes de cerrar."
      break
    }

    writeState(agent, name, target)
    switch (target) {
    case "running":
      agent.state.currentPlan = name
      registerAwareness(agent)
      break
    case "stopped":
    case "done":
      removeAwareness(agent)
      if (agent.state.currentPlan == name) agent.state.currentPlan = null
      break
    case "creating":
      agent.state.currentPlan = name
      break
    }
    return "Plan '${name}' → ${target}."
  }

  // ============ Readers ============
  static String getCurrentPlanName(def agent) {
    String name = agent.state.currentPlan
    if (!name) return "No hay ningún plan cargado. Usa listPlans() para ver los disponibles."
    Map s = readState(agent, name)
    if (s == null) {
      agent.state.currentPlan = null
      return "ERROR: el plan cargado '${name}' no existe en disco. Limpiado de memoria."
    }
    return "Plan cargado: '${name}' (estado: ${s.state})."
  }

  static String getCurrentTasks(def agent) {
    String name = agent.state.currentPlan
    if (!name) return "ERROR: no hay ningún plan cargado. Usa listPlans() primero."
    Map s = readState(agent, name)
    if (s == null) return "ERROR: el plan '${name}' no existe en disco."
    Path p = tasksPath(agent, name)
    if (!agent.fs.exists(p.toString())) return "ERROR: tasks.md no existe para '${name}'."

    def tasks      = parseTasks(agent, name)
    def inProgress = tasks.findAll { it.state == "~" }
    def pending    = tasks.findAll { it.state == "" }
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
      inProgress.each { sb.append("En curso:  #${it.id}. ${it.desc}\n") }
      if (inProgress.size() > 1)
      sb.append("AVISO: ${inProgress.size()} tareas en curso. Solo se permite una.\n")
    }
    if (pending.isEmpty()) {
      sb.append("Siguiente: (ninguna)\n")
    } else {
      sb.append("Siguiente: #${pending[0].id}. ${pending[0].desc}\n")
    }
    sb.append("Progreso:  ${completed}/${tasks.size()} completadas, ${aborted} abortadas")
    return sb.toString()
  }

  static String getCurrentPlanSpecifications(def agent) {
    String name = agent.state.currentPlan
    if (!name) return "ERROR: no hay ningún plan cargado. Usa listPlans() primero."
    Path p = specsPath(agent, name)
    return agent.fs.exists(p.toString()) ? p.toString() : "ERROR: specs.md no existe para '${name}'."
  }

  static String getCurrentIssues(def agent) {
    String name = agent.state.currentPlan
    if (!name) return "ERROR: no hay ningún plan cargado. Usa listPlans() primero."
    Path p = issuesPath(agent, name)
    return agent.fs.exists(p.toString()) ? p.toString() : "ERROR: issues.md no existe para '${name}'."
  }

  static String listPlans(def agent) {
    Path root = plansRoot(agent)
    if (!agent.fs.exists(root.toString())) return "No hay planes. Crea uno con createPlan('nombre')."

    def stateFiles = agent.fs.find(root.toString(), "**/state").toList()
    if (stateFiles.isEmpty()) return "No hay planes en disco."

    String loaded = agent.state.currentPlan
    def entries = []
    stateFiles.each { sf ->
      def sp = Paths.get(sf.toString())
      String planName = sp.getParent().getFileName().toString()
      Map s = readState(agent, planName)
      if (s == null) return
      entries << [name: planName, state: s.state, demoted: false]
    }

    // Demota running no cargados → stopped. No toca done ni creating.
    entries.each { e ->
      if (e.state == "running" && e.name != loaded) {
        writeState(agent, e.name, "stopped")
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

  static String getSections(def agent, List<String> titles) {
    String name = agent.state.currentPlan
    if (!name) return "ERROR: no hay ningún plan cargado. Usa listPlans() primero."
    Path p = specsPath(agent, name)
    if (!agent.fs.exists(p.toString())) return "ERROR: specs.md no existe para '${name}'."

    List<String> lines = []
    agent.fs.lines(p.toString()).each { lines << it }
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
  
  // ============ Helpers ============
  static String validateName(String name) {
    if (!name || !name.trim()) return "ERROR: indica el nombre del plan."
    if (name == "." || name == ".." || name.contains("/") || name.contains("\\"))
    return "ERROR: nombre inválido (sin separadores de ruta ni '..')."
    return null
  }

  static void registerAwareness(def agent) {
    removeAwareness(agent)
    agent.memory.registerProjectedOperation(AWARENESS_NAME, AWARENESS_PRIORITY) { mem, notifications ->
      try {
        String name = agent.state.currentPlan
        if (!name) return
        Map s = readState(agent, name)
        if (s == null || s.state != "running") return
        notifications.add("""[PLAN EN EJECUCIÓN: "${name}"]
Cuando termines la tarea en curso, actualiza tasks.md y pasa a la siguiente.""")
      } catch (Exception ignored) {
      }
    }
  }

  static void removeAwareness(def agent) {
    while (agent.memory.removeOperation(AWARENESS_NAME)) {
      // idempotente hasta vaciar duplicados
    }
  }
}
