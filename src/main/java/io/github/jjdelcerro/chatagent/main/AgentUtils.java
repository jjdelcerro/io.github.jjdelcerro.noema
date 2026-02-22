package io.github.jjdelcerro.chatagent.main;

import io.github.jjdelcerro.chatagent.lib.Agent;
import io.github.jjdelcerro.chatagent.lib.AgentConsole;
import io.github.jjdelcerro.chatagent.lib.AgentLocator;
import io.github.jjdelcerro.chatagent.lib.AgentManager;
import io.github.jjdelcerro.chatagent.lib.AgentServiceFactory;
import io.github.jjdelcerro.chatagent.lib.AgentSettings;
import io.github.jjdelcerro.chatagent.lib.ConnectionSupplier;
import io.github.jjdelcerro.chatagent.ui.AgentUILocator;
import io.github.jjdelcerro.chatagent.ui.AgentUISettings;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.h2.tools.Server;

/**
 *
 * @author jjdelcerro
 */
@SuppressWarnings("UseSpecificCatch")
public class AgentUtils {

  private AgentUtils() {

  }

  public static Agent init(File agentFolder) {
    try {
      // Preparar Directorios
      agentFolder = agentFolder.getCanonicalFile();
      Files.createDirectories(agentFolder.toPath());

      File dataFolder = new File(agentFolder, "data");

      // Inicializar la consola y settings del agente
      AgentConsole console = AgentUILocator.getAgentUIManager().createConsole();
      console.printSystemLog("Iniciando Agente de Memoria narrativa trazable...");
      AgentUtils.initSettings(console, dataFolder);
      AgentUtils.askSettings(console, dataFolder);

      // Cargamos los settings del agente
      File settingsFile = new File(dataFolder, "settings.properties");
      AgentSettings settings = AgentLocator.getAgentManager().createSettings();
      settings.load(settingsFile);

      // Iniciar el servidor web de H2 (Consola)
      Server webServer = Server.createWebServer("-webPort", settings.getProperty("H2_WEBPORT"), "-webAllowOthers").start();
      console.printSystemLog("H2 Web Console activa en: " + webServer.getURL());

      // Conexión a Base de Datos (H2)
      File memoryFile = new File(dataFolder, "memory").getCanonicalFile();
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
      File servicesFile = new File(dataFolder, "service").getCanonicalFile();
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
              agentFolder,
              settings,
              console
      );
      return agent;
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  private static boolean areSettingsValid(AgentConsole console, File settingsFile) {
    if (!settingsFile.exists()) {
      return false;
    }
    AgentManager agentManager = AgentLocator.getAgentManager();
    AgentSettings settings = agentManager.createSettings();
    settings.load(settingsFile);

    AgentServiceFactory[] services = {
      agentManager.getServiceFactory("MEMORY"),
      agentManager.getServiceFactory("CONVERSATION")
    };
    for (AgentServiceFactory service : services) {
      if (!service.canStart(settings)) {
        return false;
      }
    }
    return true;
  }

  private static void askSettings(AgentConsole console, File dataFolder) {
    File settingsFile = new File(dataFolder, "settings.properties");
    if (!AgentUtils.areSettingsValid(console, settingsFile)) {
      console.printSystemLog("Configuración incompleta. Abriendo asistente...");
      // Usamos el constructor que creará un FakeAgent para la UI inicial
      AgentUISettings settingsUI = AgentUILocator.getAgentUIManager().createSettings(dataFolder, console);
      settingsUI.showWindow();

      if (!AgentUtils.areSettingsValid(console, settingsFile)) {
        console.printSystemError("Configuración cancelada. Saliendo de la aplicacion.");
        System.exit(1);
      }
    }
  }

  private static void installResource(AgentConsole console, File dataFolder, String resPath) {
    String resourceBase = "/io/github/jjdelcerro/chatagent/main/";
    Path targetPath = dataFolder.toPath().resolve(resPath);
    if (!Files.exists(targetPath)) {
      try {
        Files.createDirectories(targetPath.getParent());

        try (InputStream is = AgentUtils.class.getResourceAsStream(resourceBase + resPath)) {
          if (is != null) {
            Files.copy(is, targetPath, StandardCopyOption.REPLACE_EXISTING);
            console.printSystemLog("Recurso instalado en data " + resPath);
          } else {
            console.printSystemError("Error: Recurso no encontrado en el classpath: " + resourceBase + resPath);
          }
        }
      } catch (IOException e) {
        console.printSystemError("Error al inicializar recurso " + resPath + ": " + e.getMessage());
      }
    }
  }

  private static void initSettings(AgentConsole console, File dataFolder) {
    String[] resources = new String[]{
      "models.properties",
      "providers_apikeys.properties",
      "providers_urls.properties",
      "settings.properties",
      "settingsui.json"
    };
    for (String resPath : resources) {
      installResource(console, dataFolder, resPath);
    }
  }

}
