package io.github.jjdelcerro.chatagent.lib.impl.services.memory;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import io.github.jjdelcerro.chatagent.lib.Agent;
import io.github.jjdelcerro.chatagent.lib.Agent.ModelParameters;
import static io.github.jjdelcerro.chatagent.lib.AgentActions.CHANGE_MEMORY_MODEL;
import static io.github.jjdelcerro.chatagent.lib.AgentActions.CHANGE_MEMORY_PROVIDER;
import io.github.jjdelcerro.chatagent.lib.persistence.CheckPoint;
import io.github.jjdelcerro.chatagent.lib.persistence.SourceOfTruth;
import io.github.jjdelcerro.chatagent.lib.persistence.Turn;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import io.github.jjdelcerro.chatagent.lib.AgentConsole;
import io.github.jjdelcerro.chatagent.lib.AgentService;
import io.github.jjdelcerro.chatagent.lib.AgentServiceFactory;
import io.github.jjdelcerro.chatagent.lib.AgentSettings;
import io.github.jjdelcerro.chatagent.lib.AgentTool;
import io.github.jjdelcerro.chatagent.lib.impl.services.memory.tools.LookupTurnTool;
import io.github.jjdelcerro.chatagent.lib.impl.services.memory.tools.SearchFullHistoryTool;
import java.util.Arrays;

/**
 * Componente cognitivo encargado de la consolidación de la memoria. Ejecuta el
 * "Protocolo de Generación de Puntos de Guardado" utilizando un LLM.
 */
public class MemoryService implements AgentService {
  public static final  String NAME = "Memory";

  public static final String MEMORY_PROVIDER_URL = "MEMORY_PROVIDER_URL";
  public static final String MEMORY_PROVIDER_API_KEY = "MEMORY_PROVIDER_API_KEY";
  public static final String MEMORY_MODEL_ID = "MEMORY_MODEL_ID";
  
  private final Agent agent;
  private final SourceOfTruth sourceOfTruth;
  private AgentConsole console;
  private ChatLanguageModel model;
  private String systemPrompt;
  private boolean running;
  private final AgentServiceFactory factory;

  public MemoryService(AgentServiceFactory factory, Agent agent) {
    this.factory = factory;
    this.agent = agent;
    this.sourceOfTruth = agent.getSourceOfTruth();
    this.console = agent.getConsole();
  }

  @Override
  public AgentServiceFactory getFactory() {
    return factory;
  }

  @Override
  public void start() {
    this.agent.getActions().addAction(
            CHANGE_MEMORY_PROVIDER,
            (AgentSettings settings) -> {
              model = agent.createChatModel("MEMORY");
              return true;
            }
    );
    this.agent.getActions().addAction(
            CHANGE_MEMORY_MODEL,
            (AgentSettings settings) -> {
              model = agent.createChatModel("MEMORY");
              return true;
            }
    );
    this.model = this.agent.createChatModel("MEMORY");
    loadSystemPrompt();
    this.running = true;
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
    this.console.printSystemLog("Iniciando compactación de " + newTurns.size() + " turnos...");
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

  @Override
  public ModelParameters getModelParameters(String name) {
    AgentSettings settings = this.agent.getSettings();
    switch (name) {
      case "MEMORY":
        return new ModelParameters(
                settings.getProperty(MEMORY_PROVIDER_URL),
                settings.getProperty(MEMORY_PROVIDER_API_KEY),
                settings.getProperty(MEMORY_MODEL_ID),
                0.7
        );
    }
    return null;
  }

  @Override
  public boolean canStart() {
    return this.factory.canStart(agent.getSettings());
  }

  @Override
  public List<AgentTool> getTools() {
    AgentTool[] tools = new AgentTool[]{
      new LookupTurnTool(this.agent),
      new SearchFullHistoryTool(this.agent)
    };
    return Arrays.asList(tools);
  }

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public boolean isRunning() {
    return this.running;
  }

}
