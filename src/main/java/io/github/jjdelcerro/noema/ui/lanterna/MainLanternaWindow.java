package io.github.jjdelcerro.noema.ui.lanterna;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.*;

import java.util.Arrays;

public class MainLanternaWindow extends BasicWindow {

    private final TextBox chatHistoryBox;
    private final TextBox inputArea;
    private final Label statusLabel;

    public MainLanternaWindow() {
        super("Noema Agent - Terminal UI");
        setHints(Arrays.asList(Hint.FULL_SCREEN)); // Ocupa toda la terminal

        Panel mainPanel = new Panel(new LinearLayout(Direction.VERTICAL));

        // 1. Historial de conversación (Read-Only)
        chatHistoryBox = new TextBox(new TerminalSize(80, 20), TextBox.Style.MULTI_LINE);
        chatHistoryBox.setReadOnly(true);
        mainPanel.addComponent(chatHistoryBox.withBorder(Borders.singleLine("Conversación")));

        // 2. Barra de estado
        statusLabel = new Label("Estado: Listo | Modelo: - | Tokens: 0");
        mainPanel.addComponent(statusLabel);

        // 3. Área de entrada de usuario
        Panel inputPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
        inputArea = new TextBox(new TerminalSize(70, 3));
        
        Button btnSend = new Button("Enviar", this::onSendPressed);
        
        inputPanel.addComponent(inputArea.withBorder(Borders.singleLine("Mensaje")));
        inputPanel.addComponent(btnSend);

        mainPanel.addComponent(inputPanel);

        setComponent(mainPanel);
    }

    private void onSendPressed() {
        String text = inputArea.getText().trim();
        if (!text.isEmpty()) {
            appendUserMessage(text);
            inputArea.setText("");
            // Disparar mensaje al Agente a través de la consola/puente
        }
    }

    public void appendUserMessage(String text) {
        chatHistoryBox.addLine("Usuario > " + text);
    }

    public void appendModelResponse(String text) {
        chatHistoryBox.addLine("Noema > " + text);
    }

    public void appendSystemLog(String text) {
        chatHistoryBox.addLine("[LOG] " + text);
    }
}
