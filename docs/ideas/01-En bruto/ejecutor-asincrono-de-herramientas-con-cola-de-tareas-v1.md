
# Ejecutor Asíncrono de Herramientas con Cola de Tareas

## 1. Motivación

Actualmente, todas las herramientas se ejecutan de forma síncrona dentro del bucle del orquestador (`ReasoningServiceImpl.eventDispatcher`). Esto bloquea la conversación mientras una herramienta se ejecuta (especialmente grave para tareas largas como compilaciones, descargas pesadas o comandos shell extensos). Se desea permitir que el LLM lance tareas de larga duración y continúe interactuando mientras estas se resuelven en segundo plano, notificando al agente cuando finalizan.

## 2. Visión general

Se introduce un **ejecutor de tareas asíncrono** gestionado por una cola (`BlockingQueue`) y un **hilo consumidor único** (o un `ExecutorService` con un pool fijo, pero inicialmente un solo hilo para mantener simplicidad y orden). El orquestador, al detectar una herramienta marcada como asíncrona (`isAsync() == true`), en lugar de ejecutarla directamente:

- Genera un identificador único de tarea (`taskId`).
- Encapsula la solicitud (nombre de la herramienta + argumentos JSON + `taskId`) en un objeto `AsyncTask` y lo encola.
- Responde al LLM inmediatamente con un `ToolExecutionResultMessage` que indica `status: "async_started"` y el `taskId`.

Por otro lado, el hilo consumidor (bucle infinito) extrae tareas de la cola, ejecuta la herramienta correspondiente (`tool.execute(arguments)`) y, al terminar (tanto éxito como error), envía un evento al sistema de sensores (`SensorsService.putEvent`) con el resultado. El orquestador procesará ese evento en su ciclo normal, inyectando la notificación en el contexto en un turno futuro.

## 3. Componentes

### 3.1. Interfaz `AgentTool`
Se añade un método por defecto:

```java
default boolean isAsync() { return false; }
```

Las herramientas que soporten ejecución asíncrona (por ejemplo, `ShellExecuteTool`) sobrescribirán este método devolviendo `true`.

### 3.2. Clase `AsyncTask` (registro interno)
Objeto que se encola:

```java
record AsyncTask(String taskId, String toolName, String arguments, Instant enqueuedAt) {}
```

### 3.3. `ReasoningServiceImpl`
- Añade una `BlockingQueue<AsyncTask>` (por ejemplo, `LinkedBlockingQueue`).
- Añade un `Thread` (o `ExecutorService`) que ejecuta el bucle consumidor.
- Añade un método `submitAsyncTask(AsyncTask task)` que encola y devuelve inmediatamente.

**Inicialización:** en `start()` se crea y arranca el hilo consumidor. En `stop()` se interrumpe el hilo y se espera su finalización.

### 3.4. Bucle consumidor
Pseudocódigo:

```java
while (running) {
    try {
        AsyncTask task = queue.take(); // bloqueante
        AgentTool tool = getAvailableTool(task.toolName());
        if (tool == null) {
            sendErrorEvent(task.taskId(), "Tool not found");
            continue;
        }
        String result = tool.execute(task.arguments());
        // Enviar evento de éxito
        agent.putEvent("TASK_STATUS", "COMPLETED", PRIORITY_NORMAL,
            formatResultEvent(task.taskId(), result));
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
    } catch (Exception e) {
        sendErrorEvent(task.taskId(), e.getMessage());
    }
}
```

El método `sendErrorEvent` internamente llama a `agent.putEvent` con un JSON que incluye `taskId` y `error`.

### 3.5. Integración en `eventDispatcher`
Al procesar `ToolExecutionRequest`:

```java
AvailableAgentTool available = availableTools.get(request.name());
if (available != null && available.tool.isAsync()) {
    String taskId = generateTaskId();
    asyncQueue.add(new AsyncTask(taskId, request.name(), request.arguments(), Instant.now()));
    // Respuesta inmediata
    ToolExecutionResultMessage immediate = ToolExecutionResultMessage.from(
        request, "{\"status\":\"async_started\",\"task_id\":\"" + taskId + "\"}"
    );
    session.add(immediate);
    // Se guarda el turno correspondiente en SourceOfTruth (opcional, pero recomendado)
    sourceOfTruth.add(createTurnFromToolResult(immediate));
    // No se añade ningún otro mensaje; el LLM continúa
} else {
    // ejecución síncrona como siempre
}
```

## 4. Notificación de finalización

El evento que se envía al final tiene el siguiente formato JSON (dentro del campo `contents` de `SensorEvent`):

```json
{
  "task_id": "task_12345",
  "status": "success",
  "result": "...",        // texto devuelto por la herramienta (puede ser grande)
  "error": null
}
```

O en caso de error:

```json
{
  "task_id": "task_12345",
  "status": "error",
  "error": "mensaje de excepción"
}
```

El `SensorsService` recibe estos eventos en el canal `TASK_STATUS`. El orquestador, al procesar el evento (como cualquier otro sensor), lo convertirá en un mensaje de sistema (simulando `pool_event`) y lo inyectará en el contexto en el siguiente turno. El LLM podrá entonces ver el resultado y actuar en consecuencia.

## 5. Orden y concurrencia

- La cola FIFO garantiza que las tareas se ejecuten en el mismo orden en que fueron solicitadas por el LLM (importante para consistencia si las tareas tienen dependencias implícitas).  
- Si se desea paralelismo, se podría reemplazar el único hilo por un `ExecutorService` con un número fijo de hilos (ej. 2 o 3). Sin embargo, el orden de finalización podría no respetar el orden de solicitud, pero eso no es crítico porque cada tarea lleva su `taskId` y el LLM correlacionará por ID.  
- El hilo consumidor no debe bloquearse por operaciones de E/S largas dentro de `tool.execute` (eso ya es lo que se pretende, ejecutarlas en segundo plano). El propio `execute` puede ser bloqueante, pero al estar fuera del hilo del orquestador, no afecta a la conversación.

## 6. Interfaz de usuario y gestión de cola

Opcionalmente, se puede exponer al usuario (a través del GUI o de la consola) el estado de la cola: número de tareas pendientes, tiempo estimado, etc. Para ello se podría añadir una herramienta de consulta `task_queue_status` que devuelva información de la cola. También se podría permitir cancelar tareas pendientes (aunque las ya iniciadas serían más complejas de cancelar).

## 7. Persistencia y recuperación ante reinicio

En una primera versión, las tareas en cola se perderán si el agente se reinicia. Para un uso real se podría persistir la cola en la base de datos de servicios (H2) y restaurarla al arrancar, pero eso añade complejidad. Se puede dejar como mejora futura.

## 8. Limitaciones y observaciones

- **Herramientas asíncronas deben ser idempotentes o no esperar resultado inmediato.** El LLM debe ser consciente de que obtendrá la respuesta más tarde mediante un evento.
- **El resultado de la herramienta puede ser muy grande.** El evento se almacena en la cola de sensores y puede persistirse en `sensors.json`. Conviene que las herramientas ya generen un `resource_id` apuntando a un archivo temporal (como hace `ShellExecuteTool`) para no saturar la memoria/almacenamiento. En ese caso, el evento contendría ese `resource_id` en lugar del texto completo.
- **Pruebas:** Se debe verificar que el hilo consumidor no comparta estado mutable con el orquestador (más allá de la cola). Todas las llamadas a `agent.putEvent` son seguras.

## 9. Pasos de implementación sugeridos

1. Añadir método `isAsync()` en `AgentTool` y marcar `ShellExecuteTool` como asíncrono.
2. En `ReasoningServiceImpl`, declarar `BlockingQueue<AsyncTask>` y el hilo consumidor.
3. Implementar la lógica de encolado y respuesta inmediata dentro de `eventDispatcher`.
4. Implementar el bucle consumidor que ejecuta las tareas y envía eventos.
5. Probar con un comando largo (ej. `sleep 10; echo "hecho"`) y verificar que el LLM puede seguir conversando mientras se ejecuta y recibe la notificación posterior.
6. Extender a otras herramientas (p.ej., `web_get_content` para descargas grandes) si se desea.

