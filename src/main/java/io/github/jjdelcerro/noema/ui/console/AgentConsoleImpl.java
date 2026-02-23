package io.github.jjdelcerro.noema.ui.console;

import org.jline.reader.LineReader;
import org.jline.terminal.Terminal;
import io.github.jjdelcerro.noema.lib.AgentConsole;

/**
 * Implementación de ConsoleOutput basada en JLine3.
 * Proporciona salida controlada y capacidades de interacción (confirmación).
 * 
 * @author jjdelcerro
 */
public class AgentConsoleImpl implements AgentConsole {
    
    private final Terminal terminal;
    private final LineReader lineReader;

    public AgentConsoleImpl(Terminal terminal, LineReader lineReader) {
        this.terminal = terminal;
        this.lineReader = lineReader;
    }

    public Terminal getTerminal() {
        return terminal;
    }

    public LineReader getLineReader() {
        return lineReader;
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
    public void printSystemLog(String message) {
        terminal.writer().println(">>> " + message);
        terminal.flush(); // JLine a veces buferiza
    }
    
    @Override
    public void printSystemError(String message) {
        // Podríamos usar colores ANSI aquí si quisiéramos (ej: "\u001B[31m")
        terminal.writer().println("[ERR] " + message);
        terminal.flush();
    }

  @Override
  public void printUserMessage(String message) {
        terminal.writer().println("User: " + message);
        terminal.flush();
  }

  @Override
  public void printModelResponse(String message) {
        terminal.writer().println("Model: " + message);
        terminal.flush();
  }

}