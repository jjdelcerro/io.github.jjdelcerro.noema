package io.github.jjdelcerro.noema.lib;

import io.github.jjdelcerro.noema.lib.impl.ToolSpecificationBuilder;

public interface AgentTool {

    public static final int TYPE_MEMORY = 1;
    public static final int TYPE_OPERATIONAL = 2;
    
    public static final int MODE_READ = 1;
    public static final int MODE_WRITE = 2;
    public static final int MODE_WEB = 3;
    public static final int MODE_EXECUTION = 4;
    
    public enum TrimResultType {
      None,
      Notify,
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
    
    // Ejecución de la lógica (recibe JSON args, devuelve String result)
    String execute(String jsonArguments);
    
    
    String trimResult(String result, TrimResultType trimResultType);
}
