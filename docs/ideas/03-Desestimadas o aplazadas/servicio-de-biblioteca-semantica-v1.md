
# Especificación Técnica: Servicio de Biblioteca Semántica ("Bibliotheca")

**Versión del Documento:** 1.0
**Estado:** Borrador de Arquitectura
**Dependencias:** Java 21, LangChain4j, H2, ONNX Runtime, MCP Spec.

## 1. Visión General y Propósito

### 1.1. Diferenciación de Memorias
En la arquitectura cognitiva de un agente de IA avanzado, es crítico distinguir entre dos tipos de almacenamiento para evitar la degradación del rendimiento y la "locura" narrativa:

*   **Memoria Episódica (El Agente / DHMA):** Es autobiográfica, mutable y narrativa. Registra "qué ha pasado", "qué hemos decidido" y la evolución de la interacción con el usuario. Su enemigo es el olvido. Se gestiona mediante *Puntos de Guardado* y *Turnos*.
*   **Memoria Semántica (La Biblioteca):** Es enciclopédica, inmutable (read-only durante la consulta) y estructurada. Contiene "conocimiento del mundo" (manuales, libros, normas). Su enemigo es la alucinación y la contaminación del contexto.

**Bibliotheca** es el módulo encargado exclusivamente de la **Memoria Semántica**. Su propósito es ingerir grandes volúmenes de documentación, estructurarlos y permitir búsquedas de precisión quirúrgica, entregando al agente solo los fragmentos necesarios para responder, sin saturar su ventana de contexto.

### 1.2. Principios de Diseño: Arquitectura Orientada a Servicios
Para garantizar la escalabilidad y la interoperabilidad, Bibliotheca no se diseña como una librería interna del agente, sino como un **Servicio Independiente (Daemon)**.

*   **Desacoplamiento:** El proceso de ingesta (pesado en CPU por el cálculo de vectores y uso de LLMs locales) no bloquea el bucle de razonamiento del agente.
*   **Interoperabilidad Universal (MCP):** El servicio expone su funcionalidad a través del **Model Context Protocol (MCP)**. Esto convierte a la biblioteca en una *commodity* del sistema: puede ser utilizada por el agente DHMA, pero también por un IDE (VS Code), un cliente de chat genérico (Claude Desktop) o cualquier herramienta compatible con MCP.
*   **Privacidad y Autonomía:** Todo el procesamiento (Embeddings ONNX, Clasificación con SLM) ocurre localmente. Los datos nunca salen de la máquina para ser indexados.

## 2. Arquitectura de Datos: El Formato `.bidx`

Para resolver el problema de latencia y consumo de memoria al manejar cientos de miles de vectores, se descarta el uso de JSON para el almacenamiento de índices de documentos. Se define un formato propio: **Binary Index (`.bidx`)**.

### 2.1. Justificación del Formato Híbrido
El formato `.bidx` es un híbrido que combina la velocidad del acceso binario para datos masivos con la flexibilidad del texto para metadatos estructurales.
*   **Eficiencia:** Los embeddings se guardan como arrays de floats contiguos, permitiendo el uso de **Memory Mapping (`MappedByteBuffer`)**. Esto permite al sistema operativo gestionar la carga en RAM bajo demanda, haciendo que la búsqueda vectorial sea casi instantánea y con huella de memoria mínima en la JVM.
*   **Estructura:** La jerarquía del documento se almacena como un JSON embebido, facilitando el parseo de árboles complejos sin necesidad de punteros binarios difíciles de mantener.

### 2.2. Estructura Física del Fichero
El fichero se compone de cuatro secciones contiguas:

#### A. Cabecera (Header) - Tamaño Fijo
Actúa como el mapa de direcciones del archivo.
*   `MAGIC` (4 bytes): Firma del archivo (ej: `0x42494458` -> "BIDX").
*   `VERSION` (2 bytes): Versión del formato (ej: 1).
*   `DOC_ID` (40 bytes): Hash SHA-1 o MD5 del documento original para control de integridad.
*   `VECTOR_DIMENSION` (2 bytes): Dimensión de los embeddings (ej: 384 para `all-minilm-l6-v2`).
*   `OFFSET_GLOBAL_SUMMARY` (8 bytes): Puntero al inicio del resumen.
*   `OFFSET_SECTIONS_TABLE` (8 bytes): Puntero al inicio del JSON de estructura.
*   `OFFSET_VECTORS` (8 bytes): Puntero al inicio del blob de vectores.

#### B. El resumen (GLOBAL_SUMMARY)
Contiene el `GLOBAL_SUMMARY` codificado en UTF-8, precedido por 8 bytes con su longitud

#### C. Tabla de Secciones (Structure Table)
Un bloque de texto UTF-8 (precedido por 8 bytes de longitud) que contiene un **JSON**. Este JSON describe el árbol de contenidos (TOC) y mapea cada sección a un rango de vectores.
*   *Esquema:*
    ```json
    [
      {
        "title": "1. Introducción",
        "level": 1,
        "category": "Tecnología",
        "start_vector_idx": 0,   // Índice en el bloque de vectores
        "end_vector_idx": 15,
        "children": [...]
      }
    ]
    ```

>
> Notas: 
> * Hay que definir completamente este json.
> * Valorar si aportaria o no que en lugar de una categoria fuese una lista de categorias.
>
    
#### D. Bloque de Vectores (Vector Blob)
La sección más grande. Datos binarios crudos.
*   `VECTOR_COUNT` (8 bytes): Número total de vectores.
*   `VECTOR_BYTE_SIZE` (4 bytes): Tamaño en bytes de un vector.
*   `DATA`: Secuencia ininterrumpida de floats (`IEEE 754`).
    *   *Cálculo de Offset:* Para leer el vector `N`, se salta a `OFFSET_VECTORS + 12 + (N * VECTOR_BYTE_SIZE)`.

## 3. El Catálogo Maestro (H2)

Dado que no es viable abrir todos los archivos `.bidx` para cada consulta, se utiliza una base de datos **H2 en modo embebido** como "Catálogo de Fichas" para realizar un filtrado previo (Coarse-grained retrieval).

En caso de que el sistema escale a un numero grande de documentos, se puede evolucionar directamente a un postgresql+pg_vector, que puede soportar sin problemas tablas "grandes" con indices sobre embeddings de forma nativa, permitiendo unir en una sola busqueda las buequeda por embedding con la de metadatos.

En cuanto a H2, estaria bien añadir las funciones de busqueda por similitud a la BBDD para facilitar las busquedas y que se encargue la BBDD de cachear las cosas como toca.

### 3.1. Tabla `library_catalog`
Esta tabla permite al agente decidir *qué* documentos merecen ser investigados.

| Campo | Tipo | Descripción |
| :--- | :--- | :--- |
| `uuid` | UUID | Identificador único del recurso en la biblioteca. |
| `filepath` | VARCHAR | Ruta relativa al fichero `.bidx`. |
| `original_path` | VARCHAR | Ruta al documento fuente (PDF/TXT). |
| `doc_hash` | CHAR(40) | Hash para detectar cambios en el fuente y reindexar. |
| `title` | VARCHAR | Título extraído del documento. |
| `category` | VARCHAR | **Taxonomía:** Categoría principal asignada por el LLM (ej: "Ingeniería", "Legal"). Permite filtrado SQL rápido. |
| `global_summary` | CLOB | Resumen ejecutivo del documento. |
| `meta_embedding` | BLOB | **Vector Representativo:** Embedding calculado sobre la concatenación de `Título + Resumen`. |

### 3.2. Estrategia de Búsqueda
La búsqueda en el catálogo combina técnicas SQL y vectoriales:
1.  **Filtrado Taxonómico (SQL):** Si la intención del usuario es clara, se filtra por `category`.
2.  **Búsqueda Semántica (Vector):** Se compara el vector de la consulta con `meta_embedding`. Esto encuentra documentos que *tratan sobre* el tema, no necesariamente que contengan la palabra exacta.

### 3.3 Taxonomías

No queremos que el LLM se invente etiquetas creativas (hoy dice "Informática", mañana "Computación", pasado "Tech"). Necesitamos un **Vocabulario Controlado**.

Tenemos dos opciones estándares:
1.  **Dewey Decimal Classification (DDC) Simplificada:** (000 General, 500 Ciencias, 600 Tecnología...). Es universal, pero quizás demasiado abstracta.
2.  **Taxonomía Personalizada:** Una lista JSON que tú le das al sistema (ej: `["Ingeniería/Software", "Ingeniería/Mecánica", "Finanzas", "Legal", "Ocio"]`).

Los LLMs pequeños son sorprendentemente buenos en **Clasificación Zero-Shot**. Tú les das el texto y la lista de categorías posibles, y ellos eligen la mejor.

Cuando se genera el *resumen global* del documento, haces una llamada extra al Mini LLM:

**Prompt del Sistema:**
> "Eres un bibliotecario experto. Clasifica el siguiente texto en UNA de las siguientes categorías principales: [Lista: Tecnología, Historia, Ciencia, Arte, Administración...]. Responde SOLO con el nombre de la categoría."

**Input:** El Resumen Global.
**Output:** "Tecnología".

Luego, guardas ese string `"Tecnología"` en una columna `category` de tu tabla `library_catalog` en H2.

La idea propuesta: **asignar temas a secciones individuales**.
Un manual de "Desarrollo de Videojuegos" puede tener:
*   Capítulo 1: Matemáticas (Vectores, Matrices). -> Tema: **Ciencia/Matemáticas**.
*   Capítulo 2: Renderizado (GPU). -> Tema: **Tecnología/Hardware**.
*   Capítulo 3: Guion y Narrativa. -> Tema: **Arte/Literatura**.

Si buscas "Matrices", el sistema sabrá priorizar el Capítulo 1 aunque el libro sea de Informática.

Ahora el sistema tiene la capacidad de filtrado híbrido real.

El flujo de búsqueda cambia así:

1.  **Paso 1 (Intención):** El usuario pregunta: *"¿Cómo se multiplican matrices en videojuegos?"*.
2.  **Paso 2 (Clasificación de la Pregunta):** El Agente (usando el LLM rápido) clasifica la pregunta del usuario con la misma taxonomía.
    *   *Agente:* "Esta pregunta parece de la categoría **Matemáticas** o **Tecnología**".
3.  **Paso 3 (Poda SQL):**
    *   El Agente consulta H2: `SELECT * FROM library_catalog`.
    *   *Optimización:* Puede priorizar documentos cuya categoría global sea esa, O BIEN, si has indexado las secciones en H2 (opcional), filtrar solo esas.
4.  **Paso 4 (Búsqueda Vectorial):** Busca por similitud solo en los candidatos lógicos.


Resumiendo utilizar el Mini LLM como un **"Clasificador Taxonómico"** es uno de los usos más rentables (coste vs beneficio) que existen.
*   Es rapidísimo (clasificar un resumen son milisegundos).
*   Aporta un orden humano a la entropía de los vectores.
*   Permite búsquedas facetadas ("Búscame X pero solo en documentos Legales").

## 4. Pipeline de Ingesta (Hilo Indexador)

Este proceso corre en un hilo de fondo (*Background Thread*) con baja prioridad. Su función es transformar documentos crudos en archivos `.bidx` e inserciones en H2.

### 4.1. El Rol del Mini-LLM Local (Jlama/Ollama)

Modelos que ofrecen el mejor equilibrio entre rendimiento y requisitos de hardware (fáciles de mover en portátiles o equipos de sobremesa normales).

| Modelo | Parámetros | Ventana de Contexto | Descripción Clave |
| :--- | :--- | :--- | :--- |
| **Qwen 2.5** | 0.5B / 1.5B / 3B / 7B | 128k (hasta 1M*) | Actualmente el "rey" de los modelos abiertos. Excelente en lógica, matemáticas y código. Muy ligero. |
| **Llama 3.2** | 1B / 3B | 128k | Optimizado por Meta para dispositivos "edge" (móviles/portátiles). Muy rápido y capaz para su tamaño. |
| **Microsoft Phi-3.5 Mini** | 3.8B | 128k | Sorprendentemente inteligente para ser tan pequeño. Rivaliza con modelos el doble de grandes en razonamiento. |
| **Gemma 2** | 2B / 9B | 8k | Modelo de Google. El de 9B es muy potente pero pesado; el de 2B es ideal para equipos muy modestos. |
| **Mistral NeMo** | 12B | 128k | Colaboración Mistral-NVIDIA. Un poco más pesado, pero muy robusto y versátil. |
| **SmolLM2** | 135M / 360M / 1.7B | 2k - 8k | Extremadamente pequeños. Ideales para teléfonos o tareas muy específicas y rápidas. |

*\*Nota: Existen versiones específicas de Qwen 2.5 diseñadas para contextos de 1 millón de tokens, pero requieren más RAM.*

**Recomendaciones según hardware:**

*   **< 8GB de RAM:** Ve a por **Llama 3.2 (3B)** o **Qwen 2.5 (3B)**. Son rápidos y manejan contextos largos sin problemas.
*   **8GB - 16GB de RAM:** **Mistral NeMo (12B)** o **Qwen 2.5 (7B)** te darán una inteligencia muy superior.
*   **Para tareas de lectura de documentos largos:** **Phi-3.5 Mini** o **Qwen 2.5** son los mejores gracias a su ventana de 128k nativa y buena capacidad de recuperación de información.



Para ese caso de uso específico (**extracción de estructura, títulos y resúmenes**), el modelo **Qwen 2.5 1.5B** es una elección brillante, incluso mejor que para chat general.

¿Por qué? Porque estas tareas son **mecánicas y de comprensión lectora**, áreas donde Qwen 2.5 destaca mucho sobre otros modelos pequeños (como Llama o Gemma), y al ser 1.5B, la penalización por procesar texto en CPU es mínima.

Sobre **"dárle a comer de una vez" (10k - 50k tokens) a un modelo Mini en CPU**:

*   1. ¿Cabe en la memoria? (Viabilidad Técnica): 
    **Sí.** Modelos modernos como **Qwen 2.5**, **Phi-3.5** o **Llama 3.2** (incluso los pequeños de 1.5B o 3B) soportan ventanas de contexto de **128k tokens**.
    *   Un documento de 50k tokens cabe holgadamente.
    *   En cuanto a RAM: El contexto (KV Cache) para 50k tokens ocupará unos pocos GBs. Si tienes 16GB o incluso 8GB en la máquina donde corre el proceso de fondo, no explotará.

*   2. ¿Cuánto tardará? (El cuello de botella de la CPU):
    Aquí es donde la estrategia de "segundo plano" brilla.
    En una CPU, el tiempo de **"Prefill"** (la lectura y comprensión inicial del prompt antes de generar nada) es lento.
    *   **Para 10k tokens:** Puede tardar entre 1 y 3 minutos en "leerlo".
    *   **Para 50k tokens:** Puede tardar entre 10 y 20 minutos (dependiendo de tu CPU y la velocidad de la RAM) solo en procesar la entrada.

    Como es un proceso en *background*, **esto es aceptable**. Puedes dejarlo corriendo toda la noche barriendo carpetas y, al día siguiente, tener todo indexado. No bloqueas al usuario.

*   3. ¿Será capaz de entenderlo? (El límite cognitivo):
    Aquí está el verdadero riesgo, más que en el hardware.
    Aunque técnicamente le quepan 50k tokens, un modelo "Mini" (1.5B - 3B parámetros) tiene una capacidad de atención limitada.
    *   **Riesgo "Lost in the Middle":** Es posible que el modelo extraiga muy bien el principio y el final del documento, pero olvide o alucine detalles de la parte central (el token 25.000).
    *   **Coherencia:** Para tareas de extracción de estructura (títulos), suelen funcionar bien porque la estructura es repetitiva. Para resúmenes complejos de 50k tokens de una vez, podría "divagar" un poco.

Recomendación para esta arquitectura:

Dado que el proceso es offline/background, sugiero un enfoque **Híbrido Adaptativo**:

1.  **El "Sidecar" (El archivo índice):**
    Plantea que ese archivo (ej: `documento.pdf.json` o `.index`) contenga no solo vectores, sino una estructura rica generada por el LLM:
    *   **Esqueleto:** Árbol de títulos y subtítulos.
    *   **Resumen Ejecutivo:** Del documento entero.
    *   **Entidades:** Lista de conceptos clave mencionados.

2.  **Estrategia de Ingesta:**
    *   **Documentos < 20k tokens:** Pásaselo entero de golpe. El modelo tendrá una visión global perfecta y tardará poco.
    *   **Documentos > 20k tokens:** Aunque podrías pasarlo entero, te recomiendo partirlo en **bloques lógicos grandes** (ej: por capítulos o cada 10k tokens).
        *   Procesas cada bloque independientemente para sacar su "sub-índice".
        *   Luego haces una pasada final (muy rápida) con los resúmenes de cada bloque para generar el "índice maestro".

Esto maximiza la calidad de la extracción de los modelos pequeños sin sacrificar la simplicidad de tu arquitectura de "un archivo, un índice".


### 4.2. Flujo de Procesamiento (Paso a Paso)

El pipeline de ingesta no es un simple script de troceado lineal. Se trata de un proceso de **Ingeniería Documental Cognitiva** diseñado para ejecutarse en segundo plano (Background Thread), optimizado para hardware modesto (CPU) pero con capacidad para manejar documentos extensos (10k - 50k tokens).

#### 4.2.1. Selección del Modelo de Inferencia (El "Cerebro" Local)
Para las tareas de comprensión lectora, extracción de estructura y resumen, se utiliza un **SLM (Small Language Model)** ejecutado localmente vía Jlama o Ollama.

*   **Modelo Recomendado:** **Qwen 2.5 (1.5B o 3B)**.
*   **Justificación:**
    *   **Ventana de Contexto (128k):** La mayoría de los documentos técnicos del usuario oscilan entre 10k y 50k tokens. Qwen 2.5 permite ingerir el documento entero de una sola vez sin necesidad de fragmentarlo artificialmente, manteniendo la coherencia global.
    *   **Capacidad de Razonamiento:** A pesar de su tamaño reducido, Qwen demuestra una capacidad superior a Llama o Gemma en tareas de extracción estructurada (JSON) y seguimiento de instrucciones complejas.
    *   **Eficiencia en CPU:** Un modelo de 1.5B parámetros tiene una huella de memoria mínima y un tiempo de inferencia aceptable en procesadores modernos sin GPU dedicada.

#### 4.2.2. Extracción Estructural (El Esqueleto)
El primer paso no es leer, sino **cartografiar**. Se pasa el documento completo al modelo con un prompt diseñado para extraer la Tabla de Contenidos (TOC).

*   **El Problema de la Localización:** Los LLMs no saben contar líneas ni bytes de forma fiable. Pedir "número de línea" provoca alucinaciones.
*   **La Solución del "Ancla de Texto":** Se instruye al modelo para que devuelva, por cada sección detectada:
    1.  **Título:** El texto exacto del encabezado.
    2.  **Nivel:** La jerarquía (1, 1.1, 1.1.1).
    3.  **Ancla (Anchor):** Las primeras 5-10 palabras del párrafo inmediatamente posterior al título.
*   **Mapeo Determinista:** Una vez obtenido el JSON del modelo, el código Java realiza búsquedas de texto exacto (`indexOf`) utilizando el Título y el Ancla para localizar los **Byte Offsets** reales de inicio y fin de cada sección. Esto genera un mapa virtual del documento con precisión de byte.

#### 4.2.3. Estrategia de Resumen (Map-Reduce Jerárquico)
Para generar resúmenes de alta calidad, se evita resumir el documento entero de golpe (pérdida de detalle) o resumir párrafo a párrafo (falta de contexto).

*   **Consolidación de Secciones ("Bin Packing"):** El sistema analiza el tamaño de las secciones detectadas. Si hay secciones muy breves (ej: un prólogo de 100 palabras), se agrupan con secciones adyacentes hasta alcanzar un tamaño de bloque óptimo para el modelo (ej: 2.000 tokens).
*   **Resumen por Bloques (Map):** Se invoca al Mini-LLM para generar un resumen denso de cada bloque consolidado.
*   **Resumen Global (Reduce):** Se concatenan los resúmenes de los bloques y se realiza una pasada final con el LLM para generar el **Resumen Ejecutivo Global**. Este resumen se guarda en la cabecera del `.bidx` y en la base de datos H2 para la búsqueda rápida.

#### 4.2.4. Indexación Vectorial (Semantic Chunking)
La generación de embeddings es el paso final. Se utiliza el modelo **ONNX `all-minilm-l6-v2`**, que es extremadamente rápido y ligero, pero tiene una limitación dura de **512 tokens** de entrada.

Para maximizar la precisión de la búsqueda ("Recall"), se aplica una estrategia de **Chunking Semántico Estricto**:

*   **Respeto de Fronteras:** El algoritmo de troceado **NUNCA** cruza los límites de una sección detectada en el paso 2. Un chunk pertenece exclusivamente a una sección. Esto evita que un vector contenga el final de un tema y el principio de otro, lo que "ensuciaría" su significado semántico.
*   **Troceado Recursivo (Recursive Splitter):** Dentro de cada sección, se aplica el algoritmo estándar que intenta dividir primero por párrafos (`\n\n`), luego por líneas y finalmente por frases, buscando fragmentos óptimos de ~300-400 tokens con un solapamiento (overlap) del 10-15%.
*   **Enriquecimiento de Contexto (Context Injection):** Antes de calcular el vector, se inyecta el contexto jerárquico al texto del chunk.
    *   *Texto Original:* "El valor por defecto es 50." (Semánticamente pobre).
    *   *Texto Vectorizado:* "Configuración del Timeout de Red: El valor por defecto es 50." (Semánticamente rico).
    Esto asegura que, aunque el chunk sea un fragmento aislado, el vector resultante "sepa" a qué tema pertenece.


## 5. Arquitectura del Servicio y Protocolo de Acceso

Bibliotheca funciona como un servidor que expone su funcionalidad a través de un protocolo estándar.

### 5.1. El Modelo de Demonio (Daemon)
El servicio implementa un patrón **Productor-Consumidor**:
*   **Hilo Indexador (Producer/Writer):** Monitoriza carpetas (`WatchService`). Cuando trabaja, consume CPU intensiva. Puede pausarse si se detecta alta carga en el sistema.
*   **Hilo Servidor (Consumer/Reader):** Atiende peticiones externas. Es muy ligero gracias al uso de *Memory Mapping* para la lectura. No bloquea al indexador (H2 en modo MVCC permite lecturas mientras se escribe).

### 5.2. Implementación del Servidor MCP
El servicio implementa la especificación **Model Context Protocol (MCP)**. Expone dos herramientas principales ("Tools") que cualquier cliente MCP puede descubrir y usar.

#### Herramienta A: `consult_catalog`
*   **Tipo:** Exploración / Memoria.
*   **Parámetros:** `query` (String), `category_filter` (String, Opcional).
*   **Lógica:** Realiza una búsqueda en la tabla H2.
*   **Retorno:** Lista de candidatos (UUID, Título, Resumen, Score).
*   **Uso:** Permite al agente saber *qué* hay en la biblioteca antes de leer nada.

#### Herramienta B: `investigate_document`
*   **Tipo:** Operacional / Lectura Profunda.
*   **Parámetros:** `doc_uuid` (UUID), `query` (String).
*   **Lógica:**
    1.  Localiza el `.bidx` por UUID.
    2.  Mapea el bloque de vectores en memoria.
    3.  Realiza un barrido vectorial buscando los chunks más relevantes para la `query`.
    4.  Usa la Tabla de Secciones del `.bidx` para dar contexto (ej: "Este texto pertenece a la sección 'Configuración'").
*   **Retorno:** Fragmentos de texto crudo.

### 5.3. Consumo desde el Cliente (DHMA)
El Agente (`ConversationAgent`) ya no instancia clases de biblioteca. En su lugar, incluye un **Cliente MCP**.
*   Al arrancar, conecta con el servicio Bibliotheca (vía Stdio o SSE).
*   Registra las herramientas descubiertas en su `toolDispatcher` automáticamente.
*   Para el Agente, la biblioteca es "otra herramienta más", desacoplando totalmente la implementación.

Clasificacion de las herramienta por su tipo en DHMA:
1. `consult_catalog` como Herramienta de Memoria (Grupo A):
    *   **Lo que devuelve:** Una lista de candidatos (títulos, resúmenes breves, UUIDs). Ej: 5 ítems.
    *   **Tipo de herramienta "Memoria":** Porque representa el **conocimiento disponible**. Para que el `MemoryManager` pueda narrar *por qué* el agente eligió leer el "Manual Avanzado" y no la "Guía Rápida", necesita ver esa lista de opciones en el historial.
    *   **Comportamiento en BD:** Se guarda íntegro (Full Persistence).
    *   **Efecto en "El Viaje":**
        > *"Ante la duda del usuario, el agente revisó su catálogo y **encontró tres documentos relevantes**: A, B y C. Decidió que el C era el más prometedor."*
        (Para escribir esto, el cronista necesita ver los datos de A, B y C).

2. `investigate_document` como Herramienta Operacional (Grupo B):
    *   **Lo que devuelve:** El contenido crudo ("chicha") del documento. Párrafos técnicos, tablas, código.
    *   **Tipo de herramienta "Operacional":** Porque es una acción de **consumo**. Una vez el agente ha leído el dato y ha generado su respuesta al usuario, el texto fuente (el párrafo del manual) ya no aporta valor a la historia de la conversación. Lo que aporta valor es la **respuesta** que dio el agente basada en ese texto.
    *   **Comportamiento en BD:** Se aplica la política de recorte ("Split State"). Si devuelve 10KB, en la BD se guarda `{"status": "read", "bytes": 10240}`, pero en la RAM del agente se mantiene todo para procesarlo.
    *   **Efecto en "El Viaje":**
        > *"El agente **consultó en profundidad el documento C** para extraer los detalles de configuración. Con esa información, explicó al usuario que..."*
        (El cronista no necesita ver el texto técnico del manual para escribir que el agente "lo consultó").

Habra que ajustar la lógica en `ConversationAgent.processTurn` (o en un método auxiliar `classifyTool`) para mapear estas nuevas herramientas a tus tipos de contenido existentes.

Ventaja Adicional: Protección contra "Contaminación Semántica".

Al tratar `investigate_document` como operacional (recortable en BD), proteges tu sistema de búsquedas vectoriales episódicas (`search_full_history`).

Si guardaras el texto completo del manual en la tabla `turnos`:
1.  El usuario busca en el futuro: *"¿Qué me dijiste sobre la configuración?"*.
2.  La búsqueda vectorial en `turnos` encontraría el **texto del manual** que trajiste con la herramienta, en lugar de encontrar la **explicación** que le diste al usuario.
3.  Al recortarlo en la BD, la búsqueda vectorial ignorará el "ruido" del manual y encontrará tu respuesta, que es lo que realmente importa en la memoria episódica.

**Conclusión:** Es la clasificación perfecta para mantener la higiene mental del agente.


## 6. Flujo de Recuperación de Información

Este flujo describe cómo el Agente DHMA utiliza el servicio para responder a una pregunta compleja del usuario, garantizando la higiene de su memoria episódica.

### 6.1. Algoritmo de Búsqueda Jerárquica

1.  **Intención:** Usuario pregunta: *"¿Cómo configuro los Sockets en Java?"*.
2.  **Fase 1: Catálogo (Tool `consult_catalog`):**
    *   El agente busca "Sockets Java".
    *   Bibliotheca consulta H2 y devuelve: *"Documento: Java Network Guide (UUID-123). Resumen: Guía completa de networking..."*.
    *   *Persistencia:* El agente guarda este resultado en su historial. El `MemoryManager` narrará más tarde: *"El agente encontró la guía de redes."*
3.  **Fase 2: Investigación (Tool `investigate_document`):**
    *   El agente decide leerlo: Llama a `investigate_document(UUID-123, "configuración sockets timeout")`.
    *   Bibliotheca escanea el `.bidx`, encuentra los párrafos específicos y los devuelve (ej: 10KB de texto técnico).
4.  **Fase 3: Respuesta y Recorte:**
    *   El agente lee los 10KB en RAM y responde al usuario.
    *   **Política de Recorte (Split State):** Al guardar este turno en la BD del agente (`SourceOfTruth`), se detecta que es una herramienta operacional masiva. Se guarda `{"status": "read_ok", "bytes": 10240}`. El texto técnico NO ensucia la BD del agente.

### 6.2. Prevención de Alucinaciones y Contaminación
*   **Citas Semánticas:** El agente está instruido para usar referencias `{ref:UUID-123}` cuando habla de información externa.
*   **Aislamiento del MemoryManager:** Como el contenido crudo del manual se recorta en la BD, el `MemoryManager` (que lee de la BD) nunca ve los 10KB de texto técnico al compactar. Solo ve que el agente consultó el documento. Esto evita que "El Viaje" se convierta en una copia del manual.

## 7. Roadmap de Implementación

### Fase 1: Core (`.bidx` + H2)
*   Implementar `BidxWriter` y `BidxReader` con `RandomAccessFile`.
*   Implementar esquema H2 `library_catalog`.

### Fase 2: Ingesta (El Cerebro Local)
*   Integrar Jlama/Ollama en el hilo indexador.
*   Implementar la lógica de extracción de estructura y anclaje.

### Fase 3: Servicio y Conexión
*   Envolver la lógica en un Servidor MCP.
*   Integrar el Cliente MCP en el `ConversationAgent`.

