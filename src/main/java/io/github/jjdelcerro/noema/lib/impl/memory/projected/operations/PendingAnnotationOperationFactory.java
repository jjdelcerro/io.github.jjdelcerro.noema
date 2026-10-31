package io.github.jjdelcerro.noema.lib.impl.memory.projected.operations;

import io.github.jjdelcerro.noema.lib.api.Agent;
import io.github.jjdelcerro.noema.lib.api.memory.projected.ProjectedMemoryOperation;
import io.github.jjdelcerro.noema.lib.api.memory.projected.ProjectedMemoryOperationFactory;


public class PendingAnnotationOperationFactory implements ProjectedMemoryOperationFactory {

    public static final String NAME = "pending_annotation";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public ProjectedMemoryOperation create(Agent agent) {
        PendingAnnotationOperation operation = new PendingAnnotationOperation(agent);
        return operation;
    }
}