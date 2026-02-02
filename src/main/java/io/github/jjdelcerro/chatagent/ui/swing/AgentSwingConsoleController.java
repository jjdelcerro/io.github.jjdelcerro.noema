package io.github.jjdelcerro.chatagent.ui.swing;

import io.github.jjdelcerro.chatagent.lib.AgentConsole;
import org.apache.commons.lang3.mutable.MutableBoolean;

import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import java.lang.reflect.InvocationTargetException;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/**
 * Controlador que implementa la lógica de AgentConsole para componentes Swing.
 * Gestiona la seguridad de hilos y la sincronización de diálogos.
 */
public class AgentSwingConsoleController implements AgentConsole {

  protected final JTextComponent textComponent;

  public AgentSwingConsoleController(JTextComponent textComponent) {
    this.textComponent = textComponent;
  }

  /**
   * Facilita la recuperación del texto para procesos de handover.
   */
  public String getText() {
    return textComponent.getText();
  }

  @Override
  public void print(String message) {
    // Patrón Self-Dispatching: Protección de hilos
    if (!SwingUtilities.isEventDispatchThread()) {
      SwingUtilities.invokeLater(() -> print(message));
      return;
    }

    try {
      Document doc = textComponent.getDocument();
      doc.insertString(doc.getLength(), message, null);
    } catch (BadLocationException e) {
      // Log de consola interna si el documento falla
      e.printStackTrace();
    }
  }

  @Override
  public void println(String message) {
    print(message + "\n");
  }

  @Override
  public void printerror(String message) {
    // Por ahora lo enviamos al flujo principal, pero al estar centralizado
    // aquí, es fácil añadir estilos (rojo) en el futuro.
    print(">>> [ERR] " + message);
  }

  @Override
  public void printerrorln(String message) {
    printerror(message + "\n");
  }

  @Override
  public void flush() {
    if (!SwingUtilities.isEventDispatchThread()) {
      SwingUtilities.invokeLater(this::flush);
      return;
    }
    textComponent.repaint();
    textComponent.revalidate();
  }

  @Override
  public boolean confirm(String message) {
    // Bloqueo síncrono si se llama desde fuera del thread de Swing
    if (!SwingUtilities.isEventDispatchThread()) {
      MutableBoolean result = new MutableBoolean(false);
      try {
        // invokeAndWait es vital para pausar el hilo del agente (Virtual Thread)
        // hasta que el usuario responda en la UI.
        SwingUtilities.invokeAndWait(() -> result.setValue(confirm(message)));
      } catch (InterruptedException | InvocationTargetException e) {
        e.printStackTrace();
        return false;
      }
      return result.booleanValue();
    }

    // Ejecución directa si ya estamos en el EDT
    int choice = JOptionPane.showConfirmDialog(
            SwingUtilities.getWindowAncestor(textComponent),
            message,
            "Confirmación de Acción",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE
    );

    return choice == JOptionPane.YES_OPTION;
  }
}
