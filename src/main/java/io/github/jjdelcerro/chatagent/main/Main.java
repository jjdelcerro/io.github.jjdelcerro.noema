package io.github.jjdelcerro.chatagent.main;

import io.github.jjdelcerro.chatagent.lib.Agent;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.Reference;
import org.jline.reader.UserInterruptException;
import org.jline.utils.InfoCmp.Capability;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.io.File;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import io.github.jjdelcerro.chatagent.lib.AgentConsole;
import io.github.jjdelcerro.chatagent.lib.AgentLocator;
import io.github.jjdelcerro.chatagent.lib.AgentSettings;
import io.github.jjdelcerro.chatagent.ui.AgentUIManager;
import io.github.jjdelcerro.chatagent.ui.AgentUISettings;
import io.github.jjdelcerro.chatagent.ui.console.AgentConsoleLocator;
import io.github.jjdelcerro.chatagent.ui.console.AgentConsoleManagerImpl;

public class Main {

  // Configuración de rutas
  private static final String DATA_FOLDER = "./data";

  public static void main(String[] args) {
    System.out.println(">>> Iniciando Agente de Memoria Híbrida Determinista...");

    try {
      // Inicialización de JLine (Terminal y LineReader)
      Terminal terminal = TerminalBuilder.builder()
              .system(true)
              .build();

      LineReader lineReader = LineReaderBuilder.builder()
              .terminal(terminal)
              .build();

      // Configuración de multilínea: Widget para insertar salto de línea (Alt+Enter)
      lineReader.getWidgets().put("insert-newline", () -> {
        lineReader.getBuffer().write("\n");
        return true;
      });

      // Mapeamos Alt+Enter (secuencias comunes para ESC+CR y ESC+NL)
      lineReader.getKeyMaps().get(LineReader.MAIN).bind(new Reference("insert-newline"), "\u001b\r", "\u001b\n");

      // Vinculación dinámica según terminfo (ej: Shift+Enter mapeado a kent) FIXME: Parece que no funciona
      String kent = terminal.getStringCapability(Capability.key_enter);
      if (kent != null) {
        lineReader.getKeyMaps().get(LineReader.MAIN).bind(new Reference("insert-newline"), kent);
      }

      // Preparar Directorios
      File dataFolder = new File(DATA_FOLDER);
      Files.createDirectories(dataFolder.toPath());
      
      initSettingsUI(dataFolder);

      // Inicializar la consola del agente
      AgentUIManager agentUIManager = AgentConsoleLocator.getAgentUIManager(terminal, lineReader);
      AgentConsole console = agentUIManager.createConsole();

      // Inicializar los settings del agente
      File settingsFile = new File(dataFolder, "settings.properties");
      if (!areSettingsValid(settingsFile)) {
          console.println(">>> No se ha encontrado configuracion previa. Iniciando asistente...");
          AgentUISettings settingsUI = agentUIManager.createSettings(settingsFile, console);
          settingsUI.show();
          if (!areSettingsValid(settingsFile)) {
            System.exit(1);
          }
      }
      
      AgentSettings settings = AgentLocator.getAgentManager().createSettings();
      settings.load(settingsFile);

      // Conexión a Base de Datos (H2)
      File dbfile = new File(dataFolder, "memory");
      Connection conn = DriverManager.getConnection("jdbc:h2:" + dbfile.getAbsolutePath(), "sa", "");
      console.println("Conectado a Base de Conocimiento: " + dbfile.getAbsolutePath());

      Agent agent = AgentLocator.getAgentManager().createAgent(
              conn,
              new File(DATA_FOLDER),
              settings,
              console
      );

      console.println("Sistema listo. Escribe '/quit' para terminar.");

      // Bucle de Chat (REPL con JLine)
      while (true) {
        String input = null;
        try {
          input = lineReader.readLine("\nUsuario: ");
        } catch (UserInterruptException e) {
          break; // Ctrl+C
        } catch (EndOfFileException e) {
          break; // Ctrl+D
        }

        if (input == null || input.isBlank()) {
          continue;
        }

        if ("/quit".equalsIgnoreCase(input.trim())) {
          break;
        }
        switch(input.trim().toLowerCase()) {
          case "/settings":
            showSettings(agent, agentUIManager);
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
  
  private static void showSettings(Agent agent, AgentUIManager agentUIManager) {
    AgentUISettings settingsUI = agentUIManager.createSettings(agent);
    settingsUI.show();
  }

  private static boolean areSettingsValid(File settingsFile) {
    if (!settingsFile.exists()) {
      return false;
    }
    AgentSettings settings = AgentLocator.getAgentManager().createSettings();
    settings.load(settingsFile);

    String[] criticalKeys = {
      AgentSettings.MEMORY_PROVIDER_URL,
      AgentSettings.MEMORY_PROVIDER_API_KEY,
      AgentSettings.MEMORY_MODEL_ID,
      AgentSettings.CONVERSATION_PROVIDER_URL,
      AgentSettings.CONVERSATION_PROVIDER_API_KEY,
      AgentSettings.CONVERSATION_MODEL_ID
    };

    for (String key : criticalKeys) {
      String val = settings.getProperty(key);
      if (val == null || val.trim().isEmpty()) {
        return false;
      }
    }
    return true;
  }

  private static void initSettingsUI(File dataFolder) {
    File settingsUIFile = new File(dataFolder, "settingsui.json");
    if (settingsUIFile.exists()) {
      return;
    }
    try (java.io.InputStream is = Main.class.getResourceAsStream("settingsui.json")) {
      if (is == null) {
        System.err.println(">>> [WARN] No se pudo encontrar el recurso settingsui.json");
        return;
      }
      Files.copy(is, settingsUIFile.toPath());
      System.out.println(">>> Configuración de UI inicializada en: " + settingsUIFile.getAbsolutePath());
    } catch (Exception e) {
      System.err.println(">>> [ERR] Error al inicializar settingsui.json: " + e.getMessage());
    }
  }
}
