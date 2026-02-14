package io.github.jjdelcerro.chatagent.ui.swing;

import io.github.jjdelcerro.chatagent.lib.AgentConsole;
import java.awt.BorderLayout;
import java.awt.Color;
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

  private JTextPane currentTextPanel;

  public enum MessageType {
    SYSTEM, ERROR, USER, MODEL
  }

  public AgentSwingConsoleControllerUsingMultipleJTextPane(JPanel chatContainer) {
    this.chatContainer = chatContainer;
  }

  private synchronized void addMessage(MessageType type, String text) {
    SwingUtilities.invokeLater(() -> {
      String header = null;
      switch(type) {
        case MODEL:
          header = "Modelo:";
          break;
        case USER:
          header = "Usuario:";
          break;
        default:
        case SYSTEM:
        case ERROR:
          break;
      }        
      if( header == null )  {
        if (type == lastType && currentTextPanel != null) {
          // AGRUPACIÓN: Añadimos al componente existente
          String oldText = currentTextPanel.getText();
          String newText = oldText + "\n" + text;
          currentTextPanel.setText(newText);        
        } else {
          // NUEVA CAJA: Creamos el contenedor y el text pane
          currentTextPanel = new JTextPane();
          currentTextPanel.setText(text);

          JPanel bubble = createBubbleWrapper(type, currentTextPanel);
          chatContainer.add(bubble, "growx, width 0:100:100%, gapy 0 2");
        }
      } else {
        if (type == lastType && currentTextPanel != null) {
          // AGRUPACIÓN: Añadimos al componente existente
          JMarkdownPanel markdownPanel = (JMarkdownPanel) this.currentTextPanel; 
          String oldMd = markdownPanel.getMarkdownText();
          String newMd = oldMd + "\n" + text;
          markdownPanel.setMarkdownText(header, newMd);        
        } else {
          // NUEVA CAJA: Creamos el contenedor y el text pane
          JMarkdownPanel markdownPanel = new JMarkdownPanel();
          markdownPanel.setMarkdownText(header, text);
          currentTextPanel = markdownPanel;
          
          JPanel bubble = createBubbleWrapper(type, currentTextPanel);
          chatContainer.add(bubble, "growx, width 0:100:100%, gapy 5 5");
        }
      }
      lastType = type;
      currentTextPanel.setPreferredSize(null); 
      currentTextPanel.validate();

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
          lineColor = new Color(80, 170, 110); // Color.GREEN.darker();
          break;
        case USER:
          lineColor = new Color(60, 140, 200); // Color.BLUE.darker();
          break;
        default:
        case SYSTEM:
          lineColor = Color.LIGHT_GRAY;
          break;
      }
      JPanel p = new JPanel();
      p.setLayout(new BorderLayout());
      p.setBorder(BorderFactory.createCompoundBorder(
              BorderFactory.createEmptyBorder(2, 5, 2, 5), 
              BorderFactory.createCompoundBorder(
                      createRoundedBorder(lineColor), 
                      BorderFactory.createEmptyBorder(4, 12, 4, 12) 
              )
      ));    
      p.add(textPane, BorderLayout.CENTER);
      p.setBackground(Color.DARK_GRAY);
      textPane.setBackground(Color.DARK_GRAY);
      textPane.setForeground(Color.LIGHT_GRAY);
      return p;
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
}
