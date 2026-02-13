package io.github.jjdelcerro.chatagent.lib.impl.services.conversation.tools.web;

import com.google.gson.Gson;
import dev.langchain4j.agent.tool.JsonSchemaProperty;
import dev.langchain4j.agent.tool.ToolSpecification;
import io.github.jjdelcerro.chatagent.lib.Agent;

import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import io.github.jjdelcerro.chatagent.lib.AgentTool;
import java.util.LinkedHashMap;

/**
 * Herramienta para obtener la fecha y hora actual del sistema. Permite
 * especificar una zona horaria opcional.
 */
public class TimeTool implements AgentTool {

  private final Gson gson = new Gson();

  public TimeTool(Agent agent) {
  }

  @Override
  public ToolSpecification getSpecification() {
    return ToolSpecification.builder()
            .name("get_current_time")
            .description("Obtiene la fecha, hora y zona horaria actual del sistema. "
                    + "Utilizala cuando necesites precision temporal para programar tareas, "
                    + "calcular duraciones o entender referencias relativas (ej: 'manana').")
            .addParameter("timezone", JsonSchemaProperty.STRING,
                    JsonSchemaProperty.description("ID de la zona horaria (ej: 'Europe/Madrid', 'UTC'). Opcional."))
            .build();
  }

  @Override
  public int getMode() {
    return AgentTool.MODE_READ;
  }

  @Override
  public String execute(String jsonArguments) {
    try {
      Map<String, String> args = gson.fromJson(jsonArguments, Map.class);
      ZoneId zoneId;

      if (args != null && args.containsKey("timezone")) {
        try {
          zoneId = ZoneId.of(args.get("timezone"));
        } catch (Exception e) {
          return "{\"status\": \"error\", \"message\": \"Zona horaria invalida.\"}";
        }
      } else {
        zoneId = ZoneId.systemDefault();
      }

      ZonedDateTime now = ZonedDateTime.now(zoneId);

      // Formatos para que el LLM tenga distintas representaciones
      DateTimeFormatter iso = DateTimeFormatter.ISO_ZONED_DATE_TIME;
      DateTimeFormatter human = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM 'de' yyyy, HH:mm:ss");

      return gson.toJson(Map.of(
              "status", "success",
              "iso_8601", now.format(iso),
              "readable", now.format(human),
              "timezone", zoneId.getId(),
              "offset", now.getOffset().toString()
      ));

    } catch (Exception e) {
      return "{\"status\": \"error\", \"message\": \"" + e.getMessage() + "\"}";
    }
  }
}
