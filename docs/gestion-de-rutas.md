# Gestión de Rutas (AgentPaths)

## 1. Introducción: el sistema de archivos como estado

Frente a la tendencia habitual de externalizar toda la persistencia en bases de datos o servicios en la nube, Noema adopta una postura deliberadamente minimalista y transparente: **el sistema de archivos local es el depositario principal del estado del agente**. Todos los componentes —configuración, memoria conversacional, índices vectoriales, logs, cachés y hasta los resúmenes narrativos (CheckPoints)— se almacenan como archivos planos o bases de datos embebidas (H2) dentro de un directorio sandbox. El resultado es un agente que se puede inspeccionar, respaldar y depurar con las herramientas estándar del sistema operativo (editores de texto, `grep`, `diff`, control de versiones), sin necesidad de consolas de administración propietarias.

El corazón de esta arquitectura es la clase `AgentPaths`, que actúa como un **sistema de coordenadas** para todo el sandbox. Define una jerarquía bien conocida de carpetas bajo el workspace elegido por el usuario (normalmente `.noema-agent`) y, de forma complementaria, una ruta de configuración global en el directorio `~/.config/noema-agent`. Esta dualidad permite que el agente pueda ejecutarse en modo portátil (toda la configuración dentro del proyecto) o en modo usuario (compartiendo ajustes globales entre distintos workspaces), según lo que se necesite en cada momento.

El diseño asume una premisa fuerte: **el sistema de archivos es confiable y está disponible**. Noema no abstrae el acceso a disco detrás de una capa de virtualización compleja; simplemente lo acepta como una fuente de verdad sincrónica, bloqueante y determinista. Esto simplifica drásticamente el modelo de persistencia —no hay transacciones distribuidas, ni bases de datos vectoriales dedicadas, ni servicios de caché externos— y resulta sorprendentemente adecuado para un agente de escritorio que opera en una única línea temporal continua. Cualquier efecto colateral (un archivo que no se puede escribir, una carpeta que no existe, un permiso denegado) se propaga inmediatamente al sistema de control de acceso, que decidirá si la operación puede continuar o debe abortar.

En las secciones siguientes se desglosa cómo `AgentPaths` organiza este universo de archivos, cómo resuelve rutas entre el workspace y la configuración global, y cómo se integra con el resto de servicios del agente para ofrecer una experiencia coherente y depurable.

## 2. La topología del sandbox: carpetas clave

El sandbox de Noema se organiza bajo dos grandes raíces: la **carpeta de trabajo** (workspace) elegida por el usuario y, complementariamente, la **carpeta de configuración global** en el directorio personal (`~/.config/noema-agent`). Dentro de la primera se crea una subcarpeta oculta llamada `.noema-agent` que contiene toda la estructura operativa del agente. `AgentPaths` proporciona métodos específicos para acceder a cada una de las ubicaciones que forman esta topología:

*   **`getWorkspaceFolder()`**: la raíz del proyecto o espacio de trabajo. Sobre esta ruta se construye todo el sandbox. Si el usuario no selecciona ninguna, se usa el directorio actual de ejecución.

*   **`getAgentFolder()`**: la carpeta `.noema-agent` dentro del workspace. Es el punto de entrada real del agente y la base para todas las subcarpetas siguientes.

*   **`getConfigFolder()`** (`var/config`): almacena la configuración del agente en formato JSON (`settings.json`), los archivos de propiedades de proveedores LLM, los prompts del sistema (directorio `prompts/`) y la definición de la interfaz de usuario (`settingsui.json`). El agente despliega aquí sus recursos por primera vez mediante `installResource()`.

*   **`getDataFolder()`** (`var/lib`): es el repositorio central del estado persistente. Contiene las bases de datos embebidas H2 (`memory.mv.db` para los turnos y checkpoints, `service.mv.db` para el planificador y otros servicios), el archivo `active_session.json` (volcado de la conversación activa) y la subcarpeta `checkpoints/`, donde se guardan los resúmenes narrativos generados por `MemoryService` (archivos `.md`). Aquí reside, en definitiva, la memoria a largo plazo del agente.

*   **`getCacheFolder()`** (`var/cache`): almacena datos derivados que pueden regenerarse si es necesario, pero que se conservan para ahorrar tiempo de cómputo. Por ejemplo, los textos extraídos de documentos PDF/DOCX por `file_extract_text` se guardan aquí con un hash del archivo original. También se alojan las estructuras JSON de documentos mapeados (`.struct`). La caché es segura de eliminar sin pérdida de funcionalidad.

*   **`getTempFolder()`** (`var/tmp`): alberga archivos temporales y volátiles, como las salidas paginadas de comandos (`shell_execute`), los resultados intermedios de búsquedas web, los volcados de contexto para depuración (`last_context.json`) y otros recursos que el agente necesita para fragmentar respuestas largas. A diferencia de la caché, estos archivos tienen una vida útil corta y se limpian periódicamente.

*   **`getLogFolder()`** (`var/log`): contiene los archivos de log generados por Log4j2 (`noema-agente.log` y sus rotaciones). Es la primera parada para diagnosticar problemas de ejecución, errores de API o fallos inesperados en los hilos del agente.

*   **`getSandboxHomeFolder()`** (`var/home`): un directorio que actúa como **home virtual** para el agente cuando ejecuta comandos de shell a través de `firejail`. Permite aislar el sistema de archivos real del agente, ofreciendo un entorno controlado y seguro para scripts que no deberían acceder a la configuración o las bases de datos.

*   **`getGlobalConfigFolder()`** (`~/.config/noema-agent`): complementa al workspace. Almacena los mismos tipos de recursos que `getConfigFolder()` (prompts, identidad, habilidades), pero compartidos entre distintos proyectos. Si un recurso no existe en el workspace, el agente lo busca aquí, lo que permite centralizar configuraciones comunes sin duplicarlas.

Cada uno de estos accesos devuelve un objeto `Path` absoluto y normalizado. La creación de toda la jerarquía se realiza con un único método `setupHierarchy()`, que garantiza que todos los directorios existan antes de que el agente comience a funcionar. Esta estructura plana y predecible es la base sobre la que se construyen servicios como `SourceOfTruth` (que escribe en `var/lib`), `SensorsService` (que persiste su estado en `sensors.json` dentro de `var/lib`) o las herramientas de archivo (`file_read`, `file_write`), que operan siempre dentro de los límites de este sandbox a menos que se autoricen rutas externas explícitamente.


## 3. El ciclo de vida del espacio de trabajo

El espacio de trabajo no es una entidad estática; nace, se configura y se mantiene a lo largo de la vida del agente mediante un protocolo explícito gobernado por `AgentPaths`. Todo comienza cuando el usuario selecciona una carpeta raíz (workspace) en el diálogo de bienvenida o desde la configuración. Con esa ruta, `AgentManager` crea una instancia de `AgentPaths` y, a continuación, invoca el método `setupHierarchy()`.

La responsabilidad de `setupHierarchy()` es doble. Primero, **crea físicamente la estructura de directorios** (`.noema-agent/var/config`, `var/lib`, `var/cache`, `var/tmp`, `var/log`, `home`) utilizando `Files.createDirectories()`. Esta operación es idempotente: si las carpetas ya existen, no hace nada; si faltan, las genera con los permisos por defecto del sistema. Segundo, **establece el punto de anclaje** para que el resto del agente pueda referirse a estas rutas sin necesidad de conocer la ubicación concreta del workspace. Este momento de “toma de tierra” ocurre antes de que se cargue cualquier servicio o se persista ningún estado.

Una vez establecida la jerarquía, el workspace se considera **inmutable en cuanto a su topología** durante toda la ejecución del agente. Ningún servicio puede añadir nuevos directorios raíz o cambiar el destino de las carpetas existentes. Lo que sí puede cambiar es el contenido dentro de ellas: los servicios escriben y leen archivos, el agente rota logs, el planificador actualiza su base de datos, etc. Pero la estructura básica —dónde está la configuración, dónde la memoria, dónde los temporales— permanece fija. Esta rigidez es deliberada: simplifica el modelo de persistencia y evita que el agente pierda el rastro de sus propios datos.

El ciclo de vida termina cuando el agente se detiene. En ese momento no se destruye el workspace; al contrario, se preserva íntegro para la próxima ejecución. El único acto de limpieza que realiza `AgentPaths` es la eliminación opcional de archivos temporales muy antiguos (gestionada por las herramientas que los crean, no por el propio paths). Al reiniciar, `setupHierarchy()` vuelve a ejecutarse, encuentra todo ya creado y continúa. La persistencia del workspace es, por tanto, el mecanismo que permite la **continuidad entre sesiones**: el agente despierta en el mismo estado de archivos que dejó al dormir, con sus turnos, checkpoints, configuración y logs intactos.

Finalmente, cabe destacar que el workspace puede ser **relocalizado** en cualquier momento. Si el usuario cierra la aplicación, mueve la carpeta a otra ubicación y vuelve a abrir Noema seleccionando la nueva ruta, el agente reanudará su actividad sin pérdida de información. Esto es posible porque todas las rutas se resuelven siempre desde el workspace activo, y no hay referencias absolutas embebidas en los archivos de estado (las bases de datos H2 utilizan rutas relativas al archivo `.mv.db`, y los checkpoints referencian IDs numéricos, no rutas). Esta flexibilidad es una consecuencia directa de la decisión de tratar el sistema de archivos como el estado único y de no depender de servicios externos con localizaciones fijas.

## 4. Resolución de rutas: entre lo local y lo global

Uno de los problemas más molestos en aplicaciones que gestionan configuración es decidir dónde almacenar los archivos: ¿junto al ejecutable (portable) o en el directorio personal del usuario (global)? `AgentPaths` resuelve esta tensión implementando una **resolución en dos niveles** que combina lo mejor de ambos mundos sin necesidad de opciones de instalación.

El mecanismo es sencillo pero potente. Cuando el agente necesita localizar un recurso (por ejemplo, un prompt del sistema, un módulo de identidad o un fichero de configuración), consulta primero el workspace local (`workspace/.noema-agent/...`). Si el archivo existe allí, lo utiliza. Si no, recurre a la carpeta de configuración global (`~/.config/noema-agent/...`). Este comportamiento se implementa en métodos como `getAgentPath(String name)` y `getConfigPath(String name)`, que devuelven la primera ubicación donde el recurso está presente.

¿Qué ventajas aporta esta estrategia?

- **Portabilidad por defecto**: si el usuario copia todo el workspace a otro equipo o a un pendrive, el agente sigue funcionando porque todos los recursos necesarios están autocontenidos. No hay dependencias ocultas en el sistema de archivos del usuario.

- **Reutilización de configuración global**: al mismo tiempo, el usuario puede mantener ajustes comunes (como listas de API keys, modelos preferidos o habilidades personalizadas) en su directorio `~/.config/noema-agent`. Estos estarán disponibles para cualquier workspace que no los sobrescriba localmente.

- **Actualización sin fricción**: al desplegar una nueva versión de Noema, los recursos preinstalados en el JAR se copian al workspace solo si no existen. Si el usuario ha modificado un prompt localmente, esa versión prevalece. Si no, el agente toma la versión global o la recién instalada.

El único método que rompe ligeramente esta regla es `listAgentPath(String name)`, que devuelve la **unión** de los archivos encontrados tanto en el workspace como en la configuración global. Esto es útil para, por ejemplo, enumerar todas las habilidades disponibles (`.ref.md`) sin importar dónde estén físicamente almacenadas.

Un detalle importante: esta resolución **no es recursiva**. No se busca en subcarpetas del workspace si el recurso no existe localmente; la búsqueda es directa y en un solo nivel. Si un recurso debe estar presente sí o sí (como `settings.json` durante el arranque), el agente lo desplegará desde el JAR a la ubicación local durante la fase de `setupSettings()`. En otros casos, como los módulos de identidad o las habilidades, el agente asume que o bien existen localmente, o bien no existen y no se mostrarán.

En la práctica, esta dualidad es invisible para el desarrollador de herramientas y para el LLM: ambos trabajan con rutas relativas simples (ej: `var/config/prompts/reasoning-system.md`), y `AgentPaths` se encarga de resolverlas al primer punto donde el archivo esté accesible. El resultado es un sistema de archivos virtual pero determinista, que permite tanto la portabilidad total como la centralización de la configuración común.


## 5. Acceso a recursos de configuración e identidad

Más allá de proporcionar rutas a directorios genéricos, `AgentPaths` facilita el acceso directo a los recursos que definen la personalidad y el comportamiento del agente. Estos recursos se organizan en tres grandes familias dentro de la jerarquía del sandbox:

**Configuración operativa (`var/config`)**  
Aquí residen los archivos que determinan cómo se conecta el agente a los LLMs, qué herramientas están activas y cómo se comporta el sistema en general. Los métodos `getConfigFolder()` y `getConfigPath(String name)` permiten localizar ficheros como:
- `settings.json`: el corazón de la configuración jerárquica del agente.
- `models.properties`, `providers_urls.properties`, `providers_apikeys.properties`: dominios externos que alimentan los combos y listas de selección en la UI de configuración.
- `settingsui.json`: la definición de la interfaz de usuario de ajustes (árbol de menús y componentes).
- La subcarpeta `prompts/`, que contiene los archivos Markdown con las instrucciones del sistema (`reasoning-system.md`, `memory-compact.md`, etc.). Estos prompts se cargan en caliente y pueden editarse sin recompilar.

**Identidad y conocimiento del entorno (`var/identity`)**  
Esta carpeta almacena la "personalidad" y el conocimiento biográfico o técnico del agente. Se divide en dos subdirectorios:
- `core/`: contiene la constitución operativa del agente (normas, metodologías, principios). Los archivos aquí se inyectan directamente en el prompt del sistema según los módulos que el usuario tenga activados en la configuración.
- `environ/`: alberga el conocimiento denso del entorno (biografía del usuario, proyectos, intereses). De forma ingeniosa, Noema no carga estos archivos completos en el prompt; en su lugar, utiliza archivos `.ref.md` ligeros que actúan como índices. Cuando el agente necesita información detallada de un módulo, invoca la herramienta `consult_environ`, que a través de `AgentPaths` localiza y carga el archivo `.md` correspondiente.

**Habilidades procedimentales (`var/skills`)**  
Análogamente al entorno, las habilidades siguen un patrón de carga bajo demanda. En `skills/` coexisten:
- Archivos `.ref.md` (referencias ligeras) que describen el propósito de cada habilidad y son listados por la herramienta `list_skills`.
- Archivos `.md` completos que contienen el protocolo paso a paso, que se cargan mediante `load_skill` cuando el agente decide ejecutar esa capacidad.

`AgentPaths` expone dos métodos convenientes para trabajar con estos recursos: `getAgentPath(String name)` (devuelve la primera ocurrencia del recurso en la jerarquía local/global) y `listAgentPath(String name)` (devuelve todos los recursos de una subcarpeta, fusionando las contribuciones locales y globales). El método `getResourceAsString` de `Agent` es el que realmente utiliza `AgentPaths` para leer el contenido de estos archivos y devolverlo como texto, con la lógica adicional de desplegar los recursos desde el JAR si no existen.

Este diseño permite una **personalización profunda** del agente sin modificar el código fuente: el usuario puede añadir nuevos módulos de identidad, crear nuevas habilidades o ajustar los prompts del sistema simplemente creando o editando archivos en las carpetas correspondientes. La separación entre índices ligeros y contenido denso, además, es clave para la estrategia de gestión de contexto del LLM: el agente solo carga el conocimiento que realmente necesita en cada turno, manteniendo el prompt del sistema liviano.


## 6. Persistencia y seguridad del sandbox

`AgentPaths` no solo define dónde se guardan los archivos, sino que también sienta las bases de un **modelo de persistencia seguro y predecible**. La seguridad aquí opera en dos niveles: por un lado, la prevención de fugas del sandbox (path traversal); por otro, la protección de archivos sensibles frente a modificaciones accidentales o maliciosas del propio agente.

**Aislamiento por raíz**: toda operación de lectura o escritura que realiza cualquier herramienta del agente pasa por `AgentAccessControl`, que utiliza la raíz del workspace (obtenida mediante `getWorkspaceFolder()`) como punto de anclaje. Si una herramienta intenta acceder a una ruta que no está dentro de esta raíz ni en las listas blancas explícitamente configuradas, el acceso se deniega con una excepción de seguridad. `AgentPaths` no aplica estas políticas por sí mismo, pero proporciona las coordenadas necesarias para que el control de acceso pueda evaluarlas correctamente.

**Archivos especiales no modificables**: el método `resolvePath` de `AgentAccessControl` incluye reglas adicionales sobre qué partes del sandbox están protegidas. Por ejemplo, los archivos con extensión `,jv` (las copias de respaldo generadas por el sistema RCS) son **de solo lectura** para el agente. El LLM puede listarlos o leerlos, pero nunca modificarlos ni eliminarlos directamente, preservando así la integridad del historial de versiones. Del mismo modo, la carpeta `.noema-agent/var/lib` es de solo escritura para la base de datos H2 y el sistema de checkpoints, pero herramientas como `file_write` no pueden sobrescribir esos ficheros porque están fuera de las rutas de trabajo permitidas por defecto.

**Persistencia atómica**: aunque no es una responsabilidad directa de `AgentPaths`, los servicios que escriben en el sandbox (como `SourceOfTruth` al guardar `active_session.json` o `SensorsService` al persistir `sensors.json`) utilizan un patrón de escritura atómica: primero se escribe en un archivo temporal dentro de la misma carpeta (`archivo.tmp`), y luego se renombra atómicamente al destino final. `AgentPaths` facilita este patrón proporcionando métodos para obtener las rutas de las carpetas `tempFolder` y `dataFolder`, de modo que los temporales y los destinos compartan el mismo sistema de archivos y puedan moverse de forma atómica.

**Limpieza y rotación**: la responsabilidad de limpiar archivos obsoletos recae en los servicios que los crean, no en `AgentPaths`. Por ejemplo, `ShellExecuteTool` registra sus salidas en un mapa LRU y elimina los archivos `*.out` más antiguos cuando superan un límite configurable. `WebGetTikaTool` almacena en caché los contenidos extraídos, pero confía en que el usuario o un proceso externo pueda purgar `var/cache` si es necesario. `AgentPaths` no implementa políticas de retención, pero proporciona los medios para localizar estos archivos y, en el futuro, podría incorporar un servicio de limpieza transversal.

**Consecuencias para el usuario**: esta aproximación convierte al sandbox en una **cápsula autocontenida pero inspeccionable**. Si un usuario desea respaldar todo el estado de Noema, le basta con copiar la carpeta `.noema-agent` del workspace y, opcionalmente, la carpeta `~/.config/noema-agent`. Si algo falla, puede examinar los logs en `var/log`, revisar los checkpoints en `var/lib/checkpoints/*.md` o incluso editar `active_session.json` para modificar la conversación en curso (con el riesgo que ello conlleva). El agente no oculta su estado tras formatos propietarios ni servicios remotos: todo está al alcance de un editor de texto y de las herramientas UNIX clásicas. Esta transparencia es una de las señas de identidad arquitectónicas de Noema.

## 7. Integración con el resto de servicios

`AgentPaths` no es un componente aislado; su verdadero valor se manifiesta al ser inyectado en el resto del ecosistema de Noema. La instancia de `AgentPaths` se crea en el momento del arranque (a través de `AgentManager.createAgentPaths()`) y se asocia al objeto `AgentSettings`, que a su vez es accesible desde el `Agent` central. Cualquier servicio o herramienta que necesite conocer una ruta absoluta del sandbox obtiene primero la referencia al agente y luego invoca `agent.getPaths().getXxxFolder()`.

**En `SourceOfTruth`**: el repositorio de memoria utiliza `agent.getPaths().getDataFolder()` para determinar dónde ubicar las bases de datos H2 (`memory.mv.db`, `service.mv.db`) y la subcarpeta `checkpoints/` donde se guardan los puntos de control narrativo. También escribe el archivo `turns.csv` (depuración) en esa misma ubicación. Sin `AgentPaths`, `SourceOfTruth` tendría que adivinar o recibir rutas por configuración, lo que complicaría su diseño.

**En `ReasoningService` y `Session`**: el servicio de razonamiento necesita persistir el estado de la conversación activa (`active_session.json`) y volcar el contexto actual para depuración (`last_context.json`). Ambos archivos se almacenan en `getDataFolder()` y `getTempFolder()` respectivamente, recuperados a través de las rutas que proporciona `AgentPaths`. Además, el prompt del sistema construido dinámicamente se escribe en `var/tmp/reasoning-system-prompt.md`, permitiendo al desarrollador inspeccionar qué instrucciones está recibiendo el LLM en cada momento.

**En `SensorsService`**: el estado sensorial (sensores registrados, cola de eventos, estadísticas) se serializa a `sensors.json` dentro de `getDataFolder()`. Al reiniciar el agente, `SensorsServiceImpl` lee este archivo desde la misma ubicación y rehidrata su estado interno, gracias a que `AgentPaths` le proporciona la ruta exacta donde debe buscarlo.

**En las herramientas del agente (`AgentTool`)**: muchas herramientas necesitan operar con el sistema de archivos. Por ejemplo:
- `FileReadTool`, `FileWriteTool` y `FilePatchTool` utilizan `agent.getAccessControl().resolvePath()` que internamente se basa en `agent.getPaths().getWorkspaceFolder()` como raíz del sandbox.
- `ShellExecuteTool` necesita conocer `getSandboxHomeFolder()` para configurar el entorno de `firejail` y `getTempFolder()` para almacenar la salida paginada de los comandos.
- `WebGetTikaTool` y `FileExtractTextTool` almacenan sus cachés en `getCacheFolder()`, utilizando `AgentPaths` para resolver rutas relativas a IDs de recurso.
- El sistema de paginación (`AbstractPaginatedAgentTool`) convierte identificadores como `tmp://...` o `cache://...` en rutas absolutas resolviéndolos contra `getTempFolder()` y `getCacheFolder()`, respectivamente. `AgentPaths` actúa aquí como un pequeño sistema de archivos virtual.

**En la UI de configuración**: los componentes Swing y de consola necesitan localizar `settingsui.json` (la definición de la interfaz) y los archivos de dominio (`.properties`). Utilizan `agent.getPaths().getConfigFolder().resolve(...)` para construirlos, lo que permite que la interfaz se adapte dinámicamente al workspace activo.

**En `BootUtils`**: durante la inicialización, se arranca el servidor web de H2 y se genera el archivo `.h2.server.properties` en `getConfigFolder()`, de modo que la consola web pueda conectarse a las bases de datos del workspace correcto.

Este patrón de **inyección por agregación** (el agente posee las rutas, y todos los servicios acceden a través de él) evita el uso de variables globales o singletons. Cualquier componente que necesite conocer una ubicación en disco puede obtenerla de forma predecible, sin acoplamientos ocultos. Además, facilita las pruebas unitarias: se puede crear un `AgentPaths` con un workspace temporal, inyectarlo en un agente simulado y verificar que los servicios escriben donde deben.

En conjunto, `AgentPaths` actúa como el **sistema de coordenadas del sandbox** y el **pegamento persistente** entre todos los subsistemas de Noema. Sin él, cada servicio tendría que gestionar sus propias rutas, con el consiguiente riesgo de fragmentación, errores de ubicación y dificultad para mantener la portabilidad.

## 8. Limitaciones y diseño deliberado

Como toda decisión arquitectónica, el enfoque de `AgentPaths` tiene limitaciones que no son fruto del descuido, sino de compensaciones conscientes entre simplicidad, portabilidad y funcionalidad. Es importante exponerlas para que quien lea este documento entienda por qué ciertas cosas no se hacen de otra manera.

**Ausencia de un sistema de archivos virtual**: `AgentPaths` no abstrae el acceso a disco tras una capa de red o almacenamiento en la nube. Todas las rutas son locales al sistema operativo donde se ejecuta Noema. Esto implica que el agente no puede (sin modificaciones) operar sobre archivos remotos (S3, NFS, etc.) más allá de lo que el propio sistema operativo permita montar de forma transparente. La decisión es intencionada: añadir una capa virtual habría multiplicado la complejidad del control de acceso, la paginación y la gestión de cachés, sin aportar un beneficio claro para el caso de uso principal (asistente de escritorio).

**Rutas absolutas resueltas en tiempo real**: Noema no almacena rutas canónicas ni utiliza enlaces simbólicos persistentes. Cada vez que una herramienta necesita acceder a un archivo, resuelve la ruta desde el workspace actual llamando a `resolvePath()`. Esto hace que mover el workspace de ubicación sea trivial (no hay rutas absolutas embebidas en los archivos de estado), pero también implica que referir un archivo por su ubicación en un momento posterior puede fallar si el workspace se ha reubicado entre sesiones. En la práctica, esto rara vez ocurre porque el workspace se selecciona al arrancar y no cambia durante la ejecución.

**Sin soporte para variables de entorno ni rutas dinámicas**: `AgentPaths` no expande variables como `${HOME}` o `~`. Todas las rutas se toman literalmente. Esto mantiene el código simple y predecible, pero obliga al usuario a escribir rutas completas si necesita acceder a directorios fuera del sandbox (aunque esas rutas deben estar explícitamente autorizadas en la lista blanca). Una posible mejora futura sería añadir un modesto resolutor de variables, pero hasta ahora no ha sido necesario.

**Dependencia de la fiabilidad del sistema de archivos**: Noema asume que las operaciones de creación, lectura, escritura y borrado son atómicas y confiables. Si el disco falla, se llena o los permisos cambian inesperadamente, el agente puede fallar de formas impredecibles. No hay un mecanismo de reintentos complejo ni una capa de abstracción que oculte estos errores. La filosofía es que el sistema de archivos subyacente debe ser robusto, y que el agente debe fallar rápido y limpiamente si no lo es.

**Sin gestión de cuotas ni limpieza automática global**: Como se mencionó antes, `AgentPaths` no supervisa el tamaño de `var/cache`, `var/tmp` o `var/log`. Es responsabilidad de cada servicio implementar sus propias políticas de retención. En la práctica, `AbstractPaginatedAgentTool` limpia recursos antiguos mediante un LRU, `ShellExecuteTool` rota sus salidas, y Log4j2 rota los logs. Sin embargo, no hay un recolector de basura transversal que garantice que, por ejemplo, la caché de documentos no crezca indefinidamente. En un uso prolongado, el usuario puede necesitar limpiar manualmente estas carpetas.

**No soporta múltiples workspaces simultáneos**: Una instancia de `AgentPaths` está ligada a un único workspace. No es posible que el agente opere sobre dos proyectos distintos al mismo tiempo sin reiniciarse. Esto es coherente con el diseño de "línea temporal única" de Noema, pero limita su uso como agente que coordina información entre varios repositorios independientes.

**Resolución de recursos limitada a dos niveles**: La búsqueda binaria (primero local, luego global) es suficiente para los casos de uso actuales, pero no es extensible a una jerarquía de más niveles (por ejemplo, recursos específicos de proyecto, luego del usuario, luego del sistema). Tampoco permite "sobrescribir" parcialmente recursos (modificar solo una línea de un prompt complejo sin copiar el archivo entero). Para necesidades más avanzadas, habría que rediseñar este subsistema.

Estas limitaciones no son defectos, sino **consecuencias de aplicar el principio de mínima potencia** al problema de la gestión de rutas. Noema prioriza la transparencia, la depuración sencilla y la portabilidad sobre la flexibilidad absoluta. Para un agente de investigación y acompañamiento personal, este equilibrio ha demostrado ser más que suficiente. En el improbable caso de que se necesite escalar a entornos distribuidos o a sistemas de archivos exóticos, siempre quedará la opción de reemplazar `AgentPaths` por una implementación más sofisticada sin alterar el resto de la arquitectura.

## 9. Conclusión: una base sólida pero modesta

`AgentPaths` es, en apariencia, un componente trivial: unas pocas docenas de líneas que construyen rutas y crean directorios. Sin embargo, su diseño refleja las prioridades arquitectónicas de todo Noema: **transparencia, portabilidad y control explícito**.

Al tratar el sistema de archivos como el estado único y poner todas las rutas al alcance de la mano mediante una API mínima pero completa, `AgentPaths` permite que el resto de servicios se centren en su lógica de negocio sin preocuparse por dónde persisten sus datos. Un desarrollador que necesite depurar `SourceOfTruth` sabe exactamente dónde buscar los archivos de la base de datos H2; alguien que quiera ajustar el comportamiento del agente puede editar los prompts en `var/config/prompts/` sin recompilar; un usuario que desee trasladar su agente a otro equipo simplemente copia la carpeta `.noema-agent`.

Al mismo tiempo, `AgentPaths` es modesto en sus ambiciones. No intenta resolver problemas que Noema no tiene (sistemas de archivos distribuidos, cuotas, jerarquías complejas). Su resolución en dos niveles (local/global) es suficiente para la gran mayoría de los casos de uso, y su decisión de no cachear rutas ni expandir variables mantiene el código simple y el comportamiento determinista.

La verdadera fortaleza de `AgentPaths` no reside en su sofisticación, sino en cómo se integra con el resto del ecosistema. Desde `AgentAccessControl` (que lo usa como base del sandbox) hasta `Session` (que escribe `active_session.json` en `getDataFolder()`), pasando por `AbstractPaginatedAgentTool` (que resuelve `tmp://` y `cache://` contra las carpetas correspondientes), cada componente confía en `AgentPaths` para orientarse en el sistema de archivos. Esta confianza mutua es lo que permite que Noema funcione como un todo coherente, sin fugas de abstracción ni configuraciones redundantes.

En resumen, `AgentPaths` no es la parte más brillante ni compleja de Noema, pero es quizás la que mejor ejemplifica su filosofía de diseño: **hacer lo suficiente para que el resto pueda hacer su trabajo, y nada más**. Para un agente que aspira a perdurar en el tiempo dentro del sistema de archivos de un usuario corriente, ese equilibrio entre funcionalidad y simplicidad es una virtud, no una carencia.

