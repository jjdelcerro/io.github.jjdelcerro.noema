
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
                *   Insertar un `SystemMessage` temporal al inicio de la lista que diga: *"Han pasado [X] horas y [Y] minutos desde la última interacción."*
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

