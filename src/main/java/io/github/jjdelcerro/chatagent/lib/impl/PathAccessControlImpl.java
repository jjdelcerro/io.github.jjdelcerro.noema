package io.github.jjdelcerro.chatagent.lib.impl;

import io.github.jjdelcerro.chatagent.lib.PathAccessControl;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Gestiona el acceso seguro al sistema de ficheros (Sandbox).
 */
public class PathAccessControlImpl implements PathAccessControl {

    private final Path rootPath;
    // Lista de rutas adicionales permitidas fuera del root (ej: carpetas temporales)
    private final List<Path> allowedExternalPaths = new ArrayList<>();
    private final List<Path> nomWritablePaths = new ArrayList<>();

    public PathAccessControlImpl(Path rootPath) {
        this.rootPath = rootPath.toAbsolutePath().normalize();
    }

    @Override
    public void addAllowedPath(Path path) {
        this.allowedExternalPaths.add(path.toAbsolutePath().normalize());
    }

    public void addNonWritablePath(Path path) {
        this.nomWritablePaths.add(path.toAbsolutePath().normalize());
    }
    
    /**
     * Verifica si el path es seguro y accesible para el modo solicitado.
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
     * Resuelve una ruta relativa contra el root y verifica la seguridad.Lanza excepción si el acceso es denegado.
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
            target = target.normalize(); // Elimina ".." y "."
        } catch (Exception e) {
            throw new IllegalArgumentException("Path inválido: " + rawPath);
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
            // Aquí podrías añadir reglas extra, ej: no escribir en .git, no sobrescribir pom.xml, etc.
            if (target.toString().contains("/.git/")) {
                throw new SecurityException("ACCESO DENEGADO: No se permite escribir en la carpeta .git");
            }
            //FIXME: Añadir aqui comprobacion de que la ruta no esta en la carpeta de nomWritablePaths.
        }

        return target;
    }
    
  @Override
    public Path resolvePathOrNull(String rawPath, AccessMode mode) {
      try {
        return this.resolvePath(rawPath, mode);
      } catch(Exception ex) {
        return null;
      }
    }

  @Override
  public boolean isPathAccessible(Path path, AccessMode mode) {
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
    
}
