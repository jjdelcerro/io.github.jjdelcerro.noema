package io.github.jjdelcerro.chatagent.lib.utils;

/**
 *
 * @author jjdelcerro
 */
public interface ConsoleOutput {

    boolean confirm(String message);
            
    void flush();

    void print(String message);

    void printerror(String message);

    void printerrorln(String message);

    void println(String message);
    
}
