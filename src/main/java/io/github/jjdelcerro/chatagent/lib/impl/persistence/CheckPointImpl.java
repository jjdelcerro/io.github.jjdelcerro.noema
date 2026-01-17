package io.github.jjdelcerro.chatagent.lib.impl.persistence;

import io.github.jjdelcerro.chatagent.lib.persistence.CheckPoint;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Timestamp;

/**
 * Representa un punto de consolidación de la memoria.
 * <p>
 * Sigue un patrón híbrido de persistencia: - Los metadatos (IDs, Timestamp)
 * viven en la Base de Datos. - El contenido textual (Resumen + El Viaje) vive
 * en un archivo físico (.md).
 */
public class CheckPointImpl implements CheckPoint {

    private int id;
    private final int turnFirst;
    private final int turnLast;
    private final Timestamp timestamp;
    private final File storageFolder; // Carpeta donde se guardan los ficheros

    // Cache del contenido textual. Null hasta que se llama a getText()
    private String cachedText;

    // Constructor privado
    private CheckPointImpl(int id, int turnFirst, int turnLast, Timestamp timestamp, File storageFolder) {
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
    /*friend*/ static CheckPointImpl from(int id, int turnFirst, int turnLast, Timestamp timestamp, File storageFolder) {
        return new CheckPointImpl(id, turnFirst, turnLast, timestamp, storageFolder);
    }

    /**
     * Factoría para crear un NUEVO CheckPoint. 1. Obtiene el siguiente ID del
     * contador. 2. Guarda el contenido textual inmediatamente en disco. 3.
     * Retorna la instancia para que SourceOfTruth guarde los metadatos en BD.
     */
    /*friend*/ static CheckPointImpl create(int id, int turnFirst, int turnLast, Timestamp timestamp, String text, File storageFolder) {
        CheckPointImpl cp = new CheckPointImpl(id, turnFirst, turnLast, timestamp, storageFolder);

        // Inyectamos el texto en cache y lo persistimos en disco
        cp.cachedText = text;
        return cp;
    }

    /**
     * Genera el código único del CheckPoint. Formato:
     * checkpoint-{id}-{first}-{last}
     */
    @Override
    public String getCode() {
        return String.format("checkpoint-%d-%d-%d", id, turnFirst, turnLast);
    }

    /*friend*/ void saveTextToDisk() {
        try {
            if (!storageFolder.exists()) {
                storageFolder.mkdirs();
            }
            Files.writeString(getStorageFile(storageFolder).toPath(), cachedText, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("No se pudo persistir el CheckPoint en disco: " + getCode(), e);
        }
    }

    private File getStorageFile(File storageFolder) {
        // Usamos extensión .md porque el contenido es Markdown (Resumen + El Viaje)
        return new File(storageFolder, getCode() + ".md");
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

        File file = getStorageFile(this.storageFolder);
        if (!file.exists()) {
            return "Error: El archivo de memoria " + file.getName() + " no existe.";
        }

        try {
            cachedText = Files.readString(file.toPath(), StandardCharsets.UTF_8);
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
