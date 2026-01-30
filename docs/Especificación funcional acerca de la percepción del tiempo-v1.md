
# Especificación funcional acerca de la percepción del tiempo

## 1. Percepción por Reanudación
Se descarta el uso de hilos en segundo plano para monitorizar el silencio. La percepción de inactividad se traslada íntegramente al momento de la interacción.

*   **Mecánica:** En el método `processTurn`, antes de enviar la entrada del usuario al LLM, se calcula el `delta_time` respecto al último registro en el `SourceOfTruth`.
*   **Inyección Efímera:** Si el tiempo transcurrido supera el umbral (ej. 1 hora), se inyecta una nota de sistema: `[Nota: Reanudación de actividad tras X tiempo de silencio]`.

## 2. Eventos de Ciclo de Vida de Sesión
La conversación se organiza en sesiones físicas (de la apertura al cierre del JAR). Estos eventos actúan como separadores de capítulos en el historial.

### A. Evento: INICIO DE SESIÓN
*   **Momento:** Se genera una única vez al arrancar el `Main`.
*   **Contenido:** Fecha/Hora actual.
*   **Delta Especial:** Se calcula la diferencia de tiempo entre este arranque y el último **Fin de Sesión** registrado. 
*   **Semántica:** Informa al agente de cuánto tiempo ha estado "fuera de línea" o apagado. Permite diferenciar entre una pausa con el sistema encendido y una ausencia con el sistema apagado.

### B. Evento: FIN DE SESIÓN
*   **Momento:** Se genera cuando el usuario ejecuta el comando `/quit`.
*   **Contenido:** Fecha/Hora actual.
*   **Delta Especial:** Se calcula la diferencia de tiempo respecto al último mensaje del usuario en esa sesión.
*   **Semántica:** Captura el tiempo que el usuario "tardó en irse" tras su última idea, cerrando el contexto de forma limpia.

## 3. Persistencia y Exportación (Economía de Datos)

Para mantener el principio de **Base de Datos Limpia**, aplicamos un tratamiento diferenciado:

1.  **En H2 (SourceOfTruth):** Los eventos de Inicio y Fin de Sesión **SÍ** se persisten. Son eventos de baja frecuencia (dos por ejecución) pero con un valor estructural inmenso. Se guardarán con un `contentType` específico (ej. `session_lifecycle`).
2.  **En el CSV (Compactación):**
    *   Los deltas de tiempo ordinarios (entre mensajes) se calculan al vuelo y se añaden como una columna `delta_time`.
    *   Los eventos `session_lifecycle` aparecerán en el CSV, permitiendo al `MemoryManager` ver físicamente los cortes entre sesiones.
    *   Las notas efímeras de "Reanudación" (percepción inmediata) **no se persisten**. El `MemoryManager` ya las deduce a través de la columna `delta_time` del CSV.

## 4. Impacto en la Narrativa (El Viaje)

Con este esquema, el `MemoryManager` tiene todos los elementos para una crónica perfecta:
*   *"Tras dos días con el sistema apagado {cite: ID_START}, Joaquín inició una nueva sesión..."*
*   *"La conversación fluyó con rapidez (deltas de segundos) hasta que se produjo un silencio de tres horas antes del cierre de la sesión {cite: ID_END}."*

