package io.github.jjdelcerro.chatagent.lib.impl.services.docmapper;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
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
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.file.Path;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.AbstractMap.SimpleEntry;
import org.apache.commons.lang3.StringUtils;

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
  private EmbeddingModel embeddingModel;
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
    // Cargamos el mismo modelo local de los turnos para consistencia
    this.embeddingModel = new AllMiniLmL6V2EmbeddingModel();
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

      // 1. Gestionar ID
      if (structure.getId() < 0) {
        structure.setId(this.counter.get());
      }

      // 2. Vectorizar el resumen (Summary)
      float[] vector = null;
      String textToEmbed = structure.getSummary();
      if (textToEmbed != null && !textToEmbed.isBlank()) {
        vector = embeddingModel.embed(textToEmbed).content().vector();
      }

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
        ps.setBytes(7, toBytes(vector));
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
    List<DocumentResult> results = new ArrayList<>();
    try {
      Connection conn = this.agent.getServicesDatabase();

      // 1. Construir la consulta base
      StringBuilder sql = new StringBuilder("SELECT * FROM DOCUMENTS WHERE 1=1");
      if (categories != null && !categories.isEmpty()) {
        for (String cat : categories) {
          sql.append(" AND categories LIKE '%,")
                  .append(cat.replace("'", "''")) // Escape básico
                  .append(",%'");
        }
      }

      // 2. Ejecutar y realizar ranking vectorial si hay query
      float[] queryVec = (query != null && !query.isBlank())
              ? embeddingModel.embed(query).content().vector()
              : null;

      PriorityQueue<Map.Entry<Double, DocumentResult>> topK = new PriorityQueue<>(
              Comparator.comparingDouble(Map.Entry::getKey)
      );

      try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql.toString())) {
        while (rs.next()) {
          DocumentResultImpl doc = mapResultSetToDocument(rs);
          double score = 1.0;

          if (queryVec != null) {
            float[] dbVec = fromBytes(rs.getBytes("summary_embedding"));
            score = (dbVec != null) ? cosineSimilarity(queryVec, dbVec) : 0.0;
          }

          doc.score = score;

          // Lógica Top-K
          topK.add(new SimpleEntry<>(score, doc));
          if (topK.size() > maxResults) {
            topK.poll();
          }
        }
      }

      while (!topK.isEmpty()) {
        results.add(topK.poll().getValue());
      }
      Collections.reverse(results);

    } catch (SQLException ex) {
      agent.getConsole().printerrorln("Error en búsqueda de documentos: " + ex.getMessage());
    }
    return results;
  }

  // --- MÉTODOS DE ACCESO A ESTRUCTURA ---
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

  // --- MATEMÁTICAS Y CONVERSIÓN (Igual que en SourceOfTruth) ---
  private static byte[] toBytes(float[] vector) {
    if (vector == null) {
      return null;
    }
    ByteBuffer buffer = ByteBuffer.allocate(vector.length * 4);
    buffer.asFloatBuffer().put(vector);
    return buffer.array();
  }

  private static float[] fromBytes(byte[] bytes) {
    if (bytes == null) {
      return null;
    }
    FloatBuffer buffer = ByteBuffer.wrap(bytes).asFloatBuffer();
    float[] vector = new float[buffer.remaining()];
    buffer.get(vector);
    return vector;
  }

  private static double cosineSimilarity(float[] vA, float[] vB) {
    double dot = 0.0, nA = 0.0, nB = 0.0;
    for (int i = 0; i < vA.length; i++) {
      dot += vA[i] * vB[i];
      nA += vA[i] * vA[i];
      nB += vB[i] * vB[i];
    }
    return (nA == 0 || nB == 0) ? 0.0 : dot / (Math.sqrt(nA) * Math.sqrt(nB));
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

}
