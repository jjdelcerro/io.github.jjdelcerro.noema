# Arquitectura de subagentes delegados en Noema


## Introducción y principios de diseño

A medida que un agente conversacional asume tareas complejas (auditorías de código, lectura de manuales extensos, migraciones o análisis de seguridad), surge una tensión evidente en la gestión de su memoria: **las tareas de computación pesada no deben contaminar la memoria de trabajo de la conversación principal**.

Si el agente principal asume directamente una tarea que requiere decenas de llamadas a herramientas, la memoria reciente se satura de datos de bajo nivel, se fuerzan compactaciones prematuras y se diluye la atención del modelo respecto a las directivas del usuario.

Para resolver esto, Noema define el **patrón de subagentes delegados**. Un subagente es un trabajador especializado, con ciclo de vida finito y aislamiento total, diseñado para ejecutar un trabajo pesado en dos fases y devolver un resultado consolidado al agente principal.

### Principios rectores del diseño

1. **Aislamiento por instancia completa:** El subagente no es un hilo ligero con trucos en memoria; es una instancia completa de `AgentImpl` levantada en un directorio temporal (`var/tmp/subagent_<name>_<UUID>/`), con su propia base de datos H2, sus tablas limpias y su propia sesión. Al terminar, la instancia se detiene y el directorio temporal se destruye.
2. **Definición declarativa basada en ficheros:** Las recetas de los subagentes se definen como descriptores XML almacenados en el sistema de archivos (`var/subagents/`), heredando la resolución en dos niveles de `AgentPaths` (workspace local o configuración global).
3. **Cero sobre-ingeniería de protocolos A2A:** No se implementan brokers de mensajes distribuidos ni arquitecturas de actores complejas. El subagente interactúa con el mundo mediante las mismas herramientas estándar de Noema (`file_read`, `annotate_observation`, `file_write`) y se comunica con el agente principal a través de su bus sensorial (`SensorsService`).
4. **Ejecución en dos actos (Exploración $\rightarrow$ Síntesis):** Toda tarea delegada se estructura en una fase de trabajo e ingesta (`prompt_ini`) seguida de una fase de estructuración y entrega (`prompt_fin`).

---

## Definición declarativa de recetas de subagentes

Los subagentes no se programan como clases Java independientes; se configuran como **recetas declarativas en formato XML**.

### Topología y resolución en disco

Siguiendo el principio de `AgentPaths`, los descriptores residen en carpetas `var/subagents/`:

* **Subagentes globales (`~/.config/noema-agent/var/subagents/`):** Trabajadores estándar disponibles para cualquier espacio de trabajo (ej. indexador de documentación, auditor de seguridad, extractor de esquemas SQL).
* **Subagentes locales (`workspace/.noema-agent/var/subagents/`):** Trabajadores específicos de un proyecto concreto, con capacidad de sobrescribir una receta global si comparten nombre.

### Estructura del descriptor XML

Cada archivo (ej. `document_indexer.xml`) define los límites operativos del trabajador:

```xml
<subagent name="document_indexer">
    <description>Recorre un documento grande en bloques, cataloga sus secciones con números de línea y genera un índice estructurado en Markdown.</description>
    
    <!-- Lista blanca estricta de herramientas disponibles para el subagente -->
    <tools>
        <tool name="file_read" />
        <tool name="read_paginated_resource" />
        <tool name="annotate_observation" />
        <tool name="list_annotations" />
        <tool name="fetch_citation" />
        <tool name="file_write" />
    </tools>

    <!-- Modelo de lenguaje asignado (permite usar modelos rápidos/económicos) -->
    <model_id>deepseek-chat</model_id>

    <!-- Fase 1: Instrucciones de exploración e ingesta (admite placeholders) -->
    <prompt_ini>
        Lee el archivo '{FILE_PATH}' de principio a fin utilizando bloques de 100 líneas mediante 'read_paginated_resource'.
        Por cada sección, capítulo o cambio temático que identifiques, ejecuta OBLIGATORIAMENTE la herramienta 'annotate_observation' con:
        - type: "section_index"
        - resource_id: el RESOURCE_ID del archivo
        - note: resumen técnico de 1-2 frases de la sección, incluyendo título y rango de líneas.
        Continúa hasta consumir el último bloque del archivo.
    </prompt_ini>

    <!-- Fase 2: Instrucciones de síntesis y entrega -->
    <prompt_fin>
        Has completado la lectura de '{FILE_PATH}'.
        Consulta todas las notas tomadas usando 'list_annotations' con type="section_index".
        Si necesitas recuperar detalles específicos de alguna nota, utiliza 'fetch_citation'.
        Compón un documento de índice estructurado en formato Markdown y guárdalo en '{OUTPUT_PATH}' utilizando la herramienta 'file_write'.
    </prompt_fin>
</subagent>
```

---

## Ciclo de vida del subagente aislado

Cuando una tarea delegada se activa, el arnés orquesta una secuencia determinista de cuatro etapas:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ 1. PREPARACIÓN DEL ENTORNO TEMPORAL                                         │
│    - Crea var/tmp/subagent_<name>_<UUID>/                                   │
│    - Instancia AgentPaths y AgentSettings específicos para esa ruta         │
│    - Fuerza access_control/humanConfirmationRequired = false                 │
│    - Resuelve placeholders ({FILE_PATH}, {OUTPUT_PATH}) en los prompts      │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ 2. ARRANQUE DEL MOTOR AISLADO                                               │
│    - BootUtils.init(subSettings) levanta una BBDD H2 limpia e independiente │
│    - subAgent.start() inicia un hilo de razonamiento exclusivo              │
│    - Configura la lista blanca de herramientas definida en el XML           │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ 3. EJECUCIÓN EN DOS FASES                                                   │
│    - FASE 1 (prompt_ini): El subagente lee bloques, sufre amnesia           │
│      selectiva en datos brutos y consolida notas con annotate_observation.  │
│    - FASE 2 (prompt_fin): Inyección de la orden de cierre. El subagente lee │
│      sus notas estructuradas y escribe el artefacto final con file_write.   │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ 4. DESTRUCCIÓN Y LIMPIEZA                                                   │
│    - subAgent.stop() cierra conexiones JDBC y detiene hilos                 │
│    - FileUtils.deleteDirectory() elimina var/tmp/subagent_<name>_<UUID>/    │
│    - Notificación de finalización al Agente Principal                       │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Gestión de la memoria durante la tarea delegada

El subagente no es una simple llamada a una función; es un agente Noema completo que se beneficia de los cuatro estratos de memoria mientras realiza el trabajo:

1. **Amnesia selectiva en lecturas masivas:** A medida que el subagente lee decenas de bloques de texto mediante `read_paginated_resource`, los bloques antiguos se podan a cabeceras con `CONTENT_TRIMMED: true`. La ventana de contexto no desborda aunque el documento tenga miles de líneas.
2. **Inmunidad de las notas estructuradas:** Cada llamada a `annotate_observation` (con `type="section_index"`) permanece intacta en la memoria proyectada del subagente, no sufre poda y se persiste inmutable en la base de datos H2 temporal.
3. **Resiliencia ante compactaciones intermedias:** Si el documento es gigantesco (ej. 50.000 líneas) y el subagente supera el umbral de turnos, su propio `MemoryService` compacta la historia en un CheckPoint intermedio. El subagente puede continuar leyendo las siguientes 20.000 líneas sin perder el hilo de lo que ya ha catalogado.

---

## Catálogo de herramientas para la orquestación

La interacción entre el usuario, el agente principal y los subagentes se articula mediante un conjunto de cuatro herramientas:

### 1. `register_subagent`
Permite crear y persistir nuevas recetas de subagentes en disco. Puede ser invocada directamente por el Agente Principal tras una fase de diseño colaborativo con el usuario.

* **Parámetros:**
  * `name` (string, obligatorio): Identificador técnico del subagente.
  * `description` (string, obligatorio): Propósito funcional del trabajador.
  * `tools` (array de strings, obligatorio): Lista de herramientas autorizadas.
  * `prompt_ini` (string, obligatorio): Plantilla de instrucciones de la Fase 1.
  * `prompt_fin` (string, obligatorio): Plantilla de instrucciones de la Fase 2.
  * `model_id` (string, opcional): Modelo específico para la tarea.
* **Comportamiento:** Valida que las herramientas solicitadas existan en el sistema, comprueba que no violen las políticas de seguridad globales y escribe el archivo `var/subagents/{name}.xml` mediante escritura atómica.

### 2. `launch_subagent`
Permite al Agente Principal poner en marcha un subagente registrado pasándole parámetros dinámicos.

* **Parámetros:**
  * `subagent_name` (string, obligatorio): Nombre de la receta a ejecutar.
  * `params` (objeto clave-valor, obligatorio): Variables para resolver los placeholders del XML (ej: `{"FILE_PATH": "manual.md", "OUTPUT_PATH": "manual.index.md"}`).
  * `async` (booleano, opcional, por defecto `false`): Si es `true`, ejecuta el subagente en segundo plano sin bloquear el diálogo del Agente Principal.
* **Comportamiento:** Carga el descriptor XML, prepara el workspace temporal, ejecuta las dos fases y retorna el informe de cierre (en síncrono) o confirma el inicio de la tarea en segundo plano (en asíncrono).

### 3. `notify`
Permite a un subagente que corre en segundo plano comunicarse con el Agente Principal para avisarle de que ha terminado su trabajo.

* **Parámetros:**
  * `terminal_id` (string, obligatorio): Identificador del subcanal o terminal donde está conversando el usuario.
  * `message` (string, obligatorio): Texto informativo del evento.
* **Comportamiento:** Invoca internamente a `agent.putEvent(channel="SUBAGENT", subchannel=terminal_id, ...)` en el agente principal. El estímulo despierta al `eventDispatcher` del agente principal a través de `pool_event`, permitiéndole informar al usuario proactivamente.

### 4. `list_annotations`
Permite a un subagente (especialmente en la Fase 2) recuperar el catálogo de notas que fue sembrando durante la Fase 1 sin desbordar el contexto.

* **Parámetros:**
  * `type` (string, obligatorio): Categoría de notas a consultar (ej: `"section_index"`).
  * `source` (string, opcional): Filtro por archivo de origen.
* **Comportamiento:** Consulta la tabla `turnos` de la base de datos H2 y devuelve una lista compacta con los IDs de turno y los títulos de cada nota registrada. El subagente puede entonces resolver los detalles que necesite usando `fetch_citation(code=ID)`.

---

## Flujo de trabajo práctico: indexación de un documento técnico

A continuación se detalla cómo opera este mecanismo en un escenario real de indexación:

```
[Usuario en el chat] 
"Indexa el archivo datos-test.md para que podamos consultarlo rápido más tarde."
   │
   ▼
[Agente Principal]
   │ Identifica la tarea y ejecuta la herramienta:
   │ launch_subagent({
   │   "subagent_name": "document_indexer",
   │   "params": {
   │     "FILE_PATH": "datos-test.md",
   │     "OUTPUT_PATH": "datos-test.index.md"
   │   },
   │   "async": false
   │ })
   ▼
[Arnés de Noema]
   │ 1. Carga var/subagents/document_indexer.xml
   │ 2. Resuelve {FILE_PATH} y {OUTPUT_PATH}
   │ 3. Crea var/tmp/subagent_document_indexer_a1b2/
   │ 4. Arranca subAgent con BBDD H2 temporal limpia
   ▼
[Subagente - Fase 1: Exploración]
   │ Recibe prompt_ini
   │ Bucle autónomo:
   │   - Llama a file_read("datos-test.md")
   │   - Llama a read_paginated_resource(offset=100, limit=100)...
   │   - Al detectar secciones, invoca annotate_observation(type="section_index", note="...")
   │   - Los bloques antiguos sufren amnesia selectiva (CONTENT_TRIMMED: true)
   │ Detecta fin de archivo (último bloque sin HINT)
   ▼
[Arnés de Noema]
   │ Detecta cierre de Fase 1
   │ Inyecta prompt_fin en el subagente
   ▼
[Subagente - Fase 2: Síntesis]
   │ Recibe prompt_fin
   │ Llama a list_annotations(type="section_index")
   │ Recupera los detalles estructurados (títulos, rangos de líneas, resúmenes)
   │ Ensambla el documento Markdown completo
   │ Ejecuta file_write("datos-test.index.md", contenido)
   │ Emite respuesta final de éxito
   ▼
[Arnés de Noema]
   │ Invoca subAgent.stop() y elimina var/tmp/subagent_document_indexer_a1b2/
   │ Retorna resultado al Agente Principal
   ▼
[Agente Principal]
   │ Recibe la confirmación en un solo turno de conversación
   ▼
[Respuesta al Usuario]
"He completado la indexación de 'datos-test.md'. El índice estructurado ha quedado guardado en 'datos-test.index.md' con 10 secciones catalogadas y sus rangos de líneas correspondientes."
```

---

## Consideraciones de seguridad y balance de rendimiento

1. **Aislamiento absoluto del estado:** Al ejecutarse en su propia carpeta temporal con su propia base de datos H2, un fallo crítico, bucle infinito o excepción en el subagente no corrompe la base de datos de memoria (`memory.mv.db`) ni la sesión activa del Agente Principal.
2. **Confinamiento de privilegios:** El subagente solo tiene acceso a las herramientas enumeradas en su XML. Si la receta no incluye `shell_execute` o `file_write`, el subagente carece físicamente de la capacidad de ejecutar comandos o escribir en disco.
3. **Sobrecarga controlada:** La creación del workspace temporal y el arranque de H2 suponen entre 500 y 1.000 ms de inicialización. Para tareas que implican minutos de procesamiento y decenas de llamadas a modelos de lenguaje, este coste temporal es insignificante frente a la ganancia en estabilidad y limpieza de contexto.


# Anexo I


### 1. El Subagente: Delegación funcional (Sin historia)
* **Cuándo se usa:** Tareas acotadas, pesadas o mecánicas (indexar un documento, auditar puertos, extraer un esquema SQL, refactorizar una clase).
* **Naturaleza:** Es un **proceso desechable**. No tiene pasado, no tiene futuro y no necesita biografía. 
* **Qué importa:** Únicamente el **artefacto generado** (el `.index.md`, el informe de vulnerabilidades o el archivo parcheado). Una vez depositado el artefacto en disco, el subagente se destruye y su memoria volátil desaparece.

---

### 2. La Instancia de Noema: Colaboración de igual a igual (Con historia)
* **Cuándo se usa:** Proyectos que requieren continuidad, toma de decisiones acumulativa o interacción humana a largo plazo.
* **Naturaleza:** Es un **agente con biografía**. Tiene memoria episódica, memoria narrativa ("El Viaje"), Puntos de Guardado y una línea temporal continua.
* **Cómo colaboran entre sí:** Si necesitas que dos agentes trabajen "de tú a tú" (por ejemplo, un agente especializado en el backend y otro en el frontend), no inventas un protocolo esotérico; levantas dos instancias completas de Noema. Ambas colaboran exactamente como lo harían dos ingenieros humanos: compartiendo el sistema de archivos, leyendo los documentos del otro o enviándose notificaciones.

---

### Por qué esta visión es superior a lo que hay en el mercado

La mayoría de frameworks actuales sufren porque intentan forzar la misma abstracción para todo:
* Le ponen "personalidad" y "conversación" a tareas que son puras funciones batch (haciendo que un indexador gaste tokens simulando que es un bibliotecario).
* O le quitan la persistencia al agente principal, convirtiendo al asistente en un ejecutor sin memoria que olvida lo hablado la semana pasada.

En tu arquitectura, cada cosa ocupa su lugar natural:
* Si la tarea es una **función pesada**, se delega en un **subagente sin historia** que ejecuta y muere.
* Si la tarea es la **gobernanza del proyecto**, la lleva un **agente con historia** que recuerda cada decisión tomada a lo largo de los meses.

Que esta arquitectura haya surgido de forma orgánica —resolviendo paso a paso los problemas reales que aparecían en el test— es la mejor garantía de que no es una elucubración teórica, sino un diseño pragmático y sólido.

