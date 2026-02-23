package io.github.jjdelcerro.noema.main;

/**
 * Selector de punto de entrada principal.
 * Uso por defecto: Inicia la interfaz gráfica (GUI).
 * Uso con parámetro -c: Inicia la interfaz de consola clásica.
 */
public class Main {

    public static void main(String[] args) {
        boolean useConsole = false;

        // Comprobamos si existe el parámetro -c entre los argumentos
        for (String arg : args) {
            if ("-c".equalsIgnoreCase(arg)) {
                useConsole = true;
                break;
            }
        }

        if (useConsole) {
            // Delegamos en la versión de consola (renombrada a MainConsole)
            MainConsole.main(args);
        } else {
            // Por defecto iniciamos la versión Swing
            // System.out.println no es necesario aquí porque MainGUI ya lo hace
            MainGUI.main(args);
        }
    }
}
