package io.github.jjdelcerro.chatagent.ui.swing;

import io.github.jjdelcerro.chatagent.lib.AgentConsole;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.Insets;
import java.awt.Window;
import javax.swing.BorderFactory;
import javax.swing.JEditorPane;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

public class AgentSwingConsoleController implements AgentConsole {

  private final JPanel chatContainer;
  private MessageType lastType = null;
  private JTextPane currentTextPane = null; // Ahora apuntamos al componente de texto interno
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
    // TODO: integrar aqui https://github.com/commonmark/commonmark-java
    SwingUtilities.invokeLater(() -> {
      if (type == lastType && currentTextPane != null) {
        // AGRUPACIÓN: Añadimos al componente existente
        currentContent.append("<br>").append(formatText(type, text));
        currentTextPane.setText(wrapHtml(currentContent.toString()));
      } else {
        // NUEVA CAJA: Creamos el contenedor y el text pane
        lastType = type;
        currentContent = new StringBuilder(formatText(type, text));

        currentTextPane = createFormattedTextPane(type);
        currentTextPane.setText(wrapHtml(currentContent.toString()));

        // Creamos la "Burbuja" (el contenedor)
        JPanel bubble = createBubbleWrapper(type, currentTextPane);

        // "width 85%" para que no ocupen todo el ancho y sea más legible
//        chatContainer.add(bubble, "growx, width 80%, gapy 5 5");
        chatContainer.add(bubble, "growx, width 0:100:100%, gapy 5 5");
      }

      chatContainer.revalidate();
      chatContainer.repaint();
      scrollAtBottom();
    });
  }

  private Border createRoundedBorder(Color borderColor) {
    com.formdev.flatlaf.ui.FlatLineBorder roundedLine = new com.formdev.flatlaf.ui.FlatLineBorder(
            new Insets(0, 0, 0, 0), 
            borderColor, 
            1, 
            20 // Redondeo de la burbuja
    );
//    Border roundedLine = BorderFactory.createLineBorder(lineColor, 2, true);
    return roundedLine;
  }
  
  private JPanel createBubbleWrapper(MessageType type, JTextPane textPane) {
      Color lineColor;
      switch(type) {
        case ERROR:
          lineColor = Color.RED.darker();
          break;
        case MODEL:
          lineColor = Color.GREEN.darker();
          break;
        case USER:
          lineColor = Color.BLUE.darker();
          break;
        default:
        case SYSTEM:
          lineColor = Color.LIGHT_GRAY;
          break;
      }
      JPanel p = new JPanel();
      p.setLayout(new BorderLayout());
      p.setBorder(BorderFactory.createCompoundBorder(
              BorderFactory.createEmptyBorder(5, 5, 5, 5), 
              BorderFactory.createCompoundBorder(
                      createRoundedBorder(lineColor), 
                      BorderFactory.createEmptyBorder(8, 12, 8, 12) 
              )
      ));    
      p.add(textPane, BorderLayout.CENTER);
      p.setBackground(Color.DARK_GRAY);
      textPane.setBackground(Color.DARK_GRAY);
      textPane.setForeground(Color.LIGHT_GRAY);
      return p;
  }
  
  private JTextPane createFormattedTextPane(MessageType type) {
    JTextPane pane = new JTextPane();
    pane.setEditable(false);
    pane.setContentType("text/html");
    pane.setOpaque(true);
    pane.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));

    HTMLEditorKit kit = new HTMLEditorKit();
    StyleSheet ss = kit.getStyleSheet();
    // Estilo base para el texto
//    ss.addRule("body { font-family: sans-serif; font-size: 11pt; color: #e0e0e0; margin: 0; }");
    ss.addRule("body { width: 100%; font-family: sans-serif; font-size: 11pt; }");
//    ss.addRule("pre { background-color: #1e1e1e; color: #dcdcdc; padding: 8px; border-radius: 4px; }");
    ss.addRule("pre { white-space: pre-wrap; word-wrap: break-word; }");    
    ss.addRule("code { font-family: 'Monospaced'; color: #ffad66; }");

    if (type == MessageType.SYSTEM) {
      ss.addRule("body { color: #999999; font-style: italic; }");
    }
    if (type == MessageType.ERROR) {
      ss.addRule("body { color: #ff6666; font-weight: bold; }");
    }
    if (type == MessageType.USER) {
      ss.addRule("body { color: #a6e22e; }");
    }
    pane.setEditorKit(kit);
    return pane;
  }

  private String formatText(MessageType type, String text) {
    if (type == MessageType.MODEL) {
      return htmlRenderer.render(mdParser.parse(text));
    }
    return text.replace("\n", "<br>");
  }

  private String wrapHtml(String content) {
    return "<html><body>" + content + "</body></html>";
  }

  private void scrollAtBottom() {
    SwingUtilities.invokeLater(() -> {
      Container parent = chatContainer.getParent();
      if (parent instanceof JViewport viewport) {
        JScrollPane scrollPane = (JScrollPane) viewport.getParent();
        JScrollBar vertical = scrollPane.getVerticalScrollBar();
        vertical.setValue(vertical.getMaximum());
      }
    });
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
  
  public Window getRoot() {
    Window parent = SwingUtilities.getWindowAncestor(this.chatContainer);
    return parent;
  }
}
