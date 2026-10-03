package io.github.jjdelcerro.noema.lib.impl.services.scripting.modules;

import io.github.jjdelcerro.noema.lib.api.Agent;
import io.github.jjdelcerro.noema.lib.api.services.subagents.Subagent;
import io.github.jjdelcerro.noema.lib.api.services.subagents.SubagentDefinition;
import io.github.jjdelcerro.noema.lib.api.services.scripting.AbstractScriptModule;
import io.github.jjdelcerro.noema.lib.impl.services.scripting.ScriptContextImpl;
import io.github.jjdelcerro.noema.lib.api.services.subagents.SubagentsService;
import java.util.Collections;
import java.util.Map;

/**
 *
 * @author jjdelcerro
 */
public class SubagentsModule extends AbstractScriptModule {

  public SubagentsModule(ScriptContextImpl context, Agent agent) {
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

    try {
      SubagentsService service = (SubagentsService) this.agent.getService(SubagentsService.NAME);
      SubagentDefinition def = service.getSubagentDefinition(subagentName);
      try (Subagent subagent = service.createSubagent(agent, def, null)) {
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
    try {
      SubagentsService service = (SubagentsService) this.agent.getService(SubagentsService.NAME);
      SubagentDefinition def = service.getSubagentDefinition(subagentName);
      Subagent subagent = service.createSubagent(agent, def, null);
      return subagent.launch(params != null ? params : Collections.emptyMap());
    } catch (Exception e) {
      throw new RuntimeException("Failed launching subagent '" + subagentName + "': " + e.getMessage(), e);
    }
  }

}
