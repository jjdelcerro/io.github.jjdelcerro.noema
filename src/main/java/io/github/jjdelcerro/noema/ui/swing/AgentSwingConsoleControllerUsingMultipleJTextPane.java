package io.github.jjdelcerro.noema.ui.swing;

import io.github.jjdelcerro.noema.lib.AgentConsole;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Insets;
import java.awt.Window;
import javax.swing.BorderFactory;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;

public class AgentSwingConsoleControllerUsingMultipleJTextPane implements AgentConsole {

  private final JPanel chatContainer;
  private MessageType lastType = null;
  private JBubbleTextPanel currentPanel;

  public enum MessageType {
    SYSTEM, SYSTEM_MARKDOWN, ERROR, USER, MODEL
  }

  private static class JBubbleTextPanel extends JPanel {

    protected JTextPane contents;
    private final MessageType type;

    public JBubbleTextPanel(MessageType type, Color lineColor, JTextPane contents) {
      this.type = type;
      this.setLayout(new BorderLayout());
      this.setBorder(BorderFactory.createCompoundBorder(
              BorderFactory.createEmptyBorder(2, 5, 2, 5),
              BorderFactory.createCompoundBorder(
                      createRoundedBorder(lineColor),
                      BorderFactory.createEmptyBorder(4, 12, 4, 12)
              )
      ));
      this.contents = contents;
      this.add(this.contents, BorderLayout.CENTER);
      this.setBackground(Color.DARK_GRAY);
      this.contents.setBackground(Color.DARK_GRAY);
      this.contents.setForeground(Color.LIGHT_GRAY);
    }

    private Border createRoundedBorder(Color borderColor) {
      com.formdev.flatlaf.ui.FlatLineBorder roundedLine = new com.formdev.flatlaf.ui.FlatLineBorder(
              new Insets(0, 0, 0, 0),
              borderColor,
              1,
              20 // Redondeo de la burbuja
      );
      return roundedLine;
    }

    public void appendText(String text) {
      String oldText = this.contents.getText();
      String newText = oldText + "\n" + text;
      this.contents.setText(newText);
      this.contents.setPreferredSize(null);
//      this.contents.validate();      
    }

    public String getRawText() {
      return this.contents.getText();
    }

    public String getTypeName() {
      switch (this.type) {
        case ERROR:
          return "Error";
        case USER:
          return "Usuario";
        case MODEL:
          return "Modelo";
        case SYSTEM:
        default:
          return "Sistema";
      }
    }

    public MessageType getType() {
      return type;
    }
  }

  private static class JBubbleMarkdownPanel extends JBubbleTextPanel {

    public JBubbleMarkdownPanel(MessageType type, Color lineColor, JTextPane contents) {
      super(type, lineColor, contents);
    }

    public void appendText(String text) {
      JMarkdownPanel markdownPanel = (JMarkdownPanel) this.contents;
      String oldMd = markdownPanel.getMarkdownText();
      String newMd = oldMd + "\n" + text;
      markdownPanel.setMarkdownText(this.getTypeName() + ":", newMd);
      this.contents.setPreferredSize(null);
    }

    public String getRawText() {
      return ((JMarkdownPanel) this.contents).getMarkdownText();
    }
  }

  private static class JSystemMarkdownPanel extends JBubbleMarkdownPanel {

    public JSystemMarkdownPanel() {
      super(MessageType.SYSTEM_MARKDOWN, Color.LIGHT_GRAY, new JMarkdownPanel());
    }
  }

  private static class JUserPanel extends JBubbleMarkdownPanel {

    public JUserPanel() {
      super(MessageType.USER, new Color(60, 140, 200), new JMarkdownPanel());
    }
  }

  private static class JModelPanel extends JBubbleMarkdownPanel {

    public JModelPanel() {
      super(MessageType.MODEL, new Color(80, 170, 110), new JMarkdownPanel());
    }
  }

  private static class JErrorPanel extends JBubbleTextPanel {

    public JErrorPanel() {
      super(MessageType.ERROR, Color.RED.darker(), new JTextPane());
    }
  }

  private static class JSystemPanel extends JBubbleTextPanel {

    public JSystemPanel() {
      super(MessageType.SYSTEM, Color.LIGHT_GRAY, new JTextPane());
    }
  }

  public AgentSwingConsoleControllerUsingMultipleJTextPane(JPanel chatContainer) {
    this.chatContainer = chatContainer;
  }

  private synchronized void addMessage(MessageType type, String text) {
    SwingUtilities.invokeLater(() -> {
      if (!(type == lastType && currentPanel != null)) {
        this.currentPanel = createBubblePanel(type);
        this.chatContainer.add(this.currentPanel, "growx, width 0:100:100%, gapy 0 2");
      }
      this.currentPanel.appendText(text);
      this.lastType = type;
      this.currentPanel.setPreferredSize(null);
      this.currentPanel.validate();

      this.chatContainer.revalidate();
      this.chatContainer.repaint();
      scrollAtBottom();
    }
    );
  }

  private JBubbleTextPanel createBubblePanel(MessageType type) {
    switch (type) {
      case ERROR:
        return new JErrorPanel();
      case MODEL:
        return new JModelPanel();
      case USER:
        return new JUserPanel();
      case SYSTEM_MARKDOWN:
        return new JSystemMarkdownPanel();
      case SYSTEM:
      default:
        return new JSystemPanel();
    }
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
  public void printSystemLog(String m, Format format) {
    switch (format) {
      case Markdown:
        addMessage(MessageType.SYSTEM_MARKDOWN, m);
        break;
      case RawText:
      default:
        addMessage(MessageType.SYSTEM, m);
    }
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
    return JOptionPane.showConfirmDialog(
            SwingUtils.getTopWindow(),
            message,
            "Confirmación",
            JOptionPane.YES_NO_OPTION
    ) == JOptionPane.YES_OPTION;
  }

  public Window getRoot() {
    Window parent = SwingUtilities.getWindowAncestor(this.chatContainer);
    return parent;
  }

  public String getMarkdown() {
    StringBuilder contents = new StringBuilder();
    for (Component component : this.chatContainer.getComponents()) {
      if (component instanceof JBubbleTextPanel textPanel) {
        contents.append("# ")
                .append(textPanel.getTypeName())
                .append("\n\n");
        switch (textPanel.getType()) {
          case ERROR:
          case SYSTEM:
            contents.append("```\n")
                    .append(textPanel.getRawText())
                    .append("\n```\n");
            break;
          case USER:
          case MODEL:
          default:
            contents.append(textPanel.getRawText());
            break;
        }
        contents.append("\n\n---\n\n");
      }
    }
    return contents.toString();
  }
}
