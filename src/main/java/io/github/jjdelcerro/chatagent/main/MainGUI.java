package io.github.jjdelcerro.chatagent.main;

import io.github.jjdelcerro.chatagent.lib.Agent;
import io.github.jjdelcerro.chatagent.ui.swing.AgentSwingInitializer;
import io.github.jjdelcerro.chatagent.ui.swing.MainChatPanel;

import javax.swing.*;
import java.io.File;

/**
 * Punto de entrada para la versión gráfica (Swing) del ChatAgent.
 */
public class MainGUI {

  private static final String DATA_FOLDER = "./data";

  public static void main(String[] args) {
    // Establecer el Look & Feel del sistema para que no parezca una app de los 90
    try {
      UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
    } catch (Exception e) {
      // Ignorar y usar el por defecto
    }

    SwingUtilities.invokeLater(() -> {
      try {
        new MainGUI().start();
      } catch (Exception e) {
        JOptionPane.showMessageDialog(null,
                "Error fatal al iniciar: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        e.printStackTrace();
      }
    });
  }

  public void start() throws Exception {
    AgentSwingInitializer.init();
      
    Agent agent = AgentUtils.init(new File(DATA_FOLDER));
    agent.getConsole().printSystemLog("Sistema listo.");
    agent.start();

    MainChatPanel mainPanel = new MainChatPanel(agent);
    mainPanel.showWindow();
  }

}
