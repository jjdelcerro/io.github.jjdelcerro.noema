package io.github.jjdelcerro.noema.lib.impl.scripting;

import io.github.jjdelcerro.noema.lib.impl.scripting.modules.FsModule;
import io.github.jjdelcerro.noema.lib.impl.scripting.modules.SessionStateModule;
import io.github.jjdelcerro.noema.lib.impl.scripting.modules.AnnotationModule;
import io.github.jjdelcerro.noema.lib.impl.scripting.modules.LlmModule;
import io.github.jjdelcerro.noema.lib.impl.scripting.modules.WebModule;
import io.github.jjdelcerro.noema.lib.impl.scripting.modules.SubagentsModule;
import groovy.lang.GroovyObjectSupport;
import groovy.lang.MissingPropertyException;
import io.github.jjdelcerro.noema.lib.Agent;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Root context object ('agent') exposed to the embedded Groovy environment.
 * Dynamically resolves facades and provides streamed, safe access to agent
 * capabilities.
 *
 * TODO: habria que ver como incluir lo que ofrecen los modulos en la parte de
 * configuracion de herramientas del agente, de cara a que se pueda restringir
 * el acceso a herramientas o modulos, tanto en ejecucion como a la hora de
 * exponer los que ofrecen a traves del metodo help.
 */
public class ScriptContext extends GroovyObjectSupport implements AutoCloseable, ScriptModule {

  public static final String CONTEXT_NAME = "agent";

  private static final Logger LOGGER = LoggerFactory.getLogger(ScriptContext.class);
  private static Map<String, Map<String, Object>> subchannelStates;

  private final Agent agent;
  private final String subchannel;
  private final Map<String, ScriptModule> modules;
  private final List<AutoCloseable> openResources = new ArrayList<>();

  @SuppressWarnings("OverridableMethodCallInConstructor")
  protected ScriptContext(Agent agent, String subchannel, Map<String, Object> sessionState) {
    this.agent = Objects.requireNonNull(agent, "Agent cannot be null");
    this.subchannel = subchannel != null ? subchannel : Agent.DEFAULT_SUBCHANNEL;
    this.modules = new ConcurrentHashMap<>();

    // Register standard static facades
    registerModule(new SessionStateModule(this, this.agent, sessionState));
    registerModule(new FsModule(this, this.agent));
    registerModule(new LlmModule(this, this.agent));
    registerModule(new WebModule(this, this.agent));
    registerModule(new AnnotationModule(this, this.agent, this.subchannel));
    registerModule(new SubagentsModule(this, this.agent));
    // registerModule(new MCPModule(this, this.agent)); // TODO: implementar el puenete con MCP.
  }

  public static Map<String, Object> getSessionState(String subchannel) {
    if (subchannelStates == null) {
      subchannelStates = new ConcurrentHashMap<>();
    }
    return subchannelStates.computeIfAbsent(subchannel, k -> new ConcurrentHashMap<>());
  }

  public static ScriptContext of(Agent agent) {
    return of(agent, agent.getCurrentSubchannel());
  }

  public static ScriptContext of(Agent agent, String subchannel) {
    Map<String, Object> sessionState = getSessionState(subchannel);
    ScriptContext context = new ScriptContext(agent, subchannel, sessionState);
    return context;
  }

  public String getName() {
    return CONTEXT_NAME;
  }

  public String getDescription() {
    return "";
  }

  @Override
  public String help() {
    StringBuilder sb = new StringBuilder("[agent API]\n");
    for (ScriptModule module : modules.values()) {
      sb.append("• agent.")
              .append(module.getName())
              .append(" : ")
              .append(module.getDescription())
              .append("\n");
    }
    sb.append("Ayuda detallada: println agent.<modulo>.help()");
    return sb.toString();
  }

  /**
   * Registers a facade dynamically under a specific property name.
   */
  public void registerModule(ScriptModule module) {
    if (module != null && StringUtils.isNotBlank(module.getName())) {
      this.modules.put(module.getName(), module);
    }
  }

  public ScriptModule getModule(String name) {
    return this.modules.get(name);
  }

  public Map<String, ScriptModule> getRegisteredModules() {
    return Collections.unmodifiableMap(this.modules);
  }

  /**
   * Dynamic Groovy property dispatcher. Intercepts property accesses like
   * 'noema.fs', 'noema.llm', etc.
   */
  @Override
  public Object getProperty(String property) {
    if (modules.containsKey(property)) {
      return modules.get(property);
    }
    return super.getProperty(property);
  }

  /**
   * Fallback for missing properties in Groovy.
   */
  public Object propertyMissing(String name) {
    if (modules.containsKey(name)) {
      return modules.get(name);
    }
    throw new MissingPropertyException(
            "Property '" + name + "' is not a registered module. Available modules: " + modules.keySet(),
            name,
            this.getClass()
    );
  }

  public AutoCloseable registerResource(AutoCloseable resource) {
    this.openResources.add(resource);
    return resource;
  }

  @Override
  public void close() throws Exception {
    for (AutoCloseable resource : openResources) {
      try {
        if (resource != null) {
          resource.close();
        }
      } catch (Exception ex) {
        LOGGER.warn("Can't close resource", ex);
      }
    }
  }
}
