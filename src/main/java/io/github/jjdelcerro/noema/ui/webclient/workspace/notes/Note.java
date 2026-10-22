package io.github.jjdelcerro.noema.ui.webclient.workspace.notes;

import java.util.ArrayList;
import java.util.List;

/**
 * Nota del sistema de notas de la WebUI.
 *
 * Se corresponde con un fichero XML en .noema-agent/notes/YYYY/MM/{id}.xml.
 * Solo se modelan los campos que la aplicacion conoce: id, titulo, tags,
 * fechas y contenido. Cualquier otro nodo presente en el fichero se preserva
 * al guardar, pero no se expone en este objeto.
 *
 * Las fechas se guardan como texto ISO-8601 (por ejemplo 2026-10-19T16:15:00Z)
 * para que coincidan exactamente con lo que se escribe en el XML y con lo que
 * se serializa en el fichero de cache de busqueda, sin necesidad de un
 * adaptador de Gson para java.time.
 *
 * @author jjdelcerro
 */
public class Note {

  private String id;
  private String title;
  private List<String> tags;
  private String created;
  private String updated;
  private String content;

  /**
   * Constructor vacio requerido por Gson.
   */
  public Note() {
    this.tags = new ArrayList<>();
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public List<String> getTags() {
    return tags;
  }

  /**
   * Normaliza la lista para que nunca sea null: el resto del codigo puede
   * recorrerla sin comprobaciones defensivas.
   *
   * @param tags etiquetas de la nota
   */
  public void setTags(List<String> tags) {
    if (tags == null) {
      this.tags = new ArrayList<>();
    } else {
      this.tags = tags;
    }
  }

  public String getCreated() {
    return created;
  }

  public void setCreated(String created) {
    this.created = created;
  }

  public String getUpdated() {
    return updated;
  }

  public void setUpdated(String updated) {
    this.updated = updated;
  }

  public String getContent() {
    return content;
  }

  public void setContent(String content) {
    this.content = content;
  }

  @Override
  public String toString() {
    return this.id + " : " + this.title;
  }
}
