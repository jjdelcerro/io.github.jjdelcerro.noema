package io.github.jjdelcerro.noema.lib.impl.memory.proyected.operations;

import com.google.gson.JsonObject;
import io.github.jjdelcerro.noema.lib.memory.proyected.ProjectedMemoryOperation;
import io.github.jjdelcerro.noema.lib.memory.proyected.ProjectedMemoryOperationFactory;


public class TrimmingOperationFactory implements ProjectedMemoryOperationFactory {

    public static final String NAME = "trimming";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public ProjectedMemoryOperation create(JsonObject state) {
        TrimmingOperation operation = new TrimmingOperation();
        if (state != null && !state.isEmpty()) {
            operation.restoreState(state);
        }
        return operation;
    }
}
