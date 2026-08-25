

# Integración de skills estándar en Noema

**Diseño y Especificación Técnica (Versión 3.0)**

---

## 1. Objetivo y alcance

Permitir que Noema descubra, cargue y ejecute **skills que siguen el estándar Agent Skills** (formato `SKILL.md` con frontmatter YAML) de forma nativa. 

La arquitectura soporta la activación simultánea de múltiples skills (para habilitar flujos compuestos o habilidades complementarias), ejecuta los scripts auxiliares como herramientas de usuario de confianza dentro del espacio de trabajo y retiene las instrucciones operativas en el contexto del modelo mediante un mecanismo de **fijado dinámico (*pinning*)** integrado en el pipeline de la memoria proyectada (`ProjectedMemory`), sin alterar la inmutabilidad de la memoria episódica (`EpisodicMemory`) ni interferir con el ciclo de vida natural de la memoria reciente (`RecentMemory`).

---

## 2. El estándar Agent Skills

### 2.1. Formato y estructura de un skill

Un skill es un directorio autocontenido que aloja obligatoriamente en su raíz un archivo `SKILL.md` con metadatos estructurados en un bloque frontmatter. De forma opcional, puede incluir subdirectorios con recursos auxiliares, manuales complementarios, esquemas o scripts (`scripts/`, `references/`, `templates/`).

```markdown
---
name: refactor-clean-code
description: Guía paso a paso para refactorizar código Java aplicando principios SOLID
version: 1.0.0
---

# Instrucciones del procedimiento
A partir de este punto se detallan las directivas y reglas que el agente
debe seguir mientras el skill permanezca activo en su consciencia...
```

### 2.2. Parseo del frontmatter sin dependencias externas

Para extraer los metadatos sin incorporar librerías pesadas como SnakeYAML o Jackson-YAML al `pom.xml`, el descubrimiento se apoya en un **parseador manual por líneas** implementado en `SkillUtils`:

* El archivo `SKILL.md` se lee secuencialmente desde el inicio.
* Se verifica que la primera línea no vacía sea el delimitador `---`.
* Se extraen las líneas posteriores hasta encontrar el delimitador de cierre `---`.
* Dentro de ese bloque, se procesan líneas con el formato clave-valor plano `clave: valor`:
  - `name`: Nombre técnico del skill (identificador).
  - `description`: Descripción funcional de su propósito.
  - `version`: Versión declarada del skill.
* El resto del archivo a partir del segundo `---` se trata como el cuerpo íntegro de instrucciones Markdown.

### 2.3. Ubicación canónica de descubrimiento

Los skills se ubican en el directorio estándar dentro del espacio de trabajo del proyecto:

* **Workspace del proyecto:** `.claude/skills/<nombre_skill>/`

Cada subdirectorio dentro de `.claude/skills/` que contenga un archivo `SKILL.md` válido en su raíz es descubierto como un skill disponible para el agente.

---

## 3. Herramientas del agente para la gestión de skills

Las herramientas se ubican en el paquete `io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.skills`.

```
[Agent] ──► [ReasoningService]
                 │
                 ├── ListSkillsTool (`list_skills`)
                 ├── ActivateSkillTool (`activate_skill`)  [shouldPin() = true]
                 ├── DeactivateSkillTool (`deactivate_skill`)
                 ├── ReadSkillResourceTool (`read_skill_resource`) [Paginada]
                 └── RunSkillScriptTool (`run_skill_script`)       [Paginada]
```

### 3.1. `list_skills`

* **Propósito:** Devolver al modelo el catálogo de skills disponibles en el proyecto.
* **Clase base:** `AbstractAgentTool`.
* **Modo:** `MODE_READ`. **Tipo:** `TYPE_OPERATIONAL`. `shouldPin() = false`.
* **Parámetros:** Ninguno.
* **Comportamiento:**
  1. Invoca a `SkillUtils.listSkills(this.agent)`.
  2. Escanea `.claude/skills/`, parsea los metadatos de cada `SKILL.md` y construye un array JSON estructurado:
     ```json
     [
       {
         "name": "refactor-clean-code",
         "description": "Guía paso a paso para refactorizar código Java aplicando principios SOLID",
         "version": "1.0.0"
       }
     ]
     ```
  3. Si no hay skills instalados, devuelve una lista vacía con estado exitoso.

### 3.2. `activate_skill`

* **Propósito:** Cargar las directivas de un skill y fijarlas en la memoria proyectada del agente.
* **Clase base:** `AbstractAgentTool`.
* **Modo:** `MODE_READ`. **Tipo:** `TYPE_OPERATIONAL`.
* **Directiva de fijado:** Sobrescribe `public boolean shouldPin() { return true; }`.
* **Parámetros:**
  - `name` (string, obligatorio): El identificador único del skill a activar.
* **Comportamiento:**
  1. Localiza el skill mediante `SkillUtils.getSkill(this.agent, name)`.
  2. Si no existe, retorna `formatErrorResponse("Skill no encontrado: " + name)`.
  3. Si existe, lee el contenido completo de `SKILL.md` mediante `skill.getContents()` y lo devuelve como resultado de la herramienta.
  4. Al tener `shouldPin() = true`, el pipeline de la memoria proyectada capturará automáticamente el par de mensajes para retenerlo indefinidamente tras las compactaciones.

### 3.3. `deactivate_skill`

* **Propósito:** Desactivar un skill en curso, retirando su fijado de la proyección y deteniendo sus notificaciones periódicas.
* **Clase base:** `AbstractAgentTool`.
* **Modo:** `MODE_READ`. **Tipo:** `TYPE_OPERATIONAL`. `shouldPin() = false`.
* **Parámetros:**
  - `name` (string, obligatorio): El nombre del skill a desactivar.
* **Comportamiento:**
  1. Localiza la instancia de `PinnedTurnsOperation` asociada a la memoria proyectada del subcanal actual.
  2. Invoca `pinnedOperation.removePinnedTurn(state -> ...)` evaluando si los argumentos del `requestMessage` corresponden al skill indicado.
  3. Devuelve una confirmación JSON:
     ```json
     {
       "status": "success",
       "message": "Skill 'refactor-clean-code' desactivado correctamente."
     }
     ```

### 3.4. `read_skill_resource`

* **Propósito:** Leer archivos complementarios empaquetados dentro del subárbol de un skill (documentación adicional, esquemas, plantillas) sin necesidad de activar el skill completo.
* **Clase base:** `AbstractPaginatedAgentTool`.
* **Modo:** `MODE_READ`. **Tipo:** `TYPE_OPERATIONAL`. `shouldPin() = false`.
* **Parámetros:**
  - `skill_name` (string, obligatorio): Nombre del skill contenedor.
  - `path` (string, obligatorio): Ruta relativa al archivo dentro de la carpeta del skill (ej: `references/api-guide.md`).
* **Comportamiento:**
  1. Localiza el skill y resuelve la ruta mediante `skill.resolveResource(path)`.
  2. Valida mediante `startsWith(skillRoot)` que la ruta normalizada no escape del directorio del skill (prevención de *path traversal*).
  3. Si el archivo no existe o es un directorio, devuelve un error descriptivo.
  4. Obtiene el identificador simbólico (`getIdFromPath(filePath)`) y entrega el contenido paginado mediante `servePaginatedResource(resourceId)`.
  5. La respuesta queda sujeta automáticamente a la política de poda de contexto (`trimResult`) en turnos posteriores.

### 3.5. `run_skill_script`

* **Propósito:** Ejecutar scripts de soporte ubicados en la carpeta `scripts/` de un skill.
* **Clase base:** `AbstractPaginatedAgentTool`.
* **Modo:** `MODE_READ`. **Tipo:** `TYPE_OPERATIONAL`. `shouldPin() = false`.
* **Parámetros:**
  - `name` (string, obligatorio): Nombre del skill.
  - `script` (string, obligatorio): Nombre del archivo script dentro de `scripts/` (ej: `check-style.sh`).
  - `args` (array de strings, opcional): Argumentos de línea de comandos para el script.
* **Comportamiento:**
  1. Localiza el archivo dentro de `.claude/skills/<name>/scripts/<script>`.
  2. Construye el comando de ejecución directa mediante `ProcessBuilder`, configurando como directorio de trabajo la raíz del proyecto (`agent.getPaths().getWorkspaceFolder()`). Al tratarse de scripts instalados deliberadamente por el usuario, se ejecutan de forma directa sin requerir el sandbox de firejail.
  3. Redirige la salida combinada (`stdout` y `stderr`) a un archivo temporal en `var/tmp/skill_script_<uuid>.out`.
  4. Inicia el proceso, espera su terminación y captura el código de salida (*exit code*).
  5. Sirve el resultado mediante el sistema universal de paginación (`servePaginatedResource`).

---

### 4. Detalle de las clases `Skill` y `SkillUtils`

Estas dos clases deben articular la lógica del dominio en el paquete `io.github.jjdelcerro.noema.lib.impl.services.reasoning.skills`.

#### `SkillUtils.java` (Descubrimiento y parseo)
* **Ubicación:** `io.github.jjdelcerro.noema.lib.impl.services.reasoning.skills.SkillUtils`
* **Naturaleza:** Clase de utilidades con métodos estáticos para la localización y construcción de instancias.
* **Responsabilidades y métodos:**
  - `Path getSkillsFolder(Agent agent)`: Resuelve y normaliza la ruta `.claude/skills/` relativa a la raíz del workspace (`agent.getPaths().getWorkspaceFolder()`).
  - `List<Skill> listSkills(Agent agent)`:
    1. Comprueba si el directorio `.claude/skills/` existe. Si no, retorna lista vacía.
    2. Itera sobre los subdirectorios inmediatos.
    3. Para cada subdirectorio que contenga un archivo regular `SKILL.md`, invoca el parseo del frontmatter y construye una instancia de `Skill`.
  - `Skill getSkill(Agent agent, String name)`: Localiza la carpeta específica `.claude/skills/<name>/`, valida la existencia de `SKILL.md`, parsea sus metadatos y devuelve la instancia `Skill` (o `null` si no existe).
  - `Skill parseSkillFile(Path skillFilePath)` (privado/auxiliar):
    - Lee línea a línea `SKILL.md`.
    - Detecta el primer delimitador `---`.
    - Lee las líneas clave-valor hasta el segundo delimitador `---`.
    - Extrae `name`, `description` y `version` (aplicando `trim()` y limpiando posibles comillas).
    - Captura el resto del archivo como el cuerpo de instrucciones.

#### `Skill.java` (Entidad de dominio)
* **Ubicación:** `io.github.jjdelcerro.noema.lib.impl.services.reasoning.skills.Skill`
* **Estado interno:**
  - `private final Agent agent`: Referencia al agente para acceder a rutas y memoria.
  - `private final Path rootPath`: Ruta absoluta al directorio del skill (`.claude/skills/<nombre>/`).
  - `private final String name`: Identificador del skill.
  - `private final String description`: Descripción funcional.
  - `private final String version`: Versión declarada.
* **Métodos principales:**
  - `String getName()`, `String getDescription()`, `String getVersion()`: Getters de metadatos.
  - `String getContents()`: Lee y retorna el contenido completo de `SKILL.md` (o el cuerpo de instrucciones) en UTF-8.
  - `Path resolveResource(String relativePath)`:
    1. Resuelve `rootPath.resolve(relativePath).normalize()`.
    2. Valida mediante `resolvedPath.startsWith(rootPath)` que la ruta no escape del subárbol del skill para evitar *path traversal*. Retorna `null` o lanza excepción si es inválida.
  - `ProcessBuilder createScriptProcess(String scriptName, List<String> args)`:
    1. Localiza el script en `rootPath.resolve("scripts").resolve(scriptName)`.
    2. Valida que el archivo exista y sea regular.
    3. Construye un `ProcessBuilder` con el comando y sus argumentos, configurando como directorio de trabajo la raíz del proyecto (`agent.getPaths().getWorkspaceFolder().toFile()`).
  - `void deactivate(String subchannel)`: Localiza la memoria proyectada del subcanal e invoca la retirada del fijado para este skill.

---

## 5. Ciclo de vida del fijado en el pipeline de memoria proyectada

La retención de las instrucciones operativas de un skill activo no requiere alterar la memoria reciente (`RecentMemory`) ni modificar el protocolo de compactación de `MemoryCompactionService`. Todo el ciclo de vida se delega en `PinnedTurnsOperation`, la operación del pipeline de proyección (`ProjectedMemoryOperation`) responsable del anclaje y reinyección de turnos.

#### 1. Activación y captura declarativa
Cuando el modelo invoca `activate_skill(name)`, la herramienta devuelve el texto íntegro de `SKILL.md`. Al haber sobrescrito `shouldPin() = true`, `PinnedTurnsOperation` detecta automáticamente este resultado durante la fase de proyección y registra el par `AiMessage` (petición) y `ToolExecutionResultMessage` (instrucciones) en su lista interna de turnos fijados.

Durante los primeros turnos (~0 a 40), mientras los mensajes permanezcan físicamente en `RecentMemory`, el turno se proyecta en su posición cronológica estándar dentro del flujo conversacional.

#### 2. Reinyección tras la compactación episódica
Cuando `RecentMemory` supera el umbral de 40 turnos y compacta la memoria de trabajo, la llamada original a `activate_skill` se purga del historial reciente. 

A partir de ese instante, en cada nueva inferencia:
* `PinnedTurnsOperation` comprueba si el identificador del mensaje fijado sigue existiendo en `RecentMemory.getMessages()`.
* Al constatar su ausencia, **reinyecta automáticamente la pareja de mensajes al principio del contexto proyectado**, inmediatamente después de los mensajes de sistema base (prompt base y checkpoint narrativo).
* Si hay múltiples skills activos simultáneamente, se inyectan en cabecera en el orden exacto en que fueron activados.

#### 3. Emisión del recordatorio periódico
Para evitar derivas operativas en diálogos extensos, `PinnedTurnsOperation` evalúa periódicamente el avance de turnos consultando `memory.getLastInteractionTurn()`. Cuando `(currentTurn - lastNotifiedTurn) >= 5` y existen skills fijados activos:
* Solicita a la herramienta el texto del recordatorio mediante `tool.getPinnedNotificationMessage(requestMessage, resultMessage)`.
* Inyecta el aviso en la lista de notificaciones efímeras del turno como una observación del canal `SYSTEMNOTIFICATION`.

#### 4. Desactivación
Cuando se invoca `deactivate_skill(name)`:
* La herramienta (o `Skill.deactivate`) localiza `PinnedTurnsOperation` en la memoria proyectada y ejecuta `removePinnedTurn(predicate)`.
* El predicado identifica el turno cuyo argumento de llamada corresponde al skill especificado y lo elimina de la lista interna.
* Si el turno ya estaba compactado, desaparecerá de la cabecera del contexto en la siguiente inferencia y se cancelarán sus recordatorios.

---

## 6. Trazabilidad en la memoria episódica

Cuando una herramienta devuelve un texto extenso (como un manual `SKILL.md` de varios kilobytes), la base de datos H2 debe registrar el evento sin desbordar el tamaño de columna pero conservando los metadatos esenciales para búsquedas semánticas.

Se actualiza `applyStoragePolicy` en `EpisodicMemoryImpl.java`:

* Si `originalText.length() > MAX_DB_TEXT_SIZE` (2048 caracteres):
  - Se conservan los primeros 2048 caracteres del texto original.
  - Se añade una nota de truncamiento estructurada:
    ```text
    \n\n[Nota del sistema: Contenido truncado para almacenamiento en base de datos. Tamaño original: X caracteres. Se conservan los primeros 2KB para indexación.]
    ```
* **Consecuencia técnica:** El frontmatter YAML (`name`, `description`, `version`) y la introducción metodológica del skill quedan siempre guardados en la columna `tool_result` y vectorizados en `embedding_blob`, permitiendo que herramientas como `search_full_history` localicen cuándo y por qué se utilizó un skill en el pasado.

---

## 7. Seguridad y protección del directorio de skills

Los skills definen las leyes de comportamiento del modelo y contienen scripts ejecutables. Para evitar vectores de ataque por inyección indirecta o auto-modificación accidental del comportamiento:

* **Regla de solo lectura en `AgentAccessControlImpl.java`**:
  En el método `resolvePath(String rawPath, AccessMode mode)`:
  - Si `mode == AccessMode.PATH_ACCESS_WRITE`, se verifica si la ruta normalizada contiene `/.claude/skills/` o finaliza en `/.claude/skills`.
  - En caso afirmativo, se lanza inmediatamente una `SecurityException("ACCESO DENEGADO: No se permite modificar archivos dentro de .claude/skills/")`.
* **Efecto operativo:** Ninguna herramienta de modificación del agente (`file_write`, `file_patch`, `file_search_and_replace`, `file_mkdir`) puede alterar los archivos `SKILL.md` ni los scripts asociados. La instalación y edición de skills queda reservada al usuario fuera del entorno de ejecución del agente.

---

## 8. Resumen de contratos y responsabilidades

| Elemento / Contrato | Responsabilidad | Ubicación / Implementación |
| :--- | :--- | :--- |
| `boolean shouldPin()` | Directiva declarativa para solicitar la retención del turno al pipeline de proyección | `AgentTool.java` (`false` por defecto) / `ActivateSkillTool.java` (`true`) |
| `String getPinnedNotificationMessage(...)` | Generación del texto del recordatorio periódico para el skill activo | `AgentTool.java` / `ActivateSkillTool.java` |
| `PinnedTurnsOperation` | Captura, reinyección tras compactación, emisión de recordatorios y persistencia del estado fijado | `io.github.jjdelcerro.noema.lib.impl.memory.proyected.operations` |
| `Skill` | Entidad de dominio: resolución de recursos internos, creación de procesos para scripts y desactivación | `io.github.jjdelcerro.noema.lib.impl.services.reasoning.skills.Skill` |
| `SkillUtils` | Descubrimiento en `.claude/skills/` y parseo manual de frontmatter YAML | `io.github.jjdelcerro.noema.lib.impl.services.reasoning.skills.SkillUtils` |
| `read_skill_resource` | Lectura paginada y segura de archivos auxiliares empaquetados en el skill | `ReadSkillResourceTool.java` (`AbstractPaginatedAgentTool`) |
| `run_skill_script` | Ejecución estándar de scripts en el workspace con salida paginada | `RunSkillScriptTool.java` (`AbstractPaginatedAgentTool` + `ProcessBuilder`) |
| Truncamiento a 2KB | Preservación de cabeceras YAML y contexto inicial para indexación vectorial en H2 | `EpisodicMemoryImpl.applyStoragePolicy` |
| Protección `.claude/skills/` | Bloqueo estricto de escritura en el sandbox para prevenir auto-modificación | `AgentAccessControlImpl.resolvePath` |

---

## 9. Plan de acción para la implementación

### Fase 1: Trazabilidad en la memoria episódica (Truncamiento con contexto)

El objetivo es asegurar que la base de datos H2 almacene y vectorice los metadatos y las primeras instrucciones de un skill cuando el archivo `SKILL.md` supere el límite de almacenamiento de 2 KB.

1. **Modificar `EpisodicMemoryImpl.java` (`applyStoragePolicy`)**:
   * **Ubicación:** `io.github.jjdelcerro.noema.lib.impl.memory.episodic.EpisodicMemoryImpl`.
   * **Lógica a sustituir:** Actualmente, si el texto supera `MAX_DB_TEXT_SIZE` (2048 caracteres), se descarta todo el contenido y se guarda únicamente un JSON con metadatos (`original_size_chars`), perdiendo el texto por completo.
   * **Nueva implementación:**
     ```java
     private String applyStoragePolicy(String originalText) {
         if (originalText == null) {
             return null;
         }
         if (originalText.length() <= MAX_DB_TEXT_SIZE) {
             return originalText;
         }
         String truncated = originalText.substring(0, MAX_DB_TEXT_SIZE);
         String note = String.format(
             "\n\n[Nota del sistema: Contenido truncado para almacenamiento en BD. Tamaño original: %d caracteres. Se conservan los primeros 2KB.]",
             originalText.length()
         );
         return truncated + note;
     }
     ```
   * **Impacto:** Al persistir la llamada a `activate_skill`, el frontmatter YAML y las primeras secciones de instrucciones quedan en la columna `tool_result` y se indexan en `embedding_blob`, permitiendo su recuperación semántica futura con `search_full_history`.

---

### Fase 2: Conexión de desanclaje en la memoria proyectada

El pipeline de proyección ya cuenta con `PinnedTurnsOperation`, pero es necesario exponer un mecanismo para que la herramienta `deactivate_skill` (o la entidad `Skill`) pueda solicitar la retirada de un skill fijado.

1. **Actualizar el contrato `ProjectedMemory.java`**:
   * **Ubicación:** `io.github.jjdelcerro.noema.lib.memory.proyected.ProjectedMemory`.
   * **Añadir método de desanclaje:**
     ```java
     void removePinnedTurn(Predicate<PinnedTurnsOperation.PinnedTurnState> predicate);
     ```

2. **Implementar el desanclaje en `ProjectedMemoryImpl.java`**:
   * **Ubicación:** `io.github.jjdelcerro.noema.lib.impl.memory.proyected.ProjectedMemoryImpl`.
   * **Implementación:** Localizar la operación `PinnedTurnsOperation` en la lista `this.operations` y delegar la llamada:
     ```java
     @Override
     public void removePinnedTurn(Predicate<PinnedTurnsOperation.PinnedTurnState> predicate) {
         for (ProjectedMemoryOperation op : this.operations) {
             if (op instanceof PinnedTurnsOperation pinnedOp) {
                 if (pinnedOp.removePinnedTurn(predicate)) {
                     this.save();
                 }
                 break;
             }
         }
     }
     ```

3. **Exponer la memoria proyectada en `ReasoningService.java` y `ReasoningServiceImpl.java`**:
   * **En `ReasoningService.java`:** Añadir `ProjectedMemory getProjectedMemory(String subchannel);`.
   * **En `ReasoningServiceImpl.java`:** Hacer público el método `getProyectedMemory(subchannel)` (normalizando el nombre a `getProjectedMemory`).

---

### Fase 3: Seguridad, descubrimiento y modelo de dominio de skills

Esta fase prepara el control de acceso, retira la infraestructura obsoleta y crea el paquete de dominio con la entidad `Skill` y la factoría `SkillUtils`.

1. **Regla de solo lectura en `AgentAccessControlImpl.java`**:
   * **Ubicación:** `io.github.jjdelcerro.noema.lib.impl.AgentAccessControlImpl`.
   * **Modificación en `resolvePath(String rawPath, AccessMode mode)`:** Dentro de la comprobación `if (mode == AccessMode.PATH_ACCESS_WRITE)`:
     ```java
     String targetPathString = target.toString().replace("\\", "/");
     if (targetPathString.contains("/.claude/skills/") || targetPathString.endsWith("/.claude/skills")) {
         throw new SecurityException("ACCESO DENEGADO: No se permite modificar archivos dentro de .claude/skills/");
     }
     ```

2. **Limpieza de infraestructura legacy**:
   * Eliminar las clases `LoadSkillTool.java` y `ListSkillsTool.java` del paquete `io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.identity`.
   * En `AgentSettingsImpl.java` (`setupSettings`): Eliminar `"var/skills/readme.md"` de la lista de recursos base.
   * En `ReasoningServiceImpl.java` (`start`): Eliminar `"var/skills/readme.md"` del array de instalación.

3. **Creación del paquete de dominio**:
   * Crear el paquete `io.github.jjdelcerro.noema.lib.impl.skills`.

4. **Implementación de `SkillUtils.java` (Descubrimiento y parseo)**:
   * **Ubicación:** `io.github.jjdelcerro.noema.lib.impl.skills.SkillUtils`.
   * **Métodos:**
     - `public static Path getSkillsFolder(Agent agent)`: Devuelve `agent.getPaths().getWorkspaceFolder().resolve(".claude/skills").toAbsolutePath().normalize()`.
     - `public static List<Skill> listSkills(Agent agent)`:
       1. Comprueba si `getSkillsFolder(agent)` existe. Si no, retorna lista vacía.
       2. Itera sobre los directorios mediante `Files.list(...)`.
       3. Para cada directorio que contenga un archivo regular `SKILL.md`, parsea sus metadatos e instancia un `Skill`.
     - `public static Skill getSkill(Agent agent, String name)`:
       1. Resuelve `getSkillsFolder(agent).resolve(name)`.
       2. Valida la existencia de `SKILL.md`.
       3. Parsea el archivo y retorna la instancia de `Skill` (o `null` si no existe).
         - Lee las líneas del archivo en UTF-8.
         - Localiza el primer delimitador `---` (ignorando líneas vacías iniciales).
         - Lee las líneas clave-valor hasta el segundo delimitador `---`.
         - Extrae `name`, `description` y `version` eliminando comillas envolventes si existen.
         - El resto de líneas se capturan como `instructions`.

5. **Implementación de `Skill.java` (Entidad de dominio)**:
   * **Ubicación:** `io.github.jjdelcerro.noema.lib.impl.skills.Skill`.
   * **Campos:**
     ```java
     private final Agent agent;
     private final Path rootPath;
     private final String name;
     private final String description;
     private final String version;
     ```
   * **Métodos:**
     - `public String getName()` / `public String getDescription()` / `public String getVersion()`.
     - `public String getContents()`: Lee el contenido completo de `rootPath.resolve("SKILL.md")` en UTF-8.
     - `public Path resolveResource(String relativePath)`:
       1. Normaliza `Path target = rootPath.resolve(relativePath).normalize()`.
       2. Valida `if (!target.startsWith(rootPath))` lanzando `SecurityException` si hay *path traversal*.
       3. Retorna `target`.
     - `public ProcessBuilder createScriptProcess(String scriptName, List<String> args)`:
       1. Resuelve `Path scriptFile = rootPath.resolve("scripts").resolve(scriptName).normalize()`.
       2. Verifica `if (!Files.exists(scriptFile) || !Files.isRegularFile(scriptFile))` lanzando excepción.
       3. Construye la lista de comando: `["bash", scriptFile.toString(), ...args]`.
       4. Crea `ProcessBuilder`, asignando como directorio de trabajo `agent.getPaths().getWorkspaceFolder().toFile()`.
     - `public void deactivate(String subchannel)`:
       1. Obtiene `ReasoningService` del agente.
       2. Obtiene `ProjectedMemory` del subcanal.
       3. Invoca:
          ```java
          projectedMemory.removePinnedTurn(state -> {
              ToolExecutionResultMessage result = state.getResultMessage();
              if (result != null && "activate_skill".equals(result.toolName())) {
                  AiMessage req = state.getRequestMessage();
                  if (req != null && req.hasToolExecutionRequests()) {
                      for (ToolExecutionRequest r : req.toolExecutionRequests()) {
                          if (r.arguments() != null && r.arguments().contains("\"" + this.name + "\"")) {
                              return true;
                          }
                      }
                  }
              }
              return false;
          });
          ```
          En lugar de usar una clase anonima con la implementacion del predicate, mete la implementacion en un metodo privado y usalo mediante una lambda.

---

### Fase 4: Implementación de las herramientas de skills (`AgentTools`)

Crear el paquete `io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.skills` e implementar las 5 herramientas.

1. **`ListSkillsTool.java` (`list_skills`)**:
   * **Clase base:** `AbstractAgentTool`.
   * **Modo:** `MODE_READ`. **Tipo:** `TYPE_OPERATIONAL`. `shouldPin() = false`.
   * **Parámetros:** Ninguno.
   * **Lógica en `execute()`:**
     - Llama a `SkillUtils.listSkills(this.agent)`.
     - Mapea cada `Skill` a un `Map` con `name`, `description` y `version`.
     - Retorna el JSON resultante con `gson.toJson(list)`.

2. **`ActivateSkillTool.java` (`activate_skill`)**:
   * **Clase base:** `AbstractAgentTool`.
   * **Modo:** `MODE_READ`. **Tipo:** `TYPE_OPERATIONAL`.
   * **Directiva de fijado:** Sobrescribe `public boolean shouldPin() { return true; }`.
   * **Mensaje de recordatorio periódico:**
     ```java
     @Override
     public String getPinnedNotificationMessage(ToolExecutionRequest request, ToolExecutionResultMessage result) {
         Map<String, String> args = gson.fromJson(request.arguments(), Map.class);
         String skillName = args != null ? args.get("name") : "desconocido";
         return String.format(
             "[SKILL ACTIVO: %s]\nEste skill define directivas obligatorias para tu comportamiento. "
             + "Cuando concluyas el procedimiento, invoca 'deactivate_skill(name: \"%s\")' para liberarlo del contexto.",
             skillName, skillName
         );
     }
     ```
   * **Parámetros:** `name` (string, obligatorio).
   * **Lógica en `execute()`:**
     - Obtiene `Skill skill = SkillUtils.getSkill(this.agent, name)`.
     - Si es `null`, retorna `error("Skill no encontrado: " + name)`.
     - Si existe, retorna `skill.getContents()`.

3. **`DeactivateSkillTool.java` (`deactivate_skill`)**:
   * **Clase base:** `AbstractAgentTool`.
   * **Modo:** `MODE_READ`. **Tipo:** `TYPE_OPERATIONAL`. `shouldPin() = false`.
   * **Parámetros:** `name` (string, obligatorio).
   * **Lógica en `execute()`:**
     - Obtiene `Skill skill = SkillUtils.getSkill(this.agent, name)`.
     - Si existe, invoca `skill.deactivate(this.agent.getCurrentSubchannel())`.
     - Retorna confirmación JSON: `{"status": "success", "message": "Skill '" + name + "' desactivado correctamente."}`.

4. **`ReadSkillResourceTool.java` (`read_skill_resource`)**:
   * **Clase base:** `AbstractPaginatedAgentTool`.
   * **Modo:** `MODE_READ`. **Tipo:** `TYPE_OPERATIONAL`. `shouldPin() = false`.
   * **Parámetros:** `skill_name` (string, obligatorio), `path` (string, obligatorio).
   * **Lógica en `execute()`:**
     - Obtiene `Skill skill = SkillUtils.getSkill(this.agent, skillName)`.
     - Resuelve el archivo con `Path resource = skill.resolveResource(path)`.
     - Valida `Files.exists(resource)` y `Files.isRegularFile(resource)`.
     - Obtiene `String resourceId = getIdFromPath(resource)`.
     - Retorna `servePaginatedResource(resourceId)`.

5. **`RunSkillScriptTool.java` (`run_skill_script`)**:
   * **Clase base:** `AbstractPaginatedAgentTool`.
   * **Modo:** `MODE_READ`. **Tipo:** `TYPE_OPERATIONAL`. `shouldPin() = false`.
   * **Parámetros:** `name` (string, obligatorio), `script` (string, obligatorio), `args` (array de strings, opcional).
   * **Lógica en `execute()`:**
     - Obtiene `Skill skill = SkillUtils.getSkill(this.agent, name)`.
     - Obtiene `ProcessBuilder pb = skill.createScriptProcess(script, args)`.
     - Genera un archivo temporal en `var/tmp/skill_script_<uuid>.out`.
     - Configura `pb.redirectErrorStream(true)` y redirige la salida al archivo temporal.
     - Inicia el proceso (`pb.start()`), espera a que finalice con `process.waitFor()` y captura el exit code.
     - Obtiene `String resourceId = getIdFromPath(outputFile)`.
     - Retorna `servePaginatedResource(resourceId)`.

---

### Fase 5: Configuración, despliegue y cableado final

1. **Actualizar `available_tools.properties`**:
   * **Ubicación:** `src/main/resources/.../var/config/available_tools.properties`.
   * Eliminar las líneas de `load_skill` y `list_skills` en `# Herramientas de Sistema`.
   * Añadir la sección de skills:
     ```properties
     # Herramientas de Skills
     Herramientas_de_skills_Listar_skills_disponibles=list_skills
     Herramientas_de_skills_Activar_skill=activate_skill
     Herramientas_de_skills_Desactivar_skill=deactivate_skill
     Herramientas_de_skills_Leer_recurso_de_skill=read_skill_resource
     Herramientas_de_skills_Ejecutar_script_de_skill=run_skill_script
     ```

2. **Cablear herramientas en `ReasoningServiceImpl.java`**:
   * En el método `getTools()`:
     - Eliminar `new ListSkillsTool(this.agent)` y `new LoadSkillTool(this.agent)`.
     - Añadir:
       ```java
       new ListSkillsTool(this.agent),
       new ActivateSkillTool(this.agent),
       new DeactivateSkillTool(this.agent),
       new ReadSkillResourceTool(this.agent),
       new RunSkillScriptTool(this.agent),
       ```
       