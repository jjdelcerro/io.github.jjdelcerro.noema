
# Ejecutor Asíncrono de Herramientas con Cola de Tareas y Consulta Diferida (v2)

## 1. Motivación 

Actualmente, todas las herramientas se ejecutan de forma síncrona dentro del bucle del orquestador (`ReasoningServiceImpl.eventDispatcher`). Esto bloquea la conversación mientras una herramienta se ejecuta (especialmente grave para tareas largas como compilaciones, descargas pesadas o comandos shell extensos). Se desea permitir que el LLM lance tareas de larga duración y continúe interactuando mientras estas se resuelven en segundo plano, notificando al agente cuando finalizan.


## 2. Visión general

Se mantiene la cola de tareas y el hilo consumidor. Pero los cambios clave son:

- **Resultado persistente en disco**: Cada tarea asíncrona escribe su salida (o error) en un archivo temporal cuyo nombre incluye el `task_id`. El `resource_id` correspondiente (p.ej., `tmp://task_<taskId>.out`) se genera en el momento de la solicitud y se devuelve al LLM en la respuesta inmediata.
- **Notificación ligera**: Cuando la tarea termina, el hilo consumidor envía un evento al `SensorsService` con el `task_id` y el `resource_id` (y opcionalmente el código de salida), pero **sin el contenido**.
- **Herramienta `get_task_result`**: Permite al LLM consultar el resultado de una tarea completada. Hereda de `AbstractPaginatedAgentTool` para soportar paginación automática de salidas grandes.
- **Monitorización de consultas**: El orquestador, en `prepareContextForLLM`, escanea los mensajes recientes para detectar si, tras una notificación, el LLM ha llamado a `get_task_result` para ese `task_id`. Si no, inyecta un recordatorio efímero.

## 3. Componentes

### 3.1. Marcado de herramientas asíncronas

En `AgentTool`:

```java
default boolean isAsync() { return false; }
default boolean isAsyncCapable() { return false; } // para herramientas que pueden ejecutarse de ambas formas
```

Las herramientas asíncronas (p.ej., `ShellExecuteTool`) sobrescriben `isAsync()` devolviendo `true`. Opcionalmente se puede añadir un flag en configuración global para habilitar/deshabilitar el modo asíncrono.

### 3.2. Clase `AsyncTask`

```java
record AsyncTask(String taskId, String toolName, String arguments, String resourceId, Instant enqueuedAt) {}
```

`resourceId` se genera en el momento de encolar (basado en `taskId`). De esta forma el LLM ya conoce el identificador del recurso donde se almacenará el resultado.

### 3.3. Generación de `taskId` y `resourceId`

```java
String taskId = "task_" + UUID.randomUUID().toString().replace("-", "");
String resourceId = "tmp://" + taskId + ".out";
```

### 3.4. Nueva herramienta: `GetTaskResultTool`

Ubicación: `io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.task.GetTaskResultTool`

- Hereda de `AbstractPaginatedAgentTool`.
- Nombre: `get_task_result`.
- Parámetro obligatorio: `task_id` (string).
- Funcionamiento:
  1. A partir del `task_id`, construye la ruta del archivo de resultados (usando la misma lógica de `resourceId`).
  2. Verifica si el archivo existe. Si no, devuelve un mensaje de error (tarea no encontrada o aún no completada).
  3. Obtiene el `resource_id` real del archivo (vía `getIdFromPath`).
  4. Llama a `servePaginatedResource(resource_id)` para devolver el contenido paginado.
- Adicionalmente, podría leer un archivo de metadatos (p.ej., `task_<taskId>.meta`) para devolver el código de salida o si hubo error.

### 3.5. Modificación de las herramientas asíncronas (ejemplo `ShellExecuteTool`)

La herramienta debe ser capaz de ejecutarse en dos modos: síncrono (bloqueante, como ahora) y asíncrono (lanzamiento en segundo plano). Para no duplicar código, se puede refactorizar:

- Método privado `doExecute(String command, boolean async)` que contenga la lógica común.
- En modo síncrono, ejecuta el proceso, espera y devuelve el resultado directamente.
- En modo asíncrono:
  - Crea el archivo de salida vacío (o un marcador) para reservar el `resourceId`.
  - Lanza el proceso en un hilo separado (o lo devuelve para que el consumidor lo ejecute).
  - Devuelve inmediatamente un JSON con `{"status":"async_started","task_id":"...","resource_id":"..."}`.

Alternativa más simple: crear una nueva clase `ShellExecuteAsyncTool` que herede de `ShellExecuteTool` y sobrescriba `isAsync()` y `execute()` para comportarse de forma asíncrona, reutilizando la lógica de ejecución real mediante un método protegido.

Para minimizar cambios, se puede optar por que el **hilo consumidor** ejecute el método `execute` completo de la herramienta (que será bloqueante) y luego escriba el resultado en el archivo correspondiente. En ese caso, la herramienta asíncrona debe tener un constructor o método que permita inyectar el `resourceId` para saber dónde guardar la salida. Esto implica modificar la interfaz `AgentTool` para añadir un método `executeAsync(String arguments, String resourceId, String taskId)` que devuelva inmediatamente un `CompletableFuture` o simplemente lanzar un hilo. Dado que la complejidad aumenta, se propone la primera aproximación: **el hilo consumidor ejecuta `tool.execute(arguments)` normalmente y luego escribe el resultado en el archivo asociado al `taskId`**. Para ello, la herramienta debe ser capaz de escribir su salida en un archivo en lugar de devolver un string. Eso es un cambio mayor.

**Conclusión transicional:** Para una primera implementación sencilla, asumimos que el resultado de la herramienta asíncrona no es enorme y que podemos almacenarlo en el evento (como en la v1). Más adelante, si se necesita, se refactoriza para usar archivos. Pero dado que la idea de consulta diferida es precisamente para manejar resultados grandes, conviene hacerlo bien desde el principio. Por tanto, se propone modificar las herramientas asíncronas para que **siempre escriban su salida en un archivo** y devuelvan un `resource_id`. Esto ya lo hacen `ShellExecuteTool` y `WebGetTikaTool`, por lo que la adaptación es natural: en modo asíncrono, simplemente no se espera la finalización, se lanza el proceso y se devuelve el `resource_id` (aunque el archivo aún no esté completo). Luego el hilo consumidor espera a que termine y notifica.

### 3.6. Cola y bucle consumidor (actualizado)

El `AsyncTask` ahora contiene también el `resourceId`. El bucle:

```java
while (running) {
    AsyncTask task = queue.take();
    AgentTool tool = getAvailableTool(task.toolName());
    if (tool == null) {
        sendErrorEvent(task.taskId(), "Tool not found");
        continue;
    }
    try {
        // Aquí asumimos que tool.execute es bloqueante y devuelve el resultado (string)
        String result = tool.execute(task.arguments());
        // Escribir result en el archivo asociado a task.resourceId (si la herramienta no lo ha hecho ya)
        Path resultPath = getPathFromId(task.resourceId());
        Files.writeString(resultPath, result, StandardCharsets.UTF_8);
        sendCompletionEvent(task.taskId(), task.resourceId(), 0);
    } catch (Exception e) {
        sendErrorEvent(task.taskId(), e.getMessage());
    }
}
```

Pero esto obliga a que el `execute` de la herramienta devuelva el resultado completo en memoria, lo que puede ser un problema para resultados grandes. Para evitarlo, se puede modificar la herramienta para que acepte un `OutputStream` o un `Path` donde escribir, y que el `execute` no devuelva nada, sino que escriba directamente en ese destino. Esto requiere cambios más profundos en la interfaz `AgentTool`.

**Simplificación pragmática:** Dado que el objetivo es una prueba de concepto, se puede mantener el modelo de la v1 (resultado dentro del evento) para herramientas pequeñas, y para herramientas grandes como `shell_execute` ya se usa un archivo temporal. La notificación diferida se implementará solo para aquellas herramientas que ya generan archivos (es decir, las que heredan de `AbstractPaginatedAgentTool`). De esta forma, la consulta con `get_task_result` funcionará sin tener que reescribir la herramienta.

### 3.7. Notificación de finalización (evento ligero)

El hilo consumidor envía:

```java
agent.putEvent("TASK_STATUS", "COMPLETED", PRIORITY_NORMAL,
    "{\"task_id\":\"" + task.taskId() + "\", \"resource_id\":\"" + task.resourceId() + "\", \"exit_code\":0}"
);
```

En caso de error, el `status` puede ser `FAILED` y se incluye un campo `error`.

### 3.8. Herramienta `get_task_result`

Implementación:

```java
public class GetTaskResultTool extends AbstractPaginatedAgentTool {
    public static final String TOOL_NAME = "get_task_result";

    public GetTaskResultTool(Agent agent) { super(agent); }

    @Override
    public ToolSpecificationBuilder getSpecification() {
        return ToolSpecificationBuilder.create()
            .name(TOOL_NAME)
            .description("Recupera el resultado de una tarea asíncrona previamente lanzada. "
                + "Recibe el task_id devuelto en la respuesta async_started. "
                + "El resultado se devuelve paginado igual que file_read.")
            .addStringParameter("task_id", "Identificador de la tarea.");
    }

    @Override
    public String execute(String jsonArguments) {
        try {
            Map<String, String> args = gson.fromJson(jsonArguments, Map.class);
            String taskId = args.get("task_id");
            if (taskId == null) return error("Falta task_id");
            String resourceId = "tmp://" + taskId + ".out";
            Path filePath = getPathFromId(resourceId);
            if (filePath == null || !Files.exists(filePath)) {
                return error("Tarea no encontrada o aún no completada: " + taskId);
            }
            return servePaginatedResource(resourceId);
        } catch (Exception e) {
            return error(e.getMessage());
        }
    }
}
```

### 3.9. Monitorización de consultas en `prepareContextForLLM`

Se escanea la lista de mensajes (la misma que se usa para la poda) y se buscan:

- Eventos de `TASK_STATUS` inyectados (son `ToolExecutionResultMessage` con `toolName="pool_event"` cuyo contenido incluye `task_id` y `resource_id`).
- Llamadas a `get_task_result` (cuyos argumentos contienen `task_id`).

Si para un `task_id` hay una notificación de finalización pero no hay ninguna llamada a `get_task_result` en los mensajes más recientes (por ejemplo, en los últimos `keep` mensajes), entonces se inyecta un recordatorio (similar al de las anotaciones) sugiriendo al LLM que consulte el resultado.

## 4. Configuración

Se añade en `settings.json`:

```json
"tools": {
  "async_execution_enabled": false,    // global
  "async_tools": ["shell_execute"]     // lista de herramientas que pueden ejecutarse asíncronamente
}
```

También se puede permitir por herramienta mediante `isAsync()`.

## 5. Flujo completo (ejemplo)

1. Usuario: "Compila el proyecto (lleva varios minutos)".
2. LLM: llama a `shell_execute(command="make", async=true)`.
3. Orquestador: genera `task_id=task_123`, `resource_id=tmp://task_123.out`, encola la tarea y responde al LLM con `{"status":"async_started","task_id":"task_123","resource_id":"tmp://task_123.out"}`.
4. LLM: puede responder al usuario "He iniciado la compilación en segundo plano. Te avisaré cuando termine."
5. (Opcional) Usuario: "Mientras, ¿qué tiempo hace?" → diálogo normal.
6. Cuando la compilación termina, el hilo consumidor envía evento `TASK_STATUS` con `task_id=task_123`, `resource_id=tmp://task_123.out`.
7. El orquestador procesa el evento como un sensor: inyecta un mensaje de sistema que dice: "La tarea task_123 ha finalizado. Usa 'get_task_result' con task_id='task_123' para ver el resultado."
8. LLM: llama a `get_task_result(task_id="task_123")`.
9. La herramienta devuelve el contenido del archivo paginado. El LLM procesa el resultado y responde al usuario.

Si el LLM olvida consultar, el orquestador puede inyectar un recordatorio en el siguiente turno.

## 6. Implementación por fases

**Fase 1 (prototipo básico):**
- Implementar `GetTaskResultTool`.
- Modificar `ShellExecuteTool` para que, cuando se ejecute en modo asíncrono, no espere al proceso (esto requiere reescribir parte de su lógica). Alternativa: crear `ShellExecuteAsyncTool` que herede y sobreescriba `execute` para devolver inmediatamente un `task_id`, y que el hilo consumidor ejecute un método `runCommand` que escriba en el archivo correspondiente.
- Integrar la cola y el hilo consumidor en `ReasoningServiceImpl`.
- Añadir la monitorización de consultas básica (solo sugerencia si no se ha llamado a `get_task_result` en los últimos mensajes).

**Fase 2 (resultados grandes y paginación):**
- Asegurar que el archivo de resultado se crea con el `resource_id` correcto y que `get_task_result` lo sirve paginado.
- Limpieza automática de archivos antiguos.

**Fase 3 (mejoras):**
- Herramienta `task_status` para consultar el estado de una tarea sin descargar el resultado completo.
- Persistencia de la cola para recuperar tareas tras reinicio.
- Cancelación de tareas.

## 7. Consideraciones finales

- El LLM debe ser instruido en el system prompt sobre el uso de `get_task_result` y la naturaleza de las tareas asíncronas.
- Se recomienda desactivar la asincronía por defecto y activarla solo para usuarios avanzados.
- La monitorización de consultas evita que el LLM "olvide" recoger los resultados, mejorando la robustez.

Este diseño combina lo mejor de la asincronía con la flexibilidad de la consulta diferida, minimizando la carga en el LLM y en el sistema de sensores.
