package io.github.jjdelcerro.noema.lib;

import java.net.URI;
import java.nio.file.Path;

/**
 *
 * @author jjdelcerro
 */
public interface AgentAccessControl {

  public enum AccessMode {
    PATH_ACCESS_READ,
    PATH_ACCESS_WRITE
  }

  /**
   * @return true si el agente tiene permiso para escribir archivos en el disco.
   */
  boolean isAllowedDiskWrite();

  /**
   * @return true si el agente tiene permiso para ejecutar comandos en el shell.
   */
  boolean isAllowedShellExecution();

  /**
   * @return true si el agente debe crear un backup (ci) antes de modificar un
   * archivo.
   */
  boolean isEnabledRCSBackup();

  /**
   * @return true si el agente tiene permitido hacer peticiones a Internet (URLs
   * externas).
   */
  boolean isAllowedInternetAccess();

  /**
   * Método maestro que determina si una herramienta está permitida basándose en
   * su modo de operación y las políticas de seguridad actuales.
   *
   * @param tool La herramienta a evaluar.
   * @return true si la herramienta puede ejecutarse, false en caso contrario.
   */
  boolean isToolAllowed(AgentTool tool);

  void addAllowedPath(Path path);

  void addNonWritablePath(Path path);

  void addNonReadablePath(Path path);

  /**
   * Verifica si el path es seguro y accesible para el modo solicitado.
   *
   * @param rawPath
   * @param mode
   * @return
   */
  boolean isPathAccessible(String rawPath, AccessMode mode);

  boolean isAccessible(Path path, AccessMode mode);

  public boolean isAccessible(URI url);

  /**
   * Resuelve una ruta relativa contra el root y verifica la seguridad.Lanza
   * excepción si el acceso es denegado.
   *
   * @param rawPath
   * @param mode
   * @return
   */
  Path resolvePath(String rawPath, AccessMode mode);

  Path resolvePath(Path path, AccessMode mode);

  Path resolvePathOrNull(String rawPath, AccessMode mode);

  Path resolvePathOrNull(Path path, AccessMode mode);

}
