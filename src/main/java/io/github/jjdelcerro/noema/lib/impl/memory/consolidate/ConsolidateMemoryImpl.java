package io.github.jjdelcerro.noema.lib.impl.memory.consolidate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.github.jjdelcerro.noema.lib.memory.consolidate.ConsolidateMemory;

/**
 * Representa un punto de consolidación de la memoria.
 * <p>
 * Sigue un patrón híbrido de persistencia: - Los metadatos (IDs, Timestamp)
 * viven en la Base de Datos. - El contenido textual (Resumen + El Viaje) vive
 * en un archivo físico (.md).
 * 
 * TODO: Antes CheckPointImpl, habria que actualizar la documentacion con este cambio 
 */
public class ConsolidateMemoryImpl implements ConsolidateMemory {

  private static final Logger LOGGER = LoggerFactory.getLogger(ConsolidateMemoryImpl.class);

  private int id;
  private final int turnFirst;
  private final int turnLast;
  private final LocalDateTime timestamp;
  private final Path storageFolder; // Carpeta donde se guardan los ficheros

  // Cache del contenido textual. Null hasta que se llama a getText()
  private String cachedText;
    private final String subchannel;

  // Constructor privado
  private ConsolidateMemoryImpl(String subchannel, int id, int turnFirst, int turnLast, LocalDateTime timestamp, Path storageFolder) {
    this.id = id;
    this.turnFirst = turnFirst;
    this.turnLast = turnLast;
    this.timestamp = timestamp;
    this.storageFolder = storageFolder;
    this.subchannel = subchannel;
  }

  /**
   * Factoría para rehidratar un ConsolidateMemory desde los metadatos de la Base de
   * Datos. No carga el texto del disco inmediatamente (Lazy Loading).
   */
  public static ConsolidateMemoryImpl from(String subchannel, int id, int turnFirst, int turnLast, LocalDateTime timestamp, Path storageFolder) {
    return new ConsolidateMemoryImpl(subchannel, id, turnFirst, turnLast, timestamp, storageFolder);
  }

  /**
   * Factoría para crear una nueva consolidacion de memoria.
   * - Obtiene el siguiente ID del contador. 
   * - Retorna la instancia para que EpisodicMemory guarde los metadatos en BD.
   */
  public static ConsolidateMemoryImpl create(String subchannel, int id, int turnFirst, int turnLast, LocalDateTime timestamp, String text, Path storageFolder) {
    ConsolidateMemoryImpl cp = new ConsolidateMemoryImpl(subchannel, id, turnFirst, turnLast, timestamp, storageFolder);

    // Inyectamos el texto en cache
    cp.cachedText = text;
    return cp;
  }

  /**
   * Genera el código único del ConsolidateMemory. Formato:
   * consolidatememory-{id}-{first}-{last}
   *
   * @return
   */
  @Override
  public String getCode() {
    return String.format("consolidatememory-%d-%d-%d", id, turnFirst, turnLast);
  }

  public void saveTextToDisk() {
    try {
      if (!Files.exists(storageFolder)) {
        Files.createDirectories(storageFolder);
      }
      Files.writeString(getStoragePath(storageFolder), cachedText, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new RuntimeException("No se pudo persistir el ConsolidateMemory en disco: " + getCode(), e);
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
      LOGGER.warn("No se ha podido localizar el ConsolidateMemory en '" + path.getFileName().toString() + "'.");
      return "Error: El archivo de ConsolidateMemory " + path.getFileName().toString() + " no existe.";
    }

    try {
      cachedText = Files.readString(path, StandardCharsets.UTF_8);
      return cachedText;
    } catch (IOException e) {
      throw new RuntimeException("Error crítico leyendo ConsolidateMemory del disco: " + getCode(), e);
    }
  }

  @Override
  public int getId() {
    return id;
  }

  public void setId(int id) {
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
  public LocalDateTime getTimestamp() {
    return timestamp;
  }

  @Override
  public String getSubchannel() {
      return subchannel;
  }

  @Override
  public String getSummary() { // FIME: falta por implementar
    return null;
  }
  
}

