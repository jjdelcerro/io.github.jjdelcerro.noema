package io.github.jjdelcerro.noema.ui.webclient.handlers;

import io.github.jjdelcerro.noema.lib.api.Agent;
import io.javalin.http.Context;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.tika.detect.AutoDetectReader;
import org.apache.tika.exception.TikaException;

/**
 *
 * @author jjdelcerro
 */
public class HandlerGetFileContent extends AbstractHandler {

  public HandlerGetFileContent(Agent agent) {
    super(agent);
  }
  
  @Override
  public void handle(Context ctx) throws Exception {
    String fileParam = ctx.queryParam("file");
    if (fileParam == null || fileParam.isBlank()) {
      ctx.status(400).json(Map.of("error", "Parámetro 'file' obligatorio"));
      return;
    }

    Path target = resolveFilePath(fileParam);
    if (target == null || !Files.exists(target)) {
      ctx.status(404).json(Map.of("error", "Fichero no encontrado: " + fileParam));
      return;
    }

    if (Files.isDirectory(target)) {
      ctx.status(400).json(Map.of("error", "La ruta especificada es un directorio, no un fichero: " + fileParam));
      return;
    }

    try {
      String content = readFileContent(target);
      Map<String, String> response = new LinkedHashMap<>();
      response.put("file", fileParam);
      response.put("content", content);
      ctx.json(response);
    } catch (Exception e) {
      LOGGER.error("Error leyendo contenido del fichero: {}", target, e);
      ctx.status(500).json(Map.of("error", "Error al leer el fichero: " + e.getMessage()));
    }
  }

  private String readFileContent(Path target) throws IOException, TikaException {
    // Lectura con autodeteccion de charset via Tika (soporta ISO-8859-1, UTF-8, etc.)
    StringBuilder content = new StringBuilder();
    try (InputStream in = new BufferedInputStream(new FileInputStream(target.toFile()));
            BufferedReader reader = new BufferedReader(new AutoDetectReader(in))) {
      char[] buffer = new char[8192];
      int read;
      while ((read = reader.read(buffer)) != -1) {
        content.append(buffer, 0, read);
      }
    }
    return content.toString();
  }


}
