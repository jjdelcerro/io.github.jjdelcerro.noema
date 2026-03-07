package io.github.jjdelcerro.noema.lib.impl.persistence;

import io.github.jjdelcerro.noema.lib.persistence.SourceOfTruth;
import io.github.jjdelcerro.noema.lib.persistence.CheckPointException;
import io.github.jjdelcerro.noema.lib.persistence.TurnException;
import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.persistence.CheckPoint;
import io.github.jjdelcerro.noema.lib.persistence.Turn;

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

/**
 * Repositorio central que gestiona la persistencia (H2) y la indexación
 * vectorial. Actúa como "Source of Truth" para el estado del agente.
 */
@SuppressWarnings("UseSpecificCatch")
public class SourceOfTruthImpl implements SourceOfTruth {

  private static final Logger LOGGER = LoggerFactory.getLogger(SourceOfTruthImpl.class);

  private static final int MAX_DB_TEXT_SIZE = 2048; // 2KB

  private static final String CHECKPOINTS_FOLDER = "checkpoints";
  private static final String CSVLOG_FILE = "turns.csv";

  private final Counter turnCounter;
  private final Counter checkpointCounter;
  private final Agent agent;
  
  private SourceOfTruthImpl(Agent agent) {
    this.agent = agent;
    createTables();
    this.turnCounter = Counter.from(this.getConnection(), "turnos");
    this.checkpointCounter = Counter.from(this.getConnection(), "checkpoints");
  }

  public static SourceOfTruth from(Agent agent) {
    return new SourceOfTruthImpl(agent);
  }

  private Counter getTurnCounter() {
    return this.turnCounter;
  }

  private Counter getCheckpointCounter() {
    return this.checkpointCounter;
  }

  private ConnectionSupplier getConnection() {
    return this.agent.getMemoryDatabase();
  }

  private Path getDataFolder() {
    return this.agent.getPaths().getDataFolder();
  }

  private AgentConsole getConsole() {
    return this.agent.getConsole();
  }

  private void createTables() {

    try (Connection conn = this.getConnection().get(); Statement stmt = conn.createStatement()) {
      // Tabla de Turnos con soporte BLOB para vectores
      stmt.execute(SQLProvider.from(getConnection()).get("SourceOfTtuth_createTables_turnos", """
                CREATE TABLE IF NOT EXISTS turnos (
                    id INT PRIMARY KEY,
                    timestamp TIMESTAMP,
                    contenttype VARCHAR(50),
                    text_user CLOB,
                    text_thinking CLOB,
                    text_model CLOB,
                    tool_call CLOB,
                    tool_result CLOB,
                    embedding_blob BLOB
                )
            """));

      // Tabla de CheckPoints (solo metadatos)
      stmt.execute(SQLProvider.from(getConnection()).get("SourceOfTtuth_createTables_checkpoints", """
                CREATE TABLE IF NOT EXISTS checkpoints (
                    id INT PRIMARY KEY,
                    cp_first INT,
                    cp_last INT,
                    timestamp TIMESTAMP
                )
            """));
    } catch (SQLException ex) {
      throw new RuntimeException("Can't create tables turnos/checkpoints", ex);
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

      String sql = SQLProvider.from(getConnection()).get("SourceOfTruth_add_turn",
              """
                INSERT INTO turnos (id, timestamp, contenttype, text_user, text_thinking, 
                                    text_model, tool_call, tool_result, embedding_blob) 
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """);

      try (Connection conn = getConnection().get(); PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setInt(1, turn.getId());
        ps.setTimestamp(2, Timestamp.valueOf(turn.getTimestamp()));
        ps.setString(3, dbContentType); // Usamos el tipo calculado para DB
        ps.setString(4, turn.getTextUser());
        ps.setString(5, turn.getTextModelThinking());
        ps.setString(6, turn.getTextModel());
        ps.setString(7, turn.getToolCall());
        ps.setString(8, dbToolResult); // Usamos el texto procesado (Full o Resumen)
        ps.setBytes(9, blobBytes);
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

    // Política de recorte para persistencia
    return String.format(
            "{\"status\": \"success\", \"original_size_chars\": %d, \"note\": \"Data truncated in DB. Original data was processed in memory.\"}",
            originalText.length()
    );
  }

  /**
   * Persiste los metadatos de un CheckPoint en la base de datos.
   * <p>
   * Nota: El contenido textual (archivo .md) ya debe haber sido gestionado por
   * la clase CheckPoint antes de llamar a este método.
   * <p>
   * Lógica de ID: - Si cp.getId() < 0: Se genera un nuevo ID usando el contador
   * interno. @param checkpoint
   */
  @Override
  public synchronized void add(CheckPoint checkpoint) {
    try {
      // 1. Gestión del ID
      int checkpointid = checkpoint.getId();
      if (checkpointid < 0) {
        checkpointid = this.getCheckpointCounter().get();
        ((CheckPointImpl) checkpoint).setId(checkpointid);
      }

      // 2. Persistencia de metadatos
      String sql = SQLProvider.from(getConnection()).get(
              "SourceOfTtuth_add_checkpoint",
              "INSERT INTO checkpoints (id, cp_first, cp_last, timestamp) VALUES (?, ?, ?, ?)"
      );
      try (Connection conn = getConnection().get(); PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setInt(1, checkpointid);
        ps.setInt(2, checkpoint.getTurnFirst());
        ps.setInt(3, checkpoint.getTurnLast());
        ps.setTimestamp(4, Timestamp.valueOf(checkpoint.getTimestamp()));
        ps.executeUpdate();
      }
      ((CheckPointImpl) checkpoint).saveTextToDisk();
    } catch (Exception ex) {
      throw new CheckPointException("Can't add turn", ex);
    }
  }

  @Override
  public synchronized Turn getTurnById(int id) {
    try {
      String sql = SQLProvider.from(getConnection()).get(
              "SourceOfTtuth_getTurnById",
              "SELECT * FROM turnos WHERE id = ?"
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
  public synchronized CheckPoint getCheckPointById(int id) {
    try {
      String sql = SQLProvider.from(getConnection()).get(
              "SourceOfTtuth_getCheckPointById",
              "SELECT * FROM checkpoints WHERE id = ?"
      );
      try (Connection conn = getConnection().get(); PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setInt(1, id);
        try (ResultSet rs = ps.executeQuery()) {
          if (rs.next()) {
            return mapResultSetToCheckPoint(rs);
          }
        }
      }
      return null;
    } catch (Exception ex) {
      throw new TurnException("Can't add turn", ex);
    }

  }

  @Override
  public synchronized CheckPoint getLatestCheckPoint() {
    try {
      String sql = SQLProvider.from(getConnection()).get(
              "SourceOfTtuth_getLatestCheckPoint",
              "SELECT * FROM checkpoints ORDER BY id DESC LIMIT 1"
      );
      try (Connection conn = getConnection().get(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
        if (rs.next()) {
          return mapResultSetToCheckPoint(rs);
        }
      }
      return null;
    } catch (Exception ex) {
      throw new TurnException("Can't add turn", ex);
    }
  }

  /**
   * Recupera todos los turnos que aún no han sido consolidados en un
   * CheckPoint.Estrategia: Obtener el último CP y pedir turnos con ID >
   * CP.last_turn_id.
   *
   * @return
   */
  @Override
  public synchronized List<Turn> getUnconsolidatedTurns() {
    try {
      CheckPoint lastCp = getLatestCheckPoint();
      int thresholdId = (lastCp != null) ? lastCp.getTurnLast() : 0;

      List<Turn> result = new ArrayList<>();
      String sql = SQLProvider.from(getConnection()).get(
              "SourceOfTtuth_getUnconsolidatedTurns",
              "SELECT * FROM turnos WHERE id > ? ORDER BY id ASC"
      );

      try (Connection conn = getConnection().get(); PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setInt(1, thresholdId);
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
  public synchronized List<Turn> getTurnsByIds(int first, int last) {
    try {
      List<Turn> result = new ArrayList<>();
      String sql = SQLProvider.from(getConnection()).get(
              "SourceOfTtuth_getTurnsByIds",
              "SELECT * FROM turnos WHERE id BETWEEN ? AND ? ORDER BY id ASC"
      );
      try (Connection conn = getConnection().get(); PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setInt(1, first);
        ps.setInt(2, last);
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
  public synchronized List<Turn> getTurnsByText(String query, int maxResults) {
    /* 
        La idea seria tener una implementacion de este metodo para utilizar con H2 en
        entornos personales, y para entornos con cargas mas altas ir sustituir H2 por
        PostgreSQL con el soporte de pgvector.
     */
    try {
      EmbeddingsService embedding = (EmbeddingsService) agent.getService(EmbeddingsService.NAME);
      EmbeddingFilter<Turn> search = embedding.createEmbeddingFilter(query, maxResults);

      String sql = SQLProvider.from(getConnection()).get(
              "SourceOfTtuth_getTurnsByText",
              "SELECT * FROM turnos WHERE embedding_blob IS NOT NULL"
      );
      try (Connection conn = getConnection().get(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
        while (rs.next()) {
          byte[] blob = rs.getBytes("embedding_blob");
          float[] dbVec = search.toFloat(blob);
          if (dbVec != null) {
            Turn turn = mapResultSetToTurn(rs, dbVec);
            search.add(dbVec, turn);
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
            rs.getString("text_user"),
            rs.getString("text_thinking"),
            rs.getString("text_model"),
            rs.getString("tool_call"),
            rs.getString("tool_result"),
            cachedVec // Inyectamos el vector ya deserializado si lo tenemos
    );
  }

  private CheckPoint mapResultSetToCheckPoint(ResultSet rs) throws SQLException {
    return CheckPointImpl.from(
            rs.getInt("id"),
            rs.getInt("cp_first"),
            rs.getInt("cp_last"),
            rs.getTimestamp("timestamp").toLocalDateTime(),
            this.getDataFolder().resolve(CHECKPOINTS_FOLDER)
    );
  }

  @Override
  public synchronized CheckPoint createCheckPoint(int turnFirst, int turnLast, LocalDateTime timestamp, String text) {
    CheckPoint cp = CheckPointImpl.create(-1, turnFirst, turnLast, timestamp, text, getDataFolder().resolve(CHECKPOINTS_FOLDER));
    return cp;
  }

  @Override
  public synchronized Turn createTurn(LocalDateTime timestamp, String contenttype,
          String textUser, String textModelThinking, String textModel,
          String toolCall, String toolResult, float[] embedding) {
    return TurnImpl.from(timestamp, contenttype, textUser, textModelThinking,
            textModel, toolCall, toolResult, embedding);
  }

}
