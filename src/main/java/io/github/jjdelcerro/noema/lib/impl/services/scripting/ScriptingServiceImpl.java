package io.github.jjdelcerro.noema.lib.impl.services.scripting;

import io.github.jjdelcerro.noema.lib.api.services.scripting.ScriptingService;
import io.github.jjdelcerro.noema.lib.api.Agent;
import io.github.jjdelcerro.noema.lib.api.AgentServiceFactory;
import io.github.jjdelcerro.noema.lib.api.AgentTool;
import io.github.jjdelcerro.noema.lib.impl.services.scripting.tools.ScriptExecuteTool;
import io.github.jjdelcerro.noema.lib.api.services.scripting.ScriptEngine;
import io.github.jjdelcerro.noema.lib.spi.AbstractAgentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ScriptingServiceImpl
        extends AbstractAgentService
        implements ScriptingService {

  private static final Logger LOGGER = LoggerFactory.getLogger(ScriptingServiceImpl.class);

  public ScriptingServiceImpl(AgentServiceFactory factory, Agent agent) {
    super(factory, agent);
  }

  @Override
  public void start() {
  }

  @Override
  public List<AgentTool> getTools() {
    AgentTool[] tools0 = new AgentTool[]{
      new ScriptExecuteTool(this.agent),
    };
    List<AgentTool> tools = new ArrayList<>(Arrays.asList(tools0));
    return tools;
  }

  @Override
  public ScriptEngine createScriptEngine() {
    return new ScriptEngineImpl(agent);
  }

}
