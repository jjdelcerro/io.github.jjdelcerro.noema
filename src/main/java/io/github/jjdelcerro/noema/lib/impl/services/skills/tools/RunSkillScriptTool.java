package io.github.jjdelcerro.noema.lib.impl.services.skills.tools;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.jjdelcerro.noema.lib.api.Agent;
import io.github.jjdelcerro.noema.lib.api.AgentAccessControl;
import io.github.jjdelcerro.noema.lib.api.AgentTool;
import io.github.jjdelcerro.noema.lib.api.services.scripting.ScriptContext;
import io.github.jjdelcerro.noema.lib.api.services.scripting.ScriptEngine;
import io.github.jjdelcerro.noema.lib.api.services.scripting.ScriptingService;
import io.github.jjdelcerro.noema.lib.impl.AgentDelegated;
import io.github.jjdelcerro.noema.lib.impl.AgentImpl;
import io.github.jjdelcerro.noema.lib.impl.services.scripting.ScriptEngineImpl;
import io.github.jjdelcerro.noema.lib.impl.services.scripting.modules.MemoryModule;
import io.github.jjdelcerro.noema.lib.impl.tools.AbstractPaginatedAgentTool;
import io.github.jjdelcerro.noema.lib.impl.tools.ToolSpecificationBuilder;
import io.github.jjdelcerro.noema.lib.impl.services.skills.Skill;
import io.github.jjdelcerro.noema.lib.impl.services.skills.SkillsService;
import java.io.BufferedWriter;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.AccessController;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.Strings;

@SuppressWarnings("UseSpecificCatch")
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
  public ToolResult execute(String jsonArguments) {
    try {
      SkillsService service = (SkillsService) this.agent.getService(SkillsService.NAME);

      Map<String, Object> args = gson.fromJson(jsonArguments, Map.class);
      String skillName = args != null ? (String) args.get("name") : null;
      String scriptName = args != null ? (String) args.get("script") : null;
      List<String> scriptArgs = args != null ? (List<String>) args.get("args") : null;

      if (StringUtils.isBlank(skillName) || StringUtils.isBlank(scriptName)) {
        return formatErrorResponse("Los parametros 'name' y 'script' son obligatorios.");
      }

      Skill skill = service.getSkill(this.agent, skillName);
      if (skill == null) {
        return formatErrorResponse("Skill no encontrado: " + skillName);
      }

      Files.createDirectories(agent.getPaths().getTempFolder());
      String executionId = "skill_script_" + UUID.randomUUID().toString().substring(0, 8);
      Path outputFile = agent.getPaths().getTempFolder().resolve(executionId + ".out");

      String fileExtension = skill.getScriptExtension(scriptName);
      if (Strings.CI.equals(fileExtension, "groovy")) {
        try {
          String resultResource = executeGroovyScript(skill, scriptName, scriptArgs);
          return servePaginatedResource(resultResource);
        } catch (SecurityException se) {
          LOGGER.warn("Security violation during script execution: {}", se.getMessage());
          return formatErrorResponse("Security Policy Error: " + se.getMessage());
        } catch (org.codehaus.groovy.control.MultipleCompilationErrorsException mce) {
          LOGGER.warn("Script compilation failed:\n{}", mce.getMessage());
          return formatErrorResponse("Compilation Error in script:\n" + mce.getMessage());
        } catch (Exception e) {
          String scriptReference = scriptName != null ? scriptName : "???";
          LOGGER.warn("Runtime error executing script:\n{}", scriptReference, e);
          String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
          return formatErrorResponse("Script Execution Error: " + message);
        }
      }

      ProcessBuilder pb;
      try {
        pb = skill.createScriptProcess(scriptName, scriptArgs);
      } catch (Exception ex) {
        return formatErrorResponse(ex.getMessage());
      }

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

  private String executeGroovyScript(Skill skill, String scriptName, List<String> scriptArgs) throws Exception {
    ScriptingService service = (ScriptingService) this.agent.getService(ScriptingService.NAME);

    String scriptBody = skill.getScriptBody(scriptName);

    AgentAccessControl accessControl = this.agent.getAccessControl().clone();
    accessControl.addAllowedPath(agent.getPaths().getAgentFolder());
    accessControl.addAllowedPath(agent.getPaths().getAgentFolder().resolve("var"));
    accessControl.addNonWritablePath(agent.getPaths().getAgentFolder().resolve("var"));
    AgentDelegated localAgent = new AgentDelegated(agent) {
      @Override
      public AgentAccessControl getAccessControl() {
        return accessControl;
      }      
    };
    try (ScriptEngine engine = service.createScriptEngine(localAgent)) {
      engine.addClassPath(skill.getRootPath()); 
      engine.setVariable("agent", localAgent);
      ScriptContext context = engine.getContext();
      engine.setVariable("args", scriptArgs);
      context.registerModule(new MemoryModule(context, agent));
      ScriptEngineImpl.ScriptResult result = engine.evaluate(scriptBody);
      if (result.result() != null) {
        try (BufferedWriter writer = Files.newBufferedWriter(
                result.stdout(),
                StandardCharsets.UTF_8,
                StandardOpenOption.APPEND
        )) {
          formatResult(writer, result.result());
          writer.flush();
        }
      }
      String resultResource = this.getIdFromPath(result.stdout());
      return resultResource;
    }
  }

  private void formatResult(BufferedWriter writer, Object result) throws IOException {
    if (result == null) {
      writer.append("\n");
      return;
    }
    if (result instanceof String str) {
      writer.append(str).append("\n");
      return;
    }
    if (result instanceof Number || result instanceof Boolean || result instanceof Character) {
      writer.append(result.toString()).append("\n");
      return;
    }
    if (result instanceof Iterable<?> iterable) {
      for (Object item : iterable) {
        writer.append(Objects.toString(item, "")).append("\n");
      }
      return;
    }
    if (result instanceof Map<?, ?> || result instanceof Collection<?>) {
      Gson outputGson = new GsonBuilder().setPrettyPrinting().create();
      writer.append(outputGson.toJson(result)).append("\n");
      return;
    }
    writer.append(Objects.toString(result, "")).append("\n");
  }

}
