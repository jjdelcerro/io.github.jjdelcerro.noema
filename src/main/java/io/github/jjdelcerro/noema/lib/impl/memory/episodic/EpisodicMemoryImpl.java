package io.github.jjdelcerro.noema.lib.impl.memory.episodic;

import com.google.gson.JsonObject;
import io.github.jjdelcerro.noema.lib.impl.memory.compacted.CompactedMemoryImpl;
import io.github.jjdelcerro.noema.lib.memory.compacted.CompactedMemoryException;
import io.github.jjdelcerro.noema.lib.memory.episodic.TurnException;
import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.memory.episodic.Turn;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import io.github.jjdelcerro.noema.lib.AgentConsole;
import io.github.jjdelcerro.noema.lib.ConnectionSupplier;
import io.github.jjdelcerro.noema.lib.impl.SQLProvider;
import io.github.jjdelcerro.noema.lib.impl.services.embeddings.EmbeddingFilter;
import io.github.jjdelcerro.noema.lib.impl.services.embeddings.EmbeddingsService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.github.jjdelcerro.noema.lib.memory.episodic.EpisodicMemory;
import io.github.jjdelcerro.noema.lib.memory.compacted.CompactedMemory;

/**
 * Repositorio central que gestiona la persistencia (H2) y la indexación
 * vectorial. Actúa como "Source of Truth" para el estado del agente.
 *
 * TODO: Antes SourceOfTruthImpl, habria que actualizar la documentacion con
 * este cambio
 */
@SuppressWarnings("UseSpecificCatch")
public class EpisodicMemoryImpl implements EpisodicMemory {

  private static final Logger LOGGER = LoggerFactory.getLogger(EpisodicMemoryImpl.class);

  private static final int MAX_DB_TEXT_SIZE = 2048; // 2KB

  private static final String COMPACTEDMEMORY_FOLDER = "compactedmemory";
  private static final String CSVLOG_FILE = "turns.csv";

  private final Counter turnCounter;
  private final Counter compactedMemoryCounter;
  private final Agent agent;

  private EpisodicMemoryImpl(Agent agent) {
    this.agent = agent;
    createTables();
    this.turnCounter = Counter.from(this.getConnection(), "episodicmemory");
    this.compactedMemoryCounter = Counter.from(this.getConnection(), "compactedmemory");
  }

  public static EpisodicMemory from(Agent agent) {
    return new EpisodicMemoryImpl(agent);
  }

  private Counter getTurnCounter() {
    return this.turnCounter;
  }

  private Counter getCompactedMemoryCounter() {
    return this.compactedMemoryCounter;
  }

  private ConnectionSupplier getConnection() {
    return this.agent.getMemoryDatabase();
  }

  private Path getDataFolder() {
    return this.agent.getPaths().getDataFolder();
  }

  private AgentConsole getConsole() {
    return this.agent.getCurrentConsole();
  }

  private void createTables() {

    try (Connection conn = this.getConnection().get(); Statement stmt = conn.createStatement()) {
      // Tabla de episodicmemory con soporte BLOB para vectores
      stmt.execute(SQLProvider.from(getConnection()).get("SourceOfTtuth_createTables_turnos", """
            CREATE TABLE IF NOT EXISTS episodicmemory (
                id INT PRIMARY KEY,
                timestamp TIMESTAMP,
                contenttype VARCHAR(50),
                subchannel VARCHAR(20),
                annotation_type VARCHAR(100),
                text_user CLOB,
                text_thinking CLOB,
                text_model CLOB,
                tool_call CLOB,
                tool_result CLOB,
                embedding_blob BLOB
            )                                                                                              
            """));

      // Tabla de CompactedMemory (solo metadatos)
      stmt.execute(SQLProvider.from(getConnection()).get("SourceOfTtuth_createTables_checkpoints", """
                CREATE TABLE IF NOT EXISTS compactedmemory (
                    id INT PRIMARY KEY,
                    cm_first INT,
                    cm_last INT,
                    timestamp TIMESTAMP,
                    subchannel VARCHAR(20)
                )
            """));
    } catch (SQLException ex) {
      throw new RuntimeException("Can't create tables episodicmemory/compactedmemory", ex);
    }
  }

  /**
   * Persiste un Turno en la base de datos.
   * <p>
   * Lógica de ID: - Si turn.getId() < 0: Se genera un nuevo ID usando el contador interno.
   * - Si turn.getId() >= 0: Se respeta el ID proporcionado (ej:
   * migración/restauración).
   * <p>
   * Lógica de Embedding: - Si turn.getEmbedding() es null, se calcula
   * automáticamente (si hay texto). - Si ya existe, se respeta.
   *
   * @param turn
   */
  @Override
  public synchronized void add(Turn turn) {
    try {
      // 1. Gestión del ID (igual que antes)
      if (turn.getId() < 0) {
        int newId = getTurnCounter().get();
        ((TurnImpl) turn).setId(newId);
      }
      EmbeddingsService embedding = (EmbeddingsService) agent.getService(EmbeddingsService.NAME);

      // 2. Embedding (Usa el texto completo del objeto en memoria, lo cual es bueno para la búsqueda)
      float[] vector = turn.getEmbedding();
      if (vector == null) {
        String textToEmbed = turn.getContentForEmbedding();
        vector = embedding.embed(textToEmbed);
      }
      byte[] blobBytes = (vector != null) ? embedding.toBytes(vector) : null;

      // 3. PREPARACIÓN DE DATOS PARA DB (Aquí aplicamos el recorte)
      // Definimos una función local o lógica para decidir qué texto guardar
      String dbToolResult = applyStoragePolicy(turn.getToolResult());

      // Si recortamos, quizás queramos cambiar el contenttype en BD para avisar
      String dbContentType = turn.getContenttype();
      if (turn.getToolResult() != null && !turn.getToolResult().equals(dbToolResult)) {
        if ("tool_execution".equals(dbContentType)) {
          dbContentType = "tool_execution_summarized";
        }
      }

      String sql = SQLProvider.from(getConnection()).get("EpisodicMemory_add_turn",
              """
                INSERT INTO episodicmemory (id, timestamp, contenttype, subchannel, annotation_type, 
                                  text_user, text_thinking, text_model, tool_call, tool_result, embedding_blob) 
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)                            
            """);

      try (Connection conn = getConnection().get(); PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setInt(1, turn.getId());
        ps.setTimestamp(2, Timestamp.valueOf(turn.getTimestamp()));
        ps.setString(3, dbContentType); // Usamos el tipo calculado para DB
        ps.setString(4, turn.getSubchannel());
        ps.setString(5, turn.getAnnotationType());
        ps.setString(6, turn.getTextUser());
        ps.setString(7, turn.getTextModelThinking());
        ps.setString(8, turn.getTextModel());
        ps.setString(9, turn.getToolCall());
        ps.setString(10, dbToolResult); // Usamos el texto procesado (Full o Resumen)
        ps.setBytes(11, blobBytes);
        ps.executeUpdate();
      }

      // 4. Log CSV
      log2csv(turn);

    } catch (Exception ex) {
      throw new TurnException("Can't add turn", ex);
    }
  }

  private void log2csv(Turn turn) {
    // Nota: El volcado del turno a CSV es solo para temas de depuracion, la 
    // aplicacion no usa para nada los datos del fichero CSV.
    Path csvPath = getDataFolder().resolve(CSVLOG_FILE);
    boolean exists = Files.exists(csvPath);
    try (PrintWriter pw = new PrintWriter(new FileWriter(csvPath.toFile(), true))) {
      if (!exists) {
        pw.println("code,timestamp,contenttype,text_user,text_model_thinking,text_model,tool_call,tool_result");
      }
      pw.println(turn.toCSVLine());
    } catch (Exception e) {
      LOGGER.warn("Error escribiendo en CSV log", e);
      getConsole().printSystemError("Error escribiendo en CSV log: " + e.getMessage());
    }
  }

  private String applyStoragePolicy(String originalText) {
    if (originalText == null) {
      return null;
    }
    if (originalText.length() <= MAX_DB_TEXT_SIZE) {
      return originalText;
    }

    JsonObject json = new JsonObject();
    json.addProperty("status", "truncated");
    json.addProperty("original_size_chars", originalText.length());
    json.addProperty("content", originalText.substring(0, MAX_DB_TEXT_SIZE));
    json.addProperty("note", "Data truncated in DB. First 2KB preserved for indexing and reference.");
    return json.toString();
  }

  /**
   * Persiste los metadatos de un CompactedMemory en la base de datos.
   * <p>
   * Nota: El contenido textual (archivo .md) ya debe haber sido gestionado por
   * la clase CompactedMemory antes de llamar a este método.
   * <p>
   * Lógica de ID: - Si cp.getId() < 0: Se genera un nuevo ID usando el contador
   * interno. @param compactedMemory
   */
  @Override
  public synchronized void add(CompactedMemory compactedMemory) {
    try {
      // 1. Gestión del ID
      int compactedMemoryId = compactedMemory.getId();
      if (compactedMemoryId < 0) {
        compactedMemoryId = this.getCompactedMemoryCounter().get();
        ((CompactedMemoryImpl) compactedMemory).setId(compactedMemoryId);
      }

      // 2. Persistencia de metadatos
      String sql = SQLProvider.from(getConnection()).get(
              "SourceOfTtuth_add_checkpoint",
              "INSERT INTO compactedmemory (id, cm_first, cm_last, timestamp, subchannel) VALUES (?, ?, ?, ?, ?)"
      );
      try (Connection conn = getConnection().get(); PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setInt(1, compactedMemoryId);
        ps.setInt(2, compactedMemory.getTurnFirst());
        ps.setInt(3, compactedMemory.getTurnLast());
        ps.setTimestamp(4, Timestamp.valueOf(compactedMemory.getTimestamp()));
        ps.setString(5, compactedMemory.getSubchannel());
        ps.executeUpdate();
      }
      ((CompactedMemoryImpl) compactedMemory).saveTextToDisk();
    } catch (Exception ex) {
      throw new CompactedMemoryException("Can't add turn", ex);
    }
  }

  @Override
  public synchronized Turn getTurnById(int id) {
    try {
      String sql = SQLProvider.from(getConnection()).get(
              "SourceOfTtuth_getTurnById",
              "SELECT * FROM episodicmemory WHERE id = ?"
      );
      try (Connection conn = getConnection().get(); PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setInt(1, id);
        try (ResultSet rs = ps.executeQuery()) {
          if (rs.next()) {
            return mapResultSetToTurn(rs);
          }
        }
      }
      return null;
    } catch (Exception ex) {
      throw new TurnException("Can't add turn", ex);
    }
  }

  @Override
  public synchronized CompactedMemory getCompactedMemoryById(int id) {
    try {
      String sql = SQLProvider.from(getConnection()).get(
              "SourceOfTtuth_getCheckPointById",
              "SELECT * FROM compactedmemory WHERE id = ?"
      );
      try (Connection conn = getConnection().get(); PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setInt(1, id);
        try (ResultSet rs = ps.executeQuery()) {
          if (rs.next()) {
            return mapResultSetToCompactedMemory(rs);
          }
        }
      }
      return null;
    } catch (Exception ex) {
      throw new TurnException("Can't add turn", ex);
    }

  }

  @Override
  public synchronized CompactedMemory getLatestCompactedMemory(String subchannel) {
    try {
      String sql = SQLProvider.from(getConnection()).get(
              "SourceOfTtuth_getLatestCheckPoint",
              "SELECT * FROM compactedmemory WHERE subchannel = ? ORDER BY id DESC LIMIT 1"
      );
      try (Connection conn = getConnection().get(); PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setString(1, subchannel);
        try (ResultSet rs = ps.executeQuery()) {
          if (rs.next()) {
            return mapResultSetToCompactedMemory(rs);
          }
          return null;
        }
      }
    } catch (Exception ex) {
      throw new TurnException("Can't add turn", ex);
    }
  }

  /**
   * Recupera todos los turnos que aún no han sido consolidados en un
   * CompactedMemory. Estrategia: Obtener el último CP y pedir turnos con ID >
   * CP.last_turn_id.
   *
   * @return
   */
  @Override
  public synchronized List<Turn> getUnconsolidatedTurns(String subchannel) {
    try {
      CompactedMemory lastCp = getLatestCompactedMemory(subchannel);
      int thresholdId = (lastCp != null) ? lastCp.getTurnLast() : 0;

      List<Turn> result = new ArrayList<>();
      String sql = SQLProvider.from(getConnection()).get(
              "SourceOfTtuth_getUnconsolidatedTurns",
              "SELECT * FROM episodicmemory WHERE id > ? AND subchannel = ? ORDER BY id ASC"
      );

      try (Connection conn = getConnection().get(); PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setInt(1, thresholdId);
        ps.setString(2, subchannel);
        try (ResultSet rs = ps.executeQuery()) {
          while (rs.next()) {
            result.add(mapResultSetToTurn(rs));
          }
        }
      }
      return result;
    } catch (Exception ex) {
      throw new TurnException("Can't add turn", ex);
    }
  }

  @Override
  public synchronized List<Turn> getTurnsByIds(String subchannel, int first, int last) {
    try {
      List<Turn> result = new ArrayList<>();
      String sql = SQLProvider.from(getConnection()).get(
              "SourceOfTtuth_getTurnsByIds",
              """
              SELECT * FROM episodicmemory 
                WHERE 
                  (? IS NULL OR subchannel = ?)
                  AND id BETWEEN ? AND ? 
                ORDER BY id ASC
              """
      );
      try (Connection conn = getConnection().get(); PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setString(1, subchannel);
        ps.setString(2, subchannel);
        ps.setInt(3, first);
        ps.setInt(4, last);
        try (ResultSet rs = ps.executeQuery()) {
          while (rs.next()) {
            result.add(mapResultSetToTurn(rs));
          }
        }
      }
      return result;
    } catch (Exception ex) {
      throw new TurnException("Can't add turn", ex);
    }

  }

  @Override
  public List<Turn> getTurnsByText(String subchannel, String query, int limit, double minSimilarity, String annotationType) {
    try {
      EmbeddingsService embedding = (EmbeddingsService) agent.getService(EmbeddingsService.NAME);
      EmbeddingFilter<Turn> search = embedding.createEmbeddingFilter(query, limit, minSimilarity);

      // Corregido el ID de la SQL y la columna (subchannel en lugar de sunchannel)
      String sql = SQLProvider.from(getConnection()).get(
              "EpisodicMemory_getTurnsByText",
              """
                SELECT * FROM episodicmemory 
                WHERE 
                  (? IS NULL OR subchannel = ?)
                  AND (? IS NULL OR annotation_type = ?)
                  AND embedding_blob IS NOT NULL
                ORDER BY id DESC      
                """
      );

      try (Connection conn = getConnection().get(); PreparedStatement ps = conn.prepareStatement(sql)) {

        ps.setString(1, subchannel);
        ps.setString(2, subchannel);
        ps.setString(3, annotationType);
        ps.setString(4, annotationType);

        try (ResultSet rs = ps.executeQuery()) {
          while (rs.next()) {
            byte[] blob = rs.getBytes("embedding_blob");
            float[] dbVec = search.toFloat(blob);
            if (dbVec != null) {
              Turn turn = mapResultSetToTurn(rs, dbVec);
              search.add(dbVec, turn);
            }
          }
        }
      }
      return search.get();

    } catch (Exception ex) {
      throw new TurnException("Can't retrieve turns", ex);
    }
  }

  private Turn mapResultSetToTurn(ResultSet rs) throws SQLException {
    // Versión que lee el blob y lo deserializa
    EmbeddingsService embedding = (EmbeddingsService) agent.getService(EmbeddingsService.NAME);
    byte[] blob = rs.getBytes("embedding_blob");
    return mapResultSetToTurn(rs, embedding.fromBytes(blob));
  }

  private Turn mapResultSetToTurn(ResultSet rs, float[] cachedVec) throws SQLException {
    return TurnImpl.from(
            rs.getInt("id"),
            rs.getTimestamp("timestamp").toLocalDateTime(),
            rs.getString("contenttype"),
            rs.getString("subchannel"),
            rs.getString("text_user"),
            rs.getString("text_thinking"),
            rs.getString("text_model"),
            rs.getString("tool_call"),
            rs.getString("tool_result"),
            rs.getString("annotation_type"),
            cachedVec // Inyectamos el vector ya deserializado si lo tenemos
    );
  }

  private CompactedMemory mapResultSetToCompactedMemory(ResultSet rs) throws SQLException {
    return CompactedMemoryImpl.from(
            rs.getString("subchannel"),
            rs.getInt("id"),
            rs.getInt("cm_first"),
            rs.getInt("cm_last"),
            rs.getTimestamp("timestamp").toLocalDateTime(),
            this.getDataFolder().resolve(COMPACTEDMEMORY_FOLDER)
    );
  }

  @Override
  public synchronized CompactedMemory createCompactedMemory(String subchannel, int turnFirst, int turnLast, LocalDateTime timestamp, String text) {
    CompactedMemory cp = CompactedMemoryImpl.create(subchannel, -1, turnFirst, turnLast, timestamp, text, getDataFolder().resolve(COMPACTEDMEMORY_FOLDER));
    return cp;
  }

  @Override
  public synchronized Turn createTurn(LocalDateTime timestamp, String contenttype,
          String terminalid, String textUser, String textModelThinking, String textModel,
          String toolCall, String toolResult, float[] embedding) {
    return TurnImpl.from(timestamp, contenttype, terminalid, textUser, textModelThinking,
            textModel, toolCall, toolResult, embedding);
  }

}
