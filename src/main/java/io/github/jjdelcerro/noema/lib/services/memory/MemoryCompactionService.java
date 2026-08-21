package io.github.jjdelcerro.noema.lib.services.memory;

import io.github.jjdelcerro.noema.lib.AgentService;
import io.github.jjdelcerro.noema.lib.persistence.Turn;
import java.util.List;
import io.github.jjdelcerro.noema.lib.persistence.CompactedMemory;

/**
 * 
 * TODO: Antes MemoryService, habria que actualizar la documentacion con este cambio 
 * @author jjdelcerro
 */
public interface MemoryCompactionService extends AgentService {

  String MEMORY_MODEL_ID = "memory/provider/model_id";
  String MEMORY_PROVIDER_API_KEY = "memory/provider/api_key";
  String MEMORY_PROVIDER_URL = "memory/provider/url";

  String NAME = "Memory";
  String ID = "MEMORY";

  /**
   * Ejecuta el proceso de compactación.
   *
   * @param previous El CheckPoint anterior (puede ser null si es la primera
   * vez).
   * @param newTurns La lista de turnos recientes a consolidar.
   * @return Un nuevo CheckPoint TRANSITORIO (ID -1) con el texto generado.
   */
  CompactedMemory compact(String subchannel, CompactedMemory previous, List<Turn> newTurns);
  
}
