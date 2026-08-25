package io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.skills;

import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.AgentTool;
import io.github.jjdelcerro.noema.lib.impl.AbstractAgentTool;
import io.github.jjdelcerro.noema.lib.impl.ToolSpecificationBuilder;
import io.github.jjdelcerro.noema.lib.impl.skills.Skill;
import io.github.jjdelcerro.noema.lib.impl.skills.SkillUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;

public class DeactivateSkillTool extends AbstractAgentTool {

  public static final String TOOL_NAME = "deactivate_skill";

  public DeactivateSkillTool(Agent agent) {
    super(agent);
  }

  @Override
  public ToolSpecificationBuilder getSpecification() {
    return ToolSpecificationBuilder.create()
            .name(TOOL_NAME)
            .description("Desactiva un skill previamente activado, retirando sus directivas fijadas de tu memoria proyectada "
                    + "y deteniendo los recordatorios periodicos. Invocala obligatoriamente al finalizar la tarea.")
            .addStringParameter("name", false, "El nombre del skill a desactivar.");
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
      Map<String, String> args = gson.fromJson(jsonArguments, Map.class);
      String name = args != null ? args.get("name") : null;

      if (StringUtils.isBlank(name)) {
        return error("El parametro 'name' es obligatorio.");
      }

      Skill skill = SkillUtils.getSkill(this.agent, name);
      if (skill != null) {
        skill.deactivate(this.agent.getCurrentSubchannel());
      }

      LOGGER.info("Desactivando skill: {}", name);
      return gson.toJson(Map.of(
              "status", "success",
              "message", "Skill '" + name + "' desactivado correctamente."
      ));

    } catch (Exception e) {
      LOGGER.error("Error desactivando skill", e);
      return error("Error interno al desactivar skill: " + e.getMessage());
    }
  }
}
