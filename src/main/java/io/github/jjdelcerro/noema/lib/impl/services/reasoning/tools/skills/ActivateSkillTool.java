package io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.skills;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.AgentTool;
import io.github.jjdelcerro.noema.lib.impl.AbstractAgentTool;
import io.github.jjdelcerro.noema.lib.impl.ToolSpecificationBuilder;
import io.github.jjdelcerro.noema.lib.impl.skills.Skill;
import io.github.jjdelcerro.noema.lib.impl.skills.SkillUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;

public class ActivateSkillTool extends AbstractAgentTool {

  public static final String TOOL_NAME = "activate_skill";

  public ActivateSkillTool(Agent agent) {
    super(agent);
  }

  @Override
  public ToolSpecificationBuilder getSpecification() {
    return ToolSpecificationBuilder.create()
            .name(TOOL_NAME)
            .description("Carga las directivas de un skill especifico en tu memoria de trabajo y fija sus reglas en tu contexto. "
                    + "Usalo cuando identifiques que una tarea requiere un protocolo tecnico paso a paso. "
                    + "Las directivas del skill permaneceran activas y fijadas hasta que concluyas el procedimiento.")
            .addStringParameter("name", false, "El identificador unico del skill a activar (obtenido de 'list_skills').");
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
  public boolean shouldPin() {
    return true;
  }

  @Override
  public String getPinnedNotificationMessage(ToolExecutionRequest request, ToolExecutionResultMessage result) {
    String skillName = "desconocido";
    if (request != null && request.arguments() != null) {
      try {
        JsonObject args = JsonParser.parseString(request.arguments()).getAsJsonObject();
        if (args.has("name")) {
          skillName = args.get("name").getAsString().trim();
        }
      } catch (Exception ignored) {
      }
    }
    return String.format(
            "[SKILL ACTIVO: %s]\nEste skill define directivas obligatorias para tu comportamiento. "
            + "Cuando concluyas el procedimiento, invoca 'deactivate_skill(name: \"%s\")' para liberarlo del contexto.",
            skillName, skillName
    );
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
      if (skill == null) {
        return error("Skill no encontrado: " + name);
      }

      LOGGER.info("Activando skill procedimental: {}", name);
      return skill.getContents();

    } catch (Exception e) {
      LOGGER.error("Error activando skill", e);
      return error("Error interno al activar skill: " + e.getMessage());
    }
  }
}
