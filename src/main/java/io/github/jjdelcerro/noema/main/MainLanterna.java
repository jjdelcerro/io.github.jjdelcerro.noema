package io.github.jjdelcerro.noema.main;

import com.googlecode.lanterna.gui2.DefaultWindowManager;
import com.googlecode.lanterna.gui2.EmptySpace;
import com.googlecode.lanterna.gui2.MultiWindowTextGUI;
import com.googlecode.lanterna.gui2.dialogs.MessageDialog;
import com.googlecode.lanterna.gui2.dialogs.MessageDialogBuilder;
import com.googlecode.lanterna.gui2.dialogs.MessageDialogButton;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.MouseCaptureMode;
import com.googlecode.lanterna.terminal.Terminal;

import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.AgentLocator;
import io.github.jjdelcerro.noema.lib.AgentManager;
import io.github.jjdelcerro.noema.lib.AgentPaths;
import io.github.jjdelcerro.noema.lib.settings.AgentSettings;
import io.github.jjdelcerro.noema.ui.AgentUILocator;
import io.github.jjdelcerro.noema.ui.AgentUISettings;
import io.github.jjdelcerro.noema.ui.lanterna.AgentLanternaConsoleImpl;
import io.github.jjdelcerro.noema.ui.lanterna.AgentLanternaManagerImpl;
import io.github.jjdelcerro.noema.ui.lanterna.LanternaUtils;
import io.github.jjdelcerro.noema.ui.lanterna.MainLanternaWindow;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;

@SuppressWarnings("UseSpecificCatch")
public class MainLanterna {

    @SuppressWarnings("CallToPrintStackTrace")
    public static void main(String[] args) {
        Screen screen = null;
        Agent agent = null;
        Path workspace = Path.of(".").toAbsolutePath().normalize();

        try {
            // 1. Inicializar pantalla e interfaz de Lanterna
            DefaultTerminalFactory factory = new DefaultTerminalFactory();
            factory.setMouseCaptureMode(MouseCaptureMode.CLICK_RELEASE_DRAG);

            Terminal terminal = factory.createTerminal();
            screen = new TerminalScreen(terminal);
            screen.startScreen();

            MultiWindowTextGUI gui = new MultiWindowTextGUI(
                    screen,
                    new DefaultWindowManager(),
                    new EmptySpace(LanternaUtils.COLOR_BASE_BG)
            );
            terminal.setCursorPosition(0, 0);

            // 2. Comprobar directamente la existencia de la carpeta .noema-agent
            Path agentFolder = workspace.resolve("." + AgentPaths.AGENT_FOLDER_NAME);

            if (!Files.exists(agentFolder)) {
              MessageDialog dialog = new MessageDialogBuilder()
                      .setTitle("Nuevo Espacio de Trabajo")
                      .setText("No se ha encontrado la carpeta de configuracion (.noema-agent) en:\n\n"
                              + workspace.toAbsolutePath().normalize() + "\n\n"
                              + "¿Desea inicializar un nuevo espacio de trabajo de Noema en esta carpeta?")
                      .addButton(MessageDialogButton.Yes)
                      .addButton(MessageDialogButton.No)
                      .build();
              dialog.setTheme(LanternaUtils.getMainTheme());
              MessageDialogButton confirm = dialog.showDialog(gui);              
                if (confirm != MessageDialogButton.Yes) {
                    return;
                }
            }
            Configurator.setRootLevel(Level.OFF);

            // 3. Crear Ventana y Consola Lanterna
            MainLanternaWindow chatWindow = new MainLanternaWindow(terminal);
            AgentLanternaConsoleImpl console = new AgentLanternaConsoleImpl(chatWindow, gui);

            gui.addWindow(chatWindow);
            terminal.clearScreen();

            // 4. Registrar UIManager
            AgentUILocator.registerAgentUIManager(new AgentLanternaManagerImpl(console));

            // 5. Instanciar AgentManager, AgentPaths y cargar configuracion
            AgentManager manager = AgentLocator.getAgentManager();
            AgentPaths paths = manager.createAgentPaths(workspace);
            AgentSettings settings = manager.createSettings(paths);

            settings.load();
            settings.setupSettings();

            if (!BootUtils.areSettingsValid(settings)) {
                AgentUISettings settingsUI = AgentUILocator.getAgentUIManager().createSettings(settings, console);
                settingsUI.showWindow();

                if (!BootUtils.areSettingsValid(settings)) {
                  MessageDialog dialogError = new MessageDialogBuilder()
                          .setTitle("Configuracion Incompleta")
                          .setText("La configuracion del agente sigue siendo incompleta.\nLa aplicacion se cerrara.")
                          .addButton(MessageDialogButton.OK)
                          .build();

                  dialogError.setTheme(LanternaUtils.getMainTheme());
                  dialogError.showDialog(gui);                  
                  return;
                }
            }

            // Registrar workspace en ajustes globales
            settings.setLastWorkspacePath(paths.getWorkspaceFolder().toString());

            // 6. Iniciar el motor del agente
            agent = BootUtils.init(settings);
            BootUtils.disableConsoleLogging();

            agent.start();
            chatWindow.setAgent(agent);

            // 7. Esperar a que el usuario cierre la ventana principal
            chatWindow.waitUntilClosed();

        } catch (Throwable e) {
            if (screen != null) {
                try {
                    screen.stopScreen();
                } catch (IOException ignored) {
                }
                screen = null;
            }
            System.err.println("Error fatal ejecutando Noema:");
            e.printStackTrace();
        } finally {
            if (agent != null) {
                try {
                    agent.stop();
                } catch (Exception ignored) {
                }
            }
            if (screen != null) {
                try {
                    screen.stopScreen();
                } catch (IOException ignored) {
                }
            }
        }
        System.exit(0);
    }
}
