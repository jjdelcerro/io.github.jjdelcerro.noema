
# Especificación Técnica: El Lector de Documentos

El **Lector** es un proceso desacoplado del `ConversationAgent` cuya misión es transformar un documento extenso y no estructurado en un **Mapa de Conocimiento Jerárquico** persistido en un formato binario de acceso aleatorio.

## 1. El Flujo de Procesamiento (Las tres pasadas)

Para superar las limitaciones de contexto y garantizar la coherencia, el Lector opera en tres fases secuenciales:

### Pasada 1: Descubrimiento de Estructura (Serial)
*   **Mecanismo:** Ventana deslizante sobre el documento original.
*   **Protocolo:** Se envía al LLM un fragmento del documento en formato **CSV** (número de línea y texto) junto con el **Esquema Acumulado** (lo que se ha encontrado hasta ahora).
*   **Solape:** Las ventanas tienen un solape (ej. 25 líneas) para mantener la continuidad semántica y no cortar títulos.
*   **Salida:** Un árbol jerárquico (TOC) donde cada nodo tiene un título, un nivel de profundidad y un rango de líneas (inicio/fin).
*   **Nota:** Esta fase es estrictamente serial para garantizar que el LLM sepa siempre en qué "rama" del árbol se encuentra.

### Pasada 2: Digestión de Contenido (Paralelizable)
*   **Mecanismo:** Procesamiento independiente de cada nodo identificado en la Pasada 1.
*   **Protocolo:**
    *   **Nodos Pequeños:** Si el texto bruto de la sección cabe en el contexto, se genera un resumen directo.
    *   **Nodos Grandes:** Se aplica el **Protocolo de Compactación Telescópica** (basado en el `MemoryManager`). Se resume el bloque A, se fusiona con el bloque B, y así hasta destilar el resumen de la sección completa.
*   **Paralelismo:** Al estar los rangos de líneas ya definidos, Java puede lanzar múltiples hilos (Virtual Threads) para resumir distintos capítulos simultáneamente.

### Pasada 3: Categorización y Síntesis Global
*   **Mecanismo:** Agregación jerárquica (Resumen de resúmenes).
*   **Categorización:** El LLM asigna metadatos a cada nodo basados en estándares (como **CDU** o **Eurovoc**) y etiquetas temáticas.
*   **Resumen General:** Se genera la "esencia" del documento procesando únicamente los resúmenes de nivel superior. Es un filtrado de ruido masivo que preserva la intención del autor.


## 2. Almacenamiento: El Fichero de Conocimiento (`.ckf`)

El resultado no se guarda como un JSON masivo, sino como un fichero binario optimizado para el acceso aleatorio desde el `ConversationAgent`.

### Estructura del Fichero:
1.  **Cabecera (Header):**
    *   *Magic Number* y Versión del formato.
    *   Punteros (offsets) a las secciones de JSON, Embeddings y Texto.
    *   Referencia al documento original (Path + Hash de integridad).
2.  **Sección de Índices (JSON):**
    *   Contiene el esquema jerárquico completo.
    *   Cada nodo incluye: Título, Resumen, Categorías y **Punteros Físicos** (offset y longitud) al documento original.
    *   *Importante:* No contiene el texto bruto, lo que permite cargar el esquema entero en memoria sin apenas impacto.
3.  **Sección de Embeddings:**
    *   Bloque binario con los vectores generados localmente (ONNX) para cada nodo, permitiendo búsquedas semánticas rápidas dentro del libro.


## 3. Integración con el Agente Conversacional

El `ConversationAgent` interactúa con el documento mediante un modelo de "percepción y herramientas":

*   **Notificación Proactiva:** Al terminar la lectura, el Lector inyecta un evento en el sistema (`putEvent`). El Agente percibe: *"Nuevo documento disponible: 'Manual de Topografía' [ID:DOC-001]"*.
*   **Navegación Determinista:** El Agente dispone de herramientas para "tocar" el libro sin leerlo entero:
    *   `doc_explorer`: Para ver el esquema y resúmenes de nivel superior.
    *   `doc_search`: Para buscar por significado en la sección de embeddings del fichero binario.
    *   `doc_read_leaf`: Para recuperar el texto bruto de una sección específica usando los punteros del binario.

## 4. El Rol de Java vs. El LLM

*   **El LLM:** Actúa como el **intérprete semántico**. Identifica dónde hay un título, resume la idea y clasifica el tema.
*   **Java:** Actúa como el **ingeniero de sistemas**. Gestiona la ventana deslizante, calcula los offsets de bytes reales en el fichero, genera los embeddings locales y construye la estructura binaria final.

