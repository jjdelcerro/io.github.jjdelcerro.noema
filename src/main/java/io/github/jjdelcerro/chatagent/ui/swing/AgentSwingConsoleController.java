package io.github.jjdelcerro.chatagent.ui.swing;

import io.github.jjdelcerro.chatagent.lib.AgentConsole;
import java.awt.Color;
import java.awt.Container;
import javax.swing.BorderFactory;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

public class AgentSwingConsoleController implements AgentConsole {

  private final JPanel chatContainer;
  private MessageType lastType = null;
  private JTextPane currentPane = null;
  private StringBuilder currentContent = new StringBuilder();

  private final Parser mdParser = Parser.builder().build();
  private final HtmlRenderer htmlRenderer = HtmlRenderer.builder().build();

  public enum MessageType {
    SYSTEM, ERROR, USER, MODEL
  }

  public AgentSwingConsoleController(JPanel chatContainer) {
    this.chatContainer = chatContainer;
  }

  private synchronized void addMessage(MessageType type, String text) {
    SwingUtilities.invokeLater(() -> {
      if (type == lastType && currentPane != null) {
        currentContent.append("<br>").append(formatText(type, text));
        currentPane.setText(wrapHtml(currentContent.toString()));
      } else {
        lastType = type;
        currentContent = new StringBuilder(formatText(type, text));
        currentPane = createNewBox(type);
        currentPane.setText(wrapHtml(currentContent.toString()));
        // "width 90%" limita el ancho para que no se estire demasiado en pantallas grandes
        chatContainer.add(currentPane, "growx, width 90%, gapy 5 5");
      }

      // Forzar scroll al final
      chatContainer.revalidate();
      chatContainer.repaint();
      SwingUtilities.invokeLater(() -> {
        Container parent = chatContainer.getParent();
        if (parent instanceof JViewport viewport) {
          JScrollPane scrollPane = (JScrollPane) viewport.getParent();
          JScrollBar vertical = scrollPane.getVerticalScrollBar();
          vertical.setValue(vertical.getMaximum());
        }
      });
    });
  }

  private String formatText(MessageType type, String text) {
    if (type == MessageType.MODEL) {
      return htmlRenderer.render(mdParser.parse(text));
    }
    return text.replace("\n", "<br>");
  }

  private String wrapHtml(String content) {
    return "<html><body style='margin: 5px;'>" + content + "</body></html>";
  }

  private JTextPane createNewBox(MessageType type) {
    JTextPane pane = new JTextPane();
    pane.setEditable(false);
    pane.setContentType("text/html");

    // --- ESTILO DE LA BURBUJA ---
    pane.putClientProperty("JComponent.arc", 15); // Redondeo de la caja
    pane.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12)); // Padding interno

    // Colores de fondo sutiles para diferenciar (Ajustar al gusto)
    switch (type) {
      case USER ->
        pane.setBackground(new Color(43, 87, 132)); // Azul oscuro sutil
      case MODEL ->
        pane.setBackground(new Color(60, 63, 65)); // Gris suave
      case SYSTEM, ERROR ->
        pane.setOpaque(false); // Logs sin fondo
    }

    HTMLEditorKit kit = new HTMLEditorKit();
    StyleSheet ss = kit.getStyleSheet();
    ss.addRule("body { font-family: sans-serif; font-size: 11pt; color: #dddddd; }");
    ss.addRule("pre { background-color: #1e1e1e; color: #dcdcdc; padding: 10px; border-radius: 5px; }");
    ss.addRule("code { font-family: 'Monospaced'; color: #ce9178; }");

    if (type == MessageType.SYSTEM) {
      ss.addRule("body { color: #888888; font-style: italic; }");
    }
    if (type == MessageType.ERROR) {
      ss.addRule("body { color: #ff5555; }");
    }
    if (type == MessageType.USER) {
      ss.addRule("body { font-weight: bold; }");
    }

    pane.setEditorKit(kit);
    return pane;
  }

  @Override
  public void printSystemLog(String m) {
    addMessage(MessageType.SYSTEM, m);
  }

  @Override
  public void printSystemError(String m) {
    addMessage(MessageType.ERROR, m);
  }

  @Override
  public void printUserMessage(String m) {
    addMessage(MessageType.USER, m);
  }

  @Override
  public void printModelResponse(String m) {
    addMessage(MessageType.MODEL, m);
  }

  @Override
  public boolean confirm(String message) {
    return JOptionPane.showConfirmDialog(null, message, "Confirmación", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
  }
}
