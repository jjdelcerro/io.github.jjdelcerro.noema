package io.github.jjdelcerro.noema.main;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.AgentConsole;
import io.github.jjdelcerro.noema.lib.settings.AgentSettings;
import io.github.jjdelcerro.noema.lib.settings.AgentSettingsCheckedList;
import io.github.jjdelcerro.noema.lib.settings.AgentSettingsGroup;
import io.github.jjdelcerro.noema.lib.settings.AgentSettingsItem;
import io.github.jjdelcerro.noema.lib.settings.AgentSettingsPaths;
import io.github.jjdelcerro.noema.lib.settings.AgentSettingsString;
import io.github.jjdelcerro.noema.lib.persistence.Turn;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.sse.SseClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;


/**
 * Servidor Web embebido para Noema.
 * Organizado mediante referencias a métodos para cada endpoint REST y SSE.
 */
public class NoemaWebServer {

    private static final Logger LOGGER = LoggerFactory.getLogger(NoemaWebServer.class);

    private final Agent agent;
    private final int port;
    private final Map<String, SseAgentConsole> activeConsoles;
    private Javalin app;

    public NoemaWebServer(Agent agent, int port) {
        this.agent = agent;
        this.port = port;
        this.activeConsoles = new ConcurrentHashMap<>();
    }

    /**
     * Inicia el servidor e instala la tabla de rutas mapeada a métodos de la instancia.
     */
    public synchronized void start() {
        if (this.app != null) {
            LOGGER.info("El servidor web de Noema ya se encuentra en ejecución.");
            return;
        }

        this.app = Javalin.create(config -> {
            config.bundledPlugins.enableCors(cors -> cors.addRule(it -> it.anyHost()));
            config.staticFiles.add(staticFiles -> {
                staticFiles.directory = "/webapp";
                staticFiles.location = io.javalin.http.staticfiles.Location.CLASSPATH;
            });
        });

        // --- TABLA DE ENRUTAMIENTO (BINDING DE MÉTODOS) ---

        // 1. Conversación e Historial
        this.app.post("/api/chat/{terminalId}", this::handlePostChatMessage);
        this.app.get("/api/chat/{terminalId}/history", this::handleGetHistory);
        this.app.sse("/api/console/{terminalId}", this::handleSseConsole);

        // 2. Configuración dinámica (Rutas específicas antes que las genéricas <path>)
        this.app.get("/api/config/ui", this::handleGetConfigUi);
        this.app.get("/api/config/domains/{domainName}", this::handleGetDomain);
        this.app.post("/api/config/multivalue", this::handlePostConfigMultivalue);
        this.app.post("/api/config/<path>/list", this::handlePostConfigList);
        this.app.post("/api/config/<path>", this::handlePostConfigPath);
        this.app.get("/api/config/<path>", this::handleGetConfigPath);

        this.app.start(this.port);
        LOGGER.info("Servidor web de Noema iniciado correctamente en http://localhost:{}", this.port);
    }

    /**
     * Detiene el servidor web y libera las consolas activas.
     */
    public synchronized void stop() {
        if (this.app != null) {
            this.app.stop();
            this.app = null;
            this.activeConsoles.clear();
            LOGGER.info("Servidor web de Noema detenido.");
        }
    }

    /**
     * Método estático de conveniencia para arranque rápido.
     */
    public static NoemaWebServer startServer(Agent agent, int port) {
        NoemaWebServer server = new NoemaWebServer(agent, port);
        server.start();
        return server;
    }

    // =========================================================================
    // HANDLERS DE ENDPOINTS (MÉTODOS DEDICADOS)
    // =========================================================================

    // --- 1. Conversación e Historial ---

    private void handlePostChatMessage(Context ctx) throws Exception {
        String terminalId = ctx.pathParam("terminalId");
        ChatMessageRequest body = ctx.bodyAsClass(ChatMessageRequest.class);

        if (body == null || body.message == null || body.message.trim().isEmpty()) {
            ctx.status(400).json(Map.of("status", "error", "message", "El mensaje no puede estar vacío"));
            return;
        }

        this.agent.putUsersMessage(terminalId, body.message, response -> {
            LOGGER.debug("Mensaje del terminal {} procesado asíncronamente.", terminalId);
        });

        ctx.status(202).json(Map.of(
            "status", "accepted",
            "message", "Message enqueued successfully"
        ));
    }

    private void handleGetHistory(Context ctx) throws Exception {
        String terminalId = ctx.pathParam("terminalId");
        List<Turn> turns = this.agent.getEpisodicMemory().getUnconsolidatedTurns(terminalId);
        List<FlatMessage> flatHistory = new ArrayList<>();

        for (Turn turn : turns) {
            long ts = Timestamp.valueOf(turn.getTimestamp()).getTime();

            if (turn.getTextUser() != null && !turn.getTextUser().trim().isEmpty()) {
                flatHistory.add(new FlatMessage("user-message", turn.getTextUser(), ts));
            }
            if (turn.getToolCall() != null && !turn.getToolCall().trim().isEmpty()) {
                flatHistory.add(new FlatMessage("log", turn.getToolCall(), ts));
            }
            if ("error".equals(turn.getContenttype()) && turn.getToolResult() != null) {
                flatHistory.add(new FlatMessage("error", turn.getToolResult(), ts));
            }
            if (turn.getTextModel() != null && !turn.getTextModel().trim().isEmpty()) {
                flatHistory.add(new FlatMessage("response", turn.getTextModel(), ts));
            }
        }

        ctx.json(flatHistory);
    }

    private void handleSseConsole(SseClient client) {
        String terminalId = client.ctx().pathParam("terminalId");

        SseAgentConsole console = this.activeConsoles.computeIfAbsent(terminalId, id -> {
            SseAgentConsole newConsole = new SseAgentConsole(id);
            this.agent.setConsole(id, newConsole);
            return newConsole;
        });

        console.addClient(client);

        client.onClose(() -> {
            console.removeClient(client);
            if (console.isEmpty()) {
                this.activeConsoles.remove(terminalId);
                this.agent.setConsole(terminalId, null);
            }
        });

        client.keepAlive();
    }

    // --- 2. Configuración Dinámica ---

    private void handleGetConfigUi(Context ctx) throws Exception {
        Path uiPath = this.agent.getPaths().getConfigFolder().resolve("settingsui.json");
        if (!Files.exists(uiPath)) {
            ctx.status(404).json(Map.of("error", "Archivo settingsui.json no encontrado"));
            return;
        }
        String json = Files.readString(uiPath, StandardCharsets.UTF_8);
        ctx.contentType("application/json").result(json);
    }

    private void handleGetDomain(Context ctx) throws Exception {
        String domainName = ctx.pathParam("domainName");
        Path uiPath = this.agent.getPaths().getConfigFolder().resolve("settingsui.json");

        if (!Files.exists(uiPath)) {
            ctx.status(404).json(Map.of("error", "settingsui.json no disponible"));
            return;
        }

        String uiJson = Files.readString(uiPath, StandardCharsets.UTF_8);
        JsonObject uiRoot = JsonParser.parseString(uiJson).getAsJsonObject();
        JsonObject domains = uiRoot.getAsJsonObject("domains");

        if (domains == null || !domains.has(domainName)) {
            ctx.status(404).json(Map.of("error", "Dominio no registrado: " + domainName));
            return;
        }

        String fileName = domains.get(domainName).getAsString();
        Path propPath = this.agent.getPaths().getConfigPath(fileName);

        if (!Files.exists(propPath)) {
            ctx.status(404).json(Map.of("error", "Properties del dominio no encontrado: " + fileName));
            return;
        }

        Properties props = new Properties();
        try (Reader r = Files.newBufferedReader(propPath, StandardCharsets.UTF_8)) {
            props.load(r);
        }

        List<Map<String, String>> result = new ArrayList<>();
        List<String> sortedKeys = new ArrayList<>(props.stringPropertyNames());
        Collections.sort(sortedKeys);

        for (String key : sortedKeys) {
            Map<String, String> entry = new LinkedHashMap<>();
            entry.put("key", key);
            entry.put("label", key.replace('_', ' '));
            entry.put("value", props.getProperty(key));
            result.add(entry);
        }

        ctx.json(result);
    }

    private void handlePostConfigMultivalue(Context ctx) throws Exception {
        JsonArray queryArray = JsonParser.parseString(ctx.body()).getAsJsonArray();
        JsonObject result = new JsonObject();

        Path uiPath = this.agent.getPaths().getConfigFolder().resolve("settingsui.json");
        JsonObject uiRoot = Files.exists(uiPath)
            ? JsonParser.parseString(Files.readString(uiPath, StandardCharsets.UTF_8)).getAsJsonObject()
            : new JsonObject();

        for (JsonElement el : queryArray) {
            JsonObject q = el.getAsJsonObject();
            String qPath = q.get("path").getAsString();
            JsonElement defaultValEl = q.get("defaultValue");

            Object defaultVal = defaultValEl == null ? ""
                : (defaultValEl.isJsonPrimitive() && defaultValEl.getAsJsonPrimitive().isBoolean()
                    ? defaultValEl.getAsBoolean() : defaultValEl.getAsString());

            if (q.has("enabledExpression")) {
                Map<String, Object> contextVars = new HashMap<>();
                if (q.has("context")) {
                    JsonObject contextJson = q.getAsJsonObject("context");
                    for (String k : contextJson.keySet()) {
                        contextVars.put(k, contextJson.get(k).getAsString());
                    }
                }
                Object val = this.agent.getSettings().eval(
                    q.get("enabledExpression").getAsString(), defaultVal, contextVars);
                result.addProperty(qPath, Boolean.parseBoolean(val.toString()));
            } else if (qPath.endsWith("/enabled")) {
                String parentPath = qPath.substring(0, qPath.length() - "/enabled".length());
                String expression = findChildEnabledExpression(uiRoot, parentPath);

                if (expression != null) {
                    Map<String, Object> contextVars = new HashMap<>();
                    if (q.has("context")) {
                        JsonObject contextJson = q.getAsJsonObject("context");
                        for (String k : contextJson.keySet()) {
                            contextVars.put(k, contextJson.get(k).getAsString());
                        }
                    }
                    Object val = this.agent.getSettings().eval(expression, defaultVal, contextVars);
                    result.addProperty(qPath, Boolean.parseBoolean(val.toString()));
                } else {
                    result.addProperty(qPath, true);
                }
            } else {
                String val = this.agent.getSettings().getPropertyAsString(qPath);
                if (val == null) {
                    result.addProperty(qPath, defaultVal.toString());
                } else {
                    result.addProperty(qPath, val);
                }
            }
        }

        ctx.contentType("application/json").result(new Gson().toJson(result));
    }

    private void handlePostConfigList(Context ctx) throws Exception {
        String path = ctx.pathParam("path");
        JsonArray array = JsonParser.parseString(ctx.body()).getAsJsonArray();
        List<String> values = new ArrayList<>();

        for (JsonElement el : array) {
            values.add(el.getAsString());
        }

        this.agent.getSettings().setProperty(path, values);
        this.agent.getSettings().save();
        triggerAction(path);

        ctx.json(Map.of("status", "success"));
    }

    private void handlePostConfigPath(Context ctx) throws Exception {
        String path = ctx.pathParam("path");
        JsonObject body = JsonParser.parseString(ctx.body()).getAsJsonObject();

        if (!body.has("value")) {
            ctx.status(400).json(Map.of("error", "Falta el campo 'value'"));
            return;
        }

        JsonElement valEl = body.get("value");

        int lastSlash = path.lastIndexOf('/');
        if (lastSlash != -1) {
            String parentPath = path.substring(0, lastSlash);
            String childKey = path.substring(lastSlash + 1);
            AgentSettingsItem parentItem = this.agent.getSettings().getProperty(parentPath);

            if (parentItem instanceof AgentSettingsCheckedList) {
                boolean isChecked = valEl.getAsBoolean();
                this.agent.getSettings().setChecked(parentPath, childKey, isChecked);
                this.agent.getSettings().save();
                triggerAction(parentPath);
                ctx.json(Map.of("status", "success"));
                return;
            }
        }

        this.agent.getSettings().setProperty(path, valEl.getAsString());
        this.agent.getSettings().save();
        triggerAction(path);

        ctx.json(Map.of("status", "success"));
    }

    private void handleGetConfigPath(Context ctx) throws Exception {
        String path = ctx.pathParam("path");
        AgentSettingsItem item = this.agent.getSettings().getProperty(path);

        if (item == null) {
            ctx.status(404).json(Map.of("error", "Propiedad no encontrada: " + path));
            return;
        }

        if (item instanceof AgentSettingsGroup) {
            ctx.contentType("application/json").result(new Gson().toJson(item));
        } else if (item instanceof AgentSettingsString stringItem) {
            ctx.json(Map.of("value", stringItem.getValue()));
        } else if (item instanceof AgentSettingsPaths pathsItem) {
            List<String> rawPaths = pathsItem.getValues().stream().map(Path::toString).toList();
            ctx.json(rawPaths);
        } else if (item instanceof AgentSettingsCheckedList checkedList) {
            ctx.json(checkedList.getItems());
        } else {
            ctx.json(Map.of("value", item.toString()));
        }
    }

    // =========================================================================
    // MÉTODOS AUXILIARES E INTERNOS
    // =========================================================================

    private void triggerAction(String variablePath) {
        try {
            Path uiPath = this.agent.getPaths().getConfigFolder().resolve("settingsui.json");
            if (Files.exists(uiPath)) {
                JsonObject uiRoot = JsonParser.parseString(Files.readString(uiPath, StandardCharsets.UTF_8)).getAsJsonObject();
                String actionName = findActionName(uiRoot, variablePath);
                if (actionName != null && !actionName.trim().isEmpty()) {
                    LOGGER.debug("Disparando acción lifecycle de Noema: {}", actionName);
                    this.agent.getActions().call(actionName, this.agent.getSettings());
                }
            }
        } catch (Exception e) {
            LOGGER.warn("No se pudo ejecutar la acción asociada al path: " + variablePath, e);
        }
    }

    private static String findActionName(JsonElement element, String variableName) {
        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            if (obj.has("variableName") && obj.get("variableName").getAsString().equals(variableName)) {
                if (obj.has("actionName")) {
                    return obj.get("actionName").getAsString();
                }
            }
            if (obj.has("childs")) {
                String action = findActionName(obj.get("childs"), variableName);
                if (action != null) return action;
            }
        } else if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                String action = findActionName(child, variableName);
                if (action != null) return action;
            }
        }
        return null;
    }

    private static String findChildEnabledExpression(JsonElement element, String variableName) {
        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            if (obj.has("variableName") && obj.get("variableName").getAsString().equals(variableName)) {
                if (obj.has("childEnabled")) {
                    return obj.get("childEnabled").getAsString();
                }
            }
            if (obj.has("childs")) {
                String exp = findChildEnabledExpression(obj.get("childs"), variableName);
                if (exp != null) return exp;
            }
        } else if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                String exp = findChildEnabledExpression(child, variableName);
                if (exp != null) return exp;
            }
        }
        return null;
    }

    // =========================================================================
    // DTOs Y CONSOLA SSE
    // =========================================================================

    public static class ChatMessageRequest {
        public String message;
    }

    public static class FlatMessage {
        public String type;
        public String content;
        public long timestamp;

        public FlatMessage(String type, String content, long timestamp) {
            this.type = type;
            this.content = content;
            this.timestamp = timestamp;
        }
    }

    public static class SseAgentConsole implements AgentConsole {
        private final String subchannel;
        private final List<SseClient> clients = new CopyOnWriteArrayList<>();

        public SseAgentConsole(String subchannel) {
            this.subchannel = subchannel;
        }

        public void addClient(SseClient client) {
            this.clients.add(client);
        }

        public void removeClient(SseClient client) {
            this.clients.remove(client);
        }

        public boolean isEmpty() {
            return this.clients.isEmpty();
        }

        private void broadcast(String eventType, String content) {
            JsonObject json = new JsonObject();
            json.addProperty("content", content);
            json.addProperty("timestamp", System.currentTimeMillis());
            String data = new Gson().toJson(json);

            for (SseClient client : clients) {
                try {
                    client.sendEvent(eventType, data);
                } catch (Exception e) {
                    clients.remove(client);
                }
            }
        }

        @Override
        public boolean confirm(String message) {
            return true;
        }

        @Override
        public void printSystemError(String message) {
            broadcast("error", message);
        }

        @Override
        public void printSystemLog(String message) {
            broadcast("log", message);
        }

        @Override
        public void printSystemLog(String message, Format format) {
            broadcast("log", message);
        }

        @Override
        public void printUserMessage(String message) {
            // Los mensajes de usuario son manejados localmente en la UI
        }

        @Override
        public void printModelResponse(String message) {
            broadcast("response", message);
        }
        
        @Override
        public void printModelReasoning(String message) {
            broadcast("reasoning", message);
        }
    }
}