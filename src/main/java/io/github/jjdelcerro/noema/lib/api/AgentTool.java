package io.github.jjdelcerro.noema.lib.api;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import io.github.jjdelcerro.noema.lib.impl.tools.ToolSpecificationBuilder;

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

// AgentTool.java
  /**
   * Determina si este resultado específico de la herramienta debe ser anclado
   * en la memoria proyectada (pinned turn).
   *
   * @param result Mensaje con el resultado de la ejecución.
   * @return true si la ejecución fue satisfactoria y debe anclarse; false en
   * caso contrario.
   */
  default boolean shouldPin(ToolExecutionResultMessage result) {
    return false;
  }

  default String getPinnedNotificationMessage(ToolExecutionRequest request, ToolExecutionResultMessage result) {
    return null;
  }

  // Ejecución de la lógica (recibe JSON args, devuelve String result)
  String execute(String jsonArguments);

  String trimResult(String result, TrimResultType trimResultType);
}
