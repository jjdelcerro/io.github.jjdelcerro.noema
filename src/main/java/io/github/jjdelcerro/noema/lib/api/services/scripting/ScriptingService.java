package io.github.jjdelcerro.noema.lib.api.services.scripting;

import io.github.jjdelcerro.noema.lib.api.Agent;
import io.github.jjdelcerro.noema.lib.api.AgentService;

/**
 *
 * @author jjdelcerro
 */
public interface ScriptingService extends AgentService {

  public static String NAME = "scripting";

  public ScriptEngine createScriptEngine();
  public ScriptEngine createScriptEngine(Agent agent);
}
