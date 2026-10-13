package io.github.jjdelcerro.noema.lib.impl.utils;

import io.github.jjdelcerro.noema.lib.api.AgentPaths;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.List;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author jjdelcerro
 */
public class AgentUtils {
  
  public static final Logger LOGGER = LoggerFactory.getLogger(AgentUtils.class);
  
  public static final String RESOURCES_BASE_FOLDER = "/io/github/jjdelcerro/noema/lib/impl/resources/";
  
  public static List<String> readLinesResourceFile(String resPath) {
    try ( InputStream is = AgentUtils.class.getResourceAsStream(RESOURCES_BASE_FOLDER + resPath) ) {
      List<String> lines = IOUtils.readLines(is, StandardCharsets.UTF_8);
      return lines;
    } catch (IOException ex) {
      LOGGER.warn("Recurso no encontrado en el classpath " + RESOURCES_BASE_FOLDER + resPath);
      return Collections.EMPTY_LIST;
    }
  }
  
  public static void installResource(AgentPaths paths, String resPath) {
    Path targetPath = paths.getAgentFolder().resolve(resPath);
    if (!Files.exists(targetPath)) {
      try {
        Files.createDirectories(targetPath.getParent());

        try (InputStream is = AgentUtils.class.getResourceAsStream(RESOURCES_BASE_FOLDER + resPath)) {
          if (is != null) {
            Files.copy(is, targetPath, StandardCopyOption.REPLACE_EXISTING);
          } else {
            LOGGER.warn("Recurso no encontrado en el classpath " + RESOURCES_BASE_FOLDER + resPath);
          }
        }
      } catch (Exception e) {
        LOGGER.warn("No se ha podido instalar el recurso '"+resPath+"'.",e);
      }
    }
  }
}
