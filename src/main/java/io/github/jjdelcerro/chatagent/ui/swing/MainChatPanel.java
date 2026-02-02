package io.github.jjdelcerro.chatagent.ui.swing;

import io.github.jjdelcerro.chatagent.lib.Agent;
import io.github.jjdelcerro.chatagent.lib.AgentConsole;
import io.github.jjdelcerro.chatagent.ui.AgentUISettings;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.Window;

import javax.swing.text.DefaultCaret;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;

/**
 * Panel principal de la aplicación ChatAgent. Contiene el historial de
 * conversación, la entrada de texto y la lógica de transición desde la consola
 * de bootstrap.
 */
public class MainChatPanel extends JPanel {

  private final Agent agent;
  private final JTextPane chatArea;
  private final JTextArea inputArea;
  private final JButton sendBtn;
  private final JButton settingsBtn;

  // El controlador que gestiona el JTextPane como una AgentConsole
  private final AgentSwingConsoleController chatController;

  public MainChatPanel(Agent agent) {
    this.agent = agent;

    // 1. Inicialización de componentes de texto
    this.chatArea = new JTextPane();
    this.chatArea.setEditable(false);
    // Auto-scroll automático al insertar texto
    ((DefaultCaret) chatArea.getCaret()).setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);

    // Controlador desacoplado para la lógica de consola
    this.chatController = new AgentSwingConsoleController(this.chatArea);

    this.inputArea = new JTextArea(4, 40);
    this.inputArea.setLineWrap(true);
    this.inputArea.setWrapStyleWord(true);

    this.sendBtn = new JButton("Enviar");
    this.settingsBtn = new JButton("Ajustes");

    // 2. Configuración de la interfaz y eventos
    setupUI();
    initEvents();

    // 3. Traspaso de la consola inicial (Bootstrap) a esta nueva interfaz
    performHandover();
  }

  private void setupUI() {
    setLayout(new BorderLayout(10, 10));
    setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

    // Centro: Historial de chat con scroll
    add(new JScrollPane(chatArea), BorderLayout.CENTER);

    // Sur: Contenedor de entrada y botones
    JPanel southPanel = new JPanel(new BorderLayout(5, 5));

    // Entrada de texto con scroll (por si el usuario escribe mucho)
    southPanel.add(new JScrollPane(inputArea), BorderLayout.CENTER);

    // Panel de botones a la derecha
    JPanel btnPanel = new JPanel(new GridLayout(2, 1, 5, 5));
    btnPanel.add(sendBtn);
    btnPanel.add(settingsBtn);
    southPanel.add(btnPanel, BorderLayout.EAST);

    add(southPanel, BorderLayout.SOUTH);
  }

  /**
   * Realiza la transición desde la consola de bootstrap. Recupera el texto
   * previo, cierra la ventana antigua y actualiza el agente.
   */
  private void performHandover() {
    AgentConsole currentConsole = agent.getConsole();

    // Si venimos de la consola bootstrap (AgentSwingConsoleImpl), heredamos su historia
    if (currentConsole instanceof AgentSwingConsoleImpl bootstrap) {
      String history = bootstrap.getText();
      if (history != null && !history.isEmpty()) {
        chatController.print(history);
        chatController.println("\n--- Interfaz de Chat Activada ---\n");
      }

      // Cerramos la ventana de la consola inicial
      Window bootstrapWindow = SwingUtilities.getWindowAncestor(bootstrap);
      if (bootstrapWindow != null) {
        bootstrapWindow.dispose();
      }
    }

    // Establecemos el nuevo controlador como la consola oficial del agente
    agent.setConsole(this.chatController);
  }

  private void initEvents() {
    // Acción de envío por botón
    sendBtn.addActionListener(e -> handleSend());

    // Acción de envío por teclado (Ctrl + Enter)
    inputArea.addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ENTER && e.isControlDown()) {
          handleSend();
        }
      }
    });

    // Botón de configuración
    settingsBtn.addActionListener(e -> {
      // Abrimos el panel de ajustes (suponiendo que AgentSwingSettingsImpl 
      // ya está integrado en el UIManager o se instancia aquí)
      AgentUISettings settingsUI = AgentSwingLocator.getAgentUIManager().createSettings(agent);
      settingsUI.showWindow();
    });
  }

  /**
   * Gestiona el envío del mensaje y la respuesta asíncrona del agente.
   */
  private void handleSend() {
    String text = inputArea.getText().trim();
    if (text.isEmpty()) {
      return;
    }

    // Limpiar entrada y mostrar el mensaje del usuario en el chat
    inputArea.setText("");
    chatController.println("Usuario: " + text);

    // Deshabilitar UI durante el proceso cognitivo
    setControlsEnabled(false);

    // Ejecución en Virtual Thread (Java 21) para no bloquear la interfaz
    Thread.ofVirtual().start(() -> {
      try {
        // El agente razona y ejecuta herramientas
        String response = agent.processTurn(text);

        // Volvemos al EDT para actualizar la UI
        SwingUtilities.invokeLater(() -> {
          chatController.println("\nModelo:");
          chatController.println(response);
          chatController.println("--------------------------------------------------");
          setControlsEnabled(true);
        });

      } catch (Exception ex) {
        chatController.printerrorln("Error crítico procesando el turno: " + ex.getMessage());
        SwingUtilities.invokeLater(() -> setControlsEnabled(true));
      }
    });
  }

  private void setControlsEnabled(boolean enabled) {
    inputArea.setEnabled(enabled);
    sendBtn.setEnabled(enabled);
    settingsBtn.setEnabled(enabled);
    if (enabled) {
      inputArea.requestFocusInWindow();
    }
  }

  /**
   * Método de utilidad para envolver este panel en un JFrame principal.
   */
  public void showWindow() {
    JFrame frame = new JFrame("ChatAgent - Contertulio Personal");
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    frame.getContentPane().add(this);
    frame.setSize(1000, 700);
    frame.setLocationRelativeTo(null);
    frame.setVisible(true);
    inputArea.requestFocusInWindow();
  }
}
