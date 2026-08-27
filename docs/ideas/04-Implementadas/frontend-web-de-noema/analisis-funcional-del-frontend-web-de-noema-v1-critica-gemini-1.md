El documento es excepcionalmente detallado, pero al bajar al nivel de código (especialmente con la restricción de **cero dependencias externas** y **Vanilla JS**), hay algunos puntos de fricción clásicos que casi con total seguridad surgirán. 

Aquí tienes los principales riesgos y puntos a vigilar durante la implementación:

### 1. El renderizado de Markdown y la seguridad (XSS)
*   **El problema:** El documento menciona aplicar un "formateo Markdown básico" a las respuestas del agente. Implementar un parser de Markdown desde cero con expresiones regulares en Vanilla JS es muy propenso a errores y, lo más crítico, a **vulnerabilidades XSS** (Cross-Site Scripting). Si el LLM genera código HTML o scripts maliciosos, insertarlos directamente en el DOM con `innerHTML` es peligroso.
*   **Fricción:** Lograr un Markdown robusto y seguro sin usar librerías como `marked.js` o `DOMPurify` te llevará mucho tiempo de desarrollo y testeo.
*   **Recomendación:** Considera relajar la regla de "cero dependencias" *exclusivamente* para el parseo/saneamiento de Markdown, o limítate a un formateo extremadamente básico (solo saltos de línea y negritas) usando `textContent` y creación de nodos seguros.

### 2. Diferenciación entre "Historial" y "Tiempo Real" (SSE)
*   **El problema:** El documento sugiere usar un temporizador (ej. 500ms sin eventos) si el backend no envía un evento explícito de fin de historial (`history-end`).
*   **Fricción:** Las heurísticas de tiempo en red son inestables. Si hay latencia, el cliente podría creer que el historial terminó, activar el comportamiento de "tiempo real" (ej. auto-scroll brusco, notificaciones) y luego recibir de golpe el resto del historial, rompiendo la fluidez de la UI.
*   **Recomendación:** Es vital que el backend implemente un evento explícito tipo `event: history-end` para que el cliente sepa exactamente cuándo cambiar de estado.

### 3. Rendimiento en el volcado del historial (Layout Thrashing)
*   **El problema:** Si un `terminalId` tiene cientos de mensajes, el bucle que procesa el historial insertará cientos de nodos en el DOM casi simultáneamente.
*   **Fricción:** Si por cada mensaje insertado se recalcula el scroll (`chatArea.scrollHeight`), el navegador sufrirá *layout thrashing*, congelando la interfaz durante unos segundos al cargar.
*   **Recomendación:** Durante el volcado del historial, suspende el cálculo de scroll. Inserta todos los mensajes (idealmente usando un `DocumentFragment`) y haz un único scroll al final cuando se reciba el `history-end`.

### 4. Ambigüedad en el contrato de la API de Configuración
*   **El problema:** En la Fase 6, al hablar del `checkedlist`, el documento dice: *"el endpoint GET /api/config/{path} **probablemente** devuelva un array con los valores activos"*.
*   **Fricción:** La UI dinámica depende al 100% de que la estructura de datos sea predecible. Si el backend devuelve un string separado por comas en lugar de un array, o si el formato de `settingsui.json` tiene discrepancias con lo que espera el GET/POST, el panel fallará.
*   **Recomendación:** Antes de programar `config-ui.js`, documenta con ejemplos JSON exactos qué devuelve y qué espera recibir cada endpoint de configuración (`inputstring`, `combo`, `checkedlist`).

### 5. Pérdida de mensajes en microcortes (Limitación de EventSource)
*   **El problema:** El documento asume correctamente que `EventSource` reconecta solo. Sin embargo, menciona que "no se vuelve a volcar el historial completo".
*   **Fricción:** Si el usuario envía un mensaje (POST exitoso), pero justo en ese segundo la conexión SSE se cae y tarda 3 segundos en reconectar, la respuesta del agente podría emitirse durante ese "agujero" de conexión. El usuario nunca verá la respuesta en pantalla a menos que cambie de terminal y vuelva (forzando la recarga del historial).
*   **Recomendación:** Para una V1 es aceptable, pero debes ser consciente de este caso límite. En el futuro, el backend podría necesitar soportar el header `Last-Event-ID` de SSE para enviar los eventos perdidos tras una reconexión.

### 6. Gestión del estado de los inputs al cambiar de terminal
*   **El problema:** Se indica que al cambiar de `terminalId` no se borra el `messageInput`.
*   **Fricción:** Si el usuario está escribiendo, se da cuenta de que está en el terminal equivocado, cambia el ID y pulsa "Enter" muy rápido, podría enviar el mensaje antes de que la nueva conexión SSE esté lista o el historial limpio, causando condiciones de carrera visuales.
*   **Recomendación:** Deshabilita temporalmente el botón de "Enviar" y el `textarea` durante los milisegundos que dura la transición de limpieza y reconexión.

**En resumen:** El diseño es sólido. Los únicos problemas reales que te vas a encontrar son las **condiciones de carrera en la red** (historial vs tiempo real) y la **manipulación segura del DOM** (Markdown). Si tienes control sobre el backend para ajustar pequeños detalles (como el `history-end`), la implementación será muy fluida.
