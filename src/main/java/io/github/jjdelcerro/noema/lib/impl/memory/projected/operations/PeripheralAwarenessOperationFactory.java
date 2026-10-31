package io.github.jjdelcerro.noema.lib.impl.memory.projected.operations;

import io.github.jjdelcerro.noema.lib.api.Agent;
import io.github.jjdelcerro.noema.lib.api.memory.projected.ProjectedMemoryOperation;
import io.github.jjdelcerro.noema.lib.api.memory.projected.ProjectedMemoryOperationFactory;

public class PeripheralAwarenessOperationFactory implements ProjectedMemoryOperationFactory {

  @Override
  public String getName() {
    return PeripheralAwarenessOperation.OPERATION_NAME;
  }

  @Override
  public ProjectedMemoryOperation create(Agent agent) {
    PeripheralAwarenessOperation operation = new PeripheralAwarenessOperation(agent);
    return operation;
  }
}
