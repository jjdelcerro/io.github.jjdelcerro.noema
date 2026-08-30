package io.github.jjdelcerro.noema.lib.impl.memory.recent;

import io.github.jjdelcerro.noema.lib.memory.recent.RecentMemory;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import io.github.jjdelcerro.noema.lib.impl.memory.GsonUtils.ChatMessageAdapter;
import io.github.jjdelcerro.noema.lib.impl.memory.GsonUtils.ContentAdapter;
import io.github.jjdelcerro.noema.lib.settings.AgentSettings;
import io.github.jjdelcerro.noema.lib.memory.episodic.Turn;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static io.github.jjdelcerro.noema.lib.services.reasoning.ReasoningService.MEMORY_CONSOLIDATION_TURNS;

/**
 * Agregado que gobierna el estado de la sesion activa de conversacion. Gestiona
 * la lista de mensajes (Protocolo) y su sincronizacion con los Turnos
 * (Archivo).
 * 
 * TODO: Antes SessionImpl, habria que actualizar la documentacion con este cambio
 */
public class RecentMemoryImpl implements RecentMemory {

  private static final Logger LOGGER = LoggerFactory.getLogger(RecentMemoryImpl.class);

  private static final int DEFAULT_CONSOLIDATION_THRESHOLD = 40;
  private final String subchannel;

  private static class ChatMessageInfo {

    int turnId;

    // Gson necesita este constructor para la deserialización
    public ChatMessageInfo() {
    }

    public ChatMessageInfo(int turnId) {
      this.turnId = turnId;
    }

    // necesario en needConsolidation()
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

  private final Path recentMemoryPath;
  private final Path tempPath;
  private long lastTurnId;

  // ESTADO INTERNO
  private final List<ChatMessage> messages = new ArrayList<>();
  // Key: Indice en 'messages', Value: ChatMessageInfo
  private Map<Integer, ChatMessageInfo> turnOfMessage = new HashMap<>();
  private final AgentSettings settings;

  /**
   * Constructor.
   *
   * @param dataFolder
   * @param settings
   * @param subchannel
   */
  public RecentMemoryImpl(Path dataFolder, AgentSettings settings, String subchannel) {
    this.subchannel = subchannel;
    this.settings = settings;
    this.recentMemoryPath = dataFolder.resolve("recent_memory-" + subchannel + ".json");
    this.tempPath = dataFolder.resolve("recent_memory-" + subchannel + ".json.tmp");
    this.lastTurnId = 0;
    this.load();
  }

  @Override
  public String getSubchannel() {
    return subchannel;
  }

  // =================================================================================
  // API GESTION DE CONVERSACION
  // =================================================================================
  @Override
  public void add(ChatMessage message) {
    this.messages.add(message);
    this.save();
  }

  @Override
  public void consolideTurn(Turn turn) {
    this.lastTurnId = turn.getId();
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

  @Override
  public void clear() {
    this.messages.clear();
    this.turnOfMessage.clear();
    this.lastTurnId = 0L;
    this.save();
  }

  /**
   * Indica si la sesion ha acumulado suficientes turnos para requerir
   * compactacion.
   *
   * @return true si el numero de turnos unicos consolidados supera el umbral.
   */
  @Override
  public boolean needConsolidation() {
    if (turnOfMessage.isEmpty()) {
      return false;
    }
    // TODO: habria que implementar algun mecanismo para detectar si el tamaño 
    // de contexto a superado el 40% y disparar tambien ahi la compactacion.
    //
    // TODO: Probablemente haya que estudiar que hacer cuando hay herramientas que 
    // han devuelto una cantidad inmensa de texto, tal vez haya que valorar
    // si es mejor compactar o simplemente deshacernos de la informacion
    // devuelta por esas herramientas que de todos modos se iba a perder tras
    // la compactacion.

    // Contamos cuantos IDs de turnos unicos tenemos en la sesion
    Set<ChatMessageInfo> uniqueTurns = new HashSet<>(turnOfMessage.values());
    return uniqueTurns.size() >= getConsolidationThreshold();
  }

  @Override
  public int getTurnsCount() {
    Set<ChatMessageInfo> uniqueTurns = new HashSet<>(turnOfMessage.values());
    return uniqueTurns.size();
  }

  private int getConsolidationThreshold() {
    int x = (int) this.settings.getPropertyAsLong(MEMORY_CONSOLIDATION_TURNS, -1);
    if (x < 0) {
      this.settings.setProperty(MEMORY_CONSOLIDATION_TURNS, String.valueOf(DEFAULT_CONSOLIDATION_THRESHOLD));
      x = DEFAULT_CONSOLIDATION_THRESHOLD;
    }
    return x;
  }

  @Override
  public RecentMemoryMark getOldestMark() {
    if (messages.isEmpty()) {
      return null;
    }
    // Gracias al backfill, si hay alguna consolidacion, el indice 0 tiene ID.
    if (!turnOfMessage.containsKey(0)) {
      return null;
    }

    return new RecentMemoryMarkImpl(0, turnOfMessage.get(0).turnId, messages.get(0));
  }

  @Override
  public RecentMemoryMark getNewestMark() {
    if (messages.isEmpty()) {
      return null;
    }
    // Gracias al backfill, si hay alguna consolidacion, el indice 0 tiene ID.
    if (!turnOfMessage.containsKey(0)) {
      return null;
    }

    return new RecentMemoryMarkImpl(0, turnOfMessage.get(turnOfMessage.size() - 1).turnId, messages.get(messages.size() - 1));
  }

  @Override
  public RecentMemoryMark getConsolidateMark() {
    if (turnOfMessage.isEmpty()) {
      return null;
    }

    // 1. Punto de partida: la mitad de los mensajes
    int mid = messages.size() / 2;

    // 2. Ajustar hacia atras hasta encontrar un mensaje consolidado
    while (mid >= 0 && !turnOfMessage.containsKey(mid)) {
      mid--;
    }

    if (mid < 0) {
      return null;
    }

    int currentTurnId = turnOfMessage.get(mid).turnId;

    // 3. Avanzar hasta el final del bloque del mismo turno
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

    // 4. Garantizar la atomicidad de bloques de herramientas:
    // Si el mensaje inmediatamente posterior al corte es un ToolExecutionResultMessage
    // (por ejemplo, en llamadas paralelas donde cada tool tiene distinto turnId),
    // avanzamos obligatoriamente hasta consumir todas las respuestas de herramientas consecutivas.
    while (candidate + 1 < messages.size()) {
      ChatMessage nextMsg = messages.get(candidate + 1);
      if (nextMsg instanceof ToolExecutionResultMessage) {
        candidate++;
        if (turnOfMessage.containsKey(candidate)) {
          currentTurnId = turnOfMessage.get(candidate).turnId;
        }
      } else {
        break;
      }
    }

    return new RecentMemoryMarkImpl(candidate, currentTurnId, messages.get(candidate));
  }

  @Override
  public void remove(RecentMemoryMark mark1, RecentMemoryMark mark2) {
    if (!(mark1 instanceof RecentMemoryMarkImpl) || !(mark2 instanceof RecentMemoryMarkImpl)) {
      throw new IllegalArgumentException("Marcas invalidas");
    }

    RecentMemoryMarkImpl m1 = (RecentMemoryMarkImpl) mark1;
    RecentMemoryMarkImpl m2 = (RecentMemoryMarkImpl) mark2;

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
  private static class RecentMemoryState {

    List<ChatMessage> messages;
    Map<Integer, ChatMessageInfo> turnOfMessage;
    long lastTurnId;

    RecentMemoryState(List<ChatMessage> m, Map<Integer, ChatMessageInfo> t, long lastTurnId) {
      this.messages = m;
      this.turnOfMessage = t;
      this.lastTurnId = lastTurnId;
    }

    @SuppressWarnings("unused")
    RecentMemoryState() {
    }
  }

  private void load() {
    if (!Files.exists(recentMemoryPath)) {
      return;
    }
    Gson gson = createGson();
    try (Reader reader = Files.newBufferedReader(recentMemoryPath, StandardCharsets.UTF_8)) {
      RecentMemoryState state = gson.fromJson(reader, RecentMemoryState.class);
      if (state != null) {
        if (state.messages != null) {
          this.messages.addAll(state.messages);
        }
        if (state.turnOfMessage != null) {
          this.turnOfMessage.putAll(state.turnOfMessage);
        }
        this.lastTurnId = state.lastTurnId;
      }
    } catch (Exception e) {
      LOGGER.warn("Error recuperando sesion", e);
    }
  }

  @Override
  public void save() {
    Gson gson = createGson();
    RecentMemoryState state = new RecentMemoryState(this.messages, this.turnOfMessage, this.lastTurnId);
    try {
      try (Writer writer = Files.newBufferedWriter(tempPath, StandardCharsets.UTF_8)) {
        gson.toJson(state, writer);
        writer.flush();
      }
      Files.move(tempPath, recentMemoryPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
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
  private static class RecentMemoryMarkImpl implements RecentMemoryMark {

    final int index;
    final int turnId;
    final ChatMessage message;

    public RecentMemoryMarkImpl(int index, int turnId, ChatMessage message) {
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

  @Override
  public List<ChatMessage> getMessages() {
    // Devolvemos una copia para evitar problemas de concurrencia
    return new ArrayList<>(this.messages);
  }

  @Override
  public boolean isEmpty() {
    return this.messages.isEmpty();
  }

  @Override
  public long getLastTurnId() {
    return this.lastTurnId;
  }
  
}
