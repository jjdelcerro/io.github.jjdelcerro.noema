package io.github.jjdelcerro.noema.lib;

/**
 *
 * @author jjdelcerro
 */
public interface AgentConsole {

    boolean confirm(String message);
            
    void printSystemError(String message);

    void printSystemLog(String message);
    
    void printUserMessage(String message);
    
    void printModelResponse(String message);
    
}
