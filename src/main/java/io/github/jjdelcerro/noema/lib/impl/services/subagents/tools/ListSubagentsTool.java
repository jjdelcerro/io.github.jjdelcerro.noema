package io.github.jjdelcerro.noema.lib.impl.services.subagents.tools;

import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.AgentTool;
import io.github.jjdelcerro.noema.lib.services.subagents.SubagentDefinition;
import io.github.jjdelcerro.noema.lib.services.subagents.SubagentDefinition.SubagentParam;
import io.github.jjdelcerro.noema.lib.impl.tools.AbstractAgentTool;
import io.github.jjdelcerro.noema.lib.impl.tools.ToolSpecificationBuilder;
import io.github.jjdelcerro.noema.lib.services.subagents.SubagentsService;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
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
      SubagentsService service = (SubagentsService) this.agent.getService(SubagentsService.NAME);
      List<Map<String, Object>> catalog = new ArrayList<>();

      for (SubagentDefinition def : service.getSubagentDefinitions()) {
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
      }
      return gson.toJson(catalog);
    } catch (Exception e) {
      LOGGER.error("Error executing list_subagents", e);
      return error("Error al listar el catálogo de subagentes: " + e.getMessage());
    }
  }
}
