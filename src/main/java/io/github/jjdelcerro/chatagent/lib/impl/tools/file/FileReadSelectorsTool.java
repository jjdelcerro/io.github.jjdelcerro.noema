package io.github.jjdelcerro.chatagent.lib.impl.tools.file;

import com.google.gson.Gson;
import dev.langchain4j.agent.tool.JsonSchemaProperty;
import dev.langchain4j.agent.tool.ToolSpecification;
import io.github.jjdelcerro.chatagent.lib.Agent;
import static io.github.jjdelcerro.chatagent.lib.AgentAccessControl.AccessMode.PATH_ACCESS_READ;
import io.github.jjdelcerro.chatagent.lib.tools.AgenteTool;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import javax.swing.plaf.RootPaneUI;

public class FileReadSelectorsTool implements AgenteTool {

  private final Gson gson = new Gson();

  // Límites de seguridad para evitar colapsar el contexto del LLM
  private static final int MAX_FILES = 20;
  private static final long MAX_TOTAL_SIZE = 1024 * 1024; // 1MB
  private final Agent agent;

  public FileReadSelectorsTool(Agent agent) {
    this.agent = agent;
  }

  @Override
  public ToolSpecification getSpecification() {
    return ToolSpecification.builder()
            .name("file_read_selectors")
            .description("Lee contenido de archivos basados en rutas exactas o patrones glob (ej: 'src/**/*.java').")
            .addParameter("selectors", JsonSchemaProperty.ARRAY,
                    JsonSchemaProperty.description("Lista de rutas o patrones glob."))
            .build();
  }

  @Override
  public String execute(String jsonArguments) {
    try {
      Map<String, List<String>> args = gson.fromJson(jsonArguments, Map.class);
      List<String> selectors = args.get("selectors");

      if (selectors == null || selectors.isEmpty()) {
        return error("Lista de selectores vacía.");
      }

      // 1. Resolver todos los archivos únicos
      Set<Path> filesToRead = resolveSelectors(selectors);

      if (filesToRead.isEmpty()) {
        return error("No se encontró ningún archivo que coincida con los selectores.");
      }
      if (filesToRead.size() > MAX_FILES) {
        return error("Demasiados archivos (" + filesToRead.size() + "). Máximo permitido: " + MAX_FILES);
      }

      // 2. Empaquetar contenido
      StringBuilder sb = new StringBuilder();
      sb.append("--- SELECTORS READ START ---\n");
      long totalBytes = 0;

      for (Path path : filesToRead) {
        if (totalBytes > MAX_TOTAL_SIZE) {
          sb.append("\n--- [AVISO: Límite de tamaño alcanzado, resto de archivos omitidos] ---\n");
          break;
        }
        Path relPath = this.agent.getAccessControl().resolvePathOrNull(path,PATH_ACCESS_READ);
        if( relPath==null ) {
          continue;
        }
        try {
          byte[] bytes = Files.readAllBytes(path);
          totalBytes += bytes.length;

          String status = isBinary(bytes) ? "B" : "T";
          String content;

          if ("B".equals(status)) {
            content = Base64.getEncoder().encodeToString(bytes);
          } else {
            // Intentar UTF-8, fallback a Latin-1
            try {
              content = new String(bytes, StandardCharsets.UTF_8);
            } catch (Exception e) {
              content = new String(bytes, StandardCharsets.ISO_8859_1);
              status = "L";
            }
          }

          sb.append(String.format("--- %s [%s] ---\n\n%s\n\n", relPath, status, content));

        } catch (Exception e) {
          sb.append(String.format("--- %s [E] ---\n\nError leyendo: %s\n\n", relPath, e.getMessage()));
        }
      }
      sb.append("--- SELECTORS READ END ---");

      return gson.toJson(Map.of("status", "success", "file_count", filesToRead.size(), "content", sb.toString()));

    } catch (Exception e) {
      return error(e.getMessage());
    }
  }

  private Set<Path> resolveSelectors(List<String> selectors) throws IOException {
    Set<Path> resolved = new LinkedHashSet<>();

    for (String selector : selectors) {
      if (selector.contains("*") || selector.contains("?")) {
        // Es un patrón Glob
        final PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + selector);
        Path rootPath = this.agent.getAccessControl().resolvePathOrNull(".",PATH_ACCESS_READ);
        if( rootPath!=null ) {
          Files.walkFileTree(rootPath, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
              if (matcher.matches(rootPath.relativize(file))) {
                resolved.add(file.normalize());
              }
              return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
              String name = dir.getFileName().toString();
              // Omitir carpetas ruidosas por defecto
              if (name.equals("target") || name.equals(".git") || name.equals(".idea")) {
                return FileVisitResult.SKIP_SUBTREE;
              }
              return FileVisitResult.CONTINUE;
            }
          });
        }
      } else {
        // Es una ruta directa
        Path p = this.agent.getAccessControl().resolvePathOrNull(selector,PATH_ACCESS_READ);
        if (p!=null && Files.exists(p) && Files.isRegularFile(p) ) {
          resolved.add(p);
        }
      }
    }
    return resolved;
  }

  private boolean isBinary(byte[] bytes) {
    for (int i = 0; i < Math.min(bytes.length, 1024); i++) {
      if (bytes[i] == 0) {
        return true;
      }
    }
    return false;
  }

  private String error(String m) {
    return gson.toJson(Map.of("status", "error", "message", m));
  }
}
