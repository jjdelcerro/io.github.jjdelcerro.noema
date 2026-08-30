package io.github.jjdelcerro.noema.lib.impl.services.memory;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import io.github.jjdelcerro.noema.lib.AbstractAgentAction;
import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.Agent.ModelParameters;
import static io.github.jjdelcerro.noema.lib.AgentActions.CHANGE_MEMORY_MODEL;
import static io.github.jjdelcerro.noema.lib.AgentActions.CHANGE_MEMORY_PROVIDER;
import io.github.jjdelcerro.noema.lib.memory.episodic.Turn;

import java.util.List;
import io.github.jjdelcerro.noema.lib.AgentConsole;
import io.github.jjdelcerro.noema.lib.AgentServiceFactory;
import io.github.jjdelcerro.noema.lib.settings.AgentSettings;
import io.github.jjdelcerro.noema.lib.AgentTool;
import io.github.jjdelcerro.noema.lib.impl.ModelParametersImpl;
import io.github.jjdelcerro.noema.lib.impl.services.memory.tools.AnnotateObservationTool;
import io.github.jjdelcerro.noema.lib.impl.services.memory.tools.LookupTurnTool;
import io.github.jjdelcerro.noema.lib.impl.services.memory.tools.SearchFullHistoryTool;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static io.github.jjdelcerro.noema.lib.Agent.DEFAULT_SUBCHANNEL;
import io.github.jjdelcerro.noema.lib.memory.episodic.EpisodicMemory;
import io.github.jjdelcerro.noema.lib.memory.compacted.CompactedMemory;
import io.github.jjdelcerro.noema.lib.services.memory.MemoryCompactionService;

/**
 * Componente cognitivo encargado de la consolidación de la memoria. Ejecuta el
 * "Protocolo de Generación de Puntos de Guardado" utilizando un LLM.
 * 
 * TODO: Antes MemoryServiceImpl, habria que actualizar la documentacion con este cambio 
 */
public class MemoryCompactionServiceImpl implements MemoryCompactionService {

  private static final Logger LOGGER = LoggerFactory.getLogger(MemoryCompactionServiceImpl.class);

  private final Agent agent;
  private final EpisodicMemory episodicMemory;
  private AgentConsole console;
  private Agent.ChatModel model;
  private String systemPrompt;
  private boolean running;
  private final AgentServiceFactory factory;

  public MemoryCompactionServiceImpl(AgentServiceFactory factory, Agent agent) {
    this.factory = factory;
    this.agent = agent;
    this.episodicMemory = agent.getEpisodicMemory();
    this.console = agent.getConsole(DEFAULT_SUBCHANNEL);
  }

  @Override
  public AgentServiceFactory getFactory() {
    return factory;
  }

  @Override
  public void start() {
    String[] resources = new String[]{
      "var/config/prompts/memory-compaction.md"
    };
    for (String resPath : resources) {
      this.agent.installResource(resPath);
    }
    this.agent.getActions().addAction(new AbstractAgentAction(this.agent, CHANGE_MEMORY_PROVIDER) {
      @Override
      public boolean perform(AgentSettings settings) {
        model = agent.createChatModel(MemoryCompactionService.ID);
        return true;
      }
    });
    this.agent.getActions().addAction(new AbstractAgentAction(this.agent, CHANGE_MEMORY_MODEL) {
      @Override
      public boolean perform(AgentSettings settings) {
        model = agent.createChatModel(MemoryCompactionService.ID);
        return true;
      }
    });
    this.model = this.agent.createChatModel(MemoryCompactionService.ID);
    loadSystemPrompt();
    this.running = true;

    this.console.printSystemLog("Memory compaction service " + getModelName());
  }

  private void loadSystemPrompt() {
    this.systemPrompt = agent.getResourceAsString("var/config/prompts/memory-compaction.md");
    if (this.systemPrompt.isEmpty()) {
      throw new RuntimeException("No se pudo cargar el prompt del MemoryCompactionService");
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
  public CompactedMemory compact(String subchannel, CompactedMemory previous, List<Turn> newTurns) {
    if (newTurns == null || newTurns.isEmpty()) {
      throw new IllegalArgumentException("No hay turnos para compactar.");
    }
    Set<Integer> validTurnIds = new HashSet<>();
    if( previous != null ) {
      validTurnIds.addAll(extractCitationIds(previous.getText()));
    }
    for (Turn turn : newTurns) {
      validTurnIds.add(turn.getId());
      if ("lookup_turn".equals(turn.getContenttype()) 
          || "tool_execution".equals(turn.getContenttype()) 
          || "tool_execution_summarized".equals(turn.getContenttype())) {
          validTurnIds.addAll(extractCitationIds(turn.getToolResult()));
      }
    }
    
    String userPrompt = buildUserPrompt(previous, newTurns);

    LOGGER.info("Iniciando compactación de " + newTurns.size() + " turnos.");
    this.console.printSystemLog("Iniciando compactación de " + newTurns.size() + " turnos...");
    AiMessage response = model.generate(
            SystemMessage.from(this.systemPrompt),
            UserMessage.from(userPrompt)
    ).content();

    String generatedText = response.text();

    Collection<Integer> currentCitations = extractCitationIds(generatedText);
    for (Integer turnId : currentCitations) {
      if( !validTurnIds.contains(turnId) ) {
        LOGGER.warn("Encontrada cita "+turnId+" incorrecta.");
        generatedText = generatedText.replace("{cite:" + turnId + "}", "{badcite:" + turnId + "}");
      }
    }
    
    int firstId = newTurns.getFirst().getId();
    int lastId = newTurns.getLast().getId();

    // TODO: Si venimos de un CP anterior, el rango empieza donde empezaba aquel (historia acumulada)
    // Opcional: Si queremos que el CP represente TODA la historia, firstId debería ser el del CP previo.
    // Si queremos que represente el bloque consolidado, se mantiene el actual.
    // Según tu arquitectura de "El Viaje" acumulativo, el 'first' debería ser el inicio de los tiempos.
    if (previous != null) {
      firstId = previous.getTurnFirst();
    }

    CompactedMemory cp = this.episodicMemory.createCompactedMemory(subchannel, firstId, lastId, LocalDateTime.now(), generatedText);
    LOGGER.info("Compactacion finalizada.");
    return cp;
  }

  /**
   * Extrae los IDs de las citas presentes en un texto. Soporta formatos
   * {cite:123} y {cite:12,6,24}
   */
  private Collection<Integer> extractCitationIds(String text) {
    Set<Integer> foundIds = new HashSet<>();

    // Regex: Busca "{cite:" seguido de números, comas o espacios, hasta el cierre "}"
    // Captura el grupo de contenido dentro de los corchetes
    Pattern pattern = Pattern.compile("\\{cite:\\s*([\\d,\\s]+)\\}");
    Matcher matcher = pattern.matcher(text);

    while (matcher.find()) {
      String match = matcher.group(1);
      String[] parts = StringUtils.split(match,',');

      for (String part : parts) {
        try {
          String idStr = StringUtils.trim(part);
          if (!idStr.isEmpty()) {
            foundIds.add(Integer.valueOf(idStr));
          }
        } catch (NumberFormatException e) {
          LOGGER.warn("Cita no valida '"+match+"'.");
        }
      }
    }

    return foundIds;
  }

  private String buildUserPrompt(CompactedMemory previous, List<Turn> newTurns) {
    StringBuilder sb = new StringBuilder();

    // --- CONTEXTO PREVIO (Si existe) ---
    if (previous != null) {
      sb.append("MODO DE OPERACIÓN: 2 (Actualización)\n\n");
      sb.append("=== DOCUMENTO DE PUNTO DE GUARDADO ANTERIOR ===\n");
      sb.append(previous.getText()); 
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
      case MemoryCompactionService.ID:
        return new ModelParametersImpl(
                settings.getPropertyAsString(MEMORY_PROVIDER_URL),
                settings.getPropertyAsString(MEMORY_PROVIDER_API_KEY),
                settings.getPropertyAsString(MEMORY_MODEL_ID),
                0.2f
        );
    }
    return null;
  }

  @Override
  public boolean canStart() {
    if( !this.factory.canStart(agent.getSettings()) ) {
      return false;
    }
    return this.agent.getEpisodicMemoryDatabase() != null ;
  }

  @Override
  public List<AgentTool> getTools() {
    AgentTool[] tools = new AgentTool[]{
      new LookupTurnTool(this.agent),
      new SearchFullHistoryTool(this.agent),
      new AnnotateObservationTool(this.agent)
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
    if (theModel == null) {
      return null;
    }
    return theModel.getParameters().modelId();
  }

}
