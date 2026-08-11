package io.github.jjdelcerro.noema.lib.impl.services.mcp;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.http.HttpMcpTransport;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.AgentService;
import io.github.jjdelcerro.noema.lib.AgentServiceFactory;
import io.github.jjdelcerro.noema.lib.AgentTool;
import io.github.jjdelcerro.noema.lib.settings.AgentSettingsGroup;
import io.github.jjdelcerro.noema.lib.settings.AgentSettingsItem;
import io.github.jjdelcerro.noema.lib.settings.AgentSettingsList;
import io.github.jjdelcerro.noema.lib.settings.AgentSettingsPaths;
import io.github.jjdelcerro.noema.lib.settings.AgentSettingsString;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.*;

public class McpServiceImpl implements AgentService {

  private static final Logger LOGGER = LoggerFactory.getLogger(McpServiceImpl.class);
  public static final String NAME = "MCP";

  private final AgentServiceFactory factory;
  private final Agent agent;
  private final List<McpClient> activeClients = new ArrayList<>();
  private final List<AgentTool> tools = new ArrayList<>();
  private boolean running = false;

  public McpServiceImpl(AgentServiceFactory factory, Agent agent) {
    this.factory = factory;
    this.agent = agent;
  }

  @Override
  public synchronized void start() {
    if (running) {
      return;
    }

    // 1. Leemos la lista de servidores de AgentSettings
    AgentSettingsList serverList = agent.getSettings().getPropertyAsList("mcp/servers");

    if (serverList == null || serverList.size() == 0) {
      LOGGER.info("No hay servidores MCP configurados en 'mcp/servers'.");
      this.running = true;
      return;
    }

    // 2. Iteramos sobre la lista de servidores
    for (AgentSettingsItem item : serverList) {
      if (item instanceof AgentSettingsGroup serverGroup) {
        initSingleMcpServer(serverGroup);
      }
    }

    this.running = true;
  }

  private void initSingleMcpServer(AgentSettingsGroup serverGroup) {
    String name = serverGroup.getPropertyAsString("name", "unnamed");
    String type = serverGroup.getPropertyAsString("type", "stdio").toLowerCase();
    int mode = parseToolMode(serverGroup.getPropertyAsString("mode", "READ"));

    try {
      McpTransport transport = null;

      if ("stdio".equals(type)) {
        List<String> commandList = extractCommandList(serverGroup);
        if (commandList.isEmpty()) {
          LOGGER.warn("Servidor MCP '{}' omitido: 'command' está vacío.", name);
          return;
        }

        StdioMcpTransport.Builder transportBuilder = new StdioMcpTransport.Builder()
                .command(commandList);

        // Extraer variables de entorno si el grupo 'env' existe
        AgentSettingsGroup envGroup = serverGroup.getPropertyGroup("env");
        if (envGroup instanceof AgentSettingsGroup group) {
          Map<String, String> envMap = new HashMap<>();
          for (String envKey : group.getPropertyNames()) {
            envMap.put(envKey, group.getPropertyAsString(envKey));
          }
          transportBuilder.environment(envMap);
        }

        transport = transportBuilder.build();

      } else if ("http".equals(type) || "sse".equals(type)) {
        String url = serverGroup.getPropertyAsString("url");
        if (StringUtils.isBlank(url)) {
          LOGGER.warn("Servidor MCP '{}' omitido: 'url' no especificada.", name);
          return;
        }

        transport = new HttpMcpTransport.Builder()
                .sseUrl(url)
                .build();
      } else {
        LOGGER.warn("Tipo de transporte MCP no soportado '{}' en servidor '{}'.", type, name);
        return;
      }

      // Crear cliente LangChain4j para este servidor
      McpClient client = new DefaultMcpClient.Builder()
              .clientName("Noema-" + name)
              .transport(transport)
              .build();

      // Descubrir herramientas expuestas por el servidor
      List<ToolSpecification> mcpTools = client.listTools();

      // Envolver las herramientas en McpToolWrapper de Noema
      for (ToolSpecification spec : mcpTools) {
        this.tools.add(new McpToolWrapper(agent, client, spec, mode));
      }

      this.activeClients.add(client);
      LOGGER.info("Servidor MCP '{}' conectado con éxito. {} herramientas registradas.", name, mcpTools.size());
      agent.getCurrentConsole().printSystemLog("MCP [" + name + "]: " + mcpTools.size() + " herramientas cargadas.");

    } catch (Exception e) {
      LOGGER.error("Fallo al conectar con el servidor MCP '{}'", name, e);
      agent.getCurrentConsole().printSystemError("Fallo en servidor MCP '" + name + "': " + e.getMessage());
    }
  }

  /**
   * Extrae el comando independientemente de si se configuró como String simple
   * ("npx -y @mcp/server") o como Array/Paths (["npx", "-y", "@mcp/server"]).
   */
  private List<String> extractCommandList(AgentSettingsGroup serverGroup) {
    List<String> commandList = new ArrayList<>();
    AgentSettingsItem cmdItem = serverGroup.getProperty("command");

    if (cmdItem instanceof AgentSettingsString s) {
      commandList.addAll(Arrays.asList(s.getValue().split("\\s+")));
    } else if (cmdItem instanceof AgentSettingsPaths p) {
      for (Path path : p.getValues()) {
        commandList.add(path.toString());
      }
    }
    return commandList;
  }

  private int parseToolMode(String modeStr) {
    return switch (modeStr.toUpperCase()) {
      case "WRITE" ->
        AgentTool.MODE_WRITE;
      case "EXECUTION" ->
        AgentTool.MODE_EXECUTION;
      case "WEB" ->
        AgentTool.MODE_WEB;
      default ->
        AgentTool.MODE_READ;
    };
  }

  @Override
  public synchronized void stop() {
    if (!running) {
      return;
    }

    for (McpClient client : activeClients) {
      try {
        client.close();
      } catch (Exception e) {
        LOGGER.warn("Error cerrando cliente MCP", e);
      }
    }
    activeClients.clear();
    tools.clear();
    running = false;
  }

  @Override
  public List<AgentTool> getTools() {
    return tools;
  }

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public boolean isRunning() {
    return running;
  }

  @Override
  public boolean canStart() {
    return factory.canStart(agent.getSettings());
  }

  @Override
  public AgentServiceFactory getFactory() {
    return factory;
  }

  @Override
  public Agent.ModelParameters getModelParameters(String name) {
    return null;
  }
}
