
# Comunicación Core-UI (Capa de Presentación)

### 1. Filosofía de Separación: Inversión de Dependencias

Uno de los principios arquitectónicos más estrictos de Noema es la separación absoluta entre el "cerebro" del agente (el Kernel) y su representación visual. El motor de razonamiento y los servicios no saben —ni deben saber— si se están ejecutando en un entorno gráfico con ventanas, en una terminal de texto plano o en un servidor sin interfaz (headless).

Para lograr esto, Noema utiliza el principio de **Inversión de Dependencias**: 
* El Kernel expone sus necesidades de salida a través de una interfaz mínima: `AgentConsole`.
* La Capa de Presentación (UI) conoce y depende de la interfaz `Agent`, actuando como un cliente externo que inyecta estímulos y consume resultados, respetando escrupulosamente el ciclo de vida asíncrono y el modelo de hilos del motor.

Esta frontera limpia permite cambiar de la interfaz Swing a la de terminal simplemente inyectando una implementación distinta durante el arranque, sin alterar una sola línea de lógica de negocio.

### 2. De la UI al Núcleo: `putUsersMessage` y el Callback de Respuesta

Cuando el usuario escribe un mensaje y pulsa *Enter*, la interfaz no invoca directamente al modelo de lenguaje. En su lugar, el mensaje sigue el mismo camino que cualquier otra percepción del entorno, integrándose en el flujo sensorial.

La UI llama a `agent.putUsersMessage(texto, callback)`. Bajo el capó, este método:
1. Empaqueta el texto como un evento de naturaleza `USER`.
2. Lo inyecta en la cola del `SensorsService`.
3. Esto activa el `sensorLock.notifyAll()`, que despierta inmediatamente al hilo del `eventDispatcher` (el bucle de consciencia del agente) que estaba dormido.

Para que la UI sepa cuándo el agente ha terminado de pensar (ya que el procesamiento es asíncrono y puede implicar la ejecución de múltiples herramientas), se pasa un `SensorEventCallback`. Una vez que el `ReasoningService` consolida la respuesta final del turno, invoca `callback.onComplete()`. Es en este momento cuando la interfaz (por ejemplo, `MainChatPanel`) detiene el cronómetro de "pensando...", oculta el botón de *Stop* y vuelve a habilitar el área de texto.

### 3. Del Núcleo a la UI: El Contrato `AgentConsole`

Cuando el agente necesita comunicarse con el humano, lo hace disparando métodos sobre la interfaz `AgentConsole`: `printSystemLog`, `printModelResponse`, o `printSystemError`. El núcleo se desentiende de cómo se renderiza esta información.

* **En el entorno gráfico (Swing):** La clase `AgentSwingConsoleControllerUsingMultipleJTextPane` intercepta estas llamadas. En lugar de volcar texto plano, instancia "burbujas" visuales dinámicas (`JBubbleTextPanel`). Si el mensaje es del modelo (`printModelResponse`), crea un `JMarkdownPanel` que procesa el texto en tiempo real usando `commonmark-java`, renderizándolo como HTML rico (tablas, negritas, código con colores). Todo esto se encola de forma segura en el *Event Dispatch Thread (EDT)* mediante `SwingUtilities.invokeLater()`.
* **En el entorno de terminal (CLI):** La clase `AgentConsoleImpl` toma esas mismas llamadas y simplemente las formatea (ej. prefijando `>>>` para logs del sistema o `Model:` para respuestas) escribiéndolas directamente en el `terminal.writer()` de JLine3.

### 4. El Bloqueo Síncrono: Confirmaciones Humanas (`confirm()`)

El mecanismo de seguridad más importante de Noema —la confirmación antes de ejecutar operaciones peligrosas— impone un desafío arquitectónico: el agente debe detenerse por completo hasta que el humano responda.

Cuando `ReasoningService` detecta una herramienta de escritura o ejecución de comandos, invoca `console.confirm(mensaje)`. **Esta llamada es deliberadamente síncrona y bloqueante**. El hilo del `eventDispatcher` se congela a la espera de un booleano.

* **Resolución en Swing:** La UI invoca a la utilidad `SwingUtils.getTopWindow()` para encontrar cuál es la ventana o diálogo modal que está actualmente en primer plano (top) y lanza un `JOptionPane.showConfirmDialog` sobre ella. Al cerrarse el cuadro de diálogo, se devuelve `true` o `false` al agente, que reanuda su ejecución.
* **Resolución en Consola:** Se invoca `lineReader.readLine("... (s/n): ")`. El prompt de la terminal captura la entrada por teclado, la normaliza y devuelve el control al hilo bloqueado.

### 5. UI Dinámica y Reactiva: `AgentUISettings` y Evaluador de Expresiones

Construir menús de configuración acoplados en código Java hace que mantener las opciones del agente sea tedioso. Noema resuelve esto generando sus diálogos de ajustes dinámicamente a partir de un archivo JSON (`settingsui.json`). 

Este JSON define la estructura de árbol, los campos (combos, rutas, listas marcables) y los dominios de datos. Las implementaciones `AgentSwingSettingsImpl` y `AgentConsoleSettingsImpl` leen este esquema y construyen los controles visuales correspondientes "al vuelo".

Para ir un paso más allá, la interfaz es **reactiva**. Ciertos elementos deben habilitarse o deshabilitarse según el estado de otros (por ejemplo, prohibir marcar la herramienta `shell_execute` si en la sección de seguridad se ha prohibido la ejecución de comandos). En lugar de "hardcodear" esta lógica, el JSON incluye propiedades como `childEnabled` con expresiones lógicas en texto plano. La clase `ExpressionEvaluator` (un pequeño parser recursivo descendente escrito a medida) interpreta estas expresiones en tiempo de ejecución, actualizando el estado de los *checkboxes* instantáneamente sin requerir librerías pesadas. *(Nota: Adicionalmente, el proyecto integra MVEL, pero este se reserva para el potente entorno interactivo del `DebugPanel`)*.

### 6. Ensamblaje Visual: `AgentUIManager` y `AgentUILocator`

Para cerrar el círculo de la Inversión de Dependencias, la aplicación necesita arrancar el motor visual antes que el Kernel. Esto se logra mediante un patrón Service Locator dedicado a la UI: el `AgentUILocator`.

En el punto de entrada absoluto de la aplicación (`MainGUI` o `MainConsole`), lo primero que se ejecuta es el registro del gestor visual:
`AgentUILocator.registerAgentUIManager(new AgentSwingManagerImpl(console));`

Más adelante, cuando la clase `BootUtils.init()` prepara el terreno para ensamblar el motor del agente, no necesita saber si está en Swing o JLine. Simplemente pide la consola invocando `AgentUILocator.getAgentUIManager().createConsole()` y se la inyecta al núcleo. Esta arquitectura garantiza que las librerías gráficas y las del núcleo permanezcan en compartimentos estancos, facilitando el testing y la evolución de ambas capas por separado.

