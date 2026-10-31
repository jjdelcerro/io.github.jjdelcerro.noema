# Informe de Análisis Estático del Proyecto Noema

## 1. Visión general

Noema es un agente autónomo desarrollado como proyecto personal, diseñado como un compañero para mantener interacciones y reflexiones de larga duración en ámbitos de investigación. A diferencia de las herramientas orientadas exclusivamente a completar ciclos de desarrollo de software, este sistema está optimizado para operar en una única sesión temporal continua que se extiende a lo largo del tiempo. 

El proyecto adopta un enfoque marcadamente autosuficiente en cuanto a infraestructura. Evita la dependencia de servicios externos pesados (como bases de datos vectoriales dedicadas) y basa su funcionamiento en bibliotecas embebidas y arquitecturas locales. La gestión de estado y persistencia a largo plazo se soluciona mediante bases de datos embebidas, indexación semántica en proceso y componentes como [JavaRCS](https://github.com/jjdelcerro/io.github.jjdelcerro.javarcs) integrados directamente en el código Java.

## 2. Stack tecnológico

El proyecto está construido sobre el ecosistema Java, utilizando las siguientes tecnologías principales:

*   **Lenguaje Base:** Java 25.
*   **Gestión de Dependencias y Construcción:** Maven.
*   **IA y Modelos de Lenguaje:** LangChain4j como marco de abstracción para la interacción con LLMs (OpenAI, proveedores compatibles, MCP).
*   **Modelos de Lenguaje Pequeños (SLM) y Embeddings Locales:** ONNX Runtime GenAI para ejecutar modelos cuantizados locales (ej. Qwen3-0.6B) y la biblioteca de Jlama/Llama.cpp.
*   **Base de Datos y Persistencia:** H2 Database en modo embebido.
*   **Procesamiento de Texto y Datos:** Gson para manipulación JSON, Apache Tika para extracción de texto de documentos ofimáticos/PDF.
*   **Scripting:** Apache Groovy en modo embebido para la ejecución de scripts seguros en el sandbox.
*   **Interfaces de Usuario (Capa de Presentación):**
    *   **Consola Web:** Javalin para servir endpoints REST/SSE y contenido estático HTML/JS/CSS (SPA sin frameworks externos, uso de Ace Editor).
    *   **Consola Swing:** FlatLaf y MigLayout junto con RSyntaxTextArea.
    *   **Consola TUI:** Lanterna.
    *   **Consola CLI:** JLine3.

## 3. Estructura de paquetes, interfaces e implementación

El proyecto aplica una clara segregación entre la definición de los contratos (API) y la lógica concreta (Implementación). 

*   **`io.github.jjdelcerro.noema.lib.api`**: Contiene exclusivamente interfaces formales. Define los contratos de los servicios, el modelo de memoria, los controles de acceso y las herramientas.
*   **`io.github.jjdelcerro.noema.lib.impl`**: Proporciona las clases concretas que implementan la API. Aquí se organizan subpaquetes lógicos como `memory`, `model`, `services`, `settings`, `tools` y `utils`.
*   **`io.github.jjdelcerro.noema.lib.spi`**: Contiene clases base abstractas (AbstractAgentService, AbstractAgentTool) pensadas para facilitar la creación de nuevas extensiones respetando la inyección de dependencias interna.
*   **`io.github.jjdelcerro.noema.main`**: Alberga las clases de inicialización y puntos de entrada de la aplicación (`Main`, `BootUtils`) y los disparadores de los diferentes modos de interfaz.
*   **`io.github.jjdelcerro.noema.ui`**: Maneja las diferentes implementaciones de las vistas del agente (Swing, Web, Lanterna, Console), abstrayendo la comunicación bidireccional entre el núcleo y la capa de presentación.

## 4. Arquitectura y diseño

### 1. El Kernel (o Core)

El núcleo del sistema define la topología base sobre la que se orquestan el resto de componentes.

*   **`Agent` y `AgentManager`**: El componente `Agent` representa la instancia operativa principal que concentra el acceso a la memoria, la configuración y el registro de servicios. El `AgentManager` (localizado a través del patrón `AgentLocator`) actúa como registro central de factorías y clases proveedoras.
*   **Ciclo de Vida**: La clase `BootUtils` gestiona el arranque del agente. Inicializa el sistema de ficheros, verifica la validez de la configuración a través de los servicios requeridos, instancia las bases de datos embebidas, construye la instancia central de `Agent` y emite la señal de inicio a todos los servicios registrados.
*   **Infraestructura de Datos**: Se define una interfaz `SQLProvider` que abstrae consultas nativas, inyectando un `ConnectionSupplier` que provee acceso unificado a las bases de datos locales H2, separando el almacenamiento episódico de las tareas de los servicios.
*   **Topología de Archivos**: Orquestada mediante [AgentPaths](https://jjdelcerro.github.io/noema/docs/01-fundamentos-y-ciclo-de-vida/02-agent-paths.html), define una carpeta raíz `.noema-agent` que contiene de forma estricta los entornos de configuración (`var/config`), almacenamiento de la base de datos (`var/lib`), caché (`var/cache`), temporales y de aislamiento de entorno de usuario (`home`).

### 2. Capacidades Horizontales (Cross-cutting Concerns)

Son los módulos que imponen normas transversales a todos los servicios y operaciones.

*   **Seguridad y Control de Acceso**: Gobernado por [AgentAccessControl](https://jjdelcerro.github.io/noema/docs/01-fundamentos-y-ciclo-de-vida/04-seguridad-y-control-de-acceso.html). Establece los modos de acceso (Lectura/Escritura), validación de ejecución de shell, confirmaciones humanas requeridas antes de realizar mutaciones de estado y verificación de rutas fuera del aislamiento del sandbox (lista blanca/negra de directorios).
*   **Sistema de Configuración Jerárquica**: Orquestado por [AgentSettings](https://jjdelcerro.github.io/noema/docs/01-fundamentos-y-ciclo-de-vida/03-agent-settings.html). Lee y escribe configuraciones desde ficheros JSON con soporte para evaluación dinámica de propiedades mediante expresiones JEXL/MVEL.

### 3. Servicios Cognitivos

Responsables de la gestión, flujo y preservación de la información que consume el modelo de lenguaje, conectándose con los repositorios persistentes.

*   **[ReasoningService](https://jjdelcerro.github.io/noema/docs/03-catalogo-de-servicios/01-reasoning.html)**: Es el orquestador del pensamiento. Gestiona el bucle perpetuo que recibe eventos asíncronos, compone el contexto enviándolo al modelo de inferencia, intercepta las peticiones de herramientas, delega su ejecución y devuelve los resultados a la memoria activa. Administra operaciones simultáneas separadas en "subcanales".
*   **[MemoryConsolidationService](https://jjdelcerro.github.io/noema/docs/03-catalogo-de-servicios/02-memory-consolidation.html)**: Opera en segundo plano como un servicio de compresión cognitiva. Su función es leer los segmentos inactivos del historial reciente y sintetizarlos en un relato narrativo, manteniendo referencias precisas a los datos originales para no degradar la trazabilidad histórica.

### 4. Servicios de Periferia

Conjunto de integradores funcionales que amplían el ecosistema del agente.

*   **[SensorsService](https://jjdelcerro.github.io/noema/docs/03-catalogo-de-servicios/03-sensors.html)**: Sistema de cola y multiplexación de eventos asíncronos. Escucha interacciones, estado de servicios o relojes, y las canaliza hacia el motor de razonamiento de forma controlada.
*   **[SchedulerService](https://jjdelcerro.github.io/noema/docs/03-catalogo-de-servicios/04-scheduler.html)**: Proporciona capacidades de programación temporal. Permite planificar alarmas o alertas diferidas que luego se reinyectan como señales sensoriales.
*   **[Scripting](https://jjdelcerro.github.io/noema/docs/04-subsistemas-de-ejecucion-y-capacidades/04-scripting.html)**: Instancia entornos seguros de Groovy dentro del proceso. Permite al agente ejecutar rutinas de transformación de datos o búsquedas masivas sin sobrecargar la comunicación conversacional.
*   **[EmbeddingsService](https://jjdelcerro.github.io/noema/docs/03-catalogo-de-servicios/05-embeddings.html)**: Genera y compara representaciones vectoriales locales mediante ONNX, aplicando algoritmos de agrupación como fragmentación (chunking) y comparativas MaxP para la recuperación de información.
*   **Servicio de Comunicaciones (Email/Telegram)**: Efectores y sensores integrados para el mundo exterior. El correo utiliza IMAP IDLE para notificación pasiva sin descarga de cuerpo. Telegram permite interacción instantánea.
*   **McpService**: Conector dinámico de herramientas mediante Model Context Protocol. Establece transportes (HTTP/Stdio) para importar contratos de ejecución estandarizados, ampliando las capacidades en caliente.
*   **SkillsService**: Motor de capacidades procedimentales que carga y expone flujos de trabajo documentados y scripts accesorios.
*   **SubagentsService**: Orquesta el lanzamiento asíncrono de trabajadores secundarios basados en recetas XML, cada uno con su propio bucle de razonamiento y memoria aislada, liberando al agente principal.

## 5. Mecanismos a detallar

### Gestión de memoria
El modelo no asume contextos ilimitados. La retención y recuperación de información se estructura estrictamente en capas para maximizar la economía cognitiva:

*   **[EpisodicMemory](https://jjdelcerro.github.io/noema/docs/02-el-sistema-de-memoria/020-memoria-episodica.html)**: Base de datos inmutable (H2) que guarda cada turno, interacción y resultado de herramienta como registros permanentes. Actúa como el disco duro histórico general.
*   **[RecentMemory](https://jjdelcerro.github.io/noema/docs/02-el-sistema-de-memoria/040-memoria-reciente.html)**: Representa el búfer activo de la conversación (memoria de trabajo). Posee un umbral máximo a partir del cual dispara la consolidación.
*   **[ConsolidateMemory](https://jjdelcerro.github.io/noema/docs/02-el-sistema-de-memoria/030-memoria-consolidada.html)**: Transformación narrativa generada periódicamente. Comprime el histórico en resúmenes legibles que mantienen anclajes numéricos (citas) que enlazan con la memoria episódica.
*   **[ProjectedMemory](https://jjdelcerro.github.io/noema/docs/02-el-sistema-de-memoria/050-memoria-proyectada.html)**: Es una vista dinámica, construida justo antes de solicitar la respuesta al LLM. Este mecanismo es clave por sus *operaciones registrables* (ej. `TrimmingOperation`, `PendingAnnotationOperation`, `PinnedTurnsOperation`). Las operaciones aplican filtros: podan los volcados masivos de datos para dejar solo las conclusiones (recortando contexto innecesario), mantienen "fijados" ciertos mensajes (como el mapa de arquitectura) y advierten de eventos en la periferia de la atención, garantizando que el prompt sea denso en información pero contenido en volumen.

### Gestión de la identidad del agente
La identidad del sistema se define de manera modular a través de archivos Markdown en la configuración interna. Los ficheros base imponen un marco de personalidad, el tono y las pautas técnicas imperativas. Las referencias situacionales o preferencias biográficas se mantienen referenciadas y se cargan bajo demanda cuando el agente nota su relevancia conceptual.

### Gestión de habilidades ([skills](https://jjdelcerro.github.io/noema/docs/04-subsistemas-de-ejecucion-y-capacidades/03-skills.html))
Los protocolos de actuación detallados (ej. manipulación y revisión de código fuente) se abstraen en "skills". Estos consisten en manifiestos y scripts que el agente puede listar, activar y desactivar a voluntad en la memoria proyectada temporal. Una vez un skill es activado, sus directrices se "fijan" forzosamente en el contexto del agente hasta que se considere concluida la tarea.

### Gestión de eventos
Los sucesos no provienen exclusivamente de peticiones de usuario. El sistema de sensores recoge alertas (relojes, subagentes terminados, recepciones de correos) y los trata según su naturaleza:
*   *Discretos*: Mensajes únicos y autónomos.
*   *Mergeables*: Tramos concatenables si pertenecen al mismo flujo (evitando múltiples turnos pequeños).
*   *Agregables*: Eventos de alto volumen que solo incrementan un contador de ocurrencias.
*   *Estado*: Señales cuyo valor obsoleto es sobrescrito por el nuevo antes de ser procesado.
El motor asimila esto de manera encubierta presentándole al modelo de inferencia la ilusión de haber utilizado él mismo una herramienta de lectura de cola de sucesos.

### Gestión de la seguridad
*   **Restricción del sistema de ficheros**: Se calculan árboles de accesibilidad y "agujeros" sobre ellos. Ninguna función cruza rutas absolutas sin resolverse previamente mediante la lista blanca de directorios habilitados y las prohibiciones expresas de lectura o escritura (como el directorio `.git` o las configuraciones propias del núcleo).
*   **Confirmación operativa**: Las herramientas que mutan el estado (escrituras en disco, llamadas al intérprete de comandos o ejecución en red) poseen una directiva interna de modo (`MODE_WRITE`, `MODE_EXECUTION`). Si la configuración global exige supervisión, la interfaz ([AgentConsole](https://jjdelcerro.github.io/noema/docs/05-capa-de-presentacion-e-interfaces/00-contrato-agentconsole-y-comunicacion.html)) bloquea el hilo solicitando validación explícita al usuario humano antes de invocar la acción real. Se suma a esto una abstracción para utilizar jaulas de sistema operativo (*firejail*).

### Flujos en el Reasoning Service
El ciclo es inherentemente reactivo: 
1. Un evento es detectado en la cola del sensor.
2. Se extrae y se empaqueta junto con el historial reciente.
3. Se ensambla y filtra el contexto a través de la Memoria Proyectada.
4. El LLM responde con razonamientos y peticiones de herramientas.
5. Se ejecutan las herramientas localmente, respetando bloqueos de seguridad.
6. Se devuelven los resultados como un nuevo estímulo inyectado.
7. Se repite el ciclo hasta que el modelo decide no requerir más herramientas y genera su texto final, momento en el que se emite hacia la interfaz gráfica.

### [Subagentes](https://jjdelcerro.github.io/noema/docs/04-subsistemas-de-ejecucion-y-capacidades/02-subagentes.html)
Los subagentes son instancias efímeras nacidas a partir de archivos de receta (XML) que preconfiguran su comportamiento con instrucciones precisas (ej. indexar un documento, auditar arquitectura). Estos operan en hilos en segundo plano utilizando su propio razonador aislado y base de datos de memoria local. Al concluir, depositan el resultado del procesamiento como una alerta sensorial en el sistema principal del agente contenedor, logrando descentralización de tareas extensas.

## 6. Herramientas del agente

El abanico de instrumentos implementados, estructurado por bloques funcionales:

*   **Herramientas de Memoria y Percepción:**
    *   `fetch_citation`: Recupera un fragmento exacto de la memoria episódica a partir de un identificador referenciado en el resumen narrativo.
    *   `search_full_history`: Realiza búsquedas mediante similitud semántica (embeddings) en todo el histórico a largo plazo.
    *   `annotate_observation`: Almacena información crítica extraída por el agente de lecturas efímeras, salvaguardándola antes de que el contexto la pode.
    *   `poll_event`: Herramienta inyectada sintéticamente que simula la recepción pasiva de notificaciones y sucesos externos.
    *   `consult_environ`: Carga bajo demanda segmentos densos de conocimiento sobre la identidad y el entorno del usuario a partir de referencias preliminares.

*   **Herramientas de Sistema y Planificación:**
    *   `get_current_time`: Obtiene el tiempo y zona horaria actual del servidor.
    *   `schedule_alarm`: Declara y persiste un temporizador que inyectará una señal en el futuro.
    *   `sensor_stop` / `sensor_start`: Controla la entrada de ruido periférico desactivando o reanudando canales de eventos específicos.
    *   `sensor_status`: Verifica la salud y la frecuencia estadística de las vías de entrada.

*   **Herramientas de Exploración y Edición de Archivos:**
    *   `file_find`: Recorre el directorio validando restricciones de seguridad y listando coincidencias por patrón glob.
    *   `file_grep` / `file_fuzzygrep`: Búsqueda léxica (exacta/regex) o por significado (vectores) con control estricto de paginación y límites de líneas.
    *   `file_read` / `read_paginated_resource`: Extrae volúmenes de texto de manera controlada y por fragmentos para evitar saturación del buffer.
    *   `file_write` / `file_mkdir`: Creación estructurada de contenido en sistema y nuevas carpetas.
    *   `file_patch` / `file_search_and_replace`: Mutaciones precisas mediante Diff Unificado o sustitución difusa con tolerancia a identación, generando un resguardo implícito en [JavaRCS](https://github.com/jjdelcerro/io.github.jjdelcerro.javarcs).
    *   `file_extract_text`: Extracción de contenido de archivos binarios/ofimáticos utilizando Tika.
    *   `file_read_selectors`: Permite la recopilación simultánea de múltiples recursos basándose en rutas específicas o globs combinados.
    *   `file_view_image`: Carga representaciones binarias visuales al prompt multimodal.
    *   `file_history` / `file_recovery`: Acceso al rastreo de mutaciones y reversión del sistema de control de versiones interno.

*   **Herramientas de Ejecución Lógica e Integración:**
    *   `shell_execute`: Permite lanzar instrucciones al sistema operativo limitadas bajo la supervisión explícita y/o sandboxing.
    *   `execute_script`: Corre lógica escrita en lenguaje Groovy dentro de un entorno validado.

*   **Herramientas de Internet:**
    *   `web_search`: Consulta integradores de búsqueda externos.
    *   `web_get_content`: Recupera contenido remoto procesado y sanitizado para lectura paginada.
    *   `get_weather` / `get_current_location`: Sondas informativas geográficas.

*   **Herramientas de Comunicaciones Externas:**
    *   `email_list_inbox` / `email_read` / `email_send`: Interface de mensajería asíncrona mediante correo, interactuando con servidores SMTP e IMAP.
    *   `telegram_send`: Envío de notificaciones inmediatas a canales configurados.

*   **Herramientas de Subagentes y Habilidades:**
    *   `list_subagents` / `launch_subagent`: Gestión del despliegue en segundo plano de trabajadores XML.
    *   `list_skills` / `activate_skill` / `deactivate_skill`: Carga y fijación en memoria de directrices y procedimientos operativos estructurados.
    *   `read_skill_resource` / `run_skill_script`: Consumo de documentación de apoyo e invocación de binarios empaquetados dentro del entorno del propio skill.

*   **Herramientas de Desarrollo y UI Web:**
    *   `load_project_map` / `unload_project_map`: Vinculación permanente de la cartografía de arquitectura en el pipeline de memoria de trabajo.
    *   `editor_open_file` / `editor_get_current_selection`: Enlaza la perspectiva de la interfaz gráfica web informando sobre dónde está posicionado el usuario humano.
    *   `ui_get_state`: Reporta una captura de pestañas y documentos activos en el explorador web.
    *   `editor_insert_or_replace` / `editor_add_annotation` / `editor_clear_annotations`: Interactúa con el buffer del IDE online aportando sugerencias directas sin afectar inmediatamente el almacenamiento físico.

*   **Herramientas Dinámicas (MCP):**
    *   El protocolo exporta cualquier herramienta publicada por servidores locales de `Model Context Protocol` conectados al inicio (ej. diagnóstico mediante Servidores de Lenguaje como JDTLS).

## 7. Construcción y despliegue

El proyecto se compila y empaqueta empleando Maven, utilizándose el plugin `maven-shade-plugin` para integrar de forma consolidada todos los servicios (fat-jar), incluyendo la fusión obligatoria de los ficheros en `META-INF/services/` provenientes de dependencias diversas.

El front-end está diseñado bajo el esquema de una Single Page Application (SPA), compuesto por HTML, JS estático sin transpilación ni frameworks engorrosos, servido al vuelo desde el recurso empotrado hacia un cliente ligero en el navegador gracias a Javalin, mientras que un esquema de shell script auxilia el despliegue descargando los modelos de lenguaje (SLMs) y de embeddings desde registros externos (ej. HuggingFace) directamente a los directorios preestablecidos del agente en tiempo de setup inicial. 

## 8. Conclusión

El diseño del proyecto revela una orientación exhaustiva hacia la mitigación de los cuellos de botella clásicos de los LLM (agotamiento del contexto, amnesia y hallazgo ruidoso). El núcleo del sistema recae en administrar *qué sabe el agente y cuándo lo sabe*. La implementación de métricas instrumentadas a través de la clase `Telemetry` (que contabiliza podas, ejecuciones precisas, alucinaciones de citas y consumos) resalta un esfuerzo directo por medir la eficiencia cognitiva del sistema en lugar del simple uso de volumen de tokens. 

Por otra parte, pruebas estáticas del diseño como la observada en las dinámicas *Needle in a Haystack* reflejan que esta filosofía de compresión y "recortes por operaciones" (vía herramientas de anotación y memoria proyectada) logra preservar piezas clave de conocimiento en diálogos abismales (ingestas documentales pesadas) sin el costo de conservar todo el historial crudo en RAM. En definitiva, es una arquitectura concebida para la resistencia a largo plazo mediante una gestión económica estricta de la atención artificial.

---

*Documento generado por Gemini (IA) a partir del análisis estático del código fuente, versión 0.8.0, 31 de octubre de 2026.*
