
# Especificación técnica del sistema de indexación delegada mediante agentes efímeros

## Propósito y justificación arquitectónica

El análisis de documentos de gran volumen (más de 5.000 líneas) en un sistema conversacional plantea un dilema entre dos extremos ineficientes:

1. **La lectura secuencial exhaustiva:** Obliga al agente principal a iterar decenas de veces sobre herramientas de lectura paginada (`file_read` / `read_paginated_resource`). Aunque permite una asimilación profunda, consume cientos de llamadas a la API, satura la memoria de trabajo y fuerza múltiples compactaciones de contexto para resolver lo que a menudo es una consulta puntual.
2. **El RAG vectorial tradicional:** Fragmenta el texto en trozos (*chunks*) descontextualizados y busca por similitud coseno. Este enfoque pierde la jerarquía del documento, es incapaz de evaluar la estructura global y falla cuando la respuesta depende de entender la relación entre secciones completas.

El **sistema de indexación delegada** resuelve este dilema mediante un **patrón de trabajador efímero (*worker agent*)**. En lugar de forzar al agente principal a leer el documento o depender de una base de datos vectorial rígida, el sistema delega la lectura en un agente hijo especializado. Este sub-agente recorre el documento bloque a bloque, extrae su estructura jerárquica con precisión de líneas y genera un artefacto persistente: un **mapa cartográfico en Markdown (`.index.md`)**.

A partir de ese momento, el agente principal pasa de una búsqueda a ciegas de coste lineal $O(N)$ a una navegación en dos pasos de coste constante $O(1)$: consultar el índice ligero ($\sim 500$ tokens) y saltar directamente al bloque de líneas relevante.

---

## Arquitectura general del sistema

El mecanismo desacopla la tarea de lectura del hilo principal de conversación mediante tres capas:

```
┌─────────────────────────────────────────────────────────────────┐
│                       AGENTE PRINCIPAL                          │
│  - Mantiene la conversación con el usuario                      │
│  - Invoca la herramienta: index_document(path)                  │
│  - Recibe el resultado: "Índice generado en archivo.index.md"   │
└────────────────────────────────┬────────────────────────────────┘
                                 │ Invoca herramienta
                                 ▼
┌─────────────────────────────────────────────────────────────────┐
│                HERRAMIENTA IndexDocumentTool                    │
│  1. Crea workspace temporal en var/tmp/indexer_UUID             │
│  2. Instancia un AgentImpl hijo con configuración monotarea    │
│  3. Lanza el bucle de lectura del agente hijo                   │
│  4. Recopila las secciones registradas                          │
│  5. Escribe el archivo .index.md atómicamente                   │
│  6. Destruye el agente hijo y limpia el workspace temporal      │
└────────────────────────────────┬────────────────────────────────┘
                                 │ Orquesta
                                 ▼
┌─────────────────────────────────────────────────────────────────┐
│                    AGENTE HIJO (Indexador)                      │
│  - System Prompt: Monotarea (solo catalogar secciones)          │
│  - Herramienta 1: read_paginated_resource (lectura por bloques) │
│  - Herramienta 2: record_section_index (registro de secciones)  │
│  - Sesión y memoria aisladas (cero contaminación al principal)  │
└─────────────────────────────────────────────────────────────────┘
```

---

## Anatomía del agente hijo

El agente hijo es una instancia real de `AgentImpl`, pero configurada con un perfil mínimo de recursos para maximizar la velocidad y reducir el coste computacional.

### 1. El prompt de sistema del indexador

El sub-agente no carga módulos de biografía, entorno, protocolos de código ni reglas de interacción humana. Su prompt de sistema es una instrucción algorítmica pura:

```markdown
# PROPÓSITO
Eres un indexador técnico de documentos. Tu única función es leer el archivo asignado de principio a fin y generar su tabla de contenidos estructurada.

# PROTOCOLO DE OPERACIÓN
1. Lee el archivo bloque a bloque utilizando la herramienta 'read_paginated_resource' siguiendo los parámetros indicados en cada campo 'HINT'.
2. Cada vez que identifiques el inicio de un nuevo capítulo, sección, servicio o cambio temático significativo:
   - Identifica el título de la sección.
   - Determina el rango de líneas basándote en la cabecera LINE_RANGE del bloque actual.
   - Redacta un resumen de 1 a 2 frases con los conceptos esenciales explicados en esa sección.
   - Extrae de 3 a 5 etiquetas técnicas representativas.
   - Invoca OBLIGATORIAMENTE la herramienta 'record_section_index'.
3. Continúa la lectura hasta que la cabecera no incluya el campo 'HINT', lo que indicará el final del archivo.
4. Cuando hayas leído el último bloque y registrado su sección correspondiente, emite tu respuesta final confirmando la finalización de la lectura.
```

### 2. Catálogo restringido de herramientas

El agente hijo opera bajo una lista blanca estricta con solo dos herramientas activas:

1.  **`read_paginated_resource` (`AbstractPaginatedAgentTool`):** Sirve los bloques de texto (por ejemplo, de 100 o 250 líneas) acompañados de su cabecera determinista (`LINE_RANGE`, `TOTAL_LINES`, `HINT`).
2.  **`record_section_index` (`RecordSectionIndexTool`):** Registra cada hito estructural detectado durante la lectura.

---

## La herramienta `record_section_index` y los estratos de memoria

La herramienta `record_section_index` no es solo un recolector de datos; está diseñada para encajar en la física de los cuatro estratos de memoria de Noema.

### 1. Especificación técnica de la herramienta

```java
public class RecordSectionIndexTool extends AbstractAgentTool {

    public static final String TOOL_NAME = "record_section_index";

    // Contenedor en memoria donde se van acumulando las entradas durante el trabajo del sub-agente
    private final List<SectionIndexEntry> recordedSections = new ArrayList<>();

    public record SectionIndexEntry(
        String title,
        int startLine,
        int endLine,
        String summary,
        List<String> tags,
        String resourceId
    ) {}

    ...
}
```

**Parámetros expuestos al modelo:**
*   `title` (obligatorio, string): Nombre o encabezado de la sección identificada.
*   `start_line` (obligatorio, entero): Línea inicial donde comienza la sección (extraída del `LINE_RANGE`).
*   `end_line` (obligatorio, entero): Línea final estimada donde concluye la sección.
*   `summary` (obligatorio, string): Síntesis de 1 o 2 frases del contenido técnico de la sección.
*   `tags` (obligatorio, array de strings): Palabras clave para indexación y búsqueda rápida.
*   `resource_id` (obligatorio, string): Identificador del recurso paginado leído (`user://...`).

### 2. Comportamiento en los cuatro estratos de memoria

Para que el sub-agente pueda procesar documentos masivos (de 20.000 a 50.000 líneas) sin perder el hilo, `record_section_index` recibe el mismo tratamiento de "conocimiento protegido" que `annotate_observation`:

*   **Estrato 4 (Memoria Proyectada):** Inmunidad absoluta frente a la poda. En `AbstractAgentTool`, el método `trimResult()` devuelve `null` para esta herramienta. Aunque los bloques de texto bruto leídos en los turnos 1 al 100 se hayan reducido a `CONTENT_TRIMMED: true`, las llamadas a `record_section_index` permanecen visibles en el contexto activo. El sub-agente sabe en todo momento qué secciones ya ha catalogado.
*   **Estrato 3 (Memoria Reciente / Sesión):** Cada invocación se añade a `session.messages` y se asocia a su `Turn` correspondiente mediante backfill.
*   **Estrato 2 (Memoria Compactada):** Si el documento es tan extenso que la sesión del sub-agente supera los 40 turnos y dispara `performCompaction()`, el modelo de memoria (`MemoryService`) utiliza las entradas de `record_section_index` como hitos de progreso en "El Viaje": *"El indexador ha procesado hasta la línea 12.000, catalogando 15 secciones..."*, permitiendo que la lectura continúe sin desorientación.
*   **Estrato 1 (Memoria Episódica / BBDD):** Se persiste como un turno estructurado en la tabla `turnos` de la base de datos H2 temporal.

---

## El artefacto resultante: el catálogo en Markdown (`.index.md`)

Cuando el agente hijo termina de leer el último bloque, la herramienta orquestadora toma la lista `recordedSections` acumulada en memoria y escribe un archivo Markdown estructurado junto al documento original (por ejemplo, `datos-test.index.md`).

### Estructura del fichero generado

```markdown
# Índice estructurado de contenidos: datos-test.md
* **Total de líneas:** 5366
* **Fecha de indexación:** 2026-08-19 20:00:00
* **Total de secciones identificadas:** 10

---

## 1. Servicio de Memoria (MemoryService)
* **Rango de líneas:** 1 - 650 (Bloques 0 a 6)
* **Etiquetas:** `memoria`, `compactacion_narrativa`, `checkpoints`, `citas_trazables`, `source_of_truth`
* **Resumen:** Describe la arquitectura de memoria a largo plazo basada en compactación narrativa y puntos de guardado. Detalla los componentes MemoryServiceImpl, SourceOfTruth en H2, el protocolo de citas deterministas y las herramientas fetch_citation y annotate_observation.

## 2. Especificación técnica de SensorsService
* **Rango de líneas:** 651 - 1500 (Bloques 6 a 15)
* **Etiquetas:** `sensores`, `SNA`, `fusion_maestra`, `sensor_nature`, `concurrencia`
* **Resumen:** Detalla el sistema nervioso autónomo de Noema. Explica el orquestador SensorsServiceImpl, el protocolo putEvent con sensorLock, la taxonomía de eventos (Discrete, Mergeable, Aggregate, State, User) y el arbitraje cronológico de entrega.

## 3. Seguridad y Control de Acceso (AgentAccessControl)
* **Rango de líneas:** 1501 - 2100 (Bloques 15 a 21)
* **Etiquetas:** `seguridad`, `sandbox`, `confirmacion_humana`, `RCS_backup`, `firejail`
* **Resumen:** Define el modelo de permisos en 4 modos, el sandbox de archivos con normalización resolvePath, el sistema de copias de seguridad automáticas con JavaRCS y la supervisión humana bloqueante para operaciones destructivas.

## 4. Servicio de Planificación (SchedulerService)
* **Rango de líneas:** 2101 - 2600 (Bloques 21 a 26)
* **Etiquetas:** `scheduler`, `alarmas`, `H2_scheduler`, `natty_parser`, `temporizador`
* **Resumen:** Explica el sistema de alarmas diferidas persistentes en la tabla SCHEDULER de H2 con IDs formato ALARM-<num>. Describe el ejecutor mono-hilo y el uso del parser Natty en inglés.

...
```

### Alineación con la lectura por bloques
Nótese que el índice no solo guarda números de línea exactos, sino que **indica los bloques de lectura correspondientes**. Esto permite que cualquier herramienta que trabaje por offsets (como `read_paginated_resource` con `limit: 100`) sepa exactamente qué offset solicitar sin necesidad de recalcular índices.

---

## Ciclo de vida completo del proceso de indexación

El flujo de ejecución de principio a fin sigue estos pasos:

```
[Usuario] "Indexa el manual de arquitectura datos-test.md"
   │
   ▼
[Agente Principal]
   │ Detecta necesidad de indexar
   │ Invoca: index_document(path: "datos-test.md")
   ▼
[IndexDocumentTool]
   │ 1. Crea directorio temporal: var/tmp/indexer_worker_01/
   │ 2. Instancia AgentImpl hijo (configurado con modelo rápido/económico)
   │ 3. Instala herramientas: FileReadTool (limit=100) + RecordSectionIndexTool
   │ 4. Envía prompt inicial al sub-agente
   ▼
[Bucle del Agente Hijo]
   │ ┌─► Lee bloque N vía read_paginated_resource
   │ │   Detecta cambio de sección
   │ │   Ejecuta record_section_index(title, start, end, summary, tags)
   │ └── Recibe HINT del siguiente bloque (offset += 100)
   │     ¿Fin de archivo?
   │        NO ──► Repite bucle (aplica amnesia selectiva en bloques viejos)
   │        SÍ  ──► Emite respuesta final de cierre
   ▼
[IndexDocumentTool]
   │ 5. Recopila las entradas de RecordSectionIndexTool
   │ 6. Escribe atómicamente 'datos-test.index.md' en el workspace del usuario
   │ 7. Invoca childAgent.stop() y elimina var/tmp/indexer_worker_01/
   │ 8. Retorna mensaje de éxito al Agente Principal
   ▼
[Agente Principal]
   │ Recibe confirmación en 1 turno
   ▼
[Usuario] "El documento datos-test.md ha sido indexado con 10 secciones en 'datos-test.index.md'."
```

---

## La fase de consulta: navegación en dos pasos

Una vez generado el índice, el Agente Principal ya no necesita leer las 5.300 líneas cuando el usuario realiza una pregunta sobre el documento.

### Ejemplo de interacción

1.  **Pregunta del usuario:**
    *"¿Qué parser de fechas utiliza el Scheduler de Noema y qué limitaciones tiene?"*
2.  **Paso 1: Consulta del índice ligero ($O(1)$)**
    El agente principal ejecuta:
    ```json
    file_read({"path": "datos-test.index.md"})
    ```
    Recibe el Markdown de 500 tokens. Examina las secciones y localiza:
    > *Sección 4: SchedulerService (Líneas 2101 a 2600, Bloques 21 a 26).*
3.  **Paso 2: Salto directo al bloque relevante ($O(1)$)**
    El agente principal no lee los bloques 0 al 20. Invoca directamente:
    ```json
    read_paginated_resource({
      "resource_id": "user:///.../datos-test.md",
      "offset": 2100,
      "limit": 500
    })
    ```
4.  **Respuesta al usuario:**
    El agente lee exclusivamente las 500 líneas del `SchedulerService`, extrae que usa el parser **Natty** en inglés con IDs `ALARM-<num>` y formula la respuesta final de forma inmediata.

---

## Gestión de casos límite

1.  **Documentos sin encabezados formales (texto plano o transcripciones):**
    A diferencia de un parser estático basado en `#`, el LLM del sub-agente detecta el cambio de sección por análisis semántico del contenido (identifica cuándo cambia el tema de conversación o la temática del informe).
2.  **Múltiples secciones dentro de un mismo bloque de 100 líneas:**
    Si en un bloque de 100 líneas coinciden el final de una sección y el principio de otra, el modelo puede emitir dos llamadas sucesivas a `record_section_index` dentro del mismo turno, registrando los rangos de líneas correspondientes a cada una.
3.  **Fallo o interrupción del sub-agente:**
    Si el sub-agente falla en mitad de la lectura por un error de red, el archivo `.index.md` no se escribe a medias. La herramienta `IndexDocumentTool` captura la excepción, limpia el workspace temporal y devuelve un mensaje de error limpio al Agente Principal sin corromper el estado del proyecto.
    