package io.github.jjdelcerro.noema.lib.impl.services.embeddings;

import java.util.List;

/**
 *
 * @author jjdelcerro
 * @param <T>
 */
public interface EmbeddingFilter<T> {

  /**
   * Evalúa un candidato y lo añade al Top-K si su similitud es suficiente.
   *
   * @param vector El vector del elemento recuperado de BBDD.
   * @param data El objeto de dominio construido (ej. Turn).
   * @return 
   */
  double add(float[] vector, T data);

  double add(byte[] vector, T data);
  
  /**
   * Retorna la lista final ordenada de mayor a menor relevancia (Descendente).
   * @return 
   */
  List<T> get();

  /**
   * Método de conveniencia para convertir el BLOB de la BBDD a float[]
   * delegando en el servicio.
   * @param blob
   * @return 
   */
  float[] toFloat(byte[] blob);
  
}
