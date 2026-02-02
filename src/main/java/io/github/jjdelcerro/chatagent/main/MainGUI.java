package io.github.jjdelcerro.chatagent.main;

import io.github.jjdelcerro.chatagent.lib.Agent;
import io.github.jjdelcerro.chatagent.lib.AgentConsole;
import io.github.jjdelcerro.chatagent.lib.AgentLocator;
import io.github.jjdelcerro.chatagent.lib.AgentSettings;
import io.github.jjdelcerro.chatagent.ui.AgentUILocator;
import io.github.jjdelcerro.chatagent.ui.AgentUISettings;
import io.github.jjdelcerro.chatagent.ui.swing.AgentSwingConsoleImpl;
import io.github.jjdelcerro.chatagent.ui.swing.AgentSwingInitializer;
import io.github.jjdelcerro.chatagent.ui.swing.AgentSwingSettingsImpl;
import io.github.jjdelcerro.chatagent.ui.swing.MainChatPanel;

import javax.swing.*;
import java.io.File;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;

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
        AgentUtils.initSettings(console,dataFolder);
        AgentUtils.initSettingsUI(console,dataFolder);
        AgentUtils.askSettings(console,dataFolder);

        // 4. Carga de ajustes y conexión a DB
        File settingsFile = new File(dataFolder, "settings.properties");
        AgentSettings settings = AgentLocator.getAgentManager().createSettings();
        settings.load(settingsFile);

        File dbfile = new File(dataFolder, "memory");
        console.println("Conectando a base de datos: " + dbfile.getAbsolutePath());
        Connection conn = DriverManager.getConnection("jdbc:h2:" + dbfile.getAbsolutePath(), "sa", "");

        // 5. Inicialización del Agente real
        // Se inicializa contra la bootstrapConsole para que veamos la carga de herramientas
        Agent agent = AgentLocator.getAgentManager().createAgent(
                conn,
                dataFolder,
                settings,
                console
        );

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
