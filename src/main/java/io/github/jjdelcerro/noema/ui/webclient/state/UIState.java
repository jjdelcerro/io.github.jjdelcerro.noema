package io.github.jjdelcerro.noema.ui.webclient.state;

import java.util.ArrayList;
import java.util.List;

/**
 * Snapshot of the user interface state reported by the web client for a
 * single terminal.
 *
 * <p>The state is ephemeral and non transactional: every report replaces the
 * previous one for the same component. Components are independent, so a
 * report may carry only one of them and the others keep their previous
 * value.</p>
 */
public class UIState {

    private String subchannel;
    private Editor editor = new Editor();
    private Notes notes;

    public String getSubchannel() {
        return subchannel;
    }

    public void setSubchannel(String subchannel) {
        this.subchannel = subchannel;
    }

    public Editor getEditor() {
        return editor;
    }

    public void setEditor(Editor editor) {
        this.editor = editor;
    }

    public Notes getNotes() {
        return notes;
    }

    public void setNotes(Notes notes) {
        this.notes = notes;
    }

    /**
     * State of the file editor component: open tabs, active file, cursor and
     * current selection.
     */
    public static class Editor {

        private List<OpenFile> openFiles = new ArrayList<>();
        private String activeFile;
        private Integer cursorLine;
        private Integer selectionStartLine;
        private Integer selectionEndLine;
        private String selectionText;

        public List<OpenFile> getOpenFiles() {
            return openFiles;
        }

        public void setOpenFiles(List<OpenFile> openFiles) {
            this.openFiles = openFiles;
        }

        public String getActiveFile() {
            return activeFile;
        }

        public void setActiveFile(String activeFile) {
            this.activeFile = activeFile;
        }

        public Integer getCursorLine() {
            return cursorLine;
        }

        public void setCursorLine(Integer cursorLine) {
            this.cursorLine = cursorLine;
        }

        public Integer getSelectionStartLine() {
            return selectionStartLine;
        }

        public void setSelectionStartLine(Integer selectionStartLine) {
            this.selectionStartLine = selectionStartLine;
        }

        public Integer getSelectionEndLine() {
            return selectionEndLine;
        }

        public void setSelectionEndLine(Integer selectionEndLine) {
            this.selectionEndLine = selectionEndLine;
        }

        public String getSelectionText() {
            return selectionText;
        }

        public void setSelectionText(String selectionText) {
            this.selectionText = selectionText;
        }

        /**
         * Indica si hay una seleccion real: necesita las dos lineas de
         * frontera. El texto puede ser vacio si se selecciono una zona en
         * blanco.
         *
         * @return true si hay rango de seleccion
         */
        public boolean hasSelection() {
            return selectionStartLine != null && selectionEndLine != null;
        }

        /**
         * One file opened in the editor, with its unsaved changes flag.
         */
        public static class OpenFile {

            private String path;
            private Boolean dirty;

            public String getPath() {
                return path;
            }

            public void setPath(String path) {
                this.path = path;
            }

            public Boolean getDirty() {
                return dirty;
            }

            public void setDirty(Boolean dirty) {
                this.dirty = dirty;
            }

            public boolean isDirty() {
                return Boolean.TRUE.equals(dirty);
            }

            @Override
            public String toString() {
                return String.valueOf(path) + "[" + isDirty() + "]";
            }
        }
    }

    /**
     * State of the notes panel component. Only one note can be open at a
     * time, so this component is null while the panel is closed.
     */
    public static class Notes {

        private String title;
        private String path;
        private Integer cursorLine;
        private Integer selectionStartLine;
        private Integer selectionEndLine;

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public Integer getCursorLine() {
            return cursorLine;
        }

        public void setCursorLine(Integer cursorLine) {
            this.cursorLine = cursorLine;
        }

        public Integer getSelectionStartLine() {
            return selectionStartLine;
        }

        public void setSelectionStartLine(Integer selectionStartLine) {
            this.selectionStartLine = selectionStartLine;
        }

        public Integer getSelectionEndLine() {
            return selectionEndLine;
        }

        public void setSelectionEndLine(Integer selectionEndLine) {
            this.selectionEndLine = selectionEndLine;
        }

        /**
         * Indica si hay una seleccion real en la nota abierta.
         *
         * @return true si hay rango de seleccion
         */
        public boolean hasSelection() {
            return selectionStartLine != null && selectionEndLine != null;
        }
    }
}
