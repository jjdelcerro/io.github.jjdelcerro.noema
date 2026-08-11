package io.github.jjdelcerro.noema.main;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;

/**
 * Selector de punto de entrada principal.
 * Uso por defecto: Inicia la interfaz gráfica (GUI).
 * Uso con parámetro -c: Inicia la interfaz de consola clásica.
 */
public class Main {
  
    private static final String MODE_SWING = "swing";
    private static final String MODE_CONSOLE = "console";
    private static final String MODE_TUI = "tui";
    private static final String MODE_WEB = "web";

    public static void main(String[] args) {
        String mode = MODE_SWING;
        Configurator.setRootLevel(Level.OFF);
        
        // Comprobamos si existe el parámetro -c entre los argumentos
        for (String arg : args) {
            if ("-c".equalsIgnoreCase(arg) || "--console".equalsIgnoreCase(arg) ) {
                mode = MODE_CONSOLE;
                break;
            }
            if ("--tui".equalsIgnoreCase(arg)) {
                mode = MODE_TUI;
                break;
            }
            if ("--web".equalsIgnoreCase(arg)) {
                mode = MODE_WEB;
                break;
            }
        }

        switch(mode) {
          case MODE_CONSOLE:
            MainConsole.main(args);
            break;
          case MODE_TUI:
            MainLanterna.main(args);
            break;
          case MODE_WEB: // De momento no hay un modo que solo inicia el servidor web.
          case MODE_SWING:
          default:
            MainGUI.main(args);
        }
    }
}
