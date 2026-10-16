package io.github.jjdelcerro.noema.ui.webclient.workspace.editor;

/**
 *
 * @author jjdelcerro
 */
public class EditorSelection {

  private Integer selectionStartLine;
  private String file;
  private Integer selectionEndLine;
  private String text;
  
  public EditorSelection() {
    
  }
  
  public String getFile() {
    return file;
  }

  public void setFile(String file) {
    this.file = file;
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

  public String getText() {
    return text;
  }

  public void setText(String text) {
    this.text = text;
  }
    
}
