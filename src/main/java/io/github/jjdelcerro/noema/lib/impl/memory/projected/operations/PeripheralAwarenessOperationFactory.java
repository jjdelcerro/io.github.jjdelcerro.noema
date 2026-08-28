package io.github.jjdelcerro.noema.lib.impl.memory.projected.operations;

import com.google.gson.JsonObject;
import io.github.jjdelcerro.noema.lib.memory.projected.ProjectedMemoryOperation;
import io.github.jjdelcerro.noema.lib.memory.projected.ProjectedMemoryOperationFactory;

public class PeripheralAwarenessOperationFactory implements ProjectedMemoryOperationFactory {

  @Override
  public String getName() {
    return PeripheralAwarenessOperation.OPERATION_NAME;
  }

  @Override
  public ProjectedMemoryOperation create(JsonObject state) {
    PeripheralAwarenessOperation operation = new PeripheralAwarenessOperation();
    if (state != null && !state.isEmpty()) {
      operation.restoreState(state);
    }
    return operation;
  }
}
