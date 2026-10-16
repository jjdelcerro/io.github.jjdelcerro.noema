package io.github.jjdelcerro.noema.ui.webclient.handlers;

import io.github.jjdelcerro.noema.lib.api.Agent;
import io.github.jjdelcerro.noema.ui.webclient.NoemaWebServer;
import io.javalin.http.Context;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 *
 * @author jjdelcerro
 */
public class HandlerPostFileContent extends AbstractHandler {

  public static class FileContentRequest {
    public String file;
    public String content;
  }

  
  public HandlerPostFileContent(Agent agent) {
    super(agent);
  }

  @Override
  public void handle(Context ctx) throws Exception {
    FileContentRequest body = ctx.bodyAsClass(FileContentRequest.class);
    if (body == null || body.file == null || body.file.isBlank()) {
      ctx.status(400).json(Map.of("error", "El campo 'file' es obligatorio"));
      return;
    }

    Path target = resolveFilePath(body.file);
    if (target == null) {
      ctx.status(400).json(Map.of("error", "Ruta de fichero inválida: " + body.file));
      return;
    }

    try {
      if (target.getParent() != null) {
        Files.createDirectories(target.getParent());
      }
      String contentToWrite = body.content != null ? body.content : "";
      Files.writeString(target, contentToWrite, StandardCharsets.UTF_8);

      ctx.json(Map.of("status", "success", "file", body.file));
    } catch (Exception e) {
      LOGGER.error("Error escribiendo fichero: {}", target, e);
      ctx.status(500).json(Map.of("error", "Error al guardar el fichero: " + e.getMessage()));
    }
  }
  
}
