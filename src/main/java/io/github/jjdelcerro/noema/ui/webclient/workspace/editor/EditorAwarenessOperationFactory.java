package io.github.jjdelcerro.noema.ui.webclient.workspace.editor;


import com.google.gson.JsonObject;
import io.github.jjdelcerro.noema.lib.api.memory.projected.ProjectedMemoryOperation;
import io.github.jjdelcerro.noema.lib.api.memory.projected.ProjectedMemoryOperationFactory;

public class EditorAwarenessOperationFactory implements ProjectedMemoryOperationFactory {

    public static final String NAME = "editor_perception";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public ProjectedMemoryOperation create(JsonObject state) {
        EditorAwarenessOperation operation = new EditorAwarenessOperation();
        if (state != null && !state.isEmpty()) {
            operation.restoreState(state);
        }
        return operation;
    }
}
