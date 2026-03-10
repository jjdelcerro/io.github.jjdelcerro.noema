package io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.identity;

import dev.langchain4j.agent.tool.ToolSpecification;
import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.AgentTool;
import io.github.jjdelcerro.noema.lib.impl.AbstractAgentTool;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Herramienta de introspección del catálogo de habilidades. Permite al agente
 * descubrir qué protocolos técnicos o flujos de trabajo (skills) tiene
 * disponibles en su memoria procedimental antes de decidir cuál cargar.
 */
public class ListSkillsTool extends AbstractAgentTool {

  private static final Logger LOGGER = LoggerFactory.getLogger(ListSkillsTool.class);
  public static final String NAME = "list_skills";

  public ListSkillsTool(Agent agent) {
    super(agent);
  }

  @Override
  public ToolSpecification getSpecification() {
    return ToolSpecification.builder()
            .name(NAME)
            .description("Consulta tu catálogo de habilidades procedimentales. "
                    + "Invocala cuando el usuario te pida realizar una tarea técnica compleja, "
                    + "un procedimiento paso a paso, un despliegue o una refactorización de código. "
                    + "Esta herramienta te devolverá una lista de nombres de habilidades y sus propósitos "
                    + "para que puedas elegir cuál cargar mediante 'load_skill'.")
            .build();
  }

  @Override
  public int getMode() {
    // Es una consulta al catálogo interno, no requiere autorización.
    return AgentTool.MODE_READ;
  }

  @Override
  public String execute(String jsonArguments) {
    try {
//      LOGGER.info("Escaneando catálogo de habilidades procedimentales...");

      // Obtener todos los recursos en la carpeta de habilidades
      Collection<Path> skillPaths = agent.getPaths().listAgentPath("var/skills");
      List<Map<String, String>> catalog = new ArrayList<>();

      if (skillPaths != null) {
        for (Path path : skillPaths) {
          String fileName = path.getFileName().toString();
          if( StringUtils.equalsIgnoreCase(fileName, "readme.md") ) {
            continue;
          }

          // Filtrar solo los archivos de referencia (.ref.md)
          if (fileName.endsWith(".ref.md")) {
            // El nombre técnico es el nombre base (ej: "deploy_plugin.ref.md" -> "deploy_plugin")
            String skillName = FilenameUtils.getBaseName(fileName);

            // Leer la descripción ligera
            String description = agent.getResourceAsString("var/skills/" + fileName);

            if (StringUtils.isNotBlank(description)) {
              catalog.add(Map.of(
                      "nombre", skillName,
                      "descripcion", description.trim()
              ));
            }
          }
        }
      }

//      // Si no hay habilidades, informar de forma coherente
//      if (catalog.isEmpty()) {
//        return "{\"status\": \"success\", \"skills\": [], \"message\": \"No hay manuales de procedimiento registrados actualmente.\"}";
//      }

      // Devolver el array JSON para evitar confusiones de parseo en el LLM
      return gson.toJson(catalog);

    } catch (Exception e) {
      LOGGER.warn("Error al listar el catálogo de habilidades", e);
      return error("Error interno al acceder al catálogo de habilidades: " + e.getMessage());
    }
  }
}
