package io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.file;

import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.AgentAccessControl;
import io.github.jjdelcerro.noema.lib.AgentActions;
import io.github.jjdelcerro.noema.lib.AgentPaths;
import io.github.jjdelcerro.noema.lib.FakeConsole;
import io.github.jjdelcerro.noema.lib.impl.AgentAccessControlImpl;
import io.github.jjdelcerro.noema.lib.impl.AgentActionsImpl;
import io.github.jjdelcerro.noema.lib.impl.AgentImpl;
import io.github.jjdelcerro.noema.lib.impl.AgentPathsImpl;
import io.github.jjdelcerro.noema.lib.impl.persistence.FakeEpisodicMemory;
import io.github.jjdelcerro.noema.lib.impl.settings.AgentSettingsImpl;
import io.github.jjdelcerro.noema.lib.settings.AgentSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FileGrepToolTest {

  @TempDir
  Path workspaceDir;

  @TempDir
  Path externalDir;

  private Agent agent;
  private AgentSettings settings;
  private AgentActions actions;
  private FileGrepTool fileGrepTool;
  private Path projectRoot;

  @BeforeEach
  public void setUp() {
    this.projectRoot = Path.of(".").toAbsolutePath().normalize();
    AgentPaths paths = new AgentPathsImpl(workspaceDir);
    paths.setupHierarchy();

    settings = new AgentSettingsImpl(paths);
    actions = new AgentActionsImpl();

    AgentAccessControl accessControl = new AgentAccessControlImpl(settings, actions, workspaceDir);
    accessControl.addAllowedPath(projectRoot);
    accessControl.addNonWritablePath(projectRoot);
    accessControl.addNonReadablePath(projectRoot.resolve("docs"));
    accessControl.addNonReadablePath(projectRoot.resolve("tmp"));
    accessControl.addNonReadablePath(projectRoot.resolve("target"));
    accessControl.addNonReadablePath(projectRoot.resolve("src/test/java"));

    agent = new AgentImpl(null, null, settings, new FakeConsole(), new FakeEpisodicMemory(), accessControl);

    fileGrepTool = new FileGrepTool(agent);
  }

  private void reloadConfig() {
    actions.call(AgentAccessControlImpl.RELOAD_ACTION_NAME, settings);
  }

  @Test
  @DisplayName("Debe encontrar coincidencias dentro de un único archivo")
  public void testGrepSingleFile() throws IOException {
    String jsonArgs = String.format("{\"path\": \"%s\", \"query\": \"1.16.3-beta26\", \"filePattern\": \"pom.xml\"}", this.projectRoot.toString());
    String response = fileGrepTool.execute(jsonArgs);

    assertTrue(response.contains("STATUS: OK"));
    assertTrue(response.contains("<version>1.16.3-beta26</version>"));
  }

  @Test
  @DisplayName("Debe buscar recursivamente en directorios y subdirectorios")
  public void testGrepDirectoryRecursive() throws IOException {
    String jsonArgs = String.format("{\"path\": \"%s\", \"query\": \"eventDispatcher\", \"filePattern\": \"**/*\"}", this.projectRoot.toString());
    String response = fileGrepTool.execute(jsonArgs);

    assertTrue(response.contains("STATUS: OK"));
    assertTrue(response.contains("ReasoningServiceImpl.java"));
  }

  @Test
  @DisplayName("Debe encontrar pom.xml aunque se use el patron **/pom.xml")
  public void testGrepPomXmlWithGlob() {
    String jsonArgs = String.format(
            "{\"path\": \"%s\", \"query\": \"artifactId\", \"filePattern\": \"**/pom.xml\"}",
            this.projectRoot.toString().replace("\\", "/")
    );
    String response = fileGrepTool.execute(jsonArgs);

    assertTrue(response.contains("STATUS: OK"));
    assertTrue(response.contains("pom.xml"));
    assertTrue(response.contains("io.github.jjdelcerro.noema.main"));
  }

  @Test
  @DisplayName("Debe listar todas las interfaces del paquete lib filtrando por **/*.java")
  public void testGrepInterfacesJava() {
    String jsonArgs = String.format(
            "{\"path\": \"%s\", \"query\": \"public interface Agent\", \"filePattern\": \"**/*.java\"}",
            this.projectRoot.toString().replace("\\", "/")
    );
    String response = fileGrepTool.execute(jsonArgs);

    assertTrue(response.contains("STATUS: OK"));
    assertTrue(response.contains("Agent.java"));
    assertTrue(response.contains("AgentConsole.java"));
    assertTrue(response.contains("AgentSettings.java"));
  }

  @Test
  @DisplayName("Debe encontrar una clase concreta en la jerarquia de implementacion")
  public void testGrepConcreteClass() {
    String jsonArgs = String.format(
            "{\"path\": \"%s\", \"query\": \"public class AgentManagerImpl\", \"filePattern\": \"**/*.java\"}",
            this.projectRoot.toString().replace("\\", "/")
    );
    String response = fileGrepTool.execute(jsonArgs);

    assertTrue(response.contains("STATUS: OK"));
    assertTrue(response.contains("AgentManagerImpl.java"));
  }

  @Test
  @DisplayName("Debe paginar y truncar ante una busqueda masiva como query '.'")
  public void testGrepMassiveOutput() {
    String jsonArgs = String.format(
            "{\"path\": \"%s\", \"query\": \".\", \"filePattern\": \"**/*.java\"}",
            this.projectRoot.toString().replace("\\", "/")
    );
    String response = fileGrepTool.execute(jsonArgs);

    assertTrue(response.contains("STATUS: OK"));
    assertTrue(response.contains("TOTAL_LINES:"));
    assertTrue(response.contains("HINT:"));
  }

  @Test
  @DisplayName("Debe denegar la busqueda en carpetas excluidas del proyecto (target, tmp)")
  public void testGrepExcludedDirectories() {
    Path targetDir = this.projectRoot.resolve("target");
    String jsonArgs = String.format(
            "{\"path\": \"%s\", \"query\": \"class\"}",
            targetDir.toString().replace("\\", "/")
    );
    String response = fileGrepTool.execute(jsonArgs);

    assertTrue(response.contains("STATUS: ERROR"));
    assertTrue(response.contains("Access denied or path does not exist"));
  }

  @Test
  @DisplayName("Debe encontrar lineas usando expresiones regulares (ej: ^package )")
  public void testGrepWithRegexStartOfLine() {
    String jsonArgs = String.format(
            "{\"path\": \"%s\", \"query\": \"^package \", \"filePattern\": \"**/*.java\"}",
            this.projectRoot.toString().replace("\\", "/")
    );
    String response = fileGrepTool.execute(jsonArgs);

    assertTrue(response.contains("STATUS: OK"));
    assertTrue(response.contains("EMPTY: false"));
    assertTrue(response.contains("package io.github.jjdelcerro.noema"));
  }

  @Test
  @DisplayName("Debe encontrar coincidencias con comodines regex (ej: class .*ServiceFactory)")
  public void testGrepWithRegexWildcard() {
    String jsonArgs = String.format(
            "{\"path\": \"%s\", \"query\": \"class .*ServiceFactory\", \"filePattern\": \"**/*.java\"}",
            this.projectRoot.toString().replace("\\", "/")
    );
    String response = fileGrepTool.execute(jsonArgs);

    assertTrue(response.contains("STATUS: OK"));
    assertTrue(response.contains("EMPTY: false"));
    assertTrue(response.contains("ReasoningServiceFactory.java") || response.contains("SensorsServiceFactory.java"));
  }

  @Test
  @DisplayName("Debe devolver STATUS: ERROR descriptivo ante una expresion regular invalida")
  public void testGrepWithInvalidRegexSyntax() {
    // Expresión regular inválida con paréntesis sin cerrar
    String jsonArgs = String.format(
            "{\"path\": \"%s\", \"query\": \"class (.*ServiceFactory\", \"mode\": \"regexp\"}",
            this.projectRoot.toString().replace("\\", "/")
    );
    String response = fileGrepTool.execute(jsonArgs);

    assertTrue(response.contains("STATUS: ERROR"));
    assertTrue(response.contains("Invalid regular expression"));
  }

  @Test
  @DisplayName("En modo plaintext debe buscar la cadena literal sin interpretar metacaracteres")
  public void testGrepPlaintextMode() {
    // En modo plaintext, '^package ' busca el símbolo '^' literal, por lo que no debe encontrar coincidencias
    String jsonArgs = String.format(
            "{\"path\": \"%s\", \"query\": \"^package \", \"filePattern\": \"**/*.java\", \"mode\": \"plaintext\"}",
            this.projectRoot.toString().replace("\\", "/")
    );
    String response = fileGrepTool.execute(jsonArgs);

    assertTrue(response.contains("STATUS: OK"));
    assertTrue(response.contains("EMPTY: true"));
  }

}
