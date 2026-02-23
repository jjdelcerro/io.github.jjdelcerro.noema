package io.github.jjdelcerro.noema.main;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.extras.FlatSVGUtils;
import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.AgentLocator;
import io.github.jjdelcerro.noema.lib.AgentSettings;
import io.github.jjdelcerro.noema.ui.swing.AgentSwingInitializer;
import io.github.jjdelcerro.noema.ui.swing.MainChatPanel;
import io.github.jjdelcerro.noema.ui.swing.OpenEditorAction;
import java.awt.Image;
import javax.swing.*;
import java.io.File;
import java.nio.file.Path;
import java.util.List;

public class MainGUI {

  public static void main(String[] args) {
    File dataFolder = Path.of("./data").normalize().toFile();
    
    // Cargamos los settings del agente
    File settingsFile = new File(dataFolder, "settings.properties");
    AgentSettings settings = AgentLocator.getAgentManager().createSettings();
    settings.load(settingsFile);
      
    FlatDarkLaf.setup(); // Estética moderna desde el inicio
    UIManager.put("Component.arc", 12); 
    UIManager.put("TextComponent.arc", 12);

    SwingUtilities.invokeLater(() -> {
      JFrame frame = new JFrame("ChatAgent v1.0");
      try {
          List<Image> icons = FlatSVGUtils.createWindowIconImages(
              MainGUI.class.getResource("/io/github/jjdelcerro/chatagent/ui/swing/app_icon.svg")
          );
          frame.setIconImages(icons);
      } catch (Exception e) {
          System.err.println("No se pudo cargar el icono de la aplicación: " + e.getMessage());
      }      
      frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
      frame.setSize(1024, 700);

      MainChatPanel chatPanel = new MainChatPanel(settings);
      frame.add(chatPanel);
      frame.setLocationRelativeTo(null);
      frame.setVisible(true);
      
      AgentSwingInitializer.init(chatPanel.getConsole());
      
      OpenEditorAction.registerAction("OPEN_MODELS_EDITOR","models.properties");
      OpenEditorAction.registerAction("OPEN_PROVIDERS_URL_EDITOR","providers_urls.properties");
      OpenEditorAction.registerAction("OPEN_PROVIDERS_APIKEY_EDITOR","providers_apikeys.properties");
      
      // Carga asíncrona del agente
      Thread.ofVirtual().start(() -> {
        try {
          Agent agent = AgentUtils.init(dataFolder);
          agent.start();
          SwingUtilities.invokeLater(() -> chatPanel.setAgent(agent));
        } catch (Exception e) {
          chatPanel.getConsole().printSystemError("Error fatal: " + e.getMessage());
        }
      });
    });
  }
}
