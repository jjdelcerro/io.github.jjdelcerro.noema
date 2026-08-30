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
import io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.file.FileReadTool;
import io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.file.ReadPaginatedResourceTool;
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
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import io.github.jjdelcerro.noema.lib.memory.consolidate.ConsolidateMemory;
import io.github.jjdelcerro.noema.lib.services.memory.MemoryConsolidationService;
import static io.github.jjdelcerro.noema.lib.services.memory.MemoryConsolidationService.MEMORY_MODEL_ID;
import static io.github.jjdelcerro.noema.lib.services.memory.MemoryConsolidationService.MEMORY_PROVIDER_API_KEY;
import static io.github.jjdelcerro.noema.lib.services.memory.MemoryConsolidationService.MEMORY_PROVIDER_URL;
import static io.github.jjdelcerro.noema.lib.services.reasoning.ReasoningService.REASONING_MODEL_ID;
import static io.github.jjdelcerro.noema.lib.services.reasoning.ReasoningService.REASONING_PROVIDER_API_KEY;
import static io.github.jjdelcerro.noema.lib.services.reasoning.ReasoningService.REASONING_PROVIDER_URL;

@Tag("e2e")
public class NeedleInHaystackE2ETest {

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
            System.out.println(">>> [E2E SKIPPED] No se encontro el archivo de credenciales: " + propsPath);
            Assumptions.assumeTrue(false, "Fichero ~/.noema-tests.properties ausente");
        }

        testProps = new Properties();
        try (Reader r = Files.newBufferedReader(propsPath, StandardCharsets.UTF_8)) {
            testProps.load(r);
        }

        // FIXME: usar las constantes de los inerfaces de los servicios.
        boolean hasCredentials = StringUtils.isNotBlank(testProps.getProperty("reasoning.provider.url"))
                && StringUtils.isNotBlank(testProps.getProperty("reasoning.provider.api_key"))
                && StringUtils.isNotBlank(testProps.getProperty("reasoning.provider.model_id"))
                && StringUtils.isNotBlank(testProps.getProperty("memory_consolidation.provider.url"))
                && StringUtils.isNotBlank(testProps.getProperty("memory_consolidation.provider.api_key"))
                && StringUtils.isNotBlank(testProps.getProperty("memory_consolidation.provider.model_id"));

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

        // 3. Copiar el documento pajar (datos-test.md) a la raiz del workspace
        Path fixturePath = Path.of("src/test/resources/fixtures/datos-test.md").toAbsolutePath().normalize();
        assertTrue(Files.exists(fixturePath), "No se encontro el archivo de fixture en: " + fixturePath);
        Files.copy(fixturePath, workspaceDir.resolve("datos-test.md"), StandardCopyOption.REPLACE_EXISTING);

        // 4. Inicializar configuracion de Noema
        AgentManager manager = AgentLocator.getAgentManager();
        AgentPaths paths = manager.createAgentPaths(workspaceDir);
        AgentSettings settings = manager.createSettings(paths);

        settings.setupSettings();
        settings.load();

        // Inyectamos credenciales y modelos reales
        settings.setProperty(REASONING_PROVIDER_URL, testProps.getProperty(REASONING_PROVIDER_URL.replace('/', '.')));
        settings.setProperty(REASONING_PROVIDER_API_KEY, testProps.getProperty(REASONING_PROVIDER_API_KEY.replace('/', '.')));
        settings.setProperty(REASONING_MODEL_ID, testProps.getProperty(REASONING_MODEL_ID.replace('/', '.')));

        settings.setProperty(MEMORY_PROVIDER_URL, testProps.getProperty(MEMORY_PROVIDER_URL.replace('/', '.')));
        settings.setProperty(MEMORY_PROVIDER_API_KEY, testProps.getProperty(MEMORY_PROVIDER_API_KEY.replace('/', '.')));
        settings.setProperty(MEMORY_MODEL_ID, testProps.getProperty(MEMORY_MODEL_ID.replace('/', '.')));

        // Politicas de ejecucion desatendida
        settings.setProperty("access_control/humanConfirmationRequired", "false");
        settings.setProperty("access_control/allow_disk_write", "false");
        settings.setProperty("access_control/allow_shell_execution", "false");
        settings.setProperty("access_control/allow_internet_access", "false");

        settings.save();

        // 5. Registramos el gestor de UI de pruebas y arrancamos el agente
        FakeAgentUIManager.register();
        agent = BootUtils.init(settings);
        agent.start();

        // 6. Lista blanca de herramientas
        ReasoningService reasoning = (ReasoningService) agent.getService(ReasoningService.NAME);

        for (AgentTool tool : reasoning.getAvailableTools()) {
            reasoning.setToolActive(tool.getName(), false);
        }

        // Registramos la herramienta de lectura forzada a bloques de 100 lineas
        reasoning.addTool(new FileReadTool(agent) {
            @Override
            protected int getDefaultMaxLines() {
                return 100;
            }
        });

        // Activamos unicamente las 5 herramientas necesarias para el escenario
        reasoning.setToolActive(FileReadTool.TOOL_NAME, true);
        reasoning.setToolActive(ReadPaginatedResourceTool.TOOL_NAME, true);
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

        // Damos hasta 12 minutos para completar el turno (especialmente para la lectura de los 54 bloques)
        boolean finished = latch.await(12, TimeUnit.MINUTES);
        assertTrue(finished, "Timeout esperando respuesta del agente al prompt: " + userPrompt);

        return finalResponse.get();
    }

    @Test
    @Timeout(value = 15, unit = TimeUnit.MINUTES)
    @DisplayName("E2E: Debe retener las tres agujas cognitivas tras la lectura masiva y compactacion real")
    public void testNeedleInHaystackE2E() throws Throwable {

        // --- ACTO 1: Aguja 1 (Memorizacion explicita / Directiva dura) ---
        String prompt1 = "Hola Noema. Para el proyecto Titan, la clave secreta de despliegue es 'TitanSecret-9942'. "
                + "Guardate este dato porque te lo pedire al final de la sesion.";
        String res1 = sendUserMessageAndWait(prompt1);
        assertNotNull(res1);

        // --- ACTO 2: Aguja 2 (Conversacion casual / Hecho incidental) ---
        String prompt2 = "Ayer estuve revisando con Paco el servidor de staging y acordamos que correria en el puerto 7788.";
        String res2 = sendUserMessageAndWait(prompt2);
        assertNotNull(res2);

        // --- ACTO 3: El Pajar (Lectura masiva de 5.366 lineas en bloques de 100) ---
        // Esto forzara ~54 llamadas a herramientas y disparara la compactacion real de memoria
        String prompt3 = "Por favor, lee el archivo 'datos-test.md' de principio a fin utilizando bloques de 100 lineas "
                + "para no saturar tu memoria de trabajo, y hazme un analisis general de los servicios que describe.";
        String res3 = sendUserMessageAndWait(prompt3);
        assertNotNull(res3);

        // Verificamos que se ha generado al menos un ConsolidateMemory en la base de datos
        ConsolidateMemory consolidateMemory = agent.getEpisodicMemory().getLatestConsolidateMemory(Agent.DEFAULT_SUBCHANNEL);
        assertNotNull(consolidateMemory, "La memoria deberia haberse compactado generando al menos un ConsolidateMemory.");
        System.out.println(">>> [E2E INFO] ConsolidateMemory generado: " + consolidateMemory.getCode());

        // --- ACTO 4: Interrogatorio final sobre las tres agujas ---

        // 1. Verificacion de Aguja 1 (Clave secreta explicita)
        String query1 = "Cual es la clave secreta de despliegue del proyecto Titan que te di al principio?";
        String answer1 = sendUserMessageAndWait(query1);
        System.out.println(">>> [RESPUESTA AGUJA 1] " + answer1);
        assertTrue(answer1.contains("TitanSecret-9942"),
                "El agente deberia recordar la clave secreta explicita 'TitanSecret-9942'.");

        // 2. Verificacion de Aguja 2 (Conversacion casual con Paco)
        String query2 = "En que puerto acordamos con Paco que correria el servidor de staging?";
        String answer2 = sendUserMessageAndWait(query2);
        System.out.println(">>> [RESPUESTA AGUJA 2] " + answer2);
        assertTrue(answer2.contains("7788"),
                "El agente deberia recordar el puerto '7788' acordado con Paco.");

        // 3. Verificacion de Aguja 3 (Detalle tecnico organico dentro del documento)
        String query3 = "Segun la seccion del SchedulerService que leiste en datos-test.md, "
                + "cual es el formato del ID que se asigna a cada alarma y que libreria parser de fechas utiliza?";
        String answer3 = sendUserMessageAndWait(query3);
        System.out.println(">>> [RESPUESTA AGUJA 3] " + answer3);
        assertTrue(answer3.contains("ALARM") || answer3.contains("ALARM-"),
                "El agente deberia recordar que el formato de alarma es 'ALARM-<num>'.");
        assertTrue(answer3.toLowerCase().contains("natty"),
                "El agente deberia recordar que el parser utilizado es 'Natty'.");
    }
}
