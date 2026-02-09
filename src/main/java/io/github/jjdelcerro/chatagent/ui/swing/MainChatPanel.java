package io.github.jjdelcerro.chatagent.ui.swing;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import io.github.jjdelcerro.chatagent.lib.Agent;
import io.github.jjdelcerro.chatagent.ui.AgentUILocator;
import java.awt.BorderLayout;
import java.awt.Insets;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import net.miginfocom.swing.MigLayout;

/**
 * Panel principal de Chat. Gestiona la disposición de los mensajes y la entrada
 * de usuario.
 */
public class MainChatPanel extends JPanel {

  private final JPanel chatHistory;
  private final JTextArea inputArea;
  private final JButton sendBtn;
  private final JButton settingsBtn;
  private final AgentSwingConsoleController controller;
  private Agent agent;

  public MainChatPanel() {
    setLayout(new BorderLayout());

    // 1. Área de Historial (Centro)
    // Usamos MigLayout para que los mensajes se apilen verticalmente a la izquierda
    chatHistory = new JPanel(new MigLayout("fillx, wrap 1, ins 10", "[grow, left]"));
    JScrollPane scrollPane = new JScrollPane(chatHistory);
    scrollPane.setBorder(null);
    // Mejora de velocidad de scroll
    scrollPane.getVerticalScrollBar().setUnitIncrement(16);
    add(scrollPane, BorderLayout.CENTER);

    // 2. Panel Inferior (Input + Botones)
    // Configuración de columnas: [Caja Texto (crece)][Botón Settings][Botón Enviar]
    JPanel bottomPanel = new JPanel(new MigLayout("fillx, ins 10", "[grow][][]"));

    inputArea = new JTextArea(3, 20);
    inputArea.setEnabled(false); // Desactivado hasta que el agente cargue
    inputArea.setLineWrap(true);
    inputArea.setWrapStyleWord(true);
    inputArea.putClientProperty("JComponent.arc", 20);
    inputArea.setMargin(new Insets(8, 10, 8, 10));

    settingsBtn = new JButton();
    settingsBtn.setEnabled(false);
    settingsBtn.putClientProperty("JButton.buttonType", "roundRect");
    settingsBtn.setToolTipText("Ajustes del Agente");

    sendBtn = new JButton("Cargando...");
    sendBtn.setEnabled(false);
    sendBtn.putClientProperty("JButton.buttonType", "roundRect");

    bottomPanel.add(new JScrollPane(inputArea), "growx");
    bottomPanel.add(settingsBtn, "aligny bottom, hmin 40, wmin 40");
    bottomPanel.add(sendBtn, "aligny bottom, hmin 40, wmin 40");
    add(bottomPanel, BorderLayout.SOUTH);

    // 3. Inicialización del controlador
    controller = new AgentSwingConsoleController(chatHistory);

    // 4. Eventos
    sendBtn.addActionListener(e -> handleSend());

    settingsBtn.addActionListener(e -> {
      if (agent != null) {
        AgentUILocator.getAgentUIManager().createSettings(agent).showWindow();
      }
    });
  }

  /**
   * Activa la interfaz una vez que el agente ha sido inicializado.
   */
  public void setAgent(Agent agent) {
    this.agent = agent;

    SwingUtilities.invokeLater(() -> {
      inputArea.setEnabled(true);
      sendBtn.setEnabled(true);
      settingsBtn.setEnabled(true);

      // Carga de Icono Enviar
      try {
        FlatSVGIcon sendIcon = new FlatSVGIcon("io/github/jjdelcerro/chatagent/ui/swing/send.svg", 24, 24);
        sendBtn.setIcon(sendIcon);
        sendBtn.setText("");
      } catch (Exception e) {
        sendBtn.setText("Enviar");
      }

      // Carga de Icono Ajustes
      try {
        FlatSVGIcon settingsIcon = new FlatSVGIcon("io/github/jjdelcerro/chatagent/ui/swing/settings.svg", 24, 24);
        settingsBtn.setIcon(settingsIcon);
        settingsBtn.setText("");
      } catch (Exception e) {
        settingsBtn.setText("S");
      }

      inputArea.requestFocusInWindow();
    });
  }

  public AgentSwingConsoleController getController() {
    return controller;
  }

  private void handleSend() {
    String text = inputArea.getText().trim();
    if (text.isEmpty() || agent == null) {
      return;
    }

    inputArea.setText("");
    controller.printUserMessage(text);

    // Ejecución en hilo virtual para no bloquear el Event Dispatch Thread de Swing
    Thread.ofVirtual().start(() -> {
      try {
        String response = agent.processTurn(text);
        controller.printModelResponse(response);
      } catch (Exception e) {
        controller.printSystemError("Error procesando respuesta: " + e.getMessage());
      }
    });
  }
}
