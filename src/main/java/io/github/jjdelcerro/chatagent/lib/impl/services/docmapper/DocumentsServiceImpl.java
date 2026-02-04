package io.github.jjdelcerro.chatagent.lib.impl.services.docmapper;

import io.github.jjdelcerro.chatagent.lib.Agent;
import io.github.jjdelcerro.chatagent.lib.Agent.ModelParameters;
import io.github.jjdelcerro.chatagent.lib.AgentService;
import io.github.jjdelcerro.chatagent.lib.AgentServiceFactory;
import io.github.jjdelcerro.chatagent.lib.AgentSettings;
import io.github.jjdelcerro.chatagent.lib.AgentTool;
import io.github.jjdelcerro.chatagent.lib.impl.persistence.Counter;
import io.github.jjdelcerro.chatagent.lib.impl.services.docmapper.tools.DocumentSearchByCategoriesTool;
import io.github.jjdelcerro.chatagent.lib.impl.services.docmapper.tools.DocumentSearchBySumariesTool;
import io.github.jjdelcerro.chatagent.lib.impl.services.docmapper.tools.DocumentSearchTool;
import io.github.jjdelcerro.chatagent.lib.impl.services.docmapper.tools.GetDocumentStructureTool;
import io.github.jjdelcerro.chatagent.lib.impl.services.docmapper.tools.GetPartialDocumentTool;
import io.github.jjdelcerro.chatagent.lib.impl.services.embeddings.EmbeddingFilter;
import io.github.jjdelcerro.chatagent.lib.impl.services.embeddings.EmbeddingsService;
import io.github.jjdelcerro.chatagent.lib.persistence.Turn;
import java.nio.file.Path;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.AbstractMap.SimpleEntry;

/**
 * Servicio central para la gestión de documentos. Combina persistencia en base
 * de datos H2 (metadatos y vectores) con acceso a disco (estructuras y
 * contenido original).
 */
public class DocumentsServiceImpl implements AgentService, DocumentsService {

  // Clase de transporte para los resultados
  public static class DocumentResultImpl implements DocumentResult {

    public String docId; // DOCUMENT-<id>
    public String title;
    public String summary;
    public List<String> categories;
    public String path;
    public double score; // Para ranking semántico

    @Override
    public String getDocumentId() {
      return this.docId;
    }

    @Override
    public String getTitle() {
      return this.title;
    }

    @Override
    public String getSumary() {
      return this.summary;
    }

    @Override
    public String getPath() {
      return this.path;
    }

    @Override
    public List<String> getCategories() {
      return this.categories;
    }

    @Override
    public double getScore() {
      return this.score;
    }
  }

  private final Agent agent;
  private Counter counter;
  private boolean running;
  private final AgentServiceFactory factory;

  public DocumentsServiceImpl(AgentServiceFactory factory, Agent agent) {
    this.factory = factory;
    this.agent = agent;
    this.running = false;
  }

  @Override
  public AgentServiceFactory getFactory() {
    return factory;
  }

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public boolean isRunning() {
    return this.running;
  }

  @Override
  public void start() {
    if (!this.canStart()) {
      return;
    }
    try {
      Connection conn = this.agent.getServicesDatabase();
      this.createTables(conn);
      this.counter = Counter.from(conn, "DOCUMENTS");
      this.running = true;
    } catch (SQLException ex) {
      agent.getConsole().printerrorln("Error inicializando DocumentServices: " + ex.getMessage());
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

  /**
   * Registra o actualiza un documento en el sistema.
   */
  @Override
  public void insertOrReplace(DocumentStructure structure, Path docPath) {
    try {
      Connection conn = this.agent.getServicesDatabase();
      EmbeddingsService embedding = (EmbeddingsService) agent.getService(EmbeddingsService.NAME);

      // 1. Gestionar ID
      if (structure.getId() < 0) {
        structure.setId(this.counter.get());
      }

      // 2. Vectorizar el resumen (Summary)
      String textToEmbed = structure.getSummary();
      float[] vector = embedding.embed(textToEmbed);

      // 3. Serializar categorías con el truco de las comas: ,cat1,cat2,
      String categoriesStr = serializeCategories(structure.getCategories());

      String sql = """
                MERGE INTO DOCUMENTS (id, timestamp, path, title, summary, categories, summary_embedding)
                KEY(id)
                VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

      try (PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setInt(1, structure.getId());
        ps.setTimestamp(2, Timestamp.from(Instant.now()));
        ps.setString(3, docPath.toAbsolutePath().toString());
        ps.setString(4, structure.getTitle());
        ps.setString(5, structure.getSummary());
        ps.setString(6, categoriesStr);
        ps.setBytes(7, embedding.toBytes(vector));
        ps.executeUpdate();
      }

      // 4. Guardar estructura en disco (JSON)
      structure.save(docPath);

    } catch (Exception ex) {
      agent.getConsole().printerrorln("Error al insertar documento: " + ex.getMessage());
    }
  }

  // --- MÉTODOS DE BÚSQUEDA ---
  @Override
  public List<DocumentResult> searchByCategories(List<String> categories, int maxResults) {
    return search(categories, null, maxResults);
  }

  @Override
  public List<DocumentResult> searchBySummaries(String query, int maxResults) {
    return search(null, query, maxResults);
  }

  /**
   * Búsqueda híbrida: Filtro por categorías (SQL) + Ranking por resumen
   * (Vectorial).
   */
  @Override
  public List<DocumentResult> search(List<String> categories, String query, int maxResults) {
    EmbeddingsService embedding = (EmbeddingsService) agent.getService(EmbeddingsService.NAME);
    EmbeddingFilter<DocumentResult> search = embedding.createEmbeddingFilter(query, maxResults);
    try {
      Connection conn = this.agent.getServicesDatabase();
      StringBuilder sql = new StringBuilder("SELECT * FROM DOCUMENTS WHERE 1=1");
      if (categories != null && !categories.isEmpty()) {
        for (String cat : categories) {
          sql.append(" AND categories LIKE '%,")
                  .append(cat.replace("'", "''")) // Escape básico
                  .append(",%'");
        }
      }
      try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql.toString())) {
        while (rs.next()) {
          DocumentResultImpl doc = mapResultSetToDocument(rs);
          search.add(rs.getBytes("summary_embedding"), doc);
        }
      }
    } catch (SQLException ex) {
      agent.getConsole().printerrorln("Error en búsqueda de documentos: " + ex.getMessage());
    }
    return search.get();
  }

  @Override
  public String getDocumentStructureXML(String docIdStr) {
    int id = parseDocId(docIdStr);
    DocumentStructure struct = loadStructureById(id);
    return (struct != null) ? struct.toXML() : "<error>Documento no encontrado</error>";
  }

  @Override
  public String getPartialDocumentXML(String docIdStr, List<String> sectionIds) {
    int id = parseDocId(docIdStr);
    DocumentStructure struct = loadStructureById(id);
    if (struct == null) {
      return "<error>Documento no encontrado</error>";
    }

//    Path docPath = Path.of(getDocumentPathFromDb(id));
    // Inyectar el texto solo en las secciones pedidas
    for (String sId : sectionIds) {
      DocumentStructure.DocumentStructureEntry entry = struct.get(sId);
      if (entry != null) {
        try {
          // Usamos el nuevo método de StructureEntry que accede por offset
          entry.setFullText(entry.getContents(null)); // Pasamos null si getContents ya usa el Path internamente
          // O si implementaste entry.getContents(Path):
          // entry.setFullText(entry.getContents(docPath)); 
        } catch (Exception e) {
          entry.setFullText("Error al leer contenido: " + e.getMessage());
        }
      }
    }

    return struct.toXML(sectionIds, true);
  }

  // --- HELPERS INTERNOS ---
  private String serializeCategories(List<String> cats) {
    if (cats == null || cats.isEmpty()) {
      return "";
    }
    return "," + String.join(",", cats) + ",";
  }

  private DocumentResultImpl mapResultSetToDocument(ResultSet rs) throws SQLException {
    DocumentResultImpl d = new DocumentResultImpl();
    d.docId = "DOCUMENT-" + rs.getInt("id");
    d.title = rs.getString("title");
    d.summary = rs.getString("summary");
    d.path = rs.getString("path");
    String cats = rs.getString("categories");
    d.categories = (cats == null || cats.isEmpty())
            ? new ArrayList<>()
            : Arrays.asList(cats.substring(1, cats.length() - 1).split(","));
    return d;
  }

  private String getDocumentPathFromDb(int id) {
    try {
      Connection conn = this.agent.getServicesDatabase();
      try (PreparedStatement ps = conn.prepareStatement("SELECT path FROM DOCUMENTS WHERE id = ?")) {
        ps.setInt(1, id);
        ResultSet rs = ps.executeQuery();
        if (rs.next()) {
          return rs.getString("path");
        }
      }
    } catch (SQLException ignored) {
    }
    return null;
  }

  private DocumentStructure loadStructureById(int id) {
    String pathStr = getDocumentPathFromDb(id);
    if (pathStr == null) {
      return null;
    }
    return DocumentStructure.from(Path.of(pathStr));
  }

  private int parseDocId(String docIdStr) {
    try {
      if (docIdStr.startsWith("DOCUMENT-")) {
        return Integer.parseInt(docIdStr.substring(9));
      }
      return Integer.parseInt(docIdStr);
    } catch (Exception e) {
      return -1;
    }
  }

  @Override
  public ModelParameters getModelParameters(String name) {
    AgentSettings settings = this.agent.getSettings();
    switch (name) {
      case "DOCMAPPER_REASONING":
        return new ModelParameters(
                settings.getProperty(DOCMAPPER_REASONING_PROVIDER_URL),
                settings.getProperty(DOCMAPPER_REASONING_PROVIDER_API_KEY),
                settings.getProperty(DOCMAPPER_REASONING_MODEL_ID),
                0.0
        );
      case "DOCMAPPER_BASIC":
        return new ModelParameters(
                settings.getProperty(DOCMAPPER_BASIC_PROVIDER_URL),
                settings.getProperty(DOCMAPPER_BASIC_PROVIDER_API_KEY),
                settings.getProperty(DOCMAPPER_BASIC_MODEL_ID),
                0.0
        );
    }
    return null;
  }

  @Override
  public boolean canStart() {
    return this.factory.canStart(agent.getSettings());
  }

  @Override
  public List<AgentTool> getTools() {
    AgentTool[] tools = new AgentTool[]{
      new DocumentSearchTool(this.agent),
      new DocumentSearchByCategoriesTool(this.agent),
      new DocumentSearchBySumariesTool(this.agent),
      new GetDocumentStructureTool(this.agent),
      new GetPartialDocumentTool(this.agent)
    };
    return Arrays.asList(tools);
  }

  @Override
  public void indexDocument(Path docPath) {
      DocumentMapper mapper = new DocumentMapper(agent);
      mapper.processDocument(docPath);
  }

}
