package io.github.jjdelcerro.noema.lib.impl.services.documents;

import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.Agent.ModelParameters;
import io.github.jjdelcerro.noema.lib.AgentService;
import io.github.jjdelcerro.noema.lib.AgentServiceFactory;
import io.github.jjdelcerro.noema.lib.AgentTool;
import io.github.jjdelcerro.noema.lib.ConnectionSupplier;
import io.github.jjdelcerro.noema.lib.impl.DateUtils;
import io.github.jjdelcerro.noema.lib.impl.ModelParametersImpl;
import io.github.jjdelcerro.noema.lib.impl.SQLProvider;
import io.github.jjdelcerro.noema.lib.impl.persistence.Counter;
import io.github.jjdelcerro.noema.lib.impl.services.documents.tools.DocumentSearchByCategoriesTool;
import io.github.jjdelcerro.noema.lib.impl.services.documents.tools.DocumentSearchBySumariesTool;
import io.github.jjdelcerro.noema.lib.impl.services.documents.tools.DocumentSearchTool;
import io.github.jjdelcerro.noema.lib.impl.services.documents.tools.GetDocumentStructureTool;
import io.github.jjdelcerro.noema.lib.impl.services.documents.tools.GetPartialDocumentTool;
import io.github.jjdelcerro.noema.lib.impl.services.embeddings.EmbeddingFilter;
import io.github.jjdelcerro.noema.lib.impl.services.embeddings.EmbeddingsService;
import io.github.jjdelcerro.noema.lib.services.sensors.SensorNature;
import io.github.jjdelcerro.noema.lib.settings.AgentSettings;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Servicio central para la gestión de documentos. Combina persistencia en base
 * de datos H2 (metadatos y vectores) con acceso a disco (estructuras y
 * contenido original).
 */
@SuppressWarnings("UseSpecificCatch")
public class DocumentsServiceImpl implements AgentService, DocumentsService {

  private static final Logger LOGGER = LoggerFactory.getLogger(DocumentsServiceImpl.class);

  public static final String SENSOR_NAME = "DOCUMENTS";
  private static final String SENSOR_LABEL = "Documents";
  private static final String SENSOR_DESCRIPTION = "Documents"; // FIXME: poner una descripcion decente para el LLM.

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
    this.agent.registerSensor(
            SENSOR_NAME,
            SENSOR_LABEL,
            SensorNature.DISCRETE,
            SENSOR_DESCRIPTION
    );
    String[] resources = new String[]{
      "var/config/prompts/documents/extract-structure.md",
      "var/config/prompts/documents/sumary-and-categorize.md"
    };
    for (String resPath : resources) {
      this.agent.installResource(resPath);
    }
    try (
            Connection conn = this.agent.getServicesDatabase().get()) {
      this.createTables(conn);
      this.counter = Counter.from(this.agent.getServicesDatabase(), "DOCUMENTS");
      this.running = true;
    } catch (Exception ex) {
      LOGGER.warn("Error inicializando DocumentServices", ex);
      agent.getConsole().printSystemError("Error inicializando DocumentServices: " + ex.getMessage());
    }
  }

  private ConnectionSupplier getConnection() {
    return this.agent.getServicesDatabase();
  }

  private void createTables(Connection conn) throws SQLException {
    String sql = SQLProvider.from(getConnection()).get(
            "DocumentsService_createTables_documents",
            """
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
    try (Statement stmt = conn.createStatement()) {
      stmt.execute(sql);
    }
  }

  /**
   * Registra o actualiza un documento en el sistema.
   */
  @Override
  public void insertOrReplace(DocumentStructure structure, Path docPath) {
    try (
            Connection conn = this.agent.getServicesDatabase().get()) {
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

      String sql = SQLProvider.from(getConnection()).get(
              "DocumentsService_insertOrReplace",
              """
                MERGE INTO DOCUMENTS (id, timestamp, path, title, summary, categories, summary_embedding)
                KEY(id)
                VALUES (?, ?, ?, ?, ?, ?, ?)
              """);

      try (PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setInt(1, structure.getId());
        ps.setTimestamp(2, DateUtils.timestampNow());
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
      LOGGER.warn("Error al insertar documento '" + Objects.toString(docPath) + "'.", ex);
      agent.getConsole().printSystemError("Error al insertar documento: " + ex.getMessage());
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
    try (
            Connection conn = this.getConnection().get()) {
      String sql = SQLProvider.from(this.getConnection()).getSearchDocumentsByCategories(categories);
      try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
        while (rs.next()) {
          DocumentResultImpl doc = mapResultSetToDocument(rs);
          double score = search.add(rs.getBytes("summary_embedding"), doc);
          doc.score = score;
        }
      }
    } catch (Exception ex) {
      LOGGER.warn("Error durante la busqueda de documentos (query=" + query + ").", ex);
      agent.getConsole().printSystemError("Error en búsqueda de documentos: " + ex.getMessage());
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
      LOGGER.warn("Documento no encontrado '" + docIdStr + "'.");
      return "<error>Documento no encontrado</error>";
    }

    // Inyectar el texto solo en las secciones pedidas
    for (String sId : sectionIds) {
      DocumentStructure.DocumentStructureEntry entry = struct.get(sId);
      if (entry != null) {
        try {
          entry.setFullText(entry.getContents(null)); // Pasamos null, getContents ya usa el Path internamente
        } catch (Exception e) {
          LOGGER.warn("No se ha podido cargar el contenido de 'DOCUMENT-" + docIdStr + ":" + sId + "'.", e);
          entry.setFullText("Error al leer contenido: " + e.getMessage());
        }
      }
    }

    return struct.toXML(sectionIds, true);
  }

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
    try (
            Connection conn = this.agent.getServicesDatabase().get()) {
      String sql = SQLProvider.from(getConnection()).get(
              "DocumentsService_insertOrReplace",
              "SELECT path FROM DOCUMENTS WHERE id = ?"
      );
      try (PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setInt(1, id);
        ResultSet rs = ps.executeQuery();
        if (rs.next()) {
          return rs.getString("path");
        }
      }
    } catch (Exception ex) {
      LOGGER.warn("No se ha podido recuperar la ruta del documento 'DOCUMENT-" + id + "'.", ex);
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
      LOGGER.warn("Id de documento '" + docIdStr + "' no valido.", e);
      return -1;
    }
  }

  @Override
  public ModelParameters getModelParameters(String name) {
    AgentSettings settings = this.agent.getSettings();
    switch (name) {
      case "DOCMAPPER_REASONING" -> {
        return new ModelParametersImpl(
                settings.getPropertyAsString(DOCMAPPER_REASONING_PROVIDER_URL),
                settings.getPropertyAsString(DOCMAPPER_REASONING_PROVIDER_API_KEY),
                settings.getPropertyAsString(DOCMAPPER_REASONING_MODEL_ID),
                0.0
        );
      }
      case "DOCMAPPER_BASIC" -> {
        return new ModelParametersImpl(
                settings.getPropertyAsString(DOCMAPPER_BASIC_PROVIDER_URL),
                settings.getPropertyAsString(DOCMAPPER_BASIC_PROVIDER_API_KEY),
                settings.getPropertyAsString(DOCMAPPER_BASIC_MODEL_ID),
                0.0
        );
      }
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
    DocumentStructureExtractor mapper = new DocumentStructureExtractor(agent);
    mapper.processDocument(docPath);
  }

  @Override
  public void stop() {
    this.running = false;
  }

}
