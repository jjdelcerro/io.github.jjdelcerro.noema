package io.github.jjdelcerro.chatagent.lib.impl;

import com.google.gson.JsonObject;
import dev.langchain4j.model.openai.OpenAiChatModel;
import io.github.jjdelcerro.chatagent.lib.Agent;
import io.github.jjdelcerro.chatagent.lib.AgentActions;
import static io.github.jjdelcerro.chatagent.lib.AgentActions.CHANGE_CONVERSATION_MODEL;
import static io.github.jjdelcerro.chatagent.lib.AgentActions.CHANGE_CONVERSATION_PROVIDER;
import static io.github.jjdelcerro.chatagent.lib.AgentActions.CHANGE_MEMORY_MODEL;
import static io.github.jjdelcerro.chatagent.lib.AgentActions.CHANGE_MEMORY_PROVIDER;
import io.github.jjdelcerro.chatagent.lib.AgentConsole;
import io.github.jjdelcerro.chatagent.lib.AgentLocator;
import io.github.jjdelcerro.chatagent.lib.AgentSettings;
import static io.github.jjdelcerro.chatagent.lib.AgentSettings.BRAVE_SEARCH_API_KEY;
import static io.github.jjdelcerro.chatagent.lib.AgentSettings.MEMORY_MODEL_ID;
import io.github.jjdelcerro.chatagent.lib.impl.persistence.SourceOfTruthImpl;
import io.github.jjdelcerro.chatagent.lib.impl.tools.events.PoolEventTool;
import io.github.jjdelcerro.chatagent.lib.impl.tools.file.FileExtractTextTool;
import io.github.jjdelcerro.chatagent.lib.impl.tools.file.FileFindTool;
import io.github.jjdelcerro.chatagent.lib.impl.tools.file.FileGrepTool;
import io.github.jjdelcerro.chatagent.lib.impl.tools.file.FileMkdirTool;
import io.github.jjdelcerro.chatagent.lib.impl.tools.file.FilePatchTool;
import io.github.jjdelcerro.chatagent.lib.impl.tools.file.FileReadTool;
import io.github.jjdelcerro.chatagent.lib.impl.tools.file.FileSearchAndReplaceTool;
import io.github.jjdelcerro.chatagent.lib.impl.tools.file.FileWriteTool;
import io.github.jjdelcerro.chatagent.lib.impl.tools.memory.LookupTurnTool;
import io.github.jjdelcerro.chatagent.lib.impl.tools.memory.SearchFullHistoryTool;
import io.github.jjdelcerro.chatagent.lib.impl.tools.web.LocationTool;
import io.github.jjdelcerro.chatagent.lib.impl.tools.web.TimeTool;
import io.github.jjdelcerro.chatagent.lib.impl.tools.web.WeatherTool;
import io.github.jjdelcerro.chatagent.lib.impl.tools.web.WebGetTikaTool;
import io.github.jjdelcerro.chatagent.lib.impl.tools.web.WebSearchTool;
import io.github.jjdelcerro.chatagent.lib.persistence.SourceOfTruth;
import java.io.File;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import org.apache.commons.lang3.StringUtils;
import io.github.jjdelcerro.chatagent.lib.AgentAccessControl;
import static io.github.jjdelcerro.chatagent.lib.AgentSettings.CONVERSATION_MODEL_ID;
import static io.github.jjdelcerro.chatagent.lib.AgentSettings.CONVERSATION_PROVIDER_API_KEY;
import static io.github.jjdelcerro.chatagent.lib.AgentSettings.CONVERSATION_PROVIDER_URL;
import static io.github.jjdelcerro.chatagent.lib.AgentSettings.DOCMAPPER_BASIC_MODEL_ID;
import static io.github.jjdelcerro.chatagent.lib.AgentSettings.DOCMAPPER_BASIC_PROVIDER_API_KEY;
import static io.github.jjdelcerro.chatagent.lib.AgentSettings.DOCMAPPER_BASIC_PROVIDER_URL;
import static io.github.jjdelcerro.chatagent.lib.AgentSettings.DOCMAPPER_REASONING_MODEL_ID;
import static io.github.jjdelcerro.chatagent.lib.AgentSettings.DOCMAPPER_REASONING_PROVIDER_API_KEY;
import static io.github.jjdelcerro.chatagent.lib.AgentSettings.DOCMAPPER_REASONING_PROVIDER_URL;
import static io.github.jjdelcerro.chatagent.lib.AgentSettings.MEMORY_PROVIDER_API_KEY;
import static io.github.jjdelcerro.chatagent.lib.AgentSettings.MEMORY_PROVIDER_URL;
import io.github.jjdelcerro.chatagent.lib.SchedulerService;
import io.github.jjdelcerro.chatagent.lib.impl.docmapper.DocumentServices;
import io.github.jjdelcerro.chatagent.lib.impl.tools.docmapper.DocumentSearchByCategoriesTool;
import io.github.jjdelcerro.chatagent.lib.impl.tools.docmapper.DocumentSearchBySumariesTool;
import io.github.jjdelcerro.chatagent.lib.impl.tools.docmapper.DocumentSearchTool;
import io.github.jjdelcerro.chatagent.lib.impl.tools.docmapper.GetDocumentStructureTool;
import io.github.jjdelcerro.chatagent.lib.impl.tools.docmapper.GetPartialDocumentTool;
import io.github.jjdelcerro.chatagent.lib.impl.tools.time.ScheduleAlarmTool;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author jjdelcerro
 */
public class AgentImpl implements Agent {

  private AgentConsole console;
  private final AgentSettings settings;
  private AgentActions actions;
  private final File dataFolder;
  private SourceOfTruth sourceOfTruth;
  private final ConversationManagerImpl conversationManager;
  private final MemoryManagerImpl memoryManager;

  private final AgentAccessControl accessControl;
  private final Connection connServices;
  private final SchedulerServiceImpl schedulerService;

  private final DocumentServices documentServices;

  public AgentImpl(Connection knowledgeDatabase, Connection servicesDatabase, File dataFolder, AgentSettings settings, AgentConsole console) {
    this.dataFolder = dataFolder;
    this.settings = settings;
    this.console = console;

    this.accessControl = new AgentAccessControlImpl(Paths.get(".").toAbsolutePath().normalize());

    this.actions = AgentLocator.getAgentManager().createActions();
    this.connServices = servicesDatabase;
    try {
      this.sourceOfTruth = SourceOfTruthImpl.from(knowledgeDatabase, this.dataFolder, this.console);
    } catch (SQLException ex) {
      throw new RuntimeException("Can't open database", ex);
    }

    this.schedulerService = new SchedulerServiceImpl(this);

    memoryManager = new MemoryManagerImpl(this);

    conversationManager = new ConversationManagerImpl(this);

    this.documentServices = new DocumentServices(this);
    this.documentServices.init();

    conversationManager.addTool(new PoolEventTool(this));

    conversationManager.addTool(new SearchFullHistoryTool(this));
    conversationManager.addTool(new LookupTurnTool(this));

    conversationManager.addTool(new ScheduleAlarmTool(this));

    conversationManager.addTool(new FileFindTool(this));
    conversationManager.addTool(new FileGrepTool(this));
    conversationManager.addTool(new FileReadTool(this));
    conversationManager.addTool(new FileWriteTool(this));
    conversationManager.addTool(new FileSearchAndReplaceTool(this));
    conversationManager.addTool(new FilePatchTool(this));
    conversationManager.addTool(new FileMkdirTool(this));

    console.println("File tools installed");

    conversationManager.addTool(new FileExtractTextTool(this));

    console.println("Extract text tools installed");

    conversationManager.addTool(new WebGetTikaTool(this));
    console.println("Web access tools installed");

    conversationManager.addTool(new WeatherTool(this));
    console.println("Weather tools installed");

    conversationManager.addTool(new LocationTool(this));
    console.println("Location tools installed");

    conversationManager.addTool(new TimeTool(this));
    console.println("Time tools installed");

    conversationManager.addTool(new DocumentSearchTool(this));
    conversationManager.addTool(new DocumentSearchByCategoriesTool(this));
    conversationManager.addTool(new DocumentSearchBySumariesTool(this));
    conversationManager.addTool(new GetDocumentStructureTool(this));
    conversationManager.addTool(new GetPartialDocumentTool(this));
    console.println("Document tools installed");

    String braveApiKey = this.settings.getProperty(BRAVE_SEARCH_API_KEY);
    if (StringUtils.isNotBlank(braveApiKey)) {
      conversationManager.addTool(new WebSearchTool(this));
      console.println("Web search tools installed");
    } else {
      console.println("Web search tools NOT installed");
    }

//    String telegramApiKey = System.getenv("TELEGRAM_API_KEY");
//    long telegramAuthorizedChatId = NumberUtils.toLong(System.getenv("TELEGRAM_CHAT_ID"),0);
//    if( StringUtils.isNotBlank(telegramApiKey) && telegramAuthorizedChatId>0 ) {
//        agent.addTool(TelegramTool.create(telegramApiKey, telegramAuthorizedChatId, agent));            
//        console.println("Telegram tools installed");
//    } else {
//        console.println("Telegram tools NOT installed");
//    }
//
//    String emailUser = System.getenv("EMAIL_USER");
//    String emailPass = System.getenv("EMAIL_PASS");
//    String myEmail = "joaquin@miempresa.com"; // FIXME: leerlo de algun lado
//    if (StringUtils.isNotBlank(emailUser)) {
//        EmailService.install(
//            agent,
//            "imap.gmail.com", // FIXME: leerlo de algun lado
//            "smtp.gmail.com", // FIXME: leerlo de algun lado
//            emailUser, 
//            emailPass, 
//            myEmail
//        );
//        console.println("EMail tools installed");
//    } else {
//        console.println("EMail tools NOT installed");
//    }
    this.actions.addAction(CHANGE_MEMORY_PROVIDER, (AgentSettings s) -> memoryManager.createChatLanguageModel(s));
    this.actions.addAction(CHANGE_MEMORY_MODEL, (AgentSettings s) -> memoryManager.createChatLanguageModel(s));
    this.actions.addAction(CHANGE_CONVERSATION_PROVIDER, (AgentSettings s) -> conversationManager.createChatLanguageModel(s));
    this.actions.addAction(CHANGE_CONVERSATION_MODEL, (AgentSettings s) -> conversationManager.createChatLanguageModel(s));

    this.schedulerService.start();

    console.println("MemoryManager " + settings.getProperty(MEMORY_MODEL_ID));
    console.println("ConversationManager " + settings.getProperty(CONVERSATION_MODEL_ID));

  }

  public MemoryManagerImpl getMemoryManager() {
    return this.memoryManager;
  }

  @Override
  public File getDataFolder() {
    return this.dataFolder;
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
    return this.conversationManager.processTurn(input);
  }

  @Override
  public void putEvent(String channel, String priority, String eventText) {
    this.conversationManager.putEvent(channel, priority, eventText);
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
  public Connection getServicesDatabase() {
    return this.connServices;
  }

  @Override
  public SchedulerService getSchedulerService() {
    return schedulerService;
  }

  /**
   * Realiza una llamada al modelo de lenguaje y devuelve la respuesta como
   * texto plano.
   */
  public String callChatModel(String llmid, String systemPrompt, String message, double temperature) {
    try {
      OpenAiChatModel model = this.createChatModel(llmid, temperature);

      List<dev.langchain4j.data.message.ChatMessage> messages = new ArrayList<>();
      if (org.apache.commons.lang3.StringUtils.isNotBlank(systemPrompt)) {
        messages.add(dev.langchain4j.data.message.SystemMessage.from(systemPrompt));
      }
      messages.add(dev.langchain4j.data.message.UserMessage.from(message));

      dev.langchain4j.model.output.Response<dev.langchain4j.data.message.AiMessage> response = model.generate(messages);
      return response.content().text();

    } catch (Exception e) {
      console.printerrorln("Error en callChatModel (" + llmid + "): " + e.getMessage());
      return null;
    }
  }

  /**
   * Realiza una llamada al modelo y parsea la respuesta como un JsonObject de
   * GSON. Incluye limpieza automática de bloques de código Markdown.
   */
  public JsonObject callChatModelAsJson(String llmid, String systemPrompt, String message, double temperature) {
    String rawResponse = callChatModel(llmid, systemPrompt, message, temperature);

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
      console.printerrorln("Error parseando JSON de " + llmid + ": " + e.getMessage());
      console.printerrorln("Contenido que falló: " + rawResponse);
      return null;
    }
  }

  public OpenAiChatModel createChatModel(String llmid, double temperature) {
    llmid = llmid.toUpperCase();
    String provider_url = null;
    String provider_apikey = null;
    String modelid = null;

    switch (llmid) {
      case "DOCMAPPER_REASONING":
        provider_url = DOCMAPPER_REASONING_PROVIDER_URL;
        provider_apikey = DOCMAPPER_REASONING_PROVIDER_API_KEY;
        modelid = DOCMAPPER_REASONING_MODEL_ID;
        break;
      case "DOCMAPPER_BASIC":
        provider_url = DOCMAPPER_BASIC_PROVIDER_URL;
        provider_apikey = DOCMAPPER_BASIC_PROVIDER_API_KEY;
        modelid = DOCMAPPER_BASIC_MODEL_ID;
        break;
      case "CONVERSATION":
        provider_url = CONVERSATION_PROVIDER_URL;
        provider_apikey = CONVERSATION_PROVIDER_API_KEY;
        modelid = CONVERSATION_MODEL_ID;
        break;
      case "MEMORY":
        provider_url = MEMORY_PROVIDER_URL;
        provider_apikey = MEMORY_PROVIDER_API_KEY;
        modelid = MEMORY_MODEL_ID;
        break;
    }
    OpenAiChatModel model = OpenAiChatModel.builder()
            .baseUrl(settings.getProperty(provider_url))
            .apiKey(settings.getProperty(provider_apikey))
            .modelName(settings.getProperty(modelid))
            .temperature(temperature)
            .timeout(Duration.ofSeconds(180))
            .logRequests(false)
            .logResponses(false)
            .build();
    return model;
  }

  public DocumentServices getDocumentServices() {
    return this.documentServices;
  }

  /**
   * Inicializa los recursos del sistema copiándolos del JAR a la carpeta data
   * si no existen previamente. Permite la personalización de prompts por el
   * usuario.
   */
  private void initResources() {
    String[] resources = new String[]{
      "prompts/prompt-compact-memorymanager.md",
      "prompts/prompt-system-conversationmanager.md",
      "prompts/docmapper/prompt-extract-structure-docmapper.md",
      "prompts/docmapper/prompt-sumary-docmapper.md",
      "prompts/docmapper/prompt-sumary-and-categorize-docmapper.md"
    };

    String resourceBase = "/io/github/jjdelcerro/chatagent/lib/impl/resources/";

    for (String resPath : resources) {
      Path targetPath = this.dataFolder.toPath().resolve(resPath);

      if (!Files.exists(targetPath)) {
        try {
          Files.createDirectories(targetPath.getParent());

          try (InputStream is = getClass().getResourceAsStream(resourceBase + resPath)) {
            if (is != null) {
              Files.copy(is, targetPath, StandardCopyOption.REPLACE_EXISTING);
              console.println("Recurso inicializado en data: " + resPath);
            } else {
              console.printerrorln("Error: Recurso no encontrado en el classpath: " + resourceBase + resPath);
            }
          }
        } catch (IOException e) {
          console.printerrorln("Error al inicializar recurso " + resPath + ": " + e.getMessage());
        }
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
  public String getResourceAsString(String resname) {
    Path path = this.dataFolder.toPath().resolve(resname);
    try {
      if (Files.exists(path)) {
        return Files.readString(path, StandardCharsets.UTF_8);
      } else {
        console.printerrorln("Recurso no encontrado en data: " + resname);
        return "";
      }
    } catch (IOException e) {
      console.printerrorln("Error leyendo recurso " + resname + ": " + e.getMessage());
      return "";
    }
  }

}
