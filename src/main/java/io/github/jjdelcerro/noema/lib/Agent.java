package io.github.jjdelcerro.noema.lib;

import com.google.gson.JsonObject;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.output.Response;
import io.github.jjdelcerro.noema.lib.persistence.SourceOfTruth;
import io.github.jjdelcerro.noema.lib.services.sensors.SensorInformation;
import io.github.jjdelcerro.noema.lib.services.sensors.SensorNature;
import io.github.jjdelcerro.noema.lib.services.sensors.SensorsService.SensorEventCallback;
import io.github.jjdelcerro.noema.lib.settings.AgentSettings;
import java.util.List;
import org.apache.commons.lang3.mutable.MutableBoolean;

/**
 *
 * @author jjdelcerro
 */
public interface Agent {

  public interface ModelParameters {
    public String providerUrl();
    public String providerApiKey();
    public String modelId();
    public double temperature();
    public int contextSize();
    public void setContextSize(int contextSize);
  }
  
  public interface ChatModel {
    public int getContextSize();
    public Response<AiMessage> generate(ChatMessage systemPrompt, ChatMessage message);
    public Response<AiMessage> generate(List<ChatMessage> messages);
    public Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications);
    public Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications, MutableBoolean abort) throws Throwable;
    public int estimateTokenCount(String text);
    public int estimateTokenCount(List<ChatMessage> messages);
    public Agent.ModelParameters getParameters();
  }

  public AgentPaths getPaths();

  public AgentActions getActions();

  public AgentSettings getSettings();

  public AgentConsole getConsole();

  public SourceOfTruth getSourceOfTruth();

  /**
   * Inyecta un evento externo asíncrono en el flujo de pensamiento del agente,
   * permitiendo comportamientos proactivos y reactivos ante eventos del mundo
   * real (Sensores).
   * <p>
   * <b>Arquitectura Interna:</b>
   * Este método implementa un patrón de <i>Inversión de Control Simulada</i>.
   * Dado que los LLMs operan de forma pasiva (Request-Response) y no soportan
   * interrupciones nativas, este método:
   * <ol>
   * <li>Encola el evento en un buffer concurrente ({@code pendingEvents}).</li>
   * <li>Si el agente está ocioso, dispara inmediatamente el bucle de
   * razonamiento.</li>
   * <li><b>Simulación de Herramienta:</b> Envuelve el contenido del evento
   * dentro de un {@link ToolExecutionResultMessage} asociado a una herramienta
   * ficticia llamada {@code "pool_event"}.</li>
   * </ol>
   * <p>
   * Esto "engaña" al modelo haciéndole creer que él mismo solicitó consultar
   * estos eventos, manteniendo así la coherencia estricta del historial de chat
   * (<i>User -> AI -> Tool -> Result</i>) y evitando alucinaciones o errores de
   * protocolo al inyectar texto arbitrario.
   * <p>
   * <b>Concurrencia:</b>
   * Este método es {@code synchronized} y Thread-Safe. Puede ser llamado desde
   * hilos externos (ej: Listeners de Telegram o Email). Si el agente está
   * ocupado procesando un turno ({@code isBusy}), el evento quedará en cola y
   * será procesado automáticamente al finalizar el turno actual.
   *
   * @param channel El origen del evento (ej: "Telegram", "Email", "System").
   * Útil para que el LLM sepa contextualizar la fuente.
   * @param status
   * @param priority Nivel de urgencia del evento (ej: "normal", "high").
   * Actualmente informativo.
   * @param eventText El contenido del evento.
   * <ul>
   * <li>Para canales de texto corto (Telegram): Se suele inyectar el mensaje
   * completo.</li>
   * <li>Para canales de contenido denso (Email): Se recomienda inyectar solo
   * una notificación o resumen (ej: metadatos y UID) para no saturar la ventana
   * de contexto, forzando al agente a usar una herramienta de lectura si desea
   * más detalles.</li>
   * </ul>
   * @see #processPendingEvents()
   */
  public void putEvent(String channel, String status, String priority, String eventText);
  
  public void putUsersMessage(String text, SensorEventCallback callback);
  
  public SensorInformation registerSensor(String channel, String label, SensorNature nature, String description);
  
  public AgentAccessControl getAccessControl();

  public void setConsole(AgentConsole console);

  public ConnectionSupplier getServicesDatabase();

  public ConnectionSupplier getMemoryDatabase();

  public AgentService getService(String name);

  public void start();
  
  public void stop();

  public String getResourceAsString(String resname);

  public ChatModel createChatModel(String name);

  public ModelParameters getModelParameters(String name);

  public String callChatModel(String docmapper_reasoning_llm, String extractStructureSystemPrompt, String doc_csv);

  public JsonObject callChatModelAsJson(String docmapper_basic_llm, String summaryAndCategorizeSystemPrompt, String contents);

  public void installResource(String resPath);

  public void showSession();
  
  public int getConversationContextSize();
}
