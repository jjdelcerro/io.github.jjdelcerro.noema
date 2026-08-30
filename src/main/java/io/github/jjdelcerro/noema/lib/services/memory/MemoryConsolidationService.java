package io.github.jjdelcerro.noema.lib.services.memory;

import io.github.jjdelcerro.noema.lib.AgentService;
import io.github.jjdelcerro.noema.lib.memory.episodic.Turn;
import java.util.List;
import io.github.jjdelcerro.noema.lib.memory.consolidate.ConsolidateMemory;

/**
 * Servicio encargado de la consolidación y síntesis de conocimiento a largo plazo.
 * Fusiona iterativamente el punto de guardado anterior con los turnos recientes 
 * bajo el principio de la espiral de contexto, garantizando que el agente opere 
 * siempre sobre un conocimiento estructurado y dentro de su zona óptima de atención.
 *
 * TODO: Antes MemoryService, habria que actualizar la documentacion con este cambio 
 * @author jjdelcerro
 */
public interface MemoryConsolidationService extends AgentService {

  String MEMORY_MODEL_ID = "memory_consolidation/provider/model_id";
  String MEMORY_PROVIDER_API_KEY = "memory_consolidation/provider/api_key";
  String MEMORY_PROVIDER_URL = "memory_consolidation/provider/url";

  String NAME = "MemoryConsolidation";
  String ID = "MEMORYCONSOLIDATION";

  /**
   * Ejecuta el proceso de consolidación.
   *
   * @param previous El ConsolidateMemory anterior (puede ser null si es la primera
   * vez).
   * @param newTurns La lista de turnos recientes a consolidar.
   * @return Un nuevo ConsolidateMemory TRANSITORIO (ID -1) con el texto generado.
   */
  ConsolidateMemory consolide(String subchannel, ConsolidateMemory previous, List<Turn> newTurns);
  
}
