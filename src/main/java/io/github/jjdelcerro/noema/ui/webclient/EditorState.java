package io.github.jjdelcerro.noema.ui.webclient;

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

  private String terminalId;
  private List<OpenFile> openFiles;
  private String activeFile;
  private Integer cursorLine;
  private Integer selectionStartLine;
  private Integer selectionEndLine;

  public EditorState() {
    // Constructor vacio requerido por Gson.
  }

  public String getTerminalId() {
    return terminalId;
  }

  public void setTerminalId(String terminalId) {
    this.terminalId = terminalId;
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
  }
}
