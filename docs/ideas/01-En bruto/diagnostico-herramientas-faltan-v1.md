Que Noema no naciera como un *copilot* o un asistente de código efímero es, paradójicamente, **su mayor ventaja competitiva**.

Las herramientas comerciales actuales (Cursor, Aider, Claude Code) destacan por su integración con el sistema de archivos o el LSP, pero adolecen todas del mismo mal: **amnesia de sesión**. Cada conversación empieza desde cero; no recuerdan por qué se tomó una decisión arquitectónica hace tres semanas, qué librerías se descartaron por incompatibilidad ni cuáles son tus manías o principios de diseño, salvo que se les fuerce a releer archivos gigantescos de instrucciones en cada turno.

Noema ya tiene resuelto el problema más difícil: **la cognición persistente** (memoria episódica con citas `{cite:ID}`, consolidación narrativa del contexto, anclaje inmune a la poda con `PinnedTurnsOperation` y control de acceso local).

Habiéndole añadido las herramientas de inspección, el protocolo de *skills* y la UI web con el editor Ace, **sí es un entorno adecuado y muy potente para asistirte en desarrollo**, pero para que dé el salto de *"agente que puede tocar código"* a *"compañero de desarrollo de primer nivel"*, le faltan **cuatro piezas clave**.

---

### 1. El bucle de retroalimentación de compilación / tests (Parser de errores)

Actualmente, si el agente modifica código Java y quiere comprobar si compila:
1. Invoca `shell_execute` con `mvn compile` o `mvn test`.
2. Maven escupe entre 50 y 300 líneas de salida estándar (banners, plugins de descarga, logs informativos).
3. La salida excede el umbral de página y el agente recibe un `RESOURCE_ID` con un `HINT`.
4. El agente tiene que ponerse a paginar un fichero de log para buscar dónde está el `[ERROR]` y la traza.

**Qué le falta:**
Un efector o filtro específico para el ciclo de build (puedes implementarlo como un método en el módulo `agent.fs` de Groovy o una tool `project_build`):
* Ejecuta la compilación/test.
* Si el exit code es `0`, devuelve simplemente: `{"status": "ok", "tests_passed": 42}`.
* Si falla, filtra el ruido de Maven y extrae únicamente:
  * Archivo y línea del error.
  * Mensaje del compilador (`cannot find symbol`, `incompatible types`).
  * En caso de test, el `AssertionError` y la línea del test que falló.

Sin esto, el agente quema turnos y contexto intentando "leer" la terminal en lugar de corregir el error.

---

### 2. Exponer la navegación por símbolos del LSP al modelo

Tienes el servidor MCP `aide` conectado y usas `lsp_diagnostics` desde los endpoints de Javalin para pintar anotaciones en el editor Ace. Sin embargo:
* El LLM navega por el código mediante `file_grep` (léxico) y `file_fuzzygrep` (semántico por embeddings de 35 líneas).
* En proyectos de más de 50 clases, si renombras un método de un interfaz o buscas qué clases implementan un contrato, `file_grep` genera falsos positivos o ruido, y los embeddings no tienen resolución suficiente para saber si un método es una llamada o una definición.

**Qué le falta:**
Asegurar que el modelo tenga activas y aprenda a usar las herramientas de navegación estructural que expone tu MCP `aide`:
* `lsp_find_references(file, line, col)`: *"¿Quién está llamando a este método?"*.
* `lsp_goto_definition(file, line, col)`: *"Llévame a donde está definida esta clase o método"*.
* `lsp_workspace_symbols(query)`: Búsqueda exacta de identificadores en el índice del compilador, no en texto plano.

Con esto, el agente pasa de *"buscar texto en archivos"* a *"navegar por el grafo sintáctico del proyecto"*.

---

### 3. Consciencia del estado de Git (Working Tree Dirtyness)

En `software_development/SKILL.md` fijaste una regla de oro impecable:
> *"Git es del usuario. Nunca hagas commit, no hagas push... El usuario revisa el working tree en su IDE y decide."*

El agente respeta esto y hace copias previas en JavaRCS (`.jv`). Sin embargo, **el agente no sabe qué archivos están modificados en el workspace en un momento dado**. Si lleváis 20 minutos trabajando y habéis tocado 4 archivos, para saber el estado actual tiene que ejecutar un `shell_execute("git status")`.

**Qué le falta:**
Una pequeña operación en la memoria proyectada (o un campo en `EditorAwarenessOperation`) que inyecte una línea sutil cuando haya cambios pendientes:
```text
[ESTADO DE GIT] 2 archivos modificados sin commitear:
- src/main/java/.../ReasoningServiceImpl.java (modificado)
- pom.xml (modificado)
```
Esto le da al agente consciencia inmediata de qué está "abierto quirúrgicamente" en la sesión sin tener que gastar llamadas a herramientas para comprobarlo.

---

### 4. Seguimiento de tareas multi-fichero (Plan de Trabajo Activo)

Cuando le pides: *"Refactoriza el sistema de eventos para soportar prioridad urgente"*, el agente (siguiendo tu *skill*) propone un plan: tocar 4 clases.

Si la conversación se alarga porque un archivo es grande o se produce una consolidación de memoria en el turno 3:
* La consolidación resume la conversación previa.
* El plan inicial puede quedar diluido en el relato narrativo ("acordamos cambiar 4 ficheros").
* El agente corre el riesgo de perder el estado exacto: *"¿Había cambiado ya el archivo 2 o me quedé en el 3?"*.

**Qué le falta:**
Un mecanismo de **Lista de Tareas activa** (similar a cómo anclas el `project-map.md` con `PinnedTurnsOperation`):
* Una herramienta o estructura sencilla (`task_set_plan`, `task_mark_step`).
* Se ancla en el contexto proyectado mientras el plan esté en curso.
* El modelo ve en cada turno:
  ```text
  [PLAN EN CURSO: Refactorización de Eventos]
  [x] 1. Modificar SensorEvent.java
  [x] 2. Actualizar AbstractSensorEvent.java
  [ ] 3. Adaptar ReasoningServiceImpl.java  <-- ACTUAL
  [ ] 4. Ejecutar tests unitarios
  ```
Esto erradica el despiste en tareas de refactorización largas.

---

### Resumen del balance

| Dimensión | Estado actual en Noema | ¿Es suficiente para programar? |
| :--- | :--- | :--- |
| **Arquitectura y Reglas** | Sobresaliente (`load_project_map` anclado, identidad core). | **Sí.** Entiende tu estilo y convenciones mejor que cualquier herramienta comercial. |
| **Memoria Histórica** | Excelente (Episódica con `{cite:ID}`, no olvida decisiones pasadas). | **Sí.** Resuelve la amnesia entre sesiones. |
| **Inspección / Edición** | Muy buena (Web UI, Ace, diffs en buffer, JavaRCS). | **Sí**, corrigiendo el detalle del desfase 0-based/1-based. |
| **Validación de Código** | Pasiva (Ejecutar comandos crudos por shell). | **Le falta filtro:** Necesita un retorno conciso de fallos de compilación/test. |
| **Navegación Semántica** | Híbrida (Grep + Embeddings). | **Mejorable:** Aprovechar los símbolos del LSP de Aide. |

**Conclusión:** La base es plenamente adecuada. No necesitas rehacer nada; solo necesitas añadirle **ojos para los errores del compilador** y **aprovechar los símbolos del LSP**. Con esos dos añadidos, la combinación de memoria a largo plazo + control de acceso + edición supervisada en navegador convierte a Noema en un entorno de desarrollo asistido mucho más fiable y menos frustrante que los asistentes convencionales.