package io.github.jjdelcerro.chatagent.lib.impl.persistence;

import io.github.jjdelcerro.chatagent.lib.persistence.SourceOfTruth;
import io.github.jjdelcerro.chatagent.lib.persistence.CheckPointException;
import io.github.jjdelcerro.chatagent.lib.persistence.TurnException;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import io.github.jjdelcerro.chatagent.lib.persistence.CheckPoint;
import io.github.jjdelcerro.chatagent.lib.persistence.Turn;
import io.github.jjdelcerro.chatagent.lib.utils.ConsoleOutput;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Repositorio central que gestiona la persistencia (H2) y la indexación
 * vectorial. Actúa como "Source of Truth" para el estado del agente.
 */
public class SourceOfTruthImpl implements SourceOfTruth {

    private static final int MAX_DB_TEXT_SIZE = 2048; // 2KB
    
    private static final String CHECKPOINTS_FOLDER = "checkpoints";
    private static final String CSVLOG_FILE = "turns.csv";

    private final Connection conn;
    private final File dataFolder;
    private final Counter turnCounter;
    private final Counter checkpointCounter;
    private final EmbeddingModel embeddingModel;
    private final ConsoleOutput console;

    // Constructor privado
    private SourceOfTruthImpl(Connection conn, File dataFolder, Counter turnCounter, Counter checkpointCounter, ConsoleOutput console) {
        this.conn = conn;
        this.dataFolder = dataFolder;
        this.turnCounter = turnCounter;
        this.checkpointCounter = checkpointCounter;
        this.console = console;

        try {
            Files.createDirectories(Paths.get(this.dataFolder.getAbsolutePath(), CHECKPOINTS_FOLDER));
        } catch (IOException ex) {
            throw new RuntimeException("Can't create checkpoints folder", ex);
        }

        // Inicialización del modelo local (ONNX)
        // Esto carga el modelo en memoria RAM (aprox 30-50MB)
        System.out.println(">>> Cargando motor de embeddings local...");
        this.embeddingModel = new AllMiniLmL6V2EmbeddingModel();
    }

    /**
     * Factoría de inicialización. Crea las tablas si no existen.
     */
    public static SourceOfTruth from(Connection conn, File dataFolder, ConsoleOutput console) throws SQLException {
        createTables(conn);
        Counter turnCounter = Counter.from(conn, "turnos");
        Counter checkpointCounter = Counter.from(conn, "checkpoints");
        return new SourceOfTruthImpl(conn, dataFolder, turnCounter, checkpointCounter, console);
    }

    private static void createTables(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            // Tabla de Turnos con soporte BLOB para vectores
            stmt.execute("""
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
            """);

            // Tabla de CheckPoints (solo metadatos)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS checkpoints (
                    id INT PRIMARY KEY,
                    cp_first INT,
                    cp_last INT,
                    timestamp TIMESTAMP
                )
            """);
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
     */
    @Override
    public synchronized void add(Turn turn) {
        try {
            // 1. Gestión del ID (igual que antes)
            if (turn.getId() < 0) {
                int newId = turnCounter.get();
                ((TurnImpl)turn).setId(newId);
            }

            // 2. Embedding (Usa el texto completo del objeto en memoria, lo cual es bueno para la búsqueda)
            float[] vector = turn.getEmbedding();
            if (vector == null) {
                String textToEmbed = turn.getContentForEmbedding();
                if (textToEmbed != null && !textToEmbed.isBlank()) {
                    vector = embeddingModel.embed(textToEmbed).content().vector();
                }
            }
            byte[] blobBytes = (vector != null) ? toBytes(vector) : null;

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

            String sql = """
                INSERT INTO turnos (id, timestamp, contenttype, text_user, text_thinking, 
                                    text_model, tool_call, tool_result, embedding_blob) 
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, turn.getId());
                ps.setTimestamp(2, turn.getTimestamp());
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
        File csvFile = new File(dataFolder, CSVLOG_FILE);
        boolean exists = csvFile.exists();
        try (PrintWriter pw = new PrintWriter(new FileWriter(csvFile, true))) {
            if (!exists) {
                pw.println("code,timestamp,contenttype,text_user,text_model_thinking,text_model,tool_call,tool_result");
            }
            pw.println(turn.toCSVLine());
        } catch (IOException e) {
            console.printerrorln("Error escribiendo en CSV log: " + e.getMessage());
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
     * Nota: El contenido textual (archivo .md) ya debe haber sido gestionado
     * por la clase CheckPoint antes de llamar a este método.
     * <p>
     * Lógica de ID: - Si cp.getId() < 0: Se genera un nuevo ID usando el
     * contador interno.
     */
    @Override
    public synchronized void add(CheckPoint checkpoint) {
        try {
            // 1. Gestión del ID
            int checkpointid = checkpoint.getId();
            if (checkpointid < 0) {
                checkpointid = this.checkpointCounter.get();
                ((CheckPointImpl)checkpoint).setId(checkpointid);
            }

            // 2. Persistencia de metadatos
            String sql = "INSERT INTO checkpoints (id, cp_first, cp_last, timestamp) VALUES (?, ?, ?, ?)";
            try (PreparedStatement ps = this.conn.prepareStatement(sql)) {
                ps.setInt(1, checkpointid);
                ps.setInt(2, checkpoint.getTurnFirst());
                ps.setInt(3, checkpoint.getTurnLast());
                ps.setTimestamp(4, checkpoint.getTimestamp());
                ps.executeUpdate();
            }
            ((CheckPointImpl)checkpoint).saveTextToDisk();
        } catch (Exception ex) {
            throw new CheckPointException("Can't add turn", ex);
        }
    }

    @Override
    public synchronized Turn getTurnById(int id) {
        try {
            String sql = "SELECT * FROM turnos WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
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
            String sql = "SELECT * FROM checkpoints WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
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
            String sql = "SELECT * FROM checkpoints ORDER BY id DESC LIMIT 1";
            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
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
     * CheckPoint. Estrategia: Obtener el último CP y pedir turnos con ID >
     * CP.last_turn_id.
     */
    @Override
    public synchronized List<Turn> getUnconsolidatedTurns() {
        try {
            CheckPoint lastCp = getLatestCheckPoint();
            int thresholdId = (lastCp != null) ? lastCp.getTurnLast() : 0;

            List<Turn> result = new ArrayList<>();
            String sql = "SELECT * FROM turnos WHERE id > ? ORDER BY id ASC";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
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
            String sql = "SELECT * FROM turnos WHERE id BETWEEN ? AND ? ORDER BY id ASC";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
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
        try {
            // 1. Vectorizar la query
            Embedding queryEmb = embeddingModel.embed(query).content();
            float[] queryVec = queryEmb.vector();

            // 2. PriorityQueue para mantener el TOP K
            // Guardamos un Entry<Score, Turn>
            PriorityQueue<Map.Entry<Double, Turn>> topK = new PriorityQueue<>(
                    Comparator.comparingDouble(Map.Entry::getKey) // Orden ascendente (el menor score en la cabeza)
            );

            String sql = "SELECT * FROM turnos WHERE embedding_blob IS NOT NULL";

            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) { // Streaming desde disco

                while (rs.next()) {
                    byte[] blob = rs.getBytes("embedding_blob");
                    float[] dbVec = fromBytes(blob);

                    if (dbVec == null) {
                        continue;
                    }

                    double score = cosineSimilarity(queryVec, dbVec);

                    // Lógica Top-K
                    if (topK.size() < maxResults) {
                        topK.add(new AbstractMap.SimpleEntry<>(score, mapResultSetToTurn(rs, dbVec)));
                    } else {
                        Double minScore = topK.peek().getKey();
                        if (score > minScore) {
                            topK.poll(); // Sacamos el peor
                            topK.add(new AbstractMap.SimpleEntry<>(score, mapResultSetToTurn(rs, dbVec)));
                        }
                    }
                }
            }

            // 3. Convertir a lista y ordenar descendente (el mejor primero)
            List<Turn> sortedResults = new ArrayList<>();
            while (!topK.isEmpty()) {
                sortedResults.add(topK.poll().getValue());
            }
            Collections.reverse(sortedResults);
            return sortedResults;
        } catch (Exception ex) {
            throw new TurnException("Can't add turn", ex);
        }
    }

    // --- MAPEOS Y UTILITARIOS ---
    private Turn mapResultSetToTurn(ResultSet rs) throws SQLException {
        // Versión que lee el blob y lo deserializa
        byte[] blob = rs.getBytes("embedding_blob");
        return mapResultSetToTurn(rs, fromBytes(blob));
    }

    private Turn mapResultSetToTurn(ResultSet rs, float[] cachedVec) throws SQLException {
        return TurnImpl.from(
                rs.getInt("id"),
                rs.getTimestamp("timestamp"),
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
                rs.getTimestamp("timestamp"),
                new File(this.dataFolder,CHECKPOINTS_FOLDER)
        );
    }

    // --- MATEMÁTICAS Y SERIALIZACIÓN ---
    private static byte[] toBytes(float[] vector) {
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

    private static double cosineSimilarity(float[] vectorA, float[] vectorB) {
        if (vectorA.length != vectorB.length) {
            return 0.0;
        }
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < vectorA.length; i++) {
            dotProduct += vectorA[i] * vectorB[i];
            normA += Math.pow(vectorA[i], 2);
            normB += Math.pow(vectorB[i], 2);
        }
        return (normA == 0 || normB == 0) ? 0.0 : dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    @Override
    public synchronized CheckPoint createCheckPoint(int turnFirst, int turnLast, Timestamp timestamp, String text) {
        CheckPoint cp = CheckPointImpl.create(-1, turnFirst, turnLast, timestamp, text, new File(dataFolder,CHECKPOINTS_FOLDER));
        return cp;
    }

    @Override
    public synchronized Turn createTurn(Timestamp timestamp, String contenttype,
            String textUser, String textModelThinking, String textModel,
            String toolCall, String toolResult, float[] embedding) {
        return TurnImpl.from(timestamp, contenttype, textUser, textModelThinking,
                textModel, toolCall, toolResult, embedding);
    }

}