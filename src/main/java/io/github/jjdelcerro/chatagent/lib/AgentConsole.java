package io.github.jjdelcerro.chatagent.lib;

/**
 *
 * @author jjdelcerro
 */
public interface AgentConsole {

    boolean confirm(String message);
            
    void flush();

    void print(String message);

    void printerror(String message);

    void printerrorln(String message);

    void println(String message);
    
}
