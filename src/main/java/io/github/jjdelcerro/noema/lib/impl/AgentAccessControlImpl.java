package io.github.jjdelcerro.noema.lib.impl;

import io.github.jjdelcerro.noema.lib.AbstractAgentAction;
import io.github.jjdelcerro.noema.lib.AgentAccessControl;
import io.github.jjdelcerro.noema.lib.AgentActions;
import io.github.jjdelcerro.noema.lib.AgentConsole;
import io.github.jjdelcerro.noema.lib.AgentTool;
import io.github.jjdelcerro.noema.lib.settings.AgentSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

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
  private final List<Path> allowedExternalPaths = new ArrayList<>();
  private final List<Path> nomWritablePaths = new ArrayList<>();
  private final List<Path> nomReadablePaths = new ArrayList<>();
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
    loadConfig();
  }

  @Override
  public List<Path> getAllowedPaths() {
    List<Path> paths = new ArrayList<>();
    paths.add(this.rootPath);
    paths.addAll(this.allowedExternalPaths);
    return paths;
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
      if (target.startsWith(nonReadablePath)) {
        throw new SecurityException("ACCESO DENEGADO: Ruta no permitida: " + rawPath);
      }
    }

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
        if (target.startsWith(nonWritablePath)) {
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
    if( !isAllowedInternetAccess() ) {
      return false;
    }
    // habria que ver si es intersante restringir el protocolo.
    String lower = url.toString().toLowerCase();
    return !(lower.contains("localhost") || lower.contains("127.0.0.1") || lower.contains("192.168."));
  }

  /**
   * Sincroniza las listas en memoria con lo definido en la configuración y valida incongruencias.
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
   * Inspecciona las listas cargadas para identificar errores comunes de configuración.
   */
  private void validateRules() {
    // 1. Detectar rutas en nom_writable_paths fuera del workspace y no incluidas en allowed_external_paths
    checkIsolatedRestrictedPaths(nomWritablePaths, "nom_writable_paths");

    // 2. Detectar rutas en nom_readable_paths fuera del workspace y no incluidas en allowed_external_paths
    checkIsolatedRestrictedPaths(nomReadablePaths, "nom_readable_paths");

    // 3. Detectar rutas redundantes en allowed_external_paths
    for (Path allowed : allowedExternalPaths) {
      if (allowed.startsWith(rootPath)) {
        reportIncongruency("La ruta '" + allowed + "' en 'allowed_external_paths' está dentro del workspace, por lo que es redundante.");
      }
    }

    // 4. Detectar solapamientos entre lectura prohibida y solo-lectura
    for (Path forbidden : nomReadablePaths) {
      if (nomWritablePaths.contains(forbidden)) {
        reportIncongruency("La ruta '" + forbidden + "' está en 'nom_readable_paths' y 'nom_writable_paths'. Prevalecerá el bloqueo total de lectura.");
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
   * Notifica una incongruencia de configuración al Logger y a la consola activa si está disponible.
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
}
