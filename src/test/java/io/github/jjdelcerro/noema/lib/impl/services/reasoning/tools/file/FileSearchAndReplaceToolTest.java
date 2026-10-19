package io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.file;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.github.jjdelcerro.noema.lib.FakeConsole;
import io.github.jjdelcerro.noema.lib.api.Agent;
import io.github.jjdelcerro.noema.lib.api.AgentAccessControl;
import io.github.jjdelcerro.noema.lib.api.AgentActions;
import io.github.jjdelcerro.noema.lib.api.AgentPaths;
import io.github.jjdelcerro.noema.lib.api.settings.AgentSettings;
import io.github.jjdelcerro.noema.lib.impl.AgentAccessControlImpl;
import io.github.jjdelcerro.noema.lib.impl.AgentActionsImpl;
import io.github.jjdelcerro.noema.lib.impl.AgentImpl;
import io.github.jjdelcerro.noema.lib.impl.AgentPathsImpl;
import io.github.jjdelcerro.noema.lib.impl.persistence.FakeEpisodicMemory;
import io.github.jjdelcerro.noema.lib.impl.settings.AgentSettingsImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FileSearchAndReplaceToolTest {

  @TempDir
  Path workspaceDir;

  private Agent agent;
  private AgentSettings settings;
  private FileSearchAndReplaceTool tool;
  private final Gson gson = new Gson();

  @BeforeEach
  public void setUp() {
    AgentPaths paths = new AgentPathsImpl(workspaceDir);
    paths.setupHierarchy();

    settings = new AgentSettingsImpl(paths);
    AgentActions actions = new AgentActionsImpl();

    // Habilitar escritura en sandbox y desactivar backup RCS para pruebas puras de reemplazo
    settings.setProperty("access_control/allow_disk_write", "true");
    settings.setProperty("access_control/enable_rcs_backup", "false");

    AgentAccessControl accessControl = new AgentAccessControlImpl(settings, actions, workspaceDir);
    agent = new AgentImpl(null, null, settings, new FakeConsole(), new FakeEpisodicMemory(), accessControl);

    tool = new FileSearchAndReplaceTool(agent);
  }

  private String executeTool(String path, String oldText, String newText) {
    Map<String, String> args = Map.of(
            "path", path,
            "oldText", oldText != null ? oldText : "",
            "newText", newText != null ? newText : ""
    );
    return tool.execute(gson.toJson(args)).text(); 
  }

  // =========================================================================
  // 1. VALIDACIONES Y CASOS LÍMITE
  // =========================================================================
  @Test
  @DisplayName("Debe fallar si los parámetros requeridos están vacíos")
  public void testMissingParameters() {
    String resNoPath = tool.execute("{\"oldText\": \"foo\", \"newText\": \"bar\"}").text();
    JsonObject jsonNoPath = gson.fromJson(resNoPath, JsonObject.class);
    assertEquals("error", jsonNoPath.get("status").getAsString());

    String resNoOldText = tool.execute("{\"path\": \"test.txt\", \"oldText\": \"\"}").text();
    JsonObject jsonNoOldText = gson.fromJson(resNoOldText, JsonObject.class);
    assertEquals("error", jsonNoOldText.get("status").getAsString());
  }

  @Test
  @DisplayName("Debe fallar si el archivo no existe en el workspace")
  public void testFileDoesNotExist() {
    String response = executeTool("inexistente.txt", "bloque", "nuevo");
    JsonObject json = gson.fromJson(response, JsonObject.class);

    assertEquals("error", json.get("status").getAsString());
    assertTrue(json.get("message").getAsString().contains("El archivo no existe"));
  }

  @Test
  @DisplayName("Debe permitir el borrado de código enviando newText como cadena vacía")
  public void testDeleteCodeBlockWithEmptyNewText() throws IOException {
    Path file = workspaceDir.resolve("delete_test.txt");
    String original = "linea1\nlinea_a_borrar\nlinea3\n";
    Files.writeString(file, original, StandardCharsets.UTF_8);

    String response = executeTool("delete_test.txt", "linea_a_borrar\n", "");
    JsonObject json = gson.fromJson(response, JsonObject.class);

    assertEquals("success", json.get("status").getAsString());
    String updated = Files.readString(file, StandardCharsets.UTF_8);
    assertEquals("linea1\nlinea3\n", updated);
  }

  // =========================================================================
  // 2. NIVEL 1: COINCIDENCIA EXACTA
  // =========================================================================
  @Test
  @DisplayName("Nivel 1: Debe sustituir exactamente y reportar la estrategia exact_match")
  public void testExactMatchReplacement() throws IOException {
    Path file = workspaceDir.resolve("Main.java");
    String initialContent = """
        public class Main {
            public static void main(String[] args) {
                System.out.println("Hello World");
            }
        }
        """;
    Files.writeString(file, initialContent, StandardCharsets.UTF_8);

    String oldBlock = "        System.out.println(\"Hello World\");";
    String newBlock = "        System.out.println(\"Hello Noema\");";

    String response = executeTool("Main.java", oldBlock, newBlock);
    JsonObject json = gson.fromJson(response, JsonObject.class);

    assertEquals("success", json.get("status").getAsString());
    assertEquals("exact_match", json.get("strategy").getAsString());

    String updatedFile = Files.readString(file, StandardCharsets.UTF_8);
    assertTrue(updatedFile.contains("Hello Noema"));
    assertFalse(updatedFile.contains("Hello World"));
  }

  @Test
  @DisplayName("Nivel 1: Debe rechazar el reemplazo exacto si el bloque aparece más de una vez (ambigüedad)")
  public void testExactMatchAmbiguity() throws IOException {
    Path file = workspaceDir.resolve("ambiguous.txt");
    String initialContent = "item = 10;\nitem = 10;\n";
    Files.writeString(file, initialContent, StandardCharsets.UTF_8);

    String response = executeTool("ambiguous.txt", "item = 10;", "item = 20;");
    JsonObject json = gson.fromJson(response, JsonObject.class);

    assertEquals("error", json.get("status").getAsString());
    assertTrue(json.get("message").getAsString().contains("aparece 2 veces"));
  }

  // =========================================================================
  // 3. NIVEL 2: COINCIDENCIA TOLERANTE A INDENTACIÓN
  // =========================================================================
  @Test
  @DisplayName("Nivel 2: Debe adaptar la sangría del nuevo código a la indentación base del archivo")
  public void testIndentationTolerantMatch() throws IOException {
    Path file = workspaceDir.resolve("IndentedService.java");
    // El archivo real tiene 8 espacios de indentación
    String initialContent = """
        class IndentedService {
            void execute() {
                int timeout = 30;
                String mode = "FAST";
            }
        }
        """;
    Files.writeString(file, initialContent, StandardCharsets.UTF_8);

    // El LLM envía el bloque con solo 2 espacios (o sin sangría)
    String oldBlockFromLLM = "  int timeout = 30;\n  String mode = \"FAST\";";
    String newBlockFromLLM = "  int timeout = 60;\n  String mode = \"SAFE\";";

    String response = executeTool("IndentedService.java", oldBlockFromLLM, newBlockFromLLM);
    JsonObject json = gson.fromJson(response, JsonObject.class);

    assertEquals("success", json.get("status").getAsString());
    assertEquals("indentation_tolerant", json.get("strategy").getAsString());

    String updatedFile = Files.readString(file, StandardCharsets.UTF_8);
    // Debe haber respetado los 8 espacios de indentación del archivo original
    assertTrue(updatedFile.contains("        int timeout = 60;"));
    assertTrue(updatedFile.contains("        String mode = \"SAFE\";"));
  }

  @Test
  @DisplayName("Nivel 2: Debe rechazar por ambigüedad si el bloque recortado coincide en múltiples sitios")
  public void testIndentationTolerantAmbiguity() throws IOException {
    Path file = workspaceDir.resolve("multi_block.java");
    // El archivo tiene dos métodos con sangrías diferentes (4 y 8 espacios)
    String initialContent = """
        void a() {
            int x = 1;
            int y = 2;
        }
        void b() {
                int x = 1;
                int y = 2;
        }
        """;
    Files.writeString(file, initialContent, StandardCharsets.UTF_8);

    // Bloque multilínea sin sangría:
    // Al haber un salto de línea seguido de código sin espacios (\nint),
    // indexOf falla en el Nivel 1 (en el archivo siempre hay espacios tras el \n).
    // La búsqueda cae obligatoriamente al Nivel 2, donde se compara línea a línea con trim().
    String oldBlock = "int x = 1;\nint y = 2;";
    String newBlock = "int x = 99;\nint y = 99;";

    String response = executeTool("multi_block.java", oldBlock, newBlock);
    JsonObject json = gson.fromJson(response, JsonObject.class);

    assertEquals("error", json.get("status").getAsString());
    assertTrue(json.get("message").getAsString().contains("coincide con 2 secciones diferentes"));
  }
  
  // =========================================================================
  // 4. NIVEL 3: BÚSQUEDA DIFUSA (DID-YOU-MEAN)
  // =========================================================================
  @Test
  @DisplayName("Nivel 3: Ante una errata menor en oldText debe sugerir el bloque real más parecido")
  public void testFuzzyCandidateSuggestionOnError() throws IOException {
    Path file = workspaceDir.resolve("config.properties");
    String initialContent = """
        server.host=127.0.0.1
        server.port=8080
        server.timeout.ms=15000
        server.active=true
        """;
    Files.writeString(file, initialContent, StandardCharsets.UTF_8);

    // El LLM introduce una pequeña errata en el nombre de la propiedad
    String oldBlockWithTypo = """
        server.host=127.0.0.1
        server.port=8080
        server.timeout_millis=15000
        """;

    String response = executeTool("config.properties", oldBlockWithTypo, "server.port=9090");
    JsonObject json = gson.fromJson(response, JsonObject.class);

    assertEquals("error", json.get("status").getAsString());
    String msg = json.get("message").getAsString();

    assertTrue(msg.contains("No se encontro el bloque 'oldText'"));
    assertTrue(msg.contains("El bloque mas parecido encontrado en el archivo es:"));
    // La sugerencia debe incluir las líneas reales con su número
    assertTrue(msg.contains("server.timeout.ms=15000"));
  }

  // =========================================================================
  // 5. FORMATO CRLF Y VISTA PREVIA (DIFF SNIPPET)
  // =========================================================================
  @Test
  @DisplayName("Debe preservar los saltos de línea Windows (CRLF) si el original los usaba")
  public void testPreserveCrlfLineEndings() throws IOException {
    Path file = workspaceDir.resolve("windows.txt");
    String crlfContent = "linea1\r\nlinea2\r\nlinea3\r\n";
    Files.writeString(file, crlfContent, StandardCharsets.UTF_8);

    String response = executeTool("windows.txt", "linea2", "linea_modificada");
    JsonObject json = gson.fromJson(response, JsonObject.class);

    assertEquals("success", json.get("status").getAsString());
    String updated = Files.readString(file, StandardCharsets.UTF_8);

    assertTrue(updated.contains("\r\n"), "El archivo debe seguir usando saltos CRLF");
    assertEquals("linea1\r\nlinea_modificada\r\nlinea3\r\n", updated);
  }

  @Test
  @DisplayName("La vista previa debe marcar con asterisco (*) las líneas modificadas")
  public void testPreviewDiffSnippetFormatting() throws IOException {
    Path file = workspaceDir.resolve("snippet_test.txt");
    String initialContent = "linea1\nlinea2\nlinea3\nlinea4\nlinea5\n";
    Files.writeString(file, initialContent, StandardCharsets.UTF_8);

    String response = executeTool("snippet_test.txt", "linea3", "linea3_modificada");
    JsonObject json = gson.fromJson(response, JsonObject.class);

    assertEquals("success", json.get("status").getAsString());
    assertNotNull(json.get("preview"));

    String preview = json.get("preview").getAsString();
    // La línea 3 debe tener el indicador '*' de modificación
    assertTrue(preview.contains("3*| linea3_modificada"),
            "La vista previa debe resaltar la línea editada con '*':\n" + preview);
    // Las líneas circundantes deben tener espacio ' ' sin asterisco
    assertTrue(preview.contains("2 | linea2") || preview.contains("1 | linea1"));
  }
}
