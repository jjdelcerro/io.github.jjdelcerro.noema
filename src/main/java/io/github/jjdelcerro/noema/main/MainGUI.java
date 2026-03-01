package io.github.jjdelcerro.noema.main;

import com.formdev.flatlaf.FlatDarkLaf;
import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.AgentLocator;
import io.github.jjdelcerro.noema.lib.AgentManager;
import io.github.jjdelcerro.noema.lib.settings.AgentSettings;
import io.github.jjdelcerro.noema.ui.swing.AgentSwingInitializer;
import io.github.jjdelcerro.noema.ui.swing.MainChatPanel;
import io.github.jjdelcerro.noema.ui.swing.OpenEditorAction;
import io.github.jjdelcerro.noema.ui.swing.WelcomePanel;
import static java.lang.System.exit;
import javax.swing.*;

public class MainGUI {

  public static void main(String[] args) {
    FlatDarkLaf.setup(); // Estética moderna desde el inicio
    UIManager.put("Component.arc", 12);
    UIManager.put("TextComponent.arc", 12);

    SwingUtilities.invokeLater(() -> {

      AgentManager manager = AgentLocator.getAgentManager();
      AgentSettings settings = manager.createSettings(null);

      WelcomePanel welcomePanel = new WelcomePanel(settings);
      boolean ok = welcomePanel.showWindow();

      if( !ok || !BootUtils.areSettingsValid(settings) ) {
        exit(1);        
      }
      MainChatPanel chatPanel = new MainChatPanel(settings);
      chatPanel.showWindow();

      AgentSwingInitializer.init(chatPanel.getConsole());

      OpenEditorAction.registerAction("OPEN_MODELS_EDITOR", "models.properties");
      OpenEditorAction.registerAction("OPEN_PROVIDERS_URL_EDITOR", "providers_urls.properties");
      OpenEditorAction.registerAction("OPEN_PROVIDERS_APIKEY_EDITOR", "providers_apikeys.properties");

      // Carga asíncrona del agente
      Thread.ofVirtual().start(() -> {
        try {
          Agent agent = BootUtils.init(settings);
          agent.start();
          SwingUtilities.invokeLater(() -> chatPanel.setAgent(agent));
        } catch (Exception e) {
          chatPanel.getConsole().printSystemError("Error fatal: " + e.getMessage());
        }
      });
    });
  }
}
