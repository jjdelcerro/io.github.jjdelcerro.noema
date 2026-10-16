package io.github.jjdelcerro.noema.ui.webclient.workspace.editor;

import java.util.List;

/**
 * Snapshot del estado del editor web en un momento dado, asociado a un
 * terminal (subcanal). Se recibe por push desde el cliente en cada cambio
 * relevante (cambio de pestana, guardado, movimiento de cursor, edicion)
 * y se consulta desde el agente cuando necesita saber que tiene el usuario
 * en pantalla.
 *
 * Es un estado efimero, no persistente: vive en memoria y se pierde al
 * reiniciar el servidor.
 */
public class EditorState {

  private String subchannel;
  private List<OpenFile> openFiles;
  private String activeFile;
  private Integer cursorLine;
  private Integer selectionStartLine;
  private Integer selectionEndLine;
  private String selectionText;

  public EditorState() {
    // Constructor vacio requerido por Gson.
  }

  public String getSubchannel() {
    return subchannel;
  }

  public void setSubchannel(String subchannel) {
    this.subchannel = subchannel;
  }

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
   * Entrada de un fichero abierto en el editor.
   */
  public static class OpenFile {

    private String path;
    private boolean dirty;

    public OpenFile() {
      // Constructor vacio requerido por Gson.
    }

    public String getPath() {
      return path;
    }

    public void setPath(String path) {
      this.path = path;
    }

    public boolean isDirty() {
      return dirty;
    }

    public void setDirty(boolean dirty) {
      this.dirty = dirty;
    }

    @Override
    public String toString() {
      if (this.dirty) {
        return this.path + "[dirty]";
      }
      return this.path;
    }
  }
}
