## Servicio de Embeddings (`EmbeddingsService`)

### 1. Introducción: el poder de la representación vectorial

Los modelos de lenguaje no entienden el texto como una secuencia de caracteres; lo transforman en **vectores numéricos** (embeddings) que capturan su significado semántico. Dos textos similares (aunque usen palabras distintas) producirán vectores cercanos en el espacio multidimensional. Esta propiedad es la base de la **búsqueda por similitud semántica**, una capacidad fundamental para un agente que necesita recuperar información relevante de su historial o de documentos sin depender de palabras clave exactas.

Noema integra esta funcionalidad a través de `EmbeddingsService`. Su cometido es doble: por un lado, proporciona la infraestructura para **vectorizar texto** (convertir frases en vectores de números reales); por otro, ofrece herramientas para **comparar vectores** (similitud coseno) y **recuperar los más similares** a una consulta (búsqueda top-K). Todo ello se ejecuta completamente en local, sin llamadas a APIs externas, utilizando un modelo de embeddings ligero y de código abierto.

El servicio es transversal: lo utilizan `SourceOfTruth` para la búsqueda semántica en el historial de conversación (herramienta `search_full_history`) y `DocumentsService` para la búsqueda en los resúmenes de documentos indexados. Sin embeddings, Noema estaría limitado a búsquedas por palabra clave o expresiones regulares, que son mucho menos flexibles.

### 2. Arquitectura general: modelo local y utilidades asociadas

`EmbeddingsService` es un servicio más del agente, registrado con el nombre `"Embeddings"`. Sus componentes principales son:

- **`EmbeddingsServiceImpl`**: implementación concreta. Gestiona la carga del modelo, ofrece métodos de vectorización, serialización y similitud.
- **Modelo de embeddings**: una instancia de `AllMiniLmL6V2EmbeddingModel`, de LangChain4j. Es un modelo de 384 dimensiones, entrenado por sentence-transformers, optimizado para CPU y de tamaño reducido (unos 80 MB en disco).
- **`EmbeddingFilter`**: interfaz que define el contrato para búsquedas top-K. Permite añadir candidatos y recuperar los más similares.
- **`EmbeddingFilterImpl`**: implementación con una cola de prioridad (min-heap) que mantiene los K elementos con mayor similitud a la query.
- **Utilidades de conversión**: métodos `toBytes()` y `fromBytes()` para serializar `float[]` a `byte[]` y viceversa, necesarios para almacenar vectores en las columnas BLOB de H2.

El servicio se inicia en cuanto el agente arranca (su fábrica siempre devuelve `true`). Al hacerlo, carga el modelo en memoria, lo que puede tomar unos segundos la primera vez (la descarga de pesos ocurre automáticamente). Una vez cargado, permanece residente durante toda la sesión.

### 3. El modelo de embeddings: ligero, local y sin API externa

Noema no depende de proveedores externos para los embeddings. La elección recayó en `AllMiniLmL6V2EmbeddingModel` por varias razones:

- **Local**: se ejecuta íntegramente en la JVM, sin necesidad de conexión a internet ni de API keys. Esto garantiza la privacidad de los datos y la portabilidad.
- **Ligero**: produce vectores de 384 dimensiones (frente a las 1536 de OpenAI o las 768 de otros modelos). Suficiente para tareas de similitud semántica moderada, con un consumo de memoria y CPU razonable.
- **Open source**: basado en el modelo `all-MiniLM-L6-v2` de sentence-transformers, con licencia Apache 2.0.
- **Integración sencilla**: LangChain4j proporciona el `EmbeddingModel` listo para usar, sin configuración adicional.

El modelo se instancia en `start()` mediante `new AllMiniLmL6V2EmbeddingModel()`. LangChain4j se encarga de descargar los pesos (la primera vez) a una caché local. Posteriormente, el modelo se carga desde disco. El servicio no gestiona la descarga; LangChain4j lo hace internamente.

Actualmente no se utiliza la integración con Jlama para embeddings, aunque las dependencias están presentes. El código también contiene comentarios sobre cómo implementar una función `COSINE_DISTANCE` en H2 (usando un alias de Java), pero no está activa porque H2 no soporta índices vectoriales nativos.

### 4. Vectorización de texto: el método `embed()`

El método público más importante es `embed(String text)`. Su implementación es directa:

```java
public synchronized float[] embed(String text) {
    if (StringUtils.isBlank(text)) {
        return null;
    }
    float[] vector = embeddingModel.embed(text).content().vector();
    return vector;
}
```

- Normaliza el texto (si está vacío o es null, retorna null).
- Invoca al modelo de LangChain4j, que devuelve un `EmbeddingResponse`.
- Extrae el vector como `float[]`.

Para casos en los que se necesita el vector serializado (por ejemplo, para guardar en la base de datos), se proporciona `embedAsBytes()`, que llama a `embed()` y luego a `toBytes()`.

El método es `synchronized` porque el modelo de LangChain4j puede no ser thread-safe (depende de la implementación). En la práctica, la concurrencia es baja (solo se invoca durante la persistencia de turnos o búsquedas), por lo que no supone un cuello de botella.

### 5. Serialización de vectores: `toBytes()` y `fromBytes()`

H2 (y otras bases de datos) no tienen un tipo nativo para `float[]`, pero pueden almacenar BLOBs (Binary Large Objects). Para ello, `EmbeddingsService` ofrece dos métodos de conversión:

- **`toBytes(float[] vector)`**: convierte un array de floats en un array de bytes. Utiliza `ByteBuffer.allocate(vector.length * 4)` (cada float son 4 bytes), obtiene un `FloatBuffer` y escribe los valores. El orden de bytes es el nativo de la máquina (little-endian en la mayoría de los casos), pero al ser siempre la misma JVM no hay problemas de interoperabilidad.

- **`fromBytes(byte[] bytes)`**: realiza la operación inversa. Envuelve el array de bytes en un `ByteBuffer`, obtiene un `FloatBuffer` y lee los valores en un nuevo `float[]`. Si `bytes` es null, retorna null.

Esta serialización se utiliza en `SourceOfTruthImpl` para guardar el embedding de cada turno en la columna `embedding_blob` y para recuperarlo después. Ejemplo al persistir:

```java
byte[] blobBytes = (vector != null) ? embedding.toBytes(vector) : null;
ps.setBytes(9, blobBytes);
```

Y al leer:

```java
float[] dbVec = embedding.fromBytes(rs.getBytes("embedding_blob"));
```

No se aplica compresión, porque los vectores de 384 dimensiones ocupan apenas 1.5 KB cada uno. Para miles de turnos, el espacio total es manejable.

### 6. Similitud coseno: el corazón de la búsqueda semántica

La medida de similitud entre dos vectores se calcula mediante la **similitud coseno**, implementada en el método `cosineSimilarity(float[] vectorA, float[] vectorB)`:

```java
double dotProduct = 0.0;
double normA = 0.0;
double normB = 0.0;
for (int i = 0; i < vectorA.length; i++) {
    dotProduct += vectorA[i] * vectorB[i];
    normA += Math.pow(vectorA[i], 2);
    normB += Math.pow(vectorB[i], 2);
}
return (normA == 0 || normB == 0) ? 0.0 : dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
```

El resultado es un valor entre -1 y 1:
- **1.0**: vectores idénticos (misma dirección). Textos semánticamente equivalentes.
- **0.0**: ortogonales, sin relación semántica.
- **-1.0**: opuestos (raro en embeddings de texto, pero posible si los significados son antitéticos).

Esta medida se usa para ordenar los resultados de búsqueda. El código también contiene (comentado) una función `cosineDistance` (1 - similitud), que sería útil si se implementara una función SQL en H2 para filtrar por distancia, pero actualmente no se utiliza.

### 7. Búsqueda top-K con `EmbeddingFilter`

Para evitar tener que calcular la similitud de todos los candidatos en cada búsqueda (lo cual sería costoso), `EmbeddingsService` proporciona `EmbeddingFilter`, una interfaz que permite acumular candidatos y mantener solo los K más relevantes. La implementación concreta es `EmbeddingFilterImpl`.

Su lógica interna es un **min-heap** (cola de prioridad) que ordena los elementos por su puntuación de similitud (de menor a mayor). La cabecera del heap es el peor de los K mejores. Cuando se añade un nuevo candidato:

- Si el heap tiene menos de K elementos, se añade directamente.
- Si tiene K y la similitud del nuevo candidato es mayor que la del peor elemento actual, se elimina el peor y se añade el nuevo.
- Si la similitud es menor, se descarta.

El método `add()` devuelve la similitud calculada (útil para depuración). Una vez añadidos todos los candidatos, `get()` devuelve la lista ordenada de mayor a menor similitud (invirtiendo el heap, que da el orden inverso).

También se puede pasar un `minScore` para filtrar candidatos que no alcancen un umbral mínimo de similitud (por defecto `Double.NaN`, que no filtra). Esto es útil para búsquedas que requieran una relevancia mínima.

`EmbeddingFilterImpl` se utiliza tanto en `SourceOfTruth.getTurnsByText()` como en `DocumentsService.search()`.

### 8. Integración con `SourceOfTruth`: búsqueda híbrida

El método `SourceOfTruth.getTurnsByText(String query, int maxResults)` es el que permite al agente buscar en todo el historial mediante la herramienta `search_full_history`. Su implementación es un ejemplo perfecto del uso de `EmbeddingsService`:

1. Obtiene una referencia al servicio de embeddings.
2. Crea un `EmbeddingFilter` para la query con el límite de resultados.
3. Ejecuta una consulta SQL que selecciona todos los turnos que tienen `embedding_blob` no nulo.
4. Para cada turno, recupera el blob, lo deserializa a `float[]` mediante `embedding.fromBytes()` y lo añade al filtro con `search.add(dbVec, turn)`.
5. Finalmente, obtiene la lista de turnos más similares con `search.get()`.

Esta estrategia es un **escaneo completo** de la tabla (sin índices). Para una base de datos con miles de turnos, el coste es aceptable (unos pocos milisegundos por búsqueda). Para decenas de miles, puede comenzar a ser lento. Noema asume que el historial de un usuario individual no crecerá a millones de turnos (al menos en esta fase de prototipo).

El código incluye comentarios sobre cómo se podría migrar a PostgreSQL con `pgvector` para tener índices reales, pero actualmente no es una prioridad.

### 9. Integración con Documentos: búsqueda en resúmenes

`DocumentsService` también utiliza `EmbeddingsService` para la búsqueda semántica en los resúmenes de documentos indexados. El proceso es muy similar al de los turnos, pero con algunas diferencias:

- Los documentos se almacenan en la tabla `DOCUMENTS` con una columna `summary_embedding` BLOB.
- El método `search()` (híbrido) permite combinar un filtro por categorías (SQL) con una búsqueda semántica en los resúmenes.
- `EmbeddingFilter` se utiliza de igual modo: se crea con la query, se recorren los documentos que pasan el filtro de categorías, se calcula la similitud y se mantienen los mejores.

De esta forma, el agente puede encontrar documentos relevantes tanto por su categoría explícita como por el contenido semántico de su resumen, sin necesidad de que el usuario conozca las palabras exactas que aparecen en el texto.

### 10. Limitaciones y posibles mejoras

A pesar de su utilidad, `EmbeddingsService` tiene varias limitaciones que deben tenerse en cuenta:

- **Modelo de baja dimensión (384)**: es suficiente para similitud semántica básica, pero para conceptos muy sutiles o dominios especializados, un modelo de mayor dimensión (como `all-mpnet-base-v2` con 768 dimensiones o los de OpenAI con 1536) ofrecería mejor precisión. El coste sería mayor memoria y tiempo de cálculo.

- **Escaneo completo sin índices**: para bases de datos grandes (decenas de miles de turnos o documentos), cada búsqueda puede volverse lenta. La solución natural sería migrar a una base de datos con soporte nativo de índículos vectoriales (pgvector, Milvus, etc.). El código ya tiene comentarios al respecto.

- **Sin caché de embeddings de consultas**: si el usuario repite la misma búsqueda varias veces, se recalcula el embedding de la query cada vez. Una caché trivial podría ahorrar este coste.

- **Búsqueda síncrona y bloqueante**: las búsquedas se ejecutan en el mismo hilo del `eventDispatcher`. Si la tabla es muy grande, el agente se detiene hasta que termina. Para búsquedas muy pesadas, se podría considerar asincronía.

- **Serialización sin compresión**: aunque cada vector ocupa poco, para millones de turnos el espacio en disco podría ser significativo. Una compresión ligera (por ejemplo, cuantización a 8 bits) reduciría el almacenamiento a costa de precisión.

- **Modelo cargado en memoria permanentemente**: los embeddings están siempre en RAM, consumiendo entre 100 y 300 MB según la implementación de LangChain4j. No se puede descargar el modelo para liberar recursos si no se usa.

A pesar de estas limitaciones, `EmbeddingsService` cumple sobradamente su propósito en el contexto de Noema: proporcionar búsqueda semántica en un agente local, con un modelo gratuito, sin dependencias externas, y con un rendimiento aceptable para volúmenes de datos moderados (miles de turnos, cientos de documentos). Es una pieza clave para que el agente "recuerde" y "encuentre" información relevante.
