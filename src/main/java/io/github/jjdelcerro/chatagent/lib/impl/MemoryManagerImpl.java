package io.github.jjdelcerro.chatagent.lib.impl;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import io.github.jjdelcerro.chatagent.lib.persistence.CheckPoint;
import io.github.jjdelcerro.chatagent.lib.persistence.SourceOfTruth;
import io.github.jjdelcerro.chatagent.lib.persistence.Turn;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import io.github.jjdelcerro.chatagent.lib.AgentConsole;
import io.github.jjdelcerro.chatagent.lib.AgentSettings;

/**
 * Componente cognitivo encargado de la consolidación de la memoria. Ejecuta el
 * "Protocolo de Generación de Puntos de Guardado" utilizando un LLM.
 */
public class MemoryManagerImpl {

  private final AgentImpl agent;
  private final SourceOfTruth sourceOfTruth;
  private AgentConsole console;
  private ChatLanguageModel model;
  private String systemPrompt;

  /**
   * Constructor.
   *
   * @param agent
   */
  public MemoryManagerImpl(AgentImpl agent) {
    this.agent = agent;
    this.sourceOfTruth = agent.getSourceOfTruth();
    this.console = agent.getConsole();
    this.createChatLanguageModel(this.agent.getSettings());
    loadSystemPrompt();
  }

  public final boolean createChatLanguageModel(AgentSettings settings) {
    this.model = this.agent.createChatModel("MEMORY", 0.0);
    return true;
  }

  private void loadSystemPrompt() {
    this.systemPrompt = agent.getResourceAsString("prompts/prompt-compact-memorymanager.md");
    if (this.systemPrompt.isEmpty()) {
      throw new RuntimeException("No se pudo cargar el prompt del MemoryManager");
    }
  }

  /**
   * Ejecuta el proceso de compactación.
   *
   * @param previous El CheckPoint anterior (puede ser null si es la primera
   * vez).
   * @param newTurns La lista de turnos recientes a consolidar.
   * @return Un nuevo CheckPoint TRANSITORIO (ID -1) con el texto generado.
   */
  public CheckPoint compact(CheckPoint previous, List<Turn> newTurns) {
    if (newTurns == null || newTurns.isEmpty()) {
      throw new IllegalArgumentException("No hay turnos para compactar.");
    }

    // 1. Preparar el Prompt del Usuario (Input Data)
    String userPrompt = buildUserPrompt(previous, newTurns);

    // 2. Invocar al LLM
    this.console.println("[MemoryManager] Iniciando compactación de " + newTurns.size() + " turnos...");
    AiMessage response = model.generate(
            SystemMessage.from(this.systemPrompt),
            UserMessage.from(userPrompt)
    ).content();

    String generatedText = response.text();

    // 3. Calcular metadatos del nuevo rango
    int firstId = newTurns.getFirst().getId();
    int lastId = newTurns.getLast().getId();

    // Si venimos de un CP anterior, el rango empieza donde empezaba aquel (historia acumulada)
    // Opcional: Si quieres que el CP represente TODA la historia, firstId debería ser el del CP previo.
    // Si quieres que represente el bloque consolidado, se mantiene el actual.
    // Según tu arquitectura de "El Viaje" acumulativo, el 'first' debería ser el inicio de los tiempos.
    if (previous != null) {
      firstId = previous.getTurnFirst();
    }

    // 4. Crear CheckPoint Transitorio
    return this.sourceOfTruth.createCheckPoint(firstId, lastId, Timestamp.from(Instant.now()), generatedText);
  }

  private String buildUserPrompt(CheckPoint previous, List<Turn> newTurns) {
    StringBuilder sb = new StringBuilder();

    // --- CONTEXTO PREVIO (Si existe) ---
    if (previous != null) {
      sb.append("MODO DE OPERACIÓN: 2 (Actualización)\n\n");
      sb.append("=== DOCUMENTO DE PUNTO DE GUARDADO ANTERIOR ===\n");
      sb.append(previous.getText()); // Carga el texto desde disco/cache
      sb.append("\n===============================================\n\n");
    } else {
      sb.append("MODO DE OPERACIÓN: 1 (Creación Inicial)\n\n");
    }

    // --- NUEVA CONVERSACIÓN (CSV) ---
    sb.append("=== NUEVA CONVERSACIÓN A CONSOLIDAR (CSV) ===\n");
    // Cabecera compatible con Turn.toCSVLine()
    sb.append("code,timestamp,contenttype,text_user,text_model_thinking,text_model,tool_call,tool_result\n");

    for (Turn turn : newTurns) {
      sb.append(turn.toCSVLine()).append("\n");
    }
    sb.append("=============================================\n");

    sb.append("Siguiendo el protocolo de  Generación de Puntos de Guardado procede a generar uno con la informacion del punto de guardado y los datos CSV que te acabo de suministrar.");

    return sb.toString();
  }

  public void setConsole(AgentConsole console) {
    this.console = console;
  }
}
