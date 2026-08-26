package io.github.jjdelcerro.noema.lib;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import io.github.jjdelcerro.noema.lib.impl.ToolSpecificationBuilder;

public interface AgentTool {

    public static final int TYPE_MEMORY = 1;
    public static final int TYPE_OPERATIONAL = 2;
    public static final int TYPE_ANNOTATION = 3;
    
    public static final int MODE_READ = 1;
    public static final int MODE_WRITE = 2;
    public static final int MODE_WEB = 3;
    public static final int MODE_EXECUTION = 4;
    public static final int MODE_SCRIPTING = 5;
    
    public enum TrimResultType {
      None,
      Trim
    }

    ToolSpecificationBuilder getSpecification();

    default String getName() {
        return getSpecification().name();
    }

    default int getType() {
        return TYPE_OPERATIONAL;
    }
    
    default int getMode() {
        return MODE_READ;
    }
    
    default boolean isAvailableByDefault() {
      return true;
    }
    
    default boolean shouldPin() {
        return false;
    }

    default String getPinnedNotificationMessage(ToolExecutionRequest request, ToolExecutionResultMessage result) {
        return null;
    }    
    
    // Ejecución de la lógica (recibe JSON args, devuelve String result)
    String execute(String jsonArguments);
    
    
    String trimResult(String result, TrimResultType trimResultType);
}
