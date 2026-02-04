package io.github.jjdelcerro.chatagent.main;

import io.github.jjdelcerro.chatagent.lib.AgentConsole;
import io.github.jjdelcerro.chatagent.lib.AgentLocator;
import io.github.jjdelcerro.chatagent.lib.AgentManager;
import io.github.jjdelcerro.chatagent.lib.AgentServiceFactory;
import io.github.jjdelcerro.chatagent.lib.AgentSettings;
import io.github.jjdelcerro.chatagent.ui.AgentUILocator;
import io.github.jjdelcerro.chatagent.ui.AgentUISettings;
import java.io.File;
import java.nio.file.Files;

/**
 *
 * @author jjdelcerro
 */
public class AgentUtils {
  
  private AgentUtils() {
    
  }
  
  public static boolean areSettingsValid(AgentConsole console, File settingsFile) {
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

  public static void initSettingsUI(AgentConsole console, File dataFolder) {
    File settingsUIFile = new File(dataFolder, "settingsui.json");
    if (settingsUIFile.exists()) {
      return;
    }
    try (java.io.InputStream is = AgentUtils.class.getResourceAsStream("settingsui.json")) {
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

  public static void askSettings(AgentConsole console, File dataFolder) {
    File settingsFile = new File(dataFolder, "settings.properties");
    if (!AgentUtils.areSettingsValid(console,settingsFile)) {
        console.println("Configuración incompleta. Abriendo asistente...");
        // Usamos el constructor que creará un FakeAgent para la UI inicial
        AgentUISettings settingsUI = AgentUILocator.getAgentUIManager().createSettings(dataFolder, console);
        settingsUI.showWindow();

        if (!AgentUtils.areSettingsValid(console,settingsFile)) {
            console.printerrorln("Configuración cancelada. Saliendo.");
            System.exit(1);
        }
    }        
  }
  
  public static void initSettings(AgentConsole console, File dataFolder) {
    File settingsFile = new File(dataFolder, "settings.properties");
    if (settingsFile.exists()) {
      return;
    }
    try (java.io.InputStream is = AgentUtils.class.getResourceAsStream("settings.json")) {
      if (is == null) {
        System.err.println(">>> [WARN] No se pudo encontrar el recurso settingsui.json");
        return;
      }
      Files.copy(is, settingsFile.toPath());
      System.out.println(">>> Configuración de UI inicializada en: " + settingsFile.getAbsolutePath());
    } catch (Exception e) {
      System.err.println(">>> [ERR] Error al inicializar settingsui.json: " + e.getMessage());
    }
  }
  
}
