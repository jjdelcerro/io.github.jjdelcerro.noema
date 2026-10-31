package io.github.jjdelcerro.noema.lib.api.memory.projected;

import io.github.jjdelcerro.noema.lib.api.Agent;

public interface ProjectedMemoryOperationFactory {

    String getName();

    ProjectedMemoryOperation create(Agent agent);
}
