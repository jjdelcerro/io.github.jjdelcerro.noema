package io.github.jjdelcerro.noema.lib.impl.memory.proyected.operations;

import com.google.gson.JsonObject;
import io.github.jjdelcerro.noema.lib.memory.proyected.ProjectedMemoryOperation;
import io.github.jjdelcerro.noema.lib.memory.proyected.ProjectedMemoryOperationFactory;

public class PinnedTurnsOperationFactory implements ProjectedMemoryOperationFactory {

    @Override
    public String getName() {
        return PinnedTurnsOperationImpl.OPERATION_NAME;
    }

    @Override
    public ProjectedMemoryOperation create(JsonObject state) {
        PinnedTurnsOperationImpl operation = new PinnedTurnsOperationImpl();
        if (state != null && !state.isEmpty()) {
            operation.restoreState(state);
        }
        return operation;
    }
}
