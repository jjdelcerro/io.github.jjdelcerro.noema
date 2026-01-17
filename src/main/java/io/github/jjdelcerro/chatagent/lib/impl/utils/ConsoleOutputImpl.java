package io.github.jjdelcerro.chatagent.lib.impl.utils;

import io.github.jjdelcerro.chatagent.lib.utils.ConsoleOutput;
import org.jline.reader.LineReader;
import org.jline.terminal.Terminal;

/**
 * Implementación de ConsoleOutput basada en JLine3.
 * Proporciona salida controlada y capacidades de interacción (confirmación).
 * 
 * @author jjdelcerro
 */
public class ConsoleOutputImpl implements ConsoleOutput {
    
    private final Terminal terminal;
    private final LineReader lineReader;

    private ConsoleOutputImpl(Terminal terminal, LineReader lineReader) {
        this.terminal = terminal;
        this.lineReader = lineReader;
    }

    /**
     * Factoría que requiere los componentes de JLine inicializados.
     */
    public static ConsoleOutput create(Terminal terminal, LineReader lineReader) {
        return new ConsoleOutputImpl(terminal, lineReader);
    }

    @Override
    public boolean confirm(String message) {
        while (true) {
            // Usamos el lineReader para pedir confirmación limpia
            String input = lineReader.readLine(message + " (s/n): ");
            if (input == null) return false; // Ctrl+D o cierre
            
            input = input.trim().toLowerCase();
            if (input.equals("s") || input.equals("si") || input.equals("y") || input.equals("yes")) {
                return true;
            } else if (input.equals("n") || input.equals("no")) {
                return false;
            }
            // Si no entiende, repite el bucle
        }
    }

    @Override
    public void println(String message) {
        terminal.writer().println(">>> " + message);
        terminal.flush(); // JLine a veces buferiza
    }

    @Override
    public void print(String message) {
        terminal.writer().print(message);
        terminal.flush();
    }
    
    @Override
    public void flush() {
        terminal.writer().flush();
    }
    
    @Override
    public void printerrorln(String message) {
        // Podríamos usar colores ANSI aquí si quisiéramos (ej: "\u001B[31m")
        terminal.writer().println(">>> [ERR] " + message);
        terminal.flush();
    }

    @Override
    public void printerror(String message) {
        terminal.writer().print(">>> [ERR] " + message);
        terminal.flush();
    }
}