package io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.file;

import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.AgentTool;
import io.github.jjdelcerro.noema.lib.impl.AbstractPaginatedAgentTool;
import io.github.jjdelcerro.noema.lib.impl.ToolSpecificationBuilder;
import org.apache.commons.lang3.StringUtils;

public class ReadPaginatedResourceTool extends AbstractPaginatedAgentTool {

  public static final String TOOL_NAME = "read_paginated_resource";

  public ReadPaginatedResourceTool(Agent agent) {
    super(agent);
  }

  @Override
  public ToolSpecificationBuilder getSpecification() {
    int defaultLimit = getDefaultMaxLines();
    return ToolSpecificationBuilder.create()
            .name(TOOL_NAME)
            .description("Herramienta universal para leer recursos paginados (salidas de comandos, contenido web, archivos temporales, cachés de extracción de documentos, etc.).\n"
                    + "**USO RESTRINGIDO:** Esta herramienta SOLO debe usarse cuando recibes un `HINT` explícito de otra herramienta indicándote que hay más contenido disponible. El `resource_id` debe ser exactamente el proporcionado en el HINT.\n"
                    + "\n"
                    + getPaginationSystemInstruction()
            )
            .addStringParameter("resource_id", "Identificador del recurso a leer. Debe ser exactamente el proporcionado en el HINT de otra herramienta.")
            .addIntegerParameter("offset", "Línea inicial (0-based). Default: 0.")
            .addIntegerParameter("limit", "Máximo de líneas a leer. Default: " + defaultLimit);
  }

  @Override
  public int getMode() {
    return AgentTool.MODE_READ;
  }

  @Override
  @SuppressWarnings("UseSpecificCatch")
  public String execute(String jsonArguments) {
    try {
      ReadArgs args = gson.fromJson(jsonArguments, ReadArgs.class);

      if (StringUtils.isBlank(args.resource_id)) {
        return formatErrorResponse("resource_id is required");
      }

      int offset = args.offset > 0 ? args.offset : 0;
      int limit = args.limit > 0 ? args.limit : getDefaultMaxLines();

      return servePaginatedResource(args.resource_id, offset, limit);

    } catch (Exception e) {
      LOGGER.warn("Error reading paginated resource, args=" + StringUtils.replace(jsonArguments, "\\n", " "), e);
      return formatErrorResponse("Error reading paginated resource: " + e.getMessage());
    }
  }

  private static class ReadArgs {

    String resource_id;
    int offset;
    int limit;
  }
}
