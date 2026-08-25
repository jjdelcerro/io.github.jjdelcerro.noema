He revisado el documento con detalle y he identificado varios puntos de fricción que podrían causar problemas durante la implementación. Los organizo por categoría y prioridad:

## 🔴 Críticos (pueden bloquear la implementación)

### 1. **Evento `history-end` no confirmado** (Secciones 6.4, 6.5)
El documento menciona que el servidor podría enviar un evento `history-end` para marcar el fin del volcado del historial, pero no está confirmado. La alternativa (heurística de 500ms sin eventos) es frágil:
- Si el agente está procesando algo lento, podría cortar el historial prematuramente.
- Si hay muchos mensajes, el volcado podría tardar más de 500ms entre eventos.
- **Solución**: Confirmar con el backend si se implementará `history-end` o definir claramente la heurística alternativa.

### 2. **Estructura de eventos SSE no especificada** (Sección 6.3)
Se dice que cada evento tiene `event` y `data` en JSON, pero no se detalla la estructura interna de cada tipo:
- ¿Qué campos tiene `response`? (`content`, `timestamp`, `terminalId`?)
- ¿Qué campos tiene `log`? (`tool`, `parameters`, `timestamp`?)
- ¿Qué campos tiene `error`? (`code`, `message`, `details`?)
- **Solución**: Proporcionar ejemplos JSON completos de cada tipo de evento.

### 3. **Formato de `settingsui.json` ambiguo** (Sección 7.2)
No hay un ejemplo completo del descriptor. Preguntas sin responder:
- ¿Cómo se diferencia un nodo rama de una hoja? (¿campo `type`? ¿presencia de `children`?)
- ¿Qué campos tiene cada nodo? (`title`, `path`, `type`, `options`, `values`?)
- ¿Cómo se representan las opciones de un `combo`?
- **Solución**: Incluir un ejemplo JSON real de `settingsui.json`.

### 4. **Endpoints de configuración sin especificar formato de respuesta** (Sección 2.4)
No está claro qué devuelven los endpoints:
- ¿`GET /api/config` devuelve toda la configuración como objeto JSON o envuelta en `{ "config": ... }`?
- ¿`GET /api/config/{path}` devuelve el valor directo o en un objeto `{ "value": ... }`?
- ¿`GET /api/config/{path}` para un `checkedlist` devuelve un array de valores marcados o un objeto con booleanos?
- **Solución**: Documentar el formato exacto de respuesta de cada endpoint.

## 🟡 Importantes (pueden causar bugs o mala UX)

### 5. **Agrupación de mensajes no del todo clara** (Sección 5.3)
La regla dice "eventos consecutivos del mismo tipo se agrupan", pero:
- ¿Qué pasa si hay dos `user-message` seguidos? (poco común pero posible)
- ¿Un `log` seguido de un `response` crea dos bloques separados?
- ¿Y si hay `user-message` → `log` → `response`? ¿Tres bloques?
- **Solución**: Especificar ejemplos concretos de agrupación.

### 6. **Persistencia del input al cambiar terminal** (Secciones 3.3, 9.3)
Se dice que el contenido del input se preserva al cambiar de terminal, pero:
- ¿Se asocia el input al terminal o es global?
- Si el usuario escribe un mensaje para el terminal A, cambia al B, y vuelve al A, ¿el mensaje sigue ahí?
- **Solución**: Aclarar si el input es global o se asocia a cada terminal.

### 7. **Reconexión automática de SSE sin control** (Secciones 6.5, 8.3)
Se confía en la reconexión automática de `EventSource`, pero:
- `EventSource` no permite configurar reintentos ni timeout.
- ¿Cómo se detecta que la reconexión ha fallado definitivamente?
- ¿Qué pasa si el servidor tarda mucho en responder?
- **Solución**: Implementar lógica adicional para detectar fallos definitivos (ej: contador de errores).

### 8. **Markdown "básico" no definido** (Sección 5.3)
Se menciona formateo Markdown básico, pero:
- ¿Qué se considera "básico"? (¿negrita, cursiva, listas, código, enlaces?)
- ¿Se usa una librería o se implementa manualmente?
- Si es manual, ¿qué casos límite se manejan?
- **Solución**: Especificar qué elementos Markdown se soportan y si se usa librería externa.

### 9. **Scroll automático con margen impreciso** (Sección 5.3)
Se usa un margen de 50px para detectar si el usuario está al final, pero:
- ¿Y si el usuario está a 49px del final? ¿Se interrumpe su lectura?
- ¿Qué pasa si el usuario está leyendo un mensaje antiguo y llega uno nuevo?
- **Solución**: Ajustar el margen o implementar una lógica más robusta (ej: botón "Ir al final").

### 10. **Cambio de terminal durante envío de mensaje** (Sección 9.3)
No se especifica qué pasa si:
- El usuario cambia de terminal mientras se está enviando un mensaje.
- ¿Se cancela el envío? ¿Se envía al terminal anterior?
- **Solución**: Definir el comportamiento (ej: bloquear el cambio de terminal mientras hay un envío pendiente).

## 🟢 Menores (pueden refinarse durante la implementación)

### 11. **Toasts sin duración especificada** (Sección 8.2)
Se dice que desaparecen "tras unos segundos", pero no se especifica cuántos. ¿3s? ¿5s? ¿10s?

### 12. **Panel de configuración y cambio de terminal** (Sección 7)
No se especifica qué pasa si el usuario abre el panel de configuración y luego cambia de terminal. ¿Se cierra el panel? ¿Se mantiene?

### 13. **Tamaño del historial** (Sección 6.4)
Se vuelca el historial completo al conectar, pero:
- ¿Qué pasa si el historial tiene miles de mensajes?
- ¿Hay un límite o paginación?
- ¿Podría causar problemas de rendimiento en el navegador?

### 14. **Confirmación del cambio de terminalId** (Sección 9.2)
Se confirma con Enter o al perder el foco, pero:
- ¿Qué pasa si el usuario hace clic fuera accidentalmente mientras escribe?
- ¿Hay un debounce para evitar cambios accidentales?

### 15. **Manejo de errores en optimistic UI** (Sección 3.5)
Si el envío de mensaje falla, se muestra un toast, pero:
- ¿El mensaje que ya se mostró en el chat se marca como "no enviado"?
- ¿Se elimina?
- ¿Se queda ahí sin indicador?

## Recomendación

Antes de comenzar la implementación, te sugiero:
1. **Confirmar con el backend** los puntos críticos (1-4): estructura de eventos SSE, formato de `settingsui.json`, endpoints de configuración y evento `history-end`.
2. **Aclarar los puntos importantes** (5-10) para evitar bugs durante el desarrollo.
3. **Dejar los puntos menores** (11-15) para refinamientos durante las pruebas.

Con estas aclaraciones, el documento será suficiente para una implementación sin bloqueos.
