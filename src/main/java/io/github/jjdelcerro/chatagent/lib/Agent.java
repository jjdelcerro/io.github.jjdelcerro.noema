package io.github.jjdelcerro.chatagent.lib;

import io.github.jjdelcerro.chatagent.lib.persistence.SourceOfTruth;
import java.io.File;
import java.nio.file.Path;

/**
 *
 * @author jjdelcerro
 */
public interface Agent {

  public File getDataFolder();

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

  public PathAccessControl getPathAccessControl();
}
