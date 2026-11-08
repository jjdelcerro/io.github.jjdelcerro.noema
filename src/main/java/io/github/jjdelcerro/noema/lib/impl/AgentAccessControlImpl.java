package io.github.jjdelcerro.noema.lib.impl;

import io.github.jjdelcerro.noema.lib.spi.AbstractAgentAction;
import io.github.jjdelcerro.noema.lib.api.AgentAccessControl;
import io.github.jjdelcerro.noema.lib.api.AgentActions;
import io.github.jjdelcerro.noema.lib.api.AgentConsole;
import io.github.jjdelcerro.noema.lib.api.AgentPaths;
import io.github.jjdelcerro.noema.lib.api.AgentTool;
import io.github.jjdelcerro.noema.lib.api.settings.AgentSettings;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import org.apache.commons.io.FilenameUtils;

/**
 * Gestiona el acceso seguro al sistema de ficheros (Sandbox) y audita
 * incongruencias en las reglas de configuración.
 */
@SuppressWarnings("UseSpecificCatch")
public class AgentAccessControlImpl implements AgentAccessControl {

  private static final Logger LOGGER = LoggerFactory.getLogger(AgentAccessControlImpl.class);

  public static final String RELOAD_ACTION_NAME = "RELOAD_ACCESS_CONTROL";

  private final Path rootPath;
  private final AgentSettings settings;
  private final AgentActions actions;
  private final Supplier<AgentConsole> consoleSupplier;

  private boolean allowDiskWrite;
  private boolean allowShellExecution;
  private boolean enableRcsBackup;
  private boolean allowInternetAccess;
  private boolean humanConfirmationRequired;

  // Lista de rutas adicionales permitidas fuera del root (ej: carpetas temporales)
  private List<Path> allowedExternalPaths = new ArrayList<>();
  private List<Path> nonWritablePaths = new ArrayList<>();
  private List<Path> nonReadablePaths = new ArrayList<>();
  private List<String> nonWritablePathWilcards = new ArrayList<>();
  private List<String> nonNavigableURLsWilcards = new ArrayList<>();
  private boolean enableFirejail;

  public AgentAccessControlImpl(AgentSettings settings, AgentActions actions, Path rootPath) {
    this(settings, actions, rootPath, null);
  }

  public AgentAccessControlImpl(AgentSettings settings, AgentActions actions, Path rootPath, Supplier<AgentConsole> consoleSupplier) {
    this.rootPath = rootPath.toAbsolutePath().normalize();
    this.settings = settings;
    this.actions = actions;
    this.consoleSupplier = consoleSupplier;
    this.actions.addAction(new AbstractAgentAction(RELOAD_ACTION_NAME) {
      @Override
      public boolean perform(AgentSettings settings) {
        loadConfig();
        return true;
      }
    });
    this.nonWritablePathWilcards.add("*,jv"); // FIXME: habria que afinar esto, probablemente usando AgentSettings
    this.nonWritablePathWilcards.add("*/.git/*");

    this.nonNavigableURLsWilcards.add("*://localhost:*"); // FIXME: habria que afinar esto, probablemente usando AgentSettings
    this.nonNavigableURLsWilcards.add("*://127.0.0.1:*");
    this.nonNavigableURLsWilcards.add("*://1192.168.*:*");

    loadConfig();
  }

  @Override
  public AgentAccessControl clone() throws CloneNotSupportedException {
    AgentAccessControlImpl x = (AgentAccessControlImpl) super.clone();
    x.allowedExternalPaths = new ArrayList<>(this.allowedExternalPaths);
    x.nonWritablePaths = new ArrayList<>(this.nonWritablePaths);
    x.nonReadablePaths = new ArrayList<>(this.nonReadablePaths);
    return x;
  }
  
  @Override
  public List<Path> getAllowedPaths() {
    List<Path> paths = new ArrayList<>();
    paths.add(this.rootPath);
    paths.addAll(this.allowedExternalPaths);
    return paths;
  }
  
  public List<String> getNonWritablePathWilcards() {
    return Collections.unmodifiableList(nonWritablePathWilcards);
  }

  public List<Path> getEfectiveNonReadablePaths() {
    List<Path> r = new ArrayList<>();
    r.add(rootPath.resolve("." + AgentPaths.AGENT_FOLDER_NAME).resolve("var"));
    for (Path nomReadablePath : this.nonReadablePaths) {
      r.add(nomReadablePath);
    }
    return r;
  }
  
  public List<Path> getEfectiveNonWritablePaths() {
    List<Path> r = new ArrayList<>();
    r.add(rootPath.resolve(".claude")); // skills
    r.add(rootPath.resolve(".agent")); // skills
    r.add(rootPath.resolve("." + AgentPaths.AGENT_FOLDER_NAME).resolve("var"));
    for (Path nonWritablePath : this.nonWritablePaths) {
      r.add(nonWritablePath);
    }
    return r;
  }
  
  @Override
  public void addAllowedPath(Path path) {
    this.allowedExternalPaths.add(path.toAbsolutePath().normalize());
  }

  @Override
  public void addNonWritablePath(Path path) {
    this.nonWritablePaths.add(path.toAbsolutePath().normalize());
  }

  @Override
  public void addNonReadablePath(Path path) {
    this.nonReadablePaths.add(path.toAbsolutePath().normalize());
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
   * Resuelve la ruta canonica real del archivo o directorio respetando enlaces
   * simbolicos. Si el archivo o alguno de sus directorios intermedios no existe
   * fisicamente en disco, asciende por el arbol hasta encontrar el primer
   * ancestro real existente, resuelve su toRealPath() y reconstruye los
   * segmentos restantes sin asumir la existencia del padre.
   */
  private Path toRealPath(Path path) throws IOException {
    Path normalized = path.normalize();

    // 1. Si la ruta existe fisicamente, resolucion canonica directa
    if (Files.exists(normalized)) {
      return normalized.toRealPath();
    }

    // 2. Si no existe, buscamos el ancestro existente mas cercano
    Path existingAncestor = normalized.getParent();
    while (existingAncestor != null && !Files.exists(existingAncestor)) {
      existingAncestor = existingAncestor.getParent();
    }

    // 3. Si no encontramos ningun ancestro existente (caso limite de ruta relativa pura), fallback a absoluto
    if (existingAncestor == null) {
      return normalized.toAbsolutePath();
    }

    // 4. Resolvemos los symlinks del ancestro real y reensamblamos las ramas pendientes
    Path realAncestor = existingAncestor.toRealPath();
    Path remaining = existingAncestor.relativize(normalized);
    return realAncestor.resolve(remaining).normalize();
  }

  /**
   * Comprueba si existe una regla en 'allowedExternalPaths' que coincida con
   * 'target' y que tenga una especificidad (profundidad de segmentos)
   * estrictamente mayor que el prefijo denegado, habilitando un "agujero" de
   * acceso.
   */
  private boolean isHoleAllowed(Path target, Path deniedPrefix) {
    int deniedDepth = deniedPrefix.getNameCount();
    for (Path allowed : this.allowedExternalPaths) {
      if (target.startsWith(allowed) && allowed.getNameCount() >= deniedDepth) {
        return true;
      }
    }
    return false;
  }

  @Override
  public Path resolvePath(String rawPath, AccessMode mode) {
    if (rawPath == null || rawPath.isBlank()) {
      throw new IllegalArgumentException("The path cannot be empty");
    }

    // 1. Resolver ruta contra root si es relativa y obtener su ruta real canonica
    Path target;
    try {
      Path inputPath = Paths.get(rawPath);
      Path resolved = inputPath.isAbsolute() ? inputPath : rootPath.resolve(inputPath);
      target = toRealPath(resolved);
    } catch (Exception e) {
      throw new IllegalArgumentException("invalid path: " + rawPath);
    }
    String target_s = target.toString();
    String targetPathString = target_s.replace("\\", "/");

//    // Invariante inmutable: la carpeta interna del agente nunca es accesible por el modelo
//    if (targetPathString.contains("/." + AgentPaths.AGENT_FOLDER_NAME + "/var/")
//            || targetPathString.endsWith("/." + AgentPaths.AGENT_FOLDER_NAME + "/var")) {
//      Path agentVarPrefix = rootPath.resolve("." + AgentPaths.AGENT_FOLDER_NAME).resolve("var");
//      if (!isHoleAllowed(target, agentVarPrefix)) {
//        throw new SecurityException("ACCESO DENEGADO: La carpeta de control interno del agente no es accesible.");
//      }
//    }

    // Validar restricciones de lectura con soporte de agujeros mas especificos
    for (Path nonReadablePath : this.getEfectiveNonReadablePaths()) {
      if (target.startsWith(nonReadablePath)) {
        if (!isHoleAllowed(target, nonReadablePath)) {
          throw new SecurityException("Path not found: " + rawPath);
        }
      }
    }

    // Comprobar si la ruta esta dentro del sandbox (rootPath o allowedExternalPaths)
    boolean isUnderRoot = target.startsWith(rootPath);
    boolean isWhitelisted = false;

    if (!isUnderRoot) {
      for (Path allowed : allowedExternalPaths) {
        if (target.startsWith(allowed)) {
          isWhitelisted = true;
          break;
        }
      }
    }

    if (!isUnderRoot && !isWhitelisted) {
      throw new SecurityException("Path not found: " + rawPath);
    }

    // Logica especifica para modo de escritura
    if (mode == AccessMode.PATH_ACCESS_WRITE) {
      for (String wildcardMatcher : this.getNonWritablePathWilcards()) {
        if( FilenameUtils.wildcardMatch(target_s, wildcardMatcher) ) {
            throw new SecurityException("Path not allowed for writing: " + rawPath);
        }
      }
      // Validar restricciones de no-escritura con soporte de agujeros mas especificos
      for (Path nonWritablePath : this.getEfectiveNonWritablePaths()) {
        if (target.startsWith(nonWritablePath)) {
          if (!isHoleAllowed(target, nonWritablePath)) {
            throw new SecurityException("Path not allowed for writing: " + rawPath);
          }
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
  public boolean isAccessible(URI url) { 
    if (!isAllowedInternetAccess()) {
      return false;
    }
    // habria que ver si es intersante restringir el protocolo.
    String url_s = url.toString().toLowerCase();
    for (String nonNavigableURLsWilcard : nonNavigableURLsWilcards) {
      if( FilenameUtils.wildcardMatch(url_s, nonNavigableURLsWilcard) ) {
        return false;
      }
    }
    return true;
  }

  /**
   * Sincroniza las listas en memoria con lo definido en la configuración y
   * valida incongruencias.
   */
  private synchronized void loadConfig() {

    // Limpiar reglas actuales
    allowedExternalPaths.clear();
    nonWritablePaths.clear();
    nonReadablePaths.clear();

    // Cargar Whitelist (Rutas externas permitidas)
    List<Path> whitelist = settings.getPropertyAsPaths("access_control/allowed_external_paths");
    for (Path p : whitelist) {
      allowedExternalPaths.add(p.toAbsolutePath().normalize());
    }

    // Cargar Blacklist de Escritura (Solo lectura)
    List<Path> readOnly = settings.getPropertyAsPaths("access_control/nom_writable_paths");
    for (Path p : readOnly) {
      nonWritablePaths.add(p.toAbsolutePath().normalize());
    }

    // Cargar Blacklist de Lectura (Prohibidas)
    List<Path> forbidden = settings.getPropertyAsPaths("access_control/nom_readable_paths");
    for (Path p : forbidden) {
      nonReadablePaths.add(p.toAbsolutePath().normalize());
    }
    this.allowDiskWrite = Boolean.parseBoolean(
            settings.getPropertyAsString("access_control/allow_disk_write", "true")
    );
    this.allowShellExecution = Boolean.parseBoolean(
            settings.getPropertyAsString("access_control/allow_shell_execution", "true")
    );
    this.enableRcsBackup = Boolean.parseBoolean(
            settings.getPropertyAsString("access_control/enable_rcs_backup", "true")
    );
    this.allowInternetAccess = Boolean.parseBoolean(
            settings.getPropertyAsString("access_control/allow_internet_access", "true")
    );
    this.humanConfirmationRequired = Boolean.parseBoolean(
            settings.getPropertyAsString("access_control/humanConfirmationRequired", "true")
    );
    this.enableFirejail = Boolean.parseBoolean(
            settings.getPropertyAsString("access_control/enable_firejail", "true")
    );

    // Auditoría de incongruencias
    validateRules();
  }

  /**
   * Inspecciona las listas cargadas para identificar errores comunes de
   * configuracion.
   */
  private void validateRules() {
    // 1. Detectar rutas en nom_writable_paths fuera del workspace y no incluidas en allowed_external_paths
    checkIsolatedRestrictedPaths(nonWritablePaths, "nom_writable_paths");

    // 2. Detectar rutas en nom_readable_paths fuera del workspace y no incluidas en allowed_external_paths
    checkIsolatedRestrictedPaths(nonReadablePaths, "nom_readable_paths");

    // 3. Detectar rutas redundantes en allowed_external_paths:
    // Solo es redundante si esta dentro del workspace Y NO abre un agujero sobre una restriccion
    for (Path allowed : allowedExternalPaths) {
      if (allowed.startsWith(rootPath)) {
        boolean isHoleInRestriction = nonWritablePaths.stream().anyMatch(allowed::startsWith)
                || nonReadablePaths.stream().anyMatch(allowed::startsWith);

        if (!isHoleInRestriction) {
          reportIncongruency("La ruta '" + allowed + "' en 'allowed_external_paths' esta dentro del workspace, por lo que es redundante.");
        }
      }
    }

    // 4. Detectar solapamientos directos entre lectura prohibida y solo-lectura
    for (Path forbidden : nonReadablePaths) {
      if (nonWritablePaths.contains(forbidden)) {
        reportIncongruency("La ruta '" + forbidden + "' esta en 'nom_readable_paths' y 'nom_writable_paths'. Prevalecera el bloqueo total de lectura.");
      }
    }
  }

  private void checkIsolatedRestrictedPaths(List<Path> restrictedList, String listName) {
    for (Path p : restrictedList) {
      boolean isUnderRoot = p.startsWith(rootPath);
      boolean isWhitelisted = false;

      for (Path allowed : allowedExternalPaths) {
        if (p.startsWith(allowed)) {
          isWhitelisted = true;
          break;
        }
      }

      if (!isUnderRoot && !isWhitelisted) {
        reportIncongruency("La ruta '" + p + "' en '" + listName + "' está fuera del workspace y NO está incluida en 'allowed_external_paths'. El Sandbox bloqueará completamente su acceso.");
      }
    }
  }

  /**
   * Notifica una incongruencia de configuración al Logger y a la consola activa
   * si está disponible.
   */
  private void reportIncongruency(String message) {
    LOGGER.warn(message);
    if (consoleSupplier != null) {
      AgentConsole console = consoleSupplier.get();
      if (console != null) {
        console.printSystemLog("[ADVERTENCIA DE CONFIGURACIÓN] " + message);
      }
    }
  }

  @Override
  public boolean isHumanConfirmationRequired() {
    return this.humanConfirmationRequired;
  }

  @Override
  public boolean isAllowedDiskWrite() {
    return allowDiskWrite;
  }

  @Override
  public boolean isAllowedShellExecution() {
    return allowShellExecution;
  }

  @Override
  public boolean isEnabledRCSBackup() {
    return enableRcsBackup;
  }

  @Override
  public boolean isAllowedInternetAccess() {
    return allowInternetAccess;
  }

  @Override
  public boolean isToolAllowed(AgentTool tool) {
    // Política 1: Si intenta escribir en disco pero no está permitido
    if (tool.getMode() == AgentTool.MODE_WRITE && !this.allowDiskWrite) {
      return false;
    }

    // Política 2: Si intenta ejecutar comandos pero no está permitido
    if (tool.getMode() == AgentTool.MODE_EXECUTION && !this.allowShellExecution) {
      return false;
    }

    // Política 3: Si intenta acceso a web/internet y no está permitido
    if (tool.getMode() == AgentTool.MODE_WEB && !this.allowInternetAccess) {
      return false;
    }

    return true;
  }

  @Override
  public boolean isFirejailEnabled() {
    return enableFirejail;
  }

  @Override
  public List<Path> resolveRoots(Path scope, AccessMode mode) {
    Path request;
    if (scope == null) {
      request = Path.of("/");
    } else if (scope.isAbsolute()) {
      request = scope;
    } else {
      request = rootPath.resolve(scope);
    }
    request = request.toAbsolutePath().normalize();

    List<Path> roots = new ArrayList<>();
    for (Path candidate : getAllowedPaths()) {
      Path zone = candidate.toAbsolutePath().normalize();
      if (zone.startsWith(request)) {
        addRoot(roots, zone);
      } else if (request.startsWith(zone)) {
        addRoot(roots, request);
      }
    }
    return roots;
  }

  private static void addRoot(List<Path> roots, Path root) {
    for (Path existing : roots) {
      if (root.startsWith(existing)) {
        return;
      }
    }
    roots.removeIf(existing -> existing.startsWith(root));
    roots.add(root);
  }

}
