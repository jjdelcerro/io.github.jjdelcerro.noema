package io.github.jjdelcerro.noema.ui.webclient.handlers;

import io.github.jjdelcerro.noema.lib.api.Agent;
import io.javalin.http.Context;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author jjdelcerro
 */
public class HandlerGetDirectories extends AbstractHandler {

  public HandlerGetDirectories(Agent agent) {
    super(agent);
  }

  @Override
  public void handle(Context ctx) throws Exception {
    String pathParam = ctx.queryParam("path");
    Path target;

    if (pathParam == null || pathParam.trim().isEmpty()) {
      // Prioridad: 1º Workspace, 2º user.home, 3º Directorio actual (.)
      if (this.agent.getPaths() != null && this.agent.getPaths().getWorkspaceFolder() != null) {
        target = this.agent.getPaths().getWorkspaceFolder().toAbsolutePath().normalize();
      } else {
        String userHome = System.getProperty("user.home");
        target = (userHome != null && !userHome.isBlank())
                ? Path.of(userHome).toAbsolutePath().normalize()
                : Path.of(".").toAbsolutePath().normalize();
      }
    } else {
      target = Path.of(pathParam.trim()).toAbsolutePath().normalize();
    }

    if (!Files.exists(target) || !Files.isDirectory(target)) {
      ctx.status(404).json(Map.of("error", "Directorio no encontrado: " + target));
      return;
    }

    Path parent = target.getParent();
    List<Map<String, String>> directories = new ArrayList<>();

    if (Files.isReadable(target)) {
      try (var stream = Files.list(target)) {
        directories = stream
                .filter(Files::isDirectory)
                .filter(p -> {
                  try {
                    return !Files.isHidden(p);
                  } catch (Exception e) {
                    return true;
                  }
                })
                .sorted(Comparator.comparing(p -> p.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                .map(p -> {
                  Map<String, String> item = new LinkedHashMap<>();
                  item.put("name", p.getFileName().toString());
                  item.put("path", p.toString());
                  return item;
                })
                .toList();
      } catch (Exception e) {
        LOGGER.warn("No se pudieron listar directorios en: {}", target, e);
      }
    }

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("currentPath", target.toString());
    result.put("parentPath", parent != null ? parent.toString() : null);
    result.put("canGoUp", parent != null);
    result.put("directories", directories);

    ctx.json(result);
  }
  
}
