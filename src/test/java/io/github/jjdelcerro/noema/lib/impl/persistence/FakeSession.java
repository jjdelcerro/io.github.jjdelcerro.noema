package io.github.jjdelcerro.noema.lib.impl.persistence;

import io.github.jjdelcerro.noema.lib.impl.services.reasoning.Session;
import io.github.jjdelcerro.noema.lib.impl.services.reasoning.SessionImpl;
import java.nio.file.Path;

public class FakeSession extends SessionImpl implements Session {

    private Boolean forcedNeedCompaction = null;

    public FakeSession(String subchannel) {
        // Le pasamos una ruta dummy. Al no existir el archivo en disco, 
        // el método load() heredado en el constructor no intentará leer nada.
        super(Path.of("."), null, subchannel);
    }

    /**
     * Anulamos la persistencia en disco para trabajar 100% en RAM durante las pruebas.
     */
    @Override
    public void save() {
        // No-op: Evita la creación/escritura de active_session-subchannel.json
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
