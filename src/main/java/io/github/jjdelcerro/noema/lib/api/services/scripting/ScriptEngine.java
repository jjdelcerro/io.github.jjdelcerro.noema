package io.github.jjdelcerro.noema.lib.api.services.scripting;

import io.github.jjdelcerro.noema.lib.api.services.scripting.ScriptContext;
import java.nio.file.Path;

/**
 *
 * @author jjdelcerro
 */
public interface ScriptEngine extends AutoCloseable {

  public record ScriptResult(Path stdout, Object result) {

  }
  
  public void setContext(ScriptContext context);
  
  public ScriptContext getContext();
  
  public void setVariable(String name, Object value);
  
  public ScriptResult evaluate(String script);  
}
