package io.github.jjdelcerro.noema.lib.impl.memory.projected.operations;

import io.github.jjdelcerro.noema.lib.api.Agent;
import io.github.jjdelcerro.noema.lib.api.memory.projected.ProjectedMemoryOperation;
import io.github.jjdelcerro.noema.lib.api.memory.projected.ProjectedMemoryOperationFactory;


public class TrimmingOperationFactory implements ProjectedMemoryOperationFactory {

    public static final String NAME = "trimming";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public ProjectedMemoryOperation create(Agent agent) {
        TrimmingOperation operation = new TrimmingOperation(agent);
        return operation;
    }
}
