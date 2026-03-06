package io.github.jjdelcerro.noema.lib.services.memory;

import io.github.jjdelcerro.noema.lib.AgentService;
import io.github.jjdelcerro.noema.lib.persistence.CheckPoint;
import io.github.jjdelcerro.noema.lib.persistence.Turn;
import java.util.List;

/**
 *
 * @author jjdelcerro
 */
public interface MemoryService extends AgentService {

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
  CheckPoint compact(CheckPoint previous, List<Turn> newTurns);
  
}
