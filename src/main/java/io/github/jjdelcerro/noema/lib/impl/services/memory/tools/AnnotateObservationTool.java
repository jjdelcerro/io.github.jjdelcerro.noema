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
""")
                .addStringParameter("source", false, "El origen de la información (ej: nombre del archivo, URL o 'instrucción del usuario').")
                .addStringParameter("note", false, "Los hechos clave que deseas fijar en tu memoria episódica.")
                .addStringParameter("resource_id", true, "OBLIGATORIO si la información proviene de la lectura de un archivo o web. Debes copiar exactamente el RESOURCE_ID proporcionado por la herramienta de lectura. Dejar vacío ÚNICAMENTE si el dato proviene directamente de una instrucción del usuario en el chat.");
    }

    @Override
    public int getMode() {
        return AgentTool.MODE_READ;
    }

    @Override
    public int getType() {
        /*
    ATENCION!! Aqui tenemos un problema.
    Si quiero ser "purista" esta deberia ser una herramienta de tipo "memoria", pero
    al hacerlo ahora mismo se etiquetaria como un "lookup_turn", y eso forzaria ha hacer
    cambios en el reasoning y en el memory service si no queremos que el LLM que va ha 
    generar el punto de guardado se lie.
    De momento, siendo menos purista, la voy a dejar de tipo "operativo", y aprovendo que
    la llamada a una herramienta se guarda tal cual en el turno de la BBDD, ya que solo
    se "truncan" las salidas de las herramientas. Vamos que aprovechamos un efecto 
    secundario de la arquitectura para conseguir lo que queremos. Pero hay que tener
    mucho cuidado con esto ya que si en el futuro decidimos recortar las llamadas a las
    herramientas esto dejaria de funcionar.
    Segun como vaya esto habria que abordar los cambios para tratar la herramienta
    como "memory".
         */
        return TYPE_OPERATIONAL;
//    return TYPE_MEMORY;
    }

    @Override
    public String execute(String jsonArguments) {
        // La herramienta en sí no hace nada funcional, el valor real 
        // está en que el LLM formula los argumentos y el SourceOfTruth los guarda.

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
}
