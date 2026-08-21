package io.github.jjdelcerro.noema.lib.impl.services.memory.tools;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.AgentPaths;
import io.github.jjdelcerro.noema.lib.ConnectionSupplier;
import io.github.jjdelcerro.noema.lib.FakeConsole;
import io.github.jjdelcerro.noema.lib.impl.AgentImpl;
import io.github.jjdelcerro.noema.lib.impl.AgentPathsImpl;
import io.github.jjdelcerro.noema.lib.impl.settings.AgentSettingsImpl;
import io.github.jjdelcerro.noema.lib.persistence.EpisodicMemory;
import io.github.jjdelcerro.noema.lib.persistence.Turn;
import io.github.jjdelcerro.noema.lib.settings.AgentSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SearchFullHistoryToolTest {

  @TempDir
  Path tempDir;

  private Agent agent;
  private EpisodicMemory episodicMemory;
  private SearchFullHistoryTool tool;
  private final Gson gson = new Gson();

  @BeforeEach
  public void setUp() {
    AgentPaths paths = new AgentPathsImpl(tempDir);
    paths.setupHierarchy();
    AgentSettings settings = new AgentSettingsImpl(paths);

    File memoryFile = tempDir.resolve("memory_test.db").toFile();
    ConnectionSupplier memoryDb = new ConnectionSupplier() {
      @Override
      public Connection get() {
        try {
          return DriverManager.getConnection("jdbc:h2:" + memoryFile.getAbsolutePath() + ";AUTO_SERVER=TRUE", "sa", "");
        } catch (SQLException e) {
          throw new RuntimeException("Error conectando a BD H2 de test", e);
        }
      }

      @Override
      public String getProviderName() {
        return "H2";
      }
    };

    agent = new AgentImpl(memoryDb, null, settings, new FakeConsole());
    agent.start();

    episodicMemory = agent.getEpisodicMemory();
    tool = new SearchFullHistoryTool(agent);
  }

  @AfterEach
  public void tearDown() {
    if (agent != null) {
      agent.stop();
    }
  }

  // =========================================================================
  // HELPERS PARA CREACION Y PERSISTENCIA DE TURNOS
  // =========================================================================
  private Turn addChat(String textUser, String textModel) {
    Turn turn = episodicMemory.createTurn(
            LocalDateTime.now(),
            "chat",
            Agent.DEFAULT_SUBCHANNEL,
            textUser,
            null,
            textModel,
            null,
            null,
            null
    );
    episodicMemory.add(turn);
    return turn;
  }

  private Turn addAnnotation(String source, String note, String type) {
    Map<String, Object> args = new LinkedHashMap<>();
    args.put("source", source);
    args.put("note", note);
    if (type != null) {
      args.put("type", type);
    }
    return addAnnotation(gson.toJson(args));
  }

  private Turn addAnnotation(String toolCallJson) {
    Turn turn = episodicMemory.createTurn(
            LocalDateTime.now(),
            "annotation",
            Agent.DEFAULT_SUBCHANNEL,
            null,
            null,
            null,
            toolCallJson,
            "{\"status\": \"success\"}",
            null
    );
    episodicMemory.add(turn);
    return turn;
  }

  // =========================================================================
  // CASOS DE PRUEBA
  // =========================================================================
  @Test
  @DisplayName("Debe encontrar el turno semánticamente relevante y descartar el ruido")
  public void testSemanticSearchBasic() {
    addChat("Despliegue de contenedores Docker y orquestacion con Kubernetes", "Hemos configurado el cluster.");
    Turn backupTurn = addChat("Estrategia de copias de seguridad automaticas y backups en disco", "Backups diarios a las dos de la madrugada.");
    addChat("Receta tradicional para preparar paella valenciana con verduras", "Se recomienda usar arroz bomba.");

    String jsonArgs = "{\"query\": \"copias de seguridad restauracion\", \"limit\": 2}";
    String rawResponse = tool.execute(jsonArgs);

    JsonObject response = gson.fromJson(rawResponse, JsonObject.class);
    assertEquals("success", response.get("status").getAsString());

    JsonArray results = response.getAsJsonArray("results");
    assertNotNull(results);
    assertTrue(results.size() >= 1, "Debe devolver al menos un resultado relevante");

    JsonObject firstResult = results.get(0).getAsJsonObject();
    assertEquals(String.valueOf(backupTurn.getId()), firstResult.get("code").getAsString());
    assertTrue(firstResult.get("content").getAsString().contains("backups"));
  }

  @Test
  @DisplayName("Debe filtrar estrictamente por type cuando se especifica el tipo de anotacion")
  public void testFilterByAnnotationType() {
    Turn turnArch = addAnnotation("doc.md", "Decidimos usar H2 embebido por simplicidad y portabilidad", "arquitectura");
    addAnnotation("server.log", "Fallo de timeout en base de datos al superar el limite", "bug");
    addChat("Charla general sobre rendimiento en bases de datos", "Explicacion sobre indices y transacciones.");

    String jsonArgs = "{\"query\": \"base de datos\", \"type\": \"arquitectura\"}";
    String rawResponse = tool.execute(jsonArgs);

    JsonObject response = gson.fromJson(rawResponse, JsonObject.class);
    assertEquals("success", response.get("status").getAsString());

    JsonArray results = response.getAsJsonArray("results");
    assertEquals(1, results.size(), "Solo debe devolver la anotacion de tipo arquitectura");

    JsonObject match = results.get(0).getAsJsonObject();
    assertEquals(String.valueOf(turnArch.getId()), match.get("code").getAsString());
    assertEquals("arquitectura", match.get("annotation_type").getAsString());
  }

  @Test
  @DisplayName("Debe respetar el parametro limit y no exceder el numero solicitado")
  public void testLimitParameter() {
    for (int i = 1; i <= 5; i++) {
      addChat("Nota sobre configuracion del servidor numero " + i, "Detalle del puerto y red " + i);
    }

    String jsonArgs = "{\"query\": \"configuracion servidor puerto\", \"limit\": 2}";
    String rawResponse = tool.execute(jsonArgs);

    JsonObject response = gson.fromJson(rawResponse, JsonObject.class);
    JsonArray results = response.getAsJsonArray("results");

    assertEquals(2, results.size(), "El resultado debe limitarse exactamente a 2 elementos");
  }

  @Test
  @DisplayName("Debe filtrar resultados cuando se especifica un umbral de similitud alto")
  public void testSimilarityThresholdFilter() {
    addChat("Instalacion y configuracion del servidor web Apache", "Servidor listo en puerto 80.");

    // Con umbral estricto (0.9), una consulta vagamente relacionada no debe devolver nada
    String jsonArgs = "{\"query\": \"copias de seguridad\", \"similarity\": 0.9}";
    String rawResponse = tool.execute(jsonArgs);

    JsonObject response = gson.fromJson(rawResponse, JsonObject.class);
    assertEquals("success", response.get("status").getAsString());

    JsonArray results = response.getAsJsonArray("results");
    assertEquals(0, results.size(), "No debe superar un umbral de similitud estricto");
  }

  @Test
  @DisplayName("Debe manejar errores de argumentos sin lanzar excepciones no controladas")
  public void testErrorHandlingInvalidArguments() {
    String resEmpty = tool.execute("{}");
    JsonObject jsonEmpty = gson.fromJson(resEmpty, JsonObject.class);
    assertEquals("error", jsonEmpty.get("status").getAsString());

    String resBlank = tool.execute("{\"query\": \"   \"}");
    JsonObject jsonBlank = gson.fromJson(resBlank, JsonObject.class);
    assertEquals("error", jsonBlank.get("status").getAsString());
  }
}
