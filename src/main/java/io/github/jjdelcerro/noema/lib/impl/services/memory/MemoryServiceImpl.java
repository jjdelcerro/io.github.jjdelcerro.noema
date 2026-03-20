package io.github.jjdelcerro.noema.lib.impl.services.memory;

import io.github.jjdelcerro.noema.lib.services.memory.MemoryService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import io.github.jjdelcerro.noema.lib.AbstractAgentAction;
import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.Agent.ModelParameters;
import static io.github.jjdelcerro.noema.lib.AgentActions.CHANGE_MEMORY_MODEL;
import static io.github.jjdelcerro.noema.lib.AgentActions.CHANGE_MEMORY_PROVIDER;
import io.github.jjdelcerro.noema.lib.persistence.CheckPoint;
import io.github.jjdelcerro.noema.lib.persistence.SourceOfTruth;
import io.github.jjdelcerro.noema.lib.persistence.Turn;

import java.util.List;
import io.github.jjdelcerro.noema.lib.AgentConsole;
import io.github.jjdelcerro.noema.lib.AgentServiceFactory;
import io.github.jjdelcerro.noema.lib.settings.AgentSettings;
import io.github.jjdelcerro.noema.lib.AgentTool;
import io.github.jjdelcerro.noema.lib.impl.ModelParametersImpl;
import io.github.jjdelcerro.noema.lib.impl.services.memory.tools.LookupTurnTool;
import io.github.jjdelcerro.noema.lib.impl.services.memory.tools.SearchFullHistoryTool;
import java.time.LocalDateTime;
import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Componente cognitivo encargado de la consolidación de la memoria. Ejecuta el
 * "Protocolo de Generación de Puntos de Guardado" utilizando un LLM.
 */
public class MemoryServiceImpl implements MemoryService {

  private static final Logger LOGGER = LoggerFactory.getLogger(MemoryServiceImpl.class);


  private final Agent agent;
  private final SourceOfTruth sourceOfTruth;
  private AgentConsole console;
  private Agent.ChatModel model;
  private String systemPrompt;
  private boolean running;
  private final AgentServiceFactory factory;

  public MemoryServiceImpl(AgentServiceFactory factory, Agent agent) {
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
    String[] resources = new String[]{
      "var/config/prompts/memory-compact.md"
    };
    for (String resPath : resources) {
      this.agent.installResource(resPath);
    }
    this.agent.getActions().addAction(new AbstractAgentAction(this.agent, CHANGE_MEMORY_PROVIDER) {
      @Override
      public boolean perform(AgentSettings settings) {
        model = agent.createChatModel(MemoryService.ID);
        return true;
      }
    });
    this.agent.getActions().addAction(new AbstractAgentAction(this.agent, CHANGE_MEMORY_MODEL) {
      @Override
      public boolean perform(AgentSettings settings) {
        model = agent.createChatModel(MemoryService.ID);
        return true;
      }
    });
    this.model = this.agent.createChatModel(MemoryService.ID);
    loadSystemPrompt();
    this.running = true;
    
    this.agent.getConsole().printSystemLog("Memory service " + getModelName());
  }

  private void loadSystemPrompt() {
    this.systemPrompt = agent.getResourceAsString("var/config/prompts/memory-compact.md");
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
  @Override
  public CheckPoint compact(CheckPoint previous, List<Turn> newTurns) {
    if (newTurns == null || newTurns.isEmpty()) {
      throw new IllegalArgumentException("No hay turnos para compactar.");
    }

    // 1. Preparar el Prompt del Usuario (Input Data)
    String userPrompt = buildUserPrompt(previous, newTurns);

    // 2. Invocar al LLM
    LOGGER.info("Iniciando compactación de " + newTurns.size() + " turnos.");
    this.console.printSystemLog("Iniciando compactación de " + newTurns.size() + " turnos...");
    AiMessage response = model.generate(
            SystemMessage.from(this.systemPrompt),
            UserMessage.from(userPrompt)
    ).content();

    String generatedText = response.text();

    // TODO: Comprobar aqui que las citas del nuevo "viaje" son correctas.
    
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
    CheckPoint cp = this.sourceOfTruth.createCheckPoint(firstId, lastId, LocalDateTime.now(), generatedText);
    LOGGER.info("Compactacion finalizada.");
    return cp;
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

    // TODO: Falta por implementar correctamente la rehidratacion del las herramienta de memoria.
    
    // TODO: Habria que implementar el troceado de los turnos generando mas de un punto
    // de guardado, cuando estos no entren en el contexto del LLM encargado de compactarlos.
    
    // TODO: Ver hasta que punto es necesario (hacerlo optativo) la implementacion
    // de la gestion de estado al realizar las compactaciones. Probablemente habria
    // que hacerlo en una segunda llamada al LLM para evitar problemas cognitivos y que
    // luego el agente se encargue de montarlo todo en un solo punto de guardado.
    
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
      case MemoryService.ID:
        return new ModelParametersImpl(
                settings.getPropertyAsString(MEMORY_PROVIDER_URL),
                settings.getPropertyAsString(MEMORY_PROVIDER_API_KEY),
                settings.getPropertyAsString(MEMORY_MODEL_ID),
                0.7d
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

  @Override
  public void stop() {
    this.running = false;
  }
  
  public Agent.ChatModel getModel() {
    return model;
  }
  
  public String getModelName() {
    Agent.ChatModel theModel = this.getModel();
    if( theModel == null ) {
      return null;
    }
    return theModel.getParameters().modelId();
  }

}
