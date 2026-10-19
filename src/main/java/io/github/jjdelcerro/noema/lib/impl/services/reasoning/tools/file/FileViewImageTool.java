package io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.file;

import io.github.jjdelcerro.noema.lib.api.Agent;
import io.github.jjdelcerro.noema.lib.api.AgentTool;
import io.github.jjdelcerro.noema.lib.impl.tools.AbstractAgentTool;
import io.github.jjdelcerro.noema.lib.impl.tools.ToolSpecificationBuilder;
import org.apache.commons.lang3.StringUtils;
import org.apache.tika.Tika;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static io.github.jjdelcerro.noema.lib.api.AgentAccessControl.AccessMode.PATH_ACCESS_READ;

/**
 * Returns the binary content of an image file so the model can see it.
 * Unlike file_read, the result travels as an ImageContent inside the tool
 * result message instead of being serialized to text.
 */
public class FileViewImageTool extends AbstractAgentTool {

  public static final String TOOL_NAME = "file_view_image";

  /**
   * ImageContent.from(Path, String) loads the whole file into memory, so the
   * size is checked here before building the message.
   */
  private static final long MAX_IMAGE_BYTES = 5L * 1024L * 1024L;

  /**
   * Formats most vision providers accept. Anything else is rejected here
   * instead of surfacing later as an opaque HTTP 400 from the provider.
   */
  private static final Set<String> SUPPORTED_MIME_TYPES = Set.of(
          "image/png",
          "image/jpeg",
          "image/webp",
          "image/gif");

  private final Tika tika = new Tika();

  public FileViewImageTool(Agent agent) {
    super(agent);
  }

  @Override
  public ToolSpecificationBuilder getSpecification() {
    return ToolSpecificationBuilder.create()
            .name(TOOL_NAME)
            .description("Carga una imagen en la conversación como entrada visual. "
                    + "Usa esta herramienta cuando el usuario mencione la ruta de una imagen o cuando "
                    + "necesites inspeccionar visualmente un archivo de imagen "
                    + "en la conversación. La herramienta se encarga de buscarla y adjuntarla al "
                    + "historial: en tu siguiente turno podrás verla directamente con tus propias "
                    + "capacidades visuales y razonar sobre ella. La imagen permanece en el contexto "
                    + "en los turnos posteriores. No extrae texto: si la imagen contiene texto, "
                    + "tendrás que leerlo tú al verla.")
            .addStringParameter("path", false,
                    "Ruta absoluta de la imagen. "
                    + "Ejemplos: '/doc/capturas/error.png', '/tmp/chart.jpg'. "
                    + "Formatos soportados: PNG, JPEG, WebP, GIF.");
  }

  @Override
  public int getMode() {
    return AgentTool.MODE_READ;
  }

  @Override
  @SuppressWarnings("UseSpecificCatch")
  public ToolResult execute(String jsonArguments) {
    try {
      ViewImageArgs args = gson.fromJson(jsonArguments, ViewImageArgs.class);

      if (args == null || StringUtils.isBlank(args.path)) {
        return error("El parametro 'path' es obligatorio.");
      }

      Path filePath = this.resolvePathOrNull(args.path, PATH_ACCESS_READ);
      if (filePath == null) {
        return error("Acceso denegado o ruta fuera del sandbox: " + args.path); // TODO: eliminar el concepto de sandbox e introducir aqui los sitios permitidos.
      }

      if (!Files.exists(filePath)) {
        return error("El archivo no existe: " + args.path);
      }

      if (Files.isDirectory(filePath)) {
        return error("La ruta es un directorio: " + args.path);
      }

      if (!Files.isRegularFile(filePath)) {
        return error("El archivo no es un regular-file: " + args.path);
      }

      if (!Files.isReadable(filePath)) {
        return error("El archivo no es legible: " + filePath);
      }

      long size = Files.size(filePath);
      if (size > MAX_IMAGE_BYTES) {
        return error("La imagen ocupa " + size + " bytes y el limite es " + MAX_IMAGE_BYTES
                + " bytes. Reduce su tamano antes de verla.");
      }

      String mimeType = tika.detect(filePath);
      if (mimeType == null || !mimeType.startsWith("image/")) {
        return error("El archivo " + args.path + " es " + mimeType + ", no una imagen.");
      }

      if (!SUPPORTED_MIME_TYPES.contains(mimeType)) {
        return error("El formato " + mimeType + " no esta soportado por el modelo. "
                + "Convierte la imagen a png o jpeg.");
      }

      return resultImage(filePath);

    } catch (Exception e) {
      LOGGER.warn("Error devolviendo imagen, args=" + StringUtils.replace(jsonArguments, "\n", " "), e);
      return error("Error I/O: " + e.getMessage());
    }
  }

  private static class ViewImageArgs {

    String path;
  }
}
