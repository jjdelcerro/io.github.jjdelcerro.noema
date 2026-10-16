package io.github.jjdelcerro.noema.ui.webclient.handlers;

import io.github.jjdelcerro.noema.lib.api.Agent;
import io.github.jjdelcerro.noema.lib.api.AgentAccessControl;
import io.javalin.http.Context;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.Strings;

/**
 *
 * @author jjdelcerro
 */
public class HandlerGetFilesTree extends AbstractHandler {

  public HandlerGetFilesTree(Agent agent) {
    super(agent);
  }

  @Override
  /**
   * Devuelve la estructura del sistema de archivos por niveles para el
   * explorador del editor web.
   *
   * Sin parametro 'path': lista de raices virtuales. Con parametro 'path':
   * hijos directos de esa carpeta. Los directorios con un unico hijo que
   * tambien es directorio se compactan en un solo nodo (comportamiento tipo
   * "reduced tree" de NetBeans / "compact middle packages" de IntelliJ).
   */
  public void handle(Context ctx) throws Exception {
    String pathParam = ctx.queryParam("path");
    String modeParam = ctx.queryParam("mode"); // tree vs reducedtree TODO: ajustar el javadoc

    // Caso raiz: raices virtuales (no se compactan: son puntos de entrada
    // configurados por el usuario, no carpetas del proyecto)
    if (pathParam == null || pathParam.isBlank()) {
      List<Map<String, String>> roots = new ArrayList<>();
      Map<String, Path> virtualRoots = getFilesystemRootNodes(null);
      for (Map.Entry<String, Path> entry : virtualRoots.entrySet()) {
        roots.add(treeEntry(entry.getKey(), entry.getValue(), true));
      }
      ctx.json(roots);
      return;
    }

    // Caso subcarpeta: hijos directos
    Path target;
    try {
      target = Path.of(pathParam.trim()).toAbsolutePath().normalize();
    } catch (Exception e) {
      ctx.status(400).json(Map.of("error", "Ruta invalida: " + pathParam));
      return;
    }

    if (!Files.exists(target) || !Files.isDirectory(target)) {
      ctx.status(404).json(Map.of("error", "Directorio no encontrado: " + pathParam));
      return;
    }

    List<Map<String, String>> children = new ArrayList<>();
    try (var stream = Files.list(target)) {
      List<Path> sorted = stream
              .filter(p -> !isNoiseEntry(p))
              .sorted(Comparator
                      .comparing((Path p) -> !Files.isDirectory(p))
                      .thenComparing(p -> p.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
              .toList();
      for (Path child : sorted) {
        if (Strings.CI.equals(modeParam, "reducedtree")) {
          children.add(treeEntry(child.getFileName().toString(), child, Files.isDirectory(child)));
        } else {
          if (Files.isDirectory(child)) {
            children.add(compactDirectoryEntry(child));
          } else {
            children.add(treeEntry(child.getFileName().toString(), child, false));
          }
        }
      }
    } catch (Exception e) {
      LOGGER.warn("No se pudo listar el directorio: {}", target, e);
    }

    ctx.json(children);
  }

  /**
   * Construye una entrada {name, path, type} para un directorio, compactando
   * cadenas de carpetas con un unico hijo que tambien es carpeta.
   *
   * Ejemplo: io > github > jjdelcerro > noema se muestra como un unico nodo
   * "io.github.jjdelcerro.noema" apuntando a la carpeta mas profunda.
   *
   * La condicion de compactacion es estricta: la carpeta debe tener exactamente
   * un hijo visible (tras aplicar el filtro de ruido) y ese hijo debe ser un
   * directorio. Si hay cualquier fichero entre los hijos, no se compacta.
   */
  private Map<String, String> compactDirectoryEntry(Path start) {
    StringBuilder nameBuilder = new StringBuilder(start.getFileName().toString());
    Path current = start;

    while (true) {
      List<Path> visibleChildren = listVisible(current);
      if (visibleChildren.size() != 1) {
        break;
      }
      Path only = visibleChildren.get(0);
      if (!Files.isDirectory(only)) {
        break;
      }
      nameBuilder.append('/').append(only.getFileName().toString());
      current = only;
    }

    return treeEntry(nameBuilder.toString(), current, true);
  }

  /**
   * Construye una entrada {name, path, type} para el arbol web.
   */
  private Map<String, String> treeEntry(String name, Path path, boolean isDirectory) {
    Map<String, String> entry = new LinkedHashMap<>();
    entry.put("name", name);
    entry.put("path", path.toAbsolutePath().normalize().toString());
    entry.put("type", isDirectory ? "directory" : "file");
    return entry;
  }

  /**
   * Lista los hijos visibles de una carpeta aplicando el filtro de ruido. Sin
   * ordenar: solo interesa el conteo y el tipo. Devuelve lista vacia si no se
   * puede listar (permisos, etc.), lo que detiene la compactacion.
   */
  private List<Path> listVisible(Path dir) {
    try (var stream = Files.list(dir)) {
      return stream
              .filter(p -> !isNoiseEntry(p))
              .toList();
    } catch (Exception e) {
      LOGGER.warn("No se pudo listar el directorio para compactacion: {}", dir, e);
      return List.of();
    }
  }

  /**
   * Filtro de higiene: oculta metadatos de Git, respaldos de JavaRCS y el
   * almacen interno de Noema (.noema-agent/var).
   */
  private boolean isNoiseEntry(Path p) {
    String name = p.getFileName().toString();
    if (".git".equals(name) || name.endsWith(",jv")) {
      return true;
    }
    String normalized = p.toString().replace('\\', '/');
    return normalized.contains("/.noema-agent/var/") || normalized.endsWith("/.noema-agent/var");
  }

  /**
   *
   * Devuelve las raices virtuales del explorador. La clave del Map es el nombre
   * visible en el arbol; el valor, la ruta real. Orden: primero el workspace,
   * luego las rutas externas permitidas.
   */
  private Map<String, Path> getFilesystemRootNodes(@SuppressWarnings("unused") String terminalId) {
    Map<String, Path> roots = new LinkedHashMap<>();
    Map<String, Integer> names = new HashMap<>();
    AgentAccessControl accessControl = this.agent.getAccessControl();

    for (Path path : accessControl.getAllowedPaths()) {
      String name = path.toFile().getName();
      Object x = names.get(name);
      if (x == null) {
        names.put(name, 1);
      } else {
        names.put(name, (int) x + 1);
      }
    }

    for (Path path : accessControl.getAllowedPaths()) {
      path = path.toAbsolutePath().normalize();
      String name = path.toFile().getName();
      int n = (int) names.get(name);
      if (n == 1) {
        roots.put(name, path);
      } else {
        roots.put(path.toString(), path);
      }
    }
    return roots;
  }

}
