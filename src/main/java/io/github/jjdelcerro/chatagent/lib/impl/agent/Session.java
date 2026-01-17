package io.github.jjdelcerro.chatagent.lib.impl.agent;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import dev.langchain4j.data.message.*;
import io.github.jjdelcerro.chatagent.lib.persistence.CheckPoint;
import io.github.jjdelcerro.chatagent.lib.persistence.Turn;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Agregado que gobierna el estado de la sesión activa de conversación.
 * Gestiona la lista de mensajes (Protocolo) y su sincronización con los Turnos (Archivo).
 */
public class Session {

    private final Path sessionPath;
    private final Path tempPath;

    // ESTADO INTERNO
    private final List<ChatMessage> messages = new ArrayList<>();
    // Key: Índice en 'messages', Value: ID del Turno
    private Map<Integer, Integer> turnOfMessage = new HashMap<>();

    // Interfaz pública para marcas de compactación
    public interface SessionMark {
        int getTurnId();
        ChatMessage getMessage();
    }

    /**
     * Constructor.
     */
    public Session(Path dataFolder) {
        this.sessionPath = dataFolder.resolve("active_session.json");
        this.tempPath = dataFolder.resolve("active_session.json.tmp");
        this.load();
    }

    // =================================================================================
    // API GESTIÓN DE CONVERSACIÓN
    // =================================================================================

    public void add(ChatMessage message) {
        this.messages.add(message);
        this.save();
    }
    
    public void addAll(List<ChatMessage> newMessages) {
        this.messages.addAll(newMessages);
        this.save();
    }

    public void consolideTurn(Turn turn) {
        if (messages.isEmpty()) return;
        
        // Backfill: Asignar ID a todos los mensajes recientes que aún no lo tienen
        // Iteramos hacia atrás hasta encontrar uno que ya tenga dueño o llegar al principio.
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (this.turnOfMessage.containsKey(i)) {
                break; // Stop, zona consolidada alcanzada
            }
            this.turnOfMessage.put(i, turn.getId());
        }
        
        this.save();
    }

    public List<ChatMessage> getContextMessages(CheckPoint checkpoint, String systemPrompt) {
        List<ChatMessage> context = new ArrayList<>();

        // 1. Construir System Prompt compuesto
        StringBuilder sb = new StringBuilder();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            sb.append(systemPrompt);
        }

        if (checkpoint != null) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM 'de' yyyy, HH:mm", new Locale("es", "ES"));
            sb.append("\n\n## Contexto consolidado de la conversación\n");
            sb.append("Resumen actualizado hasta: ").append(checkpoint.getTimestamp().toLocalDateTime().format(formatter)).append(".\n\n");
            sb.append("--- INICIO DEL RELATO ---\n");
            sb.append(checkpoint.getText()).append("\n");
            sb.append("--- FIN DEL RELATO ---\n");
        }

        if (sb.length() > 0) {
            context.add(SystemMessage.from(sb.toString()));
        }

        // 2. Añadir mensajes activos
        context.addAll(this.messages);

        return context;
    }
    
    public void clear() {
        this.messages.clear();
        this.turnOfMessage.clear();
        this.save();
    }

    // =================================================================================
    // API COMPACTACIÓN (SessionMark)
    // =================================================================================

    public SessionMark getOldestMark() {
        if (messages.isEmpty()) return null;
        if (!turnOfMessage.containsKey(0)) return null; // No hay nada consolidado al principio

        // Gracias al backfill, si hay consolidación, el mensaje 0 tiene ID.
        return new SessionMarkImpl(0, turnOfMessage.get(0), messages.get(0));
    }

    public SessionMark getCompactMark() {
        // Encontrar la mediana de los mensajes consolidados iterando la lista
        int consolidatedCount = 0;
        
        // Contamos cuántos mensajes tienen ID
        for (int i = 0; i < messages.size(); i++) {
            if (turnOfMessage.containsKey(i)) {
                consolidatedCount++;
            }
        }
        
        if (consolidatedCount == 0) return null;

        // El target es la mitad de los consolidados
        int targetOrdinal = consolidatedCount / 2;
        int foundOrdinal = 0;
        
        for (int i = 0; i < messages.size(); i++) {
            if (turnOfMessage.containsKey(i)) {
                if (foundOrdinal == targetOrdinal) {
                    return new SessionMarkImpl(i, turnOfMessage.get(i), messages.get(i));
                }
                foundOrdinal++;
            }
        }
        return null;
    }

    public void remove(SessionMark mark1, SessionMark mark2) {
        if (!(mark1 instanceof SessionMarkImpl) || !(mark2 instanceof SessionMarkImpl)) {
            throw new IllegalArgumentException("Marcas inválidas");
        }
        
        // Asumimos mark1 siempre es index 0 en la práctica de compactación estándar,
        // pero usaremos los índices reales por robustez.
        int idx1 = ((SessionMarkImpl) mark1).index;
        int idx2 = ((SessionMarkImpl) mark2).index;
        
        if (idx1 > idx2) { int t = idx1; idx1 = idx2; idx2 = t; }
        if (idx2 >= messages.size()) idx2 = messages.size() - 1;
        
        int offset = idx2 - idx1 + 1;
        Map<Integer, Integer> newMap = new HashMap<>();
        
        // 1. Preservar lo anterior al corte (si mark1 > 0)
        for (int i = 0; i < idx1; i++) {
            if (turnOfMessage.containsKey(i)) {
                newMap.put(i, turnOfMessage.get(i));
            }
        }
        
        // 2. Preservar lo posterior al corte (desplazado)
        // Iteramos sobre los mensajes supervivientes
        for (int i = idx2 + 1; i < messages.size(); i++) {
            if (turnOfMessage.containsKey(i)) {
                newMap.put(i - offset, turnOfMessage.get(i));
            }
        }
        
        // 3. Ejecutar borrado físico
        this.messages.subList(idx1, idx2 + 1).clear();
        
        // 4. Swap map
        this.turnOfMessage = newMap;
        
        this.save();
    }

    // =================================================================================
    // PERSISTENCIA (Interna)
    // =================================================================================

    private static class SessionState {
        List<ChatMessage> messages;
        Map<Integer, Integer> turnOfMessage;
        
        SessionState(List<ChatMessage> m, Map<Integer, Integer> t) { this.messages = m; this.turnOfMessage = t; }
        SessionState(){}
    }

    private void load() {
        if (!Files.exists(sessionPath)) return;
        Gson gson = createGson();
        try (Reader reader = Files.newBufferedReader(sessionPath, StandardCharsets.UTF_8)) {
            SessionState state = gson.fromJson(reader, SessionState.class);
            if (state != null) {
                if (state.messages != null) this.messages.addAll(state.messages);
                if (state.turnOfMessage != null) this.turnOfMessage.putAll(state.turnOfMessage);
            }
        } catch (Exception e) {
            System.err.println("Error recuperando sesión: " + e.getMessage());
        }
    }

    private void save() {
        Gson gson = createGson();
        SessionState state = new SessionState(this.messages, this.turnOfMessage);
        try {
            try (Writer writer = Files.newBufferedWriter(tempPath, StandardCharsets.UTF_8)) {
                gson.toJson(state, writer);
                writer.flush();
            }
            Files.move(tempPath, sessionPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new RuntimeException("Error guardando sesión: " + e.getMessage(), e);
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
        public int getTurnId() { return turnId; }

        @Override
        public ChatMessage getMessage() { return message; }
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
                case USER -> UserMessage.class;
                case AI -> AiMessage.class;
                case SYSTEM -> SystemMessage.class;
                case TOOL_EXECUTION_RESULT -> ToolExecutionResultMessage.class;
                default -> throw new JsonParseException("Unknown message type: " + type);
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
                case TEXT -> TextContent.class;
                case IMAGE -> ImageContent.class;
                default -> throw new JsonParseException("Unknown content type: " + type);
            };
            return context.deserialize(data, clazz);
        }
    }
}
