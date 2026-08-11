package io.github.jjdelcerro.noema.main;

import com.googlecode.lanterna.gui2.DefaultWindowManager;
import com.googlecode.lanterna.gui2.EmptySpace;
import com.googlecode.lanterna.gui2.MultiWindowTextGUI;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.Terminal;

import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.AgentLocator;
import io.github.jjdelcerro.noema.lib.AgentManager;
import io.github.jjdelcerro.noema.lib.settings.AgentSettings;
import io.github.jjdelcerro.noema.ui.AgentUILocator;
import io.github.jjdelcerro.noema.ui.lanterna.AgentLanternaConsoleImpl;
import io.github.jjdelcerro.noema.ui.lanterna.AgentLanternaManagerImpl;
import io.github.jjdelcerro.noema.ui.lanterna.MainLanternaWindow;

public class MainLanterna {

    public static void main(String[] args) {
        try {
            // 1. Inicializar pantalla de Lanterna
            Terminal terminal = new DefaultTerminalFactory().createTerminal();
            Screen screen = new TerminalScreen(terminal);
            screen.startScreen();

            MultiWindowTextGUI gui = new MultiWindowTextGUI(
                    screen, 
                    new DefaultWindowManager(), 
                    new EmptySpace()
            );

            // 2. Crear Ventana y Consola Lanterna
            MainLanternaWindow chatWindow = new MainLanternaWindow();
            AgentLanternaConsoleImpl console = new AgentLanternaConsoleImpl(chatWindow, gui);

            // 3. Registrar el UIManager
            AgentUILocator.registerAgentUIManager(new AgentLanternaManagerImpl(console));

            // 4. Cargar configuración e inicializar agente
            AgentManager manager = AgentLocator.getAgentManager();
            AgentSettings settings = manager.createSettings(null);
            settings.load();
            settings.setupSettings();

            Agent agent = BootUtils.init(settings);
            agent.start();

            // 5. Iniciar la interfaz GUI de Lanterna (Bloqueante)
            gui.addWindowAndWait(chatWindow);

            // 6. Al cerrar la ventana, apagar el agente limpiamente
            agent.stop();
            screen.stopScreen();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
