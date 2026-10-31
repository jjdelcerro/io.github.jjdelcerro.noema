package io.github.jjdelcerro.noema.lib.impl.memory.projected.operations;

import io.github.jjdelcerro.noema.lib.api.Agent;
import io.github.jjdelcerro.noema.lib.api.memory.projected.ProjectedMemoryOperation;
import io.github.jjdelcerro.noema.lib.api.memory.projected.ProjectedMemoryOperationFactory;

public class PinnedTurnsOperationFactory implements ProjectedMemoryOperationFactory {

    @Override
    public String getName() {
        return PinnedTurnsOperationImpl.OPERATION_NAME;
    }

    @Override
    public ProjectedMemoryOperation create(Agent agent) {
        PinnedTurnsOperationImpl operation = new PinnedTurnsOperationImpl(agent);
        return operation;
    }
}
