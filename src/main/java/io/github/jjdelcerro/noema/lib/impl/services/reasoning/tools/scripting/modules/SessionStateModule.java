package io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.scripting.modules;

import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.scripting.AbstractScriptingModule;
import io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.scripting.ScriptContext;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 *
 * @author jjdelcerro
 */
public class SessionStateModule extends AbstractScriptingModule {
  
  final Map<String, Object> sessionState;

  public SessionStateModule(ScriptContext context, Agent agent, Map<String, Object> sessionState) {
    super(context, agent, "state", "Modulo encargado de mantener el estado de la sesion");
    this.sessionState = sessionState != null ? sessionState : new ConcurrentHashMap<>();
  }

  public void set(String name, Object value) {
    this.sessionState.put(name, value);
  }

  public Object get(String name) {
    return this.sessionState.get(name);
  }

  // Soporte para sintaxis de propiedad en Groovy: noema.state.foo = bar
  public void propertyMissing(String name, Object value) {
    set(name, value);
  }

  public Object propertyMissing(String name) {
    return get(name);
  }
  
}
