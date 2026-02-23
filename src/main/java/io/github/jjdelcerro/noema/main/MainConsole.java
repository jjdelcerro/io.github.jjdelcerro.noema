package io.github.jjdelcerro.noema.main;

import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.AgentLocator;
import io.github.jjdelcerro.noema.lib.AgentManager;
import io.github.jjdelcerro.noema.lib.AgentPaths;
import io.github.jjdelcerro.noema.lib.AgentServiceFactory;
import io.github.jjdelcerro.noema.lib.AgentSettings;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.Reference;
import org.jline.reader.UserInterruptException;
import org.jline.utils.InfoCmp.Capability;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.io.File;
import io.github.jjdelcerro.noema.ui.AgentUILocator;
import io.github.jjdelcerro.noema.ui.AgentUISettings;
import io.github.jjdelcerro.noema.ui.console.AgentConsoleInitializer;
import java.nio.file.Path;

public class MainConsole {


  public static void main(String[] args) {
    Agent agent = null;
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
      
      
      AgentManager manager = AgentLocator.getAgentManager();
      AgentPaths paths = manager.createAgentPaths(Path.of("."));
      AgentSettings settings = manager.createSettings(paths);

      settings.setupSettings();
      
      if( !BootUtils.areSettingsValid(settings) ) {
        AgentUISettings settingsUI = AgentUILocator.getAgentUIManager().createSettings(settings);
        settingsUI.showWindow();        
        if( !BootUtils.areSettingsValid(settings) ) {
          System.err.println("Configuración cancelada. Saliendo de la aplicacion.");
          System.exit(1);        
        }
      }
      
      agent = BootUtils.init(settings);
      agent.start();
      agent.getConsole().printSystemLog("Sistema listo. Escribe '/quit' para terminar.");

      // Bucle de Chat (REPL con JLine)
      while (true) {
        String input;
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
        agent.getConsole().printModelResponse(response);
      }

    } catch (Exception e) {
      if( agent == null ) {
        System.err.println(">>> [ERR] " + e.getLocalizedMessage());
      } else {
        agent.getConsole().printSystemError(e.getLocalizedMessage());
      }
      e.printStackTrace();
    }
  }
  
  
}
