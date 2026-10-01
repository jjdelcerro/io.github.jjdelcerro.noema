package io.github.jjdelcerro.noema.lib;

import io.github.jjdelcerro.noema.lib.model.ModelStreamCallback;

/**
 *
 * @author jjdelcerro
 */
public interface AgentConsole extends ModelStreamCallback {

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
    
    void printModelReasoning(String message);
}
