package io.github.jjdelcerro.noema.lib.impl.services.mcp;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.service.tool.ToolExecutionResult;
import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.impl.tools.AbstractAgentTool;
import io.github.jjdelcerro.noema.lib.impl.tools.ToolSpecificationBuilder;

public class McpToolWrapper extends AbstractAgentTool {

  private final McpClient mcpClient;
  private final ToolSpecification toolSpecification;
  private final int mode;

  public McpToolWrapper(Agent agent, McpClient mcpClient, ToolSpecification toolSpecification, int mode) {
    super(agent);
    this.mcpClient = mcpClient;
    this.toolSpecification = toolSpecification;
    this.mode = mode; // MODE_READ, MODE_WRITE, etc.
  }

  @Override
  public boolean isAvailableByDefault() {
    return true;
  }

  @Override
  public ToolSpecificationBuilder getSpecification() {
    // Adaptamos la ToolSpecification nativa de LangChain4j al Builder de Noema
    ToolSpecificationBuilder builder = ToolSpecificationBuilder.create()
            .name(toolSpecification.name())
            .description(toolSpecification.description());

    if (toolSpecification.parameters() != null) {
      builder.parameters(toolSpecification.parameters());
    }
    return builder;
  }

  @Override
  public int getMode() {
    return this.mode; // Permite aplicar confirmación humana si el modo requiere escritura/ejecución
  }

  @Override
  public String execute(String jsonArguments) {
    try {
      ToolExecutionRequest request = ToolExecutionRequest.builder()
              .name(toolSpecification.name())
              .arguments(jsonArguments)
              .build();

      // Delegamos la ejecución al cliente MCP de LangChain4j
      ToolExecutionResult result = mcpClient.executeTool(request);
      return result.result().toString(); // FIXME Es correcto?
    } catch (Exception e) {
      LOGGER.error("Error ejecutando herramienta MCP '{}'", toolSpecification.name(), e);
      return error("Error en servidor MCP: " + e.getMessage());
    }
  }
}
