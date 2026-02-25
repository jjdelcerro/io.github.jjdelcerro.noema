package io.github.jjdelcerro.noema.lib.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import io.github.jjdelcerro.noema.lib.impl.services.conversation.ConversationService;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.langchain4j.model.openai.OpenAiChatModel;
import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.AgentActions;
import io.github.jjdelcerro.noema.lib.AgentConsole;
import io.github.jjdelcerro.noema.lib.AgentLocator;
import io.github.jjdelcerro.noema.lib.AgentSettings;
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
import static io.github.jjdelcerro.noema.lib.impl.services.conversation.ConversationService.CONVERSATION_MODEL_ID;
import static io.github.jjdelcerro.noema.lib.impl.services.memory.MemoryService.MEMORY_MODEL_ID;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
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

  private AgentConsole console;
  private final AgentSettings settings;
  private final AgentActions actions;
  private final SourceOfTruth sourceOfTruth;

  private final AgentAccessControl accessControl;

  private final ConnectionSupplier servicesDatabase;
  private final ConnectionSupplier memoryDatabase;

  private final Map<String, AgentService> services;
  
  private JsonObject openRouterModels = null;

  public AgentImpl(ConnectionSupplier memoryDatabase, ConnectionSupplier servicesDatabase, AgentSettings settings, AgentConsole console) {
    this.settings = settings;
    this.console = console;
    this.services = new LinkedHashMap<>();
    this.accessControl = new AgentAccessControlImpl(Paths.get(".").toAbsolutePath().normalize());
    this.actions = AgentLocator.getAgentManager().createActions();
    this.servicesDatabase = servicesDatabase;
    this.memoryDatabase = memoryDatabase;
    this.sourceOfTruth = SourceOfTruthImpl.from(this);

    this.accessControl.addNonReadablePath(this.getPaths().getAgentFolder());

    AgentManager manager = AgentLocator.getAgentManager();
    for (Supplier<AgentActions.AgentAction> actionFactory : manager.getActions()) {
      AgentActions.AgentAction action = actionFactory.get();
      action.setAgent(this);
      this.actions.addAction(action);
    }
  }

  @Override
  public void start() {
    AgentManager manager = AgentLocator.getAgentManager();
    for (AgentServiceFactory serviceFactory : manager.getServiceFactories()) {
      this.services.put(serviceFactory.getName(), serviceFactory.createService(this));
    }

    this.startAllServices();

    ConversationService conversation = (ConversationService) this.getService(ConversationService.NAME);
    for (AgentService service : this.services.values()) {
      if (service.canStart()) {
        List<AgentTool> tools = service.getTools();
        if (tools != null) {
          for (AgentTool tool : tools) {
            conversation.addTool(tool);
          }
          console.printSystemLog(service.getName() + " tools installed");
        }
      } else {
        console.printSystemLog(service.getName() + " tools NOT installed");
      }
    }

    console.printSystemLog("MemoryManager " + settings.getProperty(MEMORY_MODEL_ID));
    console.printSystemLog("ConversationManager " + settings.getProperty(CONVERSATION_MODEL_ID));

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
  public AgentConsole getConsole() {
    return this.console;
  }

  @Override
  public SourceOfTruth getSourceOfTruth() {
    return this.sourceOfTruth;
  }

  @Override
  public String processTurn(String input) {
    ConversationService conversation = (ConversationService) this.getService(ConversationService.NAME);
    return conversation.processTurn(input);
  }

  @Override
  public void putEvent(String status, String priority, String eventText) {
    ConversationService conversation = (ConversationService) this.getService(ConversationService.NAME);
    conversation.putEvent(status, priority, eventText);
  }

  @Override
  public AgentAccessControl getAccessControl() {
    return this.accessControl;
  }

  @Override
  public void setConsole(AgentConsole console) {
    this.console = console;
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
      console.printSystemError("Error en callChatModel (" + llmid + "): " + e.getMessage());
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
        cleanJson = cleanJson.replaceAll("^```(?:json)?\\s*", "")
                .replaceAll("\\s*```$", "");
      }

      return com.google.gson.JsonParser.parseString(cleanJson).getAsJsonObject();

    } catch (Exception e) {
      LOGGER.warn("Error en callChatModelAsJson (" + llmid + "), response: " + rawResponse, e);
      console.printSystemError("Error parseando JSON de " + llmid + ": " + e.getMessage());
      console.printSystemError("Contenido que falló: " + StringUtils.abbreviate(rawResponse, 100));
      return null;
    }
  }

  @Override
  public ChatModel createChatModel(String name) {
    name = name.toUpperCase();

    ModelParameters params = this.getModelParameters(name);
    OpenAiChatModel model = OpenAiChatModel.builder()
            .baseUrl(params.providerUrl())
            .apiKey(params.providerApiKey())
            .modelName(params.modelId())
            .temperature(params.temperature())
            .timeout(Duration.ofSeconds(180))
            .logRequests(false)
            .logResponses(false)
            .build();
    return new ChatModelImpl(model,params);
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
    String resourceBase = "/io/github/jjdelcerro/noema/lib/impl/resources/";
    Path targetPath = this.getPaths().getConfigFolder().resolve(resPath);
    if (!Files.exists(targetPath)) {
      try {
        Files.createDirectories(targetPath.getParent());

        try (InputStream is = getClass().getResourceAsStream(resourceBase + resPath)) {
          if (is != null) {
            Files.copy(is, targetPath, StandardCopyOption.REPLACE_EXISTING);
            console.printSystemLog("Recurso instalado en data " + resPath);
          } else {
            LOGGER.warn("Recurso no encontrado en el classpath " + resourceBase + resPath);
            console.printSystemError("Recurso no encontrado en el classpath: " + resourceBase + resPath);
          }
        }
      } catch (Exception e) {
        LOGGER.warn("No se ha podido instalar el recurso '" + resPath + "'.", e);
        console.printSystemError("Error al inicializar recurso " + resPath + ": " + e.getMessage());
      }
    }
  }

  /**
   * Carga un recurso de texto desde la carpeta data.
   *
   * @param resname Ruta relativa del recurso (ej:
   * "prompts/prompt-system-conversationmanager.md")
   * @return El contenido del archivo como String, o una cadena vacía si hay
   * error.
   */
  @Override
  public String getResourceAsString(String resname) {
    Path path = this.getPaths().getConfigFolder().resolve(resname);
    try {
      if (Files.exists(path)) {
        return Files.readString(path, StandardCharsets.UTF_8);
      } else {
        console.printSystemError("Recurso no encontrado en data: " + resname);
        return "";
      }
    } catch (Exception e) {
      LOGGER.warn("Error leyendo recurso " + resname + ".", e);
      console.printSystemError("Error leyendo recurso " + resname + ": " + e.getMessage());
      return "";
    }
  }

  public void startAllServices() {
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

  @Override
  public void showSession() {
    ConversationService conversation = (ConversationService) this.getService(ConversationService.NAME);
    if (conversation != null) {
      conversation.showSession();
    }
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
    ConversationService conversation = (ConversationService) this.getService(ConversationService.NAME);
    return conversation.getModel().getContextSize();
  }

}
