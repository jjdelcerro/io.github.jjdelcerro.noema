package io.github.jjdelcerro.chatagent.lib.impl;

import io.github.jjdelcerro.chatagent.lib.AgentActions;
import io.github.jjdelcerro.chatagent.lib.AgentSettings;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 *
 * @author jjdelcerro
 */
public class AgentActionsImpl implements AgentActions {

// FIXME: A la implementación actual le falta robustez ante fallos comunes:
//
//   1. Validación de existencia: Si se llama a call con un nombre que no existe en el mapa (por un error en el JSON del menú, por ejemplo), lanzará un
//      NullPointerException al intentar ejecutar action.perform(settings). Debería devolver false o lanzar una excepción controlada.
//   2. Manejo de excepciones en la acción: Si la acción perform lanza una excepción (ej: error de red al cambiar un provider), el hilo principal
//      morirá o propagará el error sin control. Sería mejor envolver la llamada en un try-catch y devolver false.
//   3. Thread-safety: Aunque usas synchronized, al ser una aplicación con componentes que pueden ser asíncronos (como Telegram o Mail), quizás un
//      ConcurrentHashMap sería más idiomático y eficiente que sincronizar los métodos completos.
//   4. Logging: No hay rastro de lo que ocurre. Sería útil loguear cuando se ejecuta una acción y si ha tenido éxito o no.
  
  private Map<String,AgentAction> actions;
  
  public AgentActionsImpl() {
    this.actions = new HashMap<>();
  }
  
  @Override
  public synchronized void addAction(String name, AgentAction action) {
    this.actions.put(name, action);
  }

  @Override
  public synchronized boolean call(String name, AgentSettings settings) {
    AgentAction action = this.actions.get(name);
    return action.perform(settings);
  }
  
}
