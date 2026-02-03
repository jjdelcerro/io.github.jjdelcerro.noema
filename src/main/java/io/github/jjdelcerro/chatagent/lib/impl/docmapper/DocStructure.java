package io.github.jjdelcerro.chatagent.lib.impl.docmapper;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import io.github.jjdelcerro.chatagent.lib.impl.docmapper.DocStructure.StructureEntry;
import java.nio.file.Path;
import java.util.Iterator;

/**
 *
 * @author jjdelcerro
 */
public class DocStructure implements Iterable<StructureEntry> {


  public class StructureEntry {

    private String title;
    private String parent;
    private int level;
    private int startLineNumber;
    private int offset; // No se incluye en el csv. Offset de la seccion en bytes dentro del fichero.
    private boolean empty; // No se incluye en el csv
    private int endLineNumber; // No se incluye en el csv
    private String summary; // No se incluye en el csv
    private String fulltext;// No se incluye en el csv, si activa el flag dirty

    // TODO: los setters pondran dirty a true.
    
    public StructureEntry() {

    }

    public String getTitle() {
      return title;
    }

    public String getParent() {
      return parent;
    }

    public int getLevel() {
      return level;
    }

    public int getStartLineNumber() {
      return startLineNumber;
    }

    public int getEndLineNumber() {
      return endLineNumber;
    }

    public String toCSV() {
      // TODO: devuelve una linea de texto con el formato en CSV de la entrie.
      return null;
    }

    public JsonObject toJson() {
      // TODO: devuelve un objecto Json con el contenido completo de la entrie.
      return null;
    }

    public JsonObject toJsonWithoutSumary() {
      // TODO: devuelve un objecto Json con el contenido completo de la entrie.
      return null;
    }

    public boolean isEmpty() {
      return this.empty;
    }

    String getContents(TextContent lines) {
      throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }

    void setSummary(String entrySummary) {
      dirty = true;
      throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }

    CharSequence getSummary() {
      throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }

    void setCategories(List<String> categories) {
      dirty = true;
      throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }

    public String getId() {
      // Lo usara en conversarion-manager a traves de las tools para acceder a los datos de una seccion.
      return "SECTION-"+this.getStartLineNumber();
    }
    
  }

  private List<StructureEntry> entries;
  private String title; // TODO add get/set
  private String summary; // TODO add get/set
  private boolean dirty;
  private int id;  

  public DocStructure() {
    this.entries = new ArrayList<>();
    this.dirty = false;
    this.id = -1;
  }

  public int getId() {
    return id;
  }

  public void setId(int id) {
    this.id = id;
  }
  
  public static DocStructure from(JsonObject json) {
    // TODO: Recibe un objeto json con con title, summary y entries y reconstruye el objeto DocStructure
    return null;
  }

  public static DocStructure from(String csv) {
    // TODO: Recibe un texto con formato CSV reconstruye el objeto DocStructure.
    return null;
  }

  public static DocStructure from(Path document) {
    // TODO: Recupera la structura del documento del fichero que este junto al path indicado con la extension ".struct" que se espera este en formato json. Si no existe devuelve null.
    return null;
  }

  public JsonObject toJson() {
    // TODO: Construye un json con title, summary y entries de esta clase 
    return null;
  }

  public JsonObject toJsonWithoutSummaries() {
    // TODO: Construye un json con title, y entries de esta clase 
    return null;
  }

  public String toCSV() {
    // TODO: Construye un string en formato CSV con los entry de esta clase.
    return null;
  }

  @Override
  public Iterator<StructureEntry> iterator() {
    // TODO: Implementar
    return null;
  }

  public int size() {
    return this.entries.size();
  }

  public boolean isEmpty() {
    return this.entries.isEmpty();
  }

  public void add(String title, String parent, int level, int startLineNumber) {
    // TODO: Implementar. Ademas de añadir la entry calcula endLineNumber y empty del anterior entry.
    this.dirty = true;
  }

  public StructureEntry get(int n) {
    return this.entries.get(n);
  }

  public StructureEntry get(String id) {
    // Recupera un entry por id.
    for (StructureEntry entry : this) {
      if( entry.getId().equalsIgnoreCase(id) ) {
        return entry;
      }
    }
    return null;
  }

  public void save(Path document) {
    // TODO: guarda la estructura junto al documento con el nombre del documento y la extension ".struct" en formato Json.
    // Si hay endLineNumber vacios los calcula en funcion del siguiente elemento y actualiza empty.
    // El ultimo entry siempre tendra empty a false.
    // El proceso de guardado nunca guarda los fulltext.
    this.dirty = false;
  }

  public String joinAll() {
    // TODO: junta en un solo string los titulos y sumarios de cada una de las entry y o devuelve.
    return null;
  }

  public void setSummary(String text) {
    this.dirty = true;
    throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
  }

  public CharSequence getSummary() {
    throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
  }

  void setCategories(List<String> categories) {
    this.dirty = true;
    throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
  }

  boolean isDirty() {
    return this.dirty;
  }

  void consolide(TextContent lines) {
    // TODO: actualiza offset, y endLineNumber del ultimo entry
  }

  public void removeSumeries() {
    // TODO: Elimina los sumarios de los entries en memoria, no persiste el cambio.
  }
  
  public void setFullText(String sectionid, String text) {
    // TODO: añade a la entrada de la seccion el fulltext suministrado. El fulltext nunca persistira en disco.
  }
}
