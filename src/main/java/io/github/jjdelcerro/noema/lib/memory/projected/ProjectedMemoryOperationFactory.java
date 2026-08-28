package io.github.jjdelcerro.noema.lib.memory.projected;

import com.google.gson.JsonObject;

public interface ProjectedMemoryOperationFactory {

    String getName();

    ProjectedMemoryOperation create(JsonObject state);
}
