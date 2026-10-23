package io.github.jjdelcerro.noema.ui.webclient.state;

import com.google.gson.JsonObject;
import dev.langchain4j.data.message.ChatMessage;
import io.github.jjdelcerro.noema.lib.api.memory.projected.ProjectedMemory;
import io.github.jjdelcerro.noema.lib.api.memory.projected.ProjectedMemoryOperation;
import io.github.jjdelcerro.noema.ui.webclient.NoemaWebServer;

import java.util.List;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;

/**
 * Injects into the prompt a notice describing what the user has open in the
 * web client for the current terminal: the editor tabs and, when the notes
 * panel has a note loaded, that note.
 *
 * <p>Each component adds an independent block, so the components can appear
 * or disappear without affecting each other.</p>
 */
public class UIAwarenessOperation implements ProjectedMemoryOperation {

    private static final int PRIORITY = 30;

    public UIAwarenessOperation() {
    }

    @Override
    public String getName() {
        return UIAwarenessOperationFactory.NAME;
    }

    @Override
    public int getPriority() {
        return PRIORITY;
    }

    @Override
    public void process(ProjectedMemory memory, List<ChatMessage> projectedMessages, List<String> notifications) {
        NoemaWebServer webserver = (NoemaWebServer) memory.getAgent().get("webserver");
        if (webserver == null) {
            return;
        }
        UIState state = webserver.getUIState(memory.getSubchannel());
        if (state == null) {
            return;
        }
        if (!Strings.CI.equals(memory.getSubchannel(), state.getSubchannel())) {
            return;
        }
        String editorNotice = buildEditorNotice(state.getEditor());
        if (editorNotice != null) {
            notifications.add(editorNotice);
        }
        String notesNotice = buildNotesNotice(state.getNotes());
        if (notesNotice != null) {
            notifications.add(notesNotice);
        }
    }

    /**
     * Construye el aviso del editor. Devuelve null cuando no hay ningun
     * fichero abierto: sin pestanias no hay nada que contar.
     *
     * @param editor estado del componente editor
     * @return aviso listo para el prompt o null
     */
    private static String buildEditorNotice(UIState.Editor editor) {
        if (editor == null || CollectionUtils.isEmpty(editor.getOpenFiles())) {
            return null;
        }
        StringBuilder notice = new StringBuilder();
        notice.append(String.format("""
[CONSCIENCIA DEL EDITOR] Archivos cargados: %s
Archivo activo: %s
""", StringUtils.join(editor.getOpenFiles(), ", "), StringUtils.defaultString(editor.getActiveFile())));
        if (editor.getCursorLine() != null) {
            notice.append(String.format("Cursor en linea: %d%n", editor.getCursorLine()));
        }
        if (editor.hasSelection()) {
            notice.append(String.format("Lineas seleccionadas: %d-%d%nTexto seleccionado:%n%s",
                    editor.getSelectionStartLine(),
                    editor.getSelectionEndLine(),
                    StringUtils.defaultString(editor.getSelectionText())));
        }
        return notice.toString();
    }

    /**
     * Construye el aviso del panel de notas. Devuelve null cuando no hay
     * ninguna nota abierta.
     *
     * @param notes estado del componente notas
     * @return aviso listo para el prompt o null
     */
    private static String buildNotesNotice(UIState.Notes notes) {
        if (notes == null) {
            return null;
        }
        StringBuilder notice = new StringBuilder();
        notice.append(String.format("""
[CONSCIENCIA DE NOTAS] Nota abierta: %s
Ruta: %s
""", StringUtils.defaultString(notes.getTitle()), StringUtils.defaultString(notes.getPath())));
        if (notes.getCursorLine() != null) {
            notice.append(String.format("Cursor en linea: %d%n", notes.getCursorLine()));
        }
        if (notes.hasSelection()) {
            notice.append(String.format("Lineas seleccionadas: %d-%d%n",
                    notes.getSelectionStartLine(),
                    notes.getSelectionEndLine()));
        }
        return notice.toString();
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
