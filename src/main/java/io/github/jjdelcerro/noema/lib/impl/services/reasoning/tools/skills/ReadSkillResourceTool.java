package io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.skills;

import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.AgentTool;
import io.github.jjdelcerro.noema.lib.impl.AbstractPaginatedAgentTool;
import io.github.jjdelcerro.noema.lib.impl.ToolSpecificationBuilder;
import io.github.jjdelcerro.noema.lib.impl.skills.Skill;
import io.github.jjdelcerro.noema.lib.impl.skills.SkillUtils;
import org.apache.commons.lang3.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class ReadSkillResourceTool extends AbstractPaginatedAgentTool {

  public static final String TOOL_NAME = "read_skill_resource";

  public ReadSkillResourceTool(Agent agent) {
    super(agent);
  }

  @Override
  public ToolSpecificationBuilder getSpecification() {
    return ToolSpecificationBuilder.create()
            .name(TOOL_NAME)
            .description("Lee archivos complementarios empaquetados dentro de un skill (documentacion adicional, esquemas, plantillas).\n\n"
                    + getShortPaginationInstruction())
            .addStringParameter("skill_name", false, "El nombre del skill contenedor.")
            .addStringParameter("path", false, "Ruta relativa del archivo dentro de la carpeta del skill (ej: 'references/api-guide.md').");
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
      String skillName = args != null ? args.get("skill_name") : null;
      String relativePath = args != null ? args.get("path") : null;

      if (StringUtils.isBlank(skillName) || StringUtils.isBlank(relativePath)) {
        return formatErrorResponse("Los parametros 'skill_name' y 'path' son obligatorios.");
      }

      Skill skill = SkillUtils.getSkill(this.agent, skillName);
      if (skill == null) {
        return formatErrorResponse("Skill no encontrado: " + skillName);
      }

      Path resource;
      try {
        resource = skill.resolveResource(relativePath);
      } catch (SecurityException se) {
        return formatErrorResponse(se.getMessage());
      }

      if (resource == null || !Files.exists(resource) || !Files.isRegularFile(resource)) {
        return formatErrorResponse("Recurso no encontrado o no es un archivo regular: " + relativePath);
      }

      String resourceId = getIdFromPath(resource);
      if (resourceId == null) {
        return formatErrorResponse("Error resolviendo identificador para el recurso: " + relativePath);
      }

      return servePaginatedResource(resourceId);

    } catch (Exception e) {
      LOGGER.warn("Error leyendo recurso de skill, args=" + jsonArguments, e);
      return formatErrorResponse("Error procesando recurso de skill: " + e.getMessage());
    }
  }
}
