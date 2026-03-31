package io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.file;

import dev.langchain4j.agent.tool.JsonSchemaProperty;
import dev.langchain4j.agent.tool.ToolSpecification;
import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.AgentTool;
import io.github.jjdelcerro.noema.lib.impl.AbstractPaginatedAgentTool;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.tika.Tika;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.github.jjdelcerro.noema.lib.AgentAccessControl.AccessMode.PATH_ACCESS_READ;

public class FileReadSelectorsTool extends AbstractPaginatedAgentTool {

  public static final String TOOL_NAME = "file_read_selectors";
  private static final long CACHE_TTL_MS = 10 * 60 * 1000;

  private final Tika tika = new Tika();

  public FileReadSelectorsTool(Agent agent) {
    super(agent);
  }

  @Override
  public ToolSpecification getSpecification() {
    return ToolSpecification.builder()
            .name(TOOL_NAME)
            .description("Lee contenido de múltiples archivos mediante rutas exactas o patrones glob (ej: 'src/**/*.java').\n" +
                    "\n" +
                    getShortPaginationInstruction())
            .addParameter("selectors", JsonSchemaProperty.ARRAY,
                    JsonSchemaProperty.description("Lista de rutas o patrones glob para seleccionar archivos."))
            .build();
  }

  @Override
  public int getMode() {
    return AgentTool.MODE_READ;
  }

  @Override
  @SuppressWarnings("UseSpecificCatch")
  public String execute(String jsonArguments) {
    String selectorLog = "unknown";
    try {
      ReadArgs args = gson.fromJson(jsonArguments, ReadArgs.class);

      if (args.selectors == null || args.selectors.isEmpty()) {
        return formatErrorResponse("La lista de selectores está vacía.");
      }

      selectorLog = String.join(", ", args.selectors);

      List<Path> resolvedPaths = resolveSelectors(args.selectors);
      if (resolvedPaths.isEmpty()) {
        return formatErrorResponse("No se encontraron archivos para los selectores proporcionados: " + selectorLog);
      }

      Path bundleFile = getOrCreateBundleFile(args.selectors, resolvedPaths);
      String resourceId = getIdFromPath(bundleFile);

      if (resourceId == null) {
        return formatErrorResponse("Error generando resource_id para el bundle de archivos.");
      }

      return servePaginatedResource(resourceId);

    } catch (Exception e) {
      LOGGER.warn("Error leyendo selectores, selectors=" + selectorLog, e);
      return formatErrorResponse("Error procesando selectores: " + e.getMessage());
    }
  }

  private Path getOrCreateBundleFile(List<String> selectors, List<Path> resolvedPaths) throws IOException {
    StringJoiner pathJoiner = new StringJoiner("|");
    resolvedPaths.stream()
            .map(p -> p.toAbsolutePath().toString())
            .sorted()
            .forEach(pathJoiner::add);

    String allPaths = pathJoiner.toString();
    String bundleHash = DigestUtils.sha256Hex(allPaths);
    Path bundleFile = agent.getPaths().getTempFolder().resolve(TOOL_NAME + "_" + bundleHash + ".txt");

    if (cacheIsValid(bundleFile, resolvedPaths)) {
      LOGGER.debug("Usando bundle cacheado: " + bundleFile.getFileName());
      return bundleFile;
    }

    LOGGER.info("Creando nuevo bundle para selectores: " + String.join(", ", selectors));
    createBundleContent(bundleFile, resolvedPaths);

    return bundleFile;
  }

  private boolean cacheIsValid(Path bundleFile, List<Path> resolvedPaths) {
    if (!Files.exists(bundleFile)) {
      return false;
    }

    try {
      long bundleTimestamp = Files.getLastModifiedTime(bundleFile).toMillis();
      long currentTime = System.currentTimeMillis();

      if (currentTime - bundleTimestamp > CACHE_TTL_MS) {
        return false;
      }

      for (Path path : resolvedPaths) {
        try {
          if (Files.getLastModifiedTime(path).toMillis() > bundleTimestamp) {
            return false;
          }
        } catch (IOException e) {
          LOGGER.warn("No se pudo obtener timestamp del archivo: " + path, e);
          return false;
        }
      }

      return true;

    } catch (IOException e) {
      LOGGER.warn("Error validando caché del bundle: " + bundleFile, e);
      return false;
    }
  }

  private void createBundleContent(Path bundleFile, List<Path> paths) throws IOException {
    Path parentDir = bundleFile.getParent();
    if (parentDir != null && !Files.exists(parentDir)) {
      Files.createDirectories(parentDir);
    }

    try (Stream<String> contentStream = getSelectedContents(paths)) {
      String content = contentStream.collect(Collectors.joining("\n"));
      Files.writeString(bundleFile, content, StandardCharsets.UTF_8);
    }
  }

  private Stream<String> getSelectedContents(List<Path> paths) {
    return paths.stream().flatMap(path -> {
      try {
        String path_s = path.toAbsolutePath().toString();
        String mimeType = tika.detect(path);
        boolean isBinary = isBinaryMime(mimeType);

        Stream<String> header = Stream.of(String.format("--- %s [%s] ---", path_s, isBinary ? "BINARY" : "TEXT"));

        Stream<String> content;
        if (isBinary) {
          content = Stream.of("[Archivo binario omitido. Usa 'file_extract_text'] para extraer contenido.");
        } else {
          content = Files.lines(path, StandardCharsets.UTF_8);
        }

        Stream<String> footer = Stream.of("");

        return Stream.concat(header, Stream.concat(content, footer));

      } catch (Exception e) {
        return Stream.of("--- ERROR leyendo " + path + ": " + e.getMessage() + " ---", "");
      }
    });
  }

  private List<Path> resolveSelectors(List<String> selectors) throws IOException {
    Set<Path> resolved = new LinkedHashSet<>();
    Path rootPath = agent.getAccessControl().resolvePathOrNull(".", PATH_ACCESS_READ);

    if (rootPath == null) {
      throw new IOException("No se pudo determinar la ruta raíz del proyecto");
    }

    for (String selector : selectors) {
      if (selector == null || selector.trim().isEmpty()) {
        continue;
      }

      if (selector.contains("*") || selector.contains("?")) {
        final PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + selector);
        final Path finalRootPath = rootPath;

        Files.walkFileTree(rootPath, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE, 
          new SimpleFileVisitor<Path>() {

          private boolean shouldSkipDirectory(Path dir) {
            String name = dir.getFileName() == null ? "" : dir.getFileName().toString();
            return name.equals("target") || name.equals(".git") || name.equals(".idea") || name.equals("node_modules");
          }

          @Override
          public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
            return shouldSkipDirectory(dir) ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
          }

          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
            Path relativeForMatcher = finalRootPath.relativize(file);

            if (matcher.matches(relativeForMatcher)) {
              Path safePath = agent.getAccessControl().resolvePathOrNull(file.toString(), PATH_ACCESS_READ);
              if (safePath != null && Files.isRegularFile(safePath)) {
                resolved.add(safePath.toAbsolutePath().normalize());
              }
            }
            return FileVisitResult.CONTINUE;
          }
        });
      } else {
        Path p = agent.getAccessControl().resolvePathOrNull(selector, PATH_ACCESS_READ);
        if (p != null && Files.exists(p) && Files.isRegularFile(p)) {
          try {
            resolved.add(p.toRealPath());
          } catch (IOException e) {
            resolved.add(p.normalize());
          }
        }
      }
    }

    return resolved.stream().sorted().collect(Collectors.toList());
  }

  private boolean isBinaryMime(String mimeType) {
    if (mimeType.startsWith("text/")) {
      return false;
    }
    if (mimeType.contains("json")
            || mimeType.contains("xml")
            || mimeType.contains("javascript")
            || mimeType.contains("properties")
            || mimeType.contains("yaml")
            || mimeType.equals("application/x-sh")) {
      return false;
    }
    return true;
  }

  private static class ReadArgs {
    List<String> selectors;
  }
}
