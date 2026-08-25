
# Análisis funcional del frontend web de Noema

## 1. Introducción y objetivo del cliente web

Este documento describe el análisis y los requisitos para el desarrollo de una nueva interfaz de usuario web para Noema, un agente conversacional autónomo con memoria narrativa persistente. El propósito de esta aplicación es proporcionar una alternativa ligera y accesible desde navegador a las interfaces de escritorio actuales (Swing y CLI), manteniendo la misma filosofía de interacción: un asistente único, sin sesiones, al que se accede mediante un identificador de terminal.

La aplicación web será una Single Page Application (SPA) construida con HTML, CSS y JavaScript vainilla, sin dependencias de frameworks externos. Como vamos a tener que renderizar markdown usaremos la libreria "Marked.js". Se servirá directamente desde el propio backend de Noema y se comunicará con el agente a través de una API REST y eventos enviados por el servidor (SSE). El presente análisis se centra exclusivamente en el lado del cliente, sin abordar las modificaciones necesarias en el núcleo de Noema para dar soporte a esta nueva interfaz.


## 2. Contexto breve de Noema

Noema es un agente conversacional autónomo diseñado como un laboratorio de arquitectura para sistemas de inteligencia artificial. A diferencia de un chatbot convencional, no establece sesiones efímeras con los usuarios; funciona como un asistente permanente que mantiene una memoria narrativa persistente y recibe estímulos de forma asíncrona desde múltiples fuentes (interfaces de usuario, sensores como Telegram o temporizadores, etc.).

La interacción con Noema se basa en una analogía telefónica: cada usuario o dispositivo se identifica mediante un **identificador de terminal** (`terminalId`), similar a un número de teléfono. Cuando se desea consultar algo al agente, se le envía un mensaje indicando el `terminalId` desde el que se llama y el contenido de la petición. El agente recupera internamente todo el historial de interacciones previas con ese terminal, procesa el nuevo mensaje a la luz de ese contexto acumulado, y emite una respuesta. Una vez concluida la atención, la comunicación finaliza; no se mantiene una sesión abierta, pero el agente conserva indefinidamente el registro de lo hablado para futuras llamadas desde el mismo terminal.

Este modelo implica que:

- El cliente no necesita gestionar ni enviar contexto; el agente lo mantiene de forma autónoma.
- No existen sesiones que caduquen ni tokens de autenticación; el `terminalId` es el único dato necesario para que el agente recupere la línea temporal de conversación correspondiente.
- El agente es una instancia única que procesa estímulos secuencialmente, por lo que atiende cada petición de forma independiente pero ordenada.
- Los mecanismos internos de memoria, persistencia (base de datos H2), puntos de guardado o herramientas son completamente opacos para el cliente. Desde su perspectiva, Noema es simplemente un asistente al que se llama, se le habla y se obtiene una respuesta.

Actualmente Noema dispone de interfaces de escritorio (Swing) y de línea de comandos (CLI) que implementan este modelo de interacción. El presente documento se centra en el análisis y diseño de una nueva interfaz de usuario web que ofrezca la misma experiencia conversacional desde un navegador, respetando la filosofía asíncrona y sin sesiones del agente.


## 3. Funcionalidades del cliente web

El cliente web debe proporcionar las siguientes capacidades funcionales, organizadas por áreas de interacción:

**3.1. Conversación asíncrona con el agente**

- El usuario puede enviar mensajes de texto asociados al `terminalId` activo mediante una petición HTTP que no espera respuesta (desacople entre envío y recepción).
- Las respuestas del agente, así como los eventos de sistema (logs de herramientas, errores), se reciben en tiempo real a través de una conexión SSE persistente, sin bloquear la interfaz.

**3.2. Visualización y gestión del historial**

- Al establecer la conexión SSE para un `terminalId`, el agente vuelca automáticamente el historial completo de conversaciones previas con ese terminal en como un aarray json con todos los mensajes, permitiendo al usuario recuperar el contexto completo.
- Los mensajes se muestran en un área de chat diferenciando visualmente su origen: los mensajes del usuario aparecen alineados a la derecha, mientras que las respuestas del agente, los logs de herramientas y los errores se alinean a la izquierda.
- Los eventos consecutivos del mismo tipo (por ejemplo, varios logs seguidos) se agrupan en un único bloque para evitar dispersión visual y mantener la fluidez de lectura.
- Todo el texto del área de chat es seleccionable para facilitar la copia de fragmentos de la conversación.

**3.3. Gestión del identificador de terminal**

- El `terminalId` se introduce en un campo editable en la cabecera, se persiste automáticamente en `localStorage` y puede modificarse en cualquier momento.
- Al cambiar el `terminalId`, el área de chat se limpia y se inicia una nueva conexión SSE que cargará el historial correspondiente al nuevo terminal. El contenido del campo de entrada de mensaje se preserva intacto, permitiendo continuar la redacción sin pérdida.
- No se solicita confirmación al cambiar de terminal, ya que el historial anterior sigue siendo accesible simplemente restaurando el `terminalId` previo.

**3.4. Indicador de estado de la conexión**

- La cabecera muestra un indicador compuesto por un punto coloreado y un texto que refleja el estado de la conexión SSE: “Conectado” (verde), “Conectando…” (amarillo intermitente) o “Desconectado” (rojo).

**3.5. Notificación de errores**

- Cualquier error de comunicación, respuesta inesperada de la API o fallo en la actualización de configuración se notifica mediante un toast temporal en la esquina superior derecha, que desaparece automáticamente tras unos segundos sin interrumpir la interacción.

**3.6. Panel de configuración del agente**

- El cliente ofrece un panel de configuración que permite modificar parámetros del agente en caliente, sin necesidad de reinicios.
- La interfaz de configuración se genera dinámicamente a partir del descriptor `settingsui.json` proporcionado por el backend. Se compone de un árbol de navegación (secciones) y un área de contenido donde se renderizan los campos según el tipo: `inputstring`, `combo`, `checkbox` y `checkedlist` (este último mediante checkboxes nativos).
- Las entradas de tipo `action` no se renderizan como interactivas; se ignoran o muestran como no disponibles.
- Cada modificación de un valor se envía inmediatamente al backend, sin botón de guardar global. Si la actualización falla, se muestra un toast de error.

**3.7. Otras características**

- La aplicación funciona sin autenticación, acorde al carácter local y de laboratorio de Noema.
- El diseño es responsivo, adaptándose a distintos tamaños de pantalla.
- No se emplean frameworks externos; toda la lógica se implementa con HTML5, CSS3 y JavaScript ES nativo.


## 4. Estructura de ficheros y organización del código

Para mantener la simplicidad y facilitar el mantenimiento sin introducir dependencias externas, el código del cliente web se organiza en cuatro ficheros independientes que se cargan como módulos ES nativos desde el documento HTML principal. Esta división separa claramente las responsabilidades de presentación, comunicación y lógica de cada área funcional.

**4.1. `index.html`** — Estructura base y carga de recursos  
Contiene la estructura semántica de la aplicación: cabecera (con el campo de `terminalId` y el indicador de estado de conexión), área de chat, campo de entrada de mensaje con botón de envío, contenedor para el panel de configuración y un área reservada para los toasts de notificación. Referencia los estilos CSS necesarios y carga los scripts JavaScript como módulos, definiendo el orden de inicialización. No incluye lógica de negocio.

**4.2. `api.js`** — Módulo de comunicación con el backend  
Encapsula toda la interacción con el servidor HTTP de Noema. Expone funciones para:
- Establecer la conexión SSE a `/api/console/{terminalId}` y gestionar sus eventos (apertura, recepción de mensajes, cierre, error), devolviendo la instancia de `EventSource` o un mecanismo de control.
- Enviar mensajes de usuario mediante `POST /api/chat/{terminalId}`.
- Obtener la configuración actual (`GET /api/config`), leer valores por ruta (`GET /api/config/{path}`) y escribir valores (`POST /api/config/{path}`, así como sus variantes para listas y checklists).
- Obtener el descriptor `settingsui.json` desde `/api/config/ui`.
Este módulo no manipula el DOM ni toma decisiones de presentación; se limita a resolver las peticiones y devolver los datos o eventos a los módulos consumidores.

**4.3. `chat-ui.js`** — Lógica de la interfaz de conversación  
Gestiona la experiencia de chat. Utiliza `api.js` para el envío de mensajes y la recepción de eventos SSE. Sus responsabilidades incluyen:
- Renderizar el historial completo al iniciar la conexión SSE, diferenciando los tipos de evento (mensaje de usuario, respuesta del agente, log, error) mediante alineación y colores de fondo, y aplicando agrupación de eventos consecutivos del mismo tipo en un único bloque.
- Procesar los eventos entrantes en tiempo real e insertarlos en el área de chat, respetando las mismas reglas de presentación.
- Gestionar el input del usuario, preservando su contenido al cambiar de `terminalId`.
- Aplicar un formateo Markdown básico al texto de las respuestas del agente.
- Coordinar la reconexión automática de SSE ante cortes y reflejar los cambios de estado en el indicador de conexión.
- Solicitar la limpieza del área de chat y la recarga del historial cuando se modifica el `terminalId`.

**4.4. `config-ui.js`** — Lógica de la interfaz de configuración  
Maneja la construcción y el comportamiento del panel de configuración dinámico. Se activa bajo demanda (por ejemplo, al pulsar un botón en la cabecera). Sus funciones incluyen:
- Obtener `settingsui.json` mediante `api.js` y construir un árbol de navegación jerárquico (secciones como ramas, parámetros como hojas).
- Renderizar en el panel de contenido los campos correspondientes al nodo seleccionado (una rama muestra todos sus hijos directos; una hoja muestra solo su campo). Soporta los tipos `inputstring`, `combo`, `checkbox` y `checkedlist` mediante checkboxes nativos.
- Ignorar o mostrar como no disponibles las entradas de tipo `action`, evitando ofrecer interactividad no soportada en web.
- Detectar cambios en los valores de los campos y enviarlos inmediatamente al backend a través de `api.js`, sin necesidad de un botón de guardado global.
- Notificar mediante toast cualquier error en la actualización de un valor.
- Cerrar el panel y liberar los recursos asociados cuando el usuario abandona la configuración.

**4.5. Relación entre módulos**  
`index.html` carga `api.js`, `chat-ui.js` y `config-ui.js`. Tanto `chat-ui.js` como `config-ui.js` importan y utilizan las funciones de `api.js` para la comunicación, pero no se comunican directamente entre sí. La orquestación general (inicialización, coordinación entre el cambio de `terminalId` y la recarga del chat, apertura del panel de configuración) recae en un pequeño bloque de código en el propio `index.html` o en un controlador principal mínimo que podrá extraerse más adelante si la complejidad lo justifica.


**5. Interfaz de usuario y experiencia de chat**

La interfaz de chat constituye la vista principal de la aplicación y concentra la mayor parte del tiempo de uso. Está diseñada para resultar funcional, clara y libre de distracciones, priorizando la legibilidad del historial y la fluidez de la conversación con el agente.

**5.1. Disposición general de la pantalla**

La pantalla se organiza en tres zonas verticales:

- **Cabecera**: barra horizontal superior que contiene el identificador de terminal, el indicador de estado de la conexión y los accesos a otras secciones (como la configuración).
- **Área de chat**: zona central que ocupa el espacio restante, con desplazamiento vertical automático. Muestra la secuencia completa de mensajes intercambiados con el agente.
- **Barra de entrada**: zona inferior fija donde se ubican el campo de texto para redactar mensajes y el botón de envío.

El diseño se adapta a distintos tamaños de pantalla manteniendo la misma disposición, redistribuyendo proporciones sin ocultar elementos esenciales.

**5.2. Cabecera**

La cabecera muestra, de izquierda a derecha:

- **Identificador de terminal**: un campo de texto editable donde el usuario puede consultar o modificar el `terminalId` activo. Su valor se conserva entre sesiones mediante `localStorage`, de modo que al abrir de nuevo la aplicación se recupera el último terminal utilizado. Mientras se escribe un nuevo identificador, el campo de entrada de mensajes permanece intacto.
- **Indicador de estado de la conexión SSE**: un punto coloreado acompañado de una etiqueta textual que refleja el estado actual de la comunicación con el agente. Los estados posibles son:
  - *Conectado*: punto verde y texto "Conectado".
  - *Conectando…*: punto amarillo con animación intermitente y texto "Conectando…".
  - *Desconectado*: punto rojo y texto "Desconectado".
- **Botón de configuración**: acceso al panel de configuración del agente (descrito en el apartado 7).

**5.3. Área de chat**

El área de chat acumula el historial completo de la interacción con el terminal activo y los nuevos mensajes que llegan en tiempo real. Su comportamiento y presentación siguen estas reglas:

- **Alineación por origen**:
  - Los mensajes del usuario se alinean a la derecha, con un color de fondo gris claro.
  - Las respuestas del agente, los logs de herramientas y los errores se alinean a la izquierda, cada uno con un color de fondo distintivo: blanco para las respuestas del agente, amarillo pálido para los logs y rojo claro para los errores.
- **Agrupación de eventos consecutivos del mismo tipo**: cuando varios eventos del mismo tipo (por ejemplo, varios logs) se reciben de forma consecutiva, se incorporan al mismo bloque visual en lugar de generar bloques independientes. Esto evita la fragmentación excesiva de la conversación y facilita la lectura. Si tras una secuencia de logs llega una respuesta del agente, se cierra el bloque de logs y se inicia uno nuevo para la respuesta.
- **Selección de texto**: todo el contenido textual del área de chat (tanto mensajes de usuario como respuestas del agente, logs y errores) es seleccionable por el usuario para permitir la copia de fragmentos.
- **Formateo de respuestas**: el texto de las respuestas del agente se interpreta con un formateo Markdown básico (negrita, cursiva, listas, bloques de código), mejorando la legibilidad cuando el agente emplea formato en sus contestaciones.
- **Desplazamiento automático**: al recibir un nuevo mensaje, el área de chat se desplaza automáticamente al final para mantener visible la última intervención. Si el usuario ha hecho scroll hacia atrás para leer mensajes anteriores, el desplazamiento automático se desactiva temporalmente hasta que vuelva al final de la conversación.

**5.4. Barra de entrada de mensajes**

En la parte inferior de la pantalla se sitúa el control para redactar y enviar mensajes:

- **Campo de texto**: permite introducir el mensaje que se enviará al agente. El texto escrito se conserva aunque se cambie de `terminalId`, de modo que el usuario no pierde lo redactado si decide consultar otro terminal antes de enviar.
- **Botón de envío**: situado junto al campo de texto. Al pulsarlo (o al presionar Enter en el campo de texto), se envía el contenido como mensaje del usuario, se limpia el campo de entrada y el mensaje aparece inmediatamente en el área de chat alineado a la derecha, sin esperar confirmación del backend. Si el campo está vacío, no se produce ninguna acción.


## 6. Comunicación con el backend y eventos SSE

La interacción entre el cliente web y el agente Noema se basa en un modelo asíncrono y desacoplado: el envío de mensajes de usuario y la recepción de respuestas del agente se realizan a través de canales independientes. Este diseño se alinea con la arquitectura interna del agente, que procesa los estímulos en su propio bucle y emite los resultados a medida que están disponibles.

**6.1. Envío de mensajes de usuario**

Para enviar un mensaje al agente, el cliente realiza una petición HTTP asíncrona al endpoint:

```
POST /api/chat/{terminalId}
Content-Type: application/json

{
  "message": "texto del mensaje"
}
```

- El `terminalId` identifica al interlocutor, tal como se ha descrito en la analogía telefónica. Es el mismo identificador que el usuario visualiza y modifica en la cabecera de la aplicación.
- La petición no espera la respuesta del agente. El servidor procesa la solicitud, encola el mensaje en el agente y responde inmediatamente con un código `202 Accepted` (u otro código de éxito que confirme la recepción).
- El módulo `api.js` se encarga de ejecutar esta llamada y notificar a la capa de UI (en concreto a `chat-ui.js`) de que el mensaje ha sido aceptado, momento en el cual se puede reflejar en el área de chat el mensaje del usuario.
- Si la petición falla por un error de red o del servidor, el módulo de comunicación lo notifica a la capa de UI para que muestre un toast de error, sin reintentar automáticamente (el usuario puede reenviar manualmente).

**6.2. Recepción de eventos vía SSE**

Para recibir las respuestas del agente y otros eventos del sistema, el cliente abre una conexión Server-Sent Events (SSE) con el endpoint:

```
GET /api/console/{terminalId}
```

- La conexión se establece utilizando la API nativa `EventSource` del navegador, que permite recibir un flujo continuo de eventos desde el servidor.
- El servidor mantiene la conexión abierta y envía eventos de texto cada vez que el agente emite una salida dirigida a ese `terminalId`.
- La conexión es de larga duración. El cliente la inicia al cargar la aplicación (con el `terminalId` actual) y la cierra y reabre cada vez que el usuario cambia el identificador de terminal.
- Mientras la conexión está activa, el servidor puede enviar diferentes tipos de eventos que representan la salida del agente: respuestas del modelo, registros de actividad de herramientas, errores y, potencialmente, solicitudes de confirmación (no implementadas en esta fase).

**6.3. Formato de los eventos SSE**

Cada evento enviado por el servidor se compone de un tipo (`event`) y un contenido (`data`) en formato JSON. Los tipos previstos en esta fase son:

- **`response`**: contiene la respuesta textual del agente a un mensaje del usuario. El campo `data` incluye el texto (posiblemente con formato Markdown) y una marca de tiempo.
- **`log`**: informa de que el agente ha ejecutado una herramienta, indicando el nombre de la herramienta y los parámetros con los que fue invocada. No incluye la salida producida por la herramienta.
- **`error`**: notifica un error interno del agente o de la comunicación durante el procesamiento de un estímulo.
- **`user-message`**: se utiliza únicamente durante el volcado del historial (ver 6.5) para reconstruir los mensajes que el usuario envió en el pasado. En tiempo real, los mensajes de usuario no se reciben por SSE porque se muestran directamente al enviarlos.

El cliente (`chat-ui.js`) interpreta el tipo de evento para aplicar las reglas de presentación (alineación, color, agrupación) descritas en el apartado 5.

**6.4. Historial al conectar**

Al abrir la conexión SSE, el servidor no espera a que se produzcan nuevos eventos, sino que de inmediato vuelca el historial completo de la conversación del `terminalId` solicitado.

- Este volcado consiste en una secuencia de eventos `user-message` y `response` (y posiblemente `log` y `error`) que reconstruyen cronológicamente todas las interacciones previas.
- El cliente trata estos eventos de la misma manera que los eventos en tiempo real: los va acumulando en el área de chat, aplicando la misma lógica de agrupación y presentación.
- De esta forma, al cambiar de terminal o al cargar la página por primera vez, el usuario ve inmediatamente la historia completa, sin necesidad de un endpoint adicional de historial.

**6.5. Gestión del ciclo de vida de la conexión SSE**

- **Apertura**: al iniciar la aplicación o al cambiar el `terminalId`, el módulo `api.js` cierra la conexión SSE anterior (si existe) y abre una nueva con el nuevo identificador. La capa de UI actualiza el indicador de estado a "Conectando…" durante el establecimiento y, cuando se recibe el primer evento (o el evento `open`), pasa a "Conectado".
- **Cierre por cambio de terminal**: al modificar el `terminalId`, se cierra explícitamente la conexión anterior mediante `EventSource.close()`. A continuación, se limpia el área de chat y se abre una nueva conexión para el nuevo terminal, lo que provoca un nuevo volcado del historial.
- **Reconexión automática**: la API `EventSource` incorpora un mecanismo de reconexión automática en caso de que la conexión se interrumpa por causas de red. Mientras se reconecta, el indicador de estado pasa a "Conectando…". Si la reconexión falla reiteradamente, el indicador se establece a "Desconectado" y se notifica al usuario mediante un toast de error. El módulo `api.js` expone los eventos de estado para que `chat-ui.js` actualice la interfaz en consecuencia.

**6.6. Consideraciones sobre la concurrencia de terminales**

Aunque el agente procesa todos los estímulos en serie, el cliente web puede cambiar de `terminalId` en cualquier momento. Las conexiones SSE de terminales anteriores se cierran inmediatamente, de modo que el servidor deja de enviar eventos de ese terminal al cliente (aunque el agente siga procesando tareas pendientes para él internamente). Esto evita que se mezclen eventos de diferentes terminales en el área de chat.


## 7. Módulo de configuración

El cliente web incluye un panel de configuración que permite modificar los parámetros del agente en tiempo real, sin necesidad de reiniciar la aplicación. La interfaz de este panel se genera dinámicamente a partir de la información proporcionada por el backend, replicando el modelo data-driven que ya utilizan las interfaces Swing y CLI de Noema.

**7.1. Acceso al panel de configuración**

El panel de configuración no está visible de forma permanente. Se accede a él mediante un botón situado en la cabecera de la aplicación (identificado con un icono de engranaje o similar). Al pulsarlo:

- Si el panel no está abierto, se despliega y ocupa la pantalla, reemplazando temporalmente la vista de chat o superponiéndose a ella como una capa modal o panel lateral.
- Si el panel ya está abierto, se cierra y se vuelve a la vista de chat.

Durante la primera apertura (o si la configuración ha cambiado externamente), el cliente solicita al backend los datos necesarios para construir la interfaz.

**7.2. Obtención del descriptor de interfaz**

El módulo `config-ui.js` obtiene el archivo `settingsui.json` mediante una petición a `/api/config/ui`. Este JSON define la estructura jerárquica de la configuración: los grupos, los parámetros, los tipos de campo, las opciones de los desplegables y las rutas internas que referencian a los valores almacenados en el agente.

A partir de este descriptor, el cliente construye íntegramente la interfaz de configuración, sin que sea necesario un conocimiento previo de los parámetros existentes.

**7.3. Estructura del panel: árbol de navegación y área de contenido**

La interfaz de configuración se organiza en dos zonas:

- **Árbol de navegación** (a la izquierda): muestra la jerarquía definida en `settingsui.json`. Cada grupo (nodo rama) se representa como un elemento expandible; cada parámetro (nodo hoja) como un elemento terminal.
- **Área de contenido** (a la derecha): muestra los campos de configuración correspondientes al nodo seleccionado en el árbol.

El comportamiento al seleccionar un nodo es el siguiente:

- **Si se selecciona una rama** (un grupo), el área de contenido muestra simultáneamente todos los campos que cuelgan directamente de esa rama. Esto permite visualizar y editar múltiples parámetros relacionados sin necesidad de navegar hoja por hoja.
- **Si se selecciona una hoja** (un parámetro), el área de contenido muestra únicamente el campo asociado a ese parámetro.

No se mantienen estados de edición no guardados entre cambios de selección; cada vez que se navega a un nodo, los campos se generan de nuevo con los valores actuales recuperados del agente (mediante llamadas al API de configuración).

**7.4. Tipos de campo soportados y su renderizado**

El cliente web reconoce y renderiza los siguientes tipos de campo definidos en `settingsui.json`:

- **`inputstring`**: campo de texto simple. Se muestra como un elemento `<input type="text">` con el valor actual y un evento que detecta cambios para enviarlos al backend.
- **`combo`**: lista desplegable. Se muestra como un `<select>` con las opciones definidas en el descriptor. La opción seleccionada coincide con el valor actual; al cambiarla, se envía la actualización.
- **`checkbox`**: casilla de verificación única para valores booleanos. Se utiliza `<input type="checkbox">`. El estado `checked` refleja el valor actual; al conmutarlo, se envía el nuevo estado.
- **`checkedlist`**: lista de elementos con casillas de verificación. Se representa mediante un conjunto de `<input type="checkbox">` nativos, cada uno con su etiqueta. Cada elemento tiene una ruta propia que identifica su estado booleano. Al marcar o desmarcar un elemento, se envía la actualización correspondiente sin necesidad de un botón adicional.

En todos los casos, el campo se muestra inmediatamente con el valor actual tan pronto como se obtiene del backend. Si el usuario modifica el valor, el cambio se envía automáticamente (ver 7.6).

**7.5. Entradas de tipo `action`**

El descriptor `settingsui.json` contiene entradas de tipo `action` que en las interfaces Swing y CLI se materializan como botones que disparan acciones complejas (por ejemplo, abrir un editor externo para gestionar proveedores de LLM). En el cliente web, estas entradas **no se renderizan como elementos interactivos**.

La decisión de diseño es ignorarlas por completo: no aparecen en el árbol de navegación ni en el área de contenido. De esta forma, el cliente web evita ofrecer funcionalidades que no puede implementar de manera coherente (por requerir interacción con el sistema de ficheros local o interfaces nativas). Si en el futuro se decide dar soporte a alguna de estas acciones, se podrá añadir un tratamiento específico para las más relevantes.

**7.6. Guardado inmediato y notificación de errores**

El cliente web no dispone de un botón de "Guardar" global. Cada modificación de un campo se considera una acción independiente y se envía inmediatamente al backend:

- Al cambiar el valor de un `inputstring`, un `combo`, un `checkbox` o una de las casillas de un `checkedlist`, el módulo `config-ui.js` llama a la función correspondiente de `api.js` (por ejemplo, `setConfigValue(path, value)`), que ejecuta un `POST` al endpoint de configuración adecuado.
- El campo no se bloquea durante la petición; el usuario puede seguir interactuando con otros elementos.
- Si la petición tiene éxito, el cambio queda confirmado sin ninguna indicación visual adicional.
- Si la petición falla (error de red, rechazo del servidor), el módulo muestra un **toast temporal** de error (según el diseño definido en el apartado 3.5), indicando que el valor no pudo ser actualizado. El campo se mantiene en su estado original (o se revierte al valor conocido) para reflejar que el cambio no fue aplicado.

**7.7. Cierre del panel**

El panel de configuración se cierra al pulsar de nuevo el botón de la cabecera o, si se utiliza un diseño modal, al pulsar un botón de cierre explícito o la zona exterior del panel. Al cerrarlo, la vista de chat vuelve a ser visible y cualquier configuración ya enviada permanece aplicada en el agente. No se mantiene ningún estado de edición pendiente.


## 8. Manejo de estados y errores

El cliente web debe informar al usuario de forma clara pero no intrusiva sobre el estado de la comunicación con el agente y sobre cualquier incidencia que impida el funcionamiento normal. Para ello se distinguen dos mecanismos principales: el indicador de estado de la conexión SSE en la cabecera y las notificaciones de error mediante toasts temporales.

**8.1. Estados de la conexión SSE**

La conexión SSE constituye el canal principal de recepción de respuestas y eventos. Su estado se refleja en todo momento en el indicador situado en la cabecera, compuesto por un punto coloreado y una etiqueta textual (opción 4 definida en el análisis de interfaz). Se contemplan tres estados:

- **Conectando**: se muestra al iniciar la aplicación, al cambiar de `terminalId` o cuando se está intentando restablecer la conexión tras una caída. El punto es de color amarillo y presenta una animación intermitente; la etiqueta muestra el texto "Conectando…". Mientras la conexión no esté establecida, no se reciben eventos del agente ni se puede cargar el historial. El envío de mensajes sigue funcionando con normalidad (el POST se completa independientemente), pero el usuario no verá la respuesta hasta que la conexión SSE se restablezca.

- **Conectado**: el punto pasa a verde fijo y la etiqueta muestra "Conectado". Este estado indica que la comunicación con el agente está activa y que cualquier nuevo evento será recibido en tiempo real. Es el estado normal de funcionamiento.

- **Desconectado**: se activa cuando la conexión SSE no ha podido establecerse tras varios intentos o se ha cerrado sin posibilidad de reconexión inmediata. El punto es de color rojo fijo y la etiqueta muestra "Desconectado". Este estado suele ir acompañado de una notificación toast que informa del problema y, si es posible, de las acciones recomendadas (por ejemplo, "No se ha podido conectar con el agente. Verifique que Noema esté en ejecución").

El indicador se actualiza automáticamente desde el módulo `chat-ui.js` en función de los eventos que emite el objeto `EventSource` (`open`, `error`) y la lógica de reconexión.

**8.2. Notificación de errores mediante toast**

Para todos los errores que no están directamente relacionados con el estado continuo de la conexión SSE, se utiliza una notificación de tipo **toast temporal**, que aparece en la esquina superior derecha de la pantalla y desaparece automáticamente al cabo de unos segundos.

Esta notificación se emplea en los siguientes casos:

- Error al enviar un mensaje (`POST /api/chat/{terminalId}`): si la petición falla (error de red o respuesta de error del servidor distinta de `202 Accepted`), se muestra un toast con el mensaje "Error al enviar el mensaje. Inténtelo de nuevo.".
- Error al obtener o modificar la configuración: si cualquiera de las peticiones de lectura del descriptor (`GET /api/config/ui`), obtención de valores (`GET /api/config/{path}`) o escritura de valores (`POST /api/config/{path}`, etc.) falla, se informa con un toast. En el caso de escritura, si el fallo se produce tras modificar un campo, el toast muestra "No se pudo actualizar el valor." y el campo revierte visualmente al valor anterior para reflejar que el cambio no fue aplicado.
- Error durante la carga del historial: si tras establecer una conexión SSE no se reciben los eventos del historial en un tiempo razonable o se produce un error en el lado del servidor que interrumpe el volcado, se puede notificar con un toast complementario al indicador de conexión.

El toast no bloquea la interacción con el resto de la aplicación; el usuario puede seguir escribiendo, enviando mensajes o navegando por la configuración mientras la notificación está visible. Si se producen varios errores consecutivos, los toasts se muestran secuencialmente (cada uno con su propia cuenta atrás de desaparición).

**8.3. Comportamiento ante pérdida de conexión SSE**

El navegador, a través de la API `EventSource`, intenta reconectar automáticamente cuando la conexión SSE se interrumpe por causas de red. El cliente web aprovecha esta capacidad y la complementa con la actualización del indicador de estado:

- En el momento en que se detecta un error en la conexión, el indicador pasa a "Conectando…" (amarillo intermitente).
- Si la reconexión tiene éxito y se recibe el evento `open`, el indicador vuelve a "Conectado" (verde) y el flujo de eventos se reanuda. No se vuelve a volcar el historial completo, sino que se continúa recibiendo eventos desde el punto en que se interrumpió la conexión (esto depende de la implementación del servidor; si el servidor no mantiene el buffer de eventos pasados, se podría perder algún mensaje intermedio, pero no es crítico en esta fase).
- Si tras varios intentos la reconexión no se consigue, el indicador pasa a "Desconectado" (rojo) y se muestra un toast informativo, por ejemplo: "Se ha perdido la conexión con el agente. Se seguirá intentando reconectar automáticamente.".

Durante el periodo de desconexión, el usuario puede seguir enviando mensajes (el POST funciona de forma independiente). Cuando la conexión se restablezca, las respuestas a esos mensajes llegarán por SSE y se incorporarán al chat. No se pierden mensajes enviados, aunque la respuesta se retrase hasta la reconexión.

**8.4. Otros estados relevantes**

- **Carga inicial y cambio de terminal**: durante el breve lapso en que se solicita una nueva conexión SSE al cambiar de `terminalId` o al cargar la página, el área de chat permanece vacía (o muestra el historial anterior hasta que se limpia) y el indicador muestra "Conectando…". No se muestra ningún spinner o loader adicional, ya que la transición es rápida y el indicador de cabecera proporciona suficiente información.
- **Envío de mensajes**: no se muestra un indicador de progreso mientras se envía un mensaje; si el envío falla, el usuario lo sabrá mediante el toast de error (como se ha descrito). El mensaje enviado se refleja inmediatamente en el área de chat, por lo que la experiencia es de respuesta instantánea en el lado del cliente.
- **Configuración**: no se muestra ningún indicador de carga al cambiar de sección en el panel de configuración ni al guardar valores. Los errores de guardado se notifican con toast y el campo revierte, tal como se detalla en el apartado 7.6.


## 9. Comportamiento del terminalId

El identificador de terminal (`terminalId`) es el único dato que el cliente necesita para que el agente Noema recupere el historial completo de conversación asociado a ese interlocutor. La interfaz web trata este identificador como un parámetro persistente y modificable en cualquier momento, garantizando que la experiencia de uso sea fluida incluso al alternar entre distintos terminales.

**9.1. Persistencia del terminalId**

El valor del `terminalId` se almacena en el almacenamiento local del navegador (`localStorage`). De esta forma:

- Al cargar la aplicación por primera vez, si no existe un `terminalId` previo, el campo se muestra vacío o con un valor por defecto (definido en la implementación) y el usuario debe introducir uno para comenzar la interacción.
- En visitas posteriores, el campo se rellena automáticamente con el último `terminalId` utilizado, agilizando la reanudación de la conversación con el mismo terminal.
- El valor persiste incluso si se cierra el navegador o se apaga el equipo, dentro de las limitaciones propias de `localStorage`.

**9.2. Edición del terminalId**

El `terminalId` se presenta en un campo de texto editable ubicado en la cabecera de la aplicación (ver apartado 5.2). El usuario puede modificar su valor en cualquier momento, escribiendo directamente sobre él.

- No se imponen restricciones de formato sobre el `terminalId` en el lado del cliente, más allá de que no sea un texto vacío. Se asume que cualquier cadena no vacía es un identificador válido que el agente sabrá interpretar.
- El campo no aplica validación asíncrona ni verifica si el `terminalId` existe en el agente; simplemente se utiliza tal cual en las peticiones. Si el identificador no es reconocido por el agente, este lo tratará como un terminal nuevo sin historial previo.

**9.3. Efectos del cambio de terminalId**

Cuando el usuario modifica el `terminalId` y confirma el cambio (por ejemplo, al pulsar Enter o al quitar el foco del campo), se desencadenan de forma automática las siguientes acciones:

1. **Cierre de la conexión SSE anterior**: el módulo `api.js` cierra explícitamente la conexión `EventSource` activa, si existe, mediante `close()`. Esto evita que se sigan recibiendo eventos del terminal anterior.

2. **Limpieza del área de chat**: el módulo `chat-ui.js` vacía por completo el área de visualización de mensajes, eliminando cualquier rastro del historial previo. Esto refleja visualmente que se está iniciando una conversación con un nuevo interlocutor.

3. **Preservación del texto de entrada**: el contenido del campo de texto de la barra de entrada de mensajes **no se modifica**. El usuario conserva intacto lo que hubiera escrito antes del cambio, lo que permite cambiar de contexto sin perder una redacción en curso.

4. **Apertura de una nueva conexión SSE**: se establece una nueva conexión `EventSource` con el endpoint `/api/console/{nuevoTerminalId}`. Al abrirse, el agente vuelca el historial completo de ese terminal (ver apartado 6.4), que se va renderizando en el área de chat.

5. **Actualización del indicador de estado**: el indicador de la cabecera pasa a "Conectando…" (punto amarillo intermitente) mientras se establece la nueva conexión y a "Conectado" (punto verde) cuando empieza a recibir eventos.

6. **Persistencia del nuevo valor**: el nuevo `terminalId` se guarda inmediatamente en `localStorage`, reemplazando al anterior.

**9.4. Ausencia de confirmación**

El cambio de `terminalId` se ejecuta de inmediato, sin cuadros de diálogo de confirmación ni advertencias. Esta decisión se justifica por dos motivos:

- No hay pérdida de información: el historial del terminal anterior permanece almacenado en el agente y el usuario puede recuperarlo en cualquier momento simplemente volviendo a escribir el `terminalId` previo.
- La inmediatez en el cambio agiliza la interacción, alineándose con la metáfora telefónica: cambiar de terminal es como colgar una llamada y marcar otro número, una acción rápida y sin fricción.

**9.5. Casos límite**

- **TerminalId vacío**: si el usuario borra el contenido del campo, la aplicación no intenta establecer una conexión SSE ni enviar mensajes. El área de chat permanece vacía y el indicador de estado puede mostrar "Desconectado" o un estado neutro hasta que se introduzca un identificador válido.
- **TerminalId nuevo (sin historial)**: si el identificador introducido no ha tenido interacciones previas con el agente, el volcado del historial será una secuencia vacía. El área de chat se muestra limpia y el usuario puede comenzar una nueva conversación desde cero.
- **Cambios rápidos consecutivos**: si el usuario modifica el `terminalId` varias veces en rápida sucesión, cada cambio cierra la conexión anterior y abre una nueva. El cliente debe manejar esta situación cancelando cualquier operación pendiente del terminal previo para evitar que eventos de conexiones ya cerradas se interfieran en la visualización actual.



## 10. Restricciones técnicas

El cliente web de Noema está concebido como una aplicación ligera, autocontenida y de ejecución estrictamente local. Las siguientes restricciones técnicas definen el marco de implementación y despliegue:

**10.1. Entorno de ejecución local y sin infraestructura externa**

La aplicación se sirve desde el mismo servidor HTTP embebido de Noema (Javalin sobre Jetty) y se ejecuta íntegramente en la máquina local del usuario. No depende de servicios externos, CDN, ni proveedores de alojamiento. El frontend se empaqueta como recursos estáticos dentro del classpath del proyecto Java y es accesible a través del propio servidor, sin necesidad de configurar un servidor web adicional.

**10.2. Tecnologías web estándar sin frameworks**

El cliente se implementa exclusivamente con HTML5, CSS3 y JavaScript (ES nativo). No se emplean frameworks, librerías de componentes ni gestores de dependencias externos. Todo el código se escribe en módulos ES, aprovechando la funcionalidad nativa de los navegadores modernos para la importación y exportación de funciones. Esta elección:

- Elimina la necesidad de transpiladores, empaquetadores o pasos de compilación.
- Reduce el tamaño total de la aplicación y los tiempos de carga.
- Garantiza la máxima compatibilidad con la filosofía de Noema de control explícito y mínimas dependencias.

**10.3. Sin autenticación ni control de acceso**

Dado el carácter personal y de laboratorio de Noema, la interfaz web no implementa ningún mecanismo de autenticación, inicio de sesión o gestión de usuarios. Se asume que el acceso a la máquina local y al servidor embebido está controlado por el propio entorno del usuario. Cualquier persona con acceso a la URL del servidor podrá interactuar con el agente, lo cual es aceptable en este contexto.

**10.4. Comunicación exclusiva con el backend de Noema**

El cliente se comunica únicamente con los endpoints del servidor HTTP embebido descritos en el análisis. No realiza peticiones a terceros ni integra APIs externas. Las llamadas se realizan mediante `fetch` nativo para las peticiones puntuales y `EventSource` para la recepción de eventos SSE.

**10.5. Persistencia limitada al navegador**

El cliente no gestiona ningún tipo de persistencia propia más allá del `localStorage` para conservar el `terminalId` entre sesiones. Todo el historial de conversación, la configuración y el estado del agente residen en el backend. Si se cierra el navegador o se borra el almacenamiento local, el único dato que se pierde es el recuerdo del último `terminalId` utilizado; el historial permanece intacto en el agente.

**10.6. Single Page Application servida desde el classpath**

La aplicación funciona como una SPA: la página se carga una sola vez y toda la navegación y actualización de contenido se gestiona dinámicamente desde JavaScript. El servidor no genera HTML en el lado del servidor; únicamente sirve los archivos estáticos y la API. Cualquier recarga de la página reinicia el estado del cliente (conexión SSE, área de chat), pero no afecta al estado del agente.

**10.7. Compatibilidad con navegadores modernos**

Se asume el uso de navegadores actuales con soporte para ES modules, `fetch`, `EventSource`, `localStorage` y las APIs web básicas. No se requiere compatibilidad con navegadores antiguos ni se utilizan polyfills.

**10.8. Escalabilidad y concurrencia**

La aplicación está diseñada para un uso personal, con un único usuario interactuando a través del navegador. No se contemplan escenarios de múltiples clientes concurrentes ni se aplican mecanismos de bloqueo desde el lado del frontend. La gestión de la concurrencia en el acceso al agente es responsabilidad exclusiva del backend.


## 11. Próximos pasos y evolución futura

El presente análisis describe el alcance inicial del cliente web de Noema, suficiente para ofrecer una experiencia de conversación y configuración equivalente a la de las interfaces Swing y CLI actuales. Una vez implementada y validada esta primera versión, se podrán abordar mejoras progresivas que amplíen las capacidades de la aplicación sin alterar su filosofía de simplicidad y ausencia de dependencias externas.

Entre las posibles evoluciones se contemplan:

- **Soporte para confirmaciones del agente**: cuando Noema solicite una confirmación humana (mecanismo `confirm`), el cliente podría mostrar un diálogo o un mensaje especial en el chat que permita responder afirmativa o negativamente, integrando esta interacción en el flujo de conversación sin necesidad de comandos textuales.
- **Ampliación de los tipos de campo en configuración**: añadir soporte para tipos adicionales definidos en `settingsui.json` que en la primera versión se ignoran, como selectores de color, campos numéricos con validación o listas dinámicas.
- **Tratamiento específico de acciones de tipo `action`**: estudiar la viabilidad de implementar en el cliente web algunas de las acciones complejas (por ejemplo, la gestión de proveedores LLM) que en Swing y CLI abren editores externos, adaptándolas a una interfaz web apropiada.
- **Migración a WebSockets**: si en el futuro se requiere una comunicación bidireccional más ágil o se necesita soporte para eventos iniciados por el servidor sin depender del polling de reconexión de SSE, se podría sustituir la conexión SSE por un canal WebSocket nativo.
- **Encapsulación de componentes reutilizables**: transformar partes de la interfaz (como el `checkedlist`, el árbol de configuración o el visor de chat con Markdown) en Custom Elements nativos para mejorar la modularidad y la reutilización del código.
- **Mejoras en la experiencia de usuario**: incorporar atajos de teclado, temas visuales alternativos (modo oscuro), o una vista de consola auxiliar para logs más detallados.

Estas ampliaciones se abordarán de manera incremental, priorizando aquellas que aporten mayor valor al uso cotidiano del laboratorio de Noema y manteniendo siempre la coherencia con su diseño autocontenido y sin dependencias externas.

## 12. Plan de implementacion

### 1. Preparación del entorno y estructura inicial de archivos**

Esta fase tiene como objetivo dejar establecida la base sobre la que se construirá el cliente web. Implica crear los archivos mínimos, ubicarlos en la estructura correcta del proyecto Noema, verificar que el servidor embebido los sirve correctamente y disponer de un mecanismo ágil para probar los cambios durante el desarrollo.

#### 1.1. Ubicación de los archivos del cliente web

El cliente web se compone exclusivamente de recursos estáticos. Deben alojarse dentro del proyecto Java de Noema, en una ubicación que el servidor HTTP embebido (Javalin) pueda servir como `static files` desde el classpath.

- **Ruta propuesta**: `src/main/resources/webapp/`
- Esta carpeta contendrá todos los archivos `.html`, `.css` y `.js` del cliente.
- Al empaquetar la aplicación, estos recursos quedarán dentro del JAR y serán accesibles desde el navegador sin necesidad de un servidor externo.

#### 1.2. Creación de la estructura de archivos inicial

Dentro de `src/main/resources/webapp/` se crearán los siguientes archivos vacíos o con contenido mínimo:

- **`index.html`**: página principal de la SPA. Contendrá inicialmente:
  - La declaración DOCTYPE y metadatos básicos (charset UTF-8, viewport para diseño responsivo).
  - Etiquetas `<link>` para las hojas de estilo (un único archivo `styles.css` que se creará más adelante; en esta fase se puede omitir o dejar la referencia comentada).
  - La estructura semántica básica de la aplicación: cabecera (con un `div` para el `terminalId`, el indicador de estado y el botón de configuración), área de chat (un `div` contenedor), barra de entrada (un `input` y un `button`), y un contenedor para toasts.
  - Etiquetas `<script type="module">` que carguen `api.js`, `chat-ui.js` y `config-ui.js` (aunque estos archivos no tengan aún funcionalidad). El orden de carga debe respetar las dependencias: primero `api.js`, luego `chat-ui.js` y `config-ui.js`, y finalmente un pequeño script inline o un `main.js` que orqueste la inicialización.
  - Un `<script type="module">` inline o un archivo `main.js` separado que ejecute una función de inicialización cuando el DOM esté listo (por ejemplo, `document.addEventListener('DOMContentLoaded', init)` donde `init` recupera el `terminalId` de `localStorage` y establece la conexión SSE si procede). En esta fase, la función `init` puede ser un simple `console.log` que confirme que la aplicación arranca.

- **`api.js`**: módulo de comunicación. Inicialmente exportará funciones vacías o con implementaciones simuladas (stubs) que devuelvan promesas resueltas o rechazadas para probar el flujo de llamadas. Funciones mínimas a declarar:
  - `connectSSE(terminalId)`: devuelve un objeto que simula un `EventSource` o simplemente imprime en consola.
  - `sendMessage(terminalId, message)`: petición POST, inicialmente con un `console.log` y una promesa resuelta.
  - `fetchConfig()`: GET a `/api/config`, stub.
  - `setConfigValue(path, value)`: POST a `/api/config/{path}`, stub.
  - `fetchConfigUI()`: GET a `/api/config/ui`, stub.

- **`chat-ui.js`**: módulo de interfaz de chat. Exportará funciones como `initChat(terminalId)`, `renderMessage(type, content)`, `clearChat()`, etc. Al inicio estarán vacías o con `console.log`.

- **`config-ui.js`**: módulo de interfaz de configuración. Exportará `initConfig()`, `openConfigPanel()`, `closeConfigPanel()`, etc., con implementaciones vacías.

- **`styles.css`** (opcional en esta fase): se puede crear un archivo vacío o con estilos mínimos para probar que se sirve correctamente. Los estilos se desarrollarán incrementalmente durante las fases posteriores.

#### 1.3. Configuración del servidor embebido para servir los estáticos

Se debe verificar y, si es necesario, ajustar la configuración de Javalin en el proyecto Noema para que sirva los archivos estáticos desde la carpeta `webapp` en el classpath.

- Añadir (si no existe) el bloque de configuración de `staticFiles` en la creación de la instancia de Javalin:
  - `config.staticFiles.add(staticFiles -> { staticFiles.directory = "/webapp"; staticFiles.location = Location.CLASSPATH; });`
- Confirmar que la ruta base `/` redirige a `index.html` o que Javalin sirve `index.html` por defecto al acceder a la raíz.
- Asegurarse de que los archivos `.js` se sirven con el MIME type correcto (`application/javascript` o `text/javascript`). Javalin normalmente lo infiere correctamente.

#### 1.4. Preparación del flujo de trabajo de desarrollo

Dado que el cliente es una SPA que se comunica con el backend de Noema, se necesita una forma de probar los cambios de forma ágil:

- **Arranque del backend**: se debe disponer de un script o comando que inicie Noema con el servidor HTTP embebido en un puerto fijo (por ejemplo, `8080`). Durante el desarrollo, se usará `mvn exec:java` o similar.
- **Prueba sin backend**: opcionalmente, se puede usar un servidor HTTP simple (como `python3 -m http.server` o `npx serve`) para servir los archivos estáticos directamente desde `src/main/resources/webapp/` mientras se desarrolla la interfaz, sin necesidad de arrancar Noema. Esto acelera la iteración sobre HTML/CSS/JS. En ese caso, las llamadas a la API fallarán, pero se pueden simular con stubs. Se recomienda esta práctica para las fases iniciales de maquetación y lógica de UI, y luego pasar a probar contra el backend real cuando la comunicación esté implementada.
- **Recarga automática**: no se emplearán herramientas de hot-reload complejas; al ser HTML+JS vanilla, basta con recargar el navegador manualmente. Se puede considerar la extensión Live Server de VS Code si se desea recarga automática al guardar.

#### 1.5. Verificación inicial

Una vez realizados los pasos anteriores, se deben ejecutar estas comprobaciones:

1. **Arrancar Noema** (o el servidor estático alternativo) y acceder con el navegador a `http://localhost:8080` (o el puerto configurado).
2. **Verificar que se carga `index.html`**: se debe ver la estructura básica (cabecera, área de chat vacía, campo de entrada y botón).
3. **Abrir la consola del navegador** y comprobar que:
   - No hay errores de carga de módulos (los scripts `api.js`, `chat-ui.js`, `config-ui.js` se importan correctamente).
   - Aparece el mensaje de inicialización (`console.log` de la función `init`).
   - El `terminalId` se recupera de `localStorage` (si existía) y se muestra en la cabecera (si ya se ha añadido esa lógica al HTML).
4. **Verificar que los archivos estáticos son accesibles individualmente**: por ejemplo, `http://localhost:8080/api.js` debería mostrar el contenido del archivo JavaScript.
5. **Verificar las referencias a módulos**: si se usan `import` y `export`, confirmar que el navegador no muestra errores de tipo MIME (el servidor debe servir los `.js` con el tipo correcto).

Tras estas verificaciones, el entorno y la estructura inicial estarán listos para comenzar el desarrollo incremental de las funcionalidades. Cualquier ajuste necesario en las rutas, configuración del servidor o división de archivos se abordará en este momento para evitar arrastrar problemas a fases posteriores.

### Fase 1: Maquetación HTML y estilos base (index.html + CSS)

Esta fase tiene como objetivo construir el esqueleto visual completo de la aplicación y dotarlo de los estilos necesarios para que la interfaz sea funcional y estéticamente cercana a la versión final. Al término de esta fase, la aplicación debe mostrar en el navegador la estructura de cabecera, área de chat, barra de entrada y contenedor de toasts, con sus proporciones, colores y tipografías correctamente aplicados, aunque sin lógica de comunicación ni comportamiento dinámico (que se añadirá en fases posteriores).

#### 1.1. Estructura semántica del HTML

Se parte del `index.html` creado en la fase de preparación. Sobre él se refinará el marcado para reflejar con precisión todos los elementos de la interfaz descritos en el análisis.

**Cabecera (`<header>`)**
- Contendrá tres bloques diferenciados:
  - **Identificador de terminal**: una etiqueta `<label>` asociada a un `<input type="text">` para el `terminalId`. El input tendrá un `id` único (por ejemplo, `terminal-id`) y un atributo `placeholder` con un texto sugerido como "Terminal ID". Se incluirá un pequeño icono o texto descriptivo ("Terminal:") para mejorar la claridad.
  - **Indicador de estado de conexión**: un `<span>` o `<div>` que contiene un elemento para el punto coloreado (un `<span>` con clase `status-dot`) y otro para el texto (`status-text`). Los valores iniciales serán "Conectando…" y el punto en amarillo, simulando el estado al cargar la página.
  - **Botón de configuración**: un `<button>` con un icono de engranaje (puede ser un carácter Unicode como ⚙ o una imagen SVG simple insertada inline). El botón no tendrá funcionalidad aún, pero estará presente para verificar su posición y estilo.

**Área de chat (`<main>` o `<section>`)**
- Un contenedor principal con `id="chat-area"` que actuará como zona de desplazamiento vertical para los mensajes.
- Inicialmente estará vacío. Para facilitar la verificación visual, se pueden insertar manualmente algunos mensajes de ejemplo (usuario, agente, log, error) con las clases CSS correctas, y luego eliminarlos al iniciar la fase de lógica de chat. Esto permite comprobar que los estilos de alineación y agrupación funcionan.
- Cada mensaje será un `<div>` o `<article>` con una clase que identifique su tipo: `message-user`, `message-agent`, `message-log`, `message-error`. Dentro llevará el contenido textual, y opcionalmente una marca de tiempo en un `<time>` o `<span>` secundario.
- Los mensajes del mismo tipo y consecutivos se agruparán visualmente en un mismo bloque. Para ello, en la maquetación estática se puede simular colocando varios `<p>` dentro del mismo `<div>` de mensaje.

**Barra de entrada (`<footer>`)**
- Un `<form>` (o un `<div>` con comportamiento similar) que contiene:
  - Un `<textarea>` o `<input type="text">` para redactar mensajes, con `id="message-input"` y un `placeholder` como "Escribe tu mensaje…".
  - Un `<button type="submit">` con texto "Enviar" o un icono de flecha/flecha hacia arriba.
- El formulario no debe provocar recarga de página (se evitará con `event.preventDefault()` cuando se añada lógica JS). Por ahora, el botón no ejecutará ninguna acción.

**Contenedor de toasts (`<div id="toast-container">`)**
- Un `<div>` fijo en la esquina superior derecha, inicialmente vacío, que alojará dinámicamente los toasts de error o notificación.
- Cada toast será un `<div>` con clase `toast` y un botón de cierre opcional (aunque desaparecerá automáticamente).

**Panel de configuración (oculto inicialmente)**
- Un `<aside>` o `<div>` con `id="config-panel"` que contenga:
  - Un botón de cierre (X) en la esquina superior derecha.
  - Un contenedor para el árbol de navegación (`<nav id="config-tree">`).
  - Un contenedor para el área de contenido (`<section id="config-content">`).
- Inicialmente estará oculto mediante CSS (`display: none`). En fases posteriores se mostrará al pulsar el botón de configuración.

#### 1.2. Estilos CSS base

Se creará o completará el archivo `styles.css` referenciado desde `index.html`. Los estilos se organizarán por secciones para facilitar el mantenimiento. Se usarán variables CSS para colores y tamaños recurrentes, permitiendo ajustes futuros (por ejemplo, para un tema oscuro).

**Variables y reseteo básico**
- Definición de variables en `:root`:
  - Colores de fondo de mensajes: `--bg-user: #e3f2fd` (gris claro azulado), `--bg-agent: #ffffff`, `--bg-log: #fff9c4` (amarillo pálido), `--bg-error: #ffcdd2` (rojo claro).
  - Colores de texto: `--text-primary: #212121`, `--text-secondary: #757575`.
  - Color de acento (botón enviar, enlaces): `--accent: #1565c0`.
  - Color del punto de estado: `--status-connected: #4caf50`, `--status-connecting: #ffeb3b`, `--status-disconnected: #f44336`.
  - Bordes y sombras suaves.
- Reset mínimo: `box-sizing: border-box` universal, márgenes y padding a cero en `body`, `font-family` base (`system-ui, sans-serif`).

**Layout general**
- `body`: ocupará toda la altura de la ventana (`height: 100vh`), sin scroll externo, usando `display: flex; flex-direction: column;`.
- Cabecera: altura fija (por ejemplo, `56px`), con `display: flex; align-items: center; justify-content: space-between; padding: 0 16px; border-bottom: 1px solid #e0e0e0;`.
- Área de chat: ocupará el espacio restante con `flex: 1; overflow-y: auto; padding: 16px;`.
- Barra de entrada: altura fija o mínima, con `border-top: 1px solid #e0e0e0; padding: 8px 16px; display: flex; gap: 8px;`.

**Cabecera**
- Campo de `terminalId`: estilo limpio, borde sutil, `border-radius`, padding interior, ancho máximo (por ejemplo, `200px`).
- Indicador de estado: el punto (`status-dot`) será un círculo de `10px` de diámetro, con `border-radius: 50%` y color de fondo según una clase (`connected`, `connecting`, `disconnected`). La animación intermitente para "connecting" se hará con una animación CSS (`@keyframes blink`).
- Botón de configuración: estilo minimalista, fondo transparente, icono centrado, con efecto hover.

**Área de chat**
- Los mensajes se mostrarán como bloques con `max-width: 70%`, `border-radius: 8px`, `padding: 8px 12px`, `margin-bottom: 8px`, y sombra suave.
- **Alineación**:
  - `message-user`: `margin-left: auto;` (alineado a la derecha), fondo `--bg-user`.
  - `message-agent`, `message-log`, `message-error`: `margin-right: auto;` (alineados a la izquierda), fondos correspondientes.
- **Agrupación**: cuando varios elementos del mismo tipo están dentro del mismo contenedor de mensaje, los márgenes entre ellos serán menores (por ejemplo, `p + p { margin-top: 4px; }`). Esto simula la agrupación que luego se hará dinámicamente en JS.
- Tipografía: `font-size: 14px; line-height: 1.5;`, texto seleccionable por defecto.
- Scroll suave: `scroll-behavior: smooth;` en el contenedor de chat.

**Barra de entrada**
- `textarea` o `input`: `flex: 1;`, borde similar al del campo de terminal, `border-radius`, padding, altura mínima. Si se usa `<textarea>`, se puede hacer autoajustable en altura más adelante; por ahora, altura fija.
- Botón enviar: `background-color: var(--accent); color: white; border: none; border-radius: 4px; padding: 8px 16px; cursor: pointer;`.

**Toasts**
- Contenedor: `position: fixed; top: 16px; right: 16px; z-index: 1000; display: flex; flex-direction: column; gap: 8px;`.
- Toast individual: `background-color: #333; color: #fff; padding: 12px 16px; border-radius: 4px; opacity: 1; transition: opacity 0.3s;`. Se usará una clase `.toast-hide` con `opacity: 0;` para la desaparición.
- Tipos de toast (error, info): colores de fondo diferentes (rojo oscuro para error, azul oscuro para info).

**Panel de configuración**
- Ocupa toda la pantalla o se superpone como un modal lateral derecho, con `position: fixed; top: 0; right: 0; width: 80vw; max-width: 600px; height: 100vh; background: #fff; box-shadow: -2px 0 8px rgba(0,0,0,0.2); z-index: 500; display: flex; flex-direction: column;`.
- Cuando esté oculto: `display: none;` o `transform: translateX(100%);` con transición (opcional en esta fase; con display none es suficiente).
- Árbol de configuración: ocupa la parte izquierda (30% del ancho) o es una lista colapsable. Inicialmente se puede maquetar estáticamente con un `<ul>` anidado para representar la jerarquía.
- Contenido de configuración: área a la derecha, con padding, donde se renderizarán los campos.

#### 1.3. Diseño responsivo

Aunque el uso principal será en escritorio, se añadirán reglas básicas para pantallas pequeñas:

- En dispositivos con anchura inferior a `600px`:
  - La cabecera se apila verticalmente si es necesario.
  - El `max-width` de los mensajes se amplía al 90%.
  - El panel de configuración ocupa el 100% del ancho.
  - La barra de entrada mantiene su usabilidad táctil.

Estos ajustes se harán con una media query `@media (max-width: 600px)`.

#### 1.4. Verificación de la fase

Al finalizar esta fase, se debe poder:

1. Cargar `index.html` en el navegador (desde el servidor embebido o desde un servidor estático alternativo) y ver todos los elementos descritos en sus posiciones correctas.
2. Comprobar visualmente los mensajes de ejemplo: el de usuario a la derecha, los demás a la izquierda, con los colores de fondo asignados.
3. Verificar que el indicador de estado muestra el punto parpadeante en amarillo (si se ha incluido la animación).
4. Confirmar que el diseño se adapta al estrechar la ventana del navegador por debajo de 600px.
5. Inspeccionar con las herramientas de desarrollo que no hay errores de carga de archivos CSS o fuentes.
6. Probar la selección de texto en el área de chat: debe ser posible seleccionar y copiar fragmentos de los mensajes.

Una vez superada esta verificación, la base visual estará lista para pasar a la implementación de la lógica de comunicación y comportamiento dinámico en las fases siguientes. Cualquier ajuste estético o estructural detectado durante el desarrollo posterior se corregirá sobre esta base.

### Fase 2: Módulo de comunicación con el backend (api.js)

Esta fase se centra en implementar todas las funciones de comunicación con el servidor HTTP de Noema que serán utilizadas por los módulos de interfaz de usuario en las fases posteriores. Al finalizar, el módulo `api.js` debe exportar funciones completamente operativas para la conexión SSE, el envío de mensajes y la lectura/escritura de configuración, con un manejo de errores básico que permita a las capas superiores reaccionar adecuadamente.

#### 2.1. Estructura del módulo

El archivo `api.js` se organizará como un módulo ES que exporta las siguientes funciones:

- `connectSSE(terminalId, eventHandlers)` — Establece la conexión SSE y devuelve un objeto de control.
- `sendMessage(terminalId, message)` — Envía un mensaje de usuario al agente.
- `fetchConfig()` — Obtiene la configuración completa.
- `fetchConfigValue(path)` — Obtiene un valor específico de configuración.
- `setConfigValue(path, value)` — Establece un valor String.
- `setConfigList(path, values)` — Establece una lista de valores.
- `setConfigChecked(path, value, checked)` — Actualiza un elemento de lista marcada.
- `fetchConfigUI()` — Obtiene el descriptor settingsui.json.

Se definirá una constante `API_BASE` con la URL base del servidor (por defecto `http://localhost:8080`), que permitirá cambiar el origen fácilmente si fuera necesario.

#### 2.2. Función `connectSSE(terminalId, eventHandlers)`

Esta función establece la conexión Server-Sent Events y la gestiona durante todo su ciclo de vida.

**Parámetros:**
- `terminalId`: identificador del terminal para el que se abre la conexión.
- `eventHandlers`: objeto con callbacks para los distintos tipos de evento que puede emitir el servidor:
  - `onHistoryMessage(type, data)`: llamado para cada mensaje del historial durante el volcado inicial. Recibe el tipo (`user-message`, `response`, `log`, `error`) y los datos del evento.
  - `onResponse(data)`: llamado cuando llega una respuesta del agente en tiempo real.
  - `onLog(data)`: llamado cuando llega un log de herramienta.
  - `onError(data)`: llamado cuando llega un evento de error del agente.
  - `onConnectionError(error)`: llamado cuando la conexión SSE experimenta un error de red o se cierra inesperadamente.
  - `onConnectionOpen()`: llamado cuando la conexión SSE se establece exitosamente.

**Implementación:**
1. Construir la URL: `${API_BASE}/api/console/${encodeURIComponent(terminalId)}`.
2. Crear una instancia de `EventSource` con esa URL.
3. Registrar el evento `open` de `EventSource`: al dispararse, llamar a `eventHandlers.onConnectionOpen()`.
4. Registrar el evento `error` de `EventSource`:
   - Si `EventSource.readyState` es `CONNECTING`, significa que está intentando reconectar; se puede notificar pero no es crítico.
   - Si es `CLOSED`, llamar a `eventHandlers.onConnectionError()` con un objeto de error descriptivo.
5. Registrar listeners para cada tipo de evento esperado del servidor:
   - `addEventListener('user-message', ...)`: parsear el `event.data` como JSON y llamar a `eventHandlers.onHistoryMessage('user-message', data)`.
   - `addEventListener('response', ...)`: parsear JSON y llamar a `eventHandlers.onResponse(data)` si la conexión ya ha terminado el volcado inicial, o a `onHistoryMessage('response', data)` durante el volcado.
   - `addEventListener('log', ...)`: ídem con `onLog` / `onHistoryMessage`.
   - `addEventListener('error', ...)`: ídem con `onError` / `onHistoryMessage`.
   - **Diferenciación entre historial y tiempo real**: para distinguir si un evento pertenece al volcado inicial o es en tiempo real, se puede usar una bandera interna. El servidor podría indicar el fin del historial con un evento especial (`history-end`) o simplemente se puede inferir tras un breve tiempo sin eventos o mediante un mensaje específico. En esta fase, se puede optar por un enfoque simplificado: todos los eventos recibidos en los primeros N segundos tras la apertura se tratan como historial; o mejor, se añade un evento `history-end` en el servidor y, mientras no se reciba, todos los eventos van a `onHistoryMessage`. Para que el plan sea completo, se asume que el servidor enviará un evento `history-end` cuando termine el volcado. Si no se implementa en el backend, se puede usar un temporizador (ej: 500ms sin eventos) como heurística.
6. Devolver un objeto de control con:
   - `close()`: llama a `EventSource.close()` y limpia los listeners.
   - `getReadyState()`: devuelve el estado actual de la conexión.
   - `addEventListener`, `removeEventListener` si se necesita exposición adicional.

**Manejo de errores:**
- Si el navegador no soporta `EventSource` (altamente improbable en navegadores modernos), lanzar un error descriptivo.
- Los errores de parseo JSON en los datos del evento deben capturarse y notificarse mediante `eventHandlers.onConnectionError` o un callback específico, evitando que rompan la conexión.

#### 2.3. Función `sendMessage(terminalId, message)`

Envía el mensaje del usuario al agente y devuelve una promesa que indica si fue aceptado.

**Parámetros:**
- `terminalId`: identificador del terminal.
- `message`: texto del mensaje.

**Implementación:**
1. Construir la URL: `${API_BASE}/api/chat/${encodeURIComponent(terminalId)}`.
2. Realizar una petición `fetch` con método `POST`, cabecera `Content-Type: application/json` y cuerpo `JSON.stringify({ message })`.
3. Verificar la respuesta:
   - Si el status es `202 Accepted` (u otro código 2xx), resolver la promesa con `{ accepted: true }`.
   - Si el status es otro (4xx, 5xx), rechazar la promesa con un objeto de error que incluya el código de estado y un mensaje descriptivo. El cuerpo de la respuesta se puede intentar parsear como JSON para obtener detalles adicionales.
4. En caso de error de red (`fetch` lanza excepción), rechazar la promesa con un error indicando "Error de conexión" o similar.
5. La función no espera la respuesta del agente, solo la aceptación del mensaje.

**Valor de retorno:** `Promise<{ accepted: boolean }>`.

#### 2.4. Funciones de configuración

Todas las funciones de configuración seguirán un patrón similar basado en `fetch`.

**`fetchConfig()`**
- URL: `${API_BASE}/api/config`
- Método: `GET`
- Retorna: `Promise` que resuelve con el objeto JSON completo de configuración.
- Manejo de errores: rechazar con mensaje si la respuesta no es exitosa.

**`fetchConfigValue(path)`**
- URL: `${API_BASE}/api/config/${encodeURIComponent(path)}`
- Método: `GET`
- Retorna: `Promise` con el valor (string, objeto, array según el nodo).

**`setConfigValue(path, value)`**
- URL: `${API_BASE}/api/config/${encodeURIComponent(path)}`
- Método: `POST`
- Cabecera: `Content-Type: application/json`
- Cuerpo: `JSON.stringify({ value })` o simplemente el valor como string si el endpoint espera un body crudo. Para mantener flexibilidad, se enviará `{ "value": value }` y se documentará la convención.
- Retorna: `Promise` que resuelve si el POST fue exitoso (2xx).

**`setConfigList(path, values)`**
- URL: `${API_BASE}/api/config/${encodeURIComponent(path)}/list`
- Método: `POST`
- Cuerpo: `JSON.stringify(values)` (array de strings).
- Retorna: `Promise`.

**`setConfigChecked(path, value, checked)`**
- URL: `${API_BASE}/api/config/${encodeURIComponent(path)}/checked`
- Método: `POST`
- Cuerpo: `JSON.stringify({ value, checked })`.
- Retorna: `Promise`.

**`fetchConfigUI()`**
- URL: `${API_BASE}/api/config/ui`
- Método: `GET`
- Retorna: `Promise` con el objeto JSON de `settingsui.json`.

**Observaciones comunes:**
- Todas las funciones deben usar `encodeURIComponent` en los segmentos de la URL que provengan de parámetros para evitar inyecciones y problemas con caracteres especiales (especialmente en las rutas de configuración que contienen `/`).
- El manejo de errores será uniforme: cualquier respuesta no exitosa o error de red rechazará la promesa con un objeto `{ status, message }`. Esto facilitará que `chat-ui.js` y `config-ui.js` muestren toasts con el mensaje apropiado.

#### 2.5. Verificación de la fase

Para comprobar que el módulo funciona correctamente antes de integrarlo con la UI:

1. **Prueba de `sendMessage`**: desde la consola del navegador, importar la función (si el módulo lo permite) o añadir una llamada de prueba en `main.js`. Enviar un mensaje a un `terminalId` de prueba y verificar en la pestaña Network que la petición POST se realiza con los datos correctos y que la promesa se resuelve o rechaza según corresponda.
2. **Prueba de `connectSSE`**: invocar la función con un `terminalId` y callbacks que impriman en consola los eventos recibidos. Arrancar Noema y comprobar que:
   - Se recibe el historial (eventos `user-message`, `response`, etc.) al abrir la conexión.
   - Al enviar un mensaje por otra vía (CLI o Swing) se recibe la respuesta por SSE.
   - Al cerrar la conexión (llamando a `close()` en el objeto de control) dejan de llegar eventos.
3. **Prueba de funciones de configuración**: ejecutar `fetchConfig()` y verificar que devuelve un objeto con la configuración actual. Probar `setConfigValue` con una ruta conocida y luego `fetchConfigValue` para confirmar que el cambio se aplicó. Probar los endpoints de lista y checkedlist de forma similar.
4. **Verificar manejo de errores**: desconectar el servidor y comprobar que las promesas se rechazan con mensajes descriptivos. Probar rutas incorrectas y verificar que los errores 404 se capturan adecuadamente.

Una vez superadas estas pruebas, el módulo `api.js` estará listo para ser consumido por `chat-ui.js` en la siguiente fase.

### Fase 3: Lógica del chat (chat-ui.js)

Esta fase implementa el corazón de la experiencia de usuario: la visualización del historial, la recepción de eventos en tiempo real, el envío de mensajes y la coordinación con el módulo de comunicación `api.js`. Al finalizar, la aplicación debe permitir mantener una conversación completa con el agente desde el navegador, con el historial agrupado, el indicador de estado funcionando y la posibilidad de cambiar de terminal preservando el texto de entrada.

#### 3.1. Estructura del módulo

El archivo `chat-ui.js` exportará un conjunto de funciones que serán invocadas desde el punto de entrada de la aplicación (`main.js` o el script inline en `index.html`). Las funciones principales serán:

- `initChat(terminalId)`: inicia la sesión de chat para un terminal dado.
- `clearChat()`: vacía el área de mensajes.
- `addMessage(type, content, fromHistory)`: añade un mensaje al área de chat, aplicando agrupación si procede.
- `setConnectionStatus(status)`: actualiza el indicador visual de estado de la conexión.
- `onTerminalIdChanged(newTerminalId)`: gestiona el cambio de identificador de terminal.

Además, mantendrá referencias a elementos del DOM (contenedor de chat, indicador de estado, campo de entrada, etc.) obtenidas al inicializarse.

#### 3.2. Referencias al DOM

Al cargar el módulo, se obtendrán y almacenarán las referencias a los siguientes elementos del documento (todos ellos creados en la Fase 1):

- `chatArea`: `#chat-area`
- `statusDot`: `.status-dot`
- `statusText`: `.status-text`
- `terminalInput`: `#terminal-id`
- `messageInput`: `#message-input`
- `sendButton`: botón de envío
- `toastContainer`: `#toast-container`

Si algún elemento no existe, se debe lanzar un error descriptivo para facilitar la depuración.

#### 3.3. Inicialización del chat (`initChat`)

La función `initChat(terminalId)` se encargará de iniciar la conexión SSE y configurar los manejadores de eventos.

**Pasos:**

1. **Llamar a `clearChat()`** para asegurarse de que el área de mensajes está limpia antes de cargar el historial del nuevo terminal.
2. **Establecer el estado de la conexión** a `'connecting'` mediante `setConnectionStatus('connecting')`.
3. **Crear una variable de control** para saber si el volcado del historial ha finalizado. Se usará una bandera `historyDumpComplete = false`.
4. **Llamar a `api.connectSSE(terminalId, eventHandlers)`**, pasando un objeto con los siguientes callbacks:

   - **`onConnectionOpen()`**: llama a `setConnectionStatus('connected')`.
   - **`onHistoryMessage(type, data)`**: invoca `addMessage(type, data.content, true)` para cada evento recibido durante el volcado.
   - **`onResponse(data)`**: si `historyDumpComplete` es `false`, se trata como historial y se llama a `addMessage('response', data.content, true)`. Si ya ha terminado el volcado, se llama a `addMessage('response', data.content, false)` para mensajes en tiempo real.
   - **`onLog(data)`**: ídem, tipo `'log'`.
   - **`onError(data)`**: ídem, tipo `'error'`.
   - **`onConnectionError(error)`**: actualiza el estado a `'disconnected'` y muestra un toast con el mensaje "Conexión perdida. Reintentando automáticamente…".
   - Además, se añadirá un listener para el evento SSE `'history-end'` que cambiará `historyDumpComplete = true`. Si el servidor no envía este evento, se puede implementar un temporizador que tras 500 ms sin recibir nuevos eventos durante el volcado, considere que el historial ha terminado. Para simplificar, se asumirá que el servidor lo implementa.

5. **Almacenar el objeto de control** devuelto por `connectSSE` (con el método `close()`) para poder cerrar la conexión cuando se cambie de terminal.

#### 3.4. Renderizado de mensajes y agrupación

La función `addMessage(type, content, fromHistory)` maneja la inserción de mensajes en el DOM respetando las reglas de presentación definidas en el análisis.

**Lógica de agrupación:**

- Se mantiene una variable `lastMessageBlock` que apunta al último elemento del DOM que contiene mensajes, junto con su tipo (`lastBlockType`).
- Si el `type` del nuevo mensaje coincide con `lastBlockType`, el contenido se añade al bloque existente (por ejemplo, se crea un nuevo `<p>` dentro del mismo `<div>`), sin modificar la alineación ni el color de fondo del bloque.
- Si el tipo es diferente, se crea un nuevo bloque `<div class="message-${type}">`, se inserta en el `chatArea` y se actualizan las referencias.
- Para el primer mensaje, siempre se crea un nuevo bloque.

**Creación de un bloque de mensaje:**

- Se crea un elemento `div` con la clase `message message-${type}`.
- Se le añade el contenido como un hijo `<p>` (o varios si se agrupan después). El contenido se inserta como texto; si se desea soporte Markdown básico, se puede transformar en HTML simple (negrita, cursiva, etc.) en este momento, pero esa funcionalidad se detallará en la Fase 3 adicional; por ahora se inserta como texto plano o con un escape HTML para evitar XSS.
- Se aplican las reglas CSS (ya definidas en la Fase 1) para la alineación: `message-user` a la derecha (`margin-left: auto`), el resto a la izquierda (`margin-right: auto`).
- Se garantiza que el texto sea seleccionable (comportamiento por defecto).

**Scroll automático:**

- Tras añadir un nuevo bloque o añadir contenido a uno existente, se debe desplazar el `chatArea` al final (`chatArea.scrollTop = chatArea.scrollHeight`), excepto si el usuario ha hecho scroll hacia atrás para leer mensajes anteriores.
- Para detectar si el usuario está en la parte inferior, se puede comprobar si `chatArea.scrollTop + chatArea.clientHeight >= chatArea.scrollHeight - 50` (con un margen de 50px). Si es cierto, se fuerza el scroll al final; si no, se mantiene la posición actual.

#### 3.5. Envío de mensajes de usuario

El evento de envío se asociará al botón "Enviar" y a la tecla Enter en el campo de entrada.

**Procedimiento:**

1. Obtener el texto del `messageInput` y eliminar espacios en blanco al principio y al final.
2. Si el texto está vacío, no hacer nada.
3. Obtener el `terminalId` actual desde el campo de la cabecera.
4. Llamar a `api.sendMessage(terminalId, text)`. Mientras la promesa no se resuelva, se puede mostrar el mensaje inmediatamente en el chat (optimistic UI) para dar sensación de inmediatez: se llama a `addMessage('user', text, false)`.
5. Limpiar el `messageInput`.
6. Si la promesa se resuelve correctamente (202), no se necesita hacer nada adicional (el mensaje del usuario ya está en pantalla).
7. Si la promesa es rechazada, se debe notificar al usuario con un toast ("Error al enviar el mensaje. Inténtelo de nuevo.") y, opcionalmente, se podría marcar visualmente el mensaje del usuario como no entregado (por ejemplo, con un borde rojo o un icono de advertencia). En esta fase se puede optar por mostrar el toast y dejar el mensaje en el chat sin indicador especial.

#### 3.6. Cambio de terminalId

La función `onTerminalIdChanged(newTerminalId)` se invocará cuando el usuario modifique el campo de `terminalId` (por ejemplo, al presionar Enter o al perder el foco, dependiendo de la decisión de UX; se puede configurar para ambos eventos). Su comportamiento es:

1. Si el nuevo valor es igual al anterior (almacenado en una variable interna), no hacer nada.
2. Si es diferente y no está vacío:
   - **Cerrar la conexión SSE actual**: llamar al método `close()` del objeto de control almacenado.
   - **Limpiar el área de chat** con `clearChat()`.
   - **Guardar el nuevo `terminalId` en `localStorage`**.
   - **Preservar el contenido del `messageInput`** (simplemente no se toca, ya que no se limpia).
   - **Invocar `initChat(newTerminalId)`** para iniciar la nueva conexión y cargar el historial del nuevo terminal.
3. Si el nuevo valor está vacío, se podría cerrar la conexión y dejar el chat limpio, sin iniciar una nueva conexión, y actualizar el indicador de estado a un estado neutro.

#### 3.7. Gestión del indicador de estado

La función `setConnectionStatus(status)` actualiza el punto y el texto en la cabecera según el estado recibido:

- `'connected'`: punto con clase `connected` (verde), texto "Conectado".
- `'connecting'`: punto con clase `connecting` (amarillo, animación), texto "Conectando…".
- `'disconnected'`: punto con clase `disconnected` (rojo), texto "Desconectado".

Se implementa aplicando/quitando clases CSS y cambiando el `textContent` del elemento `statusText`.

#### 3.8. Notificación de errores (toast)

Se creará una función auxiliar `showToast(message, type = 'error')` dentro de `chat-ui.js` (o compartida en `main.js`) que añada un elemento toast al contenedor `toastContainer`. El toast tendrá:

- Un fondo rojo (error) o azul (info).
- El texto del mensaje.
- Una animación de desvanecimiento tras unos 5 segundos, tras lo cual se elimina del DOM.
- Se usará `setTimeout` para programar la eliminación.

Esta función será usada tanto para errores de envío como para notificar desconexiones.

#### 3.9. Verificación de la fase

Al finalizar esta fase, se deben poder realizar las siguientes pruebas:

1. **Carga inicial**: al acceder a la página con un `terminalId` que tenga historial, se debe mostrar el historial completo con los mensajes correctamente alineados y agrupados. El indicador de estado pasa a "Conectado".
2. **Envío de mensaje**: escribir un mensaje y enviarlo. Debe aparecer inmediatamente a la derecha, y posteriormente la respuesta del agente debe aparecer a la izquierda cuando llegue por SSE.
3. **Agrupación de logs**: si el agente ejecuta varias herramientas consecutivas, los logs deben aparecer dentro del mismo bloque (fondo amarillo) y no como bloques separados.
4. **Reconexión y estado**: si se detiene el servidor, el indicador debe pasar a "Desconectado" y mostrar un toast. Al reiniciar el servidor, la conexión se debe restablecer automáticamente (gracias a la reconexión de EventSource) y el indicador volver a "Conectado".
5. **Cambio de terminal**: modificar el `terminalId`, verificar que el chat se limpia y carga el historial del nuevo terminal. El texto en el input se mantiene. Al volver al terminal anterior, debe aparecer su historial.
6. **Selección de texto**: todo el texto del chat debe poder seleccionarse y copiarse sin problemas.

Con estas pruebas superadas, la funcionalidad de conversación estará completa y se podrá pasar a la fase de configuración.


### Fase 4: Indicador de conexión y notificaciones de error (refinamiento)

Aunque en la Fase 3 se ha implementado la base del indicador de estado y los toasts, esta fase se dedica a pulir su comportamiento, garantizar la robustez y cubrir todos los casos límite de forma independiente. Al finalizarla, la aplicación mostrará de manera fiable el estado real de la conexión y notificará cualquier incidencia sin afectar la experiencia de chat.

#### 4.1. Comportamiento detallado del indicador de estado

El indicador compuesto por punto y texto debe reflejar fielmente los cambios en la conexión SSE. Se implementará una máquina de estados sencilla con las transiciones:

- **Al llamar a `initChat`**: estado `connecting`.
- **Al recibir `onConnectionOpen`**: estado `connected`.
- **Al recibir `onConnectionError`** cuando la reconexión automática de EventSource está activa: estado `connecting` (punto amarillo intermitente).
- **Cuando la reconexión falla definitivamente** (por ejemplo, tras varios intentos o cuando el navegador cierra la conexión): estado `disconnected` y se muestra un toast.
- **Al cambiar de terminal o al cerrar manualmente la conexión**: no se muestra un estado especial; simplemente se inicia de nuevo con `connecting`.

Se debe evitar que el indicador parpadee innecesariamente: para ello, se puede incorporar un retardo antes de pasar a `connecting` durante una reconexión (por ejemplo, solo mostrar "Conectando…" si la desconexión dura más de 2 segundos). Esto evita cambios visuales molestos ante microcortes.

#### 4.2. Sistema de notificaciones (toast) mejorado

Se extraerá la funcionalidad de toast a una pequeña utilidad independiente dentro del módulo o en un archivo común (`toast.js`) para que pueda ser utilizada también por `config-ui.js`.

**Funciones:**

- `showToast(message, type = 'error', duration = 5000)`: crea el elemento, lo añade al contenedor y programa su eliminación.
- Se gestionará una cola de toasts si se lanzan varios en rápida sucesión: se mostrarán apilados, cada uno con su temporizador independiente.
- Los tipos serán: `'error'` (fondo rojo oscuro), `'info'` (fondo azul oscuro), `'success'` (verde, por si en el futuro se usa para confirmaciones).

**Eventos que generan toast:**

- Error al enviar mensaje (Fase 3).
- Pérdida de conexión prolongada (Fase 3).
- Error al cargar configuración (Fase 6).
- Error al guardar un valor de configuración (Fase 6).

En esta fase se integrarán los dos primeros; el resto se conectarán cuando se implemente la configuración.

#### 4.3. Verificación de la fase

Se probarán los siguientes escenarios:

1. Iniciar la aplicación con el servidor funcionando: el indicador pasa de "Conectando…" a "Conectado" sin parpadeos intermedios.
2. Detener el servidor: tras unos segundos, el indicador debe mostrar "Conectando…" (si el navegador está reintentando) o "Desconectado" si se agotan los reintentos, y aparecerá un toast.
3. Rearrancar el servidor: la conexión se reanuda automáticamente y el indicador vuelve a "Conectado".
4. Probar envío de mensaje con el servidor apagado: se muestra toast de error de envío, pero el mensaje permanece en el chat.
5. Forzar varios errores seguidos para verificar que los toasts se apilan correctamente y desaparecen sin interferir.

Una vez completada, el sistema de retroalimentación al usuario será robusto y estará listo para integrarse con el módulo de configuración más adelante.

### Fase 5: Gestión del terminalId

Esta fase se encarga de implementar la lógica específica del campo `terminalId`: su persistencia, validación básica y los efectos del cambio sobre la sesión de chat, asegurando una transición suave y la preservación del input.

#### 5.1. Persistencia en localStorage

- Al cargar la página, se leerá el valor de `localStorage` con una clave predefinida (ej. `noema_terminalId`). Si existe, se asignará al campo `#terminal-id`.
- Cada vez que el usuario modifique el campo y se confirme el cambio, se escribirá el nuevo valor en `localStorage`.

#### 5.2. Detección del cambio

Se usará el evento `change` del input, y opcionalmente también `keydown` para la tecla Enter (como alternativa rápida). El cambio se procesará solo si el valor es diferente del anterior y no se está ya en proceso de cambio (evitar bucles).

#### 5.3. Acciones al cambiar el terminalId

Se implementará la función `onTerminalIdChanged(newId)` descrita en la Fase 3, que se encarga de:

1. Cerrar la conexión SSE activa.
2. Limpiar el chat con `clearChat()`.
3. Guardar el nuevo ID en `localStorage`.
4. Llamar a `initChat(newId)`.

Además, se preservará el contenido del `#message-input`, que no se tocará en ningún momento del proceso de cambio.

#### 5.4. Comportamiento con terminalId vacío

Si el usuario borra el campo, no se establecerá ninguna conexión. Se puede cerrar la conexión existente y mostrar el indicador en un estado neutro (punto gris y texto "Sin terminal"). Esto evita llamadas con un ID vacío.

#### 5.5. Verificación de la fase

- Probar el cambio entre varios terminales con historial previo; verificar que el chat se actualiza correctamente y el input mantiene su texto.
- Recargar la página y comprobar que el terminalId se recupera de localStorage y se muestra en el campo.
- Borrar el campo y verificar que no se realizan conexiones; al escribir uno nuevo, se inicia normalmente.


### Fase 6: Módulo de configuración (config-ui.js)

Esta fase implementa el panel de configuración dinámico, que permite al usuario modificar los parámetros de Noema en caliente. Se consumirá el descriptor `settingsui.json` para construir la interfaz, y se utilizarán las funciones de `api.js` para leer y escribir valores. Al finalizarla, el usuario podrá abrir el panel, navegar por las secciones y modificar cualquier parámetro soportado, con notificación de errores.

#### 6.1. Estructura del módulo

El archivo `config-ui.js` exportará funciones para controlar el panel:

- `initConfig()`: se llama una vez al cargar la aplicación para asociar eventos al botón de configuración.
- `openConfigPanel()`: solicita el descriptor, construye la interfaz y muestra el panel.
- `closeConfigPanel()`: oculta el panel y limpia su contenido (o lo mantiene en caché).
- `buildTree(settingsUI)`: genera el árbol de navegación a partir del JSON.
- `renderContent(node)`: muestra los campos correspondientes a un nodo (rama u hoja) en el área de contenido.

#### 6.2. Flujo de apertura del panel

1. El usuario pulsa el botón de configuración en la cabecera.
2. Si el panel no está abierto, se llama a `openConfigPanel()`.
3. Si el descriptor `settingsui.json` no se ha cargado aún, se hace `api.fetchConfigUI()`. Mientras se espera, se podría mostrar un texto "Cargando configuración..." en el panel.
4. Una vez obtenido el JSON, se llama a `buildTree(settingsUI)`.
5. Se muestra el panel (cambiando su `display` o aplicando una clase `visible`).

#### 6.3. Construcción del árbol de navegación

El descriptor `settingsui.json` contiene una estructura jerárquica de nodos. Cada nodo puede ser:

- **Rama (grupo)**: tiene `title`, `icon` (opcional) y un array `children`.
- **Hoja (parámetro)**: tiene `title`, `type` (`inputstring`, `combo`, `checkbox`, `checkedlist`, `action`...), `path` (ruta interna), y otros atributos según el tipo (`options` para combo, `values` para checkedlist, etc.).

La función `buildTree` recorrerá esta estructura y generará un elemento `<ul>` con `<li>` anidados que representan el árbol visual en el contenedor `#config-tree`.

Cada `<li>` tendrá:

- Un `<span>` o `<a>` con el título del nodo.
- Una clase que indique si es rama (`branch`) u hoja (`leaf`).
- Un atributo `data-path` con la ruta completa del nodo (para hojas) o un identificador del grupo.
- Evento `click`: al pulsar sobre un nodo, se llamará a `renderContent(nodeData)`.

Las ramas podrán tener un comportamiento expandible/colapsable si se desea, pero inicialmente pueden mostrarse siempre expandidas por simplicidad.

#### 6.4. Renderizado del contenido según el nodo

La función `renderContent(node)` recibe el objeto del nodo seleccionado (extraído del JSON). Dependiendo de si es rama u hoja, actuará de forma diferente:

**Nodo rama:**

- Limpia el `#config-content`.
- Itera sobre sus `children` y renderiza cada uno como un campo en el panel (un formulario con todos los campos visibles a la vez). Es decir, se muestran todos los parámetros de ese grupo, cada uno con su etiqueta y control.
- Para cada hijo hoja, se crea un contenedor (`div.field`) con:
  - Una etiqueta `<label>` con el título.
  - El control de entrada correspondiente al tipo.
  - Se obtiene el valor actual mediante `api.fetchConfigValue(node.path)` y se asigna al control.

**Nodo hoja:**

- Se limpia el `#config-content` y se muestra únicamente el campo para ese parámetro.
- La lógica de creación del control es la misma que para los hijos de una rama, pero para un único elemento.

**Creación de controles por tipo:**

- **`inputstring`**: `<input type="text">`. Al cambiar (evento `input` o `change`), se llama a `api.setConfigValue(path, newValue)`. En caso de error, se muestra toast y se revierte el valor del input al anterior.
- **`combo`**: `<select>` con `<option>` para cada elemento del array `options`. El valor actual se selecciona. Al cambiar, se envía con `setConfigValue`.
- **`checkbox`**: `<input type="checkbox">`. Al cambiar, se envía `setConfigValue(path, checked)` (o se puede enviar el booleano como string "true"/"false").
- **`checkedlist`**: se crea un contenedor con un `<div>` por cada valor posible (del array `values`). Cada uno tendrá un `<input type="checkbox">` y una `<label>`. Para obtener el estado actual de cada elemento, se necesitará una función que devuelva la lista de marcados; el endpoint `GET /api/config/{path}` probablemente devuelva un array con los valores activos. Al marcar/desmarcar una casilla, se llama a `api.setConfigChecked(path, itemValue, checked)`.
- **`action`**: se ignora completamente. No se renderiza ningún elemento interactivo; se puede mostrar un texto "No disponible en versión web" o simplemente no aparecer.

#### 6.5. Envío inmediato de cambios

Cada vez que un control cambia de valor, se dispara la actualización:

- Se captura el nuevo valor.
- Se llama a la función de API apropiada.
- Se maneja la promesa:
  - Si es exitosa, no se hace nada adicional (el valor ya está en el control).
  - Si falla, se muestra un toast de error y se restablece el control al valor anterior (que se había guardado antes de intentar el cambio). Para ello, al construir el campo se debe almacenar el valor original en una variable o atributo `data-previous-value`.

#### 6.6. Cierre del panel

Al pulsar el botón de cierre (o de nuevo el botón de configuración en la cabecera), se oculta el panel. No se guarda ningún estado de edición pendiente porque cada cambio ya se envió.

#### 6.7. Verificación de la fase

1. Abrir el panel de configuración; debe aparecer el árbol de navegación a la izquierda y el área de contenido a la derecha (inicialmente vacía o con un mensaje).
2. Seleccionar una rama: se muestran todos los campos de ese grupo con sus valores actuales.
3. Modificar un `inputstring`, cambiar de campo; el nuevo valor debe persistir (comprobar recargando el panel o consultando la API).
4. Probar `combo`, `checkbox`, `checkedlist`: los cambios se envían al backend.
5. Provocar un error (por ejemplo, desconectar el servidor al cambiar un valor): debe aparecer un toast y el campo debe volver al valor original.
6. Verificar que las acciones de tipo `action` no aparecen en el árbol ni en el contenido.

### Fase 7: Pruebas de integración y ajustes finales

Esta fase consolida todo el trabajo anterior. Se realizarán pruebas completas de extremo a extremo, se verificarán todos los flujos y se aplicarán ajustes para garantizar que el cliente web funciona de manera coherente, estable y acorde a lo especificado en el análisis.

#### 7.1. Pruebas funcionales completas

Se debe recorrer cada una de las funcionalidades descritas en el documento de análisis, verificando tanto los casos normales como los límite:

**Conversación:**
- Enviar y recibir mensajes con el agente desde la interfaz web.
- Verificar que el historial se carga correctamente al conectar un terminal que ya tiene conversaciones previas.
- Probar la agrupación de mensajes del mismo tipo consecutivos (logs múltiples, varios mensajes de usuario seguidos –aunque poco comunes–, etc.).
- Confirmar que la alineación de mensajes (usuario a la derecha, resto a la izquierda) se mantiene.
- Comprobar que el texto de los mensajes es seleccionable y copiable.

**Conexión y reconexión:**
- Forzar cortes de red y verificar el comportamiento del indicador de estado y los toasts.
- Verificar que, tras una desconexión, al enviar un mensaje éste se muestra en el chat y, cuando se restablece la conexión, la respuesta del agente aparece correctamente.
- Probar la reconexión automática sin recargar la página.

**Gestión del terminalId:**
- Cambiar entre varios terminales y comprobar que cada uno muestra su historial respectivo.
- Verificar que el campo de entrada de texto conserva su contenido al cambiar de terminal.
- Probar con un terminal nuevo (sin historial) y asegurarse de que el chat se inicia limpio.
- Borrar el campo de terminalId y comprobar que no se realizan llamadas y el indicador muestra un estado coherente.

**Configuración:**
- Abrir el panel, navegar por el árbol y verificar que cada tipo de campo se renderiza y funciona correctamente.
- Modificar valores y comprobar que se persisten (puede hacerse también consultando desde otra UI o reiniciando el agente si aplica).
- Probar el manejo de errores durante el guardado (desconexión, errores del servidor).
- Verificar que las acciones de tipo `action` no son visibles ni interactivas.

#### 7.2. Pruebas de usabilidad y consistencia visual

- Comprobar que el diseño es responsivo: estrechar la ventana y verificar que los elementos se reajustan sin desbordamientos.
- Verificar la apariencia en al menos dos navegadores modernos (Chrome, Firefox).
- Probar con diferentes longitudes de mensajes y comprobar que el scroll del chat funciona suavemente.
- Probar con logs muy largos (parámetros extensos) y verificar que no rompen el layout (se puede usar truncado o scroll horizontal en el bloque si fuera necesario).

#### 7.3. Ajustes finales

Con los resultados de las pruebas, se realizarán correcciones y mejoras menores:

- Ajustes de estilos para corregir problemas de alineación o colores.
- Mejora de mensajes de error si se detectan situaciones poco informativas.
- Optimización del código: eliminar `console.log` de depuración, refactorizar si se encuentra código duplicado.
- Añadir comentarios en las partes más complejas de los módulos para facilitar mantenimiento futuro.

#### 7.4. Preparación para integración final con el backend

Si el desarrollo se ha realizado con un servidor estático alternativo o stubs, es el momento de verificar que todo funciona correctamente sirviendo los archivos desde el classpath a través de Javalin, exactamente como estará en producción. Se comprobarán las rutas, tipos MIME y la correcta interacción con todos los endpoints reales.

#### 7.5. Empaquetado y prueba final

- Construir el JAR de Noema con el cliente web incluido.
- Ejecutar el JAR en una terminal limpia y acceder con el navegador a `http://localhost:8080`.
- Realizar una prueba de humo (smoke test) de todas las funcionalidades para confirmar que el despliegue es correcto.
