

# Arquitectura de Punteros y Paginación para el Sistema de Herramientas

## 1. Introducción y Motivación

A medida que el ecosistema de herramientas del agente ha ido evolucionando, la gestión de operaciones que devuelven grandes volúmenes de texto se ha convertido en un desafío arquitectónico central. Los Modelos de Lenguaje Grande (LLMs) operan bajo restricciones estrictas en su ventana de contexto; saturar esta ventana con volcados masivos de datos (como el contenido de un libro entero, el código fuente de un proyecto grande o la salida verbosa de un log de compilación) no solo dispara los costes de inferencia, sino que degrada severamente la capacidad de razonamiento y atención del modelo ("lost in the middle"). 

Para mitigar este problema, en las etapas tempranas del desarrollo se introdujo un mecanismo de paginación iterativa. Esta solución se implementó inicialmente de forma aislada en la herramienta `FileReadTool`, permitiendo al agente leer archivos de texto plano mediante bloques manejables (controlados por parámetros de `offset` y `limit`) y devolviendo un mensaje de ayuda o "HINT" cuando el contenido quedaba truncado. La estrategia demostró ser altamente efectiva en la práctica. Sin embargo, el éxito de este patrón provocó que su uso se extendiera de manera orgánica, pero no planificada, hacia otras capacidades del agente. 

Herramientas de naturalezas completamente distintas, como la extracción de texto de documentos binarios (`FileExtractTextTool`), la lectura concurrente de múltiples ficheros (`FileReadSelectorsTool`) y, más tarde, la ejecución de comandos de sistema (`ShellExecuteTool`), comenzaron a requerir este mismo comportamiento. Para no duplicar la lógica de cálculo de líneas y formateo, se optó por un enfoque pragmático basado en la "delegación en cascada": estas herramientas ejecutan su tarea principal, guardan el resultado en un archivo físico temporal y, a continuación, invocan de manera subrepticia la lógica de `FileReadTool` para devolver la primera página al LLM. Aunque funcional, este parche arquitectónico ha alcanzado su límite de escalabilidad. La intención actual de incorporar capacidades de navegación y extracción de contenido web (`WebGetTikaTool`) ha puesto de manifiesto que continuar apilando dependencias sobre este flujo compromete seriamente la mantenibilidad del código y la coherencia del sistema.

### 1.1. El problema del acoplamiento y la carga cognitiva

El diseño actual adolece de dos deficiencias críticas fundamentales que operan en diferentes niveles del sistema: uno puramente técnico (código) y otro heurístico (interacción con el modelo de lenguaje).

En el **nivel técnico**, el sistema sufre de un fuerte acoplamiento y una violación del Principio de Responsabilidad Única (SRP). Herramientas que deberían limitarse a interactuar con el sistema operativo (ejecutar un bash script) o con la red (descargar un HTML) terminan siendo responsables de orquestar flujos de lectura de archivos, instanciar a otras herramientas hermanas y gestionar la higiene de directorios temporales. Este acoplamiento bidireccional oscurece la trazabilidad del código. Además, provoca la aparición de clases puente redundantes cuya única razón de existir es mantener la ilusión de continuidad. Un ejemplo paradigmático es `ShellReadOutputTool`, una herramienta que actúa como un mero intermediario vacío para traducir las peticiones del LLM hacia el motor de lectura oculto en `FileReadTool`. Si el modelo actual persiste, cada nuevo dominio de obtención de datos obligará a crear nuevas herramientas puente equivalentes, multiplicando la deuda técnica.

En el **nivel heurístico o cognitivo**, la arquitectura actual penaliza la eficiencia del LLM. El agente, al actuar como "usuario" de estas herramientas, debe mapear mentalmente un árbol de decisiones innecesariamente complejo para una tarea que, conceptualmente, siempre es la misma: *seguir leyendo*. En el estado actual, si el agente lee un archivo normal truncado, debe aprender a invocar de nuevo `file_read`. Si ejecuta un comando largo, el sistema le obliga a recordar que debe invocar una herramienta distinta llamada `shell_read_output`. Si en el futuro extrae una web, se le pediría que usara un hipotético `web_read_output`. Esta inconsistencia fragmenta la atención del agente, consume tokens de razonamiento en la toma de decisiones triviales y aumenta significativamente la probabilidad de alucinaciones (por ejemplo, que el modelo intente usar `file_read` para leer el ID de un proceso de shell, provocando un error). Un sistema de herramientas maduro debe abstraer la complejidad subyacente y ofrecer al modelo una interfaz uniforme, predecible y ortogonal.

## 2. Diseño Conceptual: Arquitectura de Punteros

Para resolver las deficiencias del diseño basado en delegación en cascada, se propone una reestructuración profunda inspirada en los conceptos clásicos de los sistemas operativos (como los descriptores de archivos en UNIX) y la gestión de memoria virtual. El nuevo diseño se apoya en un patrón que denominaremos **"Arquitectura de Punteros"**. El objetivo principal de este paradigma es desacoplar por completo la fase de *obtención/generación* de información masiva de la fase de *consumo/lectura* por parte del Modelo de Lenguaje (LLM). 

En lugar de que el sistema intente gestionar flujos de datos en la memoria viva (RAM de la JVM) o de empaquetar resultados parciales dentro de la lógica misma de las herramientas ejecutoras, la nueva arquitectura delega el almacenamiento en el sistema de archivos físico del propio agente. Cuando una herramienta recibe una solicitud que implica la obtención de grandes volúmenes de texto —ya sea la respuesta a una petición GET de HTTP, el volcado exhaustivo de un volcado de error de compilador, o la transcripción de un documento PDF complejo—, la herramienta no intentará inyectar este bloque directamente en el flujo de conversación, ni invocará a otra herramienta para que lo haga. Su responsabilidad será estrictamente funcional: ejecutará la acción, atrapará el flujo de salida y lo persistirá íntegramente en un archivo físico dentro del espacio de trabajo privado del agente. 

La pieza central de esta innovación conceptual es la transición de un modelo "Push" (donde la herramienta empuja la información al contexto) a un modelo "Pull diferido". Una vez finalizada la persistencia física, la herramienta originaria empaquetará únicamente el primer bloque de texto predeterminado (la primera página) y se lo presentará al LLM. Sin embargo, y esto es lo crucial, junto a este bloque inicial no devolverá una instrucción específica de dominio, sino que entregará un "puntero": una cadena de texto (como `var/tmp/web_request_a1b2.txt`) que actúa como un identificador único y universal de ese recurso interno persistido.

Este enfoque altera fundamentalmente la forma en que el LLM comprende su propio acceso a la información. El modelo deja de percibir las respuestas como respuestas cerradas y monolíticas procedentes de APIs dispares, y comienza a percibir que ciertas acciones generan "documentos" en su mesa de trabajo virtual. El "puntero" devuelto no es más que la etiqueta de ese documento. De esta forma, el LLM adquiere un control explícito sobre la carga de su contexto: si la primera página de un resultado de búsqueda web o de un `grep` extensivo contiene la respuesta deseada, el modelo abandona el puntero y continúa su razonamiento. Si, por el contrario, necesita profundizar, utilizará ese puntero como clave para realizar operaciones de "page-in", trayendo selectivamente bloques sucesivos a su ventana de contexto activa sin necesidad de volver a ejecutar la costosa o lenta operación original (como una nueva petición de red o un nuevo análisis de Tika). 

Este rediseño no solo aporta idempotencia a la lectura posterior —permitiendo al LLM avanzar, retroceder o saltar a líneas específicas de un resultado sin efectos secundarios en el mundo real— sino que también blinda la integridad de la base de código de Java, erradicando los bucles de dependencias cruzadas entre las clases que implementan las herramientas del agente.

### 2.1. El modelo Productor-Lector y el "Hint" universal

La materialización de la Arquitectura de Punteros exige redefinir estrictamente las responsabilidades de los componentes bajo un patrón clásico de "Productor-Consumidor", donde las barreras entre quién genera los datos y quién los lee quedan nítidamente delimitadas. En este nuevo modelo, todas las herramientas operativas que interactúan con el mundo exterior (`WebGetTikaTool`, `ShellExecuteTool`, `FileExtractTextTool`) adoptan el rol exclusivo de **Productores puros**. La responsabilidad de un Productor comienza al recibir la orden del LLM y termina en el instante en que el flujo de datos resultante (ya sea un HTML limpio, un log de errores o el texto extraído de un PDF) es serializado y guardado exitosamente en un archivo físico dentro del espacio de trabajo del agente. El Productor no necesita saber cómo el LLM va a digerir esa información; su única labor de cara al modelo es devolver un primer fragmento representativo del contenido, acompañado de sus metadatos básicos (tamaño total, líneas, éxito de la operación) y el identificador físico del recurso creado. Esta estricta separación de roles elimina de raíz la necesidad de que una herramienta de red o de sistema operativo tenga que instanciar o delegar en clases especializadas en la lectura y paginación de ficheros.

Por otro lado, el modelo de Lenguaje asume el rol de **Consumidor activo**, pero lo hace a través de una interfaz radicalmente simplificada. En lugar de disponer de un abanico de herramientas de lectura específicas para cada dominio (una para shell, otra para web, otra para archivos), el agente dispondrá de una única herramienta de lectura de propósito general. Esta unificación transforma la percepción del agente: cualquier operación masiva converge en un recurso estandarizado que siempre se consume de la misma manera. Si el agente desea profundizar en los datos generados por cualquiera de los Productores, canalizará su petición exclusivamente a través de esta herramienta universal, proporcionando el identificador del recurso y los parámetros de desplazamiento (`offset` y `limit`).

El elemento tecnológico que cohesiona a los Productores con este único canal de Consumo es el mecanismo del **"Hint" (Pista o Sugerencia) universal**. Dado que los LLMs son sistemas probabilísticos, depender de que el modelo recuerde proactivamente qué herramienta usar y con qué parámetros exactos es una fuente constante de fallos y alucinaciones. Para mitigar esto, se estandariza un protocolo de comunicación en la salida de las herramientas: siempre que un Productor detecte que el volumen de datos excede el límite de líneas o tokens permitido para una sola respuesta, truncará el contenido y adjuntará automáticamente un bloque de directiva del sistema al final del texto. 

Este "Hint" operará como una *afordancia* cognitiva inyectada directamente en el contexto del modelo. Su estructura será inmutable y contendrá la firma exacta de la llamada JSON que el agente debe generar en su siguiente turno si desea continuar leyendo. Por ejemplo, en lugar de un mensaje genérico de "Salida truncada", el sistema anexará una directiva explícita como: `[HINT: To read the next block, call 'read_paginated_resource' with args: {"resource_id": "var/tmp/web_hash.txt", "offset": 1000, "limit": 1000}]`. Al estandarizar este Hint universal a nivel de la clase base, se garantiza que, sin importar cuán exótica sea la herramienta Productora que se desarrolle en el futuro, el LLM siempre recibirá las instrucciones precisas, sintácticamente perfectas y listas para ser ejecutadas, cerrando la brecha entre la recolección de datos y su consumo iterativo sin fisuras.


### 2.2. Gestión del almacenamiento interno (Tmp vs Caché)

La adopción de una Arquitectura de Punteros, al depender de la escritura sistemática de archivos físicos antes de presentar la información al LLM, introduce el reto de administrar eficientemente el espacio en disco del sistema anfitrión. Sin una política estricta de ciclo de vida, el espacio de trabajo del agente acumularía indefinidamente "residuos cognitivos" (salidas de comandos obsoletas, descargas web antiguas o transcripciones de ficheros ya eliminados), degradando el rendimiento a largo plazo. Para resolver esto, el diseño establece una separación física y semántica del almacenamiento interno, dividiendo los recursos generados en dos categorías regidas por la interfaz `AgentPaths`: el almacenamiento puramente volátil (`var/tmp`) y la memoria persistente condicional (`var/cache`). Esta dicotomía permite aplicar estrategias de higiene (Garbage Collection) radicalmente distintas en función del coste computacional y la temporalidad de los datos subyacentes.

El directorio `var/tmp` se define como un espacio de trabajo efímero, diseñado para albergar recursos desechables cuya relevancia está estrictamente ligada al "aquí y ahora" del flujo de razonamiento del agente. En este directorio recaerán los volcados de herramientas como `ShellExecuteTool` (que captura el `stdout` y `stderr` de procesos del sistema) o `WebGetTikaTool` (que descarga y limpia el HTML de una URL solicitada en ese instante). Dado que reproducir estos datos es relativamente barato o, por el contrario, su naturaleza es tan dinámica que carece de sentido conservarlos a largo plazo, el sistema ejerce una política de limpieza agresiva sobre este directorio. El contenido de `var/tmp` puede ser purgado en su totalidad en cualquier momento de inactividad, al reiniciar el agente, o al consolidar un punto de guardado de memoria, garantizando así que no queden punteros huérfanos ocupando espacio innecesario una vez que la conversación ha avanzado hacia otros temas.

Por el contrario, el directorio `var/cache` opera bajo una filosofía de persistencia vinculada al estado. Este espacio está reservado para los resultados de procesos que implican un alto coste computacional o de I/O, donde la idempotencia real requiere evitar la repetición del trabajo. El caso de uso paradigmático es la herramienta `FileExtractTextTool`, que utiliza el motor de Apache Tika para desensamblar y extraer texto plano de documentos binarios pesados (como archivos PDF, DOCX o presentaciones). Reprocesar un PDF de cientos de páginas cada vez que el LLM solicita leer una nueva porción introduciría una latencia inaceptable en el tiempo de respuesta del agente. Por ello, los archivos en `var/cache` se indexan mediante algoritmos de hash criptográfico basados en la ruta absoluta del fichero original, creando una vinculación directa entre el recurso y su caché. 

La invalidación en `var/cache` no se rige por limpiezas periódicas ciegas, sino por la observación de las fechas de modificación (timestamps): el sistema solo descartará y volverá a generar el archivo de texto en caché si detecta que el documento binario original ha sido modificado con posterioridad a la creación del volcado. De este modo, la arquitectura protege el rendimiento del sistema conservando el trabajo pesado ya realizado, al mismo tiempo que blinda al LLM de toda esta complejidad subyacente. Para el modelo de lenguaje, la distinción entre `tmp` y `cache` es totalmente invisible; él simplemente interactúa con un identificador de recurso unificado, confiando en que la infraestructura inferior gestionará de forma óptima la procedencia y la vida útil de los bytes que está leyendo.

# 3. Implementación y Cambios Estructurales

La materialización de la Arquitectura de Punteros y la separación de roles entre Productores y Consumidores requiere una refactorización guiada por principios estrictos de diseño orientado a objetos. En la estructura heredada, la clase base `AbstractAgentTool` servía como punto común para todas las herramientas del sistema (desde el envío de emails hasta la creación de directorios), proporcionando utilidades generales como el acceso a la instancia del agente, la serialización de errores en JSON y la validación de rutas contra las políticas de control de acceso. Inyectar la compleja maquinaria de manejo de streams, caché de contadores de líneas y generación de metadatos de paginación directamente en esta clase base constituiría una grave violación del principio de segregación de interfaces, ya que forzaría a herramientas simples y de disparo único a heredar una carga de dependencias I/O que jamás van a utilizar. 

Por tanto, los cambios estructurales propuestos se articulan en torno a la reorganización de la jerarquía de herencia. En lugar de engordar la superclase universal, se introducirá un nuevo nivel intermedio de abstracción diseñado específicamente para gobernar los flujos de lectura fragmentada. Este nuevo eslabón actuará como una plantilla especializada de la que heredarán, exclusivamente, aquellas herramientas cuya semántica requiera emitir o leer datos masivos. 

En paralelo, esta reestructuración posibilitará una limpieza profunda del catálogo de herramientas activas. Al centralizar la lógica de lectura y delegar el almacenamiento en las directivas de `var/tmp` y `var/cache`, los componentes que hasta ahora funcionaban como "parches" de interconexión (es decir, herramientas que no interactuaban con el mundo real, sino que existían únicamente para reenviar parámetros internos a `FileReadTool`) perderán su razón de ser. Esto resultará en una reducción neta de la superficie de código, una disminución de la cantidad de descripciones de herramientas (Tool Specifications) que se envían en el prompt del sistema al modelo de lenguaje (ahorrando tokens preciosos en cada inferencia) y un mapa de clases mucho más ortogonal, donde cada herramienta tendrá un propósito único, claro e indivisible. 

Las siguientes subsecciones detallan la anatomía de la nueva superclase, el diseño de la herramienta universal de consumo, el impacto de refactorización en las herramientas existentes y la consecuente eliminación de la deuda técnica acumulada.

### 3.1. Nueva clase base: AbstractPaginatedAgentTool

Para mantener la pureza arquitectónica y evitar el engrosamiento innecesario de la clase `AbstractAgentTool`, se introduce un nuevo nivel en la jerarquía de herencia: la clase abstracta `AbstractPaginatedAgentTool`. Esta clase actuará como el verdadero "motor de paginación" del sistema, concentrando exclusivamente la lógica algorítmica y de entrada/salida (I/O) necesaria para trocear grandes volúmenes de texto de manera eficiente y presentar un formato estándar al modelo de lenguaje. Cualquier herramienta del agente que en su ciclo de vida necesite emitir un resultado que pueda superar los límites de contexto del LLM, deberá heredar obligatoriamente de esta nueva superclase.

El corazón de `AbstractPaginatedAgentTool` estará constituido por el método protegido `servePaginatedResource`. Este método asume la responsabilidad crítica de transformar un archivo físico (ya sea código fuente, un volcado web en `var/tmp` o una extracción PDF en `var/cache`) en una cadena de texto lista para el consumo de la IA. El algoritmo que encapsula este método realizará múltiples tareas concurrentes: en primer lugar, calculará de manera perezosa el total de líneas del recurso (utilizando sistemas de caché interna basados en la fecha de modificación del archivo para evitar conteos repetitivos costosos). A continuación, abrirá un flujo de lectura (`Stream<String>`) para extraer estrictamente el bloque de texto delimitado por los parámetros de desplazamiento (`offset`) y tamaño de página (`limit`). 

Más allá de la mera extracción de bytes, la función primordial de esta clase base es la **formateación semántica** de la respuesta. `AbstractPaginatedAgentTool` inyectará automáticamente un encabezado de metadatos estandarizado en cada respuesta (por ejemplo, `STATUS: OK`, el indicador de archivo vacío `EMPTY: true/false`, y el contador de progreso `[SYSTEM: Showing lines X-Y of Z]`). 

Finalmente, la característica más sofisticada de este motor base radica en la generación dinámica del "Hint" de continuación. El método `servePaginatedResource` se diseñará para recibir por parámetro metadatos sobre *quién* lo está llamando y *cómo* el LLM debe continuar la lectura. De este modo, si el recurso está truncado, la superclase calculará el nuevo `offset` e imprimirá el bloque de ayuda exacto, instruyendo al LLM para que invoque a la herramienta de lectura universal con el identificador físico correspondiente. Al encapsular toda esta complejidad en `AbstractPaginatedAgentTool`, se libera a las clases hijas de tener que reinventar la gestión de streams de Java o de hardcodear los mensajes de asistencia al modelo, reduciendo drásticamente la repetición de código y asegurando que todas las herramientas del sistema obedezcan a un formato de salida matemáticamente consistente.


### 3.2. La herramienta ReadPaginatedResourceTool

La herramienta `ReadPaginatedResourceTool` se establece como el Consumidor Universal en la nueva arquitectura de punteros, centralizando toda la lógica de recuperación de información paginada. Su función primaria es actuar como la interfaz única a través de la cual el Modelo de Lenguaje accede a los buffers físicos de información generados por los Productores. Mientras que las herramientas productoras (como `WebGetTikaTool` o `ShellExecuteTool`) se encargan de la "fase de ejecución" y la persistencia inicial del recurso en `var/tmp` o `var/cache`, `ReadPaginatedResourceTool` es la encargada de la "fase de visualización". Al ser una herramienta independiente, delegamos en ella el ciclo de vida de la lectura: el LLM la invoca explícitamente cuando una respuesta anterior indica que el contenido está truncado, pasándole el `resource_id` (la ruta al archivo generado) y las coordenadas de lectura (`offset` y `limit`).

Su diseño es fundamentalmente ortogonal y está dotado de estrictas medidas de seguridad. `ReadPaginatedResourceTool` incorpora un mecanismo de validación de rutas que actúa como una salvaguarda del sandbox: el identificador de recurso debe estar contenido inequívocamente dentro del árbol `var/`. Esta restricción es crítica, ya que previene que el LLM —en un intento de realizar tareas de lectura de código fuente del proyecto— confunda la herramienta de lectura de buffers temporales con la herramienta de lectura de archivos de proyecto (`FileReadTool`). Al restringir el ámbito de acción de `ReadPaginatedResourceTool` exclusivamente a los recursos internos, se establece una separación clara de responsabilidades: el agente utilizará `file_read` para interactuar con la lógica del proyecto y `read_paginated_resource` exclusivamente para consumir los resultados generados por su propia actividad previa.

Desde una perspectiva técnica, esta herramienta no implementa lógica compleja de paginación por sí misma, sino que invoca el método protegido `servePaginatedResource` provisto por su superclase `AbstractPaginatedAgentTool`. Esta relación establece una inversión de control donde la herramienta específica solo se encarga de resolver la ruta lógica (el `resource_id`) y configurar los parámetros de llamada, mientras que el motor de la superclase gestiona la complejidad de los streams, la lectura eficiente de archivos y el formateo de la respuesta para el modelo. La herramienta es, en esencia, un intérprete de punteros: recibe una referencia a un archivo ya existente y despliega su contenido por fragmentos. El resultado es un componente extremadamente ligero, robusto y altamente predecible para el LLM, que elimina la incertidumbre sobre qué hacer ante volúmenes de datos que exceden la ventana de contexto, ya que siempre se le garantiza que esta herramienta le devolverá el siguiente bloque disponible con el mismo formato estándar.

### 3.2. La herramienta ReadPaginatedResourceTool

La herramienta `ReadPaginatedResourceTool` se establece como la interfaz única, universal y transparente de consumo de información en el sistema. Su propósito fundamental es centralizar la lógica de lectura paginada, actuando como un intermediario necesario entre el Modelo de Lenguaje y cualquier recurso textual, ya sea que este resida en el sistema de archivos del proyecto (workspace) o dentro de los directorios internos de trabajo (`var/`). Al unificar el acceso bajo esta herramienta, el agente ya no necesita distinguir semánticamente entre leer un archivo fuente, una salida de consola capturada o un reporte web procesado; para el LLM, todas estas entidades se consolidan bajo un único contrato operativo: la lectura de un `resource_id`. Esta abstracción es vital para la estabilidad del sistema, puesto que el identificador entregado al modelo se comporta como un "handle" opaco —un descriptor abstracto—, evitando que el LLM asuma que el puntero es una ruta manipulable y garantizando así una encapsulación total de los detalles de implementación del almacenamiento.

La robustez de esta herramienta reside en su estricta delegación de seguridad y su capacidad de resolución dinámica. `ReadPaginatedResourceTool` no contiene lógica de lectura directa, sino que delega el procesamiento pesado a los métodos protegidos de su superclase, `AbstractPaginatedAgentTool`. Sin embargo, es responsabilidad de esta herramienta realizar la resolución inicial del identificador: al recibir una solicitud, debe determinar —en función de la política de acceso y la naturaleza del `resource_id`— si la petición es legítima dentro del sandbox del proyecto o si corresponde a un buffer temporal interno. Este diseño permite que la capa de seguridad del agente (el `AgentAccessControl`) actúe como un árbitro centralizado y coherente, blindando al sistema contra posibles manipulaciones indebidas. La herramienta, por tanto, no solo sirve como portal de lectura, sino como el guardián que traduce las abstracciones del LLM en accesos validados al sistema de archivos.

Finalmente, su operatividad se fundamenta en la consistencia del protocolo de salida. Al aplicar el motor de paginación de la superclase, `ReadPaginatedResourceTool` asegura que cada fragmento de información sea devuelto con una estructura predecible: un estado de ejecución, metadatos de posición dentro del recurso total y, crucialmente, el "Hint" de continuación. Si el recurso no se ha consumido por completo, la herramienta inyectará automáticamente en el contexto del agente la llamada necesaria para solicitar el siguiente bloque, utilizando exactamente el mismo `resource_id` y el nuevo `offset` calculado. Esta recursividad asistida elimina la carga de gestión de estado por parte del modelo, convirtiendo cualquier lectura masiva en un proceso iterativo de "petición-respuesta" tan natural como la propia conversación, donde el sistema siempre guía al modelo hacia la siguiente porción de información sin ambigüedades ni errores de protocolo.

### 3.3. Refactorización de las herramientas afectadas

La transición hacia esta arquitectura implica una cirugía profunda pero de alto valor sobre el catálogo actual de herramientas, concretamente sobre `FileReadTool`, `WebGetTikaTool`, `FileExtractTextTool`, `FileReadSelectorsTool` y `ShellExecuteTool`. El objetivo de esta refactorización es despojarlas de su responsabilidad actual como "orquestadores de paginación", devolviéndoles su esencia original: ser productores de datos. En el estado actual, estas clases contienen una lógica redundante que mezcla la interacción con el entorno (leer un fichero, ejecutar un comando, extraer texto de un binario) con el complejo mecanismo de fragmentación y formateo de la respuesta. Con la nueva estructura, estas herramientas se liberarán de toda la gestión de `offsets`, límites, conteo de líneas, gestión de cachés de streams y generación manual de `hints` de continuación. A cambio, adoptarán un patrón de implementación mucho más ágil: tras concluir su tarea de producción, simplemente llamarán a un método protegido de su superclase, `AbstractPaginatedAgentTool`, delegando en él la responsabilidad de servir el primer bloque de información y establecer la estructura de navegación.

Este proceso de refactorización se divide en tres fases lógicas para cada herramienta. Primero, se extraerá toda la lógica de paginación y formateo, la cual será trasladada a la superclase abstracta, dejando el método `execute` de la herramienta limpio y enfocado exclusivamente en su dominio (la descarga, la ejecución o el filtrado). Segundo, se estandarizará la persistencia de salida: cada herramienta dejará de tratar sus resultados como un `String` que debe ser entregado completo al LLM y pasará a tratarlos como un recurso que debe ser volcado a `var/tmp` o `var/cache`. Esto garantiza que, desde el instante en que la herramienta concluye su tarea, la información ya posee una identidad persistente —un identificador de recurso— que el LLM puede invocar a través de la herramienta universal de lectura. Tercero, se actualizará el mecanismo de respuesta inicial, asegurando que todas ellas devuelvan un objeto de respuesta consistente que contenga el `resource_id` y el bloque de metadatos requerido.

El resultado final de esta refactorización será un catálogo de herramientas donde la complejidad de la paginación desaparece por completo de la vista del desarrollador y del modelo. Un cambio fundamental en esta fase será la purga de los parámetros offset y limit de las especificaciones de las herramientas de producción. Estas herramientas pasarán a definirse exclusivamente por sus parámetros de intención: una herramienta de búsqueda web solo recibirá una query, y un comando de shell solo recibirá el command. Al eliminar toda referencia a la paginación en las especificaciones de estas herramientas, se garantiza que el LLM no intente gestionar estados de lectura ni gestionar punteros prematuramente; las herramientas ahora anuncian capacidades de acción pura ("ejecutar", "buscar", "extraer"), dejando el mecanismo de lectura como una responsabilidad delegada y exclusiva de ReadPaginatedResourceTool. Al eliminar ShellReadOutputTool —que era un puente artificial hacia la lógica interna de FileReadTool— se reducirá además la superficie de ataque y el ruido en el prompt del sistema, ya que el LLM ahora solo verá un conjunto de herramientas de producción claras y una única herramienta de navegación. Este diseño asegura que cada funcionalidad tenga un propósito único, claro e indivisible, manteniendo un mapa de clases ortogonal donde la paginación se aplica de forma transparente solo cuando el modelo lo solicita explícitamente a través del lector universal.


### 3.4. Eliminación de código obsoleto

La fase final de esta transición arquitectónica consiste en la purga sistemática de los componentes que, bajo el nuevo paradigma de paginación universal, pierden su razón de ser. El ejemplo más claro de esta deuda técnica es `ShellReadOutputTool`, una clase diseñada originalmente como una muleta operativa; su única función era delegar la lectura de la salida de un proceso de shell en `FileReadTool`, actuando como un intermediario manual que añadía una capa de indirección innecesaria. Con la integración del motor de paginación en `AbstractPaginatedAgentTool`, esta herramienta se vuelve redundante: el sistema ahora gestiona el *resource_id* de cualquier salida de comando mediante la herramienta universal `ReadPaginatedResourceTool`. Su eliminación no solo reduce el recuento total de líneas de código (LOC), sino que simplifica drásticamente el espacio de búsqueda del LLM al eliminar una opción que, en el modelo anterior, generaba ambigüedad sobre qué herramienta utilizar para leer el resultado de un comando.

Además de la supresión de clases puente, la refactorización permitirá una poda extensiva dentro de los métodos `execute` de las herramientas productoras. Numerosas líneas de código actualmente dedicadas al parseo manual de argumentos de paginación (como la conversión de tipos numéricos para `offset` y `limit` en cada método), la gestión rudimentaria de errores de paginación dentro de cada herramienta, y la construcción manual de strings de respuesta, serán retiradas permanentemente. Al centralizar estas responsabilidades en la superclase `AbstractPaginatedAgentTool`, se elimina la lógica duplicada que plagaba a los servicios de archivos y de sistema. Esto no solo mejora la mantenibilidad a largo plazo, al permitir que cualquier corrección en el formato del mensaje de sistema o en el Hint de paginación se propague instantáneamente a todo el ecosistema de herramientas, sino que también elimina la posibilidad de que existan discrepancias en el comportamiento del agente frente a diferentes fuentes de datos.

En última instancia, esta limpieza profunda cumple con el objetivo de convertir el catálogo de herramientas del agente en un conjunto de capacidades ortogonales y mínimas. Cada herramienta quedará reducida a su lógica de negocio fundamental —la interacción con el entorno—, dejando la gestión de la ventana de contexto al componente responsable de ello. Esta eliminación no solo es un ejercicio de limpieza estética, sino una mejora funcional directa: al reducir el número de clases y simplificar las responsabilidades de cada una, el sistema se vuelve más fácil de auditar, menos propenso a errores de estado compartido y significativamente más predecible para el modelo de razonamiento, que ya no tendrá que navegar por un catálogo de herramientas "puente" y "auxiliares" que antes dificultaban su toma de decisiones.

# Anexo: Protocolo de Respuesta y Autodocumentación de Herramientas Paginadas

Este anexo define la lógica de paginación centralizada en la clase `AbstractPaginatedAgentTool`. El método `servePaginatedResource` es el único responsable de determinar, basándose en el estado de la lectura, cuál de los cuatro patrones de respuesta debe inyectar. La lógica de selección del patrón es automática y transparente para las clases hijas, eliminando cualquier ambigüedad en la generación de salidas.

## 1. Mecanismo de Selección de Patrones (Para Desarrolladores)

El motor `servePaginatedResource` evalúa el estado del recurso y decide qué plantilla aplicar siguiendo estas reglas de decisión:

*   **Patrón de Error:** Se dispara automáticamente si la ruta es inválida, el archivo no existe o se producen errores de I/O durante la lectura del stream.
*   **Patrón de Paginación en Curso:** Se aplica cuando el `offset + limit` es menor que el `totalLines`. El motor inyecta el `HINT` con la llamada exacta a `read_paginated_resource`, facilitando la continuación de la lectura.
*   **Patrón de Final de Paginación:** Se aplica cuando el bloque actual alcanza el límite del archivo. Incluye los metadatos de rango y total, pero **omite** el campo `HINT` para indicar explícitamente al LLM que no hay más datos disponibles.
*   **Patrón de Lectura Básica:** Se aplica cuando el recurso es tan reducido que se entrega en su totalidad en una única operación. Este patrón es el más simple y carece de metadatos de paginación.

Descripcion de la cabecera:

```text
STATUS: [ok|error]
ERROR: [descripcion del error]
EMPTY: [true|false]
LINE_RANGE: [inicio-fin]
TOTAL_LINES: [total]
HINT: [instrucción_para_continuar_paginación]
---
[Contenido del recurso]
```

Donde:
* **STATUS**: Indica si la ejecución fue correcta.
* **ERROR**: opcional, solo si STATUS es error.
* **EMPTY**: Define si el recurso no contiene datos.
* **LINE_RANGE / TOTAL_LINES**: Opcional, solo durante la paginacion. Metadatos de navegación para el seguimiento del LLM.
* **HINT**: Opcional, solo durante la paginacion y si no se ha llegado a la ultima pagina. Instrucción explícita de cómo solicitar el bloque siguiente.
* **---**: Delimitador crítico que separa los metadatos del contenido real del recurso.


## 2. Instrucciones para el LLM (Inyectadas en ToolSpecification)

Este bloque de texto debe ser devuelto por el método `getPaginationSystemInstruction()` y concatenado a la descripción de cada herramienta que herede de `AbstractPaginatedAgentTool`. Indica al modelo cómo interpretar la cabecera que recibirá:

```
**PROTOCOLO DE RESPUESTA Y NAVEGACIÓN:**
 
Esta herramienta utiliza un protocolo de respuesta estricto basado en cuatro formatos posibles. Identifica siempre el formato al inicio de la respuesta para saber cómo proceder:

1. **ERROR:**

   STATUS: error
   ERROR: [descripción]
   ---
 
2. **PAGINACIÓN EN CURSO:**

   STATUS: ok
   EMPTY: false
   LINE_RANGE: [inicio-fin]
   TOTAL_LINES: [total]
   HINT: [llamada_a_read_paginated_resource]
   ---
   [Contenido parcial]
 
3. **FINAL DE RECURSO PAGINADO:**

   STATUS: ok
   EMPTY: false
   LINE_RANGE: [inicio-fin]
   TOTAL_LINES: [total]
   ---
   [Contenido final]
 
4. **LECTURA BÁSICA (Sin paginación):**

   STATUS: ok
   EMPTY: [true|false]
   ---
   [Contenido completo]

**Reglas críticas:**
 * El delimitador `---` separa siempre los metadatos del contenido. Nunca interpretes metadatos como datos del archivo.
 * Si recibes un `HINT`, es una instrucción ejecutable para obtener el siguiente bloque. No intentes paginar por tu cuenta.
 * Si el patrón no incluye `HINT`, el recurso ha finalizado. No intentes realizar más llamadas de lectura.

```

Esta estructura asegura que el desarrollador entienda el motor centralizado, mientras que el LLM recibe un "manual de instrucciones" con cuatro reglas fijas que eliminan cualquier necesidad de razonar sobre la estructura de la respuesta.

# Anexo: Especificación de la Clase Base `AbstractPaginatedAgentTool`

Este anexo detalla los métodos públicos y protegidos que deben utilizarse para la producción y consumo de recursos paginados. La clase base `AbstractPaginatedAgentTool` garantiza que todas las herramientas mantengan la misma semántica de respuesta y utilicen los mismos estándares de seguridad al interactuar con el sistema de archivos.

## 1. Métodos de Producción (Para herramientas hijas)

Estos métodos son utilizados por las herramientas productoras para preparar un recurso y entregar la primera página al LLM.

*   **`protected String getIdFromPath(Path path)`**:
    Este método normaliza una ruta absoluta (ya sea del *workspace* o de `var/`) y devuelve un `resource_id` opaco que será utilizado como identificador universal. Esta es la única vía autorizada para transformar una ubicación física en una referencia gestionable por el sistema.

*   **`protected String servePaginatedResource(String resourceId)`**:
    Se invoca tras haber volcado el contenido a un archivo. Este método se encarga de servir la **página inicial** (offset 0, límite por defecto) y genera automáticamente la cabecera completa, incluyendo el `HINT` que instruye al modelo a utilizar `read_paginated_resource` si desea continuar la lectura.

## 2. Métodos de Consumo (Exclusivos para `ReadPaginatedResourceTool`)

Estos métodos están diseñados para el motor de lectura universal, encargado de la navegación por bloques.

*   **`protected String servePaginatedResource(String resourceId, int offset, int limit)`**:
    Versión avanzada del método de servicio. Es el encargado de procesar la petición de navegación, validando que el `resource_id` sea accesible, leyendo únicamente el segmento de líneas solicitado mediante `Stream` perezosos, y manteniendo la coherencia de la cabecera (incluyendo `LINE_RANGE` y `TOTAL_LINES`).

## 3. Lógica Interna de Motor (Encapsulada)

Estos métodos internos aseguran que todas las respuestas paginadas —independientemente de su origen— hablen el mismo lenguaje.

*   **`private long getLineCount(Path fileToRead)`**:
    Implementa la lógica de conteo de líneas de manera eficiente. Debe utilizar un sistema de caché (basado en la fecha de modificación del archivo) para evitar el re-procesamiento de archivos estáticos.
*   **`private String formatResponse(String status, boolean empty, String error, int offset, int limit, long total, String hint, String content)`**:
    Método de ensamblaje final. Centraliza la creación del bloque de texto siguiendo los cuatro patrones definidos en el protocolo, garantizando que el orden de los campos y el delimitador `---` sean siempre consistentes en todo el sistema.
*   **`protected Path getPathFromId(String id)`**: Método de resolución que traduce un identificador opaco en una ruta física válida. Este método centraliza las políticas de acceso, validación de sandbox (distinguiendo entre el workspace y `var/`), y la posible lógica de mapeo entre identificadores abstractos y rutas del sistema de archivos. Es el encargado de garantizar que el agente nunca acceda a un recurso fuera de los límites permitidos.
    
# Anexo: Analisis de la propuesta
    
### 1. Análisis de Viabilidad y Fortalezas
La arquitectura es **altamente viable** porque ya tienes la infraestructura base:
*   **Gestión de Rutas:** `AgentPaths` ya distingue entre `tmp`, `cache` y `data`.
*   **Control de Acceso:** `AgentAccessControlImpl` ya tiene la lógica de "whitelist/blacklist", lo que facilitará asegurar que el lector universal no se convierta en una herramienta de *Jailbreak*.
*   **Precedente:** `ShellExecuteTool` ya funciona casi exactamente así (crea un `.out` y delega la lectura). Estás estandarizando un éxito previo.

### 2. "Agujeros" y Riesgos Identificados

#### A. El Riesgo de la "Ruta de Retorno" (Seguridad)
El documento menciona que `resource_id` es un identificador opaco. Sin embargo, si el `resource_id` termina siendo una ruta relativa (ej: `var/tmp/output_123.txt`), un LLM astuto podría intentar inyectar rutas como `../../var/config/settings.json`.
*   **Solución:** En la nueva `AbstractPaginatedAgentTool`, el método `getPathFromId(String id)` debe ser extremadamente paranoico. No debe limitarse a concatenar; debe verificar que el archivo resultante reside **estrictamente** dentro de `var/tmp` o `var/cache`.
*   **Recomendación:** Usa un prefijo virtual en el `resource_id` (ej: `tmp://` o `cache://`) para que la herramienta sepa exactamente en qué subdirectorio buscar y no acepte rutas absolutas del sistema.

#### B. La Carrera de Limpieza (Higiene vs. Persistencia)
Si `ShellExecuteTool` escribe en `var/tmp` y el agente tarda mucho en procesar o se queda esperando una confirmación del usuario, una tarea de limpieza automática (Garbage Collector de archivos) podría borrar el archivo mientras el LLM aún tiene el "puntero" en su contexto.
*   **Riesgo:** El LLM invoca `read_paginated_resource` sobre un ID que ya no existe, causando una alucinación o un error de sistema.
*   **Solución:** La política de borrado en `var/tmp` no debería ser por "reinicio", sino por **LRU (Least Recently Used)** con una cuota de archivos (como ya intentas en `ShellExecuteTool.OutputLRUMap`).

#### C. Consistencia del "Encoding"
Tika es excelente detectando encodings, pero al mover archivos entre `Productor -> Disco -> Consumidor`, podrías encontrarte con problemas de UTF-8 vs Latin-1, especialmente en salidas de Shell.
*   **Recomendación:** Fuerza a que todos los Productores escriban siempre en **StandardCharsets.UTF_8**. El Lector universal solo debería hablar UTF-8.

### 3. Impacto en el Código Fuente (Ficheros a modificar)

1.  **`io.github.jjdelcerro.noema.lib.impl.AbstractAgentTool`**:
    *   Debe mantenerse como la base ligera para herramientas simples (ej: `TimeTool`).
2.  **`io.github.jjdelcerro.noema.lib.impl.AbstractPaginatedAgentTool` (NUEVO)**:
    *   Aquí debes mover la lógica de `execute` que actualmente está en `FileReadTool`.
    *   Debe heredar de `AbstractAgentTool`.
3.  **`io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.file.FileReadTool`**:
    *   Refactorizar para que solo sea una implementación de la nueva clase base que apunta al workspace del usuario.
4.  **`io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.file.ReadPaginatedResourceTool` (NUEVO)**:
    *   Esta será la herramienta que el LLM use para los `HINT`.
5.  **`ShellExecuteTool` y `WebGetTikaTool`**:
    *   Eliminarán sus parámetros `offset` y `limit`.
    *   Su `execute` terminará con un `return servePaginatedResource(generatedFile)`.

### 4. Observación sobre la Experiencia de Usuario (UX del Agente)
En el **Anexo I (Instrucciones para el LLM)**, asegúrate de enfatizar que el `resource_id` es **temporal**.
*   **Punto ciego:** Si el agente guarda un puntero en su memoria a largo plazo (en un `CheckPoint`), ese puntero (`var/tmp/web_result.txt`) dejará de ser válido en la siguiente sesión de usuario.

En relacion a esto si ha desaparecido el fichero cuando se pide una pagina deberia darse un codigo de error adecuado que instruya al LLM que la cache asociada a ese resource_id ha sido borrada y que si la necesita debera volver a pedir el recurso original que dio origen a ese resource_id.


    
    
    