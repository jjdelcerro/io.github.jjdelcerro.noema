package io.github.jjdelcerro.chatagent.main;

import com.formdev.flatlaf.FlatDarkLaf;
import io.github.jjdelcerro.chatagent.lib.Agent;
import io.github.jjdelcerro.chatagent.ui.swing.AgentSwingInitializer;
import io.github.jjdelcerro.chatagent.ui.swing.MainChatPanel;
import javax.swing.*;
import java.io.File;

public class MainGUI {

  public static void main(String[] args) {
    FlatDarkLaf.setup(); // Estética moderna desde el inicio
    UIManager.put("Component.arc", 12); 
    UIManager.put("TextComponent.arc", 12);

    SwingUtilities.invokeLater(() -> {
      JFrame frame = new JFrame("ChatAgent v1.0");
      frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
      frame.setSize(900, 700);

      MainChatPanel chatPanel = new MainChatPanel();
      frame.add(chatPanel);
      frame.setLocationRelativeTo(null);
      frame.setVisible(true);

      AgentSwingInitializer.init(chatPanel.getController());
      
      // Carga asíncrona del agente
      Thread.ofVirtual().start(() -> {
        try {
          Agent agent = AgentUtils.init(new File("./data"));
          agent.start();
          SwingUtilities.invokeLater(() -> chatPanel.setAgent(agent));
        } catch (Exception e) {
          chatPanel.getController().printSystemError("Error fatal: " + e.getMessage());
        }
      });
    });
  }
}
