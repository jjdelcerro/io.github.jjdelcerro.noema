package io.github.jjdelcerro.noema.lib.impl.services.subagents;

import io.github.jjdelcerro.noema.lib.services.subagents.SubagentsService;
import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.AgentServiceFactory;
import io.github.jjdelcerro.noema.lib.AgentTool;
import io.github.jjdelcerro.noema.lib.services.subagents.Subagent;
import io.github.jjdelcerro.noema.lib.services.subagents.SubagentDefinition;
import io.github.jjdelcerro.noema.lib.impl.services.subagents.tools.LaunchSubagentTool;
import io.github.jjdelcerro.noema.lib.impl.services.subagents.tools.ListSubagentsTool;
import io.github.jjdelcerro.noema.lib.spi.AbstractAgentService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.apache.commons.io.FilenameUtils;

/**
 *
 * @author jjdelcerro
 */
public class SubagentsServiceImpl
        extends AbstractAgentService
        implements SubagentsService {

  public SubagentsServiceImpl(AgentServiceFactory factory, Agent agent) {
    super(factory, agent);
  }

  @Override
  public Subagent createSubagent(Agent parent, SubagentDefinition definition, Path workspace) {
    if (workspace == null) {
      String tempFolderName = String.format("subagent_%s_%s", definition.getName(), UUID.randomUUID().toString().substring(0, 8));
      workspace = this.agent.getPaths().getTempFolder().resolve(tempFolderName);
    }
    return new SubagentImpl(parent, definition, workspace);
  }

  @Override
  public SubagentDefinition createSubagentDefinition(Path xmlPath) throws IOException {
    return SubagentDefinitionImpl.from(xmlPath);
  }

  @Override
  public void start() {
    String[] resources = new String[]{
        "var/subagents/project_mapper.md",
        "var/subagents/document_indexer.md"
    };
    for (String resPath : resources) {
      this.agent.installResource(resPath);
    }
  }

  @Override
  public List<AgentTool> getTools() {
    AgentTool[] tools0 = new AgentTool[]{
      new LaunchSubagentTool(this.agent),
      new ListSubagentsTool(this.agent)
    };
    List<AgentTool> tools = new ArrayList<>(Arrays.asList(tools0));
    return tools;
  }

  @Override
  public SubagentDefinition getSubagentDefinition(String subagentName) throws IOException {
    String relativeDescriptorPath = "var/subagents/" + subagentName + ".xml";
    Path xmlPath = this.agent.getPaths().getAgentPath(relativeDescriptorPath);

    if (xmlPath == null || !Files.exists(xmlPath)) {
      throw new RuntimeException("No se encontró el descriptor de subagente: '" + relativeDescriptorPath + "'");
    }
    SubagentDefinition definition = this.createSubagentDefinition(xmlPath);
    return definition;
  }

  public List<SubagentDefinition> getSubagentDefinitions() throws IOException {
    Collection<Path> paths = this.agent.getPaths().listAgentPath("var/subagents");
    List<SubagentDefinition> catalog = new ArrayList<>();

    if (paths != null && !paths.isEmpty()) {
      for (Path path : paths) {
        String fileName = path.getFileName().toString();

        if (!fileName.toLowerCase().endsWith(".xml")) {
          continue;
        }

        SubagentDefinition def = this.getSubagentDefinition(FilenameUtils.getBaseName(path.toString()));
        catalog.add(def);

      }
    }
    return catalog;
  }

}
