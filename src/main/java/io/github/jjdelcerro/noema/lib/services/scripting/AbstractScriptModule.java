package io.github.jjdelcerro.noema.lib.services.scripting;

import io.github.jjdelcerro.noema.lib.Agent;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author jjdelcerro
 */
public abstract class AbstractScriptModule implements ScriptModule {

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

}
