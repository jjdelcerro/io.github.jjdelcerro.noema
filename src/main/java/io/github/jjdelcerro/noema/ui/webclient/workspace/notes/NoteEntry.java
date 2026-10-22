package io.github.jjdelcerro.noema.ui.webclient.workspace.notes;

/**
 * Entrada del arbol de notas.
 *
 * Representa un hijo directo de una carpeta: o bien una carpeta de anyo o mes,
 * o bien el fichero de una nota. Es la forma que consume el arbol de la WebUI,
 * asi que lleva solo lo necesario para pintar la fila y pedir el contenido
 * despues.
 *
 * @author jjdelcerro
 */
public class NoteEntry {

  /** Carpeta de anyo o mes. */
  public static final String TYPE_FOLDER = "folder";

  /** Fichero de nota. */
  public static final String TYPE_FILE = "file";

  private String name;
  private String type;
  private String path;
  private String id;
  private String title;

  public NoteEntry() {
    // Constructor vacio requerido por Gson.
  }

  /**
   * Crea una entrada de carpeta.
   *
   * @param name nombre visible
   * @param path ruta relativa a la carpeta de notas
   * @return entrada configurada
   */
  public static NoteEntry folder(String name, String path) {
    NoteEntry entry = new NoteEntry();
    entry.name = name;
    entry.type = TYPE_FOLDER;
    entry.path = path;
    return entry;
  }

  /**
   * Crea una entrada de nota.
   *
   * @param name nombre visible
   * @param path ruta relativa a la carpeta de notas
   * @param id identificador de la nota
   * @param title titulo de la nota
   * @return entrada configurada
   */
  public static NoteEntry file(String name, String path, String id, String title) {
    NoteEntry entry = new NoteEntry();
    entry.name = name;
    entry.type = TYPE_FILE;
    entry.path = path;
    entry.id = id;
    entry.title = title;
    return entry;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public String getPath() {
    return path;
  }

  public void setPath(String path) {
    this.path = path;
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

  @Override
  public String toString() {
    return this.type + " : " + this.path;
  }
}
