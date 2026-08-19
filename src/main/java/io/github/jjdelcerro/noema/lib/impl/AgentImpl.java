package io.github.jjdelcerro.noema.lib.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import io.github.jjdelcerro.noema.lib.impl.services.reasoning.ReasoningServiceImpl;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.TokenCountEstimator;
import dev.langchain4j.model.openai.OpenAiTokenCountEstimator;
import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.AgentActions;
import io.github.jjdelcerro.noema.lib.AgentConsole;
import io.github.jjdelcerro.noema.lib.AgentLocator;
import io.github.jjdelcerro.noema.lib.impl.persistence.SourceOfTruthImpl;
import io.github.jjdelcerro.noema.lib.persistence.SourceOfTruth;
import java.nio.file.Paths;
import io.github.jjdelcerro.noema.lib.AgentAccessControl;
import io.github.jjdelcerro.noema.lib.AgentManager;
import io.github.jjdelcerro.noema.lib.AgentPaths;
import io.github.jjdelcerro.noema.lib.AgentService;
import io.github.jjdelcerro.noema.lib.AgentServiceFactory;
import io.github.jjdelcerro.noema.lib.AgentTool;
import io.github.jjdelcerro.noema.lib.ConnectionSupplier;
import io.github.jjdelcerro.noema.lib.settings.AgentSettings;
import io.github.jjdelcerro.noema.lib.services.reasoning.ReasoningService;
import io.github.jjdelcerro.noema.lib.services.sensors.SensorInformation;
import io.github.jjdelcerro.noema.lib.services.sensors.SensorNature;
import io.github.jjdelcerro.noema.lib.services.sensors.SensorsService;
import static io.github.jjdelcerro.noema.lib.services.sensors.SensorsService.PRIORITY_NORMAL;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author jjdelcerro
 */
@SuppressWarnings("UseSpecificCatch")
public class AgentImpl implements Agent {

  private static final Logger LOGGER = LoggerFactory.getLogger(AgentImpl.class);

  private static final int OVERHEAD_IN_ESTIMATE_TOOLS_TOKEN_COUNT = 15;

  public static final String USER_SENSOR_NAME = "USER";
  private static final String USER_SENSOR_LABEL = "USER";
  private static final String USER_SENSOR_DESCRIPTION = "USER";

  private Map<String,AgentConsole> consoles;
  private final AgentSettings settings;
  private final AgentActions actions;
  private final SourceOfTruth sourceOfTruth;

  private final AgentAccessControl accessControl;

  private final ConnectionSupplier servicesDatabase;
  private final ConnectionSupplier memoryDatabase;

  private final Map<String, AgentService> services;

  private JsonObject openRouterModels = null;
  private Thread shutdownHook;
  private boolean running;
  private TokenCountEstimator tokenEstimator;

/**
   * Constructor reservado para entornos de pruebas y depuracion.
   * Permite inyectar componentes simulados (Fakes) sin inicializar
   * la infraestructura de base de datos ni accesos reales al disco.
     * @param memoryDatabase
     * @param servicesDatabase
     * @param settings
     * @param accessControl
     * @param console
     * @param sourceOfTruth
   */
  public AgentImpl(
          ConnectionSupplier memoryDatabase, 
          ConnectionSupplier servicesDatabase, 
          AgentSettings settings, 
          AgentConsole console,
          SourceOfTruth sourceOfTruth,
          AgentAccessControl accessControl
  ) {
    this.running = false;
    this.actions = AgentLocator.getAgentManager().createActions();
    this.settings = settings;
    this.consoles = new HashMap<>();
    this.consoles.put(DEFAULT_SUBCHANNEL, console);
    this.services = new LinkedHashMap<>();
    
    
    Path sandboxRoot = (this.getPaths() != null && this.getPaths().getWorkspaceFolder() != null)
            ? this.getPaths().getWorkspaceFolder()
            : Paths.get(".").toAbsolutePath().normalize();

    this.accessControl = (accessControl != null) ? accessControl : new AgentAccessControlImpl(
            this.settings,
            this.actions,
            sandboxRoot,
            this::getCurrentConsole
    );    
    
    
    this.servicesDatabase = servicesDatabase;
    this.memoryDatabase = memoryDatabase;
    
    // Si se inyecta un SourceOfTruth (ej: en tests), lo usamos directamente.
    // Si no, instanciamos la implementación real conectada a la BBDD.
    if (sourceOfTruth != null) {
      this.sourceOfTruth = sourceOfTruth;
    } else {
      this.sourceOfTruth = SourceOfTruthImpl.from(this);
    }

    if (this.accessControl != null && this.getPaths() != null && this.getPaths().getAgentFolder() != null) {
      this.accessControl.addNonReadablePath(this.getPaths().getAgentFolder());
    }

    AgentManager manager = AgentLocator.getAgentManager();
    for (Supplier<AgentActions.AgentAction> actionFactory : manager.getActions()) {
      AgentActions.AgentAction action = actionFactory.get();
      action.setAgent(this);
      this.actions.addAction(action);
    }
  }  
  
  public AgentImpl(ConnectionSupplier memoryDatabase, ConnectionSupplier servicesDatabase, AgentSettings settings, AgentConsole console) {
    this(memoryDatabase, servicesDatabase, settings, console, null, null);
  }  
  
  @Override
  public synchronized void start() {
    AgentManager manager = AgentLocator.getAgentManager();
    for (AgentServiceFactory serviceFactory : manager.getServiceFactories()) {
      this.services.put(serviceFactory.getName(), serviceFactory.createService(this));
    }
    SensorsService sensors = (SensorsService) this.getService(SensorsService.NAME);
    SensorInformation sensor = sensors.createSensorInformation(
            USER_SENSOR_NAME,
            USER_SENSOR_LABEL,
            SensorNature.USER,
            USER_SENSOR_DESCRIPTION,
            false
    );
    sensors.registerSensor(sensor);

    ReasoningService reasoning = (ReasoningService) this.getService(ReasoningService.NAME);
    for (AgentService service : this.services.values()) {
      if (service.canStart()) {
        List<AgentTool> tools = service.getTools();
        if (tools != null) {
          for (AgentTool tool : tools) {
            reasoning.addTool(tool);
          }
          getConsole(DEFAULT_SUBCHANNEL).printSystemLog(service.getName() + " tools installed");
        }
      } else {
        getConsole(DEFAULT_SUBCHANNEL).printSystemLog(service.getName() + " tools NOT installed");
      }
    }

    this.startAllServices();

    this.running = true;
    this.shutdownHook = new Thread(() -> {
      // Llamamos al stop del servicio cuando la JVM se cierre
      this.stop();
    });
    Runtime.getRuntime().addShutdownHook(this.shutdownHook);
  }

  @Override
  public synchronized void stop() {
    if (!this.running) {
      return;
    }
    this.running = false;
    try {
      if (shutdownHook != null) {
        Runtime.getRuntime().removeShutdownHook(shutdownHook);
        shutdownHook = null;
      }
    } catch (IllegalStateException e) {
      // Si entramos aquí es porque la JVM ya está cerrándose. 
      // Es normal, simplemente ignoramos la excepción.
    }
    for (AgentService service : this.services.values()) {
      if (service.isRunning()) {
        service.stop();
      }
    }
  }

  @Override
  public AgentPaths getPaths() {
    return this.settings.getPaths();
  }

  @Override
  public AgentActions getActions() {
    return this.actions;
  }

  @Override
  public AgentSettings getSettings() {
    return this.settings;
  }

  @Override
  public AgentConsole getConsole(String subchannel) {
    AgentConsole console = this.consoles.get(subchannel);
    if( console == null ) {
        console = this.consoles.get(DEFAULT_SUBCHANNEL);
    }
    return console;
  }

  @Override
  public AgentConsole getCurrentConsole() {
      return this.getConsole(this.getCurrentSubchannel());
  }
  
  @Override
  public SourceOfTruth getSourceOfTruth() {
    return this.sourceOfTruth;
  }

  @Override
  public void putEvent(String channel, String subchannel, String status, String priority, String eventText) {
    SensorsService sensors = (SensorsService) this.getService(SensorsService.NAME);
    sensors.putEvent(channel, subchannel, eventText, priority, status, LocalDateTime.now());
  }

  @Override
  public AgentAccessControl getAccessControl() {
    return this.accessControl;
  }

  @Override
  public void setConsole(String subchannel, AgentConsole console) {
    this.consoles.put(subchannel, console);
  }

  @Override
  public ConnectionSupplier getServicesDatabase() {
    return this.servicesDatabase;
  }

  @Override
  public ConnectionSupplier getMemoryDatabase() {
    return this.memoryDatabase;
  }

  /**
   * Realiza una llamada al modelo de lenguaje y devuelve la respuesta como
   * texto plano.
   *
   * @param llmid
   * @param systemPrompt
   * @param message
   * @return
   */
  @Override
  public String callChatModel(String llmid, String systemPrompt, String message) {
    try {
      ChatModel model = this.createChatModel(llmid);

      List<dev.langchain4j.data.message.ChatMessage> messages = new ArrayList<>();
      if (org.apache.commons.lang3.StringUtils.isNotBlank(systemPrompt)) {
        messages.add(dev.langchain4j.data.message.SystemMessage.from(systemPrompt));
      }
      messages.add(dev.langchain4j.data.message.UserMessage.from(message));

      dev.langchain4j.model.output.Response<dev.langchain4j.data.message.AiMessage> response = model.generate(messages);
      return response.content().text();

    } catch (Exception e) {
      LOGGER.warn("Error en callChatModel (" + llmid + ")", e);
      getConsole(DEFAULT_SUBCHANNEL).printSystemError("Error en callChatModel (" + llmid + "): " + e.getMessage());
      return null;
    }
  }

  /**
   * Realiza una llamada al modelo y parsea la respuesta como un JsonObject de
   * GSON.Incluye limpieza automática de bloques de código Markdown.
   *
   * @param llmid
   * @param systemPrompt
   * @param message
   * @return
   */
  @Override
  public JsonObject callChatModelAsJson(String llmid, String systemPrompt, String message) {
    String rawResponse = callChatModel(llmid, systemPrompt, message);

    if (rawResponse == null || rawResponse.isBlank()) {
      return null;
    }

    try {
      // Limpieza de posibles bloques de código Markdown: ```json { ... } ```
      String cleanJson = rawResponse.trim();
      if (cleanJson.contains("```")) {
        // Extraemos solo lo que hay entre las primeras llaves si detectamos markdown
        // o usamos un regex para limpiar el envoltorio
        //
        // TODO: Si el LLM es un poco verboso y responde algo como:
        // "Aquí tienes el resultado:\njson\n{...}\n", el trim() inicial no eliminará 
        // el texto previo. Como la regex exige que los backticks estén al principio 
        // absoluto del string (^), fallará, no limpiará nada, y JsonParser lanzará una excepción.
        // Solución: Es más robusto buscar el primer { o [ y el último } o ], y extraer el substring.
        //
        cleanJson = cleanJson.replaceAll("^```(?:json)?\\s*", "")
                .replaceAll("\\s*```$", "");
      }

      return com.google.gson.JsonParser.parseString(cleanJson).getAsJsonObject();

    } catch (Exception e) {
      LOGGER.warn("Error en callChatModelAsJson (" + llmid + "), response: " + rawResponse, e);
      getConsole(DEFAULT_SUBCHANNEL).printSystemError("Error parseando JSON de " + llmid + ": " + e.getMessage());
      getConsole(DEFAULT_SUBCHANNEL).printSystemError("Contenido que falló: " + StringUtils.abbreviate(rawResponse, 100));
      return null;
    }
  }

  @Override
  public ChatModel createChatModel(String name) {
    name = name.toUpperCase();

    ModelParameters params = this.getModelParameters(name);
    return new ChatModelImpl(params);
  }

  @Override
  public ModelParameters getModelParameters(String name) {
    for (AgentService service : this.services.values()) {
      ModelParameters params = service.getModelParameters(name);
      if (params != null) {
        updateContextSize(params);
        return params;
      }
    }
    return null;
  }

  @Override
  public void installResource(String resPath) {
    AgentUtils.installResource(this.getPaths(), resPath);
  }

  /**
   * Carga un recurso de texto desde la carpeta data.
   *
   * @param resname Ruta relativa del recurso (ej:
   * "var/config/prompts/prompt-system-conversationmanager.md")
   * @return El contenido del archivo como String, o una cadena vacía si hay
   * error.
   */
  @Override
  public String getResourceAsString(String resname) {
    Path path = this.getPaths().getAgentPath(resname);
    try {
      if (Files.exists(path)) {
        return Files.readString(path, StandardCharsets.UTF_8);
      } else {
        getConsole(DEFAULT_SUBCHANNEL).printSystemError("Recurso no encontrado en data: " + resname);
        return "";
      }
    } catch (Exception e) {
      LOGGER.warn("Error leyendo recurso " + resname + ".", e);
      getConsole(DEFAULT_SUBCHANNEL).printSystemError("Error leyendo recurso " + resname + ": " + e.getMessage());
      return "";
    }
  }

  private void startAllServices() {
    for (AgentService service : this.services.values()) {
      if (!service.isRunning()) {
        service.start();
      }
    }
  }

  public void startService(String name) {
    AgentService service = this.services.get(name);
    if (service != null && !service.isRunning()) {
      service.start();
    }
  }

  @Override
  public AgentService getService(String name) {
    AgentService service = this.services.get(name);
    return service;
  }

  public String getCurrentSubchannel() {
    ReasoningServiceImpl reasoning = (ReasoningServiceImpl) this.getService(ReasoningServiceImpl.NAME);
    if (reasoning != null) {
      return reasoning.getCurrentSubchannel();
    }
    return DEFAULT_SUBCHANNEL;
  }
  
  private void updateContextSize(ModelParameters params) {
    if (!StringUtils.startsWith(params.providerUrl(), "https://openrouter.ai/api/v1")) {
      return;
    }

    if (this.openRouterModels == null) {
      try {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://openrouter.ai/api/v1/models"))
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
          this.openRouterModels = JsonParser.parseString(response.body()).getAsJsonObject();
        } else {
          System.err.println("Error al obtener modelos de OpenRouter: Código " + response.statusCode());
          return;
        }
      } catch (Exception e) {
        LOGGER.warn("No se ha podido obtener informacion de los modelos de OpenRouter", e);
        return;
      }
    }

    if (this.openRouterModels != null && this.openRouterModels.has("data")) {
      JsonArray data = this.openRouterModels.getAsJsonArray("data");
      String targetModelId = params.modelId();

      for (JsonElement element : data) {
        JsonObject modelObj = element.getAsJsonObject();
        if (modelObj.has("id") && StringUtils.equals(modelObj.get("id").getAsString(), targetModelId)) {
          if (modelObj.has("context_length")) {
            int contextSize = modelObj.get("context_length").getAsInt();
            params.setContextSize(contextSize);
          }
          break;
        }
      }
    }
  }

  @Override
  public int getConversationContextSize() {
    ReasoningServiceImpl reasoning = (ReasoningServiceImpl) this.getService(ReasoningServiceImpl.NAME);
    return reasoning.getModel().getContextSize();
  }

  @Override
  public void putUsersMessage(String subchannel, String text, SensorsService.SensorEventCallback callback) {
    SensorsService sensors = (SensorsService) this.getService(SensorsService.NAME);
    sensors.putEvent(USER_SENSOR_NAME, subchannel, text, PRIORITY_NORMAL, null, LocalDateTime.now(), callback);
  }

//  public void putUsersMessage(String text, SensorsService.SensorEventCallback callback) {
//    SensorsService sensors = (SensorsService) this.getService(SensorsService.NAME);
//    sensors.putEvent(USER_SENSOR_NAME, DEFAULT_SUBCHANNEL, text, PRIORITY_NORMAL, null, LocalDateTime.now(), callback);
//  }

  @Override
  public SensorInformation registerSensor(String channel, String label, SensorNature nature, String description) {
    SensorsService sensors = (SensorsService) this.getService(SensorsService.NAME);
    SensorInformation sensor = sensors.createSensorInformation(channel, label, nature, description);
    sensors.registerSensor(sensor);
    return sensor;
  }

  @Override
  public synchronized int estimateTokenCount(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
    if (this.tokenEstimator == null) {
      // Vamos a usar el TokenCountEstimator de OpenAI, ya que para otros muchos modelos
      // no disponemos de el. Se trata solo de una estimacion, y asumimos el error
      // que pueda haber en esta para otros modelos.
      this.tokenEstimator = new OpenAiTokenCountEstimator("gpt-4o");
    }
    int n = 0;
    if( messages!=null ) {
      n += this.tokenEstimator.estimateTokenCountInMessages(messages);
    }
    if( toolSpecifications!=null ) {    
      for (ToolSpecification toolSpecification : toolSpecifications) {
        String s = toolSpecification.toString();
        n += this.tokenEstimator.estimateTokenCountInText(s) + OVERHEAD_IN_ESTIMATE_TOOLS_TOKEN_COUNT;
      }
    }
    return n;
  }

}
