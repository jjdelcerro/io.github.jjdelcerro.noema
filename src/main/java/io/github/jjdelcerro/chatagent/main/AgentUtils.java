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
import java.nio.file.Files;
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
  
  public static Agent init(File dataFolder) {
    try {
      // Preparar Directorios
      Files.createDirectories(dataFolder.toPath());
      
      // Inicializar la consola y settings del agente
      AgentConsole console = AgentUILocator.getAgentUIManager().createConsole();
      console.printSystemLog("Iniciando Agente de Memoria Híbrida Determinista...");
      AgentUtils.initSettings(console, dataFolder);
      AgentUtils.initSettingsUI(console, dataFolder);
      AgentUtils.askSettings(console,dataFolder);

      // Iniciar el servidor web de H2 (Consola)
      Server webServer = Server.createWebServer("-webPort", "8082", "-webAllowOthers").start();
      console.printSystemLog("H2 Web Console activa en: " + webServer.getURL());      
      
      // Cargamos los settings del agente
      File settingsFile = new File(dataFolder, "settings.properties");
      AgentSettings settings = AgentLocator.getAgentManager().createSettings();
      settings.load(settingsFile);

      // Conexión a Base de Datos (H2)
      File memoryFile = new File(dataFolder, "memory").getCanonicalFile();
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
      File servicesFile = new File(dataFolder, "service").getCanonicalFile();
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
      console.printSystemLog("Conectado a Base de Conocimiento: " + memoryFile.getAbsolutePath());
      @SuppressWarnings("unused")
              Connection servicesConn = servicesDatabase.get();
      console.printSystemLog("Conectado a Base de datos de servicio: " + servicesFile.getAbsolutePath());
      Runtime.getRuntime().addShutdownHook(new Thread(() -> {
        try {
          if (memoryConn != null) memoryConn.close();
          if (servicesConn != null) servicesConn.close();
        } catch (SQLException e) { /* ignore */ }
      }));

      Agent agent = AgentLocator.getAgentManager().createAgent(
              memoryDatabase,
              servicesDatabase,
              dataFolder,
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
      if( !service.canStart(settings) ) {
        return false;
      }
    }
    return true;
  }

  private static void initSettingsUI(AgentConsole console, File dataFolder) {
    File settingsUIFile = new File(dataFolder, "settingsui.json");
    if (settingsUIFile.exists()) {
      return;
    }
    try (java.io.InputStream is = AgentUtils.class.getResourceAsStream("settingsui.json")) {
      if (is == null) {
        console.printSystemError("No se pudo encontrar el recurso settingsui.json");
        return;
      }
      Files.copy(is, settingsUIFile.toPath());
      console.printSystemLog("Configuración de UI inicializada en: " + settingsUIFile.getAbsolutePath());
    } catch (Exception e) {
      console.printSystemError("Error al inicializar settingsui.json: " + e.getMessage());
    }    
  }

  private static void askSettings(AgentConsole console, File dataFolder) {
    File settingsFile = new File(dataFolder, "settings.properties");
    if (!AgentUtils.areSettingsValid(console,settingsFile)) {
        console.printSystemLog("Configuración incompleta. Abriendo asistente...");
        // Usamos el constructor que creará un FakeAgent para la UI inicial
        AgentUISettings settingsUI = AgentUILocator.getAgentUIManager().createSettings(dataFolder, console);
        settingsUI.showWindow();

        if (!AgentUtils.areSettingsValid(console,settingsFile)) {
            console.printSystemError("Configuración cancelada. Saliendo de la aplicacion.");
            System.exit(1);
        }
    }        
  }
  
  private static void initSettings(AgentConsole console, File dataFolder) {
    File settingsFile = new File(dataFolder, "settings.properties");
    if (settingsFile.exists()) {
      return;
    }
    try (java.io.InputStream is = AgentUtils.class.getResourceAsStream("settings.properties")) {
      if (is == null) {
        console.printSystemError("No se pudo encontrar el recurso settings.properties");
        return;
      }
      Files.copy(is, settingsFile.toPath());
      console.printSystemLog("Configuración de UI inicializada en: " + settingsFile.getAbsolutePath());
    } catch (Exception e) {
      console.printSystemError("Error al inicializar settings.properties: " + e.getMessage());
    }
  }
  
}
