package io.github.jjdelcerro.noema.ui.swing;


import javax.swing.JDialog;
import java.awt.Window;
import java.util.Arrays;
import org.apache.commons.lang3.ArrayUtils;

public class SwingUtils {

  /**
   * Busca entre todas las ventanas activas de la aplicación y devuelve la que
   * está en primer plano (top) si es un JDialog modal.
   *
   * @return El JDialog modal que está actualmente en la cima de la pila de
   * ventanas, o null si no hay ninguna ventana modal activa.
   */
  public static JDialog getTopModalDialog() {
    // Obtenemos todas las ventanas creadas por la aplicación.
    Window[] windows = Window.getWindows();

    // La clave es iterar el array al revés. El array de ventanas
    // se ordena por tiempo de creación. La última ventana creada es la que
    // aparecerá visualmente encima de las demás.
    for (int i = windows.length - 1; i >= 0; i--) {
      Window window = windows[i];

      if (window instanceof JDialog dialog) {
        if (dialog.isShowing() && dialog.isModal()) {
          // Al iterar hacia atrás, el primer diálogo modal que encontremos
          // será el que está en la cima. Lo devolvemos inmediatamente.
          return dialog;
        }
      }
    }
    return null;
  }

  public static Window getTopWindow() {
    Window[] windows = Window.getWindows();
    if( ArrayUtils.isEmpty(windows) ) {
      return null;
    }
    return windows[windows.length-1];
  }

}
