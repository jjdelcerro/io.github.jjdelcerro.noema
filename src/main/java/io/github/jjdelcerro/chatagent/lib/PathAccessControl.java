package io.github.jjdelcerro.chatagent.lib;

import java.nio.file.Path;

/**
 *
 * @author jjdelcerro
 */
public interface PathAccessControl {

  public enum AccessMode {
    PATH_ACCESS_READ,
    PATH_ACCESS_WRITE
  }

  void addAllowedPath(Path path);

  /**
   * Verifica si el path es seguro y accesible para el modo solicitado.
   *
   * @param rawPath
   * @param mode
   * @return
   */
  boolean isPathAccessible(String rawPath, AccessMode mode);

  boolean isPathAccessible(Path path, AccessMode mode);
  
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
