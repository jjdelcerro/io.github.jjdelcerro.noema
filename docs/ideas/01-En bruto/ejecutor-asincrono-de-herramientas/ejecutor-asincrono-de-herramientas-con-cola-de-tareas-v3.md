
# Ejecutor Asíncrono de Herramientas con Cola de Tareas y Consulta Diferida

## 1. Motivación

En la arquitectura actual de Noema, todas las herramientas se ejecutan de forma síncrona dentro del bucle del orquestador. Esto provoca que tareas de larga duración (compilaciones, descargas pesadas, comandos shell extensos) bloqueen la conversación, impidiendo que el usuario o el propio agente interactúen mientras se completan. Se desea permitir que el LLM lance tareas en segundo plano, continúe la conversación y reciba una notificación ligera al finalizar, pudiendo consultar el resultado detallado más tarde. Además, se busca evitar saturar el sistema de sensores con resultados grandes y permitir al orquestador monitorizar si el LLM realmente ha recuperado el resultado, pudiendo recordarlo si es necesario.

## 2. Visión general

Se introduce un **ejecutor de tareas asíncrono** basado en una cola (`BlockingQueue`) y un **hilo consumidor único** (o un pool pequeño). Cuando el LLM solicita una herramienta marcada como asíncrona, el orquestador no la ejecuta directamente, sino que:

1. Genera un identificador único de tarea (`taskId`) y un `resourceId` asociado (apuntando a un archivo temporal donde se almacenará el resultado).
2. Encola la solicitud en un objeto `AsyncTask`.
3. Responde al LLM inmediatamente con un mensaje de tipo `ToolExecutionResultMessage` que indica `status: "async_started"` y proporciona el `taskId` y el `resourceId`.
4. El LLM puede continuar la conversación normalmente.

Por otro lado, el hilo consumidor extrae tareas de la cola, ejecuta la herramienta correspondiente (de forma bloqueante) y, al terminar:

- Escribe el resultado (salida estándar, error, etc.) en el archivo temporal apuntado por `resourceId`.
- Envía un **evento ligero** al `SensorsService` (canal `TASK_STATUS`) con el `taskId`, el `resourceId` y el código de salida (o error).
- El orquestador, en su bucle normal, recibe este evento como cualquier otro sensor y lo inyecta en el contexto de la conversación como un mensaje de sistema (simulando `pool_event`), indicando al LLM que la tarea ha finalizado y que puede consultar su resultado mediante la herramienta `get_task_result`.

La herramienta `get_task_result` hereda de `AbstractPaginatedAgentTool`, por lo que soporta paginación automática de resultados grandes. Además, el orquestador puede monitorizar si el LLM realmente ha llamado a `get_task_result` tras una notificación y, si no, inyectar recordatorios periódicos.

## 3. Componentes y sus responsabilidades

### 3.1. Marcado de herramientas asíncronas

En la interfaz `AgentTool` se añade un método por defecto:

```java
default boolean isAsync() { return false; }
```

Las herramientas que puedan ejecutarse de forma asíncrona (por ejemplo, `ShellExecuteTool`) sobrescribirán este método devolviendo `true`. Para mayor control, se puede incluir una configuración global que permita habilitar/deshabilitar el modo asíncrono (ver sección 6).

### 3.2. Clase `AsyncTask`

Registro interno que se encola:

```java
record AsyncTask(String taskId, String toolName, String arguments, String resourceId, Instant enqueuedAt) {}
```

- `taskId`: identificador único, generado como `"task_" + UUID.randomUUID().toString().replace("-", "")`.
- `resourceId`: identificador del recurso donde se guardará el resultado, por ejemplo `"tmp://" + taskId + ".out"`. Se genera en el momento de encolar para que el LLM lo conozca desde el principio.

### 3.3. Generación de `taskId` y `resourceId`

El orquestador, al detectar una solicitud de herramienta asíncrona, ejecuta:

```java
String taskId = "task_" + UUID.randomUUID().toString().replace("-", "");
String resourceId = "tmp://" + taskId + ".out";
```

### 3.4. Herramienta `get_task_result`

**Ubicación:** `io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.task.GetTaskResultTool`

**Especificación:**
- Nombre: `get_task_result`
- Hereda de `AbstractPaginatedAgentTool`
- Parámetro obligatorio: `task_id` (string)

**Funcionamiento:**
1. A partir del `task_id`, reconstruye la ruta del archivo de resultados usando el mismo patrón que se usó para generar el `resourceId`.
2. Obtiene la ruta física mediante `getPathFromId(resourceId)`.
3. Si el archivo no existe, devuelve un mensaje de error (`"Tarea no encontrada o aún no completada"`).
4. Si existe, obtiene el `resourceId` real (aunque ya lo tiene, puede usarlo directamente) y llama a `servePaginatedResource(resourceId)` para devolver el contenido paginado (cabecera + contenido).

**Código base:**

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
            if (taskId == null) return formatErrorResponse("Falta task_id");
            String resourceId = "tmp://" + taskId + ".out";
            Path filePath = getPathFromId(resourceId);
            if (filePath == null || !Files.exists(filePath)) {
                return formatErrorResponse("Tarea no encontrada o aún no completada: " + taskId);
            }
            return servePaginatedResource(resourceId);
        } catch (Exception e) {
            return formatErrorResponse(e.getMessage());
        }
    }
}
```

### 3.5. Modificación de herramientas asíncronas (ejemplo `ShellExecuteTool`)

Para que una herramienta existente pueda ejecutarse en modo asíncrono, se necesita adaptarla de forma que:

- Cuando se invoque normalmente (síncrono), se comporte como hasta ahora.
- Cuando se invoque con `async=true` (o a través de un método especial), devuelva inmediatamente el `taskId` y el `resourceId`, y delegue la ejecución real al hilo consumidor.

Para no duplicar código, se puede refactorizar la herramienta extrayendo la lógica de ejecución real a un método que reciba un `Path` donde escribir la salida. La versión asíncrona creará ese `Path` (basado en el `resourceId`) y lanzará un hilo (o devolverá un `CompletableFuture`) para que el hilo consumidor espere a que termine. Dado que ya disponemos de un hilo consumidor, la opción más limpia es que la herramienta asíncrona no ejecute nada, sino que se encargue de **reservar el archivo de salida** y devolver inmediatamente el `taskId` y `resourceId`. La ejecución real será responsabilidad del bucle consumidor, que llamará a un método `executeBlocking` de la herramienta (pasándole el `Path` de destino). Esto requiere añadir un nuevo método en `AgentTool`:

```java
default void executeBlocking(String arguments, Path outputPath) throws Exception {
    // Por defecto, ejecuta el método normal y escribe el resultado en outputPath
    String result = execute(arguments);
    Files.writeString(outputPath, result, StandardCharsets.UTF_8);
}
```

Las herramientas asíncronas pueden sobrescribir este método para una escritura más eficiente (por ejemplo, `ShellExecuteTool` podría redirigir la salida del proceso directamente al archivo, sin almacenar en memoria). De esta forma, el hilo consumidor hará:

```java
Path outputPath = getPathFromId(task.resourceId());
tool.executeBlocking(task.arguments(), outputPath);
sendCompletionEvent(...);
```

**Ventaja:** No se modifica el contrato de `execute`, se añade un nuevo método opcional. Las herramientas existentes seguirán funcionando síncronamente.

**Implementación concreta en `ShellExecuteTool`:** Se puede reutilizar la lógica actual que ya escribe la salida en un archivo temporal. Bastaría con hacer que `executeBlocking` acepte el `Path` de destino y redirija allí la salida del proceso (en lugar de usar un nombre aleatorio). Esto implica modificar ligeramente `ShellExecuteTool` para que pueda recibir un `Path` externo.

### 3.6. Cola y bucle consumidor en `ReasoningServiceImpl`

**Añadir en `ReasoningServiceImpl`:**

```java
private final BlockingQueue<AsyncTask> asyncTaskQueue = new LinkedBlockingQueue<>();
private Thread asyncWorkerThread;
```

**Inicialización (en `start()`):**

```java
if (asyncExecutionEnabled()) { // según configuración
    asyncWorkerThread = Thread.ofPlatform().start(() -> {
        while (running) {
            try {
                AsyncTask task = asyncTaskQueue.take();
                AgentTool tool = getAvailableTool(task.toolName());
                if (tool == null) {
                    sendErrorEvent(task.taskId(), "Tool not found");
                    continue;
                }
                Path outputPath = getPathFromId(task.resourceId());
                if (outputPath == null) {
                    sendErrorEvent(task.taskId(), "Invalid resourceId");
                    continue;
                }
                Files.createDirectories(outputPath.getParent());
                tool.executeBlocking(task.arguments(), outputPath);
                sendCompletionEvent(task.taskId(), task.resourceId(), 0);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                sendErrorEvent(task.taskId(), e.getMessage());
            }
        }
    });
}
```

**Envío de eventos auxiliares:**

```java
private void sendCompletionEvent(String taskId, String resourceId, int exitCode) {
    String body = String.format("{\"task_id\":\"%s\",\"resource_id\":\"%s\",\"exit_code\":%d}",
        taskId, resourceId, exitCode);
    agent.putEvent("TASK_STATUS", "COMPLETED", PRIORITY_NORMAL, body);
}

private void sendErrorEvent(String taskId, String errorMsg) {
    String body = String.format("{\"task_id\":\"%s\",\"error\":\"%s\"}", taskId, errorMsg);
    agent.putEvent("TASK_STATUS", "FAILED", PRIORITY_NORMAL, body);
}
```

**Detención (en `stop()`):**

```java
if (asyncWorkerThread != null) {
    asyncWorkerThread.interrupt();
    asyncWorkerThread.join(5000);
}
```

### 3.7. Integración en `eventDispatcher` (lanzamiento asíncrono)

Dentro del bucle de procesamiento de `aiMessage.toolExecutionRequests()`:

```java
AvailableAgentTool available = availableTools.get(request.name());
if (available != null && available.tool.isAsync() && asyncExecutionEnabled()) {
    String taskId = "task_" + UUID.randomUUID().toString().replace("-", "");
    String resourceId = "tmp://" + taskId + ".out";
    asyncTaskQueue.add(new AsyncTask(taskId, request.name(), request.arguments(), resourceId, Instant.now()));
    
    // Respuesta inmediata al LLM
    String immediateResult = String.format("{\"status\":\"async_started\",\"task_id\":\"%s\",\"resource_id\":\"%s\"}",
        taskId, resourceId);
    ToolExecutionResultMessage immediateResponse = ToolExecutionResultMessage.from(request, immediateResult);
    session.add(immediateResponse);
    sourceOfTruth.add(createTurnFromToolResult(immediateResponse));
    // No se añade ningún otro mensaje; el LLM continúa
} else {
    // Ejecución síncrona normal
    String result = executeTool(request);
    // ... resto del código actual
}
```

### 3.8. Notificación de finalización (evento ligero)

El evento enviado por el hilo consumidor es consumido por el `SensorsService`. El orquestador, en su ciclo normal, recogerá ese evento (como cualquier otro) y lo inyectará en el contexto mediante el mecanismo de `pool_event`. Para que el LLM lo entienda, el evento debe ser transformado en un `ToolExecutionResultMessage` con `toolName="pool_event"` y un contenido JSON que incluya `task_id` y `resource_id`. La fábrica de eventos (ya existente en `SensorsServiceImpl`) se encarga de ello. El LLM recibirá un mensaje como:

```
Notificación del sistema: La tarea task_123456 ha finalizado. Puede consultar su resultado usando la herramienta 'get_task_result' con task_id='task_123456'.
```

Este texto se genera en el método `toJson()` del evento. Se debe modificar ligeramente el `SensorEvent` correspondiente para que, en el canal `TASK_STATUS`, produzca un mensaje claro.

### 3.9. Monitorización de consultas en `prepareContextForLLM`

Para evitar que el LLM ignore la notificación y nunca recupere el resultado, el orquestador puede, en cada turno, examinar los mensajes recientes (los mismos que se usan para la poda) y detectar si hay notificaciones de tareas completadas sin la correspondiente llamada a `get_task_result`. En caso afirmativo, inyecta un recordatorio (similar a las sugerencias de anotación).

**Algoritmo:**

- Escanear los últimos `getNumberOfMessagesToKeep()` mensajes (o todos los de la sesión, pero limitando por eficiencia).
- Buscar mensajes de tipo `ToolExecutionResultMessage` con `toolName="pool_event"` que contengan en su texto un `task_id` y `status="COMPLETED"`.
- Para cada `task_id` encontrado, comprobar si en los mismos mensajes (o en los más recientes) existe una llamada a `get_task_result` con ese `task_id` (buscando en `ToolExecutionRequest` o en el texto de `AiMessage`).
- Si no se encuentra ninguna llamada, añadir un `SystemMessage` de recordatorio (efímero) al contexto.

**Implementación en `prepareContextForLLM`:** Después de la poda, se puede realizar este análisis y añadir el mensaje al final del `context`. Se reutiliza el mismo método `getResourcesPendingAnnotation` pero adaptado para tareas; mejor crear un método específico `getTasksPendingResult(List<ChatMessage> recentMessages)`.

## 4. Flujo completo (ejemplo)

1. **Usuario:** "Compila el proyecto (lleva varios minutos)".
2. **LLM:** Llama a `shell_execute(command="make", async=true)`.
3. **Orquestador:** Genera `taskId="task_abc123"`, `resourceId="tmp://task_abc123.out"`. Encola la tarea y responde al LLM con `{"status":"async_started","task_id":"task_abc123","resource_id":"tmp://task_abc123.out"}`.
4. **LLM:** Responde al usuario: "He iniciado la compilación en segundo plano. Te avisaré cuando termine."
5. **Usuario:** "Mientras, ¿cómo está el tiempo?" → El LLM responde usando herramientas de clima (conversación normal).
6. **Tarea en segundo plano:** La compilación termina. El hilo consumidor escribe la salida en `tmp://task_abc123.out` y envía un evento `TASK_STATUS` con `task_id` y `resource_id`.
7. **Orquestador:** Recibe el evento, lo transforma en un mensaje de sistema y lo inyecta en el contexto en el siguiente turno (cuando el LLM esté disponible). El mensaje dice: "La tarea task_abc123 ha finalizado. Usa 'get_task_result' con task_id='task_abc123' para ver el resultado."
8. **LLM:** (Opcional) Puede responder al usuario "La compilación ha terminado, ¿quieres que te muestre el resultado?" o directamente llamar a `get_task_result(task_id="task_abc123")`.
9. **Orquestador:** Ejecuta `get_task_result` (síncronamente) y devuelve el contenido paginado del archivo.
10. **LLM:** Procesa el resultado y responde al usuario.

Si el LLM no llama a `get_task_result` tras varios turnos, el orquestador puede recordárselo de nuevo.

## 5. Configuración

Se añade en `settings.json` (bajo una nueva sección `"tools"`):

```json
"tools": {
    "async_execution_enabled": false,
    "async_tools": ["shell_execute", "web_get_content"]
}
```

- `async_execution_enabled`: permite habilitar/deshabilitar globalmente el modo asíncrono (por defecto `false` para no alterar el comportamiento tradicional).
- `async_tools`: lista de nombres de herramientas que pueden ejecutarse asíncronamente. También se puede confiar en el método `isAsync()` de cada herramienta, pero este listado permite una capa adicional de control.

## 6. Implementación por fases

### Fase 1: Prototipo básico (válido para pruebas)

- Implementar `GetTaskResultTool`.
- Modificar `ShellExecuteTool` para que, cuando se ejecute asíncronamente, devuelva inmediatamente `taskId` y `resourceId`, y que el hilo consumidor ejecute el comando real escribiendo en el archivo. Esto requiere añadir `executeBlocking` en `AgentTool` y adaptar `ShellExecuteTool`.
- Integrar la cola y el hilo consumidor en `ReasoningServiceImpl`.
- Habilitar la funcionalidad solo si `async_execution_enabled` es `true`.
- Añadir monitorización simple: si hay notificación y no hay llamada a `get_task_result` en los últimos N mensajes, inyectar recordatorio (texto fijo).

### Fase 2: Soporte para resultados grandes y paginación

- Asegurar que `ShellExecuteTool.executeBlocking` escribe directamente en el `Path` proporcionado, sin pasar por memoria intermedia.
- Verificar que `get_task_result` sirve correctamente archivos grandes con paginación (ya hereda de `AbstractPaginatedAgentTool`).

### Fase 3: Mejoras avanzadas

- Herramienta `task_status` para consultar el estado de una tarea (pendiente, ejecutándose, completada, fallida) sin descargar el resultado completo.
- Persistencia de la cola en la base de datos de servicios (para recuperar tareas tras reinicio).
- Cancelación de tareas (`task_cancel`).
- Limpieza automática de archivos temporales de tareas antiguas (por ejemplo, borrar tras 24 horas o cuando se haya consultado).

## 7. Consideraciones finales

- **Instrucción al LLM:** Se debe actualizar el system prompt para explicar el funcionamiento de las herramientas asíncronas, el significado de `async_started`, y la necesidad de usar `get_task_result` tras recibir la notificación.
- **Compatibilidad hacia atrás:** Las herramientas que no sean asíncronas siguen funcionando igual. El modo asíncrono está desactivado por defecto.
- **Concurrencia:** La cola y el hilo consumidor son thread-safe. Las llamadas a `agent.putEvent` también lo son. No se requiere sincronización adicional.
- **Rendimiento:** El uso de un único hilo consumidor mantiene el orden de ejecución de las tareas (FIFO). Si se desea paralelismo, se puede reemplazar por un `ExecutorService` con varios hilos, pero se pierde el orden garantizado (no es crítico para la mayoría de casos).

Este diseño proporciona una base sólida para extender Noema con ejecución asíncrona de tareas, mejorando la experiencia de usuario y la eficiencia en operaciones largas.

<critica>

Con este documento de diseño, acabas de dotar a Noema de un **Sistema Operativo Multitarea**.

Lo que más me gusta de tu propuesta es que **no reinventas la rueda**. En lugar de crear un mecanismo de comunicación nuevo, haces *Dogfooding* (comer tu propia comida de perro): reutilizas el `SensorsService` y el `pool_event` para notificar al LLM, y reutilizas tu impecable `AbstractPaginatedAgentTool` para que el LLM lea el resultado. Esto es arquitectura de primer nivel.

Sin embargo, como me has pedido que no sea complaciente y que sea sincero, me voy a poner el sombrero de *Arquitecto del Diablo* y te voy a señalar **cuatro minas terrestres (edge cases)** que tienes en este diseño y que te van a explotar si no las controlas antes de picar el código.

### Las 4 Minas Terrestres del Diseño Asíncrono

#### 1. El momento exacto de la "Confirmación Humana" (El bloqueo de la UI)
Mencionas que las herramientas pasan a una cola `BlockingQueue`. Pero herramientas como `shell_execute` tienen `MODE_EXECUTION` y exigen que el usuario clique "Autorizar" en la interfaz. 
**¿Cuándo pides la confirmación?** 
*   Si la pides *dentro* del hilo consumidor (background), el usuario podría estar tomando un café, saltarle un popup de la nada 10 minutos después, y el hilo background se quedaría bloqueado.
*   **Solución obligatoria:** La llamada a `AgentConsole.confirm(...)` **debe** hacerse en el hilo principal (`eventDispatcher`), *antes* de encolar la tarea. Si el humano dice sí, se mete el `AsyncTask` a la cola. Si dice no, se devuelve el error sincrónicamente al LLM.

#### 2. Condiciones de carrera en el Sistema de Archivos (El LLM impaciente)
Imagina este escenario:
1. El LLM lanza `shell_execute(command="npm install && npm run build")` de forma asíncrona.
2. Recibe el `taskId: 123`.
3. El LLM, que es impaciente, decide en el siguiente turno usar `file_read` para leer el `package.json` o un archivo del `build/` que la tarea en background está modificando en ese mismo instante.
Como el LLM y el *Worker* ahora corren en paralelo, **tienes una condición de carrera sobre el disco**. El LLM puede leer archivos a medio escribir, alucinar sobre errores de sintaxis y romper el código.
*   **Solución:** A nivel del agente, es difícil bloquear archivos individuales. Lo mejor es que en el *System Prompt* añadas una regla de oro: *"Si lanzas una tarea de compilación o modificación masiva, NO interactúes con el sistema de ficheros hasta recibir la notificación de COMPLETED o FAILED"*.

#### 3. La Amnesia de Tareas Largas (El problema de la compactación)
Supongamos que el LLM lanza un `shell_execute` de un script de *Scraping* masivo que tarda **3 horas**.
Durante esas 3 horas, el usuario le pregunta a Noema por el clima, debaten sobre filosofía, se superan los 40 turnos y el sistema **compacta la memoria**.
Cuando a las 3 horas la tarea termina y el `pool_event` inyecta *"La tarea task_123456 ha finalizado"*, **el LLM se habrá olvidado de qué era la task_123456** porque el turno original donde la lanzó se compactó en "El Viaje".
*   **Solución:** El mensaje del sensor no puede ser solo *"La tarea 123 ha finalizado"*. Debe incluir contexto. El `Worker` debería enviar: *"La tarea task_123456 (Comando: 'python scraper.py') que lanzaste asíncronamente, ha finalizado"*. Así, incluso si el LLM no tiene el turno original en su ventana de contexto corta, sabrá de qué le estás hablando.

#### 4. La disonancia cognitiva del Esquema JSON (`async=true`)
En la sección 3.5 sugieres que el LLM pase `async=true` como parámetro. **Cuidado con esto**. Los LLMs son muy rígidos con los esquemas de las herramientas. Si el LLM cree que `shell_execute` devuelve la salida del comando, pero de repente al poner `async=true` le devuelves un JSON de `{"status": "async_started"}`, se va a confundir muchísimo porque no coincide con lo que él esperaba.
*   **Solución:** No dejes que el LLM elija si es síncrono o asíncrono pasándole un flag. **Fuérzalo tú en Java**. Herramientas como `shell_execute` o `document_index` deberían ser **siempre** asíncronas a ojos del modelo, y la descripción de la herramienta debe decir explícitamente: *"Esta herramienta SIEMPRE se ejecuta en segundo plano. Devuelve inmediatamente un task_id. Debes esperar a la notificación del sistema para leer el resultado"*.

---

### Lo que me parece espectacular de tu diseño

*   **El rastreador de "Vagos" (`getTasksPendingResult`):** Tu idea en la sección 3.9 es simplemente magistral. Ya descubriste que el LLM es un vago y se olvida de anotar cosas (tu solución del mensaje efímero de antes). Aplicar ese mismo patrón escaneando el historial para decirle *"Oye, tienes tareas terminadas que no has leído"* es el broche de oro para garantizar que el bucle se cierre.
*   **El `executeBlocking(arguments, outputPath)`:** Extraer el flujo de memoria intermedia para que el proceso del SO escriba *directamente* en el fichero temporal a través del `Path` que le pasas te va a ahorrar OOMs (Out Of Memory) masivos en la JVM cuando ejecutes comandos con megabytes de logs.

### Veredicto

**Hazlo. Es el camino correcto.**

Transforma por completo las capacidades del agente. Al añadir esto, Noema deja de ser un simple "chatbot glorificado" y se convierte en un auténtico **Proceso Demonio Inteligente**. Solo asegúrate de atar bien en corto las 4 minas que te he comentado (especialmente la de la confirmación humana y la información de la tarea en el evento de finalización) y la implementación será un éxito.
</critica>