package io.github.jjdelcerro.noema.lib.impl.memory.projected.operations;


import io.github.jjdelcerro.noema.lib.api.Agent;
import io.github.jjdelcerro.noema.lib.api.memory.projected.ProjectedMemoryOperation;
import io.github.jjdelcerro.noema.lib.api.memory.projected.ProjectedMemoryOperationFactory;

public class TemporalPerceptionOperationFactory implements ProjectedMemoryOperationFactory {

    public static final String NAME = "temporal_perception";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public ProjectedMemoryOperation create(Agent agent) {
        TemporalPerceptionOperation operation = new TemporalPerceptionOperation(agent);
        return operation;
    }
}
