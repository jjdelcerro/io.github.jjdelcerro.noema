package io.github.jjdelcerro.noema.lib.impl.services.sensors.tools;

import dev.langchain4j.agent.tool.JsonSchemaProperty;
import dev.langchain4j.agent.tool.ToolSpecification;
import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.impl.AbstractAgentTool;
import io.github.jjdelcerro.noema.lib.services.sensors.SensorsService;
import java.util.List;
import java.util.Map;

@SuppressWarnings("UseSpecificCatch")
public class SensorStopTool extends AbstractAgentTool {

  public static final String NAME = "sensor_stop";

  public SensorStopTool(Agent agent) {
    super(agent);
  }

  @Override
  public ToolSpecification getSpecification() {
    return ToolSpecification.builder()
            .name(NAME)
            .description("Suspende temporalmente la recepción de eventos de uno o varios canales sensoriales. "
                    + "Úsalo para evitar distracciones durante tareas que requieran alta concentración.")
            .addParameter("channels", JsonSchemaProperty.type("array"),
                    JsonSchemaProperty.items(JsonSchemaProperty.STRING),
                    JsonSchemaProperty.description("Lista de identificadores de canal a silenciar (ej: ['telegram', 'email'])."))
            .build();
  }

  @Override
  public String execute(String jsonArguments) {
    try {
      SensorsService service = (SensorsService) agent.getService("SensorsService");
      Map<String, Object> args = gson.fromJson(jsonArguments, Map.class);
      List<String> channels = (List<String>) args.get("channels");

      if (channels == null || channels.isEmpty()) {
        return error("Debe especificar al menos un canal para detener.");
      }

      for (String channel : channels) {
        service.setSilenced(channel, true);
      }

      return gson.toJson(Map.of("status", "success", "silenced_channels", channels));
    } catch (Exception e) {
      return error("Error al detener sensores: " + e.getMessage());
    }
  }
}
