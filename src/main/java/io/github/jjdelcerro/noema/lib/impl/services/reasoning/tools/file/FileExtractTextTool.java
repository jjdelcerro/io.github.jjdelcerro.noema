package io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.file;

import dev.langchain4j.agent.tool.JsonSchemaProperty;
import dev.langchain4j.agent.tool.ToolSpecification;
import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.AgentTool;
import io.github.jjdelcerro.noema.lib.impl.AbstractPaginatedAgentTool;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import org.apache.tika.Tika;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.jjdelcerro.noema.lib.AgentAccessControl.AccessMode.PATH_ACCESS_READ;

public class FileExtractTextTool extends AbstractPaginatedAgentTool {

  public static final String TOOL_NAME = "file_extract_text";
  private final Tika tika = new Tika();

  public FileExtractTextTool(Agent agent) {
    super(agent);
  }

  @Override
  public ToolSpecification getSpecification() {
    return ToolSpecification.builder()
            .name(TOOL_NAME)
            .description("Extrae el texto de archivos binarios complejos como PDF, DOCX, u otros documentos formateados.\n" +
                    "RESTRICCIONES:\n" +
                    "  • Solo archivos dentro del sandbox del proyecto.\n" +
                    "  • La extracción puede ser costosa computacionalmente para documentos grandes.\n" +
                    "\n" +
                    getPaginationSystemInstruction())
            .addParameter("path", JsonSchemaProperty.STRING, 
                    JsonSchemaProperty.description("Ruta del archivo binario (PDF, DOCX, etc.)."))
            .build();
  }

  @Override
  public int getMode() {
    return AgentTool.MODE_READ;
  }

  @Override
  @SuppressWarnings("UseSpecificCatch")
  public String execute(String jsonArguments) {
    String pathForLog = "unknown";
    try {
      ReadArgs args = gson.fromJson(jsonArguments, ReadArgs.class);

      if (args.path == null || args.path.trim().isEmpty()) {
        return formatErrorResponse("El parámetro 'path' es obligatorio.");
      }

      pathForLog = args.path;

      Path sourcePath = this.resolvePathOrNull(args.path, PATH_ACCESS_READ);
      if (sourcePath == null || !Files.exists(sourcePath)) {
        return formatErrorResponse("Archivo no encontrado o acceso denegado: " + args.path);
      }

      if (!Files.isRegularFile(sourcePath)) {
        return formatErrorResponse("La ruta no corresponde a un archivo regular: " + args.path);
      }

      if (!Files.isReadable(sourcePath)) {
        return formatErrorResponse("El archivo no es legible: " + sourcePath);
      }

      Path cachedTextPath = getCachedTextPath(sourcePath);

      String resourceId = getIdFromPath(cachedTextPath);
      if (resourceId == null) {
        return formatErrorResponse("Error generando resource_id para el archivo extraído: " + cachedTextPath);
      }

      return servePaginatedResource(resourceId);

    } catch (Exception e) {
      LOGGER.warn("Error extrayendo texto, path=" + pathForLog, e);
      return formatErrorResponse("Error extrayendo texto: " + e.getMessage());
    }
  }

  private Path getCachedTextPath(Path sourcePath) throws Exception {
    Path cacheDir = agent.getPaths().getCacheFolder().resolve(TOOL_NAME);
    if (!Files.exists(cacheDir)) {
      Files.createDirectories(cacheDir);
    }

    String fileHash = DigestUtils.sha256Hex(sourcePath.toAbsolutePath().toString());
    Path cachedFile = cacheDir.resolve(fileHash + ".txt");

    if (!Files.exists(cachedFile)
            || Files.getLastModifiedTime(sourcePath).toMillis() > Files.getLastModifiedTime(cachedFile).toMillis()) {

      LOGGER.info("Extrayendo texto (Tika) de: " + sourcePath);

      try (Reader reader = tika.parse(sourcePath);
           Writer writer = Files.newBufferedWriter(cachedFile, StandardCharsets.UTF_8)) {
        IOUtils.copy(reader, writer);
      }
    }

    return cachedFile;
  }

  private static class ReadArgs {
    String path;
  }
}
