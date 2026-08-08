package io.github.jjdelcerro.noema.lib.impl.services.reasoning;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.AgentPaths;
import io.github.jjdelcerro.noema.lib.FakeChatModel;
import io.github.jjdelcerro.noema.lib.FakeConsole;
import io.github.jjdelcerro.noema.lib.impl.AgentImpl;
import io.github.jjdelcerro.noema.lib.impl.AgentPathsImpl;
import io.github.jjdelcerro.noema.lib.impl.persistence.FakeSession;
import io.github.jjdelcerro.noema.lib.impl.persistence.FakeSourceOfTruth;

import io.github.jjdelcerro.noema.lib.impl.services.sensors.SensorInformationImpl;
import io.github.jjdelcerro.noema.lib.impl.services.sensors.nature.user.SensorEventUserImpl;
import io.github.jjdelcerro.noema.lib.impl.settings.AgentSettingsImpl;
import io.github.jjdelcerro.noema.lib.persistence.SourceOfTruth;
import io.github.jjdelcerro.noema.lib.services.sensors.ConsumableSensorEvent;
import io.github.jjdelcerro.noema.lib.services.sensors.SensorInformation;
import io.github.jjdelcerro.noema.lib.services.sensors.SensorNature;
import io.github.jjdelcerro.noema.lib.services.sensors.SensorsService.SensorEventCallback;
import io.github.jjdelcerro.noema.lib.settings.AgentSettings;

import org.apache.commons.lang3.mutable.MutableBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;


public class ReasoningServiceTest {

    @Test
    public void testMensajeHolaDevuelveRespuestaYEjecutaCallback(@TempDir Path tempDir) throws Throwable {
        // 1. Entorno de archivos efímero gestionado por JUnit
        AgentPaths paths = new AgentPathsImpl(tempDir);
        paths.setupHierarchy();
        AgentSettings settings = new AgentSettingsImpl(paths);

        // 2. Fakes con interceptor para verificar qué emite la consola
        List<String> modelResponses = new ArrayList<>();
        FakeConsole console = new FakeConsole() {
            @Override
            public void printModelResponse(String message) {
                modelResponses.add(message);
                super.printModelResponse(message);
            }
        };

        SourceOfTruth sot = new FakeSourceOfTruth();

        // 3. Simulamos la respuesta del LLM
        String respuestaEsperada = "¡Hola! ¿En qué puedo ayudarte?";
        Agent.ChatModel testModel = new FakeChatModel() {
            @Override
            public Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> tools, MutableBoolean abort) {
                return Response.from(AiMessage.from(respuestaEsperada), null, FinishReason.STOP);
            }
        };

        // 4. Instanciamos el Agente de prueba
        Agent testAgent = new AgentImpl(null, null, settings, console, sot, null);

        // 5. Instanciamos el servicio sobreescribiendo Session y ChatModel
        ReasoningServiceFactory factory = new ReasoningServiceFactory();
        ReasoningServiceImpl reasoningService = new ReasoningServiceImpl(factory, testAgent) {
            @Override
            public Session createSession(String subchannel) {
                return new FakeSession(subchannel) {
                    @Override
                    public boolean needCompaction() {
                        return false;
                    }
                };
            }
            
            @Override
            public Agent.ChatModel getModel() {
                return testModel;
            }
            
            @Override
            public boolean isRunning() {
                return true; 
            }      

            @Override
            protected String getBaseSystemPrompt() {
                String prompt = "Eres un agente personal";
                this.lastestSystemPrompt = prompt;
                return prompt;
            }
            
        };

        // 6. Preparamos el evento "Hola" con un callback para verificar que la UI recibiría el fin del turno
        AtomicReference<String> callbackResponse = new AtomicReference<>();
        SensorEventCallback callback = new SensorEventCallback() {
            @Override
            public void onComplete(String response) {
                callbackResponse.set(response);
            }
        };

        SensorInformation userInfo = new SensorInformationImpl("USER", "User", SensorNature.USER, "User input", false);
        ConsumableSensorEvent eventHola = new SensorEventUserImpl(
                userInfo,
                Agent.DEFAULT_SUBCHANNEL,
                "Hola",
                "normal",
                "ok",
                LocalDateTime.now(),
                callback
        );

        // 7. Ejecutamos la lógica de procesado de un único evento de forma síncrona
        reasoningService.processSingleEvent(eventHola);

        // 8. ASERCIONES
        // a) La consola debe haber recibido la respuesta formateada del modelo
        assertEquals(1, modelResponses.size(), "Debería haber exactamente una respuesta del modelo");
        assertEquals(respuestaEsperada, modelResponses.get(0), "El texto en consola no coincide con la respuesta del LLM");

        // b) El callback onComplete() debe haberse ejecutado (esto es lo que apaga el temporizador en la GUI)
        assertNotNull(callbackResponse.get(), "El callback onComplete no fue invocado al terminar el turno");
        assertEquals(respuestaEsperada, callbackResponse.get(), "El texto del callback no coincide con la respuesta del LLM");
    }
}
