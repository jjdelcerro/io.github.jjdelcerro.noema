package io.github.jjdelcerro.chatagent.lib.impl.tools.time;

import com.google.gson.Gson;
import com.joestelmach.natty.DateGroup;
import com.joestelmach.natty.Parser;
import dev.langchain4j.agent.tool.JsonSchemaProperty;
import dev.langchain4j.agent.tool.ToolSpecification;
import io.github.jjdelcerro.chatagent.lib.Agent;
import io.github.jjdelcerro.chatagent.lib.tools.AgenteTool;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Herramienta para programar avisos o alarmas usando lenguaje natural (Inglés).
 */
public class ScheduleAlarmTool implements AgenteTool {

  private final Gson gson = new Gson();
  private final Parser dateParser = new Parser();
  private final Agent agent;

  public ScheduleAlarmTool(Agent agent) {
    this.agent = agent;
  }

  @Override
  public ToolSpecification getSpecification() {
    return ToolSpecification.builder()
            .name("schedule_alarm")
            // Descripción general en el idioma del sistema (Castellano)
            .description("Reserva o programa una alarma o recordatorio en el sistema.")
            .addParameter("reason", JsonSchemaProperty.STRING,
                    JsonSchemaProperty.description("El motivo o contenido del recordatorio."))
            // Instrucción específica sobre el idioma para el parámetro técnico
            .addParameter("when", JsonSchemaProperty.STRING,
                    JsonSchemaProperty.description("Descripción temporal. DEBE ESTAR EN INGLÉS "
                            + "(ej: 'tomorrow at 5pm', 'in 10 minutes', 'March 21st', '2025-02-10 19:00'). "
                            + "Traduce el tiempo solicitado por el usuario a inglés si es necesario."))
            .build();
  }

  @Override
  public int getMode() {
    // Marcamos como WRITE porque es una acción que cambia el estado futuro del sistema
    return AgenteTool.MODE_WRITE;
  }

  @Override
  public String execute(String jsonArguments) {
    try {
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

      // Por ahora, como pides, simplemente devolvemos la confirmación al LLM.
      // En el futuro, aquí es donde registrarías esto en una tabla de la DB o un Scheduler.
      return gson.toJson(Map.of(
              "status", "scheduled",
              "reason", reason,
              "alarm_time", alarmLDT.toString(),
              "note", "Alarma programada en memoria del sistema."
      ));

    } catch (Exception e) {
      return "{\"status\": \"error\", \"message\": \"" + e.getMessage() + "\"}";
    }
  }
}
