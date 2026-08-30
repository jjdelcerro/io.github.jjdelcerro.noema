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
import io.github.jjdelcerro.noema.lib.memory.episodic.EpisodicMemory;
import io.github.jjdelcerro.noema.lib.memory.episodic.Turn;
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

public class LookupTurnToolTest {

  @TempDir
  Path tempDir;

  private Agent agent;
  private EpisodicMemory episodicMemory;
  private LookupTurnTool tool;
  private final Gson gson = new Gson();

  @BeforeEach
  public void setUp() {
    AgentPaths paths = new AgentPathsImpl(tempDir);
    paths.setupHierarchy();
    AgentSettings settings = new AgentSettingsImpl(paths);

    File memoryFile = tempDir.resolve("episodic_memory.db").toFile();
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
    tool = new LookupTurnTool(agent);
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
    Turn turn = episodicMemory.createTurn(
            LocalDateTime.now(),
            "annotation",
            Agent.DEFAULT_SUBCHANNEL,
            null,
            null,
            null,
            gson.toJson(args),
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
  @DisplayName("Debe recuperar el turno central junto con la ventana de contexto antes y despues")
  public void testFetchCitationWithContextWindow() {
    // Creamos una secuencia de 5 turnos (IDs 1 al 5)
    for (int i = 1; i <= 5; i++) {
      addChat("Mensaje de usuario " + i, "Respuesta del modelo " + i);
    }

    // Consultamos el turno central 3 con ventana de 1 (debe devolver turnos 2, 3 y 4)
    String jsonArgs = "{\"code\": \"3\", \"context_window\": 1}";
    String rawResponse = tool.execute(jsonArgs);

    JsonObject response = gson.fromJson(rawResponse, JsonObject.class);
    assertEquals("success", response.get("status").getAsString());
    assertEquals("3", response.get("target_code").getAsString());

    JsonArray results = response.getAsJsonArray("retrieved_turns");
    assertNotNull(results);
    assertEquals(3, results.size(), "Debe recuperar exactamente 3 turnos: [2, 3, 4]");

    assertEquals("2", results.get(0).getAsJsonObject().get("code").getAsString());
    assertEquals("3", results.get(1).getAsJsonObject().get("code").getAsString());
    assertEquals("4", results.get(2).getAsJsonObject().get("code").getAsString());
  }

  @Test
  @DisplayName("Debe soportar formatos de ID numericos directos y con prefijo ID-")
  public void testCodeFormatsSupported() {
    addChat("Pregunta sobre arquitectura", "Respuesta inicial");
    Turn target = addChat("Pregunta clave a citar", "Respuesta clave con detalles importantes");
    addChat("Pregunta de cierre", "Respuesta final");

    int targetId = target.getId();

    // 1. Formato numerico directo (ej: "2")
    String res1 = tool.execute(String.format("{\"code\": \"%d\"}", targetId));
    JsonObject json1 = gson.fromJson(res1, JsonObject.class);
    assertEquals("success", json1.get("status").getAsString());

    JsonArray array1 = json1.getAsJsonArray("retrieved_turns");
    assertEquals(3, array1.size(), "Debe devolver 3 turnos (anterior, objetivo y posterior)");
    assertEquals(String.valueOf(targetId), array1.get(1).getAsJsonObject().get("code").getAsString(), "El turno central debe ser el solicitado");

    // 2. Formato con prefijo "ID-" (ej: "ID-2")
    String res2 = tool.execute(String.format("{\"code\": \"ID-%d\"}", targetId));
    JsonObject json2 = gson.fromJson(res2, JsonObject.class);
    assertEquals("success", json2.get("status").getAsString());

    JsonArray array2 = json2.getAsJsonArray("retrieved_turns");
    assertEquals(3, array2.size(), "Debe devolver 3 turnos (anterior, objetivo y posterior)");
    assertEquals(String.valueOf(targetId), array2.get(1).getAsJsonObject().get("code").getAsString(), "El turno central debe ser el solicitado");
  }

  @Test
  @DisplayName("Debe limitar la ventana de contexto al maximo de seguridad (5)")
  public void testMaxContextWindowCappedAtFive() {
    // Creamos 15 turnos
    for (int i = 1; i <= 15; i++) {
      addChat("Pregunta " + i, "Respuesta " + i);
    }

    // Solicitamos el turno 8 con una ventana desmedida (20)
    String jsonArgs = "{\"code\": \"8\", \"context_window\": 20}";
    String rawResponse = tool.execute(jsonArgs);

    JsonObject response = gson.fromJson(rawResponse, JsonObject.class);
    JsonArray results = response.getAsJsonArray("retrieved_turns");

    // Con centro en 8 y tope de ventana 5, el rango maximo es de 8-5 a 8+5 = [3 .. 13] (11 turnos)
    assertEquals(11, results.size(), "La ventana no debe superar 5 turnos antes y 5 turnos despues (11 en total)");
    assertEquals("3", results.get(0).getAsJsonObject().get("code").getAsString());
    assertEquals("13", results.get(results.size() - 1).getAsJsonObject().get("code").getAsString());
  }

  @Test
  @DisplayName("Debe gestionar correctamente los limites en los extremos del historial")
  public void testBoundaryTurnLookup() {
    // Creamos solo 3 turnos (IDs 1, 2, 3)
    addChat("Primer turno de la historia", "Inicio de conversacion.");
    addChat("Segundo turno", "Intermedio.");
    addChat("Tercer turno", "Final.");

    // Consultamos el turno 1 con ventana de 2 (rango teorico: -1 a 3)
    String jsonArgs = "{\"code\": \"1\", \"context_window\": 2}";
    String rawResponse = tool.execute(jsonArgs);

    JsonObject response = gson.fromJson(rawResponse, JsonObject.class);
    assertEquals("success", response.get("status").getAsString());

    JsonArray results = response.getAsJsonArray("retrieved_turns");
    // En base de datos solo existen IDs >= 1, debe devolver los turnos 1, 2 y 3 sin error
    assertEquals(3, results.size());
    assertEquals("1", results.get(0).getAsJsonObject().get("code").getAsString());
  }

  @Test
  @DisplayName("Debe determinar correctamente el rol de cada tipo de turno")
  public void testRoleDetermination() {
    // 1. Turno con prompt de usuario
    Turn tUser = addChat("Pregunta de usuario", null);

    // 2. Turno con respuesta del asistente
    Turn tAssistant = addChat(null, "Respuesta del modelo");

    // 3. Turno de anotacion
    Turn tAnnotation = addAnnotation("doc.md", "Nota de arquitectura", "arquitectura");

    String jsonArgs = String.format("{\"code\": \"%d\", \"context_window\": 2}", tAssistant.getId());
    String rawResponse = tool.execute(jsonArgs);

    JsonObject response = gson.fromJson(rawResponse, JsonObject.class);
    JsonArray results = response.getAsJsonArray("retrieved_turns");

    JsonObject resUser = results.get(0).getAsJsonObject();
    JsonObject resAssistant = results.get(1).getAsJsonObject();
    JsonObject resAnnotation = results.get(2).getAsJsonObject();

    assertEquals("user", resUser.get("role").getAsString());
    assertEquals("assistant", resAssistant.get("role").getAsString());
    assertEquals("annotation", resAnnotation.get("role").getAsString());
  }

  @Test
  @DisplayName("Debe devolver status de error ante argumentos invalidos o malformados")
  public void testInvalidArgumentsErrorHandling() {
    // 1. Sin argumentos
    String resEmpty = tool.execute("{}");
    JsonObject jsonEmpty = gson.fromJson(resEmpty, JsonObject.class);
    assertEquals("error", jsonEmpty.get("status").getAsString());

    // 2. Formato no numerico
    String resInvalid = tool.execute("{\"code\": \"no_es_un_numero\"}");
    JsonObject jsonInvalid = gson.fromJson(resInvalid, JsonObject.class);
    assertEquals("error", jsonInvalid.get("status").getAsString());
  }
}
