package io.github.jjdelcerro.noema.lib.api.services.scripting;

import io.github.jjdelcerro.noema.lib.api.services.scripting.ScriptModule;
import java.util.Map;

/**
 *
 * @author jjdelcerro
 */
public interface ScriptContext extends AutoCloseable, ScriptModule {

  public static final String CONTEXT_NAME = "agent";

  public void registerModule(ScriptModule module);

  public ScriptModule getModule(String name);

  public Map<String, ScriptModule> getRegisteredModules();

  public Object getProperty(String property);

  public Object propertyMissing(String name);

  public AutoCloseable registerResource(AutoCloseable resource);
}
