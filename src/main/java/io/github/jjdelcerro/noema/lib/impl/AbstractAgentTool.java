package io.github.jjdelcerro.noema.lib.impl;

import com.google.gson.Gson;
import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.AgentAccessControl.AccessMode;
import static io.github.jjdelcerro.noema.lib.AgentAccessControl.AccessMode.PATH_ACCESS_READ;
import io.github.jjdelcerro.noema.lib.AgentTool;
import io.github.jjdelcerro.noema.lib.impl.services.reasoning.ReasoningServiceImpl;
import java.nio.file.Path;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author jjdelcerro
 */
public abstract class AbstractAgentTool implements AgentTool {

  protected static final Logger LOGGER = LoggerFactory.getLogger(AbstractAgentTool.class);

  protected final Agent agent;
  protected final Gson gson;

  public AbstractAgentTool(Agent agent) {
    this(agent, new Gson());
  }

  protected AbstractAgentTool(Agent agent, Gson customGson) {
    this.agent = agent;
    this.gson = customGson;
  }

  protected String error(String m) {
    return gson.toJson(Map.of("status", "error", "message", m));
  }

  protected Path resolvePathOrNull(String path) {
    Path x = this.agent.getAccessControl().resolvePathOrNull(path, PATH_ACCESS_READ);
    return x;
  }

  protected Path resolvePathOrNull(String path, AccessMode access) {
    Path x = this.agent.getAccessControl().resolvePathOrNull(path, access);
    return x;
  }

  protected ReasoningServiceImpl getReasoningService() {
    return (ReasoningServiceImpl) agent.getService(ReasoningServiceImpl.NAME);
  }
}
