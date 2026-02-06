
# Documento de Diseño: Consciencia Temporal Dual

Este documento detalla el diseño técnico para integrar la percepción del tiempo en el `ChatAgent`, permitiendo que el modelo sea consciente de la hora actual y de los periodos de inactividad sin necesidad de hilos de monitorización constantes.

## 1. Visión General
El sistema implementará dos mecanismos complementarios:
1.  **Percepción de Silencio (Contextual):** El agente detectará si ha pasado un tiempo prolongado desde la última vez que habló con el usuario para ajustar su tono social.
2.  **Anclaje Temporal (Operativo):** El agente recibirá la hora actual siempre que sea despertado por un evento externo (Alarma, Telegram, Email).

## 2. Mecanismos de Implementación

### A. Gestión del Silencio en la Sesión
La clase `Session` será la encargada de monitorizar el "reloj biológico" del agente.
*   **Estado persistente:** Se añadirá un campo `lastInteractionTime` (LocalDateTime) que se guardará en `active_session.json`.
*   **Inyección efímera:** En el método `getContextMessages`, si el último mensaje en la lista es un `UserMessage` y el delta entre la hora actual y `lastInteractionTime` es superior a **1 hora**, se intercalará un mensaje virtual de sistema antes de enviar el contexto al LLM.
*   **Actualización:** `lastInteractionTime` se actualizará con la hora actual en cada llamada a `getContextMessages`.

### B. Anclaje en Notificaciones (`pool_event`)
La herramienta `pool_event` y el mecanismo de eventos de `ConversationService` se enriquecerán con metadatos temporales.
*   **Payload enriquecido:** Cuando un sensor (Telegram, Scheduler) inyecte un evento, la respuesta que el LLM reciba de la herramienta virtual `pool_event` incluirá un campo `current_time`.
*   **Utilidad:** Esto permite que el LLM oriente sus acciones (ej. ejecutar una tarea de una alarma) sabiendo la hora exacta en la que ocurre el evento.


# 3. Guía de Implementación: Puntos de Intervención

### 1. Clase `Session.java` (lib.impl.services.conversation)
*   **Campo nuevo:** `private LocalDateTime lastInteractionTime;`
*   **Métodos `load()` y `save()`:**
    *   Añadir `lastInteractionTime` a la clase interna `SessionState` para que se serialize en el JSON.
*   **Método `getContextMessages(CheckPoint checkpoint, String systemPrompt)`:**
    *   **Lógica:**
        1.  Obtener `now = LocalDateTime.now()`.
        2.  Si `lastInteractionTime != null` **Y** el último mensaje de la lista `messages` es de tipo `USER`:
            *   Calcular `Duration delta = Duration.between(lastInteractionTime, now)`.
            *   Si `delta.toHours() >= 1` :
<!--                 *   Insertar una llamada ficticia a pool_event que devuelva un evento del canal "time" con la hora actual y un mensaje que diga: *"Han pasado [X] horas y [Y] minutos desde la última interacción."* -->
        3.  `this.lastInteractionTime = now;` (Actualizar siempre).
        4.  `this.save();` (Persistir el nuevo tiempo).

### 2. Clase `ConversationService.java` (lib.impl.services.conversation)
*   **Clase interna `Event`:**
    *   Añadir un campo `final String timestamp`.
*   **Método `Event.toJson()`:**
    *   Incluir el campo `"current_time"` en el JSON resultante con la hora actual para que el LLM lo vea al ejecutar `pool_event`.

### 3. Clases `Turn.java` y `TurnImpl.java` (lib.persistence / lib.impl.persistence)

*   **Método `toCSVLine()`:**
    *   Para ayudar al `MemoryManager` a escribir "El Viaje", sería ideal añadir una columna calculada al CSV.
    *   Modificar para que acepte el turno anterior: `toCSVLine(Turn previous)`.
    *   Calcular la diferencia en segundos/minutos entre `this.timestamp` y `previous.timestamp`.
    *   Añadir este valor como una nueva columna: `delta_time_seconds`.

A esta parte hay que dedicarle aun una pensada.
   
### 4. Prompts de Sistema

*   **`prompt-system-conversationmanager.md`:** Informar al agente de que recibirá notas de sistema sobre el paso del tiempo y que las use para adaptar su saludo o prioridades.
*   **`prompt-compact-memorymanager.md`:** Instruir al compactador para que utilice la nueva columna `delta_time_seconds` del CSV para narrar pausas o sesiones intensas en "El Viaje".

# 4. Flujo Lógico de Ejemplo

1.  **Usuario no habla en 5 horas.**
2.  **Usuario escribe:** "Hola".
3.  `ConversationService` llama a `session.getContextMessages()`.
4.  `Session` detecta delta de 5 horas -> Inyecta nota: *"Sistema: Han pasado 5 horas desde la última interacción"*.
5.  **LLM recibe:** [System Prompt] + [CheckPoint] + [historial-mensajes-activos] + [Usuario: Hola] + [Nota Silencio].
6.  **LLM responde:** "Hola de nuevo. Ha pasado un buen rato desde esta mañana, ¿seguimos con el código?".
7.  **Session** actualiza `lastInteractionTime` y guarda.


# 5. Consideraciones de Seguridad y Robustez
*   **Primer arranque:** Si `lastInteractionTime` es `null` (primera ejecución histórica), no se debe inyectar ninguna nota.
*   **Bucle ReAct:** La inyección solo ocurre si el último mensaje es `USER`, evitando que en un bucle de 5 llamadas a herramientas el mensaje de tiempo se repita 5 veces.
*   **Eficiencia:** No se utiliza ningún hilo `Timer` o `ScheduledExecutor` para esta percepción; todo ocurre en el hilo de la solicitud del usuario, manteniendo el sistema ligero.

# 6. Notas sobre el formatea de Duration.

Para realizar el proceso inverso (de objeto temporal a lenguaje natural), la librería estándar de Java es un poco limitada (te da formatos tipo `PT5H10M`), por lo que en el ecosistema Java la solución "estándar de facto" es **PrettyTime**.

Dado que tu proyecto es pragmático y buscas algo que se integre bien con Java 21, aquí tienes las mejores opciones:

### 1. La opción recomendada: **PrettyTime**
Es la contraparte perfecta de Natty. Es muy ligera, no tiene dependencias y soporta **Castellano** perfectamente.

**Dependencia Maven:**

```xml
<dependency>
    <groupId>org.ocpsoft.prettytime</groupId>
    <artifactId>prettytime</artifactId>
    <version>5.0.7.Final</version>
</dependency>
```

**Uso básico:**

```java
PrettyTime p = new PrettyTime(new Locale("es"));
// Para una fecha pasada (tu caso de "silencio")
System.out.println(p.format(LocalDateTime.now().minusHours(5))); 
// Resultado: "hace 5 horas"

// Para una duración específica
System.out.println(p.formatDuration(Duration.ofMinutes(10)));
// Resultado: "10 minutos"
```

### 2. Opción "Sin Dependencias" (Java 21 nativo)
Si no quieres añadir otro JAR al proyecto, puedes crear un pequeño método utilitario aprovechando que estás en Java 21. Aunque no es "lenguaje natural" puro (como "hace un ratito"), es muy legible para un LLM.

```java
public static String formatRelative(Duration duration) {
    if (duration.toDays() > 0) return duration.toDays() + " días";
    if (duration.toHours() > 0) return duration.toHours() + " horas";
    if (duration.toMinutes() > 0) return duration.toMinutes() + " minutos";
    return "pocos segundos";
}
```

### 3. Comparativa para tu diseño

Para los puntos de intervención que definimos en el documento de diseño:

*   **En la nota de `Session` (Percepción de Silencio):**
    Te recomiendo **PrettyTime** usando `format(lastInteractionTime)`. Obtendrás frases como *"hace 3 horas"* o *"hace 2 días"*, que el LLM entiende perfectamente para ajustar su saludo.
    
*   **En el CSV de `SourceOfTruth` (Delta Time):**
    Aquí es mejor guardar el número crudo (segundos) o usar la opción **nativo de Java** (ej. `5h 10m`), ya que el `MemoryManager` necesita precisión para calcular la cronología en "El Viaje".

Como ya se esta usando Natty para "entender", PrettyTime es la herramienta lógica para que el agente se "exprese" sobre el tiempo en sus logs internos y notas de sistema.

