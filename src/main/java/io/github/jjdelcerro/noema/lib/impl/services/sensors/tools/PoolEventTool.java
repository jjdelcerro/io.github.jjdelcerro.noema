package io.github.jjdelcerro.noema.lib.impl.services.sensors.tools;

import com.google.gson.GsonBuilder;
import dev.langchain4j.agent.tool.ToolSpecification;
import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.impl.AbstractAgentTool;
import io.github.jjdelcerro.noema.lib.impl.services.sensors.persistence.SensorEventGsonAdapter;
import io.github.jjdelcerro.noema.lib.impl.services.sensors.SensorsServiceImpl;
import io.github.jjdelcerro.noema.lib.services.sensors.SensorEvent;
import io.github.jjdelcerro.noema.lib.services.sensors.SensorsService;

/**
 * Tool that allows the LLM to pool for pending sensory events from all
 * registered sensors.
 */
public class PoolEventTool extends AbstractAgentTool { // FIXME: Esta tool esta repetida!!!

  public static final String TOOL_NAME = "pool_event";

  public PoolEventTool(Agent agent) {
    super(agent, new GsonBuilder()
            .registerTypeHierarchyAdapter(SensorEvent.class, new SensorEventGsonAdapter((SensorsServiceImpl) agent.getService(SensorsService.NAME)))
            .create());
  }

  @Override
  public ToolSpecification getSpecification() {
    return ToolSpecification.builder()
            .name(TOOL_NAME)
            .description("Pools for pending sensory events from all registered sensors. "
                    + "This tool should be used when notified about pending perceptions.")
            .build();
  }

  @Override
  public String execute(String jsonArguments) {
    return "{\"status\": \"success\", \"events\": [], \"message\": \"No hay eventos pendientes de procesar.\"}";
//    try {
//      // We assume SensorsService is registered with this name
//      SensorsService sensorsService = (SensorsService) agent.getService("SensorsService");
//      if (sensorsService == null) {
//        return error("SensorsService not found in agent locator.");
//      }
//
//      List<SensorEvent> events = sensorsService.getPendingEvents();
//      return gson.toJson(events);
//    } catch (Exception e) {
//      LOGGER.error("Error executing pool_event tool", e);
//      return error("Internal error while pooling events: " + e.getMessage());
//    }
  }
}
