package io.github.jjdelcerro.noema.main;

import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.AgentConsole;
import io.github.jjdelcerro.noema.lib.AgentLocator;
import io.github.jjdelcerro.noema.lib.AgentManager;
import io.github.jjdelcerro.noema.lib.AgentPaths;
import io.github.jjdelcerro.noema.lib.AgentServiceFactory;
import io.github.jjdelcerro.noema.lib.ConnectionSupplier;
import io.github.jjdelcerro.noema.lib.services.reasoning.ReasoningService;
import io.github.jjdelcerro.noema.lib.settings.AgentSettings;
import io.github.jjdelcerro.noema.ui.AgentUILocator;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.h2.tools.Server;
import io.github.jjdelcerro.noema.lib.services.memory.MemoryConsolidationService;

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

      File memoryFile = paths.getDataFolder().resolve("episodic_memory").normalize().toAbsolutePath().toFile();
      File servicesFile = paths.getDataFolder().resolve("service").normalize().toAbsolutePath().toFile();

      // Iniciar el servidor web de H2 (Consola)
      FileUtils.writeStringToFile(
              paths.getConfigFolder().resolve(".h2.server.properties").normalize().toAbsolutePath().toFile(),
              "1=Memory database, turns and ConsolidateMemory|org.h2.Driver|jdbc\\:h2\\:" + memoryFile.getAbsolutePath() + "|sa\n"
              + "0=Services database, scheduler...|org.h2.Driver|jdbc\\:h2\\:" + servicesFile.getAbsolutePath() + "|sa\n"
              + "webAllowOthers=true\n"
              + "webPort=8082\n"
              + "webSSL=false\n",
               StandardCharsets.UTF_8
      );
      Server webServer = Server.createWebServer(
              "-webPort", settings.getPropertyAsString("debug/h2_webport"),
              "-properties", paths.getConfigFolder().normalize().toAbsolutePath().toString(),
              "-webAllowOthers"
      ).start();
      console.printSystemLog("H2 Web Console activa en: " + webServer.getURL());

      // Conexión a Base de Datos (H2)
      ConnectionSupplier memoryDatabase = new ConnectionSupplier() {
        @Override
        public Connection get() {
          try {
            return DriverManager.getConnection("jdbc:h2:" + memoryFile.getAbsolutePath() + ";AUTO_SERVER=TRUE", "sa", "");
          } catch (SQLException ex) {
            throw new RuntimeException("Can't get memory database connection", ex);
          }
        }

        @Override
        public String getProviderName() {
          return "H2";
        }
      };
      ConnectionSupplier servicesDatabase = new ConnectionSupplier() {
        @Override
        public Connection get() {
          try {
            return DriverManager.getConnection("jdbc:h2:" + servicesFile.getAbsolutePath() + ";AUTO_SERVER=TRUE", "sa", "");
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
      agentManager.getServiceFactory(MemoryConsolidationService.ID),
      agentManager.getServiceFactory(ReasoningService.ID)
    };
    for (AgentServiceFactory service : services) {
      if (!service.canStart(settings)) {
        return false;
      }
    }
    return true;
  }

  public static void disableConsoleLogging() {
    LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
    Configuration config = ctx.getConfiguration();
    LoggerConfig rootLogger = config.getRootLogger();

    // "STDOUT" es el nombre que tiene el appender de consola en log4j2.properties
    rootLogger.removeAppender("STDOUT");

    // Aplica los cambios a todos los loggers activos
    ctx.updateLoggers();
  }
}
