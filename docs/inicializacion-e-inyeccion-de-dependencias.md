
# Inicialización e inyección de dependencias

### 1. Filosofía de Ensamblaje: Inyección de dependencias manual y ausencia de frameworks

A diferencia de las aplicaciones empresariales típicas en Java, Noema **no utiliza frameworks de inyección de dependencias (DI)** como Spring Boot o CDI. Esta es una decisión arquitectónica deliberada que busca maximizar la transparencia, el pragmatismo y la facilidad de depuración.

En proyectos donde el ciclo de vida de los componentes es complejo y secuencial (arrancar la base de datos antes de cargar el historial, registrar herramientas antes de despertar al LLM), la "magia" de la inyección por reflexión puede oscurecer el flujo real de ejecución. En Noema, si quieres saber cuándo y cómo se instancia un servicio, basta con hacer `Ctrl+Click` en tu IDE. 

El sistema utiliza un patrón híbrido:
1. **Inyección por Constructor:** Los componentes principales reciben sus dependencias vitales (como `AgentSettings` o `AgentPaths`) directamente en el constructor.
2. **Service Locator Localizado:** La clase `Agent` actúa como el contexto central. Las herramientas y acciones reciben la instancia de `Agent` y, a través de ella, solicitan los servicios específicos que necesitan mediante `agent.getService("NombreDelServicio")`.

### 2. Puntos de Entrada y Selección de Entorno (`Main`, `MainGUI`, `MainConsole`)

El ciclo de vida de la aplicación comienza de forma muy limpia. La clase `Main` actúa como un simple proxy de enrutamiento que lee los argumentos de la línea de comandos (específicamente `-c`) para decidir qué entorno de presentación levantar:

*   **`MainGUI` (Entorno Gráfico):** Inicializa el *Look & Feel* (FlatLaf oscuro) y levanta el `WelcomePanel`. Este panel es crítico porque fuerza al usuario a seleccionar una carpeta de trabajo (Workspace) antes de que el agente exista. Una vez seleccionado y validada la configuración básica, se instancia la interfaz de chat y se lanza la creación del agente de forma asíncrona (`Thread.ofPlatform()`).
*   **`MainConsole` (Entorno Terminal):** Inicializa un entorno REPL rico usando **JLine3** (soportando autocompletado y atajos de teclado). Si detecta que la configuración del workspace es inválida o faltan parámetros críticos, levanta el diálogo de configuración Swing de forma excepcional antes de iniciar el agente.

En ambos casos, la inicialización de la interfaz de usuario precede a la inicialización del "cerebro" del agente.

### 3. El Registro de Componentes: `AgentLocator` y `AgentManager`

Para que el agente sepa de qué piezas dispone sin recurrir al escaneo de classpath (classpath scanning), existe un catálogo estricto y centralizado.

*   `AgentLocator`: Es el único Singleton estático real del sistema. Expone el acceso global al `AgentManager`.
*   `AgentManagerImpl`: Es el "catálogo maestro" de Noema. En su constructor, se registran manualmente y en orden todas las **fábricas de servicios** (`AgentServiceFactory`) del ecosistema: `EmbeddingsServiceFactory`, `SensorsServiceFactory`, `ReasoningServiceFactory`, `MemoryServiceFactory`, etc. 

También actúa como factoría principal de configuraciones, bases de datos y la instancia base del `Agent`. Si un servicio no está registrado en el `AgentManagerImpl`, simplemente no existe en el universo de Noema.

### 4. Bootstrapping del Núcleo: La clase `BootUtils`

Antes de que el agente pueda razonar, necesita un entorno físico preparado. La clase `BootUtils` encapsula este trabajo "sucio" de fontanería a través de su método `init(AgentSettings settings)`:

1.  **Configuración de Logs:** Lee la ruta de logs del `AgentPaths` y reconfigura Log4j2 en caliente (`Configurator.reconfigure()`) para que los volcados vayan a `var/log/noema-agente.log` dentro del workspace seleccionado.
2.  **Arranque del Servidor H2:** Escribe dinámicamente un archivo `.h2.server.properties` y levanta el servidor web embebido de H2. Esto expone las bases de datos en un puerto (por defecto 8082) para permitir la inspección en tiempo real.
3.  **Conexiones a Base de Datos:** Crea las dos instancias de `ConnectionSupplier` que abstraen las URLs JDBC de las dos bases de datos (Memoria y Servicios) forzando el parámetro `AUTO_SERVER=TRUE`.
4.  **Instanciación:** Llama a `AgentManager.createAgent(...)` pasando las conexiones listas, la configuración y el manejador de la consola.

El resultado de `BootUtils.init()` es un objeto `Agent` completamente ensamblado, pero **dormido**.

### 5. Ensamblaje e Inicialización: `AgentImpl.start()`

El despertar del agente ocurre cuando se invoca `agent.start()`. Este método orquesta una coreografía muy específica para garantizar que no haya condiciones de carrera durante el inicio:

1.  **Creación de Servicios:** Itera sobre todas las fábricas registradas en el `AgentManager` y crea las instancias de los servicios (aún sin iniciarlos).
2.  **Registro de Sensores Base:** Instancia y registra manualmente el sensor primario `USER` (el canal de interacción humana) dentro del `SensorsService`.
3.  **Extracción de Herramientas:** Recorre los servicios evaluando si pueden arrancar en base a la configuración (`canStart()`). A los que sí pueden, les pide su catálogo de herramientas (`getTools()`) y se las inyecta al `ReasoningService`.
4.  **Encendido Global:** Llama al método `start()` de cada servicio habilitado (el `Scheduler` lee la BBDD, el `Reasoning` levanta el hilo del despachador de eventos, etc.).
5.  **Shutdown Hook:** Como último paso, registra un *Hook* de apagado en la JVM (`Runtime.getRuntime().addShutdownHook`) que garantiza que, ante un cierre brusco (Ctrl+C), se llame a `agent.stop()`, permitiendo al orquestador guardar la memoria volátil a disco y cerrar la base de datos limpiamente.

### 6. Receta Práctica: Cómo añadir un nuevo servicio al Agente

Gracias a este diseño determinista, extender el agente con un nuevo servicio o integración requiere pasos explícitos y rastreables. Si deseas añadir, por ejemplo, un `SpotifyService`, este es el checklist:

1.  **El Contrato:** Crea la interfaz `SpotifyService` (que extienda de `AgentService`) en `io.github.jjdelcerro.noema.lib`.
2.  **La Implementación:** Crea `SpotifyServiceImpl` en el paquete de implementación. Aquí definirás su `start()`, `stop()`, y sus herramientas `getTools()` (ej. `SpotifyPlayTool`).
3.  **La Factoría:** Crea `SpotifyServiceFactory` implementando `AgentServiceFactory`. En su método `canStart(AgentSettings settings)` debes validar si el usuario ha configurado las API Keys necesarias en `settings.json`.
4.  **El Registro (El Wiring real):** Abre `AgentManagerImpl.java` y, en su constructor, añade la línea: 
    `this.registerService(new SpotifyServiceFactory());`
5.  **Consumo:** A partir de ahora, cualquier otra herramienta o componente puede acceder a tu servicio haciendo:
    `SpotifyService spotify = (SpotifyService) agent.getService(SpotifyService.NAME);`

