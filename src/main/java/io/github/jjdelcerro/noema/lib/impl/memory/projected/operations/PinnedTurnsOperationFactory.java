package io.github.jjdelcerro.noema.lib.impl.memory.projected.operations;

import com.google.gson.JsonObject;
import io.github.jjdelcerro.noema.lib.api.memory.projected.ProjectedMemoryOperation;
import io.github.jjdelcerro.noema.lib.api.memory.projected.ProjectedMemoryOperationFactory;

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
