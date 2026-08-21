package io.github.jjdelcerro.noema.lib.impl.services.memory.tools;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.AgentTool;
import io.github.jjdelcerro.noema.lib.impl.AbstractAgentTool;
import io.github.jjdelcerro.noema.lib.impl.ToolSpecificationBuilder;
import java.util.LinkedHashMap;
import java.util.Map;

@SuppressWarnings("UseSpecificCatch")
public class AnnotateObservationTool extends AbstractAgentTool {

  public static final String TOOL_NAME = "annotate_observation";

  public AnnotateObservationTool(Agent agent) {
    super(agent);
  }

  @Override
  public ToolSpecificationBuilder getSpecification() {
    return ToolSpecificationBuilder.create()
            .name(TOOL_NAME)
            .description(
                    """
Utiliza esta herramienta para guardar una nota o resumen sobre información que acabas de leer o procesar (ej. tras usar file_read o web_search).
Utilizala para guardar:
* Insights, resúmenes o relaciones que infieres entre múltiples fuentes.
* Patrones arquitectónicos o de diseño que identificas al leer ficheros.
* Conexiones que establezcas entre archivos
* Comprensión del flujos que infieras

Recuerda que la herramienta está pensada para retener valor cognitivo, no para duplicar datos,
no la uses para duplicar contenido que puedes recuperar de nuevo con file_read o web_search.

**Parámetro opcional resource_id:**
Si estás anotando información extraída de un recurso paginado (por ejemplo, tras una llamada a file_read o web_get_content),
DEBES incluir el campo "resource_id" con el valor exacto que apareció en el campo RESOURCE_ID de la respuesta de esa herramienta.
Esto permite vincular la anotación al recurso original y evita que el sistema te lo recuerde más tarde.
                    
**Uso del parámetro `type`:**
Este campo está reservado para flujos especializados. Por defecto DEBES omitirlo. 
Solo debes incluirlo cuando recibas una instrucción explícita que te indique bajo qué tipo clasificar la información.                    
""")
            .addStringParameter("source", false, "El origen de la información (ej: nombre del archivo, URL o 'instrucción del usuario').")
            .addStringParameter("note", false, "Los hechos clave que deseas fijar en tu memoria episódica.")
            .addStringParameter("resource_id", true, "OBLIGATORIO si la información proviene de la lectura de un archivo o web. Debes copiar exactamente el RESOURCE_ID proporcionado por la herramienta de lectura. Dejar vacío ÚNICAMENTE si el dato proviene directamente de una instrucción del usuario en el chat.")
            .addStringParameter(
                "type", 
                true, 
                "USO RESTRINGIDO. Omitir o dejar vacío por defecto. "
                + "Rellenar ÚNICAMENTE si el usuario, una directiva explícita de tu rol/subagente "
                + "o una notificación del sistema (SYSTEMNOTIFICATION) te ordenan fijar un tipo específico "
                + "para esta anotación. No inventes categorías por iniciativa propia."
            )    
            ;
  }

  @Override
  public int getMode() {
    return AgentTool.MODE_READ;
  }

  @Override
  public int getType() {
    return TYPE_ANNOTATION; // Las anotaciones tienen entidad propia.
  }

  @Override
  public String execute(String jsonArguments) {
    // La herramienta en sí no hace nada funcional, el valor real 
    // está en que el LLM formula los argumentos y el EpisodicMemory los guarda.

    // Parsear argumentos para extraer el resource_id si está presente
    Map<String, Object> args = gson.fromJson(jsonArguments, Map.class);
    String resourceId = args != null ? (String) args.get("resource_id") : null;

    // Construir respuesta incluyendo resource_id si se proporcionó
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("status", "success");
    result.put("message", "Anotación fijada correctamente. Se incluirá en la próxima consolidación de memoria.");
    if (resourceId != null && !resourceId.isBlank()) {
      result.put("resource_id", resourceId);
    }

    return gson.toJson(result);
  }

  public String getResourceIdFromResultMessage(ToolExecutionResultMessage message) {
    String text = message.text();
    if (text == null) {
      return null;
    }
    try {
      JsonObject json = JsonParser.parseString(text).getAsJsonObject();
      if (json.has("resource_id")) {
        return json.get("resource_id").getAsString();
      }
    } catch (Exception e) {
      // ignore parse errors
    }
    return null;
  }

  /**
   * Extrae de forma segura el parametro 'type' a partir del texto de la llamada
   * a la herramienta. Soporta tanto JSON directo de argumentos como
   * representaciones textuales envolventes.
   *
   * @param toolCall Cadena con los argumentos o la llamada a la herramienta.
   * @return El tipo de anotacion normalizado, o null si no esta presente o no
   * es valido.
   */
  public static String getAnnotationTypeFromToolCall(String toolCall) {
    if (toolCall == null || toolCall.isBlank()) {
      return null;
    }

    try {
      String jsonText = toolCall.trim();

      // Si no empieza directamente por '{', localizamos el bloque JSON en la cadena
      if (!jsonText.startsWith("{")) {
        int firstBrace = jsonText.indexOf('{');
        int lastBrace = jsonText.lastIndexOf('}');
        if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
          jsonText = jsonText.substring(firstBrace, lastBrace + 1);
        } else {
          return null;
        }
      }

      JsonObject json = JsonParser.parseString(jsonText).getAsJsonObject();

      // Si el JSON contiene un campo anidado 'arguments' como string (ej. ToolExecutionRequest serializado)
      if (json.has("arguments") && json.get("arguments").isJsonPrimitive()) {
        String innerArgs = json.get("arguments").getAsString();
        if (innerArgs != null && innerArgs.trim().startsWith("{")) {
          json = JsonParser.parseString(innerArgs).getAsJsonObject();
        }
      }

      if (json.has("type") && !json.get("type").isJsonNull()) {
        String type = json.get("type").getAsString().trim();
        if (!type.isEmpty()) {
          return type;
        }
      }
    } catch (Exception e) {
      // Ignorar errores de parseo y retornar null de forma segura
    }

    return null;
  }
}
