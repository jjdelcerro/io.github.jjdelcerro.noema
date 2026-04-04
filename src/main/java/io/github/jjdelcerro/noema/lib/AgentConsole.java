package io.github.jjdelcerro.noema.lib;

/**
 *
 * @author jjdelcerro
 */
public interface AgentConsole {

    public enum Format {
      RawText,
      Markdown
    }
      
    boolean confirm(String message);
            
    void printSystemError(String message);

    void printSystemLog(String message);
    
    void printSystemLog(String message, Format format);
    
    void printUserMessage(String message);
    
    void printModelResponse(String message);
    
}
