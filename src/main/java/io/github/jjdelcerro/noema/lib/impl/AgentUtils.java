package io.github.jjdelcerro.noema.lib.impl;

import io.github.jjdelcerro.noema.lib.AgentPaths;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author jjdelcerro
 */
public class AgentUtils {
  
  public static final Logger LOGGER = LoggerFactory.getLogger(AgentUtils.class);
  
  public static void installResource(AgentPaths paths, String resPath) {
    String resourceBase = "/io/github/jjdelcerro/noema/lib/impl/resources/";
    Path targetPath = paths.getAgentFolder().resolve(resPath);
    if (!Files.exists(targetPath)) {
      try {
        Files.createDirectories(targetPath.getParent());

        try (InputStream is = AgentUtils.class.getResourceAsStream(resourceBase + resPath)) {
          if (is != null) {
            Files.copy(is, targetPath, StandardCopyOption.REPLACE_EXISTING);
          } else {
            LOGGER.warn("Recurso no encontrado en el classpath " + resourceBase + resPath);
          }
        }
      } catch (Exception e) {
        LOGGER.warn("No se ha podido instalar el recurso '"+resPath+"'.",e);
      }
    }
  }
}
