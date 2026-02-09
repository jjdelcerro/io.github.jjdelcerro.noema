package io.github.jjdelcerro.chatagent.ui.swing;

import io.github.jjdelcerro.chatagent.lib.AgentConsole;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.text.DefaultCaret;

public class AgentSwingConsoleImpl extends JPanel implements AgentConsole {

  private final AgentSwingConsoleController controller;
  private final JTextArea textArea;

  public AgentSwingConsoleImpl() {
    setLayout(new BorderLayout());

    // Configuración visual de la terminal
    this.textArea = new JTextArea();
    this.textArea.setEditable(false);
    this.textArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
    this.textArea.setBackground(Color.BLACK);
    this.textArea.setForeground(new Color(0, 255, 65));
    ((DefaultCaret) textArea.getCaret()).setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);

    // Instanciamos el controlador delegando en nuestra área de texto
    this.controller = new AgentSwingConsoleController(this.textArea);

    add(new JScrollPane(this.textArea), BorderLayout.CENTER);
    this.showWindow();
  }

  // Método extra para el handover
  public String getText() {
    return controller.getText();
  }

  // --- Delegación de AgentConsole al Controller ---
  @Override
  public void printSystemLog(String msg) {
    controller.printSystemLog(msg);
  }

  @Override
  public void printSystemError(String msg) {
    controller.printSystemError(msg);
  }

  @Override
  public void printUserMessage(String message) {
    controller.printUserMessage(message);
  }

  @Override
  public void printModelResponse(String message) {
    controller.printModelResponse(message);
  }

  @Override
  public boolean confirm(String msg) {
    return controller.confirm(msg);
  }

  public JFrame showWindow() {
    JFrame frame = new JFrame("Inicializando ChatAgent...");
    frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
    frame.setSize(600, 400);
    frame.add(this);
    frame.setLocationRelativeTo(null);
    frame.setVisible(true);
    return frame;
  }
}
