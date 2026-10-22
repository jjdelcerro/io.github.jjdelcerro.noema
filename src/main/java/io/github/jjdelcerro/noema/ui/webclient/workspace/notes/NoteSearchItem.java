package io.github.jjdelcerro.noema.ui.webclient.workspace.notes;

import java.util.List;

/**
 * Una coincidencia de busqueda sobre el arbol de notas.
 *
 * Lleva la ruta relativa porque la nota puede haberse movido de mes desde que
 * el cliente la vio por ultima vez: el id es estable, la ruta no.
 *
 * @author jjdelcerro
 */
public class NoteSearchItem {

  private String id;
  private String title;
  private List<String> tags;
  private String path;
  private String updated;
  private int score;

  /**
   * Constructor vacio requerido por Gson.
   */
  public NoteSearchItem() {
  }

  public String getId() {
    return this.id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getTitle() {
    return this.title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public List<String> getTags() {
    return this.tags;
  }

  public void setTags(List<String> tags) {
    this.tags = tags;
  }

  public String getPath() {
    return this.path;
  }

  public void setPath(String path) {
    this.path = path;
  }

  public String getUpdated() {
    return this.updated;
  }

  public void setUpdated(String updated) {
    this.updated = updated;
  }

  public double getScore() {
    return this.score;
  }

  public void setScore(int score) {
    this.score = score;
  }
}
