
import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.AgentPaths;
import io.github.jjdelcerro.noema.lib.FakeConsole;
import io.github.jjdelcerro.noema.lib.impl.AgentImpl;
import io.github.jjdelcerro.noema.lib.impl.AgentPathsImpl;
import io.github.jjdelcerro.noema.lib.impl.persistence.FakeEpisodicMemory;
import io.github.jjdelcerro.noema.lib.impl.services.sensors.SensorsServiceFactory;
import io.github.jjdelcerro.noema.lib.impl.services.sensors.SensorsServiceImpl;
import io.github.jjdelcerro.noema.lib.impl.settings.AgentSettingsImpl;
import io.github.jjdelcerro.noema.lib.services.sensors.ConsumableSensorEvent;
import io.github.jjdelcerro.noema.lib.services.sensors.SensorInformation;
import io.github.jjdelcerro.noema.lib.services.sensors.SensorNature;
import io.github.jjdelcerro.noema.lib.services.sensors.SensorsService;
import io.github.jjdelcerro.noema.lib.settings.AgentSettings;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class SensorsServiceTest {

    private Agent createAgent(Path tempDir) {
        AgentPaths paths = new AgentPathsImpl(tempDir);
        paths.setupHierarchy();
        AgentSettings settings = new AgentSettingsImpl(paths);
        Agent testAgent = new AgentImpl(null, null, settings, new FakeConsole(), new FakeEpisodicMemory(), null);
        return testAgent;
    }
    
    @Test
    public void testSensorsServiceProductorConsumidor(@TempDir Path tempDir) throws Exception {

        Agent agent = this.createAgent(tempDir);
        
        // 1. Instanciamos solo el SensorsServiceImpl
        SensorsServiceFactory factory = new SensorsServiceFactory();
        SensorsServiceImpl sensors = new SensorsServiceImpl(factory, agent);

        // 2. Registramos el sensor USER y arrancamos el servicio
        SensorInformation userInfo = sensors.createSensorInformation(
                AgentImpl.USER_SENSOR_NAME,
                "User",
                SensorNature.USER,
                "User input",
                false
        );
        sensors.registerSensor(userInfo);
        sensors.start(); // Pone running = true

        AtomicReference<ConsumableSensorEvent> receivedEvent = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        // 3. Hilo Consumidor (simula al eventDispatcher llamando a getEvent)
        Thread consumerThread = new Thread(() -> {
            ConsumableSensorEvent event = sensors.getEvent();
            receivedEvent.set(event);
            latch.countDown();
        });
        consumerThread.start();

        // Pequeña pausa para asegurar que el consumidor ya está bloqueado en wait()
        Thread.sleep(50);

        // 4. Hilo Productor (simula a la UI llamando a putEvent)
        sensors.putEvent(
                AgentImpl.USER_SENSOR_NAME,
                Agent.DEFAULT_SUBCHANNEL,
                "Hola",
                SensorsService.PRIORITY_NORMAL,
                "ok",
                LocalDateTime.now()
        );

        // 5. Verificamos que el consumidor despierta en menos de 1 segundo
        boolean entregado = latch.await(1, TimeUnit.SECONDS);

        sensors.stop();

        // 6. Aserciones
        assertTrue(entregado, "ERROR: getEvent() se quedó colgado en wait(). El evento no fue entregado.");
        assertNotNull(receivedEvent.get());
        assertEquals("Hola", receivedEvent.get().getContents());
    }
}
