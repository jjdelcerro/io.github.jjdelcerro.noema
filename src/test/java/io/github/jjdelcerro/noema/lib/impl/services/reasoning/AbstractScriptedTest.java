package io.github.jjdelcerro.noema.lib.impl.services.reasoning;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.impl.AgentImpl;
import io.github.jjdelcerro.noema.lib.impl.ModelParametersImpl;
import io.github.jjdelcerro.noema.lib.impl.services.sensors.SensorInformationImpl;
import io.github.jjdelcerro.noema.lib.impl.services.sensors.SensorsServiceImpl;
import io.github.jjdelcerro.noema.lib.impl.services.sensors.nature.user.SensorEventUserImpl;
import io.github.jjdelcerro.noema.lib.services.sensors.ConsumableSensorEvent;
import io.github.jjdelcerro.noema.lib.services.sensors.SensorInformation;
import io.github.jjdelcerro.noema.lib.services.sensors.SensorNature;
import io.github.jjdelcerro.noema.lib.services.sensors.SensorsService;
import org.apache.commons.lang3.mutable.MutableBoolean;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertTrue;

public abstract class AbstractScriptedTest {

  // =========================================================================
  // GETTERS ABSTRACTOS (Template Method Pattern)
  // =========================================================================
  protected abstract Agent getAgent();

  protected abstract ScriptedChatModel getScriptedModel();

  protected abstract RecentMemory getRecentMemory();

  protected abstract ReasoningServiceImpl getReasoningService();

  // =========================================================================
  // MODELO FAKE EXTENSIBLE
  // =========================================================================
  public static class ScriptedChatModel implements Agent.ChatModel {

    protected final Queue<Response<AiMessage>> programmedResponses = new ArrayDeque<>();
    protected final List<List<ChatMessage>> capturedContexts = new ArrayList<>();

    public void prepareTurn(List<AiMessage> responses) {
      this.programmedResponses.clear();
      this.capturedContexts.clear();
      if (responses != null) {
        for (AiMessage msg : responses) {
          this.programmedResponses.add(Response.from(msg, null, FinishReason.STOP));
        }
      }
    }

    public void prepareTurn(AiMessage... responses) {
      this.prepareTurn(Arrays.asList(responses));
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications, MutableBoolean abort) {
      this.capturedContexts.add(new ArrayList<>(messages));

      if (this.programmedResponses.isEmpty()) {
        throw new IllegalStateException("Llamada imprevista al modelo: no hay mas respuestas programadas en la cola.");
      }
      return this.programmedResponses.poll();
    }

    public List<List<ChatMessage>> getCapturedContexts() {
      return capturedContexts;
    }

    public List<ChatMessage> getLastContext() {
      if (capturedContexts.isEmpty()) {
        return List.of();
      }
      return capturedContexts.get(capturedContexts.size() - 1);
    }

    @Override
    public int getContextSize() {
      return 128000;
    }

    @Override
    public Response<AiMessage> generate(ChatMessage systemPrompt, ChatMessage message) {
      return null;
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {
      return null;
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
      return null;
    }

    @Override
    public Agent.ModelParameters getParameters() {
      return new ModelParametersImpl("http://fake", "key", "fake-model", 0.5);
    }

    @Override
    public Agent.ModelType getModelType() {
      return Agent.ModelType.OPENAI;
    }
  }

  // =========================================================================
  // ESTRUCTURA DE TURNO (SimTurn)
  // =========================================================================
  public static record SimTurn(
          ChatMessage userMessage,
          List<AiMessage> modelResponses,
          Predicate<SimTurn> check
          ) {

  }

  // =========================================================================
  // HELPERS DEL DSL
  // =========================================================================
  public static ChatMessage user(String text) {
    return UserMessage.from(text);
  }

  public static AiMessage ai(String text) {
    return AiMessage.from(text);
  }

  public static AiMessage aiTool(String toolName, String jsonArgs) {
    String callId = "call_" + UUID.randomUUID().toString().substring(0, 8);
    ToolExecutionRequest request = ToolExecutionRequest.builder()
            .id(callId)
            .name(toolName)
            .arguments(jsonArgs)
            .build();
    return AiMessage.from(request);
  }

  public static SimTurn turn(String userText, String aiText) {
    return new SimTurn(user(userText), List.of(ai(aiText)), null);
  }

  public static SimTurn turn(ChatMessage userMsg, AiMessage... aiResponses) {
    return new SimTurn(userMsg, Arrays.asList(aiResponses), null);
  }

  public static SimTurn turn(ChatMessage userMsg, Predicate<SimTurn> check, AiMessage... aiResponses) {
    return new SimTurn(userMsg, Arrays.asList(aiResponses), check);
  }

  // =========================================================================
  // MOTOR DE EJECUCION
  // =========================================================================
  public void execute(List<SimTurn> script) throws Throwable {
    Agent theAgent = getAgent();
    ReasoningServiceImpl reasoning = getReasoningService();
    ScriptedChatModel model = getScriptedModel();

    for (int i = 0; i < script.size(); i++) {
      SimTurn simTurn = script.get(i);

      model.prepareTurn(simTurn.modelResponses());

      ConsumableSensorEvent event = createSensorUserEvent(theAgent, simTurn.userMessage());
      reasoning.processSingleEvent(event);

      if (simTurn.check() != null) {
        boolean result = simTurn.check().test(simTurn);
        assertTrue(result, "Fallo la validacion de estado en el turno indice " + i);
      }
    }
  }

  protected ConsumableSensorEvent createSensorUserEvent(Agent theAgent, ChatMessage userMessage) {
    String text = extractText(userMessage);
    SensorsService sensorsService = (SensorsService) theAgent.getService(SensorsService.NAME);

    if (sensorsService instanceof SensorsServiceImpl sensorsImpl) {
      return sensorsImpl.createSensorEvent(
              AgentImpl.USER_SENSOR_NAME,
              Agent.DEFAULT_SUBCHANNEL,
              text,
              SensorsService.PRIORITY_NORMAL,
              "ok",
              LocalDateTime.now(),
              null
      );
    }

    SensorInformation userInfo = new SensorInformationImpl(
            AgentImpl.USER_SENSOR_NAME,
            "User",
            SensorNature.USER,
            "User input",
            false
    );
    return new SensorEventUserImpl(
            userInfo,
            Agent.DEFAULT_SUBCHANNEL,
            text,
            SensorsService.PRIORITY_NORMAL,
            "ok",
            LocalDateTime.now(),
            null
    );
  }

  // =========================================================================
  // UTILIDADES DE INSPECCION DE CONTEXTO Y SESION
  // =========================================================================
  public static String extractText(ChatMessage message) {
    if (message == null) {
      return "";
    }
    if (message instanceof UserMessage userMessage) {
      return userMessage.singleText() != null ? userMessage.singleText() : "";
    }
    if (message instanceof AiMessage aiMessage) {
      StringBuilder sb = new StringBuilder();
      if (aiMessage.text() != null && !aiMessage.text().isBlank()) {
        sb.append(aiMessage.text());
      }
      if (aiMessage.hasToolExecutionRequests()) {
        for (ToolExecutionRequest req : aiMessage.toolExecutionRequests()) {
          if (req.arguments() != null && !req.arguments().isBlank()) {
            if (sb.length() > 0) {
              sb.append(" ");
            }
            sb.append(req.arguments());
          }
        }
      }
      return sb.toString();
    }
    if (message instanceof ToolExecutionResultMessage toolMessage) {
      return toolMessage.text() != null ? toolMessage.text() : "";
    }
    if (message instanceof SystemMessage systemMessage) {
      return systemMessage.text() != null ? systemMessage.text() : "";
    }
    return "";
  }

  protected boolean hasTrimmedResource(String resourceFragment) {
    return hasTrimmedResource(getScriptedModel().getLastContext(), resourceFragment);
  }

  protected boolean hasTrimmedResource(List<ChatMessage> context, String resourceFragment) {
    for (ChatMessage msg : context) {
      if (msg instanceof ToolExecutionResultMessage toolMsg) {
        String text = toolMsg.text();
        if (text != null && text.contains(resourceFragment) && text.contains("CONTENT_TRIMMED: true")) {
          return true;
        }
      }
    }
    return false;
  }

  protected boolean hasFullResource(String resourceFragment) {
    return hasFullResource(getScriptedModel().getLastContext(), resourceFragment);
  }

  protected boolean hasFullResource(List<ChatMessage> context, String resourceFragment) {
    for (ChatMessage msg : context) {
      if (msg instanceof ToolExecutionResultMessage toolMsg) {
        String text = toolMsg.text();
        if (text != null && text.contains(resourceFragment) && !text.contains("CONTENT_TRIMMED: true")) {
          return true;
        }
      }
    }
    return false;
  }

  protected boolean projectedContextContainsText(String textFragment) {
    return contextContainsText(getScriptedModel().getLastContext(), textFragment);
  }

  protected boolean contextContainsText(List<ChatMessage> context, String textFragment) {
    for (ChatMessage msg : context) {
      String text = extractText(msg);
      if (text.contains(textFragment)) {
        return true;
      }
    }
    return false;
  }

  protected boolean recentMemoryContainsText(String textFragment) {
    RecentMemory currentRecentMemory = getRecentMemory();
    if (currentRecentMemory == null) {
      return false;
    }
    return contextContainsText(currentRecentMemory.getMessages(), textFragment);
  }

  protected boolean hasEphemeralNotification() {
    return hasEphemeralNotification(getScriptedModel().getLastContext(), null);
  }

  protected boolean hasEphemeralNotification(String expectedContentFragment) {
    return hasEphemeralNotification(getScriptedModel().getLastContext(), expectedContentFragment);
  }

  protected boolean hasEphemeralNotification(List<ChatMessage> context, String expectedContentFragment) {
    if (context == null || context.isEmpty()) {
      return false;
    }
    for (ChatMessage msg : context) {
      if (msg instanceof ToolExecutionResultMessage toolMsg) {
        String text = toolMsg.text();
        if (text != null && text.contains("SYSTEMNOTIFICATION")) {
          if (expectedContentFragment == null || text.contains(expectedContentFragment)) {
            return true;
          }
        }
      }
    }
    return false;
  }
}
