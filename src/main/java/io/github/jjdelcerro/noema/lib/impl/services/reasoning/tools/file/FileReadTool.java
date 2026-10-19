package io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.file;

import io.github.jjdelcerro.noema.lib.api.Agent;
import io.github.jjdelcerro.noema.lib.api.AgentTool;
import io.github.jjdelcerro.noema.lib.impl.tools.AbstractPaginatedAgentTool;
import io.github.jjdelcerro.noema.lib.impl.tools.ToolSpecificationBuilder;
import io.github.jjdelcerro.noema.lib.impl.utils.FsUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.tika.Tika;

import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.jjdelcerro.noema.lib.api.AgentAccessControl.AccessMode.PATH_ACCESS_READ;

public class FileReadTool extends AbstractPaginatedAgentTool {

  public static final String TOOL_NAME = "file_read";

  private final Tika tika = new Tika();

  public FileReadTool(Agent agent) {
    super(agent);
  }

  @Override
  public ToolSpecificationBuilder getSpecification() {
    int defaultLimit = getDefaultMaxLines();
    return ToolSpecificationBuilder.create()
            .name(TOOL_NAME)
            .description("Devuelve el contenido de un archivo de texto del proyecto (código fuente, "
                    + "documentación, archivos de configuración, etc.).\n"
                    + "Permite lectura completa o acotada a un rango de líneas mediante 'offset' y 'limit'.\n\n"
                    + "PROHIBIDO usar esta herramienta para cálculos acumulativos, agregaciones, medias o "
                    + "procesamiento masivo."
                    + getShortPaginationInstruction())
            .addStringParameter("path", false, "Ruta del archivo (relativa o absoluta).")
            .addIntegerParameter("offset", true, "Línea inicial (0-based) desde donde empezar a leer. Opcional, por defecto 0.")
            .addIntegerParameter("limit", true, "Número máximo de líneas a leer. Opcional, por defecto " + defaultLimit + ".");
  }

  @Override
  public int getMode() {
    return AgentTool.MODE_READ;
  }

  @Override
  @SuppressWarnings("UseSpecificCatch")
  public ToolResult execute(String jsonArguments) {
    try {
      ReadArgs args = gson.fromJson(jsonArguments, ReadArgs.class);

      if (args == null || StringUtils.isBlank(args.path)) {
        return formatErrorResponse("El parámetro 'path' es obligatorio.");
      }

      Path filePath = this.resolvePathOrNull(args.path, PATH_ACCESS_READ);
      if (filePath == null) {
        return formatErrorResponse("Acceso denegado o ruta fuera del sandbox: " + args.path);
      }

      if (!Files.exists(filePath)) {
        return formatErrorResponse("El archivo no existe: " + args.path);
      }

      if (Files.isDirectory(filePath)) {
        return formatErrorResponse("La ruta es un directorio: " + args.path);
      }

      if (!Files.isRegularFile(filePath)) {
        return formatErrorResponse("El archivo no es un regular-file: " + args.path);
      }

      if (FsUtils.isBinaryResource(filePath)) {
        String mime = tika.detect(filePath);
        return formatErrorResponse("El archivo parece binario (" + mime + "). Para archivos binarios usa 'file_extract_text' o verifica con 'file_find'.");
      }

      if (!Files.isReadable(filePath)) {
        return formatErrorResponse("El archivo no es legible: " + filePath);
      }

      String resourceId = getIdFromPath(filePath);
      if (resourceId == null) {
        return formatErrorResponse("Error generando resource_id para el archivo: " + filePath);
      }

      int offset = (args.offset != null && args.offset > 0) ? args.offset : 0;
      int limit = (args.limit != null && args.limit > 0) ? args.limit : getDefaultMaxLines();

      return servePaginatedResource(resourceId, offset, limit);

    } catch (Exception e) {
      LOGGER.warn("Error leyendo archivo, args=" + StringUtils.replace(jsonArguments, "\n", " "), e);
      return formatErrorResponse("Error I/O: " + e.getMessage());
    }
  }

  private static class ReadArgs {

    String path;
    Integer offset;
    Integer limit;
  }
}
