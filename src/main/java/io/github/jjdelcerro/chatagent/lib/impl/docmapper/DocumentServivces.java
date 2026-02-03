package io.github.jjdelcerro.chatagent.lib.impl.docmapper;

import com.google.gson.JsonObject;
import io.github.jjdelcerro.chatagent.lib.Agent;
import io.github.jjdelcerro.chatagent.lib.impl.persistence.Counter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author jjdelcerro
 */
public class DocumentServivces {

  private final Agent agent;
  private Counter counter;
  
  public static class Document {
    String sumary;
    String id; // DOCUMENT-<id>
    String title;
    List<String> categories;
    
    public JsonObject toJson() {
      return null; //TODO por implementar
    }
  }
  
  public DocumentServivces(Agent agent) {
    this.agent = agent;
  }
  
  public void init() {
    try {
      Connection conn = this.agent.getServicesDatabase();
      this.createTables(conn);
      this.counter = Counter.from(conn, "DOCUMENTS");  
    } catch (SQLException ex) {
      ex.printStackTrace(); // TODO: log and user info
    }
  }
  
  private void createTables(Connection conn) throws SQLException {
    try (Statement stmt = conn.createStatement()) {
      stmt.execute("""
                CREATE TABLE IF NOT EXISTS DOCUMENTS (
                    id INT PRIMARY KEY,
                    timestamp TIMESTAMP,
                    path VARCHAR(1024),
                    title VARCHAR(1024),
                    summary VARCHAR(4096),
                    categories VARCHAR(1024),
                    summary_embedding BLOB
                )
            """);
    }
  }

  public void insertOrReplace(DocStructure structure) {
    // TODO: actualiza o inserta el documento en la base de datos. Si el id<0 inserta y actualiza el id de la estructura y la guarda en disco. si id>=0 actualiza si existe.
  }  
  
  public List<Document> searchBySumaries(Agent agent, String query, int maxDocuments) {
    // TODO: deberia funcionar de forma similar a getTurnsByText pero sobre la tabla de DOCUMENTS.
    // Habria una tool "io.github.jjdelcerro.chatagent.lib.impl.tools.docmapper.SearchBySumariesTool"
    return null;
  }
  
  public List<Document> searchByCategories(Agent agent, List<String> categories, int maxDocuments) {
    // TODO: Falta por implementar, buscaria sobre la tabla de DOCUMENTOS campo categorias.
    // Habria una tool "io.github.jjdelcerro.chatagent.lib.impl.tools.docmapper.SearchByCategoriesTool"
    return null;
  }
  
  public List<Document> search(Agent agent, List<String> categories, String query, int maxDocuments) {
    // TODO: Falta por implementar, buscaria sobre la tabla de DOCUMENTOS campo categorias, y sobre lo encontrado aplicaria una busqueda vectorial con el query.
    // Habria una tool "io.github.jjdelcerro.chatagent.lib.impl.tools.docmapper.SearchByCategoriesTool"
    return null;
  }
  
  public DocStructure getDocumentStructure(String docid) {
    // TODO: Falta por implementar, buscaria sobre la tabla de DOCUMENTOS por el id y luego iria al disco a cargar la estructura.
    // Habria una tool "io.github.jjdelcerro.chatagent.lib.impl.tools.docmapper.getDocumentStructureTool"
    return null;
  }
  
  public DocStructure getPartialDocument(String docid, List<String> idsections) {
    // TODO: Falta por implementar, buscaria sobre la tabla de DOCUMENTOS por el id y luego iria al disco a cargar la estructura, y el doc original para recuperar el texo de las secciones e insertarlo en la estructura antes de devolverla.
    // Habria una tool "io.github.jjdelcerro.chatagent.lib.impl.tools.docmapper.getPartialDocument"
    return null;
  }
  
}
