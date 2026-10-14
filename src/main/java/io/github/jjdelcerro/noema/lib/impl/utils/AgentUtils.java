package io.github.jjdelcerro.noema.lib.impl.utils;

import io.github.jjdelcerro.noema.lib.api.AgentPaths;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
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
    try (InputStream is = AgentUtils.class.getResourceAsStream(RESOURCES_BASE_FOLDER + resPath)) {
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
        LOGGER.warn("No se ha podido instalar el recurso '" + resPath + "'.", e);
      }
    }
  }

  public static String normalizeResourceName(String resource) {
    // Normalizamos: sin barra inicial ni final
    String path = resource.startsWith("/")
            ? resource.substring(1)
            : resource;
    if (path.endsWith("/")) {
      path = path.substring(0, path.length() - 1);
    }
    return path;
  }

  public static void installResources(URL resourceUrl, Path targetFolder, boolean replace) {
    try {
      if (resourceUrl == null) {
        throw new FileNotFoundException("URL de recurso nula");
      }
      
      Files.createDirectories(targetFolder);
      
      switch (resourceUrl.getProtocol()) {
        case "file" ->
          copyFromFileSystem(resourceUrl, targetFolder, replace);
        case "jar" ->
          copyFromJar(resourceUrl, targetFolder, replace);
        default ->
          throw new IOException(
                  "Protocolo no soportado: " + resourceUrl.getProtocol()
                          + " (" + resourceUrl + ")");
      }
    } catch (IOException ex) {
      throw new RuntimeException("Can't install resources",ex);
    }
  }

  private static void copyFromFileSystem(URL url, Path targetFolder, boolean replace) throws IOException {
    Path source;
    try {
      source = Paths.get(url.toURI());
    } catch (Exception e) {
      throw new IOException("URI inválida: " + url, e);
    }

    try (var stream = Files.walk(source)) {
      for (Path src : (Iterable<Path>) stream::iterator) {
        Path relative = source.relativize(src);
        Path dest = targetFolder.resolve(relative.toString());
        if (Files.isDirectory(src)) {
          Files.createDirectories(dest);
        } else {
          Files.createDirectories(dest.getParent());
          if( replace || !dest.toFile().exists() ) {
            Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
          }
        }
      }
    }
  }

  private static void copyFromJar(URL url, Path targetFolder, boolean replace) throws IOException {
    JarURLConnection conn = (JarURLConnection) url.openConnection();
    String entryName = conn.getEntryName();     // p.ej. "static/templates" o null
    String prefix = (entryName == null || entryName.isEmpty())
            ? ""
            : (entryName.endsWith("/") ? entryName : entryName + "/");

    try (JarFile jar = conn.getJarFile()) {
      Enumeration<JarEntry> entries = jar.entries();
      while (entries.hasMoreElements()) {
        JarEntry entry = entries.nextElement();
        String name = entry.getName();

        if (!prefix.isEmpty()) {
          if (!name.startsWith(prefix) || name.equals(prefix)) {
            continue;
          }
        }

        String relative = name.substring(prefix.length());
        if (relative.isEmpty()) {
          continue;
        }

        Path out = targetFolder.resolve(relative);

        if (entry.isDirectory()) {
          Files.createDirectories(out);
        } else {
          Files.createDirectories(out.getParent());
          if( replace || !out.toFile().exists() ) {
            try (InputStream in = jar.getInputStream(entry)) {
              Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
            }
          }
        }
      }
    }
  }

}
