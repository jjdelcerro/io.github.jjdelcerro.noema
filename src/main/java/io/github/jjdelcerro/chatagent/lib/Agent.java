package io.github.jjdelcerro.chatagent.lib;

import com.google.gson.JsonObject;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.openai.OpenAiChatModel;
import io.github.jjdelcerro.chatagent.lib.persistence.SourceOfTruth;
import java.io.File;
import java.sql.Connection;
import java.util.function.Supplier;

/**
 *
 * @author jjdelcerro
 */
public interface Agent {

  public record ModelParameters(
        String providerUrl, 
        String providerApiKey,
        String modelId,
        double temperature
  ) { }
  
  public File getDataFolder();

  public File getDataFolder(String name);

  public AgentActions getActions();

  public AgentSettings getSettings();

  public AgentConsole getConsole();

  public SourceOfTruth getSourceOfTruth();

  public String processTurn(String input);

  /**
   * Inyecta un estímulo externo asíncrono en el flujo de conciencia del agente,
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
  public void putEvent(String channel, String priority, String eventText);

  public AgentAccessControl getAccessControl();

  public void setConsole(AgentConsole console);
  
  public ConnectionSupplier getServicesDatabase();

  public ConnectionSupplier getMemoryDatabase();

  public AgentService getService(String name);
  
  public void start();
  
  public String getResourceAsString(String resname);
  
  public OpenAiChatModel createChatModel(String name);
  
  public ModelParameters getModelParameters(String name);
  
  public String callChatModel(String docmapper_reasoning_llm, String extractStructureSystemPrompt, String doc_csv);

  public JsonObject callChatModelAsJson(String docmapper_basic_llm, String summaryAndCategorizeSystemPrompt, String contents);

  public void installResource(String resPath);
  
}
