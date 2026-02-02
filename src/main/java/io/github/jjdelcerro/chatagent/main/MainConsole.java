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
import io.github.jjdelcerro.chatagent.ui.AgentUILocator;
import io.github.jjdelcerro.chatagent.ui.console.AgentConsoleInitializer;

public class MainConsole {

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
      AgentConsoleInitializer.init(terminal, lineReader);
      
      // Preparar Directorios
      File dataFolder = new File(DATA_FOLDER);
      Files.createDirectories(dataFolder.toPath());
      
      // Inicializar la consola y settings del agente
      AgentConsole console = AgentUILocator.getAgentUIManager().createConsole();
      AgentUtils.initSettings(console, dataFolder);
      AgentUtils.initSettingsUI(console, dataFolder);
      AgentUtils.askSettings(console,dataFolder);

      // Cargamos los settings del agente
      File settingsFile = new File(dataFolder, "settings.properties");
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
            AgentUILocator.getAgentUIManager().createSettings(agent).showWindow();            
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
