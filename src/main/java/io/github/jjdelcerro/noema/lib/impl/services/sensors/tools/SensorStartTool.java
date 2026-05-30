package io.github.jjdelcerro.noema.lib.impl.services.sensors.tools;

import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.impl.AbstractAgentTool;
import io.github.jjdelcerro.noema.lib.impl.ToolSpecificationBuilder;
import io.github.jjdelcerro.noema.lib.services.sensors.SensorsService;
import java.util.List;
import java.util.Map;

public class SensorStartTool extends AbstractAgentTool {

  public static final String NAME = "sensor_start";

  public SensorStartTool(Agent agent) {
    super(agent);
  }

  @Override
  public ToolSpecificationBuilder getSpecification() {
    return ToolSpecificationBuilder.create()
            .name(NAME)
            .description("Reactiva la recepción de eventos en los canales sensoriales especificados.")
            .addStringArrayParameter("channels", false, "Lista de identificadores de canal a iniciar (ej: ['telegram', 'email']).");
  }

  @Override
  public String execute(String jsonArguments) {
    try {
      SensorsService service = (SensorsService) agent.getService("SensorsService");
      Map<String, Object> args = gson.fromJson(jsonArguments, Map.class);
      List<String> channels = (List<String>) args.get("channels");

      if (channels == null || channels.isEmpty()) {
        return error("Debe especificar al menos un canal para activar.");
      }

      for (String channel : channels) {
        service.setSilenced(channel, false);
      }

      return gson.toJson(Map.of("status", "success", "activated_channels", channels));
    } catch (Exception e) {
      return error("Error al activar sensores: " + e.getMessage());
    }
  }
}
