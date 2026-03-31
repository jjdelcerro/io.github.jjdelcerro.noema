package io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.file;

import dev.langchain4j.agent.tool.JsonSchemaProperty;
import dev.langchain4j.agent.tool.ToolSpecification;
import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.AgentTool;
import io.github.jjdelcerro.noema.lib.impl.AbstractPaginatedAgentTool;
import org.apache.commons.lang3.StringUtils;
import org.apache.tika.Tika;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.jjdelcerro.noema.lib.AgentAccessControl.AccessMode.PATH_ACCESS_READ;

public class FileReadTool extends AbstractPaginatedAgentTool {

  public static final String TOOL_NAME = "file_read";

  private final Tika tika = new Tika();

  public FileReadTool(Agent agent) {
    super(agent);
  }

  @Override
  public ToolSpecification getSpecification() {
    return ToolSpecification.builder()
            .name(TOOL_NAME)
            .description("Devuelve el contenido de un archivo de texto del proyecto (código fuente, documentación, archivos de configuración, etc.).\n\n" +
                    getShortPaginationInstruction())
            .addParameter("path", JsonSchemaProperty.STRING,
                    JsonSchemaProperty.description("Ruta del archivo (relativa o absoluta)."))
            .build();
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
      
      if (StringUtils.isBlank(args.path)) {
        return formatErrorResponse("El parámetro 'path' es obligatorio.");
      }

      Path filePath = this.resolvePathOrNull(args.path, PATH_ACCESS_READ);
      if (filePath == null) {
        return formatErrorResponse("Acceso denegado o ruta fuera del sandbox: " + args.path);
      }

      if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
        return formatErrorResponse("El archivo no existe o es un directorio: " + args.path);
      }

      if (isBinaryResource(filePath)) {
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

      return servePaginatedResource(resourceId);

    } catch (Exception e) {
      LOGGER.warn("Error leyendo archivo, args=" + StringUtils.replace(jsonArguments, "\n", " "), e);
      return formatErrorResponse("Error I/O: " + e.getMessage());
    }
  }

  private boolean isBinaryResource(Path path) {
    try {
      String mimeType = tika.detect(path);

      if (mimeType.startsWith("text/")) {
        return false;
      }

      if (mimeType.contains("json")
              || mimeType.contains("xml")
              || mimeType.contains("javascript")
              || mimeType.contains("properties")
              || mimeType.contains("yaml")
              || mimeType.equals("application/x-sh")
              || mimeType.equals("application/x-bat")) {
        return false;
      }

      return true;

    } catch (IOException e) {
      return true;
    }
  }

  private static class ReadArgs {
    String path;
  }
}
