package io.github.jjdelcerro.noema.lib.impl.services.memory;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import io.github.jjdelcerro.noema.lib.spi.AbstractAgentAction;
import io.github.jjdelcerro.noema.lib.api.Agent;
import io.github.jjdelcerro.noema.lib.api.model.ModelParameters;
import static io.github.jjdelcerro.noema.lib.api.AgentActions.CHANGE_MEMORY_MODEL;
import static io.github.jjdelcerro.noema.lib.api.AgentActions.CHANGE_MEMORY_PROVIDER;
import io.github.jjdelcerro.noema.lib.api.memory.episodic.Turn;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import io.github.jjdelcerro.noema.lib.api.AgentConsole;
import io.github.jjdelcerro.noema.lib.api.AgentServiceFactory;
import io.github.jjdelcerro.noema.lib.api.settings.AgentSettings;
import io.github.jjdelcerro.noema.lib.api.AgentTool;
import io.github.jjdelcerro.noema.lib.impl.services.memory.tools.AnnotateObservationTool;
import io.github.jjdelcerro.noema.lib.impl.services.memory.tools.LookupTurnTool;
import io.github.jjdelcerro.noema.lib.impl.services.memory.tools.SearchFullHistoryTool;
import io.github.jjdelcerro.noema.lib.impl.services.reasoning.ReasoningServiceImpl;
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
import static io.github.jjdelcerro.noema.lib.api.Agent.DEFAULT_SUBCHANNEL;
import io.github.jjdelcerro.noema.lib.api.model.ChatModel;
import io.github.jjdelcerro.noema.lib.api.memory.episodic.EpisodicMemory;
import io.github.jjdelcerro.noema.lib.api.services.memory.MemoryConsolidationService;
import io.github.jjdelcerro.noema.lib.api.memory.consolidate.ConsolidateMemory;
import io.github.jjdelcerro.noema.lib.spi.AbstractAgentService;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.ZoneOffset;
import java.util.Objects;
import org.apache.commons.lang3.Strings;

/**
 * Componente cognitivo encargado de la consolidación de la memoria. Ejecuta el
 * "Protocolo de Generación de Puntos de Guardado" utilizando un LLM.
 *
 */
@SuppressWarnings("UseSpecificCatch")
public class MemoryConsolidationServiceImpl
        extends AbstractAgentService
        implements MemoryConsolidationService {

  public static final String SYSTEM_PROMPT_FILENAME = "memory-consolidation.md";

  private static final String CONTENT_TYPE_ANNOTATION = "annotation";
  private static final String CONTENT_TYPE_TOOL_EXECUTION = "tool_execution";
  private static final String CONTENT_TYPE_TOOL_EXECUTION_SUMMARIZED = "tool_execution_summarized";

  /**
   * Categorias de anotacion cuyo contenido no debe llegar al consolidador:
   * describen el estado momentaneo de una tarea o el mapa del codigo en un
   * instante, no historia.
   */
  private static final Set<String> OMITTED_NOTE_CATEGORIES = Set.of("TASK_SCRATCHPAD", "PROJECT_MAP");

  private static final String NOTE_OMITTED_MARK = "[Anotacion omitida]";

  private static final Gson GSON = new Gson();

  private static final Logger LOGGER = LoggerFactory.getLogger(MemoryConsolidationServiceImpl.class);

  private final EpisodicMemory episodicMemory;
  private AgentConsole console;
  private ChatModel model;
  private String systemPrompt;

  public MemoryConsolidationServiceImpl(AgentServiceFactory factory, Agent agent) {
    super(factory, agent);
    this.episodicMemory = agent.getEpisodicMemory();
    this.console = agent.getConsole(DEFAULT_SUBCHANNEL);
  }

  @Override
  public void start() {
    String[] resources = new String[]{
      "var/config/prompts/" + SYSTEM_PROMPT_FILENAME
    };
    for (String resPath : resources) {
      this.agent.installResource(resPath);
    }
    this.agent.getActions().addAction(new AbstractAgentAction(this.agent, CHANGE_MEMORY_PROVIDER) {
      @Override
      public boolean perform(AgentSettings settings) {
        model = agent.createChatModel(MemoryConsolidationService.ID);
        return true;
      }
    });
    this.agent.getActions().addAction(new AbstractAgentAction(this.agent, CHANGE_MEMORY_MODEL) {
      @Override
      public boolean perform(AgentSettings settings) {
        model = agent.createChatModel(MemoryConsolidationService.ID);
        return true;
      }
    });
    this.model = this.agent.createChatModel(MemoryConsolidationService.ID);
    loadSystemPrompt();
    this.running = true;

    this.console.printSystemLog("Memory consolidation service " + getModelName());
  }

  private void loadSystemPrompt() {
    this.systemPrompt = agent.getResourceAsString("var/config/prompts/" + SYSTEM_PROMPT_FILENAME);
    if (this.systemPrompt.isEmpty()) {
      throw new RuntimeException("No se pudo cargar el prompt del MemoryConsolidationService");
    }
  }

  /**
   * Ejecuta el proceso de compactación.
   *
   * @param previous El ConsolidateMemory anterior (puede ser null si es la
   * primera vez).
   * @param newTurns La lista de turnos recientes a consolidar.
   * @return Un nuevo ConsolidateMemory TRANSITORIO (ID -1) con el texto
   * generado.
   */
  @Override
  public ConsolidateMemory consolide(String subchannel, ConsolidateMemory previous, List<Turn> newTurns) {
    try {
      if (newTurns == null || newTurns.isEmpty()) {
        throw new IllegalArgumentException("No hay turnos para consolidar.");
      }

      List<Turn> refinedTurns = refineTurns(newTurns);

      Set<Integer> validTurnIds = new HashSet<>();
      if (previous != null) {
        validTurnIds.addAll(extractCitationIds(previous.getText()));
      }
      for (Turn turn : refinedTurns) {
        validTurnIds.add(turn.getId());
        if ("lookup_turn".equals(turn.getContenttype())
                || "tool_execution".equals(turn.getContenttype())
                || "tool_execution_summarized".equals(turn.getContenttype())) {
          validTurnIds.addAll(extractCitationIds(turn.getToolResult()));
        }
      }

      String userPrompt = buildUserPrompt(previous, refinedTurns);

      LOGGER.info("Iniciando consolidación de " + refinedTurns.size() + " turnos.");
      this.console.printSystemLog("Iniciando consolidación de " + refinedTurns.size() + " turnos...");
      AiMessage response = model.generate(
              SystemMessage.from(this.systemPrompt),
              UserMessage.from(userPrompt)
      ).content();

      String generatedText = response.text(); 
      if( StringUtils.isBlank(generatedText) ) {
        this.console.printSystemError("Error grave creando la consolidacion");
        dumpPrompts(this.systemPrompt, userPrompt);
      }

      Collection<Integer> currentCitations = extractCitationIds(generatedText);
      for (Integer turnId : currentCitations) {
        if (!validTurnIds.contains(turnId)) {
          LOGGER.warn("Encontrada cita " + turnId + " incorrecta.");
          this.console.printSystemLog("Encontrada cita " + turnId + " incorrecta.");
          generatedText = generatedText.replace("{cite:" + turnId + "}", "{badcite:" + turnId + "}");
        }
      }

      int firstId = refinedTurns.getFirst().getId();
      int lastId = refinedTurns.getLast().getId();

      if (previous != null) {
        firstId = previous.getTurnFirst();
      }

      LocalDateTime tm = LocalDateTime.now();
      ConsolidateMemory cp = this.episodicMemory.createConsolidateMemory(subchannel, firstId, lastId, tm, generatedText);
      dump(previous, cp, refinedTurns);
      LOGGER.info("Consolidación finalizada.");
      return cp;
    } catch(Throwable t) {
      LOGGER.warn("Se ha producido un error realizando la consolidacion.",t);
      this.console.printSystemLog("Se ha producido un error realizando la consolidacion. Error "+t.getLocalizedMessage());
      return null;
    }
  }
  
  private void dump(ConsolidateMemory previous, ConsolidateMemory current, List<Turn> turns) {
    // Nota: El volcado a XML es solo para temas de depuracion, la 
    // aplicacion no usa para nada los datos del fichero XML.
    
    try  {
      Timestamp tm = Timestamp.from(LocalDateTime.now().toInstant(ZoneOffset.UTC));
      String fname = String.format("consolidation-unknown-XX-XX-%s.xml",tm.toString());
      if( current != null ) {
        fname = String.format("consolidation-%s-%d-%d-%s.xml", 
                current.getSubchannel(), 
                current.getTurnFirst(),
                current.getTurnLast(), 
                current.getTimestamp().toString());
      }
      Path debugPath = agent.getPaths().getTempFolder().resolve("consolidation/"+fname);

      if (debugPath.getParent() != null) {
        Files.createDirectories(debugPath.getParent());
      }
    
      try (PrintWriter pw = new PrintWriter(new FileWriter(debugPath.toFile(), true))) {
        pw.println("<consolidation>");
        if( previous!=null ) {
          pw.print("  <previousText>CDATA[[");
          pw.print(getCDataSafe(previous.getText()));
          pw.println("]]</previousText>");
        }
        if( current!=null ) {
          pw.print("  <currentText>CDATA[[");
          pw.print(getCDataSafe(current.getText()));
          pw.println("]]</currentText>");
        }
        pw.println("  <turns>");
        for (Turn turn : turns) {
          if( turn!=null ) {
            pw.println(turn.toXML());
          }
        }
        pw.println("  </turns>");
        pw.println("</consolidation>");
      }
    } catch (Exception e) {
      LOGGER.warn("Error escribiendo en XML el log de la consolidacion de memoria", e);
    }
  }
  
  private String getCDataSafe(String text)  { // FIXME: mover getCDataSafe a un modulo reutilizable
    if (text == null) {
      return "";
    }
    String sanitized = sanitizeXmlText(text);
    if (sanitized.contains("]]>")) {
      // Split into safe chunks
      String safe = sanitized.replace("]]>", "]]]]><![CDATA[>");
      return safe;
    } else {
      return sanitized;
    }
  }

  private String sanitizeXmlText(String text) { // FIXME: mover sanitizeXmlText a un modulo reutilizable
    if (text == null) {
      return null;
    }
    StringBuilder sb = new StringBuilder(text.length());
    int i = 0;
    while (i < text.length()) {
      int cp = text.codePointAt(i);
      boolean legal = (cp == 0x9 || cp == 0xA || cp == 0xD)
              || (cp >= 0x20 && cp <= 0xD7FF)
              || (cp >= 0xE000 && cp <= 0xFFFD)
              || (cp >= 0x10000 && cp <= 0x10FFFF);
      if (legal) {
        sb.appendCodePoint(cp);
      } else {
        sb.append('\uFFFD');
      }
      i += Character.charCount(cp);
    }
    return sb.toString();
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
      String[] parts = StringUtils.split(match, ',');

      for (String part : parts) {
        try {
          String idStr = StringUtils.trim(part);
          if (!idStr.isEmpty()) {
            foundIds.add(Integer.valueOf(idStr));
          }
        } catch (NumberFormatException e) {
          LOGGER.warn("Cita no valida '" + match + "'.");
        }
      }
    }

    return foundIds;
  }

  /**
   * Refina la lista de turnos antes de enviarla al consolidador. La lista
   * devuelta conserva la longitud y el orden de la original; los turnos que no
   * se alteran se devuelven tal cual, sin copiar, y ninguno de los turnos
   * recibidos se modifica.
   *
   * @param newTurns Turnos procedentes de la memoria episodica.
   * @return Una lista nueva con los turnos refinados.
   */
  protected List<Turn> refineTurns(List<Turn> newTurns) {
    List<Turn> refined = new ArrayList<>(newTurns.size());
    for (Turn turn : newTurns) {
      Turn candidate = refineAnnotation(turn);
      if (candidate == null) {
        candidate = refineToolResult(turn);
      }
      refined.add(candidate != null ? candidate : turn);
    }
    return refined;
  }

  /**
   * Sustituye el contenido de las notas de trabajo y de mapa del proyecto por
   * la marca de contenido omitido, conservando origen, categoria y recurso.
   * Las notas de otro tipo y las que no declaran categoria pasan intactas.
   *
   * <p>El contenido de una anotacion vive en el toolCall, no en el toolResult,
   * que solo contiene el acuse de la herramienta.</p>
   *
   * @param turn Turno a examinar.
   * @return El turno refinado, o null si no procede modificarlo.
   */
  
  private Turn refineAnnotation(Turn turn) {
    if (!CONTENT_TYPE_ANNOTATION.equals(turn.getContenttype())) {
      return null;
    }
    String toolCall = turn.getToolCall();
    JsonObject toolCallJson = parseJson(toolCall);
    if (toolCallJson == null) {
      return null;
    }    
    if( !Strings.CI.equals(getJsonAttribute(toolCallJson, "name"),AnnotateObservationTool.TOOL_NAME) ) {
      return null;
    }
    JsonObject arguments = readArguments(toolCall);
    if (arguments == null) {
      return null;
    }
    JsonElement categoryElement = arguments.get("category");
    if (categoryElement == null || categoryElement.isJsonNull() || !categoryElement.isJsonPrimitive()) {
      return null;
    }
    String category = categoryElement.getAsString();
    if (!OMITTED_NOTE_CATEGORIES.contains(category)) {
      return null;
    }
    String refinedToolCall = omitNoteContent(toolCall, arguments);
    if (refinedToolCall == null || refinedToolCall.equals(toolCall)) {
      return null;
    }
    return turn.withToolCall(refinedToolCall);
  }

  private String getJsonAttribute(JsonObject o, String name) {
    JsonElement x = o.get(name);
    if( x == null ) {
      return null;
    }
    String s = x.getAsString();
    return s;
  }
  
  /**
   * Delega en la propia herramienta el recorte de su resultado. Solo actua
   * sobre herramientas de tipo operativo; si la herramienta no se resuelve, si
   * el servicio de razonamiento no esta disponible o si el recorte no aplica,
   * el turno pasa sin tocar.
   *
   * @param turn Turno a examinar.
   * @return El turno refinado, o null si no procede modificarlo.
   */
  private Turn refineToolResult(Turn turn) {
    String contenttype = turn.getContenttype();
    if (!CONTENT_TYPE_TOOL_EXECUTION.equals(contenttype)
            && !CONTENT_TYPE_TOOL_EXECUTION_SUMMARIZED.equals(contenttype)) {
      return null;
    }
    if (StringUtils.isBlank(turn.getToolResult())) {
      return null;
    }
    AgentTool tool = resolveTool(extractToolName(turn.getToolCall()));
    if (tool == null || tool.getType() != AgentTool.TYPE_OPERATIONAL) {
      return null;
    }
    String content = turn.getToolResult();
    JsonObject x = this.parseJson(content);
    if( x!=null ) {
      String s = getJsonAttribute(x, "content");
      if( !StringUtils.isBlank(s) &&
          !StringUtils.isBlank(getJsonAttribute(x, "status")) &&
          !StringUtils.isBlank(getJsonAttribute(x, "original_size_chars")) &&
          !StringUtils.isBlank(getJsonAttribute(x, "note")) ) {
          content = s;
      }
    }
    AgentTool.ToolResult trimmed = tool.trimResult(content, AgentTool.TrimResultType.Trim);
    if (trimmed == null || StringUtils.isBlank(trimmed.text())) {
      return null;
    }
    if (trimmed.text().equals(turn.getToolResult())) {
      return null;
    }
    return turn.withToolResult(trimmed.text());
  }

  /**
   * Resuelve una herramienta por nombre a traves del servicio de razonamiento
   * del agente.
   *
   * @param toolName Nombre de la herramienta.
   * @return La herramienta, o null si no esta disponible.
   */
  private AgentTool resolveTool(String toolName) {
    if (StringUtils.isBlank(toolName)) {
      return null;
    }
    try {
      Object service = this.agent.getService(ReasoningServiceImpl.NAME);
      if (!(service instanceof ReasoningServiceImpl)) {
        return null;
      }
      return ((ReasoningServiceImpl) service).getAvailableTool(toolName);
    } catch (Throwable t) {
      LOGGER.warn("No se pudo resolver la herramienta '" + toolName + "' para el refinamiento.", t);
      return null;
    }
  }

  /**
   * Parses the tool call of a turn into a JSON object. Returns null when the
   * text is not valid JSON, which is the case for turns stored with the old
   * ToolExecutionRequest.toString() format; those turns are left untouched.
   *
   * @param toolCall ToolCall del turno.
   * @return El objeto JSON raiz, o null si no se puede parsear.
   */
  private JsonObject parseJson(String toolCall) {
    if (StringUtils.isBlank(toolCall)) {
      return null;
    }
    try {
      return JsonParser.parseString(toolCall).getAsJsonObject();
    } catch (Throwable t) {
      return null;
    }
  }

  /**
   * Devuelve el objeto de argumentos de un toolCall.
   *
   * @param toolCall ToolCall del turno.
   * @return Los argumentos, o null si no estan presentes o no son un objeto.
   */
  private JsonObject readArguments(String toolCall) {
    JsonObject json = parseJson(toolCall);
    if (json == null) {
      return null;
    }
    JsonElement arguments = json.get("arguments");
    if (arguments == null || !arguments.isJsonObject()) {
      return null;
    }
    return arguments.getAsJsonObject();
  }

  /**
   * Reconstruye el toolCall de una anotacion conservando los campos de
   * clasificacion y sustituyendo el texto de la nota por la marca.
   *
   * @param toolCall ToolCall original.
   * @param arguments Argumentos ya parseados del toolCall.
   * @return El toolCall refinado, o null si no se pudo construir.
   */
  private String omitNoteContent(String toolCall, JsonObject arguments) {
    JsonObject json = parseJson(toolCall);
    if (json == null) {
      return null;
    }
    try {
      JsonObject refinedArguments = new JsonObject();
      copyStringField(arguments, "source", refinedArguments);
      copyStringField(arguments, "category", refinedArguments);
      copyStringField(arguments, "resource_id", refinedArguments);
      copyStringField(arguments, "type", refinedArguments);
      refinedArguments.addProperty("note", NOTE_OMITTED_MARK);

      JsonObject refined = new JsonObject();
      copyJsonElement(json, "id", refined);
      copyJsonElement(json, "name", refined);
      refined.add("arguments", refinedArguments);
      return GSON.toJson(refined);
    } catch (Throwable t) {
      LOGGER.warn("No se pudo reconstruir el toolCall de la anotacion.", t);
      return null;
    }
  }

  private void copyStringField(JsonObject source, String field, JsonObject target) {
    JsonElement value = source.get(field);
    if (value != null && !value.isJsonNull() && value.isJsonPrimitive()) {
      target.add(field, value);
    }
  }

  private void copyJsonElement(JsonObject source, String field, JsonObject target) {
    JsonElement value = source.get(field);
    if (value != null && !value.isJsonNull()) {
      target.add(field, value);
    }
  }

  /**
   * Extrae el nombre de la herramienta a partir del toolCall del turno.
   *
   * @param toolCall ToolCall del turno.
   * @return El nombre de la herramienta, o null si no se puede determinar.
   */
  private String extractToolName(String toolCall) {
    JsonObject json = parseJson(toolCall);
    if (json == null) {
      return null;
    }
    JsonElement name = json.get("name");
    if (name == null || name.isJsonNull() || !name.isJsonPrimitive()) {
      return null;
    }
    return name.getAsString();
  }

  private String buildUserPrompt(ConsolidateMemory previous, List<Turn> newTurns) {
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

    // TODO: Falta por implementar correctamente la rehidratacion del las herramienta de memoria.
    // TODO: Habria que implementar el troceado de los turnos generando mas de un punto
    // de guardado, cuando estos no entren en el contexto del LLM encargado de compactarlos.
    // TODO: Ver hasta que punto es necesario (hacerlo optativo) la implementacion
    // de la gestion de estado al realizar las compactaciones. Probablemente habria
    // que hacerlo en una segunda llamada al LLM para evitar problemas cognitivos y que
    // luego el agente se encargue de montarlo todo en un solo punto de guardado.
    boolean headerPending = true;
    for (Turn turn : newTurns) {
      if( headerPending ) {
        sb.append(turn.getCSVHeader()).append("\n");
        headerPending = false;
      }
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
      case MemoryConsolidationService.ID:
        return this.agent.getModelParameters(
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
    if (!this.factory.canStart(agent.getSettings())) {
      return false;
    }
    return this.agent.getEpisodicMemoryDatabase() != null;
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

  public ChatModel getModel() {
    return model;
  }

  public String getModelName() {
    ChatModel theModel = this.getModel();
    if (theModel == null) {
      return null;
    }
    return theModel.getParameters().modelId();
  }

  
  private void dumpPrompts(String systemPrompt, String userPrompt) {
    Timestamp tm = Timestamp.from(LocalDateTime.now().toInstant(ZoneOffset.UTC));
    Path path = agent.getPaths().getTempFolder().resolve("consolidate_prompt-" + tm.toString() + ".txt");
    if (path == null) {
      return;
    }
    try {
      if (path.getParent() != null) {
        Files.createDirectories(path.getParent());
      }
      Files.writeString(
              path, 
              "\n\n\n**System prompt**\n\n\n" + systemPrompt + "\n\n\n**User prompt**\n\n\n"+ userPrompt, 
              StandardCharsets.UTF_8
      );
    } catch (Exception e) {
      LOGGER.warn("Can't write consolidate prompts to "+path.toString(), e);
    }
  }
  
}
