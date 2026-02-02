package io.github.jjdelcerro.chatagent.lib.impl;

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
import static io.github.jjdelcerro.chatagent.lib.AgentSettings.CONVERSATION_MODEL_ID;
import static io.github.jjdelcerro.chatagent.lib.AgentSettings.MEMORY_MODEL_ID;
import io.github.jjdelcerro.chatagent.lib.PathAccessControl;
import io.github.jjdelcerro.chatagent.lib.impl.persistence.SourceOfTruthImpl;
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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;

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

  private final PathAccessControl pathAccessControl;

  public AgentImpl(Connection conn, File dataFolder, AgentSettings settings, AgentConsole console) {
    this.dataFolder = dataFolder;
    this.settings = settings;
    this.console = console;

    this.pathAccessControl = new PathAccessControlImpl(Paths.get(".").toAbsolutePath().normalize());
    
    this.actions = AgentLocator.getAgentManager().createActions();
    
    try {
      this.sourceOfTruth = SourceOfTruthImpl.from(conn, this.dataFolder, this.console);
    } catch (SQLException ex) {
      throw new RuntimeException("Can't open database", ex);
    }

    memoryManager = new MemoryManagerImpl(this);

    conversationManager = new ConversationManagerImpl(this);

    conversationManager.addTool(new SearchFullHistoryTool(this));
    conversationManager.addTool(new LookupTurnTool(this));  

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
    
    
    String braveApiKey = this.settings.getProperty(BRAVE_SEARCH_API_KEY);
    if( StringUtils.isNotBlank(braveApiKey) ) {
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

    console.println("MemoryManager "+settings.getProperty(MEMORY_MODEL_ID));
    console.println("ConversationManager "+settings.getProperty(CONVERSATION_MODEL_ID));

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
  public PathAccessControl getPathAccessControl() {
    return this.pathAccessControl;
  }

  @Override
  public void setConsole(AgentConsole console) {
    this.console = console;
  }
  
}
