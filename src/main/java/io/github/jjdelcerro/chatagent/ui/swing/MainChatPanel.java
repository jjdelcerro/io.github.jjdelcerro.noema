package io.github.jjdelcerro.chatagent.ui.swing;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import io.github.jjdelcerro.chatagent.lib.Agent;
import io.github.jjdelcerro.chatagent.lib.AgentConsole;
import io.github.jjdelcerro.chatagent.lib.AgentSettings;
import io.github.jjdelcerro.chatagent.ui.AgentUILocator;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Insets;
import java.awt.event.KeyEvent;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import net.miginfocom.swing.MigLayout;

public class MainChatPanel extends JPanel {

// Para localizar iconos: https://icons.getbootstrap.com/

  private final JPanel chatHistory;
  private final JTextArea inputArea;
  private final JButton sendBtn;
  private final JButton settingsBtn;
  private final JButton btnSave; // Cambiado
  private final JButton btnCopy; // Cambiado
  private final AgentConsole controller;
  private Agent agent;
  private AgentSettings settings;

  public MainChatPanel(AgentSettings settings) {
    this.settings = settings;
    setLayout(new BorderLayout());

    // --- 1. PANELES LATERALES ---
    // Panel Izquierdo (Settings)
    JPanel leftBar = new JPanel(new MigLayout("wrap 1, ins 10, filly", "[]", "[][grow]"));
    leftBar.setBackground(Color.DARK_GRAY);
    settingsBtn = new JButton();
    settingsBtn.setEnabled(false);
    settingsBtn.putClientProperty("JButton.buttonType", "roundRect");
    leftBar.add(settingsBtn, "w 25!, h 25!");

    // Panel Derecho (Guardar y Copiar)
    JPanel rightBar = new JPanel(new MigLayout("wrap 1, ins 10, filly", "[]", "[][][grow]"));
    rightBar.setBackground(Color.DARK_GRAY);
    btnSave = new JButton();
    btnCopy = new JButton();

    btnSave.putClientProperty("JButton.buttonType", "roundRect");
    btnCopy.putClientProperty("JButton.buttonType", "roundRect");
    btnSave.setEnabled(false);
    btnCopy.setEnabled(false);

    rightBar.add(btnSave, "w 25!, h 25!");
    rightBar.add(btnCopy, "w 25!, h 25!");

    // --- 2. ÁREA CENTRAL (Chat) ---
    chatHistory = new JPanel(new MigLayout("fillx, wrap 1, ins 10", "[grow, left]"));
    chatHistory.setBackground(Color.DARK_GRAY);
    
    JScrollPane scrollPane = new JScrollPane(chatHistory);
    scrollPane.setBorder(null);
    scrollPane.getVerticalScrollBar().setUnitIncrement(16);
    scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

    JPanel centerContainer = new JPanel(new BorderLayout());
    centerContainer.add(leftBar, BorderLayout.WEST);
    centerContainer.add(scrollPane, BorderLayout.CENTER);
    centerContainer.add(rightBar, BorderLayout.EAST);

    add(centerContainer, BorderLayout.CENTER);

    // --- 3. PANEL INFERIOR (Cápsula de Input) ---
    JPanel bottomPanel = new JPanel(new MigLayout("fillx, ins 10 15 15 15", "[grow][]"));

    inputArea = new JTextArea(3, 20); // AHORA 3 LÍNEAS
    inputArea.setEnabled(false);
    inputArea.setLineWrap(true);
    inputArea.setWrapStyleWord(true);
    inputArea.setOpaque(false);
    inputArea.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));
    // Definimos el KeyStroke (Ctrl + Enter)
    KeyStroke ctrlEnter = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, java.awt.event.InputEvent.CTRL_DOWN_MASK);

    // Lo registramos en el InputMap del área de texto
    inputArea.getInputMap(JComponent.WHEN_FOCUSED).put(ctrlEnter, "send-message");

    // Registramos la acción en el ActionMap
    inputArea.getActionMap().put("send-message", new AbstractAction() {
        @Override
        public void actionPerformed(java.awt.event.ActionEvent e) {
            handleSend();
        }
    });
    
    JScrollPane inputScroll = new JScrollPane(inputArea);
    inputScroll.setOpaque(false);
    inputScroll.getViewport().setOpaque(false);
    inputScroll.setBackground(UIManager.getColor("TextArea.background"));
    inputScroll.setBorder(new com.formdev.flatlaf.ui.FlatLineBorder(
            new Insets(1, 1, 1, 1), UIManager.getColor("Component.borderColor"), 2, 25));

    sendBtn = new JButton();
    sendBtn.setEnabled(false);
    sendBtn.putClientProperty("JButton.buttonType", "roundRect");
    sendBtn.setToolTipText("Enviar mensaje (Ctrl+Enter)");

    // "hmin 80" para acomodar visualmente las 3 líneas con margen
    bottomPanel.add(inputScroll, "growx, hmin 80, aligny center");
    bottomPanel.add(sendBtn, "aligny center, w 25!, h 25!");
    bottomPanel.setBackground(Color.DARK_GRAY);

    add(bottomPanel, BorderLayout.SOUTH);

    try {
      String iconPath = "io/github/jjdelcerro/chatagent/ui/swing/";
      settingsBtn.setIcon(new FlatSVGIcon(iconPath + "settings.svg", 16, 16));
      sendBtn.setIcon(new FlatSVGIcon(iconPath + "send.svg", 16, 16));
      btnSave.setIcon(new FlatSVGIcon(iconPath + "save.svg", 16, 16));
      btnCopy.setIcon(new FlatSVGIcon(iconPath + "copy.svg", 16, 16));
    } catch (Exception e) {
      settingsBtn.setText("S");
      sendBtn.setText(">");
      btnSave.setText("V");
      btnCopy.setText("C");
    }

    controller = this.createConsoleController(chatHistory);

    JPopupMenu copyMenu = new JPopupMenu();
    JMenuItem itemCopyMarkdown = new JMenuItem("Copy as Markdown");
    JMenuItem itemCopyText = new JMenuItem("Copy as Text");
    copyMenu.add(itemCopyMarkdown);
    copyMenu.add(itemCopyText);

    btnCopy.addActionListener(e -> {
      copyMenu.show(btnCopy, 0, btnCopy.getHeight());
    });

    sendBtn.addActionListener(e -> handleSend());
    settingsBtn.addActionListener(e -> {
      if (agent != null) {
        AgentUILocator.getAgentUIManager().createSettings(agent).showWindow();
      }
    });

    itemCopyMarkdown.addActionListener(e -> {
      // TODO: Implementar lógica de lectura de historial
      JOptionPane.showMessageDialog(this, "Markdown copiado al portapapeles");
    });

    itemCopyText.addActionListener(e -> {
      JOptionPane.showMessageDialog(this, "Texto plano copiado al portapapeles");
    });

    btnSave.addActionListener(e -> {
      JOptionPane.showMessageDialog(this, "Guardando historial...");
    });
  }

  public void setAgent(Agent agent) {
    this.agent = agent;
    this.settings = agent.getSettings();
    
    SwingUtilities.invokeLater(() -> {
      inputArea.setEnabled(true);
      sendBtn.setEnabled(true);
      settingsBtn.setEnabled(true);
      btnSave.setEnabled(true);
      btnCopy.setEnabled(true);
      inputArea.requestFocusInWindow();
    });
  }

  public AgentConsole getConsole() {
    return controller;
  }

  private void handleSend() {
    String text = inputArea.getText().trim();
    if (text.isEmpty() || agent == null) {
      return;
    }
    inputArea.setText("");
    controller.printUserMessage(text);
    Thread.ofVirtual().start(() -> {
      try {
        String response = agent.processTurn(text);
        controller.printModelResponse(response);
      } catch (Exception e) {
        controller.printSystemError("Error: " + e.getMessage());
      }
    });
  }
  
  private AgentConsole createConsoleController(JPanel panel) {
    AgentConsole controller;
    controller = new AgentSwingConsoleControllerUsingMultipleJTextPane(panel);
    return controller;
  }
}
