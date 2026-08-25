package io.github.jjdelcerro.noema.lib.impl.persistence;

import io.github.jjdelcerro.noema.lib.impl.memory.recent.RecentMemoryImpl;
import java.nio.file.Path;
import io.github.jjdelcerro.noema.lib.memory.recent.RecentMemory;

public class FakeRecentMemory extends RecentMemoryImpl implements RecentMemory {

    private Boolean forcedNeedCompaction = null;

    public FakeRecentMemory(String subchannel) {
        // Le pasamos una ruta dummy. Al no existir el archivo en disco, 
        // el método load() heredado en el constructor no intentará leer nada.
        super(Path.of("."), null, subchannel);
    }

    /**
     * Anulamos la persistencia en disco para trabajar 100% en RAM durante las pruebas.
     */
    @Override
    public void save() {
        // No-op: Evita la creación/escritura la memoria reciente en el json de disco
    }

    /**
     * Permite forzar o simular la condición de compactación en los tests de forma controlada.
     */
    public void setNeedCompaction(boolean needCompaction) {
        this.forcedNeedCompaction = needCompaction;
    }

    @Override
    public boolean needCompaction() {
        if (forcedNeedCompaction != null) {
            return forcedNeedCompaction;
        }
        return super.needCompaction();
    }
}
