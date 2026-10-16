package io.github.jjdelcerro.noema.ui.webclient.workspace.editor;

import com.google.gson.JsonObject;
import dev.langchain4j.data.message.ChatMessage;
import io.github.jjdelcerro.noema.lib.api.memory.projected.ProjectedMemory;
import io.github.jjdelcerro.noema.lib.api.memory.projected.ProjectedMemoryOperation;
import io.github.jjdelcerro.noema.ui.webclient.NoemaWebServer;

import java.util.List;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;

public class EditorAwarenessOperation implements ProjectedMemoryOperation {

    private static final int PRIORITY = 30;

    public EditorAwarenessOperation() {
    }

    @Override
    public String getName() {
        return EditorAwarenessOperationFactory.NAME;
    }

    @Override
    public int getPriority() {
        return PRIORITY;
    }

    @Override
    public void process(ProjectedMemory memory, List<ChatMessage> projectedMessages, List<String> notifications) {
      NoemaWebServer webserver = (NoemaWebServer) memory.getAgent().get("webserver");
      if( webserver == null ) {
        return;
      }
      EditorState state = webserver.getEditorState(memory.getSubchannel());
      if( state == null ) {
        return;
      }
      if( !Strings.CI.equals(memory.getSubchannel(), state.getSubchannel()) ) {
        return;
      }
      if( CollectionUtils.isEmpty(state.getOpenFiles()) ) {
        return;
      }
      String notice = String.format("""
[CONSCIENCIA DEL EDITOR] Archivos cargados: %s
Archivo activo: %s
Cursor en linea: %s
""", StringUtils.join(state.getOpenFiles(), ", "), state.getActiveFile(), state.getCursorLine()+1);
      if( state.getSelectionStartLine() != null && state.getSelectionEndLine() != null) {
        notice += String.format("Líneas seleccionadas: %d-%d\n", state.getSelectionStartLine()+1, state.getSelectionEndLine()+1);
      }
      notifications.add(notice);
    }

    @Override
    public JsonObject getState() {
        return null;
    }

    @Override
    public void restoreState(JsonObject state) {
        // Operacion sin estado persistente propio
    }
}
