package io.github.jjdelcerro.noema.lib.impl.services.conversation.tools.file;

import io.github.jjdelcerro.noema.lib.impl.AbstractAgentTool;
import dev.langchain4j.agent.tool.JsonSchemaProperty;
import dev.langchain4j.agent.tool.ToolSpecification;
import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.impl.services.conversation.ConversationServiceImpl;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.tika.Tika;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static io.github.jjdelcerro.noema.lib.AgentAccessControl.AccessMode.PATH_ACCESS_READ;

public class FileExtractTextTool extends AbstractAgentTool {

  public static final String TOOL_NAME = "file_extract_text";
  private final Tika tika = new Tika();

  public FileExtractTextTool(Agent agent) {
    super(agent);
  }

  @Override
  public ToolSpecification getSpecification() {
    return ToolSpecification.builder()
            .name(TOOL_NAME)
            .description("""
                    Extrae el texto de archivos complejos (PDF, DOCX, etc). 
                    Soporta paginación. Si el contenido es largo, usa 'offset' y 'limit' para leer por partes.
                    """)
            .addParameter("path", JsonSchemaProperty.STRING, JsonSchemaProperty.description("Ruta del archivo binario."))
            .addParameter("offset", JsonSchemaProperty.INTEGER, JsonSchemaProperty.description("Línea inicial del texto extraído."))
            .addParameter("limit", JsonSchemaProperty.INTEGER, JsonSchemaProperty.description("Líneas a leer."))
            .build();
  }

  @Override
  public String execute(String jsonArguments) {
    try {
      Map<String, Object> args = gson.fromJson(jsonArguments, Map.class);
      String relPath = (String) args.get("path");
      int offset = args.get("offset") != null ? ((Double) args.get("offset")).intValue() : 0;
      int limit = args.get("limit") != null ? ((Double) args.get("limit")).intValue() : 1000;

      Path sourcePath = this.resolvePathOrNull(relPath, PATH_ACCESS_READ);
      if (sourcePath == null || !Files.exists(sourcePath)) {
        return error("Archivo no encontrado o acceso denegado.");
      }

      // 1. Obtener/Crear versión de texto en caché
      Path cachedTextPath = getCachedTextPath(sourcePath);

      // 2. Delegar lectura a FileReadTool
      ConversationServiceImpl conv = (ConversationServiceImpl) agent.getService(ConversationServiceImpl.NAME);
      FileReadTool fileRead = (FileReadTool) conv.getAvailableTool(FileReadTool.TOOL_NAME);

      // Usamos el execute interno con el nombre de esta herramienta (para el HINT) y la ruta original
      return fileRead.execute(cachedTextPath, relPath, TOOL_NAME, offset, limit);

    } catch (Exception e) {
      return error("Error extrayendo texto: " + e.getMessage());
    }
  }

  private Path getCachedTextPath(Path sourcePath) throws Exception {
    Path cacheDir = getTemporaryFolder();
    if (!Files.exists(cacheDir)) {
      Files.createDirectories(cacheDir);
    }

    // Nombre basado en Hash de la ruta absoluta para evitar colisiones
    String fileHash = DigestUtils.sha256Hex(sourcePath.toAbsolutePath().toString());
    Path cachedFile = cacheDir.resolve(fileHash + ".txt");

    // Invalida si el origen es más nuevo que la caché
    if (!Files.exists(cachedFile)
            || Files.getLastModifiedTime(sourcePath).toMillis() > Files.getLastModifiedTime(cachedFile).toMillis()) {

      LOGGER.info("Extrayendo texto (Tika) de: " + sourcePath);
      String text = tika.parseToString(sourcePath);
      Files.writeString(cachedFile, text, StandardCharsets.UTF_8);
    }

    return cachedFile;
  }

  private Path getTemporaryFolder() {
    return agent.getPaths().getCacheFolder().resolve(TOOL_NAME);
  }
}
