package io.github.jjdelcerro.noema.lib.impl.services.subagents.tools;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.AgentLocator;
import io.github.jjdelcerro.noema.lib.AgentTool;
import io.github.jjdelcerro.noema.lib.services.subagents.Subagent;
import io.github.jjdelcerro.noema.lib.services.subagents.SubagentDefinition;
import io.github.jjdelcerro.noema.lib.impl.AbstractAgentTool;
import io.github.jjdelcerro.noema.lib.impl.ToolSpecificationBuilder;
import io.github.jjdelcerro.noema.lib.services.subagents.SubagentsService;
import org.apache.commons.lang3.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tool that launches a specialized worker subagent asynchronously in the
 * background. The subagent executes its isolated task and notifies the
 * originating channel upon completion.
 */
public class LaunchSubagentTool extends AbstractAgentTool {

  public static final String TOOL_NAME = "launch_subagent";

  public LaunchSubagentTool(Agent agent) {
    super(agent);
  }

  @Override
  public ToolSpecificationBuilder getSpecification() {
    return ToolSpecificationBuilder.create()
            .name(TOOL_NAME)
            .description(
                    "Lanza un subagente especializado en segundo plano para realizar tareas pesadas o aisladas "
                    + "(como indexación exhaustiva de documentos, auditorías de código, migraciones o extracciones). "
                    + "El subagente se ejecuta de forma totalmente asíncrona sin bloquear la conversación actual. "
                    + "Cuando finalice su trabajo, recibirás automáticamente una notificación del sistema en este canal."
            )
            .addStringParameter("subagent_name", false, "El nombre de la receta XML del subagente en var/subagents/ (ej: 'document_indexer').")
            .addStringParameter("params", false, "Objeto JSON con los pares clave-valor para sustituir los placeholders en los prompts del subagente (ej: {\"FILE_PATH\": \"documento.md\", \"OUTPUT_PATH\": \"documento.index.md\"}).");
  }

  @Override
  public int getMode() {
    return AgentTool.MODE_WRITE;
  }

  @Override
  public String execute(String jsonArguments) {
    try {
      SubagentsService service = (SubagentsService) this.agent.getService(SubagentsService.NAME);

      if (StringUtils.isBlank(jsonArguments)) {
        return error("Los argumentos de la herramienta no pueden estar vacíos.");
      }

      JsonObject args = JsonParser.parseString(jsonArguments).getAsJsonObject();

      if (!args.has("subagent_name") || args.get("subagent_name").isJsonNull()) {
        return error("El parámetro 'subagent_name' es obligatorio.");
      }

      String subagentName = args.get("subagent_name").getAsString().trim();
      if (subagentName.isEmpty()) {
        return error("El nombre del subagente no puede estar vacío.");
      }

      // Extract key-value params map
      Map<String, String> paramsMap = extractParams(args);

      SubagentDefinition definition = service.getSubagentDefinition(subagentName);
      Subagent subagent = service.createSubagent(this.agent, definition, null);
      int subagentId = subagent.launch(paramsMap);

      LOGGER.info("Subagent '{}' launched with ID {} in background thread", subagentName, subagentId);

      Map<String, Object> result = new LinkedHashMap<>();
      result.put("status", "launched");
      result.put("subagent_id", subagentId);
      result.put("subagent_name", subagentName);
      result.put("message", String.format(
              "El subagente '%s' (ID: %d) se ha iniciado con éxito en segundo plano. "
              + "Recibirás una notificación en este canal cuando concluya su tarea.",
              subagentName, subagentId
      ));

      return gson.toJson(result);

    } catch (Exception e) {
      LOGGER.error("Error launching subagent from arguments: " + jsonArguments, e);
      return error("Fallo al iniciar el subagente: " + e.getMessage());
    }
  }

  private Map<String, String> extractParams(JsonObject args) {
    Map<String, String> paramsMap = new HashMap<>();

    if (!args.has("params") || args.get("params").isJsonNull()) {
      return paramsMap;
    }

    JsonElement paramsElement = args.get("params");

    if (paramsElement.isJsonObject()) {
      JsonObject paramsObj = paramsElement.getAsJsonObject();
      for (String key : paramsObj.keySet()) {
        JsonElement val = paramsObj.get(key);
        if (val != null && !val.isJsonNull()) {
          paramsMap.put(key, val.isJsonPrimitive() ? val.getAsString() : val.toString());
        }
      }
    } else if (paramsElement.isJsonPrimitive() && paramsElement.getAsJsonPrimitive().isString()) {
      // In case the model sends params as an escaped JSON string
      String paramsStr = paramsElement.getAsString().trim();
      if (paramsStr.startsWith("{")) {
        try {
          JsonObject parsed = JsonParser.parseString(paramsStr).getAsJsonObject();
          for (String key : parsed.keySet()) {
            JsonElement val = parsed.get(key);
            if (val != null && !val.isJsonNull()) {
              paramsMap.put(key, val.isJsonPrimitive() ? val.getAsString() : val.toString());
            }
          }
        } catch (Exception ignored) {
          // Ignore parse fallback
        }
      }
    }

    return paramsMap;
  }
}
