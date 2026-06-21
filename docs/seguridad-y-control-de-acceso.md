## Seguridad y Control de Acceso (`AgentAccessControl`)

### 1. Introducción

Noema no es un simple conversador; tiene la capacidad de leer y escribir archivos, ejecutar comandos en el sistema operativo y conectarse a servicios externos. Esta autonomía es necesaria para que el agente resulte útil, pero también introduce riesgos evidentes: un error del modelo, una alucinación o una instrucción maliciosa podrían tener consecuencias no deseadas sobre el sistema de archivos o la privacidad del usuario.

Para gestionar este dilema, Noema incorpora un subsistema de seguridad explícito centrado en la clase `AgentAccessControl`. Su misión es doble: por un lado, **definir qué operaciones están permitidas** en función del contexto y la configuración; por otro, **someter las operaciones peligrosas a confirmación humana** antes de ejecutarlas. Actúa como un guardián que filtra todas las acciones del agente, asegurando que la autonomía se ejerza siempre dentro de unos límites controlados.

El diseño parte de una premisa pragmática: la seguridad no consiste en impedir que el agente actúe, sino en garantizar que cada acción con posibles efectos destructivos cuente con la supervisión explícita del usuario. De este modo, Noema puede ofrecer capacidades avanzadas (escribir archivos, ejecutar scripts) sin renunciar a la confianza del operador humano.

### 2. El modelo de permisos: modos de acceso y políticas

Toda herramienta (`AgentTool`) declara, mediante el método `getMode()`, uno de los siguientes modos de operación:

- **`MODE_READ`**: operaciones de solo lectura (leer un archivo, consultar una API, buscar en el historial). No alteran el estado del sistema y se consideran seguras.
- **`MODE_WRITE`**: operaciones que modifican el sistema de archivos (escribir, parchear, crear directorios). Pueden destruir información si se usan incorrectamente.
- **`MODE_EXECUTION`**: ejecución de comandos en el shell del sistema. El más peligroso, pues permite cualquier acción que el usuario pueda realizar desde la terminal.
- **`MODE_WEB`**: acceso a internet (búsquedas, descargas). Aunque no suele ser destructivo, puede comprometer la privacidad o consumir recursos.

Estos modos se combinan con políticas globales que el usuario puede configurar en `settings.json` bajo la sección `access_control`:

```json
"access_control": {
  "humanConfirmationRequired": true,
  "allow_disk_write": false,
  "allow_shell_execution": false,
  "allow_internet_access": false,
  "enable_rcs_backup": true,
  "enable_firejail": false,
  ...
}
```

`AgentAccessControl` expone métodos como `isAllowedDiskWrite()`, `isAllowedShellExecution()` e `isAllowedInternetAccess()`. Si una herramienta intenta ejecutarse en un modo que está deshabilitado globalmente, `isToolAllowed()` devuelve `false` y `ReasoningService` ni siquiera la ofrecerá al modelo (o la ejecución se denegará). Esta doble capa (declaración local + política global) permite un control muy fino: el usuario puede, por ejemplo, permitir lectura de archivos pero prohibir cualquier escritura, o activar la ejecución de shell solo cuando realmente confíe en el agente.

### 3. El sandbox de archivos

El control de acceso al sistema de archivos se basa en un mecanismo de **resolución de rutas** implementado en `resolvePath(String rawPath, AccessMode mode)`. El proceso es el siguiente:

1. **Normalización y absoluto**: la ruta introducida (puede ser relativa o absoluta) se resuelve contra la raíz del workspace (`getWorkspaceFolder()`). Se normaliza y se convierte a ruta real (`toRealPath()`) para eliminar `..` y enlaces simbólicos maliciosos.

2. **Comprobación de jailbreak**: se verifica que la ruta resultante esté dentro del workspace o dentro de alguna de las rutas externas autorizadas (lista blanca configurable mediante `allowed_external_paths`). Si no es así, se lanza una excepción de seguridad.

3. **Restricciones específicas de escritura**: si el modo es `PATH_ACCESS_WRITE`, se aplican reglas adicionales:
   - No se puede escribir sobre archivos con extensión `,jv` (los backups de RCS). Son de solo lectura para preservar la integridad del historial de versiones.
   - No se puede escribir dentro de la carpeta `.git` (evita corromper repositorios de control de versiones).
   - Se comprueba si la ruta está en `nom_writable_paths` (lista de rutas no escribibles configurada por el usuario).

4. **Comprobaciones de lectura**: incluso para `PATH_ACCESS_READ`, se verifica que la ruta no esté en `nom_readable_paths` (lista de rutas prohibidas, como archivos de configuración sensibles del agente).

Si alguna de estas condiciones falla, se lanza una `SecurityException`. Para situaciones en las que no se desea interrumpir el flujo (por ejemplo, al listar archivos), existe `resolvePathOrNull()` que devuelve `null` en lugar de lanzar excepción.

Este diseño impide eficazmente los ataques de *path traversal* (ejemplo: `../../etc/passwd`). Además, es extensible: el usuario puede añadir nuevas rutas a la lista blanca (como su carpeta `Documentos`) y restringir otras que considere peligrosas.

### 4. Confirmación humana

El filtro más importante es la **confirmación humana**. Cuando una herramienta con modo `MODE_WRITE` o `MODE_EXECUTION` está a punto de ejecutarse, y la política global `humanConfirmationRequired` está activa, `AgentAccessControl` (o más bien el `ReasoningService` antes de invocar la herramienta) solicita autorización al usuario mediante `AgentConsole.confirm()`.

El mensaje incluye el nombre de la herramienta y los argumentos que se van a utilizar. Por ejemplo:

```
El agente quiere ejecutar la herramienta: file_write
Argumentos: {"path": "config.json", "content": "{\"key\": \"value\"}"}
¿Autorizar? (s/n):
```

El usuario puede responder afirmativa o negativamente. Si deniega, la herramienta no se ejecuta y se devuelve un mensaje de error que el LLM recibe como resultado de su llamada. El agente puede entonces explicar que la operación no fue autorizada y, opcionalmente, proponer una alternativa.

La confirmación es **bloqueante**: el hilo del `eventDispatcher` se detiene hasta que el usuario responda. Esto es intencionado, pues el agente no debe continuar razonando mientras una acción peligrosa está pendiente de decisión. En la interfaz gráfica, se muestra un diálogo modal; en la consola, se espera entrada por teclado.

Este mecanismo sitúa al usuario en la posición de **supervisor último**. Incluso si el agente, por error o engaño, intenta borrar un archivo crítico, el humano tiene la oportunidad de detenerlo. Es una salvaguarda rudimentaria pero efectiva, especialmente en una fase de prototipo donde el comportamiento del LLM no es totalmente fiable.

### 5. Backup automático con RCS

Antes de que cualquier herramienta modifique un archivo existente (escritura, parche, búsqueda y reemplazo), se invoca al sistema RCS embebido (JavaRCS) para hacer un **check-in automático** de la versión actual. El código típico es:

```java
if (Files.exists(filePath)) {
    RCSManager rcsmanager = RCSLocator.getRCSManager();
    CheckinOptions opciones = rcsmanager.createCheckinOptions(filePath);
    opciones.setAuthor(getReasoningService().getModelName());
    opciones.setInit(true);
    RCSCommand ci = rcsmanager.create(opciones);
    ci.execute(opciones);
}
```

El resultado es que, junto al archivo original, se genera un fichero de historial (normalmente con extensión `,jv`) que contiene todas las versiones anteriores. El LLM puede recuperar una versión antigua mediante las herramientas `file_history` (para listar revisiones) y `file_recovery` (para restaurar una revisión concreta).

Esta funcionalidad, que se activa mediante `enable_rcs_backup` (por defecto `true`), constituye una **red de seguridad** frente a errores del LLM. Si el agente escribe un contenido erróneo o corrompe un archivo, el usuario. o el propio agente, puede deshacer el cambio. Además, fomenta la experimentación: el usuario puede permitir escrituras sin temor a perder información valiosa.

### 6. Ejecución de comandos

La herramienta `shell_execute` es la más sensible, pues permite ejecutar cualquier comando en el sistema. Por ello, incorpora capas de protección adicionales:

- **Confirmación humana obligatoria**: su modo es `MODE_EXECUTION`, y siempre requiere autorización explícita, independientemente de otras políticas.

- **Sandboxing con firejail**: si el sistema tiene instalado `firejail` y `enable_firejail` está activo, el comando se envuelve en un entorno restringido. El directorio home del agente se aísla, el acceso al sistema de archivos se limita a una lista blanca (el workspace y poco más), y se bloquean ciertas capacidades de red. La herramienta detecta automáticamente si `firejail` está disponible y muestra una advertencia si no lo está.

- **Timeout y control de procesos**: el comando se lanza en un proceso separado y se supervisa. Cada 30 segundos se pregunta al usuario si desea continuar esperando, permitiéndole abortar comandos que se eternicen.

- **Captura de salida**: la salida estándar y de error se redirigen a un archivo temporal en `var/tmp`. La salida se sirve paginada mediante `AbstractPaginatedAgentTool`, evitando saturar la ventana de contexto del LLM.

- **Desactivación global**: el usuario puede prohibir completamente la ejecución de comandos mediante `allow_shell_execution: false`. En ese caso, la herramienta ni siquiera aparecerá en el catálogo de capacidades del agente.

Estas medidas reducen drásticamente el riesgo de que un comando malicioso o erróneo cause daños. No obstante, la responsabilidad última sigue recayendo en el usuario, que debe autorizar cada ejecución conscientemente.

### 7. Recarga en caliente y listas dinámicas

La configuración de seguridad puede modificarse sin reiniciar el agente gracias a la acción `RELOAD_ACCESS_CONTROL`. Cuando el usuario cambia alguna de las listas (rutas blancas, rutas prohibidas, flags booleanos) en `settings.json`, puede ejecutar esta acción desde el menú de depuración. `AgentAccessControlImpl` vuelve a leer todas las propiedades y actualiza sus estructuras internas (listas de rutas, flags). Esto permite, por ejemplo, autorizar temporalmente la escritura en disco para una tarea específica y revocarla después, todo ello sin detener al agente.

Las listas se gestionan mediante `AgentSettingsPaths` y `AgentSettingsCheckedList`, lo que facilita su edición desde la interfaz gráfica de configuración. El usuario puede añadir o eliminar rutas externas permitidas, marcar directorios como de solo lectura, o establecer exclusiones completas, todo desde una UI amigable.

### 8. Integración con el subsistema de herramientas

`AgentAccessControl` no actúa de forma aislada; está integrado en los puntos críticos del flujo de ejecución:

- **En `ReasoningService`**: antes de ejecutar cualquier herramienta, se invoca a `accessControl.isToolAllowed(tool)`. Si devuelve `false`, la herramienta se considera no disponible (no se ofrece al modelo) o su ejecución se deniega con un mensaje de error.

- **En `AbstractAgentTool` y sus descendientes**: los métodos `resolvePathOrNull()` y `resolvePath()` utilizan `agent.getAccessControl()` para validar cada acceso a archivo. De este modo, todas las herramientas de lectura/escritura comparten la misma política de sandbox.

- **En `AgentPaths`**: aunque no depende directamente de `AgentAccessControl`, la raíz del workspace (`getWorkspaceFolder()`) es el punto de partida para la resolución de rutas. Ambas clases colaboran estrechamente.

- **En `ShellExecuteTool`**: además de consultar `isToolAllowed()`, verifica `isFirejailEnabled()` y utiliza `getSandboxHomeFolder()` para configurar el entorno aislado.

Esta integración garantiza que no haya ningún "camino secreto" para eludir los controles de seguridad. Cada operación de lectura, escritura o ejecución pasa por el guardián.

### 9. Limitaciones y decisiones deliberadas

El sistema de seguridad de Noema no pretende ser infranqueable ni adecuado para entornos multiusuario. Está diseñado para un agente de escritorio que opera en una sola máquina bajo la supervisión directa del usuario. Por ello, presenta limitaciones asumidas:

- **Sin control de acceso basado en roles**: no hay distinción entre distintos tipos de usuarios (administrador, invitado). Solo existe el usuario que ejecuta el agente.

- **Sin sandboxing a nivel de red**: la política `allow_internet_access` es un veto global. No se pueden permitir ciertos dominios y denegar otros, ni restringir por puertos o protocolos.

FIXME: Repasar lo del sandboxing a nivel de red, no tengo claro si es correcto.

- **Dependencia de `firejail` externo**: el sandbox de comandos solo funciona si `firejail` está instalado en el sistema. Noema no proporciona su propio contenedor ni mecanismos de aislamiento más ligeros.

- **Confirmación humana bloqueante**: no hay timeout. Si el usuario se ausenta, el agente quedará detenido indefinidamente esperando respuesta. Esto puede ser problemático en tareas automáticas que requieran supervisión.

- **Protección limitada contra ataques de inyección**: si el LLM recibe un prompt malicioso que le hace invocar herramientas con argumentos peligrosos, el filtro de rutas y la confirmación humana pueden detenerlo, pero no hay análisis semántico de los argumentos (por ejemplo, detectar `rm -rf /`).

Estas limitaciones son aceptables para un prototipo de investigación. En un escenario de producción o de alta seguridad se requerirían medidas adicionales (como listas de comandos permitidos, análisis heurístico de la salida del modelo o ejecución en contenedores completos).

### 10. Conclusión

`AgentAccessControl` no es un sistema de seguridad industrial, pero proporciona las barreras necesarias para prevenir daños accidentales y mantener al usuario en control. Su diseño combina tres principios fundamentales:

- **Declaración explícita del peligro**: cada herramienta etiqueta su modo, y el sistema aplica políticas coherentes.
- **Defensa en profundidad**: sandbox de archivos + confirmación humana + backups automáticos + sandbox de comandos.
- **Transparencia y control**: el usuario puede inspeccionar y modificar todas las políticas en caliente, y es consultado antes de cualquier acción irreversible.

Gracias a este diseño, Noema puede ofrecer capacidades avanzadas (escritura de archivos, ejecución de comandos) sin generar una sensación de inseguridad constante. El usuario sabe que, en última instancia, la decisión es suya. Y si algo sale mal, los backups RCS permiten deshacer el cambio. Es un equilibrio pragmático que refleja bien la filosofía general del proyecto: **autonomía con supervisión, poder con responsabilidad**.
