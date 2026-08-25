package io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.skills;

import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.AgentTool;
import io.github.jjdelcerro.noema.lib.impl.AbstractPaginatedAgentTool;
import io.github.jjdelcerro.noema.lib.impl.ToolSpecificationBuilder;
import io.github.jjdelcerro.noema.lib.impl.skills.Skill;
import io.github.jjdelcerro.noema.lib.impl.skills.SkillUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class RunSkillScriptTool extends AbstractPaginatedAgentTool {

  public static final String TOOL_NAME = "run_skill_script";
  private static final long EXECUTION_TIMEOUT_SECONDS = 120L;

  public RunSkillScriptTool(Agent agent) {
    super(agent);
  }

  @Override
  public ToolSpecificationBuilder getSpecification() {
    return ToolSpecificationBuilder.create()
            .name(TOOL_NAME)
            .description("Ejecuta un script auxiliar ubicado en la carpeta scripts/ de un skill.\n\n"
                    + getShortPaginationInstruction())
            .addStringParameter("name", false, "El nombre del skill.")
            .addStringParameter("script", false, "El nombre del script a ejecutar dentro de scripts/ (ej: 'check-style.sh').")
            .addStringArrayParameter("args", true, "Argumentos opcionales para la ejecucion del script.");
  }

  @Override
  public int getMode() {
    return AgentTool.MODE_READ; // Esta puesto read aproposito
  }

  @Override
  public int getType() {
    return AgentTool.TYPE_OPERATIONAL;
  }

  @Override
  public String execute(String jsonArguments) {
    try {
      Map<String, Object> args = gson.fromJson(jsonArguments, Map.class);
      String skillName = args != null ? (String) args.get("name") : null;
      String scriptName = args != null ? (String) args.get("script") : null;
      List<String> scriptArgs = args != null ? (List<String>) args.get("args") : null;

      if (StringUtils.isBlank(skillName) || StringUtils.isBlank(scriptName)) {
        return formatErrorResponse("Los parametros 'name' y 'script' son obligatorios.");
      }

      Skill skill = SkillUtils.getSkill(this.agent, skillName);
      if (skill == null) {
        return formatErrorResponse("Skill no encontrado: " + skillName);
      }

      ProcessBuilder pb;
      try {
        pb = skill.createScriptProcess(scriptName, scriptArgs);
      } catch (Exception ex) {
        return formatErrorResponse(ex.getMessage());
      }

      Files.createDirectories(agent.getPaths().getTempFolder());
      String executionId = "skill_script_" + UUID.randomUUID().toString().substring(0, 8);
      Path outputFile = agent.getPaths().getTempFolder().resolve(executionId + ".out");

      pb.redirectErrorStream(true);
      pb.redirectOutput(outputFile.toFile());

      Process process = pb.start();
      boolean finished = process.waitFor(EXECUTION_TIMEOUT_SECONDS, TimeUnit.SECONDS);

      if (!finished) {
        process.destroyForcibly();
        return formatErrorResponse("El script supero el tiempo limite de ejecucion (" + EXECUTION_TIMEOUT_SECONDS + "s).");
      }

      String resourceId = getIdFromPath(outputFile);
      if (resourceId == null) {
        return formatErrorResponse("Error generando resource_id para la salida del script.");
      }

      return servePaginatedResource(resourceId);

    } catch (IOException | InterruptedException e) {
      LOGGER.warn("Error ejecutando script de skill, args=" + jsonArguments, e);
      return formatErrorResponse("Fallo en la ejecucion del script: " + e.getMessage());
    } catch (Exception e) {
      LOGGER.error("Error inesperado en RunSkillScriptTool", e);
      return formatErrorResponse("Error inesperado: " + e.getMessage());
    }
  }
}
