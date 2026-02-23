package io.github.jjdelcerro.noema.ui.swing;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.AgentConsole;
import io.github.jjdelcerro.noema.lib.AgentSettings;
import io.github.jjdelcerro.noema.lib.impl.services.conversation.ConversationService;
import io.github.jjdelcerro.noema.ui.AgentUILocator;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Insets;
import java.awt.event.KeyEvent;
import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import net.miginfocom.swing.MigLayout;

public class MainChatPanel extends JPanel {

  private final JPanel chatHistory;
  private final JTextArea inputArea;
  private final JButton btnSend, btnStop, btnTools, btnInsert;
  private final JLabel lblModelInfo, lblTokens, lblTimer;
  private final JButton settingsBtn, btnSave, btnCopy;

  private final AgentConsole consoleController;
  private Agent agent;
  private AgentSettings settings;
  private Timer thinkingTimer;
  private long thinkingStartTime;

  public MainChatPanel(AgentSettings settings) {
    this.settings = settings;
    setLayout(new BorderLayout());

    // --- 1. PANELES LATERALES (Existentes) ---    
    // --- Panel Izquierdo (Settings) ---
    JPanel leftBar = new JPanel(new MigLayout("wrap 1, ins 10, filly", "[]", "[][grow]"));
    leftBar.setBackground(Color.DARK_GRAY);
    settingsBtn = createSidebarButton(); // <--- Usamos una función de ayuda
    leftBar.add(settingsBtn, "w 28!, h 28!"); // Un pelín más grandes para que respiren

    // --- Panel Derecho (Guardar y Copiar) ---
    JPanel rightBar = new JPanel(new MigLayout("wrap 1, ins 10, filly", "[]", "[][][grow]"));
    rightBar.setBackground(Color.DARK_GRAY);
    btnSave = createSidebarButton();
    btnCopy = createSidebarButton();
    rightBar.add(btnSave, "w 28!, h 28!");
    rightBar.add(btnCopy, "w 28!, h 28!");

    // --- 2. HISTORIAL DE CHAT ---
    chatHistory = new JPanel(new MigLayout("fillx, wrap 1, ins 1 10 1 10", "[grow, left]"));
    chatHistory.setBackground(Color.DARK_GRAY);
    JScrollPane historyScroll = new JScrollPane(chatHistory);
    historyScroll.setBorder(null);
    historyScroll.getVerticalScrollBar().setUnitIncrement(16);

    JPanel centerContainer = new JPanel(new BorderLayout());
    centerContainer.add(leftBar, BorderLayout.WEST);
    centerContainer.add(historyScroll, BorderLayout.CENTER);
    centerContainer.add(rightBar, BorderLayout.EAST);
    add(centerContainer, BorderLayout.CENTER);

    // --- 3. CÁPSULA DE ENTRADA (Estilo Gemini) ---
    JPanel bottomPanel = new JPanel(new MigLayout("fillx, ins 10 15 15 15", "[grow]"));
    bottomPanel.setBackground(Color.DARK_GRAY);

    // Contenedor principal redondeado
    JPanel inputCapsule = new JPanel(new MigLayout("fillx, wrap 1, ins 8 12 8 12", "[grow]"));
    inputCapsule.setBackground(new Color(45, 45, 45)); // Un gris ligeramente distinto o el mismo
    inputCapsule.setBorder(new com.formdev.flatlaf.ui.FlatLineBorder(
            new Insets(1, 1, 1, 1), UIManager.getColor("Component.borderColor"), 1, 25));

    // Area de texto (Arriba en la cápsula)
    inputArea = new JTextArea(3, 20);
    inputArea.setLineWrap(true);
    inputArea.setWrapStyleWord(true);
    inputArea.setOpaque(false);
    inputArea.setBorder(null);
    inputArea.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));

    JScrollPane inputScroll = new JScrollPane(inputArea);
    inputScroll.setOpaque(false);
    inputScroll.getViewport().setOpaque(false);
    inputScroll.setBorder(null);
    inputCapsule.add(inputScroll, "growx, hmin 60");

    // BARRA DE CONTROL (Abajo en la cápsula)
    // [Herramientas] --- [Metadata] --- [Timer] [+] [Send/Stop]
    JPanel controlBar = new JPanel(new MigLayout("fillx, ins 0", "[pref][grow, center][pref][pref][pref]"));
    controlBar.setOpaque(false);

    // 1. Botón Herramientas (Izq)
    btnTools = createCapsuleButton("tools.svg", "Herramientas");
    controlBar.add(btnTools);

    // 2. Metadata (Centro)
    JPanel metaDataPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
    metaDataPanel.setOpaque(false);
    lblModelInfo = new JLabel("Cargando modelo...");
    lblModelInfo.setForeground(Color.GRAY);
    lblModelInfo.setFont(new Font(Font.SANS_SERIF, Font.ITALIC, 11));
    lblTokens = new JLabel("0 + 0");
    lblTokens.setForeground(Color.GRAY);
    lblTokens.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
    metaDataPanel.add(lblModelInfo);
    metaDataPanel.add(new JLabel("|")).setForeground(Color.DARK_GRAY);
    metaDataPanel.add(lblTokens);
    controlBar.add(metaDataPanel, "growx");

    // 3. Timer (Oculto por defecto)
    lblTimer = new JLabel("0.0s");
    lblTimer.setForeground(UIManager.getColor("ProgressBar.foreground"));
    lblTimer.setFont(new Font(Font.MONOSPACED, Font.BOLD, 12));
    lblTimer.setVisible(false);
    controlBar.add(lblTimer, "gapright 10");

    // 4. Botón Insertar (+)
    btnInsert = createCapsuleButton("insert.svg", "Insertar");
    controlBar.add(btnInsert);

    // 5. Botón Enviar / Stop (Superpuestos)
    btnSend = createCapsuleButton("send.svg", "Enviar (Ctrl+Enter)");
    btnStop = createCapsuleButton("stop.svg", "Detener"); // Necesitarás stop.svg
    btnStop.setVisible(false);

    // Usamos un panel pequeño para que ocupen el mismo sitio
    JPanel actionStack = new JPanel(new BorderLayout());
    actionStack.setOpaque(false);
    actionStack.add(btnSend, BorderLayout.CENTER);
    actionStack.add(btnStop, BorderLayout.NORTH); // El layout se encarga de visibilidad
    controlBar.add(actionStack);

    inputCapsule.add(controlBar, "growx, gaptop 5");
    bottomPanel.add(inputCapsule, "growx");
    add(bottomPanel, BorderLayout.SOUTH);

    // --- 4. LÓGICA Y EVENTOS ---
    consoleController = new AgentSwingConsoleControllerUsingMultipleJTextPane(chatHistory);
    initIcons();
    setupActions();
    setupThinkingTimer();
  }

  private void initIcons() {
    try {
      String iconPath = "io/github/jjdelcerro/chatagent/ui/swing/";
      settingsBtn.setIcon(new FlatSVGIcon(iconPath + "settings.svg", 16, 16));
      btnSave.setIcon(new FlatSVGIcon(iconPath + "save.svg", 16, 16));
      btnCopy.setIcon(new FlatSVGIcon(iconPath + "copy.svg", 16, 16));
    } catch (Exception ignored) {
    }
  }

  private JButton createCapsuleButton(String iconName, String tooltip) {
    JButton btn = new JButton();
    try {
      btn.setIcon(new FlatSVGIcon("io/github/jjdelcerro/chatagent/ui/swing/" + iconName, 8, 8));
    } catch (Exception e) {
      btn.setText(iconName.replace(".svg", ""));
    }
    btn.setToolTipText(tooltip);
    btn.putClientProperty("JButton.buttonType", "roundRect");
    // Márgenes internos para hacerlo alargado (oblongo)
    btn.setMargin(new Insets(2, 12, 2, 12));
    return btn;
  }

  private void setupActions() {
    // Ctrl+Enter para enviar
    KeyStroke ctrlEnter = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, java.awt.event.InputEvent.CTRL_DOWN_MASK);
    inputArea.getInputMap(JComponent.WHEN_FOCUSED).put(ctrlEnter, "send");
    inputArea.getActionMap().put("send", new AbstractAction() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent e) {
        handleSend();
      }
    });

    btnSend.addActionListener(e -> handleSend());
    settingsBtn.addActionListener(e -> {
      if (agent != null) {
        AgentUILocator.getAgentUIManager().createSettings(agent).showWindow();
      }
    });
  }

  private void setupThinkingTimer() {
    thinkingTimer = new Timer(100, e -> {
      double elapsed = (System.currentTimeMillis() - thinkingStartTime) / 1000.0;
      lblTimer.setText(String.format("%.1fs", elapsed));
    });
  }

  private void handleSend() {
    if (!inputArea.isEnabled()) {
      return;
    }

    String text = inputArea.getText().trim();
    if (text.isEmpty() || agent == null) {
      return;
    }

    inputArea.setText("");
    consoleController.printUserMessage(text);

    startThinking();

    Thread.ofVirtual().start(() -> {
      try {
        String response = agent.processTurn(text);
        consoleController.printModelResponse(response);
      } catch (Exception e) {
        consoleController.printSystemError("Error: " + e.getMessage());
      } finally {
        stopThinking();
        updateMetadata();
      }
    });
  }

  private void startThinking() {
    SwingUtilities.invokeLater(() -> {
      inputArea.setEnabled(false);
      btnSend.setVisible(false);
      btnStop.setVisible(true);
      lblTimer.setText("0.0s");
      lblTimer.setVisible(true);
      thinkingStartTime = System.currentTimeMillis();
      thinkingTimer.start();
    });
  }

  private void stopThinking() {
    SwingUtilities.invokeLater(() -> {
      thinkingTimer.stop();
      btnStop.setVisible(false);
      btnSend.setVisible(true);
      lblTimer.setVisible(false);

      inputArea.setEnabled(true);
      inputArea.requestFocusInWindow();
    });
  }

  public void updateMetadata() {
    if (agent == null) {
      return;
    }
    ConversationService conv = (ConversationService) agent.getService(ConversationService.NAME);
    SwingUtilities.invokeLater(() -> {
      lblModelInfo.setText(conv.getModelName());
      lblTokens.setText(conv.estimateToolsTokenCount() + "+" + conv.estimateMessagesTokenCount());
    });
  }

  public void setAgent(Agent agent) {
    this.agent = agent;
    SwingUtilities.invokeLater(() -> {
      Thread.ofVirtual().start(() -> agent.showSession());
      inputArea.setEnabled(true);
      updateMetadata();
      inputArea.requestFocusInWindow();
    });
  }

  public AgentConsole getConsole() {
    return consoleController;
  }

  private JButton createSidebarButton() {
    JButton btn = new JButton();

    // 1. IMPORTANTE: activamos el pintado del fondo
    btn.setContentAreaFilled(true);
    btn.setBorderPainted(true);

    // 2. Definimos el tipo de botón
//    btn.putClientProperty("JButton.buttonType", "roundRect");
    // 3. EL TRUCO: Ajustamos el arco. 
    // Un valor bajo (8 o 10) lo hace parecer un cuadrado con puntas romas.
    // Un valor alto (999) lo haría un círculo.
    btn.putClientProperty("Component.arc", 10);

    btn.setFocusable(false);
    btn.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));

    // Un pequeño margen para que el icono no toque los bordes
    btn.setMargin(new Insets(4, 4, 4, 4));

    return btn;
  }
}
