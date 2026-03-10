package io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.identity;

import dev.langchain4j.agent.tool.JsonSchemaProperty;
import dev.langchain4j.agent.tool.ToolSpecification;
import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.AgentTool;
import io.github.jjdelcerro.noema.lib.impl.AbstractAgentTool;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Herramienta de recuperación de conocimiento denso del entorno. Permite al
 * agente realizar el "page-in" de módulos biográficos o técnicos que ha
 * identificado previamente en su índice de referencias (.ref.md).
 */
@SuppressWarnings("UseSpecificCatch")
public class ConsultEnvironTool extends AbstractAgentTool {

  private static final Logger LOGGER = LoggerFactory.getLogger(ConsultEnvironTool.class);
  public static final String NAME = "consult_environ";

  public ConsultEnvironTool(Agent agent) {
    super(agent);
  }

  @Override
  public ToolSpecification getSpecification() {
    return ToolSpecification.builder()
            .name(NAME)
            .description("Recupera el contenido completo de un módulo de conocimiento sobre el entorno"
                    + "(biografía, gustos, proyectos, marcos de investigación del usuario). "
                    + "Úsalo exclusivamente cuando el índice de la sección [CONSCIENCIA DE ENTORNO] "
                    + "sugiera que un módulo contiene información crítica para sintonizar con la petición del usuario.")
            .addParameter("module_name", JsonSchemaProperty.STRING,
                    JsonSchemaProperty.description("El ID del módulo a consultar (ej: '01_personal_joaquin', '05_pasiones_scifi'). "
                            + "Debe coincidir con el ID listado en el índice de entorno."))
            .build();
  }

  @Override
  public int getMode() {
    // Al ser una herramienta de lectura de identidad interna, no requiere confirmación humana.
    return AgentTool.MODE_READ;
  }

  @Override
  public String execute(String jsonArguments) {
    try {
      // 1. Parsear argumentos
      Map<String, String> args = gson.fromJson(jsonArguments, Map.class);
      String moduleName = args.get("module_name");

      if (moduleName == null || moduleName.isBlank()) {
        return error("El parámetro 'module_name' es obligatorio.");
      }

      // 2. Construir la ruta relativa dentro de la jerarquía de identidad
      // Usamos .md porque consultamos el fichero denso, no la referencia.
      String relativePath = "identity/environ/" + moduleName + ".md";

      // 3. Localizar el archivo usando la infraestructura de AgentPaths
      Path modulePath = agent.getPaths().getAgentPath(relativePath);

      if (modulePath == null || !Files.exists(modulePath)) {
        LOGGER.warn("Módulo de entorno no encontrado: {}", relativePath);
        return error("El módulo '" + moduleName + "' no existe en la biblioteca de entorno.");
      }

      // 4. Leer y devolver el contenido íntegro
      LOGGER.info("Cargando conocimiento denso del módulo: {}", moduleName);
      return Files.readString(modulePath, StandardCharsets.UTF_8);

    } catch (Exception e) {
      LOGGER.error("Error consultando módulo de entorno", e);
      return error("Error interno al acceder a la identidad: " + e.getMessage());
    }
  }
}
