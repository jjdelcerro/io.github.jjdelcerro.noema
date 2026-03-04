package io.github.jjdelcerro.noema.lib.impl.services.sensors.tools;

import com.google.gson.GsonBuilder;
import dev.langchain4j.agent.tool.ToolSpecification;
import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.impl.AbstractAgentTool;
import io.github.jjdelcerro.noema.lib.impl.services.sensors.persistence.SensorInformationGsonAdapter;
import io.github.jjdelcerro.noema.lib.impl.services.sensors.persistence.SensorStatisticsGsonAdapter;
import io.github.jjdelcerro.noema.lib.services.sensors.SensorInformation;
import io.github.jjdelcerro.noema.lib.services.sensors.SensorStatistics;
import io.github.jjdelcerro.noema.lib.services.sensors.SensorsService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tool for the LLM to get the status and statistics of sensors.
 */
public class SensorStatusTool extends AbstractAgentTool {

  public static final String TOOL_NAME = "sensor_status";

  public SensorStatusTool(Agent agent) {
    super(agent, new GsonBuilder()
            .registerTypeHierarchyAdapter(SensorInformation.class, new SensorInformationGsonAdapter())
            .registerTypeHierarchyAdapter(SensorStatistics.class, new SensorStatisticsGsonAdapter())
            .setPrettyPrinting()
            .create());
  }

  @Override
  public ToolSpecification getSpecification() {
    return ToolSpecification.builder()
            .name(TOOL_NAME)
            .description("Muestra el inventario completo de canales sensoriales (capacidades), su configuración y sus estadísticas de actividad. Úsalo para conocer qué sensores tienes disponibles, ver cuáles están silenciados o identificar canales con exceso de ruido")
            .build();
  }

  @Override
  public String execute(String jsonArguments) {
    try {
      SensorsService sensorsService = (SensorsService) agent.getService("SensorsService");
      if (sensorsService == null) {
        return error("SensorsService not found in agent locator.");
      }

      List<SensorInformation> sensors = sensorsService.getAllRegisteredSensors();
      List<Map<String, Object>> statusReport = new ArrayList<>();

      for (SensorInformation info : sensors) {
        Map<String, Object> report = new HashMap<>();
        report.put("info", info);
        SensorStatistics stats = sensorsService.getSensorStatistics(info.getChannel());
        report.put("statistics", stats);
        statusReport.add(report);
      }

      return gson.toJson(statusReport);
    } catch (Exception e) {
      LOGGER.error("Error executing sensor_status tool", e);
      return error("Internal error while retrieving sensor status: " + e.getMessage());
    }
  }
}
