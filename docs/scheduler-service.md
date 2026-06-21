## Servicio de Planificación (`SchedulerService`)

### 1. Introducción: la necesidad de planificación temporal

Uno de los rasgos distintivos de un agente autónomo es su capacidad para *actuar en el futuro*. Noema no solo reacciona a estímulos inmediatos; también puede programar recordatorios, alarmas o ejecuciones diferidas como respuesta a una instrucción del usuario: *“Avísame dentro de diez minutos”*, *“Recuérdame revisar el correo a las cinco”*, o incluso *“Ejecuta este script mañana a primera hora”*.

Para satisfacer esta necesidad, Noema incorpora `SchedulerService`, un componente ligero pero persistente que permite al agente registrar eventos temporales y garantizar que se disparen en el momento preciso, incluso si la aplicación se detiene y se reinicia entre la programación y el disparo. El servicio se apoya en una base de datos H2 embebida para almacenar las alarmas pendientes y en `SensorsService` para inyectar el aviso como un evento más dentro del flujo de percepción del agente.

El diseño busca el mínimo indispensable: no hay dependencias externas (como servicios de cron del sistema operativo), y la precisión es la suficiente para un asistente conversacional (del orden de segundos). La simplicidad es la clave.

### 2. Arquitectura general: componentes y flujo

El `SchedulerService` se compone de cuatro elementos fundamentales:

- **`SchedulerServiceImpl`**: la implementación concreta del servicio. Gestiona el ciclo de vida, la persistencia y el hilo de ejecución.
- **Tabla `SCHEDULER`** (H2): almacén de las alarmas pendientes. Cada fila contiene un identificador único (`id`), la marca de tiempo de creación (`timestamp`), el momento programado (`alarm_time`) y un texto descriptivo (`reason`).
- **`ScheduledExecutorService`**: un planificador de Java (un solo hilo) que ejecuta la tarea de disparo en el momento exacto.
- **`SensorsService`**: destino final de la alarma. Cuando se alcanza el tiempo, se invoca `agent.putEvent()` para inyectar un evento en el bus sensorial del agente.

El flujo típico es:
1. El LLM, mediante la herramienta `schedule_alarm`, solicita programar una alarma.
2. `SchedulerService` parsea la fecha, guarda la alarma en la base de datos y reprograma la próxima tarea pendiente.
3. Cuando llega el momento, el `ScheduledExecutorService` ejecuta el código que genera un evento y lo envía a `SensorsService`.
4. El agente, en su bucle de razonamiento, recibirá ese evento como un estímulo más y actuará en consecuencia (por ejemplo, enviando un mensaje al usuario).

Este diseño desacopla la planificación de la reacción: `SchedulerService` solo sabe cuándo y qué notificar, pero no cómo responder. La respuesta final queda delegada al `ReasoningService` y al modelo de lenguaje.

### 3. Persistencia: la tabla `SCHEDULER` y el contador de IDs

Las alarmas se almacenan en la base de datos H2 de servicios (la misma que usa el agente para otros fines, como el registro de documentos). La tabla se crea durante el arranque del servicio mediante la sentencia SQL:

```sql
CREATE TABLE IF NOT EXISTS SCHEDULER (
    id VARCHAR(255) PRIMARY KEY,
    timestamp TIMESTAMP,
    alarm_time TIMESTAMP,
    reason VARCHAR(1024)
);
```

Cada alarma recibe un identificador único con el formato `ALARM-<num>`, donde `<num>` es un entero autoincremental gestionado por la clase `Counter`. Este contador, al iniciarse, consulta el valor máximo de `id` en la tabla y arranca desde ahí. Por ejemplo, si ya existen alarmas con id `ALARM-1`, `ALARM-2`, el siguiente será `ALARM-3`.

La columna `alarm_time` almacena el momento exacto (con precisión de milisegundo) en que debe dispararse la alarma. La columna `reason` contiene una descripción textual (la razón que proporcionó el agente al programarla) y se incluirá en el evento sensorial cuando llegue el momento.

### 4. Ciclo de vida del servicio: inicio, parada y resincronización

El servicio se inicia junto con el agente, siempre que su fábrica (`SchedulerServiceFactory`) lo permita (actualmente siempre devuelve `true`, pues no requiere configuración externa). El método `start()` realiza las siguientes operaciones:

1. Crea un `ScheduledExecutorService` de un solo hilo (de plataforma, no virtual, por razones de estabilidad).
2. Registra un nuevo sensor en `SensorsService` con el nombre `SCHEDULER`, naturaleza `DISCRETE` (cada alarma se entrega como un evento atómico).
3. Conecta a la base de datos H2 de servicios y crea la tabla `SCHEDULER` si no existe.
4. Inicializa el contador de IDs (leyendo el máximo id actual).
5. Invoca `rescheduleNextAlarm()` para recuperar la alarma más próxima (si existe) y programar su ejecución.
6. Marca el servicio como `running`.

Cuando el agente se detiene, se invoca `stop()`: se cancela la tarea futura actual (`currentScheduledTask.cancel(false)`) y se pone el flag `running` a `false`. No se borran las alarmas pendientes, de modo que al reiniciar el agente se recuperarán automáticamente.

La resincronización al arranque es especialmente importante: si el agente estaba apagado durante el momento en que debía dispararse una alarma, `rescheduleNextAlarm()` seleccionará la siguiente alarma futura. Las que ya vencieron mientras el agente no estaba activo no se ejecutarán (se consideran perdidas). Para evitar esta pérdida, se podría modificar el servicio para que, al arrancar, ejecute inmediatamente todas las alarmas con `alarm_time` anterior a `now`, pero el diseño actual asume que el agente no permanece detenido mucho tiempo o que el usuario prefiere no recibir notificaciones atrasadas.

### 5. Programación de una alarma: la herramienta `schedule_alarm`

El LLM accede a la planificación a través de la herramienta `schedule_alarm`. Su especificación incluye dos parámetros:

- `reason` (texto obligatorio): la descripción de la alarma (ej: "Revisar correo").
- `when` (texto obligatorio): descripción temporal en **inglés** (ej: "tomorrow at 5pm", "in 10 minutes").

**¿Por qué inglés?** El parser de fechas utilizado es Natty, una librería Java que entiende expresiones flexibles pero solo de forma fiable en inglés. Por tanto, se pide al modelo que, si recibe una instrucción en español, traduzca la expresión temporal al inglés antes de invocar la herramienta. Esto es una limitación asumida; en el futuro podría reemplazarse por un parser más polivalente.

La implementación de la herramienta (`ScheduleAlarmTool.execute()`) realiza los siguientes pasos:

1. Extrae los argumentos JSON.
2. Invoca `dateParser.parse(when)` para obtener una lista de fechas candidatas.
3. Toma la primera fecha detectada y la convierte a `LocalDateTime` (zona horaria del sistema).
4. Llama a `SchedulerService.schedule(alarmLDT, reason)`.
5. Devuelve una respuesta JSON confirmando la programación e incluyendo el `id` generado y la hora exacta interpretada.

Ejemplo de respuesta exitosa:

```json
{
  "status": "scheduled",
  "id": "ALARM-42",
  "reason": "Revisar correo",
  "alarm_time": "2025-05-20T17:00:00",
  "note": "Alarma programada en el sistema."
}
```

Si el parseo falla (ej: "en algún momento"), se devuelve un error y el agente debe pedir al usuario que reformule la fecha.

### 6. El bucle de planificación: `rescheduleNextAlarm()` y `schedule_alarm()` interno

El corazón del servicio es el método `rescheduleNextAlarm()`. Su lógica es:

```java
private void rescheduleNextAlarm() {
    if (currentScheduledTask != null && !currentScheduledTask.isDone()) {
        currentScheduledTask.cancel(false);
    }
    // SELECT id, reason, alarm_time FROM SCHEDULER WHERE alarm_time > now ORDER BY alarm_time LIMIT 1
    // Si existe, calcular delay = alarm_time - now
    // schedule_alarm(id, reason, alarmTime)
}
```

Este método se invoca:
- En `start()`, para recuperar la alarma más próxima existente (resincronización).
- Después de **insertar** una nueva alarma (en `schedule()`).
- Después de **eliminar** una alarma ya disparada (en el callback de la tarea).

El método `schedule_alarm` (privado, no confundir con la herramienta) es quien realmente programa la tarea en el `ScheduledExecutorService`:

```java
long delay = Duration.between(LocalDateTime.now(), alarmTime).toMillis();
if (delay < 0) delay = 0;

currentScheduledTask = scheduler.schedule(() -> {
    sendEvent(alarmTime, reason);
    removeAlarm(id);
    rescheduleNextAlarm();
}, delay, TimeUnit.MILLISECONDS);
```

Cuando la tarea se ejecuta, envía el evento, borra la alarma de la base de datos y vuelve a programar la siguiente (si existe). Nótese que el `rescheduleNextAlarm()` también cancela la tarea actual antes de programar la nueva, garantizando que solo haya una tarea pendiente en todo momento.

### 7. Disparo de la alarma: generación del evento sensorial

El método `sendEvent` construye un objeto JSON con la información de la alarma y lo inyecta en el `SensorsService` mediante `agent.putEvent()`:

```java
String notify = gson.toJson(Map.of(
    "alarm_time", when.toString(),
    "reason", reason
));
agent.putEvent(SENSOR_NAME, "ALARM TRIGGERED", PRIORITY_NORMAL, notify);
```

El `SensorsService`, a su vez, transforma esta llamada en un `SensorEventDiscrete` (porque la naturaleza del sensor `SCHEDULER` es `DISCRETE`) y lo encola para que el `ReasoningService` lo recoja en su próximo ciclo. Cuando el agente recibe el evento, el campo `contents` contendrá el JSON anterior, que el LLM podrá interpretar.

De esta forma, el agente puede reaccionar a la alarma con total naturalidad: puede enviar un mensaje al usuario, ejecutar una tarea, o incluso programar otra alarma. La lógica de *qué hacer* queda completamente delegada al modelo.

### 8. Eliminación y limpieza de alarmas

Una vez que una alarma se ha disparado, se elimina de la base de datos mediante `removeAlarm(id)`. Esto evita que vuelva a ejecutarse en futuros reinicios. No hay actualmente una herramienta que permita al LLM cancelar una alarma programada (aunque podría añadirse fácilmente con una nueva herramienta que ejecute un `DELETE FROM SCHEDULER WHERE id = ...`).

Si el agente se detiene antes de que se dispare una alarma, la tarea pendiente se pierde (la `ScheduledFuture` no se serializa), pero la alarma sigue en la base de datos. Al reiniciar, `rescheduleNextAlarm()` la recuperará y la reprogramará con el tiempo restante (la diferencia entre la hora actual y `alarm_time`). Esto garantiza la persistencia a largo plazo.

### 9. Concurrencia y diseño de hilos

El servicio utiliza un `ScheduledExecutorService` con un solo hilo (creado mediante `Executors.newSingleThreadScheduledExecutor()`). Originalmente se probó con hilos virtuales (`Thread.ofVirtual().factory()`), pero se revirtió a hilos de plataforma por problemas de estabilidad en tiempo de depuración. Dado que solo hay una tarea de planificación activa a la vez y el trabajo dentro de la tarea (enviar evento y borrar de BD) es mínimo, la diferencia de rendimiento es irrelevante.

El único punto delicado es la cancelación de la tarea actual cuando se programa una nueva alarma más cercana. Como `rescheduleNextAlarm()` y `schedule` se ejecutan en el hilo del agente (normalmente el `eventDispatcher`), y la tarea programada se ejecuta en el hilo del `ScheduledExecutorService`, no hay condiciones de carrera entre la cancelación y la ejecución porque la cancelación se hace siempre antes de crear la nueva tarea, y el `scheduler` está diseñado para que `cancel()` no interrumpa una tarea que ya ha comenzado (se usa `mayInterruptIfRunning = false`).

### 10. Limitaciones y posibles mejoras

El `SchedulerService` cumple su cometido básico, pero adolece de varias limitaciones que sería deseable abordar en versiones futuras:

- **Sin recurrencia**: las alarmas son únicas. No se pueden programar eventos periódicos ("cada día a las 8:00") ni basados en cron. Tampoco hay soporte para cancelación o modificación de una alarma existente.
- **Solo inglés en `when`**: Natty funciona bien en inglés, pero no entiende español ni otros idiomas. El modelo debe traducir, lo que añade un paso y riesgo de error.
- **Precisión limitada**: el `ScheduledExecutorService` de Java está sujeto a la granularidad del temporizador del sistema operativo (normalmente milisegundos, pero en sistemas cargados puede haber desviaciones de varios segundos). Para recordatorios conversacionales es suficiente; para tareas de milisegundo no.
- **Sin interfaz de usuario**: no hay forma de listar las alarmas pendientes desde la UI ni de cancelarlas. El usuario depende de que el agente recuerde lo que programó.
- **Notificaciones perdidas si el agente está apagado**: si el agente se detiene justo cuando debía dispararse una alarma, al reiniciar esa alarma ya ha vencido y se pierde. Una mejora sería ejecutar al arranque todas las alarmas vencidas (quizá con un límite de tiempo).
- **Dependencia de la base de datos H2**: actualmente sí, pero es una dependencia ligera y embebida. No se prevé cambiar.

A pesar de estas carencias, el servicio es funcional y suficiente para demostrar la capacidad de planificación temporal de Noema. La mayoría de las mejoras (recurrencias, cancelación, mejor parser de fechas) pueden añadirse sin romper la arquitectura existente.
