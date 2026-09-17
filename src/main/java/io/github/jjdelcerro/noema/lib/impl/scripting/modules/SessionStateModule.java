package io.github.jjdelcerro.noema.lib.impl.scripting.modules;

import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.impl.scripting.AbstractScriptModule;
import io.github.jjdelcerro.noema.lib.impl.scripting.ScriptContext;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 *
 * @author jjdelcerro
 */
public class SessionStateModule extends AbstractScriptModule {

  final Map<String, Object> sessionState;

  public SessionStateModule(ScriptContext context, Agent agent, Map<String, Object> sessionState) {
    super(context, agent, "state", "Modulo encargado de mantener el estado de la sesion");
    this.sessionState = sessionState != null ? sessionState : new ConcurrentHashMap<>();
  }

  @Override
  public String help() {
    return """
[agent.state API] (almacén volátil en memoria entre scripts dentro del mismo subcanal)
• Propiedad dinámica: agent.state.clave = valor | def val = agent.state.clave
• set(name, value): void
• get(name): Object
""";
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
