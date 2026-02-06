package io.github.jjdelcerro.chatagent.main;

import io.github.jjdelcerro.chatagent.lib.Agent;
import io.github.jjdelcerro.chatagent.lib.AgentConsole;
import io.github.jjdelcerro.chatagent.lib.AgentLocator;
import io.github.jjdelcerro.chatagent.lib.AgentSettings;
import io.github.jjdelcerro.chatagent.lib.ConnectionSupplier;
import io.github.jjdelcerro.chatagent.ui.AgentUILocator;
import io.github.jjdelcerro.chatagent.ui.swing.AgentSwingInitializer;
import io.github.jjdelcerro.chatagent.ui.swing.MainChatPanel;

import javax.swing.*;
import java.io.File;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.function.Supplier;

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

    // 1. Preparar directorios
    File dataFolder = new File(DATA_FOLDER);
    if (!dataFolder.exists()) {
      Files.createDirectories(dataFolder.toPath());
    }

    // 2. Inicializar la consola y settings del agente
    AgentConsole console = AgentUILocator.getAgentUIManager().createConsole();
    AgentUtils.initSettings(console, dataFolder);
    AgentUtils.initSettingsUI(console, dataFolder);
    AgentUtils.askSettings(console, dataFolder);

    // 4. Carga de ajustes y conexión a DB
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
          throw new RuntimeException("Can't get memory database connection", ex);
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
          throw new RuntimeException("Can't get services database connection", ex);
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
            new File(DATA_FOLDER),
            settings,
            console
    );
    agent.start();

    // 6. Lanzar Interfaz Principal
    // El MainChatPanel se encargará de:
    // - Traspasar el texto de bootstrapConsole a su panel.
    // - Cerrar la ventana de bootstrapConsole.
    // - Hacer agent.setConsole(su_propio_controller).
    MainChatPanel mainPanel = new MainChatPanel(agent);
    mainPanel.showWindow();

    console.println("Sistema listo.");
  }

}
