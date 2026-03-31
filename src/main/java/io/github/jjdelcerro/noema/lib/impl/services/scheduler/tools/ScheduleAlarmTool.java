package io.github.jjdelcerro.noema.lib.impl.services.scheduler.tools;

import com.joestelmach.natty.DateGroup;
import com.joestelmach.natty.Parser;
import dev.langchain4j.agent.tool.JsonSchemaProperty;
import dev.langchain4j.agent.tool.ToolSpecification;
import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.impl.services.scheduler.SchedulerService;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Map;
import io.github.jjdelcerro.noema.lib.AgentTool;
import io.github.jjdelcerro.noema.lib.impl.AbstractAgentTool;

/**
 * Herramienta para programar avisos o alarmas usando lenguaje natural (Inglés).
 */
public class ScheduleAlarmTool extends AbstractAgentTool {
  public static final String TOOL_NAME = "schedule_alarm";

  private final Parser dateParser = new Parser();

  public ScheduleAlarmTool(Agent agent) {
    super(agent);
  }

  @Override
  public ToolSpecification getSpecification() {
    return ToolSpecification.builder()
            .name(TOOL_NAME)
            .description("Permite programar una alarma para que te avise cuando se dispare mediante un evento. Cuando recibas el evento de que se ha disparado la alarma sera responsabilidad tuya dar el aviso al usuario.")
            .addParameter("reason", JsonSchemaProperty.STRING,
                    JsonSchemaProperty.description("El motivo o contenido de la alarma."))
            .addParameter("when", JsonSchemaProperty.STRING,
                    JsonSchemaProperty.description("Descripción temporal. DEBE ESTAR EN INGLÉS "
                            + "(ej: 'tomorrow at 5pm', 'in 10 minutes', 'March 21st', '2025-02-10 19:00'). "
                            + "Traduce el tiempo solicitado por el usuario a inglés si es necesario."))
            .build();
  }

  @Override
  public int getMode() {
    // Marcamos como WRITE porque es una acción que cambia el estado futuro del sistema
    return AgentTool.MODE_WRITE;
  }

  @Override
  public String execute(String jsonArguments) {
    try {
      SchedulerService service = (SchedulerService) this.agent.getService(SchedulerService.NAME);

      Map<String, String> args = gson.fromJson(jsonArguments, Map.class);
      String when = args.get("when");
      String reason = args.getOrDefault("reason", "Sin motivo");

      if (when == null || when.isBlank()) {
        return "{\"status\": \"error\", \"message\": \"Falta el parámetro 'when'\"}";
      }

      // Parsear la fecha usando Natty
      List<DateGroup> groups = dateParser.parse(when);
      if (groups.isEmpty() || groups.get(0).getDates().isEmpty()) {
        return "{\"status\": \"error\", \"message\": \"No se pudo entender la fecha: " + when + "\"}";
      }

      // Obtenemos la primera fecha detectada
      Date date = groups.get(0).getDates().get(0);
      LocalDateTime alarmLDT = LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());

      // Llama al servicio de planificacion para registrar la alarma.
      String alarmId = service.schedule(alarmLDT, reason);

      return gson.toJson(Map.of(
              "status", "scheduled",
              "id", alarmId,
              "reason", reason,
              "alarm_time", alarmLDT.toString(),
              "note", "Alarma programada en el sistema."
      ));

    } catch (Exception e) {
      return "{\"status\": \"error\", \"message\": \"" + e.getMessage() + "\"}";
    }
  }
}
