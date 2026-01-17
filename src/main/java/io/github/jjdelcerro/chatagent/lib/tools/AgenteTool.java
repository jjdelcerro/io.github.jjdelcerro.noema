package io.github.jjdelcerro.chatagent.lib.tools;

import dev.langchain4j.agent.tool.ToolSpecification;

public interface AgenteTool {

    public static final int TYPE_MEMORY = 1;
    public static final int TYPE_OPERATIONAL = 2;
    
    public static final int MODE_READ = 1;
    public static final int MODE_WRITE = 2;
    
    // Devuelve la especificación para OpenAI
    ToolSpecification getSpecification();

    // Nombre para el dispatcher
    default String getName() {
        return getSpecification().name();
    }

    default int getType() {
        return TYPE_OPERATIONAL;
    }
    
    default int getMode() {
        return MODE_READ;
    }
    
    // Ejecución de la lógica (recibe JSON args, devuelve String result)
    String execute(String jsonArguments);
}
