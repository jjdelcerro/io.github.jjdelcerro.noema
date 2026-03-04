package io.github.jjdelcerro.noema.lib.impl.services.conversation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ContentType;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import static io.github.jjdelcerro.noema.lib.impl.services.conversation.ConversationServiceImpl.SYSTEMCLOCK_SENSOR_NAME;
import io.github.jjdelcerro.noema.lib.impl.services.sensors.SensorsServiceImpl;
import io.github.jjdelcerro.noema.lib.settings.AgentSettings;
import io.github.jjdelcerro.noema.lib.persistence.CheckPoint;
import io.github.jjdelcerro.noema.lib.persistence.Turn;
import static io.github.jjdelcerro.noema.lib.services.conversarion.ConversationService.MEMORY_COMPACTION_TURNS;
import io.github.jjdelcerro.noema.lib.services.sensors.ConsumableSensorEvent;
import io.github.jjdelcerro.noema.lib.services.sensors.SensorsService;
import static io.github.jjdelcerro.noema.lib.services.sensors.SensorsService.PRIORITY_NORMAL;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.ocpsoft.prettytime.PrettyTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Agregado que gobierna el estado de la sesion activa de conversacion. Gestiona
 * la lista de mensajes (Protocolo) y su sincronizacion con los Turnos
 * (Archivo).
 */
public class Session {
  
  private static final Logger LOGGER = LoggerFactory.getLogger(Session.class);

  private static final int DEFAULT_COMPACTION_THRESHOLD = 40;

  private static class ChatMessageInfo {

    int turnId;

    // Gson necesita este constructor para la deserialización
    public ChatMessageInfo() {
    }

    public ChatMessageInfo(int turnId) {
      this.turnId = turnId;
    }

    // necesario en needCompaction()
    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      ChatMessageInfo that = (ChatMessageInfo) o;
      return turnId == that.turnId;
    }

    @Override
    public int hashCode() {
      return Objects.hash(turnId);
    }
  }

  private final Path sessionPath;
  private final Path tempPath;
  private final SensorsServiceImpl sensors;

  // ESTADO INTERNO
  private final List<ChatMessage> messages = new ArrayList<>();
  // Key: Indice en 'messages', Value: ChatMessageInfo
  private Map<Integer, ChatMessageInfo> turnOfMessage = new HashMap<>();
  private LocalDateTime lastInteractionTime;
  private final AgentSettings settings;

  /**
   * Interfaz publica para marcas de compactacion.
   */
  public interface SessionMark {

    int getTurnId();

    ChatMessage getMessage();
  }

  /**
   * Constructor.
   *
   * @param dataFolder
   * @param settings
   */
  public Session(Path dataFolder, AgentSettings settings, SensorsServiceImpl sensors) {
    this.sensors = sensors;
    this.settings = settings;
    this.sessionPath = dataFolder.resolve("active_session.json");
    this.tempPath = dataFolder.resolve("active_session.json.tmp");
    this.load();
  }

  // =================================================================================
  // API GESTION DE CONVERSACION
  // =================================================================================
  public void add(ChatMessage message) {
    this.messages.add(message);
    this.save();
  }

  public void consolideTurn(Turn turn) {
    if (messages.isEmpty()) {
      return;
    }

    // Backfill: Asignar ID a todos los mensajes recientes que aun no lo tienen.
    // Se recorre hacia atras hasta encontrar un mensaje ya consolidado.
    for (int i = messages.size() - 1; i >= 0; i--) {
      if (this.turnOfMessage.containsKey(i)) {
        break;
      }
      this.turnOfMessage.put(i, new ChatMessageInfo(turn.getId()));
    }

    this.save();
  }

  public List<ChatMessage> getContextMessages(CheckPoint checkpoint, String systemPrompt) {
    List<ChatMessage> context = new ArrayList<>();

    StringBuilder sb = new StringBuilder();
    if (systemPrompt != null && !systemPrompt.isEmpty()) {
      sb.append(systemPrompt);
    }

    if (checkpoint != null) {
      DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM 'de' yyyy, HH:mm", new Locale("es", "ES"));
      sb.append("\n\n## Contexto consolidado de la conversacion\n");
      sb.append("Resumen actualizado hasta: ").append(checkpoint.getTimestamp().toLocalDateTime().format(formatter)).append(".\n\n");
      sb.append("--- INICIO DEL RELATO ---\\n");
      sb.append(checkpoint.getText()).append("\n");
      sb.append("--- FIN DEL RELATO ---\\n");
    }

    if (sb.length() > 0) {
      context.add(SystemMessage.from(sb.toString()));
    }

    LocalDateTime now = LocalDateTime.now();
    if (this.lastInteractionTime != null && !this.messages.isEmpty()) {
      ChatMessage lastMessage = this.messages.get(this.messages.size() - 1);
      if (lastMessage instanceof UserMessage) {
        // Introduccion de la percepcion temporal.
        Duration delta = Duration.between(this.lastInteractionTime, now);
        if (delta.toHours() >= 1) {
          PrettyTime pt = new PrettyTime(Locale.of("es"));
          String timeAgo = pt.format(this.lastInteractionTime);
          String content = "Ha pasado " + timeAgo + " desde la última interacción con el usuario.";
          ConsumableSensorEvent timerEvent = this.sensors.createSensorEvent(
                  SYSTEMCLOCK_SENSOR_NAME,
                  content, 
                  PRIORITY_NORMAL,
                  "A pasado el tiempo",
                  now, 
                  null
          );
          this.messages.add(timerEvent.getChatMessage());
          this.messages.add(timerEvent.getResponseMessage());
        }
      }
    }

    context.addAll(this.messages);
    this.lastInteractionTime = now;

    return context;
  }

  public void clear() {
    this.messages.clear();
    this.turnOfMessage.clear();
    this.save();
  }

  /**
   * Indica si la sesion ha acumulado suficientes turnos para requerir
   * compactacion.
   *
   * @return true si el numero de turnos unicos consolidados supera el umbral.
   */
  public boolean needCompaction() {
    if (turnOfMessage.isEmpty()) {
      return false;
    }
    // TODO: habria que implementar algun mecanismo para detectar si el tamaño 
    // de contexto a superado el 40% y disparar tambien ahi la compactacion.
    //
    // TODO: Probablemente haya que estudir que hacer cuando hay herramientas que 
    // han devuelto una cantidad inmensa de texto, tal vez haya que valorar
    // si es mejor compactar o simplemente deshacernos de la informacion
    // devuelta por esas herramientas que de todos modos se iba a perder tras
    // la compactacion.
    
    // Contamos cuantos IDs de turnos unicos tenemos en la sesion
    Set<ChatMessageInfo> uniqueTurns = new HashSet<>(turnOfMessage.values());
    return uniqueTurns.size() >= getCompactationThreshold();
  }

  private int getCompactationThreshold() {
    int x = (int) this.settings.getPropertyAsLong(MEMORY_COMPACTION_TURNS, -1);
    if (x < 0) {
      this.settings.setProperty(MEMORY_COMPACTION_TURNS, String.valueOf(DEFAULT_COMPACTION_THRESHOLD));
      x = DEFAULT_COMPACTION_THRESHOLD;
    }
    return x;
  }

  // =================================================================================
  // API COMPACTACION (SessionMark)
  // =================================================================================
  public SessionMark getOldestMark() {
    if (messages.isEmpty()) {
      return null;
    }
    // Gracias al backfill, si hay alguna consolidacion, el indice 0 tiene ID.
    if (!turnOfMessage.containsKey(0)) {
      return null;
    }

    return new SessionMarkImpl(0, turnOfMessage.get(0).turnId, messages.get(0));
  }

  public SessionMark getCompactMark() {
    if (turnOfMessage.isEmpty()) {
      return null;
    }

    // Punto de partida: la mitad de los mensajes
    int mid = messages.size() / 2;

    // Ajustar hacia atras hasta encontrar un mensaje consolidado
    while (mid >= 0 && !turnOfMessage.containsKey(mid)) {
      mid--;
    }

    if (mid < 0) {
      return null;
    }

    int currentTurnId = turnOfMessage.get(mid).turnId;

    // Avanzar hasta el final del bloque del mismo turno para no romper la secuencia.
    int candidate = mid;
    while (candidate + 1 < messages.size()) {
      if (!turnOfMessage.containsKey(candidate + 1)) {
        break;
      }
      if (turnOfMessage.get(candidate + 1).turnId != currentTurnId) {
        break;
      }
      candidate++;
    }

    return new SessionMarkImpl(candidate, currentTurnId, messages.get(candidate));
  }

  public void remove(SessionMark mark1, SessionMark mark2) {
    if (!(mark1 instanceof SessionMarkImpl) || !(mark2 instanceof SessionMarkImpl)) {
      throw new IllegalArgumentException("Marcas invalidas");
    }

    SessionMarkImpl m1 = (SessionMarkImpl) mark1;
    SessionMarkImpl m2 = (SessionMarkImpl) mark2;

    int idx1 = m1.index;
    int idx2 = m2.index;

    // Ordenar indices por seguridad
    if (idx1 > idx2) {
      int t = idx1;
      idx1 = idx2;
      idx2 = t;
    }

    if (idx2 >= messages.size()) {
      idx2 = messages.size() - 1;
    }

    int offset = idx2 - idx1 + 1;
    Map<Integer, ChatMessageInfo> newMap = new HashMap<>();

    // 1. Preservar lo anterior al corte (indices menores a idx1)
    for (int i = 0; i < idx1; i++) {
      if (turnOfMessage.containsKey(i)) {
        newMap.put(i, turnOfMessage.get(i));
      }
    }

    // 2. Preservar y re-indexar lo posterior al corte (indices mayores a idx2)
    for (int i = idx2 + 1; i < messages.size(); i++) {
      if (turnOfMessage.containsKey(i)) {
        newMap.put(i - offset, turnOfMessage.get(i));
      }
    }

    // 3. Borrado fisico en la lista
    this.messages.subList(idx1, idx2 + 1).clear();

    // 4. Actualizar mapa
    this.turnOfMessage = newMap;

    this.save();
  }

  // =================================================================================
  // PERSISTENCIA (Interna)
  // =================================================================================
  private static class SessionState {

    List<ChatMessage> messages;
    Map<Integer, ChatMessageInfo> turnOfMessage;
    String lastInteractionTime;

    SessionState(List<ChatMessage> m, Map<Integer, ChatMessageInfo> t, String l) {
      this.messages = m;
      this.turnOfMessage = t;
      this.lastInteractionTime = l;
    }

    @SuppressWarnings("unused")
    SessionState() {
    }
  }

  private void load() {
    if (!Files.exists(sessionPath)) {
      return;
    }
    Gson gson = createGson();
    try (Reader reader = Files.newBufferedReader(sessionPath, StandardCharsets.UTF_8)) {
      SessionState state = gson.fromJson(reader, SessionState.class);
      if (state != null) {
        if (state.messages != null) {
          this.messages.addAll(state.messages);
        }
        if (state.turnOfMessage != null) {
          this.turnOfMessage.putAll(state.turnOfMessage);
        }
        if (state.lastInteractionTime != null) {
          this.lastInteractionTime = LocalDateTime.parse(state.lastInteractionTime);
        }
      }
    } catch (Exception e) {
      LOGGER.warn("Error recuperando sesion", e);
    }
  }

  private void save() {
    Gson gson = createGson();
    String lastTimeStr = this.lastInteractionTime != null ? this.lastInteractionTime.toString() : null;
    SessionState state = new SessionState(this.messages, this.turnOfMessage, lastTimeStr);
    try {
      try (Writer writer = Files.newBufferedWriter(tempPath, StandardCharsets.UTF_8)) {
        gson.toJson(state, writer);
        writer.flush();
      }
      Files.move(tempPath, sessionPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    } catch (IOException e) {
      throw new RuntimeException("Error guardando sesion: " + e.getMessage(), e);
    }
  }

  private Gson createGson() {
    return new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(ChatMessage.class, new ChatMessageAdapter())
            .registerTypeAdapter(Content.class, new ContentAdapter())
            .enableComplexMapKeySerialization()
            .create();
  }

  // =================================================================================
  // MEMENTO IMPL
  // =================================================================================
  private static class SessionMarkImpl implements SessionMark {

    final int index;
    final int turnId;
    final ChatMessage message;

    public SessionMarkImpl(int index, int turnId, ChatMessage message) {
      this.index = index;
      this.turnId = turnId;
      this.message = message;
    }

    @Override
    public int getTurnId() {
      return turnId;
    }

    @Override
    public ChatMessage getMessage() {
      return message;
    }
  }

  // =================================================================================
  // ADAPTADORES GSON
  // =================================================================================
  private static class ChatMessageAdapter implements JsonSerializer<ChatMessage>, JsonDeserializer<ChatMessage> {

    @Override
    public JsonElement serialize(ChatMessage src, Type typeOfSrc, JsonSerializationContext context) {
      JsonObject wrapper = new JsonObject();
      wrapper.addProperty("type", src.type().name());
      wrapper.add("data", context.serialize(src, src.getClass()));
      return wrapper;
    }

    @Override
    public ChatMessage deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
      JsonObject wrapper = json.getAsJsonObject();
      String typeStr = wrapper.get("type").getAsString();
      JsonElement data = wrapper.get("data");
      ChatMessageType type = ChatMessageType.valueOf(typeStr);
      Class<? extends ChatMessage> clazz = switch (type) {
        case USER ->
          UserMessage.class;
        case AI ->
          AiMessage.class;
        case SYSTEM ->
          SystemMessage.class;
        case TOOL_EXECUTION_RESULT ->
          ToolExecutionResultMessage.class;
        default ->
          throw new JsonParseException("Unknown message type: " + type);
      };
      return context.deserialize(data, clazz);
    }
  }

  private static class ContentAdapter implements JsonSerializer<Content>, JsonDeserializer<Content> {

    @Override
    public JsonElement serialize(Content src, Type typeOfSrc, JsonSerializationContext context) {
      JsonObject wrapper = new JsonObject();
      wrapper.addProperty("type", src.type().name());
      wrapper.add("data", context.serialize(src, src.getClass()));
      return wrapper;
    }

    @Override
    public Content deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
      JsonObject wrapper = json.getAsJsonObject();
      String typeStr = wrapper.get("type").getAsString();
      JsonElement data = wrapper.get("data");
      ContentType type = ContentType.valueOf(typeStr);
      Class<? extends Content> clazz = switch (type) {
        case TEXT ->
          TextContent.class;
        case IMAGE ->
          ImageContent.class;
        default ->
          throw new JsonParseException("Unknown content type: " + type);
      };
      return context.deserialize(data, clazz);
    }
  }

  public List<ChatMessage> getMessages() {
    // Devolvemos una copia para evitar problemas de concurrencia
    return new ArrayList<>(this.messages);
  }
}
