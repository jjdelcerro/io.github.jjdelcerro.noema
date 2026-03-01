package io.github.jjdelcerro.noema.lib.impl;

import io.github.jjdelcerro.noema.lib.AbstractAgentAction;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import io.github.jjdelcerro.noema.lib.AgentAccessControl;
import io.github.jjdelcerro.noema.lib.AgentActions;
import io.github.jjdelcerro.noema.lib.settings.AgentSettings;
import java.net.URI;

/**
 * Gestiona el acceso seguro al sistema de ficheros (Sandbox).
 */
@SuppressWarnings("UseSpecificCatch")
public class AgentAccessControlImpl implements AgentAccessControl {

  public static final String RELOAD_ACTION_NAME = "RELOAD_ACCESS_CONTROL";

  private final Path rootPath;
  private final AgentSettings settings;
  private final AgentActions actions;

  // Lista de rutas adicionales permitidas fuera del root (ej: carpetas temporales)
  private final List<Path> allowedExternalPaths = new ArrayList<>();
  private final List<Path> nomWritablePaths = new ArrayList<>();
  private final List<Path> nomReadablePaths = new ArrayList<>();

  public AgentAccessControlImpl(AgentSettings settings, AgentActions actions, Path rootPath) {
    this.rootPath = rootPath.toAbsolutePath().normalize();
    this.settings = settings;
    this.actions = actions;
    this.actions.addAction(new AbstractAgentAction(RELOAD_ACTION_NAME) {
      @Override
      public boolean perform(AgentSettings settings) {
        loadConfig();
        return true;
      }
    });
    loadConfig();
  }

  @Override
  public void addAllowedPath(Path path) {
    this.allowedExternalPaths.add(path.toAbsolutePath().normalize());
  }

  @Override
  public void addNonWritablePath(Path path) {
    this.nomWritablePaths.add(path.toAbsolutePath().normalize());
  }

  @Override
  public void addNonReadablePath(Path path) {
    this.nomReadablePaths.add(path.toAbsolutePath().normalize());
  }

  /**
   * Verifica si el path es seguro y accesible para el modo solicitado.
   *
   * @param rawPath
   * @param mode
   * @return
   */
  @Override
  public boolean isPathAccessible(String rawPath, AccessMode mode) {
    try {
      resolvePath(rawPath, mode);
      return true;
    } catch (SecurityException | IllegalArgumentException e) {
      return false;
    }
  }

  /**
   * Resuelve una ruta relativa contra el root y verifica la seguridad.Lanza
   * excepción si el acceso es denegado.
   *
   * @param rawPath
   * @param mode
   * @return
   */
  @Override
  public Path resolvePath(String rawPath, AccessMode mode) {
    if (rawPath == null || rawPath.isBlank()) {
      throw new IllegalArgumentException("El path no puede estar vacío");
    }

    // 1. Resolver ruta
    Path target;
    try {
      // Si es absoluta, la usamos tal cual, si es relativa, la resolvemos contra root
      Path inputPath = Paths.get(rawPath);
      target = inputPath.isAbsolute() ? inputPath : rootPath.resolve(inputPath);
      target = target.normalize().toRealPath();
    } catch (Exception e) {
      throw new IllegalArgumentException("Path inválido: " + rawPath);
    }

    for (Path nonReadablePath : this.nomReadablePaths) {
      if (target.startsWith(nonReadablePath.toString())) {
        throw new SecurityException("ACCESO DENEGADO: Ruta no permitida: " + rawPath);
      }
    }

    // 2. Verificar Jailbreak (Path Traversal)
    // Comprobamos si la ruta final empieza por el rootPath
    boolean isUnderRoot = target.startsWith(rootPath);
    boolean isWhitelisted = false;

    if (!isUnderRoot) {
      // Chequeo de lista blanca externa
      for (Path allowed : allowedExternalPaths) {
        if (target.startsWith(allowed)) {
          isWhitelisted = true;
          break;
        }
      }
    }

    if (!isUnderRoot && !isWhitelisted) {
      throw new SecurityException("ACCESO DENEGADO: La ruta intenta salir del sandbox: " + rawPath);
    }

    // 3. Lógica específica de Escritura (Opcional)
    if (mode == AccessMode.PATH_ACCESS_WRITE) {

      // Nunca se permitirse el acceso en escritura a los archivos ",jv".
      // Son la copia de respaldo de la informacion cuando hay modificacion de archivos
      // por parte del LLM, asi que no se puede tocar, solo leer.
      String target_s = target.toString();
      if (target_s.endsWith(",jv")) {
        throw new SecurityException("ACCESO DENEGADO: No se permite escribir en archivos ',jv'");
      }

      // Aquí podrías añadir reglas extra, ej: no escribir en .git, no sobrescribir pom.xml, etc.
      if (target_s.contains("/.git/")) {
        throw new SecurityException("ACCESO DENEGADO: No se permite escribir en la carpeta .git");
      }

      for (Path nonWritablePath : this.nomWritablePaths) {
        if (target_s.startsWith(nonWritablePath.toString())) {
          throw new SecurityException("ACCESO DENEGADO: Ruta no permitida para escritura: " + rawPath);
        }
      }
    }

    return target;
  }

  @Override
  public Path resolvePathOrNull(String rawPath, AccessMode mode) {
    try {
      return this.resolvePath(rawPath, mode);
    } catch (Exception ex) {
      return null;
    }
  }

  @Override
  public boolean isAccessible(Path path, AccessMode mode) {
    return isPathAccessible(path.toString(), mode);
  }

  @Override
  public Path resolvePath(Path path, AccessMode mode) {
    return resolvePath(path.toString(), mode);
  }

  @Override
  public Path resolvePathOrNull(Path path, AccessMode mode) {
    return resolvePathOrNull(path.toString(), mode);
  }

  @Override
  public boolean isAccessible(URI url) { // FIXME: habria que afinar esto, probablemente usando AgentSettings
    // habria que ver si es intersante restringir el protocolo.
    String lower = url.toString().toLowerCase();
    return lower.contains("localhost") || lower.contains("127.0.0.1") || lower.contains("192.168.");
  }

  /**
   * Sincroniza las listas en memoria con lo definido en la configuracion
   */
  private synchronized void loadConfig() {

    // Limpiar reglas actuales
    allowedExternalPaths.clear();
    nomWritablePaths.clear();
    nomReadablePaths.clear();

    // Cargar Whitelist (Rutas externas permitidas)
    List<Path> whitelist = settings.getPropertyAsPaths("access_control/allowed_external_paths");
    for (Path p : whitelist) {
      allowedExternalPaths.add(p.toAbsolutePath().normalize());
    }

    // Cargar Blacklist de Escritura (Solo lectura)
    List<Path> readOnly = settings.getPropertyAsPaths("access_control/nom_writable_paths");
    for (Path p : readOnly) {
      nomWritablePaths.add(p.toAbsolutePath().normalize());
    }

    // Cargar Blacklist de Lectura (Prohibidas)
    List<Path> forbidden = settings.getPropertyAsPaths("access_control/nom_readable_paths");
    for (Path p : forbidden) {
      nomReadablePaths.add(p.toAbsolutePath().normalize());
    }
  }

}
