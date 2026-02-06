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
import io.github.jjdelcerro.chatagent.lib.ConnectionSupplier;
import io.github.jjdelcerro.chatagent.ui.AgentUILocator;
import io.github.jjdelcerro.chatagent.ui.console.AgentConsoleInitializer;
import java.sql.SQLException;
import java.util.function.Supplier;
import org.h2.tools.Server;

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

      // Iniciar el servidor web de H2 (Consola)
      Server webServer = Server.createWebServer("-webPort", "8082", "-webAllowOthers").start();
      console.println("H2 Web Console activa en: " + webServer.getURL());      
      
      // Cargamos los settings del agente
      File settingsFile = new File(dataFolder, "settings.properties");
      AgentSettings settings = AgentLocator.getAgentManager().createSettings();
      settings.load(settingsFile);

      // Conexión a Base de Datos (H2)
      File memoryFile = new File(dataFolder, "memory");
      ConnectionSupplier memoryDatabase = new ConnectionSupplier() {
        @Override
        public Connection get() {
          try {
            return DriverManager.getConnection("jdbc:h2:" + memoryFile.getAbsolutePath(), "sa", "");
          } catch (SQLException ex) {
            throw new RuntimeException("Can't get memory database connection",ex);
          }
        }

        @Override
        public String getProviderName() {
          return "H2";
        }
      };
      File servicesFile = new File(dataFolder, "service");
      ConnectionSupplier servicesDatabase = new ConnectionSupplier() {
        @Override
        public Connection get() {
          try {
            return DriverManager.getConnection("jdbc:h2:" + servicesFile.getAbsolutePath(), "sa", "");
          } catch (SQLException ex) {
            throw new RuntimeException("Can't get services database connection",ex);
          }
        }

        @Override
        public String getProviderName() {
          return "H2";
        }
      };
      
      // Create databses and maintain server loaded
      @SuppressWarnings("unused")
      Connection memoryConn = memoryDatabase.get();
      console.println("Conectado a Base de Conocimiento: " + memoryFile.getAbsolutePath());
      @SuppressWarnings("unused")
      Connection servicesConn = servicesDatabase.get();
      console.println("Conectado a Base de datos de servicio: " + servicesFile.getAbsolutePath());
      Runtime.getRuntime().addShutdownHook(new Thread(() -> {
          try {
              if (memoryConn != null) memoryConn.close();
              if (servicesConn != null) servicesConn.close();
          } catch (SQLException e) { /* ignore */ }
      }));

      Agent agent = AgentLocator.getAgentManager().createAgent(
              memoryDatabase,
              servicesDatabase,
              new File(DATA_FOLDER),
              settings,
              console
      );
      agent.start();
      
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
