package io.github.jjdelcerro.noema.ui.lanterna;

import com.googlecode.lanterna.gui2.MultiWindowTextGUI;
import com.googlecode.lanterna.gui2.dialogs.MessageDialog;
import com.googlecode.lanterna.gui2.dialogs.MessageDialogButton;
import io.github.jjdelcerro.noema.lib.AgentConsole;

import java.io.IOException;

public class AgentLanternaConsoleImpl implements AgentConsole {

    private final MainLanternaWindow window;
    private final MultiWindowTextGUI gui;

    public AgentLanternaConsoleImpl(MainLanternaWindow window, MultiWindowTextGUI gui) {
        this.window = window;
        this.gui = gui;
    }

    public MultiWindowTextGUI getGui() {
        return gui;
    }

    @Override
    public boolean confirm(String message) {
        MessageDialogButton result = MessageDialog.showMessageDialog(
                gui,
                "Confirmación de Acción",
                message,
                MessageDialogButton.Yes,
                MessageDialogButton.No
        );
        return result == MessageDialogButton.Yes;
    }

    @Override
    public void printSystemLog(String message) {
        window.appendSystemLog(message);
        refreshUi();
    }

    @Override
    public void printSystemLog(String message, Format format) {
        printSystemLog(message);
    }

    @Override
    public void printSystemError(String message) {
        window.appendSystemLog("[ERR] " + message);
        refreshUi();
    }

    @Override
    public void printUserMessage(String message) {
        window.appendUserMessage(message);
        refreshUi();
    }

    @Override
    public void printModelResponse(String message) {
        window.appendModelResponse(message);
        refreshUi();
    }

    @Override
    public void printModelReasoning(String message) {
        window.appendSystemLog("[RAZONAMIENTO] " + message);
        refreshUi();
    }

    private void refreshUi() {
        try {
            gui.updateScreen();
        } catch (IOException ignored) {}
    }
}
