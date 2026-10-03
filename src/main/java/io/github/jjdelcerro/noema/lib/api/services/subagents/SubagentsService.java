package io.github.jjdelcerro.noema.lib.api.services.subagents;

import io.github.jjdelcerro.noema.lib.api.Agent;
import io.github.jjdelcerro.noema.lib.api.AgentService;
import io.github.jjdelcerro.noema.lib.api.services.subagents.Subagent;
import io.github.jjdelcerro.noema.lib.api.services.subagents.SubagentDefinition;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 *
 * @author jjdelcerro
 */
public interface SubagentsService extends AgentService {

  public static String NAME = "subagents";

  public SubagentDefinition getSubagentDefinition(String name) throws IOException;
  
  public Subagent createSubagent(Agent parent, SubagentDefinition definition, Path workspace);

  public SubagentDefinition createSubagentDefinition(Path xmlPath) throws IOException;
  
  public List<SubagentDefinition> getSubagentDefinitions() throws IOException;

}
