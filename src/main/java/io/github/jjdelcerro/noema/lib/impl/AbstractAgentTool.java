package io.github.jjdelcerro.noema.lib.impl;

import com.google.gson.Gson;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
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
  
  protected JsonSchemaElement createJsonStringArraySchema(String description) {
    return JsonArraySchema.builder().description(description).items(new JsonStringSchema()).build();
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

  @Override
  public String trimResult(String result, TrimResultType trimResultType) {
    return null;
  }

  
 
}
