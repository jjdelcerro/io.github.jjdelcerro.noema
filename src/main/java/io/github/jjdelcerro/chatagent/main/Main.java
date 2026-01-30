package io.github.jjdelcerro.chatagent.main;

import dev.langchain4j.model.openai.OpenAiChatModel;
import io.github.jjdelcerro.chatagent.lib.impl.agent.ConversationAgent;
import io.github.jjdelcerro.chatagent.lib.impl.agent.MemoryManager;
import io.github.jjdelcerro.chatagent.lib.impl.persistence.SourceOfTruthImpl;
import io.github.jjdelcerro.chatagent.lib.impl.tools.file.FileExtractTextTool;
import io.github.jjdelcerro.chatagent.lib.impl.tools.file.FileFindTool;
import io.github.jjdelcerro.chatagent.lib.impl.tools.file.FileGrepTool;
import io.github.jjdelcerro.chatagent.lib.impl.tools.file.FileMkdirTool;
import io.github.jjdelcerro.chatagent.lib.impl.tools.file.FilePatchTool;
import io.github.jjdelcerro.chatagent.lib.impl.tools.file.FileReadTool;
import io.github.jjdelcerro.chatagent.lib.impl.tools.file.FileSearchAndReplaceTool;
import io.github.jjdelcerro.chatagent.lib.impl.tools.file.FileWriteTool;
import io.github.jjdelcerro.chatagent.lib.impl.tools.mail.EmailService;
import io.github.jjdelcerro.chatagent.lib.persistence.SourceOfTruth;
import io.github.jjdelcerro.chatagent.lib.impl.tools.memory.LookupTurnTool;
import io.github.jjdelcerro.chatagent.lib.impl.tools.memory.SearchFullHistoryTool;
import io.github.jjdelcerro.chatagent.lib.impl.tools.telegram.TelegramTool;
import io.github.jjdelcerro.chatagent.lib.impl.tools.web.WebGetTikaTool;
import io.github.jjdelcerro.chatagent.lib.impl.tools.web.WebSearchTool;
import io.github.jjdelcerro.chatagent.lib.impl.utils.ConsoleOutputImpl;
import io.github.jjdelcerro.chatagent.lib.utils.ConsoleOutput;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

public class Main {

    // Configuración de rutas
    private static final String DATA_FOLDER = "./data";
    private static final String DB_PATH = DATA_FOLDER + "/memoria"; // H2 añade .mv.db

    private static final String API_URL = "https://openrouter.ai/api/v1";
    
    private static final String MODEL_NAME_LLAMA_3_3_70B = "meta-llama/llama-3.3-70b-instruct:free";
    private static final String MODEL_NAME_HERMES_3_LLAMA_3_1_405B = "nousresearch/hermes-3-llama-3.1-405b:free";
    private static final String MODEL_NAME_GLM_4_5_AIR = "z-ai/glm-4.5-air:free";
    private static final String MODEL_NAME_GLM_4_7 = "z-ai/glm-4.7";
    
    private static final String MODEL_NAME_MISTRAL_SMALL_3_1_24B = "mistralai/mistral-small-3.1-24b-instruct:free";
    private static final String MODEL_NAME_DEEPSEEK_R1T2 = "tngtech/deepseek-r1t2-chimera:free";
    private static final String MODEL_NAME_GPT_OSS_120B = "openai/gpt-oss-120b:free";
    private static final String MODEL_NAME_GPT_OSS_20B = "openai/gpt-oss-20b:free";
    private static final String MODEL_NAME_QWEN3_CODER = "qwen/qwen3-coder:free";
    private static final String MODEL_NAME_QWEN3_4B = "qwen/qwen3-4b:free";
    
//    private static final String MODEL_NAME_DEVSTRAL_2512 = "mistralai/devstral-2512:free";
//    private static final String MODEL_NAME_GEMINI_2_0_FLASH = "google/gemini-2.0-flash-exp:free";
    
    
    private static final String MEMORY_MANAGER_MODEL_NAME = MODEL_NAME_DEEPSEEK_R1T2;
    private static final String CONVERSATION_AGENT_MODEL_NAME = MODEL_NAME_GLM_4_5_AIR;
    
    public static void main(String[] args) {
        System.out.println(">>> Iniciando Agente de Memoria Híbrida Determinista...");

        // 1. Obtener API KEY
        String apiKey = System.getenv("LLM_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("ERROR: La variable de entorno LLM_API_KEY no está definida.");
            System.exit(1);
        }

        try {
            // Inicialización de JLine (Terminal y LineReader)
            Terminal terminal = TerminalBuilder.builder()
                .system(true)
                .build();
            
            LineReader lineReader = LineReaderBuilder.builder()
                .terminal(terminal)
                .build();
                
            ConsoleOutput console = ConsoleOutputImpl.create(terminal, lineReader);

            // 2. Preparar Directorios
            Files.createDirectories(Paths.get(DATA_FOLDER));
            

            // 3. Conexión a Base de Datos (H2)
            Connection conn = DriverManager.getConnection("jdbc:h2:" + DB_PATH, "sa", "");
            console.println("Conectado a Base de Conocimiento: " + DB_PATH);

            // 4. Inicializar Componentes de Persistencia
            SourceOfTruth sourceOfTruth = SourceOfTruthImpl.from(conn, new File(DATA_FOLDER), console);

            // 5. Inicializar Componentes Cognitivos
            MemoryManager memoryManager = new MemoryManager(
                sourceOfTruth, 
                OpenAiChatModel.builder()
                    .baseUrl(API_URL)
                    .apiKey(apiKey)
                    .modelName(MEMORY_MANAGER_MODEL_NAME) 
                    .temperature(0.0)
                    .timeout(Duration.ofSeconds(180))
                    .logRequests(false)  // Útil para ver qué se envía en el PoC
                    .logResponses(false)
                    .build(),
                console
            );
            
            ConversationAgent agent = new ConversationAgent(
                sourceOfTruth,
                memoryManager,
                OpenAiChatModel.builder()
                    .baseUrl(API_URL)
                    .apiKey(apiKey)
                    .modelName(CONVERSATION_AGENT_MODEL_NAME) 
                    .temperature(0.7)
                    .timeout(Duration.ofSeconds(180))
                    .logRequests(false)  // Util para ver que se envia en el PoC
                    .logResponses(false)
                    .build(),
                console,
                Paths.get(DATA_FOLDER)
            );

            // 6. Configurar Herramientas
            agent.addTool(new SearchFullHistoryTool(sourceOfTruth));
            agent.addTool(new LookupTurnTool(sourceOfTruth));  
            
            agent.addTool(new FileFindTool());
            agent.addTool(new FileGrepTool());
            agent.addTool(new FileReadTool());            
            agent.addTool(new FileWriteTool());
            agent.addTool(new FileSearchAndReplaceTool());
            agent.addTool(new FilePatchTool());
            agent.addTool(new FileMkdirTool());

            console.println("File tools installed");
            
            agent.addTool(new FileExtractTextTool());

            console.println("Extract text tools installed");

            agent.addTool(new WebGetTikaTool());
            
            console.println("Web access tools installed");

            String braveApiKey = System.getenv("BRAVE_SEARCH_API_KEY");
            if( StringUtils.isNotBlank(braveApiKey) ) {
                agent.addTool(new WebSearchTool(braveApiKey));
                console.println("Web search tools installed");
            } else {
                console.println("Web search tools NOT installed");
            }

            String telegramApiKey = System.getenv("TELEGRAM_API_KEY");
            long telegramAuthorizedChatId = NumberUtils.toLong(System.getenv("TELEGRAM_CHAT_ID"),0);
            if( StringUtils.isNotBlank(telegramApiKey) && telegramAuthorizedChatId>0 ) {
                agent.addTool(TelegramTool.create(telegramApiKey, telegramAuthorizedChatId, agent));            
                console.println("Telegram tools installed");
            } else {
                console.println("Telegram tools NOT installed");
            }
            
            String emailUser = System.getenv("EMAIL_USER");
            String emailPass = System.getenv("EMAIL_PASS");
            String myEmail = "joaquin@miempresa.com"; // FIXME: leerlo de algun lado
            if (StringUtils.isNotBlank(emailUser)) {
                EmailService.install(
                    agent,
                    "imap.gmail.com", // FIXME: leerlo de algun lado
                    "smtp.gmail.com", // FIXME: leerlo de algun lado
                    emailUser, 
                    emailPass, 
                    myEmail
                );
                console.println("EMail tools installed");
            } else {
                console.println("EMail tools NOT installed");
            }
            
            
            console.println("MemoryManager LLM "+MEMORY_MANAGER_MODEL_NAME);
            console.println("ConversationAgent LLM "+CONVERSATION_AGENT_MODEL_NAME);
            console.println("Sistema listo. Escribe '/quit' para terminar.");

            // 7. Bucle de Chat (REPL con JLine)
            while (true) {
                String input = null;
                try {
                    input = lineReader.readLine("\nUsuario: ");
                } catch (UserInterruptException e) {
                    break; // Ctrl+C
                } catch (EndOfFileException e) {
                    break; // Ctrl+D
                }

                if (input == null || "/quit".equalsIgnoreCase(input.trim())) {
                    break;
                }
                if (input.isBlank()) {
                    continue;
                }

                // Ejecución del turno completo
                String response = agent.processTurn(input);
                
                terminal.writer().println("Modelo:");
                terminal.writer().println(response);
                terminal.flush();                
            }

        } catch (Exception e) {
            System.err.println(">>> [ERR] " + e.getLocalizedMessage());
            e.printStackTrace();
        }
    }
}
