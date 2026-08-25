package io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.scripting.modules;

import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.AgentLocator;
import io.github.jjdelcerro.noema.lib.Subagent;
import io.github.jjdelcerro.noema.lib.SubagentDefinition;
import io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.scripting.AbstractScriptingModule;
import io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.scripting.ScriptContext;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;

/**
 *
 * @author jjdelcerro
 */
public class SubagentsModule extends AbstractScriptingModule {
  
  public SubagentsModule(ScriptContext context, Agent agent) {
    super(context, agent, "subagents", "Modulo de acceso a las funciones relacionadas con subagentes");
  }

  /**
   * Runs a subagent recipe synchronously and returns its final response
   * string.
   */
  public String run(String subagentName, Map<String, ?> params) {
    Path xmlPath = agent.getPaths().getAgentPath("var/subagents/" + subagentName + ".xml");
    if (xmlPath == null || !Files.exists(xmlPath)) {
      throw new IllegalArgumentException("Subagent recipe not found: " + subagentName);
    }
    try {
      SubagentDefinition def = AgentLocator.getAgentManager().createSubagentDefinition(xmlPath);
      Path tempWorkspace = agent.getPaths().getTempFolder().resolve("script_subagent_" + System.currentTimeMillis());
      try (Subagent subagent = AgentLocator.getAgentManager().createSubagent(agent, def, tempWorkspace)) {
        return subagent.run(params != null ? params : Collections.emptyMap());
      }
    } catch (Exception e) {
      throw new RuntimeException("Failed running subagent '" + subagentName + "': " + e.getMessage(), e);
    }
  }

  /**
   * Launches a subagent asynchronously in the background.
   */
  public int launch(String subagentName, Map<String, ?> params) {
    Path xmlPath = agent.getPaths().getAgentPath("var/subagents/" + subagentName + ".xml");
    if (xmlPath == null || !Files.exists(xmlPath)) {
      throw new IllegalArgumentException("Subagent recipe not found: " + subagentName);
    }
    try {
      SubagentDefinition def = AgentLocator.getAgentManager().createSubagentDefinition(xmlPath);
      Path tempWorkspace = agent.getPaths().getTempFolder().resolve("script_subagent_async_" + System.currentTimeMillis());
      Subagent subagent = AgentLocator.getAgentManager().createSubagent(agent, def, tempWorkspace);
      return subagent.launch(params != null ? params : Collections.emptyMap());
    } catch (Exception e) {
      throw new RuntimeException("Failed launching subagent '" + subagentName + "': " + e.getMessage(), e);
    }
  }
  
}
