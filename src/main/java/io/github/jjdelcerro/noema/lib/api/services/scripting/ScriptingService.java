package io.github.jjdelcerro.noema.lib.api.services.scripting;

import io.github.jjdelcerro.noema.lib.api.AgentService;
import io.github.jjdelcerro.noema.lib.impl.services.scripting.ScriptEngineImpl;

/**
 *
 * @author jjdelcerro
 */
public interface ScriptingService extends AgentService {

  public static String NAME = "scripting";

  public ScriptEngine createScriptEngine();

}
