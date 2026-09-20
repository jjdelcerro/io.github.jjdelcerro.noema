package io.github.jjdelcerro.noema.lib.impl;

import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.AgentAccessControl;
import io.github.jjdelcerro.noema.lib.AgentActions;
import io.github.jjdelcerro.noema.lib.AgentPaths;
import io.github.jjdelcerro.noema.lib.FakeConsole;
import io.github.jjdelcerro.noema.lib.impl.FileFuzzySearchUtils.FuzzyMatch;
import io.github.jjdelcerro.noema.lib.impl.persistence.FakeEpisodicMemory;
import io.github.jjdelcerro.noema.lib.impl.settings.AgentSettingsImpl;
import io.github.jjdelcerro.noema.lib.settings.AgentSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FileFuzzySearchUtilsTest {

  @TempDir
  Path tempDir;

  private Agent agent;
  private Path testResourcesDir;
  private Path testFile;

  @BeforeEach
  public void setUp() {
    // 1. Ubicar el directorio de recursos de test y el fichero de fixture directamente
    testResourcesDir = Path.of("src/test/resources").toAbsolutePath().normalize();
    testFile = testResourcesDir.resolve("fixtures/datos-test.md");
    assertTrue(Files.exists(testFile), "El fichero de fixture datos-test.md debe existir en: " + testFile);

    // 2. Jerarquía temporal para el agente
    AgentPaths paths = new AgentPathsImpl(tempDir);
    paths.setupHierarchy();

    AgentSettings settings = new AgentSettingsImpl(paths);
    AgentActions actions = new AgentActionsImpl();

    // 3. Control de acceso: permitimos la lectura en src/test/resources
    AgentAccessControl accessControl = new AgentAccessControlImpl(settings, actions, tempDir);
    accessControl.addAllowedPath(testResourcesDir);

    // 4. Instanciación y arranque estándar del agente:
    // EmbeddingsService se inicializa automáticamente en memoria vía ONNX
    agent = new AgentImpl(null, null, settings, new FakeConsole(), new FakeEpisodicMemory(), accessControl);
    agent.start();
  }

  @AfterEach
  public void tearDown() {
    if (agent != null) {
      agent.stop();
    }
  }

  // =========================================================================
  // 1. BÚSQUEDA SEMÁNTICA PURA (SIN COINCIDENCIA LÉXICA)
  // =========================================================================
  @Test
  @DisplayName("Semántica: Debe localizar la sección de SchedulerService ante una consulta conceptual")
  public void testSemanticSearchSchedulerConcept() {
    // Consulta abstracta que no comparte palabras literales con la cabecera ni con el código
    String query = "planificación diferida de recordatorios y avisos en el tiempo";

    List<FuzzyMatch> results = FileFuzzySearchUtils.search(agent, testFile, query, "**", 3, 0.25);

    assertNotNull(results);
    assertFalse(results.isEmpty(), "Debe encontrar coincidencias semánticas relevantes");

    FuzzyMatch topMatch = results.get(0);
    String contentLower = topMatch.content().toLowerCase();

    assertTrue(
            contentLower.contains("schedulerservice") || contentLower.contains("alarm"),
            "El fragmento más relevante debe pertenecer a la sección del servicio de alarmas"
    );
    assertTrue(topMatch.startLine() >= 1000, "La sección de Scheduler se encuentra a partir de la línea ~1000");
  }

  @Test
  @DisplayName("Semántica: Debe localizar el Sandbox y control de acceso ante conceptos de seguridad")
  public void testSemanticSearchSecuritySandboxConcept() {
    String query = "mecanismos de contención de privilegios y aislamiento de procesos en el sistema operativo";

    List<FuzzyMatch> results = FileFuzzySearchUtils.search(agent, testFile, query, "**", 3, 0.25);

    assertNotNull(results);
    assertFalse(results.isEmpty());

    FuzzyMatch topMatch = results.get(0);
    String contentLower = topMatch.content().toLowerCase();

    assertTrue(
            contentLower.contains("firejail") || contentLower.contains("accesscontrol") || contentLower.contains("sandbox"),
            "Debe apuntar a las secciones de seguridad, firejail o aislamiento de archivos"
    );
  }

  @Test
  @DisplayName("Semántica: Debe localizar el procesamiento de mensajes fusionables (MERGEABLE)")
  public void testSemanticSearchMergeableSensorsConcept() {
    String query = "acumulación de múltiples mensajes en un único bloque continuo para evitar fragmentación";

    List<FuzzyMatch> results = FileFuzzySearchUtils.search(agent, testFile, query, "**", 3, 0.25);

    assertNotNull(results);
    assertFalse(results.isEmpty());

    boolean foundMergeableSection = results.stream()
            .anyMatch(m -> m.content().contains("MERGEABLE") || m.content().contains("Fusionable"));

    assertTrue(foundMergeableSection, "Debe rankear en el Top-3 la sección sobre sensores MERGEABLE");
  }

  // =========================================================================
  // 2. PARÁMETROS LIMIT Y MINSIMILARITY
  // =========================================================================
  @Test
  @DisplayName("Parámetros: Debe respetar estrictamente el parámetro limit y mantener el orden descendente")
  public void testLimitParameterEnforcement() {
    String query = "persistencia y almacenamiento en bases de datos";

    List<FuzzyMatch> limit1 = FileFuzzySearchUtils.search(agent, testFile, query, "**", 1, 0.20);
    List<FuzzyMatch> limit3 = FileFuzzySearchUtils.search(agent, testFile, query, "**", 3, 0.20);
    List<FuzzyMatch> limit5 = FileFuzzySearchUtils.search(agent, testFile, query, "**", 5, 0.20);

    assertEquals(1, limit1.size());
    assertEquals(3, limit3.size());
    assertEquals(5, limit5.size());

    // El resultado más relevante debe coincidir en las tres búsquedas
    assertEquals(limit1.get(0).startLine(), limit3.get(0).startLine());
    assertEquals(limit1.get(0).startLine(), limit5.get(0).startLine());
  }

  @Test
  @DisplayName("Parámetros: Debe respetar el tope de seguridad MAX_LIMIT (20) aunque se soliciten más")
  public void testMaxLimitSafetyCap() {
    String broadQuery = "sistema agente servicio";

    List<FuzzyMatch> results = FileFuzzySearchUtils.search(agent, testFile, broadQuery, "**", 50, 0.10);

    assertTrue(results.size() <= FileFuzzySearchUtils.MAX_LIMIT,
            "El número de resultados nunca debe superar MAX_LIMIT ("+FileFuzzySearchUtils.MAX_LIMIT+")");
  }

  @Test
  @DisplayName("Parámetros: minSimilarity debe discriminar resultados irrelevantes o retornar lista vacía")
  public void testMinSimilarityFiltering() {
    String query = "control de acceso a archivos y permisos";

    List<FuzzyMatch> permissiveResults = FileFuzzySearchUtils.search(agent, testFile, query, "**", 5, 0.25);
    assertFalse(permissiveResults.isEmpty());

    List<FuzzyMatch> strictResults = FileFuzzySearchUtils.search(agent, testFile, query, "**", 5, 0.60);
    assertTrue(strictResults.size() <= permissiveResults.size());

    List<FuzzyMatch> impossibleResults = FileFuzzySearchUtils.search(agent, testFile, query, "**", 5, 0.99);
    assertTrue(impossibleResults.isEmpty(), "Un umbral de 0.99 debe filtrar todos los candidatos");
  }

  // =========================================================================
  // 3. VENTANA DESLIZANTE, SOLAPAMIENTO Y RANGOS DE LÍNEA
  // =========================================================================
  @Test
  @DisplayName("Ventana Deslizante: Ningún fragmento debe superar CHUNK_LINES")
  public void testSlidingWindowChunkSizeInvariant() {
    String query = "arquitectura de componentes del agente";

    List<FuzzyMatch> results = FileFuzzySearchUtils.search(agent, testFile, query, "**", 10, 0.20);
    assertFalse(results.isEmpty());

    for (FuzzyMatch match : results) {
      int lineCount = match.endLine() - match.startLine() + 1;
      assertTrue(lineCount > 0, "El fragmento debe tener al menos una línea");
      assertTrue(lineCount <= FileFuzzySearchUtils.CHUNK_LINES,
              "El tamaño del fragmento no puede exceder CHUNK_LINES ("+FileFuzzySearchUtils.CHUNK_LINES+")");
    }
  }

  @Test
  @DisplayName("Ventana Deslizante: Los índices de inicio deben respetar el avance por STEP_LINES")
  public void testSlidingWindowStepAlignment() {
    String query = "memoria y razonamiento";

    List<FuzzyMatch> results = FileFuzzySearchUtils.search(agent, testFile, query, "**", 10, 0.20);
    assertFalse(results.isEmpty());

    for (FuzzyMatch match : results) {
      // Avance efectivo de FileFuzzySearchUtils.STEP_LINES líneas: inicios en 1, 26, 51, 76...
      int normalizedStart = match.startLine() - 1;
      assertEquals(0, normalizedStart % FileFuzzySearchUtils.STEP_LINES,
              "La línea de inicio (" + match.startLine() + ") debe estar alineada al paso de "+FileFuzzySearchUtils.STEP_LINES+" líneas");
    }
  }

  // =========================================================================
  // 4. BÚSQUEDA EN DIRECTORIOS (src/test/resources), FILTRADO GLOB Y CASOS LÍMITE
  // =========================================================================
  @Test
  @DisplayName("Directorio: Debe escanear recursivamente src/test/resources con glob coincidente y descartar no coincidentes")
  public void testDirectoryScanWithFilePattern() {
    String query = "protocolo de compactación de memoria";

    // Con patrón **/*.md debe encontrarlo dentro de src/test/resources
    List<FuzzyMatch> mdResults = FileFuzzySearchUtils.search(agent, testResourcesDir, query, "**/*.md", 3, 0.25);
    assertFalse(mdResults.isEmpty(), "Debe encontrar coincidencias filtrando por **/*.md");
    assertTrue(mdResults.get(0).file().endsWith("datos-test.md"), "El archivo coincidente debe ser datos-test.md");

    // Con patrón **/*.java no debe haber coincidencias en la carpeta de recursos de test
    List<FuzzyMatch> javaResults = FileFuzzySearchUtils.search(agent, testResourcesDir, query, "**/*.java", 3, 0.25);
    assertTrue(javaResults.isEmpty(), "No debe haber archivos coincidentes con **/*.java en src/test/resources");
  }

  @Test
  @DisplayName("Casos Límite: Consulta vacía o archivo inexistente deben retornar lista vacía")
  public void testEdgeCases() {
    assertTrue(FileFuzzySearchUtils.search(agent, testFile, null, "**", 5, 0.25).isEmpty());
    assertTrue(FileFuzzySearchUtils.search(agent, testFile, "   ", "**", 5, 0.25).isEmpty());
    assertTrue(FileFuzzySearchUtils.search(agent, testResourcesDir.resolve("no_existe.md"), "test", "**", 5, 0.25).isEmpty());
    assertTrue(FileFuzzySearchUtils.search(null, testFile, "test", "**", 5, 0.25).isEmpty());
  }
}
