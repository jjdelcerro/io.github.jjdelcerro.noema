package io.github.jjdelcerro.noema.lib.impl.services.reasoning;

import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.AgentLocator;
import io.github.jjdelcerro.noema.lib.AgentManager;
import io.github.jjdelcerro.noema.lib.AgentPaths;
import io.github.jjdelcerro.noema.lib.AgentTool;
import io.github.jjdelcerro.noema.lib.FakeAgentUIManager;
import io.github.jjdelcerro.noema.lib.impl.services.memory.tools.AnnotateObservationTool;
import io.github.jjdelcerro.noema.lib.impl.services.memory.tools.LookupTurnTool;
import io.github.jjdelcerro.noema.lib.impl.services.memory.tools.SearchFullHistoryTool;
import io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.file.FileFuzzyGrepTool;
import io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.file.FileGrepTool;
import io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.file.FileReadTool;
import io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.file.ReadPaginatedResourceTool;
import io.github.jjdelcerro.noema.lib.memory.episodic.Turn;
import io.github.jjdelcerro.noema.lib.services.reasoning.ReasoningService;
import io.github.jjdelcerro.noema.lib.settings.AgentSettings;
import io.github.jjdelcerro.noema.main.BootUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static io.github.jjdelcerro.noema.lib.services.memory.MemoryConsolidationService.MEMORY_MODEL_ID;
import static io.github.jjdelcerro.noema.lib.services.memory.MemoryConsolidationService.MEMORY_PROVIDER_API_KEY;
import static io.github.jjdelcerro.noema.lib.services.memory.MemoryConsolidationService.MEMORY_PROVIDER_URL;
import static io.github.jjdelcerro.noema.lib.services.reasoning.ReasoningService.REASONING_MODEL_ID;
import static io.github.jjdelcerro.noema.lib.services.reasoning.ReasoningService.REASONING_PROVIDER_API_KEY;
import static io.github.jjdelcerro.noema.lib.services.reasoning.ReasoningService.REASONING_PROVIDER_URL;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test E2E de optimización y arbitraje de herramientas.
 * <p>
 * A diferencia de {@link NeedleInHaystackE2ETest} (que prueba la resiliencia de
 * la memoria bajo ingesta masiva forzada), esta suite evalúa la economía
 * cognitiva: ante una pregunta técnica sobre un documento extenso, el modelo
 * debe elegir de forma autónoma el acceso quirúrgico indexado (grep)
 * resolviendo la consulta en segundos y en 1-2 turnos, sin activar la
 * paginación secuencial.
 */
@Tag("e2e")
public class TargetedRetrievalE2ETest {

  @TempDir
  Path tempDir;

  private Path workspaceDir;
  private Agent agent;
  private Properties testProps;

  @BeforeEach
  public void setUp() throws IOException {
    // 1. Cargar credenciales desde ~/.noema-tests.properties
    Path propsPath = Path.of(System.getProperty("user.home"), ".noema-tests.properties");
    if (!Files.exists(propsPath)) {
      System.out.println(">>> [E2E SKIPPED] No se encontró el archivo de credenciales: " + propsPath);
      Assumptions.assumeTrue(false, "Fichero ~/.noema-tests.properties ausente");
    }

    testProps = new Properties();
    try (Reader r = Files.newBufferedReader(propsPath, StandardCharsets.UTF_8)) {
      testProps.load(r);
    }

    boolean hasCredentials = StringUtils.isNotBlank(testProps.getProperty(REASONING_PROVIDER_URL.replace('/', '.')))
            && StringUtils.isNotBlank(testProps.getProperty(REASONING_PROVIDER_API_KEY.replace('/', '.')))
            && StringUtils.isNotBlank(testProps.getProperty(REASONING_MODEL_ID.replace('/', '.')))
            && StringUtils.isNotBlank(testProps.getProperty(MEMORY_PROVIDER_URL.replace('/', '.')))
            && StringUtils.isNotBlank(testProps.getProperty(MEMORY_PROVIDER_API_KEY.replace('/', '.')))
            && StringUtils.isNotBlank(testProps.getProperty(MEMORY_MODEL_ID.replace('/', '.')));

    if (!hasCredentials) {
      System.out.println(">>> [E2E SKIPPED] Credenciales incompletas en: " + propsPath);
      Assumptions.assumeTrue(false, "Credenciales incompletas en ~/.noema-tests.properties");
    }

    // 2. Determinar workspace: limpieza garantizada de ejecuciones previas
    String dumpPathStr = testProps.getProperty("debug.dump.path");
    if (StringUtils.isNotBlank(dumpPathStr)) {
      workspaceDir = Path.of(dumpPathStr).toAbsolutePath().normalize();
      if (Files.exists(workspaceDir)) {
        FileUtils.deleteDirectory(workspaceDir.toFile());
      }
      Files.createDirectories(workspaceDir);
      System.out.println(">>> [E2E DEBUG] Workspace persistente limpio en: " + workspaceDir);
    } else {
      workspaceDir = tempDir;
    }

    // 3. Copiar la documentación técnica (datos-test.md) a la raíz del workspace
    Path fixturePath = Path.of("src/test/resources/fixtures/datos-test.md").toAbsolutePath().normalize();
    assertTrue(Files.exists(fixturePath), "No se encontró el archivo de fixture en: " + fixturePath);
    Files.copy(fixturePath, workspaceDir.resolve("datos-test.md"), StandardCopyOption.REPLACE_EXISTING);

    // 4. Inicializar configuración de Noema
    AgentManager manager = AgentLocator.getAgentManager();
    AgentPaths paths = manager.createAgentPaths(workspaceDir);
    AgentSettings settings = manager.createSettings(paths);

    settings.setupSettings();
    settings.load();

    settings.setProperty(REASONING_PROVIDER_URL, testProps.getProperty(REASONING_PROVIDER_URL.replace('/', '.')));
    settings.setProperty(REASONING_PROVIDER_API_KEY, testProps.getProperty(REASONING_PROVIDER_API_KEY.replace('/', '.')));
    settings.setProperty(REASONING_MODEL_ID, testProps.getProperty(REASONING_MODEL_ID.replace('/', '.')));

    settings.setProperty(MEMORY_PROVIDER_URL, testProps.getProperty(MEMORY_PROVIDER_URL.replace('/', '.')));
    settings.setProperty(MEMORY_PROVIDER_API_KEY, testProps.getProperty(MEMORY_PROVIDER_API_KEY.replace('/', '.')));
    settings.setProperty(MEMORY_MODEL_ID, testProps.getProperty(MEMORY_MODEL_ID.replace('/', '.')));

    settings.setProperty("access_control/humanConfirmationRequired", "false");
    settings.setProperty("access_control/allow_disk_write", "false");
    settings.setProperty("access_control/allow_shell_execution", "false");
    settings.setProperty("access_control/allow_internet_access", "false");

    settings.save();

    // 5. Registrar UI silenciosa e iniciar el agente
    FakeAgentUIManager.register();
    agent = BootUtils.init(settings);
    agent.start();

    // 6. Configurar lista blanca de herramientas para evaluar el arbitraje
    ReasoningService reasoning = (ReasoningService) agent.getService(ReasoningService.NAME);

    for (AgentTool tool : reasoning.getAvailableTools()) {
      reasoning.setToolActive(tool.getName(), false);
    }

    // Dejamos disponible la lectura paginada a 100 líneas como alternativa lenta
    reasoning.addTool(new FileReadTool(agent) {
      @Override
      protected int getDefaultMaxLines() {
        return 100;
      }
    });

    // Activamos las herramientas del escenario
    reasoning.setToolActive(FileReadTool.TOOL_NAME, true);
    reasoning.setToolActive(ReadPaginatedResourceTool.TOOL_NAME, true);
    reasoning.setToolActive(FileGrepTool.TOOL_NAME, true); // Herramienta diana
    reasoning.setToolActive(AnnotateObservationTool.TOOL_NAME, true);
    reasoning.setToolActive(LookupTurnTool.NAME, true);
    reasoning.setToolActive(SearchFullHistoryTool.NAME, true);
  }

  @AfterEach
  public void tearDown() {
    if (agent != null) {
      agent.stop();
    }
  }

  private String sendUserMessageAndWait(String userPrompt) throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<String> finalResponse = new AtomicReference<>("");

    agent.putUsersMessage(Agent.DEFAULT_SUBCHANNEL, userPrompt, response -> {
      finalResponse.set(response != null ? response : "");
      latch.countDown();
    });

    // En búsqueda dirigida debe responder en segundos; 90s es un tope seguro
    boolean finished = latch.await(90, TimeUnit.SECONDS);
    assertTrue(finished, "Timeout esperando respuesta del agente al prompt: " + userPrompt);

    return finalResponse.get();
  }

  @Test
  @Timeout(value = 90, unit = TimeUnit.SECONDS)
  @DisplayName("E2E Optimización: Debe resolver la consulta técnica mediante file_grep en 1 o 2 turnos")
  public void testLexicalGrepRetrieval() throws Throwable {

    // Prompt que contiene anclas técnicas claras sin ordenar directamente la herramienta
    String prompt = "Consultando el documento técnico 'datos-test.md', ¿qué librería parser de fechas "
            + "utiliza el servicio de alarmas y cuál es el formato exacto que reciben sus identificadores de alarma?";

    long startMs = System.currentTimeMillis();
    String answer = sendUserMessageAndWait(prompt);
    long elapsedSeconds = (System.currentTimeMillis() - startMs) / 1000;

    assertNotNull(answer, "La respuesta del agente no debe ser nula.");
    System.out.println(">>> [RESPUESTA DIRIGIDA] " + answer);

    // 1. ASERCIÓN FACTUAL: los mismos datos que en el pajar masivo
    assertTrue(answer.contains("ALARM-") || answer.contains("ALARM"),
            "Debe identificar que el formato de ID es 'ALARM-' o 'ALARM-<num>'.");
    assertTrue(answer.toLowerCase().contains("natty"),
            "Debe identificar que la librería de parseo es 'Natty'.");

    // 2. ASERCIÓN DE ARBITRAJE: verificar qué herramientas se ejecutaron en la memoria episódica
    List<Turn> turns = agent.getEpisodicMemory().getUnconsolidatedTurns(Agent.DEFAULT_SUBCHANNEL);
    assertNotNull(turns);

    boolean usedGrep = false;
    int paginatedReadCalls = 0;

    for (Turn t : turns) {
      String toolCall = t.getToolCall();
      if (StringUtils.isNotBlank(toolCall)) {
        if (toolCall.contains(FileGrepTool.TOOL_NAME)) {
          usedGrep = true;
        }
        if (toolCall.contains(ReadPaginatedResourceTool.TOOL_NAME)) {
          paginatedReadCalls++;
        }
      }
    }

    assertTrue(usedGrep, "El agente debía haber optado por 'file_grep' ante una consulta técnica puntual.");
    assertFalse(paginatedReadCalls > 3, "El agente NO debía paginar secuencialmente el archivo (llamadas: " + paginatedReadCalls + ").");

    // 3. ASERCIÓN DE ECONOMÍA COGNITIVA: turnos y tiempo de ejecución
    ReasoningService reasoning = (ReasoningService) agent.getService(ReasoningService.NAME);
    int turnCount = reasoning.getTurnsCount(Agent.DEFAULT_SUBCHANNEL);

    // Turnos esperados: 1 llamada a grep (+ opcionalmente 1 lectura acotada) + respuesta final
    assertTrue(turnCount <= 3, "La tarea debía resolverse en máximo 2 o 3 turnos, pero tomó: " + turnCount);
    assertTrue(elapsedSeconds < 45, "La consulta dirigida debía tomar menos de 45 segundos, tardó: " + elapsedSeconds + "s");

    System.out.printf(">>> [OPTIMIZACIÓN GREP OK] Resuelto con éxito en %ds y %d turnos.%n", elapsedSeconds, turnCount);
  }

  @Test
  @Timeout(value = 90, unit = TimeUnit.SECONDS)
  @DisplayName("E2E Optimización Semántica: Debe resolver consultas conceptuales mediante file_fuzzygrep")
  public void testSemanticFuzzyRetrieval() throws Throwable {

    // 1. Aseguramos que file_fuzzygrep está activa junto a grep y read
    ReasoningService reasoning = (ReasoningService) agent.getService(ReasoningService.NAME);
    reasoning.setToolActive(FileFuzzyGrepTool.TOOL_NAME, true);

    // 2. Prompt puramente conceptual: sin palabras clave del código ni nombres de clases
    String prompt = "Consultando el documento técnico 'datos-test.md', ¿qué convención o nomenclatura "
            + "se sigue para identificar los avisos diferidos en el tiempo y qué restricción lingüística "
            + "presenta el componente encargado de interpretar cuándo deben ejecutarse?";

    long startMs = System.currentTimeMillis();
    String answer = sendUserMessageAndWait(prompt);
    long elapsedSeconds = (System.currentTimeMillis() - startMs) / 1000;

    assertNotNull(answer, "La respuesta del agente no debe ser nula.");
    System.out.println(">>> [RESPUESTA FUZZY GREP] " + answer);

    // 3. ASERCIÓN FACTUAL
    // Debe haber extraído el formato ALARM- y la restricción del inglés / Natty
    assertTrue(answer.contains("ALARM-") || answer.contains("ALARM"),
            "Debe identificar la convención de ID 'ALARM-' a partir del concepto de aviso diferido.");
    assertTrue(answer.toLowerCase().contains("inglés") || answer.toLowerCase().contains("ingles") || answer.toLowerCase().contains("natty"),
            "Debe identificar que la restricción es el idioma inglés (librería Natty).");

    // 4. ASERCIÓN DE ARBITRAJE: verificar que usó file_fuzzygrep
    List<Turn> turns = agent.getEpisodicMemory().getUnconsolidatedTurns(Agent.DEFAULT_SUBCHANNEL);
    assertNotNull(turns);

    boolean usedFuzzyGrep = false;
    boolean usedRegularGrep = false;
    int paginatedReadCalls = 0;

    for (Turn t : turns) {
      String toolCall = t.getToolCall();
      if (StringUtils.isNotBlank(toolCall)) {
        if (toolCall.contains(FileFuzzyGrepTool.TOOL_NAME)) {
          usedFuzzyGrep = true;
        }
        if (toolCall.contains(FileGrepTool.TOOL_NAME)) {
          usedRegularGrep = true;
        }
        if (toolCall.contains(ReadPaginatedResourceTool.TOOL_NAME)) {
          paginatedReadCalls++;
        }
      }
    }

    // El modelo debe haber seleccionado fuzzygrep para una consulta conceptual
    assertTrue(usedFuzzyGrep,
            "El agente debía haber optado por 'file_fuzzygrep' ante una consulta conceptual sin palabras clave exactas.");
    assertFalse(paginatedReadCalls > 3,
            "El agente NO debía paginar secuencialmente todo el archivo.");

    // 5. ASERCIÓN DE ECONOMÍA COGNITIVA
    int turnCount = reasoning.getTurnsCount(Agent.DEFAULT_SUBCHANNEL);

    // Esperamos: 1 llamada a fuzzygrep (+ opcionalmente 1 file_read de contexto) + respuesta final
    assertTrue(turnCount <= 4,
            "La tarea debía resolverse en máximo 3 o 4 turnos, pero tomó: " + turnCount);
    assertTrue(elapsedSeconds < 45,
            "La consulta semántica debía tomar menos de 45 segundos, tardó: " + elapsedSeconds + "s");

    System.out.printf(">>> [OPTIMIZACIÓN FUZZY OK] Resuelto en %ds y %d turnos (usó regular grep: %b).%n",
            elapsedSeconds, turnCount, usedRegularGrep);
  }
}
