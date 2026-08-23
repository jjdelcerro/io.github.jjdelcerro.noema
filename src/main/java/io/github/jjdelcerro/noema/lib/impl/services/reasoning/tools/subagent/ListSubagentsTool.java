package io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.subagent;

import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.AgentLocator;
import io.github.jjdelcerro.noema.lib.AgentTool;
import io.github.jjdelcerro.noema.lib.SubagentDefinition;
import io.github.jjdelcerro.noema.lib.SubagentDefinition.SubagentParam;
import io.github.jjdelcerro.noema.lib.impl.AbstractAgentTool;
import io.github.jjdelcerro.noema.lib.impl.ToolSpecificationBuilder;
import org.apache.commons.lang3.StringUtils;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tool that lists all available subagent recipes found in var/subagents/.
 * Returns their names, descriptions, and expected parameters so the LLM can
 * discover available workers and invoke them via launch_subagent.
 */
public class ListSubagentsTool extends AbstractAgentTool {

  public static final String TOOL_NAME = "list_subagents";

  public ListSubagentsTool(Agent agent) {
    super(agent);
  }

  @Override
  public ToolSpecificationBuilder getSpecification() {
    return ToolSpecificationBuilder.create()
            .name(TOOL_NAME)
            .description(
                    "Lista todas las recetas de subagentes especializados disponibles en el sistema junto con sus descripciones "
                    + "y los parámetros que requieren. Úsala antes de 'launch_subagent' para descubrir qué trabajadores "
                    + "tienes a tu disposición y qué claves debes incluir en el objeto 'params'."
            );
  }

  @Override
  public int getMode() {
    return AgentTool.MODE_READ;
  }

  @Override
  public String execute(String jsonArguments) {
    try {
      Collection<Path> paths = this.agent.getPaths().listAgentPath("var/subagents");
      List<Map<String, Object>> catalog = new ArrayList<>();

      if (paths != null && !paths.isEmpty()) {
        for (Path path : paths) {
          String fileName = path.getFileName().toString();

          if (!fileName.toLowerCase().endsWith(".xml")) {
            continue;
          }

          try {
            SubagentDefinition def = AgentLocator.getAgentManager().createSubagentDefinition(path);

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", def.getName());
            entry.put("description", def.getDescription());

            Map<String, String> paramsMap = new LinkedHashMap<>();
            List<SubagentParam> paramsList = def.getParams();
            if (paramsList != null && !paramsList.isEmpty()) {
              for (SubagentParam param : paramsList) {
                String desc = StringUtils.isNotBlank(param.description())
                        ? param.description()
                        : "Valor requerido";
                paramsMap.put(param.name(), desc);
              }
            }
            entry.put("params", paramsMap);

            catalog.add(entry);

          } catch (Exception ex) {
            LOGGER.warn("Could not load subagent definition from '{}': {}", fileName, ex.getMessage());
          }
        }
      }

      return gson.toJson(catalog);

    } catch (Exception e) {
      LOGGER.error("Error executing list_subagents", e);
      return error("Error al listar el catálogo de subagentes: " + e.getMessage());
    }
  }
}
