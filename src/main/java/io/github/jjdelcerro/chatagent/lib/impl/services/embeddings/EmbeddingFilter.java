/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */
package io.github.jjdelcerro.chatagent.lib.impl.services.embeddings;

import java.util.List;

/**
 *
 * @author jjdelcerro
 */
public interface EmbeddingFilter<T> {

  /**
   * Evalúa un candidato y lo añade al Top-K si su similitud es suficiente.
   *
   * @param vector El vector del elemento recuperado de BBDD.
   * @param data El objeto de dominio construido (ej. Turn).
   */
  double add(float[] vector, T data);

  double add(byte[] vector, T data);
  
  /**
   * Retorna la lista final ordenada de mayor a menor relevancia (Descendente).
   */
  List<T> get();

  /**
   * Método de conveniencia para convertir el BLOB de la BBDD a float[]
   * delegando en el servicio.
   */
  float[] toFloat(byte[] blob);
  
}
