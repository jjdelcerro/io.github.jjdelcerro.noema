package io.github.jjdelcerro.noema.lib.impl.persistence;

import io.github.jjdelcerro.noema.lib.persistence.CheckPoint;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Representa un punto de consolidación de la memoria.
 * <p>
 * Sigue un patrón híbrido de persistencia: - Los metadatos (IDs, Timestamp)
 * viven en la Base de Datos. - El contenido textual (Resumen + El Viaje) vive
 * en un archivo físico (.md).
 */
public class CheckPointImpl implements CheckPoint {

  private static final Logger LOGGER = LoggerFactory.getLogger(CheckPointImpl.class);

  private int id;
  private final int turnFirst;
  private final int turnLast;
  private final Timestamp timestamp;
  private final Path storageFolder; // Carpeta donde se guardan los ficheros

  // Cache del contenido textual. Null hasta que se llama a getText()
  private String cachedText;

  // Constructor privado
  private CheckPointImpl(int id, int turnFirst, int turnLast, Timestamp timestamp, Path storageFolder) {
    this.id = id;
    this.turnFirst = turnFirst;
    this.turnLast = turnLast;
    this.timestamp = timestamp;
    this.storageFolder = storageFolder;
  }

  /**
   * Factoría para rehidratar un CheckPoint desde los metadatos de la Base de
   * Datos. No carga el texto del disco inmediatamente (Lazy Loading).
   */
  /*friend*/ static CheckPointImpl from(int id, int turnFirst, int turnLast, Timestamp timestamp, Path storageFolder) {
    return new CheckPointImpl(id, turnFirst, turnLast, timestamp, storageFolder);
  }

  /**
   * Factoría para crear un NUEVO CheckPoint. - Obtiene el siguiente ID del
   * contador. - Retorna la instancia para que SourceOfTruth guarde los
   * metadatos en BD.
   */
  /*friend*/ static CheckPointImpl create(int id, int turnFirst, int turnLast, Timestamp timestamp, String text, Path storageFolder) {
    CheckPointImpl cp = new CheckPointImpl(id, turnFirst, turnLast, timestamp, storageFolder);

    // Inyectamos el texto en cache
    cp.cachedText = text;
    return cp;
  }

  /**
   * Genera el código único del CheckPoint.Formato:
   * checkpoint-{id}-{first}-{last}
   *
   * @return
   */
  @Override
  public String getCode() {
    return String.format("checkpoint-%d-%d-%d", id, turnFirst, turnLast);
  }

  /*friend*/ void saveTextToDisk() {
    try {
      if (!Files.exists(storageFolder)) {
        Files.createDirectories(storageFolder);
      }
      Files.writeString(getStoragePath(storageFolder), cachedText, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new RuntimeException("No se pudo persistir el CheckPoint en disco: " + getCode(), e);
    }
  }

  private Path getStoragePath(Path storageFolder) {
    // Usamos extensión .md para facilitar el debug (Resumen + El Viaje)
    return storageFolder.resolve(getCode() + ".md");
  }

  /**
   * Obtiene el contenido textual (Resumen + El Viaje). Si no está en memoria,
   * lo lee del archivo correspondiente en disco.
   */
  @Override
  public String getText() {
    if (cachedText != null) {
      return cachedText;
    }

    Path path = getStoragePath(this.storageFolder);
    if (!Files.exists(path)) {
      LOGGER.warn("No se ha podido localizar el checkpoint en '" + path.getFileName().toString() + "'.");
      return "Error: El archivo de memoria " + path.getFileName().toString() + " no existe.";
    }

    try {
      cachedText = Files.readString(path, StandardCharsets.UTF_8);
      return cachedText;
    } catch (IOException e) {
      throw new RuntimeException("Error crítico leyendo CheckPoint del disco: " + getCode(), e);
    }
  }

  @Override
  public int getId() {
    return id;
  }

  /*friend*/ void setId(int id) {
    if (this.id >= 0) {
      throw new IllegalStateException();
    }
    this.id = id;
  }

  @Override
  public int getTurnFirst() {
    return turnFirst;
  }

  @Override
  public int getTurnLast() {
    return turnLast;
  }

  @Override
  public Timestamp getTimestamp() {
    return timestamp;
  }
}
