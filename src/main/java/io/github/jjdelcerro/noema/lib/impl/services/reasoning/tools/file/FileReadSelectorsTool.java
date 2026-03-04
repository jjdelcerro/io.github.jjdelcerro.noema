package io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.file;

import io.github.jjdelcerro.noema.lib.impl.AbstractAgentTool;
import dev.langchain4j.agent.tool.JsonSchemaProperty;
import dev.langchain4j.agent.tool.ToolSpecification;
import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.impl.services.reasoning.ReasoningServiceImpl;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.collections4.map.LRUMap;
import org.apache.tika.Tika;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.github.jjdelcerro.noema.lib.AgentAccessControl.AccessMode.PATH_ACCESS_READ;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FileReadSelectorsTool extends AbstractAgentTool {

  public static final String TOOL_NAME = "file_read_selectors";
  private static final int DEFAULT_MAX_LINES = 1000;
  private static final int CACHE_SIZE = 10;
  private static final long CACHE_TTL_MS = 10 * 60 * 1000; // 10 minutos

  private final Tika tika = new Tika();

  // Caché de Bundles: Hash de lista de archivos -> (lineCount, timestamp)
  private final Map<String, BundleMeta> bundleCache = Collections.synchronizedMap(new LRUMap<>(CACHE_SIZE));

  private record BundleMeta(long lineCount, long timestamp) {

  }

  public FileReadSelectorsTool(Agent agent) {
    super(agent);
  }

  @Override
  public ToolSpecification getSpecification() {
    return ToolSpecification.builder()
            .name(TOOL_NAME)
            .description("""
                    Lee contenido de múltiples archivos mediante rutas exactas o patrones glob (ej: 'src/**/*.java').
                    Soporta paginación. Si el conjunto de archivos es muy grande, usa 'offset' y 'limit' para navegar.
                    """)
            .addParameter("selectors", JsonSchemaProperty.ARRAY,
                    JsonSchemaProperty.description("Lista de rutas o patrones glob."))
            .addParameter("offset", JsonSchemaProperty.INTEGER,
                    JsonSchemaProperty.description("Línea inicial (0-based) del conjunto. Default: 0."))
            .addParameter("limit", JsonSchemaProperty.INTEGER,
                    JsonSchemaProperty.description("Líneas a leer en este bloque. Default: " + DEFAULT_MAX_LINES))
            .build();
  }

  @Override
  public String execute(String jsonArguments) {
    try {
      Map<String, Object> args = new com.google.gson.Gson().fromJson(jsonArguments, Map.class);
      List<String> selectors = (List<String>) args.get("selectors");
      int offset = args.get("offset") != null ? ((Double) args.get("offset")).intValue() : 0;
      int limit = args.get("limit") != null ? ((Double) args.get("limit")).intValue() : DEFAULT_MAX_LINES;

      if (selectors == null || selectors.isEmpty()) {
        return "{\"status\": \"error\", \"message\": \"Lista de selectores vacía.\"}";
      }

      // 1. Resolver y normalizar archivos (Ordenados para hash estable)
      List<Path> resolvedPaths = resolveSelectors(selectors);
      Collections.sort(resolvedPaths);

      if (resolvedPaths.isEmpty()) {
        return "{\"status\": \"error\", \"message\": \"No se encontraron archivos para esos selectores.\"}";
      }

      // 2. Identificar el "Bundle" mediante Hash de sus rutas
      String bundleHash = calculateBundleHash(resolvedPaths);

      // 3. Gestionar el conteo de líneas con la estrategia de "Sincronización en Offset 0"
      long totalLines;
      if (offset == 0) {
        // Siempre recalcular en la primera página
        totalLines = countTotalLines(resolvedPaths);
        bundleCache.put(bundleHash, new BundleMeta(totalLines, System.currentTimeMillis()));
      } else {
        // Intentar recuperar de caché para páginas sucesivas
        BundleMeta meta = bundleCache.get(bundleHash);
        if (meta != null && (System.currentTimeMillis() - meta.timestamp < CACHE_TTL_MS)) {
          totalLines = meta.lineCount;
        } else {
          // Si expiró o no está, recalcular
          totalLines = countTotalLines(resolvedPaths);
          bundleCache.put(bundleHash, new BundleMeta(totalLines, System.currentTimeMillis()));
        }
      }

      // 4. Delegar al motor de FileReadTool pasándole el Stream perezoso
      ReasoningServiceImpl reasoning = (ReasoningServiceImpl) agent.getService(ReasoningServiceImpl.NAME);
      FileReadTool fileRead = (FileReadTool) reasoning.getAvailableTool(FileReadTool.TOOL_NAME);

      // Pasamos un Stream fresco para la lectura real
      try (Stream<String> contentStream = getSelectedContents(resolvedPaths)) {
        // Generamos un String descriptivo para el HINT (ej: un resumen de los selectores)
        String originalPathHint = String.join(", ", selectors);
        return fileRead.execute(contentStream, originalPathHint, TOOL_NAME, totalLines, offset, limit);
      }

    } catch (Exception e) {
      return "{\"status\": \"error\", \"message\": \"" + e.getMessage() + "\"}";
    }
  }

  /**
   * Genera un Stream perezoso que concatena cabeceras, contenido y separadores
   * de todos los archivos.
   */
  private Stream<String> getSelectedContents(List<Path> paths) {
    return paths.stream().flatMap(path -> {
      try {
        String path_s = path.toAbsolutePath().toString();
        String mimeType = tika.detect(path);
        boolean isBinary = isBinaryMime(mimeType);

        // Cabecera del archivo
        Stream<String> header = Stream.of(String.format("--- %s [%s] ---", path_s, isBinary ? "B" : "T"));

        // Contenido (Líneas reales o Base64 si es binario)
        Stream<String> content;
        if (isBinary) {
//          byte[] bytes = Files.readAllBytes(path);
//          content = Stream.of(Base64.getEncoder().encodeToString(bytes));
            content = Stream.of("");
        } else {
          // Nota: flatMap cerrará este stream interno al terminar de leerlo
          content = Files.lines(path, StandardCharsets.UTF_8);
        }

        // Separador final
        Stream<String> footer = Stream.of("");

        return Stream.concat(header, Stream.concat(content, footer));
      } catch (Exception e) {
        return Stream.of("--- ERROR leyendo " + path + ": " + e.getMessage() + " ---", "");
      }
    });
  }

  private long countTotalLines(List<Path> paths) throws IOException {
    try (Stream<String> s = getSelectedContents(paths)) {
      return s.count();
    }
  }

  private String calculateBundleHash(List<Path> paths) {
    String allPaths = paths.stream()
            .map(p -> p.toAbsolutePath().toString())
            .collect(Collectors.joining("|"));
    return DigestUtils.sha256Hex(allPaths);
  }

  private List<Path> resolveSelectors(List<String> selectors) throws IOException {
    Set<Path> resolved = new LinkedHashSet<>();
    Path rootPath = agent.getAccessControl().resolvePathOrNull(".", PATH_ACCESS_READ);

    for (String selector : selectors) {
      if (selector.contains("*") || selector.contains("?")) {
        final PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + selector);

        // Activamos FOLLOW_LINKS para descubrir archivos a través de symlinks
        Files.walkFileTree(rootPath, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE, new SimpleFileVisitor<>() {
          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
            try {
              // FIXME: no tengo claro que aqui debamos usar toRealPath, deberia dejarse como esta y que sea el AccessControl quien decida que hacer con las rutas.
              // 1. Obtenemos la ruta absoluta real del archivo (resolviendo symlinks)
              Path realPath = file.toRealPath();

              // 2. Calculamos la ruta relativa de lo que el matcher espera ver
              // (el matcher suele esperar la ruta relativa al root del walk)
              Path relativeForMatcher = rootPath.relativize(file);

              if (matcher.matches(relativeForMatcher)) {
                // 3. Validamos la RUTA REAL contra el AccessControl
                // Esto asegura que si el link apunta fuera y no está en la whitelist, falle.
                Path safePath = agent.getAccessControl().resolvePathOrNull(realPath.toString(), PATH_ACCESS_READ);
                if (safePath != null) {
                  resolved.add(safePath);
                }
              }
            } catch (IOException e) {
              // Si el link está roto o no hay permisos de lectura de metadatos, saltamos
              LOGGER.warn("No se pudo resolver la ruta real de: " + file, e);
            }
            return FileVisitResult.CONTINUE;
          }

          @Override
          public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
            String name = dir.getFileName().toString();
            if (name.equals("target") || name.equals(".git") || name.equals(".idea")) {
              return FileVisitResult.SKIP_SUBTREE;
            }
            return FileVisitResult.CONTINUE;
          }
        });
      } else {
        // Caso de ruta directa
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
    return new ArrayList<>(resolved);
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
}
