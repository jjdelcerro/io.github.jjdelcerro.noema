
# Diseño de Arquitectura: Consciencia de Identidad y Entorno (Core vs. Environ)

# 1. Visión General: La Memoria Virtual del Agente

El mayor desafío técnico al que se enfrenta Noema es la paradoja de la **atención frente al volumen**. A medida que profundizamos en la relación entre el usuario y el agente, acumulamos una cantidad masiva de información semántica (biografía, valores, gustos culturales, marcos técnicos) que supera fácilmente los 25.000 tokens. En un escenario tradicional, cargar este bloque completo de forma estática en la ventana de contexto (por ejemplo, el contenido íntegro de `contexto_y_protocolo_de_interaccion.md`) supondría canibalizar entre el 15% y el 25% de la capacidad de razonamiento del LLM antes siquiera de recibir el primer mensaje del usuario. Esta ineficiencia se traduce en costes elevados, lentitud de respuesta y, lo que es peor, una pérdida de precisión en la tarea actual debido a la saturación de ruido informativo.

Para resolver este conflicto, esta arquitectura propone el concepto de **Memoria Virtual Cognitiva**. Inspirada en la gestión de memoria de los sistemas operativos modernos, Noema deja de tratar su identidad y su entorno como un bloque de texto plano para tratarlos como un **Grafo de Módulos Cargables**. El sistema no intenta que el agente «sea» Joaquín cargando toda su vida en RAM; en su lugar, busca que el agente **«conozca»** a Joaquín mediante una **Tabla de Páginas Semántica**.

### 1.1. La división funcional: El "Cómo" vs. El "Quién"
La arquitectura fragmenta el conocimiento permanente basándose en su frecuencia de uso y su peso en el razonamiento, utilizando como base la estructura ya definida en los documentos de contexto:

*   **La Constitución (Core):** Define las reglas del juego. Son los archivos que rigen la *forma* en que el agente debe procesar cada token. Módulos como `00-stack_tecnico.md` (Java 1.8/21, Swing, Linux), `60-normas_codigo.md` (llaves en la misma línea, Javadoc en inglés) y `07-metodologiadetrabajo.md` (los Cuatro Actos) operan como el sistema operativo del agente. Deben estar siempre presentes en el prompt porque su ausencia provocaría que el agente generara código o soluciones incompatibles con el entorno del usuario.
*   **La Consciencia de Entorno (Environ):** Define la atmósfera cultural y vital. Son archivos que contienen el *significado* profundo del interlocutor pero que no siempre son necesarios para la tarea inmediata. Documentos como `01-personal.md` (Asimov, Susan Calvin, la robopsicología), `02-trayectoria.md` (MSX, Dirac, gvSIG) o `03-investigacion.md` (GrammarNet, memoria híbrida) forman una biblioteca de referencia. 

### 1.2. El mecanismo de Anclaje Semántico (Ref-Pattern)
La innovación clave de esta visión general es la sustitución del dato bruto por la **referencia**. Para cada módulo del entorno, el sistema utiliza un archivo gemelo con extensión `.ref.md` (ejemplo: `01-personal.ref.md`). Este archivo contiene únicamente las "direcciones de memoria" o palabras clave críticas que permiten al agente saber que existe información detallada disponible. 

En lugar de cargar los 25k tokens, el agente recibe un **Índice de Consciencia**. Al ver en su prompt la referencia a "Susan Calvin" o "Kant", el agente experimenta una **Propiocepción sobre su conocimiento**: sabe que esos conceptos forman parte de su entorno y sabe exactamente qué cable tirar para recuperar el detalle.

### 1.3. Objetivos de la arquitectura
1.  **Escalabilidad Infinita:** La memoria del agente puede crecer hasta los millones de tokens sin que el prompt del sistema aumente de tamaño, ya que solo crecen las referencias.
2.  **Cognición Bajo Demanda:** Obliga al agente a realizar un acto de **Metacognición**. Antes de responder a una "rayada personal", el LLM debe decidir proactivamente si necesita cargar el marco biográfico, convirtiéndose en un investigador activo de su propia identidad.
3.  **Higiene del Relato:** Evita que el historial episódico y los Checkpoints se contaminen con copias masivas de la biografía del usuario. El sistema recuerda la *intención* (ej: "Joaquín se sintió como Susan Calvin y cargué el módulo scifi") pero no el *volumen* redundante.

Esta Visión General sienta las bases para un agente que ya no sufre de amnesia ni de inundación informativa, sino que habita un equilibrio dinámico: una **Homeostasis Cognitiva** donde el agente gestiona su presupuesto de atención con la misma maestría con la que gestiona sus razonamientos técnicos.

# 2. El ADN Operativo: Carpeta `/core/` y Constitución Modular

Si la Visión General establece la estrategia, la carpeta `/var/identity/core/` es la implementación física de la personalidad técnica y operativa del agente. En esta capa de la arquitectura, Noema no almacena "recuerdos", sino que define su **Constitución**: el conjunto de leyes, protocolos y marcos técnicos que rigen su comportamiento. A diferencia de otros sistemas de memoria, la información contenida en el Core es considerada **Ley de Rango Superior**, lo que implica que su presencia en el prompt del sistema es constante para garantizar la integridad de las respuestas.

### 2.1. Estructura Física y Ordenamiento Determinista
Para evitar que el cerebro del agente reciba instrucciones contradictorias o desordenadas, la carpeta `/core/` utiliza un sistema de **prefijos numéricos** que establece una jerarquía de carga. El orquestador de Noema escanea esta carpeta al inicio y ordena los archivos lexicográficamente, asegurando que las reglas básicas se asienten antes que los refinamientos metodológicos.

Ejemplo de estructura real basada en tu contexto:
*   `01_stack_tecnico.md`: Define la base tecnológica (Java SE 1.8, Swing, Linux). Es lo primero que el agente debe "saber" antes de generar una sola línea de código.
*   `02_metodologia.md`: Establece el marco de trabajo colaborativo (Los Cuatro Actos, "El Arquitexto y la Máquina").
*   `03_normas_codigo.md`: Dicta la sintaxis fina (llaves en la misma línea, Javadoc en inglés).

Este orden garantiza que si el agente recibe una instrucción de "Escribir código Java" en el mensaje del usuario, ya haya pasado por los filtros previos de "Versión 1.8/21" y "Estilo Swing".

### 2.2. El Mapa de Identidad: `identity_core.properties`
Para que el sistema sea amigable y modular, Noema utiliza un puente de traducción entre los archivos físicos y la interfaz de usuario. El fichero `identity_core.properties` actúa como el **Censo de la Constitución**, permitiendo asignar etiquetas legibles a los ficheros técnicos.

Contenido del mapeo:
```properties
Stack_tecnico=01_stack_tecnico
Metodologia=02_metodologia
Normas_de_codigo=03_normas_codigo
```
Esta abstracción permite que, en el futuro, puedas renombrar un archivo físico (ej: de `01_stack_tecnico` a `01_stack_moderno`) sin que el usuario perciba el cambio en la configuración, manteniendo la estabilidad de la interfaz.

### 2.3. Gestión de la Soberanía en el `settings.json`
La arquitectura otorga al usuario el control absoluto sobre el "ADN" del agente en cada sesión mediante el uso de un **`AgentSettingsCheckedList`**. En el `settings.json`, bajo la ruta `reasoning/identity/core`, el sistema persiste qué módulos han sido activados.

*   **Detección de "Módulos Huérfanos":** El orquestador aplica una lógica de seguridad: si un archivo existe en la carpeta `/core/` pero no está referenciado en la lista de los `settings`, se carga por defecto. Esto asegura que nuevas reglas añadidas manualmente al sistema de archivos entren en vigor de inmediato.
*   **Módulos Desmarcados (Unchecked):** Si un módulo está definido en el `.properties` pero el usuario lo desmarca en la UI (ej: desmarcar `Normas_de_codigo` porque en este chat se va a picar Python), el orquestador **omite activamente** ese archivo en la construcción del prompt. Esto permite una "conmutación de personalidad técnica" instantánea sin borrar archivos.

### 2.4. Integración en el Sándwich de Contexto (Capa 2a)
Durante la construcción del `SystemMessage` en el `ReasoningServiceImpl`, el bloque del Core se inyecta bajo el encabezado `[CONSTITUCIÓN]`. El orquestador concatena los contenidos de los archivos `.md` seleccionados, separándolos por saltos de línea y respetando el orden numérico.

**Ventaja Técnica:** Al ser archivos `.md` estáticos cargados desde disco a RAM, el tiempo de latencia es despreciable y no requiere de llamadas a servicios externos de búsqueda. El agente nace en cada turno con su "manual de estilo" integrado, evitando alucinaciones sobre el lenguaje o la arquitectura del proyecto gvSIG.

### 2.5. Resumen de Responsabilidades
El Core no es una base de datos, es el **Registro de la Voluntad Técnica**. Al separar esta información de la biografía (que irá al Environ), Noema consigue ser un agente ligero: solo carga en cada turno las leyes que el usuario ha decidido que deben regir esa conversación específica.


# 3. El Mapa del Mundo: Carpeta `/environ/` y Estrategia de Referencia

Si la carpeta `/core/` define el «yo» del agente, la carpeta `/var/identity/environ/` define el «donde» y el «con quién». En esta capa de la arquitectura, Noema almacena el mapa semántico de su entorno operativo: el perfil profundo del usuario, el contexto geográfico y cultural, y el rastro intelectual de sus investigaciones pasadas. Para gestionar el volumen masivo de esta información (tus 25.000 tokens) sin saturar la atención, implementamos la estrategia de **Ficheros Gemelos**, un mecanismo de "punteros semánticos" que permite al agente saber que la información existe sin necesidad de leerla en cada turno.

### 3.1. La Arquitectura de Ficheros Gemelos (`.md` vs `.ref.md`)
Dentro de la carpeta `/environ/`, cada bloque de conocimiento se fragmenta en dos archivos con funciones radicalmente distintas. Esta división es el núcleo de la eficiencia del sistema:

1.  **El Cuerpo del Conocimiento (`XXX.md`):** Es el archivo denso que contiene la información completa y detallada. Por ejemplo, `01_personal_joaquin.md` albergaría tu biografía desde 1966, tus relaciones familiares y tu trayectoria desde el MSX. Este archivo **nunca se carga automáticamente** en el prompt. Permanece en el disco como una "memoria a largo plazo" inerte.
2.  **El Ancla Semántica (`XXX.ref.md`):** Es un archivo minimalista que actúa como la "ficha de catálogo" de una biblioteca. Contiene un resumen ejecutivo y, lo más importante, una lista de **palabras clave críticas** (anclas) que deben disparar la curiosidad del agente. 

### 3.2. El Índice de Consciencia de Entorno
El orquestador de Noema escanea exclusivamente los archivos `.ref.md` para construir la sección **[CONSCIENCIA DE ENTORNO]** del System Message. 

Ejemplo real de cómo se vería una referencia basada en tu artículo de Susan Calvin:
*   **Archivo:** `05_pasiones_scifi.ref.md`
*   **Contenido inyectado en el prompt:**
    > `pasiones_scifi`: Información detallada sobre el marco cultural de Joaquín. Contiene referencias a Isaac Asimov, Tolkien, la robopsicología de Susan Calvin y la fascinación por los sistemas complejos (Akira, Robot Carnival).

Este pequeño bloque de apenas 50 tokens permite que, cuando tú digas "me siento como Susan Calvin", el LLM realice un escaneo interno de sus anclas semánticas, identifique el término en la referencia `pasiones_scifi` y sepa que debe invocar la herramienta `consult_environ("pasiones_scifi")` para entender el marco de la conversación.

### 3.3. Propiocepción sobre el Conocimiento (Saber que se sabe)
Esta técnica dota al agente de una **Propiocepción Cognitiva**. En lugar de ser un agente que "lo sabe todo de golpe" (inundación) o un agente que "lo ignora todo hasta que se le dice" (amnesia), Noema es un agente que **"sabe que sabe"**. 

*   El agente es consciente de que Valencia es tu ciudad y que tiene detalles sobre su huso horario y cultura en el módulo `location_valencia`.
*   El agente sabe que tus reflexiones sobre Kant y la inteligencia neuro-simbólica están en `03_investigacion_ia`.
*   El agente reconoce que ante una duda sobre gvSIG, debe consultar `04_proyecto_gvsig` para no alucinar con versiones modernas de Java.

### 3.4. Dinamismo y Escalabilidad
A diferencia de la carpeta `/core/`, que es más estática y se rige por los `settings.json`, la carpeta `/environ/` es puramente **descubrible**. 
- Si añades un nuevo archivo `musica_jazz.ref.md` mientras el agente está corriendo, en el siguiente arranque Noema "sentirá" que ha adquirido un nuevo sentido sobre tus gustos musicales.
- No hay límite para el número de módulos; el agente puede tener miles de módulos biográficos, ya que el prompt solo crecerá proporcionalmente al tamaño de las referencias (un par de líneas por módulo), manteniendo la ventana de contexto siempre disponible para el razonamiento activo.

### 3.5. Resumen de Responsabilidades
El `environ` es la **Biblioteca de Contexto bajo demanda**. Al separar el índice (Capa 2b) del dato bruto (disco), Noema resuelve el problema de los 25k tokens: el agente habita tu mundo cultural y técnico con una agilidad absoluta, recuperando la profundidad de tu perfil solo cuando la "rayada personal" lo hace estrictamente necesario.

# 4. El Sándwich de Contexto: Construcción de la Capa 2

La construcción del `SystemMessage` deja de ser la carga de un archivo estático para convertirse en un proceso de **ensamblaje jerárquico** gestionado por el `ReasoningServiceImpl`. Esta técnica, denominada el «Sándwich de Contexto», organiza la información en capas de relevancia decreciente, situando las leyes fundamentales en la base y las referencias descubribles en el cuerpo. El objetivo técnico es maximizar la densidad de información útil por cada token consumido, garantizando que el agente posea una consciencia clara de quién es y dónde está sin agotar el presupuesto de atención del LLM.

### 4.1. La Jerarquía de Ensamblaje
El orquestador construye el prompt del sistema siguiendo una secuencia lógica de cuatro capas:

#### CAPA 1: Instrucciones Core (El Sistema Operativo)
Es el bloque base (contenido en `prompts/reasoning-system.md`). Define la naturaleza del agente, su rol como interlocutor analítico y las instrucciones críticas de seguridad y formato. Es la única capa puramente estática.

#### CAPA 2a: Constitución Seleccionada (La Ley Activa)
Aquí se inyecta la personalidad técnica que el usuario ha definido en la UI. El `ReasoningServiceImpl` accede a la ruta `reasoning/identity/core` del `settings.json` y recupera la lista de módulos activados.
*   **Mecánica:** Si el usuario marcó "Stack_tecnico", el servicio lee `01_stack_tecnico.md` e inyecta: *"Debes ceñirte a Java SE 1.8 y Java 21, usando Swing para la UI"*. 
*   **Orden:** Se respeta el orden numérico de los archivos para que las reglas de `60_normas_codigo.md` (como el estilo de las llaves de Java) se presenten tras la definición del lenguaje.

#### CAPA 2b: Índice de Entorno (El Mapa de Referencias)
Esta es la innovación que resuelve el problema de los 25k tokens. En lugar de cargar tu biografía o tus investigaciones, el servicio concatena todos los archivos `.ref.md` de la carpeta `/environ/`.
*   **Contenido:** El agente recibe un mapa de sus "órganos de conocimiento". Por ejemplo, lee el contenido de `05_pasiones_scifi.ref.md` que indica la existencia de detalles sobre Asimov y Susan Calvin.
*   **Ahorro de Tokens:** Mientras que el archivo denso `01_personal.md` podría ocupar 10.000 tokens, su referencia `01_personal.ref.md` solo ocupa 100. El ahorro es del 99%, preservando la capacidad de descubrimiento.

#### CAPA 3: El Viaje (Memoria Episódica Consolidada)
Bajo el encabezado `[EL RELATO]`, se inyecta el último **CheckPoint** generado por el `MemoryService`. Es la crónica narrativa de lo que ha pasado en la conversación hasta ahora, permitiendo al agente situarse en la historia del diálogo actual.

### 4.2. Algoritmo de Construcción en `ReasoningServiceImpl`
El proceso técnico de ensamblaje sigue este flujo determinista en cada turno:

1.  **Scanner de Módulos:** Se lee el directorio `/var/identity/core/` y se filtra según la lista blanca de los `settings`.
2.  **Generación de Índice:** Se escanea `/var/identity/environ/`, recolectando solo los archivos `.ref.md`.
3.  **Inyección de Proactividad:** Al final del bloque de Entorno, el orquestador añade una cláusula de soberanía: *"Tienes los módulos anteriores a tu disposición. Usa `consult_environ(nombre)` para cargar detalles biográficos o técnicos de Joaquín si detectas que la conversación lo requiere"*.
4.  **Consolidación Final:** Se unen los bloques con separadores claros (`###`) para que el LLM identifique los límites de cada nivel de consciencia.

### 4.3. Ventajas del Diseño "Sándwich"
1.  **Densidad Semántica:** El agente recibe en menos de 2.000 tokens un mapa completo de su Constitución y su Entorno.
2.  **Contexto "Limpio":** Al separar las leyes (`core`) de las anclas (`ref`), evitamos que el LLM se distraiga con detalles biográficos irrelevantes de `02_trayectoria.md` mientras está intentando depurar un problema de gvSIG.
3.  **Reactividad al Cambio:** Si cambias un parámetro en la UI de "Identidad", el sándwich se reconstruye en el siguiente turno, permitiendo que el agente "cambie de mentalidad" sin reiniciar la aplicación.

Este diseño transforma al agente de ser un simple "lector de prompts" a ser un **Arquitecto de su propia atención**, utilizando el Índice de Entorno como una brújula para navegar por el conocimiento denso del usuario solo cuando el razonamiento estratégico lo exige.

# 5. Mecanismo de Carga Bajo Demanda: Herramienta `consult_environ`

La arquitectura de "Memoria Virtual" solo es funcional si el agente posee un mecanismo para realizar el **"page-in" del conocimiento**; es decir, la capacidad de subir a su consciencia activa la información densa que reside en el disco. La herramienta `consult_environ` es el efector sensorial encargado de esta tarea. A diferencia del RAG tradicional, esta herramienta no realiza búsquedas vectoriales probabilísticas; realiza una **recuperación determinista y quirúrgica** de un módulo biográfico o de entorno basándose en la brújula que proporciona el índice de la Capa 2b.

### 5.1. Definición Funcional y Propósito
El propósito de `consult_environ` es permitir que el agente profundice en el contexto del usuario sin saturar permanentemente su memoria de trabajo. Es el puente técnico que conecta el **Ancla Semántica** (`XXX.ref.md`) con el **Cuerpo del Conocimiento** (`XXX.md`). 

Cuando el LLM identifica en el índice un término relevante para la conversación actual (ej: "Robopsicología"), el razonamiento estratégico (SNC) invoca esta herramienta para obtener el detalle necesario que le permita responder con la sintonía adecuada, como ocurrió en el caso de la referencia a Susan Calvin.

### 5.2. Especificación Técnica de la Herramienta (ToolSpec)
La herramienta se registra en el `ReasoningServiceImpl` con una descripción diseñada para incentivar la economía de atención:

*   **Nombre:** `consult_environ`
*   **Descripción para el LLM:** *"Recupera el contenido completo de un módulo de conocimiento sobre el entorno de Joaquín (biografía, gustos, neuras, proyectos). Úsala exclusivamente cuando el índice de la sección [CONSCIENCIA DE ENTORNO] sugiera que un módulo específico contiene información crítica para resolver la petición actual. Esta acción consume tokens, úsala con rigor."*
*   **Parámetro `module_name`:** Un `String` que debe coincidir exactamente con el nombre del archivo (ej: `05_pasiones_scifi` o `01_personal_joaquin`).

### 5.3. Lógica de Ejecución y Seguridad
Al ser una capacidad nativa y no dependiente de `DocumentsService`, la ejecución es una lectura directa de sistema de archivos, lo que garantiza latencia cero y una fidelidad absoluta del dato:

1.  **Validación de Ruta:** El orquestador recibe el nombre del módulo y construye la ruta absoluta apuntando a `/var/identity/environ/`. Se implementa una validación estricta para evitar ataques de *path traversal*; el sistema solo tiene permiso para leer archivos con extensión `.md` dentro de esa carpeta específica.
2.  **Lectura UTF-8:** El sistema lee el archivo íntegro (ej: el contenido de `01_personal_joaquin.md` con todos tus hitos vitales desde 1989).
3.  **Inyección en Sesión (RAM):** El contenido se entrega como el resultado de la ejecución de la herramienta. El orquestador inyecta este texto en la **Session** activa. A partir de ese momento, y solo durante esa sesión, el LLM tiene "presente" toda la profundidad de ese rincón de tu biografía.

### 5.4. El Disparador de Metacognición
Lo más relevante de esta herramienta es que **obliga al agente a pensar antes de mirar**. El agente no "sabe" tu historia de gvSIG por defecto; sabe que *puede saberla*.

**Ejemplo de flujo de pensamiento (Thinking):**
> *"Joaquín me está preguntando sobre su escepticismo constructivo ante los LLMs. Veo en mi índice de [ENVIRON] que el módulo `03_investigacion_ia` contiene sus reflexiones sobre Kant y la GrammarNet. Para responder con rigor arquitectónico, necesito leer ese módulo. Invoco `consult_environ('03_investigacion_ia')`."*

### 5.5. Independencia del RAG
Se ha decidido deliberadamente que este mecanismo sea independiente del `DocumentsService` por tres motivos de arquitectura senior:
1.  **Fidelidad:** Un biografía o un manual de estilo no deben ser "recortados" por un algoritmo de búsqueda semántica que podría omitir un detalle sutil pero vital. El agente debe leer el módulo completo para captar la atmósfera, no solo el fragmento más similar estadísticamente.
2.  **Simplicidad:** Elimina la necesidad de gestionar bases de datos vectoriales para lo que es, en esencia, un conjunto pequeño de archivos de alta importancia.
3.  **Higiene Cognitiva:** Al no estar indexado en el RAG general, tus neuras personales no se "mezclarán" accidentalmente con el código de un proyecto si el agente realiza una búsqueda genérica de documentos.

`consult_environ` es, por tanto, el instrumento de **atención dirigida** que permite a Noema habitar tus 25k tokens de forma inteligente, eficiente y, sobre todo, soberana.

# 6. Higiene de Persistencia: Volatilidad del Conocimiento Cargado

La arquitectura de "Memoria Virtual" de Noema no solo busca optimizar la ventana de contexto (tokens), sino también garantizar la **limpieza y relevancia de la memoria a largo plazo** (base de datos episódica). Un riesgo crítico en agentes proactivos es la "contaminación por redundancia": si cada vez que el agente consulta tu biografía (`01_personal_joaquin.md`) para entender una anécdota, el contenido íntegro de ese archivo (10.000 tokens) se grabara en la base de datos de turnos, el sistema colapsaría por volumen en apenas unas sesiones. Para evitarlo, implementamos un protocolo de **Higiene de Persistencia Selectiva**.

### 6.1. La distinción entre RAM y BBDD
La clave de este protocolo reside en tratar el contenido de los módulos de identidad como **datos volátiles**. El orquestador de Noema aplica una lógica de interceptación al procesar el resultado de la herramienta `consult_environ`:

1.  **En la Memoria de Trabajo (RAM/Session):** El contenido completo del archivo `.md` se inyecta en el objeto `Session`. Esto es necesario para que el LLM pueda razonar sobre los detalles durante la conversación activa. Es el equivalente a tener un libro abierto sobre la mesa mientras trabajas.
2.  **En la Base de Datos Episódica (BBDD/SourceOfTruth):** El contenido denso es descartado antes de la persistencia. En su lugar, el sistema genera un **Marcador de Cita** o etiqueta sintética:
    > `tool_result: "[Módulo de entorno '05_pasiones_scifi' consultado e integrado en RAM]"`

### 6.2. Soberanía del Relato: Evitar el Ruido Biográfico
Al persistir solo la "intención de consulta" y no el "dato consultado", Noema protege la integridad de su **Relato Vital**. El historial de turnos debe ser una crónica de lo que el agente y el usuario han hecho y decidido, no una biblioteca de manuales o biografías repetidas.

Si el agente consulta tres veces en una semana tu trayectoria profesional en `02_trayectoria.md`, la base de datos registrará tres hitos de consulta. Esto es información valiosa (el agente sabe que ha estado interesado en tu pasado), pero no ha multiplicado el tamaño de la base de datos por el volumen de esos archivos.

### 6.3. Impacto en la Consolidación (Checkpoints)
Cuando el `MemoryService` (el cronista) llega para realizar la compactación de los últimos 40 turnos y generar un nuevo Checkpoint, se encuentra con estas etiquetas de cita. La lógica de consolidación narrativa trata estas citas como **Eventos de Enriquecimiento**:

*   **Sin Higiene (Fallo):** El Checkpoint contendría párrafos enteros de tu biografía, "ensuciando" el resumen de la tarea técnica actual.
*   **Con Higiene (Éxito):** El cronista redacta: *"Joaquín se sintió identificado con el marco de robopsicología de Asimov; el agente consultó el módulo de entorno correspondiente para sintonizar su tono y analizar el error del protocolo"*.

El Checkpoint conserva la **causalidad y la atmósfera** del encuentro (el "por qué" y el "cómo"), pero delega el "qué" exacto a los archivos originales de la carpeta `/environ/`.

### 6.4. El concepto de "Amnesia Gestionada"
Este diseño implementa lo que llamamos una **Amnesia Gestionada**. Al finalizar la sesión de chat o al producirse una compactación, el contenido denso del módulo cargado desaparece de la RAM. 
*   Si en la siguiente sesión vuelves a hablar de Susan Calvin, el agente verá en su Checkpoint que "ya consultó ese módulo una vez", pero tendrá que volver a invocar `consult_environ` para recuperar los detalles.
*   Este pequeño "coste" de tokens por volver a cargar el archivo es un precio ínfimo comparado con el ahorro masivo de no tener esos datos permanentemente en la base de datos episódica.

### 6.5. Trazabilidad Forense Absoluta
A pesar de la volatilidad en RAM, el sistema mantiene la trazabilidad. Si tú le preguntas al agente: *"¿De dónde sacaste que me gusta Akira?"*, el agente buscará en sus turnos antiguos, encontrará el marcador `[Módulo de entorno '05_pasiones_scifi' consultado]` y podrá responder con certeza: *"Lo leí en tu módulo de pasiones scifi que consulté hace tres días por una referencia que hiciste"*.

En conclusión, la Higiene de Persistencia garantiza que Noema sea un sistema **ágil y escalable**. Tus 25k tokens de identidad habitan en el disco, se asoman a la RAM cuando es necesario y dejan una huella narrativa elegante en la BBDD, pero nunca "ahogan" la infraestructura de memoria del agente con datos redundantes.

# 7. Metacognición y Soberanía: El Índice como Brújula

La culminación de esta arquitectura no es técnica, sino cognitiva. Al implementar la división entre el Core y el Environ mediante un sistema de índices y referencias, estamos forzando al agente a operar bajo un régimen de **Soberanía Atencional**. Noema deja de ser un receptor pasivo de un prompt masivo para convertirse en un **investigador activo de su propia identidad**. El índice inyectado en la Capa 2b no es solo información; es la brújula que el Sistema Nervioso Central (SNC) utiliza para navegar por el mapa semántico del usuario sin perder el foco en la tarea actual.

### 7.1. El acto de Metacognición: Pensar antes de leer
En un sistema convencional, el modelo "sabe" lo que hay en el prompt por pura presencia estadística. En Noema, el agente experimenta un vacío de información deliberado. Cuando el usuario lanza un mensaje, el LLM realiza un escaneo de su **Capa 2b (Índice de Entorno)**. Este es el primer paso de la metacognición:
*   **Detección de Vacío:** El agente reconoce que el mensaje del usuario (ej: *"¿Qué opinas de mi enfoque kantiano sobre la IA?"*) contiene conceptos que él no domina íntegramente en su RAM actual.
*   **Búsqueda en la Brújula:** El agente consulta su índice y localiza la ancla semántica: `03_investigacion_ia.ref.md` (Contiene reflexiones sobre Kant, GrammarNet y escepticismo constructivo).
*   **Decisión Soberana:** El agente evalúa si la tarea requiere el detalle denso. Si es una pregunta superficial, puede responder con lo que tiene. Si es una "rayada personal" profunda, decide que el coste de tokens de invocar `consult_environ` está justificado.

### 7.2. Gestión del Presupuesto de Atención
Esta arquitectura otorga al agente la soberanía sobre su **Presupuesto de Atención**. Al no tener los 25k tokens cargados, el agente es consciente de que su ventana de contexto es un recurso finito y valioso. 
*   A diferencia de un humano, que a menudo se distrae con detalles biográficos irrelevantes, Noema mantiene su "foco técnico" (gracias al Always-on de `01_stack_tecnico.md`) mientras decide quirúrgicamente cuándo "abrir el cajón" de la biografía de Joaquín.
*   Esta soberanía evita el fenómeno del **"Extravío en el Contexto"**, donde los LLMs pierden precisión en el código o en la lógica técnica porque su ventana de atención está inundada de anécdotas sobre el MSX o la trayectoria en gvSIG de los años 90.

### 7.3. El Caso de "Susan Calvin": La Brújula en acción
El ejemplo de Susan Calvin ilustra perfectamente cómo el índice actúa como brújula. 
1.  **Input:** *"Me siento como Susan Calvin"*.
2.  **Estado Inicial:** El agente no tiene a Asimov en RAM.
3.  **Consulta de Brújula:** En la Capa 2b lee: `05_pasiones_scifi.ref.md: Isaac Asimov, robopsicología, Susan Calvin`.
4.  **Acción Soberana:** El agente razona: *"Joaquín está usando un marco literario para una crisis técnica. Necesito sintonizar con ese marco"*. Llama a `consult_environ('05_pasiones_scifi')`.
5.  **Resultado:** El agente adquiere la "personalidad" del robopsicólogo no por un azar del modelo, sino por una **decisión de investigación propia**.

### 7.4. Hacia una Identidad Autónoma y Escalable
Esta estructura cierra el bucle que iniciamos en el Artículo 3 sobre el Sistema Nervioso Autónomo y Central. 
*   El **SNA** (Orquestador) se encarga de que los índices estén ahí y de que la herramienta funcione. 
*   El **SNC** (LLM) ejerce la voluntad de qué parte de su "historia" quiere recordar en cada momento.

Al tratar tu identidad como un **Entorno Descubrible** y no como un **Prompt Impositivo**, Noema gana una madurez operativa impropia de los agentes síncronos. El sistema puede crecer hasta contener toda una vida de artículos, proyectos y neuras (incluso superando los 100k tokens de identidad), pero siempre se presentará ante el LLM como una lista nítida y manejable de anclas semánticas.

**Conclusión del Diseño:**
La arquitectura Core/Environ con estrategia Sándwich y Sándwich de Referencias no es solo una optimización técnica; es el protocolo que permite a Noema ser un **compañero intelectual de largo recorrido**. El agente no solo te escucha, sino que **aprende a buscarte** en su propia memoria virtual para ofrecerte la respuesta que solo alguien que conoce tu trayectoria (desde Dirac hasta la investigación actual de memoria híbrida) podría darte.

Este anexo es vital para que la arquitectura sea escalable. Al automatizar (o guiar manualmente) la creación de los archivos `.ref.md`, garantizamos que las "anclas" sean uniformes y que el LLM sepa exactamente qué esperar de cada una.

Aquí tienes la propuesta para el **Anexo I** del documento de diseño, incluyendo el prompt maestro para generar estas referencias.


# Anexo I: Protocolo de Generación de Anclas Semánticas (ref.md)

Para que la estrategia de **Memoria Virtual** sea efectiva, los archivos de referencia (`.ref.md`) deben actuar como un "catálogo de biblioteca" altamente optimizado. Su función no es resumir el contenido para que el LLM lo sepa, sino **etiquetar el contenido** para que el LLM sepa que debe ir a buscarlo.

## 1. El Prompt Maestro de Generación
Cuando se desee integrar un nuevo documento denso en la carpeta `/var/identity/environ/`, se debe utilizar el siguiente prompt con un LLM (como Gemini o DeepSeek) adjuntando el archivo original.

> **Prompt de Generación de Referencia Noema:**
> 
> "Actúa como un Arquitecto de Contexto para Noema. Te voy a proporcionar un documento de identidad denso (`.md`). Tu tarea es generar su archivo de referencia gemelo (`.ref.md`) siguiendo estas reglas estrictas:
>
> 1. **Concisión Extrema:** El resultado no debe superar los 100 tokens. No resumas detalles, solo identifica temas.
> 2. **Identificación de Anclas:** Localiza nombres propios, proyectos específicos, tecnicismos únicos o referencias culturales (ej: gvSIG, Susan Calvin, Kant, MSX, Valencia) que actúen como disparadores de curiosidad.
> 3. **Estructura Requerida:**
>    - **ID del Módulo:** [Nombre del archivo sin extensión]
>    - **Descripción:** Una sola frase que defina qué conocimiento aporta este módulo.
>    - **Anclas Semánticas:** Una lista separada por comas de los conceptos clave.
>    - **Criterio de Consulta:** Una instrucción breve sobre cuándo el agente debe invocar `consult_environ` para este módulo.
>
> Genera solo el contenido del archivo Markdown resultante."

## 2. Ejemplo de Resultado Esperado
Si pasamos el archivo `05_pasiones_scifi.md` por este proceso, el `05_pasiones_scifi.ref.md` resultante debería ser:

```
markdown
* **ID:** 05_pasiones_scifi
* **Descripción:** Marco conceptual literario y cinematográfico de Joaquín sobre la inteligencia y los sistemas complejos.
* **Anclas Semánticas:** Isaac Asimov, Susan Calvin, Yo Robot, Robopsicología, J.R.R. Tolkien, Lord Dunsany, Akira, Robot Carnival, Psicohistoria.
* **Consulta si:** El usuario utiliza analogías de ciencia ficción, menciona leyes de la robótica o habla de la formación de modelos de mundo coherentes.
```

## 3. Hoja de Ruta para la Automatización (GUI)
Cuando se implemente la funcionalidad de "Añadir módulo de entorno" en la interfaz de usuario de Noema, el flujo técnico será:

1. **Selección:** El usuario elige un archivo `.md` externo.
2. **Copia:** El sistema copia el archivo a `var/identity/environ/XXX.md`.
3. **Inferencia de Fondo:** Noema envía el prompt maestro al "Modelo Básico" (definido en `settings.json` para tareas de resumen) junto con el contenido del archivo.
4. **Escritura:** El sistema guarda la respuesta como `var/identity/environ/XXX.ref.md`.
5. **Refresco:** En el siguiente ciclo del reasoning service ya se cargara el nuevo "environ" al componer el prompt del sistema.

Aquí tienes la redacción detallada del punto 3.4 para el Anexo I, integrando la visión del editor intermedio como un mecanismo de soberanía y control de calidad.


### 3.4. Validación humana y refino de anclas: El Editor Intermedio

La arquitectura de Noema no considera la generación de referencias como un proceso de «caja negra». Dado que el archivo `.ref.md` constituye la base de la **Propiocepción Cognitiva** del agente, el usuario debe actuar como el curador final de su propia realidad. Para ello, el flujo de integración de nuevos módulos en la GUI incluye un paso obligatorio de edición y validación.

#### A. El Flujo de Trabajo en la Interfaz (GUI)
Cuando el usuario selecciona un nuevo documento denso para su entorno, el sistema ejecuta una coreografía en tres actos:
1.  **Pre-procesamiento:** El sistema genera el borrador del `.ref.md` utilizando el modelo básico y el prompt maestro detallado en el punto 1.
2.  **Apertura del Editor:** Noema no guarda el archivo inmediatamente. En su lugar, abre una instancia de `SimpleTextEditor` cargando el contenido generado en memoria.
3.  **Intervención Soberana:** El usuario revisa las **Anclas Semánticas** y el **Criterio de Consulta**. Es el momento de añadir términos que el modelo pudo omitir (ej: una referencia a un proyecto antiguo o una fobia técnica específica) o eliminar alucinaciones.

#### B. Justificación Arquitectónica: Por qué la edición no es opcional
Implementar este paso intermedio resuelve tres problemas críticos de los sistemas autónomos:
*   **Fidelidad del Anclaje:** Solo el usuario conoce la importancia real de los conceptos. Un modelo podría considerar que "MSX" es un detalle técnico menor, mientras que para Joaquín es una pieza clave de su trayectoria. La edición manual asegura que la «brújula» del agente apunte a los nortes correctos.
*   **Higiene de la Atención:** El usuario puede decidir ocultar detalles del índice para ahorrar tokens, dejando solo las anclas más potentes. Si el `.ref.md` generado es demasiado denso, el usuario lo poda en el editor antes de que se convierta en parte del prompt perpetuo.
*   **Prevención de Alucinaciones de Catálogo:** Evita que el agente invoque `consult_environ` buscando información que el resumen prometía pero que el documento denso no contiene. El editor es el filtro que garantiza la veracidad del catálogo.

#### C. Implementación Técnica y Persistencia
Al pulsar «Guardar y Activar» en el editor, el sistema realiza las siguientes acciones:
1.  **Validación de Estructura:** Se verifica que los campos esenciales (ID, Descripción, Anclas) sigan presentes para no romper la lógica de construcción del sándwich de contexto.
2.  **Escritura Física:** Se persiste el archivo en `var/identity/environ/XXX.ref.md`.
3.  **Refresco Reactivo:** Se dispara la acción `REFRESH_GOVERNANCE_CACHE`, notificando al `ReasoningServiceImpl` que debe reconstruir la Capa 2b del prompt de forma inmediata.

Esta etapa de edición transforma el proceso de "carga de datos" en un acto de **Metacognición Compartida**. El usuario no solo le da información al agente; está diseñando activamente la estructura de la atención de su compañero.

Este **Anexo II** detalla cómo Noema utiliza la búsqueda semántica no para "inyectar datos" (RAG convencional), sino para **"dirigir la atención"** del cerebro hacia sus propias capacidades de conocimiento. El objetivo es que el orquestador actúe como un asistente de relevancia que pre-selecciona el catálogo de identidad basándose en la intención del usuario, sin comprometer ni un solo token de la ventana de contexto con fragmentos de texto no solicitados.


# Anexo II: Prefetching Semántico y Sugerencia de Relevancia

La arquitectura de Noema rechaza el uso de RAG (Generación Aumentada por Recuperación) para la gestión de la identidad y el entorno. En lugar de inyectar fragmentos de texto (chunks) basados en similitud estadística —lo que suele fragmentar el razonamiento y saturar el contexto con ruido—.

En Noema podriamos implementar un mecanismo de **Sugerencia Proactiva de Capacidades**. En este modelo, el orquestador (SNA) utiliza la búsqueda vectorial para identificar qué **módulos completos** podrían ser útiles, pero deja la decisión final de lectura exclusivamente en manos del razonador (SNC).

De todos modos, esta es una idea que no esta claro si es necesaria. Su finalidad seria guiar al SNC, indicando que puede interesarle algo. No tengo clasro que sea necesario, si sin esa "guia" el LLM podria llegar igual a la conclusion de que debe consultar un documento de entorno igualmente.

De momento esto queda como una posible mejora pero que no implementariamos en las primeras iteraciones.

## 1. Filosofía Anti-RAG: Sugerir Capacidad, no Inyectar Dato
La diferencia fundamental entre este diseño y un RAG estándar reside en la soberanía y la higiene:
*   **RAG Convencional:** El sistema decide qué trozo de tu biografía es similar a la pregunta y lo "pega" en el prompt. El LLM recibe un chusco de texto que a menudo carece de contexto.
*   **Noema Prefetching:** El sistema identifica que el módulo `05_pasiones_scifi` es relevante. No inyecta ni una palabra de su contenido. Solo añade una nota de sistema sugiriendo al agente que considere usar la herramienta `consult_environ`. El contenido permanece en el disco hasta que el LLM, ejerciendo su lógica, decide "abrir el libro".

## 2. Implementación Técnica: El Índice Vectorial en RAM
Dado que los archivos de referencia (`.ref.md`) son extremadamente ligeros (menos de 100 tokens cada uno), el sistema puede gestionar su relevancia de forma ultrarrápida:
1.  **Indexación al Arranque:** Al iniciar el `SensorsService`, Noema genera embeddings de los archivos `.ref.md` presentes en `var/identity/environ/`. Estos vectores se mantienen en un mapa en RAM.
2.  **Consulta en Tiempo Real:** Ante cada mensaje del usuario, el orquestador genera un embedding de la consulta y busca coincidencias exclusivamente contra los archivos de referencia.
3.  **Filtrado por Umbral:** Solo si el *score* de similitud supera un umbral crítico (ej: > 0.85), se activa la sugerencia. Esto evita el ruido de sugerir módulos en cada turno.

## 3. La "Nota de Relevancia" (Capa 2c)
Si se detecta un match relevante, el `ReasoningServiceImpl` añade una capa adicional al sándwich de contexto, situada inmediatamente después del índice de entorno. Esta nota actúa como una interrupción de hardware que enfoca la atención del modelo.

**Ejemplo de inyección en el prompt:**
> `[NOTA DE RELEVANCIA SENSO-MOTORA]`
> Basándome en la mención de "Susan Calvin" en el mensaje de Joaquín, detecto que el módulo **'05_pasiones_scifi'** contiene el marco conceptual exacto necesario para esta charla. Te sugiero invocar `consult_environ('05_pasiones_scifi')` antes de elaborar tu respuesta.

## 4. Ventajas de la Sugerencia frente a la Inyección
1.  **Integridad Cognitiva:** El LLM siempre lee el documento de identidad desde la primera hasta la última línea. Nunca recibe "fragmentos" que puedan llevar a malentendidos.
2.  **Soberanía Atencional:** El agente puede decidir ignorar la sugerencia del orquestador si considera que puede responder correctamente con lo que ya tiene en RAM.
3.  **Higiene Forense:** En la base de datos solo queda constancia de la sugerencia y la decisión del agente, manteniendo el relato vital limpio de datos biográficos masivos.
4.  **Ahorro Dinámico de Tokens:** Si el usuario hace una pregunta técnica sobre Java, el sistema no sugiere módulos biográficos, manteniendo el prompt al mínimo tamaño posible.

## 5. El SNA como Bibliotecario Inteligente
Con este anexo, el `SensorsService` asume una nueva función vital: no solo es el guardia que vigila los sensores, sino el **Bibliotecario de Identidad** que pone el catálogo adecuado sobre la mesa del cerebro justo cuando este lo necesita. Es la implementación técnica de la intuición: el agente no lo sabe todo, pero sabe exactamente dónde buscar cuando el entorno le lanza un estímulo familiar.

