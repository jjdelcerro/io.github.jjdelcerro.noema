package io.github.jjdelcerro.noema.main;

import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.AgentConsole;
import io.github.jjdelcerro.noema.lib.AgentLocator;
import io.github.jjdelcerro.noema.lib.AgentManager;
import io.github.jjdelcerro.noema.lib.AgentPaths;
import io.github.jjdelcerro.noema.lib.AgentServiceFactory;
import io.github.jjdelcerro.noema.lib.ConnectionSupplier;
import io.github.jjdelcerro.noema.lib.services.memory.MemoryService;
import io.github.jjdelcerro.noema.lib.services.reasoning.ReasoningService;
import io.github.jjdelcerro.noema.lib.settings.AgentSettings;
import io.github.jjdelcerro.noema.ui.AgentUILocator;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import org.h2.tools.Server;

/**
 *
 * @author jjdelcerro
 */
@SuppressWarnings("UseSpecificCatch")
public class BootUtils {

  private BootUtils() {

  }

  public static Agent init(AgentSettings settings) {
    try {
      AgentConsole console = AgentUILocator.getAgentUIManager().createConsole();
      
      AgentPaths paths = settings.getPaths();
      System.setProperty("noema.log.path", paths.getLogFolder().toString());
      Configurator.reconfigure();     

      // Iniciar el servidor web de H2 (Consola)
      Server webServer = Server.createWebServer("-webPort", settings.getPropertyAsString("debug/h2_webport"), "-webAllowOthers").start();
      console.printSystemLog("H2 Web Console activa en: " + webServer.getURL());

      // Conexión a Base de Datos (H2)
      File memoryFile = paths.getDataFolder().resolve("memory").normalize().toAbsolutePath().toFile();
      ConnectionSupplier memoryDatabase = new ConnectionSupplier() {
        @Override
        public Connection get() {
          try {
            return DriverManager.getConnection("jdbc:h2:" + memoryFile.getAbsolutePath(), "sa", "");
          } catch (SQLException ex) {
            throw new RuntimeException("Can't get memory database connection", ex);
          }
        }

        @Override
        public String getProviderName() {
          return "H2";
        }
      };
      File servicesFile = paths.getDataFolder().resolve("service").normalize().toAbsolutePath().toFile();
      ConnectionSupplier servicesDatabase = new ConnectionSupplier() {
        @Override
        public Connection get() {
          try {
            return DriverManager.getConnection("jdbc:h2:" + servicesFile.getAbsolutePath(), "sa", "");
          } catch (SQLException ex) {
            throw new RuntimeException("Can't get services database connection", ex);
          }
        }

        @Override
        public String getProviderName() {
          return "H2";
        }
      };

      // Create databases and maintain server loaded
      @SuppressWarnings("unused")
      Connection memoryConn = memoryDatabase.get();
      console.printSystemLog("Conectado a Base de Conocimiento: " + memoryFile.getAbsolutePath());
      @SuppressWarnings("unused")
      Connection servicesConn = servicesDatabase.get();
      console.printSystemLog("Conectado a Base de datos de servicio: " + servicesFile.getAbsolutePath());
      
      Runtime.getRuntime().addShutdownHook(new Thread(() -> {
        try {
          if (memoryConn != null) {
            memoryConn.close();
          }
          if (servicesConn != null) {
            servicesConn.close();
          }
        } catch (SQLException e) {
          /* ignore */ }
      }));

      Agent agent = AgentLocator.getAgentManager().createAgent(
              memoryDatabase,
              servicesDatabase,
              settings,
              console
      );
      return agent;
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  public static boolean areSettingsValid(AgentSettings settings) {
    AgentManager agentManager = AgentLocator.getAgentManager();

    AgentServiceFactory[] services = {
      agentManager.getServiceFactory(MemoryService.ID),
      agentManager.getServiceFactory(ReasoningService.ID)
    };
    for (AgentServiceFactory service : services) {
      if (!service.canStart(settings)) {
        return false;
      }
    }
    return true;
  }

}
