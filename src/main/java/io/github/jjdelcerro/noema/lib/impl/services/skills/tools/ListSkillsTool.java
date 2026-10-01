package io.github.jjdelcerro.noema.lib.impl.services.skills.tools;

import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.AgentTool;
import io.github.jjdelcerro.noema.lib.impl.tools.AbstractAgentTool;
import io.github.jjdelcerro.noema.lib.impl.tools.ToolSpecificationBuilder;
import io.github.jjdelcerro.noema.lib.impl.services.skills.Skill;
import io.github.jjdelcerro.noema.lib.impl.services.skills.SkillsService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ListSkillsTool extends AbstractAgentTool {

  public static final String TOOL_NAME = "list_skills";

  public ListSkillsTool(Agent agent) {
    super(agent);
  }

  @Override
  public ToolSpecificationBuilder getSpecification() {
    return ToolSpecificationBuilder.create()
            .name(TOOL_NAME)
            .description("Consulta tu catalogo de skills procedimentales disponibles en el proyecto. "
                    + "Invocala para descubrir que protocolos tecnicos paso a paso tienes disponibles "
                    + "antes de decidir cual activar mediante 'activate_skill'.");
  }

  @Override
  public int getMode() {
    return AgentTool.MODE_READ;
  }

  @Override
  public int getType() {
    return AgentTool.TYPE_OPERATIONAL;
  }

  @Override
  public String execute(String jsonArguments) {
    try {
      SkillsService service = (SkillsService) this.agent.getService(SkillsService.NAME);

      List<Skill> skills = service.listSkills(this.agent);
      List<Map<String, String>> catalog = new ArrayList<>();

      for (Skill skill : skills) {
        Map<String, String> item = new LinkedHashMap<>();
        item.put("name", skill.getName());
        item.put("description", skill.getDescription());
        item.put("version", skill.getVersion());
        catalog.add(item);
      }

      return gson.toJson(catalog);
    } catch (Exception e) {
      LOGGER.warn("Error al listar skills disponibles", e);
      return error("Error interno al listar skills: " + e.getMessage());
    }
  }
}
