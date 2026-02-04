package io.github.jjdelcerro.chatagent.lib.impl.services.embeddings;

import java.util.*;

/**
 * Clase de utilidad para realizar búsquedas vectoriales en memoria (Client-side
 * ranking). Ideal para bases de datos que no soportan búsqueda vectorial nativa
 * (como H2).
 *
 * @param <T> El tipo de objeto que se está recuperando (ej. Turn).
 */
public class EmbeddingFilterImpl<T> implements EmbeddingFilter<T> {

  private final EmbeddingsService embeddingService;
  private final float[] queryVector;
  private final int maxResults;

  // Almacenamos Entry<Score, Data>.
  // Usamos orden ASCENDENTE (Min-Heap): la cabeza de la cola es el score más bajo.
  // Esto permite ver rápidamente cuál es el "peor" de los mejores para eliminarlo si entra uno mejor.
  private final PriorityQueue<Map.Entry<Double, T>> topK;
  private final double minScore;

  /**
   * Constructor que prepara la búsqueda vectorizando la query.
   *
   * @param embeddingService Servicio para operaciones vectoriales.
   * @param query Texto de la consulta del usuario.
   * @param maxResults Número máximo de elementos a retornar.
   */
  public EmbeddingFilterImpl(EmbeddingsService embeddingService, String query, int maxResults, double minScore) {
    this.embeddingService = embeddingService;
    this.maxResults = maxResults;
    this.minScore = minScore;

    // 1. Vectorizamos la query al instanciar (Fail-fast si hay error en el servicio)
    this.queryVector = embeddingService.embed(query);

    // 2. Inicializamos la cola de prioridad
    this.topK = new PriorityQueue<>(
            Comparator.comparingDouble(Map.Entry::getKey)
    );
  }

  /**
   * Método de conveniencia para convertir el BLOB de la BBDD a float[]
   * delegando en el servicio.
   */
  @Override
  public float[] toFloat(byte[] blob) {
    if (blob == null) {
      return null;
    }
    return embeddingService.fromBytes(blob);
  }

  public double add(byte[] bytes, T data) {
    if (bytes == null) {
      return -1;
    }
    return this.add(this.toFloat(bytes),data);
  }
  
  /**
   * Evalúa un candidato y lo añade al Top-K si su similitud es suficiente.
   *
   * @param vector El vector del elemento recuperado de BBDD.
   * @param data El objeto de dominio construido (ej. Turn).
   */
  @Override
  public double add(float[] vector, T data) {
    if (vector == null) {
      return -1;
    }

    // Calculamos similitud
    double score = embeddingService.cosineSimilarity(queryVector, vector);
    if (Double.isNaN(minScore) || score >= minScore) {
      if (topK.size() < maxResults) {
        // Si aún no hemos llenado el cupo, entra directo
        topK.add(new AbstractMap.SimpleEntry<>(score, data));
      } else {
        // Si la cola está llena, comparamos con el "peor" (el min) que tenemos dentro
        Double minScoreInTop = topK.peek().getKey();

        if (score > minScoreInTop) {
          topK.poll(); // Sacamos el peor
          topK.add(new AbstractMap.SimpleEntry<>(score, data)); // Metemos el nuevo
        }
      }
    }
    return score;
  }

  /**
   * Retorna la lista final ordenada de mayor a menor relevancia (Descendente).
   */
  @Override
  public List<T> get() {
    List<T> sortedResults = new ArrayList<>(topK.size());

    // Vaciamos la cola. Como es un Min-Heap, salen del menor score al mayor.
    while (!topK.isEmpty()) {
      sortedResults.add(topK.poll().getValue());
    }

    // Invertimos para que el usuario reciba primero el de mayor score.
    Collections.reverse(sortedResults);

    return sortedResults;
  }
}
