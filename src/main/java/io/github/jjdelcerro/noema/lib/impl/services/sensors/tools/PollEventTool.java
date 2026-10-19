package io.github.jjdelcerro.noema.lib.impl.services.sensors.tools;

import com.google.gson.GsonBuilder;
import io.github.jjdelcerro.noema.lib.api.Agent;
import io.github.jjdelcerro.noema.lib.impl.tools.AbstractAgentTool;
import io.github.jjdelcerro.noema.lib.impl.tools.ToolSpecificationBuilder;
import io.github.jjdelcerro.noema.lib.impl.services.sensors.persistence.SensorEventGsonAdapter;
import io.github.jjdelcerro.noema.lib.impl.services.sensors.SensorsServiceImpl;
import io.github.jjdelcerro.noema.lib.api.services.sensors.SensorEvent;
import io.github.jjdelcerro.noema.lib.api.services.sensors.SensorsService;

/**
 * Tool that allows the LLM to pool for pending sensory events from all
 * registered sensors.
 */
public class PollEventTool extends AbstractAgentTool { 

  public static final String TOOL_NAME = Agent.POLL_EVENT_TOOL_NAME;

  public PollEventTool(Agent agent) {
    super(agent, new GsonBuilder()
            .registerTypeHierarchyAdapter(SensorEvent.class, new SensorEventGsonAdapter((SensorsServiceImpl) agent.getService(SensorsService.NAME)))
            .create());
  }

  @Override
  public ToolSpecificationBuilder getSpecification() {
    return ToolSpecificationBuilder.create()
            .name(TOOL_NAME)
            .description("Polls for pending sensory events from all registered sensors. "
                    + "This tool should be used when notified about pending perceptions.");
  }

  @Override
  public ToolResult execute(String jsonArguments) {
    return result("{\"status\": \"success\", \"events\": [], \"message\": \"No hay eventos pendientes de procesar.\"}");
  }
}
