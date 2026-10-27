package io.github.jjdelcerro.noema.lib.api.services.scripting;

import io.github.jjdelcerro.noema.lib.api.Agent;

/**
 *
 * @author jjdelcerro
 */
public interface ScriptModuleFactory {

  public String getName();

  public ScriptModule createModule(Agent agent, ScriptContext context);
  
}
