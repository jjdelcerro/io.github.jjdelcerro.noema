package io.github.jjdelcerro.noema.lib.spi.services.scripting;

import io.github.jjdelcerro.noema.lib.api.Agent;
import io.github.jjdelcerro.noema.lib.api.Telemetry;
import io.github.jjdelcerro.noema.lib.api.services.scripting.ScriptContext;
import io.github.jjdelcerro.noema.lib.api.services.scripting.ScriptModule;
import io.github.jjdelcerro.noema.lib.api.services.scripting.ScriptModuleFactory;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author jjdelcerro
 */
public abstract class AbstractScriptModule implements ScriptModule {

  
  public static abstract class AbstractScriptModuleFactory implements ScriptModuleFactory {

    final String name;
    public AbstractScriptModuleFactory(String name) {
      this.name = name;
    }

    @Override
    public String getName() {
      return this.name;
    }
    
  }
  
  protected static final Logger LOGGER = LoggerFactory.getLogger(AbstractScriptModule.class);

  final String name;
  final String description;
  protected final Agent agent;
  protected final ScriptContext context;

  protected AbstractScriptModule(ScriptContext context, Agent agent, String name, String description) {
    this.context = context;
    this.agent = Objects.requireNonNull(agent, "Agent cannot be null");
    this.name = name;
    this.description = description;
  }

  public Telemetry getTelemetry() {
    return this.agent.getTelemetry();
  }
  
  @Override
  public String getName() {
    return name;
  }

  @Override
  public String getDescription() {
    return description;
  }

  @Override
  public String help() {
    return "[agent." + getName() + "] " + getDescription();
  }

  @Override
  public List<Class> getImports() {
    return Collections.emptyList();
  } 

}
