package io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.identity;

import dev.langchain4j.agent.tool.JsonSchemaProperty;
import dev.langchain4j.agent.tool.ToolSpecification;
import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.AgentTool;
import io.github.jjdelcerro.noema.lib.impl.AbstractAgentTool;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Herramienta de carga de protocolos técnicos. Permite al agente recuperar el
 * contenido íntegro de un manual de procedimiento (.md) una vez que ha
 * identificado su relevancia a través de 'list_skills'.
 */
@SuppressWarnings("UseSpecificCatch")
public class LoadSkillTool extends AbstractAgentTool {

  private static final Logger LOGGER = LoggerFactory.getLogger(LoadSkillTool.class);
  public static final String NAME = "load_skill";

  public LoadSkillTool(Agent agent) {
    super(agent);
  }

  @Override
  public ToolSpecification getSpecification() {
    return ToolSpecification.builder()
            .name(NAME)
            .description("Carga un manual de procedimiento específico desde tu memoria procedimental. "
                    + "Úsalo para obtener las instrucciones paso a paso, flujos de trabajo o comandos exactos "
                    + "de una habilidad que hayas identificado previamente en 'list_skills'. "
                    + "La carga es volátil: el manual estará disponible para razonar en este turno, "
                    + "pero no saturará tu memoria a largo plazo.")
            .addParameter("skill_id", JsonSchemaProperty.STRING,
                    JsonSchemaProperty.description("El identificador único de la habilidad (ej: 'java_refactor', 'deploy_plugin'). "
                            + "Debe ser el valor obtenido del campo 'nombre' al usar 'list_skills'."))
            .build();
  }

  @Override
  public int getMode() {
    // Es una herramienta de lectura de manuales internos. No requiere confirmación.
    return AgentTool.MODE_READ;
  }

  @Override
  public String execute(String jsonArguments) {
    try {
      // 1. Obtener el ID de la habilidad desde los argumentos
      Map<String, String> args = gson.fromJson(jsonArguments, Map.class);
      String skillId = args.get("skill_id");

      if (StringUtils.isBlank(skillId)) {
        return error("El parámetro 'skill_id' es obligatorio.");
      }

      // 2. Construir la ruta relativa al fichero denso (.md)
      String relativePath = "var/skills/" + skillId + ".md";

      // 3. Verificar existencia antes de intentar cargar
      Path skillPath = agent.getPaths().getAgentPath(relativePath);
      if (skillPath == null || !Files.exists(skillPath)) {
        LOGGER.warn("Habilidad no encontrada en el sistema de archivos: {}", relativePath);
        return error("El manual de procedimiento para '" + skillId + "' no existe.");
      }

      // 4. Cargar el contenido usando el método mejorado de Agent
      LOGGER.info("Cargando protocolo procedimental: {}", skillId);
      String content = agent.getResourceAsString(relativePath);

      if (StringUtils.isBlank(content)) {
        return error("El manual de la habilidad '" + skillId + "' está vacío.");
      }

      // Devolvemos el texto plano directamente para que el LLM lo procese
      return content;

    } catch (Exception e) {
      LOGGER.error("Error crítico al cargar la habilidad", e);
      return error("Error interno al acceder a la memoria procedimental: " + e.getMessage());
    }
  }
}
