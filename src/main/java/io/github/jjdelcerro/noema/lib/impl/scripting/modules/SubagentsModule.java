package io.github.jjdelcerro.noema.lib.impl.scripting.modules;

import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.AgentLocator;
import io.github.jjdelcerro.noema.lib.Subagent;
import io.github.jjdelcerro.noema.lib.SubagentDefinition;
import io.github.jjdelcerro.noema.lib.impl.scripting.AbstractScriptModule;
import io.github.jjdelcerro.noema.lib.impl.scripting.ScriptContext;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;

/**
 *
 * @author jjdelcerro
 */
public class SubagentsModule extends AbstractScriptModule {

  public SubagentsModule(ScriptContext context, Agent agent) {
    super(context, agent, "subagents", "Modulo de acceso a las funciones relacionadas con subagentes");
  }

  @Override
  public String help() {
    return """
[agent.subagents API] (ejecución de recetas declarativas en var/subagents/*.xml)
• run(recipeName, Map params): String (SÍNCRONO, devuelve respuesta textual final)
  -> def out = agent.subagents.run('document_indexer', [FILE_PATH: 'doc.txt', OUTPUT_PATH: 'out.md'])
• launch(recipeName, Map params): int (ASÍNCRONO en 2º plano, notifica fin vía SYSTEMNOTIFICATION)
  -> int id = agent.subagents.launch('document_indexer', [FILE_PATH: 'doc.txt', OUTPUT_PATH: 'out.md'])
""";
  }

  /**
   * Runs a subagent recipe synchronously and returns its final response string.
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
