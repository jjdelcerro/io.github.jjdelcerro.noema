package io.github.jjdelcerro.noema.ui.webclient.state;

import io.github.jjdelcerro.noema.lib.api.Agent;
import io.github.jjdelcerro.noema.lib.api.memory.projected.ProjectedMemoryOperation;
import io.github.jjdelcerro.noema.lib.api.memory.projected.ProjectedMemoryOperationFactory;

/**
 * Factory de la operacion de consciencia de la UI.
 *
 * <p>El nombre forma parte del contrato de serializacion de las operaciones
 * proyectadas, pero esta operacion no guarda estado propio (getState devuelve
 * null), asi que renombrarla no pierde nada.</p>
 */
public class UIAwarenessOperationFactory implements ProjectedMemoryOperationFactory {

    public static final String NAME = "ui_perception";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public ProjectedMemoryOperation create(Agent agent) {
        UIAwarenessOperation operation = new UIAwarenessOperation(agent);
        return operation;
    }
}
