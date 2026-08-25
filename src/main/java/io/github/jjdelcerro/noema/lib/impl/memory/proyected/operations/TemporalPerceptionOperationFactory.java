package io.github.jjdelcerro.noema.lib.impl.memory.proyected.operations;


import com.google.gson.JsonObject;
import io.github.jjdelcerro.noema.lib.memory.proyected.ProjectedMemoryOperation;
import io.github.jjdelcerro.noema.lib.memory.proyected.ProjectedMemoryOperationFactory;

public class TemporalPerceptionOperationFactory implements ProjectedMemoryOperationFactory {

    public static final String NAME = "temporal_perception";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public ProjectedMemoryOperation create(JsonObject state) {
        TemporalPerceptionOperation operation = new TemporalPerceptionOperation();
        if (state != null && !state.isEmpty()) {
            operation.restoreState(state);
        }
        return operation;
    }
}
