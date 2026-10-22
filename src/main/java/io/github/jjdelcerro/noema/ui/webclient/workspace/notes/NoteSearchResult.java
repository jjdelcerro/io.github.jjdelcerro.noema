package io.github.jjdelcerro.noema.ui.webclient.workspace.notes;

import java.util.ArrayList;
import java.util.List;

/**
 * Resultado completo de una busqueda sobre el arbol de notas.
 *
 * 'total' es el numero de elementos devueltos, no el numero de coincidencias
 * reales. Si 'truncated' es verdadero, la busqueda encontro mas de las que
 * caben en el limite y el cliente debe saber que la lista no es completa.
 *
 * @author jjdelcerro
 */
public class NoteSearchResult {

  private String query;
  private String mode;
  private String root;
  private String timestamp;
  private int total;
  private boolean truncated;
  private List<NoteSearchItem> items = new ArrayList<>();

  public String getQuery() {
    return this.query;
  }

  public void setQuery(String query) {
    this.query = query;
  }

  public String getMode() {
    return this.mode;
  }

  public void setMode(String mode) {
    this.mode = mode;
  }

  public String getRoot() {
    return this.root;
  }

  public void setRoot(String root) {
    this.root = root;
  }

  public String getTimestamp() {
    return this.timestamp;
  }

  public void setTimestamp(String timestamp) {
    this.timestamp = timestamp;
  }

  public int getTotal() {
    return this.total;
  }

  public void setTotal(int total) {
    this.total = total;
  }

  public boolean isTruncated() {
    return this.truncated;
  }

  public void setTruncated(boolean truncated) {
    this.truncated = truncated;
  }

  public List<NoteSearchItem> getItems() {
    return this.items;
  }

  public void setItems(List<NoteSearchItem> items) {
    this.items = items;
  }
}
