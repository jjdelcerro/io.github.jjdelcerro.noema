

**1. Introducción y objetivo del cliente web**  
Define el propósito del documento: especificar los requisitos y el diseño de la interfaz web (HTML+JS vainilla) que permitirá conversar con el agente Noema desde un navegador, replicando las funcionalidades esenciales de las interfaces Swing y CLI.

**2. Contexto breve de Noema**  
Describe sucintamente que Noema es un agente conversacional único con memoria narrativa persistente, al que se accede mediante un identificador de terminal (terminalId). No existen sesiones: cada interacción es independiente pero el agente recuerda el historial completo asociado a cada terminal.

**3. Funcionalidades del cliente web**  
Enumera las capacidades que debe ofrecer: envío de mensajes sin bloqueo, recepción de respuestas y eventos en tiempo real vía SSE, visualización del historial completo al conectar, cambio de terminal con preservación del texto en el input, y un panel de configuración dinámico que permite modificar los ajustes del agente en caliente.

**4. Estructura de ficheros y organización del código**  
Establece la división modular del frontend en cuatro archivos: `index.html` (estructura base y carga de scripts), `api.js` (comunicación con el backend: fetch y SSE), `chat-ui.js` (lógica de la interfaz de chat, renderizado de mensajes con Markdown, envío, gestión del historial y agrupación), y `config-ui.js` (construcción dinámica del árbol y formularios de configuración a partir de settingsui.json).

**5. Interfaz de usuario y experiencia de chat**  
Describe la composición visual: cabecera con el terminalId editable (persistido en localStorage), el estado de la conexión SSE (indicador con punto coloreado + texto: “Conectado”, “Conectando…”, “Desconectado”), y el área central de chat. Detalla la alineación de mensajes (usuario a la derecha, el resto a la izquierda), la agrupación de mensajes consecutivos del mismo tipo en un único bloque (especialmente los logs de herramientas), y la diferenciación de tipos (usuario, agente, log, error) mediante colores de fondo sutiles. Indica que todo el texto del chat debe ser seleccionable para copia.

**6. Comunicación con el backend y eventos SSE**  
Especifica el flujo: el envío se realiza con POST a `/api/chat/{terminalId}` sin esperar respuesta (recibe 202 Accepted) y la recepción se gestiona mediante una conexión EventSource a `/api/console/{terminalId}`. Al abrir la conexión, el agente vuelca el historial completo; después, cada evento (respuesta, log, error) se recibe en tiempo real y se renderiza según el tipo. Los logs se agrupan consecutivamente y se muestran con sus parámetros, pero sin la salida de la herramienta.

**7. Módulo de configuración**  
Detalla el comportamiento del panel de configuración: al activarlo se obtiene `settingsui.json` desde `/api/config/ui`, se construye un árbol de navegación y un panel de contenido dinámico. Solo se soportan los tipos de campo `inputstring`, `combo`, `checkbox` y `checkedlist` (este último mediante checkboxes nativos). Las entradas de tipo `action` se ignoran. Los cambios de valor se envían inmediatamente con POST a las rutas correspondientes, sin botón de guardar; cualquier fallo se notifica con un toast.

**8. Manejo de estados y errores**  
Define el comportamiento ante incidencias: para la conexión SSE se usa el indicador de cabecera (opción 4). Los errores de red, de la API o de la configuración se muestran con un toast temporal en la esquina superior derecha. Si la conexión SSE se pierde, se intenta la reconexión automática y se refleja en el indicador.

**9. Comportamiento del terminalId**  
Explica que el terminalId se guarda en `localStorage`. Al cambiarlo, se limpia el área de chat y se inicia una nueva conexión SSE que volcará el historial del nuevo terminal; el contenido del input de texto se preserva sin modificaciones. No se solicita confirmación al cambiar, ya que el historial anterior se recupera simplemente restaurando el terminalId previo.

**10. Restricciones técnicas**  
Indica que el cliente es una SPA ligera sin frameworks, ejecutada en local, servida desde el mismo servidor embebido de Noema. No se implementan mecanismos de autenticación. Solo se emplean tecnologías web estándar (HTML5, CSS3, JavaScript ES nativo).

**11. Próximos pasos y evolución futura (opcional)**  
Menciona posibles mejoras: soporte para confirmaciones del agente, renderizado de más tipos de campo en configuración, migración a WebSockets si se requiere bidireccionalidad total, y encapsulación de componentes (Custom Elements) para el checkedlist u otros elementos complejos.



Aquí tienes un posible esquema para el plan de implementación del cliente web:

1. **Preparación del entorno y estructura inicial de archivos**
2. **Fase 1: Maquetación HTML y estilos base (index.html + CSS)**
3. **Fase 2: Módulo de comunicación con el backend (api.js)** – fetch para chat, SSE para consola y configuración
4. **Fase 3: Lógica del chat (chat-ui.js)** – envío de mensajes, renderizado con agrupación y Markdown, historial al conectar
5. **Fase 4: Indicador de conexión y notificaciones de error** – estado SSE (punto + texto), toast temporal
6. **Fase 5: Gestión del terminalId** – persistencia en localStorage, cambio con limpieza y preservación del input
7. **Fase 6: Módulo de configuración (config-ui.js)** – carga de settingsui.json, árbol dinámico, formularios y guardado en caliente
8. **Fase 7: Pruebas de integración y ajustes finales**



ahora la fase "Fase 1: Maquetación HTML y estilos base (index.html + CSS)". cuanto mas detalle mejor estara el plan



